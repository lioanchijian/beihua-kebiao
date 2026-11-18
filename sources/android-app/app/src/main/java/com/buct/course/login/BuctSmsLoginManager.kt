package com.buct.course.login

import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * 北化统一身份认证「短信验证码登录」原生实现（金智 linkid, sidportal.buct.edu.cn）。
 *
 * 2026-10 逆向实测全链路（与网页行为一致）：
 * 1. POST /sso-extend/protected/api/aggregate/sms/publicNoToken/generate
 *      body {"phone":"学号","type":"DEFAULT"} + Csrf/Device-Id 头 → 200 PNG 图形验证码
 * 2. GET  /sso-extend/protected/api/aggregate/sms/publicNoToken/sendCheckCaptcha/DEFAULT/{图形码}/{学号}/0008
 *      → 200 {"result":true} 触发短信（业务号必须 0008；0024 可发短信但校验查不到记录）
 * 3. POST /api/protected/sms/checkTokenResult
 *      body {"phone":"学号","token":"短信码","delete":false} → 200 {"code":200,"data":true}
 * 4. GET  /login?service=<jziotlogin> 取 login-page-flowkey(execution)
 * 5. POST /login 表单(application/x-www-form-urlencoded):
 *      username=学号, password=短信码(明文), type=smsLogin, _eventId=submit,
 *      geolocation=, execution=, captcha_code= → 302 → jwglxt/sso/jziotlogin?ticket=ST-..
 * 6. 跟随 → jwglxt/xtgl/index_initMenu.html → 教务会话 cookie
 *
 * 关键约束（踩坑结论）：
 * - 发送与校验必须使用同一 Device-Id（设备指纹），否则 checkTokenResult 恒 1320009。
 * - 发送/校验/登录必须同一 Cookie 会话（SESSION 变化会丢验证码）。
 * - 图形码识别：手写体 OCR 不可靠，App 内应把 PNG 直接展示给用户肉眼识别。
 */
class BuctSmsLoginManager private constructor() {

    interface SmsLoginCallback {
        /** 图形验证码已生成，交给 UI 展示给用户识别 */
        fun onCaptchaRequired(pngBytes: ByteArray)
        /** 短信已发出，等待用户输入短信验证码 */
        fun onSmsSent(expireSeconds: Int)
        /** 登录成功，返回教务会话 cookie */
        fun onSuccess(cookie: String)
        /** 图形码错误（可重试） */
        fun onCaptchaInvalid()
        /** 验证码错误（1320009）或过期 */
        fun onSmsCodeInvalid(message: String)
        /** 短信发送失败/被限流（13002022 等） */
        fun onSmsSendFailed(message: String)
        /** 其他错误 */
        fun onError(message: String)
    }

    private val stateLock = Any()
    private var activeAttempt: SmsAttempt? = null

    /** 开始短信登录：拉取图形验证码 */
    fun startLogin(username: String, callback: SmsLoginCallback) {
        clearState()
        if (username.isBlank()) {
            callback.onError("请输入学号/工号")
            return
        }
        val cookieJar = MatchingMemoryCookieJar()
        val attempt = SmsAttempt(username, callback, cookieJar, buildClient(cookieJar))
        synchronized(stateLock) { activeAttempt = attempt }
        fetchCaptcha(attempt)
    }

    /** 用户识别完图形码后调用：发送短信 */
    fun submitCaptcha(captchaCode: String, callback: SmsLoginCallback) {
        val attempt = synchronized(stateLock) { activeAttempt }
        if (attempt == null || attempt.completed.get()) {
            callback.onError("短信登录会话已失效，请重新开始")
            return
        }
        attempt.callback = callback
        sendSms(attempt, captchaCode.trim())
    }

    /** 用户输入短信验证码后调用：校验并登录 */
    fun submitSmsCode(smsCode: String, callback: SmsLoginCallback) {
        val attempt = synchronized(stateLock) { activeAttempt }
        if (attempt == null || attempt.completed.get()) {
            callback.onError("短信登录会话已失效，请重新开始")
            return
        }
        attempt.callback = callback
        verifyAndLogin(attempt, smsCode.trim())
    }

    fun clearState() {
        val attempt = synchronized(stateLock) {
            val current = activeAttempt
            activeAttempt = null
            current
        } ?: return
        attempt.completed.set(true)
        attempt.currentCall?.cancel()
    }

    // ---------- 阶段一：图形验证码 ----------

    private fun fetchCaptcha(attempt: SmsAttempt) {
        val url = attempt.portal.newBuilder()
            .encodedPath("/sso-extend/protected/api/aggregate/sms/publicNoToken/generate")
            .build()
        val json = "{\"phone\":\"${attempt.username}\",\"type\":\"DEFAULT\"}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh")
            .header("sid-language", "zh_CN")
            .header("Csrf-Key", attempt.csrfKey())
            .header("Csrf-Value", attempt.csrfValue())
            .header("Device-Id", attempt.deviceId)
            .header("Content-Type", "application/json")
            .header("Origin", attempt.portal.toString())
            .header("Referer", attempt.portal.toString() + "/login")
            .post(json.toRequestBody())
            .build()
        executeNoRedirect(attempt, request, onFailure = { fail(attempt, "无法获取图形验证码，请检查网络") }) { resp ->
            resp.use {
                val bytes = it.body?.bytes()
                if (it.isSuccessful && bytes != null && bytes.size > 16) {
                    attempt.callback.onCaptchaRequired(bytes)
                } else {
                    fail(attempt, "图形验证码获取失败")
                }
            }
        }
    }

    // ---------- 阶段二：发送短信 ----------

    private fun sendSms(attempt: SmsAttempt, captcha: String) {
        if (attempt.completed.get()) return
        if (captcha.length < 3) {
            attempt.callback.onCaptchaInvalid()
            return
        }
        val url = attempt.portal.newBuilder()
            .encodedPath("/sso-extend/protected/api/aggregate/sms/publicNoToken/sendCheckCaptcha/DEFAULT/${captcha}/${attempt.username}/0008")
            .build()
        android.util.Log.d(TAG, "sendSms GET ${url.encodedPath}?${url.encodedQuery}")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh")
            .header("sid-language", "zh_CN")
            .header("Csrf-Key", attempt.csrfKey())
            .header("Csrf-Value", attempt.csrfValue())
            .header("Device-Id", attempt.deviceId)
            .header("Origin", attempt.portal.toString())
            .header("Referer", attempt.portal.toString() + "/login")
            .get()
            .build()
        executeNoRedirect(attempt, request, onFailure = { fail(attempt, "发送短信失败，请检查网络") }) { resp ->
            resp.use {
                val text = it.body?.string().orEmpty()
                val errCode = extractJsonField(text, "errorCode") ?: ""
                android.util.Log.d(TAG, "sendSms resp ${it.code}: ${text.take(200)}")
                when {
                    !it.isSuccessful -> fail(attempt, "发送短信失败 (${it.code})")
                    text.contains("\"result\":true") || text.contains("\"code\":200") ->
                        attempt.callback.onSmsSent(120)
                    errCode == "2180000" -> attempt.callback.onCaptchaInvalid()
                    errCode == "13001016" -> attempt.callback.onError("验证码还在有效期内，请勿重复发送")
                    errCode == "13002022" || text.contains("BUSINESS_LIMIT") ->
                        attempt.callback.onSmsSendFailed("短信发送过于频繁，请稍后再试")
                    else -> fail(attempt, "发送短信失败：${errCode.ifEmpty { text.take(120) }}")
                }
            }
        }
    }

    // ---------- 阶段三：校验短信码 + 登录 ----------

    private fun verifyAndLogin(attempt: SmsAttempt, smsCode: String) {
        if (attempt.completed.get()) return
        if (smsCode.length < 4) {
            attempt.callback.onSmsCodeInvalid("请输入短信验证码")
            return
        }
        // 3.1 checkTokenResult
        val url = attempt.portal.newBuilder()
            .encodedPath("/api/protected/sms/checkTokenResult")
            .build()
        val json = "{\"phone\":\"${attempt.username}\",\"token\":\"$smsCode\",\"delete\":false}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh")
            .header("sid-language", "zh_CN")
            .header("Csrf-Key", attempt.csrfKey())
            .header("Csrf-Value", attempt.csrfValue())
            .header("Device-Id", attempt.deviceId)
            .header("Content-Type", "application/json")
            .header("Origin", attempt.portal.toString())
            .header("Referer", attempt.portal.toString() + "/login")
            .post(json.toRequestBody())
            .build()
        executeNoRedirect(attempt, request, onFailure = { fail(attempt, "验证短信码失败，请检查网络") }) { resp ->
            resp.use {
                val text = it.body?.string().orEmpty()
                if (it.isSuccessful && text.contains("\"code\":200")) {
                    submitSmsLoginForm(attempt, smsCode)
                } else {
                    val errCode = extractJsonField(text, "errorCode")
                    attempt.callback.onSmsCodeInvalid(
                        when (errCode) {
                            "1320009" -> "验证码有误，请重新输入"
                            else -> "验证码校验失败，请重试"
                        }
                    )
                }
            }
        }
    }

    /** 3.2 校验通过后 POST /login 表单完成登录 */
    private fun submitSmsLoginForm(attempt: SmsAttempt, smsCode: String) {
        val url = attempt.portal.newBuilder()
            .encodedPath("/login")
            .setQueryParameter("service", BuctSsoEndpoints.teachingService.toString())
            .build()
        executeNoRedirect(attempt, Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build(),
            onFailure = { fail(attempt, "获取登录参数失败") }) { resp ->
            resp.use {
                val html = it.body?.string().orEmpty()
                val execution = extractHtmlField(html, "login-page-flowkey")
                if (execution == null) {
                    fail(attempt, "登录会话已失效，请重新开始")
                    return@use
                }
                val body = FormBody.Builder()
                    .add("username", attempt.username)
                    .add("password", smsCode)
                    .add("type", "smsLogin")
                    .add("_eventId", "submit")
                    .add("geolocation", "")
                    .add("execution", execution)
                    .add("captcha_code", "")
                    .build()
                val postUrl = attempt.portal.newBuilder()
                    .encodedPath("/login")
                    .setQueryParameter("service", BuctSsoEndpoints.teachingService.toString())
                    .build()
                executeNoRedirect(
                    attempt,
                    Request.Builder().url(postUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", postUrl.toString())
                        .post(body)
                        .build(),
                    onFailure = { fail(attempt, "登录提交失败，请检查网络") }
                ) { loginResp ->
                    loginResp.use {
                        if (it.isRedirect) {
                            val loc = it.header("Location") ?: run {
                                fail(attempt, "统一认证未返回跳转地址")
                                return@use
                            }
                            val service = if (loc.startsWith("http")) loc else postUrl.resolve(loc).toString()
                            followTicket(attempt, service, 0)
                        } else {
                            fail(attempt, "登录失败（${it.code}），请重新输入验证码")
                        }
                    }
                }
            }
        }
    }

    /** 3.3 跟随 ticket → 教务系统，取会话 cookie */
    private fun followTicket(attempt: SmsAttempt, url: String, count: Int) {
        if (count > MAX_REDIRECTS) {
            fail(attempt, "登录跳转次数过多")
            return
        }
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        execute(attempt, request, onFailure = { fail(attempt, "已取得票据，但连接教务系统失败") }) { resp ->
            if (resp.code in REDIRECT_CODES) {
                val loc = resp.header("Location")
                val next = loc?.let(resp.request.url::resolve)
                resp.close()
                if (next == null) {
                    fail(attempt, "教务登录跳转无效")
                } else {
                    followTicket(attempt, next.toString(), count + 1)
                }
                return@execute
            }
            resp.use {
                val body = it.body?.string().orEmpty()
                if (it.request.url.host == BuctSsoEndpoints.teachingBase.host &&
                    it.request.url.encodedPath.contains("/xtgl/index_initMenu.html") &&
                    !body.contains("用户登录") && !body.contains("login_slogin")
                ) {
                    val cookieUrl = BuctSsoEndpoints.teachingBase.newBuilder()
                        .encodedPath("/jwglxt/").query(null).build()
                    val cookie = attempt.cookieJar.cookieHeaderFor(cookieUrl, setByHost = BuctSsoEndpoints.teachingBase.host)
                        .split("; ")
                        .filter { p -> p.isNotBlank() && !p.startsWith("rememberMe=") }
                        .joinToString("; ")
                    if (cookie.isBlank()) {
                        fail(attempt, "教务系统未返回有效会话")
                    } else {
                        succeed(attempt, cookie)
                    }
                } else {
                    fail(attempt, "教务系统登录会话建立失败")
                }
            }
        }
    }

    // ---------- 基础设施 ----------

    private fun buildClient(cookieJar: CookieJar): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .sslSocketFactory(BuctTlsTrustManager().sslSocketFactory(), BuctTlsTrustManager())
            .dns(BuctDns())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    private fun execute(
        attempt: SmsAttempt,
        request: Request,
        onFailure: (() -> Unit)? = null,
        onResponse: (Response) -> Unit
    ) {
        if (attempt.completed.get()) return
        val call = attempt.client.newCall(request)
        attempt.currentCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (attempt.completed.get()) return
                if (onFailure != null) onFailure() else fail(attempt, "网络连接失败，请稍后重试")
            }

            override fun onResponse(call: Call, response: Response) {
                if (attempt.completed.get()) {
                    response.close()
                    return
                }
                try {
                    onResponse(response)
                } catch (_: Exception) {
                    response.close()
                    fail(attempt, "处理响应失败")
                }
            }
        })
    }

    private fun executeNoRedirect(
        attempt: SmsAttempt,
        request: Request,
        onFailure: (() -> Unit)? = null,
        onResponse: (Response) -> Unit
    ) = execute(attempt, request, onFailure, onResponse)

    private fun succeed(attempt: SmsAttempt, cookie: String) {
        if (!attempt.completed.compareAndSet(false, true)) return
        detach(attempt)
        attempt.callback.onSuccess(cookie)
    }

    private fun fail(attempt: SmsAttempt, message: String) {
        if (!attempt.completed.compareAndSet(false, true)) return
        detach(attempt)
        attempt.callback.onError(message)
    }

    private fun detach(attempt: SmsAttempt) {
        synchronized(stateLock) {
            if (activeAttempt === attempt) activeAttempt = null
        }
    }

    private class SmsAttempt(
        val username: String,
        @Volatile var callback: SmsLoginCallback,
        val cookieJar: MatchingMemoryCookieJar,
        val client: OkHttpClient,
        val portal: HttpUrl = BuctSsoEndpoints.authPortal
    ) {
        val completed = AtomicBoolean(false)
        @Volatile var currentCall: Call? = null
        /** 固定设备指纹：发送/校验必须一致（重要！） */
        val deviceId: String = DEVICE_ID_FIXED
        var csrfKeyCache: String? = null
        var csrfValueCache: String? = null

        fun csrfKey(): String {
            csrfKeyCache?.let { return it }
            val key = (1..32).map { CHARS.random() }.joinToString("")
            val e = android.util.Base64.encodeToString(key.toByteArray(), android.util.Base64.NO_WRAP)
            val half = e.length / 2
            val valPart = md5Hex(e.substring(0, half) + e + e.substring(half))
            csrfKeyCache = key
            csrfValueCache = valPart
            return key
        }

        fun csrfValue(): String = csrfValueCache ?: csrfKey().let { csrfValueCache!! }

        private fun md5Hex(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    private class BuctDns : okhttp3.Dns {
        private val fallback = mapOf(
            "sidportal.buct.edu.cn" to listOf("222.199.255.33", "222.199.255.34"),
            "jwglxt.buct.edu.cn" to listOf("121.195.154.238")
        )
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            try {
                val resolved = okhttp3.Dns.SYSTEM.lookup(hostname)
                if (resolved.isNotEmpty()) return resolved
            } catch (_: java.net.UnknownHostException) {
            }
            val ips = fallback[hostname] ?: throw java.net.UnknownHostException(hostname)
            return ips.map { java.net.InetAddress.getByName(it) }
        }
    }

    private fun extractHtmlField(html: String, id: String): String? {
        val m = HTML_FIELD_PATTERN.matcher(html)
        while (m.find()) {
            if (m.group(1) == id) {
                val v = m.group(2)?.trim().orEmpty()
                return v.ifEmpty { null }
            }
        }
        return null
    }

    private fun extractJsonField(json: String, field: String): String? {
        val m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"?([^\",}]+)\"?").matcher(json)
        return if (m.find()) m.group(1) else null
    }

    private fun String.toRequestBody(): okhttp3.RequestBody =
        okhttp3.RequestBody.create(JSON_MEDIA_TYPE, this)

    companion object {
        private const val TAG = "BuctSms"
        private const val MAX_REDIRECTS = 12
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        /** 固定设备指纹（可用一次随机生成后持久化） */
        private const val DEVICE_ID_FIXED = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        private val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val HTML_FIELD_PATTERN =
            Pattern.compile("<p[^>]*id=\"([^\"]+)\"[^>]*>(.*?)</p>", Pattern.DOTALL)
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var instance: BuctSmsLoginManager? = null

        fun get(): BuctSmsLoginManager =
            instance ?: synchronized(this) {
                instance ?: BuctSmsLoginManager().also { instance = it }
            }
    }
}

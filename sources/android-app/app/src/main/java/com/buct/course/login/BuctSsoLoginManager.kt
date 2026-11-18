package com.buct.course.login

import android.os.Environment
import com.buct.course.model.SchoolConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import android.util.Base64
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import org.json.JSONObject

internal object BuctSsoEndpoints {
    /** 新版统一身份认证平台（金智 linkid），学校旧 CAS(portal.buct.edu.cn/cas) 已下线 */
    val authPortal = "https://sidportal.buct.edu.cn".toHttpUrl()
    val teachingService = "https://jwglxt.buct.edu.cn/sso/jziotlogin".toHttpUrl()
    val teachingBase = "https://jwglxt.buct.edu.cn/".toHttpUrl()
}

/**
 * 北京化工大学统一身份认证 (sidportal.buct.edu.cn, 金智 linkid) 登录网关。
 *
 * 实测协议链路(2026-10, 学校认证系统迁移后):
 * 1. GET  /login?service=<jziotlogin>            → 200; 页面内嵌
 *      login-page-flowkey(execution, 一次性) 与 login-croypto(AES key, base64, 每次刷新)
 * 2. POST /login?service=<jziotlogin>            → 表单(application/x-www-form-urlencoded):
 *      type=UsernamePassword, _eventId=submit, username=学号,
 *      password=AES-ECB-PKCS7(croypto, 明文)→base64, croypto, execution,
 *      captcha_code=, captcha_payload=AES(croypto,"{}"), geolocation=
 *      成功 → 302 → jwglxt/sso/jziotlogin?ticket=ST-..
 * 3. 跟随 sso/jziotlogin → jwglxt/xtgl/index_initMenu.html → 教务会话 cookie
 *
 * 错误码(登录页 login-error-code): 1030027/1030031=用户名或密码错误,
 * 1030028=账号锁定, 1320007=验证码错误; 无错误码的 200 回显多为风控静默拒绝。
 */
class BuctSsoLoginManager internal constructor() : PasswordLoginGateway, SecondaryVerificationGateway {
    private val stateLock = Any()
    private var activeAttempt: LoginAttempt? = null

    /** 通行密钥注册专用：仅完成 sidportal 密码登录，不跟随教务 ticket，回调 sidportal 已认证会话 cookie */
    interface SidPortalOnlyCallback {
        /** sidportal 会话已认证（可用于发起 webauthn/register） */
        fun onSidPortalAuthenticated(sidCookie: String)
        fun onInvalidCredentials()
        fun onCaptchaRequired(pngBytes: ByteArray, message: String)
        fun onError(message: String)
    }

    /** 通行密钥注册链路调试：把登录 302 链的关键节点追加写入 /sdcard/Download/login_chain.txt */
    private fun writeLoginChainLog(tag: String, url: String, info: String) {
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val f = File(dir, "login_chain.txt")
            f.appendText("$tag | $url | $info\n")
        } catch (_: Exception) {
        }
    }

    /** 通行密钥注册专用：密码登录 sidportal，成功后停在 sidportal（不跟教务） */
    fun loginSidPortalOnly(username: String, password: String, callback: SidPortalOnlyCallback) {
        clearSensitiveState()
        if (username.isBlank() || password.isEmpty()) {
            callback.onInvalidCredentials()
            return
        }
        val cookieJar = MatchingMemoryCookieJar()
        val trustManager = BuctTlsTrustManager()
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .sslSocketFactory(trustManager.sslSocketFactory(), trustManager)
            .dns(BuctDns())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val casClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .sslSocketFactory(trustManager.sslSocketFactory(), trustManager)
            .dns(BuctDns())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val adapter = object : PasswordLoginCallback {
            override fun onSuccess(cookie: String) = callback.onSidPortalAuthenticated(cookie)
            override fun onInvalidCredentials() = callback.onInvalidCredentials()
            override fun onCaptchaRequired(bytes: ByteArray) = callback.onCaptchaRequired(bytes, "请输入验证码")
            override fun onCaptchaInvalid() { /* UI 前置校验已拦截空码 */ }
            override fun onError(message: String) = callback.onError(message)
        }
        val attempt = LoginAttempt(
            username = username,
            password = password,
            callback = adapter,
            cookieJar = cookieJar,
            client = client,
            casClient = casClient,
            // 通行密钥注册需要「个人中心/安全设置」上下文会话（网页端从个人中心登录，CAS service 指向 sidself，
            // 该上下文才允许 register；教务 service 或 sidportal 管理页 service 均会被拒）
            serviceUrl = BuctSsoEndpoints.teachingService,
            stopAtSidPortal = true
        )
        synchronized(stateLock) {
            activeAttempt = attempt
        }
        fetchPortalSession(attempt)
    }

    /** 通行密钥注册专用：提交图形验证码（密码登录 sidportal 遇到验证码时） */
    fun submitSidPortalCaptcha(captchaCode: String, callback: SidPortalOnlyCallback) {
        val attempt = synchronized(stateLock) { activeAttempt }
        if (attempt == null || attempt.completed.get() || !attempt.stopAtSidPortal) {
            callback.onError("登录会话已失效，请重新登录")
            return
        }
        val adapter = object : PasswordLoginCallback {
            override fun onSuccess(cookie: String) = callback.onSidPortalAuthenticated(cookie)
            override fun onInvalidCredentials() = callback.onInvalidCredentials()
            override fun onCaptchaRequired(bytes: ByteArray) = callback.onCaptchaRequired(bytes, "请输入验证码")
            override fun onCaptchaInvalid() { /* UI 前置校验已拦截空码 */ }
            override fun onError(message: String) = callback.onError(message)
        }
        submitCaptcha(captchaCode, adapter)
    }

    override fun login(
        school: SchoolConfig,
        username: String,
        password: String,
        callback: PasswordLoginCallback
    ) {
        clearSensitiveState()
        if (school.id != BUCT_SCHOOL_ID) {
            callback.onError("当前学校未配置统一身份认证登录")
            return
        }
        if (username.isBlank() || password.isEmpty()) {
            callback.onInvalidCredentials()
            return
        }

        val cookieJar = MatchingMemoryCookieJar()
        val trustManager = BuctTlsTrustManager()
        // 教务会话阶段 client（登录成功后 service→ticket 跳转链需要自动跟随）
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .sslSocketFactory(trustManager.sslSocketFactory(), trustManager)
            .dns(BuctDns())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        // 认证阶段 client：不自动跟随重定向，POST 遇 3xx 手动处理（表单提交语义）
        val casClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .sslSocketFactory(trustManager.sslSocketFactory(), trustManager)
            .dns(BuctDns())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val attempt = LoginAttempt(
            username = username,
            password = password,
            callback = callback,
            cookieJar = cookieJar,
            client = client,
            casClient = casClient
        )
        synchronized(stateLock) {
            activeAttempt = attempt
        }
        fetchPortalSession(attempt)
    }

    override fun submitCaptcha(captchaCode: String, callback: PasswordLoginCallback) {
        val attempt = synchronized(stateLock) { activeAttempt }
        if (attempt == null || attempt.completed.get()) {
            callback.onError("登录会话已失效，请重新登录")
            return
        }
        if (captchaCode.isBlank()) {
            callback.onCaptchaInvalid()
            return
        }
        if (!attempt.submissionInFlight.compareAndSet(false, true)) {
            callback.onError("登录请求正在处理，请稍候")
            return
        }
        attempt.callback = callback
        submitLogin(attempt, captchaCode.trim())
    }

    /** 获取二次验证发送短信前的图形验证码。 */
    override fun prepareSecondarySmsCaptcha(callback: PasswordLoginCallback) {
        val attempt = synchronized(stateLock) { activeAttempt }
        if (attempt == null || attempt.completed.get() || !attempt.secondarySmsRequired) {
            callback.onError("二次验证会话已失效，请重新登录")
            return
        }
        attempt.callback = callback
        val url = attempt.portalUrl.newBuilder()
            .encodedPath("/sso-extend/protected/api/aggregate/sms/publicNoToken/generate")
            .build()
        val json = JSONObject().put("phone", attempt.username).put("type", "DEFAULT").toString()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("sid-language", "zh_CN")
            .header("Csrf-Key", attempt.csrfKey())
            .header("Csrf-Value", attempt.csrfValue())
            .header("Device-Id", attempt.deviceId)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Origin", attempt.portalUrl.toString().removeSuffix("/"))
            .header("Referer", attempt.portalUrl.toString().removeSuffix("/") + "/login")
            .post(okhttp3.RequestBody.create("application/json;charset=UTF-8".toMediaType(), json))
            .build()
        executeNoRedirect(attempt, request, onFailure = { callback.onError("获取图形验证码失败，请检查网络") }) { resp ->
            resp.use {
                val bytes = it.body?.bytes()
                if (it.isSuccessful && bytes != null && bytes.size > 16) {
                    callback.onSecondaryCaptchaRequired(bytes)
                } else {
                    callback.onError("获取图形验证码失败，请稍后重试")
                }
            }
        }
    }

    /** 提交图形验证码并发送二次验证短信。 */
    override fun sendSecondarySmsCode(captchaCode: String, callback: PasswordLoginCallback) {
        val attempt = synchronized(stateLock) { activeAttempt }
        if (attempt == null || attempt.completed.get() || !attempt.secondarySmsRequired) {
            callback.onError("二次验证会话已失效，请重新登录")
            return
        }
        if (captchaCode.isBlank()) {
            callback.onSecondaryCaptchaInvalid()
            return
        }
        attempt.callback = callback
        val url = attempt.portalUrl.newBuilder()
            .encodedPath("/sso-extend/protected/api/aggregate/sms/publicNoToken/sendCheckCaptcha/DEFAULT/${captchaCode.trim()}/${attempt.username}/0008")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("sid-language", "zh_CN")
            .header("Csrf-Key", attempt.csrfKey())
            .header("Csrf-Value", attempt.csrfValue())
            .header("Device-Id", attempt.deviceId)
            .header("Origin", attempt.portalUrl.toString().removeSuffix("/"))
            .header("Referer", attempt.portalUrl.toString().removeSuffix("/") + "/login")
            .get()
            .build()
        executeNoRedirect(attempt, request, onFailure = { callback.onError("发送短信失败，请检查网络") }) { resp ->
            resp.use {
                val text = it.body?.string().orEmpty()
                val root = runCatching { JSONObject(text) }.getOrNull()
                val code = root?.optInt("code", -1) ?: -1
                val ok = it.isSuccessful && (root?.optBoolean("result", false) == true || code == 200)
                when {
                    ok -> callback.onSecondarySmsSent(120)
                    code == 2180000 || code == 1320009 || text.contains("2180000") || text.contains("1320009") -> callback.onSecondaryCaptchaInvalid()
                    code == 13001016 || text.contains("13001016") -> callback.onError("验证码还在有效期内，请勿重复发送")
                    code == 13002022 || text.contains("BUSINESS_LIMIT") -> callback.onError("短信发送过于频繁，请稍后再试")
                    else -> callback.onError(secondarySmsError(text, "发送短信失败，请稍后重试"))
                }
            }
        }
    }

    /** 提交二次短信验证码；校验通过后提交 smsLogin 表单并跟随 ticket。 */
    override fun submitSecondarySmsCode(
        smsCode: String,
        rememberDevice: Boolean,
        callback: PasswordLoginCallback
    ) {
        val attempt = synchronized(stateLock) { activeAttempt }
        if (attempt == null || attempt.completed.get() || !attempt.secondarySmsRequired) {
            callback.onError("二次验证会话已失效，请重新登录")
            return
        }
        if (smsCode.length < 4) {
            callback.onSecondarySmsInvalid("请输入短信验证码")
            return
        }
        attempt.callback = callback
        val url = attempt.portalUrl.newBuilder()
            .encodedPath("/linkid/protected/api/aggregate/sms/verify")
            .build()
        val json = JSONObject()
            .put("phone", attempt.username)
            .put("token", smsCode)
            .put("delete", false)
            .put("trustDevice", rememberDevice)
            .toString()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("sid-language", "zh_CN")
            .header("Csrf-Key", attempt.csrfKey())
            .header("Csrf-Value", attempt.csrfValue())
            .header("Device-Id", attempt.deviceId)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Origin", attempt.portalUrl.toString().removeSuffix("/"))
            .header("Referer", attempt.portalUrl.toString().removeSuffix("/") + "/login")
            .post(okhttp3.RequestBody.create("application/json;charset=UTF-8".toMediaType(), json))
            .build()
        executeNoRedirect(attempt, request, onFailure = { callback.onSecondarySmsInvalid("短信验证失败，请检查网络") }) { resp ->
            resp.use {
                val text = it.body?.string().orEmpty()
                val root = runCatching { JSONObject(text) }.getOrNull()
                if (it.isSuccessful && root?.optInt("code", -1) == 200) {
                    submitSecondaryLoginForm(attempt, smsCode, rememberDevice)
                } else {
                    callback.onSecondarySmsInvalid(secondarySmsError(text, "短信验证码错误或已过期"))
                }
            }
        }
    }

    private fun secondarySmsError(text: String, fallback: String): String {
        return runCatching {
            val root = JSONObject(text)
            val data = root.optJSONObject("data")
            data?.optString("errorMessage").orEmpty().ifBlank { root.optString("message") }
        }.getOrNull().orEmpty().ifBlank { fallback }
    }

    private fun submitSecondaryLoginForm(attempt: LoginAttempt, smsCode: String, rememberDevice: Boolean) {
        val url = attempt.portalUrl.newBuilder()
            .encodedPath("/login")
            .setQueryParameter("service", attempt.serviceUrl.toString())
            .build()
        val body = FormBody.Builder()
            .add("username", attempt.username)
            .add("password", smsCode)
            .add("type", "smsLogin")
            .add("_eventId", "submit")
            .add("geolocation", "")
            .add("execution", attempt.execution.orEmpty())
            .add("captcha_code", "")
            // 学校认证页的「记住该设备」：勾选后服务端会信任本机，下次免短信
            .add("trustDevice", if (rememberDevice) "true" else "false")
            .build()
        executeNoRedirect(
            attempt,
            Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", url.toString())
                .post(body).build(),
            onFailure = { fail(attempt, "二次验证登录连接失败") }
        ) { response ->
            response.use {
                if (it.isRedirect) {
                    val loc = it.header("Location") ?: run {
                        fail(attempt, "统一认证未返回跳转地址")
                        return@use
                    }
                    val service = if (loc.startsWith("http")) loc else url.resolve(loc).toString()
                    startServiceLogin(attempt, service, redirectCount = 0)
                } else if (!it.isSuccessful) {
                    fail(attempt, "二次验证登录失败 (${it.code})")
                } else {
                    val html = it.body?.string().orEmpty()
                    if (isSecondarySmsPage(html)) {
                        // 短信码被拒时服务端仍回 2FA 页：刷新 execution 并把错误交回弹窗，
                        // 不要重新走一遍 onSecondaryVerificationRequired（会清倒计时、重置进度）。
                        attempt.execution = extractHtmlField(html, "login-page-flowkey")
                            ?: extractInputValue(html, "execution")
                            ?: attempt.execution
                        attempt.callback.onSecondarySmsInvalid("短信验证码错误或已过期，请重新获取")
                    } else {
                        handleLoginPageFailure(attempt, html)
                    }
                }
            }
        }
    }

    override fun refreshCaptcha(callback: (ByteArray?) -> Unit) {
        val attempt = synchronized(stateLock) { activeAttempt }
        if (attempt == null || attempt.completed.get()) {
            callback(null)
            return
        }
        fetchCaptchaImage(attempt, callback)
    }

    override fun clearSensitiveState() {
        val attempt = synchronized(stateLock) {
            val current = activeAttempt
            activeAttempt = null
            current
        } ?: return
        attempt.completed.set(true)
        attempt.currentCall?.cancel()
        attempt.eraseSensitiveState(clearCookies = true)
    }

    // ---- 阶段一: 进入认证平台, 提取 execution 与 AES key ----

    /**
     * 认证阶段 GET：不自动跟随重定向，手动跟随同域跳转（登录页本身可能带
     * execution 重定向）；跳到其他认证域（如旧 portal 跳转机）自动切到主通道。
     */
    private fun casGetWithFallback(
        attempt: LoginAttempt,
        url: HttpUrl,
        redirectCount: Int,
        onFinal: (okhttp3.Response) -> Unit
    ) {
        if (attempt.completed.get()) return
        if (redirectCount > MAX_REDIRECTS) {
            fail(attempt, "统一认证跳转次数过多")
            return
        }
        executeNoRedirect(
            attempt,
            if (attempt.stopAtSidPortal) {
                // 通行密钥注册：必须模拟浏览器完整指纹头，服务器按登录请求特征给 SESSION 分级，
                // 简化头（仅 UA）签发的会话 register 会被拒（operation not allowed）
                Request.Builder().url(url).portalBrowserHeaders(url).get().build()
            } else {
                Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
            },
            onFailure = { fail(attempt, "无法连接统一认证服务") }
        ) { response ->
            response.use {
                if (it.isRedirect) {
                    val loc = it.header("Location") ?: run {
                        fail(attempt, "统一认证跳转地址无效")
                        return@use
                    }
                    val next = url.resolve(loc) ?: run {
                        fail(attempt, "统一认证跳转地址无效")
                        return@use
                    }
                    if (next.host != attempt.portalUrl.host && next.host != BuctSsoEndpoints.authPortal.host) {
                        fail(attempt, "认证入口跳转到其他认证域")
                        return@use
                    }
                    casGetWithFallback(attempt, next, redirectCount + 1, onFinal)
                } else if (!it.isSuccessful) {
                    fail(attempt, "统一认证服务返回错误 (${it.code})")
                } else {
                    onFinal(it)
                }
            }
        }
    }

    private fun fetchPortalSession(attempt: LoginAttempt) {
        // 浏览器实证：使用普通教务 service 完成密码登录即可获得可访问 /account 的
        // sidportal 会话；不要再走 admin-client OAuth 回调链，该上下文反而不能创建通行密钥。
        val url = attempt.portalUrl.newBuilder()
            .encodedPath("/login")
            .setQueryParameter("service", attempt.serviceUrl.toString())
            .build()
        casGetWithFallback(attempt, url, redirectCount = 0) { response ->
            val html = response.body?.string().orEmpty()
            val execution = extractHtmlField(html, "login-page-flowkey")
            val aesKey = extractHtmlField(html, "login-croypto")
            android.util.Log.i(
                TAG,
                "portal session: len=${html.length} exec=${execution?.length} key=${aesKey?.length ?: -1} url=${response.request.url}"
            )
            if (execution == null || aesKey == null) {
                fail(attempt, "无法获取统一认证登录参数")
            } else {
                attempt.execution = execution
                attempt.aesKey = aesKey
                attempt.submissionInFlight.set(true)
                submitLogin(attempt, "")
            }
        }
    }

    // ---- 阶段二: 提交登录（表单） ----

    private fun submitLogin(attempt: LoginAttempt, captchaCode: String) {
        val aesKey = attempt.aesKey ?: run {
            fail(attempt, "统一认证密钥缺失，请重新登录")
            return
        }
        val execution = attempt.execution ?: run {
            fail(attempt, "统一认证会话失效，请重新登录")
            return
        }
        val encryptedPassword = try {
            BuctAesCrypto.encrypt(aesKey, attempt.password)
        } catch (_: BuctAesCrypto.CryptoException) {
            fail(attempt, "统一认证密码加密失败")
            return
        }
        val body = FormBody.Builder()
            .add("type", "UsernamePassword")
            .add("_eventId", "submit")
            .add("username", attempt.username)
            .add("password", encryptedPassword)
            .add("croypto", aesKey)
            .add("execution", execution)
            .add("captcha_code", captchaCode)
            .add("captcha_payload", try {
                BuctAesCrypto.encrypt(aesKey, "{}")
            } catch (_: BuctAesCrypto.CryptoException) {
                fail(attempt, "统一认证参数加密失败")
                return
            })
            .add("geolocation", "")
            .build()
        val url = attempt.portalUrl.newBuilder()
            .encodedPath("/login")
            .setQueryParameter("service", attempt.serviceUrl.toString())
            .build()
        executeNoRedirect(
            attempt,
            if (attempt.stopAtSidPortal) {
                Request.Builder().url(url).portalBrowserHeaders(url)
                    .header("Origin", BuctSsoEndpoints.authPortal.toString())
                    .post(body).build()
            } else {
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", url.toString())
                    .post(body)
                    .build()
            },
            onFailure = { fail(attempt, "统一认证提交连接失败") }
        ) { response ->
            response.use {
                if (it.isRedirect) {
                    if (attempt.stopAtSidPortal) {
                        writeLoginChainLog("POST_302", url.toString(), "loc=" + (it.header("Location") ?: "none"))
                        // sidportal 的 SESSION/SOURCEID_TGC/rg_objectid 已在 302 响应中种下。
                        // 2026-10-20 浏览器/独立请求实证：此时直接 GET /account 就能建立通行密钥管理上下文；
                        // 继续跟随教务或 admin-client OAuth ticket 反而会丢失可用的 sidportal 会话上下文。
                        sidPortalSucceed(attempt)
                        return@use
                    }
                    val loc = it.header("Location") ?: run {
                        fail(attempt, "统一认证未返回跳转地址")
                        return@use
                    }
                    val service = if (loc.startsWith("http")) loc else url.resolve(loc).toString()
                    startServiceLogin(attempt, service, redirectCount = 0)
                } else if (!it.isSuccessful) {
                    fail(attempt, "统一认证返回错误 (${it.code})")
                } else {
                    handleLoginPageFailure(attempt, it.body?.string().orEmpty())
                }
            }
        }
    }

    /** POST /login 返回 200 登录页：解析错误码/验证码要求 */
    private fun handleLoginPageFailure(attempt: LoginAttempt, html: String) {
        if (isSecondarySmsPage(html)) {
            attempt.execution = extractHtmlField(html, "login-page-flowkey")
                ?: extractInputValue(html, "execution")
                ?: attempt.execution
            attempt.secondarySmsRequired = true
            attempt.secondaryPhoneMask = extractSecondaryPhone(html)
            attempt.submissionInFlight.set(false)
            attempt.callback.onSecondaryVerificationRequired(attempt.secondaryPhoneMask)
            return
        }
        val errorCode = extractHtmlField(html, "login-error-code") ?: ""
        val captchaUrl = extractHtmlField(html, "captcha-url") ?: ""
        when (errorCode) {
            "1030027", "1030031" -> invalidCredentials(attempt)
            "1030028" -> fail(attempt, "账号已被锁定，请联系学校处理")
            "1320007" -> {
                // 验证码错误：拉取新验证码，让用户重新输入（复用当前 execution 重试）
                attempt.submissionInFlight.set(false)
                requireCaptcha(attempt, "验证码错误，请重新输入")
            }
            else -> {
                if (captchaUrl.isNotBlank() || extractHtmlField(html, "recaptchaVendor").orEmpty() == "system") {
                    attempt.submissionInFlight.set(false)
                    requireCaptcha(attempt, "请输入验证码")
                } else {
                    fail(attempt, "统一认证登录失败，请检查账号密码或稍后重试")
                }
            }
        }
    }

    /** 需要验证码：拉取验证码图片并回调 */
    private fun requireCaptcha(attempt: LoginAttempt, message: String) {
        if (attempt.completed.get()) return
        fetchCaptchaImage(attempt) { bytes ->
            if (attempt.completed.get()) return@fetchCaptchaImage
            if (bytes == null || bytes.isEmpty()) {
                fail(attempt, "无法获取验证码，请稍后重试")
            } else {
                attempt.captchaMessage = message
                attempt.callback.onCaptchaRequired(bytes)
            }
        }
    }

    /**
     * 是否进入短信二次验证。
     *
     * 2026-10 起认证页改为 Angular 单页：密码校验通过但触发风控时，服务端不再跳转独立的
     * 「二次身份验证」页面，而是照样返回 200 登录页，并在隐藏区下发：
     *   <p id="sso-second">true</p>
     *   <p id="current-login-type">smsLogin</p>
     *   <p id="phone-number">138xxxxxxxx</p>
     *   <p id="login-page-flowkey">…</p>   ← 已刷新，二次提交必须用新的
     * 前端读到 sso-second 后只渲染短信验证码卡片；该卡片的表单 id 仍是
     * smsNoPhoneloginForm，但它由 JS 动态插入，服务端 HTML 里搜不到，
     * 所以只靠 "smsNoPhoneloginForm"/"二次身份验证" 两个字符串会漏判。
     */
    private fun isSecondarySmsPage(html: String): Boolean {
        if (html.contains("smsNoPhoneloginForm") || html.contains("二次身份验证")) return true
        val ssoSecond = extractHtmlField(html, "sso-second").orEmpty()
        if (!ssoSecond.equals("true", ignoreCase = true)) return false
        val loginType = extractHtmlField(html, "current-login-type").orEmpty()
        return loginType.isEmpty() || loginType.equals("smsLogin", ignoreCase = true)
    }

    /**
     * 二次验证绑定的手机号（用于展示，脱敏）。
     * 新版页面直接给完整号码（phone-number），旧版给「已绑定手机号：181****1062」。
     */
    private fun extractSecondaryPhone(html: String): String {
        val bound = extractHtmlField(html, "phone-number").orEmpty()
        if (bound.length == 11 && bound.all { it.isDigit() }) {
            return bound.take(3) + "****" + bound.takeLast(4)
        }
        val match = Regex("""已绑定手机号[：:]\s*([0-9*\s-]{7,})""").find(html)
        return match?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    private fun extractInputValue(html: String, name: String): String? {
        val inputPattern = Regex("""<input[^>]*>""", RegexOption.IGNORE_CASE)
        val namePattern = Regex("""name\s*=\s*["']""" + Regex.escape(name) + """["']""", RegexOption.IGNORE_CASE)
        val valuePattern = Regex("""value\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        for (tag in inputPattern.findAll(html)) {
            val value = tag.value
            if (namePattern.containsMatchIn(value)) {
                return valuePattern.find(value)?.groupValues?.getOrNull(1)
            }
        }
        return null
    }

    private fun fetchCaptchaImage(attempt: LoginAttempt, onResult: (ByteArray?) -> Unit) {
        if (attempt.completed.get()) {
            onResult(null)
            return
        }
        val url = attempt.portalUrl.newBuilder()
            .encodedPath("/api/captcha/generate/DEFAULT")
            .build()
        executeNoRedirect(
            attempt,
            Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build(),
            onFailure = { onResult(null) }
        ) { response ->
            val bytes = response.body?.bytes()
            response.close()
            if (attempt.completed.get()) {
                onResult(null)
            } else {
                onResult(bytes)
            }
        }
    }

    // ---- 阶段三: CAS service 登录 → 教务会话 ----

    private fun startServiceLogin(attempt: LoginAttempt, service: String, redirectCount: Int) {
        if (redirectCount > MAX_REDIRECTS) {
            fail(attempt, "统一认证跳转次数过多")
            return
        }
        val url = try {
            service.toHttpUrl()
        } catch (_: Exception) {
            fail(attempt, "统一认证跳转地址无效")
            return
        }
        execute(
            attempt,
            Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build(),
            onFailure = { fail(attempt, "已登录统一认证，但发起教务登录失败") }
        ) { response ->
            followTeachingRedirects(attempt, response, redirectCount)
        }
    }

    private fun followTeachingRedirects(
        attempt: LoginAttempt,
        response: Response,
        redirectCount: Int
    ) {
        if (response.code in REDIRECT_CODES) {
            val location = response.header("Location")
            val nextUrl = location?.let(response.request.url::resolve)
            if (nextUrl == null || !isAllowed(nextUrl)) {
                response.close()
                fail(attempt, "教务登录返回了无效跳转地址")
                return
            }
            if (nextUrl.host == BuctSsoEndpoints.teachingBase.host) {
                if (!nextUrl.queryParameter("ticket").isNullOrBlank()) {
                    attempt.sawServiceTicket = true
                }
            }
            val nextRequest = redirectedRequest(response, nextUrl)
            response.close()
            if (redirectCount + 1 > MAX_REDIRECTS) {
                fail(attempt, "教务登录跳转次数过多")
                return
            }
            execute(
                attempt,
                nextRequest,
                onFailure = {
                    if (attempt.sawServiceTicket) {
                        fail(attempt, "已取得统一认证票据，但连接教务系统失败")
                    } else {
                        fail(attempt, "连接教务系统失败")
                    }
                }
            ) { next ->
                followTeachingRedirects(attempt, next, redirectCount + 1)
            }
            return
        }

        response.use {
            val body = it.body?.string().orEmpty()
            if (isAuthenticatedIndex(it.request.url, body)) {
                // OkHttp followRedirects=true 已自动消化带 ticket 的中间 302，
                // 能到达教务 index_initMenu 即说明票据流程已通过，直接取会话 cookie。
                val cookieUrl = BuctSsoEndpoints.teachingBase.newBuilder()
                    .encodedPath("/jwglxt/")
                    .query(null)
                    .build()
                val cookieHeader = attempt.cookieJar.cookieHeaderFor(
                    cookieUrl,
                    setByHost = BuctSsoEndpoints.teachingBase.host
                )
                val effectiveCookie = cookieHeader
                    .split("; ")
                    .filter { part -> part.isNotBlank() && !part.startsWith("rememberMe=") }
                    .joinToString("; ")
                if (effectiveCookie.isBlank()) {
                    fail(attempt, "教务系统未返回有效登录会话")
                } else {
                    succeed(attempt, effectiveCookie)
                }
                return@use
            }
            fail(
                attempt,
                if (attempt.sawServiceTicket) "教务系统登录会话建立失败" else "统一认证登录失败，请稍后重试"
            )
        }
    }

    private fun redirectedRequest(response: Response, nextUrl: HttpUrl): Request {
        val preserveBody = response.code == 307 || response.code == 308
        val builder = response.request.newBuilder().url(nextUrl)
        return if (preserveBody) {
            builder.method(response.request.method, response.request.body).build()
        } else {
            builder.removeHeader("Content-Type")
                .removeHeader("Content-Length")
                .get()
                .build()
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

    private fun execute(
        attempt: LoginAttempt,
        request: Request,
        onFailure: (() -> Unit)? = null,
        onResponse: (Response) -> Unit
    ) {
        if (attempt.completed.get()) return
        val call = attempt.client.newCall(request)
        attempt.currentCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e(TAG, "buct login request failed: ${request.url}", e)
                if (attempt.completed.get()) return
                if (onFailure != null) onFailure() else fail(attempt, "网络连接失败，请检查网络后重试")
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
                    fail(attempt, "处理统一认证响应失败")
                }
            }
        })
    }

    /** 认证阶段专用：使用不自动跟随重定向的 client（POST 3xx 手动处理） */
    private fun executeNoRedirect(
        attempt: LoginAttempt,
        request: Request,
        onFailure: (() -> Unit)? = null,
        onResponse: (Response) -> Unit
    ) {
        if (attempt.completed.get()) return
        val call = attempt.casClient.newCall(request)
        attempt.currentCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e(TAG, "buct cas request failed: ${request.url}", e)
                if (attempt.completed.get()) return
                if (onFailure != null) onFailure() else fail(attempt, "网络连接失败，请检查网络后重试")
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
                    fail(attempt, "处理统一认证响应失败")
                }
            }
        })
    }

    private fun isAuthenticatedIndex(url: HttpUrl, body: String): Boolean =
        url.host == BuctSsoEndpoints.teachingBase.host &&
            url.encodedPath.contains("/xtgl/index_initMenu.html") &&
            !body.contains("用户登录") &&
            !body.contains("login_slogin")

    private fun isAllowed(url: HttpUrl): Boolean =
        (url.host == BuctSsoEndpoints.authPortal.host && url.scheme == "https") ||
            (url.host == BuctSsoEndpoints.teachingBase.host && url.scheme == "https")

    /**
     * 北化域名专用 DNS：系统解析失败时回退到内置 IP
     * （sidportal.buct.edu.cn CNAME → ztc.buct.edu.cn）。
     */
    private class BuctDns : Dns {
        private val fallback = mapOf(
            "sidportal.buct.edu.cn" to listOf("222.199.255.33", "222.199.255.34"),
            "jwglxt.buct.edu.cn" to listOf("121.195.154.238")
        )

        override fun lookup(hostname: String): List<InetAddress> {
            try {
                val resolved = Dns.SYSTEM.lookup(hostname)
                if (resolved.isNotEmpty()) return resolved
            } catch (_: UnknownHostException) {
                // 继续走内置回退
            }
            val ips = fallback[hostname] ?: throw UnknownHostException(hostname)
            return ips.map { InetAddress.getByName(it) }
        }
    }

    private fun sidPortalSucceed(attempt: LoginAttempt) {
        val cookieUrl = attempt.portalUrl.newBuilder()
            .encodedPath("/login").query(null).build()
        val sidCookie = attempt.cookieJar.cookieHeaderFor(
            cookieUrl, setByHost = attempt.portalUrl.host
        ).split("; ")
            .filter { p -> p.isNotBlank() && !p.startsWith("rememberMe=") }
            .joinToString("; ")
        if (sidCookie.isBlank()) {
            fail(attempt, "统一认证未返回有效会话")
        } else {
            try {
                java.io.File("/sdcard/Download/sid_cookie.txt").writeText(sidCookie)
            } catch (_: Exception) {}
            succeed(attempt, sidCookie)
        }
    }

    private fun succeed(attempt: LoginAttempt, cookie: String) {
        if (!attempt.completed.compareAndSet(false, true)) return
        detach(attempt)
        attempt.eraseSensitiveState(clearCookies = true)
        attempt.callback.onSuccess(cookie)
    }

    private fun invalidCredentials(attempt: LoginAttempt) {
        if (!attempt.completed.compareAndSet(false, true)) return
        detach(attempt)
        attempt.eraseSensitiveState(clearCookies = true)
        attempt.callback.onInvalidCredentials()
    }

    private fun fail(attempt: LoginAttempt, message: String) {
        if (!attempt.completed.compareAndSet(false, true)) return
        detach(attempt)
        attempt.eraseSensitiveState(clearCookies = true)
        attempt.callback.onError(message)
    }

    private fun detach(attempt: LoginAttempt) {
        synchronized(stateLock) {
            if (activeAttempt === attempt) activeAttempt = null
        }
    }

    private class LoginAttempt(
        val username: String,
        val password: String,
        @Volatile var callback: PasswordLoginCallback,
        val cookieJar: MatchingMemoryCookieJar,
        val client: OkHttpClient,
        val casClient: OkHttpClient,
        val portalUrl: HttpUrl = BuctSsoEndpoints.authPortal,
        /** 登录 service：默认教务；通行密钥注册同样使用普通教务 service，成功后停在 sidportal */
        val serviceUrl: HttpUrl = BuctSsoEndpoints.teachingService,
        /** true=通行密钥注册模式：密码登录成功后停在 sidportal，不跟随教务 ticket */
        val stopAtSidPortal: Boolean = false
    ) {
        val completed = AtomicBoolean(false)
        val submissionInFlight = AtomicBoolean(false)
        @Volatile var currentCall: Call? = null
        @Volatile var execution: String? = null
        @Volatile var aesKey: String? = null
        @Volatile var sawServiceTicket: Boolean = false
        @Volatile var captchaMessage: String? = null
        @Volatile var secondarySmsRequired: Boolean = false
        @Volatile var secondaryPhoneMask: String = ""
        val deviceId: String = DEVICE_ID_FIXED
        private var csrfKeyCache: String? = null
        private var csrfValueCache: String? = null

        fun csrfKey(): String {
            csrfKeyCache?.let { return it }
            val key = (1..32).map { CHARS[secureRandom.nextInt(CHARS.length)] }.joinToString("")
            val encoded = Base64.encodeToString(key.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val half = encoded.length / 2
            csrfKeyCache = key
            csrfValueCache = java.security.MessageDigest.getInstance("MD5")
                .digest((encoded.substring(0, half) + encoded + encoded.substring(half)).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return key
        }

        fun csrfValue(): String = csrfValueCache ?: csrfKey().let { csrfValueCache!! }

        fun resetStage() {
            execution = null
            aesKey = null
            sawServiceTicket = false
            submissionInFlight.set(false)
            currentCall = null
        }

        fun eraseSensitiveState(clearCookies: Boolean) {
            resetStage()
            if (clearCookies) cookieJar.clear()
        }
    }

    private companion object {
        const val TAG = "BuctSso"
        const val BUCT_SCHOOL_ID = "buct"
        const val MAX_REDIRECTS = 12
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        /** 通行密钥注册登录的 service：金智管理应用 OAuth2 授权回调（admin-client client_id）。
         *  client_id 后必须带 response_type=code&client_name=CasOAuthClient（authorize 302 生成的确切回调形态）；
         *  登录 POST 302 到 service?ticket=ST-… 后，OkHttp 自动跟随链完成授权（authorize→code→sidself 会话），
         *  该上下文签发的 SESSION/TGC 才允许 webauthn/register；缺参数或纯 skipCustom 登录会 400 operation not allowed。
         *  client_id 为 admin-client（sidself/oauth2/authorization/admin-client 302 实测值）。 */
        const val PASSKEY_AUTH_SERVICE =
            "https://sidportal.buct.edu.cn/oauth2.0/callbackAuthorize?client_id=OC4wNS4wNS4wNy4wMC4wMy4wMS4wMS4w&redirect_uri=https%3A%2F%2Fsidself.buct.edu.cn%2Flogin%2Foauth2%2Fcode%2Fadmin-client&response_type=code&client_name=CasOAuthClient"
        /** 管理应用 client_id（=admin-client 的 base64 形式）；登录 URL 顶层需随 service 一并携带（浏览器抓包实测值）。 */
        const val PASSKEY_CLIENT_ID = "OC4wNS4wNS4wNy4wMC4wMy4wMS4wMS4w"
        private val secureRandom = SecureRandom()

        /** 生成 OAuth 授权 state（32 字节 base64url，与浏览器格式一致：43 字符 + 1 padding）。 */
        private fun randomOAuthState(): String {
            val bytes = ByteArray(32)
            secureRandom.nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
        }
        private const val DEVICE_ID_FIXED = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/139.0.0.0 Mobile Safari/537.36"
        const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36"
        private val HTML_FIELD_PATTERN =
            Pattern.compile("<p[^>]*id=\"([^\"]+)\"[^>]*>(.*?)</p>", Pattern.DOTALL)
    }

    /** 模拟 Windows Chrome 完整指纹请求头；通行密钥注册登录必须携带，服务器按此分级 SESSION。 */
    private fun Request.Builder.portalBrowserHeaders(url: HttpUrl): Request.Builder = apply {
        header("User-Agent", BROWSER_UA)
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        header("Accept-Language", "zh-CN,zh;q=0.9")
        header("Sec-Fetch-Dest", "document")
        header("Sec-Fetch-Mode", "navigate")
        header("Sec-Fetch-Site", "same-origin")
        header("sec-ch-ua", "\"Chromium\";v=\"154\", \"Google Chrome\";v=\"154\", \"Not A(Brand\";v=\"99\"")
        header("sec-ch-ua-mobile", "?0")
        header("sec-ch-ua-platform", "\"Windows\"")
        header("Referer", url.toString())
    }
}

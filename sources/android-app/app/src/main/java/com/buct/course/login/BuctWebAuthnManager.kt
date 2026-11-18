package com.buct.course.login

import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 北化统一身份认证「通行密钥（WebAuthn/FIDO2）」原生实现（金智 linkid, sidportal.buct.edu.cn）。
 *
 * 2026-10-17 逆向实测全链路（Node 复刻加密请求成功）：
 * 1. GET /login?service=<jziotlogin> 建立会话（Cookie 由 cookieJar 收集）
 * 2. POST /webauthn/authenticate
 *      body = AES-ECB(CryptoJS 兼容) 加密的 {"username":"学号"}（base64）
 *      headers: X-CSRF-TOKEN=csrfToken, Content-Type: application/json,
 *               hasCrypto:"true", privateKey=RSA 加密的 AES 密钥（base64, RSA_PKCS1）
 *      → 响应 AES-ECB 解密 → JSON:
 *        {"request":{"requestId","publicKeyCredentialRequestOptions":{challenge,rpId,...}},
 *         "actions":{"finish":"webauthn/authenticate/finish"},"success":true}
 * 3. App 用 Android Credential Manager（系统指纹/面部）完成 WebAuthn 断言
 * 4. POST {HOST}/{finish} body {"requestId":...,"credential":<断言JSON>,"sessionToken":null}
 *      → success=true（假断言返回 400，真实签名成功）
 * 5. GET /login?service=<jziotlogin> → 302 → jwglxt/sso/jziotlogin?ticket=ST-.. → 教务会话 cookie
 */
class BuctWebAuthnManager private constructor() {

    interface WebAuthnCallback {
        /** 已取得认证选项（requestId + publicKeyCredentialRequestOptions JSON + finish 相对路径） */
        fun onOptions(requestId: String, requestJson: String, finishUrl: String, sessionToken: String?)
        /** 登录成功，返回教务会话 cookie */
        fun onSuccess(cookie: String)
        /** 错误 */
        fun onError(message: String)
    }

    interface RegistrationCallback {
        /** 已取得注册选项（requestId + publicKeyCredentialCreationOptions JSON + finish 相对路径 + sessionToken） */
        fun onCreationOptions(requestId: String, creationJson: String, finishUrl: String, sessionToken: String?)
        /** 注册（创建）完成 */
        fun onRegistered()
        /** 错误 */
        fun onError(message: String)
    }

    private val stateLock = Any()
    private var activeAttempt: WaaAttempt? = null

    /** 开始通行密钥认证：建会话 + 请求 challenge/options */
    fun startAuthentication(username: String, callback: WebAuthnCallback) {
        clearState()
        if (username.isBlank()) {
            callback.onError("请输入学号")
            return
        }
        val cookieJar = MatchingMemoryCookieJar()
        val attempt = WaaAttempt(username, callback, cookieJar, buildClient(cookieJar))
        synchronized(stateLock) { activeAttempt = attempt }
        authenticate(attempt)
    }

    /** 系统通行密钥断言完成后提交 finish，成功则跟随 ticket 取教务 cookie */
    fun submitAuthentication(requestId: String, finishUrl: String, credentialJson: String, sessionToken: String?, callback: WebAuthnCallback) {
        val attempt = synchronized(stateLock) { activeAttempt }
        if (attempt == null || attempt.completed.get()) {
            callback.onError("通行密钥会话已失效，请重新开始")
            return
        }
        attempt.callback = callback
        submitFinish(attempt, requestId, finishUrl, credentialJson, sessionToken)
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

    // ---------- 注册（创建通行密钥）----------

    /**
     * 网页端创建通行密钥是在「通行密钥管理页」（sidportal/?skipCustom=true）上发起 register；
     * App 若直接发 register 会因会话缺少「已访问管理页」上下文而 400 operation not allowed。
     * 先用当前 sidportal 会话 GET 管理页建立上下文，合并 Set-Cookie 后回调返回。
     */
    fun probeSafeCenterContext(sidCookie: String, onResult: (mergedCookie: String) -> Unit) {
        val client = buildStandaloneClient()
        val request = Request.Builder()
            .url("https://sidportal.buct.edu.cn/account")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Referer", "https://sidportal.buct.edu.cn/login")
            .header("Cookie", sidCookie)
            .get()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(sidCookie)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { onResult(sidCookie) }
            }
        })
    }

    fun startRegistration(
        sidCookie: String,
        username: String,
        displayName: String,
        credentialNickname: String,
        callback: RegistrationCallback
    ) {
        val client = buildStandaloneClient()
        val url = BuctSsoEndpoints.authPortal.newBuilder().encodedPath("/webauthn/register").build()
        val body = okhttp3.FormBody.Builder()
            .add("username", username)
            .add("displayName", displayName)
            .add("credentialNickname", credentialNickname)
            .add("requireResidentKey", "false")
            .add("sessionToken", "null")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("X-CSRF-TOKEN", "csrfToken")
            .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .header("Accept", "*/*")
            .header("Origin", "https://sidportal.buct.edu.cn")
            .header("Referer", "https://sidportal.buct.edu.cn/account")
            .header("Cookie", sidCookie)
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("请求创建通行密钥失败，请检查网络")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        // register 错误体为明文 JSON：尽量提取具体原因
                        val reason = try {
                            val root = JSONObject(text)
                            val msgs = root.optJSONArray("messages")
                            if (msgs != null && msgs.length() > 0) msgs.getString(0) else null
                        } catch (_: Exception) {
                            null
                        }
                        callback.onError(
                            if (reason != null) "创建通行密钥失败：$reason" else "创建通行密钥请求失败（${it.code}）"
                        )
                        return
                    }
                    try {
                        val root = JSONObject(text)
                        val request = root.getJSONObject("request")
                        val requestId = request.getString("requestId")
                        val options = request.getJSONObject("publicKeyCredentialCreationOptions")
                        val finish = root.getJSONObject("actions").getString("finish")
                        val sessionToken = request.optString("sessionToken").ifBlank { null }
                        callback.onCreationOptions(requestId, options.toString(), finish, sessionToken)
                    } catch (e: Exception) {
                        callback.onError("创建通行密钥响应解析失败：${e.message}")
                    }
                }
            }
        })
    }

    /** 系统创建凭据完成后提交 register finish */
    fun submitRegistration(
        sidCookie: String,
        requestId: String,
        finishUrl: String,
        credentialJson: String,
        sessionToken: String?,
        callback: RegistrationCallback
    ) {
        val client = buildStandaloneClient()
        val resolved = BuctSsoEndpoints.authPortal.resolve(finishUrl)
        if (resolved == null) {
            callback.onError("创建通行密钥提交地址无效")
            return
        }
        val body = JSONObject()
            .put("requestId", requestId)
            .put("credential", JSONObject(credentialJson))
            .put("sessionToken", sessionToken ?: JSONObject.NULL)
            .toString()
        val request = Request.Builder()
            .url(resolved)
            .header("User-Agent", USER_AGENT)
            .header("X-CSRF-TOKEN", "csrfToken")
            .header("Accept", "*/*")
            .header("Origin", "https://sidportal.buct.edu.cn")
            .header("Referer", "https://sidportal.buct.edu.cn/account")
            .header("Cookie", sidCookie)
            .post(body.toRequestBody(TEXT_MEDIA_TYPE))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("提交创建失败，请检查网络")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    val success = it.isSuccessful && runCatching { JSONObject(text).optBoolean("success") }.getOrDefault(false)
                    if (!success) {
                        callback.onError("创建通行密钥验证失败，请重试")
                        return
                    }
                    callback.onRegistered()
                }
            }
        })
    }

    // ---------- 阶段一：建会话 + 请求认证选项 ----------

    private fun authenticate(attempt: WaaAttempt) {
        val loginUrl = attempt.portal.newBuilder()
            .encodedPath("/login")
            .setQueryParameter("service", BuctSsoEndpoints.teachingService.toString())
            .build()
        executeNoRedirect(attempt, Request.Builder().url(loginUrl).header("User-Agent", USER_AGENT).get().build(),
            onFailure = { fail(attempt, "无法连接统一认证，请检查网络") }) { resp ->
            resp.use {
                val html = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    fail(attempt, "统一认证不可用（${it.code}）")
                    return@use
                }
                attempt.loginExecution = parseLoginFlowkey(html)
                // 会话 cookie 已由 cookieJar 收集，直接请求 authenticate
                requestAuthenticate(attempt)
            }
        }
    }

    private fun requestAuthenticate(attempt: WaaAttempt) {
        val url = attempt.portal.newBuilder().encodedPath("/webauthn/authenticate").build()
        val aesKey = randomAesKey()
        val aesKeyB64 = android.util.Base64.encodeToString(aesKey, android.util.Base64.NO_WRAP)
        val bodyPlain = "{\"username\":\"${attempt.username}\"}"
        val encryptedBody = aesEncrypt(aesKey, bodyPlain)
        val encryptedAesKey = rsaEncrypt(aesKeyB64.toByteArray(Charsets.UTF_8))
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("X-CSRF-TOKEN", "csrfToken")
            .header("Content-Type", "application/json")
            .header("hasCrypto", "true")
            .header("privateKey", encryptedAesKey)
            .header("Accept", "*/*")
            .header("Origin", "https://sidportal.buct.edu.cn")
            .header("Referer", "https://sidportal.buct.edu.cn/login")
            .post(encryptedBody.toRequestBody())
            .build()
        executeNoRedirect(attempt, request, onFailure = { fail(attempt, "请求通行密钥失败，请检查网络") }) { resp ->
            resp.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    // 400 响应体也是 AES 加密的，解密出具体原因（如 username.not.registered）
                    val reason = try {
                        val decrypted = aesDecrypt(aesKey, text)
                        val root = JSONObject(decrypted)
                        val msgs = root.optJSONArray("messages")
                        if (msgs != null && msgs.length() > 0) msgs.getString(0) else null
                    } catch (_: Exception) {
                        null
                    }
                    val friendly = when (reason) {
                        "username.not.registered" ->
                            "该学号未注册通行密钥：请检查学号是否输错；若确认无误，需先登录一次创建通行密钥"
                        "operation.not.allowed", "operation not allowed" ->
                            "当前操作不被允许，请稍后重试"
                        else -> reason
                    }
                    fail(attempt, if (friendly != null) "通行密钥请求失败：$friendly" else "通行密钥请求失败（${it.code}）")
                    return@use
                }
                val decrypted = try {
                    aesDecrypt(aesKey, text)
                } catch (_: Exception) {
                    text // 后端可能回退明文 JSON
                }
                parseOptions(attempt, decrypted)
            }
        }
    }

    private fun parseOptions(attempt: WaaAttempt, json: String) {
        try {
            val root = JSONObject(json)
            val request = root.getJSONObject("request")
            val requestId = request.getString("requestId")
            val options = request.getJSONObject("publicKeyCredentialRequestOptions")
            val finish = root.getJSONObject("actions").getString("finish")
            val sessionToken = request.optString("sessionToken").ifBlank { null }
            attempt.callback.onOptions(requestId, options.toString(), finish, sessionToken)
        } catch (e: Exception) {
            fail(attempt, "通行密钥响应解析失败：${e.message}")
        }
    }

    // ---------- 阶段二：提交断言 + ticket 跟随 ----------

    private fun submitFinish(attempt: WaaAttempt, requestId: String, finishUrl: String, credentialJson: String, sessionToken: String?) {
        val resolvedUrl = attempt.portal.resolve(finishUrl)
        if (resolvedUrl == null) {
            fail(attempt, "通行密钥提交地址无效")
            return
        }
        val url = resolvedUrl
        val body = JSONObject()
            .put("requestId", requestId)
            .put("credential", JSONObject(credentialJson))
            .put("sessionToken", sessionToken ?: JSONObject.NULL)
            .toString()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("X-CSRF-TOKEN", "csrfToken")
            .header("Accept", "*/*")
            .header("Origin", "https://sidportal.buct.edu.cn")
            .header("Referer", "https://sidportal.buct.edu.cn/login")
            .post(body.toRequestBody(TEXT_MEDIA_TYPE))
            .build()
        executeNoRedirect(attempt, request, onFailure = { fail(attempt, "通行密钥提交失败，请检查网络") }) { resp ->
            resp.use {
                val text = it.body?.string().orEmpty()
                val finishSuccess = it.isSuccessful && runCatching { JSONObject(text).optBoolean("success") }.getOrDefault(false)
                if (!finishSuccess) {
                    fail(attempt, "通行密钥验证失败，请重试")
                    return@use
                }
                val authSessionToken = runCatching { JSONObject(text).optString("sessionToken") }.getOrNull().orEmpty()
                if (authSessionToken.isBlank()) {
                    fail(attempt, "通行密钥认证未返回会话令牌")
                    return@use
                }
                val loginUrl = attempt.portal.newBuilder().encodedPath("/login").build()
                val form = okhttp3.FormBody.Builder()
                    .add("username", attempt.username)
                    .add("_eventId", "validateWebAuthn")
                    .add("geolocation", "")
                    .add("execution", attempt.loginExecution.orEmpty())
                    .add("token", authSessionToken)
                    .build()
                val loginRequest = Request.Builder()
                    .url(loginUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Origin", "https://sidportal.buct.edu.cn")
                    .header("Referer", "https://sidportal.buct.edu.cn/login")
                    .post(form)
                    .build()
                executeNoRedirect(attempt, loginRequest,
                    onFailure = { fail(attempt, "登录跳转失败，请重试") }) { loginResp ->
                    loginResp.use {
                        if (it.isRedirect) {
                            val loc = it.header("Location") ?: run {
                                fail(attempt, "统一认证未返回跳转地址")
                                return@use
                            }
                            val service = if (loc.startsWith("http")) loc else loginUrl.resolve(loc).toString()
                            followTicket(attempt, service, 0)
                        } else {
                            fail(attempt, "通行密钥认证未完成（${it.code}）")
                        }
                    }
                }
            }
        }
    }

    /** 跟随 ticket → 教务系统，取会话 cookie（与短信/密码一致） */
    private fun followTicket(attempt: WaaAttempt, url: String, count: Int) {
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

    // ---------- 加密（CryptoJS / JSEncrypt 兼容） ----------

    /** AES-128-ECB PKCS7 加密（CryptoJS AES.encrypt base64 输出兼容） */
    private fun aesEncrypt(key: ByteArray, plain: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return android.util.Base64.encodeToString(
            cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), android.util.Base64.NO_WRAP)
    }

    /** AES-128-ECB 解密 */
    private fun aesDecrypt(key: ByteArray, b64: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return String(cipher.doFinal(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)), Charsets.UTF_8)
    }

    /** RSA 公钥加密（JSEncrypt RSA_PKCS1 + base64 兼容） */
    private fun rsaEncrypt(plain: ByteArray): String {
        val pemBody = RSA_PUBLIC_KEY_PEM
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s"), "")
        val keySpec = X509EncodedKeySpec(android.util.Base64.decode(pemBody, android.util.Base64.NO_WRAP))
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pubKey)
        return android.util.Base64.encodeToString(cipher.doFinal(plain), android.util.Base64.NO_WRAP)
    }

    private fun randomAesKey(): ByteArray {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    // ---------- 基础设施（复用短信管理器模式） ----------

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

    /** 注册用独立 client：不共享会话 cookie（sidCookie 直接以请求头传入） */
    private fun buildStandaloneClient(): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .sslSocketFactory(BuctTlsTrustManager().sslSocketFactory(), BuctTlsTrustManager())
            .dns(BuctDns())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    private fun execute(
        attempt: WaaAttempt,
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
        attempt: WaaAttempt,
        request: Request,
        onFailure: (() -> Unit)? = null,
        onResponse: (Response) -> Unit
    ) = execute(attempt, request, onFailure, onResponse)

    private fun succeed(attempt: WaaAttempt, cookie: String) {
        if (!attempt.completed.compareAndSet(false, true)) return
        detach(attempt)
        attempt.callback.onSuccess(cookie)
    }

    private fun fail(attempt: WaaAttempt, message: String) {
        if (!attempt.completed.compareAndSet(false, true)) return
        detach(attempt)
        attempt.callback.onError(message)
    }

    private fun detach(attempt: WaaAttempt) {
        synchronized(stateLock) {
            if (activeAttempt === attempt) activeAttempt = null
        }
    }

    private class WaaAttempt(
        val username: String,
        @Volatile var callback: WebAuthnCallback,
        val cookieJar: MatchingMemoryCookieJar,
        val client: OkHttpClient,
        val portal: HttpUrl = BuctSsoEndpoints.authPortal
    ) {
        val completed = AtomicBoolean(false)
        @Volatile var currentCall: Call? = null
        @Volatile var loginExecution: String? = null
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

    private fun parseLoginFlowkey(html: String): String? {
        val m = Regex("<p[^>]*id=\"login-page-flowkey\"[^>]*>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL)).find(html)
        return m?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun String.toRequestBody(): okhttp3.RequestBody =
        okhttp3.RequestBody.create(JSON_MEDIA_TYPE, this)

    private fun String.toRequestBody(mediaType: MediaType): okhttp3.RequestBody =
        okhttp3.RequestBody.create(mediaType, this)

    companion object {
        private const val TAG = "BuctWebAuthn"
        private const val MAX_REDIRECTS = 12
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val TEXT_MEDIA_TYPE = "text/plain;charset=UTF-8".toMediaType()

        /** 金智 webauthn.js 内置 RSA 公钥（逆向提取） */
        private const val RSA_PUBLIC_KEY_PEM = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjVr1zKwohU3xA0afprWLSQvIymaSH/V27MedFc+CecXSnORIFMAp4uEIb4taDq/2X4eMeTI66Mu/rB5GKSFDbExF2Gu4NaO/CNDpf1gHMScUrIFCh4CDqzBnx17kclvezLkIK0T8FVa4cRsINvzjbnA6jUSMaf6Fm1n9wTAtW6QYBjssGOEtCj+c38PTBdFMmJbXp3brt1tEBesz6lb3Fjp76FGvDZ08xtYG8fxYPuiMwKU04eS+mcX/BunwgpU3zwekHYB+PWRIvq0lBry9Wms25sJE5T/RAv5fEuMLbBkfcZK3+7ivSZthTmPpr2Ap/ji70ZZ6u2jvR5VJq+LJHQIDAQAB
-----END PUBLIC KEY-----"""

        @Volatile
        private var instance: BuctWebAuthnManager? = null

        fun get(): BuctWebAuthnManager =
            instance ?: synchronized(this) {
                instance ?: BuctWebAuthnManager().also { instance = it }
            }
    }
}

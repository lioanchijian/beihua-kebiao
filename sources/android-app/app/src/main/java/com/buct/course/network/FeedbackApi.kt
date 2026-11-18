package com.buct.course.network

import android.content.Context
import com.buct.course.analytics.InstallId
import com.buct.course.manager.CredentialStore
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class FeedbackIdentity(
    val displayId: String,
    val nickname: String,
    val unread: Int
)

data class FeedbackMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val createdAt: String
) {
    val fromAdmin: Boolean get() = sender == "admin"
}

/**
 * 匿名意见反馈通道。
 *
 * 身份由服务端按安装 ID 分配（短号 BH-XXXXXX + 随机昵称 + 一次性 token），
 * 通信内容只有短号、昵称和消息正文，不含姓名、学号、账号、密码或 Cookie。
 *
 * token 存在 [CredentialStore]（Keystore 加密），不落明文 prefs。
 */
object FeedbackApi {
    private const val PRIMARY_BASE = "https://buctkb.cn"
    private const val FALLBACK_BASE = "http://47.243.237.7"
    private const val TOKEN_KEY = "feedback_token"
    private const val PREFS = "feedback_identity"
    private const val KEY_DISPLAY_ID = "display_id"
    private const val KEY_NICKNAME = "nickname"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun token(context: Context): String? =
        CredentialStore.load(context, TOKEN_KEY)?.takeIf { it.isNotBlank() }

    fun cachedIdentity(context: Context): FeedbackIdentity? {
        val p = prefs(context)
        val displayId = p.getString(KEY_DISPLAY_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val nickname = p.getString(KEY_NICKNAME, null).orEmpty()
        return FeedbackIdentity(displayId, nickname, 0)
    }

    private fun storeIdentity(context: Context, displayId: String, nickname: String, token: String?) {
        val edit = prefs(context).edit()
            .putString(KEY_DISPLAY_ID, displayId)
            .putString(KEY_NICKNAME, nickname)
        edit.apply()
        if (!token.isNullOrBlank()) runCatching { CredentialStore.save(context, TOKEN_KEY, token) }
    }

    private fun versionOf(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** 注册（幂等）。失败返回 null，界面给「重试」。 */
    suspend fun register(context: Context): FeedbackIdentity? {
        val payload = JSONObject()
            .put("installId", InstallId.get(context))
            .put("version", versionOf(context))
            .put("platform", "android")
            .toString()
        val body = postJson(context, "/api/feedback/register", payload) ?: return null
        if (!body.optBoolean("ok")) return null
        val displayId = body.optString("displayId")
        val nickname = body.optString("nickname")
        if (displayId.isBlank()) return null
        storeIdentity(context, displayId, nickname, body.optString("token"))
        return FeedbackIdentity(displayId, nickname, body.optInt("unread", 0))
    }

    /** 确保有 token：先试本地，缺失则静默注册一次。 */
    suspend fun ensureIdentity(context: Context): FeedbackIdentity? {
        if (token(context) != null) {
            cachedIdentity(context)?.let { return it }
        }
        return register(context)
    }

    suspend fun thread(context: Context, sinceId: Long = 0, markRead: Boolean = false): Pair<List<FeedbackMessage>, Int>? {
        val token = token(context) ?: return null
        val path = "/api/feedback/thread?token=${urlEncode(token)}&since_id=$sinceId" +
            if (markRead) "&mark_read=1" else ""
        val body = get(context, path) ?: return null
        if (!body.optBoolean("ok")) return null
        val displayId = body.optString("displayId")
        val nickname = body.optString("nickname")
        if (displayId.isNotBlank()) storeIdentity(context, displayId, nickname, null)
        val array = body.optJSONArray("messages")
        val messages = ArrayList<FeedbackMessage>()
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                messages += FeedbackMessage(
                    id = item.optLong("id"),
                    sender = item.optString("sender"),
                    body = item.optString("body"),
                    createdAt = item.optString("created_at")
                )
            }
        }
        return messages to body.optInt("unread", 0)
    }

    suspend fun send(context: Context, text: String): String? {
        val token = token(context) ?: return "请先重新进入反馈页"
        val payload = JSONObject().put("token", token).put("body", text).toString()
        val body = postJson(context, "/api/feedback/send", payload) ?: return "网络异常，草稿已保留"
        if (body.optBoolean("ok")) return null
        return body.optString("error").ifBlank { "发送失败" }
    }

    suspend fun setNickname(context: Context, nickname: String): String? {
        val token = token(context) ?: return "请先重新进入反馈页"
        val payload = JSONObject().put("token", token).put("nickname", nickname).toString()
        val body = postJson(context, "/api/feedback/nickname", payload) ?: return "网络异常，请稍后重试"
        if (body.optBoolean("ok")) {
            cachedIdentity(context)?.let { storeIdentity(context, it.displayId, nickname, null) }
            return null
        }
        return body.optString("error").ifBlank { "修改失败" }
    }

    /** 未读数（进设置页时轻量拉一次）。 */
    suspend fun unread(context: Context): Int? {
        if (token(context) == null) return null
        return thread(context, sinceId = 0, markRead = false)?.second
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private suspend fun postJson(context: Context, path: String, payload: String): JSONObject? {
        return try {
            val result = execute { base ->
                Request.Builder()
                    .url(base + path)
                    .post(payload.toRequestBody(jsonType))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .build()
            }
            result?.let { JSONObject(it) }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun get(context: Context, path: String): JSONObject? {
        return try {
            val result = execute { base ->
                Request.Builder().url(base + path).get().build()
            }
            result?.let { JSONObject(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** 主站失败自动回落 IP 直连（与统计上报同一策略）。 */
    private suspend fun execute(build: (String) -> Request): String? {
        for (base in listOf(PRIMARY_BASE, FALLBACK_BASE)) {
            val text = runCatching { callOnce(build(base)) }.getOrNull()
            if (text != null) return text
        }
        return null
    }

    private suspend fun callOnce(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    if (continuation.isActive) continuation.resume(text)
                }
            }
        })
    }
}

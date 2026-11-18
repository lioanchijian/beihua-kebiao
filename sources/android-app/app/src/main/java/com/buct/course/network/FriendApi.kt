package com.buct.course.network

import android.content.Context
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

data class FriendSummary(
    val displayId: String,
    val nickname: String,
    val unread: Int,
    val lastBody: String,
    val lastKind: String,
    val lastAt: String
)

data class FriendRequest(
    val id: Long,
    val displayId: String,
    val nickname: String,
    val createdAt: String
)

data class FriendMessage(
    val id: Long,
    val fromMe: Boolean,
    val kind: String,
    val body: String,
    val payload: String,
    val createdAt: String
) {
    val isSchedule: Boolean get() = kind == "schedule"
}

data class FriendListData(
    val displayId: String,
    val nickname: String,
    val unread: Int,
    val friends: List<FriendSummary>,
    val requests: List<FriendRequest>
)

data class FriendThreadData(
    val nickname: String,
    val displayId: String,
    val messages: List<FriendMessage>,
    val unread: Int
)

data class FriendActionResult(val error: String? = null, val status: String = "")

/**
 * 好友与课表互传 API。
 *
 * 复用匿名反馈那套身份：同一个安装 ID → 同一个短号（BH-XXXXXX）和 token，
 * 用户只需把短号给同学就能互加，不用注册账号、不涉及学号密码。
 */
object FriendApi {
    private const val PRIMARY_BASE = "https://buctkb.cn"
    private const val FALLBACK_BASE = "http://47.243.237.7"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun token(context: Context): String? = FeedbackApi.token(context)

    suspend fun ensureIdentity(context: Context): FeedbackIdentity? = FeedbackApi.ensureIdentity(context)

    suspend fun list(context: Context): FriendListData? {
        val token = token(context) ?: return null
        val body = get("/api/friend/list?token=${encode(token)}") ?: return null
        if (!body.optBoolean("ok")) return null
        val me = body.optJSONObject("me")
        val friends = ArrayList<FriendSummary>()
        body.optJSONArray("friends")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                friends += FriendSummary(
                    displayId = o.optString("displayId"),
                    nickname = o.optString("nickname"),
                    unread = o.optInt("unread"),
                    lastBody = o.optString("lastBody"),
                    lastKind = o.optString("lastKind"),
                    lastAt = o.optString("lastAt")
                )
            }
        }
        val requests = ArrayList<FriendRequest>()
        body.optJSONArray("requests")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                requests += FriendRequest(
                    id = o.optLong("id"),
                    displayId = o.optString("displayId"),
                    nickname = o.optString("nickname"),
                    createdAt = o.optString("createdAt")
                )
            }
        }
        return FriendListData(
            displayId = me?.optString("displayId").orEmpty(),
            nickname = me?.optString("nickname").orEmpty(),
            unread = body.optInt("unread"),
            friends = friends,
            requests = requests
        )
    }

    /**
     * 加好友。status: pending（已发送）/ accepted（对方之前也加了你，直接成为好友）/
     * already（本来就是好友）。error 非空表示失败。
     */
    suspend fun requestFriend(context: Context, displayId: String): FriendActionResult {
        val token = token(context) ?: return FriendActionResult(error = "请先重新进入好友页")
        val payload = JSONObject().put("token", token).put("displayId", displayId).toString()
        val body = post("/api/friend/request", payload)
            ?: return FriendActionResult(error = "网络异常，请稍后重试")
        if (body.optBoolean("ok")) {
            return FriendActionResult(status = body.optString("status"))
        }
        return FriendActionResult(error = body.optString("error").ifBlank { "添加失败" })
    }

    suspend fun respond(context: Context, requestId: Long, accept: Boolean): String? {
        val token = token(context) ?: return "请先重新进入好友页"
        val payload = JSONObject().put("token", token).put("requestId", requestId).put("accept", accept).toString()
        val body = post("/api/friend/respond", payload) ?: return "网络异常，请稍后重试"
        return if (body.optBoolean("ok")) null else body.optString("error").ifBlank { "操作失败" }
    }

    suspend fun thread(context: Context, displayId: String, sinceId: Long = 0): FriendThreadData? {
        val token = token(context) ?: return null
        val body = get(
            "/api/friend/thread?token=${encode(token)}&displayId=${encode(displayId)}&since_id=$sinceId"
        ) ?: return null
        if (!body.optBoolean("ok")) return null
        val messages = ArrayList<FriendMessage>()
        body.optJSONArray("messages")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                messages += FriendMessage(
                    id = o.optLong("id"),
                    fromMe = o.optBoolean("fromMe"),
                    kind = o.optString("kind"),
                    body = o.optString("body"),
                    payload = o.optString("payload"),
                    createdAt = o.optString("createdAt")
                )
            }
        }
        return FriendThreadData(
            nickname = body.optString("nickname"),
            displayId = body.optString("displayId"),
            messages = messages,
            unread = body.optInt("unread")
        )
    }

    suspend fun sendText(context: Context, displayId: String, text: String): String? {
        val token = token(context) ?: return "请先重新进入好友页"
        val payload = JSONObject()
            .put("token", token).put("displayId", displayId)
            .put("kind", "text").put("body", text).toString()
        val body = post("/api/friend/send", payload) ?: return "网络异常，草稿已保留"
        return if (body.optBoolean("ok")) null else body.optString("error").ifBlank { "发送失败" }
    }

    /**
     * 发课表。payload 里必须带上发送者的姓名/学号，接收端存下来后
     * 小组件标题、切换账号列表都能直接显示「谁的课表」。
     */
    suspend fun sendSchedule(
        context: Context,
        displayId: String,
        ownerName: String,
        ownerStudentId: String,
        school: String,
        term: String,
        scheduleJson: String
    ): String? {
        val token = token(context) ?: return "请先重新进入好友页"
        val payload = JSONObject()
            .put("token", token)
            .put("displayId", displayId)
            .put("kind", "schedule")
            .put("body", buildString {
                append(ownerName.ifBlank { "我" })
                append("的课表")
                if (term.isNotBlank()) append(" · ").append(term)
            })
            .put("payload", JSONObject()
                .put("ownerName", ownerName)
                .put("ownerStudentId", ownerStudentId)
                .put("school", school)
                .put("term", term)
                .put("schedule", scheduleJson))
            .toString()
        val body = post("/api/friend/send", payload) ?: return "网络异常，课表没发出去"
        return if (body.optBoolean("ok")) null else body.optString("error").ifBlank { "发送失败" }
    }

    suspend fun remove(context: Context, displayId: String): String? {
        val token = token(context) ?: return "请先重新进入好友页"
        val payload = JSONObject().put("token", token).put("displayId", displayId).toString()
        val body = post("/api/friend/remove", payload) ?: return "网络异常，请稍后重试"
        return if (body.optBoolean("ok")) null else body.optString("error").ifBlank { "删除失败" }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private suspend fun post(path: String, payload: String): JSONObject? = try {
        execute { base ->
            Request.Builder()
                .url(base + path)
                .post(payload.toRequestBody(jsonType))
                .header("Content-Type", "application/json; charset=utf-8")
                .build()
        }?.let { JSONObject(it) }
    } catch (_: Exception) {
        null
    }

    private suspend fun get(path: String): JSONObject? = try {
        execute { base -> Request.Builder().url(base + path).get().build() }?.let { JSONObject(it) }
    } catch (_: Exception) {
        null
    }

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

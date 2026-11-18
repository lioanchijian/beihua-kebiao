package com.buct.course.network

import com.buct.course.model.SchoolConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class EmptyClassroom(
    val id: String,
    val code: String,
    val name: String,
    val campus: String,
    val building: String,
    val floor: String,
    val type: String,
    val seats: Int,
    val examSeats: Int
)

data class EmptyClassroomCampus(val id: Int, val label: String)

object EmptyClassroomApi {
    /** 教务侧表示「未登录」的 HTTP 状态码。 */
    private val SESSION_EXPIRED_HTTP_CODES = setOf(401, 403, 901)

    private val fakeRoomPattern = Regex("^北\\d.*")
    val campuses = listOf(
        EmptyClassroomCampus(0, "全部"),
        EmptyClassroomCampus(2, "北区"),
        EmptyClassroomCampus(1, "东区"),
        EmptyClassroomCampus(3, "西区")
    )

    suspend fun load(
        school: SchoolConfig,
        semester: String,
        week: Int,
        day: Int,
        period: Int,
        campusId: Int
    ): List<EmptyClassroom> {
        val params = school.parseSemester(semester)
        val xnm = params.getOrNull(0).orEmpty()
        val xqm = params.getOrNull(1).orEmpty()
        val campusIds = if (campusId == 0) listOf(2, 1, 3) else listOf(campusId)
        val all = campusIds.flatMap { id ->
            fetchOne(school, xnm, xqm, id, week, day, period)
        }
        return all.distinctBy { it.id }
            .sortedWith(
                compareBy<EmptyClassroom>(
                    { campusRank(it.campus) },
                    { buildingRank(it.building) },
                    { it.building.trim() },
                    { it.code }
                )
            )
    }

    private fun campusRank(name: String): Int = when {
        name.contains("北区") -> 0
        name.contains("东区") -> 1
        name.contains("西区") -> 2
        else -> 3
    }

    private fun buildingRank(name: String): Int {
        val value = name.trim()
        return when {
            value.contains("第一教学楼") || value.contains("一教") || value == "教一" -> 0
            value.contains("第二教学楼") || value.contains("二教") || value == "教二" -> 1
            else -> 2
        }
    }

    private suspend fun fetchOne(
        school: SchoolConfig,
        xnm: String,
        xqm: String,
        campusId: Int,
        week: Int,
        day: Int,
        period: Int
    ): List<EmptyClassroom> = suspendCancellableCoroutine { continuation ->
        CourseApiClient.getInstance().fetchEmptyClassrooms(
            school, xnm, xqm, campusId, week, day, period,
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { result ->
                        if (!result.isSuccessful) {
                            if (continuation.isActive) {
                                // 北化教务未登录时直接回 HTTP 901（也有 401/403 的情况）
                                continuation.resumeWithException(
                                    if (result.code in SESSION_EXPIRED_HTTP_CODES) {
                                        SessionExpiredException(result.code)
                                    } else {
                                        IOException("HTTP ${result.code}")
                                    }
                                )
                            }
                            return
                        }
                        val body = result.body?.string().orEmpty()
                        val expiredCode = expiredBusinessCode(body)
                        if (expiredCode != null) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(SessionExpiredException(expiredCode))
                            }
                            return
                        }
                        val parsed = runCatching { parse(body) }
                        if (continuation.isActive) {
                            parsed.onSuccess { continuation.resume(it) }
                                .onFailure {
                                    // 被网关重定向成登录页时 JSON 解析会失败，这里再兜一层
                                    continuation.resumeWithException(
                                        if (looksLikeLoginPage(body)) SessionExpiredException(901) else it
                                    )
                                }
                        }
                    }
                }
            }
        )
    }

    /**
     * 正方接口有时 HTTP 200 但业务码表示未登录。
     * 返回非空即视为会话失效，值为业务码（901 等）。
     */
    private fun expiredBusinessCode(body: String): Int? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return null
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val raw = when {
            root.has("code") -> root.optString("code", "")
            root.has("status") -> root.optString("status", "")
            else -> ""
        }.trim()
        val code = raw.toIntOrNull() ?: return null
        return code.takeIf { it == 901 || it == 401 || it == 403 }
    }

    /** 登录页兜底判定：网关拦截后返回的是统一身份认证 HTML 而不是 JSON。 */
    private fun looksLikeLoginPage(body: String): Boolean {
        if (body.isBlank()) return false
        val lower = body.lowercase()
        val markers = listOf(
            "authserver/login",
            "loginname",
            "统一身份认证",
            "用户登录",
            "请先登录",
            "login_form",
            "caslogin"
        )
        val hasHtml = lower.contains("<html") || lower.contains("<form")
        return hasHtml && markers.any { lower.contains(it) }
    }

    private fun parse(body: String): List<EmptyClassroom> {
        val root = JSONObject(body)
        val items = root.optJSONArray("items") ?: return emptyList()
        val result = ArrayList<EmptyClassroom>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (item.optString("sfybyy") == "1") continue
            val name = item.optString("cdmc").trim()
            if (name.isEmpty()) continue
            val code = item.optString("cdbh").trim()
            if (fakeRoomPattern.matches(name) || fakeRoomPattern.matches(code)) continue
            val id = item.optString("cd_id").ifBlank { "$code|$name" }
            result += EmptyClassroom(
                id = id,
                code = code,
                name = name,
                campus = item.optString("xqmc").trim(),
                building = item.optString("jxlmc").trim(),
                floor = item.optString("lch").trim(),
                type = item.optString("cdlbmc").trim(),
                seats = item.optInt("zws", 0),
                examSeats = item.optInt("kszws1", 0)
            )
        }
        return result
    }
}
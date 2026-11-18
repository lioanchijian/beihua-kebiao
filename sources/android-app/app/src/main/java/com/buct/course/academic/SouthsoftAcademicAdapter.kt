package com.buct.course.academic

import com.buct.course.model.SchoolConfig
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * 南京南软研究生管理信息系统（GMIS 5.x）适配器。
 *
 * 该校系统只能从校园网/VPN 访问，登录必须经过图片验证码；课表、成绩由
 * [SouthsoftStudyReader] 读取。研究生选课协议尚未验证，因此这里明确返回
 * UNSUPPORTED，避免误提交学校的写入接口。
 */
internal class SouthsoftAcademicAdapter(
    school: SchoolConfig,
    session: AcademicSession,
    transport: AcademicHttpTransport
) : BaseAcademicAdapter(school, session, transport, AcademicSystem.SOUTHSOFT), AcademicCaptchaLogin {

    private data class LoginAttempt(
        val credentials: Credentials,
        val publicKeyPem: String,
        val loginPageUrl: String,
        val sessionEpoch: Long
    )

    @Volatile
    private var attempt: LoginAttempt? = null

    override suspend fun login(credentials: Credentials): LoginResult = serial {
        clearLoginState()
        session.invalidate()
        session.username = credentials.username
        try {
            val page = transport.get(transport.appUrl(SouthsoftProtocol.loginPagePath()))
            if (page.code !in 200..299) {
                return@serial LoginResult(AcademicStatus.NETWORK_RETRYABLE, message = networkHint())
            }
            val document = Jsoup.parse(page.text, page.url)
            val pem = document.selectFirst("#pubkey")?.attr("value")
                ?: document.selectFirst("input[name=pubkey]")?.attr("value")
            if (pem.isNullOrBlank()) {
                return@serial LoginResult(AcademicStatus.PAGE_CHANGED, message = "研究生系统登录页缺少公钥，可能已改版")
            }
            val captchaUrl = captchaUrl()
            val image = transport.getImage(captchaUrl, page.url)
            attempt = LoginAttempt(credentials, pem, page.url, session.epoch)
            LoginResult(
                AcademicStatus.CAPTCHA_REQUIRED,
                captcha = CaptchaChallenge(image, "VeriCode", captchaUrl)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loginError(e)
        }
    }

    override suspend fun submitCaptcha(code: String): LoginResult = serial {
        val current = attempt?.takeIf { it.sessionEpoch == session.epoch }
            ?: return@serial LoginResult(AcademicStatus.SESSION_EXPIRED, message = "登录会话已失效，请重新登录")
        if (code.isBlank()) {
            return@serial LoginResult(AcademicStatus.CAPTCHA_REQUIRED, message = "请输入验证码")
        }
        try {
            val encrypted = SouthsoftProtocol.rsaEncrypt(current.publicKeyPem, current.credentials.password)
            val payload = JSONObject()
                .put("UserId", current.credentials.username)
                .put("Password", encrypted)
                .put("VeriCode", code.trim())
                .put("url", "")
                .put("city", "")
            val response = transport.postForm(
                transport.appUrl(SouthsoftProtocol.loginSubmitPath()),
                listOf("json" to payload.toString()),
                current.loginPageUrl,
                ajax = true
            )
            val json = SouthsoftProtocol.jsonObject(response.text)
            if (json != null) {
                if (SouthsoftProtocol.isSuccess(json)) return@serial finishLogin(current)
                val message = SouthsoftProtocol.message(json).ifBlank { "研究生系统登录失败" }
                if (SouthsoftProtocol.indicatesCaptcha(message)) {
                    attempt = current
                    return@serial LoginResult(
                        AcademicStatus.CAPTCHA_REQUIRED,
                        message = "验证码错误或已过期，请重新输入",
                        captcha = refreshCaptchaQuietly()
                    )
                }
                if (SouthsoftProtocol.indicatesCredentials(message)) {
                    clearLoginState()
                    return@serial LoginResult(AcademicStatus.INVALID_CREDENTIALS, message = "学号或密码不正确")
                }
                return@serial LoginResult(AcademicStatus.VALIDATION_FAILED, message = message)
            }
            // 少数部署会直接 302 到首页；跟随到首页后以个人信息接口确认登录结果。
            finishLogin(current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loginError(e)
        }
    }

    override suspend fun refreshCaptcha(): CaptchaChallenge? = serial {
        if (attempt == null) null else refreshCaptchaQuietly()
    }

    override fun clearLoginState() {
        attempt = null
    }

    override suspend fun validateSession(): LoginResult = serial {
        val identity = profile()
        if (identity == null) LoginResult(AcademicStatus.SESSION_EXPIRED, message = "登录已失效，请重新登录")
        else LoginResult(AcademicStatus.SUCCESS, identity.first, identity.second)
    }

    private suspend fun finishLogin(current: LoginAttempt): LoginResult {
        val identity = profile()
        return if (identity != null) {
            clearLoginState()
            LoginResult(AcademicStatus.SUCCESS, identity.first, identity.second)
        } else {
            LoginResult(AcademicStatus.VALIDATION_FAILED, message = "登录成功，但未能读取研究生学号，请重试")
        }
    }

    private suspend fun profile(): Pair<String, String>? {
        val response = transport.get(
            transport.appUrl("student/default/getxscardinfo?_=${System.currentTimeMillis()}"),
            referer = transport.appUrl("student/default/index"),
            ajax = true
        )
        if (response.code !in 200..299) return null
        val rows: JSONArray = SouthsoftProtocol.jsonArray(response.text) ?: return null
        val row: JSONObject = rows.optJSONObject(0) ?: return null
        val name = row.optString("xm").trim()
        val id = row.optString("xh").trim().ifBlank { session.username }
        if (id.isBlank()) return null
        session.username = id
        return name to id
    }

    private suspend fun refreshCaptchaQuietly(): CaptchaChallenge? = try {
        val url = captchaUrl()
        CaptchaChallenge(
            transport.getImage(url, transport.appUrl(SouthsoftProtocol.loginPagePath())),
            "VeriCode", url
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun captchaUrl(): String = transport.appUrl(
        String.format(java.util.Locale.US, SouthsoftProtocol.captchaPath(), System.currentTimeMillis())
    )

    private fun networkHint(): String = if (school.campusNetworkOnly)
        "研究生系统仅限校园网/VPN 访问，请连接校园网或学校 VPN 后重试"
    else "无法连接研究生系统，请检查网络后重试"

    private fun loginError(e: Exception): LoginResult = when (e) {
        is AcademicException -> when (e.status) {
            AcademicStatus.NETWORK_RETRYABLE -> LoginResult(AcademicStatus.NETWORK_RETRYABLE, message = networkHint())
            else -> LoginResult(e.status, message = e.message.orEmpty().ifBlank { "研究生系统登录失败，请稍后重试" })
        }
        else -> LoginResult(AcademicStatus.VALIDATION_FAILED, message = "研究生系统登录失败，请稍后重试")
    }

    override suspend fun loadCourseContext(): CourseContext = serial {
        val response = transport.postForm(
            transport.appUrl("student/pygl/xswsxk_kb_load"),
            emptyList(),
            referer = transport.appUrl("student/pygl/xswsxk_kb"),
            ajax = true
        )
        if (response.code !in 200..299) throw AcademicException(
            AcademicStatus.SESSION_EXPIRED, "研究生系统登录已失效，请重新登录"
        )
        if (SouthsoftProtocol.jsonObject(response.text) == null) throw AcademicException(
            AcademicStatus.PAGE_CHANGED, "研究生选课页结构已变化，请稍后重试"
        )
        CourseContext(
            sessionEpoch = session.epoch,
            scopes = listOf(
                CourseScope("3", "全校课程（可搜索）"),
                CourseScope("1", "公共课"),
                CourseScope("2", "专业课")
            )
        )
    }

    override suspend fun listCourses(context: CourseContext, query: CourseQuery): List<CourseOffer> = serial {
        check(context)
        val scope = query.scopeId.ifBlank { "3" }
        val pageSize = query.pageSize.coerceIn(10, 500)
        val page = (query.start / pageSize) + 1
        suspend fun request(keyword: String): JSONObject {
            val response = transport.postForm(
                transport.appUrl("student/pygl/xswsxk_kb_list"),
                listOf(
                    "lb" to scope,
                    "xwk" to "",
                    "zh" to "",
                    "jc1" to "",
                    "jc2" to "",
                    "yxsh" to "",
                    "kcbh" to keyword,
                    "page" to page.toString(),
                    "rows" to pageSize.toString(),
                    "type" to "2"
                ),
                referer = transport.appUrl("student/pygl/xswsxk_kb"),
                ajax = true
            )
            return SouthsoftProtocol.jsonObject(response.text)
                ?: throw AcademicException(AcademicStatus.PAGE_CHANGED, "研究生选课列表返回格式已变化")
        }
        var json = request(query.keyword.trim())
        var rows = json.optJSONArray("kc") ?: JSONArray()
        // 部分部署的 kcbh 只匹配课程编号；按名称搜索无结果时，退回本地过滤。
        if (query.keyword.isNotBlank() && rows.length() == 0) {
            json = request("")
            rows = json.optJSONArray("kc") ?: JSONArray()
        }
        val result = mutableListOf<CourseOffer>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val name = row.optString("kcmc").trim().ifBlank { row.optString("bjmc").trim() }
            val id = row.optString("bjid").trim()
            if (name.isBlank() || id.isBlank()) continue
            if (query.keyword.isNotBlank() && !name.contains(query.keyword, true) &&
                !row.optString("kcbh").contains(query.keyword, true) &&
                !row.optString("bjmc").contains(query.keyword, true)
            ) continue
            if (query.teacher.isNotBlank() && !row.optString("rkjsxm").contains(query.teacher, true)) continue
            result += CourseOffer(
                stableId = id,
                name = name,
                teacher = row.optString("rkjsxm").trim(),
                time = row.optString("sjms").trim(),
                location = row.optString("dz").trim(),
                credit = row.optString("kcxf").trim(),
                capacity = row.optString("rnrs").toIntOrNull(),
                selected = row.optString("xkrs").toIntOrNull(),
                scopeId = scope,
                raw = mapOf(
                    "kch_id" to row.optString("kcbh").trim(),
                    "jxb_id" to id,
                    "sectionId" to id,
                    "bjmc" to row.optString("bjmc").trim(),
                    "lb" to scope,
                    "xwk" to row.optString("xwk").trim(),
                    "yxkc" to row.optString("yxkc").trim(),
                    "xkfs" to row.optString("xkfs").trim(),
                    "cjs" to row.optString("cjs").trim(),
                    "ksz" to row.optString("ksz").trim(),
                    "jsz" to row.optString("jsz").trim(),
                    "isSelected" to (row.optString("yxkc") == "1").toString()
                )
            )
        }
        result
    }

    override suspend fun listSections(course: CourseOffer): List<CourseSection> = serial {
        listOf(
            CourseSection(
                stableId = course.stableId,
                courseStableId = course.stableId,
                name = course.raw["bjmc"].orEmpty(),
                teacher = course.teacher,
                time = course.time,
                location = course.location,
                capacity = course.capacity,
                selected = course.selected,
                raw = course.raw
            )
        )
    }

    override suspend fun select(target: SelectionTarget): SelectionResult = serial {
        val id = target.section.stableId.ifBlank { target.course.stableId }
        val response = transport.postForm(
            transport.appUrl("student/pygl/xswsxk_kb_xk"),
            listOf(
                "lb" to "1",
                "bjid" to id,
                "xkyzm" to target.section.raw["xkyzm"].orEmpty()
            ),
            referer = transport.appUrl("student/pygl/xswsxk_kb"),
            ajax = true
        )
        selectionResult(response.text)
    }

    override suspend fun selected(context: CourseContext): List<SelectedCourse> = serial {
        check(context)
        val result = mutableListOf<SelectedCourse>()
        for (scope in context.scopes) {
            val offers = listCourses(context, CourseQuery(pageSize = 500, scopeId = scope.id))
            offers.filter { it.raw["yxkc"] == "1" }.forEach { offer ->
                result += SelectedCourse(
                    stableId = offer.stableId,
                    name = offer.name,
                    teacher = offer.teacher,
                    courseId = offer.raw["kch_id"].orEmpty(),
                    sectionId = offer.stableId,
                    raw = offer.raw
                )
            }
        }
        result.distinctBy { it.stableId }
    }

    override suspend fun drop(target: SelectionTarget): OperationResult = serial {
        val id = target.section.stableId.ifBlank { target.course.stableId }
        val response = transport.postForm(
            transport.appUrl("student/pygl/xswsxk_kb_xk"),
            listOf("lb" to "0", "bjid" to id, "xkyzm" to target.section.raw["xkyzm"].orEmpty()),
            referer = transport.appUrl("student/pygl/xswsxk_kb"),
            ajax = true
        )
        val result = selectionResult(response.text)
        OperationResult(result.status, result.message, result.status == AcademicStatus.SUCCESS)
    }

    private fun selectionResult(body: String): SelectionResult {
        val json = SouthsoftProtocol.jsonObject(body)
            ?: return SelectionResult(AcademicStatus.RESULT_UNKNOWN, "学校未返回明确结果，请刷新已选课程确认")
        val message = json.optString("msg").trim().ifBlank { json.optString("message").trim() }
        if (message.isBlank() || message == "选课成功" || message == "退选成功") {
            return SelectionResult(AcademicStatus.SUCCESS, message)
        }
        val status = when {
            message.contains("验证码") -> AcademicStatus.HUMAN_VERIFICATION_REQUIRED
            message.contains("人满") || message.contains("容量") -> AcademicStatus.NO_CAPACITY
            message.contains("时间") && message.contains("冲突") -> AcademicStatus.CONFLICT
            message.contains("已选") || message.contains("已经选") -> AcademicStatus.ALREADY_SELECTED
            message.contains("学分") -> AcademicStatus.CREDIT_LIMIT
            message.contains("轮次") || message.contains("时间") -> AcademicStatus.ROUND_CLOSED
            else -> AcademicStatus.VALIDATION_FAILED
        }
        return SelectionResult(status, message)
    }
}

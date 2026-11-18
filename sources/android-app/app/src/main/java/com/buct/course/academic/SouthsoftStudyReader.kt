package com.buct.course.academic

import com.buct.course.model.SchoolConfig
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * 南软 GMIS 5.x 的课表与成绩读取。
 *
 * 实测接口：
 * - 学期：GET student/default/bindterm
 * - 课表：POST student/pygl/py_kbcx_ew，kblx=xs&termcode=...
 * - 成绩：GET student/pygl/xscjcx_list
 */
internal class SouthsoftStudyReader(
    private val school: SchoolConfig,
    private val session: AcademicSession,
    private val transport: AcademicHttpTransport
) : AcademicStudyAdapter {

    private var cachedCatalog: AcademicStudyCatalog? = null
    private val termCodes = linkedMapOf<String, String>()

    override suspend fun catalog(): AcademicStudyCatalog = session.withProtocolLock {
        val response = transport.get(
            transport.appUrl("student/default/bindterm?_=${System.currentTimeMillis()}"),
            referer = homeUrl(),
            ajax = true
        )
        if (response.code !in 200..299) throw AcademicException(
            AcademicStatus.NETWORK_RETRYABLE, networkHint()
        )
        val rows: JSONArray = SouthsoftProtocol.jsonArray(response.text)
            ?: throw AcademicException(AcademicStatus.PAGE_CHANGED, "研究生系统未返回可识别的学期列表")
        termCodes.clear()
        val terms = mutableListOf<AcademicTerm>()
        var current: AcademicTerm? = null
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val code = row.optString("termcode").trim()
            val name = row.optString("termname").trim()
            if (code.isBlank() || name.isBlank()) continue
            val id = termId(name) ?: continue
            if (termCodes.put(id, code) == null) terms += AcademicTerm(id, name)
            if (row.optBoolean("selected", false) && current == null) current = AcademicTerm(id, name)
        }
        if (terms.isEmpty()) throw AcademicException(AcademicStatus.PAGE_CHANGED, "研究生系统未返回可识别的学期")
        val selected = current ?: terms.first()
        AcademicStudyCatalog(terms, selected).also { cachedCatalog = it }
    }

    override suspend fun schedule(term: AcademicTerm): List<AcademicScheduleEntry> = session.withProtocolLock {
        val code = termCode(term)
        val response = transport.postForm(
            transport.appUrl("student/pygl/py_kbcx_ew"),
            listOf("kblx" to "xs", "termcode" to code),
            referer = transport.appUrl("student/pygl/py_kbcx")
        )
        if (response.code !in 200..299) throw AcademicException(
            AcademicStatus.NETWORK_RETRYABLE, networkHint()
        )
        val json = SouthsoftProtocol.jsonObject(response.text)
            ?: throw AcademicException(AcademicStatus.PAGE_CHANGED, "研究生系统未返回可识别的课表")
        val rows = json.optJSONArray("rows") ?: json.optJSONArray("data")
            ?: throw AcademicException(AcademicStatus.PAGE_CHANGED, "研究生系统课表缺少 rows 字段")

        val entries = mutableListOf<AcademicScheduleEntry>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val period = period(row.optString("mc")) ?: continue
            for (day in 1..7) {
                val html = row.optString("z$day").trim()
                if (html.isBlank() || html == "null") continue
                entries += parseCell(html, day, period)
            }
        }
        mergeAdjacentPeriods(entries)
    }

    override suspend fun grades(term: AcademicTerm?): AcademicGradeReport = session.withProtocolLock {
        val selected = term ?: runCatching { catalog().currentTerm }.getOrElse {
            throw AcademicException(AcademicStatus.PAGE_CHANGED, "无法确定研究生系统当前学期")
        }
        val response = transport.get(
            transport.appUrl("student/pygl/xscjcx_list?_=${System.currentTimeMillis()}"),
            referer = transport.appUrl("student/pygl/xscjcx"),
            ajax = true
        )
        if (response.code !in 200..299) throw AcademicException(
            AcademicStatus.NETWORK_RETRYABLE, networkHint()
        )
        val json = SouthsoftProtocol.jsonObject(response.text)
            ?: throw AcademicException(AcademicStatus.PAGE_CHANGED, "研究生系统未返回可识别的成绩数据")

        val grades = mutableListOf<AcademicGrade>()
        fun addRows(key: String, type: String) {
            val rows = json.optJSONArray(key) ?: return
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val name = row.optString("kcmc").trim()
                if (name.isBlank()) continue
                val score = row.optString("cj").trim()
                val numeric = row.optString("cjfs").trim()
                val detail = buildList {
                    if (numeric.isNotBlank() && numeric != score) add("百分制: $numeric")
                    row.optString("rkjs").trim().takeIf(String::isNotBlank)?.let { add("任课教师: $it") }
                }.joinToString(" | ")
                grades += AcademicGrade(
                    name = name,
                    score = score,
                    credits = row.optString("kcxf").trim(),
                    gradePoint = gradePoint(score, numeric),
                    type = type,
                    term = selected.id,
                    code = row.optString("kcbh").trim(),
                    sectionId = row.optString("bjid").trim(),
                    detail = detail
                )
            }
        }
        addRows("xwklist", "学位课")
        addRows("fxwklist", "非学位课")
        addRows("hjcjlist", "环节")

        val summary = json.optJSONObject("xftj")
            ?: json.optJSONArray("xftj")?.optJSONObject(0)
        AcademicGradeReport(
            grades = grades,
            gradePointAverage = summary?.optString("zfgpa")?.trim().orEmpty(),
            totalCredits = summary?.optString("yxzxf")?.trim().orEmpty()
                .ifBlank { json.optString("hjzxf").trim() }
        )
    }

    /** 研究生成绩常用 A/B+/B/C+ 两级或字母制；按学校常见 4.3 制换算，仅用于列表汇总。 */
    private fun gradePoint(grade: String, numeric: String): String {
        val letter = grade.trim().uppercase()
        val mapped = when (letter) {
            "A+", "A" -> 4.0
            "A-" -> 3.7
            "B+" -> 3.3
            "B" -> 3.0
            "B-" -> 2.7
            "C+" -> 2.3
            "C" -> 2.0
            "C-" -> 1.7
            "D+" -> 1.3
            "D" -> 1.0
            "F", "E" -> 0.0
            else -> null
        }
        if (mapped != null) return String.format(java.util.Locale.ROOT, "%.1f", mapped)
        val score = numeric.toDoubleOrNull() ?: grade.toDoubleOrNull() ?: return ""
        return when {
            score >= 90 -> "4.0"
            score >= 85 -> "3.7"
            score >= 82 -> "3.3"
            score >= 78 -> "3.0"
            score >= 75 -> "2.7"
            score >= 72 -> "2.3"
            score >= 68 -> "2.0"
            score >= 64 -> "1.7"
            score >= 60 -> "1.0"
            else -> "0.0"
        }
    }

    /** 研究生系统暂未提供已验证的考试查询接口，返回空列表而不是伪造数据。 */
    override suspend fun exams(term: AcademicTerm): List<AcademicExam> = emptyList()

    private suspend fun termCode(term: AcademicTerm): String {
        termCodes[term.id]?.let { return it }
        if (term.id.all(Char::isDigit)) return term.id
        runCatching { catalog() }
        return termCodes[term.id] ?: throw AcademicException(
            AcademicStatus.PAGE_CHANGED, "研究生系统学期已变化，请刷新后重试"
        )
    }

    private fun termId(name: String): String? {
        val years = Regex("""(\d{4})\s*-\s*(\d{4})""").find(name) ?: return null
        val semester = when {
            name.contains("秋") -> 1
            name.contains("春") -> 2
            else -> 3
        }
        return "${years.groupValues[1]}-${years.groupValues[2]}-$semester"
    }

    private fun period(label: String): Int? =
        Regex("""\d+""").find(label)?.value?.toIntOrNull()?.takeIf { it in 1..20 }

    private fun parseCell(html: String, day: Int, period: Int): List<AcademicScheduleEntry> {
        val lines = html.split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))
            .map { Jsoup.parse(it).text().replace('\u00a0', ' ').trim() }
            .filter { it.isNotBlank() && it != "null" }
        val result = mutableListOf<AcademicScheduleEntry>()
        for (line in lines) {
            val full = Regex("""^(.+?)\s*\[([^\]]+)]\s*(.*?)\s*\[([^\]]+)]\s*$""").find(line)
            if (full != null) {
                result += AcademicScheduleEntry(
                    name = full.groupValues[1].trim(),
                    teacher = full.groupValues[3].trim(),
                    location = full.groupValues[4].trim(),
                    day = day, startPeriod = period, endPeriod = period,
                    weeks = full.groupValues[2].trim()
                )
            } else {
                val partial = Regex("""^(.+?)\s*\[([^\]]+)]\s*(.*)$""").find(line)
                if (partial != null) {
                    result += AcademicScheduleEntry(
                        name = partial.groupValues[1].trim(),
                        teacher = partial.groupValues[3].trim(),
                        location = "",
                        day = day, startPeriod = period, endPeriod = period,
                        weeks = partial.groupValues[2].trim()
                    )
                } else {
                    result += AcademicScheduleEntry(
                        name = line, teacher = "", location = "",
                        day = day, startPeriod = period, endPeriod = period,
                        weeks = "1-20周"
                    )
                }
            }
        }
        return result
    }

    /** 同一门课连续占多个节次时合并成一个时间块，避免课表显示成多张碎片。 */
    private fun mergeAdjacentPeriods(entries: List<AcademicScheduleEntry>): List<AcademicScheduleEntry> {
        val merged = mutableListOf<AcademicScheduleEntry>()
        entries.sortedWith(compareBy({ it.day }, { it.startPeriod })).forEach { entry ->
            val last = merged.lastOrNull()
            if (last != null && last.day == entry.day && last.name == entry.name &&
                last.teacher == entry.teacher && last.location == entry.location &&
                last.weeks == entry.weeks && entry.startPeriod <= last.endPeriod + 1
            ) {
                merged[merged.lastIndex] = last.copy(endPeriod = maxOf(last.endPeriod, entry.endPeriod))
            } else {
                merged += entry
            }
        }
        return merged
    }

    private fun homeUrl(): String = transport.appUrl("student/default/index")

    private fun networkHint(): String = if (school.campusNetworkOnly)
        "研究生系统仅限校园网/VPN 访问，请连接校园网或学校 VPN 后重试"
    else "无法连接研究生系统，请检查网络后重试"
}

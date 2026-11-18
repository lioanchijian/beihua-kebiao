package com.buct.course.schedule

import java.util.Calendar
import java.util.TimeZone

object ScheduleDates {
    /** UI-selected dates use Monday-based teaching weeks, in the user's local zone. */
    fun mondayOfWeek(millis: Long, zone: TimeZone = TimeZone.getDefault()): Calendar =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun calendarDate(value: String?, zone: TimeZone): Calendar? = runCatching {
        val parts = requireNotNull(value).split('-').map(String::toInt)
        require(parts.size == 3)
        Calendar.getInstance(zone).apply {
            clear(); isLenient = false
            set(parts[0], parts[1] - 1, parts[2])
            timeInMillis
        }
    }.getOrNull()

    fun firstMonday(value: String?, zone: TimeZone = TimeZone.getDefault()): Calendar? =
        calendarDate(value, zone)?.takeIf { it.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY }

    /** Older versions persisted any selected weekday; normalize the civil date without a zone shift. */
    fun normalizeFirstWeekDate(value: String?): String? {
        val zone = TimeZone.getTimeZone("UTC")
        val chosen = calendarDate(value, zone) ?: return null
        return ScheduleTimeBase.dateFromMillis(mondayOfWeek(chosen.timeInMillis, zone).timeInMillis, zone)
    }

    fun date(value: String?, week: Int, day: Int = 1, zone: TimeZone = TimeZone.getDefault()): Calendar? =
        firstMonday(value, zone)?.apply { add(Calendar.DAY_OF_YEAR, (week - 1) * 7 + day - 1) }

    fun weekAt(value: String?, now: Long, zone: TimeZone = TimeZone.getDefault()): Int? {
        val start = firstMonday(value, TimeZone.getTimeZone("UTC")) ?: return null
        val local = Calendar.getInstance(zone).apply { timeInMillis = now }
        val today = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }
        val days = (today.timeInMillis - start.timeInMillis) / 86_400_000L
        return if (days < 0) null else (days / 7 + 1).toInt().takeIf { it in 1..ScheduleMaxWeeks }
    }

    /** 某年某月的第一个周一（教学周起点）。month: 1-12。 */
    fun firstMondayOfMonth(year: Int, month: Int, zone: TimeZone = TimeZone.getDefault()): Calendar? = runCatching {
        Calendar.getInstance(zone).apply {
            clear(); isLenient = false
            set(year, month - 1, 1)
            val dow = get(Calendar.DAY_OF_WEEK)
            add(Calendar.DAY_OF_MONTH, (Calendar.MONDAY - dow + 7) % 7)
            timeInMillis
        }
    }.getOrNull()

    /** 解析 "yyyy-MM-dd"（可从 "yyyy-MM-dd(…)" 前缀截取）。 */
    fun parseDateMillis(dateStr: String?, zone: TimeZone = TimeZone.getDefault()): Long? = runCatching {
        val d = requireNotNull(dateStr).substring(0, 10)
        val parts = d.split('-').map(String::toInt)
        require(parts.size == 3)
        Calendar.getInstance(zone).apply {
            clear(); isLenient = false
            set(parts[0], parts[1] - 1, parts[2])
            timeInMillis
        }.timeInMillis
    }.getOrNull()

    /** 星期中文标签（dayOfWeek: Calendar.DAY_OF_WEEK，1=周日）。 */
    fun weekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.MONDAY -> "周一"; Calendar.TUESDAY -> "周二"; Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"; Calendar.FRIDAY -> "周五"; Calendar.SATURDAY -> "周六"
        Calendar.SUNDAY -> "周日"; else -> "周$dayOfWeek"
    }

    /** 开学日所在周的周一到开学日的天数（周一=0 … 周日=6），用于把 diffDays 对齐到周一基准。 */
    fun weekDayOffset(startMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val dow = Calendar.getInstance(zone).apply { timeInMillis = startMillis }.get(Calendar.DAY_OF_WEEK)
        return ((dow + 5) % 7).toLong()
    }

    /** 教学周号：以开学日所在周的周一为第 1 周起点（与 App 课表周次口径一致）。 */
    fun weekNumber(diffDays: Long, startMillis: Long): Int =
        (((diffDays + weekDayOffset(startMillis)) / 7) + 1).toInt().coerceAtLeast(1)

    /**
     * 考试日期 → "第X周 星期X"（如 "2026-11-03(14:30-16:30)" → "第10周 周二"）。
     * 优先使用当前学期实际开学日（semesterStartMillis，北化 2026-09-01）：
     * 考试日期 ≥ 开学日所在周周一 → 按 App 课表同源周次；
     * 旧学期考试（如 2025-2026-1 的 2026-01 期末）→ 按该学年 9 月 1 日为第 1 周起点推算。
     */
    fun examWeekDayLabel(examTime: String?, semesterStartMillis: Long?, zone: TimeZone = TimeZone.getDefault()): String? {
        val millis = parseDateMillis(examTime, zone) ?: return null
        val local = Calendar.getInstance(zone).apply { timeInMillis = millis }
        val utc = TimeZone.getTimeZone("UTC")

        fun utcCivilDate(source: Calendar): Long = Calendar.getInstance(utc).apply {
            clear()
            set(source.get(Calendar.YEAR), source.get(Calendar.MONTH), source.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis

        fun mondayOfCivilDate(dateMillis: Long): Long {
            val dow = Calendar.getInstance(utc).apply { timeInMillis = dateMillis }.get(Calendar.DAY_OF_WEEK)
            return dateMillis - (((dow + 5) % 7).toLong() * 86_400_000L)
        }

        val examDate = utcCivilDate(local)
        val currentStart = semesterStartMillis
            ?.takeIf { it > 0 }
            ?.let { utcCivilDate(Calendar.getInstance(zone).apply { timeInMillis = it }) }
            ?.let(::mondayOfCivilDate)
        val startMillis = if (currentStart != null && examDate >= currentStart) {
            currentStart
        } else {
            val month = local.get(Calendar.MONTH) + 1
            val anchorYear = when {
                month >= 9 -> local.get(Calendar.YEAR)
                month <= 6 -> local.get(Calendar.YEAR) - 1
                else -> return null
            }
            val sep1 = Calendar.getInstance(utc).apply {
                clear(); set(anchorYear, Calendar.SEPTEMBER, 1)
            }.timeInMillis
            mondayOfCivilDate(sep1)
        }
        val days = (examDate - startMillis) / 86_400_000L
        if (days < 0) return null
        val week = (days / 7 + 1).toInt().takeIf { it in 1..ScheduleMaxWeeks } ?: return null
        return "第${week}周 " + weekdayLabel(local.get(Calendar.DAY_OF_WEEK))
    }

    fun examWeek(examTime: String?, semesterStartMillis: Long?, zone: TimeZone = TimeZone.getDefault()): Int? {
        val label = examWeekDayLabel(examTime, semesterStartMillis, zone) ?: return null
        val week = label.substringAfter("第", "").substringBefore("周").trim().toIntOrNull() ?: return null
        return week.takeIf { it in 1..ScheduleMaxWeeks }
    }

    /** 考试日期对应的星期（1=周一 … 7=周日，Calendar 风格换算），解析失败返回 null。 */
    fun examWeekday(examTime: String?, zone: TimeZone = TimeZone.getDefault()): Int? {
        val millis = parseDateMillis(examTime, zone) ?: return null
        val dow = Calendar.getInstance(zone).apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
        return ((dow + 5) % 7) + 1
    }
}
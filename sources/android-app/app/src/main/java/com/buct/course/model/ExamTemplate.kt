package com.buct.course.model

import java.util.Calendar

/**
 * 常见大学生考试模板。
 *
 * 刻意不写死年月日：四六级、教资、竞赛每年日期都在变，写死必然过期。
 * 这里只描述「每年哪个月的第九个星期几、几点开考」，生成时按当年推算，
 * 生成后所有字段仍可编辑。
 */
data class ExamTemplate(
    val id: String,
    val name: String,
    val hint: String,
    /** 可能开考的月份（1-12），按顺序取最近一次。 */
    private val months: List<Int>,
    /** [Calendar.MONDAY]…[Calendar.SUNDAY] */
    private val weekday: Int,
    /** 第几个该星期几；-1 表示最后一个，-2 表示倒数第二个。 */
    private val nth: Int,
    private val startHour: Int,
    private val startMinute: Int,
    private val durationMinutes: Int
) {
    /**
     * 从 [now] 起最近一次开考时间；没有固定日期的模板返回 null。
     * @return 开始与结束时间戳（毫秒）
     */
    fun nextOccurrence(now: Long = System.currentTimeMillis()): Pair<Long, Long>? {
        if (months.isEmpty()) return null
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val startYear = today.get(Calendar.YEAR)
        var best: Long? = null
        for (year in startYear..startYear + 1) {
            for (month in months) {
                val day = dayOfMonth(year, month) ?: continue
                val cal = Calendar.getInstance().apply {
                    clear()
                    set(year, month - 1, day, startHour, startMinute, 0)
                }
                val start = cal.timeInMillis
                if (start > now && (best == null || start < best!!)) best = start
            }
        }
        val start = best ?: return null
        return start to (start + durationMinutes * 60_000L)
    }

    private fun dayOfMonth(year: Int, month: Int): Int? {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1)
        }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return when {
            nth > 0 -> {
                var day = 1
                while (day <= daysInMonth && cal.get(Calendar.DAY_OF_WEEK) != weekday) {
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                    day++
                }
                val target = day + (nth - 1) * 7
                if (target > daysInMonth) null else target
            }
            else -> {
                cal.set(year, month - 1, daysInMonth)
                while (cal.get(Calendar.DAY_OF_WEEK) != weekday) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                }
                val lastDay = cal.get(Calendar.DAY_OF_MONTH)
                // nth = -1 → 最后一个；-2 → 倒数第二个
                val target = lastDay - (if (nth == -1) 0 else (-nth - 1) * 7)
                if (target < 1) null else target
            }
        }
    }

    companion object {
        val all: List<ExamTemplate> = listOf(
            ExamTemplate(
                id = "cet4",
                name = "大学英语四级（CET-4）",
                hint = "6 月 / 12 月第二个周六 09:00-11:20",
                months = listOf(6, 12),
                weekday = Calendar.SATURDAY,
                nth = 2,
                startHour = 9,
                startMinute = 0,
                durationMinutes = 140
            ),
            ExamTemplate(
                id = "cet6",
                name = "大学英语六级（CET-6）",
                hint = "6 月 / 12 月第二个周六 15:00-17:25",
                months = listOf(6, 12),
                weekday = Calendar.SATURDAY,
                nth = 2,
                startHour = 15,
                startMinute = 0,
                durationMinutes = 145
            ),
            ExamTemplate(
                id = "ncre",
                name = "全国计算机等级考试",
                hint = "3 月 / 9 月最后一个周末，分批上机",
                months = listOf(3, 9),
                weekday = Calendar.SATURDAY,
                nth = -1,
                startHour = 9,
                startMinute = 0,
                durationMinutes = 120
            ),
            ExamTemplate(
                id = "teacher",
                name = "教师资格证（笔试）",
                hint = "3 月 / 9 月第二个周六上午",
                months = listOf(3, 9),
                weekday = Calendar.SATURDAY,
                nth = 2,
                startHour = 9,
                startMinute = 0,
                durationMinutes = 120
            ),
            ExamTemplate(
                id = "cmc",
                name = "全国大学生数学竞赛",
                hint = "11 月第二个周六 09:00-11:30",
                months = listOf(11),
                weekday = Calendar.SATURDAY,
                nth = 2,
                startHour = 9,
                startMinute = 0,
                durationMinutes = 150
            ),
            ExamTemplate(
                id = "kaoyan",
                name = "考研初试",
                hint = "12 月倒数第二个周末上午",
                months = listOf(12),
                weekday = Calendar.SATURDAY,
                nth = -2,
                startHour = 8,
                startMinute = 30,
                durationMinutes = 180
            ),
            ExamTemplate(
                id = "mandarin",
                name = "普通话水平测试",
                hint = "无固定日期，生成后请手动选择",
                months = emptyList(),
                weekday = Calendar.SATURDAY,
                nth = 1,
                startHour = 9,
                startMinute = 0,
                durationMinutes = 60
            )
        )

        fun byId(id: String): ExamTemplate? = all.firstOrNull { it.id == id }
    }
}

package com.buct.course.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.buct.course.R
import com.buct.course.manager.ScheduleSettingsManager
import com.buct.course.manager.UserManager
import com.buct.course.schedule.NetworkScheduleCourse
import com.buct.course.schedule.ScheduleJson
import com.buct.course.schedule.ScheduleWeeks
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 桌面「今日课表」小组件的 RemoteViewsService。
 * 从本地课表缓存读取数据，渲染所选日期（今天 ± offset）一天的课程与考试。
 * 样式与 App 内「按日显示」统一：紧凑卡片（课程名 + 节次时段 + 地点·教师），
 * 同一课程连续节次合并为一个卡片，考试卡片置顶，无时间轴留白。
 */
class ScheduleDayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduleDayRemoteViewsFactory(this.applicationContext, intent)
    }
}

class ScheduleDayRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    /** 当前要渲染的实例 id：读取该实例配置的账号/标题用。 */
    private val appWidgetId: Int = intent.getIntExtra(
        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
        android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
    )


    // 开学日期：2026-09-01（与周次小组件一致）
    private val semesterStartDate = 1788192000000L

    private var courses: List<NetworkScheduleCourse> = emptyList()
    private var courseColorMap = mutableMapOf<String, Int>()
    private var selectedDay = 1
    private var selectedDate = ""
    private var dayExams: List<ExamRow> = emptyList()
    private var merged: List<MergedCourse> = emptyList()

    // 日期偏移从 prefs 读取（不依赖 intent extra，保证切换后列表即时刷新到对应日期）

    /** 合并后的紧凑课程卡片 */
    private data class MergedCourse(
        val name: String,
        val location: String,
        val teacher: String,
        val startPeriod: Int,
        val endPeriod: Int
    )

    /** 考试卡片数据 */
    private data class ExamRow(
        val name: String,
        val time: String,
        val loc: String
    )

    override fun onCreate() {}

    override fun onDataSetChanged() {
        try {
            UserManager.getInstance().init(context)
            // 来源优先级：实例配置的朋友课表 > 实例配置/当前账号的本机课表
            val friendJson = WidgetScheduleSource.loadFriendJson(context, appWidgetId)
            val scheduleJson = friendJson ?: loadOwnScheduleJson()

            if (scheduleJson != null) {
                courses = ScheduleJson.parse(scheduleJson) ?: emptyList()
                val courseNames = courses.map { it.course.name }.distinct()
                courseColorMap.clear()
                courseNames.forEachIndexed { index, name ->
                    courseColorMap[name] = index
                }
            } else {
                courses = emptyList()
            }

            // 所选日期：今天 + offset（无限前后切换，从 prefs 读取保证最新）
            val dayOffset = ScheduleDayWidget.getDayOffset(context)
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, dayOffset)
            selectedDay = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1 // 周一=1..周日=7
            selectedDate = String.format(
                java.util.Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )

            // 所选日期所在周（按开学日推算，周一基准与 App 课表一致）
            val diffDays = TimeUnit.MILLISECONDS.toDays(cal.timeInMillis - semesterStartDate)
            val currentWeek = com.buct.course.schedule.ScheduleDates.weekNumber(diffDays, semesterStartDate)

            // 所选日期的课程 → 按节次排序 → 合并连续节次
            val todayCourses = courses
                .filter {
                    it.course.day == selectedDay &&
                        ScheduleWeeks.parse(it.course.weeks).visibleIn(currentWeek)
                }
                .map { it.course }
                .sortedBy { it.startPeriod }

            merged = buildList {
                for (c in todayCourses) {
                    val last = lastOrNull()
                    if (last != null && last.name == c.name && last.location == c.location &&
                        last.teacher == c.teacher && c.startPeriod == last.endPeriod + 1
                    ) {
                        this[lastIndex] = last.copy(endPeriod = c.endPeriod)
                    } else {
                        add(MergedCourse(c.name, c.location, c.teacher, c.startPeriod, c.endPeriod))
                    }
                }
            }

            // 考试：读取成绩页持久化的考试缓存，按所选日期匹配
            // 显示朋友课表时不带考试（那是本机账号的数据，混进来会误导）
            dayExams = emptyList()
            val examPrefs = context.getSharedPreferences("exam_cache", Context.MODE_PRIVATE)
            val examJson = if (friendJson == null) examPrefs.getString("exams", null) else null
            if (examJson != null) {
                val arr = JSONArray(examJson)
                dayExams = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val time = o.optString("examTime", "")
                        if (time.startsWith(selectedDate)) {
                            val name = o.optString("courseName", "考试")
                            val timeText = formatExamTime(time)
                            val loc = buildString {
                                val location = o.optString("location", "")
                                val seat = o.optString("seatNumber", "")
                                val examName = o.optString("examName", "")
                                if (examName.isNotBlank()) {
                                    if (location.isNotBlank() || seat.isNotBlank()) append("$examName · ")
                                    else append(examName)
                                }
                                if (location.isNotBlank()) append(location)
                                if (seat.isNotBlank()) append(" · $seat")
                            }
                            add(ExamRow(name, timeText, loc))
                        }
                    }
                }.sortedBy { it.time }
            }
        } catch (e: Exception) {
            courses = emptyList()
            merged = emptyList()
            dayExams = emptyList()
        }
    }

    /** 本机账号课表：实例配置的账号优先，否则跟随当前账号。 */
    private fun loadOwnScheduleJson(): String? {
        val instanceAccount = WidgetConfigStore.accountKey(context, appWidgetId)
        val account = instanceAccount ?: UserManager.getInstance().currentAccountStorageKey
        val school = UserManager.getInstance().currentSchool?.id ?: "buct"

        val prefs = context.getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)
        val baseKey = "schedule_${account}_${school}"

        val currentJson = prefs.getString("${baseKey}_current", null)
        var termId = "2026-2027-1"
        if (currentJson != null) {
            val obj = JSONObject(currentJson)
            termId = obj.optString("term", termId)
        }

        return prefs.getString("${baseKey}_${termId}", null)
            ?: com.buct.course.manager.LocalStore.load(
                context,
                "schedule_${account}_${school}_${termId}.json"
            )
    }

    private fun formatExamTime(time: String): String {
        // "2026-11-03 14:30-16:30" → "14:30-16:30"（保留完整也行）
        val idx = time.indexOf(' ')
        return if (idx > 0) time.substring(idx + 1) else time
    }

    override fun onDestroy() {}

    override fun getCount(): Int {
        val total = dayExams.size + merged.size
        return if (total == 0) 1 else total
    }

    override fun getViewAt(position: Int): RemoteViews {
        val themePrefs = context.getSharedPreferences("appearance_settings", Context.MODE_PRIVATE)
        val themeMode = themePrefs.getString("theme_mode", "system")
        val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val systemDark = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isDarkMode = when (themeMode) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }

        if (dayExams.isEmpty() && merged.isEmpty()) {
            // 空态
            val views = RemoteViews(context.packageName, R.layout.widget_schedule_day_row)
            views.setTextViewText(R.id.item_name, "当天没有课程安排")
            views.setTextViewText(R.id.item_time, "")
            views.setTextViewText(R.id.item_loc, "")
            views.setTextColor(R.id.item_name, if (isDarkMode) Color.WHITE else Color.BLACK)
            WidgetTextSizes.apply(views, WidgetTextSizes.DAY_ROW, WidgetTextSizes.scale(context))
            return views
        }

        if (position < dayExams.size) {
            // 考试卡片
            val e = dayExams[position]
            val views = RemoteViews(context.packageName, R.layout.widget_schedule_exam_row)
            views.setTextViewText(R.id.exam_name, e.name)
            views.setTextViewText(R.id.exam_time, e.time)
            views.setTextViewText(R.id.exam_loc, e.loc)
            views.setInt(R.id.exam_root, "setBackgroundResource", R.drawable.widget_exam_bg)
            WidgetTextSizes.apply(views, WidgetTextSizes.DAY_EXAM_ROW, WidgetTextSizes.scale(context))
            return views
        }

        // 课程卡片
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_day_row)
        val c = merged[position - dayExams.size]

        // 精确时段（与 App 内一致）
        val periodTimes = ScheduleSettingsManager.getInstance().apply { init(context) }.getPeriodTimes()
        val startTime = periodTimes.firstOrNull { it.period == c.startPeriod }?.startTime ?: ""
        val endTime = periodTimes.firstOrNull { it.period == c.endPeriod }?.endTime ?: ""
        val span = if (c.endPeriod > c.startPeriod) "第${c.startPeriod}-${c.endPeriod}节" else "第${c.startPeriod}节"

        views.setTextViewText(R.id.item_name, c.name)
        views.setTextViewText(
            R.id.item_time,
            if (startTime.isNotBlank() || endTime.isNotBlank()) "$span  $startTime-$endTime" else span
        )
        val locText = buildString {
            if (c.location.isNotBlank()) append(c.location)
            if (c.teacher.isNotBlank()) append(" · ${c.teacher}")
        }
        views.setTextViewText(R.id.item_loc, locText)

        // 圆角色块背景（与 App 日视图卡片一致）
        val colorIdx = courseColorMap[c.name] ?: 0
        val bgRes = when (colorIdx % 8) {
            0 -> R.drawable.widget_course_card_indigo
            1 -> R.drawable.widget_course_card_blue
            2 -> R.drawable.widget_course_card_green
            3 -> R.drawable.widget_course_card_orange
            4 -> R.drawable.widget_course_card_purple
            5 -> R.drawable.widget_course_card_red
            6 -> R.drawable.widget_course_card_cyan
            else -> R.drawable.widget_course_card_brown
        }
        views.setInt(R.id.item_root, "setBackgroundResource", bgRes)
        WidgetTextSizes.apply(views, WidgetTextSizes.DAY_ROW, WidgetTextSizes.scale(context))

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 3

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    /** 组装静态卡片（考试在前 + 课程），供静态化小组件使用 */
    fun buildDayCards(): List<DayCard> {
        val cards = mutableListOf<DayCard>()
        for (e in dayExams) {
            cards.add(DayCard(e.name, e.time, e.loc, R.drawable.widget_exam_bg))
        }
        val periodTimes = ScheduleSettingsManager.getInstance().apply { init(context) }.getPeriodTimes()
        for (c in merged) {
            val startTime = periodTimes.firstOrNull { it.period == c.startPeriod }?.startTime ?: ""
            val endTime = periodTimes.firstOrNull { it.period == c.endPeriod }?.endTime ?: ""
            val span = if (c.endPeriod > c.startPeriod) "第${c.startPeriod}-${c.endPeriod}节" else "第${c.startPeriod}节"
            val timeText = if (startTime.isNotBlank() || endTime.isNotBlank()) "$span  $startTime-$endTime" else span
            val locText = buildString {
                if (c.location.isNotBlank()) append(c.location)
                if (c.teacher.isNotBlank()) append(" · ${c.teacher}")
            }
            val colorIdx = courseColorMap[c.name] ?: 0
            val bgRes = when (colorIdx % 8) {
                0 -> R.drawable.widget_course_card_indigo
                1 -> R.drawable.widget_course_card_blue
                2 -> R.drawable.widget_course_card_green
                3 -> R.drawable.widget_course_card_orange
                4 -> R.drawable.widget_course_card_purple
                5 -> R.drawable.widget_course_card_red
                6 -> R.drawable.widget_course_card_cyan
                else -> R.drawable.widget_course_card_brown
            }
            cards.add(DayCard(c.name, timeText, locText, bgRes))
        }
        return cards
    }
}

/** 静态化今日小组件的卡片数据 */
data class DayCard(
    val name: String,
    val time: String,
    val loc: String,
    val bgRes: Int
)

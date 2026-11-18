package com.buct.course.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.buct.course.R
import com.buct.course.manager.UserManager
import com.buct.course.schedule.NetworkScheduleCourse
import com.buct.course.schedule.ScheduleJson
import com.buct.course.schedule.ScheduleWeeks
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 桌面课表小组件的 RemoteViewsService
 */
class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduleRemoteViewsFactory(this.applicationContext, intent)
    }
}

class ScheduleRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    /** 当前要渲染的实例 id：读取该实例配置的账号/标题用。 */
    private val appWidgetId: Int = intent.getIntExtra(
        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
        android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
    )


    private val periodTimes = listOf(
        "08:00", "08:50", "09:45", "10:40", "11:30", "13:30",
        "14:20", "15:15", "16:05", "18:00", "18:50", "19:40"
    )

    // 开学日期：2026-09-01
    private val semesterStartDate = 1788192000000L

    // 课程颜色（更柔和的版本）
    private val courseColors = listOf(
        Color.parseColor("#805C6BC0"), // Indigo
        Color.parseColor("#8042A5F5"), // Blue
        Color.parseColor("#8066BB6A"), // Green
        Color.parseColor("#80FFA726"), // Orange
        Color.parseColor("#80AB47BC"), // Purple
        Color.parseColor("#80EF5350"), // Red
        Color.parseColor("#8026C6DA"), // Cyan
        Color.parseColor("#808D6E63")  // Brown
    )

    private var courses: List<NetworkScheduleCourse> = emptyList()
    private var courseColorMap = mutableMapOf<String, Int>()
    private var rowHasCourse = BooleanArray(12) { false }
    private var currentWeek = 1

    override fun onCreate() {}

    override fun onDataSetChanged() {
        try {
            UserManager.getInstance().init(context)
            // 来源优先级：实例配置的朋友课表 > 实例配置/当前账号的本机课表
            val scheduleJson = WidgetScheduleSource.loadFriendJson(context, appWidgetId)
                ?: loadOwnScheduleJson()

            if (scheduleJson != null) {
                courses = ScheduleJson.parse(scheduleJson) ?: emptyList()
                val courseNames = courses.map { it.course.name }.distinct()
                courseColorMap.clear()
                courseNames.forEachIndexed { index, name ->
                    courseColorMap[name] = index % courseColors.size
                }
            } else {
                courses = emptyList()
            }
            // 预构建「该节次是否有课」：空行可快速返回，避免每次逐格 find
            // 周数：24 小时内手动切过周则保留，否则自动回到今天所在周（与今日课表小组件统一）
            val widgetPrefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val lastNav = widgetPrefs.getLong("last_week_nav", 0L)
            val freshNav = System.currentTimeMillis() - lastNav < 86_400_000L
            val defaultWeek = getDefaultWeek()
            currentWeek = if (freshNav) {
                widgetPrefs.getInt("current_week", defaultWeek)
            } else {
                if (widgetPrefs.getInt("current_week", defaultWeek) != defaultWeek) {
                    widgetPrefs.edit()
                        .putInt("current_week", defaultWeek)
                        .putLong("last_week_nav", 0L)
                        .apply()
                }
                defaultWeek
            }
            rowHasCourse = BooleanArray(12) { false }
            for (course in courses) {
                val parsedWeeks = ScheduleWeeks.parse(course.course.weeks)
                if (parsedWeeks.visibleIn(currentWeek)) {
                    for (p in course.course.startPeriod..course.course.endPeriod) {
                        if (p in 1..12) rowHasCourse[p - 1] = true
                    }
                }
            }
        } catch (e: Exception) {
            courses = emptyList()
            rowHasCourse = BooleanArray(12) { false }
        }
    }

    override fun onDestroy() {}

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

    override fun getCount(): Int = 12

    override fun getViewAt(position: Int): RemoteViews {
        val period = position + 1
        var views = RemoteViews(context.packageName, R.layout.widget_schedule_row)

        // 读取 APP 里的主题设置
        val themePrefs = context.getSharedPreferences("appearance_settings", Context.MODE_PRIVATE)
        val themeMode = themePrefs.getString("theme_mode", "system")

        // 读取系统深色模式
        val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val systemDark = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES

        // 决定是否是深色模式
        val isDarkMode = when (themeMode) {
            "light" -> false
            "dark" -> true
            else -> systemDark // system 模式
        }

        val time = if (period - 1 < periodTimes.size) periodTimes[period - 1] else ""
        views.setTextViewText(R.id.item_period, "${period}\n${time}")

        // 根据模式设置文字颜色
        val textColor = if (isDarkMode) Color.WHITE else Color.BLACK
        views.setTextColor(R.id.item_period, textColor)

        // 空行快速路径：本行无课时只清空格子，跳过逐格查找，显著提速
        if (!rowHasCourse[position]) {
            for (day in 1..7) {
                val textViewId = when (day) {
                    1 -> R.id.item_monday
                    2 -> R.id.item_tuesday
                    3 -> R.id.item_wednesday
                    4 -> R.id.item_thursday
                    5 -> R.id.item_friday
                    6 -> R.id.item_saturday
                    7 -> R.id.item_sunday
                    else -> R.id.item_monday
                }
                views.setInt(textViewId, "setBackgroundColor", Color.TRANSPARENT)
                views.setTextViewText(textViewId, "")
            }
            WidgetTextSizes.apply(views, WidgetTextSizes.WEEK_ROW, WidgetTextSizes.scale(context))
            return views
        }

        // 布局选择：单节(cell全圆角) > 拼接首节(top上圆角+下溢) > 拼接末节(bottom下圆角+上溢) > 中间/空
        var layoutRes = R.layout.widget_schedule_row
        var hasCell = false
        var hasTop = false
        var hasBottom = false
        for (day in 1..7) {
            val probe = courses.find {
                val parsedWeeks = ScheduleWeeks.parse(it.course.weeks)
                it.course.day == day &&
                it.course.startPeriod <= period &&
                it.course.endPeriod >= period &&
                parsedWeeks.visibleIn(currentWeek)
            }
            if (probe != null) {
                if (probe.course.startPeriod == probe.course.endPeriod) hasCell = true
                else if (probe.course.startPeriod == period) hasTop = true
                else if (probe.course.endPeriod == period) hasBottom = true
            }
        }
        layoutRes = when {
            hasCell -> R.layout.widget_schedule_row_cell
            hasTop -> R.layout.widget_schedule_row_top
            hasBottom -> R.layout.widget_schedule_row_bottom
            else -> R.layout.widget_schedule_row
        }
        views = RemoteViews(context.packageName, layoutRes)
        // 布局重建后必须重新设置节次/时间（否则有课行左侧时间丢失）
        views.setTextViewText(R.id.item_period, "${period}\n${time}")
        views.setTextColor(R.id.item_period, textColor)

        for (day in 1..7) {
            val currentCourse = courses.find {
                val parsedWeeks = ScheduleWeeks.parse(it.course.weeks)
                it.course.day == day &&
                it.course.startPeriod <= period &&
                it.course.endPeriod >= period &&
                parsedWeeks.visibleIn(currentWeek)
            }

            val textViewId = when (day) {
                1 -> R.id.item_monday
                2 -> R.id.item_tuesday
                3 -> R.id.item_wednesday
                4 -> R.id.item_thursday
                5 -> R.id.item_friday
                6 -> R.id.item_saturday
                7 -> R.id.item_sunday
                else -> R.id.item_monday
            }

            if (currentCourse != null) {
                // 圆角拼接背景（与今日课表小组件同色板，单节全圆角，连续节次上/中/下拼接）
                val colorIdx = courseColorMap[currentCourse.course.name] ?: 0
                val start = currentCourse.course.startPeriod
                val end = currentCourse.course.endPeriod
                val bgRes = when {
                    start == end -> cellRes(colorIdx)
                    period == start -> topRes(colorIdx)
                    period == end -> bottomRes(colorIdx)
                    else -> middleRes(colorIdx)
                }
                views.setInt(textViewId, "setBackgroundResource", bgRes)

                // 文字分散到拼接块：起始格放课程名，第二格放地点，其余留空（视觉上占满整块）
                if (period == start) {
                    views.setTextViewText(textViewId, currentCourse.course.name)
                    views.setTextColor(textViewId, Color.WHITE)
                } else if (period == start + 1 && end >= start + 1) {
                    views.setTextViewText(textViewId, currentCourse.course.location)
                    views.setTextColor(textViewId, Color.WHITE)
                } else {
                    views.setTextViewText(textViewId, "")
                }
            } else {
                views.setInt(textViewId, "setBackgroundColor", Color.TRANSPARENT)
                views.setTextViewText(textViewId, "")
            }
        }

        val sizeMap = when (layoutRes) {
            R.layout.widget_schedule_row_cell -> WidgetTextSizes.WEEK_CELL
            R.layout.widget_schedule_row_top -> WidgetTextSizes.WEEK_ROW_TOP
            R.layout.widget_schedule_row_bottom -> WidgetTextSizes.WEEK_ROW_BOTTOM
            else -> WidgetTextSizes.WEEK_ROW
        }
        WidgetTextSizes.apply(views, sizeMap, WidgetTextSizes.scale(context))

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 4

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun cellRes(idx: Int): Int = when (idx % 8) {
        0 -> R.drawable.widget_course_cell_indigo
        1 -> R.drawable.widget_course_cell_blue
        2 -> R.drawable.widget_course_cell_green
        3 -> R.drawable.widget_course_cell_orange
        4 -> R.drawable.widget_course_cell_purple
        5 -> R.drawable.widget_course_cell_red
        6 -> R.drawable.widget_course_cell_cyan
        else -> R.drawable.widget_course_cell_brown
    }

    private fun topRes(idx: Int): Int = when (idx % 8) {
        0 -> R.drawable.widget_course_top_indigo
        1 -> R.drawable.widget_course_top_blue
        2 -> R.drawable.widget_course_top_green
        3 -> R.drawable.widget_course_top_orange
        4 -> R.drawable.widget_course_top_purple
        5 -> R.drawable.widget_course_top_red
        6 -> R.drawable.widget_course_top_cyan
        else -> R.drawable.widget_course_top_brown
    }

    private fun middleRes(idx: Int): Int = when (idx % 8) {
        0 -> R.drawable.widget_course_middle_indigo
        1 -> R.drawable.widget_course_middle_blue
        2 -> R.drawable.widget_course_middle_green
        3 -> R.drawable.widget_course_middle_orange
        4 -> R.drawable.widget_course_middle_purple
        5 -> R.drawable.widget_course_middle_red
        6 -> R.drawable.widget_course_middle_cyan
        else -> R.drawable.widget_course_middle_brown
    }

    private fun bottomRes(idx: Int): Int = when (idx % 8) {
        0 -> R.drawable.widget_course_bottom_indigo
        1 -> R.drawable.widget_course_bottom_blue
        2 -> R.drawable.widget_course_bottom_green
        3 -> R.drawable.widget_course_bottom_orange
        4 -> R.drawable.widget_course_bottom_purple
        5 -> R.drawable.widget_course_bottom_red
        6 -> R.drawable.widget_course_bottom_cyan
        else -> R.drawable.widget_course_bottom_brown
    }

    private fun getDefaultWeek(): Int {
        val now = System.currentTimeMillis()
        val diffMillis = now - semesterStartDate
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)
        return com.buct.course.schedule.ScheduleDates.weekNumber(diffDays, semesterStartDate)
    }
}

package com.buct.course.manager

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 课表设置管理器 - 管理节次时间、第一周日期、自定义课程
 */
class ScheduleSettingsManager internal constructor(private var prefs: SharedPreferences? = null) {
    var revision by mutableIntStateOf(0)
        private set
    
    companion object {
        private const val PREFS_NAME = "schedule_settings"
        private const val KEY_SEMESTER_START = "semester_start"
        private const val KEY_PERIOD_TIMES = "period_times"
        private const val KEY_CUSTOM_COURSES = "custom_courses"
        private const val KEY_PERIOD_COUNT = "period_count"
        private const val KEY_PERIOD_HEIGHT = "period_height"
        private const val KEY_SHOW_OTHER_WEEK = "show_other_week"
        private const val KEY_WIDGET_FONT_SCALE = "widget_font_scale"
        private const val KEY_WIDGET_TITLE = "widget_title"

        const val MIN_PERIOD_HEIGHT = 32
        const val MAX_PERIOD_HEIGHT = 112

        /** 小组件字号：无级范围与吸附档位。 */
        const val WIDGET_FONT_MIN = 0.7f
        const val WIDGET_FONT_MAX = 1.5f
        val WIDGET_FONT_SNAPS = listOf(0.8f, 0.9f, 1.0f, 1.15f, 1.3f)
        val WIDGET_FONT_SNAP_LABELS = listOf("小", "较小", "标准", "较大", "大")

        /** 七档预设，从紧凑到宽松（低段更密，避免小屏用户只能选到 64 以上）。 */
        val periodHeightPresets = listOf(32, 48, 64, 80, 96, 104, 112)
        
        @Volatile
        private var instance: ScheduleSettingsManager? = null
        
        fun getInstance(): ScheduleSettingsManager {
            return instance ?: synchronized(this) {
                instance ?: ScheduleSettingsManager().also { instance = it }
            }
        }
    }
    
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun accountStorageKey(): String {
        return UserManager.getInstance().currentAccountStorageKey.ifBlank { "default" }
    }

    private fun scopedKey(key: String): String {
        return "${key}_${accountStorageKey()}"
    }

    private fun getScopedInt(key: String, defaultValue: Int): Int {
        val scoped = scopedKey(key)
        val p = prefs ?: return defaultValue
        if (!p.contains(scoped) && p.contains(key)) {
            val value = p.getInt(key, defaultValue)
            p.edit().putInt(scoped, value).remove(key).apply()
            return value
        }
        return p.getInt(scoped, defaultValue)
    }

    private fun getScopedLong(key: String, defaultValue: Long): Long {
        val scoped = scopedKey(key)
        val p = prefs ?: return defaultValue
        if (!p.contains(scoped) && p.contains(key)) {
            val value = p.getLong(key, defaultValue)
            p.edit().putLong(scoped, value).remove(key).apply()
            return value
        }
        return p.getLong(scoped, defaultValue)
    }

    private fun getScopedString(key: String): String? {
        val scoped = scopedKey(key)
        val p = prefs ?: return null
        if (!p.contains(scoped) && p.contains(key)) {
            val value = p.getString(key, null)
            p.edit().putString(scoped, value).remove(key).apply()
            return value
        }
        return p.getString(scoped, null)
    }
    
    // ============ 节次数量 ============
    
    var periodCount: Int
        get() = getScopedInt(KEY_PERIOD_COUNT, 12)
        set(value) {
            prefs?.edit()?.putInt(scopedKey(KEY_PERIOD_COUNT), value)?.remove(KEY_PERIOD_COUNT)?.apply()
            revision++
        }

    // ============ 格子高度 ============

    /**
     * 课表每节课格子的基准高度（dp）。
     * 0 = 跟随屏幕默认（高屏 84、矮屏 68），与历史行为一致。
     */
    var periodHeight: Int
        get() = getScopedInt(KEY_PERIOD_HEIGHT, 0)
        set(value) {
            val normalized = value.coerceIn(MIN_PERIOD_HEIGHT, MAX_PERIOD_HEIGHT)
            prefs?.edit()?.putInt(scopedKey(KEY_PERIOD_HEIGHT), normalized)?.remove(KEY_PERIOD_HEIGHT)?.apply()
            revision++
        }

    fun resetPeriodHeight() {
        prefs?.edit()?.remove(scopedKey(KEY_PERIOD_HEIGHT))?.remove(KEY_PERIOD_HEIGHT)?.apply()
        revision++
    }

    // ============ 非本周课程 ============

    /** 课表是否显示「非本周」的课程（默认关闭，只显示当前周）。 */
    var showOtherWeekCourses: Boolean
        get() = prefs?.getBoolean(scopedKey(KEY_SHOW_OTHER_WEEK), false) ?: false
        set(value) {
            prefs?.edit()?.putBoolean(scopedKey(KEY_SHOW_OTHER_WEEK), value)?.remove(KEY_SHOW_OTHER_WEEK)?.apply()
            revision++
        }

    /** 桌面小组件的文字缩放（1.0 = 布局默认字号），供两种小组件共同读取。 */
    var widgetFontScale: Float
        get() = prefs?.getFloat(scopedKey(KEY_WIDGET_FONT_SCALE), 1f) ?: 1f
        set(value) {
            val clamped = value.coerceIn(WIDGET_FONT_MIN, WIDGET_FONT_MAX)
            if (widgetFontScale == clamped) return
            prefs?.edit()?.putFloat(scopedKey(KEY_WIDGET_FONT_SCALE), clamped)?.apply()
            revision++
        }

    /** 小组件全局默认标题（空 = 用「我的课表 / 今日课表」，不再拼接学员姓名）。 */
    var widgetTitle: String
        get() = prefs?.getString(KEY_WIDGET_TITLE, "") ?: ""
        set(value) {
            val trimmed = value.trim().take(12)
            if (widgetTitle == trimmed) return
            prefs?.edit()?.putString(KEY_WIDGET_TITLE, trimmed)?.apply()
            revision++
        }

    // ============ 第一周日期 ============
    
    var semesterStartDate: Long
        get() = getScopedLong(KEY_SEMESTER_START, 1788192000000L) // 2026-09-01 北化开学
        set(value) {
            if (semesterStartDate == value) return
            prefs?.edit()?.putLong(scopedKey(KEY_SEMESTER_START), value)?.remove(KEY_SEMESTER_START)?.apply()
            revision++
        }
    
    /**
     * 根据第一周日期计算当前是第几周（周一基准，与 App 课表周次口径一致）
     */
    fun calculateCurrentWeek(): Int {
        val startDate = semesterStartDate
        if (startDate == 0L) return 1
        
        val now = System.currentTimeMillis()
        val diffDays = (now - startDate) / (1000 * 60 * 60 * 24)
        val week = com.buct.course.schedule.ScheduleDates.weekNumber(diffDays, startDate)
        return week.coerceIn(1, 25)
    }
    
    /**
     * 获取学期开始日期的 Calendar 对象（用于 iCal 导出）
     */
    fun getSemesterStartCalendar(): Calendar {
        val calendar = Calendar.getInstance()
        val startDate = semesterStartDate
        if (startDate > 0L) {
            calendar.timeInMillis = startDate
        } else {
            // 默认使用当前学期的开始日期（假设9月第一个周一）
            calendar.set(Calendar.MONTH, Calendar.SEPTEMBER)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            // 找到第一个周一
            while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return calendar
    }
    
    // ============ 节次时间 ============
    
    data class PeriodTime(
        val period: Int,
        val startTime: String,
        val endTime: String
    )
    
    fun getPeriodTimes(): List<PeriodTime> {
        val json = getScopedString(KEY_PERIOD_TIMES)
        if (json != null) {
            try {
                val array = JSONArray(json)
                val list = mutableListOf<PeriodTime>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(PeriodTime(
                        period = obj.getInt("period"),
                        startTime = obj.getString("start"),
                        endTime = obj.getString("end")
                    ))
                }
                return list
            } catch (e: Exception) {
                // ignore
            }
        }
        return getDefaultPeriodTimes()
    }
    
    fun savePeriodTimes(times: List<PeriodTime>) {
        val array = JSONArray()
        times.forEach { pt ->
            val obj = JSONObject()
            obj.put("period", pt.period)
            obj.put("start", pt.startTime)
            obj.put("end", pt.endTime)
            array.put(obj)
        }
        prefs?.edit()
            ?.putString(scopedKey(KEY_PERIOD_TIMES), array.toString())
            ?.remove(KEY_PERIOD_TIMES)
            ?.apply()
    }
    
    fun getDefaultPeriodTimes(): List<PeriodTime> {
        return listOf(
            PeriodTime(1, "08:00", "08:45"),
            PeriodTime(2, "08:50", "09:35"),
            PeriodTime(3, "09:45", "10:30"),
            PeriodTime(4, "10:40", "11:25"),
            PeriodTime(5, "11:30", "12:15"),
            PeriodTime(6, "13:30", "14:15"),
            PeriodTime(7, "14:20", "15:05"),
            PeriodTime(8, "15:15", "16:00"),
            PeriodTime(9, "16:05", "16:50"),
            PeriodTime(10, "18:00", "18:45"),
            PeriodTime(11, "18:50", "19:35"),
            PeriodTime(12, "19:40", "20:25"),
        )
    }
    
    // ============ 自定义课程 ============
    
    data class CustomCourse(
        val id: String,
        val name: String,
        val location: String,
        val teacher: String,
        val day: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val weeks: String
    )
    
    private fun customCoursesKey(accountKey: String): String {
        return "${KEY_CUSTOM_COURSES}_$accountKey"
    }
    
    fun getCustomCourses(accountKey: String = accountStorageKey()): List<CustomCourse> {
        val scoped = customCoursesKey(accountKey)
        val json = prefs?.getString(scoped, null)
            ?: prefs?.getString(KEY_CUSTOM_COURSES, null)?.also { legacy ->
                prefs?.edit()
                    ?.putString(scoped, legacy)
                    ?.remove(KEY_CUSTOM_COURSES)
                    ?.apply()
            }
            ?: return emptyList()
        try {
            val array = JSONArray(json)
            val list = mutableListOf<CustomCourse>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(CustomCourse(
                    id = obj.optString("id", ""),
                    name = obj.getString("name"),
                    location = obj.optString("location", ""),
                    teacher = obj.optString("teacher", ""),
                    day = obj.getInt("day"),
                    startPeriod = obj.getInt("startPeriod"),
                    endPeriod = obj.getInt("endPeriod"),
                    weeks = obj.optString("weeks", "1-16周")
                ))
            }
            val seen = mutableSetOf<String>()
            val repaired = list.map { course ->
                if (course.id.isNotBlank() && seen.add(course.id)) course
                else course.copy(id = java.util.UUID.randomUUID().toString()).also { seen.add(it.id) }
            }
            if (repaired != list) saveCustomCourses(repaired, accountKey)
            return repaired
        } catch (e: Exception) {
            return emptyList()
        }
    }
    
    fun addCustomCourse(course: CustomCourse, accountKey: String = accountStorageKey()) {
        updateCustomCourse(course, accountKey)
    }
    
    fun updateCustomCourse(course: CustomCourse, accountKey: String = accountStorageKey()) {
        require(accountKey.isNotBlank() && course.id.isNotBlank())
        val existing = getCustomCourses(accountKey)
        val courses = if (existing.any { it.id == course.id }) existing.map { if (it.id == course.id) course else it }
            else existing + course
        saveCustomCourses(courses, accountKey)
    }
    
    fun removeCustomCourse(courseId: String, accountKey: String = accountStorageKey()) {
        saveCustomCourses(getCustomCourses(accountKey).filter { it.id != courseId }, accountKey)
    }

    private fun saveCustomCourses(courses: List<CustomCourse>, accountKey: String) {
        val array = JSONArray()
        courses.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("location", c.location)
            obj.put("teacher", c.teacher)
            obj.put("day", c.day)
            obj.put("startPeriod", c.startPeriod)
            obj.put("endPeriod", c.endPeriod)
            obj.put("weeks", c.weeks)
            array.put(obj)
        }
        prefs?.edit()
            ?.putString(customCoursesKey(accountKey), array.toString())
            ?.remove(KEY_CUSTOM_COURSES)
            ?.apply()
        revision++
    }
}

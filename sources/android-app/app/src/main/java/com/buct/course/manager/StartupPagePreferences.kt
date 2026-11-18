package com.buct.course.manager

import android.content.Context
import android.content.SharedPreferences

enum class StartupPage(val route: String, val label: String) {
    Courses("courses", "课程"),
    Schedule("schedule", "课表"),
    /** @deprecated 抢课已并入「课程」页的二级目录，仅保留取值兼容老配置 */
    Grab("grab", "抢课"),
    Friends("friends", "好友"),
    Grades("grades", "成绩"),
    Settings("settings", "设置");

    companion object {
        fun decode(value: String?): StartupPage {
            // 老配置里启动页/底栏可能是 grab，统一落到「课程」（里面就有抢课二级页）
            if (value == Grab.route) return Courses
            return entries.firstOrNull { it.route == value } ?: Schedule
        }
    }
}

/** App preference shared across accounts; activity restoration still retains its current page. */
class StartupPagePreferences(private val preferences: SharedPreferences) {
    fun read(): StartupPage = StartupPage.decode(runCatching { preferences.getString(KEY, null) }.getOrNull())

    fun write(page: StartupPage) {
        preferences.edit().putString(KEY, page.route).apply()
    }

    companion object {
        private const val KEY = "startup_page"
        fun from(context: Context) = StartupPagePreferences(context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE))
    }
}

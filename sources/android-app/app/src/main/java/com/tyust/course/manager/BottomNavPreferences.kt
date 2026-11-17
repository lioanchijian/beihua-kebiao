package com.tyust.course.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 底部导航栏的显示与顺序。
 *
 * 两件事：
 * 1. 顺序 —— 用户可以把常用页拖到前面；
 * 2. 显示 —— 不用的页可以隐藏，底栏只保留需要的几个。
 *
 * 约束：「设置」永远可见。否则用户把设置隐藏后就没有入口再改回来，
 * 只能清数据或重装，这是不可恢复的死锁。
 */
object BottomNavPreferences {
    private const val PREFS_NAME = "bottom_nav"
    private const val KEY_ORDER = "order"
    private const val KEY_HIDDEN = "hidden"

    /** 默认顺序：课程、抢课、课表、成绩、设置（课表在中间，主要功能更顺手）。 */
    val defaultOrder: List<StartupPage> = listOf(
        StartupPage.Courses,
        StartupPage.Grab,
        StartupPage.Schedule,
        StartupPage.Grades,
        StartupPage.Settings
    )

    /** 变更计数：MainActivity 读它订阅配置变化。 */
    var revision by mutableIntStateOf(0)
        private set

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 完整顺序（含已隐藏项），保证包含全部 5 个页面。 */
    fun order(): List<StartupPage> {
        val raw = prefs?.getString(KEY_ORDER, null)
        val saved = raw?.split(',')?.mapNotNull { name ->
            StartupPage.entries.firstOrNull { it.name == name.trim() }
        }.orEmpty()
        val cleaned = saved.distinct().toMutableList()
        defaultOrder.forEach { if (it !in cleaned) cleaned.add(it) }
        return cleaned
    }

    fun hidden(): Set<StartupPage> {
        val raw = prefs?.getString(KEY_HIDDEN, null) ?: return emptySet()
        return raw.split(',').mapNotNull { name ->
            StartupPage.entries.firstOrNull { it.name == name.trim() }
        }.toSet() - StartupPage.Settings
    }

    fun isVisible(page: StartupPage): Boolean =
        page == StartupPage.Settings || page !in hidden()

    /** 底栏实际渲染的页面，按用户顺序。 */
    fun visiblePages(): List<StartupPage> = order().filter { isVisible(it) }

    fun setVisible(page: StartupPage, visible: Boolean) {
        // 设置页不可隐藏：隐藏后没有任何入口能改回来
        if (page == StartupPage.Settings) return
        val current = hidden().toMutableSet()
        if (visible) current.remove(page) else current.add(page)
        prefs?.edit()?.putString(KEY_HIDDEN, current.joinToString(",") { it.name })?.apply()
        revision++
    }

    /** 在完整顺序里上移 / 下移一位；delta 为 -1 上移、+1 下移。 */
    fun move(page: StartupPage, delta: Int) {
        val list = order().toMutableList()
        val index = list.indexOf(page)
        if (index < 0) return
        val target = (index + delta).coerceIn(0, list.lastIndex)
        if (target == index) return
        list.removeAt(index)
        list.add(target, page)
        prefs?.edit()?.putString(KEY_ORDER, list.joinToString(",") { it.name })?.apply()
        revision++
    }

    fun reset() {
        prefs?.edit()?.remove(KEY_ORDER)?.remove(KEY_HIDDEN)?.apply()
        revision++
    }
}

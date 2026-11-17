package com.tyust.course.widget

import android.content.Context
import com.tyust.course.manager.ScheduleSettingsManager

/**
 * 桌面小组件按 appWidgetId 保存的实例级配置：
 * - 标题（实例自定义 → 全局默认 → 兜底文案）
 * - 显示账号（9.13 配置页可选；空 = 跟随当前账号）
 * - 来源（9.15 预留：本机账号 / 朋友课表）
 *
 * 全部存在独立的 widget_config SharedPreferences，删除实例时按 id 清理，避免 id 复用串配置。
 */
object WidgetConfigStore {
    private const val PREFS = "widget_config"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun title(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("title_$appWidgetId", null)?.takeIf { it.isNotBlank() }

    fun setTitle(context: Context, appWidgetId: Int, value: String?) {
        prefs(context).edit().apply {
            if (value.isNullOrBlank()) remove("title_$appWidgetId")
            else putString("title_$appWidgetId", value.trim())
        }.apply()
    }

    fun accountKey(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("account_$appWidgetId", null)?.takeIf { it.isNotBlank() }

    fun setAccount(context: Context, appWidgetId: Int, value: String?) {
        prefs(context).edit().apply {
            if (value.isNullOrBlank()) remove("account_$appWidgetId")
            else putString("account_$appWidgetId", value)
        }.apply()
    }

    /** 9.15 预留：课表来源（本机账号 / 朋友课表 id）。 */
    fun source(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("source_$appWidgetId", null)

    fun setSource(context: Context, appWidgetId: Int, value: String?) {
        prefs(context).edit().apply {
            if (value.isNullOrBlank()) remove("source_$appWidgetId")
            else putString("source_$appWidgetId", value)
        }.apply()
    }

    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove("title_$appWidgetId")
            .remove("account_$appWidgetId")
            .remove("source_$appWidgetId")
            .apply()
    }

    /** 回收桌面上已删除实例的配置，避免 appWidgetId 被系统复用时串配置。 */
    fun prune(context: Context, aliveIds: Set<Int>) {
        val p = prefs(context)
        val stale = p.all.keys.mapNotNull { key ->
            key.substringAfterLast('_', "").toIntOrNull()?.takeIf { it !in aliveIds }?.let { key }
        }
        if (stale.isEmpty()) return
        p.edit().apply { stale.forEach { remove(it) } }.apply()
    }

    /** 实例标题解析：实例自定义 → 全局默认 → 兜底「今日课表 / 我的课表」。 */
    fun resolveTitle(context: Context, appWidgetId: Int, isDay: Boolean): String {
        title(context, appWidgetId)?.let { return it }
        val global = runCatching {
            ScheduleSettingsManager.getInstance().apply { init(context) }.widgetTitle
        }.getOrNull()
        if (!global.isNullOrBlank()) return global
        return if (isDay) "今日课表" else "我的课表"
    }
}

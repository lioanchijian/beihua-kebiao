package com.buct.course.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 小组件 provider 统一入口。
 *
 * 桌面上实际注册的是**旧包名**的两个兼容壳（`com.tyust.course.widget.*`）——
 * 老用户已经添加的实例绑的就是这个全名，不能改。新代码（配置页、管理页、刷新广播）
 * 必须同时认识新旧两套组件，否则会出现"组件在桌面上、App 里却看不到"的割裂。
 */
object WidgetProviders {

    private const val LEGACY_WEEK = "com.tyust.course.widget.ScheduleWidget"
    private const val LEGACY_DAY = "com.tyust.course.widget.ScheduleDayWidget"

    fun week(context: Context): List<ComponentName> = listOf(
        ComponentName(context, ScheduleWidget::class.java),
        ComponentName(context.packageName, LEGACY_WEEK)
    )

    fun day(context: Context): List<ComponentName> = listOf(
        ComponentName(context, ScheduleDayWidget::class.java),
        ComponentName(context.packageName, LEGACY_DAY)
    )

    fun all(context: Context): List<ComponentName> = week(context) + day(context)

    fun isDayComponent(context: Context, component: ComponentName): Boolean =
        day(context).any { it == component }

    /** 指定一组组件上已添加实例的 id。 */
    fun idsFor(components: List<ComponentName>, manager: AppWidgetManager): IntArray =
        components.flatMap { component ->
            runCatching { manager.getAppWidgetIds(component).toList() }.getOrDefault(emptyList())
        }.distinct().toIntArray()

    /** 所有已添加实例的 id（含旧组件）。 */
    fun allIds(context: Context, manager: AppWidgetManager): IntArray = idsFor(all(context), manager)

    fun weekIds(context: Context, manager: AppWidgetManager): IntArray = idsFor(week(context), manager)

    fun dayIds(context: Context, manager: AppWidgetManager): IntArray = idsFor(day(context), manager)

    /** 通知全部小组件刷新（含旧组件名）。 */
    fun refreshAll(context: Context) {
        all(context).forEach { component ->
            runCatching {
                context.sendBroadcast(
                    Intent().setComponent(component).setAction(ScheduleWidget.ACTION_REFRESH)
                )
            }
        }
    }
}

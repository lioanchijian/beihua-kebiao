package com.buct.course.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.buct.course.R

/**
 * 桌面课表小组件
 */
open class ScheduleWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_PREV_WEEK || intent.action == ACTION_NEXT_WEEK) {
            val widgetPrefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            var currentWeek = widgetPrefs.getInt("current_week", getDefaultWeek())

            if (intent.action == ACTION_PREV_WEEK) {
                currentWeek = (currentWeek - 1).coerceAtLeast(1)
            } else {
                currentWeek += 1
            }

            widgetPrefs.edit()
                .putInt("current_week", currentWeek)
                .putLong("last_week_nav", System.currentTimeMillis())
                .apply()

            // 增量刷新：标题用 partialUpdate 局部更新，列表用 notify 让 Service 重读周数，
            // 不再整卡重建（避免切换缓慢 + 按钮 PendingIntent 被重置导致点击不稳定）
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(intent.component)
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_schedule)
                views.setTextViewText(R.id.widget_week_info, weekInfoText(currentWeek))
                bindWeekHeader(views, currentWeek)
                WidgetTextSizes.apply(views, WidgetTextSizes.WEEK_ROOT, WidgetTextSizes.scale(context))
                appWidgetManager.partiallyUpdateAppWidget(intArrayOf(id), views)
            }
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        } else if (intent.action == "com.buct.course.widget.REFRESH_WIDGET") {
            // 主题变化，刷新所有小组件
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = WidgetProviders.weekIds(context, appWidgetManager)
            onUpdate(context, appWidgetManager, ids)

            // 通知 ListView 的 Service 刷新数据
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) {
            WidgetConfigStore.clear(context, id)
        }
    }

    companion object {
        const val ACTION_PREV_WEEK = "com.buct.course.widget.PREV_WEEK"
        const val ACTION_NEXT_WEEK = "com.buct.course.widget.NEXT_WEEK"
        const val ACTION_REFRESH = "com.buct.course.widget.REFRESH_WIDGET"

        /** 通知桌面所有课表小组件重读数据（朋友课表保存/删除后调用）。 */
        fun refreshAllWidgets(context: Context) {
            // 必须广播到「旧包名兼容壳 + 新包名」全部组件，
            // 否则桌面上那些老实例收不到刷新（组件名没变，一直收不到）。
            WidgetProviders.refreshAll(context)
        }

        private const val semesterStartDate = 1788192000000L

        private fun weekMonday(week: Int): java.util.Calendar =
            com.buct.course.schedule.ScheduleDates.mondayOfWeek(semesterStartDate).apply {
                add(java.util.Calendar.DAY_OF_YEAR, (week - 1) * 7)
            }

        private fun weekInfoText(week: Int): String {
            val monday = weekMonday(week)
            val sunday = (monday.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, 6)
            }
            val firstMonth = monday.get(java.util.Calendar.MONTH) + 1
            val lastMonth = sunday.get(java.util.Calendar.MONTH) + 1
            val monthText = if (firstMonth == lastMonth) "${firstMonth}月" else "${firstMonth}月/${lastMonth}月"
            return "第${week}周 · $monthText"
        }

        private fun bindWeekHeader(views: RemoteViews, week: Int) {
            val monday = weekMonday(week)
            val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
            val headerIds = listOf(
                R.id.widget_monday_header, R.id.widget_tuesday_header, R.id.widget_wednesday_header,
                R.id.widget_thursday_header, R.id.widget_friday_header, R.id.widget_saturday_header,
                R.id.widget_sunday_header
            )
            for (i in 0..6) {
                val dayCal = (monday.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.DAY_OF_YEAR, i)
                }
                views.setTextViewText(
                    headerIds[i],
                    "${weekdayLabels[i]}\n${dayCal.get(java.util.Calendar.MONTH) + 1}/${dayCal.get(java.util.Calendar.DAY_OF_MONTH)}"
                )
            }
        }

        private fun getDefaultWeek(): Int {
            val now = System.currentTimeMillis()
            val diffMillis = now - semesterStartDate
            val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)
            return com.buct.course.schedule.ScheduleDates.weekNumber(diffDays, semesterStartDate)
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)

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

            // 设置标题：实例自定义 → 全局默认 → 「我的课表」
            views.setTextViewText(
                R.id.widget_title,
                WidgetConfigStore.resolveTitle(context, appWidgetId, isDay = false)
            )

            // 根据模式设置文字颜色和背景色
            val textColor = if (isDarkMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            val bgColor = if (isDarkMode) android.graphics.Color.argb(180, 30, 30, 46) else android.graphics.Color.argb(180, 255, 255, 255)

            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)
            views.setTextColor(R.id.widget_title, textColor)
            views.setTextColor(R.id.widget_week_info, textColor)
            views.setTextColor(R.id.widget_prev_week, textColor)
            views.setTextColor(R.id.widget_next_week, textColor)

            // 设置星期表头的文字颜色
            views.setTextColor(R.id.widget_monday_header, textColor)
            views.setTextColor(R.id.widget_tuesday_header, textColor)
            views.setTextColor(R.id.widget_wednesday_header, textColor)
            views.setTextColor(R.id.widget_thursday_header, textColor)
            views.setTextColor(R.id.widget_friday_header, textColor)
            views.setTextColor(R.id.widget_saturday_header, textColor)
            views.setTextColor(R.id.widget_sunday_header, textColor)

            // 读取当前周数：24 小时内手动切过周则保留，否则自动回到今天所在周（与今日课表小组件统一）
            val widgetPrefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val lastNav = widgetPrefs.getLong("last_week_nav", 0L)
            val freshNav = System.currentTimeMillis() - lastNav < 86_400_000L
            val defaultWeek = getDefaultWeek()
            val currentWeek = if (freshNav) {
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
            views.setTextViewText(R.id.widget_week_info, weekInfoText(currentWeek))

            // 星期表头带上月份和日期，跨月时每一天仍然能看懂属于哪个月。
            bindWeekHeader(views, currentWeek)

            // 设置一周按钮点击事件
            val prevIntent = Intent(context, ScheduleWidget::class.java).apply {
                action = ACTION_PREV_WEEK
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context, 0, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_prev_week, prevPendingIntent)

            // 设置下一周按钮点击事件
            val nextIntent = Intent(context, ScheduleWidget::class.java).apply {
                action = ACTION_NEXT_WEEK
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 1, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_next_week, nextPendingIntent)

            // 设置 ListView 的 RemoteViewsService
            val serviceIntent = Intent(context, ScheduleWidgetService::class.java)
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)

            // 点击标题进「小组件配置」，点击列表项跳转到 app
            val configIntent = Intent(context, com.buct.course.ui.widget.WidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(com.buct.course.ui.widget.WidgetConfigActivity.EXTRA_IS_DAY, false)
            }
            val titleClickPendingIntent = PendingIntent.getActivity(
                context, appWidgetId + 2000, configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, titleClickPendingIntent)

            // 点击列表项跳转到 app
            val clickIntent = Intent(context, com.buct.course.MainActivity::class.java)
            val clickPendingIntent = PendingIntent.getActivity(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntent)
            WidgetTextSizes.apply(views, WidgetTextSizes.WEEK_ROOT, WidgetTextSizes.scale(context))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

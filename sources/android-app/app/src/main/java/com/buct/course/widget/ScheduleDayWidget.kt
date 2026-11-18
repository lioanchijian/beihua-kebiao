package com.buct.course.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.buct.course.R
import com.buct.course.manager.UserManager
import java.util.Calendar

/**
 * 桌面「今日课表」小组件：可左右切换查看任意一天（含考试），一键回到今天。
 * 静态卡片布局（weight 均分占满），根治 ListView 行渲染导致的顶部裁剪/底部留白/圆角切角。
 * 样式与周次小组件（ScheduleWidget）保持一致，自动跟随系统日期与深浅色主题。
 */
open class ScheduleDayWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_DAY_PREV = "com.buct.course.widget.DAY_PREV"
        const val ACTION_DAY_NEXT = "com.buct.course.widget.DAY_NEXT"
        const val ACTION_DAY_TODAY = "com.buct.course.widget.DAY_TODAY"
        private const val PREFS = "day_widget_prefs"
        private const val KEY_OFFSET = "day_offset"
        private const val semesterStartDate = 1788192000000L

        private val idCache = HashMap<String, Int>()

        private fun rid(context: Context, name: String): Int =
            idCache.getOrPut(name) { context.resources.getIdentifier(name, "id", context.packageName) }

        /** 今天所在周（与周次小组件默认计算一致） */
        private fun getDefaultWeek(): Int {
            val now = System.currentTimeMillis()
            val diffMillis = now - semesterStartDate
            val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)
            return com.buct.course.schedule.ScheduleDates.weekNumber(diffDays, semesterStartDate)
        }

        fun getDayOffset(context: Context): Int {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return p.getInt(KEY_OFFSET, 0)
        }

        /** 构建完整静态卡片视图（主题 + 日期 + 卡组） */
        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule_day)

            // 主题跟随：与周次小组件同一套规则
            val themePrefs = context.getSharedPreferences("appearance_settings", Context.MODE_PRIVATE)
            val themeMode = themePrefs.getString("theme_mode", "system")
            val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val systemDark = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDarkMode = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }

            UserManager.getInstance().init(context)
            views.setTextViewText(
                R.id.widget_title,
                WidgetConfigStore.resolveTitle(context, appWidgetId, isDay = true)
            )

            val textColor = if (isDarkMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            val bgColor = if (isDarkMode) android.graphics.Color.argb(180, 30, 30, 46) else android.graphics.Color.argb(180, 255, 255, 255)
            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)
            views.setTextColor(R.id.widget_title, textColor)
            views.setTextColor(R.id.widget_day_info, textColor)
            views.setTextColor(R.id.widget_prev_day, textColor)
            views.setTextColor(R.id.widget_next_day, textColor)
            views.setTextColor(R.id.widget_back_today, textColor)

            // 所选日期：今天 + offset（无限前后切换）
            val offset = getDayOffset(context)
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, offset)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val weekdayIndex = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1 // 周一=1..周日=7
            val weekdayText = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[weekdayIndex - 1]
            val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(cal.timeInMillis - semesterStartDate)
            val week = com.buct.course.schedule.ScheduleDates.weekNumber(diffDays, semesterStartDate)
            views.setTextViewText(R.id.widget_day_info, "${month}月${day}日 · $weekdayText · 第${week}周")

            // 回到今天按钮高亮（非今天时显示）
            views.setTextViewText(R.id.widget_back_today, if (offset == 0) "今天" else "回到今天")

            // 导航按钮
            fun pending(action: String, requestCode: Int): PendingIntent {
                val intent = Intent(context, ScheduleDayWidget::class.java).setAction(action)
                return PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            views.setOnClickPendingIntent(R.id.widget_prev_day, pending(ACTION_DAY_PREV, 11))
            views.setOnClickPendingIntent(R.id.widget_next_day, pending(ACTION_DAY_NEXT, 12))
            views.setOnClickPendingIntent(R.id.widget_back_today, pending(ACTION_DAY_TODAY, 13))

            // 静态卡片：按当天课程/考试数量切换卡组（weight 均分占满）
            // 把实例 id 传进去：工厂要按这个 id 读实例配置的账号
            val factory = ScheduleDayRemoteViewsFactory(
                context,
                Intent().putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            factory.onDataSetChanged()
            val cards = factory.buildDayCards()
            val n = cards.size
            if (n == 0) {
                // 空态：单卡组，提示文本
                for (g in 1..6) {
                    views.setViewVisibility(rid(context, "day_cards_$g"), if (g == 1) View.VISIBLE else View.GONE)
                }
                views.setInt(rid(context, "dc1_card1_root"), "setBackgroundResource", R.drawable.widget_exam_bg)
                views.setTextViewText(rid(context, "dc1_card1_name"), "当天没有课程安排")
                views.setTextViewText(rid(context, "dc1_card1_time"), "")
                views.setTextViewText(rid(context, "dc1_card1_loc"), "")
            } else {
                val shown = n.coerceAtMost(6)
                for (g in 1..6) {
                    views.setViewVisibility(rid(context, "day_cards_$g"), if (g == shown) View.VISIBLE else View.GONE)
                }
                // 按当天卡片数量动态缩放字号/内边距，避免内容被卡片高度吞掉
                val nameSize = when { shown <= 2 -> 14f; shown == 3 -> 13f; shown == 4 -> 12f; else -> 11f }
                val timeSize = when { shown <= 2 -> 12f; shown == 3 -> 11f; shown == 4 -> 10f; else -> 9f }
                val locSize = when { shown <= 2 -> 11f; shown == 3 -> 10f; shown == 4 -> 9f; else -> 8f }
                val cardPad = if (shown >= 5) 4 else if (shown == 4) 6 else 10
                for (i in 0 until shown) {
                    val card = cards[i]
                    val p = "dc${shown}_card${i + 1}"
                    views.setTextViewText(rid(context, "${p}_name"), card.name)
                    views.setTextViewText(rid(context, "${p}_time"), card.time)
                    views.setTextViewText(rid(context, "${p}_loc"), card.loc)
                    views.setInt(rid(context, "${p}_root"), "setBackgroundResource", card.bgRes)
                    views.setTextViewTextSize(rid(context, "${p}_name"), android.util.TypedValue.COMPLEX_UNIT_SP, nameSize)
                    views.setTextViewTextSize(rid(context, "${p}_time"), android.util.TypedValue.COMPLEX_UNIT_SP, timeSize)
                    views.setTextViewTextSize(rid(context, "${p}_loc"), android.util.TypedValue.COMPLEX_UNIT_SP, locSize)
                    views.setViewPadding(
                        rid(context, "${p}_root"), cardPad, (cardPad * 0.6f).toInt(),
                        cardPad, (cardPad * 0.6f).toInt()
                    )
                }
            }

            // 点击卡片/标题跳转到 app；标题单独进「小组件配置」
            val clickIntent = Intent(context, com.buct.course.MainActivity::class.java)
            val clickPendingIntent = PendingIntent.getActivity(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val configIntent = Intent(context, com.buct.course.ui.widget.WidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(com.buct.course.ui.widget.WidgetConfigActivity.EXTRA_IS_DAY, true)
            }
            val configPendingIntent = PendingIntent.getActivity(
                context, appWidgetId + 2000, configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, configPendingIntent)
            for (g in 1..6) {
                for (k in 1..g) {
                    views.setOnClickPendingIntent(rid(context, "dc${g}_card${k}_root"), clickPendingIntent)
                }
            }

            WidgetTextSizes.apply(views, WidgetTextSizes.DAY_ROOT, WidgetTextSizes.scale(context))
            return views
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = buildViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

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
        when (intent.action) {
            ACTION_DAY_PREV, ACTION_DAY_NEXT, ACTION_DAY_TODAY -> {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val current = prefs.getInt(KEY_OFFSET, 0)
                val next = when (intent.action) {
                    ACTION_DAY_PREV -> current - 1
                    ACTION_DAY_NEXT -> current + 1
                    else -> 0
                }
                prefs.edit().putInt(KEY_OFFSET, next).apply()
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = WidgetProviders.dayIds(context, appWidgetManager)
                // 增量刷新：静态视图整体 partial 更新（日期 + 卡组 + 卡片内容）
                for (id in ids) {
                    val views = buildViews(context, id)
                    appWidgetManager.partiallyUpdateAppWidget(intArrayOf(id), views)
                }
            }
            "com.buct.course.widget.REFRESH_WIDGET" -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = WidgetProviders.dayIds(context, appWidgetManager)
                onUpdate(context, appWidgetManager, ids)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) {
            WidgetConfigStore.clear(context, id)
        }
    }
}

package com.buct.course.widget

import android.content.Context
import android.widget.RemoteViews
import com.buct.course.R
import com.buct.course.manager.ScheduleSettingsManager

/**
 * 小组件字号缩放。
 *
 * RemoteViews 不能改样式表，只能在构建视图时对每个 TextView 显式调
 * `setTextSize`。这些 id 和基准字号是从 res/layout 的 widget_*.xml 生成的
 * （tools/widget_text_maps.txt），布局改字号时记得同步重新生成。
 */
object WidgetTextSizes {

    fun scale(context: Context): Float =
        ScheduleSettingsManager.getInstance().apply { init(context) }.widgetFontScale

    fun apply(views: RemoteViews, sizes: Map<Int, Float>, scale: Float) {
        // 不能在 scale==1f 时跳过：小组件走局部更新，视图树里可能还留着上一次
        // 放大后的字号，只有显式 setTextSize 才能把它们改回默认，否则「恢复默认」不生效
        sizes.forEach { (id, base) ->
            runCatching { views.setFloat(id, "setTextSize", base * scale) }
        }
    }

    /** widget_schedule_day.xml */
    val DAY_ROOT: Map<Int, Float> = mapOf(
        R.id.widget_title to 16.0f,
        R.id.widget_day_info to 12.0f,
        R.id.widget_prev_day to 14.0f,
        R.id.widget_back_today to 11.0f,
        R.id.widget_next_day to 14.0f,
        R.id.dc1_card1_name to 14.0f,
        R.id.dc1_card1_time to 12.0f,
        R.id.dc1_card1_loc to 11.0f,
        R.id.dc2_card1_name to 14.0f,
        R.id.dc2_card1_time to 12.0f,
        R.id.dc2_card1_loc to 11.0f,
        R.id.dc2_card2_name to 14.0f,
        R.id.dc2_card2_time to 12.0f,
        R.id.dc2_card2_loc to 11.0f,
        R.id.dc3_card1_name to 14.0f,
        R.id.dc3_card1_time to 12.0f,
        R.id.dc3_card1_loc to 11.0f,
        R.id.dc3_card2_name to 14.0f,
        R.id.dc3_card2_time to 12.0f,
        R.id.dc3_card2_loc to 11.0f,
        R.id.dc3_card3_name to 14.0f,
        R.id.dc3_card3_time to 12.0f,
        R.id.dc3_card3_loc to 11.0f,
        R.id.dc4_card1_name to 14.0f,
        R.id.dc4_card1_time to 12.0f,
        R.id.dc4_card1_loc to 11.0f,
        R.id.dc4_card2_name to 14.0f,
        R.id.dc4_card2_time to 12.0f,
        R.id.dc4_card2_loc to 11.0f,
        R.id.dc4_card3_name to 14.0f,
        R.id.dc4_card3_time to 12.0f,
        R.id.dc4_card3_loc to 11.0f,
        R.id.dc4_card4_name to 14.0f,
        R.id.dc4_card4_time to 12.0f,
        R.id.dc4_card4_loc to 11.0f,
        R.id.dc5_card1_name to 14.0f,
        R.id.dc5_card1_time to 12.0f,
        R.id.dc5_card1_loc to 11.0f,
        R.id.dc5_card2_name to 14.0f,
        R.id.dc5_card2_time to 12.0f,
        R.id.dc5_card2_loc to 11.0f,
        R.id.dc5_card3_name to 14.0f,
        R.id.dc5_card3_time to 12.0f,
        R.id.dc5_card3_loc to 11.0f,
        R.id.dc5_card4_name to 14.0f,
        R.id.dc5_card4_time to 12.0f,
        R.id.dc5_card4_loc to 11.0f,
        R.id.dc5_card5_name to 14.0f,
        R.id.dc5_card5_time to 12.0f,
        R.id.dc5_card5_loc to 11.0f,
        R.id.dc6_card1_name to 14.0f,
        R.id.dc6_card1_time to 12.0f,
        R.id.dc6_card1_loc to 11.0f,
        R.id.dc6_card2_name to 14.0f,
        R.id.dc6_card2_time to 12.0f,
        R.id.dc6_card2_loc to 11.0f,
        R.id.dc6_card3_name to 14.0f,
        R.id.dc6_card3_time to 12.0f,
        R.id.dc6_card3_loc to 11.0f,
        R.id.dc6_card4_name to 14.0f,
        R.id.dc6_card4_time to 12.0f,
        R.id.dc6_card4_loc to 11.0f,
        R.id.dc6_card5_name to 14.0f,
        R.id.dc6_card5_time to 12.0f,
        R.id.dc6_card5_loc to 11.0f,
        R.id.dc6_card6_name to 14.0f,
        R.id.dc6_card6_time to 12.0f,
        R.id.dc6_card6_loc to 11.0f,
    )
    /** widget_schedule_day_row.xml */
    val DAY_ROW: Map<Int, Float> = mapOf(
        R.id.item_name to 14.0f,
        R.id.item_time to 11.0f,
        R.id.item_loc to 11.0f,
    )
    /** widget_schedule_exam_row.xml */
    val DAY_EXAM_ROW: Map<Int, Float> = mapOf(
        R.id.exam_name to 14.0f,
        R.id.exam_time to 12.0f,
        R.id.exam_loc to 11.0f,
    )
    /** widget_schedule.xml */
    val WEEK_ROOT: Map<Int, Float> = mapOf(
        R.id.widget_title to 16.0f,
        R.id.widget_monday_header to 10.0f,
        R.id.widget_tuesday_header to 10.0f,
        R.id.widget_wednesday_header to 10.0f,
        R.id.widget_thursday_header to 10.0f,
        R.id.widget_friday_header to 10.0f,
        R.id.widget_saturday_header to 10.0f,
        R.id.widget_sunday_header to 10.0f,
        R.id.widget_prev_week to 16.0f,
        R.id.widget_week_info to 12.0f,
        R.id.widget_next_week to 16.0f,
    )
    /** widget_schedule_row.xml */
    val WEEK_ROW: Map<Int, Float> = mapOf(
        R.id.item_period to 10.0f,
        R.id.item_monday to 9.0f,
        R.id.item_tuesday to 9.0f,
        R.id.item_wednesday to 9.0f,
        R.id.item_thursday to 9.0f,
        R.id.item_friday to 9.0f,
        R.id.item_saturday to 9.0f,
        R.id.item_sunday to 9.0f,
    )
    /** widget_schedule_row_cell.xml */
    val WEEK_CELL: Map<Int, Float> = mapOf(
        R.id.item_period to 10.0f,
        R.id.item_monday to 9.0f,
        R.id.item_tuesday to 9.0f,
        R.id.item_wednesday to 9.0f,
        R.id.item_thursday to 9.0f,
        R.id.item_friday to 9.0f,
        R.id.item_saturday to 9.0f,
        R.id.item_sunday to 9.0f,
    )
    /** widget_schedule_row_top.xml */
    val WEEK_ROW_TOP: Map<Int, Float> = mapOf(
        R.id.item_period to 10.0f,
        R.id.item_monday to 9.0f,
        R.id.item_tuesday to 9.0f,
        R.id.item_wednesday to 9.0f,
        R.id.item_thursday to 9.0f,
        R.id.item_friday to 9.0f,
        R.id.item_saturday to 9.0f,
        R.id.item_sunday to 9.0f,
    )
    /** widget_schedule_row_bottom.xml */
    val WEEK_ROW_BOTTOM: Map<Int, Float> = mapOf(
        R.id.item_period to 10.0f,
        R.id.item_monday to 9.0f,
        R.id.item_tuesday to 9.0f,
        R.id.item_wednesday to 9.0f,
        R.id.item_thursday to 9.0f,
        R.id.item_friday to 9.0f,
        R.id.item_saturday to 9.0f,
        R.id.item_sunday to 9.0f,
    )
}

package com.buct.course.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * 全局字号缩放。
 *
 * 只改 Compose 的 [androidx.compose.ui.unit.LocalDensity.fontScale]，不动系统设置，
 * 所以影响范围仅限本应用：所有 `sp` 自动跟随，`dp` 尺寸（图标、间距、圆角）保持不变，
 * 布局不会被整体撑大。
 *
 * 桌面小组件走 RemoteViews，读不到这个值，需单独控制。
 */
object FontScaleManager {
    private const val PREFS_NAME = "font_scale_settings"
    private const val KEY_SCALE = "font_scale"

    const val MIN_SCALE = 0.85f
    const val MAX_SCALE = 1.30f

    /** 滑杆下方的五档吸附。 */
    val presets: List<FontScalePreset> = listOf(
        FontScalePreset(0.85f, "小"),
        FontScalePreset(1.00f, "标准"),
        FontScalePreset(1.10f, "稍大"),
        FontScalePreset(1.20f, "大"),
        FontScalePreset(1.30f, "特大")
    )

    var scale by mutableFloatStateOf(1f)
        private set

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        scale = p.getFloat(KEY_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /** 实时预览用：改状态但不落盘（滑杆拖动过程中）。 */
    fun preview(value: Float) {
        scale = value.coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /** 松手 / 点档位 / 输入框确认后落盘。 */
    fun commit(value: Float = scale) {
        val clamped = value.coerceIn(MIN_SCALE, MAX_SCALE)
        scale = clamped
        prefs?.edit()?.putFloat(KEY_SCALE, clamped)?.apply()
    }

    fun reset() = commit(1f)

    fun percentOf(value: Float = scale): Int = (value * 100f).roundToInt()

    fun fromPercent(percent: Int): Float = (percent / 100f).coerceIn(MIN_SCALE, MAX_SCALE)
}

data class FontScalePreset(val scale: Float, val label: String) {
    val percent: Int get() = (scale * 100f).roundToInt()
}

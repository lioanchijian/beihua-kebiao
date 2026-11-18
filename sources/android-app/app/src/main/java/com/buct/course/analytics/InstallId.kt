package com.buct.course.analytics

import android.content.Context
import java.util.UUID

/**
 * 本机的匿名安装标识。
 *
 * 只用于使用统计与匿名反馈，不含任何设备指纹、账号或学号信息。
 * 沿用 [UsageStatsReporter] 原来的 prefs 键，升级后老用户的 ID 不变。
 */
object InstallId {
    private const val PREFS = "usage_stats"
    private const val KEY_INSTALL_ID = "install_id"

    fun get(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_INSTALL_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, generated).apply()
        return generated
    }
}

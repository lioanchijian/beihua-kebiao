package com.buct.course.manager

import android.content.Context
import android.content.SharedPreferences

/**
 * 在线同步开关：默认开启。
 * 关闭时 App 只使用本地文件数据，不自动联网拉取，避免顶掉电脑/其他端的教务会话；
 * 打开后进入相应页面会自动从官网拉取最新数据并覆盖本地旧数据（用户自定义课程保留）。
 */
object SyncSettings {
    private const val PREFS = "sync_prefs"
    private const val KEY_ENABLED = "online_sync_enabled"

    fun enabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}

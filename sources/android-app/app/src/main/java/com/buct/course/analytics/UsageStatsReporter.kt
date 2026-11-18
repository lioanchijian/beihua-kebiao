package com.buct.course.analytics

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 匿名使用统计。
 *
 * 只发送随机安装 ID、版本号、平台和日期，不发送姓名、学号、账号、密码或 Cookie。
 * 用户可在设置中关闭；关闭后不再上报。
 */
object UsageStatsReporter {
    private const val TAG = "UsageStatsReporter"
    private const val PREFS = "usage_stats"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_LAST_PING_DAY = "last_ping_day"
    private const val PRIMARY_URL = "https://buctkb.cn/api/stats/ping"
    private const val FALLBACK_URL = "http://47.243.237.7/api/stats/ping"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun reportLaunch(context: Context) {
        if (!isEnabled(context)) return
        val preferences = prefs(context)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (preferences.getString(KEY_LAST_PING_DAY, null) == today) return

        val installId = preferences.getString(KEY_INSTALL_ID, null)
            ?: UUID.randomUUID().toString().also {
                preferences.edit().putString(KEY_INSTALL_ID, it).apply()
            }
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
        if (version.isBlank()) return

        val payload = JSONObject()
            .put("installId", installId)
            .put("version", version)
            .put("platform", "android")
            .toString()

        Thread {
            if (send(PRIMARY_URL, payload) || send(FALLBACK_URL, payload)) {
                preferences.edit().putString(KEY_LAST_PING_DAY, today).apply()
            }
        }.start()
    }

    private fun send(url: String, payload: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json; charset=utf-8")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.d(TAG, "统计上报失败: ${e.message}")
            false
        }
    }
}
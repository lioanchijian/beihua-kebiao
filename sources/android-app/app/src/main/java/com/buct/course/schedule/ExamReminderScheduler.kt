package com.buct.course.schedule

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.buct.course.R
import com.buct.course.manager.CustomExamStore
import org.json.JSONArray

/**
 * 自建考试的本地提醒。
 *
 * 复用与课程提醒相同的 AlarmManager 精确闹钟 + 通知通道；
 * 每次改动（新增/编辑/删除/开机）都整体重排一遍，避免残留闹钟。
 */
object ExamReminderScheduler {
    const val ACTION = "com.buct.course.action.EXAM_REMINDER"
    const val EXTRA_TITLE = "exam_title"
    const val EXTRA_START_AT = "exam_start_at"
    const val EXTRA_LOCATION = "exam_location"
    const val EXTRA_OFFSET = "exam_offset"

    private const val CHANNEL = "exam_reminders"
    private const val PREFS = "exam_reminder_schedule"
    private const val KEY_CODES = "scheduled_codes"

    fun reschedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        // 冷启动时 UserManager 可能还没初始化，先兜一次，否则会按 default 账号取到空列表
        runCatching { com.buct.course.manager.UserManager.getInstance().init(context) }
        cancelAll(context, alarmManager)

        val now = System.currentTimeMillis()
        val codes = mutableListOf<Int>()

        CustomExamStore.list(context).filterNot { it.hidden }.forEach { exam ->
            val base = if (exam.endAt > 0) exam.startAt else exam.startAt
            exam.remindOffsets.forEach { offset ->
                val triggerAt = base - offset * 60_000L
                if (triggerAt <= now) return@forEach
                val code = requestCode(exam.id, offset)
                val pending = pendingIntent(context, code, exam.title, exam.startAt, exam.location, offset)
                runCatching {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                    codes += code
                }
            }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODES, JSONArray(codes).toString())
            .apply()
    }

    private fun cancelAll(context: Context, alarmManager: AlarmManager) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CODES, null)
            ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val code = array.optInt(i)
                val intent = Intent(context, ExamReminderReceiver::class.java).setAction(ACTION)
                val pending = PendingIntent.getBroadcast(
                    context, code, intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pending != null) {
                    alarmManager.cancel(pending)
                    pending.cancel()
                }
            }
        }
    }

    private fun requestCode(id: String, offsetMinutes: Int): Int =
        (id.hashCode() * 31 + offsetMinutes) and 0x7FFFFFFF

    private fun pendingIntent(
        context: Context,
        code: Int,
        title: String,
        startAt: Long,
        location: String,
        offset: Int
    ): PendingIntent {
        val intent = Intent(context, ExamReminderReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_START_AT, startAt)
            putExtra(EXTRA_LOCATION, location)
            putExtra(EXTRA_OFFSET, offset)
        }
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun notifyNow(context: Context, title: String, startAt: Long, location: String, offset: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 &&
            manager.getNotificationChannel(CHANNEL) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "考试提醒", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val offsetText = when {
            offset % 1440 == 0 -> "${offset / 1440} 天后"
            offset % 60 == 0 -> "${offset / 60} 小时后"
            else -> "${offset} 分钟后"
        }
        val timeText = java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA)
            .format(java.util.Date(startAt))
        val body = buildString {
            append(timeText)
            append(" · ")
            append(offsetText)
            if (location.isNotBlank()) {
                append(" · ")
                append(location)
            }
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            startAt.toInt(),
            Intent(context, com.buct.course.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_course_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(startAt.toInt(), notification)
        } catch (_: SecurityException) {
        }
    }
}

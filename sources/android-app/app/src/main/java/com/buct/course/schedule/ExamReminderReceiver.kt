package com.buct.course.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buct.course.manager.UserManager

class ExamReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UserManager.getInstance().init(context)
        if (intent.action != ExamReminderScheduler.ACTION) {
            // 开机 / 应用更新 / 改时间：整体重排，避免闹钟丢失
            ExamReminderScheduler.reschedule(context)
            return
        }
        ExamReminderScheduler.notifyNow(
            context = context,
            title = intent.getStringExtra(ExamReminderScheduler.EXTRA_TITLE).orEmpty().ifBlank { "考试提醒" },
            startAt = intent.getLongExtra(ExamReminderScheduler.EXTRA_START_AT, 0L),
            location = intent.getStringExtra(ExamReminderScheduler.EXTRA_LOCATION).orEmpty(),
            offset = intent.getIntExtra(ExamReminderScheduler.EXTRA_OFFSET, 0)
        )
    }
}

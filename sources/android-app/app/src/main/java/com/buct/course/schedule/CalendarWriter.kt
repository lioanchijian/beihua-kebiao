package com.buct.course.schedule

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast

/**
 * 把考试写进系统日历。
 *
 * 默认走「打开系统日历并预填」这条路（[Intent.ACTION_INSERT]），不需要申请
 * READ/WRITE_CALENDAR 权限，也不会在用户不知情时改动日历。用户点保存才真正写入。
 */
object CalendarWriter {
    fun createEvent(
        context: Context,
        title: String,
        startAt: Long,
        endAt: Long,
        location: String,
        note: String
    ) {
        if (startAt <= 0) return
        val begin = startAt
        val end = if (endAt > begin) endAt else begin + 60 * 60 * 1000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            putExtra(CalendarContract.Events.DESCRIPTION, note)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "没有找到可用的日历应用", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "无法打开日历，请检查系统权限", Toast.LENGTH_SHORT).show()
        }
    }
}

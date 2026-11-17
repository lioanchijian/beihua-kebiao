package com.tyust.course

import android.app.Application
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.manager.AppThemeCoordinator
import com.tyust.course.manager.FontScaleManager
import com.tyust.course.ui.system.GlassRuntimeGuard

class CourseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GlassRuntimeGuard.initialize(this)
        AppearanceSettingsManager.initialize(this)
        FontScaleManager.initialize(this)
        com.tyust.course.manager.BottomNavPreferences.initialize(this)
        AppThemeCoordinator.initialize(this)
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName() else {
            getSystemService(android.app.ActivityManager::class.java).runningAppProcesses
                ?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        }
        if (processName == packageName) {
            com.tyust.course.analytics.UsageStatsReporter.reportLaunch(this)
            com.tyust.course.schedule.ScheduleReminderScheduler.get(this).start(this)
            // 自建考试的提醒：每次冷启动重排一遍，代价很小，能兜住漏掉的闹钟
            runCatching { com.tyust.course.schedule.ExamReminderScheduler.reschedule(this) }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppThemeCoordinator.configurationChanged()
    }
}

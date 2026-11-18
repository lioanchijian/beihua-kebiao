package com.buct.course

import android.app.Application
import com.buct.course.manager.AppearanceSettingsManager
import com.buct.course.manager.AppThemeCoordinator
import com.buct.course.manager.FontScaleManager
import com.buct.course.ui.system.GlassRuntimeGuard

class CourseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GlassRuntimeGuard.initialize(this)
        AppearanceSettingsManager.initialize(this)
        FontScaleManager.initialize(this)
        com.buct.course.manager.BottomNavPreferences.initialize(this)
        AppThemeCoordinator.initialize(this)
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName() else {
            getSystemService(android.app.ActivityManager::class.java).runningAppProcesses
                ?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        }
        if (processName == packageName) {
            com.buct.course.analytics.UsageStatsReporter.reportLaunch(this)
            com.buct.course.schedule.ScheduleReminderScheduler.get(this).start(this)
            // 自建考试的提醒：每次冷启动重排一遍，代价很小，能兜住漏掉的闹钟
            runCatching { com.buct.course.schedule.ExamReminderScheduler.reschedule(this) }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppThemeCoordinator.configurationChanged()
    }
}

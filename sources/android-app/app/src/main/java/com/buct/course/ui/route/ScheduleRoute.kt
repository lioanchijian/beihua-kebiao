package com.buct.course.ui.route

import com.buct.course.ui.system.GlassToaster
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.buct.course.schedule.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.buct.course.demo.DemoData
import com.buct.course.academic.AcademicGatewayFactory
import com.buct.course.academic.AcademicStudyBridge
import com.buct.course.manager.ScheduleSettingsManager
import com.buct.course.manager.UserManager
import com.buct.course.network.CourseApiClient
import com.buct.course.ui.screen.PeriodTimeUi
import com.buct.course.ui.screen.ScheduleCourseUi
import com.buct.course.ui.screen.ScheduleScreen
import com.buct.course.ui.screen.EmptyClassroomDialog
import com.buct.course.ui.screen.ScheduleSettingsScreen
import com.buct.course.ui.system.DisablePlatformDialogDim
import com.buct.course.ui.system.SystemDialog
import com.buct.course.ui.system.SystemPrimaryButton
import com.buct.course.ui.theme.MotionDuration
import com.buct.course.ui.theme.MotionEasing
import com.buct.course.ui.theme.MotionSpring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar
import com.buct.course.utils.ICalExporter

private const val ScheduleRouteSnapshotMaxAgeMs = 5 * 60 * 1000L

/** 保留课表数据状态，但页面 Composition 与玻璃图层仍在切走时立即释放。 */
private data class EmptyClassroomRequest(val week: Int, val day: Int, val period: Int)

private data class ScheduleRouteSnapshot(
    val savedAtMs: Long,
    val currentWeek: Int,
    val courses: List<ScheduleCourseUi>,
    val periodTimes: List<PeriodTimeUi>,
    val periodCount: Int,
    val semesterIndex: Int,
    val termId: String = "",
    val appliedCalendar: String? = null
)

private object ScheduleRouteMemoryCache {
    private val snapshots = mutableMapOf<String, ScheduleRouteSnapshot>()

    fun get(accountKey: String): ScheduleRouteSnapshot? = snapshots[accountKey]?.takeIf {
        System.currentTimeMillis() - it.savedAtMs <= ScheduleRouteSnapshotMaxAgeMs
    }

    fun put(accountKey: String, snapshot: ScheduleRouteSnapshot) {
        snapshots[accountKey] = snapshot
    }
}

@Composable
fun ScheduleRoute() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDemoMode = remember { UserManager.getInstance().isDemoMode }
    val routeAccountKey = remember { UserManager.getInstance().currentAccountStorageKey }
    val sessions = UserManager.getInstance().sessionState
    val session by sessions.state.collectAsState()
    val requests = remember { com.buct.course.manager.SessionRequestGate(sessions) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    // 选中的「朋友的课表」（本地文件 id），非空时课表页展示对方的课表（只读）
    var viewingFriendId by rememberSaveable { mutableStateOf<String?>(null) }
    val savedAccounts = remember(routeAccountKey, showAccountSwitcher) {
        runCatching { UserManager.getInstance().savedAccounts }.getOrNull().orEmpty()
    }
    val accountLabel = remember(routeAccountKey) {
        UserManager.getInstance().studentName?.takeIf { it.isNotBlank() } ?: "切换账号"
    }
    fun switchAccount(key: String) {
        showAccountSwitcher = false
        viewingFriendId = null
        val user = UserManager.getInstance()
        if (key == user.currentAccountKey) return
        // 切号前取消在途请求，避免旧账号的响应写进新账号的缓存
        requests.cancelAll()
        if (user.switchToAccount(key)) {
            com.buct.course.ui.system.GlassToaster.show("已切换账号")
        } else {
            com.buct.course.ui.system.GlassToaster.show("账号切换失败，请重新登录")
        }
    }
    DisposableEffect(requests) { onDispose { requests.cancelAll() } }
    val restoredSnapshot = remember(routeAccountKey) {
        ScheduleRouteMemoryCache.get(routeAccountKey)
    }
    var hasInitializedRoute by remember(routeAccountKey) {
        mutableStateOf(restoredSnapshot != null)
    }
    var skipFirstSemesterLoad by remember(routeAccountKey) {
        mutableStateOf(restoredSnapshot != null)
    }
    
    // State
    var currentWeek by rememberSaveable(routeAccountKey) {
        mutableIntStateOf(restoredSnapshot?.currentWeek ?: 1)
    }
    var courses by remember(routeAccountKey) {
        mutableStateOf(restoredSnapshot?.courses ?: emptyList())
    }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember(routeAccountKey) { mutableStateOf("") }
    var studyLoadJob by remember(routeAccountKey) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var studyGeneration by remember(routeAccountKey) { mutableIntStateOf(0) }
    var pendingForceRefreshAfterRenew by remember(routeAccountKey) { mutableStateOf(false) }
    var periodTimes by remember(routeAccountKey) {
        mutableStateOf(restoredSnapshot?.periodTimes ?: emptyList())
    }
    var periodCount by remember(routeAccountKey) {
        mutableIntStateOf(restoredSnapshot?.periodCount ?: 12)
    }
    var semesterIndex by rememberSaveable(routeAccountKey) {
        mutableIntStateOf(restoredSnapshot?.semesterIndex ?: 0)
    }
    // 课表视图：日视图（只看当天）/ 周视图（整周）
    var dayView by rememberSaveable(routeAccountKey) { mutableStateOf(false) }
    var selectedDay by rememberSaveable(routeAccountKey) { mutableIntStateOf(0) }
    
    // Dialog State
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable(routeAccountKey) { mutableStateOf<String?>(null) }
    var detailSourceBounds by remember(routeAccountKey) { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var emptyClassroomRequest by remember { mutableStateOf<EmptyClassroomRequest?>(null) }
    var editingId by rememberSaveable(routeAccountKey) { mutableStateOf<String?>(null) }
    var resolvedTermId by rememberSaveable(routeAccountKey) { mutableStateOf(restoredSnapshot?.termId.orEmpty()) }
    // 「发送课表」要带学期号：学期变化时同步进共享缓存（parseSchedule 里那份可能早于赋值执行）
    androidx.compose.runtime.LaunchedEffect(resolvedTermId) {
        com.buct.course.social.ScheduleShareCache.termLabel = resolvedTermId
    }
    var appliedCalendar by rememberSaveable(routeAccountKey) { mutableStateOf(restoredSnapshot?.appliedCalendar) }
    var notificationCourseJson by rememberSaveable(routeAccountKey) { mutableStateOf<String?>(null) }
    var settingsTermOverride by rememberSaveable(routeAccountKey) { mutableStateOf<String?>(null) }
    var deletedCourseJson by rememberSaveable(routeAccountKey) { mutableStateOf<String?>(null) }
    var deletedRemindersJson by rememberSaveable(routeAccountKey) { mutableStateOf("[]") }
    var undoDeadline by rememberSaveable(routeAccountKey) { mutableLongStateOf(0L) }
    
    // Managers
    val settingsManager = remember { ScheduleSettingsManager.getInstance().apply { init(context) } }
    val scheduleCache = remember(context) {
        ScheduleCacheStore(context, context.getSharedPreferences("schedule_cache", android.content.Context.MODE_PRIVATE))
    }
    val reminderScheduler = remember(context) { ScheduleReminderScheduler.get(context) }
    val customRevision = settingsManager.revision
    val customCourses = remember(customRevision, routeAccountKey) { settingsManager.getCustomCourses(routeAccountKey) }
    val remindersRevision = reminderScheduler.revision
    val settingsTerm = settingsTermOverride ?: resolvedTermId
    val termTimeBase = remember(settingsTerm, remindersRevision) { reminderScheduler.timeBase(routeAccountKey, settingsTerm) }
    val displayedTimeBase = remember(resolvedTermId, remindersRevision) { reminderScheduler.timeBase(routeAccountKey, resolvedTermId) }
    fun periodTimesFor(base: ScheduleTimeBase?): List<ScheduleSettingsManager.PeriodTime> = settingsManager.getPeriodTimes().map {
        it.copy(startTime = base?.periodStarts?.get(it.period) ?: it.startTime, endTime = base?.periodEnds?.get(it.period) ?: it.endTime)
    }
    val focusRegistry = remember { com.buct.course.ui.screen.ScheduleFocusRegistry() }
    val reminderRequest = CourseReminderNavigation.requestedId
    LaunchedEffect(reminderRequest) {
        if (reminderRequest != null) {
            detailSourceBounds = null
            reminderScheduler.findById(reminderRequest)?.let {
                notificationCourseJson = ReminderJson.reminder(it).toString()
                detailId = it.course.id
            }
            CourseReminderNavigation.consume()
        }
    }

    val snapshotForCache = ScheduleRouteSnapshot(
        savedAtMs = System.currentTimeMillis(),
        currentWeek = currentWeek,
        courses = courses,
        periodTimes = periodTimes,
        periodCount = periodCount,
        semesterIndex = semesterIndex,
        termId = resolvedTermId,
        appliedCalendar = appliedCalendar
    )
    val latestSnapshotForCache by rememberUpdatedState(snapshotForCache)
    val canCacheSnapshot by rememberUpdatedState(hasInitializedRoute && !isLoading && loadError.isBlank())
    DisposableEffect(routeAccountKey) {
        onDispose {
            if (canCacheSnapshot) {
                ScheduleRouteMemoryCache.put(routeAccountKey, latestSnapshotForCache)
            }
        }
    }
    
    // Colors
    val courseColors = remember {
        listOf(
            Color(0xFF5C6BC0), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFFFA726),
            Color(0xFFAB47BC), Color(0xFFEF5350), Color(0xFF26C6DA), Color(0xFF8D6E63)
        )
    }

    fun parseSchedule(json: String): List<ScheduleCourseUi>? = ScheduleJson.parse(json)?.map { entry ->
        val c = entry.course
        ScheduleCourseUi(c.name, c.teacher, c.location, c.day, c.startPeriod, c.endPeriod, c.weeks,
            courseColors[ScheduleIdentity.colorIndex(c.id, courseColors.size)], sourceId = entry.sourceId, id = c.id)
    }.also {
        // 顺手留一份原始 JSON，好友页「发送课表」直接用，不必重新联网拉
        if (it != null) {
            com.buct.course.social.ScheduleShareCache.rawJson = json
            com.buct.course.social.ScheduleShareCache.termLabel = resolvedTermId
        }
    }

    fun reloadCustomCourses(currentList: List<ScheduleCourseUi>): List<ScheduleCourseUi> {
        val customCourses = settingsManager.getCustomCourses(routeAccountKey)
        val customUi = customCourses.map { cc ->
            ScheduleCourseUi(
                name = cc.name, teacher = cc.teacher, location = cc.location, day = cc.day,
                startPeriod = cc.startPeriod, endPeriod = cc.endPeriod, weeks = cc.weeks,
                color = courseColors[ScheduleIdentity.colorIndex("custom:${cc.id}", courseColors.size)], isCustom = true, customId = cc.id
            )
        }
        val nonCustom = currentList.filter { !it.isCustom }
        return nonCustom + customUi
    }

    // ---- 朋友的课表：存在本机固定目录，切换账号面板里直接可选 ----
    val viewingFriendEntry = remember(viewingFriendId, showAccountSwitcher, routeAccountKey) {
        viewingFriendId?.let { com.buct.course.social.FriendScheduleStore.find(context, it) }
    }
    val friendCourses = remember(viewingFriendEntry) {
        viewingFriendEntry?.let { entry ->
            com.buct.course.social.FriendScheduleStore.readJson(entry)?.let { parseSchedule(it) }
        }.orEmpty()
    }
    val friendSchedulesForDialog = remember(showAccountSwitcher) {
        com.buct.course.social.FriendScheduleStore.list(context)
    }
    val displayCourses = if (viewingFriendEntry != null) friendCourses else courses
    val effectiveAccountLabel = viewingFriendEntry?.let { "${it.label}的课表" } ?: accountLabel

    // Load Schedule Function
    val loadSchedule = remember(semesterIndex, session.token) {
        fun(forceRefresh: Boolean) {
            if (isDemoMode) {
                resolvedTermId = when (semesterIndex) {
                1 -> DemoData.currentTerm.next().id
                2 -> DemoData.currentTerm.next().next().id
                else -> DemoData.currentTerm.id
            }
                courses = reloadCustomCourses(DemoData.scheduleCourses())
                isLoading = false
                return
            }
            val school = UserManager.getInstance().currentSchool
            if (school == null) return
            val ticket = requests.begin("schedule")
            studyLoadJob?.cancel()
            val generation = ++studyGeneration
            val account = UserManager.getInstance().currentAccountStorageKey
            val currentTerm = scheduleCache.currentTerm(account, school.id)
            val requestedTerm = when (semesterIndex) {
                1 -> currentTerm.next()
                2 -> currentTerm.next().next()
                else -> currentTerm
            }
            val cached = scheduleCache.selected(account, school.id, semesterIndex)
            loadError = ""
            val onlineSync = com.buct.course.manager.SyncSettings.enabled(context)
            // 在线同步关闭：手动刷新 → 提示并仅展示本地数据，不联网
            if (forceRefresh && !onlineSync) {
                if (cached != null) {
                    courses = reloadCustomCourses(requireNotNull(parseSchedule(cached.json)))
                    resolvedTermId = cached.term.id
                    reminderScheduler.updateSnapshot(routeAccountKey, cached.term.id, courses.map { it.record() })
                }
                isLoading = false
                com.buct.course.ui.system.GlassToaster.show("在线同步未开启，请到设置页打开「在线同步」后再刷新")
                return
            }
            if (!forceRefresh && cached != null) {
                courses = reloadCustomCourses(requireNotNull(parseSchedule(cached.json)))
                resolvedTermId = cached.term.id
                isLoading = false
                reminderScheduler.updateSnapshot(routeAccountKey, cached.term.id, courses.map { it.record() })
                return
            }
            // 在线同步关闭且无本地数据：不联网，提示开启
            if (!onlineSync && cached == null) {
                courses = reloadCustomCourses(emptyList())
                isLoading = false
                com.buct.course.ui.system.GlassToaster.show("暂无本地课表数据，请到设置页开启「在线同步」自动下载")
                return
            }
            // Refresh in place. Only a different semester must discard the previous rows.
            if (resolvedTermId != requestedTerm.id) courses = reloadCustomCourses(emptyList())
            val hasRetainedSchedule = cached != null || courses.isNotEmpty()
            isLoading = true

            if (AcademicGatewayFactory.supports(school)) {
                studyLoadJob = scope.launch {
                    try {
                        val loaded = withContext(Dispatchers.IO) {
                            scheduleCache.load(account, school.id, semesterIndex, forceRefresh) {
                                AcademicStudyBridge.reader(school, account, ticket.session)
                            }
                        }
                        if (!requests.isCurrent(ticket) || studyGeneration != generation) return@launch
                        scheduleCache.save(account, school.id, loaded)
                        courses = reloadCustomCourses(requireNotNull(parseSchedule(loaded.json)))
                        resolvedTermId = loaded.term.id
                        reminderScheduler.updateSnapshot(routeAccountKey, loaded.term.id, courses.map { it.record() })
                        if (forceRefresh) GlassToaster.show("已同步课表")
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (requests.isCurrent(ticket) && studyGeneration == generation) {
                            val sessionExpired = (e as? com.buct.course.academic.AcademicException)?.status ==
                                com.buct.course.academic.AcademicStatus.SESSION_EXPIRED
                            if (forceRefresh && sessionExpired && UserManager.getInstance().canAutoRelogin()) {
                                // 手动刷新时 Cookie 失效：用已保存密码静默续期，token 更新后自动重试刷新
                                pendingForceRefreshAfterRenew = true
                                com.buct.course.utils.SessionRenewer.request(ticket.session, manual = true) { result ->
                                    if (result !is com.buct.course.utils.SessionRecoveryResult.Recovered) {
                                        pendingForceRefreshAfterRenew = false
                                        if (requests.isCurrent(ticket) && studyGeneration == generation) {
                                            val message = if (result is com.buct.course.utils.SessionRecoveryResult.NeedsLogin) {
                                                "登录已失效，请重新登录"
                                            } else {
                                                "登录状态恢复失败，请重试"
                                            }
                                            if (hasRetainedSchedule) GlassToaster.show("同步失败，已保留本地课表：$message")
                                            else loadError = message
                                        }
                                    }
                                }
                            } else {
                                val message = e.message ?: "课表同步失败，请重试"
                                if (hasRetainedSchedule) GlassToaster.show("同步失败，已保留本地课表：$message")
                                else loadError = message
                            }
                        }
                    } finally {
                        if (requests.isCurrent(ticket) && studyGeneration == generation) isLoading = false
                    }
                }
                return
            }
            
            val xnm = requestedTerm.year.toString()
            val xqm = when (requestedTerm.semester) { 1 -> "3"; 2 -> "12"; 3 -> "16"; else -> "3" }
            fun isRequestAccountActive(): Boolean {
                return requests.isCurrent(ticket)
            }
            val requestTermId = requestedTerm.id
            resolvedTermId = requestTermId
            CourseApiClient.getInstance().fetchSchedule(school, "xnm=$xnm&xqm=$xqm", object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    scope.launch(Dispatchers.Main) {
                        if (!isRequestAccountActive()) return@launch
                        isLoading = false
                        if (hasRetainedSchedule) GlassToaster.show("同步失败，已保留本地课表")
                        else loadError = "加载失败：${e.message}"
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val json = response.body?.string() ?: ""
                    if (json.contains("用户登录") || response.code == 401 || response.code == 403) {
                        if (forceRefresh && UserManager.getInstance().canAutoRelogin()) {
                            pendingForceRefreshAfterRenew = true
                            com.buct.course.utils.SessionRenewer.request(ticket.session, manual = true) { result ->
                                if (result !is com.buct.course.utils.SessionRecoveryResult.Recovered) {
                                    pendingForceRefreshAfterRenew = false
                                    scope.launch(Dispatchers.Main) {
                                        if (!isRequestAccountActive()) return@launch
                                        isLoading = false
                                        if (!hasRetainedSchedule) loadError = "请先登录"
                                        GlassToaster.show("登录已失效，请重新登录")
                                    }
                                }
                            }
                        } else {
                            scope.launch(Dispatchers.Main) {
                                if (!isRequestAccountActive()) return@launch
                                isLoading = false
                                if (!hasRetainedSchedule) loadError = "请先登录"
                                GlassToaster.show("请先登录")
                            }
                        }
                        return
                    }
                    val parsed = if (response.isSuccessful) parseSchedule(json) else null
                    scope.launch(Dispatchers.Main) {
                        if (!isRequestAccountActive()) return@launch
                        isLoading = false
                        if (parsed != null) {
                            scheduleCache.save(account, school.id, CachedSchedule(currentTerm, requestedTerm, json, false))
                            courses = reloadCustomCourses(parsed)
                            reminderScheduler.updateSnapshot(routeAccountKey, requestTermId, courses.map { it.record() })
                            if (forceRefresh) GlassToaster.show("已刷新")
                        } else {
                            val message = "课表响应无效，请重试"
                            if (!hasRetainedSchedule) loadError = message
                            GlassToaster.show(message)
                        }
                    }
                }
            })
        }
    }

    // Init Effect
    LaunchedEffect(routeAccountKey) {
        if (restoredSnapshot == null) {
            periodCount = settingsManager.periodCount
            periodTimes = settingsManager.getPeriodTimes().map {
                PeriodTimeUi(it.period, it.startTime, it.endTime)
            }
        }
    }
    
    // 监听学期切换并重新加载
    LaunchedEffect(semesterIndex, session.token) {
        if (skipFirstSemesterLoad) {
            skipFirstSemesterLoad = false
            return@LaunchedEffect
        }
        hasInitializedRoute = true
        val force = pendingForceRefreshAfterRenew
        pendingForceRefreshAfterRenew = false
        loadSchedule(force)
    }

    // Refresh custom courses when dialogs close
    LaunchedEffect(customRevision, routeAccountKey) {
        courses = reloadCustomCourses(courses)
        periodCount = settingsManager.periodCount
    }
    LaunchedEffect(resolvedTermId) {
        if (resolvedTermId.isNotBlank() && semesterIndex == 0) {
            reminderScheduler.migrateLegacyTimeBase(routeAccountKey, resolvedTermId, ScheduleTimeBase(
                ScheduleTimeBase.dateFromMillis(settingsManager.semesterStartDate), periodTimes.associate { it.period to it.startTime },
                periodTimes.associate { it.period to it.endTime }))
        }
    }
    LaunchedEffect(displayedTimeBase) {
        periodTimes = periodTimesFor(displayedTimeBase).map { PeriodTimeUi(it.period, it.startTime, it.endTime) }
    }
    LaunchedEffect(resolvedTermId, displayedTimeBase?.firstWeekDate) {
        if (resolvedTermId.isBlank()) return@LaunchedEffect
        val calendar = "$resolvedTermId|${displayedTimeBase?.firstWeekDate.orEmpty()}"
        // Apply date changes when saved, including system-back dismissal. Retain a
        // browsed week across page restoration and unrelated reminder/time changes.
        if (appliedCalendar != calendar) {
            appliedCalendar = calendar
            currentWeek = ScheduleDates.weekAt(displayedTimeBase?.firstWeekDate, System.currentTimeMillis()) ?: 1
        }
    }
    LaunchedEffect(undoDeadline) {
        if (undoDeadline > 0) {
            kotlinx.coroutines.delay((undoDeadline - System.currentTimeMillis()).coerceAtLeast(0))
            deletedCourseJson = null
            deletedRemindersJson = "[]"
        }
    }
    com.buct.course.ui.system.ReportPageContent(courses.isNotEmpty())
    // 日视图所需的「今天」参考点
    val todayCalendar = remember { Calendar.getInstance() }
    val todayWeekday = remember { ((todayCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1 }
    val todayWeekNumber = displayedTimeBase?.firstWeekDate?.let {
        com.buct.course.schedule.ScheduleDates.weekAt(it, System.currentTimeMillis())
    } ?: 1
    Box(Modifier.fillMaxSize()) {
    CompositionLocalProvider(com.buct.course.ui.screen.LocalScheduleFocus provides focusRegistry) {
    ScheduleScreen(
        currentWeek = currentWeek,
        courses = displayCourses,
        isLoading = if (viewingFriendEntry != null) false else isLoading,
        errorMessage = if (viewingFriendEntry != null) "" else loadError,
        onRetry = { loadSchedule(true) },
        periodTimes = periodTimes,
        periodCount = periodCount,
        firstWeekDate = displayedTimeBase?.firstWeekDate,
        weekRequestKey = appliedCalendar.orEmpty(),
        onWeekChange = { currentWeek = it },
        onCourseClick = {
            notificationCourseJson = null
            // Freeze the tapped card before pager neighbours or sheet layout update their bounds.
            detailSourceBounds = focusRegistry.bounds(it.id)
            detailId = it.id
        },
        onEmptyCellClick = { day, period -> emptyClassroomRequest = EmptyClassroomRequest(currentWeek, day, period) },
        onSettingsClick = { settingsTermOverride = null; showSettingsDialog = true },
        onExportClick = {
            if (displayCourses.isEmpty()) {
                GlassToaster.show("课表为空，无法导出")
            } else {
                try {
                    val semesterStart = ScheduleDates.firstMonday(displayedTimeBase?.firstWeekDate)
                    if (semesterStart == null) {
                        GlassToaster.show("请先设置这个学期的第一周周一日期")
                        settingsTermOverride = null
                        showSettingsDialog = true
                    } else {
                    ICalExporter.exportAndShare(
                        context = context,
                        courses = displayCourses,
                        semesterStartDate = semesterStart,
                        totalWeeks = ScheduleMaxWeeks,
                        periodTimes = periodTimes.associate { it.period to (it.startTime to it.endTime) }
                    )
                    GlassToaster.show("课表已导出，可导入到系统日历中查看")
                    }
                } catch (e: Exception) {
                    GlassToaster.show("导出失败：${e.message}")
                }
            }
        },
        semesterIndex = semesterIndex,
        onSelectSemester = { semesterIndex = it },
        dayView = dayView,
        onDayViewChange = { value ->
            dayView = value
            if (value) {
                // 切到日视图 = 看「今天」：定位今天所在周与星期，避免停在
                // 别的周而显示空白。
                selectedDay = todayWeekday
                currentWeek = todayWeekNumber.coerceIn(1, com.buct.course.schedule.ScheduleMaxWeeks)
            }
        },
        selectedDay = selectedDay,
        onDaySelect = { day ->
            dayView = true
            selectedDay = day
        },
        onTodayClick = {
            // 日视图点「今天」：回到今天（选中今天、跳本周）；
            // 周视图点「本周」：只跳回本周，保持整周视图。
            if (dayView) selectedDay = todayWeekday
            currentWeek = todayWeekNumber.coerceIn(1, com.buct.course.schedule.ScheduleMaxWeeks)
        },
        // 考试安排 = 成绩页共享缓存（教务）+ 本地自建考试
        exams = run {
            val localExamRevision = com.buct.course.manager.CustomExamStore.revision
            remember(localExamRevision, com.buct.course.ui.screen.ExamScheduleSharedCache.examList) {
                com.buct.course.ui.screen.mergeExamsWithLocal(
                    context,
                    com.buct.course.ui.screen.ExamScheduleSharedCache.examList
                )
            }
        },
        accountLabel = effectiveAccountLabel,
        onAccountClick = { showAccountSwitcher = true }
    )
    }
    if (showAccountSwitcher) {
        com.buct.course.ui.screen.AccountSwitcherDialog(
            accounts = savedAccounts,
            currentKey = UserManager.getInstance().currentAccountKey,
            onSelect = { switchAccount(it) },
            onManage = {
                showAccountSwitcher = false
                showSettingsDialog = true
            },
            onDismiss = { showAccountSwitcher = false },
            friendSchedules = friendSchedulesForDialog,
            currentFriendId = viewingFriendId,
            onSelectFriend = { id ->
                viewingFriendId = id
                showAccountSwitcher = false
                GlassToaster.show("已切换到朋友的课表（只读）")
            }
        )
    }
    emptyClassroomRequest?.let { request ->
        EmptyClassroomDialog(
            semester = resolvedTermId,
            week = request.week,
            day = request.day,
            period = request.period,
            onDismiss = { emptyClassroomRequest = null }
        )
    }

    if (deletedCourseJson != null) {
        androidx.compose.material3.Snackbar(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp)
                .padding(bottom = com.buct.course.ui.system.LocalAppOverlayBottomInset.current + 12.dp),
            action = {
                androidx.compose.material3.TextButton(onClick = {
                    if (System.currentTimeMillis() < undoDeadline) {
                        val record = deletedCourseJson?.let { ReminderJson.course(JSONObject(it)) }
                        if (record != null) {
                            settingsManager.updateCustomCourse(ScheduleSettingsManager.CustomCourse(record.id.removePrefix("custom:"), record.name,
                                record.location, record.teacher, record.day, record.startPeriod, record.endPeriod, record.weeks), routeAccountKey)
                            reminderScheduler.restoreUndo(deletedRemindersJson)
                        }
                    }
                    deletedCourseJson = null
                }) { Text("撤销") }
            }
        ) { Text("已删除课程") }
    }
    }
    
    if (showSettingsDialog) {
        com.buct.course.ui.system.GlassSubpage(onDismiss = { showSettingsDialog = false; settingsTermOverride = null }) { close ->
            ScheduleSettingsScreen(
                manager = settingsManager,
                periodTimesOverride = periodTimesFor(termTimeBase),
                semesterStartOverride = termTimeBase?.firstWeekDate?.takeIf { it.isNotBlank() }?.let {
                    runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).parse(it)?.time }.getOrNull()
                } ?: 0L,
                onSemesterStartChange = { millis ->
                    if (semesterIndex == 0 && settingsTermOverride == null) settingsManager.semesterStartDate = millis
                    val times = periodTimesFor(termTimeBase)
                    reminderScheduler.updateTimeBase(routeAccountKey, settingsTerm, ScheduleTimeBase(
                        ScheduleTimeBase.dateFromMillis(millis), times.associate { it.period to it.startTime }, times.associate { it.period to it.endTime }))
                },
                onPeriodTimesChange = { times ->
                    if (semesterIndex == 0 && settingsTermOverride == null) settingsManager.savePeriodTimes(times)
                    reminderScheduler.updateTimeBase(routeAccountKey, settingsTerm,
                        (reminderScheduler.timeBase(routeAccountKey, settingsTerm) ?: ScheduleTimeBase()).copy(
                            periodStarts = times.associate { it.period to it.startTime }, periodEnds = times.associate { it.period to it.endTime }))
                },
                customCourses = customCourses,
                onAddCustomCourse = { editingId = java.util.UUID.randomUUID().toString() },
                onEditCustomCourse = { editingId = it },
                onSyncSchedule = {
                    close()
                    loadSchedule(true)
                },
                onClose = {
                    periodCount = settingsManager.periodCount
                    periodTimes = periodTimesFor(displayedTimeBase).map { PeriodTimeUi(it.period, it.startTime, it.endTime) }
                    close()
                }
            )
        }
    }
    
    val notificationCourse = notificationCourseJson?.let { runCatching { ReminderJson.reminder(JSONObject(it)) }.getOrNull() }
    val detailTerm = notificationCourse?.key?.term ?: resolvedTermId
    val selectedDetail = if (viewingFriendEntry != null) {
        displayCourses.firstOrNull { it.id == detailId }
    } else {
        courses.firstOrNull { it.id == detailId && detailTerm == resolvedTermId }
    } ?: notificationCourse?.course?.let {
        ScheduleCourseUi(it.name, it.teacher, it.location, it.day, it.startPeriod, it.endPeriod, it.weeks,
            courseColors[ScheduleIdentity.colorIndex(it.id, courseColors.size)], it.custom, if (it.custom) it.id.removePrefix("custom:") else "", id = it.id)
    }
    selectedDetail?.let { course ->
        com.buct.course.ui.screen.ScheduleCourseSheet(course, routeAccountKey, detailTerm, if (detailTerm == resolvedTermId) courses else listOf(course),
            sourceCenterX = detailSourceBounds?.center?.x,
            sourceBounds = detailSourceBounds,
            onDismiss = { detailId = null; detailSourceBounds = null; notificationCourseJson = null; focusRegistry.restore(course.id) },
            onEdit = { editingId = course.customId },
            onConfigureTime = { settingsTermOverride = detailTerm; showSettingsDialog = true },
            onDelete = {
                deletedCourseJson = ReminderJson.course(course.record()).toString()
                deletedRemindersJson = reminderScheduler.encodeUndo(reminderScheduler.removeCourse(routeAccountKey, course.id))
                undoDeadline = System.currentTimeMillis() + 5000L
                settingsManager.removeCustomCourse(course.customId, routeAccountKey)
                detailId = null
                detailSourceBounds = null
                notificationCourseJson = null
                focusRegistry.restore(course.id)
            })
    }
    editingId?.let { id ->
        val initial = customCourses.firstOrNull { it.id == id } ?: ScheduleSettingsManager.CustomCourse(
            id, "", "", "", 1, 1, 2, "1-16周")
        com.buct.course.ui.system.GlassSubpage(onDismiss = { editingId = null }) { close ->
            com.buct.course.ui.screen.ScheduleCourseEditor(initial, courses, periodCount,
                isNew = customCourses.none { it.id == id }, onClose = close, onSave = { saved ->
                    settingsManager.updateCustomCourse(saved, routeAccountKey)
                    reminderScheduler.updateCustomCourse(routeAccountKey, ScheduleCourseRecord("custom:${saved.id}", saved.name,
                        saved.teacher, saved.location, saved.day, saved.startPeriod, saved.endPeriod, saved.weeks, true))
                    notificationCourse?.key?.storageId?.let { reminderScheduler.findById(it) }?.let {
                        notificationCourseJson = ReminderJson.reminder(it).toString()
                    }
                    close()
                })
        }
    }
}

package com.buct.course.ui.screen

import com.buct.course.ui.theme.moduleEntrance

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import com.buct.course.ui.system.glass.DampedDragAnimation
import com.buct.course.ui.system.glass.adaptiveGlassChip
import com.buct.course.ui.system.glass.glassRim
import com.buct.course.ui.system.glass.applyChipContentDeformation
import com.buct.course.ui.system.glass.rememberInteractiveOptics
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buct.course.ui.system.GlassLoadingState
import com.buct.course.ui.system.SystemDialog
import com.buct.course.ui.system.PagePadding
import com.buct.course.ui.system.SystemCard
import com.buct.course.ui.system.SystemCompactSegmentedControl
import com.buct.course.ui.system.SystemEmptyState
import com.buct.course.ui.system.SystemStatusBadge
import com.buct.course.ui.system.SystemTone
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.buct.course.ui.system.GlassMaterialRole
import com.buct.course.ui.system.HeaderGlassSlab
import com.buct.course.ui.system.StatusBarFrost
import com.buct.course.ui.system.lerpDp
import com.buct.course.ui.system.rememberScreenMetrics
import com.buct.course.ui.system.lerpSp
import com.buct.course.ui.system.GlassMaterials
import com.buct.course.ui.system.GlassRecipe
import com.buct.course.ui.system.GlassSemesterToggle
import com.buct.course.ui.system.LocalAppBackdrop
import com.buct.course.ui.system.LocalControlBackdrop
import com.buct.course.ui.system.isBackdropSupported
import com.buct.course.ui.system.glass.LiquidActionGroup
import com.buct.course.ui.system.glass.glassRim
import com.buct.course.ui.system.glass.resolvePhysicalLens
import com.buct.course.ui.system.rememberGlassAccessibilityMode
import com.buct.course.ui.system.reportNoticeAnchor

import com.buct.course.ui.theme.MotionSpring
import com.buct.course.ui.theme.NeuDivider
import com.buct.course.ui.theme.NeuPrimary
import com.buct.course.ui.theme.NeuSurface

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlinx.coroutines.launch

private val ScheduleTimeColumnWidth = 36.dp
private val ScheduleTimeColumnShadowWidth = 8.dp
private val SchedulePeriodHeight = 84.dp

/** 窄屏收窄的时间列与网格左右留白，把省下的宽度全给七个日列。 */
private val ScheduleTimeColumnWidthTight = 28.dp
private val SchedulePeriodHeightTight = 68.dp
private val ScheduleGridPaddingTight = 10.dp

// ── 顶栏折叠几何 ────────────────────────────────────────────────
// 展开态与折叠态的高度【差】必须等于折叠行程（travel）：
// 手指走 60dp -> 内容上移 60dp -> 顶栏下缘也上移 60dp，两者间距恒定。
// 行程和高度差一旦脱钩，网格顶端就会与收缩中的顶栏彼此追赶——所以 travel
// 定义成差值而不是另一个常量（与成绩页的 GradesHeaderMetrics 同一套写法）。
private val HeaderTopPadExpanded = 10.dp
private val HeaderTopPadCollapsed = 6.dp
/** 标题 + 分段控件 + 芯片同一行：34(标题) + 6 + 36(分段) = 76。 */
private val HeaderActionRowExpanded = 76.dp
private val HeaderActionRowCollapsed = 48.dp
private val HeaderTitleGap = 6.dp
/** 分段控件自身高度。它内部写死 36dp，容器给不足就会被压扁而不是被裁。 */
private val HeaderSegmentHeight = 36.dp
private val HeaderWeekRowExpanded = 48.dp
private val HeaderWeekRowCollapsed = 32.dp
private val HeaderBottomPadExpanded = 8.dp
private val HeaderBottomPadCollapsed = 4.dp

/**
 * 顶栏两态高度。**短屏只压展开态的空白**——折叠态、分段控件高度与玻璃条几何
 * 一律不动，那几个值同时是胶囊圆角与折射行程的依据。
 */
private class ScheduleHeaderMetrics(
    val expanded: Dp,
    val collapsed: Dp,
    val topPadExpanded: Dp,
    val titleGap: Dp,
    val weekRowExpanded: Dp,
    val weekRowCollapsed: Dp,
    val actionRowExpanded: Dp,
    val actionRowCollapsed: Dp,
    val stackedActions: Boolean,
    val semesterWidth: Dp,
    val prefixFontSize: TextUnit
) {
    /** 折叠行程。定义成差值，于是不可能与两态高度脱钩。 */
    val travel: Dp get() = expanded - collapsed
}

@Composable
private fun rememberScheduleHeaderMetrics(availableWidth: Dp): ScheduleHeaderMetrics {
    val screen = rememberScreenMetrics()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.bodyLarge
    val segmentStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
    return remember(screen, availableWidth, density, measurer, titleStyle, segmentStyle) {
        fun textSize(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1)
        fun textWidth(text: String, style: TextStyle) = with(density) { textSize(text, style).size.width.toDp() }
        fun textHeight(text: String, style: TextStyle) = with(density) { textSize(text, style).size.height.toDp() }
        val topPad = screen.tall(HeaderTopPadExpanded, 6.dp)
        val titleGap = screen.tall(HeaderTitleGap, 4.dp)
        // 星期行高度必须按真实字体度量算，不能写死。
        // 行内是「12sp 星期 + 2dp + 15sp 日期 + 2dp + 3dp 圆点」两行文本，
        // 而 Text 不给 lineHeight 时会继承主题的大行高；系统字号一放大，
        // 内容就超过固定 48dp，日期数字被行框裁成「只显示上半截」。
        val dayStyle = titleStyle.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
        val dateStyle = titleStyle.copy(fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        val dayStyleCollapsed = titleStyle.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
        val dateStyleCollapsed = titleStyle.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
        val weekdayStack = textHeight("一", dayStyle) + 2.dp + textHeight("28", dateStyle) + 2.dp + 3.dp
        val weekdayStackCollapsed =
            textHeight("一", dayStyleCollapsed) + 1.dp + textHeight("28", dateStyleCollapsed) + 1.dp + 2.dp
        val weekRow = maxOf(screen.tall(HeaderWeekRowExpanded, 42.dp), weekdayStack, 30.dp * density.fontScale)
        val bottomPad = screen.tall(HeaderBottomPadExpanded, 4.dp)
        // Four 48dp targets keep their original row. Fit the text to the actual parent,
        // rather than stacking every device below an arbitrary screen-width breakpoint.
// 字体放大后动作栏也会变宽。固定只扣 204dp 会把标题列压得过窄，
        // 在 Android 16 / 大字号设备上「第 9 周」会被裁成只剩「第」。
        // 实际动作栏约 270dp：本周 + 日/周 + 四个图标按钮。
        // 之前只扣 204dp，标题列被压到「第」一个字宽。
        val actionReserve = 276.dp * density.fontScale.coerceAtLeast(1f)
        val titleAvailable = (availableWidth - PagePadding * 2 - actionReserve - 8.dp).coerceAtLeast(0.dp)
        val prefixSize = if (availableWidth < 380.dp) 12.sp else 14.sp
        val expandedStyle = titleStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
        val collapsedStyle = expandedStyle.copy(fontSize = 17.sp)
        val prefixStyle = titleStyle.copy(fontSize = prefixSize, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
        val segmentMinimum = textWidth("本学期", segmentStyle) * 2 + 30.dp
        val titleMinimum = maxOf(textWidth("第 25 周", expandedStyle),
            textWidth("第 25 周", collapsedStyle) + textWidth("下学期 · ", prefixStyle), segmentMinimum)
        // 大字号优先级最高：不要为了把动作栏塞进同一行而牺牲标题。
        val stacked = density.fontScale > 1.08f || titleAvailable < titleMinimum
        val maxSemesterWidth = (availableWidth - PagePadding * 2).coerceAtLeast(1.dp)
        val semesterWidth = if (stacked) {
            minOf(maxOf(210.dp, segmentMinimum), maxSemesterWidth)
        } else {
            maxOf(segmentMinimum, minOf(210.dp * density.fontScale.coerceAtLeast(1f), titleAvailable, maxSemesterWidth))
        }
        val titleExpanded = textHeight("第 25 周", expandedStyle) + titleGap + HeaderSegmentHeight
        val titleCollapsed = maxOf(textHeight("第 25 周", collapsedStyle), textHeight("下学期 · ", prefixStyle))
        val actionExpanded = if (stacked) titleExpanded + 6.dp + 48.dp else maxOf(HeaderActionRowExpanded, titleExpanded)
        val actionCollapsed = if (stacked) titleCollapsed + 6.dp + 48.dp else maxOf(HeaderActionRowCollapsed, titleCollapsed)
        val collapsedWeekRow = maxOf(HeaderWeekRowCollapsed, weekdayStackCollapsed, 26.dp * density.fontScale)
        ScheduleHeaderMetrics(
            // 展开态：上留白 + 标题行 + 标题间距 + 周次行 + 下留白
            expanded = topPad + actionExpanded + titleGap + weekRow + bottomPad,
            collapsed = HeaderTopPadCollapsed + actionCollapsed + collapsedWeekRow + HeaderBottomPadCollapsed,
            topPadExpanded = topPad,
            titleGap = titleGap,
            weekRowExpanded = weekRow,
            weekRowCollapsed = collapsedWeekRow,
            actionRowExpanded = actionExpanded,
            actionRowCollapsed = actionCollapsed,
            stackedActions = stacked,
            semesterWidth = semesterWidth,
            prefixFontSize = prefixSize
        )
    }
}

/** 悬浮玻璃条相对屏幕边缘的内缩。前景内容不在条里，所以这个值不影响任何对齐。 */
private val HeaderSlabInset = 12.dp
/** 与状态栏磨砂之间留一道透明缝：网格清晰穿过，条的上缘才看得出折射。 */
private val HeaderSlabTopGap = 6.dp
private val HeaderSlabBottomGap = 4.dp
/**
 * 固定 29dp = 折叠态条高(58dp)的一半，于是折叠态正好是一枚胶囊。
 * 不用 percent=50：那样半可见的中途会是一枚很大的软药片，观感突兀。
 */
private val HeaderSlabCorner = 29.dp

/**
 * 网格的横向几何。**星期条与网格必须调用同一对函数**——星期标签靠
 * 「相同的左右留白 + 相同宽度的时间列占位」才和日期列对齐（见 `WeekHeaderCompact`
 * 里那条注释），两边取不同的值就会整体错位。
 */
@Composable
private fun scheduleGridPadding(): Dp =
    rememberScreenMetrics().wide(PagePadding, ScheduleGridPaddingTight)

@Composable
private fun scheduleTimeColumnWidth(): Dp =
    rememberScreenMetrics().wide(ScheduleTimeColumnWidth, ScheduleTimeColumnWidthTight)

/** 单节课的行高。短屏收到 68dp，一屏能多看一节多。 */
@Composable
private fun schedulePeriodHeight(): Dp {
    val fallback = rememberScreenMetrics().tall(SchedulePeriodHeight, SchedulePeriodHeightTight)
    val manager = com.buct.course.manager.ScheduleSettingsManager.getInstance()
    // 先读 revision 建立 Compose 订阅，改完高度课表能立刻重组
    val settingsRevision = manager.revision
    val configured = manager.periodHeight
    return if (configured > 0 && settingsRevision >= 0) configured.dp else fallback
}

data class ScheduleCourseUi(
    val name: String,
    val teacher: String,
    val location: String,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: String,
    val color: Color,
    val isCustom: Boolean = false,
    val customId: String = "",
    val sourceId: String = "",
    val id: String = if (isCustom) "custom:$customId" else com.buct.course.schedule.ScheduleIdentity.network(
        sourceId, name, teacher, day, startPeriod, endPeriod, weeks, location
    ),
    val hasConflict: Boolean = false,
    val isCurrent: Boolean = false,
    /** 该课在本周不上，只是作为「非本周课程」被额外显示出来。 */
    val isOtherWeek: Boolean = false,
    /** 同一时间段还有多少门其他课程（用于「+N」提示）。 */
    val slotSiblings: Int = 0
) {
    fun record() = com.buct.course.schedule.ScheduleCourseRecord(id, name, teacher, location, day, startPeriod, endPeriod, weeks, isCustom)
}

data class PeriodTimeUi(val period: Int, val startTime: String, val endTime: String)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(
    currentWeek: Int,
    courses: List<ScheduleCourseUi>,
    isLoading: Boolean,
    periodTimes: List<PeriodTimeUi> = emptyList(),
    periodCount: Int = 12,
    onWeekChange: (Int) -> Unit,
    onCourseClick: (ScheduleCourseUi) -> Unit,
    onEmptyCellClick: ((day: Int, period: Int) -> Unit)? = null,
    onSettingsClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    semesterIndex: Int = 0,
    onSelectSemester: (Int) -> Unit = {},
    errorMessage: String = "",
    onRetry: () -> Unit = {},
    firstWeekDate: String? = null,
    weekRequestKey: String? = null,
    /** 日视图（只看当天）/ 周视图（整周）切换 */
    dayView: Boolean = false,
    onDayViewChange: (Boolean) -> Unit = {},
    /** 1-7，日视图下当前查看的星期 */
    selectedDay: Int = 0,
    onDaySelect: (Int) -> Unit = {},
    onTodayClick: () -> Unit = {},
    /** 考试安排（成绩页共享缓存读入），日视图/周视图展示当天/本周考试 */
    exams: List<ExamItemUi> = emptyList(),
    /** 顶栏账号芯片文案（当前账号显示名）；空串则不显示 */
    accountLabel: String = "",
    onAccountClick: () -> Unit = {}
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val coroutineScope = rememberCoroutineScope()
    val maxWeeks = com.buct.course.schedule.ScheduleMaxWeeks
    val reducedMotion = com.buct.course.ui.system.rememberGlassAccessibilityMode().reduceMotion
    val minuteClock by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) {
            kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = System.currentTimeMillis()
        }
    }
    val actualWeek = com.buct.course.schedule.ScheduleDates.weekAt(firstWeekDate, minuteClock)
    val conflictIds = remember(courses) {
        val records = courses.map { it.record() }
        records.filter { com.buct.course.schedule.scheduleConflicts(it, records).isNotEmpty() }.map { it.id }.toSet()
    }
    val liveIds = remember(courses, periodTimes, minuteClock) {
        val clock = Calendar.getInstance().apply { timeInMillis = minuteClock }
        val today = (clock.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val nowMinutes = clock.get(Calendar.HOUR_OF_DAY) * 60 + clock.get(Calendar.MINUTE)
        fun minutes(text: String?): Int? {
            val parts = text?.split(':')?.map { it.toIntOrNull() } ?: return null
            return if (parts.size == 2 && parts[0] != null && parts[1] != null) parts[0]!! * 60 + parts[1]!! else null
        }
        courses.filter { course ->
            val start = minutes(periodTimes.firstOrNull { it.period == course.startPeriod }?.startTime)
            val end = minutes(periodTimes.firstOrNull { it.period == course.endPeriod }?.endTime)
            course.day == today && start != null && end != null && nowMinutes in start until end
        }.map { it.id }.toSet()
    }
    val pagerState = rememberPagerState(
        initialPage = (currentWeek - 1).coerceIn(0, maxWeeks - 1),
        pageCount = { maxWeeks }
    )
    val semesterStartMillis = remember {
        com.buct.course.manager.ScheduleSettingsManager.getInstance().semesterStartDate
    }

    val latestWeekChange by rememberUpdatedState(onWeekChange)
    val latestRequestedWeek by rememberUpdatedState(currentWeek)
    val latestWeekRequestKey by rememberUpdatedState(weekRequestKey)
    val weekSync = remember(pagerState) {
        com.buct.course.schedule.ScheduleWeekPagerSync(currentWeek, weekRequestKey)
    }

    var arrowJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var requestedPage by remember { mutableIntStateOf(pagerState.currentPage) }
    val userDragging by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(userDragging) {
        if (userDragging) { arrowJob?.cancel(); requestedPage = pagerState.currentPage }
    }
    fun moveWeek(delta: Int) {
        // 日视图：左右箭头按「天」移动；跨过 7/1 时顺带切周。
        if (dayView && selectedDay in 1..7) {
            val newDay = ((selectedDay - 1 + delta + 7) % 7) + 1
            if (newDay != selectedDay) onDaySelect(newDay)
            if ((delta > 0 && newDay == 1) || (delta < 0 && newDay == 7)) {
                requestedPage = ((if (arrowJob?.isActive == true) requestedPage else pagerState.currentPage) + delta).coerceIn(0, maxWeeks - 1)
                arrowJob?.cancel()
                arrowJob = coroutineScope.launch {
                    if (reducedMotion) pagerState.scrollToPage(requestedPage)
                    else pagerState.animateScrollToPage(requestedPage, animationSpec = com.buct.course.ui.theme.MotionProfile.pagerSpring())
                }
            }
            return
        }
        requestedPage = ((if (arrowJob?.isActive == true) requestedPage else pagerState.currentPage) + delta).coerceIn(0, maxWeeks - 1)
        arrowJob?.cancel()
        arrowJob = coroutineScope.launch {
            if (reducedMotion) pagerState.scrollToPage(requestedPage)
            else pagerState.animateScrollToPage(requestedPage, animationSpec = com.buct.course.ui.theme.MotionProfile.pagerSpring())
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow {
            Triple(latestRequestedWeek to latestWeekRequestKey, pagerState.isScrollInProgress, pagerState.settledPage)
        }.collect { (request, scrolling, page) ->
            val targetPage = weekSync.requestPage(request.first, request.second)
            if (targetPage != null) {
                arrowJob?.cancel()
                pagerState.scrollToPage(targetPage)
                weekSync.settledWeek(targetPage)
            } else if (!scrolling) {
                weekSync.settledWeek(page)?.let(latestWeekChange)
            }
        }
    }

    // 外部直接把 currentWeek 设到别的周（「回到本周」「回到今天」按钮、
    // 切日视图自动定位）时，pager 要跟着走。weekSync.requestPage 只在 key
    // 变化或 key 为空时才响应，同 key 的外部切周它不触发，这里补一条直连。
    // 防回环：箭头/手势滚动完成后 settledWeek 把 currentWeek 报告回同一周，
    // 此时 pager 已在目标页，条件不成立，不会自己滚回去。
    LaunchedEffect(currentWeek) {
        val targetPage = (currentWeek - 1).coerceIn(0, maxWeeks - 1)
        if (targetPage != pagerState.currentPage && !pagerState.isScrollInProgress) {
            arrowJob?.cancel()
            arrowJob = coroutineScope.launch {
                if (reducedMotion) pagerState.scrollToPage(targetPage)
                else pagerState.animateScrollToPage(targetPage, animationSpec = com.buct.course.ui.theme.MotionProfile.pagerSpring())
            }
        }
    }

    // 所有 pager 页共用一个滚动位置：顶栏折叠进度要跟着它推导，
    // 而且左右切周时纵向位置不该跳回顶部。
    val gridScrollState = rememberScrollState()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerMetrics = rememberScheduleHeaderMetrics(maxWidth)
    // 折叠行程 = 顶栏高度差，于是收缩与滚动 1:1 对消，全程跟手。
    val travelPx = with(LocalDensity.current) { headerMetrics.travel.toPx() }
    val headerCollapse by remember(travelPx) {
        derivedStateOf { (gridScrollState.value / travelPx).coerceIn(0f, 1f) }
    }
    // 课表内容的捕获层。顶栏玻璃采样「壁纸 + 这一层」，于是网格从顶栏底下
    // 穿过时会被折射；顶栏本身不在这一层内，不构成自采样。
    val wallpaperBackdrop = LocalAppBackdrop.current
    val contentBackdrop = if (wallpaperBackdrop != null && isBackdropSupported()) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val headerSampleBackdrop = if (wallpaperBackdrop != null && contentBackdrop != null) {
        rememberCombinedBackdrop(wallpaperBackdrop, contentBackdrop)
    } else {
        null
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // topBar slot 只测量单个子项，两个兄弟节点会叠放并让通知条压到状态栏，
            // 因此顶栏与内联通知必须在同一个 Column 里纵向排布。
            Column(modifier = Modifier.moduleEntrance(0).reportNoticeAnchor()) {
                WeekHeaderCompact(
                    currentWeek = pagerState.currentPage + 1,
                    weekOffset = if (reducedMotion) 0f else pagerState.currentPageOffsetFraction,
                    firstWeekDate = firstWeekDate,
                    actualWeek = actualWeek,
                    onPrevClick = {
                        moveWeek(-1)
                    },
                    onNextClick = {
                        moveWeek(1)
                    },
                    onSettingsClick = onSettingsClick,
                    onExportClick = onExportClick,
                    accountLabel = accountLabel,
                    onAccountClick = onAccountClick,
                    semesterIndex = semesterIndex,
                    onSelectSemester = { index ->
                        onSelectSemester(index)
                        coroutineScope.launch { gridScrollState.scrollTo(0) }
                    },
                    collapseFraction = headerCollapse,
                    sampleBackdrop = headerSampleBackdrop,
                    dayView = dayView,
                    onDayViewChange = onDayViewChange,
                    selectedDay = if (dayView) selectedDay else 0,
                    onDaySelect = onDaySelect,
                    showToday = dayView && selectedDay in 1..7 &&
                        (currentWeek != actualWeek || selectedDay != ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1)),
                    onTodayClick = onTodayClick
                )
            }
        }
    ) { paddingValues ->
        when {
            isLoading && courses.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .moduleEntrance(1)
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    GlassLoadingState(text = "正在同步课表…")
                }
            }

            errorMessage.isNotBlank() -> {
                Box(Modifier.fillMaxSize().moduleEntrance(1).padding(paddingValues).padding(24.dp), contentAlignment = Alignment.Center) {
                    com.buct.course.ui.system.SystemEmptyState(title = "课表同步失败", message = errorMessage) {
                        com.buct.course.ui.system.SystemSecondaryButton(text = "重新同步", onClick = onRetry)
                    }
                }
            }

            else -> {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !dayView,
                    flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                        state = pagerState, snapAnimationSpec = com.buct.course.ui.theme.MotionProfile.pagerSpring()),
                    modifier = Modifier
                        .testTag("schedule-pager")
                        .fillMaxSize()
                        .moduleEntrance(1)
                        // 内容捕获层挂在 pager 这个稳定节点上（不要挂进每一页）：
                        // 顶栏玻璃与芯片采样它，才能折射滚动中的网格与课程卡片。
                        .then(
                            if (contentBackdrop != null) {
                                Modifier.layerBackdrop(contentBackdrop)
                            } else {
                                Modifier
                            }
                        ),
                    beyondViewportPageCount = 0,
                    pageSpacing = 0.dp
                ) { page ->
                    val weekNumber = page + 1
                    val displayedCourses = remember(courses, conflictIds, liveIds, weekNumber, actualWeek, semesterIndex) {
                        courses.map { it.copy(hasConflict = it.id in conflictIds,
                            isCurrent = semesterIndex == 0 && weekNumber == actualWeek && it.id in liveIds) }
                    }
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            val offset = pagerState.currentPage + pagerState.currentPageOffsetFraction - page
                            scaleX = if (reducedMotion) 1f else com.buct.course.ui.theme.SchedulePagerMotion.scale(offset)
                            scaleY = scaleX
                            alpha = if (reducedMotion) 1f else com.buct.course.ui.theme.SchedulePagerMotion.alpha(offset)
                        }.then(
                            // 日视图：左右滑动按「天」切换（复用 moveWeek 的按天逻辑），周视图保持按周翻页
                            if (dayView && selectedDay in 1..7) {
                                val dragThreshold = with(LocalDensity.current) { 60.dp.toPx() }
                                Modifier.pointerInput(selectedDay, currentWeek) {
                                    var acc = 0f
                                    detectHorizontalDragGestures(
                                        onDragEnd = { },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            acc += dragAmount
                                            // 手势方向：手指右移（右→左滑的相反）= 昨天；手指左移（右→左滑）= 明天
                                            while (acc >= dragThreshold) { moveWeek(-1); acc -= dragThreshold }
                                            while (acc <= -dragThreshold) { moveWeek(1); acc += dragThreshold }
                                        }
                                    )
                                }
                            } else Modifier
                        )
                    ) {
                    if (dayView && selectedDay in 1..7) {
                        // 紧凑日视图：直接放两个区（考试 + 课程），时间放到课程名下面，内容置顶。
                        DayScheduleCompact(
                            courses = displayedCourses,
                            exams = exams,
                            currentWeek = weekNumber,
                            selectedDay = selectedDay,
                            periodTimes = periodTimes,
                            onCourseClick = onCourseClick,
                            scrollState = gridScrollState,
                            topInset = statusBarHeight + headerMetrics.expanded,
                            semesterStartMillis = semesterStartMillis
                        )
                    } else {
                    ScheduleGrid(
                        courses = displayedCourses,
                        currentWeek = weekNumber,
                        periodTimes = periodTimes,
                        periodCount = periodCount,
                        onCourseClick = onCourseClick,
                        onEmptyCellClick = onEmptyCellClick,
                        scrollState = gridScrollState,
                        // 【常量】而不是 paddingValues.calculateTopPadding()：后者随顶栏
                        // 一起收缩，而它施加在 verticalScroll 内部，于是顶栏每缩 1dp
                        // 内容就被额外上提 1dp——手指走 60dp、内容走 120dp。
                        topInset = statusBarHeight + headerMetrics.expanded,
                        exams = exams,
                        semesterStartMillis = semesterStartMillis
                    )
                    }
                    }
                }
            }
        }
    }
    }
}

@Composable
fun WeekHeaderCompact(
    currentWeek: Int,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    semesterIndex: Int = 0,
    onSelectSemester: (Int) -> Unit = {},
    accountLabel: String = "",
    onAccountClick: () -> Unit = {},
    /** 0=未滚动（大标题直接浮在课表上）、1=已上划（收拢成一条悬浮玻璃）。 */
    collapseFraction: Float = 0f,
    /** 「壁纸 + 课表内容」的合成采样源。为空则退回无玻璃顶栏。 */
    sampleBackdrop: Backdrop? = null,
    weekOffset: Float = 0f,
    firstWeekDate: String? = null,
    actualWeek: Int? = null,
    /** 日视图（只看当天）/ 周视图（整周）切换 */
    dayView: Boolean = false,
    onDayViewChange: (Boolean) -> Unit = {},
    /** 1-7，日视图下选中并高亮的星期；0 表示周视图 */
    selectedDay: Int = 0,
    onDaySelect: (Int) -> Unit = {},
    /** 不在「今天」时显示回到今天入口 */
    showToday: Boolean = false,
    onTodayClick: () -> Unit = {}
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val calendar = Calendar.getInstance()
    val headerFocus = remember { FocusRequester() }
    val focusRegistry = LocalScheduleFocus.current
    DisposableEffect(focusRegistry, headerFocus) {
        focusRegistry?.register("header", headerFocus)
        onDispose { focusRegistry?.remove("header", headerFocus) }
    }
    val currentDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // CourseSelectorTheme 已根据系统模式与壁纸明暗选择配色，无需读取 GPU 像素。
    val titleColor = MaterialTheme.colorScheme.onSurface

    val collapse = collapseFraction.coerceIn(0f, 1f)

    // 顶栏玻璃把自己的渲染结果导出到这一层，供板上的芯片二次采样，
    // 于是芯片折射「壁纸 + 滚动网格 + 顶栏玻璃」三者的合成——玻璃叠玻璃。
    val headerBackdrop = rememberLayerBackdrop()
    val chipBackdrop = if (sampleBackdrop != null) {
        rememberCombinedBackdrop(sampleBackdrop, headerBackdrop)
    } else {
        null
    }
    // 与 ScheduleScreen 里那份是同一个纯函数结果，不会算出两套几何
    val headerMetrics = rememberScheduleHeaderMetrics(maxWidth)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 定高：这是"收起"的来源，也让悬浮条的几何是确定的
            .height(
                statusBarHeight +
                    lerpDp(headerMetrics.expanded, headerMetrics.collapsed, collapse)
            )
            .then(com.buct.course.ui.system.wallpaperHeaderScrim())
    ) {
        // 玻璃层：【必须是前景内容的兄弟节点，不能是它的父节点】。
        // layerBackdrop 捕获所在节点的整棵子树——挂在包含按钮的父节点上，
        // 按钮就会采样一个含有自己的图层，RenderThread 死循环直接 native 崩溃。
        // 同款写法见 SystemUi.kt 的 SystemDialog。
        //
        // 节点常驻、不用 if(collapse>0) 摘掉：一摘掉这一层就没有内容可导出，
        // 采样它的芯片会拿到空图层。强度全部由 collapse 调制。
        if (sampleBackdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(headerBackdrop)
            ) {
                StatusBarFrost(
                    height = statusBarHeight + 1.dp,
                    collapse = collapse,
                    backdrop = sampleBackdrop
                )
                HeaderGlassSlab(
                    // 比 collapse 晚起步：顶栏还高的时候它是一张大卡片，
                    // 提前显形会让人先看到"卡"再看到"条"。
                    strength = ((collapse - 0.35f) / 0.65f).coerceIn(0f, 1f),
                    backdrop = sampleBackdrop,
                    cornerRadius = HeaderSlabCorner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = statusBarHeight + HeaderSlabTopGap,
                            start = HeaderSlabInset,
                            end = HeaderSlabInset,
                            bottom = HeaderSlabBottomGap
                        )
                        .fillMaxHeight()
                )
            }
        }

        CompositionLocalProvider(LocalControlBackdrop provides chipBackdrop) {
            // 前景横向几何刻意保持不变（padding = PagePadding）：星期条要和网格的
            // 日期列对齐，塞进内缩 12dp 的玻璃条里就得反向补偿，多一处会飘的耦合。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        top = lerpDp(headerMetrics.topPadExpanded, HeaderTopPadCollapsed, collapse)
                    )
            ) {
                val actionScrollState = rememberScrollState()
                ScheduleHeaderActionLayout(
                    stacked = headerMetrics.stackedActions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            lerpDp(headerMetrics.actionRowExpanded, headerMetrics.actionRowCollapsed, collapse)
                        )
                        .padding(horizontal = PagePadding)
                ) {
                    // 标题与分段控件同属一个纵列、芯片在右侧垂直居中——这是改动前
                    // 的构图。把芯片单独提到标题行会在分段控件右边空出一大块。
                    Column(
                        modifier = Modifier.weight(1f).testTag("schedule-header-title"),
                        verticalArrangement = Arrangement.spacedBy(
                            lerpDp(headerMetrics.titleGap, 0.dp, collapse)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 学期信息从分段控件"凝聚"进标题：控件缩掉的同时
                            // 「下学期 ·」从左侧挤出、把标题推向右，标题同时在缩字号。
                            // 一段连续形变，而不是字符串在某一帧突变。
                            SemesterTitlePrefix(
                                text = when (semesterIndex) { 0 -> "第一学期 · "; 1 -> "第二学期 · "; else -> "小学期 · " },
                                fontSize = headerMetrics.prefixFontSize,
                                progress = ((collapse - 0.45f) / 0.55f).coerceIn(0f, 1f)
                            )
                            Text(
                                text = "第 $currentWeek 周",
                                modifier = Modifier.focusRequester(headerFocus).focusable().graphicsLayer {
                                    alpha = (1f - kotlin.math.abs(weekOffset) * 2f).coerceIn(0f, 1f)
                                    translationX = -weekOffset * 12.dp.toPx()
                                },
                                fontSize = lerpSp(26f, 17f, collapse),
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                letterSpacing = 0.sp,
                                maxLines = 1
                            )
                        }

                        // 收拢方式：容器高度与内容缩放【同一个系数】，于是绘制尺寸
                        // 永远等于容器高度——既不会被裁出一条平边，也不会被压扁。
                        // 单独缩容器高度是上一版"文字挤出轨道"的成因：分段控件内部
                        // 写死 height(36.dp)，父约束一小它就被压扁。
                        val segmentFraction = (1f - collapse).coerceIn(0f, 1f)
                        // 账号快速切换和「第 X 学期」放同一行、紧贴分段控件：
                        // 它不占据动作栏宽度（窄屏上会把最后的「设置」挤出屏幕），
                        // 也不会像靠右对齐那样在大屏上飘到屏幕中间。
                        // 折叠时整行高度归零，clipToBounds 让它跟着一起被裁掉，不额外占高度。
                        Box(
                            modifier = Modifier
                                .height(HeaderSegmentHeight * segmentFraction)
                                .clipToBounds(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SemesterCapsuleToggle(
                                    semesterIndex = semesterIndex,
                                    onSelect = onSelectSemester,
                                    width = headerMetrics.semesterWidth,
                                    modifier = Modifier
                                )
                                if (accountLabel.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    AccountChip(
                                        label = accountLabel,
                                        onClick = onAccountClick,
                                        collapse = collapse
                                    )
                                }
                            }
                        }
                    }

                    // 动作栏回到加账号芯片之前的构成：今天/本周 + 日/周 + 四个圆钮。
                    // 账号芯片已经挪到标题行，这一行不再需要挤，窄屏也放得下；
                    // stacked 时仍保留横向滚动，兜住超大字号等极端情况。
                    Row(
                        modifier = Modifier.then(
                            if (headerMetrics.stackedActions) {
                                Modifier.fillMaxWidth().horizontalScroll(actionScrollState)
                            } else Modifier
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TodayChip(
                            label = if (dayView) "今天" else "本周",
                            onClick = onTodayClick,
                            size = lerpDp(34.dp, 30.dp, collapse),
                            collapse = collapse
                        )
                        Spacer(Modifier.width(6.dp))
                        DayViewToggle(
                            dayView = dayView,
                            onSelect = onDayViewChange,
                            collapse = collapse,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        com.buct.course.ui.system.TopBarActionRail(
                            Modifier.testTag("schedule-header-actions"),
                            spacing = lerpDp(4.dp, 3.dp, collapse)
                        ) {
                        val buttonSize = lerpDp(34.dp, 30.dp, collapse)
                        val iconSize = lerpDp(16.dp, 15.dp, collapse)
                        action(
                            index = 0,
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一周",
                            onClick = onPrevClick,
                            enabled = currentWeek > 1,
                            buttonSize = buttonSize,
                            iconSize = iconSize
                        )
                        action(
                            index = 1,
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "下一周",
                            onClick = onNextClick,
                            enabled = currentWeek < 25,
                            buttonSize = buttonSize,
                            iconSize = iconSize
                        )
                        action(
                            index = 2,
                            icon = Icons.Default.Share,
                            contentDescription = "导出",
                            onClick = onExportClick,
                            buttonSize = buttonSize,
                            iconSize = iconSize
                        )
                        action(
                            index = 3,
                            icon = Icons.Default.Settings,
                            contentDescription = "设置",
                            onClick = onSettingsClick,
                            buttonSize = buttonSize,
                            iconSize = iconSize
                        )
                    }
                    }
                }

                Spacer(Modifier.height(lerpDp(headerMetrics.titleGap, 0.dp, collapse)))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lerpDp(headerMetrics.weekRowExpanded, headerMetrics.weekRowCollapsed, collapse))
                        .padding(horizontal = scheduleGridPadding()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fallbackMonday = com.buct.course.schedule.ScheduleDates.mondayOfWeek(
                        com.buct.course.manager.ScheduleSettingsManager.getInstance().semesterStartDate
                    )
                    val headerDates = (0..6).map { index ->
                        com.buct.course.schedule.ScheduleDates.date(firstWeekDate, currentWeek, index + 1)
                            ?: (fallbackMonday.clone() as Calendar).apply {
                                add(Calendar.DAY_OF_YEAR, (currentWeek - 1) * 7 + index)
                            }
                    }
                    val firstMonth = headerDates.first().get(Calendar.MONTH) + 1
                    val lastMonth = headerDates.last().get(Calendar.MONTH) + 1
                    val monthLabel = if (firstMonth == lastMonth) "${firstMonth}月" else "${firstMonth}月\n${lastMonth}月"
                    Box(
                        modifier = Modifier
                            .width(scheduleTimeColumnWidth() + ScheduleTimeColumnShadowWidth)
                            .padding(end = ScheduleTimeColumnShadowWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = monthLabel,
                            fontSize = lerpSp(9.5f, 8f, collapse),
                            lineHeight = lerpSp(10.5f, 9f, collapse),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            softWrap = false,
                            modifier = Modifier.graphicsLayer {
                                alpha = (1f - kotlin.math.abs(weekOffset) * 2f).coerceIn(0f, 1f)
                            }
                        )
                    }
                    weekLabels.forEachIndexed { index, day ->
                        val isToday = index + 1 == currentDayOfWeek && currentWeek == actualWeek
                        val isSelected = dayView && selectedDay == index + 1
                        val date = headerDates[index]
                        val monthChanged = index > 0 &&
                            date.get(Calendar.MONTH) != headerDates[index - 1].get(Calendar.MONTH)
                        val dateText = if (monthChanged) {
                            "${date.get(Calendar.MONTH) + 1}/${date.get(Calendar.DAY_OF_MONTH)}"
                        } else {
                            "${date.get(Calendar.DAY_OF_MONTH)}"
                        }
                        CompactWeekdayLabel(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("schedule-weekday-${index + 1}")
                                .clickable { onDaySelect(index + 1) },
                            day = day,
                            isToday = !dayView && isToday,
                            isSelected = isSelected,
                            collapse = collapse,
                            date = dateText,
                            weekOffset = weekOffset
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ScheduleHeaderActionLayout(stacked: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val actions = measurables[1].measure(loose.copy(maxHeight = 48.dp.roundToPx()))
        val gap = if (stacked) 6.dp.roundToPx() else 8.dp.roundToPx()
        val title = measurables[0].measure(loose.copy(
            maxWidth = if (stacked) constraints.maxWidth else (constraints.maxWidth - actions.width - gap).coerceAtLeast(0),
            maxHeight = if (stacked) (constraints.maxHeight - actions.height - gap).coerceAtLeast(0) else constraints.maxHeight
        ))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (stacked) {
                title.placeRelative(0, 0)
                actions.placeRelative(constraints.maxWidth - actions.width, constraints.maxHeight - actions.height)
            } else {
                title.placeRelative(0, (constraints.maxHeight - title.height) / 2)
                actions.placeRelative(constraints.maxWidth - actions.width, (constraints.maxHeight - actions.height) / 2)
            }
        }
    }
}

/**
 * 标题左侧的学期前缀。宽度按 progress 缩放，于是它是"挤出来"的而不是"闪出来"的。
 * 用 Modifier.layout 改上报宽度，而不是 animateContentSize：后者需要先测到目标宽度
 * 再补一帧动画，跟手的折叠里会慢半拍。
 */
@Composable
private fun SemesterTitlePrefix(
    text: String,
    fontSize: TextUnit,
    progress: Float
) {
    if (progress <= 0.001f) return
    Box(
        modifier = Modifier
            .graphicsLayer { alpha = progress }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val width = (placeable.width * progress).roundToInt()
                layout(width, placeable.height) { placeable.place(0, 0) }
            }
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun CompactWeekdayLabel(
    modifier: Modifier = Modifier,
    day: String,
    isToday: Boolean,
    isSelected: Boolean = false,
    collapse: Float = 0f,
    date: String? = null,
    weekOffset: Float = 0f
) {
    val textColor by animateColorAsState(
        targetValue = if (isToday || isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "weekdayColor"
    )
    val dotScale by animateFloatAsState(
        targetValue = if (isToday || isSelected) 1f else 0.65f,
        animationSpec = MotionSpring.gentle(),
        label = "weekdayDotScale"
    )
    // 折叠后只留今天那一点——"只留必要信息"落到最小的一处
    val dotColor = if (isToday || isSelected) {
        NeuPrimary
    } else {
        NeuDivider.copy(alpha = NeuDivider.alpha * (1f - collapse))
    }

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            if (isToday || isSelected) stateDescription = if (isToday) "今天" else "选中"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(lerpDp(2.dp, 1.dp, collapse))
    ) {
        Text(
            text = day,
            fontSize = lerpSp(12f, 11f, collapse),
            lineHeight = lerpSp(14f, 13f, collapse),
            fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1
        )
        if (date != null) {
            // GitHub 新版观感：日期大数字在下，15sp 半粗 tabular 数字、列居中；今天/选中 accent，其余深色
            Text(
                text = date,
                fontSize = if (date.length > 2) lerpSp(12f, 10f, collapse) else lerpSp(15f, 12f, collapse),
                // 显式行高，避免继承主题的大行高把这一行撑出行框（真机上表现为数字被裁掉下半截）
                lineHeight = if (date.length > 2) lerpSp(14f, 12f, collapse) else lerpSp(17f, 14f, collapse),
                fontWeight = FontWeight.SemiBold,
                color = if (isToday || isSelected) textColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer {
                    alpha = (1f - kotlin.math.abs(weekOffset) * 2f).coerceIn(0f, 1f)
                },
                maxLines = 1
            )
            // 底部小圆点：今天/选中用主色，其余淡色（作者式 3dp 指示）
            Box(
                modifier = Modifier
                    .scale(dotScale)
                    .size(lerpDp(3.dp, 2.dp, collapse))
                    .background(
                        color = if (isToday || isSelected) NeuPrimary
                        else NeuDivider.copy(alpha = NeuDivider.alpha * 0.5f * (1f - collapse)),
                        shape = CircleShape
                    )
            )
        } else Box(
            modifier = Modifier
                .scale(dotScale)
                .size(lerpDp(5.dp, 4.dp, collapse))
                .background(color = dotColor, shape = CircleShape)
        )
    }
}

@Composable
private fun SemesterCapsuleToggle(
    semesterIndex: Int,
    onSelect: (Int) -> Unit,
    width: Dp,
    modifier: Modifier = Modifier
) {
    GlassSemesterToggle(
        options = listOf("第一学期", "第二学期", "小学期"),
        selectedIndex = semesterIndex,
        onSelect = { selectedIndex -> onSelect(selectedIndex) },
        modifier = modifier.width(width).height(HeaderSegmentHeight),
        backdrop = com.buct.course.ui.system.LocalAppBackdrop.current
            ?: com.buct.course.ui.system.LocalControlBackdrop.current
    )
}
/** 日/周 视图切换：两个小胶囊，「日」选中 = 日视图（只看当天）。 */
@Composable
private fun DayViewToggle(
    dayView: Boolean,
    onSelect: (Boolean) -> Unit,
    collapse: Float = 0f,
    modifier: Modifier = Modifier
) {
    // 与右侧箭头按钮同一尺寸体系：展开 34dp，折叠收 30dp
    val chipSize = lerpDp(34.dp, 30.dp, collapse)
    Row(
        modifier = modifier.height(chipSize),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayViewChip(label = "日", selected = dayView, onClick = { if (!dayView) onSelect(true) }, size = chipSize)
        DayViewChip(label = "周", selected = !dayView, onClick = { if (dayView) onSelect(false) }, size = chipSize)
    }
}

@Composable
private fun DayViewChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    size: Dp
) {
    // 与右侧 LiquidActionGroup 同一玻璃：真折射/边缘光 + 按压形变，
    // 选中态用主题色淡染 + 主题色文字，不破坏玻璃表面。
    val backdrop = com.buct.course.ui.system.LocalControlBackdrop.current
    val optics = rememberInteractiveOptics()
    Box(
        modifier = Modifier
            .size(size)
            .adaptiveGlassChip(backdrop, CircleShape, optics, enabled = true, interactive = true)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), CircleShape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .graphicsLayer {
                applyChipContentDeformation(optics, 2.dp.toPx(), 0.02f, 0.02f, com.buct.course.ui.system.GlassRecipe.ChipContentDeformDamping)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 回到今天/本周：玻璃圆钮，与右侧图标同一质感。 */
@Composable
private fun TodayChip(
    label: String,
    onClick: () -> Unit,
    size: Dp,
    collapse: Float
) {
    val backdrop = com.buct.course.ui.system.LocalControlBackdrop.current
    val optics = rememberInteractiveOptics()
    val alpha = (1f - collapse * 1.4f).coerceIn(0.25f, 1f)
    Box(
        modifier = Modifier
            .size(size)
            .adaptiveGlassChip(backdrop, CircleShape, optics, enabled = true, interactive = true)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .graphicsLayer {
                this.alpha = alpha
                applyChipContentDeformation(optics, 2.dp.toPx(), 0.02f, 0.02f, com.buct.course.ui.system.GlassRecipe.ChipContentDeformDamping)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}


@Composable
fun ScheduleGrid(
    courses: List<ScheduleCourseUi>,
    currentWeek: Int,
    periodTimes: List<PeriodTimeUi> = emptyList(),
    periodCount: Int = 12,
    onCourseClick: (ScheduleCourseUi) -> Unit,
    onEmptyCellClick: ((day: Int, period: Int) -> Unit)? = null,
    /**
     * scrollState 由调用方持有：顶栏的玻璃浓度要跟着它推导，而且所有 pager 页
     * 共用一个，左右切周时纵向位置不会跳回顶部。
     */
    scrollState: ScrollState = rememberScrollState(),
    /** 日视图：1-7 只渲染当天一列；null 渲染整周 */
    dayOnly: Int? = null,
    /** 本周考试（顶部「本周考试」条） */
    exams: List<ExamItemUi> = emptyList(),
    /** 当前学期开学日（毫秒），用于考试周次换算 */
    semesterStartMillis: Long? = null,
    /**
     * 顶栏高度。**施加在 verticalScroll 内部**——容器保持全出血，于是滚动位置 0
     * 时内容起始于顶栏之下，滚起来则从顶栏底下穿过，顶栏芯片才有东西可折射。
     * 加在容器上（`Modifier.padding(paddingValues)`）就成了"内容被推到顶栏下方"，
     * 芯片背后永远是空的。
     */
    topInset: Dp = 0.dp
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val dayCount = if (dayOnly != null) 1 else 7
    // 设置项：是否显示非本周课程（读 revision 订阅变化）
    val showOtherWeek = com.buct.course.manager.ScheduleSettingsManager.getInstance().let { manager ->
        val revision = manager.revision
        if (revision >= 0) manager.showOtherWeekCourses else false
    }
    val weekCourses = remember(courses, currentWeek, dayOnly) {
        courses.filter { isInWeek(it.weeks, currentWeek) && (dayOnly == null || it.day == dayOnly) }
    }
    // 显示列表：开启「非本周」后同一时间段只画一门——本周正在上的优先，
    // 其次按「还有几周才上」由近到远；同格其余课程用 slotSiblings 计数。
    val weeklyCourses = remember(courses, currentWeek, dayOnly, showOtherWeek) {
        if (!showOtherWeek) {
            weekCourses
        } else {
            courses.filter { dayOnly == null || it.day == dayOnly }
                .groupBy { it.day to it.startPeriod }
                .values
                .map { group ->
                    val primary = group.sortedWith(
                        compareBy({ weeksAhead(it.weeks, currentWeek) }, { it.name })
                    ).first()
                    primary.copy(
                        isOtherWeek = !isInWeek(primary.weeks, currentWeek),
                        slotSiblings = (group.size - 1).coerceAtLeast(0)
                    )
                }
        }
    }
    // 同一时间段（同一天同一开始节次）的全部候选课程：点击带「+N」的课程时，
    // 用它弹出按时间排序的完整列表
    val slotGroups = remember(courses, dayOnly) {
        courses.filter { dayOnly == null || it.day == dayOnly }.groupBy { it.day to it.startPeriod }
    }
    var conflictSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val timeColumnWidth = scheduleTimeColumnWidth()
    val gridPadding = scheduleGridPadding()
    val dayColumnWidthPx = with(LocalDensity.current) {
        ((constraints.maxWidth - gridPadding.roundToPx() * 2 - timeColumnWidth.roundToPx() -
            ScheduleTimeColumnShadowWidth.roundToPx()) / dayCount.toFloat()).roundToInt()
    }
    // 显式设过高度就不再按文字量撑高，否则用户把格子调小也看不到效果
    val customPeriodHeight = com.buct.course.manager.ScheduleSettingsManager.getInstance().periodHeight > 0
    val periodHeight = rememberCoursePeriodHeight(
        courses, dayColumnWidthPx, schedulePeriodHeight(), fixed = customPeriodHeight
    )
    val totalHeight = periodHeight * periodCount
    val darkGrid = com.buct.course.ui.system.rememberGlassDarkTheme()
    val gridTint = MaterialTheme.colorScheme.surface.copy(alpha =
        if (darkGrid || com.buct.course.manager.AppearanceSettingsManager.mode != com.buct.course.manager.WallpaperMode.Preset) 0.86f else 0.18f)
    val timeColumnTint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (darkGrid) 0.4f else 0.28f)

    Column(
        modifier = Modifier
            .testTag("schedule-grid-$currentWeek")
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = gridPadding,
                end = gridPadding,
                top = topInset + 8.dp,
                bottom = com.buct.course.ui.system.LocalAppOverlayBottomInset.current + 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val weekExams = remember(exams, currentWeek, semesterStartMillis) {
            exams.filter { com.buct.course.schedule.ScheduleDates.examWeek(it.examTime, semesterStartMillis) == currentWeek }
        }
        if (weekExams.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "本周考试",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SystemStatusBadge(text = "${weekExams.size} 场", tone = SystemTone.Warning)
                }
                weekExams.forEach { exam -> ExamDayCard(exam = exam, semesterStartMillis = semesterStartMillis) }
            }
        }
        // 去卡片化：轻玻璃衬底压到很低，让壁纸渐变与网格线透上来。
        // 这个 alpha 是"顶栏芯片能不能看出折射"的直接开关——衬底一厚，
        // 白芯片压在白板上，折射再准也是白压白。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(gridTint)
                .border(
                    0.5.dp,
                    Color.White.copy(alpha = 0.34f),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
            ) {
                Column(
                    modifier = Modifier
                        .width(timeColumnWidth)
                        .fillMaxHeight()
                        .background(timeColumnTint),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (i in 1..periodCount) {
                        val periodTime = periodTimes.find { it.period == i }
                        Box(
                            modifier = Modifier
                                .height(periodHeight)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = i.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (periodTime != null) {
                                    Text(
                                        text = periodTime.startTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = periodTime.endTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 时间列右侧阴影，柔和过渡
                Box(
                    modifier = Modifier
                        .width(ScheduleTimeColumnShadowWidth)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    NeuDivider.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TimetableBackground(
                        periodCount = periodCount,
                        periodHeight = periodHeight,
                        dayCount = dayCount,
                        dayNumbers = if (dayOnly != null) listOf(dayOnly) else (1..7).toList(),
                        onEmptyCellClick = onEmptyCellClick?.let { callback ->
                            { day, period ->
                                if (weeklyCourses.none { it.day == day && period in it.startPeriod..it.endPeriod }) {
                                    callback(day, period)
                                }
                            }
                        }
                    )
                    TimetableLayout(
                        courses = weeklyCourses,
                        periodCount = periodCount,
                        periodHeight = periodHeight,
                        onCourseClick = { course ->
                            val key = course.day to course.startPeriod
                            if (showOtherWeek && (slotGroups[key]?.size ?: 0) > 1) {
                                conflictSlot = key
                            } else {
                                onCourseClick(course)
                            }
                        },
                        dayCount = dayCount,
                        dayOnly = dayOnly
                    )

                    if (weeklyCourses.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            SystemEmptyState(
                                title = if (dayOnly != null) "当天暂无课程" else "本周暂无课程",
                                message = "可以切换周次、学期，或在设置中管理自定义课程。",
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }

                    // 同格多课列表：按「离现在最近要上的」排序，点任意一项直接看课程详情
                    conflictSlot?.let { slot ->
                        val group = slotGroups[slot].orEmpty()
                            .sortedWith(compareBy({ weeksAhead(it.weeks, currentWeek) }, { it.name }))
                        val head = group.firstOrNull()
                        SystemDialog(
                            onDismissRequest = { conflictSlot = null },
                            title = {
                                Column {
                                    Text(
                                        text = "同一时段的课程",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (head != null) {
                                        Text(
                                            text = "周${"一二三四五六日".getOrNull(head.day - 1) ?: head.day.toString()} · 第${head.startPeriod}-${head.endPeriod}节 · 共 ${group.size} 门",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                com.buct.course.ui.system.SystemSecondaryButton(
                                    text = "关闭",
                                    onClick = { conflictSlot = null },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                group.forEach { course ->
                                    val inWeek = isInWeek(course.weeks, currentWeek)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .clickable {
                                                conflictSlot = null
                                                onCourseClick(course)
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(course.color)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = course.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = listOfNotNull(
                                                    slotWeeksLabel(course.weeks, currentWeek),
                                                    course.location.ifBlank { null },
                                                    course.teacher.ifBlank { null }
                                                ).joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        SystemStatusBadge(
                                            text = if (inWeek) "本周" else "非本周",
                                            tone = if (inWeek) SystemTone.Info else SystemTone.Warning
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (weeklyCourses.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SystemStatusBadge(
                    text = if (dayOnly != null) "当天 ${weeklyCourses.size} 门课程" else "本周 ${weeklyCourses.size} 门课程",
                    tone = SystemTone.Info
                )
                if (weeklyCourses.any { it.isCustom }) {
                    SystemStatusBadge(
                        text = "含自定义课程",
                        tone = SystemTone.Warning
                    )
                }
            }
        }
    }
    }
}

private fun courseNameStyle(base: TextStyle, duration: Int, large: Boolean = false): TextStyle = base.copy(
    fontWeight = FontWeight.Bold,
    fontSize = when {
        large -> 16.sp
        duration == 1 -> 10.5.sp
        duration == 2 -> 11.sp
        else -> 11.5.sp
    },
    lineHeight = when {
        large -> 18.sp
        duration == 1 -> 12.sp
        duration == 2 -> 12.5.sp
        else -> 13.sp
    },
    letterSpacing = (-0.2).sp
)

private fun courseLocationStyle(base: TextStyle, duration: Int, large: Boolean = false): TextStyle = base.copy(
    fontWeight = FontWeight.Normal,
    fontSize = if (large) 13.sp else if (duration <= 2) 9.sp else 9.5.sp,
    lineHeight = if (large) 15.sp else if (duration <= 2) 10.5.sp else 11.sp
)

/** 日视图课程卡里的任课教师：比地点稍弱一档，避免与课程名抢视觉。 */
private fun courseTeacherStyle(base: TextStyle, large: Boolean): TextStyle = base.copy(
    fontWeight = FontWeight.Medium,
    fontSize = if (large) 12.sp else 9.sp,
    lineHeight = if (large) 14.sp else 10.5.sp
)

private fun ScheduleCourseUi.hasCardStatus() = hasConflict || isCurrent || isCustom ||
    !com.buct.course.schedule.ScheduleWeeks.parse(weeks).valid

/** The same measured row height drives the time rail, grid lines and course spans. */
@Composable
private fun rememberCoursePeriodHeight(
    courses: List<ScheduleCourseUi>,
    columnWidthPx: Int,
    minimum: Dp,
    /** true = 用户显式设了高度，按固定值渲染，不再按文字量撑高（要的就是紧凑）。 */
    fixed: Boolean = false
): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val style = MaterialTheme.typography.labelSmall
    return remember(courses, columnWidthPx, minimum, fixed, density, measurer, style) {
        with(density) {
            val contentWidth = (columnWidthPx - 2 * 1.dp.roundToPx() - 5.dp.roundToPx() - 2.dp.roundToPx()).coerceAtLeast(1)
            var rowHeight = minimum.toPx()
            if (!fixed) for (course in courses) {
                val duration = (course.endPeriod - course.startPeriod + 1).coerceAtLeast(1)
                val name = measurer.measure(course.name, courseNameStyle(style, duration),
                    maxLines = 4, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = contentWidth))
                val hasStatus = course.hasCardStatus()
                val locationHeight = if (course.location.isNotBlank()) measurer.measure(
                    course.location, courseLocationStyle(style, duration), constraints = Constraints(maxWidth = contentWidth)
                ).size.height else 0
                val informationHeight = locationHeight + if (hasStatus) 11.dp.roundToPx() else 0
                // Includes card insets, content padding, inter-line gap and rounding slack.
                val required = name.size.height + informationHeight + 9.dp.toPx()
                rowHeight = maxOf(rowHeight, ceil(required / duration))
            }
            ceil(rowHeight).toDp()
        }
    }
}

@Composable
private fun TimetableBackground(
    periodCount: Int,
    periodHeight: Dp,
    dayCount: Int = 7,
    dayNumbers: List<Int> = (1..7).toList(),
    onEmptyCellClick: ((day: Int, period: Int) -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 去斑马纹：透明列 + 极淡分隔线，壁纸从网格间透出
        Row(modifier = Modifier.fillMaxSize()) {
            repeat(dayCount) { dayIndex ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (dayIndex < 6) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(NeuDivider.copy(alpha = 0.18f))
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            repeat(periodCount) { rowIndex ->
                Box(
                    modifier = Modifier
                        .height(periodHeight)
                        .fillMaxWidth()
                ) {
                    if (rowIndex < periodCount - 1) {
                        HorizontalDivider(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            color = NeuDivider.copy(alpha = 0.15f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }

        if (onEmptyCellClick != null) {
            Row(modifier = Modifier.fillMaxSize()) {
                repeat(dayCount) { dayIndex ->
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        repeat(periodCount) { rowIndex ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clickable {
                                        onEmptyCellClick(dayNumbers.getOrElse(dayIndex) { dayIndex + 1 }, rowIndex + 1)
                                    }
                            )
                        }
                    }
                }
            }
        }
}

@Composable
fun TimetableLayout(
    courses: List<ScheduleCourseUi>,
    periodCount: Int = 12,
    periodHeight: Dp = SchedulePeriodHeight,
    modifier: Modifier = Modifier,
    onCourseClick: (ScheduleCourseUi) -> Unit,
    dayCount: Int = 7,
    dayOnly: Int? = null
) {
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            courses.forEach { course ->
                androidx.compose.runtime.key(course.id) { CourseCard(course = course, onClick = { onCourseClick(course) }, large = dayOnly != null) }
            }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val columnWidth = width / dayCount.toFloat()
        val cardInset = 1.dp.roundToPx()
        val pxPerPeriod = periodHeight.toPx()

        val placeables = measurables.mapIndexed { index, measurable ->
            val course = courses[index]
            val duration = course.endPeriod - course.startPeriod + 1
            val height = (duration * pxPerPeriod).roundToInt()
            val cardWidth = (columnWidth.roundToInt() - cardInset * 2).coerceAtLeast(1)
            val cardHeight = (height - cardInset * 2).coerceAtLeast(1)

            measurable.measure(
                constraints.copy(
                    minWidth = cardWidth,
                    maxWidth = cardWidth,
                    minHeight = cardHeight,
                    maxHeight = cardHeight
                )
            )
        }

        layout(width, (periodCount * pxPerPeriod).roundToInt()) {
            placeables.forEachIndexed { index, placeable ->
                val course = courses[index]
                // 日视图：课程固定画在唯一的一列（x=0），否则会按 dayIndex*全宽
                // 画到屏幕外。周视图按真实星期定位。
                val dayIndex = if (dayOnly != null) 0 else (course.day - 1).coerceIn(0, 6)
                val startPeriodIndex = (course.startPeriod - 1).coerceIn(0, periodCount - 1)

                val x = (dayIndex * columnWidth).roundToInt() + cardInset
                val y = (startPeriodIndex * pxPerPeriod).roundToInt() + cardInset

                placeable.place(x, y)
            }
        }
    }
}

@Composable
fun CourseCard(course: ScheduleCourseUi, onClick: () -> Unit, large: Boolean = false) {
    val darkCard = com.buct.course.ui.system.rememberGlassDarkTheme()
    val focusRequester = remember { FocusRequester() }
    val focusRegistry = LocalScheduleFocus.current
    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val unknownWeeks = remember(course.weeks) { !com.buct.course.schedule.ScheduleWeeks.parse(course.weeks).valid }
    DisposableEffect(course.id, focusRegistry) {
        focusRegistry?.register(course.id, focusRequester)
        onDispose { focusRegistry?.remove(course.id, focusRequester) }
    }
    val duration = (course.endPeriod - course.startPeriod + 1).coerceAtLeast(1)
    // 彩色半透玻璃 tile：加深填充保证壁纸上可读，边缘细亮线似透镜
    // 非本周课程整块降饱和成灰色，避免和本周要上的课抢注意力
    val baseColor = if (course.isOtherWeek) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    } else {
        course.color
    }
    val containerColor = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, baseColor, if (course.isCustom) 0.30f else 0.22f)
    val borderColor = baseColor.copy(alpha = 0.50f)
    val accentColor = baseColor.copy(alpha = 0.85f)

    val displayLocation = course.location

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !com.buct.course.ui.system.rememberGlassAccessibilityMode().reduceMotion) 0.97f else 1f,
        animationSpec = com.buct.course.ui.theme.MotionProfile.iconSpring(),
        label = "courseCardScale"
    )

    Surface(
        modifier = Modifier
            .testTag("schedule-course-${course.id}")
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                cardBounds = it.boundsInWindow()
                focusRegistry?.place(course.id, it.boundsInWindow())
            }
            .semantics {
                stateDescription = listOfNotNull(if (course.hasConflict) "时间冲突" else null,
                    if (course.isCurrent) "正在上课" else null, if (course.isCustom) "自定义课程" else null,
                    if (unknownWeeks) "周次待核对" else null).joinToString("，")
            }
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    // A course can also exist in the pager's adjacent week. The clicked copy
                    // owns the opening origin and the focus returned after dismissal.
                    focusRegistry?.register(course.id, focusRequester)
                    cardBounds?.let { focusRegistry?.place(course.id, it) }
                    onClick()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(0.6.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 顶部玻璃高光渐变：模拟光源照射的反射
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { (duration * 20).dp })
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (darkCard) 0.08f else 0.35f),
                                Color.White.copy(alpha = if (darkCard) 0.02f else 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // 左侧彩色指示条：课程颜色标识
            Box(
                modifier = Modifier
                    .width((3f + (1f - scale) * 50f).dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
            // 内容区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 5.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = course.name,
                    style = courseNameStyle(MaterialTheme.typography.labelSmall, duration, large),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (large) 3 else 4,
                    overflow = TextOverflow.Ellipsis,
                )

                if (large && course.teacher.isNotBlank()) {
                    Text(
                        text = course.teacher,
                        modifier = Modifier.fillMaxWidth().testTag("schedule-teacher-${course.id}"),
                        style = courseTeacherStyle(MaterialTheme.typography.labelSmall, large),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (displayLocation.isNotBlank() || course.isOtherWeek) {
                    Text(
                        // 「非本周」并入信息行而不是悬浮角标：卡片很窄，悬浮层会压住课程名
                        text = if (course.isOtherWeek) "非本周 · $displayLocation".trimEnd(' ', '·')
                        else displayLocation,
                        modifier = Modifier.fillMaxWidth().testTag("schedule-location-${course.id}"),
                        style = courseLocationStyle(MaterialTheme.typography.labelSmall, duration, large),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = true
                    )
                }
                // A status symbol must not reserve a column beside every line of a long address.
                if (course.hasConflict || course.isCurrent || course.isCustom || unknownWeeks) Icon(
                        imageVector = when { course.hasConflict || unknownWeeks -> Icons.Default.Warning
                            course.isCurrent -> Icons.Default.PlayArrow
                            else -> Icons.Default.Edit },
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = if (course.hasConflict || unknownWeeks) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
            }
        }
    }
}

internal fun isInWeek(weeks: String?, week: Int): Boolean {
    return com.buct.course.schedule.ScheduleWeeks.parse(weeks).visibleIn(week)
}

/**
 * 距离「下一次要上这门课」还有几周，用于同一时间段多门课时的排序：
 * 0 = 本周就上；1 = 下周；依此类推。已经结课的排到最后（用大数表示）。
 */
internal fun weeksAhead(weeks: String?, currentWeek: Int): Int {
    val parsed = com.buct.course.schedule.ScheduleWeeks.parse(weeks)
    if (!parsed.valid) return 5000
    for (week in currentWeek..com.buct.course.schedule.ScheduleMaxWeeks) {
        if (parsed.visibleIn(week)) return week - currentWeek
    }
    // 已经全部上完：按「越晚结束越靠前」排在后面
    val last = (currentWeek downTo 1).firstOrNull { parsed.visibleIn(it) } ?: 0
    return 1000 + (currentWeek - last)
}

/** 冲突列表里的时间描述：本周上课 / 第 N 周 / 已结课 / 待核对。 */
internal fun slotWeeksLabel(weeks: String, currentWeek: Int): String {
    val parsed = com.buct.course.schedule.ScheduleWeeks.parse(weeks)
    if (!parsed.valid) return "周次待核对"
    if (parsed.visibleIn(currentWeek)) return "本周上课"
    val ahead = weeksAhead(weeks, currentWeek)
    return if (ahead < 1000) "第 ${currentWeek + ahead} 周" else "已结课"
}

/** 紧凑日视图：考试区 + 课程区，时间放到课程名下面，内容置顶。 */
@Composable
private fun DayScheduleCompact(
    courses: List<ScheduleCourseUi>,
    exams: List<ExamItemUi>,
    currentWeek: Int,
    selectedDay: Int,
    periodTimes: List<PeriodTimeUi>,
    onCourseClick: (ScheduleCourseUi) -> Unit,
    scrollState: ScrollState,
    topInset: Dp,
    semesterStartMillis: Long?
) {
    val dayExams = remember(exams, currentWeek, selectedDay, semesterStartMillis) {
        exams.filter {
            com.buct.course.schedule.ScheduleDates.examWeek(it.examTime, semesterStartMillis) == currentWeek &&
                com.buct.course.schedule.ScheduleDates.examWeekday(it.examTime) == selectedDay
        }
    }
    val dayCourses = remember(courses, currentWeek, selectedDay) {
        courses.filter { isInWeek(it.weeks, currentWeek) && it.day == selectedDay }
            .sortedBy { it.startPeriod }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = scheduleGridPadding(),
                end = scheduleGridPadding(),
                top = topInset + 8.dp,
                bottom = com.buct.course.ui.system.LocalAppOverlayBottomInset.current + 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (dayExams.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "考试",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                SystemStatusBadge(text = "${dayExams.size} 场", tone = SystemTone.Warning)
            }
            dayExams.forEach { exam -> ExamDayCard(exam = exam, semesterStartMillis = semesterStartMillis) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "课程",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            SystemStatusBadge(text = "${dayCourses.size} 门", tone = SystemTone.Info)
        }
        if (dayCourses.isEmpty() && dayExams.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                SystemEmptyState(
                    title = "当天暂无安排",
                    message = "可以切换周次、学期，或查看本周课表。"
                )
            }
        }
        dayCourses.forEach { course ->
            DayCourseCard(course = course, periodTimes = periodTimes, onClick = { onCourseClick(course) })
        }
    }
}

/** 日视图课程卡片：课程名 +（节次 时间）+ 地点 · 教师。 */
@Composable
private fun DayCourseCard(
    course: ScheduleCourseUi,
    periodTimes: List<PeriodTimeUi>,
    onClick: () -> Unit
) {
    val start = periodTimes.firstOrNull { it.period == course.startPeriod }?.startTime ?: ""
    val end = periodTimes.firstOrNull { it.period == course.endPeriod }?.endTime ?: ""
    val span = if (course.endPeriod > course.startPeriod) "第${course.startPeriod}-${course.endPeriod}节" else "第${course.startPeriod}节"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(course.color.copy(alpha = 0.30f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (start.isNotBlank() || end.isNotBlank()) "$span  $start-$end" else span,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = course.color,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (course.location.isNotBlank() || course.teacher.isNotBlank()) {
                Text(
                    text = buildString {
                        if (course.location.isNotBlank()) append(course.location)
                        if (course.teacher.isNotBlank()) append(" · ${course.teacher}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 考试卡片：课程名 + 考试类型徽章 +（时间 · 第X周 星期X）+ 地点 · 座位。 */
@Composable
private fun ExamDayCard(exam: ExamItemUi, semesterStartMillis: Long?) {
    val weekLabel = com.buct.course.schedule.ScheduleDates.examWeekDayLabel(exam.examTime, semesterStartMillis)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(0.5.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val examWeek = com.buct.course.schedule.ScheduleDates.examWeek(exam.examTime, semesterStartMillis)
            val badgeText = com.buct.course.ui.screen.examBadgeLabel(exam.examName, examWeek)
            val badgeTone = com.buct.course.ui.screen.examBadgeTone(exam.examName, examWeek)
            // 长标签独占一行，避免把课程名压成省略号
            if (badgeText.length > 6) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = exam.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    SystemStatusBadge(text = badgeText, tone = badgeTone)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = exam.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    SystemStatusBadge(text = badgeText, tone = badgeTone)
                }
            }
            Text(
                text = exam.examTime.ifBlank { "未提供考试时间" } +
                    if (weekLabel != null) "  ·  $weekLabel" else "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (exam.location.isNotBlank() || exam.seatNumber.isNotBlank()) {
                Text(
                    text = buildString {
                        append(exam.location.ifBlank { "未提供地点" })
                        if (exam.seatNumber.isNotBlank()) append(" · 座位 ${exam.seatNumber}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

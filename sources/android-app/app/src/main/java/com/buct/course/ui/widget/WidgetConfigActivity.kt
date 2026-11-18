package com.buct.course.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buct.course.R
import com.buct.course.manager.AppThemeCoordinator
import com.buct.course.ui.system.InsetGroupedSection
import com.buct.course.ui.system.SystemIconButton
import com.buct.course.ui.system.SystemTopBar
import com.buct.course.social.FriendScheduleStore
import com.buct.course.ui.system.GlassTextField
import com.buct.course.ui.system.SystemPrimaryButton
import com.buct.course.ui.system.SystemSecondaryButton
import com.buct.course.ui.theme.CourseSelectorTheme
import com.buct.course.widget.ScheduleDayWidget
import com.buct.course.widget.ScheduleWidget
import com.buct.course.widget.WidgetConfigStore
import com.buct.course.widget.WidgetProviders
import com.buct.course.widget.WidgetScheduleSource

/** 标题输入上限（与 ScheduleSettingsManager.widgetTitle 的 take(12) 保持一致）。 */
private const val MAX_TITLE_LENGTH = 12

/**
 * 单个桌面小组件的配置页（Android 12+ 的 appwidget configure 入口）。
 *
 * 从桌面「添加小组件」或点击小组件标题进入：
 * - 只设置标题（实例级，覆盖全局默认；留空回落到全局默认/兜底文案）
 * - 保存后立即刷新该实例
 * - 首次添加时由系统把 EXTRA_APPWIDGET_ID 带进来，返回 RESULT_OK 才真正落桌面
 */
class WidgetConfigActivity : ComponentActivity() {

    companion object {
        const val EXTRA_IS_DAY = "extra_is_day"

        fun start(context: Context, appWidgetId: Int, isDay: Boolean) {
            context.startActivity(
                Intent(context, WidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    .putExtra(EXTRA_IS_DAY, isDay)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var isDay = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppThemeCoordinator.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // 桌面「添加小组件」进来时系统只带 EXTRA_APPWIDGET_ID，不带我们的 isDay；
        // 这里按实例所属 provider 反查，避免「今日课表」打开成周课表的配置页。
        isDay = if (intent?.hasExtra(EXTRA_IS_DAY) == true) {
            intent.getBooleanExtra(EXTRA_IS_DAY, false)
        } else {
            runCatching {
                val manager = AppWidgetManager.getInstance(this)
                WidgetProviders.dayIds(this, manager).contains(appWidgetId)
            }.getOrDefault(false)
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // 首次添加：默认按「取消添加」返回，用户点保存才改成 RESULT_OK
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        setContent {
            CourseSelectorTheme {
                WidgetConfigPage(
                    appWidgetId = appWidgetId,
                    isDay = isDay,
                    onCancel = { finish() },
                    onSave = { rawTitle, accountKey, source ->
                        save(rawTitle.trim(), accountKey, source)
                        finish()
                    }
                )
            }
        }
    }

    private fun save(title: String, accountKey: String?, source: String?) {
        val ctx = applicationContext
        WidgetConfigStore.setTitle(ctx, appWidgetId, title)
        WidgetConfigStore.setAccount(ctx, appWidgetId, accountKey)
        WidgetConfigStore.setSource(ctx, appWidgetId, source)

        val manager = AppWidgetManager.getInstance(ctx)
        runCatching {
            if (isDay) {
                ScheduleDayWidget.updateAppWidget(ctx, manager, appWidgetId)
            } else {
                ScheduleWidget.updateAppWidget(ctx, manager, appWidgetId)
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
            }
        }

        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
    }
}

@Composable
private fun WidgetConfigPage(
    appWidgetId: Int,
    isDay: Boolean,
    onCancel: () -> Unit,
    onSave: (String, String?, String?) -> Unit
) {
    val context = LocalContext.current
    val effectiveDefault = remember(appWidgetId, isDay) {
        WidgetConfigStore.resolveTitle(context, appWidgetId, isDay)
    }
    var title by remember(appWidgetId) {
        mutableStateOf(WidgetConfigStore.title(context, appWidgetId) ?: "")
    }
    val accounts = remember(appWidgetId) {
        runCatching {
            com.buct.course.manager.UserManager.getInstance().apply { init(context) }.savedAccounts
        }.getOrNull().orEmpty()
    }
    var accountKey by remember(appWidgetId) {
        mutableStateOf(WidgetConfigStore.accountKey(context, appWidgetId))
    }
    // 朋友课表：好友页收到并保存到固定目录的那些
    val friends = remember(appWidgetId) {
        runCatching { FriendScheduleStore.list(context) }.getOrNull().orEmpty()
    }
    var friendId by remember(appWidgetId) {
        mutableStateOf(WidgetScheduleSource.friendEntry(context, appWidgetId)?.id)
    }
    val friendLabel = friends.firstOrNull { it.id == friendId }?.label?.takeIf { it.isNotBlank() }
    val previewTitle = title.ifBlank {
        if (friendLabel != null) "${friendLabel}的课表" else effectiveDefault
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
    ) {
        SystemTopBar(
            title = if (isDay) "今日课表小组件" else "课表小组件",
            subtitle = "只对当前这一个小组件生效",
            navigationIcon = {
                SystemIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onCancel)
            }
        )

        Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 标题输入
                InsetGroupedSection(
                    header = "小组件标题",
                    footer = "在桌面点小组件标题可随时回来修改；最多 $MAX_TITLE_LENGTH 个字，留空则显示「$effectiveDefault」。"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        GlassTextField(
                            value = title,
                            onValueChange = {
                                if (it.length <= MAX_TITLE_LENGTH) title = it
                            },
                            placeholder = effectiveDefault,
                            singleLine = true
                        )
                    }
                }

                // 课表来源：本机账号 / 好友传来的课表
                if (friends.isNotEmpty()) {
                    InsetGroupedSection(
                        header = "显示谁的课表",
                        footer = if (friendId == null) {
                            "好友页收到并保存的课表会列在这里，可以让这个小组件专门显示朋友的课表。"
                        } else {
                            "只显示课表，不含成绩和考试；朋友课表被删掉后自动回到我的课表。"
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = friendId == null,
                                    onClick = { friendId = null },
                                    label = { Text("我的课表") }
                                )
                                friends.forEach { entry ->
                                    FilterChip(
                                        selected = friendId == entry.id,
                                        onClick = { friendId = entry.id },
                                        label = { Text("${entry.label}的课表") }
                                    )
                                }
                            }
                        }
                    }
                }

                // 账号选择（多账号时才值得显示；选了朋友课表就不需要）
                if (accounts.size > 1 && friendId == null) {
                    InsetGroupedSection(
                        header = "显示哪个账号的课表",
                        footer = "选定后，切换 App 内账号不会影响这个小组件。"
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = accountKey == null,
                                onClick = { accountKey = null },
                                label = { Text("跟随当前账号") }
                            )
                            // 同一个人的多个账号会显示成两个一模一样的名字，加学号后 4 位区分
                            val duplicateNames = accounts.groupBy { it.getDisplayName() }
                                .filterValues { it.size > 1 }.keys
                            accounts.forEach { record ->
                                val name = record.getDisplayName()
                                val label = if (name in duplicateNames) {
                                    val tail = record.getAccountIdText().takeLast(4)
                                    if (tail.isBlank()) name else "$name·$tail"
                                } else name
                                FilterChip(
                                    selected = accountKey == record.key,
                                    onClick = { accountKey = record.key },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                // 预览：按小组件真实排版画一个缩小版，比两行文字直观
                InsetGroupedSection(
                    header = "预览",
                    footer = if (friendLabel != null) "内容：${friendLabel}的课表" else "内容：本机账号课表"
                ) {
                    WidgetMiniPreview(
                        title = previewTitle,
                        isDay = isDay,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.weight(1f)) {
                SystemSecondaryButton(
                    text = "取消",
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(Modifier.weight(1f)) {
                SystemPrimaryButton(
                    text = "保存",
                    onClick = {
                        val source = friendId?.let { WidgetScheduleSource.encodeFriend(it) }
                        onSave(title, accountKey, source)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

/**
 * 小组件缩小版预览。
 *
 * 之前这里只显示两行文字，用户以为是「预览坏了」；实际桌面小组件是网格 + 课程卡片，
 * 这里按同样的构图画一个静态示意，让用户在保存前就能看出大致长相。
 */
@Composable
private fun WidgetMiniPreview(
    title: String,
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val blockColors = listOf(
        Color(0xFF5C6BC0), Color(0xFF42A5F5), Color(0xFF66BB6A),
        Color(0xFFFFA726), Color(0xFFAB47BC), Color(0xFF26C6DA)
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title.ifBlank { if (isDay) "今日课表" else "课表" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )

        if (isDay) {
            // 今日课表：几条「节次 + 课程」横条
            repeat(3) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${index * 2 + 1}-${index * 2 + 2}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(blockColors[index % blockColors.size].copy(alpha = 0.75f))
                    )
                }
            }
        } else {
            // 周课表：5 列 × 4 行迷你网格
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(5) { col ->
                            val filled = (row * 5 + col) % 3 == 0
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (filled) blockColors[(row + col) % blockColors.size].copy(alpha = 0.75f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

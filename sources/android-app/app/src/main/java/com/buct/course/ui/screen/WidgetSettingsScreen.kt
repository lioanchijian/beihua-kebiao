package com.buct.course.ui.screen

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.buct.course.manager.UserManager
import com.buct.course.ui.system.InsetGroupedRow
import com.buct.course.ui.system.InsetGroupedSection
import com.buct.course.ui.system.SystemIconButton
import com.buct.course.ui.system.SystemTopBar
import com.buct.course.ui.widget.WidgetConfigActivity
import com.buct.course.widget.ScheduleDayWidget
import com.buct.course.widget.ScheduleWidget
import com.buct.course.widget.WidgetConfigStore
import com.buct.course.widget.WidgetProviders
import com.buct.course.widget.WidgetScheduleSource

/** 一个已经摆在桌面上的小组件实例。 */
internal data class WidgetInstance(
    val appWidgetId: Int,
    val isDay: Boolean
)

/**
 * 列表一行要显示的内容。
 *
 * 标题/副标题在「刷新时」就解析成字符串存进状态，不能放在组合里现算：
 * 从配置页返回时实例列表本身没变（同一个 appWidgetId），状态相等不会触发重组，
 * 现算的文字就会一直是旧值，必须退出设置再进才更新。
 */
private data class WidgetRowData(
    val instance: WidgetInstance,
    val title: String,
    val subtitle: String
)

/** 枚举本 App 当前所有桌面小组件实例（课表 + 今日课表两种 provider）。 */
internal fun listWidgetInstances(context: Context): List<WidgetInstance> {
    val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return emptyList()
    // 桌面上实际注册的是旧包名的兼容壳，这里必须按 WidgetProviders 把新旧组件都数上，
    // 否则用户明明摆着小组件，管理页却显示「还没有小组件」。
    val week = WidgetProviders.weekIds(context, manager).map { WidgetInstance(it, isDay = false) }
    val day = WidgetProviders.dayIds(context, manager).map { WidgetInstance(it, isDay = true) }
    return (week + day)
        .distinctBy { it.appWidgetId }
        .sortedWith(compareBy({ it.isDay }, { it.appWidgetId }))
}

/**
 * 桌面小组件管理页。
 *
 * 设置页入口进来后能看到桌面上到底摆了几个小组件，每个实例单独设置：
 * - 显示哪个账号的课表（空 = 跟随 App 当前账号）
 * - 小组件标题（留空用 App 里的默认名称）
 *
 * 数据源是 AppWidgetManager 的实时查询，不是本地缓存，所以在桌面删掉实例后回来就少一个。
 */
@Composable
fun WidgetSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var rows by remember { mutableStateOf(emptyList<WidgetRowData>()) }

    // 关键：标题/副标题在这里就解析好。每次回到本页（含从配置页返回）都重新算一遍，
    // 即使实例 id 没变，文字变了也会触发重组，不用退出设置再进。
    DisposableEffect(lifecycleOwner) {
        fun reload() {
            val accounts = loadAccounts(context)
            rows = loadRows(context, accounts)
            WidgetConfigStore.prune(context, rows.map { it.instance.appWidgetId }.toSet())
        }
        reload()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reload()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        SystemTopBar(
            title = "桌面小组件",
            subtitle = "每个小组件都能单独设置名称、账号或朋友的课表",
            navigationIcon = { SystemIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack) }
        )

        Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                InsetGroupedSection(
                    header = if (rows.isEmpty()) "桌面上还没有小组件" else "桌面上已添加 ${rows.size} 个小组件",
                    footer = if (rows.isEmpty()) null else "点任意一个就能改它的标题和来源，互不影响。"
                ) {
                    rows.forEachIndexed { index, row ->
                        InsetGroupedRow(
                            title = row.title,
                            subtitle = row.subtitle,
                            icon = if (row.instance.isDay) Icons.Outlined.CalendarToday else Icons.Outlined.ViewWeek,
                            iconTint = if (row.instance.isDay) Color(0xFF34C759) else Color(0xFF5E5CE6),
                            showDivider = index != rows.lastIndex,
                            onClick = {
                                WidgetConfigActivity.start(context, row.instance.appWidgetId, row.instance.isDay)
                            },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "设置这个小组件",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }

                if (rows.isEmpty()) {
                    InsetGroupedSection(header = "怎么添加") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "1. 回到桌面，长按空白处\n2. 选「小组件」\n3. 找到「北化课表」，把课表小组件拖到桌面",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = "想再放一个？回到桌面长按空白处 → 小组件（或「桌面卡片」）→ 找到「北化课表」→ 拖到桌面即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

}

private data class AccountOption(val key: String, val label: String)

private fun loadAccounts(context: Context): List<AccountOption> = runCatching {
    UserManager.getInstance().apply { init(context) }.savedAccounts.map {
        AccountOption(it.key, it.getDisplayName())
    }
}.getOrNull().orEmpty()

/** 组装列表数据：每个实例的标题与来源说明都在这里定稿。 */
private fun loadRows(context: Context, accounts: List<AccountOption>): List<WidgetRowData> =
    listWidgetInstances(context).mapIndexed { index, instance ->
        val type = if (instance.isDay) "今日课表" else "课表"
        WidgetRowData(
            instance = instance,
            title = runCatching {
                WidgetConfigStore.resolveTitle(context, instance.appWidgetId, instance.isDay)
            }.getOrDefault(""),
            subtitle = "$type · 第 ${index + 1} 个 · ${sourceLabel(context, instance, accounts)}"
        )
    }

/** 第三段说明：配置页里选了朋友课表就显示朋友，否则显示跟随/指定的账号。 */
private fun sourceLabel(
    context: Context,
    instance: WidgetInstance,
    accounts: List<AccountOption>
): String {
    WidgetScheduleSource.friendEntry(context, instance.appWidgetId)?.let { entry ->
        return "${entry.label}的课表"
    }
    val key = WidgetConfigStore.accountKey(context, instance.appWidgetId)
        ?: return "跟随当前账号"
    return accounts.firstOrNull { it.key == key }?.label ?: "指定账号"
}

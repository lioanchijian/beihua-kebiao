package com.tyust.course.ui.screen

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tyust.course.manager.UserManager
import com.tyust.course.ui.widget.WidgetConfigActivity
import com.tyust.course.widget.ScheduleDayWidget
import com.tyust.course.widget.ScheduleWidget
import com.tyust.course.widget.WidgetConfigStore

/** 一个已经摆在桌面上的小组件实例。 */
internal data class WidgetInstance(
    val appWidgetId: Int,
    val isDay: Boolean
)

/** 枚举本 App 当前所有桌面小组件实例（课表 + 今日课表两种 provider）。 */
internal fun listWidgetInstances(context: Context): List<WidgetInstance> {
    val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return emptyList()
    fun idsOf(clazz: Class<*>): IntArray = runCatching {
        manager.getAppWidgetIds(ComponentName(context, clazz))
    }.getOrNull() ?: IntArray(0)

    val week = idsOf(ScheduleWidget::class.java).map { WidgetInstance(it, isDay = false) }
    val day = idsOf(ScheduleDayWidget::class.java).map { WidgetInstance(it, isDay = true) }
    return (week + day).sortedWith(compareBy({ it.isDay }, { it.appWidgetId }))
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
    var instances by remember { mutableStateOf(listWidgetInstances(context)) }
    var accounts by remember { mutableStateOf(loadAccounts(context)) }

    // 每次回到本页重新查一次：用户可能在桌面添加/删除了小组件，或改了账号
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                instances = listWidgetInstances(context)
                accounts = loadAccounts(context)
                WidgetConfigStore.prune(context, instances.map { it.appWidgetId }.toSet())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column {
                Text(
                    text = "桌面小组件",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "每个小组件都能单独设置名称和账号",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CountCard(count = instances.size)

                if (instances.isEmpty()) {
                    EmptyHint()
                } else {
                    instances.forEachIndexed { index, instance ->
                        WidgetRow(
                            index = index + 1,
                            instance = instance,
                            title = WidgetConfigStore.resolveTitle(context, instance.appWidgetId, instance.isDay),
                            accountLabel = accountLabel(context, instance, accounts),
                            onClick = {
                                WidgetConfigActivity.start(context, instance.appWidgetId, instance.isDay)
                            }
                        )
                    }
                }

                Text(
                    text = "想再放一个？回到桌面长按空白处 → 小组件 → 找到「北化课表」，添加后会自动跳到设置页。",
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

private fun accountLabel(
    context: Context,
    instance: WidgetInstance,
    accounts: List<AccountOption>
): String {
    val key = WidgetConfigStore.accountKey(context, instance.appWidgetId)
        ?: return "跟随当前账号"
    return accounts.firstOrNull { it.key == key }?.label ?: "指定账号"
}

@Composable
private fun CountCard(count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (count == 0) "桌面上还没有小组件" else "桌面上已添加 $count 个小组件",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (count == 0) {
                "添加后可以在这里统一改名称、换账号。"
            } else {
                "点任意一个就能改它的标题和显示的账号，互不影响。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "怎么添加",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "1. 回到桌面，长按空白处\n2. 选「小组件」\n3. 找到「北化课表」，把课表小组件拖到桌面",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WidgetRow(
    index: Int,
    instance: WidgetInstance,
    title: String,
    accountLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    RoundedCornerShape(12.dp)
                )
                .padding(8.dp)
        ) {
            Icon(
                imageVector = if (instance.isDay) Icons.Outlined.CalendarToday else Icons.Outlined.ViewWeek,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "${if (instance.isDay) "今日课表" else "课表"} · 第 $index 个 · $accountLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "设置这个小组件",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.buct.course.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buct.course.BottomNavItem
import com.buct.course.manager.BottomNavPreferences
import com.buct.course.manager.StartupPage
import com.buct.course.ui.system.SystemDialog
import com.buct.course.ui.system.SystemPrimaryButton
import com.buct.course.ui.system.SystemSecondaryButton

/**
 * 底部导航设置的开关状态。
 *
 * 刻意放在页面之外：调整顺序 / 隐藏页面会改变底栏下标，设置页会被重新挂载，
 * 弹窗若跟着页面走就会每点一下自动关闭。提到主界面层级后可以连续操作。
 */
object BottomNavSettingsDialogState {
    var visible by androidx.compose.runtime.mutableStateOf(false)
}

/**
 * 底部导航设置：调整顺序 + 隐藏不用的页面。
 *
 * 「设置」固定保留且不可隐藏——否则用户隐藏后没有任何入口能改回来，
 * 只能清数据或重装才能恢复。
 */
@Composable
fun BottomNavSettingsDialog(onDismiss: () -> Unit) {
    // 订阅配置变化：任何一次 move / setVisible 都会让整页重组
    val revision = BottomNavPreferences.revision
    val order = remember(revision) { BottomNavPreferences.order() }
    val hidden = remember(revision) { BottomNavPreferences.hidden() }

    SystemDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "底部导航",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "完成",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            SystemSecondaryButton(
                text = "恢复默认",
                onClick = { BottomNavPreferences.reset() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "用上下箭头调整顺序，关掉开关即可在底栏隐藏该页。设置页不可隐藏。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            order.forEachIndexed { index, page ->
                val item = BottomNavItem.of(page)
                val isSettings = page == StartupPage.Settings
                val visible = page !in hidden
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (visible) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = page.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (visible) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { BottomNavPreferences.move(page, -1) },
                        enabled = index > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "上移",
                            tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = { BottomNavPreferences.move(page, 1) },
                        enabled = index < order.lastIndex
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "下移",
                            tint = if (index < order.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    Switch(
                        checked = visible || isSettings,
                        onCheckedChange = { BottomNavPreferences.setVisible(page, it) },
                        enabled = !isSettings
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "隐藏的页面只是从底栏移除，数据不会删除；重新打开开关即可恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

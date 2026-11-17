package com.tyust.course.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tyust.course.manager.UserManager
import com.tyust.course.ui.system.SystemDialog
import com.tyust.course.ui.system.SystemPrimaryButton
import com.tyust.course.ui.system.SystemSecondaryButton

/** 顶栏账号芯片：色块 + 名字，点开切换面板。 */
@Composable
fun AccountChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    collapse: Float = 0f
) {
    val tint = accountColor(label)
    Row(
        modifier = modifier
            .height(30.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f - 0.2f * collapse),
                RoundedCornerShape(15.dp)
            )
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(18.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                text = label.take(1).ifBlank { "同" },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label.take(6).ifBlank { "切换账号" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = "切换账号",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
    }
}

/** 切换账号面板：列出所有已保存账号，点一行即切换。 */
@Composable
fun AccountSwitcherDialog(
    accounts: List<UserManager.AccountRecord>,
    currentKey: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    SystemDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "切换课表账号",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "管理账号",
                onClick = onManage,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            SystemSecondaryButton(
                text = "关闭",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (accounts.isEmpty()) {
                Text(
                    text = "还没有保存其他账号，到「设置 → 账号管理」里添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                accounts.forEach { record ->
                    val current = record.key == currentKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .background(
                                if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = !current) { onSelect(record.key) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(30.dp).background(accountColor(record.getDisplayName()), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = record.getDisplayName().take(1),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = record.getDisplayName(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = listOf(record.getAccountIdText(), record.schoolName)
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (current) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "当前账号",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "切换后课表、成绩、考试会自动换成该账号的数据。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 由名字稳定派生一个柔和的色块颜色（同一个账号每次颜色一致）。 */
internal fun accountColor(seed: String): Color {
    if (seed.isBlank()) return Color(0xFF8E8E93)
    val palette = listOf(
        Color(0xFF0A84FF), Color(0xFF34C759), Color(0xFFFF9F0A), Color(0xFFBF5AF2),
        Color(0xFF32ADE6), Color(0xFFFF375F), Color(0xFF5E5CE6), Color(0xFF30B0C7)
    )
    return palette[(seed.hashCode() and 0x7FFFFFFF) % palette.size]
}

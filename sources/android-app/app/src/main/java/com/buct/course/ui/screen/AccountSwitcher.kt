package com.buct.course.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buct.course.manager.UserManager
import com.buct.course.ui.system.glass.adaptiveGlassChip
import com.buct.course.ui.system.glass.applyChipContentDeformation
import com.buct.course.ui.system.glass.rememberInteractiveOptics
import com.buct.course.ui.system.SystemDialog
import com.buct.course.ui.system.SystemPrimaryButton
import com.buct.course.ui.system.SystemSecondaryButton

/**
 * 顶栏账号芯片：玻璃胶囊 + 6dp 色点 + 名字 + 下拉箭头。
 *
 * 与右侧「今天/本周」「日/周」同一套材质与尺寸体系（展开 34dp、折叠 30dp），
 * 不再用扁平 surfaceVariant 药丸，避免和周边的玻璃控件风格割裂。
 */
@Composable
fun AccountChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    collapse: Float = 0f
) {
    val tint = accountColor(label)
    val backdrop = com.buct.course.ui.system.LocalControlBackdrop.current
    val optics = rememberInteractiveOptics()
    val alpha = (1f - collapse * 1.4f).coerceIn(0.25f, 1f)
    val chipHeight = (34f - 4f * collapse.coerceIn(0f, 1f)).dp

    Row(
        modifier = modifier
            .height(chipHeight)
            .adaptiveGlassChip(backdrop, CircleShape, optics, enabled = true, interactive = true)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                this.alpha = alpha
                applyChipContentDeformation(
                    optics,
                    2.dp.toPx(),
                    0.02f,
                    0.02f,
                    com.buct.course.ui.system.GlassRecipe.ChipContentDeformDamping
                )
            }
            .padding(start = 9.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).background(tint, CircleShape))
        Text(
            text = label.take(6).ifBlank { "切换账号" },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = "切换账号",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
    }
}

/** 切换账号面板：本机账号 + 朋友传来的课表，点一行即切换。 */
@Composable
fun AccountSwitcherDialog(
    accounts: List<UserManager.AccountRecord>,
    currentKey: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
    friendSchedules: List<com.buct.course.social.FriendScheduleStore.Entry> = emptyList(),
    currentFriendId: String? = null,
    onSelectFriend: (String) -> Unit = {}
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
                    val current = record.key == currentKey && currentFriendId == null
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

            // 朋友传来的课表：保存到本机后自动出现在这里，选中即只读查看
            if (friendSchedules.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "朋友的课表",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                friendSchedules.forEach { entry ->
                    val current = currentFriendId == entry.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .background(
                                if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = !current) { onSelectFriend(entry.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(30.dp).background(accountColor(entry.label), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = entry.label.take(1),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${entry.label}的课表",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = entry.subtitle.ifBlank { "只读 · 朋友传来" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (current) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "正在查看",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "朋友的课表只读，不会覆盖你自己的数据。",
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

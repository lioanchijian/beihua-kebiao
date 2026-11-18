package com.buct.course.ui.screen

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buct.course.manager.ScheduleSettingsManager
import com.buct.course.ui.system.GlassTextField
import com.buct.course.ui.system.SystemDialog
import com.buct.course.ui.system.SystemPrimaryButton
import com.buct.course.ui.system.SystemSecondaryButton
import kotlin.math.roundToInt

/**
 * 课表格子高度设置。
 *
 * 与字号设置同一套交互：无级滑杆 + 右侧输入框 + 下方七档吸附。
 * 中途只预览不落盘，松手/点档位/输完才写入，避免滑杆拖动时反复写盘。
 */
@Composable
fun PeriodHeightDialog(
    fallbackHeight: Int,
    onDismiss: () -> Unit
) {
    val manager = ScheduleSettingsManager.getInstance()
    val initial = if (manager.periodHeight > 0) manager.periodHeight else fallbackHeight

    var current by remember { mutableIntStateOf(initial) }
    var draft by remember { mutableStateOf(initial.toString()) }

    val minHeight = ScheduleSettingsManager.MIN_PERIOD_HEIGHT
    val maxHeight = ScheduleSettingsManager.MAX_PERIOD_HEIGHT

    fun applyPreset(value: Int) {
        val clamped = value.coerceIn(minHeight, maxHeight)
        current = clamped
        draft = clamped.toString()
        manager.periodHeight = clamped
    }

    /**
     * 输入框确认：把草稿修正到合法区间再落盘。
     * 只在「完成 / 收起弹窗」时调用——打字过程中不调用，
     * 否则用户删空再输入会被立刻改写成边界值，接着输入就变成 320 之类。
     */
    fun commitDraft() {
        val typed = draft.toIntOrNull()
        val target = if (typed == null) current else typed.coerceIn(minHeight, maxHeight)
        current = target
        draft = target.toString()
        manager.periodHeight = target
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp
    // 顶栏 + 星期行 + 底部导航大致占 230dp，剩下的由格子分
    val visiblePeriods = ((screenHeight - 230).coerceAtLeast(120) / current.toFloat())
        .roundToInt().coerceIn(3, 30)

    SystemDialog(
        onDismissRequest = {
            commitDraft()
            onDismiss()
        },
        title = {
            Text(
                text = "格子高度",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "完成",
                onClick = {
                    commitDraft()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            SystemSecondaryButton(
                text = "恢复默认",
                onClick = {
                    manager.resetPeriodHeight()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 预览：一块真实高度的课程格，直观看到松紧
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(current.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (current < 56) {
                    // 格子过矮时只留课程名，避免两行文字挤出框
                    Text(
                        text = "高等数学 A · 教 101",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                } else {
                    Column {
                        Text(
                            text = "高等数学 A",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "教 101 · 第 3-4 节",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = current.toFloat(),
                    onValueChange = {
                        current = it.roundToInt()
                        draft = current.toString()
                    },
                    onValueChangeFinished = { manager.periodHeight = current },
                    valueRange = minHeight.toFloat()..maxHeight.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                GlassTextField(
                    value = draft,
                    onValueChange = { raw ->
                        // 只保留数字，且**不回写**成夹取后的值：
                        // 打字过程中顺手改写会让"删空再输"变成 32 → 320 → 112 的跳变。
                        val digits = raw.filter { it.isDigit() }.take(3)
                        draft = digits
                        digits.toIntOrNull()?.let { typed ->
                            current = typed.coerceIn(minHeight, maxHeight)
                        }
                    },
                    modifier = Modifier.width(62.dp),
                    placeholder = "84",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    minHeight = 44.dp
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "dp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ScheduleSettingsManager.periodHeightPresets.forEach { preset ->
                    PeriodHeightChip(
                        selected = current == preset,
                        label = preset.toString(),
                        onClick = { applyPreset(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "当前 ${current}dp，一屏约 $visiblePeriods 节。设置后格子按此高度固定，课程信息会按可用空间省略；点「恢复默认」回到随内容自适应。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PeriodHeightChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

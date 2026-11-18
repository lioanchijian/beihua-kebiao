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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buct.course.manager.FontScaleManager
import com.buct.course.ui.system.GlassTextField
import com.buct.course.ui.system.SystemDialog
import com.buct.course.ui.system.SystemPrimaryButton
import com.buct.course.ui.system.SystemSecondaryButton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 全局字体大小设置。
 *
 * 交互：无级滑杆 + 右侧百分比输入框 + 下方五档吸附。
 * 拖动过程中实时预览（[FontScaleManager.preview]），松手才落盘，避免拖动时反复写 SharedPreferences。
 */
@Composable
fun FontSizeSettingsDialog(onDismiss: () -> Unit) {
    var current by remember { mutableFloatStateOf(FontScaleManager.scale) }
    var percentText by remember { mutableStateOf(FontScaleManager.percentOf(current).toString()) }

    val minPercent = (FontScaleManager.MIN_SCALE * 100f).roundToInt()
    val maxPercent = (FontScaleManager.MAX_SCALE * 100f).roundToInt()

    fun applyPercent(text: String) {
        val value = text.toIntOrNull() ?: return
        val clamped = value.coerceIn(minPercent, maxPercent)
        current = FontScaleManager.fromPercent(clamped)
        FontScaleManager.preview(current)
    }

    fun pickPreset(scale: Float) {
        current = scale
        percentText = FontScaleManager.percentOf(scale).toString()
        FontScaleManager.commit(scale)
    }

    /** 收起弹窗前把输入框修正到合法值，避免停在「800%」这种显示。 */
    fun commitAll() {
        val typed = percentText.toIntOrNull()
        val target = if (typed == null) {
            current
        } else {
            FontScaleManager.fromPercent(typed.coerceIn(minPercent, maxPercent))
        }
        current = target
        percentText = FontScaleManager.percentOf(target).toString()
        FontScaleManager.commit(target)
    }

    SystemDialog(
        onDismissRequest = {
            commitAll()
            onDismiss()
        },
        title = {
            Text(
                text = "字体大小",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "完成",
                onClick = {
                    commitAll()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            SystemSecondaryButton(
                text = "恢复默认",
                onClick = { pickPreset(1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FontSizePreviewCard()

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = current,
                    onValueChange = {
                        current = it
                        percentText = FontScaleManager.percentOf(it).toString()
                        FontScaleManager.preview(it)
                    },
                    onValueChangeFinished = { FontScaleManager.commit(current) },
                    valueRange = FontScaleManager.MIN_SCALE..FontScaleManager.MAX_SCALE,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                GlassTextField(
                    value = percentText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(3)
                        percentText = digits
                        if (digits.isNotEmpty()) applyPercent(digits)
                    },
                    modifier = Modifier.width(62.dp),
                    placeholder = "100",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    minHeight = 44.dp
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FontScaleManager.presets.forEach { preset ->
                    FontScaleChip(
                        selected = abs(current - preset.scale) < 0.005f,
                        label = "${preset.percent}%",
                        onClick = { pickPreset(preset.scale) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "滑杆可无级调节 ${minPercent}%–${maxPercent}%，点下方档位可快速吸附。只影响本应用显示，不改系统字号，桌面小组件不受影响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 预览卡片：用真实课表卡片的层级（课名 / 教室·教师 / 周次）呈现当前字号。 */
@Composable
private fun FontSizePreviewCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "高等数学 A",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "教 101 · 张老师",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "1-16 周 · 第 3-4 节",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

/** 五档吸附按钮：等宽排列，选中态跟随滑杆位置。 */
@Composable
private fun FontScaleChip(
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

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buct.course.manager.ScheduleSettingsManager
import com.buct.course.ui.system.GlassTextField
import com.buct.course.ui.system.SystemDialog
import com.buct.course.ui.system.SystemPrimaryButton
import com.buct.course.ui.system.SystemSecondaryButton
import com.buct.course.widget.ScheduleDayWidget
import com.buct.course.widget.ScheduleWidget
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 桌面小组件字体大小设置。
 *
 * 交互与「字体大小」一致：无级滑杆 + 右侧百分比输入框 + 下方五档吸附。
 * 与 App 内字号不同，小组件是 RemoteViews，只能整体重建刷新，
 * 所以松手/点档位后才提交并请求两种小组件重绘。
 */
@Composable
fun WidgetFontScaleDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = ScheduleSettingsManager.getInstance().apply { init(context) }

    var current by remember { mutableFloatStateOf(manager.widgetFontScale) }
    var percentText by remember { mutableStateOf(percentOf(current).toString()) }

    val minPercent = (ScheduleSettingsManager.WIDGET_FONT_MIN * 100f).roundToInt()
    val maxPercent = (ScheduleSettingsManager.WIDGET_FONT_MAX * 100f).roundToInt()

    fun applyPercent(text: String) {
        val value = text.toIntOrNull() ?: return
        current = (value.coerceIn(minPercent, maxPercent)) / 100f
    }

    fun commit() {
        val typed = percentText.toIntOrNull()
        val target = if (typed == null) {
            current
        } else {
            (typed.coerceIn(minPercent, maxPercent)) / 100f
        }
        current = target
        percentText = percentOf(target).toString()
        manager.widgetFontScale = target
    }

    fun commitAndRefresh() {
        commit()
        refreshWidgets(context)
    }

    SystemDialog(
        onDismissRequest = {
            commitAndRefresh()
            onDismiss()
        },
        title = {
            Text(
                text = "小组件字体大小",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "完成",
                onClick = {
                    commitAndRefresh()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            SystemSecondaryButton(
                text = "恢复默认",
                onClick = {
                    current = 1f
                    percentText = "100"
                    manager.widgetFontScale = 1f
                    // 恢复默认要立即重绘小组件，否则用户会以为恢复没生效
                    refreshWidgets(context)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 预览卡片：与小组件同构（课名 / 时间 / 教室），字号跟随滑杆实时变化
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
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp * current),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "第 3-4 节 · 08:00",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp * current),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "教 101 · 张老师",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp * current),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = current,
                    onValueChange = {
                        current = it
                        percentText = percentOf(it).toString()
                    },
                    onValueChangeFinished = {
                        manager.widgetFontScale = current
                        refreshWidgets(context)
                    },
                    valueRange = ScheduleSettingsManager.WIDGET_FONT_MIN..ScheduleSettingsManager.WIDGET_FONT_MAX,
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
                ScheduleSettingsManager.WIDGET_FONT_SNAPS.forEachIndexed { index, snap ->
                    WidgetFontChip(
                        selected = abs(current - snap) < 0.005f,
                        // 只放档名不放百分比：两三个字在等宽按钮里才能居中，
                        // 具体数值看右侧输入框
                        label = ScheduleSettingsManager.WIDGET_FONT_SNAP_LABELS[index],
                        onClick = {
                            current = snap
                            percentText = percentOf(snap).toString()
                            manager.widgetFontScale = snap
                            refreshWidgets(context)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "调整桌面小组件里课表的字号，${minPercent}%–${maxPercent}% 无级可调，调整立即生效并重绘小组件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun percentOf(scale: Float): Int = (scale * 100f).roundToInt()

/** 两种小组件都重绘：周视图走 REFRESH 广播，日视图走「回到今天」重置（顺带重建）。 */
private fun refreshWidgets(context: android.content.Context) {
    runCatching {
        context.sendBroadcast(
            android.content.Intent(context, ScheduleWidget::class.java)
                .setAction("com.buct.course.widget.REFRESH_WIDGET")
        )
        context.sendBroadcast(
            android.content.Intent(context, ScheduleDayWidget::class.java)
                .setAction(ScheduleDayWidget.ACTION_DAY_TODAY)
        )
    }
}

/** 五档吸附按钮，与「字体大小」弹窗同款。 */
@Composable
private fun WidgetFontChip(
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

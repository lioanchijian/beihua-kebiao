package com.buct.course.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.buct.course.model.CustomExam
import com.buct.course.model.ExamTemplate
import com.buct.course.ui.system.GlassDateTimePickerDialog
import com.buct.course.ui.system.GlassTextField
import com.buct.course.ui.system.SystemDialog
import com.buct.course.ui.system.SystemPrimaryButton
import com.buct.course.ui.system.SystemSecondaryButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 新增 / 编辑自建考试。
 *
 * 模板只负责把「名称 + 最近一次开考时间 + 时长」填进来，填完仍可逐项修改，
 * 因为这类考试每年的具体日期都会变。
 */
@Composable
fun ExamEditorDialog(
    existing: CustomExam? = null,
    onSave: (CustomExam) -> Unit,
    onDelete: ((CustomExam) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("M月d日 HH:mm", Locale.CHINA) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var course by remember { mutableStateOf(existing?.course.orEmpty()) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var seat by remember { mutableStateOf(existing?.seat.orEmpty()) }
    var startAt by remember { mutableStateOf(existing?.startAt ?: 0L) }
    var endAt by remember { mutableStateOf(existing?.endAt ?: 0L) }
    var tag by remember { mutableStateOf(existing?.tag ?: "自建") }
    var offsets by remember { mutableStateOf(existing?.remindOffsets ?: CustomExam.DEFAULT_REMIND_OFFSETS) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    fun applyTemplate(template: ExamTemplate) {
        title = template.name
        tag = template.name
        val occurrence = template.nextOccurrence()
        if (occurrence != null) {
            startAt = occurrence.first
            endAt = occurrence.second
        }
    }

    fun build(): CustomExam = (existing ?: CustomExam(
        title = "",
        startAt = 0L
    )).copy(
        title = title.trim(),
        course = course.trim(),
        location = location.trim(),
        seat = seat.trim(),
        startAt = startAt,
        endAt = endAt,
        tag = tag.ifBlank { "自建" },
        remindOffsets = offsets.sorted()
    )

    SystemDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "添加考试" else "编辑考试",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "保存",
                onClick = { onSave(build()) },
                enabled = title.isNotBlank() && startAt > 0,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            if (existing != null && onDelete != null) {
                SystemSecondaryButton(
                    text = "删除",
                    onClick = { onDelete(existing) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                SystemSecondaryButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel("快速填充")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExamTemplate.all.forEach { template ->
                    androidx.compose.material3.FilterChip(
                        selected = tag == template.name,
                        onClick = { applyTemplate(template) },
                        label = {
                            Text(
                                text = template.name.substringBefore("（"),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    )
                }
            }
            Text(
                text = ReviewHint(tag),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionLabel("考试名称")
            GlassTextField(
                value = title,
                onValueChange = { title = it.take(30) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "例如：大学英语四级",
                minHeight = 48.dp
            )

            SectionLabel("开始时间")
            PickerRow(text = if (startAt > 0) formatter.format(Date(startAt)) else "请选择") {
                showStartPicker = true
            }

            SectionLabel("结束时间（可选）")
            PickerRow(text = if (endAt > 0) formatter.format(Date(endAt)) else "未设置") {
                showEndPicker = true
            }

            SectionLabel("地点 / 座位")
            GlassTextField(
                value = location,
                onValueChange = { location = it.take(30) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "考场，如 教 101",
                minHeight = 48.dp
            )
            GlassTextField(
                value = seat,
                onValueChange = { seat = it.take(12) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "座位号（可选）",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                minHeight = 48.dp
            )

            SectionLabel("提醒")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CustomExam.REMIND_PRESETS.forEach { (minutes, label) ->
                    val selected = minutes in offsets
                    androidx.compose.material3.FilterChip(
                        selected = selected,
                        onClick = {
                            offsets = if (selected) offsets - minutes else offsets + minutes
                        },
                        label = {
                            Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    )
                }
            }
            Text(
                text = "可多选；不选则不提醒。提醒依赖通知与精确闹钟权限，被系统关闭时不会响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            androidx.compose.material3.TextButton(
                onClick = {
                    com.buct.course.schedule.CalendarWriter.createEvent(
                        context = context,
                        title = title.trim(),
                        startAt = startAt,
                        endAt = endAt,
                        location = location.trim(),
                        note = listOf(course, seat.takeIf { it.isNotBlank() }?.let { "座位 $it" })
                            .filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                    )
                },
                enabled = title.isNotBlank() && startAt > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("添加到系统日历")
            }
        }
    }

    if (showStartPicker) {
        GlassDateTimePickerDialog(
            title = "选择开始时间",
            initialMillis = if (startAt > 0) startAt else System.currentTimeMillis(),
            onConfirm = {
                startAt = it
                if (endAt in 1 until it) endAt = 0L
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        GlassDateTimePickerDialog(
            title = "选择结束时间",
            initialMillis = if (endAt > 0) endAt else (startAt + 2 * 60 * 60 * 1000L),
            onConfirm = {
                endAt = it
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PickerRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 14.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun ReviewHint(tag: String): String {
    val template = ExamTemplate.all.firstOrNull { it.name == tag }
    return template?.hint ?: "模板只填入大致时间，请按准考证核对后修改"
}

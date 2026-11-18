package com.buct.course.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.buct.course.manager.UserManager
import com.buct.course.network.EmptyClassroom
import com.buct.course.network.EmptyClassroomApi
import com.buct.course.network.SessionExpiredException
import com.buct.course.ui.system.SessionExpiredHint
import com.buct.course.utils.SessionRepair
import kotlinx.coroutines.CancellationException

@Composable
fun EmptyClassroomDialog(
    semester: String,
    week: Int,
    day: Int,
    period: Int,
    onDismiss: () -> Unit
) {
    val school = remember { UserManager.getInstance().currentSchool }
    val context = LocalContext.current
    var campusId by remember { mutableIntStateOf(2) }
    var selectedBuilding by remember { mutableStateOf("全部") }
    var selectedFloor by remember { mutableStateOf("全部") }
    var classrooms by remember { mutableStateOf<List<EmptyClassroom>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var expiredCode by remember { mutableStateOf(0) }
    var repairing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val dayText = listOf("一", "二", "三", "四", "五", "六", "日").getOrElse(day - 1) { day.toString() }

    LaunchedEffect(school, semester, week, day, period, campusId, reload) {
        if (school == null) {
            error = "请先登录教务系统"
            return@LaunchedEffect
        }
        loading = true
        error = ""
        expiredCode = 0
        try {
            classrooms = EmptyClassroomApi.load(school, semester, week, day, period, campusId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            classrooms = emptyList()
            expiredCode = e.statusCode
        } catch (e: Exception) {
            classrooms = emptyList()
            error = e.message ?: "查询失败，请稍后重试"
        } finally {
            loading = false
        }
    }

    val allLabel = "全部"
    val buildings = remember(classrooms) {
        classrooms.map { it.building.trim() }.filter { it.isNotBlank() }.distinct()
            .sortedWith(compareBy<String> { buildingFilterRank(it) }.thenBy { it })
    }
    val floors = remember(classrooms, selectedBuilding) {
        classrooms.filter { selectedBuilding == allLabel || it.building.trim() == selectedBuilding }
            .map { it.floor.trim() }.filter { it.isNotBlank() }.distinct()
            .sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
    }
    val filteredClassrooms = remember(classrooms, selectedBuilding, selectedFloor) {
        classrooms.filter {
            (selectedBuilding == allLabel || it.building.trim() == selectedBuilding) &&
                (selectedFloor == allLabel || it.floor.trim() == selectedFloor)
        }
    }
    LaunchedEffect(buildings) {
        if (selectedBuilding != allLabel && selectedBuilding !in buildings) selectedBuilding = allLabel
    }
    LaunchedEffect(floors) {
        if (selectedFloor != allLabel && selectedFloor !in floors) selectedFloor = allLabel
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 680.dp).clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("空闲教室", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("第 ${week} 周 · 星期${dayText} · 第 ${period} 节", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EmptyClassroomApi.campuses.forEach { option ->
                        FilterChip(
                            selected = campusId == option.id,
                            onClick = {
                                if (campusId != option.id) {
                                    campusId = option.id
                                    selectedBuilding = allLabel
                                    selectedFloor = allLabel
                                }
                            },
                            label = { Text(option.label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                FilterSection(
                    label = "教学楼",
                    options = buildings,
                    selected = selectedBuilding,
                    onSelect = { selectedBuilding = it; selectedFloor = allLabel }
                )
                Spacer(Modifier.height(6.dp))
                FilterSection(
                    label = "楼层",
                    options = floors,
                    selected = selectedFloor,
                    onSelect = { selectedFloor = it },
                    format = { floor -> if (floor.endsWith("层")) floor else "${floor}层" }
                )
                Spacer(Modifier.height(12.dp))
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("正在查询空闲教室…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    expiredCode != 0 -> Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                        SessionExpiredHint(
                            title = "登录状态已失效（$expiredCode）",
                            subtitle = error.ifBlank { "教务登录过期了，更新一下就能继续查" },
                            repairing = repairing,
                            onRepair = {
                                repairing = true
                                error = ""
                                SessionRepair.repair(context) { outcome ->
                                    repairing = false
                                    if (outcome.ok) {
                                        reload++
                                    } else {
                                        error = outcome.message ?: "更新失败，请稍后重试"
                                    }
                                }
                            }
                        )
                    }
                    error.isNotBlank() -> Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { reload++ }) { Text("重试") }
                        }
                    }
                    classrooms.isEmpty() -> Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                        Text("这个时间没有查到空闲教室", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    filteredClassrooms.isEmpty() -> Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("当前筛选条件下没有空闲教室", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { selectedBuilding = allLabel; selectedFloor = allLabel }) { Text("清除筛选") }
                        }
                    }
                    else -> {
                        Text("共 ${filteredClassrooms.size} 间空闲教室", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredClassrooms, key = { it.id }) { room -> EmptyClassroomRow(room) }
                        }
                    }
                }
            }
        }
        val verification by com.buct.course.utils.SessionRenewer.verification.collectAsState()
        com.buct.course.ui.system.SecondaryVerificationOverlay(
            state = verification,
            onRememberChange = { com.buct.course.utils.SessionRenewer.setRememberDevice(it) },
            onSendCode = { captcha -> com.buct.course.utils.SessionRenewer.sendSecondarySms(captcha) },
            onSubmit = { code -> com.buct.course.utils.SessionRenewer.submitSecondarySms(code) },
            onDismiss = { com.buct.course.utils.SessionRenewer.cancelVerification() }
        )
        }
    }
}

private fun buildingFilterRank(name: String): Int {
    val value = name.trim()
    return when {
        value == "教学楼" -> 0
        value == "实验楼" -> 1
        value == "体育馆" -> 2
        value.contains("第一教学楼") -> 3
        value.contains("第二教学楼") -> 4
        else -> 5
    }
}

@Composable
private fun EmptyClassroomRow(room: EmptyClassroom) {
    val location = listOf(room.building, room.campus, room.floor.takeIf { it.isNotBlank() }?.let { "${it}层" } ?: "")
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(room.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (location.isNotBlank()) {
                    Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (room.code.isNotBlank() && room.code != room.name) {
                    Text("编号 ${room.code}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${room.seats} 座", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(room.type.ifBlank { "教室" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FilterSection(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    format: (String) -> String = { it }
) {
    if (options.isEmpty()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selected == "全部",
                onClick = { onSelect("全部") },
                label = { Text("全部") }
            )
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(format(option)) }
                )
            }
        }
    }
}

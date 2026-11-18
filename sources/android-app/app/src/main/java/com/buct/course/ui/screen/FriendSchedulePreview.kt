package com.buct.course.ui.screen

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buct.course.schedule.ScheduleJson
import com.buct.course.ui.system.GlassCircleButton
import com.buct.course.ui.system.SystemIconButton
import com.buct.course.ui.system.SystemTopBar
import com.buct.course.ui.system.glass.adaptiveGlassChip
import com.buct.course.ui.system.glass.applyChipContentDeformation
import com.buct.course.ui.system.glass.rememberInteractiveOptics

private val PreviewPalette = listOf(
    Color(0xFF5C6BC0), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFFFA726),
    Color(0xFFAB47BC), Color(0xFFEF5350), Color(0xFF26C6DA), Color(0xFF8D6E63)
)

/**
 * 朋友发来的课表预览（只读）。
 *
 * 直接复用课表网格，所以颜色、节次、周次显示和自己课表一致；
 * 只是没有编辑、没有同步，点课程也不弹详情。
 */
@Composable
fun FriendSchedulePreview(
    title: String,
    json: String,
    onBack: () -> Unit
) {
    val courses = remember(json) {
        ScheduleJson.parse(json)?.mapIndexed { index, entry ->
            val c = entry.course
            ScheduleCourseUi(
                name = c.name,
                teacher = c.teacher,
                location = c.location,
                day = c.day,
                startPeriod = c.startPeriod,
                endPeriod = c.endPeriod,
                weeks = c.weeks,
                color = PreviewPalette[index % PreviewPalette.size],
                sourceId = entry.sourceId,
                id = c.id
            )
        }.orEmpty()
    }
    var week by remember { mutableIntStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        SystemTopBar(
            title = "$title 的课表",
            subtitle = "${courses.size} 门课 · 只读",
            navigationIcon = {
                SystemIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            }
        )

        // 周次切换沿用课表页那套玻璃胶囊，保持同一种控件语言
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassCircleButton(
                onClick = { if (week > 1) week-- },
                icon = Icons.Filled.KeyboardArrowLeft,
                contentDescription = "上一周",
                size = 34.dp
            )
            Text(
                text = "第 $week 周",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            GlassCircleButton(
                onClick = { if (week < 25) week++ },
                icon = Icons.Filled.KeyboardArrowRight,
                contentDescription = "下一周",
                size = 34.dp
            )
        }

        Box(Modifier.weight(1f)) {
            ScheduleGrid(
                courses = courses,
                currentWeek = week,
                onCourseClick = { },
                topInset = 0.dp
            )
        }
    }
}

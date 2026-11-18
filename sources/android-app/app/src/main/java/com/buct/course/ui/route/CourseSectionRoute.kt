package com.buct.course.ui.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.buct.course.ui.system.SystemCompactSegmentedControl

/**
 * 「课程」页 = 课程 + 抢课 两个二级目录。
 *
 * 抢课原本是底栏的一个独立 tab，实际使用时和选课是同一件事的上下游，
 * 合并成二级切换后底栏腾出一格给「好友」。
 *
 * 切换器直接用全局的玻璃分段控件（和课表页「日/周」、设置页里的分段项同一种材质），
 * 不再自己画一个半透明药丸，避免同一屏出现两种控件语言。
 *
 * 状态栏间距在这里统一吃掉（consumeWindowInsets），子页面自己的 statusBarsPadding
 * 就不会再叠一层，不会出现顶栏下面多一条空白。
 */
@Composable
fun CourseSectionRoute() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SystemCompactSegmentedControl(
                options = listOf("课程", "抢课"),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.widthIn(max = 220.dp)
            )
            Box(Modifier.weight(1f))
            // 与课表页一致：二级目录标题右对齐提示当前所在处，字号取 labelSmall
            androidx.compose.material3.Text(
                text = if (tab == 0) "选课与课程表" else "抢课工作台",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .consumeWindowInsets(WindowInsets.statusBars)
        ) {
            if (tab == 0) CourseListRoute() else GrabProRoute()
        }
    }
}

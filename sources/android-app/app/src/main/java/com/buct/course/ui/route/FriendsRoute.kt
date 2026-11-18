package com.buct.course.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * 底部导航「好友」页。
 *
 * 列表和会话都在同一个 tab 内切换：进会话不跳新页面，返回直接回到列表，
 * 和底栏其它页的层级保持一致。
 *
 * onBack 为 null 表示这是底栏的一级页（顶栏不显示返回箭头）；
 * 从设置页以子页形式打开时才传回调。
 */
@Composable
fun FriendsRoute(onBack: (() -> Unit)? = null) {
    var chatTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var chatNickname by rememberSaveable { mutableStateOf("") }

    val displayId = chatTarget
    if (displayId != null) {
        com.buct.course.ui.screen.FriendChatScreen(
            displayId = displayId,
            nickname = chatNickname,
            onBack = { chatTarget = null }
        )
    } else {
        com.buct.course.ui.screen.FriendsScreen(
            onBack = onBack,
            onOpenChat = { id, nickname ->
                chatTarget = id
                chatNickname = nickname
            }
        )
    }
}

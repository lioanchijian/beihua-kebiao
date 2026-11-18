package com.buct.course.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buct.course.network.FriendApi
import com.buct.course.network.FriendMessage
import com.buct.course.social.FriendChatStore
import com.buct.course.social.FriendScheduleStore
import com.buct.course.social.ScheduleShareCache
import com.buct.course.ui.system.GlassTextField
import com.buct.course.ui.system.GlassToaster
import com.buct.course.ui.system.SystemIconButton
import com.buct.course.ui.system.SystemSecondaryButton
import com.buct.course.ui.system.SystemTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 轮询间隔。带本地缓存之后首屏不等网络，这个间隔可以放松一些：
 * 一个会话从每分钟 15 次请求降到 7 次左右，服务器压力直接减半。
 */
private const val CHAT_POLL_MS = 8_000L

/**
 * 好友会话页：文字 + 课表互传。
 *
 * - 收到课表消息时显示一张卡片，可以直接"保存到本机"，保存后再到课表页切换账号里就能选；
 * - 右上角「发课表」把当前账号的课表（含姓名、学号、学校、学期）发给对方。
 */
@Composable
fun FriendChatScreen(
    displayId: String,
    nickname: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 首屏直接用本地缓存渲染，不等网络；缓存为空才显示加载态
    var messages by remember(displayId) {
        mutableStateOf(FriendChatStore.load(context, displayId))
    }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loading by remember(displayId) { mutableStateOf(messages.isEmpty()) }
    var lastId by remember(displayId) { mutableStateOf(messages.maxOfOrNull { it.id } ?: 0L) }
    var previewTitle by remember { mutableStateOf<String?>(null) }
    var previewJson by remember { mutableStateOf<String?>(null) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle

    /** 增量拉取：只要 since_id 之后的新消息，并合并进本地缓存。 */
    suspend fun pullNew() {
        val thread = FriendApi.thread(context, displayId, sinceId = lastId)
        if (thread == null) {
            loading = false
            return
        }
        if (thread.messages.isNotEmpty()) {
            messages = FriendChatStore.merge(context, displayId, thread.messages)
            lastId = messages.maxOfOrNull { it.id } ?: lastId
        }
        loading = false
    }

    LaunchedEffect(displayId) {
        FriendApi.ensureIdentity(context)
        pullNew()
        while (isActive) {
            delay(CHAT_POLL_MS)
            // 退到后台就不再打服务器，回到前台由下一轮自然接上
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) continue
            pullNew()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // 课表预览
    previewJson?.let { json ->
        FriendSchedulePreview(
            title = previewTitle.orEmpty(),
            json = json,
            onBack = { previewJson = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        SystemTopBar(
            title = nickname.ifBlank { "好友" },
            subtitle = displayId,
            navigationIcon = {
                SystemIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            },
            actions = {
                SystemIconButton(
                    icon = Icons.Outlined.CalendarMonth,
                    contentDescription = "发送我的课表",
                    onClick = {
                        val snapshot = ScheduleShareCache.snapshot(context)
                        if (snapshot == null) {
                            GlassToaster.show("请先打开课表页加载一次课表")
                            return@SystemIconButton
                        }
                        scope.launch {
                            val err = FriendApi.sendSchedule(
                                context = context,
                                displayId = displayId,
                                ownerName = snapshot.ownerName,
                                ownerStudentId = snapshot.ownerStudentId,
                                school = snapshot.school,
                                term = snapshot.term,
                                scheduleJson = snapshot.json
                            )
                            if (err == null) {
                                GlassToaster.show("课表已发送")
                                pullNew()
                            } else {
                                GlassToaster.show(err)
                            }
                        }
                    },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )

        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = com.buct.course.ui.system.LocalAppOverlayBottomInset.current + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = "还没有消息。点右上角日历按钮，把你现在的课表发过去。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        onSaveSchedule = { payload ->
                            val json = payload.optString("schedule")
                            if (json.isBlank()) {
                                GlassToaster.show("课表内容为空")
                            } else {
                                FriendScheduleStore.save(
                                    context = context,
                                    displayId = displayId,
                                    ownerName = payload.optString("ownerName").ifBlank { nickname },
                                    ownerStudentId = payload.optString("ownerStudentId"),
                                    school = payload.optString("school"),
                                    term = payload.optString("term"),
                                    json = json
                                )
                                GlassToaster.show("已保存，可在课表页「切换课表账号」里选到")
                                // 桌面上如果正好有小组件在显示这位朋友的课表，立刻刷新
                                com.buct.course.widget.ScheduleWidget.refreshAllWidgets(context)
                            }
                        },
                        onPreviewSchedule = { title, json ->
                            previewTitle = title
                            previewJson = json
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp, end = 12.dp, top = 8.dp,
                    bottom = com.buct.course.ui.system.LocalAppOverlayBottomInset.current + 8.dp
                ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f)) {
                GlassTextField(
                    value = input,
                    onValueChange = { if (it.length <= 1000) input = it },
                    placeholder = "说点什么…",
                    singleLine = false
                )
            }
            IconButton(
                enabled = !sending,
                onClick = {
                    val text = input.trim()
                    if (text.isEmpty() || sending) return@IconButton
                    sending = true
                    scope.launch {
                        val err = FriendApi.sendText(context, displayId, text)
                        sending = false
                        if (err == null) {
                            input = ""
                            pullNew()
                        } else {
                            GlassToaster.show(err)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "发送",
                    tint = if (input.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: FriendMessage,
    onSaveSchedule: (JSONObject) -> Unit,
    onPreviewSchedule: (String, String) -> Unit
) {
    val mine = message.fromMe
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        if (message.isSchedule) {
            val payload = remember(message.id) {
                runCatching { JSONObject(message.payload) }.getOrNull()
            }
            val ownerName = payload?.optString("ownerName").orEmpty()
            val ownerId = payload?.optString("ownerStudentId").orEmpty()
            val term = payload?.optString("term").orEmpty()
            val scheduleJson = payload?.optString("schedule").orEmpty()
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        com.buct.course.ui.system.glassSurfaceColor(),
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        0.5.dp,
                        com.buct.course.ui.system.glassBorderColor(),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "课表",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = listOf(ownerName, ownerId, term)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { message.body },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "查看",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            if (scheduleJson.isNotBlank()) {
                                onPreviewSchedule(ownerName.ifBlank { "好友" }, scheduleJson)
                            } else {
                                GlassToaster.show("课表内容为空")
                            }
                        }
                    )
                    if (!mine) {
                        Text(
                            text = "保存到本机",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                if (payload == null) {
                                    GlassToaster.show("课表数据损坏")
                                } else {
                                    onSaveSchedule(payload)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .background(
                        if (mine) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            )
        }
    }
}

package com.tyust.course.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tyust.course.network.FriendApi
import com.tyust.course.network.FriendListData
import com.tyust.course.network.FriendSummary
import com.tyust.course.social.FriendScheduleStore
import com.tyust.course.ui.system.GlassTextField
import com.tyust.course.ui.system.GlassToaster
import com.tyust.course.ui.system.SystemPrimaryButton
import kotlinx.coroutines.launch

/**
 * 好友页。
 *
 * 身份沿用匿名反馈那套短号：不用注册、不用交换学号密码，把 BH-XXXXXX 给同学就能互加。
 * 加成功后可以聊天、互发课表；收到的课表会存到本机固定目录，随后出现在
 * 课表页「切换课表账号」里，直接选中就能看。
 */
@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onOpenChat: (displayId: String, nickname: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var data by remember { mutableStateOf<FriendListData?>(null) }
    var booting by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var schedules by remember { mutableStateOf(FriendScheduleStore.list(context)) }
    var pendingDelete by remember { mutableStateOf<FriendScheduleStore.Entry?>(null) }

    suspend fun refresh() {
        booting = data == null
        error = ""
        val identity = FriendApi.ensureIdentity(context)
        if (identity == null) {
            error = "连不上服务器，请检查网络后重试"
            booting = false
            return
        }
        val result = FriendApi.list(context)
        if (result == null) {
            error = "拉取好友列表失败，请重试"
        } else {
            data = result
        }
        schedules = FriendScheduleStore.list(context)
        booting = false
    }

    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                schedules = FriendScheduleStore.list(context)
                scope.launch { refresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = com.tyust.course.ui.system.LocalAppOverlayBottomInset.current)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column {
                Text(
                    text = "好友",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "用短号互加，互相发课表",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (booting) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }

                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // 我的短号
                data?.let { me ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "我的短号",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = me.displayId,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = me.nickname,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "把这串短号发给同学，他加你之后就能互发消息和课表。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "复制短号",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    clipboard.setText(AnnotatedString(me.displayId))
                                    GlassToaster.show("短号已复制")
                                }
                            )
                        }
                    }
                }

                // 添加好友
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "添加好友",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    GlassTextField(
                        value = draft,
                        onValueChange = { draft = it.uppercase().take(12) },
                        placeholder = "输入对方短号，如 BH-ABC123",
                        singleLine = true
                    )
                    SystemPrimaryButton(
                        text = if (busy) "正在添加…" else "添加",
                        onClick = {
                            if (busy) return@SystemPrimaryButton
                            val code = draft.trim().uppercase()
                            if (code.length < 4) {
                                GlassToaster.show("请输入完整短号")
                                return@SystemPrimaryButton
                            }
                            busy = true
                            scope.launch {
                                val result = FriendApi.requestFriend(context, code)
                                busy = false
                                if (result.error != null) {
                                    GlassToaster.show(result.error)
                                } else {
                                    draft = ""
                                    GlassToaster.show(
                                        when (result.status) {
                                            "accepted" -> "你们互相加过了，已经是好友"
                                            "already" -> "已经是好友了"
                                            else -> "申请已发出，等对方同意"
                                        }
                                    )
                                    refresh()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 待处理请求
                data?.requests?.takeIf { it.isNotEmpty() }?.let { requests ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "好友申请（${requests.size}）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        requests.forEach { request ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = request.nickname,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = request.displayId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "同意",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch {
                                                val err = FriendApi.respond(context, request.id, true)
                                                GlassToaster.show(err ?: "已添加好友")
                                                refresh()
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                Text(
                                    text = "忽略",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch {
                                                FriendApi.respond(context, request.id, false)
                                                refresh()
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // 好友列表
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "我的好友（${data?.friends?.size ?: 0}）",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val friends = data?.friends.orEmpty()
                    if (friends.isEmpty()) {
                        Text(
                            text = "还没有好友。把上面的短号发给同学，互相加一下就能发课表了。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        friends.forEach { friend -> FriendRow(friend, onOpenChat) }
                    }
                }

                // 朋友的课表
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "朋友的课表（${schedules.size}）",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (schedules.isEmpty()) {
                        Text(
                            text = "收到的课表会自动存在本机，不用手动选文件；在课表页「切换课表账号」里就能选到。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        schedules.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(accountColor(entry.label), CircleShape)
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = entry.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = entry.subtitle.ifBlank { "本地课表文件" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                IconButton(onClick = { pendingDelete = entry }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "删除这份课表",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        com.tyust.course.ui.system.SystemDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    text = "删除「${entry.label}」的课表？",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                SystemPrimaryButton(
                    text = "删除",
                    onClick = {
                        FriendScheduleStore.delete(context, entry.id)
                        schedules = FriendScheduleStore.list(context)
                        pendingDelete = null
                        // 有小组件正在显示这份课表的话，删掉后让它回落到本机课表
                        com.tyust.course.widget.ScheduleWidget.refreshAllWidgets(context)
                        GlassToaster.show("已删除")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                com.tyust.course.ui.system.SystemSecondaryButton(
                    text = "取消",
                    onClick = { pendingDelete = null },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Text(
                text = "只删本机这份课表，不影响对方。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FriendRow(friend: FriendSummary, onOpenChat: (String, String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(14.dp)
            )
            .clickable { onOpenChat(friend.displayId, friend.nickname) }
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(34.dp).background(accountColor(friend.nickname), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = friend.nickname.take(1),
                style = MaterialTheme.typography.labelLarge,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = friend.nickname,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = friend.displayId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = when {
                    friend.lastAt.isBlank() -> "还没有聊过，打个招呼吧"
                    friend.lastKind == "schedule" -> "[课表]"
                    else -> friend.lastBody
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (friend.unread > 0) {
            Text(
                text = if (friend.unread > 99) "99+" else friend.unread.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onError,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(9.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

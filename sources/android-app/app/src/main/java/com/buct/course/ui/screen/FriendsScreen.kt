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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.People
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.buct.course.network.FeedbackApi
import com.buct.course.network.FriendApi
import com.buct.course.network.FriendListData
import com.buct.course.network.FriendSummary
import com.buct.course.social.FriendScheduleStore
import com.buct.course.ui.system.GlassIconChip
import com.buct.course.ui.system.GlassTextField
import com.buct.course.ui.system.GlassToaster
import com.buct.course.ui.system.InsetGroupedRow
import com.buct.course.ui.system.InsetGroupedSection
import com.buct.course.ui.system.LocalAppOverlayBottomInset
import com.buct.course.ui.system.SystemIconButton
import com.buct.course.ui.system.SystemPrimaryButton
import com.buct.course.ui.system.SystemTopBar
import kotlinx.coroutines.launch

/**
 * 好友页。
 *
 * 身份沿用匿名反馈那套短号：不用注册、不用交换学号密码，把 BH-XXXXXX 给同学就能互加。
 * 加成功后可以聊天、互发课表；收到的课表会存到本机固定目录，随后出现在
 * 课表页「切换课表账号」里，直接选中就能看。
 *
 * 版式跟设置页一致：玻璃顶栏 + iOS inset grouped 分组，不再用一叠自绘卡片。
 */
@Composable
fun FriendsScreen(
    onBack: (() -> Unit)? = null,
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
    var showNicknameDialog by remember { mutableStateOf(false) }
    var nicknameDraft by remember { mutableStateOf("") }
    var savingNickname by remember { mutableStateOf(false) }

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
            .padding(bottom = LocalAppOverlayBottomInset.current)
    ) {
        SystemTopBar(
            title = "好友",
            subtitle = "用短号互加，互相发课表",
            navigationIcon = onBack?.let {
                { SystemIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", it) }
            }
        )

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
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
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // 我的短号
            data?.let { me ->
                InsetGroupedSection(header = "我的短号") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassIconChip(Icons.Outlined.People, accountColor(me.nickname), size = 36.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = me.displayId,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${me.nickname} · 把这串短号发给同学，他加你之后就能互发消息和课表",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "改昵称",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        nicknameDraft = me.nickname
                                        showNicknameDialog = true
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                            Text(
                                text = "复制",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        clipboard.setText(AnnotatedString(me.displayId))
                                        GlassToaster.show("短号已复制")
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 添加好友
            InsetGroupedSection(
                header = "添加好友",
                footer = "输入对方的短号，对方同意后就能互发消息和课表。"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
            }

            // 待处理申请
            data?.requests?.takeIf { it.isNotEmpty() }?.let { requests ->
                InsetGroupedSection(header = "好友申请（${requests.size}）") {
                    requests.forEachIndexed { index, request ->
                        InsetGroupedRow(
                            title = request.nickname,
                            subtitle = request.displayId,
                            icon = Icons.Outlined.People,
                            iconTint = accountColor(request.nickname),
                            showDivider = index != requests.lastIndex,
                            trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        )
                    }
                }
            }

            // 好友列表
            val friends = data?.friends.orEmpty()
            InsetGroupedSection(
                header = "我的好友（${friends.size}）",
                footer = if (friends.isEmpty()) "还没有好友。把上面的短号发给同学，互相加一下就能发课表了。" else null
            ) {
                friends.forEachIndexed { index, friend ->
                    FriendRow(
                        friend = friend,
                        showDivider = index != friends.lastIndex,
                        onOpenChat = onOpenChat
                    )
                }
            }

            // 朋友的课表
            InsetGroupedSection(
                header = "朋友的课表（${schedules.size}）",
                footer = if (schedules.isEmpty()) {
                    "收到的课表会自动存在本机，不用手动选文件；在课表页「切换课表账号」里就能选到。"
                } else {
                    "在课表页「切换课表账号」里可以直接选，也能让桌面小组件专门显示某一位的课表。"
                }
            ) {
                schedules.forEachIndexed { index, entry ->
                    InsetGroupedRow(
                        title = entry.label,
                        subtitle = entry.subtitle.ifBlank { "本地课表文件" },
                        icon = Icons.Outlined.CalendarMonth,
                        iconTint = accountColor(entry.label),
                        showDivider = index != schedules.lastIndex,
                        trailing = {
                            IconButton(onClick = { pendingDelete = entry }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "删除这份课表",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (showNicknameDialog) {
        com.buct.course.ui.system.SystemDialog(
            onDismissRequest = { if (!savingNickname) showNicknameDialog = false },
            title = {
                Text(
                    text = "修改昵称",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                SystemPrimaryButton(
                    text = if (savingNickname) "正在保存…" else "保存",
                    onClick = {
                        if (savingNickname) return@SystemPrimaryButton
                        val name = nicknameDraft.trim()
                        if (name.length < 2) {
                            GlassToaster.show("昵称需 2-12 个字")
                            return@SystemPrimaryButton
                        }
                        savingNickname = true
                        scope.launch {
                            val err = FeedbackApi.setNickname(context, name)
                            savingNickname = false
                            if (err == null) {
                                showNicknameDialog = false
                                GlassToaster.show("昵称已更新")
                                refresh()
                            } else {
                                GlassToaster.show(err)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                com.buct.course.ui.system.SystemSecondaryButton(
                    text = "取消",
                    onClick = { showNicknameDialog = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "好友看到的就是这个昵称，改完立刻生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlassTextField(
                    value = nicknameDraft,
                    onValueChange = { if (it.length <= 12) nicknameDraft = it },
                    placeholder = "2-12 个字",
                    singleLine = true
                )
            }
        }
    }

    pendingDelete?.let { entry ->
        com.buct.course.ui.system.SystemDialog(
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
                        com.buct.course.widget.ScheduleWidget.refreshAllWidgets(context)
                        GlassToaster.show("已删除")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                com.buct.course.ui.system.SystemSecondaryButton(
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
private fun FriendRow(
    friend: FriendSummary,
    showDivider: Boolean,
    onOpenChat: (String, String) -> Unit
) {
    InsetGroupedRow(
        title = friend.nickname,
        subtitle = when {
            friend.lastAt.isBlank() -> "${friend.displayId} · 还没有聊过，打个招呼吧"
            friend.lastKind == "schedule" -> "${friend.displayId} · [课表]"
            else -> "${friend.displayId} · ${friend.lastBody}"
        },
        icon = Icons.Outlined.People,
        iconTint = accountColor(friend.nickname),
        showDivider = showDivider,
        onClick = { onOpenChat(friend.displayId, friend.nickname) },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (friend.unread > 0) {
                    Text(
                        text = if (friend.unread > 99) "99+" else friend.unread.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(9.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
}

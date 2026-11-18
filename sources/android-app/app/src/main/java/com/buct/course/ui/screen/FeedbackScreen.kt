package com.buct.course.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buct.course.network.FeedbackApi
import com.buct.course.network.FeedbackIdentity
import com.buct.course.network.FeedbackMessage
import com.buct.course.ui.system.GlassTextField
import com.buct.course.ui.system.GlassToaster
import com.buct.course.ui.system.SystemDialog
import com.buct.course.ui.system.SystemPrimaryButton
import com.buct.course.ui.system.SystemSecondaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MAX_INPUT = 1000
private const val POLL_INTERVAL_MS = 5_000L

/**
 * 匿名意见反馈（和开发者柳岸的 1:1 会话）。
 *
 * 身份是服务端分配的短号 + 随机昵称，页面顶部会明确写明只上传匿名设备 ID，
 * 不上传姓名、学号、密码。消息 5 秒轮询拉取，进页面即标记已读。
 */
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    var identity by remember { mutableStateOf<FeedbackIdentity?>(null) }
    var messages by remember { mutableStateOf<List<FeedbackMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var booting by remember { mutableStateOf(true) }
    var bootError by remember { mutableStateOf<String?>(null) }
    var lastId by remember { mutableStateOf(0L) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var nicknameDraft by remember { mutableStateOf("") }
    var nicknameError by remember { mutableStateOf<String?>(null) }

    suspend fun bootstrap() {
        booting = true
        bootError = null
        val id = FeedbackApi.ensureIdentity(context)
        if (id == null) {
            bootError = "连不上服务器，请检查网络后重试"
            booting = false
            return
        }
        identity = id
        val result = FeedbackApi.thread(context, sinceId = 0, markRead = true)
        if (result != null) {
            messages = result.first
            lastId = result.first.maxOfOrNull { it.id } ?: 0L
        }
        booting = false
    }

    LaunchedEffect(Unit) { bootstrap() }

    // 轮询：页面在前台时每 5 秒拉新消息
    LaunchedEffect(identity != null) {
        if (identity == null) return@LaunchedEffect
        while (isActive) {
            delay(POLL_INTERVAL_MS)
            val result = FeedbackApi.thread(context, sinceId = lastId, markRead = true) ?: continue
            if (result.first.isNotEmpty()) {
                messages = messages + result.first.filter { new -> messages.none { it.id == new.id } }
                lastId = messages.maxOfOrNull { it.id } ?: lastId
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = "意见反馈",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "和开发者直接聊，5 秒自动刷新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
            ) {
                when {
                    booting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    bootError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(bootError!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { scope.launch { bootstrap() } }) { Text("重试") }
                        }
                    }
                    else -> {
                        PrivacyNote()
                        identity?.let { id ->
                            IdentityRow(
                                identity = id,
                                onClick = {
                                    nicknameDraft = id.nickname
                                    nicknameError = null
                                    showNicknameDialog = true
                                },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(id.displayId))
                                    GlassToaster.show("短号 ${id.displayId} 已复制")
                                }
                            )
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages, key = { it.id }) { message -> MessageBubble(message) }
                        }
                    }
                }
            }
        }

        // 输入区
        Row(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassTextField(
                value = input,
                onValueChange = { if (it.length <= MAX_INPUT) input = it },
                modifier = Modifier.weight(1f),
                placeholder = "说点什么…（最多 $MAX_INPUT 字）",
                singleLine = false,
                minHeight = 52.dp
            )
            IconButton(
                enabled = input.isNotBlank() && !sending,
                onClick = {
                    val text = input.trim()
                    if (text.isBlank()) return@IconButton
                    input = ""
                    sending = true
                    scope.launch {
                        val error = FeedbackApi.send(context, text)
                        sending = false
                        if (error != null) {
                            GlassToaster.show(error)
                            if (input.isBlank()) input = text
                        } else {
                            val result = FeedbackApi.thread(context, sinceId = lastId, markRead = true)
                            if (result != null) {
                                messages = (messages + result.first).distinctBy { it.id }
                                lastId = messages.maxOfOrNull { it.id } ?: lastId
                            }
                        }
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showNicknameDialog) {
        SystemDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = {
                Text(
                    text = "修改昵称",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                SystemPrimaryButton(
                    text = "保存",
                    onClick = {
                        val name = nicknameDraft.trim()
                        if (name.length !in 2..12) {
                            nicknameError = "昵称需 2-12 个字"
                            return@SystemPrimaryButton
                        }
                        scope.launch {
                            val error = FeedbackApi.setNickname(context, name)
                            if (error == null) {
                                identity = identity?.copy(nickname = name)
                                showNicknameDialog = false
                            } else {
                                nicknameError = error
                            }
                        }
                    },
                    enabled = nicknameDraft.trim().length in 2..12,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                SystemSecondaryButton(
                    text = "取消",
                    onClick = { showNicknameDialog = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "昵称只在本会话里显示，方便我认出你，不会关联真实身份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlassTextField(
                    value = nicknameDraft,
                    onValueChange = {
                        if (it.length <= 12) {
                            nicknameDraft = it
                            nicknameError = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "2-12 个字",
                    singleLine = true
                )
                nicknameError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "匿名反馈",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "服务器只会收到一个随机设备 ID 和短号，不含你的姓名、学号、账号或密码。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IdentityRow(
    identity: FeedbackIdentity,
    onClick: () -> Unit,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = identity.nickname.take(1).ifBlank { "北" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = identity.nickname.ifBlank { "匿名同学" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${identity.displayId} · 点这里改昵称，长按复制",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        TextButton(onClick = onClick) { Text("改昵称") }
        TextButton(onClick = onCopy) { Text("复制") }
    }
}

@Composable
private fun MessageBubble(message: FeedbackMessage) {
    val mine = !message.fromAdmin
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            if (!mine) {
                Text(
                    text = "柳岸",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp,
                textAlign = TextAlign.Start
            )
            Text(
                text = formatStamp(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = if (mine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 3.dp).align(Alignment.End)
            )
        }
    }
}

/** 服务端给的是 ISO8601 UTC（2026-11-17T10:34:33+00:00），这里转成本地 MM-dd HH:mm。 */
private fun formatStamp(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(raw.take(19)) ?: return raw
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(date)
    }.getOrDefault(raw)
}

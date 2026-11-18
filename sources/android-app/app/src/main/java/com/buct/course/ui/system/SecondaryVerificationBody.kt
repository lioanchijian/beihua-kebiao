package com.buct.course.ui.system

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 短信二次验证的完整内容（图形验证码 → 发送短信 → 6 位短信码 → 记住该设备）。
 *
 * 抽成独立组件是为了能塞进任意容器：独立弹窗、或直接盖在已经被打开的弹窗上。
 * 表单 state（短信码输入）放在这里，按钮也在这里，避免外层再维护一份。
 */
@Composable
fun SecondaryVerificationContent(
    phoneMasked: String,
    sentUntil: Long,
    captchaBytes: ByteArray?,
    sending: Boolean,
    submitting: Boolean,
    errorMessage: String?,
    rememberDevice: Boolean,
    onRememberChange: (Boolean) -> Unit,
    onSendCode: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var captchaInput by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sentUntil) {
        while (sentUntil > System.currentTimeMillis()) {
            kotlinx.coroutines.delay(1000L)
            now = System.currentTimeMillis()
        }
        now = System.currentTimeMillis()
    }
    LaunchedEffect(captchaBytes) { captchaInput = "" }
    val remaining = if (sentUntil > now) ((sentUntil - now + 999L) / 1000L).toInt() else 0
    val sendText = when {
        sending -> "发送中"
        remaining > 0 -> "${remaining}s 后重发"
        captchaBytes != null -> "发送验证码"
        else -> "获取验证码"
    }
    val sendEnabled = !sending && remaining == 0 &&
        (captchaBytes == null || captchaInput.isNotBlank())
    val hint = when {
        remaining > 0 -> "验证码已发送至 ${phoneMasked.ifBlank { "绑定的手机号" }}，请输入 6 位验证码"
        captchaBytes != null -> "请输入图形验证码，再点「发送验证码」"
        else -> "为保护账号安全，本次登录需要短信二次验证"
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        captchaBytes?.let { bytes ->
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            if (bitmap != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "图形验证码，点击换一张",
                        modifier = Modifier
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSendCode("") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                GlassTextField(
                    value = captchaInput,
                    onValueChange = { captchaInput = it.take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "请输入图形验证码",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    minHeight = 50.dp
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        GlassTextField(
            value = code,
            onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(6) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "请输入短信验证码",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            minHeight = 50.dp,
            trailing = {
                Text(
                    text = sendText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sendEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = sendEnabled) { onSendCode(captchaInput.trim()) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        )

        // 对应学校统一认证页的「记住该设备」
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onRememberChange(!rememberDevice) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = rememberDevice,
                onCheckedChange = { onRememberChange(it) },
                modifier = Modifier.size(36.dp)
            )
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                Text(
                    text = "记住该设备",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "与学校认证页同名选项一致，下次登录可免短信验证",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.weight(1f)) {
                SystemSecondaryButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(Modifier.weight(1f)) {
                SystemPrimaryButton(
                    text = if (submitting) "验证中…" else "验证并登录",
                    onClick = { onSubmit(code.trim()) },
                    enabled = code.isNotBlank() && !submitting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

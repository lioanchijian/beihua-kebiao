package com.buct.course.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.buct.course.utils.SecondaryVerificationUi
import com.buct.course.utils.SessionRenewer

/**
 * 短信二次验证的独立弹窗。
 *
 * 适用于没有其它弹窗遮挡的场景（设置页、启动恢复）。已经开着弹窗时请改用
 * [SecondaryVerificationOverlay]，否则新 Dialog 会被旧弹窗压住看不见。
 */
@Composable
fun SecondaryVerificationDialog(
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.92f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = "二次身份验证",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                SecondaryVerificationContent(
                    phoneMasked = phoneMasked,
                    sentUntil = sentUntil,
                    captchaBytes = captchaBytes,
                    sending = sending,
                    submitting = submitting,
                    errorMessage = errorMessage,
                    rememberDevice = rememberDevice,
                    onRememberChange = onRememberChange,
                    onSendCode = onSendCode,
                    onSubmit = onSubmit,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

/**
 * 直接盖在当前弹窗内容之上的验证层。
 *
 * 空教室、课程详情这类已经打开了 Dialog 的页面用它：Cono 的 Dialog 是独立窗口，
 * 再开一个新 Dialog 未必在最上层（实测会被先开的弹窗遮住），所以直接画在同一层。
 */
@Composable
fun SecondaryVerificationOverlay(
    state: SecondaryVerificationUi,
    onRememberChange: (Boolean) -> Unit,
    onSendCode: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.active) return
    var code by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.94f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text(
                text = "二次身份验证",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            SecondaryVerificationContent(
                phoneMasked = state.phoneMasked,
                sentUntil = state.sentUntil,
                captchaBytes = state.captcha,
                sending = state.sending,
                submitting = state.submitting,
                errorMessage = state.error,
                rememberDevice = state.rememberDevice,
                onRememberChange = onRememberChange,
                onSendCode = onSendCode,
                onSubmit = onSubmit,
                onDismiss = onDismiss
            )
        }
    }
}

/** 全局入口：没有弹窗遮挡时用独立弹窗。 */
@Composable
fun SecondaryVerificationHost() {
    val state by SessionRenewer.verification.collectAsState()
    if (!state.active) return
    SecondaryVerificationDialog(
        phoneMasked = state.phoneMasked,
        sentUntil = state.sentUntil,
        captchaBytes = state.captcha,
        sending = state.sending,
        submitting = state.submitting,
        errorMessage = state.error,
        rememberDevice = state.rememberDevice,
        onRememberChange = { SessionRenewer.setRememberDevice(it) },
        onSendCode = { captcha -> SessionRenewer.sendSecondarySms(captcha) },
        onSubmit = { code -> SessionRenewer.submitSecondarySms(code) },
        onDismiss = { SessionRenewer.cancelVerification() }
    )
}

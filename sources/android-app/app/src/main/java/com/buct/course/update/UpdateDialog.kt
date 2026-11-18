package com.buct.course.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buct.course.ui.system.SystemDialog
import com.buct.course.ui.system.SystemPrimaryButton
import com.buct.course.ui.system.SystemSecondaryButton
import com.buct.course.ui.theme.MotionDuration
import com.buct.course.ui.theme.NeuPrimary

/**
 * 更新对话框（玻璃 SystemDialog 版）
 *
 * @param onUpdate 参数是选定的下载 URL：只有一个源时直接传 downloadUrl，
 *                 多个源时先弹「选择下载源」再回传所选源的 URL
 */
@Composable
fun UpdateDialog(
    updateInfo: UpdateManager.UpdateInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    onUpdate: (String) -> Unit,
    downloadProgress: Int = -1,  // -1 表示未开始下载
    isDownloading: Boolean = false
) {
    // LocalContext.current 只能在组合期读取；remember / onClick 的 lambda 里用这个普通值
    val appContext = LocalContext.current
    val canDismiss = !isDownloading && !updateInfo.forceUpdate

    // 下载源选择：多源才弹框；默认选中上次用过的源
    var showSourcePicker by remember(updateInfo) { mutableStateOf(false) }
    var selectedSourceIndex by remember(updateInfo) {
        mutableIntStateOf(
            updateInfo.sources.indexOfFirst { it.url == UpdateManager.getLastSourceUrl(appContext) }
                .takeIf { it >= 0 } ?: 0
        )
    }

    // 图标动画：呼吸效果
    val infiniteTransition = rememberInfiniteTransition(label = "iconScale")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionDuration.EmphasisPulse, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    SystemDialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        icon = {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(NeuPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = NeuPrimary
                )
            }
        },
        title = {
            Text(
                text = "发现新版本",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        dismissButton = if (!isDownloading && !updateInfo.forceUpdate) {
            {
                SystemSecondaryButton(
                    text = "稍后",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else null,
        confirmButton = if (!isDownloading) {
            {
                SystemPrimaryButton(
                    text = "立即更新",
                    onClick = {
                        // 多个下载源：先选通道；单源：直接下载 downloadUrl
                        if (updateInfo.sources.size > 1) showSourcePicker = true
                        else onUpdate(updateInfo.downloadUrl)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 版本信息
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "v$currentVersion",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = " → ",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "v${updateInfo.versionName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeuPrimary
                )
            }

            // 更新说明
            if (updateInfo.releaseNotes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .heightIn(max = 200.dp)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "更新内容",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = updateInfo.releaseNotes,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }

            // 下载进度
            AnimatedVisibility(visible = isDownloading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = downloadProgress / 100f,
                        label = "progress"
                    )

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = NeuPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (downloadProgress >= 100) "下载完成，正在安装..." else "正在下载 $downloadProgress%",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // 多下载源选择框：version.json 提供 sources 且不止一个时，「立即更新」先选通道
    if (showSourcePicker && updateInfo.sources.size > 1) {
        SystemDialog(
            onDismissRequest = { showSourcePicker = false },
            title = {
                Text(
                    text = "选择下载源",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            dismissButton = {
                SystemSecondaryButton(
                    text = "取消",
                    onClick = { showSourcePicker = false },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                SystemPrimaryButton(
                    text = "开始下载",
                    onClick = {
                        val chosen = updateInfo.sources.getOrNull(selectedSourceIndex)
                            ?: updateInfo.sources.first()
                        UpdateManager.setLastSourceUrl(appContext, chosen.url)
                        showSourcePicker = false
                        onUpdate(chosen.url)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                updateInfo.sources.forEachIndexed { index, source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedSourceIndex = index }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == selectedSourceIndex,
                            onClick = { selectedSourceIndex = index }
                        )
                        Text(
                            text = source.name,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "各下载源为同一个安装包（已校验 SHA-256 一致），高速源通常更快",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 更新检查状态管理
 */
@Composable
fun rememberUpdateState(
    context: android.content.Context = LocalContext.current
): UpdateState {
    val updateManager = remember { UpdateManager.getInstance(context) }
    
    var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    
    return remember(updateManager) {
        UpdateState(
            updateManager = updateManager,
            updateInfo = { updateInfo },
            setUpdateInfo = { updateInfo = it },
            showDialog = { showDialog },
            setShowDialog = { showDialog = it },
            isDownloading = { isDownloading },
            setIsDownloading = { isDownloading = it },
            downloadProgress = { downloadProgress },
            setDownloadProgress = { downloadProgress = it }
        )
    }
}

class UpdateState(
    private val updateManager: UpdateManager,
    val updateInfo: () -> UpdateManager.UpdateInfo?,
    val setUpdateInfo: (UpdateManager.UpdateInfo?) -> Unit,
    val showDialog: () -> Boolean,
    val setShowDialog: (Boolean) -> Unit,
    val isDownloading: () -> Boolean,
    val setIsDownloading: (Boolean) -> Unit,
    val downloadProgress: () -> Int,
    val setDownloadProgress: (Int) -> Unit
) {
    fun checkForUpdate(onFinished: (Boolean) -> Unit = {}) {
        updateManager.checkForUpdate({ info ->
            if (info != null) {
                setUpdateInfo(info)
                setShowDialog(true)
            }
        }, onFinished)
    }

    /**
     * @param url 指定下载源（更新弹窗里选的）；null 时用主 downloadUrl
     */
    fun startDownload(url: String? = null) {
        val info = updateInfo() ?: return
        val downloadUrl = url ?: info.downloadUrl
        setIsDownloading(true)
        setDownloadProgress(0)

        updateManager.downloadApk(
            downloadUrl = downloadUrl,
            expectedSha256 = info.sha256,
            onProgress = { progress ->
                setDownloadProgress(progress)
            },
            onComplete = { file ->
                setIsDownloading(false)
                if (file != null && file.exists()) {
                    updateManager.installApk(file)
                    setShowDialog(false)
                } else {
                    // 下载失败或校验没通过（哈希/签名不对），必须明确告诉用户，不能静默
                    updateManager.notifyUser("安装包校验未通过，已取消本次更新，请稍后重试")
                }
            }
        )
    }
    
    fun dismiss() {
        setShowDialog(false)
        setUpdateInfo(null)
    }
    
    fun getCurrentVersion(): String {
        return updateManager.getCurrentVersionName()
    }
}

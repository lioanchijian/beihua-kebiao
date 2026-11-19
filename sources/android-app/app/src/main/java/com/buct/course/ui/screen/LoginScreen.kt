package com.buct.course.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buct.course.ui.system.isBackdropSupported
import com.buct.course.ui.system.rememberGlassAccessibilityMode
import com.buct.course.ui.system.DialogHost
import com.buct.course.ui.system.GlassWindowHost
import com.buct.course.ui.system.LocalAppBackdrop
import com.buct.course.ui.system.LocalControlBackdrop
import com.buct.course.ui.system.LocalDialogHost
import com.buct.course.ui.system.rememberDialogHostState
import com.buct.course.ui.system.SystemPrimaryButton
import com.buct.course.ui.system.SystemSecondaryButton
import com.buct.course.ui.system.SystemSegmentedControl
import com.buct.course.ui.system.SystemDialog
import com.buct.course.ui.system.SystemPicker
import com.buct.course.ui.system.glass.glassSheet
import com.buct.course.ui.theme.*

import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import com.buct.course.model.SchoolConfig
import com.buct.course.manager.AppearanceSettingsManager
import com.buct.course.manager.UserManager
import com.buct.course.ui.system.GlassTextField
import com.buct.course.ui.system.drawWallpaperPattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    schools: List<SchoolConfig>,
    onSchoolSelected: (SchoolConfig) -> Unit,
    onLoginClick: (cookie: String) -> Unit,
    onOpenWebView: () -> Unit = {},
    onSchoolAdded: () -> Unit = {},
    onDemoMode: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onClearError: () -> Unit = {},
    cookieValue: String = "",
    // Password login
    onPasswordLogin: ((username: String, password: String) -> Unit)? = null,
    captchaImageBytes: ByteArray? = null,
    onCaptchaSubmit: ((code: String) -> Unit)? = null,
    onCaptchaRefresh: (() -> Unit)? = null,
    // 短信登录（原生表单）
    onSmsSendCode: ((username: String) -> Unit)? = null,
    onSmsCaptchaSubmit: ((captcha: String) -> Unit)? = null,
    onSmsLogin: ((username: String, code: String) -> Unit)? = null,
    smsCodeSent: Boolean = false,
    smsCaptchaBytes: ByteArray? = null,
    // 密码登录后的短信二次验证
    secondarySmsRequired: Boolean = false,
    secondarySmsPhone: String = "",
    secondarySmsSentUntil: Long = 0L,
    secondarySmsCaptchaBytes: ByteArray? = null,
    secondarySmsSending: Boolean = false,
    secondarySmsSubmitting: Boolean = false,
    onSecondarySmsSend: ((String) -> Unit)? = null,
    onSecondarySmsSubmit: ((String) -> Unit)? = null,
    secondarySmsRememberDevice: Boolean = true,
    onSecondarySmsRememberChange: ((Boolean) -> Unit)? = null,
    onSecondarySmsCancel: (() -> Unit)? = null,
    // 通行密钥登录（学号 + 原生系统通行密钥认证）
    onPasskeyLogin: ((username: String) -> Unit)? = null,
    // 通行密钥注册（创建）：设备未注册时显示密码输入，用密码登录 sidportal 创建
    passkeyNeedPassword: Boolean = false,
    passkeyNeedCaptcha: Boolean = false,
    passkeyCaptchaBytes: ByteArray? = null,
    onPasskeyRegister: ((username: String, password: String, nickname: String) -> Unit)? = null,
    onPasskeyCaptchaSubmit: ((code: String) -> Unit)? = null,
    // 通行密钥网页认证浮层回调：返回教务会话 Cookie
    onPortalCookie: ((cookie: String) -> Unit)? = null,
    // New parameters for binding dialog
    showBindingDialog: Boolean = false,
    bindingStudentName: String = "",
    bindingMaxStudents: Int = 0,
    bindingUsedNames: Set<String> = emptySet(),
    bindingUsedCount: Int = bindingUsedNames.size,
    onConfirmBinding: () -> Unit = {},
    onCancelBinding: () -> Unit = {}
) {
    var cookie by remember { mutableStateOf(cookieValue) }
    var loginTab by remember { mutableStateOf(if (onPasswordLogin != null) 0 else 1) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // 北京化工大学已接入统一身份认证(CAS)：直接使用统一认证密码登录
    val isBuctSchool = remember {
        UserManager.getInstance().currentSchool?.id == "buct" ||
            UserManager.getInstance().supportedSchools.any { it.id == "buct" && it.id == UserManager.getInstance().currentSchool?.id }
    }
    var captchaInput by remember { mutableStateOf("") }
    var captchaSubmitting by remember { mutableStateOf(false) }
    val captchaVisible = captchaImageBytes != null
    var smsUsername by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var smsCaptchaInput by remember { mutableStateOf("") }
    var smsSending by remember { mutableStateOf(false) }
    var passkeyUsername by remember { mutableStateOf("") }
    var passkeyPassword by remember { mutableStateOf("") }
    var passkeyNickname by remember { mutableStateOf("北化课表") }
    var passkeyCaptchaInput by remember { mutableStateOf("") }
    var passkeyLoading by remember { mutableStateOf(false) }
    var showPortalLogin by remember { mutableStateOf(false) }
    
    // Update cookie when external value changes
    LaunchedEffect(cookieValue) {
        if (cookieValue.isNotEmpty()) {
            cookie = cookieValue
        }
    }
    var showPassword by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var showEditSchoolDialog by remember { mutableStateOf(false) }
    val accessibility = rememberGlassAccessibilityMode()
    val loginPanelEnter = if (accessibility.reduceMotion) {
        androidx.compose.animation.EnterTransition.None
    } else {
        slideInVertically(
            initialOffsetY = { 100 },
            animationSpec = androidx.compose.animation.core.spring(
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
            )
        ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
    }
    
    LaunchedEffect(Unit) {
        visible = true
    }

    // 验证码刷新时清空输入
    LaunchedEffect(captchaImageBytes) {
        if (captchaImageBytes != null) {
            captchaInput = ""
            captchaSubmitting = false
        }
    }

    // 短信验证码发送完成/失败后复位"发送中"状态
    LaunchedEffect(smsCodeSent) {
        if (smsCodeSent) smsSending = false
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) smsSending = false
    }
    // 兜底：任何"发送中"状态 20 秒后强制复位，防止回调丢失导致按钮卡死
    LaunchedEffect(smsSending) {
        if (smsSending) {
            kotlinx.coroutines.delay(20000)
            smsSending = false
        }
    }
    // 图形码返回后复位"获取中"状态（关键：onCaptchaRequired 回调时复位发送中标记）
    LaunchedEffect(smsCaptchaBytes) {
        if (smsCaptchaBytes != null) smsSending = false
    }
    // 通行密钥注册阶段切换（未注册→密码/图形码）时复位加载态
    LaunchedEffect(passkeyNeedPassword, passkeyNeedCaptcha) {
        if (passkeyNeedPassword || passkeyNeedCaptcha) passkeyLoading = false
    }
    
    GlassWindowHost {
    val backdrop = LocalControlBackdrop.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("login-screen")
            .semantics { contentDescription = "login-screen" }
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Icon
            AnimatedVisibility(
                visible = visible,
                enter = androidx.compose.animation.scaleIn(initialScale = 0.9f, animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow, dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.buct.course.R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.buct.course.R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "课表、成绩与选课",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Login Card (Glassmorphism / Outline style)
            AnimatedVisibility(
                visible = visible,
                enter = loginPanelEnter
            ) {
                val cardInner: @Composable ColumnScope.() -> Unit = {
                    // Settings button
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Settings button
                            IconButton(
                                onClick = { showEditSchoolDialog = true },
                                modifier = Modifier.align(Alignment.CenterEnd).size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "编辑学校配置",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // School Selector
                        Text(
                            text = "选择学校",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Neutral700,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        var selectedSchool by remember { mutableStateOf<SchoolConfig?>(null) }
                        var showAddSchoolDialog by remember { mutableStateOf(false) }
                        val passwordOnlyLogin = selectedSchool?.passwordOnly == true

                        LaunchedEffect(passwordOnlyLogin) {
                            if (passwordOnlyLogin) loginTab = 0
                        }
                        
                        // Keep the user's current choice when the list refreshes after add/edit.
                        LaunchedEffect(schools) {
                            val chosenId = selectedSchool?.id
                                ?: UserManager.getInstance().currentSchool?.id
                            val school = schools.firstOrNull { it.id == chosenId } ?: schools.firstOrNull()
                            if (school != null) {
                                selectedSchool = school
                                onSchoolSelected(school)
                            }
                        }

                        val selectedSchoolIndex = schools
                            .indexOfFirst { it.id == selectedSchool?.id }
                            .takeIf { it >= 0 }
                        SystemPicker(
                            options = schools.map { it.name },
                            selectedIndex = selectedSchoolIndex,
                            onSelect = { index ->
                                selectedSchool = schools[index]
                                onSchoolSelected(schools[index])
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "请选择学校",
                            actionLabel = "添加学校",
                            onAction = { showAddSchoolDialog = true },
                            backdrop = backdrop,
                            maxLabelLines = 2
                        )
                        if (selectedSchool?.loginHint?.isNotBlank() == true) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = selectedSchool?.loginHint.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        
                        // Add School Dialog
                        if (showAddSchoolDialog) {
                            AddSchoolDialog(
                                onDismiss = { showAddSchoolDialog = false },
                                onConfirm = { draft ->
                                    val newSchool = draft.toSchoolConfig()
                                    com.buct.course.manager.UserManager.getInstance().addCustomSchool(newSchool)
                                    selectedSchool = newSchool
                                    onSchoolSelected(newSchool)
                                    onSchoolAdded()
                                    showAddSchoolDialog = false
                                }
                            )
                        }
                        
                        // Edit School Config Dialog
                        if (showEditSchoolDialog && selectedSchool != null) {
                            EditSchoolConfigDialog(
                                school = selectedSchool!!,
                                onDismiss = { showEditSchoolDialog = false },
                                onSave = { updatedSchool ->
                                    showEditSchoolDialog = false
                                    com.buct.course.manager.UserManager.getInstance().updateSchoolConfig(updatedSchool)
                                    selectedSchool = updatedSchool
                                    onSchoolSelected(updatedSchool)
                                    onSchoolAdded()
                                }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))

                        // 登录方式切换（密码 / 短信 / 通行密钥）。研究生 GMIS 只有密码登录，
                        // 不显示其它入口，避免用户进入学校未开放的流程。
                        if (onPasswordLogin != null && !passwordOnlyLogin) {
                            SystemSegmentedControl(
                                options = listOf("密码登录", "短信登录", "通行密钥"),
                                selectedIndex = loginTab,
                                onSelect = {
                                    loginTab = it
                                    // 切换登录方式时清空上一种方式遗留的错误与临时状态
                                    onClearError()
                                    captchaInput = ""
                                    captchaSubmitting = false
                                    smsCode = ""
                                    smsCaptchaInput = ""
                                    smsSending = false
                                    passkeyLoading = false
                                },
                                backdrop = backdrop
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (loginTab == 0 && onPasswordLogin != null) {
                            // 密码登录表单
                            Text(
                                text = "账号密码",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Neutral700,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // 已保存账号下拉选择
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val savedAccounts = remember {
                                UserManager.getInstance().accountsForCurrentSchool
                            }
                            var expanded by remember { mutableStateOf(false) }
                            var selectedAccount by remember { mutableStateOf("") }

                            if (savedAccounts.isNotEmpty()) {
                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = it }
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Transparent,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                                    ) {
                                        Text(
                                            text = selectedAccount.ifEmpty { "选择已保存账号" },
                                            modifier = Modifier.padding(16.dp),
                                            color = if (selectedAccount.isEmpty()) Color.Gray else Color.Black
                                        )
                                    }
                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        savedAccounts.forEach { account ->
                                            DropdownMenuItem(
                                                text = { Text("${account.getDisplayName()} (${account.getAccountIdText()})") },
                                                onClick = {
                                                    selectedAccount = account.getDisplayName()
                                                    username = account.studentId.ifEmpty { account.username }
                                                    password = com.buct.course.manager.CredentialStore.load(context, account.key) ?: ""
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }


                            GlassTextField(
                                value = username,
                                onValueChange = { username = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = "学号",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                minHeight = 50.dp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            GlassTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = "密码",
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailing = {
                                    IconButton(
                                        onClick = { showPassword = !showPassword },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                minHeight = 50.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // 验证码（金智统一认证 system 验证码）：需要时内嵌一行显示
                            if (captchaVisible && captchaImageBytes != null) {
                                val bitmap = remember(captchaImageBytes) {
                                    BitmapFactory.decodeByteArray(captchaImageBytes, 0, captchaImageBytes.size)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "验证码",
                                            modifier = Modifier
                                                .size(width = 110.dp, height = 46.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onCaptchaRefresh?.invoke() }
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 110.dp, height = 46.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("验证码", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    GlassTextField(
                                        value = captchaInput,
                                        onValueChange = { captchaInput = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = "请输入验证码",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                        minHeight = 46.dp
                                    )
                                    IconButton(
                                        onClick = { onCaptchaRefresh?.invoke() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "刷新验证码",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                        } else if (loginTab == 1 && onPortalCookie != null) {
                            // 短信验证码登录（原生接口链：图形码 → 发短信 → 短信码登录）
                            Text(
                                text = "短信验证码登录",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Neutral700,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GlassTextField(
                                value = smsUsername,
                                onValueChange = { smsUsername = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = "学号",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                minHeight = 50.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            when {
                                // 阶段1：未获取图形码
                                smsCaptchaBytes == null && !smsCodeSent -> {
                                    Text(
                                        text = "第一步：获取图形验证码，识别后发送短信",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                // 阶段2：图形码已返回，输入图形码发短信
                                smsCodeSent.not() -> {
                                    val smsCaptchaBitmap = smsCaptchaBytes?.let { bytes ->
                                        runCatching {
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                        }.getOrNull()
                                    }
                                    if (smsCaptchaBitmap != null) {
                                        Image(
                                            bitmap = smsCaptchaBitmap,
                                            contentDescription = "短信图形验证码",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        GlassTextField(
                                            value = smsCaptchaInput,
                                            onValueChange = { smsCaptchaInput = it },
                                            modifier = Modifier.weight(1f),
                                            placeholder = "图形验证码",
                                            minHeight = 50.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                smsCaptchaInput = ""
                                                onSmsSendCode?.invoke(smsUsername)
                                            },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "刷新图形验证码",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                // 阶段3：短信已发出，输入短信码登录
                                else -> {
                                    Text(
                                        text = "验证码已发送到绑定手机（120 秒内有效），请尽快输入",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    GlassTextField(
                                        value = smsCode,
                                        onValueChange = { smsCode = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = "短信验证码",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        minHeight = 50.dp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "短信码 120 秒内有效，输入后请尽快点「短信登录」",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                }
                            }
                        } else if (loginTab == 2 && onPasskeyLogin != null) {
                            // 通行密钥登录表单（原生：输学号后调起系统通行密钥；未注册则输密码创建）
                            Text(
                                text = "通行密钥",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Neutral700,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GlassTextField(
                                value = passkeyUsername,
                                onValueChange = { passkeyUsername = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = "学号",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                minHeight = 50.dp
                            )
                            if (passkeyNeedPassword) {
                                Spacer(modifier = Modifier.height(8.dp))
                                GlassTextField(
                                    value = passkeyPassword,
                                    onValueChange = { passkeyPassword = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = "统一认证密码",
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    minHeight = 50.dp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                GlassTextField(
                                    value = passkeyNickname,
                                    onValueChange = { passkeyNickname = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = "设备名称（如：我的手机）",
                                    minHeight = 50.dp
                                )
                            }
                            if (passkeyNeedCaptcha) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val passkeyCaptchaBitmap = passkeyCaptchaBytes?.let { bytes ->
                                    runCatching {
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                    }.getOrNull()
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    GlassTextField(
                                        value = passkeyCaptchaInput,
                                        onValueChange = { passkeyCaptchaInput = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = "图形验证码",
                                        minHeight = 50.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (passkeyCaptchaBitmap != null) {
                                        Image(
                                            bitmap = passkeyCaptchaBitmap,
                                            contentDescription = "图形验证码",
                                            modifier = Modifier.size(72.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when {
                                    passkeyNeedPassword ->
                                        "该设备未注册通行密钥，输入统一认证密码后将自动创建并登录（首次需创建一次）"
                                    else ->
                                        "输入学号后点「登录」，将调起系统指纹 / 面部识别完成通行密钥认证"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                        }
                        
                        // Error Message
                        AnimatedVisibility(visible = errorMessage != null) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SemanticDanger
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Login Button
                        if (loginTab == 0 && onPasswordLogin != null) {
                            SystemPrimaryButton(
                                text = when {
                                    isLoading -> "登录中…"
                                    captchaVisible -> "登录"
                                    else -> "密码登录"
                                },
                                onClick = {
                                    if (captchaVisible && captchaInput.isNotBlank()) {
                                        captchaSubmitting = true
                                        onCaptchaSubmit?.invoke(captchaInput)
                                    } else {
                                        onPasswordLogin(username, password)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                enabled = if (captchaVisible) {
                                    captchaInput.isNotBlank() && !isLoading && !captchaSubmitting
                                } else {
                                    username.isNotBlank() && password.isNotBlank() && !isLoading
                                }
                            )
                        } else if (loginTab == 1 && onPortalCookie != null) {
                            SystemPrimaryButton(
                                text = when {
                                    isLoading -> "登录中…"
                                    smsCodeSent.not() && smsCaptchaBytes != null -> "发送短信验证码"
                                    smsCodeSent -> "短信登录"
                                    else -> "获取图形验证码"
                                },
                                onClick = {
                                    when {
                                        smsCodeSent -> {
                                            if (smsCode.isNotBlank()) {
                                                smsSending = true
                                                onSmsLogin?.invoke(smsUsername, smsCode)
                                            }
                                        }
                                        smsCaptchaBytes != null -> {
                                            if (smsCaptchaInput.isNotBlank()) {
                                                smsSending = true
                                                onSmsCaptchaSubmit?.invoke(smsCaptchaInput)
                                            }
                                        }
                                        else -> {
                                            if (smsUsername.isNotBlank()) {
                                                smsSending = true
                                                onSmsSendCode?.invoke(smsUsername)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                enabled = when {
                                    isLoading -> false
                                    smsCodeSent -> smsCode.isNotBlank() && !smsSending
                                    smsCaptchaBytes != null -> smsCaptchaInput.isNotBlank() && !smsSending
                                    else -> smsUsername.isNotBlank() && !smsSending
                                }
                            )
                        } else if (loginTab == 2 && onPasskeyLogin != null) {
                            SystemPrimaryButton(
                                text = when {
                                    isLoading -> "登录中…"
                                    passkeyNeedCaptcha -> "提交验证码"
                                    passkeyNeedPassword -> "创建并登录"
                                    else -> "通行密钥"
                                },
                                onClick = {
                                    when {
                                        passkeyNeedCaptcha -> {
                                            if (passkeyCaptchaInput.isNotBlank()) {
                                                passkeyLoading = true
                                                onPasskeyCaptchaSubmit?.invoke(passkeyCaptchaInput)
                                            }
                                        }
                                        passkeyNeedPassword -> {
                                            if (passkeyUsername.isNotBlank() && passkeyPassword.isNotBlank()) {
                                                passkeyLoading = true
                                                onPasskeyRegister?.invoke(passkeyUsername, passkeyPassword, passkeyNickname)
                                            }
                                        }
                                        else -> {
                                            if (passkeyUsername.isNotBlank()) {
                                                passkeyLoading = true
                                                onPasskeyLogin(passkeyUsername)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                enabled = when {
                                    isLoading -> false
                                    passkeyNeedCaptcha -> passkeyCaptchaInput.isNotBlank() && !passkeyLoading
                                    passkeyNeedPassword -> passkeyUsername.isNotBlank() && passkeyPassword.isNotBlank() && !passkeyLoading
                                    else -> passkeyUsername.isNotBlank() && !isLoading
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    cardInner()
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }

    }

    // Student Binding Confirmation Dialog
    if (showBindingDialog) {
        BindingConfirmationDialog(
            studentName = bindingStudentName,
            maxStudents = bindingMaxStudents,
            usedNames = bindingUsedNames,
            usedCount = bindingUsedCount,
            onConfirm = onConfirmBinding,
            onDismiss = onCancelBinding
        )
    }

    if (secondarySmsRequired) {
        com.buct.course.ui.system.SecondaryVerificationDialog(
            phoneMasked = secondarySmsPhone,
            sentUntil = secondarySmsSentUntil,
            captchaBytes = secondarySmsCaptchaBytes,
            sending = secondarySmsSending,
            submitting = secondarySmsSubmitting,
            errorMessage = errorMessage,
            rememberDevice = secondarySmsRememberDevice,
            onRememberChange = { onSecondarySmsRememberChange?.invoke(it) },
            onSendCode = { captcha -> onSecondarySmsSend?.invoke(captcha) },
            onSubmit = { code -> onSecondarySmsSubmit?.invoke(code) },
            onDismiss = { onSecondarySmsCancel?.invoke() }
        )
    }

    // 通行密钥现已原生实现（BuctWebAuthnManager + 系统 Credential Manager），不再使用网页浮层
    // 浮层保留仅兼容旧代码，永不显示
    if (false && showPortalLogin && isBuctSchool && onPortalCookie != null) {
        PortalLoginWebDialog(
            smsUsername = if (loginTab == 1) smsUsername else null,
            passkeyUsername = if (loginTab == 2) passkeyUsername else null,
            onCookie = { cookie ->
                showPortalLogin = false
                passkeyLoading = false
                onPortalCookie(cookie)
            },
            onDismiss = {
                showPortalLogin = false
                passkeyLoading = false
            }
        )
    }
    } // 关闭 CompositionLocalProvider
}

@Composable
fun BindingConfirmationDialog(
    studentName: String,
    maxStudents: Int,
    usedNames: Set<String>,
    usedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    SystemDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "确认绑定账号",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "确认绑定",
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            SystemSecondaryButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val iconContainerShape = RoundedCornerShape(16.dp)
            Surface(
                modifier = Modifier.size(64.dp),
                shape = iconContainerShape,
                color = NeuPrimary.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    NeuPrimary.copy(alpha = 0.16f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = NeuPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "检测到新账号：「$studentName」",
                style = MaterialTheme.typography.titleMedium,
                color = NeuPrimary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Info Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "绑定配额：",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (maxStudents <= 0) "无限制" else "$usedCount / $maxStudents",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (maxStudents > 0 && usedCount >= maxStudents) SemanticDanger else SemanticSuccess
                        )
                    }

                    if (usedNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "已绑定：${usedNames.joinToString("、")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (maxStudents <= 0) "不同学校可共用设备，不限制学生账号数量。确认后即完成绑定。" else "不同学校可共用设备，合计最多绑定 $maxStudents 个学生账号。确认后占用 1 个名额，且无法撤销。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSchoolDialog(
    onDismiss: () -> Unit,
    onConfirm: (com.buct.course.model.SchoolFormDraft) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var basePath by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("https") }
    var academicSystem by remember { mutableStateOf("auto") }
    var addressError by remember { mutableStateOf<String?>(null) }
    
    fun parseUrl(url: String) {
        val parsed = com.buct.course.academic.AcademicAddress.parse(url)
        if (parsed == null) {
            addressError = "无法解析，请检查教务网址"
            return
        }
        addressError = null
        protocol = parsed.protocol
        domain = parsed.domain
        basePath = parsed.basePath
    }
    
    val draft = com.buct.course.model.SchoolFormDraft(name, domain, protocol, basePath, academicSystem)
    val showError = domain.isNotBlank() && !draft.isValidDomain
    
    SystemDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "添加学校",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "添加",
                onClick = {
                    onConfirm(draft)
                },
                enabled = draft.isValid && addressError == null,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            SystemSecondaryButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SchoolFormPanel {
                SchoolFormPanelTitle(
                    icon = Icons.Default.AutoAwesome,
                    text = "从网址填写"
                )
                SchoolFormField(
                    label = "教务系统网址",
                    value = urlInput,
                    onValueChange = { urlInput = it; addressError = null },
                    placeholder = "http://jwxt.example.edu.cn/jwglxt",
                    helper = "粘贴教务登录页或登录后的页面网址，解析域名、协议和基础路径",
                    error = addressError
                )
                SystemPrimaryButton(
                    text = "解析网址",
                    onClick = { parseUrl(urlInput) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = urlInput.isNotBlank()
                )
            }

            SchoolFormSectionTitle("基本信息")

            SchoolAcademicSystemField(academicSystem) { academicSystem = it }

            SchoolFormField(
                label = "学校名称（可选）",
                value = name,
                onValueChange = { name = it },
                placeholder = "例如：XX 大学"
            )

            SchoolFormField(
                label = "教务系统域名",
                value = domain,
                onValueChange = { domain = it },
                placeholder = "jwxt.example.edu.cn",
                helper = "非标准端口可直接带上，如 jw.example.edu.cn:30443",
                error = if (showError) "请输入有效域名" else null
            )

            SchoolFormField(
                label = "基础路径",
                value = basePath,
                onValueChange = { basePath = it },
                placeholder = "/jwglxt、/jsxsd 或留空",
                helper = "优先从完整网址解析；教务位于网站根目录时留空"
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "协议",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 两个选项用分段控件，不用下拉：一次点击就能选完，也和全 App 一致
                SystemSegmentedControl(
                    options = listOf("HTTPS", "HTTP"),
                    selectedIndex = if (protocol == "https") 0 else 1,
                    onSelect = { index -> protocol = if (index == 0) "https" else "http" },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (protocol == "https") "加密连接，推荐优先尝试" else "非加密连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

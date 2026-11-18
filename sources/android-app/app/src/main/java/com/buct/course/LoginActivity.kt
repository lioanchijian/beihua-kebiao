package com.buct.course

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.buct.course.demo.DemoData
import com.buct.course.manager.UserManager
import com.buct.course.model.SchoolConfig
import com.buct.course.network.CourseApiClient
import com.buct.course.ui.screen.LoginScreen
import com.buct.course.ui.screen.SchoolAdaptationCompletionReminder
import com.buct.course.ui.screen.SchoolAdaptationFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import com.buct.course.ui.theme.CourseSelectorTheme
import com.buct.course.utils.CourseParser
import com.buct.course.login.BuctSmsLoginManager
import com.buct.course.login.BuctSsoLoginManager
import com.buct.course.login.BuctWebAuthnManager
import com.buct.course.login.PasswordLoginCallback
import com.buct.course.login.PasswordLoginGateway
import com.buct.course.login.PasswordLoginGatewayFactory
import com.buct.course.academic.AcademicGatewayFactory
import com.buct.course.academic.AcademicPasswordLoginGateway
import com.buct.course.AcademicWebViewActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.buct.course.login.BuctSoftwarePasskey
import java.security.Signature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

class LoginActivity : androidx.fragment.app.FragmentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.buct.course.manager.AppThemeCoordinator.wrapContext(newBase))
    }

    companion object {
        private const val TAG = "LoginActivity"
        const val EXTRA_RETURN_TO_CALLER = "return_to_caller"
    }

    private var isLoading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var cookieFromWebView by mutableStateOf("")
    private var academicWebPageUrl = ""
    private var academicValidationGeneration = 0
    private var isAutoValidating by mutableStateOf(false)
    private var selectedLoginSchool: SchoolConfig? = null
    private var validationCall: Call? = null
    private val validationAccounts = mutableMapOf<String, SchoolConfig>()
    private var validationJob: kotlinx.coroutines.Job? = null

    // Binding Dialog State
    private var showBindingDialog by mutableStateOf(false)
    private var bindingStudentName by mutableStateOf("")
    private var bindingStudentId by mutableStateOf("")
    private var bindingMaxStudents by mutableStateOf(0)
    private var bindingUsedCount by mutableStateOf(0)
    private var bindingUsedNames by mutableStateOf<Set<String>>(emptySet())
    private var pendingCookie by mutableStateOf("")
    private var pendingPasswordLogin by mutableStateOf(false)

    // Password Login State
    private var activePasswordLoginGateway: PasswordLoginGateway? = null
    private var pendingPasswordSchool: SchoolConfig? = null
    private var pendingPasswordUsername = ""
    private var pendingPasswordValue = ""
    private var captchaImageBytes by mutableStateOf<ByteArray?>(null)
    private var secondarySmsRequired by mutableStateOf(false)
    private var secondarySmsPhoneMask by mutableStateOf("")
    private var secondarySmsSending by mutableStateOf(false)
    private var secondarySmsSubmitting by mutableStateOf(false)
    /** 对应学校认证页的「记住该设备」 */
    private var secondarySmsRememberDevice by mutableStateOf(true)
    private var secondarySmsSentUntil by mutableStateOf(0L)
    private var secondarySmsCaptchaBytes by mutableStateOf<ByteArray?>(null)
    private var smsCodeSentState by mutableStateOf(false)
    private var smsCaptchaBytes by mutableStateOf<ByteArray?>(null)
    private var smsInFlight = false
    private var smsClient: okhttp3.OkHttpClient? = null
    private var smsCookieJar: okhttp3.CookieJar? = null
    private val smsUa = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36"

    // 通行密钥（原生 WebAuthn）状态
    private var passkeyInFlight = false
    private var pendingPasskeyRequestId = ""
    private var pendingPasskeyFinishUrl = ""
    private var pendingPasskeySessionToken: String? = null
    private var pendingPasskeyUsername = ""
    private var passkeyBiometricPrompt: BiometricPrompt? = null
    // 通行密钥注册（创建）状态
    private var passkeyNeedPassword by mutableStateOf(false)
    private var passkeyNeedCaptcha by mutableStateOf(false)
    private var passkeyCaptchaBytes = mutableStateOf<ByteArray?>(null)
    private var pendingSidCookie = ""
    private var pendingRegisterUsername = ""
    private var pendingRegisterNickname = "北化课表"
    private var pendingRegisterRequestId = ""
    private var pendingRegisterFinishUrl = ""
    private var pendingRegisterSessionToken: String? = null
    private var sidPortalSso: BuctSsoLoginManager? = null

    // WebView result launcher
    private val webViewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val cookie = result.data?.getStringExtra(CookieWebViewActivity.EXTRA_COOKIE_RESULT)
            if (!cookie.isNullOrBlank()) {
                cookieFromWebView = cookie
                academicWebPageUrl = result.data?.getStringExtra(AcademicWebViewActivity.EXTRA_PAGE_URL).orEmpty()
                Toast.makeText(this, "Cookie 已获取，点击登录", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.buct.course.ui.theme.StartupLogoAnimation.install(this)
        super.onCreate(savedInstanceState)
        
        // Initialize UserManager with context for SharedPreferences
        UserManager.getInstance().init(this)
        selectedLoginSchool = UserManager.getInstance().currentSchool
        
            // 🔄 每次启动 App 都同步云端激活配置（获取最新的 max_students）
        lifecycleScope.launch {
            if (!BuildConfig.UI_PREVIEW) try {
                withContext(Dispatchers.IO) {
                    com.buct.course.activation.ActivationManager.checkActivation(this@LoginActivity)
                }
                Log.d(TAG, "已同步云端配置")
            } catch (e: Exception) {
                Log.w(TAG, "启动同步失败: ${e.message}")
            }
            
            // 🔧 关键修复：如果是为了“重新登录”而跳转过来的，不要执行自动登录检查
            val forceRelogin = intent.getBooleanExtra("force_relogin", false)
            if (forceRelogin) {
                errorMessage = "请重新登录以继续使用"
            } else {
                // 检查是否有保存的有效登录状态
                checkSavedLoginState()
            }
        }
        
        setContent {
            CourseSelectorTheme {
                // Use mutableStateOf for reactive schools list
                var schools by remember { mutableStateOf(UserManager.getInstance().supportedSchools) }
                var showSchoolAdaptation by remember { mutableStateOf(false) }
                
                // 🔧 强化版学校选择记忆逻辑
                LaunchedEffect(schools) {
                    val userManager = UserManager.getInstance()
                    // 1. 如果当前没有选定学校，先尝试加载存过的
                    if (selectedLoginSchool == null) {
                        userManager.loadLoginState()
                        selectedLoginSchool = userManager.currentSchool
                    }
                    
                    // 2. 如果加载后依然没选中任何学校（比如第一次用），才选第一个
                    if (schools.isNotEmpty() && selectedLoginSchool == null) {
                        selectedLoginSchool = schools[0]
                    }
                }
                
                // 如果正在自动验证，显示加载状态
                if (showSchoolAdaptation) {
                    SchoolAdaptationFlow(
                        onNavigateBack = { showSchoolAdaptation = false }
                    )
                } else {
                    LoginScreen(
                    onBack = if (intent.getBooleanExtra(EXTRA_RETURN_TO_CALLER, false)) {
                        { onBackPressedDispatcher.onBackPressed() }
                    } else null,
                    onClearError = { errorMessage = null },
                    schools = schools,
                    onSchoolSelected = { school ->
                        selectedLoginSchool = school
                        academicValidationGeneration++
                        validationCall?.cancel()
                        validationJob?.cancel()
                        clearValidationSessions()
                        discardPendingPasswordLogin()
                        isLoading = false
                    },
                    onLoginClick = { cookie ->
                        handleLogin(cookie)
                    },
                    onOpenWebView = {
                        openWebView()
                    },
                    onSchoolAdded = {
                        // Refresh schools list after adding
                        schools = UserManager.getInstance().supportedSchools
                    },
                    onDemoMode = {
                        handleDemoMode()
                    },
                    onSchoolAdaptation = {
                        showSchoolAdaptation = true
                    },
                    onPasswordLogin = { username, password ->
                        handlePasswordLogin(username, password)
                    },
                    onSmsSendCode = { smsUsername ->
                        sendSmsCode(smsUsername)
                    },
                    onSmsCaptchaSubmit = { captcha ->
                        submitSmsCaptcha(captcha)
                    },
                    onSmsLogin = { smsUsername, smsCode ->
                        smsLogin(smsUsername, smsCode)
                    },
                    smsCodeSent = smsCodeSentState,
                    smsCaptchaBytes = smsCaptchaBytes,
                    secondarySmsRequired = secondarySmsRequired,
                    secondarySmsPhone = secondarySmsPhoneMask,
                    secondarySmsSentUntil = secondarySmsSentUntil,
                    secondarySmsCaptchaBytes = secondarySmsCaptchaBytes,
                    secondarySmsSending = secondarySmsSending,
                    secondarySmsSubmitting = secondarySmsSubmitting,
                    secondarySmsRememberDevice = secondarySmsRememberDevice,
                    onSecondarySmsRememberChange = { secondarySmsRememberDevice = it },
                    onSecondarySmsSend = { captcha -> sendSecondarySmsCode(captcha) },
                    onSecondarySmsSubmit = { code -> submitSecondarySmsCode(code) },
                    onSecondarySmsCancel = {
                        discardPendingPasswordLogin()
                        errorMessage = null
                    },
                    onPasskeyLogin = { passkeyUsername ->
                        openPasskeyLogin(passkeyUsername)
                    },
                    passkeyNeedPassword = passkeyNeedPassword,
                    passkeyNeedCaptcha = passkeyNeedCaptcha,
                    passkeyCaptchaBytes = passkeyCaptchaBytes.value,
                    onPasskeyRegister = { username, password, nickname ->
                        passkeyRegisterAndLogin(username, password, nickname)
                    },
                    onPasskeyCaptchaSubmit = { code ->
                        passkeySubmitCaptcha(code)
                    },
                    onPortalCookie = { cookie ->
                        val school = selectedLoginSchool
                        if (school != null) {
                            pendingPasswordLogin = false
                            performLoginValidation(school, cookie)
                        } else {
                            errorMessage = "请先选择学校"
                        }
                    },
                    captchaImageBytes = captchaImageBytes,
                    onCaptchaSubmit = { code ->
                        handleCaptchaSubmit(code)
                    },
                    onCaptchaRefresh = {
                        refreshCaptcha()
                    },
                    isLoading = isLoading || isAutoValidating,
                    errorMessage = if (isAutoValidating) "正在验证登录状态..." else errorMessage,
                    cookieValue = cookieFromWebView,
                    showBindingDialog = showBindingDialog,
                    bindingStudentName = bindingStudentName,
                    bindingMaxStudents = bindingMaxStudents,
                    bindingUsedNames = bindingUsedNames,
                    bindingUsedCount = bindingUsedCount,
                    onConfirmBinding = {
                        showBindingDialog = false
                        val recorded = com.buct.course.manager.StudentLimitManager.recordStudent(
                            context = this@LoginActivity,
                            schoolId = selectedLoginSchool?.id.orEmpty(),
                            schoolName = selectedLoginSchool?.name.orEmpty(),
                            studentName = bindingStudentName,
                            studentId = bindingStudentId
                        )
                        if (recorded) {
                            proceedToMain(UserManager.getInstance(), bindingStudentName, pendingCookie)
                        } else {
                            discardPendingPasswordLogin()
                            errorMessage = "账号数量已达上限"
                        }
                    },
                    onCancelBinding = {
                        showBindingDialog = false
                        discardPendingPasswordLogin()
                        errorMessage = "已取消，账号未绑定"
                    }
                )
                }
                SchoolAdaptationCompletionReminder(
                    enabled = !showSchoolAdaptation &&
                        !showBindingDialog &&
                        captchaImageBytes == null &&
                        !isLoading &&
                        !isAutoValidating
                )
            }
        }
    }

    // 检查保存的登录状态，有 Cookie 直接进入主页面
    private fun checkSavedLoginState() {
        val userManager = UserManager.getInstance()
        
        if (userManager.hasSavedCookie() && userManager.currentSchool != null) {
            Log.d(TAG, "发现保存的 Cookie，直接进入主页面")
            
            val savedCookie = userManager.savedCookie
            val currentSchool = userManager.currentSchool
            
            // 设置 Cookie 到 API Client
            CourseApiClient.getInstance().setCookie(currentSchool.baseUrl, savedCookie)
            
            // 直接跳转到主页面，不验证 Cookie
            userManager.isLoggedIn = true
            
            Toast.makeText(
                this@LoginActivity,
                "欢迎回来，${userManager.studentName.ifEmpty { "同学" }}",
                Toast.LENGTH_SHORT
            ).show()
            
            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            finish()
        }
    }

    private fun openWebView() {
        val currentSchool = selectedLoginSchool
        if (currentSchool != null && AcademicGatewayFactory.supports(currentSchool)) {
            val hosts = java.util.ArrayList<String>().apply {
                add(currentSchool.domain)
                addAll(currentSchool.allowedAcademicHosts)
            }
            val intent = Intent(this, AcademicWebViewActivity::class.java).apply {
                putExtra(AcademicWebViewActivity.EXTRA_START_URL, AcademicGatewayFactory.loginUrl(currentSchool))
                putExtra(AcademicWebViewActivity.EXTRA_COOKIE_URL, currentSchool.fullBasePath.trimEnd('/') + "/")
                putExtra(AcademicWebViewActivity.EXTRA_SEARCH_KEYWORD, "${currentSchool.name} 教务系统 登录")
                putStringArrayListExtra(AcademicWebViewActivity.EXTRA_ALLOWED_HOSTS, hosts)
            }
            webViewLauncher.launch(intent)
            return
        }
        val searchKeyword = if (currentSchool != null) {
            "${currentSchool.name} 教务系统"
        } else {
            "教务系统 登录"
        }
        
        val intent = Intent(this, CookieWebViewActivity::class.java).apply {
            putExtra(CookieWebViewActivity.EXTRA_SEARCH_KEYWORD, searchKeyword)
        }
        webViewLauncher.launch(intent)
    }

    // 通行密钥登录（原生 WebAuthn）：金智 /webauthn/authenticate 加密链路
    // + Android Credential Manager 系统指纹/面部 + finish 提交 + ticket 跟随
    // 未注册设备自动引导：密码登录 sidportal → 创建通行密钥 → 再认证登录
    private fun openPasskeyLogin(username: String) {
        Toast.makeText(this, "正在获取通行密钥…", Toast.LENGTH_SHORT).show()
        if (passkeyInFlight) {
            Toast.makeText(this, "上一次请求还未完成，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        val school = selectedLoginSchool
        if (school == null || school.id != "buct") {
            errorMessage = "当前学校暂不支持通行密钥登录"
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            return
        }
        if (username.isBlank()) {
            errorMessage = "请输入学号"
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            return
        }
        if (android.os.Build.VERSION.SDK_INT < 34) {
            errorMessage = "通行密钥需要 Android 14 及以上系统"
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            return
        }
        passkeyInFlight = true
        passkeyNeedPassword = false
        passkeyNeedCaptcha = false
        passkeyCaptchaBytes.value = null
        pendingPasskeyUsername = username
        errorMessage = null
        Log.d("BuctPasskey", "passkey native login, username=$username")
        BuctWebAuthnManager.get().startAuthentication(username, passkeyAuthCallback)
    }

    private val passkeyAuthCallback: BuctWebAuthnManager.WebAuthnCallback =
        object : BuctWebAuthnManager.WebAuthnCallback {
        override fun onOptions(requestId: String, requestJson: String, finishUrl: String, sessionToken: String?) {
            runOnUiThread {
                pendingPasskeyRequestId = requestId
                pendingPasskeyFinishUrl = finishUrl
                pendingPasskeySessionToken = sessionToken
                Log.d("BuctPasskey", "options ready, requestId=$requestId finish=$finishUrl")
                startSoftwarePasskeyAuthentication(requestJson)
            }
        }

        override fun onSuccess(cookie: String) {
            runOnUiThread {
                passkeyInFlight = false
                passkeyNeedPassword = false
                passkeyNeedCaptcha = false
                Log.d("BuctPasskey", "passkey login success, cookie len=${cookie.length}")
                val school = selectedLoginSchool ?: return@runOnUiThread
                pendingPasswordLogin = false
                performLoginValidation(school, cookie)
            }
        }

        override fun onError(message: String) {
            runOnUiThread {
                passkeyInFlight = false
                if (message.contains("未注册") || message.contains("No credential") || message.contains("not registered")) {
                    // 该学号没有可用通行密钥：直接在 App 内显示密码框，密码登录 sidportal 后
                    // 创建新通行密钥；不再把用户赶去浏览器（浏览器注册后回到 App 仍可能因
                    // 旧凭据未真正完成 finish 而无法认证）。
                    passkeyNeedPassword = true
                    passkeyNeedCaptcha = false
                    passkeyCaptchaBytes.value = null
                    errorMessage = "该学号尚未创建可用通行密钥。请输入统一认证密码，首次创建后即可用指纹/面部登录。"
                } else {
                    errorMessage = message
                }
            }
        }
    }

    /** 引导用户在浏览器注册通行密钥：不 App 内创建（App 内创建曾 operation not allowed，浏览器侧可稳定创建） */
    private fun guideBrowserRegister(reason: String) {
        passkeyInFlight = false
        passkeyNeedPassword = false
        passkeyNeedCaptcha = false
        errorMessage = "$reason。\n\n请先在浏览器注册通行密钥：电脑或手机浏览器打开 https://sidportal.buct.edu.cn 登录 → 个人中心 → 安全设置 → 通行密钥管理 → 创建通行密钥（选择“连接手机”，按提示录指纹）。注册成功后回到本 App，点“通行密钥”直接用指纹登录。"
        Toast.makeText(this@LoginActivity, "请先在浏览器注册通行密钥", Toast.LENGTH_LONG).show()
    }

    // ---------- 通行密钥注册（创建）流程 ----------

    /** 用户输入密码后：密码登录 sidportal → 创建通行密钥 → 再认证登录 */
    private fun passkeyRegisterAndLogin(username: String, password: String, nickname: String) {
        if (passkeyInFlight) return
        if (username.isBlank() || password.isBlank()) {
            errorMessage = "请输入学号和密码"
            return
        }
        passkeyInFlight = true
        passkeyNeedCaptcha = false
        pendingRegisterUsername = username
        pendingRegisterNickname = nickname
        errorMessage = null
        Log.d("BuctPasskey", "register flow: password login sidportal")
        val school = selectedLoginSchool
        if (school == null) {
            passkeyInFlight = false
            errorMessage = "请先选择学校"
            return
        }
        val sso = PasswordLoginGatewayFactory.create(school) as? BuctSsoLoginManager
        if (sso == null) {
            passkeyInFlight = false
            errorMessage = "当前学校暂不支持通行密钥创建"
            return
        }
        sidPortalSso = sso
        sso.loginSidPortalOnly(username, password, sidPortalCallback)
    }

    /** 创建流程中密码登录遇到图形验证码 */
    private fun passkeySubmitCaptcha(captchaCode: String) {
        if (captchaCode.isBlank()) return
        passkeyInFlight = true
        errorMessage = null
        val sso = sidPortalSso ?: run {
            passkeyInFlight = false
            errorMessage = "创建会话已失效，请重新开始"
            return
        }
        sso.submitSidPortalCaptcha(captchaCode, sidPortalCallback)
    }

    private val sidPortalCallback: BuctSsoLoginManager.SidPortalOnlyCallback =
        object : BuctSsoLoginManager.SidPortalOnlyCallback {
        override fun onSidPortalAuthenticated(sidCookie: String) {
            runOnUiThread {
                pendingSidCookie = sidCookie
                Log.d("BuctPasskey", "sidportal authenticated, cookie len=${sidCookie.length}")
                val username = pendingRegisterUsername
                if (username.isBlank()) {
                    passkeyInFlight = false
                    errorMessage = "创建会话失效，请重试"
                    return@runOnUiThread
                }
                // 网页端创建前必经「个人中心→安全设置页」：先探测建立管理上下文，再注册
                BuctWebAuthnManager.get().probeSafeCenterContext(sidCookie) { mergedCookie ->
                    runOnUiThread {
                        BuctWebAuthnManager.get().startRegistration(
                            mergedCookie, username, username, pendingRegisterNickname, registrationCallback)
                    }
                }
            }
        }

        override fun onInvalidCredentials() {
            runOnUiThread {
                passkeyInFlight = false
                errorMessage = "学号或密码错误"
            }
        }

        override fun onCaptchaRequired(pngBytes: ByteArray, message: String) {
            runOnUiThread {
                passkeyNeedCaptcha = true
                passkeyCaptchaBytes.value = pngBytes
                passkeyInFlight = false
                errorMessage = message
            }
        }

        override fun onError(message: String) {
            runOnUiThread {
                passkeyInFlight = false
                errorMessage = message
            }
        }
    }

    private fun startSoftwarePasskeyAuthentication(requestJson: String) {
        val username = pendingPasskeyUsername
        if (username.isBlank()) {
            passkeyInFlight = false
            errorMessage = "通行密钥会话已失效，请重新输入学号"
            return
        }
        try {
            val session = BuctSoftwarePasskey.beginAuthentication(this, username, requestJson)
            promptPasskeyBiometric(
                title = "通行密钥登录",
                subtitle = "验证指纹、面部或设备密码",
                signature = session.signature
            ) {
                try {
                    val credentialJson = BuctSoftwarePasskey.completeAssertion(this@LoginActivity, session)
                    BuctWebAuthnManager.get().submitAuthentication(
                        pendingPasskeyRequestId, pendingPasskeyFinishUrl,
                        credentialJson, pendingPasskeySessionToken, passkeyAuthCallback
                    )
                } catch (e: Exception) {
                    passkeyInFlight = false
                    errorMessage = "通行密钥签名失败，请重试"
                }
            }
        } catch (e: BuctSoftwarePasskey.NotRegisteredException) {
            passkeyInFlight = false
            passkeyNeedPassword = true
            passkeyNeedCaptcha = false
            passkeyCaptchaBytes.value = null
            errorMessage = "该学号尚未创建可用通行密钥。请输入统一认证密码，首次创建后即可用指纹/面部登录。"
        } catch (e: Exception) {
            passkeyInFlight = false
            errorMessage = "通行密钥不可用：${e.message}"
        }
    }

    private fun promptPasskeyBiometric(
        title: String,
        subtitle: String,
        signature: Signature? = null,
        onSuccess: () -> Unit
    ) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val can = BiometricManager.from(this).canAuthenticate(authenticators)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            passkeyInFlight = false
            errorMessage = "请先在系统设置中录入指纹/面部或设置锁屏密码"
            return
        }
        var completed = false
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (completed) return
                    completed = true
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (completed) return
                    completed = true
                    passkeyInFlight = false
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED) {
                        errorMessage = "通行密钥验证失败：$errString"
                    }
                }
                override fun onAuthenticationFailed() {
                    Toast.makeText(this@LoginActivity, "指纹/面部未识别，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        )
        passkeyBiometricPrompt = prompt
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()
        if (signature != null) {
            prompt.authenticate(info, BiometricPrompt.CryptoObject(signature))
        } else {
            prompt.authenticate(info)
        }
    }

    private val registrationCallback: BuctWebAuthnManager.RegistrationCallback =
        object : BuctWebAuthnManager.RegistrationCallback {
        override fun onCreationOptions(requestId: String, creationJson: String, finishUrl: String, sessionToken: String?) {
            runOnUiThread {
                pendingRegisterRequestId = requestId
                pendingRegisterFinishUrl = finishUrl
                pendingRegisterSessionToken = sessionToken
                val username = pendingRegisterUsername
                Log.d("BuctPasskey", "creation options ready, requestId=$requestId")
                promptPasskeyBiometric(
                    title = "创建通行密钥",
                    subtitle = "验证指纹、面部或设备密码"
                ) {
                    try {
                        val credentialJson = BuctSoftwarePasskey.createCredential(
                            this@LoginActivity, username, creationJson
                        )
                        BuctWebAuthnManager.get().submitRegistration(
                            pendingSidCookie, pendingRegisterRequestId,
                            pendingRegisterFinishUrl, credentialJson,
                            pendingRegisterSessionToken, registrationCallback
                        )
                    } catch (e: Exception) {
                        passkeyInFlight = false
                        errorMessage = "创建通行密钥失败：${e.message}"
                    }
                }
            }
        }

        override fun onRegistered() {
            runOnUiThread {
                Log.d("BuctPasskey", "passkey registered, now login")
                // 创建成功 → 立即用通行密钥认证登录
                passkeyNeedPassword = false
                passkeyNeedCaptcha = false
                passkeyInFlight = true
                errorMessage = null
                BuctWebAuthnManager.get().startAuthentication(pendingRegisterUsername, passkeyAuthCallback)
            }
        }

        override fun onError(message: String) {
            runOnUiThread {
                passkeyInFlight = false
                errorMessage = message
            }
        }
    }

    private fun handleDemoMode() {
        DemoData.resetSession()
        UserManager.getInstance().startDemoSession(DemoData.school())
        Toast.makeText(this, "已进入本地演示模式，不会连接教务系统", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // ================= 短信验证码登录（原生接口链，金智 linkid 0008 业务号） =================
    private fun sendSmsCode(username: String) {
        if (smsInFlight) return
        val school = selectedLoginSchool
        if (school == null || school.id != "buct") {
            errorMessage = "当前学校暂不支持短信登录"
            return
        }
        if (username.isBlank()) {
            errorMessage = "请输入学号"
            return
        }
        smsInFlight = true
        errorMessage = null
        smsCaptchaBytes = null
        smsCodeSentState = false
        BuctSmsLoginManager.get().startLogin(username, smsCallback)
    }

    /** 用户识别图形验证码后发送短信 */
    private fun submitSmsCaptcha(captcha: String) {
        if (smsInFlight) return
        if (captcha.isBlank()) {
            errorMessage = "请输入图形验证码"
            return
        }
        smsInFlight = true
        errorMessage = null
        BuctSmsLoginManager.get().submitCaptcha(captcha, smsCallback)
    }

    private fun smsLogin(username: String, code: String) {
        if (smsInFlight) return
        if (code.isBlank()) {
            errorMessage = "请输入短信验证码"
            return
        }
        errorMessage = null
        smsInFlight = true
        BuctSmsLoginManager.get().submitSmsCode(code, smsCallback)
    }

    private val smsCallback = object : BuctSmsLoginManager.SmsLoginCallback {
        override fun onCaptchaRequired(pngBytes: ByteArray) {
            runOnUiThread {
                smsInFlight = false
                smsCaptchaBytes = pngBytes
            }
        }

        override fun onSmsSent(expireSeconds: Int) {
            runOnUiThread {
                smsInFlight = false
                smsCodeSentState = true
            }
        }

        override fun onSuccess(cookie: String) {
            runOnUiThread {
                smsInFlight = false
                smsCodeSentState = false
                smsCaptchaBytes = null
                val school = selectedLoginSchool ?: return@runOnUiThread
                pendingPasswordLogin = false
                performLoginValidation(school, cookie)
            }
        }

        override fun onCaptchaInvalid() {
            runOnUiThread {
                smsInFlight = false
                errorMessage = "图形验证码错误，请重新输入"
            }
        }

        override fun onSmsCodeInvalid(message: String) {
            runOnUiThread {
                smsInFlight = false
                errorMessage = message
            }
        }

        override fun onSmsSendFailed(message: String) {
            runOnUiThread {
                smsInFlight = false
                errorMessage = message
            }
        }

        override fun onError(message: String) {
            runOnUiThread {
                smsInFlight = false
                errorMessage = message
            }
        }
    }

    private fun smsPostJson(client: okhttp3.OkHttpClient, url: String, json: String): Pair<Int, String> {
        val base = "https://sidportal.buct.edu.cn"
        val csrf = smsCsrfHeaders()
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create(
                "application/json;charset=UTF-8".toMediaType(), json))
            .header("User-Agent", smsUa)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Csrf-Key", csrf.first)
            .header("Csrf-Value", csrf.second)
            .header("Origin", base)
            .header("Referer", base + "/login")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            return resp.code to body
        }
    }

    /** 金智 CSRF 头：随机 32 位 key，Csrf-Value = MD5(base64(key) 前半 + base64(key) + 后半) */
    private fun smsCsrfHeaders(): Pair<String, String> {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val key = (1..32).map { chars.random() }.joinToString("")
        val e = android.util.Base64.encodeToString(key.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val i = e.substring(0, e.length / 2) + e + e.substring(e.length / 2)
        val md5 = java.security.MessageDigest.getInstance("MD5")
            .digest(i.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return key to md5
    }

    private fun handleLogin(cookieStr: String) {
        discardPendingPasswordLogin()
        val currentSchool = selectedLoginSchool
        if (currentSchool == null) {
            errorMessage = "请先选择学校"
            return
        }

        if (cookieStr.isBlank()) {
            errorMessage = "请输入 Cookie"
            return
        }

        isLoading = true
        errorMessage = null
        pendingPasswordLogin = false

        // 🔄 先同步云端激活配置（获取最新的 max_students）
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    com.buct.course.activation.ActivationManager.checkActivation(this@LoginActivity)
                }
                Log.d(TAG, "激活配置已同步，max_students=${com.buct.course.activation.ActivationManager.getMaxStudents(this@LoginActivity)}")
            } catch (e: Exception) {
                Log.w(TAG, "激活配置同步失败: ${e.message}")
            }
            
            // 同步完成后继续登录流程
            pendingPasswordLogin = false
            performLoginValidation(currentSchool, cookieStr)
        }
    }
    
    private fun performLoginValidation(currentSchool: SchoolConfig, cookieStr: String) {
        val generation = ++academicValidationGeneration
        validationCall?.cancel()
        validationJob?.cancel()
        clearValidationSessions()
        val validationKey = "login-validation-" + java.util.UUID.randomUUID()
        validationAccounts[validationKey] = currentSchool
        val academicGateway = activePasswordLoginGateway as? AcademicPasswordLoginGateway
        if (AcademicGatewayFactory.supports(currentSchool)) {
            val username = pendingPasswordUsername.ifBlank {
                runCatching { android.net.Uri.parse(academicWebPageUrl).getQueryParameter("xh") }.getOrNull().orEmpty()
            }
            val key = validationKey
            validationJob = lifecycleScope.launch {
                val result = runCatching { withContext(Dispatchers.IO) {
                    if (currentSchool.academicSystem == "auto" && AcademicGatewayFactory.detect(currentSchool, key) == null)
                        throw IllegalStateException("无法识别教务系统，请在学校配置中手动选择")
                    AcademicGatewayFactory.importCookie(currentSchool, key, cookieStr, username = username)
                    AcademicGatewayFactory.create(currentSchool, key).validateSession()
                } }
                if (generation != academicValidationGeneration || selectedLoginSchool?.id != currentSchool.id) return@launch
                result.onSuccess { identity ->
                    if (identity.status != com.buct.course.academic.AcademicStatus.SUCCESS) {
                        isLoading = false
                        errorMessage = identity.message.ifBlank { "未能验证登录，请在教务网页完成登录后重试" }
                        discardPendingPasswordLogin()
                    } else {
                        val id = identity.studentId.ifBlank { academicGateway?.studentId.orEmpty() }.ifBlank { username }
                        if (id.isBlank()) {
                            isLoading = false
                            errorMessage = "已登录，但未识别到学号，请使用密码登录或重新打开教务网页"
                            discardPendingPasswordLogin()
                        } else {
                            UserManager.getInstance().updateSchoolConfig(currentSchool)
                            finishAcademicLogin(currentSchool, cookieStr, identity.studentName.ifBlank { academicGateway?.studentName.orEmpty() }, id)
                        }
                    }
                }.onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    isLoading = false
                    errorMessage = it.message ?: "教务登录验证失败"
                    discardPendingPasswordLogin()
                }
            }
            return
        }
        // 1. Set Cookie
        CourseApiClient.getInstance().setCookie(currentSchool.baseUrl, cookieStr.trim(), validationKey)

        // 2. Validate Cookie
        validationCall = CourseApiClient.getInstance().validateCookie(currentSchool, validationKey, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    if (generation != academicValidationGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    isLoading = false
                    errorMessage = "网络请求失败: ${e.message}"
                    discardPendingPasswordLogin()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val html = response.body?.string() ?: ""
                
                // 3. Logic to check success
                val isLoginPage = html.contains("用户登录") ||
                        html.contains("登 录") ||
                        html.contains("统一身份认证") ||
                        html.contains("请先登录")

                val name = CourseParser.parseStudentName(html)
                val studentId = CourseParser.parseStudentId(html)
                val hasWelcomeSign = html.contains("欢迎您") ||
                        html.contains("退出") ||
                        html.contains("xsxxwh") ||
                        html.contains("index_initMenu")

                val success = !isLoginPage && (name != null || hasWelcomeSign)

                runOnUiThread {
                    if (generation != academicValidationGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    isLoading = false
                    if (success) {
                        val userManager = UserManager.getInstance()
                        if (selectedLoginSchool?.id != currentSchool.id) {
                            errorMessage = "学校已切换，请重新登录"
                            discardPendingPasswordLogin()
                            return@runOnUiThread
                        }
                        val studentNameParsed = name ?: "同学"
                        val studentIdParsed = studentId ?: ""
                        
                        // 保存信息
                        bindingStudentName = studentNameParsed
                        bindingStudentId = studentIdParsed
                        
                        val maxStudents = com.buct.course.activation.ActivationManager.getMaxStudents(this@LoginActivity)
                        val bindingCheck = com.buct.course.manager.StudentLimitManager.checkCanUseStudent(
                            context = this@LoginActivity,
                            schoolId = currentSchool.id,
                            studentName = studentNameParsed,
                            studentId = studentIdParsed
                        )

                        if (!bindingCheck.allowed) {
                            errorMessage = bindingCheck.reason
                            discardPendingPasswordLogin()
                            return@runOnUiThread
                        }

                        if (bindingCheck.alreadyBound) {
                            proceedToMain(userManager, studentNameParsed, cookieStr)
                            return@runOnUiThread
                        }

                        bindingStudentName = studentNameParsed
                        bindingStudentId = studentIdParsed
                        bindingMaxStudents = maxStudents
                        bindingUsedNames = bindingCheck.usedNames
                        bindingUsedCount = bindingCheck.usedCount
                        pendingCookie = cookieStr
                        showBindingDialog = true

                    } else {
                        discardPendingPasswordLogin()
                        errorMessage = if (isLoginPage) {
                            "Cookie 已过期或无效，请重新获取"
                        } else {
                            "无法解析页面，请检查 Cookie 格式"
                        }
                    }
                }
            }
        })
    }

    private fun finishAcademicLogin(currentSchool: SchoolConfig, cookieStr: String, parsedName: String, parsedId: String) {
        val studentNameParsed = parsedName.ifBlank { "同学" }
        val studentIdParsed = parsedId
        val userManager = UserManager.getInstance()
        bindingStudentName = studentNameParsed
        bindingStudentId = studentIdParsed
        val bindingCheck = com.buct.course.manager.StudentLimitManager.checkCanUseStudent(
            context = this, schoolId = currentSchool.id,
            studentName = studentNameParsed, studentId = studentIdParsed
        )
        runOnUiThread {
            isLoading = false
            if (!bindingCheck.allowed) {
                errorMessage = bindingCheck.reason; discardPendingPasswordLogin(); return@runOnUiThread
            }
            if (bindingCheck.alreadyBound) {
                proceedToMain(userManager, studentNameParsed, cookieStr)
            } else {
                bindingStudentName = studentNameParsed; bindingStudentId = studentIdParsed
                bindingMaxStudents = com.buct.course.activation.ActivationManager.getMaxStudents(this)
                bindingUsedNames = bindingCheck.usedNames; bindingUsedCount = bindingCheck.usedCount
                pendingCookie = cookieStr; showBindingDialog = true
            }
        }
    }
    
    /**
     * 登录成功后进入主界面
     */
    private fun proceedToMain(userManager: UserManager, studentName: String, cookieStr: String) {
        if (pendingPasswordLogin) {
            val passwordSchool = pendingPasswordSchool
            if (passwordSchool == null || selectedLoginSchool?.id != passwordSchool.id) {
                errorMessage = "学校已切换，请重新登录"
                discardPendingPasswordLogin()
                return
            }
        }
        userManager.currentSchool = selectedLoginSchool ?: return
        userManager.isLoggedIn = true
        userManager.studentId = bindingStudentId
        userManager.studentName = studentName
        
        // 保存 Cookie 用于下次自动登录
        if (pendingPasswordLogin) {
            userManager.savePasswordLogin(
                pendingPasswordUsername,
                cookieStr.trim(),
                pendingPasswordValue
            )
        } else {
            userManager.saveCookieLogin(cookieStr.trim())
        }
        discardPendingPasswordLogin()
        Log.d(TAG, "Cookie 已保存，下次可自动登录")
        
        Toast.makeText(
            this@LoginActivity,
            "登录成功！欢迎 $studentName",
            Toast.LENGTH_SHORT
        ).show()
        
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_CALLER, false)) {
            setResult(RESULT_OK)
        } else {
            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
        }
        finish()
    }

    // ============ 密码登录 ============

    private fun handlePasswordLogin(username: String, password: String) {
        val school = selectedLoginSchool
        Log.d(TAG, "handlePasswordLogin: school=${school?.name}, baseUrl=${school?.getBaseUrl()}, fullPath=${school?.getFullBasePath()}")
        if (school == null) {
            errorMessage = "请先选择学校"
            return
        }
        if (username.isBlank() || password.isBlank()) {
            errorMessage = "请输入学号和密码"
            return
        }

        isLoading = true
        errorMessage = null
        captchaImageBytes = null

        pendingPasswordUsername = username
        pendingPasswordValue = password
        pendingPasswordSchool = school
        activePasswordLoginGateway?.clearSensitiveState()
        val gateway = PasswordLoginGatewayFactory.create(school)
        activePasswordLoginGateway = gateway

        gateway.login(school, username, password, object : PasswordLoginCallback {
            override fun onSuccess(cookie: String) {
                runOnUiThread {
                    if (selectedLoginSchool?.id != school.id) {
                        isLoading = false
                        errorMessage = "学校已切换，请重新登录"
                        discardPendingPasswordLogin()
                        return@runOnUiThread
                    }
                    gateway.clearSensitiveState()
                    isLoading = false
                    Log.d(TAG, "密码登录成功")
                    pendingPasswordLogin = true
                    // 复用现有验证流程
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                com.buct.course.activation.ActivationManager.checkActivation(this@LoginActivity)
                            }
                        } catch (_: Exception) {}
                        performLoginValidation(school, cookie)
                    }
                }
            }

            override fun onCaptchaRequired(imageBytes: ByteArray) {
                runOnUiThread {
                    isLoading = false
                    captchaImageBytes = imageBytes
                    Log.d(TAG, "Captcha received: ${imageBytes.size} bytes, dialog should show")
                }
            }

            override fun onSecondaryVerificationRequired(phoneMasked: String) {
                runOnUiThread {
                    isLoading = false
                    secondarySmsRequired = true
                    secondarySmsPhoneMask = phoneMasked
                    secondarySmsSentUntil = 0L
                    secondarySmsCaptchaBytes = null
                    secondarySmsSending = false
                    secondarySmsSubmitting = false
                    errorMessage = null
                    prepareSecondarySmsCaptcha()
                }
            }

            override fun onCaptchaInvalid() {
                runOnUiThread {
                    isLoading = false
                    errorMessage = "验证码错误，请重新输入"
                    refreshCaptcha()
                }
            }

            override fun onInvalidCredentials() {
                runOnUiThread {
                    isLoading = false
                    errorMessage = "用户名或密码不正确"
                    discardPendingPasswordLogin()
                    Log.d(TAG, "onInvalidCredentials called")
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    isLoading = false
                    errorMessage = message
                    discardPendingPasswordLogin()
                    Log.e(TAG, "onError called: $message")
                }
            }

            override fun onWebLoginRequired(message: String) {
                runOnUiThread {
                    isLoading = false
                    errorMessage = message
                    UserManager.getInstance().updateSchoolConfig(school)
                    discardPendingPasswordLogin()
                    openWebView()
                }
            }
        })
    }

    private fun handleCaptchaSubmit(code: String) {
        isLoading = true
        errorMessage = null
        // 不清除 captchaImageBytes，保持弹窗可见直到收到响应

        val gateway = activePasswordLoginGateway
        if (gateway == null) {
            isLoading = false
            errorMessage = "登录会话已失效，请重新登录"
            return
        }
        gateway.submitCaptcha(code, object : PasswordLoginCallback {
            override fun onSuccess(cookie: String) {
                Log.d(TAG, "Captcha submit: login SUCCESS")
                runOnUiThread {
                    gateway.clearSensitiveState()
                    isLoading = false
                    captchaImageBytes = null  // 成功时清除
                    val school = pendingPasswordSchool
                    if (school == null || selectedLoginSchool?.id != school.id) {
                        errorMessage = "学校已切换，请重新登录"
                        discardPendingPasswordLogin()
                        return@runOnUiThread
                    }
                    pendingPasswordLogin = true
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                com.buct.course.activation.ActivationManager.checkActivation(this@LoginActivity)
                            }
                        } catch (_: Exception) {}
                        performLoginValidation(school, cookie)
                    }
                }
            }

            override fun onCaptchaRequired(imageBytes: ByteArray) {
                Log.d(TAG, "Captcha submit: server returned new captcha, size=${imageBytes.size}")
                runOnUiThread {
                    isLoading = false
                    captchaImageBytes = imageBytes  // 刷新图片
                }
            }

            override fun onSecondaryVerificationRequired(phoneMasked: String) {
                runOnUiThread {
                    isLoading = false
                    secondarySmsRequired = true
                    secondarySmsPhoneMask = phoneMasked
                    secondarySmsSentUntil = 0L
                    secondarySmsCaptchaBytes = null
                    secondarySmsSending = false
                    secondarySmsSubmitting = false
                    errorMessage = null
                    prepareSecondarySmsCaptcha()
                }
            }

            override fun onCaptchaInvalid() {
                Log.d(TAG, "Captcha submit: captcha INVALID")
                runOnUiThread {
                    isLoading = false
                    errorMessage = "验证码错误，请重新输入"
                    refreshCaptcha()
                }
            }

            override fun onInvalidCredentials() {
                Log.d(TAG, "Captcha submit: INVALID credentials")
                runOnUiThread {
                    isLoading = false
                    errorMessage = "用户名或密码不正确"
                    captchaImageBytes = null
                    discardPendingPasswordLogin()
                }
            }

            override fun onError(message: String) {
                Log.e(TAG, "Captcha submit error: $message")
                runOnUiThread {
                    isLoading = false
                    captchaImageBytes = null
                    errorMessage = message
                    discardPendingPasswordLogin()
                }
            }
        })
    }

    private fun refreshCaptcha() {
        activePasswordLoginGateway?.refreshCaptcha { bytes ->
            runOnUiThread {
                if (bytes != null) captchaImageBytes = bytes
            }
        }
    }

    private fun prepareSecondarySmsCaptcha() {
        val gateway = activePasswordLoginGateway as? BuctSsoLoginManager ?: return
        secondarySmsSending = true
        errorMessage = null
        gateway.prepareSecondarySmsCaptcha(object : PasswordLoginCallback {
            override fun onSuccess(cookie: String) {}
            override fun onCaptchaRequired(imageBytes: ByteArray) {}
            override fun onCaptchaInvalid() {}
            override fun onInvalidCredentials() {}
            override fun onError(message: String) {
                runOnUiThread {
                    secondarySmsSending = false
                    errorMessage = message
                }
            }
            override fun onSecondaryCaptchaRequired(imageBytes: ByteArray) {
                runOnUiThread {
                    secondarySmsSending = false
                    secondarySmsCaptchaBytes = imageBytes
                }
            }
            override fun onSecondaryCaptchaInvalid() {
                runOnUiThread {
                    secondarySmsSending = false
                    secondarySmsCaptchaBytes = null
                    errorMessage = "图形验证码错误，请重新获取"
                }
            }
            override fun onSecondarySmsSent(expireSeconds: Int) {
                runOnUiThread {
                    secondarySmsSending = false
                    secondarySmsSentUntil = System.currentTimeMillis() + expireSeconds * 1000L
                    secondarySmsCaptchaBytes = null
                    errorMessage = null
                }
            }
            override fun onSecondarySmsInvalid(message: String) {
                runOnUiThread {
                    secondarySmsSending = false
                    errorMessage = message
                }
            }
        })
    }

    private fun sendSecondarySmsCode(captchaCode: String) {
        if (secondarySmsSending) return
        val gateway = activePasswordLoginGateway as? BuctSsoLoginManager
        if (gateway == null) {
            errorMessage = "当前登录方式不支持短信二次验证"
            return
        }
        if (captchaCode.isBlank() || secondarySmsCaptchaBytes == null) {
            prepareSecondarySmsCaptcha()
            return
        }
        secondarySmsSending = true
        errorMessage = null
        gateway.sendSecondarySmsCode(captchaCode.trim(), object : PasswordLoginCallback {
            override fun onSuccess(cookie: String) {}
            override fun onCaptchaRequired(imageBytes: ByteArray) {}
            override fun onCaptchaInvalid() {}
            override fun onInvalidCredentials() {}
            override fun onError(message: String) {
                runOnUiThread {
                    secondarySmsSending = false
                    errorMessage = message
                }
            }
            override fun onSecondaryCaptchaRequired(imageBytes: ByteArray) {
                runOnUiThread {
                    secondarySmsSending = false
                    secondarySmsCaptchaBytes = imageBytes
                }
            }
            override fun onSecondaryCaptchaInvalid() {
                runOnUiThread {
                    secondarySmsSending = false
                    secondarySmsCaptchaBytes = null
                    errorMessage = "图形验证码错误，请重新获取"
                    prepareSecondarySmsCaptcha()
                }
            }
            override fun onSecondarySmsSent(expireSeconds: Int) {
                runOnUiThread {
                    secondarySmsSending = false
                    secondarySmsSentUntil = System.currentTimeMillis() + expireSeconds * 1000L
                    secondarySmsCaptchaBytes = null
                    errorMessage = null
                }
            }
            override fun onSecondarySmsInvalid(message: String) {
                runOnUiThread {
                    secondarySmsSending = false
                    errorMessage = message
                }
            }
        })
    }

    private fun submitSecondarySmsCode(code: String) {
        if (secondarySmsSubmitting) return
        if (code.isBlank()) {
            errorMessage = "请输入短信验证码"
            return
        }
        val gateway = activePasswordLoginGateway
        val school = pendingPasswordSchool
        if (gateway == null || school == null) {
            errorMessage = "登录会话已失效，请重新登录"
            return
        }
        val buctGateway = gateway as? BuctSsoLoginManager
        if (buctGateway == null) {
            errorMessage = "当前登录方式不支持短信二次验证"
            return
        }
        secondarySmsSubmitting = true
        errorMessage = null
        buctGateway.submitSecondarySmsCode(code.trim(), secondarySmsRememberDevice, object : PasswordLoginCallback {
            override fun onSuccess(cookie: String) {
                runOnUiThread {
                    if (selectedLoginSchool?.id != school.id) {
                        secondarySmsSubmitting = false
                        errorMessage = "学校已切换，请重新登录"
                        discardPendingPasswordLogin()
                        return@runOnUiThread
                    }
                    gateway.clearSensitiveState()
                    secondarySmsRequired = false
                    secondarySmsSubmitting = false
                    secondarySmsSentUntil = 0L
                    isLoading = false
                    pendingPasswordLogin = true
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                com.buct.course.activation.ActivationManager.checkActivation(this@LoginActivity)
                            }
                        } catch (_: Exception) {}
                        performLoginValidation(school, cookie)
                    }
                }
            }

            override fun onCaptchaRequired(imageBytes: ByteArray) {
                runOnUiThread {
                    secondarySmsSubmitting = false
                    captchaImageBytes = imageBytes
                }
            }

            override fun onCaptchaInvalid() {
                runOnUiThread {
                    secondarySmsSubmitting = false
                    errorMessage = "验证码错误，请重新输入"
                }
            }

            override fun onInvalidCredentials() {
                runOnUiThread {
                    secondarySmsSubmitting = false
                    errorMessage = "用户名或密码不正确"
                    discardPendingPasswordLogin()
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    secondarySmsSubmitting = false
                    errorMessage = message
                }
            }

            override fun onSecondarySmsInvalid(message: String) {
                runOnUiThread {
                    secondarySmsSubmitting = false
                    errorMessage = message
                }
            }
        })
    }

    private fun discardPendingPasswordLogin() {
        activePasswordLoginGateway?.clearSensitiveState()
        activePasswordLoginGateway = null
        pendingPasswordSchool = null
        pendingPasswordUsername = ""
        pendingPasswordValue = ""
        pendingPasswordLogin = false
        secondarySmsRequired = false
        secondarySmsPhoneMask = ""
        secondarySmsSending = false
        secondarySmsSubmitting = false
        secondarySmsSentUntil = 0L
    }

    private fun clearValidationSessions() {
        validationAccounts.forEach { (key, school) ->
            CourseApiClient.getInstance().clearCookies(key)
            AcademicGatewayFactory.invalidate(school, key)
        }
        validationAccounts.clear()
    }

    override fun onDestroy() {
        academicValidationGeneration++
        validationCall?.cancel()
        validationJob?.cancel()
        clearValidationSessions()
        discardPendingPasswordLogin()
        super.onDestroy()
    }
}




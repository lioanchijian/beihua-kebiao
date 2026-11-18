package com.buct.course.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.buct.course.login.PasswordLoginCallback
import com.buct.course.login.PasswordLoginGateway
import com.buct.course.login.PasswordLoginGatewayFactory
import com.buct.course.login.SecondaryVerificationGateway
import com.buct.course.manager.SessionToken
import com.buct.course.manager.UserManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 密码重新登录被要求短信二次验证时的界面状态。
 *
 * 只在 [SessionRenewer] 的一次 recovery 过程中非空；验证结束（成功/取消/超时）立即回到 idle。
 * 字段与 [com.buct.course.ui.system.SecondaryVerificationDialog] 一一对应。
 */
data class SecondaryVerificationUi(
    val active: Boolean = false,
    val phoneMasked: String = "",
    val captcha: ByteArray? = null,
    /** 短信可重发的时间点（epoch 毫秒），0 表示尚未发送。 */
    val sentUntil: Long = 0L,
    val sending: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    /** 对应学校认证页的「记住该设备」；默认勾选。 */
    val rememberDevice: Boolean = true
) {
    // data class 里带 ByteArray 时 equals 是引用比较，会让 Compose 误判「没变」而不刷新
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecondaryVerificationUi) return false
        return active == other.active &&
            phoneMasked == other.phoneMasked &&
            (captcha?.contentEquals(other.captcha) ?: (other.captcha == null)) &&
            sentUntil == other.sentUntil &&
            sending == other.sending &&
            submitting == other.submitting &&
            error == other.error &&
            rememberDevice == other.rememberDevice
    }

    override fun hashCode(): Int {
        var result = active.hashCode()
        result = 31 * result + phoneMasked.hashCode()
        result = 31 * result + (captcha?.contentHashCode() ?: 0)
        result = 31 * result + sentUntil.hashCode()
        result = 31 * result + sending.hashCode()
        result = 31 * result + submitting.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + rememberDevice.hashCode()
        return result
    }
}

/** All entry points share one recovery operation; Android callbacks are serialized on main. */
object SessionRenewer {
    private val handler = Handler(Looper.getMainLooper())
    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post(action)
    }

    /** 二次验证 5 分钟无操作自动放弃，避免网关实例被无限期挂住。 */
    private const val VERIFICATION_TIMEOUT_MS = 5 * 60 * 1000L

    private val mutableVerification = MutableStateFlow(SecondaryVerificationUi())
    val verification = mutableVerification.asStateFlow()

    /** 正在进行 recovery 的网关；二次验证期间必须保留同一个实例。 */
    private var activeGateway: PasswordLoginGateway? = null
    private var secondaryGateway: SecondaryVerificationGateway? = null
    private var gatewayCallback: PasswordLoginCallback? = null
    private var pendingDone: ((LoginRecoveryOutcome) -> Unit)? = null

    private val timeoutTask = Runnable { cancelVerification() }

    private fun resetVerification() {
        handler.removeCallbacks(timeoutTask)
        activeGateway = null
        secondaryGateway = null
        gatewayCallback = null
        pendingDone = null
        mutableVerification.value = SecondaryVerificationUi()
    }

    /** 结束 recovery：清掉网关敏感态并把结果交回协调器。 */
    private fun finish(outcome: LoginRecoveryOutcome) {
        val done = pendingDone
        val gateway = activeGateway
        resetVerification()
        runCatching { gateway?.clearSensitiveState() }
        done?.let { onMain { it(outcome) } }
    }

    private val coordinator by lazy {
        val user = UserManager.getInstance()
        SessionRecoveryCoordinator(
            sessions = user.sessionState,
            now = SystemClock::elapsedRealtime,
            canRestore = { user.currentSchool != null && user.canAutoRelogin() },
            install = { expected, cookie -> user.saveCookieIfCurrent(expected, cookie) },
            login = { _, done ->
                val school = requireNotNull(user.currentSchool)
                val gateway = PasswordLoginGatewayFactory.create(school)
                activeGateway = gateway
                secondaryGateway = gateway as? SecondaryVerificationGateway
                pendingDone = done
                val callback = object : PasswordLoginCallback {
                    override fun onSuccess(cookie: String) = finish(LoginRecoveryOutcome.Cookie(cookie))

                    override fun onCaptchaRequired(imageBytes: ByteArray) =
                        finish(LoginRecoveryOutcome.Failure(RecoveryFailure.VerificationRequired))

                    override fun onCaptchaInvalid() =
                        finish(LoginRecoveryOutcome.Failure(RecoveryFailure.VerificationRequired))

                    override fun onInvalidCredentials() =
                        finish(LoginRecoveryOutcome.Failure(RecoveryFailure.CredentialsRejected))

                    override fun onSecondaryVerificationRequired(phoneMasked: String) {
                        // 关键：不结束 recovery，保持请求挂着，等用户完成短信验证
                        mutableVerification.value = SecondaryVerificationUi(
                            active = true,
                            phoneMasked = phoneMasked,
                            sending = true
                        )
                        handler.removeCallbacks(timeoutTask)
                        handler.postDelayed(timeoutTask, VERIFICATION_TIMEOUT_MS)
                        secondaryGateway?.prepareSecondarySmsCaptcha(this)
                    }

                    override fun onSecondaryCaptchaRequired(imageBytes: ByteArray) {
                        mutableVerification.value = mutableVerification.value.copy(
                            active = true,
                            captcha = imageBytes,
                            sending = false,
                            error = null
                        )
                    }

                    override fun onSecondaryCaptchaInvalid() {
                        mutableVerification.value = mutableVerification.value.copy(
                            sending = false,
                            captcha = null,
                            error = "图形验证码错误，请重新输入"
                        )
                        secondaryGateway?.prepareSecondarySmsCaptcha(this)
                    }

                    override fun onSecondarySmsSent(expireSeconds: Int) {
                        val expire = (expireSeconds.coerceAtLeast(30)).toLong() * 1000L
                        mutableVerification.value = mutableVerification.value.copy(
                            active = true,
                            sending = false,
                            submitting = false,
                            sentUntil = System.currentTimeMillis() + expire,
                            error = null
                        )
                        // 每发一次短信就续一次超时
                        handler.removeCallbacks(timeoutTask)
                        handler.postDelayed(timeoutTask, VERIFICATION_TIMEOUT_MS)
                    }

                    override fun onSecondarySmsInvalid(message: String) {
                        mutableVerification.value = mutableVerification.value.copy(
                            sending = false,
                            submitting = false,
                            error = message
                        )
                    }

                    override fun onError(message: String) {
                        if (mutableVerification.value.active) {
                            // 验证过程中的报错留在弹窗里，不要顺手把尚未过期的 cookie 作废
                            mutableVerification.value = mutableVerification.value.copy(
                                sending = false,
                                submitting = false,
                                error = message
                            )
                        } else {
                            finish(LoginRecoveryOutcome.Failure(RecoveryFailure.Network))
                        }
                    }
                }
                gatewayCallback = callback
                gateway.login(school, user.username, user.accountPassword, callback)
                val cancel: () -> Unit = {
                    val active = activeGateway
                    resetVerification()
                    runCatching { active?.clearSensitiveState() }
                }
                cancel
            }
        )
    }

    val state get() = coordinator.state

    @JvmStatic fun canRenew(): Boolean =
        UserManager.getInstance().currentSchool?.academicSystem != "southsoft" &&
            coordinator.canAttempt(UserManager.getInstance().sessionState.token)

    fun sessionChanged() = onMain { coordinator.sessionChanged() }

    fun request(
        expected: SessionToken,
        manual: Boolean = false,
        onDone: (SessionRecoveryResult) -> Unit = {}
    ) = onMain {
        if (UserManager.getInstance().currentSchool?.academicSystem == "southsoft") {
            onDone(SessionRecoveryResult.NeedsLogin(RecoveryFailure.VerificationRequired))
            return@onMain
        }
        coordinator.request(expected, manual, onDone)
    }

    /** 验证过程中刷新图形验证码（也是首次进入二次验证时的首张图）。 */
    fun refreshSecondaryCaptcha() {
        val gateway = secondaryGateway ?: return
        val callback = gatewayCallback ?: return
        onMain {
            mutableVerification.value = mutableVerification.value.copy(sending = true, error = null)
            gateway.prepareSecondarySmsCaptcha(callback)
        }
    }

    /** 提交图形验证码并发短信；图形码为空时等同于刷新验证码。 */
    fun sendSecondarySms(captchaCode: String) {
        val gateway = secondaryGateway ?: return
        val callback = gatewayCallback ?: return
        val state = mutableVerification.value
        if (state.sending) return
        if (captchaCode.isBlank() || state.captcha == null) {
            refreshSecondaryCaptcha()
            return
        }
        onMain {
            mutableVerification.value = mutableVerification.value.copy(sending = true, error = null)
            gateway.sendSecondarySmsCode(captchaCode.trim(), callback)
        }
    }

    /** 提交短信验证码完成登录。 */
    fun submitSecondarySms(smsCode: String) {
        val gateway = secondaryGateway ?: return
        val callback = gatewayCallback ?: return
        if (smsCode.isBlank() || mutableVerification.value.submitting) return
        onMain {
            mutableVerification.value = mutableVerification.value.copy(submitting = true, error = null)
            gateway.submitSecondarySmsCode(
                smsCode.trim(),
                mutableVerification.value.rememberDevice,
                callback
            )
        }
    }

    /** 勾选/取消「记住该设备」（对应学校认证页的同名选项）。 */
    fun setRememberDevice(remember: Boolean) {
        if (!mutableVerification.value.active) return
        mutableVerification.value = mutableVerification.value.copy(rememberDevice = remember)
    }

    /**
     * 放弃二次验证（用户取消 / 超时）。
     * 以 VerificationRequired 结束本次 recovery，现有 cookie 保持不变。
     */
    fun cancelVerification() {
        onMain {
            if (mutableVerification.value.active) finish(LoginRecoveryOutcome.Failure(RecoveryFailure.VerificationRequired))
        }
    }

    /** Compatibility for the existing background service; UI uses the typed result above. */
    @JvmStatic fun renew(context: Context, onDone: (Boolean) -> Unit) {
        request(UserManager.getInstance().sessionState.token) { onDone(it is SessionRecoveryResult.Recovered) }
    }
}

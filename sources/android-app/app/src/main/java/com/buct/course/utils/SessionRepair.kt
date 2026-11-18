package com.buct.course.utils

import android.content.Context
import com.buct.course.manager.SyncSettings
import com.buct.course.manager.UserManager

/**
 * 「更新登录状态」的统一入口。
 *
 * 设置页、空教室 901 提示、后续成绩页等场景共用一套逻辑，避免各处重复判断
 * 在线同步开关、文案映射与 token 竞态（切换账号期间旧请求的结果必须丢弃）。
 */
object SessionRepair {

    data class Outcome(val ok: Boolean, val message: String? = null)

    /**
     * 让当前账号用已保存的密码重新走一次登录，拿到新 cookie。
     *
     * 成功后新 cookie 已写入 [UserManager.sessionState]，调用方直接重查数据即可。
     * 回调在主线程。
     */
    fun repair(context: Context, onResult: (Outcome) -> Unit) {
        val user = UserManager.getInstance()
        if (user.currentSchool?.academicSystem == "southsoft") {
            onResult(Outcome(false, "研究生系统需要重新输入密码和图形验证码，请到登录页重新登录"))
            return
        }
        if (!SyncSettings.enabled(context.applicationContext)) {
            onResult(Outcome(false, "在线同步未开启，更新登录状态需要联网，请先打开「在线同步」"))
            return
        }
        if (!user.canAutoRelogin()) {
            onResult(Outcome(false, "没有保存密码，请到登录页重新登录"))
            return
        }
        val expected = user.sessionState.token
        SessionRenewer.request(expected, manual = true) { result ->
            when (result) {
                is SessionRecoveryResult.Recovered -> {
                    onResult(if (user.sessionState.isCurrent(result.token)) Outcome(true) else Outcome(false, "账号已切换，请重试"))
                }
                is SessionRecoveryResult.NeedsLogin -> {
                    if (!user.sessionState.isCurrent(expected)) {
                        onResult(Outcome(false, "账号已切换，请重试"))
                    } else {
                        onResult(Outcome(false, reasonText(result.reason)))
                    }
                }
                SessionRecoveryResult.Superseded -> onResult(Outcome(false, "账号已切换，请重试"))
            }
        }
    }

    private fun reasonText(reason: RecoveryFailure): String = when (reason) {
        RecoveryFailure.Network -> "暂时无法连接，请稍后重试"
        RecoveryFailure.Storage -> "保存失败，请重试"
        RecoveryFailure.Cooldown -> "刚刚更新过，请稍后再试"
        RecoveryFailure.NoPassword -> "没有保存密码，请到登录页重新登录"
        RecoveryFailure.CredentialsRejected -> "密码已失效，请到登录页重新登录"
        RecoveryFailure.VerificationRequired -> "教务要求二次验证，请到设置页或登录页完成验证"
    }
}

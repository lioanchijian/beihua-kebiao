package com.buct.course.login

/**
 * 支持「密码登录后被要求短信二次验证」的网关。
 *
 * 目前只有北化金智统一认证（[BuctSsoLoginManager]）实现了这条链路：
 * 密码校验通过后，认证平台可能要求先填图形验证码再发短信，最后用短信码完成登录。
 * 期间必须保留同一个网关实例（[PasswordLoginGateway.clearSensitiveState] 会丢掉进行中的会话）。
 */
interface SecondaryVerificationGateway {
    /** 获取发送短信前的图形验证码；结果通过 callback.onSecondaryCaptchaRequired 回调。 */
    fun prepareSecondarySmsCaptcha(callback: PasswordLoginCallback)

    /** 提交图形验证码并触发发送短信；成功回调 onSecondarySmsSent(有效秒数)。 */
    fun sendSecondarySmsCode(captchaCode: String, callback: PasswordLoginCallback)

    /**
     * 提交短信验证码；成功时回调 onSuccess(cookie)。
     * [rememberDevice] 对应学校认证页的「记住该设备」，勾选后此设备可免下次短信验证。
     */
    fun submitSecondarySmsCode(smsCode: String, rememberDevice: Boolean, callback: PasswordLoginCallback)
}

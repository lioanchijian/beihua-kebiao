package com.buct.course.login

import com.buct.course.model.SchoolConfig

interface PasswordLoginGateway {
    fun login(
        school: SchoolConfig,
        username: String,
        password: String,
        callback: PasswordLoginCallback
    )

    fun submitCaptcha(captchaCode: String, callback: PasswordLoginCallback)

    fun refreshCaptcha(callback: (ByteArray?) -> Unit)

    fun clearSensitiveState()
}

object PasswordLoginGatewayFactory {
    fun create(school: SchoolConfig): PasswordLoginGateway =
        if (school.id == TYUST_SCHOOL_ID) TyustSsoLoginManager()
        else if (school.id == ZJUT_SCHOOL_ID) ZjutSsoLoginManager()
        else if (school.id == BUCT_SCHOOL_ID) BuctSsoLoginManager()
        else if (com.buct.course.academic.AcademicGatewayFactory.supports(school)) com.buct.course.academic.AcademicPasswordLoginGateway(school)
        else PasswordLoginManager()

    private const val TYUST_SCHOOL_ID = "tyust"
    private const val ZJUT_SCHOOL_ID = "zjut"
    private const val BUCT_SCHOOL_ID = "buct"
}

package com.buct.course.network

import java.io.IOException

/**
 * 教务侧判定为「未登录 / 会话失效」时抛出。
 *
 * 触发来源：
 * - HTTP 401 / 403
 * - 正方接口 200 但返回体带 `code=901`（未登录）之类的业务码
 * - 200 但返回的是一张登录页 HTML（被网关拦截重定向）
 *
 * 与 [IOException] 的区别在于 UI 可据此给出「更新登录状态」按钮，而不是提示「查询失败」。
 */
class SessionExpiredException(
    val code: Int,
    message: String? = null
) : IOException(message ?: "登录状态已失效（HTTP $code）") {

    /** 业务码（901 等）或 HTTP 状态码，仅用于文案与日志。 */
    val statusCode: Int get() = code
}

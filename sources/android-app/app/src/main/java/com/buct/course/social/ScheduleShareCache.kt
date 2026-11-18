package com.buct.course.social

import com.buct.course.manager.UserManager

/**
 * 当前账号课表的原始 JSON 暂存。
 *
 * 课表页每次解析成功都会写进来，好友聊天里的「发送课表」直接取用，
 * 不用为了发一次课表再联网/重新解析。姓名学号在发送时才从 UserManager 取，
 * 避免切换账号后拿到旧值。
 */
object ScheduleShareCache {
    @Volatile
    var rawJson: String = ""

    @Volatile
    var termLabel: String = ""

    data class Snapshot(
        val json: String,
        val ownerName: String,
        val ownerStudentId: String,
        val school: String,
        val term: String
    )

    fun snapshot(context: android.content.Context? = null): Snapshot? {
        val json = rawJson.takeIf { it.isNotBlank() } ?: return null
        val user = runCatching { UserManager.getInstance() }.getOrNull() ?: return null
        if (context != null) runCatching { user.init(context) }
        // 学号不传：账号记录里的 studentId 经常是空的（教务返回里没有），
        // 传空字符串反而在接收端显示成"未记录学号"，不如只带姓名 + 学期。
        return Snapshot(
            json = json,
            ownerName = user.studentName.orEmpty(),
            ownerStudentId = "",
            school = runCatching { user.currentSchool?.name }.getOrNull().orEmpty(),
            term = termLabel
        )
    }
}

package com.buct.course.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 用户自己添加的考试（四六级、教资、竞赛等教务系统里没有的）。
 *
 * 与教务同步来的考试分开存放：教务数据每次同步会被整体替换，自建数据必须长期保留。
 * 只存本地，按账号隔离，不上传服务器。
 */
data class CustomExam(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    /** 关联课程名，可空。用于和教务考试做重复提示。 */
    val course: String = "",
    val startAt: Long,
    /** 结束时间，0 表示只记开始。 */
    val endAt: Long = 0L,
    val location: String = "",
    val seat: String = "",
    val note: String = "",
    /** 来源标记：模板名或「自建」。 */
    val tag: String = "自建",
    /** 提前提醒的分钟数，可多个。 */
    val remindOffsets: List<Int> = DEFAULT_REMIND_OFFSETS,
    /** 已考完可以隐藏，避免列表越来越长。 */
    val hidden: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isPast: Boolean
        get() = (if (endAt > 0) endAt else startAt) < System.currentTimeMillis()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("course", course)
        put("startAt", startAt)
        put("endAt", endAt)
        put("location", location)
        put("seat", seat)
        put("note", note)
        put("tag", tag)
        put("remindOffsets", JSONArray(remindOffsets))
        put("hidden", hidden)
        put("createdAt", createdAt)
    }

    companion object {
        /** 默认：提前 1 天 + 2 小时。 */
        val DEFAULT_REMIND_OFFSETS = listOf(1440, 120)

        /** 可选提醒档位：7/5/3/2/1 天，考前 2/1/0.5 小时。 */
        val REMIND_PRESETS = listOf(
            7 * 1440 to "7 天",
            5 * 1440 to "5 天",
            3 * 1440 to "3 天",
            2 * 1440 to "2 天",
            1 * 1440 to "1 天",
            120 to "2 小时",
            60 to "1 小时",
            30 to "30 分钟"
        )

        fun fromJson(o: JSONObject): CustomExam {
            val arr = o.optJSONArray("remindOffsets")
            val offsets = if (arr == null) {
                DEFAULT_REMIND_OFFSETS
            } else {
                (0 until arr.length()).map { arr.optInt(it) }.filter { it > 0 }
            }
            return CustomExam(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = o.optString("title"),
                course = o.optString("course"),
                startAt = o.optLong("startAt"),
                endAt = o.optLong("endAt"),
                location = o.optString("location"),
                seat = o.optString("seat"),
                note = o.optString("note"),
                tag = o.optString("tag", "自建").ifBlank { "自建" },
                remindOffsets = offsets,
                hidden = o.optBoolean("hidden", false),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

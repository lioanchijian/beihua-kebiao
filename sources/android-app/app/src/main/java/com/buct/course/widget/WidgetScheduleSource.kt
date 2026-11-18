package com.buct.course.widget

import android.content.Context
import com.buct.course.social.FriendScheduleStore

/**
 * 小组件实例的数据来源解析（Week 课表 / 今日课表共用）。
 *
 * 实例配置里的 source 字段取值：
 * - 空 / null      → 本机账号课表（再用 accountKey 决定是哪个账号）
 * - "friend:<id>"  → 好友传过来、已保存在固定目录里的课表
 *
 * 朋友课表被删掉（或文件丢失）时这里返回 null，调用方自动回落到本机账号，
 * 不会出现「小组件空白但标题还写着别人名字」的情况。
 */
object WidgetScheduleSource {
    private const val PREFIX = "friend:"

    fun encodeFriend(id: String): String = PREFIX + id

    /** 当前实例配置的朋友课表条目；没配置/已删除则为 null。 */
    fun friendEntry(context: Context, appWidgetId: Int): FriendScheduleStore.Entry? {
        val source = WidgetConfigStore.source(context, appWidgetId) ?: return null
        if (!source.startsWith(PREFIX)) return null
        val id = source.removePrefix(PREFIX).takeIf { it.isNotBlank() } ?: return null
        return runCatching { FriendScheduleStore.find(context, id) }.getOrNull()
    }

    /** 朋友课表的原始 JSON；无来源或内容为空返回 null。 */
    fun loadFriendJson(context: Context, appWidgetId: Int): String? {
        val entry = friendEntry(context, appWidgetId) ?: return null
        return FriendScheduleStore.readJson(entry)?.takeIf { it.isNotBlank() }
    }

    /** 小组件标题兜底：显示朋友课表时用「XX的课表」，否则交回原有默认值。 */
    fun friendTitle(context: Context, appWidgetId: Int): String? =
        friendEntry(context, appWidgetId)?.label
            ?.takeIf { it.isNotBlank() }
            ?.let { "${it}的课表" }
}

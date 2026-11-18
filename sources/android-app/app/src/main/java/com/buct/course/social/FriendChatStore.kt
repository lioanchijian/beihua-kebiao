package com.buct.course.social

import android.content.Context
import com.buct.course.network.FriendMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 好友会话的本地缓存。
 *
 * 为什么要有它：
 * - 进会话先把本地记录画出来，立刻能看，不用等网络；
 * - 拉新消息只带 `since_id`（本地最后一条的 id）增量拉，不再每次都要服务器把整段会话吐一遍，
 *   服务端压力随「打开会话次数」而不是「消息条数」增长。
 *
 * 存储：`filesDir/friend_chats/<displayId>.json`，只保留最近 [MAX_KEEP] 条。
 * 缓存是展示加速用的，删掉只会重新从服务器拉，不影响任何业务数据。
 */
object FriendChatStore {
    private const val DIR = "friend_chats"
    private const val MAX_KEEP = 300

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, DIR).apply { if (!exists()) mkdirs() }

    private fun fileOf(context: Context, displayId: String): File =
        File(dir(context), "${sanitize(displayId)}.json")

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "friend" }

    /** 本地缓存的消息（按 id 升序）；没有缓存返回空表。 */
    fun load(context: Context, displayId: String): List<FriendMessage> {
        val file = fileOf(context, displayId)
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    add(
                        FriendMessage(
                            id = o.optLong("id"),
                            fromMe = o.optBoolean("fromMe"),
                            kind = o.optString("kind", "text"),
                            body = o.optString("body"),
                            payload = o.optString("payload"),
                            createdAt = o.optString("createdAt")
                        )
                    )
                }
            }.sortedBy { it.id }
        }.getOrDefault(emptyList())
    }

    /** 本地已知的最大消息 id（增量拉取的起点）。 */
    fun lastId(context: Context, displayId: String): Long =
        load(context, displayId).maxOfOrNull { it.id } ?: 0L

    /** 合并新消息并落盘（按 id 去重，只留最近 MAX_KEEP 条）。 */
    fun merge(context: Context, displayId: String, incoming: List<FriendMessage>): List<FriendMessage> {
        val existing = load(context, displayId)
        val merged = (existing + incoming)
            .associateBy { it.id }
            .values
            .sortedBy { it.id }
            .takeLast(MAX_KEEP)
        write(context, displayId, merged)
        return merged
    }

    fun clear(context: Context, displayId: String) {
        runCatching { fileOf(context, displayId).delete() }
    }

    private fun write(context: Context, displayId: String, messages: List<FriendMessage>) {
        runCatching {
            val array = JSONArray()
            messages.forEach { m ->
                array.put(
                    JSONObject()
                        .put("id", m.id)
                        .put("fromMe", m.fromMe)
                        .put("kind", m.kind)
                        .put("body", m.body)
                        .put("payload", m.payload)
                        .put("createdAt", m.createdAt)
                )
            }
            fileOf(context, displayId).writeText(array.toString())
        }
    }
}

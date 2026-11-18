package com.buct.course.social

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 朋友传过来的课表，统一落在 App 私有目录 `filesDir/friend_schedules/`。
 *
 * 为什么不用 SAF 让用户手动选文件：
 * - 用户要求「收到就存好，课表页切换账号里直接能选」，不想再走系统文件选择器；
 * - 目录固定，App 启动时自己扫一遍就知道有哪些课表，卸载即清理，不污染相册/下载目录。
 *
 * 元数据（姓名、学号、学校、学期、时间）存 prefs，课表正文按 `<id>.json` 落在目录里。
 * 目录里如果有别处拷进来的 `.json`（没有元数据），也会被扫描出来当课表用，标题取文件名。
 */
object FriendScheduleStore {
    private const val PREFS = "friend_schedules"
    private const val KEY_META = "meta_v1"
    private const val DIR = "friend_schedules"

    data class Entry(
        val id: String,
        val displayId: String,
        val ownerName: String,
        val ownerStudentId: String,
        val school: String,
        val term: String,
        val savedAt: Long,
        val file: File
    ) {
        /** 切换账号列表里显示的名字：姓名优先，没有就用学号/昵称。 */
        val label: String
            get() = ownerName.ifBlank { ownerStudentId }.ifBlank { id }

        val subtitle: String
            get() = listOf(ownerStudentId, school, term)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, DIR).apply { if (!exists()) mkdirs() }

    private fun fileOf(context: Context, id: String): File = File(dir(context), "$id.json")

    private fun readMeta(context: Context): MutableList<JSONObject> {
        val raw = prefs(context).getString(KEY_META, null) ?: return mutableListOf()
        return runCatching {
            val array = JSONArray(raw)
            MutableList(array.length()) { array.getJSONObject(it) }
        }.getOrDefault(mutableListOf())
    }

    private fun writeMeta(context: Context, list: List<JSONObject>) {
        prefs(context).edit().putString(KEY_META, JSONArray(list).toString()).apply()
    }

    /** 保存（同一 displayId 覆盖旧的，保留最新一份）。 */
    fun save(
        context: Context,
        displayId: String,
        ownerName: String,
        ownerStudentId: String,
        school: String,
        term: String,
        json: String
    ): Entry {
        val id = sanitize(displayId.ifBlank { "friend" })
        val file = fileOf(context, id)
        runCatching { file.writeText(json) }
        val meta = JSONObject()
            .put("id", id)
            .put("displayId", displayId)
            .put("ownerName", ownerName)
            .put("ownerStudentId", ownerStudentId)
            .put("school", school)
            .put("term", term)
            .put("savedAt", System.currentTimeMillis())
        val list = readMeta(context).filter { it.optString("id") != id }
        writeMeta(context, list + meta)
        return Entry(id, displayId, ownerName, ownerStudentId, school, term, System.currentTimeMillis(), file)
    }

    /** 全部可用课表：元数据条目 + 目录里没有元数据的 json 文件。 */
    fun list(context: Context): List<Entry> {
        val entries = LinkedHashMap<String, Entry>()
        readMeta(context).forEach { meta ->
            val id = meta.optString("id")
            val file = fileOf(context, id)
            if (id.isBlank() || !file.isFile) return@forEach
            entries[id] = Entry(
                id = id,
                displayId = meta.optString("displayId"),
                ownerName = meta.optString("ownerName"),
                ownerStudentId = meta.optString("ownerStudentId"),
                school = meta.optString("school"),
                term = meta.optString("term"),
                savedAt = meta.optLong("savedAt"),
                file = file
            )
        }
        // 目录扫描：用户把课表 json 直接丢进来也能用，不需要在 App 里再选文件
        runCatching {
            dir(context).listFiles { f -> f.isFile && f.extension.equals("json", true) }
                ?.forEach { file ->
                    val id = file.nameWithoutExtension
                    if (id !in entries) {
                        entries[id] = Entry(
                            id = id,
                            displayId = "",
                            ownerName = id,
                            ownerStudentId = "",
                            school = "",
                            term = "",
                            savedAt = file.lastModified(),
                            file = file
                        )
                    }
                }
        }
        return entries.values.sortedByDescending { it.savedAt }
    }

    fun readJson(entry: Entry): String? =
        runCatching { if (entry.file.isFile) entry.file.readText() else null }.getOrNull()

    fun find(context: Context, id: String): Entry? = list(context).firstOrNull { it.id == id }

    fun delete(context: Context, id: String) {
        writeMeta(context, readMeta(context).filter { it.optString("id") != id })
        runCatching { fileOf(context, id).delete() }
    }

    fun count(context: Context): Int = list(context).size

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)
}

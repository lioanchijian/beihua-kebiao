package com.buct.course.manager

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.buct.course.model.CustomExam
import org.json.JSONArray
import org.json.JSONObject

/**
 * 自建考试的本地存储。
 *
 * 与教务同步的考试完全分开：教务数据每次同步整体替换，自建数据必须长期保留。
 * 按账号隔离，换账号看不到对方添加的考试；只存本机，不上传服务器。
 */
object CustomExamStore {
    private const val PREFS_NAME = "custom_exams"
    private const val KEY_PREFIX = "exams"

    /** 变更计数：Compose 读它来订阅列表变化。 */
    var revision by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(): String {
        val account = UserManager.getInstance().currentAccountStorageKey.ifBlank { "default" }
        return "${KEY_PREFIX}_$account"
    }

    fun list(context: Context): List<CustomExam> {
        val raw = prefs(context).getString(key(), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { CustomExam.fromJson(it) }
            }
        }.getOrDefault(emptyList()).sortedBy { it.startAt }
    }

    fun get(context: Context, id: String): CustomExam? = list(context).firstOrNull { it.id == id }

    fun save(context: Context, exam: CustomExam) {
        val items = list(context).toMutableList()
        val index = items.indexOfFirst { it.id == exam.id }
        if (index >= 0) items[index] = exam else items.add(exam)
        write(context, items)
    }

    fun delete(context: Context, id: String) {
        val items = list(context).filterNot { it.id == id }
        write(context, items)
    }

    fun setHidden(context: Context, id: String, hidden: Boolean) {
        val items = list(context).map { if (it.id == id) it.copy(hidden = hidden) else it }
        write(context, items)
    }

    private fun write(context: Context, items: List<CustomExam>) {
        val array = JSONArray()
        items.sortedBy { it.startAt }.forEach { array.put(it.toJson()) }
        prefs(context).edit().putString(key(), array.toString()).apply()
        revision++
    }
}

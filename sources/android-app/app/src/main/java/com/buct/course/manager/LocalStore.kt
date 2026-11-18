package com.buct.course.manager

import android.content.Context
import java.io.File

/**
 * 本地数据文件存储：课表 / 考试 / 成绩等经常使用的数据落盘到 App 私有目录，
 * 与在线同步开关配合使用，避免每次联网拉取导致教务会话被顶号。
 * 文件存放在 filesDir/local_data 下，卸载才清除，常规清理不会误删。
 */
object LocalStore {

    private const val DIR_NAME = "local_data"

    private fun dir(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** 原子写入（临时文件 + 重命名） */
    fun save(context: Context, fileName: String, content: String): Boolean {
        return try {
            val d = dir(context)
            val tmp = File(d, "$fileName.tmp")
            val target = File(d, fileName)
            tmp.writeText(content, Charsets.UTF_8)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun load(context: Context, fileName: String): String? {
        return try {
            val f = File(dir(context), fileName)
            if (f.exists() && f.length() > 0) f.readText(Charsets.UTF_8) else null
        } catch (e: Exception) {
            null
        }
    }

    fun delete(context: Context, fileName: String) {
        runCatching { File(dir(context), fileName).delete() }
    }

    fun list(context: Context): List<String> {
        return runCatching { dir(context).listFiles()?.map { it.name } ?: emptyList() }.getOrDefault(emptyList())
    }
}

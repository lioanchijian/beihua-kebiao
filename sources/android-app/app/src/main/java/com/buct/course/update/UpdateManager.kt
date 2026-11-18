package com.buct.course.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * 应用更新管理器
 * 负责检查更新、下载APK、安装APK
 */
class UpdateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateManager"
        
        // 更新清单：域名优先，IP 作为备用（新旧服务器都保留）
        // 只保留 HTTPS 源：明文 HTTP 会被中间人替换成恶意 version.json，
        // 一旦配合自动下载安装就是远程代码执行。IP 直连源已移除。
        private val VERSION_URLS = listOf(
            "https://buctkb.cn/version.json",
            "https://www.buctkb.cn/version.json"
        )
        
        // 下载文件名
        private const val APK_FILE_NAME = "zhengfang_update.apk"
        
        @Volatile
        private var instance: UpdateManager? = null
        
        fun getInstance(context: Context): UpdateManager {
            return instance ?: synchronized(this) {
                instance ?: UpdateManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    
    /**
     * 更新信息数据类
     */
    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val forceUpdate: Boolean,
        val sha256: String = ""
    )
    
    /**
     * 获取本地版本信息
     */
    private fun getPackageInfo(): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取版本信息失败: ${e.message}")
            null
        }
    }
    
    private fun getLocalVersionCode(): Long {
        val packageInfo = getPackageInfo() ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }
    
    private fun getLocalVersionName(): String {
        return getPackageInfo()?.versionName ?: "1.0.0"
    }
    
    /**
     * 检查更新
     */
    fun checkForUpdate(callback: (UpdateInfo?) -> Unit, onFinished: (Boolean) -> Unit = {}) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        fun tryUrl(index: Int) {
            if (index >= VERSION_URLS.size) {
                mainHandler.post { callback(null); onFinished(false) }
                return
            }
            val url = VERSION_URLS[index]
            Log.d(TAG, "检查更新($index): $url")
            val request = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "更新清单请求失败($index): ${e.message}")
                    tryUrl(index + 1)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { result ->
                            if (!result.isSuccessful) {
                                Log.e(TAG, "更新清单 HTTP ${result.code}: $url")
                                tryUrl(index + 1)
                                return
                            }
                            val json = result.body?.string() ?: ""
                            Log.d(TAG, "版本信息($index): $json")
                            val obj = JSONObject(json)
                            val serverVersionCode = obj.optInt("versionCode", 0)
                            if (serverVersionCode <= 0) {
                                Log.e(TAG, "更新清单缺少 versionCode: $url")
                                tryUrl(index + 1)
                                return
                            }
                            val localVersionCode = getLocalVersionCode()
                            Log.d(TAG, "本地版本: $localVersionCode, 服务器版本: $serverVersionCode")
                            if (serverVersionCode > localVersionCode) {
                                // 只用 HTTPS 下载地址；downloadUrlIp 是明文 HTTP，已弃用
                                val downloadUrl = obj.optString("downloadUrl", "")
                                if (downloadUrl.isBlank()) {
                                    Log.e(TAG, "更新清单缺少 downloadUrl: $url")
                                    tryUrl(index + 1)
                                    return
                                }
                                val updateInfo = UpdateInfo(
                                    versionCode = serverVersionCode,
                                    versionName = obj.optString("versionName", ""),
                                    releaseNotes = obj.optString("releaseNotes", ""),
                                    downloadUrl = downloadUrl,
                                    forceUpdate = obj.optBoolean("forceUpdate", false),
                                    sha256 = obj.optString("sha256", "")
                                )
                                mainHandler.post { callback(updateInfo); onFinished(true) }
                            } else {
                                mainHandler.post { callback(null); onFinished(true) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析更新清单失败($index): ${e.message}")
                        tryUrl(index + 1)
                    }
                }
            })
        }
        tryUrl(0)
    }
    
    /**
     * 下载APK
     */
    fun downloadApk(
        downloadUrl: String,
        expectedSha256: String = "",
        onProgress: (Int) -> Unit,
        onComplete: (File?) -> Unit
    ) {
        Log.d(TAG, "开始下载: $downloadUrl")
        
        // 删除旧的APK文件
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        if (apkFile.exists()) {
            apkFile.delete()
        }
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("正在下载更新")
            setDescription("正在下载新版本...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        
        downloadId = downloadManager.enqueue(request)
        
        // 监听下载进度
        Thread {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    if (statusIndex >= 0 && bytesIndex >= 0 && totalIndex >= 0) {
                        val status = cursor.getInt(statusIndex)
                        val bytesDownloaded = cursor.getLong(bytesIndex)
                        val bytesTotal = cursor.getLong(totalIndex)
                        
                        if (bytesTotal > 0) {
                            val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                            onProgress(progress)
                        }
                        
                        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                downloading = false
                                // 校验通过才交给安装流程：哈希对不上或签名不是本应用，直接丢弃
                                val ok = verifyDownloadedApk(apkFile, expectedSha256)
                                mainHandler.post { onComplete(if (ok) apkFile else null) }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                downloading = false
                                mainHandler.post { onComplete(null) }
                            }
                        }
                    }
                }
                cursor.close()
                
                if (downloading) {
                    Thread.sleep(500)
                }
            }
        }.start()
    }
    
    /**
     * 校验下载到的安装包。
     *
     * 双重校验，缺一不可：
     * 1. 与 version.json 里的 sha256 比对——防止下载被替换/截断（老版本完全没校验）；
     * 2. 签名证书必须和当前应用一致——防止有人塞一个同包名的其他签名包。
     */
    private fun verifyDownloadedApk(file: File, expectedSha256: String): Boolean {
        if (!file.exists() || file.length() <= 0L) {
            Log.e(TAG, "安装包不存在或为空")
            return false
        }
        if (expectedSha256.isNotBlank()) {
            val actual = runCatching { sha256Of(file) }.getOrNull()
            if (actual == null || !actual.equals(expectedSha256, ignoreCase = true)) {
                Log.e(TAG, "安装包哈希不匹配: expected=$expectedSha256 actual=$actual")
                runCatching { file.delete() }
                return false
            }
            Log.d(TAG, "安装包哈希校验通过")
        } else {
            Log.w(TAG, "更新清单没有 sha256，跳过哈希校验")
        }
        if (!signatureMatchesHostApp(file)) {
            Log.e(TAG, "安装包签名与当前应用不一致，已丢弃")
            runCatching { file.delete() }
            return false
        }
        return true
    }

    private fun sha256Of(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun signatureMatchesHostApp(file: File): Boolean = runCatching {
        val pm = context.packageManager
        val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(
                file.absolutePath,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, android.content.pm.PackageManager.GET_SIGNATURES)
        } ?: return false

        val self = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
        }

        fun certOf(info: android.content.pm.PackageInfo): String? {
            val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            } ?: return null
            return java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
        }

        val a = certOf(archive)
        val b = certOf(self)
        a != null && b != null && a == b
    }.getOrDefault(false)

    /** 给用户一个可见提示（下载/校验失败的场景不能静默）。 */
    fun notifyUser(message: String) {
        runCatching {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 安装APK
     */
    fun installApk(apkFile: File) {
        Log.d(TAG, "安装APK: ${apkFile.absolutePath}")
        
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                } else {
                    Uri.fromFile(apkFile)
                }
                
                setDataAndType(uri, "application/vnd.android.package-archive")
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "安装APK失败: ${e.message}")
        }
    }
    
    /**
     * 取消下载
     */
    fun cancelDownload() {
        if (downloadId != -1L) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
            downloadId = -1
        }
    }
    
    /**
     * 获取当前版本名
     */
    fun getCurrentVersionName(): String {
        return getLocalVersionName()
    }
    
    /**
     * 获取当前版本号
     */
    fun getCurrentVersionCode(): Int {
        return getLocalVersionCode().toInt()
    }
}


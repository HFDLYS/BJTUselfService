package team.bjtuss.bjtuselfservice.utils

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import team.bjtuss.bjtuselfservice.MainApplication
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object DownloadUtil {
    private val downloadManager =
        MainApplication.appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun downloadFile(
        url: String,
        title: String? = "下载文件",
        Cookie: String? = null,
        fileType: String = "pdf"
    ) {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(title)
            setDescription("正在下载文件")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "$title.$fileType"
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            Cookie?.let {
                addRequestHeader("Cookie", it)
            }
            addRequestHeader(
                "User-Agent",
                StudentAccountManager.getInstance().userAgent
            )
        }
        downloadManager.enqueue(request)
    }


    suspend fun downloadFileWithOkHttp(
        url: String,
        filename: String,
        relativePath: String = "" // 相对路径，如 "hello/hello1/hello2"
    ): File = withContext(Dispatchers.IO) {
        val client = SmartCurriculumPlatformRepository.client
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", StudentAccountManager.getInstance().userAgent)
            .header("Cookie", KotlinUtils.getCookieOfClient() ?: "")
            .header("Referer", "http://123.121.147.7:88/ve/")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("下载失败: ${response.code}")

        // 获取Downloads目录
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        // 创建多层目录结构
        val targetDir = if (relativePath.isNotEmpty()) {
            File(downloadDir, relativePath).apply {
                if (!exists() && !mkdirs()) {
                    throw IOException("无法创建目标文件夹: $absolutePath")
                }
            }
        } else {
            downloadDir
        }

        // 创建目标文件
        val file = File(targetDir, filename)

        response.use {
            it.body?.byteStream()?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }

        // 通知系统媒体扫描器更新文件
        MediaScannerConnection.scanFile(
            MainApplication.appContext,
            arrayOf(file.absolutePath),
            null,
            null
        )

        file
    }
}
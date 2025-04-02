package team.bjtuss.bjtuselfservice.utils

import android.app.DownloadManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import team.bjtuss.bjtuselfservice.MainApplication
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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

    fun downloadFile2(
        url: String,
        title: String? = "下载文件",
        Cookie: String? = null,
        fileType: String = "pdf"
    ): Long {
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
            addRequestHeader(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
            )
            addRequestHeader(
                "accept-language",
                "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6"
            )
            addRequestHeader(
                "Referer",
                "http://123.121.147.7:88/ve/"
            )
        }
        return downloadManager.enqueue(request)
    }

    suspend fun downloadFileWithOkHttp(
        url: String,
        filename: String,
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

        val downloadDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, filename)

        response.body?.byteStream()?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
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
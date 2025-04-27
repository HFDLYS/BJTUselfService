package team.bjtuss.bjtuselfservice.utils

import android.app.DownloadManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import team.bjtuss.bjtuselfservice.MainApplication
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object DownloadUtil {
    // Existing download manager
    private val downloadManager =
        MainApplication.appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    // Add a StateFlow to track download progress
    private val _downloadProgress = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadStatus>> = _downloadProgress

    // Status data class to track download progress
    data class DownloadStatus(
        val filename: String,
        val progress: Float = 0f,
        val status: Status = Status.PENDING,
        val path: String = ""
    )

    enum class Status {
        PENDING, DOWNLOADING, COMPLETED, FAILED
    }

    fun downloadFile(
        url: String,
        title: String? = "下载文件",
        cookie: String? = null,
        fileType: String = "pdf"
    ) {
        // Existing implementation remains the same
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
            cookie?.let {
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
        try {
            // Update status to PENDING
            updateDownloadStatus(filename, DownloadStatus(filename, 0f, Status.PENDING, relativePath))

            val client = SmartCurriculumPlatformRepository.client
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", StudentAccountManager.getInstance().userAgent)
                .header("Cookie", KotlinUtils.getCookieOfClient() ?: "")
                .header("Referer", "http://123.121.147.7:88/ve/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                updateDownloadStatus(filename, DownloadStatus(filename, 0f, Status.FAILED, relativePath))
                throw IOException("下载失败: ${response.code}")
            }

            // 获取Downloads目录
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            // 创建多层目录结构
            val targetDir = if (relativePath.isNotEmpty()) {
                File(downloadDir, relativePath).apply {
                    if (!exists() && !mkdirs()) {
                        updateDownloadStatus(filename, DownloadStatus(filename, 0f, Status.FAILED, relativePath))
                        throw IOException("无法创建目标文件夹: $absolutePath")
                    }
                }
            } else {
                downloadDir
            }

            // 创建目标文件
            val file = File(targetDir, filename)

            // Get content length for progress tracking
            val contentLength = response.body?.contentLength() ?: -1L

            // Update status to DOWNLOADING
            updateDownloadStatus(filename, DownloadStatus(filename, 0f, Status.DOWNLOADING, relativePath))

            response.use { resp ->
                resp.body?.let { body ->
                    val input = body.byteStream()
                    val output = FileOutputStream(file)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead: Long = 0

                    try {
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            // Calculate and update progress
                            if (contentLength > 0) {
                                val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                                updateDownloadStatus(
                                    filename,
                                    DownloadStatus(filename, progress, Status.DOWNLOADING, relativePath)
                                )
                            }
                        }
                        output.flush()
                    } finally {
                        input.close()
                        output.close()
                    }
                }
            }

            // Notify system about the new file
            MediaScannerConnection.scanFile(
                MainApplication.appContext,
                arrayOf(file.absolutePath),
                null,
                null
            )

            // Update status to COMPLETED
            updateDownloadStatus(filename, DownloadStatus(filename, 1f, Status.COMPLETED, relativePath))

            // Return the file
            file
        } catch (e: Exception) {
            // Update status to FAILED on exception
            updateDownloadStatus(filename, DownloadStatus(filename, 0f, Status.FAILED, relativePath))
            Log.e("DownloadUtil", "Download failed: ${e.message}", e)
            throw e
        }
    }

    // Helper method to update download status
    private fun updateDownloadStatus(id: String, status: DownloadStatus) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
            put(id, status)
        }
    }

    // Remove completed downloads from tracking
    fun clearCompletedDownload(id: String) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
            remove(id)
        }
    }
}
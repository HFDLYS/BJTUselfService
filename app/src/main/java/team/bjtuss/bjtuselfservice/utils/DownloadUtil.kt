package team.bjtuss.bjtuselfservice.utils

import android.app.DownloadManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import team.bjtuss.bjtuselfservice.MainApplication
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

object DownloadUtil {
    // Existing download manager
    private val downloadManager =
        MainApplication.appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    // Add a StateFlow to track download progress
    private val _downloadProgress = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadStatus>> = _downloadProgress

    // 添加一个队列来管理下载请求
    private val downloadQueue = ConcurrentLinkedQueue<DownloadRequest>()

    // 控制同时下载的最大数量
    private val maxConcurrentDownloads = 8
    private val activeDownloads = AtomicInteger(0)

    // 处理下载队列的协程作用域
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 下载请求数据类
    data class DownloadRequest(
        val url: String,
        val filename: String,
        val relativePath: String = ""
    )

    // Status data class to track download progress
    data class DownloadStatus(
        val filename: String,
        val progress: Float = 0f,
        val status: Status = Status.PENDING,
        val path: String = "",
        val errorMessage: String = "" // 添加错误信息字段
    )

    enum class Status {
        PENDING, DOWNLOADING, COMPLETED, FAILED
    }

    init {
        // 启动下载队列处理器
        downloadScope.launch {
            processDownloadQueue()
        }
    }

    private suspend fun processDownloadQueue() {
        while (true) {
            if (activeDownloads.get() < maxConcurrentDownloads && downloadQueue.isNotEmpty()) {
                val request = downloadQueue.poll() ?: continue
                activeDownloads.incrementAndGet()

                downloadScope.launch {
                    try {
                        downloadFileWithOkHttp(request.url, request.filename, request.relativePath)
                    } catch (e: Exception) {
                        // 异常已在downloadFileWithOkHttp中处理
                    } finally {
                        activeDownloads.decrementAndGet()
                    }
                }
            }
            delay(100) // 避免CPU高占用
        }
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

    // 添加新方法来将下载请求加入队列
    fun queueDownload(url: String, filename: String, relativePath: String = "") {
        val downloadId = filename  // 使用文件名作为下载ID

        // 立即添加到状态跟踪中，确保UI可见
        updateDownloadStatus(downloadId, DownloadStatus(filename, 0f, Status.PENDING, relativePath))

        // 将请求添加到队列
        downloadQueue.add(DownloadRequest(url, filename, relativePath))
    }

    suspend fun downloadFileWithOkHttp(
        url: String,
        filename: String,
        relativePath: String = "" // 相对路径，如 "hello/hello1/hello2"
    ): File = withContext(Dispatchers.IO) {
        try {
            // Update status to DOWNLOADING
            updateDownloadStatus(filename, DownloadStatus(filename, 0f, Status.DOWNLOADING, relativePath))

            val client = SmartCurriculumPlatformRepository.client
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", StudentAccountManager.getInstance().userAgent)
                .header("Cookie", KotlinUtils.getCookieOfClient() ?: "")
                .header("Referer", "http://123.121.147.7:88/ve/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = "服务器返回错误: ${response.code} ${response.message}"
                updateDownloadStatus(filename, DownloadStatus(filename, 0f, Status.FAILED, relativePath, errorMsg))
                throw IOException(errorMsg)
            }

            // 获取Downloads目录
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            // 创建多层目录结构
            val targetDir = if (relativePath.isNotEmpty()) {
                File(downloadDir, relativePath).apply {
                    if (!exists() && !mkdirs()) {
                        val errorMsg = "无法创建目标文件夹: $absolutePath"
                        updateDownloadStatus(filename, DownloadStatus(filename, 0f, Status.FAILED, relativePath, errorMsg))
                        throw IOException(errorMsg)
                    }
                }
            } else {
                downloadDir
            }

            // 创建目标文件
            val file = File(targetDir, filename)

            // Get content length for progress tracking
            val contentLength = response.body?.contentLength() ?: -1L
            if (contentLength <= 0) {
                Log.w("DownloadUtil", "Unable to determine content length for $filename")
            }

            response.use { resp ->
                resp.body?.let { body ->
                    val input = body.byteStream()
                    val output = FileOutputStream(file)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead: Long = 0
                    var lastProgressUpdate = 0L

                    try {
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            // Calculate and update progress (limit updates frequency to reduce UI overhead)
                            val currentTime = System.currentTimeMillis()
                            if (contentLength > 0 && (currentTime - lastProgressUpdate > 100)) {
                                val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                                updateDownloadStatus(
                                    filename,
                                    DownloadStatus(filename, progress, Status.DOWNLOADING, relativePath)
                                )
                                lastProgressUpdate = currentTime
                            }
                        }
                        output.flush()
                    } finally {
                        input.close()
                        output.close()
                    }
                } ?: run {
                    val errorMsg = "服务器返回的响应体为空"
                    updateDownloadStatus(filename, DownloadStatus(filename, 0f, Status.FAILED, relativePath, errorMsg))
                    throw IOException(errorMsg)
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
            // Update status to FAILED on exception with error message
            val errorMsg = e.message ?: "未知错误"
            updateDownloadStatus(filename, DownloadStatus(filename, 0f, Status.FAILED, relativePath, errorMsg))
            Log.e("DownloadUtil", "Download failed: $errorMsg", e)
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

    // 清除所有已完成或失败的下载记录
    fun clearAllCompletedDownloads() {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
            keys.toList().forEach { key ->
                val status = get(key)
                if (status?.status == Status.COMPLETED || status?.status == Status.FAILED) {
                    remove(key)
                }
            }
        }
    }
}
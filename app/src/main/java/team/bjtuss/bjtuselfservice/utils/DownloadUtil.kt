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


    fun downloadFileByPost(
        url: String,
        title: String? = "下载文件",
        cookie: String? = null,
        fileType: String = "pdf",

        postParams: Map<String, String> = mapOf(),
        headers: Map<String, String> = mapOf()
    ) {
        val context = MainApplication.appContext
        // POST请求下载方式
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // 创建目标文件
                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outputFile = File(downloadsDir, "$title.$fileType")
                // 创建通知
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notificationChannelId = "download_channel"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        notificationChannelId,
                        "下载通知",
                        NotificationManager.IMPORTANCE_LOW
                    )
                    notificationManager.createNotificationChannel(channel)
                }

                val notificationBuilder = NotificationCompat.Builder(context, notificationChannelId)
                    .setContentTitle(title)
                    .setContentText("正在下载")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setProgress(0, 0, true)

                notificationManager.notify(1, notificationBuilder.build())

                // 构建POST请求
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

                val formBodyBuilder = FormBody.Builder()
                postParams.forEach { (key, value) ->
                    formBodyBuilder.add(key, value)
                }

                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(formBodyBuilder.build())
                    .addHeader("User-Agent", StudentAccountManager.getInstance().userAgent)

                cookie?.let { requestBuilder.addHeader("Cookie", it) }

                headers.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }

                val response = client.newCall(requestBuilder.build()).execute()

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "下载失败: ${response.code}", Toast.LENGTH_SHORT)
                            .show()
                    }
                    notificationBuilder
                        .setContentText("下载失败")
                        .setProgress(0, 0, false)
                    notificationManager.notify(1, notificationBuilder.build())
                    return@launch
                }

                // 保存响应内容到文件
                val inputStream = response.body?.byteStream()
                val outputStream = FileOutputStream(outputFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var totalBytesRead: Long = 0
                val contentLength = response.body?.contentLength() ?: -1L

                while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (contentLength > 0) {
                        val progress = (totalBytesRead * 100 / contentLength).toInt()
                        notificationBuilder.setProgress(100, progress, false)
                        notificationBuilder.setContentText("已下载: $progress%")
                        notificationManager.notify(1, notificationBuilder.build())
                    }
                }

                outputStream.close()
                inputStream?.close()

                // 下载完成通知
                notificationBuilder
                    .setContentText("下载完成")
                    .setProgress(0, 0, false)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)

                val intent = Intent(Intent.ACTION_VIEW)
//                val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                    FileProvider.getUriForFile(
//                        context,
//                        "${context.packageName}.provider",
//                        outputFile
//                    )
//                } else {
//                    Uri.fromFile(outputFile)
//                }
//
//                intent.setDataAndType(fileUri, getMimeType(fileType))
//                intent.flags =
//                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
//
//                val pendingIntent = PendingIntent.getActivity(
//                    context, 0, intent,
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
//                )

//                notificationBuilder.setContentIntent(pendingIntent)
                notificationManager.notify(1, notificationBuilder.build())

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "文件已下载", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "下载出错: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

        }
    }

    private fun getMimeType(fileType: String): String {
        return when (fileType.lowercase()) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "txt" -> "text/plain"
            else -> "*/*"
        }
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

//    fun smartDownload(url: String, cookie: String? = null) {
//        // 创建下载请求
//        val context= MainApplication.appContext
//        val request = DownloadManager.Request(Uri.parse(url)).apply {
//            // 设置网络类型
//            setAllowedOverMetered(true)
//            setAllowedOverRoaming(true)
//
//            // 设置下载完成后通知用户
//            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
//
//            // 添加请求头
//            cookie?.let { addRequestHeader("Cookie", it) }
//            addRequestHeader(
//                "User-Agent",
//                StudentAccountManager.getInstance().userAgent
//            )
//
//            // 文件名和目标位置会在回调中设置
//        }
//
//        // 获取下载管理器
//        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
//
//        // 在后台线程中获取文件信息
//        GlobalScope.launch(Dispatchers.IO) {
//            try {
//                // 创建连接来获取文件信息
//                val conn = URL(url).openConnection() as HttpURLConnection
//                conn.requestMethod = "HEAD"
//                cookie?.let { conn.setRequestProperty("Cookie", it) }
//                conn.setRequestProperty("User-Agent", StudentAccountManager.getInstance().userAgent)
//                conn.connect()
//
//                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
//                    // 尝试从Content-Disposition获取文件名
//                    var fileName: String? = null
//                    var fileType: String? = null
//
//                    val disposition = conn.getHeaderField("Content-Disposition")
//                    disposition?.let {
//                        val pattern = Pattern.compile("filename[^;=\\n]*=((['\"]).*?\\2|[^;\\n]*)")
//                        val matcher = pattern.matcher(it)
//                        if (matcher.find()) {
//                            fileName = matcher.group(1)?.replace("\"", "")?.replace("'", "")
//                        }
//                    }
//
//                    // 如果Content-Disposition中没有文件名，从URL中提取
//                    if (fileName.isNullOrEmpty()) {
//                        val urlPath = URL(url).path
//                        fileName = urlPath.substring(urlPath.lastIndexOf('/') + 1)
//
//                        // 处理URL中的查询参数
//                        if (fileName.contains('?')) {
//                            fileName = fileName.substring(0, fileName.indexOf('?'))
//                        }
//
//                        // 处理URL编码
//                        fileName = URLDecoder.decode(fileName, "UTF-8")
//                    }
//
//                    // 如果文件名仍为空，使用时间戳创建一个
//                    if (fileName.isNullOrEmpty()) {
//                        fileName = "download_${System.currentTimeMillis()}"
//                    }
//
//                    // 从Content-Type获取文件类型
//                    val contentType = conn.contentType
//                    contentType?.let {
//                        // 从MIME类型推断文件扩展名
//                        fileType = when {
//                            it.contains("pdf") -> "pdf"
//                            it.contains("msword") || it.contains("document") -> "doc"
//                            it.contains("sheet") || it.contains("excel") -> "xlsx"
//                            it.contains("presentation") || it.contains("powerpoint") -> "pptx"
//                            it.contains("text/plain") -> "txt"
//                            it.contains("image/jpeg") -> "jpg"
//                            it.contains("image/png") -> "png"
//                            it.contains("video/mp4") -> "mp4"
//                            it.contains("audio/mp3") -> "mp3"
//                            it.contains("application/zip") -> "zip"
//                            it.contains("application/json") -> "json"
//                            it.contains("application/xml") -> "xml"
//                            else -> null
//                        }
//                    }
//
//                    // 如果无法从Content-Type推断，则从文件名提取扩展名
//                    if (fileType.isNullOrEmpty() && fileName.contains('.')) {
//                        fileType = fileName.substring(fileName.lastIndexOf('.') + 1)
//                    }
//
//                    // 如果仍然无法确定文件类型，使用二进制文件
//                    if (fileType.isNullOrEmpty()) {
//                        fileType = "bin"
//                    }
//
//                    // 确保文件名有扩展名
//                    if (!fileName.contains('.')) {
//                        fileName = "$fileName.$fileType"
//                    }
//
//                    // 在主线程中更新UI并启动下载
//                    withContext(Dispatchers.Main) {
//                        // 设置下载目标
//                        request.setDestinationInExternalPublicDir(
//                            Environment.DIRECTORY_DOWNLOADS,
//                            fileName
//                        )
//
//                        // 设置通知标题
//                        request.setTitle(fileName)
//                        request.setDescription("正在下载文件")
//
//                        // 启动下载
//                        val downloadId = downloadManager.enqueue(request)
//
//                        // 可选：显示提示或执行其他操作
//                        Toast.makeText(context, "开始下载: $fileName", Toast.LENGTH_SHORT).show()
//                    }
//                } else {
//                    withContext(Dispatchers.Main) {
//                        Toast.makeText(context, "无法获取文件信息: ${conn.responseCode}", Toast.LENGTH_SHORT).show()
//                    }
//                }
//
//                conn.disconnect()
//
//            } catch (e: Exception) {
//                e.printStackTrace()
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(context, "下载出错: ${e.message}", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
}
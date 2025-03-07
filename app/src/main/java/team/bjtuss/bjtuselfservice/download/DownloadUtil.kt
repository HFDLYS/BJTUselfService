package team.bjtuss.bjtuselfservice.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import okhttp3.internal.addHeaderLenient
import team.bjtuss.bjtuselfservice.MainApplication
import team.bjtuss.bjtuselfservice.StudentAccountManager

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
}
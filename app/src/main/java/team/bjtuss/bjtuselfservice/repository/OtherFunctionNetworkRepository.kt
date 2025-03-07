package team.bjtuss.bjtuselfservice.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import team.bjtuss.bjtuselfservice.MainApplication
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.jsonclass.SchoolCalendarUrl
import team.bjtuss.bjtuselfservice.jsonclass.SemesterJsonType
import team.bjtuss.bjtuselfservice.utils.KotlinUtils.moshi

object OtherFunctionNetworkRepository {
    private val client = StudentAccountManager.getInstance().client

    suspend fun downloadSchoolCalendar() {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://bksy.bjtu.edu.cn/Admin/SemesterTranPage.aspx?noRemark=1")
                .header("User-Agent", StudentAccountManager.getInstance().userAgent)
                .build()
            val response = client.newCall(request).execute().body?.string()?.let {
                val doc = Jsoup.parse(it)
                val scriptContent = doc.select("script").html()
                val start = scriptContent.indexOf("[")
                val end = scriptContent.indexOf("]")
                val listStr = scriptContent.substring(start, end + 1)
                val regex = Regex("""var imgList = ($$.*?$$);""", RegexOption.DOT_MATCHES_ALL)
                val matchResult = regex.find(scriptContent)
                println("12111111")
                println(listStr)
                // 查找第一个url:部分
                val urlIndex = listStr.indexOf("url:")
                // 查找url后面的引号部分
                val startQuote = listStr.indexOf("\"", urlIndex)
                val endQuote = listStr.indexOf("\"", startQuote + 1)
                val postfix = listStr.substring(startQuote + 1, endQuote)
                val url = "https://bksy.bjtu.edu.cn" + postfix

                val request = DownloadManager.Request(Uri.parse(url)).apply {
//                    setTitle(fileName)
                    setDescription("正在下载文件")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
//                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }
                val downloadManager =
                    MainApplication.appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                downloadManager.enqueue(request)
            }


        }
    }

}
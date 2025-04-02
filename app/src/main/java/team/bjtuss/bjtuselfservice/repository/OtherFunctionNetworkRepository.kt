package team.bjtuss.bjtuselfservice.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.utils.DownloadUtil

object OtherFunctionNetworkRepository {
    private val client = StudentAccountManager.getInstance().client


    suspend fun downloadSchoolCalendar() {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://bksy.bjtu.edu.cn/Admin/SemesterTranPage.aspx?noRemark=1")
                .header("User-Agent", StudentAccountManager.getInstance().userAgent)
                .build()
            client.newCall(request).execute().body?.string()?.let {
                val url = parseCalendarUrlFromRawHtml(it)
                DownloadUtil.downloadFile(url = url, title = "校历")

            }
        }
    }


    suspend fun downloadGradeList(isEnglish: Boolean) {
        withContext(Dispatchers.IO) {
            var cookies = ""
            client.cookieJar.loadForRequest(
                Request.Builder().url("https://aa.bjtu.edu.cn").build().url
            )
                .forEach {
                    cookies += "${it.name}=${it.value};"
                }
            if (isEnglish) {
                DownloadUtil.downloadFile(
                    url =
                    "https://aa.bjtu.edu.cn/score/scorecard/stu/5201314/download_pdf/?type=card_en_sign&has_advance_query=",
                    title = "英文成绩单",
                    cookies
                )
            } else {
                DownloadUtil.downloadFile(
                    url = "https://aa.bjtu.edu.cn/score/scorecard/stu/5201314/download_pdf/?type=card_cn_sign&has_advance_query=",
                    title = "中文成绩单",
                    cookies
                )
            }
        }
    }


    private fun parseCalendarUrlFromRawHtml(html: String): String {
        val doc = Jsoup.parse(html)
        val scriptContent = doc.select("script").html()
        val start = scriptContent.indexOf("[")
        val end = scriptContent.indexOf("]")
        val listStr = scriptContent.substring(start, end + 1)
        val urlIndex = listStr.indexOf("url:")
        // 查找url后面的引号部分
        val startQuote = listStr.indexOf("\"", urlIndex)
        val endQuote = listStr.indexOf("\"", startQuote + 1)
        val postfix = listStr.substring(startQuote + 1, endQuote)
        val url = "https://bksy.bjtu.edu.cn" + postfix
        return url
    }


}
package team.bjtuss.bjtuselfservice.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import team.bjtuss.bjtuselfservice.StudentAccountManager
import java.text.SimpleDateFormat
import java.util.Locale

data class EmailSummary(
    val id: String,
    val from: String,
    val subject: String,
    val date: String,
    val unread: Boolean,
    val size: Long,
)

data class EmailDetail(
    val subject: String,
    val from: String,
    val to: String,
    val date: String,
    val body: String,
    val html: String?,
    val attachments: List<EmailAttachment>,
)

data class EmailAttachment(val id: String, val name: String)

/**
 * Coremail (mail.bjtu.edu.cn) backend used by MIS module 26.
 *
 * MIS redirects module/26 to /coremail/XT/index.jsp?sid=... with the wrong
 * host; the actual mail host is mail.bjtu.edu.cn. A fresh sid is obtained on
 * every request and the mail index is opened first so Coremail sets its
 * handshake cookies in the shared OkHttp cookie jar.
 */
object EmailRepository {
    private const val MIS_MAIL_MODULE = "https://mis.bjtu.edu.cn/module/module/26/"
    private const val MAIL_HOST = "https://mail.bjtu.edu.cn"
    private const val SID_PATTERN = "[?&]sid=([^&]+)"

    private val client: OkHttpClient
        get() = StudentAccountManager.getInstance().client

    suspend fun loadInbox(limit: Int = 30): List<EmailSummary> = withContext(Dispatchers.IO) {
        val sid = openSession()
        val url = "$MAIL_HOST/coremail/s".toHttpUrl().newBuilder()
            .addQueryParameter("sid", sid)
            .addQueryParameter("func", "mbox:listMessages")
            .addQueryParameter("mbox", "1")
            .addQueryParameter("start", "0")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("order", "date")
            .addQueryParameter("desc", "1")
            .build()
        val xml = execute(url)
        val root = Jsoup.parse(xml, "", Parser.xmlParser())
        val code = root.selectFirst("result > code")?.text()
        if (code != "S_OK") {
            throw IllegalStateException("Coremail list failed: $code")
        }
        root.select("result > array[name=var] > object")
            .mapNotNull { item -> parseSummary(item) }
            .sortedByDescending { parseEmailDate(it.date) }
    }

    internal fun parseEmailDate(value: String): Long {
        val text = value.trim()
        if (text.isEmpty()) return 0L
        text.toLongOrNull()?.let { raw ->
            return if (raw > 100_000_000_000L) raw else raw * 1000L
        }
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/M/d HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy年M月d日 HH:mm",
            "yyyy年M月d日",
            "yyyy-MM-dd",
            "yyyy/M/d",
        )
        patterns.forEach { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(text)
            }.getOrNull()?.let { return it.time }
        }
        return 0L
    }

    suspend fun loadMessage(mid: String): EmailDetail = withContext(Dispatchers.IO) {
        parseMessage(execute(messageRequest(openSession(), mid)))
    }

    internal fun messageRequest(sid: String, mid: String): Request {
        require(sid.isNotBlank() && mid.isNotBlank())
        val url = "$MAIL_HOST/coremail/XT/jsp/readMessage.jsp".toHttpUrl().newBuilder()
            .addQueryParameter("sid", sid)
            .build()
        val body = FormBody.Builder().add("mid", mid).add("mboxa", "")
            .add("part", "").add("mailCipherPassword", "").build()
        return Request.Builder().url(url).post(body).build()
    }

    internal fun parseMessage(raw: String): EmailDetail {
        val root = JsonParser.parseString(raw).asJsonObject
        check(root.get("code")?.asString == "S_OK") { "邮件读取失败：${root.get("code")}" }
        val result = root.getAsJsonObject("var") ?: error("邮件详情缺失")
        check(result.get("mailCipherEncrypted")?.asBoolean != true) { "暂不支持读取加密邮件" }
        val mail = result.getAsJsonObject("mail") ?: error("邮件内容缺失")
        val info = result.getAsJsonObject("mailInfo")
        val content = normalizeHtmlPart(mail.getAsJsonObject("mainPartData")?.get("content")?.asString.orEmpty())
        val html = content.takeIf { looksLikeHtml(it) }
        return EmailDetail(
            subject = mail.get("subject")?.asString.orEmpty(),
            from = mail.getAsJsonArray("from")?.joinToString { it.asString }.orEmpty(),
            to = mail.getAsJsonArray("to")?.joinToString { it.asString }.orEmpty(),
            date = info?.get("sentDate")?.asString.orEmpty(),
            body = if (html != null) Jsoup.parse(html).text() else content,
            html = html,
            attachments = mail.getAsJsonArray("attachments")?.map { parseAttachment(it.asJsonObject) }.orEmpty(),
        )
    }

    private fun parseAttachment(item: com.google.gson.JsonObject): EmailAttachment {
        val id = item.get("id")?.asString.orEmpty()
        val name = item.get("filename")?.asString.orEmpty()
        check(id.isNotBlank() && name.isNotBlank()) { "附件信息不完整" }
        return EmailAttachment(id, name)
    }

    suspend fun markRead(mid: String) = withContext(Dispatchers.IO) {
        CoremailReadStatus(client).markRead(openSession(), mid)
    }

    suspend fun downloadAttachment(context: Context, mid: String, attachment: EmailAttachment) = withContext(Dispatchers.IO) {
        val sid = openSession()
        val url = "$MAIL_HOST/coremail/s".toHttpUrl().newBuilder()
            .addQueryParameter("sid", sid)
            .addQueryParameter("func", "mbox:getMessageData")
            .addQueryParameter("mode", "download")
            .addQueryParameter("mbox", "1")
            .addQueryParameter("mid", mid)
            .addQueryParameter("part", attachment.id)
            .build()
        val cookies = client.cookieJar.loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }
        val request = DownloadManager.Request(Uri.parse(url.toString()))
            .setTitle(attachment.name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, attachment.name.substringAfterLast('/').substringAfterLast('\\'))
            .addRequestHeader("Cookie", cookies)
            .addRequestHeader("User-Agent", StudentAccountManager.getInstance().userAgent)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    suspend fun unreadCount(): Int = withContext(Dispatchers.IO) {
        CoremailReadStatus(client).unreadCount(openSession())
    }

    private fun openSession(): String {
        val moduleResponse = client.newCall(
            Request.Builder().url(MIS_MAIL_MODULE).header("Host", "mis.bjtu.edu.cn").build()
        ).execute()
        moduleResponse.use { response ->
            val finalUrl = response.request.url.toString()
            val html = response.body?.string().orEmpty()
            Log.d("EmailDebug", "module code=${response.code}")
            val sid = SID_PATTERN.toRegex().find(finalUrl)?.groupValues?.get(1)
                ?: SID_PATTERN.toRegex().find(html)?.groupValues?.get(1)
                ?: throw IllegalStateException("MIS did not return a Coremail sid")
            // Let Coremail set its handshake cookies before the XML API.
            val indexUrl = "$MAIL_HOST/coremail/XT/index.jsp?sid=$sid"
            client.newCall(Request.Builder().url(indexUrl).build()).execute().use { indexResponse ->
                Log.d("EmailDebug", "index code=${indexResponse.code}")
                if (indexResponse.code >= 500) {
                    throw IllegalStateException("Coremail index failed: ${indexResponse.code}")
                }
            }
            return sid
        }
    }

    private fun execute(url: okhttp3.HttpUrl): String = execute(Request.Builder().url(url).build())

    private fun execute(request: Request): String {
        val response = client.newCall(request).execute()
        response.use {
            if (!response.isSuccessful) {
                throw IllegalStateException("Coremail HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun normalizeHtmlPart(raw: String): String {
        val text = raw.trim()
        if (!text.contains("<") && text.contains("&lt;")) {
            return Parser.unescapeEntities(text, false)
        }
        return text
    }

    private fun looksLikeHtml(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = raw.lowercase(Locale.US)
        if (lower.startsWith("<!doctype") || lower.startsWith("<html")) return true
        return Regex("<(html|body|div|p|br|table|span|font|style)\\b").containsMatchIn(lower)
    }

    private fun parseSummary(item: Element): EmailSummary? {
        val id = item.childString("id").ifBlank { return null }
        val isRead = item.selectFirst("object[name=flags] > boolean[name=read]")
            ?.text()?.equals("true", ignoreCase = true) ?: false
        return EmailSummary(
            id = id,
            from = item.childString("from"),
            subject = item.childString("subject").ifBlank { "(无主题)" },
            date = item.childDate("sentDate").ifBlank { item.childDate("receivedDate") },
            unread = !isRead,
            size = item.childLong("size"),
        )
    }

    private fun Element.childString(name: String): String =
        children().firstOrNull { it.tagName() == "string" && it.attr("name") == name }?.text().orEmpty()

    private fun Element.childDate(name: String): String =
        children().firstOrNull { it.tagName() == "date" && it.attr("name") == name }?.text().orEmpty()

    private fun Element.childLong(name: String): Long =
        children().firstOrNull { it.tagName() == "int" && it.attr("name") == name }
            ?.text()?.toLongOrNull() ?: 0L
}

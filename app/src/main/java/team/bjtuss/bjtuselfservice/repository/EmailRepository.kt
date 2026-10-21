package team.bjtuss.bjtuselfservice.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

data class EmailAttachment(
    val id: Int,
    val name: String,
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
        val sid = openSession()
        val metaUrl = "$MAIL_HOST/coremail/s".toHttpUrl().newBuilder()
            .addQueryParameter("sid", sid)
            .addQueryParameter("func", "mbox:readMessage")
            .addQueryParameter("mbox", "1")
            .addQueryParameter("mid", mid)
            .build()
        val metaXml = execute(metaUrl)
        val root = Jsoup.parse(metaXml, "", Parser.xmlParser())
        val code = root.selectFirst("result > code")?.text()
        if (code != "S_OK") {
            throw IllegalStateException("Coremail read failed: $code")
        }
        val varObject = root.selectFirst("result > object[name=var]")
            ?: throw IllegalStateException("Coremail message metadata missing")

        val subject = varObject.childString("subject")
        val from = varObject.selectFirst("array[name=from] > string")?.text().orEmpty()
        val to = varObject.selectFirst("array[name=to] > string")?.text().orEmpty()
        val date = varObject.childDate("sentDate")
        val attachments = varObject.select("array[name=attachments] > object").mapNotNull { attachment ->
            val name = attachment.childString("name")
            if (name.isBlank()) null else EmailAttachment(
                id = attachment.childInt("id"),
                name = name,
                size = attachment.childLong("size"),
            )
        }

        val htmlPart = varObject.selectFirst("object[name=html]")
            ?.takeIf { it.childInt("id") > 0 }
        val textPart = varObject.selectFirst("object[name=text]")
            ?.takeIf { it.childInt("id") > 0 }
        val part = htmlPart ?: textPart
        val partId = part?.childInt("id") ?: 0
        var rawHtml: String? = null
        val body = if (partId > 0) {
            val bodyUrl = "$MAIL_HOST/coremail/s".toHttpUrl().newBuilder()
                .addQueryParameter("sid", sid)
                .addQueryParameter("func", "mbox:getMessageData")
                .addQueryParameter("mbox", "1")
                .addQueryParameter("mid", mid)
                .addQueryParameter("part", partId.toString())
                .build()
            val raw = normalizeHtmlPart(execute(bodyUrl))
            if (htmlPart != null || looksLikeHtml(raw)) {
                rawHtml = raw
                Jsoup.parse(raw).text()
            } else {
                raw
            }
        } else {
            ""
        }

        EmailDetail(
            subject = subject,
            from = from,
            to = to,
            date = date,
            body = body,
            html = rawHtml,
            attachments = attachments,
        )
    }

    suspend fun downloadAttachment(mid: String, attachment: EmailAttachment): ByteArray =
        withContext(Dispatchers.IO) {
            val sid = openSession()
            val url = "$MAIL_HOST/coremail/s".toHttpUrl().newBuilder()
                .addQueryParameter("sid", sid)
                .addQueryParameter("func", "mbox:getMessageData")
                .addQueryParameter("mbox", "1")
                .addQueryParameter("mid", mid)
                .addQueryParameter("part", attachment.id.toString())
                .build()
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (!response.isSuccessful) throw IllegalStateException("附件 HTTP " + response.code)
                val bytes = response.body?.bytes() ?: ByteArray(0)
                val text = bytes.toString(Charsets.UTF_8)
                if (text.trimStart().startsWith("<")) {
                    val root = Jsoup.parse(text, "", Parser.xmlParser())
                    val encoded = root.selectFirst("string")?.text().orEmpty()
                    if (encoded.isNotBlank()) {
                        android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                    } else bytes
                } else {
                    bytes
                }
            }
        }

    private fun openSession(): String {
        val moduleResponse = client.newCall(
            Request.Builder().url(MIS_MAIL_MODULE).header("Host", "mis.bjtu.edu.cn").build()
        ).execute()
        moduleResponse.use { response ->
            val finalUrl = response.request.url.toString()
            val html = response.body?.string().orEmpty()
            Log.d("EmailDebug", "module code=${response.code} final=$finalUrl")
            val sid = SID_PATTERN.toRegex().find(finalUrl)?.groupValues?.get(1)
                ?: SID_PATTERN.toRegex().find(html)?.groupValues?.get(1)
                ?: throw IllegalStateException("MIS did not return a Coremail sid")
            // Let Coremail set its handshake cookies before the XML API.
            val indexUrl = "$MAIL_HOST/coremail/XT/index.jsp?sid=$sid"
            client.newCall(Request.Builder().url(indexUrl).build()).execute().use { indexResponse ->
                Log.d("EmailDebug", "index code=${indexResponse.code} sid=$sid")
                if (indexResponse.code >= 500) {
                    throw IllegalStateException("Coremail index failed: ${indexResponse.code}")
                }
            }
            return sid
        }
    }

    private fun execute(url: okhttp3.HttpUrl): String {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
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

    private fun Element.childInt(name: String): Int =
        children().firstOrNull { it.tagName() == "int" && it.attr("name") == name }
            ?.text()?.toIntOrNull() ?: 0

    private fun Element.childLong(name: String): Long =
        children().firstOrNull { it.tagName() == "int" && it.attr("name") == name }
            ?.text()?.toLongOrNull() ?: 0L
}

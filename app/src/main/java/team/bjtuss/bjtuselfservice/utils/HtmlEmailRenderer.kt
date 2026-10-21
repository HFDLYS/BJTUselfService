package team.bjtuss.bjtuselfservice.utils

import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import androidx.core.text.HtmlCompat
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.util.ArrayDeque

/**
 * Native HTML mail renderer.
 *
 * Mail HTML often carries a full document, inline CSS and hard-coded black
 * colours. Those are removed before HtmlCompat parses it; the TextView then
 * supplies the active Material colour for the current light/dark theme.
 */
object HtmlEmailRenderer {
    fun renderForDisplay(html: String): Spanned {
        val source = decodeEscapedHtml(html)
        val document = Jsoup.parse(source)
        document.select("style, script, head, meta, title, link").remove()
        document.select("[style]").forEach { it.removeAttr("style") }
        document.select("font[color]").forEach { it.removeAttr("color") }
        document.select("body").forEach {
            it.removeAttr("text")
            it.removeAttr("bgcolor")
            it.removeAttr("link")
            it.removeAttr("vlink")
            it.removeAttr("alink")
        }
        val cleaned = document.body()?.html().orEmpty().ifBlank { source }
        return HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_COMPACT, null, MarkTagHandler())
    }

    private fun decodeEscapedHtml(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return text
        // Some Coremail parts arrive entity-escaped: &lt;html&gt;...
        return if (!text.contains("<") && text.contains("&lt;")) {
            Parser.unescapeEntities(text, false)
        } else {
            text
        }
    }

    private class MarkTagHandler : Html.TagHandler {
        private val starts = ArrayDeque<Int>()

        override fun handleTag(
            opening: Boolean,
            tag: String,
            output: Editable,
            xmlReader: org.xml.sax.XMLReader,
        ) {
            if (!tag.equals("mark", ignoreCase = true)) return
            if (opening) {
                starts.addLast(output.length)
            } else {
                val start = starts.pollLast() ?: return
                output.setSpan(
                    BackgroundColorSpan(0xFFFFF59D.toInt()),
                    start,
                    output.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }
}

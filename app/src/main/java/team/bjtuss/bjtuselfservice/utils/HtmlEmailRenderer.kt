package team.bjtuss.bjtuselfservice.utils

import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import androidx.core.text.HtmlCompat
import java.util.ArrayDeque

/**
 * Converts received HTML mail to a Spanned value for native display.
 * <mark> is preserved as a yellow highlight without introducing a WebView.
 */
object HtmlEmailRenderer {
    fun renderForDisplay(html: String): Spanned =
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT, null, MarkTagHandler())

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

package team.bjtuss.bjtuselfservice.utils

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View

enum class SmartLinkType {
    EMAIL,
    PHONE,
    URL,
    CODE,
}

data class SmartLinkAction(
    val type: SmartLinkType,
    val value: String,
)

/** Adds clickable highlights to email/phone/URL/ID tokens in a Spanned body. */
object SmartLinkifier {
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val PHONE = Regex("""(?<!\d)(?:1[3-9]\d{9}|0\d{2,3}-?\d{7,8})(?!\d)""")
    private val KEYWORD = Regex(
        """(?i)(?:学号|工号|订单号|单号|编号|手机号|手机|电话|邮箱|email|e-mail|QQ|快递|取件码|验证码)\s*[:：]?\s*([A-Za-z0-9@._%+\-]{4,})"""
    )

    fun linkify(
        text: CharSequence,
        linkColor: Int,
        highlightColor: Int,
        onAction: (SmartLinkAction) -> Unit,
    ): CharSequence {
        val spannable: Spannable = when (text) {
            is Spannable -> text
            else -> SpannableString(text)
        }
        val source = spannable.toString()
        val ranges = mutableListOf<Range>()

        fun add(start: Int, end: Int, type: SmartLinkType, value: String) {
            if (start < 0 || end <= start || end > source.length) return
            if (ranges.any { start < it.end && end > it.start }) return
            ranges.add(Range(start, end, type, value))
        }

        KEYWORD.findAll(source).forEach { match ->
            val group = match.groups[1] ?: return@forEach
            val value = group.value.trimEnd('.', ',', '。', '，', ')', '）', ';', '；')
            val type = when {
                value.contains("@") -> SmartLinkType.EMAIL
                PHONE.matches(value) -> SmartLinkType.PHONE
                else -> SmartLinkType.CODE
            }
            add(group.range.first, group.range.first + value.length, type, value)
        }
        EMAIL.findAll(source).forEach {
            add(it.range.first, it.range.last + 1, SmartLinkType.EMAIL, it.value)
        }
        URL.findAll(source).forEach {
            val value = it.value.trimEnd('.', ',', '。', '，', ')', '）', ';', '；', ']', '}')
            add(it.range.first, it.range.first + value.length, SmartLinkType.URL, value)
        }
        PHONE.findAll(source).forEach {
            add(it.range.first, it.range.last + 1, SmartLinkType.PHONE, it.value)
        }

        ranges.sortedBy { it.start }.forEach { range ->
            val action = SmartLinkAction(range.type, range.value)
            spannable.setSpan(
                SmartClickSpan(action, linkColor, highlightColor, onAction),
                range.start,
                range.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            spannable.setSpan(
                ForegroundColorSpan(linkColor),
                range.start,
                range.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            spannable.setSpan(
                BackgroundColorSpan(highlightColor),
                range.start,
                range.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                range.start,
                range.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return spannable
    }

    private data class Range(
        val start: Int,
        val end: Int,
        val type: SmartLinkType,
        val value: String,
    )

    private class SmartClickSpan(
        private val action: SmartLinkAction,
        private val color: Int,
        private val backgroundColor: Int,
        private val onAction: (SmartLinkAction) -> Unit,
    ) : ClickableSpan() {
        override fun onClick(widget: View) {
            onAction(action)
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.color = color
            ds.bgColor = backgroundColor
            ds.isUnderlineText = false
        }
    }
}

package team.bjtuss.bjtuselfservice.shared.feature.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

/**
 * 小组件点击后打开的应用内位置。链接形如：
 * - `bjtuselfservice://widget/schedule?date=2026-10-13`：课程表定位到该日；
 * - `bjtuselfservice://widget/course?id=123&date=2026-10-13`：课程表定位到该日并打开这节课的详情，
 *   实验同样打开课程表里的实验详情；考试改去考试安排页打开该考试详情（由壳层按事件种类决定）。
 *
 * 链接只含课程表内部 id 与日期，不含账号或个人信息。
 */
sealed interface WidgetLink {
    data class Schedule(val date: LocalDate?) : WidgetLink
    data class Course(val id: Int, val date: LocalDate?) : WidgetLink

    fun toUrl(): String = when (this) {
        is Schedule -> "$WIDGET_LINK_PREFIX/schedule" + (date?.let { "?date=$it" } ?: "")
        is Course -> "$WIDGET_LINK_PREFIX/course?id=$id" + (date?.let { "&date=$it" } ?: "")
    }

    companion object {
        fun parse(url: String): WidgetLink? {
            if (!url.startsWith("$WIDGET_LINK_PREFIX/")) return null
            val rest = url.removePrefix("$WIDGET_LINK_PREFIX/")
            val path = rest.substringBefore('?')
            val query = rest.substringAfter('?', "").split('&').filter { '=' in it }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            val date = query["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            return when (path) {
                "schedule" -> Schedule(date)
                "course" -> query["id"]?.toIntOrNull()?.let { Course(it, date) }
                else -> null
            }
        }
    }
}

internal const val WIDGET_LINK_PREFIX = "bjtuselfservice://widget"

/**
 * 平台入口（iOS 的 onOpenURL）收到链接后放这里，登录后的壳层取走并跳转。
 * 只保留最近一次点击；未登录时一直留着，登录完成后再处理。
 */
object WidgetLinkInbox {
    private val mutablePending = MutableStateFlow<WidgetLink?>(null)
    val pending: StateFlow<WidgetLink?> = mutablePending.asStateFlow()

    /** 返回链接是否属于小组件；不认识的链接交给系统默认处理。 */
    fun offer(url: String): Boolean {
        val link = WidgetLink.parse(url) ?: return false
        mutablePending.value = link
        return true
    }

    fun consume(link: WidgetLink) {
        mutablePending.compareAndSet(link, null)
    }
}

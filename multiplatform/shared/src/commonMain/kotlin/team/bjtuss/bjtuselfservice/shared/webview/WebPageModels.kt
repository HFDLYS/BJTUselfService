package team.bjtuss.bjtuselfservice.shared.webview

import io.ktor.http.Url

/**
 * 共享网页容器协议。只负责把"要在应用内打开一个学校网页"的意图
 * 连同必要的会话 Cookie 一起交给平台实现；不持有任何平台视图对象。
 *
 * 安全边界：Cookie 同步只允许学校域名，且按域名/路径精确匹配；
 * 外部链接不注入 Cookie，是否允许由 `externalLinkPolicy` 决定。
 */

/** 一条要同步进网页容器的会话 Cookie。值在字符串化时脱敏。 */
data class WebCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val secure: Boolean = true,
) {
    override fun toString(): String =
        "WebCookie(name=$name, value=<redacted>, domain=$domain, path=$path, secure=$secure)"
}

/** 外部链接（非请求目标域名）的处理策略。 */
enum class ExternalLinkPolicy {
    /** 阻止在应用内打开，交由系统浏览器处理。 */
    OPEN_EXTERNALLY,

    /** 直接阻止，不离开当前页面。 */
    BLOCK,
}

/** 一次应用内网页请求。 */
data class WebPageRequest(
    val url: String,
    val title: String,
    val cookies: List<WebCookie> = emptyList(),
    val externalLinkPolicy: ExternalLinkPolicy = ExternalLinkPolicy.OPEN_EXTERNALLY,
) {
    init {
        require(url.startsWith("https://")) { "应用内网页只允许 https，收到: ${url.substringBefore('?')}" }
        require(title.isNotBlank()) { "网页标题不能为空" }
    }

    override fun toString(): String =
        "WebPageRequest(url=${url.substringBefore('?')}, title=$title, cookies=${cookies.size}, policy=$externalLinkPolicy)"
}

/** 校验结果。 */
sealed interface WebPageValidation {
    data object Allowed : WebPageValidation
    data class Rejected(val reason: String) : WebPageValidation
}

/**
 * 只允许学校域名在应用内网页容器中使用会话 Cookie。
 * 返回每个请求域名是否允许注入 Cookie。
 */
object SchoolWebDomainPolicy {
    private val allowedHosts = setOf(
        "mis.bjtu.edu.cn",
        "cas.bjtu.edu.cn",
        "sso.bjtu.edu.cn",
        "aa.bjtu.edu.cn",
        "bksycenter.bjtu.edu.cn",
        "dean.bjtu.edu.cn",
        "mail.bjtu.edu.cn",
        "phyvlab.bjtu.edu.cn",
    )

    /**
     * 应用内导航是否允许留在 WebView：必须是 https，且 host 在学校白名单。
     * 只看 host 会放行 `http://mis.bjtu.edu.cn` 或 `file://mis.bjtu.edu.cn/...`。
     */
    fun isSchoolHost(url: String): Boolean {
        val parsed = runCatching { Url(url) }.getOrNull() ?: return false
        if (parsed.protocol.name != "https") return false
        return parsed.host.lowercase() in allowedHosts
    }

    private fun hostOf(url: String): String =
        runCatching { Url(url).host.lowercase() }.getOrDefault("")

    /**
     * 校验一个网页请求的 Cookie 是否都只发往请求自身所属的学校域名。
     * 任何 Cookie 的域名与请求域名不匹配，或与学校域名白名单不符，都会被拒绝。
     */
    fun validate(request: WebPageRequest): WebPageValidation {
        val pageHost = hostOf(request.url)
        if (!isSchoolHost(request.url)) {
            return WebPageValidation.Rejected("非学校域名不允许在应用内打开: $pageHost")
        }
        val offender = request.cookies.firstOrNull { cookie ->
            val cookieHost = cookie.domain.removePrefix(".").lowercase()
            cookieHost !in allowedHosts || cookieHost != pageHost
        }
        if (offender != null) {
            return WebPageValidation.Rejected("Cookie 域名 ${offender.domain} 与页面 $pageHost 不匹配")
        }
        return WebPageValidation.Allowed
    }
}

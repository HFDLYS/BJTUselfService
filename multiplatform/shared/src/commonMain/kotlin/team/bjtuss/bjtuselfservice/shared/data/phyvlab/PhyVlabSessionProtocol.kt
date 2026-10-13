package team.bjtuss.bjtuselfservice.shared.data.phyvlab

import team.bjtuss.bjtuselfservice.shared.network.SchoolEndpoints

import com.fleeksoft.ksoup.Ksoup
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.parseOffMain

private val PHYVLAB_LOGIN_URL = "${SchoolEndpoints.PHYVLAB_ORIGIN}/login/index.php"
private val PHYVLAB_HOME_URL = "${SchoolEndpoints.PHYVLAB_ORIGIN}/"
private val PHYVLAB_COURSES_URL = "${SchoolEndpoints.PHYVLAB_ORIGIN}/my/courses.php"

private val ALLOWED_HANDSHAKE_HOSTS = setOf("phyvlab.bjtu.edu.cn", "cas.bjtu.edu.cn")

sealed interface PhyVlabSessionResult {
    data class Ready(val homeUrl: String) : PhyVlabSessionResult
    data object CasLoginRequired : PhyVlabSessionResult
    /**
     * `detail` is deliberately a coarse, non-sensitive diagnostic label.  It
     * never contains a URL query, cookie, OAuth code, or Moodle session key.
     */
    data class Failed(
        val reason: PhyVlabRemoteFailure,
        val detail: String? = null,
    ) : PhyVlabSessionResult
}

/**
 * 物理在线 Moodle OAuth2/CAS 握手。
 *
 * 真实流程：未登录访问 `/login/index.php` 会给出 OAuth 登录按钮和 Moodle `sesskey`；
 * 访问 `/auth/oauth2/login.php?id=1&sesskey=...&wantsurl=/` 后：
 * - CAS 已有会话：直接回到 Moodle 个人主页/目标页（建立 Moodle Cookie）；
 * - CAS 未登录：停在 `cas.bjtu.edu.cn/auth/login/`（含 CSRF/CAPTCHA 表单）。
 *
 * 安全边界：只允许 phyvlab/cas 的 HTTPS 443，任何其它主机、HTTP 或未知端口立即停止。
 */
class PhyVlabSessionProtocol(
    private val transport: SchoolHttpTransport,
) {
    suspend fun establishSession(): PhyVlabSessionResult = try {
        val login = executeRaw(PHYVLAB_LOGIN_URL, referer = PHYVLAB_HOME_URL)
        // Moodle/反向代理有时会先把登录入口规范化到带语言或 redirect 参数的地址。
        // raw client 不自动跟随跳转，因此这里先在同一白名单内落地，再解析页面。
        val loginPage = if (login.statusCode in 300..399) {
            followHandshake(login)
        } else {
            login
        }
        if (loginPage.statusCode !in 200..299) {
            return PhyVlabSessionResult.Failed(
                PhyVlabRemoteFailure.NETWORK,
                detail = "login-page-http-error",
            )
        }
        val marks = parseOffMain { loginPage.pageMarks() }
        phyVlabDebug(
            "login page auth=${marks.authenticated} " +
                "casForm=${marks.casLoginForm} oauth=${marks.oauthUrl != null}",
        )

        // 已登录：页面显示“退出登录”或包含个人主页导航，Moodle 会话已存在。
        if (marks.authenticated) {
            return PhyVlabSessionResult.Ready(PHYVLAB_HOME_URL)
        }

        // 如果 CAS 会话已失效，初始入口也可能直接落在 CAS 登录表单。
        if (loginPage.finalUrl.startsWith(SchoolEndpoints.CAS_ORIGIN) && marks.casLoginForm) {
            return PhyVlabSessionResult.CasLoginRequired
        }

        val oauthUrl = marks.oauthUrl ?: return PhyVlabSessionResult.Failed(
            PhyVlabRemoteFailure.SESSION_EXPIRED,
            detail = "login-page-oauth-link-missing",
        )
        val oauth = executeRaw(
            url = oauthUrl,
            referer = loginPage.finalUrl,
        )
        if (oauth.statusCode in 300..399) {
            followHandshake(oauth).toSessionResult()
        } else {
            oauth.toSessionResult()
        }
    } catch (error: PhyVlabRemoteException) {
        PhyVlabSessionResult.Failed(error.reason, detail = error.reason.name.lowercase())
    }

    /** 依次处理 OAuth 跳转和 CAS 回调，直到落地 phyvlab 或 CAS 登录表单。 */
    private suspend fun followHandshake(first: SchoolHttpResponse): SchoolHttpResponse {
        var current = first
        repeat(MAX_HOPS) {
            if (current.statusCode !in 300..399) return current
            val location = current.header("Location")?.trim().orEmpty()
            if (location.isEmpty()) return current
            val target = resolveAllowedTarget(current.finalUrl, location)
                ?: throw PhyVlabRemoteException(PhyVlabRemoteFailure.SESSION_EXPIRED)
            current = executeRaw(url = target, referer = current.finalUrl)
            if (current.statusCode !in 300..399) return current
        }
        return current
    }

    private suspend fun SchoolHttpResponse.toSessionResult(): PhyVlabSessionResult {
        val url = finalUrl
        if (url.startsWith(SchoolEndpoints.CAS_ORIGIN) && parseOffMain { looksLikeCasLoginForm(bodyText()) }) {
            return PhyVlabSessionResult.CasLoginRequired
        }
        if (url.startsWith(SchoolEndpoints.PHYVLAB_ORIGIN)) {
            if (parseOffMain { isPhyVlabAuthenticatedPage(url, bodyText()) }) {
                return PhyVlabSessionResult.Ready(PHYVLAB_HOME_URL)
            }

            // OAuth 回调页本身可能只是一个无导航的中间页。再探测一次“我的课程”，
            // 只有页面出现登录态标记才算建立了 Moodle 会话，避免把访客/登录页误判为成功。
            val probe = executeRaw(PHYVLAB_COURSES_URL, referer = url)
            val settled = if (probe.statusCode in 300..399) followHandshake(probe) else probe
            val settledMarks = parseOffMain { settled.pageMarks() }
            phyVlabDebug(
                "oauth landing ${safePhyVlabEndpoint(settled.finalUrl)} " +
                    "auth=${settledMarks.authenticated} " +
                    "casForm=${settledMarks.casLoginForm} " +
                    "loginPage=${settledMarks.phyVlabLoginPage}",
            )
            if (settled.finalUrl.startsWith(SchoolEndpoints.CAS_ORIGIN) && settledMarks.casLoginForm) {
                return PhyVlabSessionResult.CasLoginRequired
            }
            if (settled.finalUrl.startsWith(SchoolEndpoints.PHYVLAB_ORIGIN) && settledMarks.authenticated) {
                return PhyVlabSessionResult.Ready(PHYVLAB_HOME_URL)
            }
            return PhyVlabSessionResult.Failed(
                PhyVlabRemoteFailure.SESSION_EXPIRED,
                detail = if (settledMarks.phyVlabLoginPage) {
                    "phyvlab-login-session-missing"
                } else {
                    "phyvlab-session-unconfirmed"
                },
            )
        }
        return PhyVlabSessionResult.Failed(
            PhyVlabRemoteFailure.SESSION_EXPIRED,
            detail = "oauth-finished-outside-approved-host",
        )
    }

    private suspend fun executeRaw(url: String, referer: String): SchoolHttpResponse = try {
        val request = SchoolHttpRequest(
            method = SchoolHttpMethod.GET,
            url = url,
            headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
                "Referer" to referer,
            ),
        )
        val response = transport.executeWithoutRedirects(request)
        phyVlabDebug(
            "raw GET ${safePhyVlabEndpoint(url)} -> ${response.statusCode} " +
                "${safePhyVlabEndpoint(response.finalUrl)} bytes=${response.body.size}",
        )
        response
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (_: Exception) {
        phyVlabDebug("raw GET ${safePhyVlabEndpoint(url)} -> exception")
        throw PhyVlabRemoteException(PhyVlabRemoteFailure.NETWORK)
    }
}

private fun resolveAllowedTarget(base: String, location: String): String? {
    if (location.any(Char::isISOControl) || '#' in location) return null
    val parsed = runCatching {
        URLBuilder(Url(base)).takeFrom(location).build()
    }.getOrNull() ?: return null
    if (parsed.protocol.name != "https") return null
    if (parsed.host in ALLOWED_HANDSHAKE_HOSTS && (parsed.port == 443 || parsed.port == 0)) {
        return parsed.toString()
    }
    return null
}

private fun parsePhyVlabSesskey(html: String): String? {
    val doc = Ksoup.parse(html)
    doc.select("script").forEach { script ->
        val text = script.data()
        val match = Regex("[\"']sesskey[\"']\\s*:\\s*[\"']([^\"']+)[\"']")
            .find(text)
        if (match != null) return match.groupValues[1]
    }
    return null
}

/**
 * Moodle 登录页的 OAuth 按钮是动态生成的，不能依赖固定 client id、state 或 wantsurl。
 * 优先使用按钮 href；少数旧模板只在 M.cfg 中给出 sesskey 时再补到白名单路径上。
 */
private fun parsePhyVlabOauthUrl(html: String): String? {
    val document = Ksoup.parse(html)
    val sesskey = parsePhyVlabSesskey(html)
    val link = document.select("a[href]")
        .asSequence()
        .mapNotNull { anchor ->
            val resolved = resolveAllowedTarget(PHYVLAB_LOGIN_URL, anchor.attr("href"))
                ?: return@mapNotNull null
            resolved.takeIf(::isPhyVlabOauthUrl)
        }
        .firstOrNull()
    if (link != null) {
        // href 已经带 sesskey 时完全保留站点生成的查询参数；否则仅补当前页的动态值。
        if (sesskey == null || Url(link).parameters["sesskey"].orEmpty().isNotBlank()) {
            return link
        }
        return runCatching {
            URLBuilder(Url(link)).apply {
                parameters.append("sesskey", sesskey)
            }.build().toString()
        }.getOrNull()
    }
    return sesskey?.let {
        "${SchoolEndpoints.PHYVLAB_ORIGIN}/auth/oauth2/login.php?id=1&sesskey=$it&wantsurl=%2F"
    }
}

private fun isPhyVlabOauthUrl(url: String): Boolean {
    val parsed = runCatching { Url(url) }.getOrNull() ?: return false
    return parsed.protocol.name == "https" &&
        parsed.host == "phyvlab.bjtu.edu.cn" &&
        (parsed.port == 443 || parsed.port == 0) &&
        parsed.encodedPath == "/auth/oauth2/login.php"
}

/** 握手页面的登录态标记：一次在后台算齐，调试日志与判断共用，不再逐项重复解析整页。 */
private class PhyVlabPageMarks(
    val authenticated: Boolean,
    val casLoginForm: Boolean,
    val oauthUrl: String?,
    val phyVlabLoginPage: Boolean,
)

private fun SchoolHttpResponse.pageMarks(): PhyVlabPageMarks {
    val body = bodyText()
    return PhyVlabPageMarks(
        authenticated = isPhyVlabAuthenticatedPage(finalUrl, body),
        casLoginForm = looksLikeCasLoginForm(body),
        oauthUrl = parsePhyVlabOauthUrl(body),
        phyVlabLoginPage = looksLikePhyVlabLoginPage(body),
    )
}

private fun isPhyVlabAuthenticatedPage(url: String, html: String): Boolean {
    if (!url.startsWith(SchoolEndpoints.PHYVLAB_ORIGIN)) return false
    val doc = Ksoup.parse(html)
    val logout = doc.selectFirst("a[href*='/login/logout.php']") != null
    // “我的课程/个人主页”链接在访客页也可能出现，不能单独作为登录态凭据；
    // Moodle 登录后的全局菜单会稳定提供退出链接。
    val userMenu = doc.selectFirst("[data-region='usermenu'], a[href='/user/profile.php']") != null
    val body = doc.title() + " " + doc.text()
    val confirmedLoggedIn = "您已经以" in body && "身份登录" in body
    return logout || userMenu || confirmedLoggedIn
}

private fun looksLikePhyVlabLoginPage(html: String): Boolean {
    val document = Ksoup.parse(html)
    return document.selectFirst("a[href*='/auth/oauth2/login.php']") != null ||
        document.selectFirst("form[action*='/login/index.php']") != null ||
        (document.text().contains("用户名或邮箱") && document.text().contains("密码"))
}

private fun looksLikeCasLoginForm(html: String): Boolean {
    val doc = Ksoup.parse(html)
    return doc.selectFirst("form#login") != null && doc.selectFirst("input#id_captcha_0") != null
}

private const val MAX_HOPS = 12

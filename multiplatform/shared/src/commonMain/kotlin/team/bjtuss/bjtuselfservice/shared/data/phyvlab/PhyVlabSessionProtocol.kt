package team.bjtuss.bjtuselfservice.shared.data.phyvlab

import team.bjtuss.bjtuselfservice.shared.auth.Credentials
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

private const val PHYVLAB_HOST = "phyvlab.bjtu.edu.cn"

sealed interface PhyVlabSessionResult {
    data class Ready(val homeUrl: String) : PhyVlabSessionResult
    /** 尚未配置物理在线独立账号；需要用户在设置里填写。 */
    data object AccountRequired : PhyVlabSessionResult
    /** 已配置的独立账号被 Moodle 拒绝（“登录无效”）。 */
    data object CredentialsRejected : PhyVlabSessionResult
    /**
     * `detail` is deliberately a coarse, non-sensitive diagnostic label.  It
     * never contains a URL query, cookie, or Moodle session key.
     */
    data class Failed(
        val reason: PhyVlabRemoteFailure,
        val detail: String? = null,
    ) : PhyVlabSessionResult
}

/**
 * 物理在线 Moodle 会话。
 *
 * 学校统一身份认证改为 sso.bjtu.edu.cn 后，物理在线的 OAuth 按钮仍指向已下线的 CAS，
 * 无法再借 MIS 登录态进入。现在与物理实验、演示实验一样使用独立账号密码：
 * 已有 MoodleSession 时直接复用；否则取 `/login/index.php` 的 `form#login`（含 logintoken），
 * 用 [credentials] 提供的账号提交，再确认页面出现登录态标记。
 *
 * 安全边界：只访问 phyvlab 的 HTTPS 443，跳转到任何其它主机、HTTP 或未知端口立即停止。
 * 凭据只由调用方从系统安全存储读出后传入，本类不保存、不记录。
 */
class PhyVlabSessionProtocol(
    private val transport: SchoolHttpTransport,
    private val credentials: suspend () -> Credentials? = { null },
) {
    suspend fun establishSession(): PhyVlabSessionResult = try {
        val loginPage = loadLoginPage()
        if (parseOffMain { loginPage.pageMarks() }.authenticated) {
            PhyVlabSessionResult.Ready(PHYVLAB_HOME_URL)
        } else {
            val account = credentials()?.takeIf { it.isValid }
            if (account == null) PhyVlabSessionResult.AccountRequired else submitLogin(loginPage, account)
        }
    } catch (error: PhyVlabRemoteException) {
        PhyVlabSessionResult.Failed(error.reason, detail = error.reason.name.lowercase())
    }

    /**
     * 设置页“保存”时校验新账号：即使当前 MoodleSession 属于旧账号，也先注销再用新账号登录，
     * 只有登录成功才算校验通过。
     */
    suspend fun signInWith(account: Credentials): PhyVlabSessionResult = try {
        var loginPage = loadLoginPage()
        if (parseOffMain { loginPage.pageMarks() }.authenticated) {
            parseOffMain { parsePhyVlabLogoutUrl(loginPage.bodyText()) }?.let { logout ->
                val response = executeRaw(logout, referer = loginPage.finalUrl)
                if (response.statusCode in 300..399) followHandshake(response)
            }
            loginPage = loadLoginPage()
        }
        submitLogin(loginPage, account)
    } catch (error: PhyVlabRemoteException) {
        PhyVlabSessionResult.Failed(error.reason, detail = error.reason.name.lowercase())
    }

    private suspend fun loadLoginPage(): SchoolHttpResponse {
        val login = executeRaw(PHYVLAB_LOGIN_URL, referer = PHYVLAB_HOME_URL)
        // Moodle/反向代理有时会先把登录入口规范化到带语言或 redirect 参数的地址。
        val page = if (login.statusCode in 300..399) followHandshake(login) else login
        if (page.statusCode !in 200..299) {
            throw PhyVlabRemoteException(PhyVlabRemoteFailure.NETWORK)
        }
        return page
    }

    private suspend fun submitLogin(loginPage: SchoolHttpResponse, account: Credentials): PhyVlabSessionResult {
        if (!account.isValid) return PhyVlabSessionResult.AccountRequired
        val fields = parseOffMain { parsePhyVlabLoginForm(loginPage.bodyText()) }
            ?: return PhyVlabSessionResult.Failed(PhyVlabRemoteFailure.PARSE, detail = "login-form-missing")
        val submitted = try {
            transport.executeWithoutRedirects(
                SchoolHttpRequest(
                    method = SchoolHttpMethod.POST,
                    url = PHYVLAB_LOGIN_URL,
                    headers = mapOf(
                        "Referer" to PHYVLAB_LOGIN_URL,
                        "Origin" to SchoolEndpoints.PHYVLAB_ORIGIN,
                    ),
                    formFields = fields + mapOf(
                        "username" to account.username,
                        "password" to account.password,
                    ),
                ),
            )
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            throw PhyVlabRemoteException(PhyVlabRemoteFailure.NETWORK)
        }
        phyVlabDebug("login POST -> ${submitted.statusCode}")
        val landing = if (submitted.statusCode in 300..399) followHandshake(submitted) else submitted
        val marks = parseOffMain { landing.pageMarks() }
        if (marks.authenticated) return PhyVlabSessionResult.Ready(PHYVLAB_HOME_URL)
        if (marks.phyVlabLoginPage) return PhyVlabSessionResult.CredentialsRejected

        // 登录后的落地页可能只是无导航的中间页；再探测一次“我的课程”确认登录态。
        val probe = executeRaw(PHYVLAB_COURSES_URL, referer = landing.finalUrl)
        val settled = if (probe.statusCode in 300..399) followHandshake(probe) else probe
        val settledMarks = parseOffMain { settled.pageMarks() }
        phyVlabDebug(
            "login landing ${safePhyVlabEndpoint(settled.finalUrl)} " +
                "auth=${settledMarks.authenticated} loginPage=${settledMarks.phyVlabLoginPage}",
        )
        return when {
            settledMarks.authenticated -> PhyVlabSessionResult.Ready(PHYVLAB_HOME_URL)
            settledMarks.phyVlabLoginPage -> PhyVlabSessionResult.CredentialsRejected
            else -> PhyVlabSessionResult.Failed(PhyVlabRemoteFailure.SESSION_EXPIRED, detail = "phyvlab-session-unconfirmed")
        }
    }

    /** 依次处理 Moodle 的 303 跳转（testsession、wantsurl），只在 phyvlab 内跟随。 */
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
        // takeFrom 会把 Location 的查询追加到旧查询后面；先清空，避免 testsession 等参数串到下一跳。
        URLBuilder(Url(base)).apply { parameters.clear() }.takeFrom(location).build()
    }.getOrNull() ?: return null
    if (parsed.protocol.name != "https") return null
    if (parsed.host == PHYVLAB_HOST && (parsed.port == 443 || parsed.port == 0)) {
        return parsed.toString()
    }
    return null
}

/** Moodle 登录表单：保留 logintoken/anchor 等隐藏字段，账号密码由调用方填入。 */
internal fun parsePhyVlabLoginForm(html: String): Map<String, String>? {
    val form = Ksoup.parse(html).selectFirst("form#login") ?: return null
    val action = form.attr("action")
    if (action.isNotBlank() && resolveAllowedTarget(PHYVLAB_LOGIN_URL, action)?.substringBefore('?') != PHYVLAB_LOGIN_URL) {
        return null
    }
    if (form.selectFirst("input[name=username]") == null || form.selectFirst("input[name=password]") == null) return null
    val fields = form.select("input[type=hidden][name]").associate { it.attr("name") to it.attr("value") }
    if (fields["logintoken"].isNullOrBlank()) return null
    return fields
}

/** 已登录页面右上角菜单里的注销链接（带 sesskey），只接受 phyvlab 自己的地址。 */
internal fun parsePhyVlabLogoutUrl(html: String): String? =
    Ksoup.parse(html).select("a[href*='/login/logout.php']")
        .asSequence()
        .mapNotNull { resolveAllowedTarget(PHYVLAB_LOGIN_URL, it.attr("href")) }
        .firstOrNull { Url(it).parameters["sesskey"].orEmpty().isNotBlank() }

/** 握手页面的登录态标记：一次在后台算齐，调试日志与判断共用，不再逐项重复解析整页。 */
private class PhyVlabPageMarks(
    val authenticated: Boolean,
    val phyVlabLoginPage: Boolean,
)

private fun SchoolHttpResponse.pageMarks(): PhyVlabPageMarks {
    val body = bodyText()
    return PhyVlabPageMarks(
        authenticated = isPhyVlabAuthenticatedPage(finalUrl, body),
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

private const val MAX_HOPS = 12

package team.bjtuss.bjtuselfservice.shared.data.homework

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.auth.ParseResult
import team.bjtuss.bjtuselfservice.shared.auth.parseAcademicRedirectUrl
import team.bjtuss.bjtuselfservice.shared.network.*

internal enum class SmartHandshakeFailure { NETWORK, SESSION_EXPIRED, SECURE_CHANNEL_UNAVAILABLE }
internal class SmartHandshakeException(val reason: SmartHandshakeFailure) : Exception(reason.name)

private const val PORTAL_MODULE = "https://mis.bjtu.edu.cn/module/module/104/"
private const val PORTAL_HOME = "https://bksy.bjtu.edu.cn/login_introduce_t.html"
private const val PORTAL_BRIDGE = "https://bksycenter.bjtu.edu.cn/NoMasterJumpPage.aspx?URL=jwcZhjx&FPC=page:jwcZhjx"

/** MIS 界面的 29（module 28）优先，失败后走 11（module 104）→ 智慧课程平台。 */
internal suspend fun SmartPlatformEndpoint.openSmartPlatform(
    allowUnparsedModulePage: Boolean = false,
    execute: suspend (SchoolHttpRequest) -> SchoolHttpResponse,
): SchoolHttpResponse {
    val primaryFailure = try {
        val module = execute(get(SchoolEndpoints.SMART_MODULE_URL, "https://mis.bjtu.edu.cn/home/"))
        if (module.looksLikeSessionExpired()) fail(SmartHandshakeFailure.SESSION_EXPIRED)
        var settled = followSmartHandshakeRedirects(module, SchoolEndpoints.SMART_MODULE_URL, execute = execute)
        if (settled === module && !acceptsHandshakeUrl(settled.finalUrl) && settled.statusCode in 200..299) {
            val redirect = (parseAcademicRedirectUrl(module.bodyText()) as? ParseResult.Success)?.value
            // 当前周查询原本允许 MIS 无跳转的 200，随后独立探测 getTimeList。
            if (redirect == null && allowUnparsedModulePage && module.finalUrl == SchoolEndpoints.SMART_MODULE_URL) return module
            if (redirect == null) fail(SmartHandshakeFailure.SECURE_CHANNEL_UNAVAILABLE)
            if (!acceptsHandshakeUrl(redirect)) fail(SmartHandshakeFailure.SECURE_CHANNEL_UNAVAILABLE)
            settled = followSmartHandshakeRedirects(
                execute(get(redirect, SchoolEndpoints.SMART_MODULE_URL)), SchoolEndpoints.SMART_MODULE_URL,
                execute = execute,
            )
        }
        validateLanding(settled)
        return settled
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (error is SmartHandshakeException && error.reason == SmartHandshakeFailure.SESSION_EXPIRED) throw error
        error
    }
    // 此回退落在既有授权的旧课程 IP，不让 VerifiedHttps 策略暗中降级。
    if (!isLegacyInsecure) throw primaryFailure
    try {
        val portal = followPortal(execute(get(PORTAL_MODULE, "https://mis.bjtu.edu.cn/home/")), execute)
        if (portal.looksLikeSessionExpired()) fail(SmartHandshakeFailure.SESSION_EXPIRED)
        if (portal.statusCode !in 200..299) fail(SmartHandshakeFailure.NETWORK)
        if (portal.finalUrl != PORTAL_HOME) fail(SmartHandshakeFailure.SECURE_CHANNEL_UNAVAILABLE)
        var landing = followPortal(execute(get(PORTAL_BRIDGE, PORTAL_HOME)), execute)
        // 浏览器 qx_kl.jsp 通过 JS 进入 index；只复刻已验证的固定 GET，不执行网页脚本。
        if (landing.statusCode in 200..299 && landing.finalUrl == "$apiOrigin/ve/back/core/main/qx_kl.jsp") {
            landing = followPortal(execute(get(
                "$apiOrigin/ve/back/core/main/index.shtml?method=index&type=qxkt", landing.finalUrl,
            )), execute)
        }
        validateLanding(landing)
        return landing
    } catch (error: CancellationException) {
        throw error
    } catch (error: SmartHandshakeException) {
        throw error
    } catch (_: Exception) {
        fail(SmartHandshakeFailure.NETWORK)
    }
}

private fun SmartPlatformEndpoint.validateLanding(response: SchoolHttpResponse) {
    if (response.statusCode in 300..399) fail(SmartHandshakeFailure.SECURE_CHANNEL_UNAVAILABLE)
    if (response.statusCode !in 200..299) fail(SmartHandshakeFailure.NETWORK)
    if (!acceptsHandshakeUrl(response.finalUrl)) fail(SmartHandshakeFailure.SECURE_CHANNEL_UNAVAILABLE)
    if (response.looksLikeSessionExpired()) fail(SmartHandshakeFailure.SESSION_EXPIRED)
}

private suspend fun SmartPlatformEndpoint.followPortal(
    first: SchoolHttpResponse,
    execute: suspend (SchoolHttpRequest) -> SchoolHttpResponse,
): SchoolHttpResponse {
    var current = first
    repeat(12) {
        if (current.statusCode !in 300..399) return current
        val location = current.header("Location")?.trim().orEmpty()
        if (location.isEmpty() || location.any(Char::isISOControl)) fail(SmartHandshakeFailure.SECURE_CHANNEL_UNAVAILABLE)
        var target = runCatching {
            URLBuilder(Url(current.finalUrl)).apply { parameters.clear() }.takeFrom(location).buildString()
        }.getOrNull() ?: fail(SmartHandshakeFailure.SECURE_CHANNEL_UNAVAILABLE)
        // Chrome 的 HTTPS 升级已实测可用；本科平台 OAuth 绝不发送明文。
        if (target.startsWith("http://bksycenter.bjtu.edu.cn/oauth2.ashx")) {
            target = "https://" + target.removePrefix("http://")
        }
        val parsed = runCatching { Url(target) }.getOrNull() ?: fail(SmartHandshakeFailure.SECURE_CHANNEL_UNAVAILABLE)
        val portalAllowed = parsed.protocol.name == "https" && parsed.port == 443 &&
            parsed.user.isNullOrEmpty() && parsed.password.isNullOrEmpty() && parsed.fragment.isEmpty() && when (parsed.host) {
                "bksycenter.bjtu.edu.cn" -> parsed.encodedPath in setOf("/oauth2.ashx", "/NoMasterJumpPage.aspx")
                "bksy.bjtu.edu.cn" -> parsed.encodedPath == "/login_introduce_t.html"
                "cas.bjtu.edu.cn", "sso.bjtu.edu.cn", "mis.bjtu.edu.cn" -> true
                else -> false
            }
        if (!portalAllowed && !acceptsHandshakeUrl(target)) fail(SmartHandshakeFailure.SECURE_CHANNEL_UNAVAILABLE)
        current = execute(get(target, current.finalUrl.substringBefore('?')))
    }
    fail(SmartHandshakeFailure.SECURE_CHANNEL_UNAVAILABLE)
}

private fun get(url: String, referer: String) = SchoolHttpRequest(
    SchoolHttpMethod.GET, url, headers = mapOf("Referer" to referer),
)
private fun fail(reason: SmartHandshakeFailure): Nothing = throw SmartHandshakeException(reason)

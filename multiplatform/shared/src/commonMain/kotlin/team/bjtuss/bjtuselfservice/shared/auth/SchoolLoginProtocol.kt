package team.bjtuss.bjtuselfservice.shared.auth

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import team.bjtuss.bjtuselfservice.shared.network.SchoolEndpoints

import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.looksLikeSessionExpired
import team.bjtuss.bjtuselfservice.shared.network.parseOffMain
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

private const val MIS_HOME_URL = "https://mis.bjtu.edu.cn/home/"
private const val AA_MODULE_URL = "https://mis.bjtu.edu.cn/module/module/10/"
private const val MIS_LOGOUT_URL = "https://mis.bjtu.edu.cn/auth/slogout/"
private val SSO_AUTHORIZE_PREFIX = "${SchoolEndpoints.SSO_ORIGIN}/o/authorize/"
private val SSO_LOGIN_URL = "${SchoolEndpoints.SSO_ORIGIN}/sso/login"
private val SSO_CAPTCHA_URL = "${SchoolEndpoints.SSO_ORIGIN}/api/captcha/getCaptcha"
private val SSO_LOGOUT_URL = "${SchoolEndpoints.SSO_ORIGIN}/sso/logout"

sealed interface SessionProbeResult {
    data object Active : SessionProbeResult
    data object Missing : SessionProbeResult
}

sealed interface ChallengeResult {
    data class SessionActive(val profile: StudentProfile) : ChallengeResult
    data class Ready(val challenge: CaptchaChallenge) : ChallengeResult
    data class Failed(val reason: LoginFailure) : ChallengeResult
}

sealed interface AuthenticationResult {
    data class Success(val profile: StudentProfile) : AuthenticationResult
    data class Failed(val reason: LoginFailure) : AuthenticationResult
}

class SchoolLoginProtocol(
    private val transport: SchoolHttpTransport,
) : LoginAutomationGateway {
    suspend fun checkSession(): SessionProbeResult {
        val response = transport.execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = MIS_HOME_URL,
                headers = mapOf("Referer" to MIS_HOME_URL),
            ),
        )
        return if (
            response.statusCode in 200..299 &&
                response.finalUrl.matchesEndpoint(MIS_HOME_URL) &&
                // 各模块刷新前都会探测一次；MIS 首页很大，整页判断放后台（真机每次约 30~40ms 主线程卡顿）。
                !parseOffMain { response.looksLikeSessionExpired() }
        ) {
            SessionProbeResult.Active
        } else {
            SessionProbeResult.Missing
        }
    }

    /**
     * 学校在登录失败后可能要求验证码；该要求挂在服务端会话/账号上，新开的授权页本身并不提示。
     * 记住最近一次回显页的要求，下一次取挑战时直接附带验证码图片，避免先空提交一次再失败。
     */
    private var ssoCaptchaRequired = false

    /**
     * MIS 已切换到统一身份认证（sso.bjtu.edu.cn，OAuth 授权码）：访问 MIS 首页未登录时会经
     * `/auth/nsso/` 跳到 SSO 授权页。SSO 还有 TGC 时会直接回到 MIS 首页（SessionActive），
     * 否则落在登录页，解析表单隐藏字段与 SM2 公钥作为本次挑战。
     */
    override suspend fun requestCaptchaChallenge(studentId: String): ChallengeResult {
        val entry = transport.execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = MIS_HOME_URL,
                headers = mapOf("Referer" to MIS_HOME_URL),
            ),
        )
        if (entry.finalUrl.matchesEndpoint(MIS_HOME_URL)) {
            return when (val profile = parseOffMain { parseMisStudentProfile(entry.bodyText(), studentId) }) {
                is ParseResult.Success -> ChallengeResult.SessionActive(profile.value)
                is ParseResult.Failure -> ChallengeResult.Failed(LoginFailure.MALFORMED_RESPONSE)
            }
        }
        if (!entry.finalUrl.startsWith(SSO_AUTHORIZE_PREFIX)) {
            return ChallengeResult.Failed(LoginFailure.MALFORMED_RESPONSE)
        }
        val page = when (val parsed = parseOffMain { parseSsoLoginPage(entry.bodyText()) }) {
            is ParseResult.Success -> parsed.value
            is ParseResult.Failure -> return ChallengeResult.Failed(LoginFailure.MALFORMED_RESPONSE)
        }
        return challengeFrom(entry.finalUrl, page)
    }

    /**
     * 业务请求或物理在线发现会话失效时的恢复入口。SSO 不再区分“探测 MIS”和“重新取登录页”：
     * 从 MIS 首页进入授权流程，SSO 仍有效就直接恢复，否则返回新的登录挑战。
     */
    suspend fun requestFreshCaptchaChallenge(studentId: String): ChallengeResult =
        requestCaptchaChallenge(studentId)

    private suspend fun challengeFrom(pageUrl: String, page: SsoLoginPage): ChallengeResult {
        if (page.error?.requiresCaptcha == true) ssoCaptchaRequired = true
        if (!ssoCaptchaRequired) {
            return ChallengeResult.Ready(
                CaptchaChallenge(
                    loginPageUrl = pageUrl,
                    csrfToken = "",
                    captchaId = "",
                    imageBytes = ByteArray(0),
                    formFields = page.fields,
                    sm2PublicKey = page.sm2PublicKey,
                    autoRecognizable = false,
                ),
            )
        }
        val captcha = transport.execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.POST,
                url = "$SSO_CAPTCHA_URL?rnd=${kotlin.random.Random.nextDouble()}",
                headers = mapOf(
                    "Referer" to pageUrl,
                    "Origin" to SchoolEndpoints.SSO_ORIGIN,
                    "X-Requested-With" to "XMLHttpRequest",
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                ),
                rawBody = ByteArray(0),
                rawBodyContentType = "application/x-www-form-urlencoded; charset=UTF-8",
            ),
        )
        if (captcha.statusCode !in 200..299) return ChallengeResult.Failed(LoginFailure.NETWORK)
        val image = parseOffMain { parseSsoCaptcha(captcha.bodyText()) }
            ?: return ChallengeResult.Failed(LoginFailure.MALFORMED_RESPONSE)
        return ChallengeResult.Ready(
            CaptchaChallenge(
                loginPageUrl = pageUrl,
                csrfToken = "",
                captchaId = image.first,
                imageBytes = image.second,
                formFields = page.fields,
                sm2PublicKey = page.sm2PublicKey,
                autoRecognizable = false,
            ),
        )
    }

    override suspend fun authenticateMis(
        credentials: Credentials,
        challenge: CaptchaChallenge,
        captchaAnswer: String,
    ): AuthenticationResult {
        if (!credentials.isValid) {
            return AuthenticationResult.Failed(LoginFailure.INVALID_CREDENTIALS)
        }
        if (!challenge.loginPageUrl.startsWith(SSO_AUTHORIZE_PREFIX) || challenge.formFields.isEmpty()) {
            return AuthenticationResult.Failed(LoginFailure.MALFORMED_RESPONSE)
        }
        if (challenge.requiresCaptcha && captchaAnswer.isBlank()) {
            return AuthenticationResult.Failed(LoginFailure.CAPTCHA_REJECTED)
        }
        val password = if (challenge.sm2PublicKey.isBlank()) {
            credentials.password
        } else {
            try {
                SsoSm2.encryptPassword(credentials.password, challenge.sm2PublicKey)
            } catch (_: IllegalArgumentException) {
                return AuthenticationResult.Failed(LoginFailure.MALFORMED_RESPONSE)
            }
        }
        val fields = challenge.formFields.toMutableMap().apply {
            put("username", credentials.username)
            put("password", password)
            put("loginType", "username_password")
            put(
                "captcha",
                if (challenge.requiresCaptcha) {
                    buildJsonObject {
                        put("captchaId", challenge.captchaId)
                        put("captchaValue", captchaAnswer.trim())
                    }.toString()
                } else {
                    ""
                },
            )
        }
        // 默认客户端不跟随 POST 的 302；这里手动只在 SSO/MIS 的 HTTPS 地址内跟随授权回调。
        val submitted = transport.executeWithoutRedirects(
            SchoolHttpRequest(
                method = SchoolHttpMethod.POST,
                url = SSO_LOGIN_URL,
                headers = mapOf(
                    "Referer" to challenge.loginPageUrl,
                    "Origin" to SchoolEndpoints.SSO_ORIGIN,
                ),
                formFields = fields,
            ),
        )
        val landing = if (submitted.statusCode in 300..399) {
            val target = submitted.header("Location")?.let { resolveLoginRedirect(SSO_LOGIN_URL, it) }
                ?: return AuthenticationResult.Failed(LoginFailure.MALFORMED_RESPONSE)
            transport.execute(
                SchoolHttpRequest(
                    method = SchoolHttpMethod.GET,
                    url = target,
                    headers = mapOf("Referer" to challenge.loginPageUrl),
                ),
            )
        } else {
            submitted
        }

        if (landing.finalUrl.startsWith(SchoolEndpoints.SSO_ORIGIN)) {
            val error = (parseOffMain { parseSsoLoginPage(landing.bodyText()) } as? ParseResult.Success)?.value?.error
            return AuthenticationResult.Failed(
                when {
                    error == null -> LoginFailure.MALFORMED_RESPONSE
                    error.requiresCaptcha -> {
                        ssoCaptchaRequired = true
                        LoginFailure.CAPTCHA_REJECTED
                    }
                    error.wrongCredentials -> LoginFailure.ACCOUNT_REJECTED
                    else -> LoginFailure.MALFORMED_RESPONSE
                },
            )
        }
        // 授权码回调偶尔因 state 过期再走一遍 nsso；只要 SSO 会话已建立，再取一次首页即可落地。
        val authenticatedHome = if (landing.finalUrl.matchesEndpoint(MIS_HOME_URL)) {
            landing
        } else {
            transport.execute(
                SchoolHttpRequest(
                    method = SchoolHttpMethod.GET,
                    url = MIS_HOME_URL,
                    headers = mapOf("Referer" to MIS_HOME_URL),
                ),
            )
        }
        if (!authenticatedHome.finalUrl.matchesEndpoint(MIS_HOME_URL)) {
            return AuthenticationResult.Failed(LoginFailure.MALFORMED_RESPONSE)
        }
        ssoCaptchaRequired = false
        return when (val profile = parseOffMain { parseMisStudentProfile(authenticatedHome.bodyText(), credentials.username) }) {
            is ParseResult.Success -> AuthenticationResult.Success(profile.value)
            is ParseResult.Failure -> AuthenticationResult.Failed(LoginFailure.MALFORMED_RESPONSE)
        }
    }

    suspend fun linkAcademicSystem(): Boolean {
        val module = transport.execute(SchoolHttpRequest(SchoolHttpMethod.GET, AA_MODULE_URL))
        val redirect = when (val parsed = parseAcademicRedirectUrl(module.bodyText())) {
            is ParseResult.Success -> parsed.value
            is ParseResult.Failure -> return false
        }
        val response = transport.execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = redirect + "?",
                headers = mapOf("Referer" to AA_MODULE_URL),
            ),
        )
        return response.finalUrl.matchesEndpoint(SchoolEndpoints.AA_HOME_URL)
    }

    /**
     * Best-effort single sign-out：先让 MIS 注销本地会话，再请求 SSO 注销 TGC。
     * 两步都不跟随跳转（SSO 注销会 302 到明文 http 地址，不能被带出 HTTPS 白名单）。
     * 无论学校接口是否可用，本地 Cookie 都会清除。
     */
    suspend fun logout(): Boolean {
        val serverCleared = runCatching {
            val mis = transport.executeWithoutRedirects(SchoolHttpRequest(SchoolHttpMethod.GET, MIS_LOGOUT_URL))
            val sso = transport.executeWithoutRedirects(
                SchoolHttpRequest(
                    method = SchoolHttpMethod.GET,
                    url = "$SSO_LOGOUT_URL?next=$MIS_HOME_URL",
                    headers = mapOf("Referer" to MIS_HOME_URL),
                ),
            )
            mis.statusCode in 200..399 && sso.statusCode in 200..399
        }.getOrDefault(false)
        transport.clearSession()
        return serverCleared
    }
}

/** POST 登录后的跳转只允许留在 SSO/MIS 的 HTTPS 443；其它目标视为异常。 */
private fun resolveLoginRedirect(base: String, location: String): String? {
    if (location.any(Char::isISOControl)) return null
    val target = runCatching { URLBuilder(Url(base)).apply { parameters.clear() }.takeFrom(location).build() }.getOrNull() ?: return null
    if (target.protocol.name != "https" || (target.port != 443 && target.port != 0)) return null
    if (target.host != "sso.bjtu.edu.cn" && target.host != "mis.bjtu.edu.cn") return null
    return target.toString()
}

/** `/api/captcha/getCaptcha` 返回 `{data:{captchaId, captchaImg}}`，图片可能是裸 base64 或 data URI。 */
internal fun parseSsoCaptcha(body: String): Pair<String, ByteArray>? {
    val json = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
    val data = json["data"] as? JsonObject ?: return null
    val id = (data["captchaId"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
    val image = (data["captchaImg"] as? JsonPrimitive)?.content?.trim().orEmpty()
        .substringAfter("base64,")
    val bytes = runCatching { Base64.decode(image) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
    return id to bytes
}

private fun String.matchesEndpoint(expected: String): Boolean =
    substringBefore('#')
        .substringBefore('?')
        .trimEnd('/') == expected.trimEnd('/')

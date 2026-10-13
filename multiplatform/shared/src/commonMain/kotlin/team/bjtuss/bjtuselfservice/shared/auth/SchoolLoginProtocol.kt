package team.bjtuss.bjtuselfservice.shared.auth

import team.bjtuss.bjtuselfservice.shared.network.SchoolEndpoints

import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.looksLikeSessionExpired
import team.bjtuss.bjtuselfservice.shared.network.parseOffMain
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

private const val MIS_SSO_URL = "https://mis.bjtu.edu.cn/auth/sso/?next=/"
private const val MIS_HOME_URL = "https://mis.bjtu.edu.cn/home/"
private const val CAS_LOGIN_PREFIX = "https://cas.bjtu.edu.cn/auth/login/?next="
private val CAS_REFRESH_LOGIN_URL =
    "${SchoolEndpoints.CAS_ORIGIN}/auth/login/?next=%2Fauth%2Fsso%2F%3Fnext%3D%2F"
private const val AA_MODULE_URL = "https://mis.bjtu.edu.cn/module/module/10/"
private const val CAS_LOGOUT_URL = "https://cas.bjtu.edu.cn/auth/logout/"

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

    override suspend fun requestCaptchaChallenge(studentId: String): ChallengeResult {
        val sso = transport.execute(SchoolHttpRequest(SchoolHttpMethod.GET, MIS_SSO_URL))
        if (sso.finalUrl.matchesEndpoint(MIS_HOME_URL)) {
            return when (val profile = parseMisStudentProfile(sso.bodyText(), studentId)) {
                is ParseResult.Success -> ChallengeResult.SessionActive(profile.value)
                is ParseResult.Failure -> ChallengeResult.Failed(LoginFailure.MALFORMED_RESPONSE)
            }
        }
        if (!sso.finalUrl.startsWith(CAS_LOGIN_PREFIX)) {
            return ChallengeResult.Failed(LoginFailure.MALFORMED_RESPONSE)
        }
        return loadCaptchaChallenge(
            loginPageUrl = sso.finalUrl,
            referer = MIS_SSO_URL,
        )
    }

    /**
     * 物理在线 Moodle 会话失效时使用的 CAS 恢复入口。
     *
     * [requestCaptchaChallenge] 为避免打断正常登录会先探测 MIS 会话；如果 MIS
     * 仍有效，它会直接返回 SessionActive。物理在线恢复需要真正重新拿到 CAS
     * 登录页，因此这里从 CAS 的 SSO 回调入口重新加载 challenge。
     */
    suspend fun requestFreshCaptchaChallenge(studentId: String): ChallengeResult {
        val loginPage = transport.execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = CAS_REFRESH_LOGIN_URL,
                headers = mapOf("Referer" to MIS_SSO_URL),
            ),
        )
        if (loginPage.finalUrl.matchesEndpoint(MIS_HOME_URL)) {
            return when (val profile = parseMisStudentProfile(loginPage.bodyText(), studentId)) {
                is ParseResult.Success -> ChallengeResult.SessionActive(profile.value)
                is ParseResult.Failure -> ChallengeResult.Failed(LoginFailure.MALFORMED_RESPONSE)
            }
        }
        if (!loginPage.finalUrl.startsWith(CAS_LOGIN_PREFIX)) {
            return ChallengeResult.Failed(LoginFailure.MALFORMED_RESPONSE)
        }
        return loadCaptchaChallenge(
            loginPageUrl = loginPage.finalUrl,
            referer = CAS_REFRESH_LOGIN_URL,
        )
    }

    private suspend fun loadCaptchaChallenge(
        loginPageUrl: String,
        referer: String,
    ): ChallengeResult {
        // The initial SSO request may already have returned the CAS HTML. Fetching
        // the URL again follows the existing login behavior and obtains a fresh challenge.
        val loginPage = transport.execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = loginPageUrl,
                headers = mapOf("Referer" to referer),
            ),
        )
        val form = when (val parsed = parseCasLoginForm(loginPage.bodyText())) {
            is ParseResult.Success -> parsed.value
            is ParseResult.Failure -> return ChallengeResult.Failed(LoginFailure.MALFORMED_RESPONSE)
        }
        val image = transport.execute(
            SchoolHttpRequest(SchoolHttpMethod.GET, "${SchoolEndpoints.CAS_ORIGIN}/image/${form.captchaId}/"),
        )
        if (image.statusCode !in 200..299 || image.body.isEmpty()) {
            return ChallengeResult.Failed(LoginFailure.NETWORK)
        }
        return ChallengeResult.Ready(
            CaptchaChallenge(
                loginPageUrl = loginPageUrl,
                csrfToken = form.csrfToken,
                captchaId = form.captchaId,
                imageBytes = image.body,
            ),
        )
    }

    override suspend fun authenticateMis(
        credentials: Credentials,
        challenge: CaptchaChallenge,
        captchaAnswer: String,
    ): AuthenticationResult {
        if (!credentials.isValid || captchaAnswer.isBlank()) {
            return AuthenticationResult.Failed(LoginFailure.INVALID_CREDENTIALS)
        }
        if (!challenge.loginPageUrl.startsWith(CAS_LOGIN_PREFIX)) {
            return AuthenticationResult.Failed(LoginFailure.MALFORMED_RESPONSE)
        }

        val response = transport.execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.POST,
                url = challenge.loginPageUrl,
                headers = mapOf(
                    "Referer" to challenge.loginPageUrl,
                    "Origin" to SchoolEndpoints.CAS_ORIGIN,
                ),
                formFields = mapOf(
                    "csrfmiddlewaretoken" to challenge.csrfToken,
                    "captcha_0" to challenge.captchaId,
                    "captcha_1" to captchaAnswer,
                    "loginname" to credentials.username,
                    "password" to credentials.password,
                ),
            ),
        )
        val authenticatedHome = if (response.finalUrl.matchesEndpoint(MIS_HOME_URL)) {
            response
        } else {
            transport.execute(
                SchoolHttpRequest(
                    method = SchoolHttpMethod.GET,
                    url = MIS_HOME_URL,
                    headers = mapOf("Referer" to challenge.loginPageUrl),
                ),
            )
        }
        if (!authenticatedHome.finalUrl.matchesEndpoint(MIS_HOME_URL)) {
            return AuthenticationResult.Failed(LoginFailure.CAPTCHA_REJECTED)
        }
        return when (val profile = parseMisStudentProfile(authenticatedHome.bodyText(), credentials.username)) {
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
     * Best-effort single sign-out. The CAS endpoint invalidates the server-side SSO
     * session carried by this transport. Local cookies are always destroyed even when
     * the school endpoint is unavailable.
     */
    suspend fun logout(): Boolean {
        val serverCleared = runCatching {
            val response = transport.execute(
                SchoolHttpRequest(SchoolHttpMethod.GET, CAS_LOGOUT_URL),
            )
            response.statusCode in 200..399 &&
                response.finalUrl.startsWith("https://cas.bjtu.edu.cn/auth/")
        }.getOrDefault(false)
        transport.clearSession()
        return serverCleared
    }
}

private fun String.matchesEndpoint(expected: String): Boolean =
    substringBefore('#')
        .substringBefore('?')
        .trimEnd('/') == expected.trimEnd('/')

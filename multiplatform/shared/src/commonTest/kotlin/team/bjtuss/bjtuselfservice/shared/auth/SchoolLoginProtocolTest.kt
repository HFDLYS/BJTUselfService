package team.bjtuss.bjtuselfservice.shared.auth

import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.coroutines.startCoroutine

internal const val SSO_AUTHORIZE_FIXTURE_URL =
    "https://sso.bjtu.edu.cn/o/authorize/?response_type=code&client_id=fixture&state=1&" +
        "redirect_uri=https://mis.bjtu.edu.cn/auth/ncallback/?redirect_to=/home/"

internal const val MIS_HOME_FIXTURE =
    """<section class="name_right"><h3><a>测试用户，欢迎</a></h3><div class="nr_con"><span>身份：学生</span><span>部门：测试学院</span></div></section>"""

/** 结构取自真实 SSO 登录页（脱敏）；公钥为测试专用 SM2 密钥。 */
internal fun ssoLoginPageFixture(error: String = "null"): String = """
    <html><body>
    <form method="post" id="loginForm" action="/sso/login">
        <input type="text" class="form-control" name="username">
        <input type="password" class="form-control" name="password" autocomplete="off">
        <input class="btn btn-login" type="submit" name="submit" value="登录">
        <input name="loginType" value="username_password"/>
        <input name="flowId" value="flow-fixture"/>
        <input name="client_id" value="fixture"/>
        <input name="redirect_uri" value="https://mis.bjtu.edu.cn/auth/ncallback/?redirect_to=/home/"/>
        <input name="response_type" value="code"/>
        <input name="scope" value=""/>
        <input name="state" value="1"/>
        <input name="captcha" value=""/>
        <input name="delegator" value=""/>
    </form>
    <script>
    var error = $error;
    if (error) {
        config.error = error
    }
    var ssoConfig = {"ucAddress":"https:\/\/uc.example","sm2":{"enabled":true,"publicKey":"BCNfHMe8JNA+F0xYeDk+8jpQX\/u7gIRDbmOvxyzq5cUyaEGp88qjQT78wi2lAzmdPM6OVMEwDiqrRWqeeO+789s="},"challenge":"x"};
    </script>
    </body></html>
""".trimIndent()

class SchoolLoginProtocolTest {
    @Test
    fun challengeAndAuthenticationFollowSsoAuthorizationCodeFlow() = runSuspend {
        val transport = QueueTransport(
            listOf(
                response(SSO_AUTHORIZE_FIXTURE_URL, ssoLoginPageFixture()),
                SchoolHttpResponse(
                    statusCode = 302,
                    finalUrl = "https://sso.bjtu.edu.cn/sso/login",
                    headers = mapOf(
                        "Location" to listOf("https://mis.bjtu.edu.cn/auth/ncallback/?redirect_to=/home/&state=1&code=CODE-x"),
                    ),
                ),
                response("https://mis.bjtu.edu.cn/home/", MIS_HOME_FIXTURE),
            ),
        )
        val protocol = SchoolLoginProtocol(transport)
        val challenge = assertIs<ChallengeResult.Ready>(
            protocol.requestCaptchaChallenge("student"),
        ).challenge
        assertFalse(challenge.requiresCaptcha)
        assertFalse(challenge.autoRecognizable)

        val authenticated = assertIs<AuthenticationResult.Success>(
            protocol.authenticateMis(Credentials("student", "secret"), challenge, ""),
        )

        assertEquals("student", authenticated.profile.studentId)
        assertEquals("https://mis.bjtu.edu.cn/home/", transport.requests.first().url)
        val post = transport.requests[1]
        assertEquals("https://sso.bjtu.edu.cn/sso/login", post.url)
        assertEquals("flow-fixture", post.formFields["flowId"])
        assertEquals("1", post.formFields["state"])
        assertEquals("student", post.formFields["username"])
        assertEquals("username_password", post.formFields["loginType"])
        assertEquals("", post.formFields["captcha"])
        // 密码必须是 SM2 密文（C1 64 + C3 32 + 明文长度），不能明文提交。
        val password = post.formFields.getValue("password")
        assertNotEquals("secret", password)
        assertEquals(64 + 32 + "secret".length, Base64.decode(password).size)
        assertTrue(transport.requests[2].url.startsWith("https://mis.bjtu.edu.cn/auth/ncallback/"))
    }

    @Test
    fun activeMisSessionReturnsProfileWithoutRequestingCaptcha() = runSuspend {
        val transport = QueueTransport(
            listOf(response("https://mis.bjtu.edu.cn/home/", MIS_HOME_FIXTURE)),
        )

        val result = assertIs<ChallengeResult.SessionActive>(
            SchoolLoginProtocol(transport).requestCaptchaChallenge("student"),
        )

        assertEquals("student", result.profile.studentId)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun wrongPasswordIsReportedAsAccountRejected() = runSuspend {
        val transport = QueueTransport(
            listOf(
                response(SSO_AUTHORIZE_FIXTURE_URL, ssoLoginPageFixture()),
                response(
                    "https://sso.bjtu.edu.cn/sso/login",
                    ssoLoginPageFixture("""{"code":40400,"msg":"\u8D26\u53F7\u6216\u5BC6\u7801\u9519\u8BEF","data":null,"success":false}"""),
                ),
            ),
        )
        val protocol = SchoolLoginProtocol(transport)
        val challenge = assertIs<ChallengeResult.Ready>(protocol.requestCaptchaChallenge("student")).challenge

        val failed = assertIs<AuthenticationResult.Failed>(
            protocol.authenticateMis(Credentials("student", "wrong"), challenge, ""),
        )

        assertEquals(LoginFailure.ACCOUNT_REJECTED, failed.reason)
    }

    @Test
    fun captchaDemandIsRememberedAndSubmittedAsJson() = runSuspend {
        val captchaImage = Base64.encode(byteArrayOf(9, 8, 7))
        val transport = QueueTransport(
            listOf(
                response(SSO_AUTHORIZE_FIXTURE_URL, ssoLoginPageFixture()),
                response(
                    "https://sso.bjtu.edu.cn/sso/login",
                    ssoLoginPageFixture("""{"code":41100,"msg":"need captcha","data":{"captchaType":"penggle.image"}}"""),
                ),
                response(SSO_AUTHORIZE_FIXTURE_URL, ssoLoginPageFixture()),
                response(
                    "https://sso.bjtu.edu.cn/api/captcha/getCaptcha",
                    """{"code":20000,"data":{"captchaId":"captcha.code.fixture","captchaImg":"$captchaImage"}}""",
                ),
                response("https://mis.bjtu.edu.cn/home/", MIS_HOME_FIXTURE),
            ),
        )
        val protocol = SchoolLoginProtocol(transport)
        val first = assertIs<ChallengeResult.Ready>(protocol.requestCaptchaChallenge("student")).challenge
        assertEquals(
            LoginFailure.CAPTCHA_REJECTED,
            assertIs<AuthenticationResult.Failed>(protocol.authenticateMis(Credentials("student", "secret"), first, "")).reason,
        )

        val second = assertIs<ChallengeResult.Ready>(protocol.requestCaptchaChallenge("student")).challenge
        assertTrue(second.requiresCaptcha)
        assertEquals("captcha.code.fixture", second.captchaId)
        assertTrue(second.imageBytes.contentEquals(byteArrayOf(9, 8, 7)))

        assertIs<AuthenticationResult.Success>(
            protocol.authenticateMis(Credentials("student", "secret"), second, "AB12C"),
        )
        assertEquals(
            """{"captchaId":"captcha.code.fixture","captchaValue":"AB12C"}""",
            transport.requests.last().formFields["captcha"],
        )
    }

    @Test
    fun loginRedirectOutsideSsoAndMisIsRejected() = runSuspend {
        val transport = QueueTransport(
            listOf(
                response(SSO_AUTHORIZE_FIXTURE_URL, ssoLoginPageFixture()),
                SchoolHttpResponse(
                    statusCode = 302,
                    finalUrl = "https://sso.bjtu.edu.cn/sso/login",
                    headers = mapOf("Location" to listOf("http://evil.example/steal")),
                ),
            ),
        )
        val protocol = SchoolLoginProtocol(transport)
        val challenge = assertIs<ChallengeResult.Ready>(protocol.requestCaptchaChallenge("student")).challenge

        val failed = assertIs<AuthenticationResult.Failed>(
            protocol.authenticateMis(Credentials("student", "secret"), challenge, ""),
        )

        assertEquals(LoginFailure.MALFORMED_RESPONSE, failed.reason)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun unexpectedEntryPageIsMalformed() = runSuspend {
        val transport = QueueTransport(listOf(response("https://cas.bjtu.edu.cn/auth/login/")))

        val failed = assertIs<ChallengeResult.Failed>(SchoolLoginProtocol(transport).requestCaptchaChallenge("student"))

        assertEquals(LoginFailure.MALFORMED_RESPONSE, failed.reason)
    }

    @Test
    fun academicLinkUsesRedirectFormAndLogoutClearsCookies() = runSuspend {
        val transport = QueueTransport(
            listOf(
                response(
                    "https://mis.bjtu.edu.cn/module/module/10/",
                    """<form id="redirect" action="https://aa.bjtu.edu.cn/sso"></form>""",
                ),
                response("https://aa.bjtu.edu.cn/notice/item?source=sso"),
                response("https://mis.bjtu.edu.cn/auth/slogout/", status = 302),
                response("https://sso.bjtu.edu.cn/sso/logout", status = 302),
            ),
        )
        val protocol = SchoolLoginProtocol(transport)

        assertTrue(protocol.linkAcademicSystem())
        assertTrue(protocol.logout())
        assertEquals(1, transport.clearCount)
        assertEquals("https://mis.bjtu.edu.cn/auth/slogout/", transport.requests[2].url)
        assertTrue(transport.requests.last().url.startsWith("https://sso.bjtu.edu.cn/sso/logout"))
    }

    @Test
    fun logoutAlwaysClearsLocalCookiesWhenServerLogoutFails() = runSuspend {
        val transport = QueueTransport(emptyList())
        val protocol = SchoolLoginProtocol(transport)

        assertFalse(protocol.logout())
        assertEquals(1, transport.clearCount)
        assertEquals("https://mis.bjtu.edu.cn/auth/slogout/", transport.requests.single().url)
    }

    private fun response(finalUrl: String, body: String = "", status: Int = 200) = SchoolHttpResponse(
        statusCode = status,
        finalUrl = finalUrl,
        body = body.encodeToByteArray(),
    )
}

private class QueueTransport(
    responses: List<SchoolHttpResponse>,
) : SchoolHttpTransport {
    private val queue = responses.toMutableList()
    val requests = mutableListOf<SchoolHttpRequest>()
    var clearCount = 0

    override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse {
        requests += request
        return queue.removeAt(0)
    }

    override fun clearSession() {
        clearCount++
    }
}

private fun runSuspend(block: suspend () -> Unit) {
    var failure: Throwable? = null
    block.startCoroutine(
        object : kotlin.coroutines.Continuation<Unit> {
            override val context = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                failure = result.exceptionOrNull()
            }
        },
    )
    failure?.let { throw it }
}

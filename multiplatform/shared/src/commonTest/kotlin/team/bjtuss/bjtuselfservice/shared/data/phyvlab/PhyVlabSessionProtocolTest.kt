package team.bjtuss.bjtuselfservice.shared.data.phyvlab

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

class PhyVlabSessionProtocolTest {
    @Test
    fun authenticatedLoginPageIsReadyWithoutCredentials() = runBlocking {
        val transport = ProtocolQueueTransport(page("https://phyvlab.bjtu.edu.cn/login/index.php", authenticatedHtml()))
        val result = PhyVlabSessionProtocol(transport) { error("credentials should not be read") }.establishSession()

        assertIs<PhyVlabSessionResult.Ready>(result)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun missingAccountRequiresIndependentCredentials() = runBlocking {
        val transport = ProtocolQueueTransport(page("https://phyvlab.bjtu.edu.cn/login/index.php", loginPageHtml()))

        val result = PhyVlabSessionProtocol(transport) { null }.establishSession()

        assertEquals(PhyVlabSessionResult.AccountRequired, result)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun configuredAccountPostsMoodleLoginFormAndFollowsTestSession() = runBlocking {
        val transport = ProtocolQueueTransport(
            page("https://phyvlab.bjtu.edu.cn/login/index.php", loginPageHtml()),
            redirect("https://phyvlab.bjtu.edu.cn/login/index.php", "https://phyvlab.bjtu.edu.cn/login/index.php?testsession=7"),
            redirect("https://phyvlab.bjtu.edu.cn/login/index.php?testsession=7", "https://phyvlab.bjtu.edu.cn/"),
            page("https://phyvlab.bjtu.edu.cn/", authenticatedHtml()),
        )

        val result = PhyVlabSessionProtocol(transport) { Credentials("lab-user", "lab-secret") }.establishSession()

        assertIs<PhyVlabSessionResult.Ready>(result)
        val post = transport.requests[1]
        assertEquals(SchoolHttpMethod.POST, post.method)
        assertEquals("https://phyvlab.bjtu.edu.cn/login/index.php", post.url)
        assertEquals("token-fixture", post.formFields["logintoken"])
        assertEquals("", post.formFields["anchor"])
        assertEquals("lab-user", post.formFields["username"])
        assertEquals("lab-secret", post.formFields["password"])
        assertEquals("https://phyvlab.bjtu.edu.cn/", transport.requests.last().url)
    }

    @Test
    fun rejectedAccountLandsBackOnLoginPage() = runBlocking {
        val transport = ProtocolQueueTransport(
            page("https://phyvlab.bjtu.edu.cn/login/index.php", loginPageHtml()),
            redirect("https://phyvlab.bjtu.edu.cn/login/index.php", "https://phyvlab.bjtu.edu.cn/login/index.php"),
            page("https://phyvlab.bjtu.edu.cn/login/index.php", loginPageHtml(error = "登录无效，请重试")),
        )

        val result = PhyVlabSessionProtocol(transport) { Credentials("lab-user", "wrong") }.establishSession()

        assertEquals(PhyVlabSessionResult.CredentialsRejected, result)
    }

    @Test
    fun loginRedirectToAnotherHostStopsHandshake() = runBlocking {
        val transport = ProtocolQueueTransport(
            page("https://phyvlab.bjtu.edu.cn/login/index.php", loginPageHtml()),
            redirect("https://phyvlab.bjtu.edu.cn/login/index.php", "https://cas.bjtu.edu.cn/o/authorize/?client_id=x"),
        )

        val result = PhyVlabSessionProtocol(transport) { Credentials("lab-user", "lab-secret") }.establishSession()

        assertEquals(PhyVlabRemoteFailure.SESSION_EXPIRED, assertIs<PhyVlabSessionResult.Failed>(result).reason)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun signInWithLogsOutExistingMoodleSessionBeforeVerifyingNewAccount() = runBlocking {
        val transport = ProtocolQueueTransport(
            page("https://phyvlab.bjtu.edu.cn/login/index.php", authenticatedHtml()),
            redirect("https://phyvlab.bjtu.edu.cn/login/logout.php?sesskey=abc", "https://phyvlab.bjtu.edu.cn/"),
            page("https://phyvlab.bjtu.edu.cn/", "<html><body>访客</body></html>"),
            page("https://phyvlab.bjtu.edu.cn/login/index.php", loginPageHtml()),
            redirect("https://phyvlab.bjtu.edu.cn/login/index.php", "https://phyvlab.bjtu.edu.cn/"),
            page("https://phyvlab.bjtu.edu.cn/", authenticatedHtml()),
        )

        val result = PhyVlabSessionProtocol(transport).signInWith(Credentials("new-user", "new-secret"))

        assertIs<PhyVlabSessionResult.Ready>(result)
        assertTrue(transport.requests[1].url.startsWith("https://phyvlab.bjtu.edu.cn/login/logout.php"))
        assertEquals("new-user", transport.requests[4].formFields["username"])
    }

    private fun page(url: String, html: String) = SchoolHttpResponse(200, url, body = html.encodeToByteArray())

    private fun redirect(from: String, to: String) = SchoolHttpResponse(303, from, headers = mapOf("Location" to listOf(to)))

    private fun authenticatedHtml(): String = """
        <html><body>
          <a href="/login/logout.php?sesskey=abc">退出登录</a>
          <a href="/my">个人主页</a>
        </body></html>
    """.trimIndent()

    /** 结构取自真实物理在线登录页（脱敏）。 */
    private fun loginPageHtml(error: String? = null): String = """
        <html><body>
          ${error?.let { "<div class=\"alert alert-danger\" role=\"alert\">$it</div>" }.orEmpty()}
          <form class="login-form" action="https://phyvlab.bjtu.edu.cn/login/index.php" method="post" id="login">
            <input id="anchor" type="hidden" name="anchor" value="">
            <input type="hidden" name="logintoken" value="token-fixture">
            <input type="text" name="username" id="username" value="" placeholder="用户名或邮箱">
            <input type="password" name="password" id="password" value="" placeholder="密码">
          </form>
          <form action="https://phyvlab.bjtu.edu.cn/login/index.php" method="post" id="guestlogin">
            <input type="hidden" name="logintoken" value="token-fixture">
            <input type="hidden" name="username" value="guest" />
          </form>
        </body></html>
    """.trimIndent()

    private class ProtocolQueueTransport(vararg responses: SchoolHttpResponse) : SchoolHttpTransport {
        private val queue = responses.toMutableList()
        val requests = mutableListOf<SchoolHttpRequest>()

        override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse = executeWithoutRedirects(request)

        override suspend fun executeWithoutRedirects(request: SchoolHttpRequest): SchoolHttpResponse {
            requests += request
            return queue.removeFirst()
        }

        override fun clearSession() = Unit
    }
}

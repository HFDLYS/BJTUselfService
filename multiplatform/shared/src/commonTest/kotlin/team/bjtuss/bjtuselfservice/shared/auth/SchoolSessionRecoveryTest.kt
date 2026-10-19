package team.bjtuss.bjtuselfservice.shared.auth

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

class SchoolSessionRecoveryTest {
    @Test
    fun successfulRecoveryRelinksAcademicSystem() = runSuspend {
        val transport = QueueTransport(
            response(SSO_AUTHORIZE_FIXTURE_URL, ssoLoginPageFixture()),
            response("https://mis.bjtu.edu.cn/home/", MIS_HOME_FIXTURE),
            response(
                "https://mis.bjtu.edu.cn/module/module/10/",
                "<form id=\"redirect\" action=\"https://aa.bjtu.edu.cn/sso\"></form>",
            ),
            response("https://aa.bjtu.edu.cn/notice/item?source=sso"),
        )
        val recovery = SchoolSessionRecovery(
            protocol = SchoolLoginProtocol(transport),
            // 统一身份认证默认免验证码：恢复过程不应调用识别模型。
            captchaRecognizer = CaptchaRecognizer { error("recognizer should not run") },
            credentialsProvider = { Credentials("student", "secret") },
        )

        assertTrue(recovery.attempt())
        assertEquals(4, transport.requests.size)
        assertEquals("https://sso.bjtu.edu.cn/sso/login", transport.requests[1].url)
        assertTrue(transport.requests[2].url.contains("/module/module/10/"))
    }

    @Test
    fun recoveryDoesNotStartWithoutCredentials() = runSuspend {
        val transport = QueueTransport()
        val recovery = SchoolSessionRecovery(
            protocol = SchoolLoginProtocol(transport),
            captchaRecognizer = CaptchaRecognizer { error("recognizer should not run") },
            credentialsProvider = { null },
        )

        assertEquals(false, recovery.attempt())
        assertEquals(0, transport.requests.size)
    }

    private class QueueTransport(vararg responses: SchoolHttpResponse) : SchoolHttpTransport {
        private val queue = responses.toMutableList()
        val requests = mutableListOf<SchoolHttpRequest>()

        override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse {
            requests += request
            return queue.removeFirst()
        }

        override fun clearSession() = Unit
    }

    private fun response(finalUrl: String, body: String = "") = SchoolHttpResponse(
        statusCode = 200,
        finalUrl = finalUrl,
        body = body.encodeToByteArray(),
    )
}

private fun runSuspend(block: suspend () -> Unit) {
    var failure: Throwable? = null
    block.startCoroutine(
        object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                failure = result.exceptionOrNull()
            }
        },
    )
    failure?.let { throw it }
}

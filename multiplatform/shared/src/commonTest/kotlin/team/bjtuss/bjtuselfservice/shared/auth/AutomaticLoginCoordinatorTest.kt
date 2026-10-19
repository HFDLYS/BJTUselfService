package team.bjtuss.bjtuselfservice.shared.auth

import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AutomaticLoginCoordinatorTest {
    @Test
    fun ssoChallengeWithoutCaptchaSubmitsDirectlyWithoutRecognizer() = runSuspend {
        val gateway = SsoGateway(rejection = null)
        val result = AutomaticLoginCoordinator(gateway, CaptchaRecognizer { error("recognizer should not run") })
            .login(Credentials("student", "secret"))

        assertEquals(1, assertIs<AutomaticLoginResult.Authenticated>(result).attempts)
        assertEquals(listOf(""), gateway.answers)
    }

    @Test
    fun wrongPasswordStopsAfterOneSubmission() = runSuspend {
        val gateway = SsoGateway(rejection = LoginFailure.ACCOUNT_REJECTED)
        val result = assertIs<AutomaticLoginResult.ManualRequired>(
            AutomaticLoginCoordinator(gateway, UnavailableCaptchaRecognizer).login(Credentials("student", "wrong")),
        )

        assertEquals(LoginFailure.ACCOUNT_REJECTED, result.reason)
        assertEquals(1, gateway.answers.size)
    }

    @Test
    fun ssoCaptchaGoesStraightToManualEntry() = runSuspend {
        val gateway = SsoGateway(rejection = null, captcha = true)
        val result = assertIs<AutomaticLoginResult.ManualRequired>(
            AutomaticLoginCoordinator(gateway, CaptchaRecognizer { error("recognizer should not run") })
                .login(Credentials("student", "secret")),
        )

        assertEquals(LoginFailure.CAPTCHA_REJECTED, result.reason)
        assertEquals(true, result.challenge?.requiresCaptcha)
        assertEquals(0, gateway.answers.size)
    }

    @Test
    fun retriesWithFreshChallengesAndSucceedsOnThirdAttempt() = runSuspend {
        val gateway = FakeAutomationGateway(failuresBeforeSuccess = 2)
        val attempts = mutableListOf<Int>()
        val result = AutomaticLoginCoordinator(gateway, SuccessfulRecognizer).login(
            Credentials("student", "secret"),
        ) { attempt, _ -> attempts += attempt }

        assertEquals(listOf(1, 2, 3), attempts)
        assertEquals(listOf(1, 2, 3), gateway.challengeIds)
        assertEquals(3, assertIs<AutomaticLoginResult.Authenticated>(result).attempts)
    }

    @Test
    fun threeFailuresReturnLatestChallengeForManualEntry() = runSuspend {
        val gateway = FakeAutomationGateway(failuresBeforeSuccess = Int.MAX_VALUE)
        val result = assertIs<AutomaticLoginResult.ManualRequired>(
            AutomaticLoginCoordinator(gateway, SuccessfulRecognizer).login(Credentials("student", "secret")),
        )

        assertEquals(3, result.attempts)
        assertEquals("captcha-3", result.challenge?.captchaId)
        assertEquals(LoginFailure.CAPTCHA_REJECTED, result.reason)
    }

    @Test
    fun activeSessionSkipsRecognitionAndAuthentication() = runSuspend {
        val gateway = FakeAutomationGateway(sessionActive = true)
        val result = AutomaticLoginCoordinator(gateway, UnavailableCaptchaRecognizer)
            .login(Credentials("student", "secret"))

        assertIs<AutomaticLoginResult.SessionActive>(result)
        assertEquals(0, gateway.authenticationCount)
    }

    @Test
    fun recognitionFailuresUseFreshChallengesBeforeManualFallback() = runSuspend {
        val gateway = FakeAutomationGateway()
        val recognizer = SequencedRecognizer(failuresBeforeSuccess = Int.MAX_VALUE)
        val result = assertIs<AutomaticLoginResult.ManualRequired>(
            AutomaticLoginCoordinator(gateway, recognizer)
                .login(Credentials("student", "secret")),
        )

        assertEquals(3, result.attempts)
        assertEquals(listOf(1, 2, 3), gateway.challengeIds)
        assertEquals(0, gateway.authenticationCount)
        assertEquals("captcha-3", result.challenge?.captchaId)
    }

    @Test
    fun unavailablePlatformModelStillUsesThreeFreshChallengesBeforeFallback() = runSuspend {
        val gateway = FakeAutomationGateway()
        val result = assertIs<AutomaticLoginResult.ManualRequired>(
            AutomaticLoginCoordinator(gateway, UnavailableCaptchaRecognizer)
                .login(Credentials("student", "secret")),
        )

        assertEquals(3, result.attempts)
        assertEquals(listOf(1, 2, 3), gateway.challengeIds)
        assertEquals(0, gateway.authenticationCount)
    }

    @Test
    fun transientChallengeExceptionsAreRetriedUpToThreeTimes() = runSuspend {
        val gateway = FakeAutomationGateway(challengeExceptionsBeforeSuccess = 2)
        val attempts = mutableListOf<Int>()
        val result = AutomaticLoginCoordinator(gateway, SuccessfulRecognizer).login(
            Credentials("student", "secret"),
        ) { attempt, _ -> attempts += attempt }

        assertEquals(listOf(1, 2, 3), attempts)
        assertIs<AutomaticLoginResult.Authenticated>(result)
        assertEquals(3, gateway.challengeRequestCount)
    }
}

private object SuccessfulRecognizer : CaptchaRecognizer {
    override suspend fun recognize(imageBytes: ByteArray): CaptchaRecognitionResult =
        CaptchaRecognitionResult.Success(CaptchaRecognition("1+2=", "3", 1f))
}

private class SequencedRecognizer(
    private val failuresBeforeSuccess: Int,
) : CaptchaRecognizer {
    private var calls = 0

    override suspend fun recognize(imageBytes: ByteArray): CaptchaRecognitionResult {
        calls++
        return if (calls <= failuresBeforeSuccess) {
            CaptchaRecognitionResult.Failed(CaptchaRecognitionFailure.INVALID_EXPRESSION)
        } else {
            SuccessfulRecognizer.recognize(imageBytes)
        }
    }
}

private class FakeAutomationGateway(
    private val failuresBeforeSuccess: Int = 0,
    private val sessionActive: Boolean = false,
    private val challengeExceptionsBeforeSuccess: Int = 0,
) : LoginAutomationGateway {
    val challengeIds = mutableListOf<Int>()
    var authenticationCount = 0
    var challengeRequestCount = 0

    override suspend fun requestCaptchaChallenge(studentId: String): ChallengeResult {
        challengeRequestCount++
        if (challengeRequestCount <= challengeExceptionsBeforeSuccess) {
            error("temporary network failure")
        }
        if (sessionActive) return ChallengeResult.SessionActive(Profile)
        val id = challengeIds.size + 1
        challengeIds += id
        return ChallengeResult.Ready(
            CaptchaChallenge("https://cas.bjtu.edu.cn/auth/login/?next=/", "csrf", "captcha-$id", byteArrayOf(1)),
        )
    }

    override suspend fun authenticateMis(
        credentials: Credentials,
        challenge: CaptchaChallenge,
        captchaAnswer: String,
    ): AuthenticationResult {
        authenticationCount++
        return if (authenticationCount <= failuresBeforeSuccess) {
            AuthenticationResult.Failed(LoginFailure.CAPTCHA_REJECTED)
        } else {
            AuthenticationResult.Success(Profile)
        }
    }

    private companion object {
        val Profile = StudentProfile("Test", "student", "student", "test")
    }
}

private class SsoGateway(
    private val rejection: LoginFailure?,
    private val captcha: Boolean = false,
) : LoginAutomationGateway {
    val answers = mutableListOf<String>()

    override suspend fun requestCaptchaChallenge(studentId: String): ChallengeResult = ChallengeResult.Ready(
        CaptchaChallenge(
            loginPageUrl = "https://sso.bjtu.edu.cn/o/authorize/?state=1",
            csrfToken = "",
            captchaId = if (captcha) "captcha.code.fixture" else "",
            imageBytes = if (captcha) byteArrayOf(1) else ByteArray(0),
            formFields = mapOf("flowId" to "flow-fixture"),
            autoRecognizable = false,
        ),
    )

    override suspend fun authenticateMis(
        credentials: Credentials,
        challenge: CaptchaChallenge,
        captchaAnswer: String,
    ): AuthenticationResult {
        answers += captchaAnswer
        return rejection?.let(AuthenticationResult::Failed)
            ?: AuthenticationResult.Success(StudentProfile("Test", "student", "student", "test"))
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

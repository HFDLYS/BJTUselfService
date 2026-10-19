package team.bjtuss.bjtuselfservice.shared.auth

const val DEFAULT_AUTO_LOGIN_ATTEMPTS = 3

interface LoginAutomationGateway {
    suspend fun requestCaptchaChallenge(studentId: String): ChallengeResult

    suspend fun authenticateMis(
        credentials: Credentials,
        challenge: CaptchaChallenge,
        captchaAnswer: String,
    ): AuthenticationResult
}

sealed interface AutomaticLoginResult {
    data class SessionActive(val profile: StudentProfile) : AutomaticLoginResult
    data class Authenticated(val profile: StudentProfile, val attempts: Int) : AutomaticLoginResult
    data class ManualRequired(
        val challenge: CaptchaChallenge?,
        val reason: LoginFailure,
        val attempts: Int,
    ) : AutomaticLoginResult
}

class AutomaticLoginCoordinator(
    private val gateway: LoginAutomationGateway,
    private val captchaRecognizer: CaptchaRecognizer,
    private val maximumAttempts: Int = DEFAULT_AUTO_LOGIN_ATTEMPTS,
) {
    init {
        require(maximumAttempts in 1..3)
    }

    suspend fun login(
        credentials: Credentials,
        onAttempt: (Int, Int) -> Unit = { _, _ -> },
    ): AutomaticLoginResult {
        if (!credentials.isValid) {
            return AutomaticLoginResult.ManualRequired(
                challenge = null,
                reason = LoginFailure.INVALID_CREDENTIALS,
                attempts = 0,
            )
        }

        var latestChallenge: CaptchaChallenge? = null
        var latestFailure = LoginFailure.CAPTCHA_RECOGNITION_FAILED
        repeat(maximumAttempts) { index ->
            val attempt = index + 1
            onAttempt(attempt, maximumAttempts)
            val challenge = try {
                gateway.requestCaptchaChallenge(credentials.username)
            } catch (_: Exception) {
                latestFailure = LoginFailure.NETWORK
                return@repeat
            }
            when (challenge) {
                is ChallengeResult.SessionActive -> {
                    return AutomaticLoginResult.SessionActive(challenge.profile)
                }
                is ChallengeResult.Failed -> {
                    latestFailure = challenge.reason
                }
                is ChallengeResult.Ready -> {
                    latestChallenge = challenge.challenge
                    val answer = when {
                        // 统一身份认证默认免验证码：直接提交账号密码。
                        !challenge.challenge.requiresCaptcha -> ""
                        // 学校要求的字母数字验证码不是算式模型能识别的，直接交给用户手动输入。
                        !challenge.challenge.autoRecognizable -> return AutomaticLoginResult.ManualRequired(
                            challenge = challenge.challenge,
                            reason = LoginFailure.CAPTCHA_REJECTED,
                            attempts = attempt,
                        )
                        else -> {
                            val recognition = try {
                                captchaRecognizer.recognize(challenge.challenge.imageBytes)
                            } catch (_: Exception) {
                                latestFailure = LoginFailure.CAPTCHA_RECOGNITION_FAILED
                                return@repeat
                            }
                            when (recognition) {
                                is CaptchaRecognitionResult.Failed -> {
                                    latestFailure = LoginFailure.CAPTCHA_RECOGNITION_FAILED
                                    return@repeat
                                }
                                is CaptchaRecognitionResult.Success -> recognition.value.answer
                            }
                        }
                    }
                    val authentication = try {
                        gateway.authenticateMis(credentials, challenge.challenge, answer)
                    } catch (_: Exception) {
                        latestFailure = LoginFailure.NETWORK
                        return@repeat
                    }
                    when (authentication) {
                        is AuthenticationResult.Success -> {
                            return AutomaticLoginResult.Authenticated(
                                profile = authentication.profile,
                                attempts = attempt,
                            )
                        }
                        is AuthenticationResult.Failed -> {
                            // 密码错误时不再重试：重复提交只会累积失败次数，触发验证码甚至锁定账号。
                            if (authentication.reason == LoginFailure.ACCOUNT_REJECTED) {
                                return AutomaticLoginResult.ManualRequired(
                                    challenge = null,
                                    reason = LoginFailure.ACCOUNT_REJECTED,
                                    attempts = attempt,
                                )
                            }
                            latestFailure = authentication.reason
                        }
                    }
                }
            }
        }
        return AutomaticLoginResult.ManualRequired(
            challenge = latestChallenge,
            reason = latestFailure,
            attempts = maximumAttempts,
        )
    }
}

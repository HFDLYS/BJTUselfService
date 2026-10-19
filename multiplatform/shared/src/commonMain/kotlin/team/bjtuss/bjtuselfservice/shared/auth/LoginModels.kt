package team.bjtuss.bjtuselfservice.shared.auth

class Credentials(
    val username: String,
    val password: String,
) {
    val isValid: Boolean get() = username.isNotBlank() && password.isNotBlank()

    override fun equals(other: Any?): Boolean =
        other is Credentials && username == other.username && password == other.password

    override fun hashCode(): Int = 31 * username.hashCode() + password.hashCode()

    override fun toString(): String = "Credentials(username=<redacted>, password=<redacted>)"
}

data class StudentProfile(
    val name: String,
    val studentId: String,
    val identity: String,
    val department: String,
) {
    override fun toString(): String =
        "StudentProfile(name=<redacted>, studentId=<redacted>, identity=<redacted>, department=<redacted>)"
}

/**
 * 一次登录挑战。统一身份认证（sso.bjtu.edu.cn）默认不要求验证码，此时 [imageBytes] 为空，
 * [formFields] 是登录页 `#loginForm` 的隐藏字段（flowId/state 等），[sm2PublicKey] 用于加密密码。
 * 只有学校在失败后要求验证码时才带图片；该验证码是字母数字，不能交给算式识别模型。
 */
data class CaptchaChallenge(
    val loginPageUrl: String,
    val csrfToken: String,
    val captchaId: String,
    val imageBytes: ByteArray,
    val formFields: Map<String, String> = emptyMap(),
    val sm2PublicKey: String = "",
    val autoRecognizable: Boolean = true,
) {
    val requiresCaptcha: Boolean get() = imageBytes.isNotEmpty()

    override fun equals(other: Any?): Boolean = other is CaptchaChallenge &&
        loginPageUrl == other.loginPageUrl &&
        csrfToken == other.csrfToken &&
        captchaId == other.captchaId &&
        imageBytes.contentEquals(other.imageBytes) &&
        formFields == other.formFields &&
        sm2PublicKey == other.sm2PublicKey &&
        autoRecognizable == other.autoRecognizable

    override fun hashCode(): Int {
        var result = loginPageUrl.hashCode()
        result = 31 * result + csrfToken.hashCode()
        result = 31 * result + captchaId.hashCode()
        result = 31 * result + imageBytes.contentHashCode()
        result = 31 * result + formFields.hashCode()
        result = 31 * result + sm2PublicKey.hashCode()
        result = 31 * result + autoRecognizable.hashCode()
        return result
    }

    override fun toString(): String =
        "CaptchaChallenge(loginPageUrl=${loginPageUrl.substringBefore('?')}, csrfToken=<redacted>, captchaId=<redacted>, " +
            "imageBytes=${imageBytes.size} bytes, formFields=${formFields.keys}, autoRecognizable=$autoRecognizable)"
}

sealed interface LoginState {
    data object SignedOut : LoginState
    data object CheckingSession : LoginState
    data class AwaitingCaptcha(val challenge: CaptchaChallenge) : LoginState
    data object SubmittingCredentials : LoginState
    data class LinkingAcademicSystem(val profile: StudentProfile) : LoginState
    data class SignedIn(val profile: StudentProfile) : LoginState
    data class Failed(val reason: LoginFailure, val canRetry: Boolean) : LoginState
}

enum class LoginFailure {
    INVALID_CREDENTIALS,
    /** 统一身份认证明确回复“账号或密码错误”。 */
    ACCOUNT_REJECTED,
    CAPTCHA_REJECTED,
    CAPTCHA_RECOGNITION_FAILED,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
    NETWORK,
    ACADEMIC_LINK_FAILED,
}

sealed interface LoginEvent {
    data object Start : LoginEvent
    data class ChallengeLoaded(val challenge: CaptchaChallenge) : LoginEvent
    data object SubmitCredentials : LoginEvent
    data class MisAuthenticated(val profile: StudentProfile) : LoginEvent
    data object AcademicLinked : LoginEvent
    data class Failure(val reason: LoginFailure, val canRetry: Boolean) : LoginEvent
    data object Logout : LoginEvent
}

fun reduceLoginState(state: LoginState, event: LoginEvent): LoginState = when (event) {
    LoginEvent.Start -> if (state is LoginState.SignedOut || state is LoginState.Failed) {
        LoginState.CheckingSession
    } else {
        state
    }
    is LoginEvent.ChallengeLoaded -> if (state is LoginState.CheckingSession) {
        LoginState.AwaitingCaptcha(event.challenge)
    } else {
        state
    }
    LoginEvent.SubmitCredentials -> if (state is LoginState.AwaitingCaptcha) {
        LoginState.SubmittingCredentials
    } else {
        state
    }
    is LoginEvent.MisAuthenticated -> if (state is LoginState.SubmittingCredentials) {
        LoginState.LinkingAcademicSystem(event.profile)
    } else {
        state
    }
    LoginEvent.AcademicLinked -> if (state is LoginState.LinkingAcademicSystem) {
        LoginState.SignedIn(state.profile)
    } else {
        state
    }
    is LoginEvent.Failure -> LoginState.Failed(event.reason, event.canRetry)
    LoginEvent.Logout -> LoginState.SignedOut
}

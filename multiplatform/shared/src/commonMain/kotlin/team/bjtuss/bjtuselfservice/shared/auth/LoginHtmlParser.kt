package team.bjtuss.bjtuselfservice.shared.auth

import com.fleeksoft.ksoup.Ksoup

data class CasLoginForm(
    val csrfToken: String,
    val captchaId: String,
)

sealed interface ParseResult<out T> {
    data class Success<T>(val value: T) : ParseResult<T>
    data class Failure(val missingField: String) : ParseResult<Nothing>
}

fun parseCasLoginForm(html: String): ParseResult<CasLoginForm> {
    val document = Ksoup.parse(html)
    val captchaId = document.selectFirst("input#id_captcha_0")?.attr("value").orEmpty()
    if (captchaId.isBlank()) return ParseResult.Failure("captcha_id")
    val csrf = document.selectFirst("input[name=csrfmiddlewaretoken]")?.attr("value").orEmpty()
    if (csrf.isBlank()) return ParseResult.Failure("csrf_token")
    return ParseResult.Success(CasLoginForm(csrfToken = csrf, captchaId = captchaId))
}

fun parseMisStudentProfile(
    html: String,
    studentId: String,
): ParseResult<StudentProfile> {
    val document = Ksoup.parse(html)
    val rawName = document.selectFirst(".name_right > h3 > a")?.text().orEmpty()
    if (rawName.isBlank()) return ParseResult.Failure("name")

    val spans = document.select(".name_right .nr_con span")
    val identity = spans.firstOrNull { it.text().contains("身份") }
        ?.text()?.removePrefix("身份：").orEmpty()
    if (identity.isBlank()) return ParseResult.Failure("identity")
    val department = spans.firstOrNull { it.text().contains("部门") }
        ?.text()?.removePrefix("部门：").orEmpty()
    if (department.isBlank()) return ParseResult.Failure("department")

    return ParseResult.Success(
        StudentProfile(
            name = rawName.substringBefore("，"),
            studentId = studentId,
            identity = identity,
            department = department,
        ),
    )
}

fun parseAcademicRedirectUrl(html: String): ParseResult<String> {
    val action = Ksoup.parse(html).selectFirst("form#redirect")?.attr("action").orEmpty()
    return if (action.isBlank()) ParseResult.Failure("redirect_action") else ParseResult.Success(action)
}

/** 统一身份认证登录页（`/o/authorize/` 或 POST `/sso/login` 失败后的回显页）。 */
data class SsoLoginPage(
    val fields: Map<String, String>,
    val sm2PublicKey: String,
    val error: SsoLoginError?,
) {
    override fun toString(): String =
        "SsoLoginPage(fields=${fields.keys}, sm2=${sm2PublicKey.isNotBlank()}, error=$error)"
}

/** 页面脚本 `var error = {...}` 中的错误；`msg` 是学校给出的中文提示。 */
data class SsoLoginError(
    val code: Int,
    val message: String,
    val captchaType: String?,
) {
    /** 41100/41102 等 411xx 表示需要（或答错）验证码；数据里带验证码类型时同样处理。 */
    val requiresCaptcha: Boolean get() = code in 41100..41199 || !captchaType.isNullOrBlank()

    val wrongCredentials: Boolean get() = code == 40400
}

private val SsoJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
private val SsoErrorRegex = Regex("""var\s+error\s*=\s*(\{.*?\})\s*;\s*\n""")
private val SsoSm2Regex = Regex(""""sm2"\s*:\s*(\{[^{}]*\})""")

fun parseSsoLoginPage(html: String): ParseResult<SsoLoginPage> {
    val document = Ksoup.parse(html)
    val form = document.selectFirst("form#loginForm") ?: return ParseResult.Failure("login_form")
    val fields = linkedMapOf<String, String>()
    form.select("input[name]").forEach { input ->
        val name = input.attr("name")
        if (name.isNotBlank() && input.attr("type") != "checkbox") fields[name] = input.attr("value")
    }
    if (fields["flowId"].isNullOrBlank()) return ParseResult.Failure("flow_id")
    if (!fields.containsKey("username") || !fields.containsKey("password")) return ParseResult.Failure("credential_fields")

    val sm2 = SsoSm2Regex.find(html)?.groupValues?.get(1)?.let { raw ->
        runCatching { SsoJson.parseToJsonElement(raw).jsonObjectOrNull() }.getOrNull()
    }
    val sm2Enabled = sm2?.get("enabled")?.toString() == "true"
    val publicKey = sm2?.get("publicKey")?.stringContent().orEmpty()
    if (sm2Enabled && publicKey.isBlank()) return ParseResult.Failure("sm2_public_key")

    val error = SsoErrorRegex.find(html)?.groupValues?.get(1)?.let { raw ->
        val json = runCatching { SsoJson.parseToJsonElement(raw).jsonObjectOrNull() }.getOrNull()
            ?: return@let null
        val code = json["code"]?.stringContent()?.toIntOrNull() ?: return@let null
        val data = json["data"]?.jsonObjectOrNull()
        SsoLoginError(
            code = code,
            message = json["msg"]?.stringContent().orEmpty(),
            captchaType = data?.get("captchaType")?.stringContent(),
        )
    }
    return ParseResult.Success(
        SsoLoginPage(
            fields = fields,
            sm2PublicKey = if (sm2Enabled) publicKey else "",
            error = error,
        ),
    )
}

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): kotlinx.serialization.json.JsonObject? =
    this as? kotlinx.serialization.json.JsonObject

private fun kotlinx.serialization.json.JsonElement.stringContent(): String? =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.content

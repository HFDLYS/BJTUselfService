package team.bjtuss.bjtuselfservice.shared.network

enum class SchoolHttpMethod {
    GET,
    POST,
}

data class SchoolHttpRequest(
    val method: SchoolHttpMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val formFields: Map<String, String> = emptyMap(),
    val multipartFiles: List<SchoolMultipartFile> = emptyList(),
    /**
     * 可选的原始请求体；用于少数学校接口要求的 JSON POST。
     * 与表单/多段文件体互斥，日志只记录字节数，不记录内容。
     */
    val rawBody: ByteArray? = null,
    val rawBodyContentType: String = "application/octet-stream",
    /**
     * 大文件下载（课件、附件）：放宽整体请求时长，但仍保留无数据的 socket 超时，
     * 断网或卡死时照常失败。普通接口请求保持默认的短超时。
     */
    val largeTransfer: Boolean = false,
) {
    init {
        require(rawBody == null || (formFields.isEmpty() && multipartFiles.isEmpty())) {
            "rawBody cannot be combined with formFields or multipartFiles"
        }
        require(rawBodyContentType.isNotBlank() && '\r' !in rawBodyContentType && '\n' !in rawBodyContentType) {
            "rawBodyContentType must be a single non-empty header value"
        }
    }

    override fun toString(): String =
        "SchoolHttpRequest(method=$method, url=${url.redactedUrl()}, headers=${headers.redactedHeaders()}, " +
            "formFields=${formFields.redactedFormFields()}, multipartFiles=${multipartFiles.redactedMultipartFiles()}, " +
            "rawBody=${rawBody?.let { "<redacted>(${it.size} bytes)" } ?: "none"})"
}

data class SchoolMultipartFile(
    val fieldName: String,
    val fileName: String,
    val contentType: String = "application/octet-stream",
    val bytes: ByteArray,
) {
    init {
        require(fieldName.isNotBlank() && '\r' !in fieldName && '\n' !in fieldName)
        require(fileName.isNotBlank() && '\r' !in fileName && '\n' !in fileName)
        require(contentType.isNotBlank() && '\r' !in contentType && '\n' !in contentType)
    }

    override fun equals(other: Any?): Boolean = other is SchoolMultipartFile &&
        fieldName == other.fieldName &&
        fileName == other.fileName &&
        contentType == other.contentType &&
        bytes.contentEquals(other.bytes)

    override fun hashCode(): Int {
        var result = fieldName.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    override fun toString(): String =
        "SchoolMultipartFile(fieldName=$fieldName, fileName=<redacted>, contentType=$contentType, bytes=${bytes.size})"
}

data class SchoolHttpResponse(
    val statusCode: Int,
    val finalUrl: String,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: ByteArray = byteArrayOf(),
) {
    fun bodyText(): String = body.decodeToString()

    /**
     * 智慧平台个别老接口（如已提交附件列表）以 GBK/GB18030 返回中文文件名，
     * 直接 UTF-8 解码会得到乱码（真实观察为西里尔/拉丁扩展字符错位）。
     * 此方法按 GB18030 解码，仅在已确认该接口编码的调用点使用。
     */
    fun bodyTextGbk(): String = decodeLegacyGb18030OrNull(body) ?: body.decodeToString()

    /** HTTP 头部大小写不敏感读取，取首个值。 */
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()

    override fun toString(): String =
        "SchoolHttpResponse(statusCode=$statusCode, finalUrl=${finalUrl.redactedUrl()}, headers=${headers.keys}, body=${body.size} bytes)"
}

/**
 * Some school endpoints return HTTP 200 with a login page instead of redirecting.
 * Callers must classify that response as a lost session before handing it to a
 * JSON/HTML parser, otherwise the user sees a misleading "response structure changed" error.
 */
fun SchoolHttpResponse.looksLikeSessionExpired(): Boolean {
    if (statusCode == 401 || statusCode == 403) return true
    val url = finalUrl.lowercase()
    if (
        url.contains("/auth/login") ||
        url.contains("/login/") ||
        url.contains("cas.bjtu.edu.cn/auth/")
    ) {
        return true
    }

    // 不再整页 trim()/lowercase() 复制：大页面每次判断都会多分配两份整页字符串，
    // 同步期间逐页调用会推高 GC（iOS 真机可见 stop-the-world 标记卡住主线程）。
    val bodyText = bodyText()
    val start = bodyText.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return false
    if (listOf(
            "会话失效",
            "会话结束",
            "登录失效",
            "登录超时",
            "未登录",
            "请重新登录",
            "session expired",
            "session timeout",
            "not logged in",
        ).any { bodyText.contains(it, ignoreCase = true) }
    ) {
        return true
    }

    val looksLikeHtml = bodyText.startsWith("<!doctype html", start, ignoreCase = true) ||
        bodyText.startsWith("<html", start, ignoreCase = true) ||
        bodyText.contains("<form", ignoreCase = true)
    val hasCredentialField = bodyText.contains("type=\"password\"", ignoreCase = true) ||
        bodyText.contains("type='password'", ignoreCase = true) ||
        bodyText.contains("name=\"password\"", ignoreCase = true) ||
        bodyText.contains("name='password'", ignoreCase = true) ||
        bodyText.contains("id=\"id_captcha", ignoreCase = true) ||
        bodyText.contains("name=\"loginname\"", ignoreCase = true)
    return looksLikeHtml && hasCredentialField
}

interface SchoolHttpTransport {
    /**
     * 会话相关请求（CAS / aa / 智慧平台等）。实现应串行化，保护共享 Cookie jar。
     */
    suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse

    /**
     * 不自动跟随重定向的会话请求，供需要逐跳白名单验证的握手使用。
     * 默认实现回退到 [execute]；Ktor 实现使用共享 Cookie jar 的独立客户端。
     */
    suspend fun executeWithoutRedirects(request: SchoolHttpRequest): SchoolHttpResponse = execute(request)

    /**
     * 不依赖登录会话的公开页请求。
     *
     * 默认回退到 [execute]。[KtorSchoolHttpTransport] 用独立客户端、不占会话请求锁，
     * 避免 bksy 校历等跨域公开页在代理下挂起时把所有 aa 查询堵在队列里
     *（教室占用切周转圈就是这个症状）。
     */
    suspend fun executePublic(request: SchoolHttpRequest): SchoolHttpResponse = execute(request)

    suspend fun sessionCookiesFor(url: String): List<SchoolSessionCookie> = emptyList()
    fun clearSession()
    /** Clear only the independent demo-lab login, without touching CAS or physics labs. */
    fun clearPhysicsDemoSession() {}
}

/** 只用于把当前内存会话桥接给受信任网页容器；字符串化永不包含值。 */
data class SchoolSessionCookie(
    val name: String,
    val value: String,
    val path: String = "/",
    val secure: Boolean = true,
) {
    override fun toString(): String =
        "SchoolSessionCookie(name=$name, value=<redacted>, path=$path, secure=$secure)"
}

class SchoolNetworkException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 平台 GB18030 解码入口。Ktor 的通用 charset 注册表在 Kotlin/Native iOS 上没有
 * GB18030，必须由 iOS Foundation/CoreFoundation 实现；JVM 平台使用 JDK Charset。
 */
internal expect fun decodeLegacyGb18030OrNull(bytes: ByteArray): String?

private val sensitiveFormFieldNames = setOf(
    "password",
    "username",
    "logintoken",
    "captcha_0",
    "captcha_1",
    "csrfmiddlewaretoken",
    "loginname",
    "content",
    "filelist",
    "sesskey",
    "itemid",
    "ctx_id",
    "client_id",
    "repo_id",
)

private fun Map<String, String>.redactedFormFields(): Map<String, String> = mapValues { (name, value) ->
    if (name.lowercase() in sensitiveFormFieldNames || name.contains("tbPassword", ignoreCase = true) || name.contains("tbUserId", ignoreCase = true) || name.startsWith("__")) "<redacted>" else value
}

private fun Map<String, String>.redactedHeaders(): Map<String, String> = mapValues { (name, value) ->
    if (
        name.equals("Cookie", ignoreCase = true) ||
        name.equals("Authorization", ignoreCase = true) ||
        name.equals("sessionid", ignoreCase = true)
    ) {
        "<redacted>"
    } else if (name.equals("Referer", ignoreCase = true)) {
        value.redactedUrl()
    } else {
        value
    }
}

private fun List<SchoolMultipartFile>.redactedMultipartFiles(): List<String> = map { file ->
    "${file.fieldName}:<redacted>(${file.bytes.size} bytes)"
}

private fun String.redactedUrl(): String = if ('?' in this) {
    substringBefore('?') + "?<redacted>"
} else {
    this
}

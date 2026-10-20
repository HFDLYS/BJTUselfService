package team.bjtuss.bjtuselfservice.shared.data.homework

import team.bjtuss.bjtuselfservice.shared.util.jsonEscape

import team.bjtuss.bjtuselfservice.shared.network.SchoolEndpoints

import com.fleeksoft.ksoup.Ksoup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import team.bjtuss.bjtuselfservice.shared.auth.ParseResult
import team.bjtuss.bjtuselfservice.shared.logging.AppLog
import team.bjtuss.bjtuselfservice.shared.auth.parseAcademicRedirectUrl
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkAttachment
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkDetail
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.domain.homework.SubmittedHomeworkAttachment
import team.bjtuss.bjtuselfservice.shared.domain.homework.stableKey
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.SchoolMultipartFile
import team.bjtuss.bjtuselfservice.shared.network.looksLikeSessionExpired
import team.bjtuss.bjtuselfservice.shared.network.parseOffMain

private const val ARTICLE_PATH = "/ve/back/coursePlatform/message.shtml"
private const val SEMESTER_PATH = "/ve/back/rp/common/teachCalendar.shtml"
private const val COURSE_PATH = "/ve/back/coursePlatform/course.shtml"
private const val HOMEWORK_PATH = "/ve/back/coursePlatform/homeWork.shtml"
private const val GRADE_PATH = "/ve/back/course/courseWorkInfo.shtml"
private const val ATTACHMENT_PATH = "/ve/back/coursePlatform/dataSynAction.shtml"
private const val SUBMITTED_ATTACHMENT_PATH = "/ve//downloadZyFj.shtml"

/**
 * 作业附件上传：学生与老师用的是两个不同端点。
 *
 * 2026-09-16 用 Chrome DevTools MCP 直接读取课程平台作业弹层
 * `courseWorkInfo.shtml?method=uploadDiv3` 的前端源码确认：
 * - 学生走 `homeworkUpload.shtml?noteId=<upId>`（缺失 noteId 回 `STATUS=3 缺少作业ID参数`）；
 * - `rpUpload.shtml` 只在老师分支里出现，学生调用会回 `{"STATUS":"2","MSG":"学生角色无权限上传"}`，
 *   这正是移植版此前一直失败的原因。
 */
private const val UPLOAD_PATH = "/ve/back/rp/common/homeworkUpload.shtml"
// 与传输层智慧教学主机闸门（5）一致；真正的主机上限由 HostRequestGate 保证。
private const val MAX_CONCURRENT_HOMEWORK_REQUESTS = 5

enum class HomeworkRemoteFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
    SECURE_CHANNEL_UNAVAILABLE,

    /** 服务端明确回绝了提交/上传（如「学生角色无权限上传」「上传文件类型不支持」）。 */
    SUBMIT_REJECTED,
}

class HomeworkRemoteException(
    val reason: HomeworkRemoteFailure,
    message: String? = null,
) : Exception(message ?: "Unable to refresh homework: ${reason.name}")

/**
 * 服务端回绝文案与内部字段名（`uploadReceipt`/`flag`/`json`/`root`）的区分。
 * 只有真正来自服务端的中文/英文提示才回传给界面，内部标记继续走通用失败文案。
 */
private fun isServerRejectionMessage(message: String): Boolean {
    if (message.isBlank() || message.length < 3) return false
    if (message in setOf("uploadReceipt", "flag", "json", "root")) return false
    // 只允许可打印字符，避免把回执里的控制字符带进界面。
    return message.none { it.code < 0x20 }
}

interface HomeworkRemoteDataSource {
    suspend fun fetchHomework(): List<Homework>
    suspend fun fetchDetail(homework: Homework): HomeworkDetail
    suspend fun fetchSubmittedAttachments(homework: Homework): List<SubmittedHomeworkAttachment>
    suspend fun downloadTeacherAttachment(
        homeworkId: Int,
        attachment: HomeworkAttachment,
    ): HomeworkFileContent
    suspend fun downloadSubmittedAttachment(
        attachment: SubmittedHomeworkAttachment,
    ): HomeworkFileContent
    suspend fun submitHomework(
        homework: Homework,
        content: String,
        files: List<HomeworkFileContent>,
    )
    fun attachmentDownloadUrl(homeworkId: Int, attachmentId: Int): String
}

/**
 * 默认只允许学校 HTTPS 域名。macOS 可在用户明确授权后注入封闭的旧 HTTP 端点；
 * 每次重定向仍重新校验 origin，sessionid 只保存在该实例内存中。
 */
class SchoolHomeworkRemoteDataSource(
    private val transport: SchoolHttpTransport,
    private val requestDelayMillis: Long = 0,
    private val endpoint: SmartPlatformEndpoint = SmartPlatformEndpoint.VerifiedHttps,
) : HomeworkRemoteDataSource {
    private var initialized = false
    private var sessionId: String? = null
    private var courses: List<SmartCourse> = emptyList()

    override suspend fun fetchHomework(): List<Homework> {
        ensureInitialized()
        val listRequests = buildList {
            courses.forEach { course ->
                (0..2).forEach { homeworkType ->
                    add(HomeworkListRequest(course, homeworkType))
                }
            }
        }
        val listSlots = Semaphore(MAX_CONCURRENT_HOMEWORK_REQUESTS)
        val responses = coroutineScope {
            listRequests.map { request ->
                async {
                    listSlots.withPermit {
                        val response = smartGet(
                            path = HOMEWORK_PATH,
                            query = linkedMapOf(
                                "method" to "getHomeWorkList",
                                "cId" to request.course.id.toString(),
                                "subType" to request.homeworkType.toString(),
                                "page" to "1",
                                "pagesize" to "100",
                            ),
                        )
                        when (val parsed = parseHomeworkList(response.bodyText(), request.homeworkType)) {
                            is HomeworkJsonParseResult.Failure -> HomeworkListResponse(
                                homework = emptyList(),
                                malformed = true,
                            )
                            is HomeworkJsonParseResult.Success -> HomeworkListResponse(
                                homework = parsed.value,
                                malformed = false,
                            )
                        }
                    }
                }
            }.awaitAll()
        }
        if (responses.any(HomeworkListResponse::malformed)) malformed()
        val unique = responses.flatMap(HomeworkListResponse::homework).distinctBy(Homework::stableKey)
        val scoreSlots = Semaphore(MAX_CONCURRENT_HOMEWORK_REQUESTS)
        return coroutineScope {
            unique.map { item ->
                async { scoreSlots.withPermit { item.withBestEffortScore() } }
            }.awaitAll()
        }
    }

    override suspend fun fetchDetail(homework: Homework): HomeworkDetail {
        ensureInitialized()
        val teacherId = courses.firstOrNull { it.id == homework.courseId }?.teacherId
            ?: malformed()
        val response = smartGet(
            path = HOMEWORK_PATH,
            query = linkedMapOf(
                "method" to "queryStudentCourseNote",
                "id" to homework.upId.toString(),
                "courseId" to homework.courseId.toString(),
                "teacherId" to teacherId.toString(),
            ),
        )
        return when (val parsed = parseHomeworkDetail(response.bodyText(), homework.content)) {
            is HomeworkJsonParseResult.Failure -> malformed()
            is HomeworkJsonParseResult.Success -> parsed.value
        }
    }

    override suspend fun fetchSubmittedAttachments(
        homework: Homework,
    ): List<SubmittedHomeworkAttachment> {
        ensureInitialized()
        val response = smartGet(
            path = GRADE_PATH,
            query = linkedMapOf(
                "method" to "piGaiDiv",
                "upId" to homework.upId.toString(),
                "id" to homework.idSnId.orEmptyNumber(),
                "score" to homework.score,
                "uLevel" to "1",
                "type" to "1",
                "username" to "null",
                "userId" to homework.userId.toString(),
            ),
        )
        // 已提交附件接口以 GBK 返回中文文件名（真实观察为西里尔/拉丁扩展乱码）；
        // bodyTextGbk 在不支持 GB18030 的平台安全回退 UTF-8。
        return when (val parsed = parseSubmittedHomeworkAttachments(response.bodyTextGbk())) {
            SubmittedHomeworkParseResult.Failure -> malformed()
            is SubmittedHomeworkParseResult.Success -> parsed.attachments
        }
    }

    override suspend fun downloadTeacherAttachment(
        homeworkId: Int,
        attachment: HomeworkAttachment,
    ): HomeworkFileContent {
        ensureInitialized()
        val response = smartGetUrl(attachmentDownloadUrl(homeworkId, attachment.id), largeTransfer = true)
        return response.toFileContent(attachment.fileName)
    }

    override suspend fun downloadSubmittedAttachment(
        attachment: SubmittedHomeworkAttachment,
    ): HomeworkFileContent {
        ensureInitialized()
        val response = smartGet(
            path = SUBMITTED_ATTACHMENT_PATH,
            query = linkedMapOf(
                "path" to attachment.sourcePath,
                "filename" to attachment.fileName,
                "id" to attachment.id,
            ),
            largeTransfer = true,
        )
        return response.toFileContent(attachment.fileName)
    }

    override suspend fun submitHomework(
        homework: Homework,
        content: String,
        files: List<HomeworkFileContent>,
    ) {
        require(files.isNotEmpty()) { "At least one homework file is required" }
        ensureInitialized()
        val receipts = files.map { file ->
            // 上传与提交各允许一次瞬时网络重试。作业弹层本身没有重试路径，
            // 移动网络下偶发一次抖动就会让整次提交失败（2026-09-16 模拟器实证）。
            // 提交是覆盖式的（服务端删旧记录再写新记录），重试不会产生重复提交。
            retryOnceOnNetworkFailure {
                val upload = smartRequest(
                    method = SchoolHttpMethod.POST,
                    path = UPLOAD_PATH,
                    // 学生上传端点把作业 ID 放在 noteId 查询参数里；缺它服务端直接回
                    // `STATUS=3 缺少作业ID参数(noteId)`。
                    query = linkedMapOf("noteId" to homework.upId.toString()),
                    // 网页端 layui/uploadify 的学生上传同样不带 AJAX/Referer 业务头，
                    // 只靠登录 Cookie 与教学平台 sessionid。
                    includeSmartHeaders = false,
                    // sessionid 是旧平台区别于 JSESSIONID 的教学平台会话标识。
                    includeSessionHeader = true,
                    multipartFiles = listOf(
                        SchoolMultipartFile(
                            fieldName = "file",
                            fileName = file.fileName,
                            // 网页端上传不带文件类型；服务端按扩展名校验（实测 application/octet-stream
                            // 常量即可通过）。真实类型只用于让服务端更准确识别。
                            contentType = file.contentType.ifBlank { "application/octet-stream" },
                            bytes = file.bytes,
                        ),
                    ),
                )
                val uploadBody = upload.bodyText()
                when (val parsed = parseHomeworkUploadReceipt(uploadBody)) {
                    is HomeworkJsonParseResult.Failure -> {
                        if (uploadResponseLooksLikeSessionExpired(upload)) {
                            invalidateSmartSession()
                            sessionExpired()
                        }
                        AppLog.d(
                            "Homework",
                            "upload receipt rejected: field=${parsed.field}, " +
                                uploadReceiptShape(upload),
                        )
                        // 服务端明确回绝（文件类型不支持、缺少 noteId、权限不足）时把原文带给用户，
                        // 网络/解析类问题仍按原有分类处理。
                        parsed.field.takeIf(::isServerRejectionMessage)?.let(::submitRejected)
                        malformed()
                    }
                    is HomeworkJsonParseResult.Success -> parsed.value
                }
            }
        }
        retryOnceOnNetworkFailure {
            val submit = smartRequest(
                method = SchoolHttpMethod.POST,
                path = GRADE_PATH,
                query = linkedMapOf("method" to "sendStuHomeWorks"),
                // 与网页端 jQuery 提交保持一致：写请求不附加智慧平台查询接口专用的 AJAX 头，
                // 但保留教学平台 sessionid。
                includeSmartHeaders = false,
                includeSessionHeader = true,
                formFields = linkedMapOf(
                    // 网页端 sendHomeWorks() 先 encodeURIComponent 再交给 jQuery 编码一次；
                    // 服务端按两层解码处理，这里保持同样的预编码。
                    "content" to content.formValuePreEncode(),
                    "groupName" to "",
                    "groupId" to "",
                    "courseId" to homework.courseId.toString(),
                    "contentType" to homework.homeworkType.toString(),
                    "fz" to "0",
                    "jxrl_id" to "",
                    "fileList" to receipts.toUploadFileListJson(),
                    "upId" to homework.upId.toString(),
                    // 服务端把 return_num 当整数解析：网页表单未初始化时提交的 `{}` 会得到
                    // `{"flag":"bad"}`，并连带清掉上一次提交记录；列表页自己的
                    // jiaozuoye(...) 传的是 '0'，实测 0 / 空 / 省略都能正常提交。
                    "return_num" to "0",
                    "isTeacher" to "0",
                ),
            )
            // 服务端回执是 `{"flag":"success"}`；`{"flag":"bad"}` 表示这次提交没有生效，
            // 并且会连带清掉上一次提交记录。只看 HTTP 2xx 会把失败当成功（历史缺陷）。
            when (val parsed = parseHomeworkSubmitReceipt(submit.bodyText())) {
                is HomeworkJsonParseResult.Success -> Unit
                is HomeworkJsonParseResult.Failure -> {
                    if (uploadResponseLooksLikeSessionExpired(submit)) {
                        invalidateSmartSession()
                        sessionExpired()
                    }
                    AppLog.d(
                        "Homework",
                        "submit receipt rejected: field=${parsed.field}, " +
                            uploadReceiptShape(submit),
                    )
                    parsed.field.takeIf(::isServerRejectionMessage)?.let(::submitRejected)
                    malformed()
                }
            }
        }
    }

    /**
     * 只对 [HomeworkRemoteFailure.NETWORK] 重试一次：会话失效、安全通道拒绝和服务端回绝
     * 都已有各自的恢复/提示路径，重试只会拖慢反馈。
     */
    private suspend fun <T> retryOnceOnNetworkFailure(block: suspend () -> T): T = try {
        block()
    } catch (error: HomeworkRemoteException) {
        if (error.reason != HomeworkRemoteFailure.NETWORK) throw error
        AppLog.d("Homework", "submit step failed with NETWORK, retrying once")
        block()
    }

    override fun attachmentDownloadUrl(homeworkId: Int, attachmentId: Int): String = endpoint.apiUrl(
        path = ATTACHMENT_PATH,
        query = linkedMapOf(
            "method" to "downLoadPic",
            "id" to attachmentId.toString(),
            "noteId" to homeworkId.toString(),
        ),
    )

    private suspend fun ensureInitialized() {
        if (initialized) return
        val module = executeWithoutRedirects(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = SchoolEndpoints.SMART_MODULE_URL,
                headers = mapOf("Referer" to "https://mis.bjtu.edu.cn/home/"),
            ),
        )
        if (module.looksLikeSessionExpired()) {
            invalidateSmartSession()
            sessionExpired()
        }
        // 登录态下 module 28 直接以裸 3xx 指向智慧平台明文入口。必须关掉
        // Ktor 自动跟随，否则同协议跳转在白名单检查前就已经发出。
        val settled = endpoint.followSmartHandshakeRedirects(
            first = module,
            referer = SchoolEndpoints.SMART_MODULE_URL,
        ) { request -> executeWithoutRedirects(request) }
        if (settled !== module || settled.statusCode in 300..399) {
            // 走过了至少一跳；最终落地必须是白名单握手地址且 2xx。
            if (settled.statusCode in 300..399) {
                // 握手链停在了未放行的跳转（HTTPS 策略下即明文降级目标），
                // 属于安全拒绝而非网络故障，避免误报为"请检查网络"。
                secureChannelUnavailable()
            }
            if (settled.statusCode !in 200..299) network()
            if (!endpoint.acceptsHandshakeUrl(settled.finalUrl)) secureChannelUnavailable()
        }

        if (!endpoint.acceptsHandshakeUrl(settled.finalUrl) && settled === module) {
            // 兼容旧行为：响应为 200 HTML 表单跳转时从 <form id="redirect"> 解析。
            run {
                val redirect = when (val parsed = parseAcademicRedirectUrl(module.bodyText())) {
                    is ParseResult.Failure -> secureChannelUnavailable()
                    is ParseResult.Success -> parsed.value
                }
                if (!endpoint.acceptsHandshakeUrl(redirect)) secureChannelUnavailable()
                val linked = execute(
                    SchoolHttpRequest(
                        method = SchoolHttpMethod.GET,
                        url = redirect,
                        headers = mapOf("Referer" to SchoolEndpoints.SMART_MODULE_URL),
                    ),
                )
                if (linked.statusCode !in 200..299) network()
                if (!endpoint.acceptsHandshakeUrl(linked.finalUrl)) secureChannelUnavailable()
            }
        }

        // 智慧平台会话经握手最后一跳的 Set-Cookie: JSESSIONID 下发（见 settled 响应）。
        // 但旧版 Android 还会从 getArticleList 取得一个不同的教学平台 sessionId，
        // 后续接口（尤其上传）依赖这个自定义头；不能因为已有 JSESSIONID 就跳过解析。
        val cookieSessionId = transport.sessionCookiesFor(endpoint.apiOrigin)
            .firstOrNull { it.name.equals("JSESSIONID", ignoreCase = true) }
            ?.value

        val article = smartGet(
            path = ARTICLE_PATH,
            query = linkedMapOf("method" to "getArticleList"),
            includeSession = false,
        )
        sessionId = when (val parsed = parseSmartSessionId(article.bodyText())) {
            is HomeworkJsonParseResult.Failure -> cookieSessionId ?: malformed()
            is HomeworkJsonParseResult.Success -> parsed.value
        }

        val semester = smartGet(
            path = SEMESTER_PATH,
            query = linkedMapOf("method" to "queryCurrentXq"),
        )
        val semesterCode = when (val parsed = parseCurrentSemesterCode(semester.bodyText())) {
            is HomeworkJsonParseResult.Failure -> malformed()
            is HomeworkJsonParseResult.Success -> parsed.value
        }
        if (semesterCode.isBlank()) {
            courses = emptyList()
            initialized = true
            return
        }

        val courseResponse = smartGet(
            path = COURSE_PATH,
            query = linkedMapOf(
                "method" to "getCourseList",
                "pagesize" to "100",
                "page" to "1",
                "xqCode" to semesterCode,
            ),
        )
        courses = when (val parsed = parseSmartCourses(courseResponse.bodyText())) {
            is HomeworkJsonParseResult.Failure -> malformed()
            is HomeworkJsonParseResult.Success -> parsed.value
        }
        initialized = true
    }

    private suspend fun Homework.withBestEffortScore(): Homework {
        if (scoreId == 0) return this
        return try {
            val response = smartGet(
                path = GRADE_PATH,
                query = linkedMapOf(
                    "method" to "piGaiDiv",
                    "upId" to upId.toString(),
                    "id" to idSnId.orEmptyNumber(),
                    "uLevel" to "1",
                ),
            )
            val parsedScore = Ksoup.parse(response.bodyText())
                .selectFirst("#oldScore")
                ?.attr("value")
                .orEmpty()
            if (parsedScore.isBlank()) this else copy(score = parsedScore)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            this
        }
    }

    private suspend fun smartGet(
        path: String,
        query: LinkedHashMap<String, String>,
        includeSession: Boolean = true,
        largeTransfer: Boolean = false,
    ): SchoolHttpResponse = smartRequest(
        method = SchoolHttpMethod.GET,
        path = path,
        query = query,
        includeSession = includeSession,
        largeTransfer = largeTransfer,
    )

    private suspend fun smartGetUrl(url: String, largeTransfer: Boolean = false): SchoolHttpResponse {
        if (!endpoint.acceptsApiUrl(url)) secureChannelUnavailable()
        return smartRequest(
            method = SchoolHttpMethod.GET,
            path = url.removePrefix(endpoint.apiOrigin).substringBefore('?'),
            query = url.queryParameters(),
            largeTransfer = largeTransfer,
        )
    }

    private suspend fun smartRequest(
        method: SchoolHttpMethod,
        path: String,
        query: LinkedHashMap<String, String>,
        formFields: LinkedHashMap<String, String> = linkedMapOf(),
        multipartFiles: List<SchoolMultipartFile> = emptyList(),
        includeSession: Boolean = true,
        includeSmartHeaders: Boolean = true,
        includeSessionHeader: Boolean = includeSmartHeaders,
        largeTransfer: Boolean = false,
    ): SchoolHttpResponse {
        val headers = if (includeSmartHeaders) {
            linkedMapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                "Referer" to endpoint.apiOrigin,
                "X-Requested-With" to "XMLHttpRequest",
            ).also {
                if (includeSession) {
                    sessionId?.let { session -> it["sessionid"] = session }
                }
            }
        } else {
            linkedMapOf()
        }
        if (!includeSmartHeaders && includeSessionHeader && includeSession) {
            sessionId?.let { session -> headers["sessionid"] = session }
        }
        val response = executeWithoutRedirects(
            SchoolHttpRequest(
                method = method,
                url = endpoint.apiUrl(path, query),
                headers = headers,
                formFields = formFields,
                multipartFiles = multipartFiles,
                // 附件上传同样受整体时长影响。
                largeTransfer = largeTransfer || multipartFiles.isNotEmpty(),
            ),
        )
        if (response.statusCode in 300..399) {
            // 自定义 sessionid 头不是 Authorization，Ktor 跨域跟随不会剥掉。
            // 3xx 必须在发出第二跳之前拒绝，不能等 finalUrl 再检查。
            invalidateSmartSession()
            sessionExpired()
        }
        if (
            !endpoint.isLegacyInsecure &&
            path == ARTICLE_PATH &&
            response.statusCode == 404 &&
            endpoint.acceptsApiUrl(response.finalUrl)
        ) {
            secureChannelUnavailable()
        }
        if (response.statusCode !in 200..299) network()
        if (parseOffMain { response.looksLikeSessionExpired() }) {
            invalidateSmartSession()
            sessionExpired()
        }
        if (!endpoint.acceptsApiUrl(response.finalUrl)) {
            invalidateSmartSession()
            sessionExpired()
        }
        return response
    }

    private fun invalidateSmartSession() {
        initialized = false
        sessionId = null
        courses = emptyList()
    }

    private suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse = try {
        if (requestDelayMillis > 0) delay(requestDelayMillis)
        transport.execute(request)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        network()
    }

    private suspend fun executeWithoutRedirects(request: SchoolHttpRequest): SchoolHttpResponse = try {
        if (requestDelayMillis > 0) delay(requestDelayMillis)
        transport.executeWithoutRedirects(request)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        network()
    }
}

private data class HomeworkListRequest(
    val course: SmartCourse,
    val homeworkType: Int,
)

private data class HomeworkListResponse(
    val homework: List<Homework>,
    val malformed: Boolean,
)

private fun String.queryParameters(): LinkedHashMap<String, String> {
    val query = substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return linkedMapOf()
    return query.split('&').mapNotNull { part ->
        val name = part.substringBefore('=', missingDelimiterValue = "")
        val value = part.substringAfter('=', missingDelimiterValue = "")
        if (name.isEmpty()) null else name.urlDecode() to value.urlDecode()
    }.toMap(linkedMapOf())
}

private fun String.formValuePreEncode(): String = buildString {
    encodeToByteArray().forEach { byte ->
        val value = byte.toInt() and 0xff
        val isFormSafe = value in 'A'.code..'Z'.code ||
            value in 'a'.code..'z'.code ||
            value in '0'.code..'9'.code ||
            value == '-'.code || value == '_'.code || value == '.'.code || value == '*'.code
        when {
            isFormSafe -> append(value.toChar())
            value == ' '.code -> append('+')
            else -> {
                append('%')
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
    }
}

private fun String.urlDecode(): String {
    val result = mutableListOf<Byte>()
    var cursor = 0
    while (cursor < length) {
        if (this[cursor] == '%' && cursor + 2 < length) {
            val high = this[cursor + 1].hexValueOrNull()
            val low = this[cursor + 2].hexValueOrNull()
            if (high != null && low != null) {
                result += ((high shl 4) or low).toByte()
                cursor += 3
                continue
            }
        }
        result += this[cursor].code.toByte()
        cursor++
    }
    return result.toByteArray().decodeToString()
}

private fun Char.hexValueOrNull(): Int? = when (this) {
    in '0'..'9' -> code - '0'.code
    in 'a'..'f' -> code - 'a'.code + 10
    in 'A'..'F' -> code - 'A'.code + 10
    else -> null
}

private fun SchoolHttpResponse.toFileContent(suggestedName: String): HomeworkFileContent {
    if (body.isEmpty()) malformed()
    val contentType = headers.entries.firstOrNull { (name, _) ->
        name.equals("Content-Type", ignoreCase = true)
    }?.value?.firstOrNull()?.substringBefore(';')?.trim().orEmpty()
    // 存储路径也没有扩展名时，按服务端声明的类型补上，避免系统保存成无法打开的无后缀文件。
    val fileName = if ('.' in suggestedName) suggestedName else
        mimeExtensions[contentType.lowercase()]?.let { "$suggestedName.$it" } ?: suggestedName
    return HomeworkFileContent(
        fileName = fileName,
        contentType = contentType.ifBlank { "application/octet-stream" },
        bytes = body,
    )
}

private val mimeExtensions = mapOf(
    "application/pdf" to "pdf",
    "application/zip" to "zip",
    "application/x-zip-compressed" to "zip",
    "application/x-rar-compressed" to "rar",
    "application/vnd.rar" to "rar",
    "application/x-7z-compressed" to "7z",
    "application/msword" to "doc",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
    "application/vnd.ms-excel" to "xls",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
    "application/vnd.ms-powerpoint" to "ppt",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
    "text/plain" to "txt",
    "image/png" to "png",
    "image/jpeg" to "jpg",
)

/** 只记录上传回执的结构，避免把文件名、路径、正文或会话值写入日志。 */
private fun uploadReceiptShape(response: SchoolHttpResponse): String {
    val body = response.bodyText().trim()
    val root = parseStrictJsonObject(body)
    val keys = root?.keys
        ?.sorted()
        ?.joinToString(",")
        ?: "non-json"
    val status = root?.string("STATUS").orEmpty().safeLogValue()
    val flag = root?.string("flag").orEmpty().safeLogValue()
    val message = (root?.string("MSG") ?: root?.string("msg") ?: root?.string("MESSAGE"))
        .orEmpty()
        .safeLogValue()
    val contentType = response.header("Content-Type")
        ?.substringBefore(';')
        ?.trim()
        ?.take(80)
        .orEmpty()
        .ifBlank { "unknown" }
    return "status=${response.statusCode},bytes=${response.body.size},contentType=$contentType," +
        "keys=$keys,statusValue=$status,flag=$flag,message=$message"
}

private fun String.safeLogValue(): String = replace(Regex("[\\r\\n\\t]"), " ").take(80)

/** 老接口把会话失效混成 HTTP 200 的 STATUS/MSG/flag 正文或登录 HTML，不能按普通 JSON 缺字段处理。 */
private fun uploadResponseLooksLikeSessionExpired(response: SchoolHttpResponse): Boolean {
    val text = response.bodyText()
    val normalized = (text + "\n" + response.bodyTextGbk() + "\n" + response.finalUrl).lowercase()
    if (listOf(
            "会话结束",
            "会话失效",
            "未登录",
            "登录失效",
            "登录超时",
            "重新登录",
            "请退出系统",
            "session expired",
            "session timeout",
            "not logged",
        ).any(normalized::contains)
    ) {
        return true
    }
    return false
}

private fun List<HomeworkUploadReceipt>.toUploadFileListJson(): String = joinToString(
    prefix = "[",
    postfix = "]",
) { receipt ->
    "{" +
        "\"fileNameNoExt\":\"${receipt.fileNameNoExt.jsonEscape()}\"," +
        "\"fileExtName\":\"${receipt.fileExtName.jsonEscape()}\"," +
        "\"fileSize\":\"${receipt.fileSize.jsonEscape()}\"," +
        "\"visitName\":\"${receipt.visitName.jsonEscape()}\"," +
        "\"pid\":\"\",\"ftype\":\"insert\"}"
}

private fun Int?.orEmptyNumber(): String = this?.toString().orEmpty()

private fun network(): Nothing = throw HomeworkRemoteException(HomeworkRemoteFailure.NETWORK)
private fun sessionExpired(): Nothing = throw HomeworkRemoteException(HomeworkRemoteFailure.SESSION_EXPIRED)
private fun malformed(): Nothing = throw HomeworkRemoteException(HomeworkRemoteFailure.MALFORMED_RESPONSE)
private fun submitRejected(message: String): Nothing =
    throw HomeworkRemoteException(HomeworkRemoteFailure.SUBMIT_REJECTED, message.safeLogValue())
private fun secureChannelUnavailable(): Nothing =
    throw HomeworkRemoteException(HomeworkRemoteFailure.SECURE_CHANNEL_UNAVAILABLE)

private const val HEX = "0123456789ABCDEF"

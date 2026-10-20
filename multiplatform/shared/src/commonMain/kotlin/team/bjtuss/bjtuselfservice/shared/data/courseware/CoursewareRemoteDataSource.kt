package team.bjtuss.bjtuselfservice.shared.data.courseware

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import team.bjtuss.bjtuselfservice.shared.data.homework.HomeworkJsonParseResult
import team.bjtuss.bjtuselfservice.shared.data.homework.SmartCourse
import team.bjtuss.bjtuselfservice.shared.data.homework.SmartPlatformEndpoint
import team.bjtuss.bjtuselfservice.shared.data.homework.openSmartPlatform
import team.bjtuss.bjtuselfservice.shared.data.homework.SmartHandshakeException
import team.bjtuss.bjtuselfservice.shared.data.homework.SmartHandshakeFailure
import team.bjtuss.bjtuselfservice.shared.data.homework.parseCurrentSemesterCode
import team.bjtuss.bjtuselfservice.shared.data.homework.parseSmartCourses
import team.bjtuss.bjtuselfservice.shared.data.homework.parseSmartSessionId
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareCourse
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNodeKind
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareSnapshot
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.looksLikeSessionExpired

private const val ARTICLE_PATH = "/ve/back/coursePlatform/message.shtml"
private const val SEMESTER_PATH = "/ve/back/rp/common/teachCalendar.shtml"
private const val COURSE_PATH = "/ve/back/coursePlatform/course.shtml"
private const val RESOURCE_PATH = "/ve/back/coursePlatform/courseResource.shtml"
private const val DOWNLOAD_TICKET_PATH = "/ve/back/resourceSpace.shtml"
private const val COURSE_PLATFORM_PATH = "/ve/back/coursePlatform/coursePlatform.shtml"

enum class CoursewareRemoteFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
    SECURE_CHANNEL_UNAVAILABLE,
}

class CoursewareRemoteException(
    val reason: CoursewareRemoteFailure,
) : Exception("Unable to refresh courseware: ${reason.name}")

interface CoursewareRemoteDataSource {
    suspend fun fetchSnapshot(): CoursewareSnapshot
    suspend fun fetchChildren(course: CoursewareCourse, parentId: Int): List<CoursewareNode> =
        throw CoursewareRemoteException(CoursewareRemoteFailure.MALFORMED_RESPONSE)
    suspend fun downloadResource(node: CoursewareNode): HomeworkFileContent
    suspend fun downloadTeachingCalendar(course: CoursewareCourse): HomeworkFileContent
}

class SchoolCoursewareRemoteDataSource(
    private val transport: SchoolHttpTransport,
    private val requestDelayMillis: Long = 0,
    private val endpoint: SmartPlatformEndpoint = SmartPlatformEndpoint.VerifiedHttps,
) : CoursewareRemoteDataSource {
    // 并发预加载各课顶层时，多个协程会同时 ensureInitialized；必须串行握手，否则会话互相踩坏。
    private val initMutex = Mutex()
    private var initialized = false
    private var sessionId: String? = null
    private var courses: List<SmartCourse> = emptyList()

    override suspend fun fetchSnapshot(): CoursewareSnapshot {
        ensureInitialized()
        val seenCourseIds = mutableSetOf<Int>()
        courses.forEach { course ->
            if (!seenCourseIds.add(course.id)) malformed()
            if (
                course.courseNumber.isBlank() ||
                course.groupId.isBlank() ||
                course.semesterCode.isBlank()
            ) {
                malformed()
            }
        }
        val snapshotCourses = courses.map { course ->
            CoursewareCourse(
                id = course.id,
                name = course.name,
                courseNumber = course.courseNumber,
                groupId = course.groupId,
                semesterCode = course.semesterCode,
                teacherId = course.teacherId,
                children = emptyList(),
                childrenLoaded = false,
            )
        }
        return CoursewareSnapshot(snapshotCourses)
    }

    override suspend fun fetchChildren(
        course: CoursewareCourse,
        parentId: Int,
    ): List<CoursewareNode> {
        ensureInitialized()
        if (courses.none { it.id == course.id }) malformed()
        val response = smartGet(
            path = RESOURCE_PATH,
            query = linkedMapOf(
                "method" to "stuQueryUploadResourceForCourseList",
                "courseId" to course.courseNumber,
                "cId" to course.courseNumber,
                "xkhId" to course.groupId,
                "xqCode" to course.semesterCode,
                "docType" to "1",
                "up_id" to parentId.toString(),
                "searchName" to "",
            ),
        )
        val parsedNodes = when (val parsed = parseCoursewareChildren(response.bodyText(), course.id)) {
            is CoursewareJsonParseResult.Failure -> malformed()
            is CoursewareJsonParseResult.Success -> parsed.value
        }
        val seenKeys = mutableSetOf<String>()
        return parsedNodes.map { node ->
            if (!seenKeys.add(node.stableKey)) malformed()
            if (node.isFolder) node.copy(children = emptyList(), childrenLoaded = false) else node
        }
    }

    override suspend fun downloadResource(node: CoursewareNode): HomeworkFileContent {
        require(node.kind == CoursewareNodeKind.RESOURCE && node.rpId.isNotBlank())
        ensureInitialized()
        val ticketResponse = smartRequest(
            method = SchoolHttpMethod.POST,
            path = DOWNLOAD_TICKET_PATH,
            query = linkedMapOf(
                "method" to "rpinfoDownloadUrl",
                "rpId" to node.rpId,
            ),
        )
        val ticket = when (val parsed = parseCoursewareDownloadTicket(ticketResponse.bodyText())) {
            is CoursewareJsonParseResult.Failure -> {
                // 会话过期时智慧教学接口有时仍返回 200，但正文已经是登录页；
                // 若只按 JSON 解析失败处理，界面会误报“无效的文件信息”。
                if (ticketResponse.looksLikeHtmlPage()) {
                    invalidateSession()
                    sessionExpired()
                }
                malformed()
            }
            is CoursewareJsonParseResult.Success -> parsed.value
        }
        if (!endpoint.acceptsResourceUrl(ticket.url)) secureChannelUnavailable()
        val response = execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = ticket.url,
                headers = buildHeaders(includeSession = false),
                largeTransfer = true,
            ),
        )
        if (response.statusCode !in 200..299) network()
        if (!endpoint.acceptsResourceUrl(response.finalUrl)) secureChannelUnavailable()
        if (response.body.isEmpty()) malformed()
        return HomeworkFileContent(
            fileName = node.suggestedFileName(),
            contentType = response.contentTypeOrDefault(),
            bytes = response.body,
        )
    }

    override suspend fun downloadTeachingCalendar(course: CoursewareCourse): HomeworkFileContent {
        ensureInitialized()
        val platform = smartGet(
            path = COURSE_PLATFORM_PATH,
            query = linkedMapOf(
                "method" to "toCoursePlatform",
                "courseId" to course.courseNumber,
                "dataSource" to "1",
                "cId" to course.id.toString(),
                "xkhId" to course.groupId,
                "xqCode" to course.semesterCode,
            ),
        )
        when (parseCoursewareTeacherId(platform.bodyText())) {
            is CoursewareHtmlParseResult.Failure -> malformed()
            is CoursewareHtmlParseResult.Success -> Unit
        }
        val calendarPage = smartGet(
            path = COURSE_PLATFORM_PATH,
            query = linkedMapOf(),
        )
        val rawUrl = when (val parsed = parseTeachingCalendarFrameUrl(calendarPage.bodyText())) {
            is CoursewareHtmlParseResult.Failure -> malformed()
            is CoursewareHtmlParseResult.Success -> parsed.value
        }
        val calendarUrl = endpoint.resolveTeachingCalendarUrl(rawUrl) ?: secureChannelUnavailable()
        val response = execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = calendarUrl,
                headers = buildHeaders(includeSession = false),
            ),
        )
        if (response.statusCode !in 200..299) network()
        if (!endpoint.acceptsCalendarUrl(response.finalUrl)) secureChannelUnavailable()
        if (response.body.isEmpty()) malformed()
        val contentType = response.contentTypeOrDefault()
        if (!contentType.equals("application/pdf", ignoreCase = true) && !response.body.isPdfBytes()) {
            malformed()
        }
        return HomeworkFileContent(
            fileName = "${course.name}_教学日历.pdf",
            contentType = "application/pdf",
            bytes = response.body,
        )
    }

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            ensureInitializedLocked()
        }
    }

    private suspend fun ensureInitializedLocked() {
        try {
            endpoint.openSmartPlatform { request -> executeWithoutRedirects(request) }
        } catch (error: SmartHandshakeException) {
            if (error.reason == SmartHandshakeFailure.SESSION_EXPIRED) invalidateSession()
            throw CoursewareRemoteException(CoursewareRemoteFailure.valueOf(error.reason.name))
        }

        // 与作业一致：会话经 Set-Cookie: JSESSIONID 下发，优先读 Cookie，
        // 旧版 article JSON 解析仅作回退。
        sessionId = transport.sessionCookiesFor(endpoint.apiOrigin)
            .firstOrNull { it.name.equals("JSESSIONID", ignoreCase = true) }
            ?.value

        val article = smartGet(
            path = ARTICLE_PATH,
            query = linkedMapOf("method" to "getArticleList"),
            includeSession = sessionId != null,
        )
        if (sessionId == null) {
            sessionId = when (val parsed = parseSmartSessionId(article.bodyText())) {
                is HomeworkJsonParseResult.Failure -> malformed()
                is HomeworkJsonParseResult.Success -> parsed.value
            }
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

    private suspend fun smartGet(
        path: String,
        query: LinkedHashMap<String, String>,
        includeSession: Boolean = true,
    ): SchoolHttpResponse = smartRequest(
        method = SchoolHttpMethod.GET,
        path = path,
        query = query,
        includeSession = includeSession,
    )

    private suspend fun smartRequest(
        method: SchoolHttpMethod,
        path: String,
        query: LinkedHashMap<String, String>,
        includeSession: Boolean = true,
    ): SchoolHttpResponse {
        val response = executeWithoutRedirects(
            SchoolHttpRequest(
                method = method,
                url = endpoint.apiUrl(path, query),
                headers = buildHeaders(includeSession),
            ),
        )
        if (response.statusCode in 300..399) {
            invalidateSession()
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
        if (response.statusCode == 401 || response.statusCode == 403) {
            invalidateSession()
            sessionExpired()
        }
        if (response.statusCode !in 200..299) network()
        if (response.looksLikeSessionExpired()) {
            invalidateSession()
            sessionExpired()
        }
        if (!endpoint.acceptsApiUrl(response.finalUrl)) {
            invalidateSession()
            sessionExpired()
        }
        return response
    }

    private fun invalidateSession() {
        initialized = false
        sessionId = null
        courses = emptyList()
    }

    private fun buildHeaders(includeSession: Boolean = true): Map<String, String> = linkedMapOf(
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
        "Referer" to endpoint.apiOrigin,
        "X-Requested-With" to "XMLHttpRequest",
    ).apply {
        if (includeSession) sessionId?.let { put("sessionid", it) }
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

private fun ByteArray.isPdfBytes(): Boolean = size >= 5 &&
    this[0] == '%'.code.toByte() &&
    this[1] == 'P'.code.toByte() &&
    this[2] == 'D'.code.toByte() &&
    this[3] == 'F'.code.toByte() &&
    this[4] == '-'.code.toByte()

private fun CoursewareNode.suggestedFileName(): String {
    val clean = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "resource" }
    val normalizedExtension = extension.trim().trimStart('.')
    return if (normalizedExtension.isBlank() || clean.substringAfterLast('.', "").isNotBlank()) {
        clean
    } else {
        "$clean.$normalizedExtension"
    }
}

private fun SchoolHttpResponse.contentTypeOrDefault(): String = headers.entries
    .firstOrNull { (name, _) -> name.equals("Content-Type", ignoreCase = true) }
    ?.value?.firstOrNull()?.substringBefore(';')?.trim()
    ?.takeIf(String::isNotBlank)
    ?: "application/octet-stream"

private fun SchoolHttpResponse.looksLikeHtmlPage(): Boolean {
    val contentType = contentTypeOrDefault()
    if (contentType.equals("text/html", ignoreCase = true)) return true
    val prefix = bodyText().trimStart().take(160)
    return prefix.startsWith("<!doctype html", ignoreCase = true) ||
        prefix.startsWith("<html", ignoreCase = true) ||
        prefix.startsWith("<form", ignoreCase = true)
}

private fun network(): Nothing = throw CoursewareRemoteException(CoursewareRemoteFailure.NETWORK)
private fun sessionExpired(): Nothing = throw CoursewareRemoteException(CoursewareRemoteFailure.SESSION_EXPIRED)
private fun malformed(): Nothing = throw CoursewareRemoteException(CoursewareRemoteFailure.MALFORMED_RESPONSE)
private fun secureChannelUnavailable(): Nothing =
    throw CoursewareRemoteException(CoursewareRemoteFailure.SECURE_CHANNEL_UNAVAILABLE)

private const val HEX = "0123456789ABCDEF"

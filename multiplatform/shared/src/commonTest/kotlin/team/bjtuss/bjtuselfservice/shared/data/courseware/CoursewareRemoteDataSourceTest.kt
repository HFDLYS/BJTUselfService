package team.bjtuss.bjtuselfservice.shared.data.courseware

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareCourse
import team.bjtuss.bjtuselfservice.shared.data.homework.SmartPlatformEndpoint

class CoursewareRemoteDataSourceTest {
    @Test
    fun fetchesCourseCatalogWithoutBlockingOnAnyCourseRoot() = runBlocking {
        val transport = ConcurrentRootTransport(courseCount = 7)
        val remote = SchoolCoursewareRemoteDataSource(transport, requestDelayMillis = 0)

        val snapshot = remote.fetchSnapshot()

        assertEquals(7, snapshot.courses.size)
        assertTrue(snapshot.courses.all { !it.childrenLoaded && it.children.isEmpty() })
        assertEquals(0, transport.rootRequestCount)
        assertEquals(0, transport.maxActiveRootRequests)
    }

    @Test
    fun fetchesRootFirstThenLoadsFolderOnDemandAndDownloadsHttpsResource() = runBlocking {
        val transport = QueueTransport(
            *initializationResponses(),
            smartResponse(
                """{"STATUS":"0","bagList":[{"id":1,"bag_name":"第一章"}],"resList":[{"resId":3,"rpId":"rp-3","rpName":"说明.pdf","extName":"pdf"}]}""",
            ),
            smartResponse(
                """{"STATUS":"0","bagList":[],"resList":[{"resId":2,"rpId":"rp-2","rpName":"第一讲.pdf","extName":"pdf","rpSize":"2 MB"}]}""",
            ),
            smartResponse(
                """{"flag":true,"rpUrl":"https://bksycenter.bjtu.edu.cn/resource/2","download_type":"file"}""",
            ),
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://bksycenter.bjtu.edu.cn/resource/2",
                headers = mapOf("Content-Type" to listOf("application/pdf; charset=binary")),
                body = "pdf-body".encodeToByteArray(),
            ),
        )
        val remote = SchoolCoursewareRemoteDataSource(transport, requestDelayMillis = 0)

        val catalogCourse = remote.fetchSnapshot().courses.single()
        assertFalse(catalogCourse.childrenLoaded)
        assertTrue(catalogCourse.children.isEmpty())

        val roots = remote.fetchChildren(catalogCourse, parentId = 0)
        val course = catalogCourse.copy(children = roots, childrenLoaded = true)
        val folder = course.children.first()
        assertFalse(folder.childrenLoaded)
        assertTrue(folder.children.isEmpty())
        assertFalse(transport.requests.any { "up_id=1" in it.url })

        val nested = remote.fetchChildren(course, folder.id).single()
        val file = remote.downloadResource(nested)

        assertEquals(listOf("第一章", "说明.pdf"), course.children.map { it.name })
        assertEquals("第一讲.pdf", nested.name)
        assertEquals("第一讲.pdf", file.fileName)
        assertEquals("application/pdf", file.contentType)
        assertContentEquals("pdf-body".encodeToByteArray(), file.bytes)
        assertTrue(transport.requests.all { it.url.startsWith("https://") })
        assertTrue(transport.requests.any { "up_id=1" in it.url })
        assertFalse(transport.requests.any { "123.121.147.7" in it.url })
        assertFalse(transport.requests.last().headers.containsKey("sessionid"))
        // 几十 MB 的课件不能受 30 秒整体超时限制；目录/取票等接口请求仍保持短超时。
        assertTrue(transport.requests.last().largeTransfer)
        assertTrue(transport.requests.dropLast(1).none { it.largeTransfer })
    }

    @Test
    fun macLegacyEndpointDownloadsOnlyWhitelistedResourceAndCalendarOrigins() = runBlocking {
        val transport = QueueTransport(
            *legacyInitializationResponses(),
            legacyResponse(
                """{"flag":true,"rpUrl":"http://123.121.147.7:88/ve/download/rp-2","download_type":"file"}""",
            ),
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "http://123.121.147.7:88/ve/download/rp-2",
                body = "resource".encodeToByteArray(),
            ),
            legacyResponse("""<input id="teacherId" value="T-28">"""),
            legacyResponse(
                """<iframe id="pdfIframe" src="https://frame.invalid/a/b/c/d/calendar.pdf"></iframe>""",
            ),
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "http://123.121.147.7:1936/kk/rp/a/b/c/d/calendar.pdf",
                headers = mapOf("Content-Type" to listOf("application/pdf")),
                body = "%PDF-calendar".encodeToByteArray(),
            ),
        )
        val remote = SchoolCoursewareRemoteDataSource(
            transport = transport,
            requestDelayMillis = 0,
            endpoint = SmartPlatformEndpoint.LegacyHttp,
        )

        assertEquals("resource", remote.downloadResource(resource()).bytes.decodeToString())
        assertEquals("程序设计_教学日历.pdf", remote.downloadTeachingCalendar(course()).fileName)
        assertEquals("https://mis.bjtu.edu.cn/module/module/28/", transport.requests.first().url)
        assertTrue(
            transport.requests.drop(1).all { request ->
                request.url.startsWith("http://123.121.147.7:88/ve/") ||
                    request.url.startsWith("http://123.121.147.7:1936/kk/rp/")
            },
        )
        assertFalse(transport.requests.last().headers.containsKey("sessionid"))
    }

    @Test
    fun rejectsExternalDownloadTicketBeforeRequestingResource() {
        val transport = QueueTransport(
            *initializationResponses(),
            smartResponse(
                """{"flag":true,"rpUrl":"https://example.com/private/resource","download_type":"file"}""",
            ),
        )
        val remote = SchoolCoursewareRemoteDataSource(transport, requestDelayMillis = 0)

        val error = assertFailsWith<CoursewareRemoteException> {
            runBlocking { remote.downloadResource(resource()) }
        }

        assertEquals(CoursewareRemoteFailure.SECURE_CHANNEL_UNAVAILABLE, error.reason)
        assertEquals(5, transport.requests.size)
        assertFalse("example.com" in error.toString())
    }

    @Test
    fun treatsHtmlDownloadTicketAsExpiredSessionAndReinitializesBeforeRetry() = runBlocking {
        val transport = QueueTransport(
            *initializationResponses(),
            smartResponse("<html><form action=\"/auth/login/\"></form></html>"),
            *initializationResponses(),
            smartResponse(
                """{"flag":true,"rpUrl":"https://bksycenter.bjtu.edu.cn/resource/recovered","download_type":"file"}""",
            ),
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://bksycenter.bjtu.edu.cn/resource/recovered",
                body = "recovered".encodeToByteArray(),
            ),
        )
        val remote = SchoolCoursewareRemoteDataSource(transport, requestDelayMillis = 0)

        val error = assertFailsWith<CoursewareRemoteException> {
            remote.downloadResource(resource())
        }
        assertEquals(CoursewareRemoteFailure.SESSION_EXPIRED, error.reason)

        val recovered = remote.downloadResource(resource())

        assertEquals("recovered", recovered.bytes.decodeToString())
        assertEquals(2, transport.requests.count { it.url == "https://mis.bjtu.edu.cn/module/module/28/" })
    }

    @Test
    fun downloadsTeachingCalendarOnlyFromSchoolHttpsFrame() = runBlocking {
        val transport = QueueTransport(
            *initializationResponses(),
            smartResponse("""<input id="teacherId" value="T-28">"""),
            smartResponse(
                """<iframe id="pdfIframe" src="https://bksycenter.bjtu.edu.cn/calendar/1.pdf"></iframe>""",
            ),
            SchoolHttpResponse(
                statusCode = 200,
                finalUrl = "https://bksycenter.bjtu.edu.cn/calendar/1.pdf",
                headers = mapOf("Content-Type" to listOf("application/pdf")),
                body = "%PDF-calendar".encodeToByteArray(),
            ),
        )
        val remote = SchoolCoursewareRemoteDataSource(transport, requestDelayMillis = 0)

        val file = remote.downloadTeachingCalendar(course())

        assertEquals("程序设计_教学日历.pdf", file.fileName)
        assertEquals("application/pdf", file.contentType)
        assertTrue(transport.requests.last().url.startsWith("https://bksycenter.bjtu.edu.cn/"))
        assertFalse(transport.requests.last().headers.containsKey("sessionid"))
    }

    @Test
    fun rejectsLegacyTeachingCalendarFrameWithoutRequestingIt() {
        val transport = QueueTransport(
            *initializationResponses(),
            smartResponse("""<input id="teacherId" value="T-28">"""),
            smartResponse(
                """<iframe id="pdfIframe" src="http://123.121.147.7:1936/kk/rp/calendar.pdf"></iframe>""",
            ),
        )
        val remote = SchoolCoursewareRemoteDataSource(transport, requestDelayMillis = 0)

        val error = assertFailsWith<CoursewareRemoteException> {
            runBlocking { remote.downloadTeachingCalendar(course()) }
        }

        assertEquals(CoursewareRemoteFailure.SECURE_CHANNEL_UNAVAILABLE, error.reason)
        assertEquals(6, transport.requests.size)
        assertFalse("123.121.147.7" in error.toString())
    }

    @Test
    fun reportsMissingHttpsArticleEndpointAsSecureChannelUnavailable() {
        val transport = QueueTransport(
            smartResponse("<html></html>"),
            SchoolHttpResponse(
                statusCode = 404,
                finalUrl = "https://bksycenter.bjtu.edu.cn/ve/back/coursePlatform/message.shtml",
            ),
        )

        val error = assertFailsWith<CoursewareRemoteException> {
            runBlocking {
                SchoolCoursewareRemoteDataSource(transport, requestDelayMillis = 0).fetchSnapshot()
            }
        }

        assertEquals(CoursewareRemoteFailure.SECURE_CHANNEL_UNAVAILABLE, error.reason)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun refusesApiRedirectBeforeFollowingOffOrigin() {
        val transport = QueueTransport(
            smartResponse("<html></html>"),
            smartResponse("""{"sessionId":"session-value"}"""),
            SchoolHttpResponse(
                statusCode = 302,
                finalUrl = "https://bksycenter.bjtu.edu.cn/ve/back/coursePlatform/course.shtml",
                headers = mapOf("Location" to listOf("https://evil.example/collect")),
            ),
        )

        val error = assertFailsWith<CoursewareRemoteException> {
            runBlocking {
                SchoolCoursewareRemoteDataSource(transport, requestDelayMillis = 0).fetchSnapshot()
            }
        }

        assertEquals(CoursewareRemoteFailure.SESSION_EXPIRED, error.reason)
        assertEquals(3, transport.requests.size)
        assertTrue(transport.requests.none { "evil.example" in it.url })
    }

    private class QueueTransport(vararg responses: SchoolHttpResponse) : SchoolHttpTransport {
        private val queue = responses.toMutableList()
        val requests = mutableListOf<SchoolHttpRequest>()

        override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse {
            requests += request
            return queue.removeFirst()
        }

        override suspend fun executeWithoutRedirects(request: SchoolHttpRequest): SchoolHttpResponse =
            execute(request)

        override fun clearSession() = Unit
    }

    private class ConcurrentRootTransport(
        private val courseCount: Int,
    ) : SchoolHttpTransport {
        var rootRequestCount = 0
        var maxActiveRootRequests = 0
        private var activeRootRequests = 0

        override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse = when {
            request.url == "https://mis.bjtu.edu.cn/module/module/28/" -> response("<html></html>")
            "message.shtml" in request.url -> response("""{"sessionId":"session-value"}""")
            "teachCalendar.shtml" in request.url ->
                response("""{"STATUS":"0","result":[{"xqCode":"2026-1"}]}""")
            "course.shtml" in request.url && "courseResource.shtml" !in request.url -> {
                val items = (1..courseCount).joinToString(",") { index ->
                    """{"id":$index,"name":"课程$index","teacher_id":28,"course_num":"CS$index","fz_id":"G$index","xq_code":"2026-1"}"""
                }
                response("""{"STATUS":"0","courseList":[$items]}""")
            }
            "courseResource.shtml" in request.url -> {
                rootRequestCount += 1
                activeRootRequests += 1
                maxActiveRootRequests = maxOf(maxActiveRootRequests, activeRootRequests)
                activeRootRequests -= 1
                response("""{"STATUS":"0","bagList":[],"resList":[]}""")
            }
            else -> error("Unexpected request: ${request.url}")
        }

        override suspend fun executeWithoutRedirects(request: SchoolHttpRequest): SchoolHttpResponse =
            execute(request)

        override fun clearSession() = Unit

        private fun response(body: String) = SchoolHttpResponse(
            statusCode = 200,
            finalUrl = "https://bksycenter.bjtu.edu.cn/ve/",
            body = body.encodeToByteArray(),
        )
    }

    private fun initializationResponses() = arrayOf(
        smartResponse("<html></html>"),
        smartResponse("""{"sessionId":"session-value"}"""),
        smartResponse("""{"STATUS":"0","result":[{"xqCode":"2026-1"}]}"""),
        smartResponse(
            """{"STATUS":"0","courseList":[{"id":17,"name":"程序设计","teacher_id":28,"course_num":"CS101","fz_id":"G1","xq_code":"2026-1"}]}""",
        ),
    )

    private fun legacyInitializationResponses() = arrayOf(
        legacyResponse("<html></html>"),
        legacyResponse("""{"sessionId":"session-value"}"""),
        legacyResponse("""{"STATUS":"0","result":[{"xqCode":"2026-1"}]}"""),
        legacyResponse(
            """{"STATUS":"0","courseList":[{"id":17,"name":"程序设计","teacher_id":28,"course_num":"CS101","fz_id":"G1","xq_code":"2026-1"}]}""",
        ),
    )

    private fun smartResponse(body: String) = SchoolHttpResponse(
        statusCode = 200,
        finalUrl = "https://bksycenter.bjtu.edu.cn/ve/",
        body = body.encodeToByteArray(),
    )

    private fun legacyResponse(body: String) = SchoolHttpResponse(
        statusCode = 200,
        finalUrl = "http://123.121.147.7:88/ve/",
        body = body.encodeToByteArray(),
    )

    private fun resource() = team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode(
        id = 2,
        courseId = 17,
        name = "第一讲.pdf",
        kind = team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNodeKind.RESOURCE,
        rpId = "rp-2",
        extension = "pdf",
    )

    private fun course() = CoursewareCourse(
        id = 17,
        name = "程序设计",
        courseNumber = "CS101",
        groupId = "G1",
        semesterCode = "2026-1",
        teacherId = 28,
        children = emptyList(),
    )
}

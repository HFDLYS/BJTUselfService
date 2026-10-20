package team.bjtuss.bjtuselfservice.shared.data.course

import team.bjtuss.bjtuselfservice.shared.network.SchoolEndpoints

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import team.bjtuss.bjtuselfservice.shared.data.homework.SmartPlatformEndpoint
import team.bjtuss.bjtuselfservice.shared.data.homework.openSmartPlatform
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.looksLikeSessionExpired
import team.bjtuss.bjtuselfservice.shared.network.parseOffMain

private const val TEACHER_URL =
    "https://aa.bjtu.edu.cn/course_selection/courseselectabsent/absent_list/"
private const val CURRENT_SCHEDULE_URL =
    "https://aa.bjtu.edu.cn/course_selection/courseselect/stuschedule/"
private const val SELECTION_SCHEDULE_URL =
    "https://aa.bjtu.edu.cn/course_selection/courseselecttask/schedule/"
private const val TIME_LIST_PATH = "/ve/back/coursePlatform/course.shtml"

data class RemoteCourseScheduleSnapshot(
    val courses: List<Course>,
    val currentWeek: Int,
)

enum class CourseScheduleRemoteFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
}

class CourseScheduleRemoteException(
    val reason: CourseScheduleRemoteFailure,
) : Exception("Unable to refresh course schedule: ${reason.name}")

interface CourseScheduleRemoteDataSource {
    suspend fun fetchSchedule(): RemoteCourseScheduleSnapshot
}

class SchoolCourseScheduleRemoteDataSource(
    private val transport: SchoolHttpTransport,
    private val requestDelayMillis: Long = 100,
    private val endpoint: SmartPlatformEndpoint? = null,
) : CourseScheduleRemoteDataSource {
    override suspend fun fetchSchedule(): RemoteCourseScheduleSnapshot {
        val teacherResponse = request(TEACHER_URL)
        val teachers = when (val parsed = parseOffMain { parseTeacherTable(teacherResponse.bodyText()) }) {
            is TeacherTableParseResult.Failure -> malformed()
            is TeacherTableParseResult.Success -> parsed.teachersByCourse
        }

        val currentResponse = request(CURRENT_SCHEDULE_URL)
        val currentCourses = parseOffMain {
            parseSchedule(
                html = currentResponse.bodyText(),
                isSelectionSchedule = false,
                teachers = teachers,
            )
        }
        val selectionResponse = request(SELECTION_SCHEDULE_URL)
        val selectionCourses = parseOffMain {
            parseSchedule(
                html = selectionResponse.bodyText(),
                isSelectionSchedule = true,
                teachers = teachers,
            )
        }

        return RemoteCourseScheduleSnapshot(
            courses = (currentCourses + selectionCourses).distinctBy { course ->
                listOf(
                    course.courseId,
                    course.courseLocationIndex.toString(),
                    course.courseTime,
                    course.coursePlace,
                    course.isCurrentSemester.toString(),
                ).joinToString("\u0000")
            },
            currentWeek = resolveCurrentWeek(),
        )
    }

    /**
     * 与 1.7.0 对齐：优先智慧教学 `getTimeList.weekCode`；失败再回退教务
     * `room_view?zc=`。取周失败不得让整张课表快照失败。
     */
    private suspend fun resolveCurrentWeek(): Int {
        val fromTimeList = fetchTimeListWeek()
        if (fromTimeList in TEACHING_WEEK_RANGE) return fromTimeList
        return fetchRoomViewWeek()
    }

    private suspend fun fetchTimeListWeek(): Int {
        val smart = endpoint ?: return 0
        return try {
            landOnSmartPlatform(smart)
            val response = executeSoft(
                SchoolHttpRequest(
                    method = SchoolHttpMethod.GET,
                    url = smart.apiUrl(
                        TIME_LIST_PATH,
                        linkedMapOf("method" to "getTimeList"),
                    ),
                    headers = mapOf(
                        "Accept" to "application/json, text/javascript, */*; q=0.01",
                        "Referer" to smart.apiOrigin,
                        "X-Requested-With" to "XMLHttpRequest",
                    ),
                ),
            ) ?: return 0
            if (response.statusCode !in 200..299) return 0
            if (!smart.acceptsApiUrl(response.finalUrl)) return 0
            parseCurrentWeekFromTimeList(response.bodyText())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            0
        }
    }

    private suspend fun landOnSmartPlatform(smart: SmartPlatformEndpoint) {
        try {
            smart.openSmartPlatform(allowUnparsedModulePage = true) { request ->
                executeHandshakeSoft(request) ?: throw IllegalStateException("Smart-platform handshake unavailable")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 当前周仍保留教务 room_view 回退。
        }
    }

    private suspend fun fetchRoomViewWeek(): Int = try {
        parseCurrentWeekFromUrl(request(SchoolEndpoints.ROOM_VIEW_URL).finalUrl)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        0
    }

    private suspend fun request(url: String) = try {
        if (requestDelayMillis > 0) delay(requestDelayMillis)
        transport.execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = url,
                headers = mapOf("Host" to "aa.bjtu.edu.cn"),
            ),
        ).also { response ->
            if (response.statusCode !in 200..299) {
                throw CourseScheduleRemoteException(CourseScheduleRemoteFailure.NETWORK)
            }
            if (parseOffMain { response.looksLikeSessionExpired() } || !response.finalUrl.startsWith(SchoolEndpoints.AA_ORIGIN)) {
                throw CourseScheduleRemoteException(CourseScheduleRemoteFailure.SESSION_EXPIRED)
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: CourseScheduleRemoteException) {
        throw error
    } catch (_: Exception) {
        throw CourseScheduleRemoteException(CourseScheduleRemoteFailure.NETWORK)
    }

    private suspend fun executeSoft(request: SchoolHttpRequest) = try {
        if (requestDelayMillis > 0) delay(requestDelayMillis)
        transport.execute(request)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun executeHandshakeSoft(request: SchoolHttpRequest) = try {
        if (requestDelayMillis > 0) delay(requestDelayMillis)
        transport.executeWithoutRedirects(request)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun parseSchedule(
        html: String,
        isSelectionSchedule: Boolean,
        teachers: Map<String, String>,
    ): List<Course> = when (
        val parsed = parseCourseScheduleTable(html, isSelectionSchedule, teachers)
    ) {
        is CourseScheduleTableParseResult.Failure -> malformed()
        is CourseScheduleTableParseResult.Success -> parsed.courses
    }

    private fun malformed(): Nothing =
        throw CourseScheduleRemoteException(CourseScheduleRemoteFailure.MALFORMED_RESPONSE)
}

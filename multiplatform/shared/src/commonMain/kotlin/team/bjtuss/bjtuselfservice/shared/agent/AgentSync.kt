package team.bjtuss.bjtuselfservice.shared.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.auth.AutomaticLoginCoordinator
import team.bjtuss.bjtuselfservice.shared.auth.AutomaticLoginResult
import team.bjtuss.bjtuselfservice.shared.auth.AuthenticationResult
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaChallenge
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognizer
import team.bjtuss.bjtuselfservice.shared.auth.ChallengeResult
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.auth.LoginAutomationGateway
import team.bjtuss.bjtuselfservice.shared.auth.LoginFailure
import team.bjtuss.bjtuselfservice.shared.auth.SchoolLoginProtocol
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.cache.SyncSource
import team.bjtuss.bjtuselfservice.shared.cache.markSynced
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.DefaultClassroomOccupancyRepository
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.SchoolClassroomOccupancyRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.course.CacheStoreCourseScheduleLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.course.DefaultCourseScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.course.SchoolCourseScheduleRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.exam.CacheStoreExamScheduleLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.exam.DefaultExamScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.exam.ExamScheduleRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.exam.SchoolExamScheduleRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.grade.CacheStoreGradeLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.grade.DefaultGradeRepository
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.grade.SchoolGradeRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.grade.SchoolTrainingProgramRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.home.CacheStoreHomeChangeFeedRepository
import team.bjtuss.bjtuselfservice.shared.data.home.courseChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.examChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.gradeChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.homeworkChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.homework.CacheStoreHomeworkLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.homework.DefaultHomeworkRepository
import team.bjtuss.bjtuselfservice.shared.data.homework.HomeworkRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.homework.SchoolHomeworkRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.domain.change.recordSafely
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleType
import team.bjtuss.bjtuselfservice.shared.feature.course.resolveCourseScheduleCalendarMappings
import team.bjtuss.bjtuselfservice.shared.feature.course.saveAcademicWeeksIfChanged
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.smartPlatformEndpointFor

/** `cache update` 能刷新的数据源（教务与智慧教学；物理在线、CITEL 等独立账号的数据源仍需打开 App）。 */
enum class AgentSyncTarget(val wire: String, val label: String) {
    GRADES("grades", "成绩"),
    COURSES("courses", "课表"),
    HOMEWORK("homework", "课程平台作业"),
    EXAMS("exams", "考试"),
}

sealed interface AgentLoginOutcome {
    data object Ok : AgentLoginOutcome
    /** 自动登录用尽（验证码识别多次失败、密码错误等），需要用户打开 App 手动登录。 */
    data class ManualRequired(val reason: LoginFailure) : AgentLoginOutcome
    data object Network : AgentLoginOutcome
}

data class AgentSyncReport(
    val login: AgentLoginOutcome,
    /** 每个数据源：null 表示成功，否则为失败原因。未尝试的不在表里。 */
    val results: Map<AgentSyncTarget, String?>,
    val academicCalendarUpdated: Boolean,
) {
    val allOk: Boolean get() = login == AgentLoginOutcome.Ok && results.values.all { it == null }
}

/**
 * 编排：先登录，再按 App 启动同步的顺序刷新（成绩 → 课表（含校历）→ 作业 → 考试）。
 * 任何一项失败不影响其它项，失败项保留原缓存（仓库本身保证）。
 */
suspend fun runAgentSync(
    targets: List<AgentSyncTarget>,
    login: suspend () -> AgentLoginOutcome,
    refresh: suspend (AgentSyncTarget) -> String?,
    refreshCalendar: suspend () -> Boolean,
    perTargetTimeoutMillis: Long = 90_000,
): AgentSyncReport {
    val loginOutcome = try {
        login()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        AgentLoginOutcome.Network
    }
    if (loginOutcome != AgentLoginOutcome.Ok) return AgentSyncReport(loginOutcome, emptyMap(), false)
    val ordered = AgentSyncTarget.entries.filter { it in targets }
    val results = linkedMapOf<AgentSyncTarget, String?>()
    var calendarUpdated = false
    for (target in ordered) {
        results[target] = try {
            withTimeout(perTargetTimeoutMillis) { refresh(target) }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            "超时"
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            "失败（${error::class.simpleName}）"
        }
        if (target == AgentSyncTarget.COURSES) {
            calendarUpdated = runCatching { withTimeout(perTargetTimeoutMillis) { refreshCalendar() } }.getOrDefault(false)
        }
    }
    return AgentSyncReport(loginOutcome, results, calendarUpdated)
}

/** 真实依赖装配：与 App 的会话工厂使用同一套仓库、变动记录和同步时间写入。 */
class AgentSyncGraph(
    private val studentId: String,
    private val cache: CacheStore,
    private val transport: SchoolHttpTransport,
    private val captchaRecognizer: CaptchaRecognizer,
    platformFamily: PlatformFamily,
    private val nowEpochSeconds: () -> Long,
    private val today: () -> LocalDate,
) {
    private val protocol = SchoolLoginProtocol(transport)
    private val endpoint = smartPlatformEndpointFor(platformFamily)
    private val feed = CacheStoreHomeChangeFeedRepository(studentId, cache)

    private fun synced(source: SyncSource): () -> Unit = { cache.markSynced(studentId, source, nowEpochSeconds()) }

    /** 与 [team.bjtuss.bjtuselfservice.shared.auth.SchoolSessionRecovery] 相同的流程，但保留失败原因。 */
    suspend fun login(credentials: Credentials): AgentLoginOutcome {
        val gateway = object : LoginAutomationGateway {
            override suspend fun requestCaptchaChallenge(studentId: String): ChallengeResult =
                protocol.requestFreshCaptchaChallenge(studentId)

            override suspend fun authenticateMis(
                credentials: Credentials,
                challenge: CaptchaChallenge,
                captchaAnswer: String,
            ): AuthenticationResult = protocol.authenticateMis(credentials, challenge, captchaAnswer)
        }
        return when (val result = AutomaticLoginCoordinator(gateway, captchaRecognizer).login(credentials)) {
            is AutomaticLoginResult.SessionActive, is AutomaticLoginResult.Authenticated ->
                if (protocol.linkAcademicSystem()) AgentLoginOutcome.Ok else AgentLoginOutcome.Network
            is AutomaticLoginResult.ManualRequired ->
                if (result.reason == LoginFailure.NETWORK) AgentLoginOutcome.Network
                else AgentLoginOutcome.ManualRequired(result.reason)
        }
    }

    suspend fun refresh(target: AgentSyncTarget): String? = when (target) {
        AgentSyncTarget.GRADES -> {
            val repository = DefaultGradeRepository(studentId, CacheStoreGradeLocalDataSource(cache),
                SchoolGradeRemoteDataSource(transport), SchoolTrainingProgramRemoteDataSource(transport), synced(SyncSource.GRADES))
            val before = repository.load().grades
            when (val result = repository.refresh()) {
                is GradeRefreshResult.Success -> null.also { gradeChangeRecorder(feed).recordSafely(before, result.snapshot.grades) }
                is GradeRefreshResult.Failure -> result.reason.name.lowercase()
            }
        }
        AgentSyncTarget.COURSES -> {
            val repository = courseRepository()
            val before = repository.load().courses
            when (val result = repository.refresh()) {
                is CourseScheduleRefreshResult.Success -> null.also { courseChangeRecorder(feed).recordSafely(before, result.snapshot.courses) }
                is CourseScheduleRefreshResult.Failure -> result.reason.name.lowercase()
            }
        }
        AgentSyncTarget.HOMEWORK -> {
            val repository = DefaultHomeworkRepository(studentId, CacheStoreHomeworkLocalDataSource(cache),
                SchoolHomeworkRemoteDataSource(transport = transport, endpoint = endpoint), synced(SyncSource.HOMEWORK))
            val before = repository.load().homework
            when (val result = repository.refresh()) {
                is HomeworkRefreshResult.Success -> null.also { homeworkChangeRecorder(feed).recordSafely(before, result.snapshot.homework) }
                is HomeworkRefreshResult.Failure -> result.reason.name.lowercase()
            }
        }
        AgentSyncTarget.EXAMS -> {
            val repository = DefaultExamScheduleRepository(studentId, CacheStoreExamScheduleLocalDataSource(cache),
                SchoolExamScheduleRemoteDataSource(transport), synced(SyncSource.EXAMS))
            val before = repository.load().exams
            when (val result = repository.refresh()) {
                is ExamScheduleRefreshResult.Success -> null.also { examChangeRecorder(feed).recordSafely(before, result.snapshot.exams) }
                is ExamScheduleRefreshResult.Failure -> result.reason.name.lowercase()
            }
        }
    }

    /** 当前学期校历：写入 academic_weeks_v1，并按今天校准当前周（与课表页同一套解析）。 */
    suspend fun refreshCalendar(): Boolean {
        val occupancy = DefaultClassroomOccupancyRepository(SchoolClassroomOccupancyRemoteDataSource(transport))
        val semesters = occupancy.fetchSemesters()
        val weekDates = occupancy.fetchWeekDates()
        val day = today()
        val current = resolveCourseScheduleCalendarMappings(semesters.selected?.label, weekDates, day)[CourseScheduleType.CURRENT]
            ?: return false
        cache.saveAcademicWeeksIfChanged(studentId, current.weeks)
        val week = current.weeks.firstOrNull { week ->
            val start = week.startDate ?: return@firstOrNull false
            day >= start && day <= start.plus(6, DateTimeUnit.DAY)
        }?.week
        if (week != null) courseRepository().reconcileCurrentWeek(week)
        return true
    }

    private fun courseRepository() = DefaultCourseScheduleRepository(studentId, CacheStoreCourseScheduleLocalDataSource(cache),
        SchoolCourseScheduleRemoteDataSource(transport = transport, endpoint = endpoint), synced(SyncSource.COURSES))
}

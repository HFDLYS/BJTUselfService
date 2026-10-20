package team.bjtuss.bjtuselfservice.shared

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import platform.UIKit.UIViewController
import team.bjtuss.bjtuselfservice.shared.auth.*
import team.bjtuss.bjtuselfservice.shared.cache.*
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.network.*
import team.bjtuss.bjtuselfservice.shared.security.*
import team.bjtuss.bjtuselfservice.shared.files.*
import team.bjtuss.bjtuselfservice.shared.calendar.*
import team.bjtuss.bjtuselfservice.shared.feature.shell.*
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.data.course.*
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.homework.*
import team.bjtuss.bjtuselfservice.shared.data.homework.*
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkScreenModel

/** Only selected by the Swift DEBUG gate. No network, real cache, Keychain or login UI. */
fun OfflineLayoutBootstrap(onReady: (AuthenticatedSession) -> Unit): UIViewController = ComposeUIViewController {
    val cache = remember { CacheStore(NativeSqliteDriver(CacheDatabaseSql.Schema, ":memory:")) }
    val transport = remember { object : SchoolHttpTransport {
        override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse = error("Offline layout fixture")
        override fun clearSession() = Unit
    } }
    val security = remember { AccountSecurityCoordinator(AccountSecurityStore(null, object : AccountPreferences {
        override suspend fun shouldRememberCredentials() = false
        override suspend fun setShouldRememberCredentials(enabled: Boolean) = Unit
        override suspend fun clearRememberCredentialsSetting() = Unit
    })) }
    val preferences = remember { AppPreferences(bottomNavigationItems = listOf("HOME", "SCHEDULE", "GRADES", "ASSIGNMENTS", "MORE"), physicsLabEnabled = false, citelEnabled = false, redesignGuideAcknowledged = true, citelGuideAcknowledged = true, assignmentAggregateGuideAcknowledged = true, release184GuideAcknowledged = true, emergencyFix185NoticeAcknowledged = true) }
    val source = rememberAuthenticatedSession(
        StudentProfile("布局测试", "offline-layout", "测试", "测试"), false,
        transport, remember { SchoolLoginProtocol(transport) }, cache, currentPlatform(), preferences,
        { true }, security, UnavailableCaptchaRecognizer, "", "",
        UnavailableHomeworkFileGateway, UnavailableCoursewareDirectoryGateway, UnavailableSystemCalendarGateway,
        {}, {},
    )
    val session = remember(source) {
        val snapshot = CourseScheduleSnapshot((1..7).flatMap { slot -> (1..7).map { day ->
            Course(slot * 10 + day, "fixture", "测试课程 $slot", "测试教师", slot * 8 + day,
                "第1-16周", "思源楼，SY101", false)
        } }, 1)
        AuthenticatedSession(
            profile = source.profile, entryLoggingIn = false, gradeModel = source.gradeModel,
            courseScheduleModel = CourseScheduleScreenModel(object : CourseScheduleRepository {
                override fun load() = snapshot
                override suspend fun refresh() = CourseScheduleRefreshResult.Success(snapshot)
            }), examScheduleModel = source.examScheduleModel, homeworkModel = HomeworkScreenModel(OfflineLayoutHomeworkRepository()),
            coursewareModel = source.coursewareModel, otherFunctionModel = source.otherFunctionModel,
            classroomModel = source.classroomModel, classroomOccupancyModel = source.classroomOccupancyModel,
            settingsModel = source.settingsModel, loginSyncPreferences = preferences,
            mailboxModel = source.mailboxModel, phyVlabModel = source.phyVlabModel,
            homeModel = source.homeModel, homeChangeFeed = source.homeChangeFeed,
            homeworkFileGateway = source.homeworkFileGateway, coursewareDirectoryGateway = source.coursewareDirectoryGateway,
            systemCalendarGateway = source.systemCalendarGateway, onLogout = {},
        ).also { it.legacyHttpWarningDismissed = true }
    }
    LaunchedEffect(session) { onReady(session) }
}

fun OfflineLayoutRootViewController(session: AuthenticatedSession, onOpenRoute: (String) -> Unit): UIViewController {
    lateinit var controller: UIViewController
    val presenter = IosNativeSheetPresenter { controller }
    controller = ComposeUIViewController {
        PlatformAppTheme(false, false) { fontScale ->
            CompositionLocalProvider(LocalNativeSheetPresenter provides presenter) {
                Surface(Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                        AuthenticatedAppShell(session, currentPlatform(), adaptiveWindowClassFor(maxWidth.value.toInt(), fontScale),
                            nativeNavigationEnabled = true, onOpenNativeRoute = onOpenRoute)
                    }
                }
            }
        }
    }
    return controller
}

/** Deterministic long list and detail for native navigation verification; no network or submission. */
private class OfflineLayoutHomeworkRepository : HomeworkRepository {
    private val snapshot = HomeworkSnapshot((1..18).map { index ->
        Homework(upId = index, idSnId = null, score = "", userId = 1, courseId = 1,
            courseName = "布局验证课程", title = "离线作业 $index", content = "原生滚动验证",
            createDate = "2026-10-01 08:00:00", endTime = "2099-12-31 23:59:59",
            openDate = "2026-10-01 08:00:00", status = 0, submitCount = 0, allCount = 1,
            subStatus = "", scoreId = 0, homeworkType = 0)
    })
    override fun load() = snapshot
    override suspend fun refresh() = HomeworkRefreshResult.Success(snapshot)
    override suspend fun loadDetail(homework: Homework) = HomeworkDetailResult.Success(
        HomeworkDetail((1..24).joinToString("\n\n") { "第 $it 段：这是用于检查原生导航收起与恢复的离线内容。列表、详情和返回按钮应保持稳定。" }, emptyList()))
    override suspend fun loadSubmittedAttachments(homework: Homework) = HomeworkOperationResult.Success(emptyList<SubmittedHomeworkAttachment>())
    override suspend fun downloadTeacherAttachment(homeworkId: Int, attachment: HomeworkAttachment) = HomeworkOperationResult.Failure(HomeworkSyncFailure.NETWORK)
    override suspend fun downloadSubmittedAttachment(attachment: SubmittedHomeworkAttachment) = HomeworkOperationResult.Failure(HomeworkSyncFailure.NETWORK)
    override suspend fun submitHomework(homework: Homework, content: String, files: List<HomeworkFileContent>) = HomeworkOperationResult.Failure(HomeworkSyncFailure.SUBMIT_REJECTED)
    override fun attachmentDownloadUrl(homeworkId: Int, attachmentId: Int) = ""
}

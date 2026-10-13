package team.bjtuss.bjtuselfservice.shared.feature.shell

import team.bjtuss.bjtuselfservice.shared.feature.widget.encode
import androidx.compose.foundation.layout.windowInsetsPadding
import team.bjtuss.bjtuselfservice.shared.feature.grade.GradeChangeNoticeDialog
import team.bjtuss.bjtuselfservice.shared.feature.grade.GradeContentSource
import team.bjtuss.bjtuselfservice.shared.feature.grade.GradeWorkspace


import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import team.bjtuss.bjtuselfservice.shared.LocalReduceMotion
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.PlatformInfo
import team.bjtuss.bjtuselfservice.shared.WindowClass
import team.bjtuss.bjtuselfservice.shared.currentPlatform
import kotlin.math.PI
import team.bjtuss.bjtuselfservice.shared.accessibleAlpha
import team.bjtuss.bjtuselfservice.shared.data.grade.formatGradeDetailForDisplay
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppErrorBanner
import team.bjtuss.bjtuselfservice.shared.usesLegacySmartTransportFor
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.home.HomeStatusFailure
import team.bjtuss.bjtuselfservice.shared.data.home.HomeChangeFeedRepository
import team.bjtuss.bjtuselfservice.shared.data.homework.HomeworkSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.courseware.CoursewareSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.exam.ExamScheduleSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.ClassroomOccupancySyncFailure
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseCompactViewMode
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleContentSource
import kotlinx.coroutines.flow.MutableStateFlow
import team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLabSettings
import team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLabState
import team.bjtuss.bjtuselfservice.shared.feature.course.scheduleEventCourses
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleContentSource
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleDetailWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkContentSource
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkDetailWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.assignment.*
import team.bjtuss.bjtuselfservice.shared.logging.SyncTiming
import team.bjtuss.bjtuselfservice.shared.feature.citel.CitelDetailWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareContentSource
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.OtherFunctionScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.SCHOOL_CALENDAR_ARTICLE_URL
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.SchoolCalendarArticleWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.ReportCardDownloadWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomBuildingState
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomBuildingWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomUiState
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.classroomoccupancy.ClassroomOccupancyBuildingWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.classroomoccupancy.ClassroomOccupancyQueryState
import team.bjtuss.bjtuselfservice.shared.feature.classroomoccupancy.ClassroomOccupancyWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.settings.AppUpdateResultDialog
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxUiState
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxFailure
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxComposeScreen
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabDetailWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabContentSource
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSyncFailure
import team.bjtuss.bjtuselfservice.shared.feature.shell.SessionRefreshCoordinator
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.home.homeIdleStatusText
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeSyncItem
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeSyncItemState
import team.bjtuss.bjtuselfservice.shared.feature.home.homeSyncDialogTitle
import team.bjtuss.bjtuselfservice.shared.webview.openExternalUrl
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppCommand
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppCommandBus
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeInfoResult
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSortOrder
import team.bjtuss.bjtuselfservice.shared.domain.grade.displayCourseName
import team.bjtuss.bjtuselfservice.shared.domain.grade.displayName
import team.bjtuss.bjtuselfservice.shared.domain.grade.scoreForSorting
import team.bjtuss.bjtuselfservice.shared.domain.homework.stableKey
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeKind
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeDomain
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeRecord

/**
 * 物理在线的列表和首页日程共用同一个模型，因此自动同步只能由一个稳定的宿主启动。
 *
 * 旧的共享壳只有一个 Compose 实例，`forcedRouteId == null` 就足够了。iOS Liquid
 * 壳则为每个一级 Tab 建一个宿主；它们都会执行这个文件里的副作用，如果只判断
 * `forcedRouteId != null`，首页也会被排除，物理在线便只会在用户点进页面后才同步。
 */
/** 同步期间课表、实验、校历、课程性质会接连到达，合并成一次小组件快照写入。 */
internal const val COURSE_WIDGET_WRITE_DEBOUNCE_MILLIS = 1_500L

internal fun shouldStartPhyVlabAutoSync(
    forcedRouteId: String?,
    nativeTabBarEnabled: Boolean,
): Boolean = forcedRouteId == null ||
    (nativeTabBarEnabled && forcedRouteId == AppSection.HOME.name)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedAppShell(
    session: team.bjtuss.bjtuselfservice.shared.AuthenticatedSession,
    platform: PlatformInfo,
    windowClass: WindowClass,
    appCommandBus: AppCommandBus? = null,
    nativeNavigationEnabled: Boolean = false,
    /** 本实例作为宿主原生 tab 的根页面：底部导航条由系统容器提供，Compose 不再自绘。 */
    nativeTabBarEnabled: Boolean = false,
    /** 二级页标题与返回按钮交给宿主导航栏，Compose 顶栏只保留仍有页面动作的一行。 */
    useNativeTitleBar: Boolean = false,
    onOpenNativeRoute: (String) -> Unit = {},
    /** 目的地内部请求切换一级入口（原生 tab 根页面之间的跳转）。 */
    onSelectNativeTab: (String) -> Unit = {},
    /** 向宿主发布当前目的地标题；宿主据此渲染系统导航栏标题。 */
    onNativeTitleChanged: (String) -> Unit = {},
    /** 向宿主发布导航栏右侧动作；传 null 表示本页没有宿主动作，宿主应撤掉按钮。 */
    onNativeActionChanged: (NativeBarAction?) -> Unit = {},
    onOpenExternalUrl: (String) -> Unit = ::openExternalUrl,
    forcedRouteId: String? = null,
    onCloseNativeRoute: () -> Unit = {},
    homeworkFileGatewayOverride: HomeworkFileGateway? = null,
    coursewareDirectoryGatewayOverride: CoursewareDirectoryGateway? = null,
) {
    val profile = session.profile
    val entryLoggingIn = session.entryLoggingIn
    val gradeModel = session.gradeModel
    val courseScheduleModel = session.courseScheduleModel
    val examScheduleModel = session.examScheduleModel
    val homeworkModel = session.homeworkModel
    val coursewareModel = session.coursewareModel
    val otherFunctionModel = session.otherFunctionModel
    val classroomModel = session.classroomModel
    val classroomOccupancyModel = session.classroomOccupancyModel
    val settingsModel = session.settingsModel
    val mailboxModel = session.mailboxModel
    val phyVlabModel = session.phyVlabModel
    val homeModel = session.homeModel
    val homeChangeFeed = session.homeChangeFeed
    val homeworkFileGateway = homeworkFileGatewayOverride ?: session.homeworkFileGateway
    val coursewareDirectoryGateway = coursewareDirectoryGatewayOverride ?: session.coursewareDirectoryGateway
    val systemCalendarGateway = session.systemCalendarGateway
    val onLogout = session.onLogout
    val reauthenticateSession = session.reauthenticateSession
    val gradeState by gradeModel.state.collectAsState()
    val courseState by courseScheduleModel.state.collectAsState()
    val examState by examScheduleModel.state.collectAsState()
    val homeworkState by homeworkModel.state.collectAsState()
    val coursewareState by coursewareModel.state.collectAsState()
    val classroomState by classroomModel.state.collectAsState()
    val classroomOccupancyState by classroomOccupancyModel.state.collectAsState()
    val mailboxState by mailboxModel.state.collectAsState()
    val mailboxUnread by mailboxModel.unreadSummary.collectAsState()
    val mailboxMessageLoading = (mailboxState as? MailboxUiState.Ready)?.isMessageLoading == true
    val phyVlabState by phyVlabModel.state.collectAsState()
    val settingsState by settingsModel.state.collectAsState()
    val physicsLabEnabled = settingsState.preferences.isPhysicsLabEnabled
    val citelEnabled = settingsState.preferences.isCitelEnabled
    val physicsLabFlow = remember(session) { session.physicsLabModel?.state ?: MutableStateFlow(PhysicsLabState()) }
    val physicsLabState by physicsLabFlow.collectAsState()
    val physicsDemoFlow = remember(session) { session.physicsDemoModel?.state ?: MutableStateFlow(team.bjtuss.bjtuselfservice.shared.feature.phydemo.PhysicsDemoState()) }
    val physicsDemoState by physicsDemoFlow.collectAsState()
    val citelFlow = remember(session) { session.citelModel?.state ?: MutableStateFlow(team.bjtuss.bjtuselfservice.shared.feature.citel.CitelState()) }
    val citelState by citelFlow.collectAsState()
    val citelEvents = remember(citelState.tasks, citelEnabled) {
        if (citelEnabled) team.bjtuss.bjtuselfservice.shared.feature.citel.citelAgendaEvents(citelState.tasks) else emptyList()
    }
    val scheduleEvents = remember(courseState.academicWeeks, examState.exams, physicsLabState.labs, physicsLabEnabled, physicsDemoState.bookings, physicsDemoState.enabled) {
        scheduleEventCourses(examState.exams, if (physicsLabEnabled) physicsLabState.labs else emptyList(), courseState.academicWeeks, if (physicsDemoState.enabled) physicsDemoState.bookings else emptyList())
    }
    val scheduleStateWithEvents = courseState.copy(
        supplementalCourses = scheduleEvents,
        physicsLabs = if (physicsLabEnabled) physicsLabState.labs else emptyList(),
        physicsDemoBookings = if (physicsDemoState.enabled) physicsDemoState.bookings else emptyList(),
    )
    val homeScheduleEvents = remember(courseState.homeAcademicWeeks, examState.exams, physicsLabState.labs,
        physicsLabEnabled, physicsDemoState.bookings, physicsDemoState.enabled) {
        scheduleEventCourses(examState.exams, if (physicsLabEnabled) physicsLabState.labs else emptyList(),
            courseState.homeAcademicWeeks, if (physicsDemoState.enabled) physicsDemoState.bookings else emptyList())
    }
    // iOS 课程小组件：课表、实验、校历或日期变化时重写快照；内容（不含生成时间）不变就不写，
    // 避免每次重组都让 WidgetKit 重新加载时间线。
    // iOS 每个原生 Tab 都有一个壳宿主，只让主宿主写（否则同步期间每个宿主各算一遍、各写一遍、
    // 各触发一次 WidgetKit 全量刷新）；同步时数据连续变化，先等一会儿合并成一次，计算放到后台线程。
    val widgetToday = homeworkState.now.date
    var lastWidgetDays by remember(session) { mutableStateOf<List<team.bjtuss.bjtuselfservice.shared.feature.widget.CourseWidgetDay>?>(null) }
    LaunchedEffect(courseState.courses, homeScheduleEvents, courseState.homeAcademicWeeks, courseState.currentWeek, widgetToday,
        gradeState.courseTypesByCode) {
        if (!shouldStartPhyVlabAutoSync(forcedRouteId, nativeTabBarEnabled)) return@LaunchedEffect
        if (courseState.courses.isEmpty() && homeScheduleEvents.isEmpty()) return@LaunchedEffect
        if (courseState.homeAcademicWeeks.isEmpty() && courseState.currentWeek <= 0) return@LaunchedEffect
        delay(COURSE_WIDGET_WRITE_DEBOUNCE_MILLIS)
        val courses = courseState.courses
        val events = homeScheduleEvents
        val weeks = courseState.homeAcademicWeeks
        val currentWeek = courseState.currentWeek
        val types = gradeState.courseTypesByCode
        val previous = lastWidgetDays
        val written = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val (widgetStart, widgetDayCount) = team.bjtuss.bjtuselfservice.shared.feature.widget.courseWidgetRange(widgetToday)
            val snapshot = team.bjtuss.bjtuselfservice.shared.feature.widget.buildCourseWidgetSnapshot(
                schedule = team.bjtuss.bjtuselfservice.shared.feature.home.HomeSchedulePresentation(
                    courses = courses,
                    supplementalCourses = events,
                    academicWeeks = weeks,
                    currentWeek = currentWeek,
                    today = widgetToday,
                ),
                startDate = widgetStart,
                generatedAtEpochSeconds = kotlin.time.Clock.System.now().epochSeconds,
                dayCount = widgetDayCount,
                courseTypesByCode = types,
            )
            if (snapshot.days == previous) return@withContext null
            team.bjtuss.bjtuselfservice.shared.feature.widget.writeCourseWidgetSnapshot(snapshot.encode())
            snapshot.days
        }
        if (written != null) lastWidgetDays = written
    }
    val homeState by homeModel.state.collectAsState()
    val phyVlabEnabled = settingsState.preferences.isPhyVlabEnabled
    val aggregatedAssignments = aggregateAssignments(homeworkState.homework,
        if (phyVlabEnabled) phyVlabModel.allActivities() else emptyList(),
        if (citelEnabled) citelState.tasks else emptyList(), homeworkState.timeZone)
    val assignmentSync = buildList {
        add(AssignmentSourceSync(AssignmentSource.COURSE_PLATFORM, homeworkState.isLoading || homeworkState.isRefreshing,
            homeworkState.failure != null, homeworkState.source == HomeworkContentSource.NETWORK,
            homeworkState.source == HomeworkContentSource.CACHE,
            if (homeworkState.failure != null) "课程平台作业同步失败，请检查网络或登录状态。" else null))
        if (phyVlabEnabled) add(AssignmentSourceSync(AssignmentSource.PHYVLAB, phyVlabState.isLoading,
            phyVlabState.failure != null || phyVlabState.casLoginRequired, phyVlabState.contentSource == PhyVlabContentSource.NETWORK,
            phyVlabState.contentSource == PhyVlabContentSource.CACHE,
            phyVlabState.failureDetail ?: if (phyVlabState.failure != null || phyVlabState.casLoginRequired) "请检查校园网或重新登录物理在线。" else null))
        if (citelEnabled) add(AssignmentSourceSync(AssignmentSource.CITEL, citelState.refreshing, citelState.failed,
            citelState.lastSync != null && !citelState.fromCache, citelState.fromCache, citelState.message))
    }
    var aggregateSyncDialogVisible by remember { mutableStateOf(false) }
    val compactBottomNavSections = remember(settingsState.preferences) {
        bottomNavSections(settingsState.preferences)
    }
    if (nativeTabBarEnabled) {
        // 底栏由 UIKit 装配，Compose 侧的入口集合变化必须显式回推给宿主，否则切换「物理在线」底栏不动。
        LaunchedEffect(compactBottomNavSections) {
            session.onNativeTabItemsChanged?.invoke(nativeTabItems(session).map { it.routeId })
        }
    }
    val homeChanges by homeChangeFeed.records.collectAsState()
    var sessionRecoveryInProgress by remember(session) { mutableStateOf(false) }
    val homeSyncItems = buildHomeSyncItems(
        isLoggingIn = entryLoggingIn,
        homeBusy = homeState.isRefreshing,
        homeFailed = homeState.failure != null,
        homeReady = homeState.status != null,
        gradeBusy = gradeState.isLoading || gradeState.isRefreshing,
        gradeFailed = gradeState.failure != null,
        gradeReady = gradeState.source != null,
        homeworkBusy = homeworkState.isLoading || homeworkState.isRefreshing,
        homeworkFailed = homeworkState.failure != null,
        homeworkReady = homeworkState.source != null,
        examBusy = examState.isLoading || examState.isRefreshing,
        examFailed = examState.failure != null,
        examReady = examState.source != null,
        // 课表来源只说明课程列表到了。教学周未确认时不能显示「已完成」，
        // 否则秋假空档或校历失败时，面板已完成、首页仍停在「日程加载中」。
        courseBusy = courseState.isLoading || courseState.isRefreshing || courseState.isCalendarLoading,
        courseFailed = courseState.failure != null || courseState.calendarFailed,
        courseReady = courseState.source != null && courseState.weekResolved,
        physicsLabEnabled = physicsLabEnabled,
        physicsLabBusy = physicsLabState.refreshing,
        physicsLabFailed = physicsLabState.failed,
        physicsLabReady = physicsLabState.synced,
        physicsDemoEnabled = physicsDemoState.enabled,
        physicsDemoBusy = physicsDemoState.refreshing,
        physicsDemoFailed = physicsDemoState.failed,
        physicsDemoReady = physicsDemoState.synced,
        citelEnabled = citelEnabled,
        citelBusy = citelState.refreshing,
        citelFailed = citelState.failed,
        citelReady = citelState.lastSync != null && !citelState.fromCache,
        phyVlabEnabled = phyVlabEnabled,
        phyVlabBusy = phyVlabEnabled && phyVlabState.isLoading,
        phyVlabFailed = phyVlabEnabled && (phyVlabState.failure != null || phyVlabState.casLoginRequired),
        phyVlabReady = phyVlabEnabled &&
            (phyVlabState.courses.isNotEmpty() || phyVlabState.agendaEvents.isNotEmpty()),
    )
    val homeSyncFailureItems = homeSyncItems
        .filter { it.state == HomeSyncItemState.FAILED }
        .map(HomeSyncItem::title)
    val homeSyncInProgress = entryLoggingIn || sessionRecoveryInProgress || homeSyncItems.any {
        it.state == HomeSyncItemState.SYNCING
    }
    var homeTodayRequest by remember { mutableStateOf(0) }
    var homeTodaySelected by remember { mutableStateOf(true) }
    var homeSyncDialogVisible by remember { mutableStateOf(false) }
    var citelSyncDialogVisible by remember { mutableStateOf(false) }
    var partialSyncFailureDialogItems by remember { mutableStateOf<List<String>?>(null) }
    // 挂 session：原生二级页重建 Compose 时仍记住本登录态是否关过提示。
    // 同时必须有本地 mutableState，否则只写 session 字段不会触发重组，Banner 点了不关。
    var legacyWarningDismissed by remember(session) {
        mutableStateOf(session.legacyHttpWarningDismissed)
    }
    val dismissLegacyWarning: () -> Unit = {
        session.legacyHttpWarningDismissed = true
        legacyWarningDismissed = true
    }
    var classroomIntroBannerDismissed by remember(session) {
        mutableStateOf(session.classroomIntroBannerDismissed)
    }
    var showCourseCalendarExport by remember(session) { mutableStateOf(false) }
    val dismissClassroomIntroBanner: () -> Unit = {
        session.classroomIntroBannerDismissed = true
        classroomIntroBannerDismissed = true
    }
    val scope = rememberCoroutineScope()
    // 紧凑端底栏挂在 NavDisplay 外层（与内容解耦）：一级 tab 切换时底栏实例保持存活，
    // 避免整页销毁把 NavigationBarItem 的按压水波纹掐断。
    // 内容区预留底栏高度：Material3 NavigationBar 80.dp + navigationBars 安全区。
    val compactBottomBarOverlayPadding = if (nativeTabBarEnabled) {
        // 底栏换成系统玻璃 TabBar：宿主是全出血的，UIKit 不会把 tab bar 算进 navigationBars
        // （那里只剩 home indicator），真实高度由宿主写进 session。宿主还没回报时退回安全区，
        // 最坏情况是尾部留白偏小，不会崩。
        maxOf(
            WindowInsets.bottomSystemBars.asPaddingValues().calculateBottomPadding(),
            session.glassTabBarBottomInsetDp.dp,
        )
    } else if (windowClass == WindowClass.Expanded) {
        0.dp
    } else {
        80.dp + WindowInsets.bottomSystemBars.asPaddingValues().calculateBottomPadding()
    }
    // 宽屏（平板横屏/全屏、macOS）：二级页留在壳内，侧栏固定、右侧换内容，对齐桌面分屏。
    // 已经由原生宿主持有的页面始终继续使用宿主路由，旋转/展开不能把返回栈拆成两份。
    val useNativeSecondaryRoutes =
        usesNativeSecondaryRoutes(nativeNavigationEnabled, windowClass, hostedDestination = forcedRouteId != null)
    // 本实例是「被宿主压入的页面」而不是玻璃 tab 根：两者都带 forcedRouteId，
    // 但只有压入页需要返回入口，也不该再为一条不存在的底栏预留高度。
    val isPushedHostDestination = forcedRouteId != null && !nativeTabBarEnabled

    // 未启用原生二级路由时，邮件详情留在邮箱目的地内；
    // 启用原生二级路由时，邮箱列表、邮件详情和写信/回复均由平台页面承载。
    // 邮箱页的唯一返回入口固定在左上角，详情正文不再另放「返回邮件列表」按钮。
    val mailboxReadyState = mailboxState as? MailboxUiState.Ready
    val mailboxInlineDetail = mailboxBackTarget(
        useNativeSecondaryRoutes = useNativeSecondaryRoutes,
        hasSelectedMessage = mailboxReadyState?.selectedMessage != null,
        isMessageLoading = mailboxReadyState?.isMessageLoading == true,
    ) == MailboxBackTarget.LIST
    val mailboxInlineCompose = !useNativeSecondaryRoutes &&
        (mailboxReadyState?.compose?.isLoading == true ||
            mailboxReadyState?.compose?.draft != null ||
            mailboxReadyState?.compose?.failure != null)

    // Navigation 3：应用直接拥有返回栈。一级 tab 总是以 HOME 为根，二/三级页继续压栈；
    // NavDisplay 负责 Android predictive back 与 iOS start-edge back 的连续手势进度。
    val initialRoute = remember(forcedRouteId) {
        forcedRouteId?.toAppRoute() ?: AppSection.HOME
    }
    val backStack = remember(forcedRouteId) { mutableStateListOf<AppRoute>(initialRoute) }
    val currentRoute = backStack.last()
    val section: AppSection = when (currentRoute) {
        ClassroomDetailRoute -> AppSection.CLASSROOMS
        ClassroomOccupancyDetailRoute -> AppSection.CLASSROOM_OCCUPANCY
        HomeworkDetailRoute -> AppSection.HOMEWORK
        ExamDetailRoute -> AppSection.EXAMS
        PhyVlabDetailRoute -> AppSection.PHYVLAB
        CitelDetailRoute -> AppSection.CITEL
        CitelSettingsRoute -> AppSection.CITEL
        MailboxDetailRoute -> AppSection.MAILBOX
        MailboxComposeRoute -> AppSection.MAILBOX
        PhysicsLabSettingsRoute -> AppSection.PHYSICS_LAB
        PhysicsDemoRoute -> AppSection.MORE
        PhysicsDemoSettingsRoute -> AppSection.SETTINGS
        is AppSection -> currentRoute
    }
    val popBackStack: () -> Unit = if (forcedRouteId != null) {
        onCloseNativeRoute
    } else {
        { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    }
    val mailboxBack: () -> Unit = when {
        mailboxInlineCompose -> {
            { scope.launch { mailboxModel.cancelCompose() } }
        }
        mailboxInlineDetail -> mailboxModel::clearSelectedMessage
        else -> popBackStack
    }
    // 宽屏 Android/iPad 邮箱详情留在邮箱一级页内，没有额外 route 可供系统返回弹出；
    // 系统侧滑/返回键必须和左上角返回共用同一目标，否则会直接弹回「更多」根目录。
    val handleBack: () -> Unit = {
        if (
            shouldHandleInlineMailboxBack(
                currentRouteIsMailbox = currentRoute == AppSection.MAILBOX,
                mailboxInlineDetail = mailboxInlineDetail,
                mailboxInlineCompose = mailboxInlineCompose,
            )
        ) {
            mailboxBack()
        } else {
            popBackStack()
        }
    }
    val startMailboxComposeFromTopBar: () -> Unit = {
        if (useNativeSecondaryRoutes) {
            onOpenNativeRoute(MAILBOX_COMPOSE_ROUTE_ID)
        }
        scope.launch { mailboxModel.startCompose() }
    }
    fun navigateToSection(requested: AppSection) {
        val target = when {
            requested == AppSection.CLASSROOMS -> AppSection.CLASSROOM_OCCUPANCY
            settingsState.preferences.aggregateAssignments && requested in setOf(AppSection.HOMEWORK, AppSection.PHYVLAB, AppSection.CITEL) -> AppSection.ASSIGNMENTS
            else -> requested
        }
        if (target == AppSection.PHYVLAB && !phyVlabEnabled) return
        if (target == AppSection.PHYSICS_LAB && !physicsLabEnabled) return
        if (target == AppSection.CITEL && !citelEnabled) return
        if (backStack.lastOrNull() != target) {
            // 先 yield 一帧：让 NavigationBarItem 的 press/ripple 先上屏，
            // 再替换 destination，避免首次点 tab 时内容重组抢掉按压反馈。
            scope.launch {
                yield()
                if (backStack.lastOrNull() == target) return@launch
                if (
                    forcedRouteId != null &&
                        forcedRouteId.toAppRoute() in compactBottomNavSections &&
                        target in compactBottomNavSections
                ) {
                    // 原生 tab 根页面之间互跳：交给宿主切换 tab，本 Compose 栈只保留自己的根，
                    // 否则会出现「内容是成绩、高亮仍是首页」的壳层错位。
                    onSelectNativeTab(target.name)
                } else if (shouldOpenNativeSectionRoute(target.name, useNativeSecondaryRoutes, compactBottomNavSections)) {
                    onOpenNativeRoute(target.name)
                } else if (
                    target !in compactBottomNavSections &&
                        target != AppSection.MORE
                ) {
                    // 「更多」子页：固定为 [更多, 子页]，返回一定回到更多目录。
                    backStack.clear()
                    backStack.add(AppSection.MORE)
                    backStack.add(target)
                } else {
                    // 一级底栏页（含「更多」根目录）：单层替换。
                    // 旧 popUpTo(HOME) 会形成 [HOME, 课表/作业…]，边缘侧滑/系统返回会误回首页。
                    backStack.clear()
                    backStack.add(target)
                }
            }
        }
    }
    fun openExamDetail(examId: Int) {
        examScheduleModel.showExamDetails(examId)
        if (useNativeSecondaryRoutes) {
            onOpenNativeRoute(EXAM_DETAIL_ROUTE_ID)
        } else if (backStack.lastOrNull() != ExamDetailRoute) {
            backStack.add(ExamDetailRoute)
        }
    }
    suspend fun refreshAssignmentSource(source: AssignmentSource) {
        when (source) {
            AssignmentSource.COURSE_PLATFORM -> SessionRefreshCoordinator(reauthenticate = reauthenticateSession,
                probeSession = session.probeSession, onRecoveryStateChanged = { sessionRecoveryInProgress = it })
                .run(homeworkModel::refresh, { homeworkModel.state.value.failure == HomeworkSyncFailure.SESSION_EXPIRED })
            AssignmentSource.PHYVLAB -> if (phyVlabEnabled) phyVlabModel.refresh()
            AssignmentSource.CITEL -> if (citelEnabled) session.citelModel?.refresh()
        }
    }
    val refreshGlobal: suspend (Boolean) -> Unit = globalRefresh@ { forceReauthenticate ->
        val sessionRefresh = SessionRefreshCoordinator(reauthenticate = reauthenticateSession,
            probeSession = session.probeSession, onRecoveryStateChanged = { sessionRecoveryInProgress = it })
        if (forceReauthenticate && !sessionRefresh.recoverBeforeRefresh()) return@globalRefresh
        suspend fun refreshModule(operation: suspend () -> Unit, sessionExpired: () -> Boolean) {
            sessionRefresh.run(operation, sessionExpired)
        }
        coroutineScope {
            launch {
                refreshModule(
                    operation = homeModel::refresh,
                    sessionExpired = { homeModel.state.value.failure == HomeStatusFailure.SESSION_EXPIRED },
                )
            }
            launch {
                sessionRefresh.run(
                    operation = { mailboxModel.refreshUnreadInboxCount() },
                    sessionExpired = {
                        when (val current = mailboxModel.state.value) {
                            MailboxUiState.SessionUnavailable -> true
                            is MailboxUiState.Ready -> current.failure == MailboxFailure.SESSION_EXPIRED
                            else -> false
                        }
                    },
                )
            }
            launch {
                refreshModule(
                    operation = homeworkModel::refresh,
                    sessionExpired = { homeworkModel.state.value.failure == HomeworkSyncFailure.SESSION_EXPIRED },
                )
            }
            launch {
                refreshModule(
                    operation = examScheduleModel::refresh,
                    sessionExpired = { examScheduleModel.state.value.failure == ExamScheduleSyncFailure.SESSION_EXPIRED },
                )
            }
            launch {
                refreshModule(
                    operation = courseScheduleModel::refresh,
                    sessionExpired = { courseScheduleModel.state.value.failure == CourseScheduleSyncFailure.SESSION_EXPIRED },
                )
            }
            if (physicsLabEnabled) launch { session.physicsLabModel?.refresh() }
            launch { session.physicsDemoModel?.refresh() }
            if (citelEnabled) launch { session.citelModel?.refresh() }
            // 用户主动刷新或后台超过阈值后的全局刷新；关闭的模块不访问网络。
            if (phyVlabEnabled) {
                launch { phyVlabModel.refresh() }
            }
            launch {
                refreshModule(
                    operation = gradeModel::refresh,
                    sessionExpired = { gradeModel.state.value.failure == GradeSyncFailure.SESSION_EXPIRED },
                )
                if (gradeModel.state.value.courseTypesByCode == null) {
                    gradeModel.ensureProgramCourseTypes()
                }
            }
        }
    }
    val latestGlobalRefresh = rememberUpdatedState(refreshGlobal)
    LaunchedEffect(session, forcedRouteId, nativeTabBarEnabled) {
        if (!shouldStartPhyVlabAutoSync(forcedRouteId, nativeTabBarEnabled)) return@LaunchedEffect
        session.appForegroundGeneration.collect { generation ->
            if (!session.entryLoggingIn && session.claimAppForegroundRefresh(generation)) latestGlobalRefresh.value(false)
        }
    }
    LaunchedEffect(session, forcedRouteId, nativeTabBarEnabled) {
        if (!shouldStartPhyVlabAutoSync(forcedRouteId, nativeTabBarEnabled)) return@LaunchedEffect
        session.appIdleClickGeneration.collect { generation ->
            if (!session.entryLoggingIn && session.claimAppIdleClickRefresh(generation)) latestGlobalRefresh.value(true)
        }
    }
    val refresh: () -> Unit = {
        scope.launch {
            // 静默自动登录期间会话未就绪，忽略刷新；登录完成后各模块会按自动同步设置初始化。
            if (entryLoggingIn) return@launch
            if (currentRoute == PhyVlabDetailRoute) {
                // 物理在线详情模型自身已在幂等读取失败时恢复一次 CAS；不要在这里重复
                // 套一层全量刷新，否则会清空当前作业选择并重复发起验证码。
                phyVlabModel.loadSelectedActivityDetail(force = true)
                return@launch
            }
            val sessionRefresh = SessionRefreshCoordinator(
                reauthenticate = reauthenticateSession,
                probeSession = session.probeSession,
                onRecoveryStateChanged = { sessionRecoveryInProgress = it },
            )
            suspend fun refreshModule(
                operation: suspend () -> Unit,
                sessionExpired: () -> Boolean,
            ) {
                sessionRefresh.run(operation, sessionExpired)
            }
            when (section) {
                AppSection.HOME -> refreshGlobal(false)
                AppSection.GRADES -> refreshModule(
                    operation = gradeModel::refresh,
                    sessionExpired = { gradeModel.state.value.failure == GradeSyncFailure.SESSION_EXPIRED },
                )
                AppSection.SCHEDULE -> coroutineScope {
                    launch { session.physicsLabModel?.refresh() }
                    launch { session.physicsDemoModel?.refresh() }
                    launch {
                        refreshModule(
                            operation = examScheduleModel::refresh,
                            sessionExpired = { examScheduleModel.state.value.failure == ExamScheduleSyncFailure.SESSION_EXPIRED },
                        )
                    }
                    launch {
                        refreshModule(
                            operation = courseScheduleModel::refresh,
                            sessionExpired = { courseScheduleModel.state.value.failure == CourseScheduleSyncFailure.SESSION_EXPIRED },
                        )
                    }
                    launch {
                        if (gradeModel.state.value.courseTypesByCode == null) gradeModel.ensureProgramCourseTypes()
                    }
                }
                AppSection.EXAMS -> refreshModule(
                    operation = examScheduleModel::refresh,
                    sessionExpired = { examScheduleModel.state.value.failure == ExamScheduleSyncFailure.SESSION_EXPIRED },
                )
                AppSection.HOMEWORK -> refreshModule(
                    operation = homeworkModel::refresh,
                    sessionExpired = { homeworkModel.state.value.failure == HomeworkSyncFailure.SESSION_EXPIRED },
                )
                AppSection.COURSEWARE -> refreshModule(
                    operation = coursewareModel::refresh,
                    sessionExpired = { coursewareModel.state.value.failure == CoursewareSyncFailure.SESSION_EXPIRED },
                )
                AppSection.CLASSROOMS -> classroomModel.refresh()
                AppSection.CLASSROOM_OCCUPANCY -> refreshModule(
                    operation = classroomOccupancyModel::refresh,
                    sessionExpired = {
                        (classroomOccupancyModel.state.value.queryState as? ClassroomOccupancyQueryState.Failed)
                            ?.reason == ClassroomOccupancySyncFailure.SESSION_EXPIRED
                    },
                )
                AppSection.MAILBOX -> refreshModule(
                    operation = mailboxModel::refresh,
                    sessionExpired = {
                        when (val current = mailboxModel.state.value) {
                            MailboxUiState.SessionUnavailable -> true
                            is MailboxUiState.Ready -> current.failure == MailboxFailure.SESSION_EXPIRED
                            else -> false
                        }
                    },
                )
                AppSection.PHYVLAB -> if (phyVlabEnabled) phyVlabModel.refresh()
                AppSection.PHYSICS_LAB -> if (physicsLabEnabled) session.physicsLabModel?.refresh()
                AppSection.CITEL -> if (currentRoute == CitelDetailRoute) citelState.selectedTask?.let { session.citelModel?.selectTask(it) } else if (currentRoute != CitelSettingsRoute && citelEnabled) session.citelModel?.refresh()
                AppSection.ASSIGNMENTS -> coroutineScope { assignmentSync.forEach { status -> launch { refreshAssignmentSource(status.source) } } }
                AppSection.CALENDAR -> Unit
                AppSection.REPORT_CARD_DOWNLOAD -> Unit
                AppSection.SETTINGS -> Unit
                AppSection.MORE -> Unit
            }
        }
    }
    val retryPhyVlabDetail: () -> Unit = {
        scope.launch {
            phyVlabModel.loadSelectedActivityDetail(force = true)
        }
    }

    // 回到前台时，教学周仍未确认就补拉校历：长时间挂起会取消进行中的校历请求，
    // 只恢复 SESSION_EXPIRED 的业务请求修不好「日程加载中」。
    // 已经明确处于 SESSION_EXPIRED 的当前页面再自动重试一次；
    // 失败后保留当前错误页，不弹回登录页，把再次恢复的主动权交给右上角刷新。
    val latestRoute = rememberUpdatedState(currentRoute)
    LaunchedEffect(session) {
        suspend fun retryIfExpired(
            operation: suspend () -> Unit,
            sessionExpired: () -> Boolean,
        ) {
            if (sessionExpired()) operation()
        }

        session.appResumeGeneration.collect { generation ->
            if (!session.claimAppResume(generation) || entryLoggingIn) return@collect
            if (!courseScheduleModel.state.value.weekResolved) {
                courseScheduleModel.ensureCalendarLoaded()
            }
            when (latestRoute.value) {
                PhyVlabDetailRoute -> retryIfExpired(
                    operation = { phyVlabModel.loadSelectedActivityDetail(force = true) },
                    sessionExpired = {
                        phyVlabModel.state.value.detailFailure == PhyVlabSyncFailure.SESSION_EXPIRED
                    },
                )
                AppSection.HOME -> coroutineScope {
                    launch {
                        retryIfExpired(
                            operation = homeModel::refresh,
                            sessionExpired = { homeModel.state.value.failure == HomeStatusFailure.SESSION_EXPIRED },
                        )
                    }
                    launch {
                        retryIfExpired(
                            operation = homeworkModel::refresh,
                            sessionExpired = { homeworkModel.state.value.failure == HomeworkSyncFailure.SESSION_EXPIRED },
                        )
                    }
                    launch {
                        retryIfExpired(
                            operation = examScheduleModel::refresh,
                            sessionExpired = { examScheduleModel.state.value.failure == ExamScheduleSyncFailure.SESSION_EXPIRED },
                        )
                    }
                    launch {
                        retryIfExpired(
                            operation = courseScheduleModel::refresh,
                            sessionExpired = { courseScheduleModel.state.value.failure == CourseScheduleSyncFailure.SESSION_EXPIRED },
                        )
                    }
                    launch { session.physicsLabModel?.refresh() }
                    launch { session.physicsDemoModel?.refresh() }
                    if (phyVlabEnabled) {
                        launch {
                            retryIfExpired(
                                operation = phyVlabModel::refresh,
                                sessionExpired = {
                                    val current = phyVlabModel.state.value
                                    current.failure == PhyVlabSyncFailure.SESSION_EXPIRED || current.casLoginRequired
                                },
                            )
                        }
                    }
                    launch {
                        retryIfExpired(
                            operation = gradeModel::refresh,
                            sessionExpired = { gradeModel.state.value.failure == GradeSyncFailure.SESSION_EXPIRED },
                        )
                    }
                }
                AppSection.GRADES -> retryIfExpired(
                    operation = gradeModel::refresh,
                    sessionExpired = { gradeModel.state.value.failure == GradeSyncFailure.SESSION_EXPIRED },
                )
                AppSection.SCHEDULE -> retryIfExpired(
                    operation = courseScheduleModel::refresh,
                    sessionExpired = { courseScheduleModel.state.value.failure == CourseScheduleSyncFailure.SESSION_EXPIRED },
                )
                AppSection.EXAMS -> retryIfExpired(
                    operation = examScheduleModel::refresh,
                    sessionExpired = { examScheduleModel.state.value.failure == ExamScheduleSyncFailure.SESSION_EXPIRED },
                )
                AppSection.HOMEWORK -> retryIfExpired(
                    operation = homeworkModel::refresh,
                    sessionExpired = { homeworkModel.state.value.failure == HomeworkSyncFailure.SESSION_EXPIRED },
                )
                AppSection.COURSEWARE -> retryIfExpired(
                    operation = coursewareModel::refresh,
                    sessionExpired = { coursewareModel.state.value.failure == CoursewareSyncFailure.SESSION_EXPIRED },
                )
                AppSection.CLASSROOMS -> Unit
                AppSection.CLASSROOM_OCCUPANCY -> retryIfExpired(
                    operation = classroomOccupancyModel::refresh,
                    sessionExpired = {
                        (classroomOccupancyModel.state.value.queryState as? ClassroomOccupancyQueryState.Failed)
                            ?.reason == ClassroomOccupancySyncFailure.SESSION_EXPIRED
                    },
                )
                AppSection.MAILBOX -> retryIfExpired(
                    operation = mailboxModel::refresh,
                    sessionExpired = {
                        when (val current = mailboxModel.state.value) {
                            MailboxUiState.SessionUnavailable -> true
                            is MailboxUiState.Ready -> current.failure == MailboxFailure.SESSION_EXPIRED
                            else -> false
                        }
                    },
                )
                AppSection.PHYVLAB -> if (phyVlabEnabled) {
                    retryIfExpired(
                        operation = phyVlabModel::refresh,
                        sessionExpired = {
                            val current = phyVlabModel.state.value
                            current.failure == PhyVlabSyncFailure.SESSION_EXPIRED || current.casLoginRequired
                        },
                    )
                }
                AppSection.CALENDAR,
                AppSection.REPORT_CARD_DOWNLOAD,
                AppSection.SETTINGS,
                AppSection.MORE,
                -> Unit
                else -> Unit
            }
        }
    }

    if (citelSyncDialogVisible) AppleSheetOrAlert(onDismissRequest = { citelSyncDialogVisible = false },
        title = "CITEL 同步状态", confirmLabel = "刷新", dismissLabel = "关闭",
        onConfirm = { citelSyncDialogVisible = false; scope.launch { session.citelModel?.refresh() } }) {
        Text(citelState.message ?: if (citelState.fromCache) "本轮尚未同步，当前显示上次同步的缓存。" else "尚未同步 CITEL 作业。")
    }
    LaunchedEffect(appCommandBus) {
        appCommandBus?.commands?.collect { command ->
            when (command) {
                AppCommand.NAVIGATE_HOME -> navigateToSection(AppSection.HOME)
                AppCommand.NAVIGATE_GRADES -> navigateToSection(AppSection.GRADES)
                AppCommand.NAVIGATE_SCHEDULE -> navigateToSection(AppSection.SCHEDULE)
                AppCommand.NAVIGATE_EXAMS -> navigateToSection(AppSection.EXAMS)
                AppCommand.NAVIGATE_HOMEWORK -> navigateToSection(AppSection.HOMEWORK)
                AppCommand.NAVIGATE_COURSEWARE -> navigateToSection(AppSection.COURSEWARE)
                AppCommand.NAVIGATE_CLASSROOMS -> navigateToSection(AppSection.CLASSROOMS)
                AppCommand.NAVIGATE_CLASSROOM_OCCUPANCY ->
                    navigateToSection(AppSection.CLASSROOM_OCCUPANCY)
                AppCommand.NAVIGATE_MAILBOX -> navigateToSection(AppSection.MAILBOX)
                AppCommand.NAVIGATE_SETTINGS -> navigateToSection(AppSection.SETTINGS)
                AppCommand.REFRESH_CURRENT -> refresh()
            }
        }
    }

    // 登录未完成（静默自动登录中）不触发任何网络自动同步。
    // 各 Workspace 只会 initialize(refreshFromNetwork=false) 灌缓存；真正的自动同步只在这里启动。
    LaunchedEffect(gradeModel, entryLoggingIn) {
        if (entryLoggingIn) return@LaunchedEffect
        val sessionRefresh = SessionRefreshCoordinator(
            reauthenticate = reauthenticateSession,
            probeSession = session.probeSession,
            // The entry login has just verified this session. Start module work
            // immediately; expired-session recovery still runs after a failure.
            sessionVerifiedAtStart = true,
            onRecoveryStateChanged = { sessionRecoveryInProgress = it },
        )
        sessionRefresh.run(
            operation = { SyncTiming.measure("startup.grades") { gradeModel.initialize(refreshFromNetwork = true) } },
            sessionExpired = { gradeModel.state.value.failure == GradeSyncFailure.SESSION_EXPIRED },
        )
        // 网络失败的重试已在 GradeScreenModel.initialize 内（最多 3 次）。
        if (gradeModel.state.value.courseTypesByCode == null) {
            gradeModel.ensureProgramCourseTypes()
        }
    }
    LaunchedEffect(
        homeworkModel,
        examScheduleModel,
        courseScheduleModel,
        entryLoggingIn,
    ) {
        if (entryLoggingIn) return@LaunchedEffect
        val sessionRefresh = SessionRefreshCoordinator(
            reauthenticate = reauthenticateSession,
            probeSession = session.probeSession,
            // The entry login has just verified this session. Start module work
            // immediately; expired-session recovery still runs after a failure.
            sessionVerifiedAtStart = true,
            onRecoveryStateChanged = { sessionRecoveryInProgress = it },
        )
        coroutineScope {
            launch {
                // 作业自动同步的失败重试在 ScreenModel 内（最多 3 次），与课表一致。
                sessionRefresh.run(
                    operation = { SyncTiming.measure("startup.homework") { homeworkModel.initialize(refreshFromNetwork = true) } },
                    sessionExpired = {
                        homeworkModel.state.value.failure == HomeworkSyncFailure.SESSION_EXPIRED
                    },
                )
            }
            launch {
                sessionRefresh.run(
                    operation = { SyncTiming.measure("startup.exams") { examScheduleModel.initialize(refreshFromNetwork = true) } },
                    sessionExpired = {
                        examScheduleModel.state.value.failure == ExamScheduleSyncFailure.SESSION_EXPIRED
                    },
                )
                if (examScheduleModel.state.value.failure != null) {
                    delay(LOGIN_SYNC_RETRY_DELAY_MILLIS)
                    sessionRefresh.run(
                        operation = { examScheduleModel.refresh() },
                        sessionExpired = {
                            examScheduleModel.state.value.failure == ExamScheduleSyncFailure.SESSION_EXPIRED
                        },
                    )
                }
            }
            launch {
                // 课表：登录成功后才网络同步；失败重试在 ScreenModel 内（最多 3 次）。
                // 先灌入缓存，课表与校历随后由模型并行同步；周数仍只由校历确认。
                courseScheduleModel.initialize(refreshFromNetwork = false)
                sessionRefresh.run(
                    operation = { SyncTiming.measure("startup.schedule") { courseScheduleModel.initialize(refreshFromNetwork = true) } },
                    sessionExpired = {
                        courseScheduleModel.state.value.failure ==
                            CourseScheduleSyncFailure.SESSION_EXPIRED
                    },
                )
            }
        }
    }

    // 首页「新邮件」徽标：登录完成后探测一次收件箱实际未读数（Coremail 列表），
    // 之后由首页刷新和读信动作维护；探测失败时首页回退 MIS 聚合值。
    LaunchedEffect(mailboxModel, entryLoggingIn) {
        if (entryLoggingIn) return@LaunchedEffect
        SyncTiming.measure("startup.mail-unread") { mailboxModel.refreshUnreadInboxCount() }
    }

    // 独立校园网数据源由稳定首页宿主启动，原生二级页面仅共享状态。
    LaunchedEffect(session.citelModel, citelEnabled, entryLoggingIn, forcedRouteId, nativeTabBarEnabled) {
        val model = session.citelModel ?: return@LaunchedEffect
        model.setEnabled(citelEnabled)
        if (citelEnabled && !model.state.value.configured) settingsModel.setCitelEnabled(false)
        if (citelEnabled && !entryLoggingIn && shouldStartPhyVlabAutoSync(forcedRouteId, nativeTabBarEnabled)) {
            SyncTiming.measure("startup.citel") { model.refreshForAppEntry(0) }
        }
        // Only initial App entry triggers this; foreground/focus changes are not keys.
        // Reads/submissions recover the session only after confirmed expiry.
    }
    // 与物理实验/CITEL 同一门控：iOS 原生 Tab 下首页宿主带 forcedRouteId=HOME，仍需自动同步。
    LaunchedEffect(session.physicsDemoModel, entryLoggingIn, forcedRouteId, nativeTabBarEnabled) {
        val demo = session.physicsDemoModel ?: return@LaunchedEffect
        demo.initialize()
        if (!entryLoggingIn && shouldStartPhyVlabAutoSync(forcedRouteId, nativeTabBarEnabled)) {
            SyncTiming.measure("startup.physics-demo") { demo.refresh(onlyIfUnsynced = true) }
        }
    }
    LaunchedEffect(session.physicsLabModel, physicsLabEnabled, entryLoggingIn, forcedRouteId, nativeTabBarEnabled) {
        val labModel = session.physicsLabModel ?: return@LaunchedEffect
        labModel.setEnabled(physicsLabEnabled)
        if (physicsLabEnabled && !labModel.state.value.configured) settingsModel.setPhysicsLabEnabled(false)
        if (physicsLabEnabled && !entryLoggingIn && shouldStartPhyVlabAutoSync(forcedRouteId, nativeTabBarEnabled)) {
            SyncTiming.measure("startup.physics-lab") { labModel.refresh() }
        }
    }

    // 物理在线总开关同时控制同步与底栏入口。
    LaunchedEffect(phyVlabModel, phyVlabEnabled, entryLoggingIn, forcedRouteId, nativeTabBarEnabled) {
        // 原生作业详情页复用同一个模型，但不能在这里重新刷新整份课程数据；
        // refresh 成功会清空 selectedActivity，导致详情页退化成“未选择物理在线作业”。
        if (
            entryLoggingIn ||
            !phyVlabEnabled ||
            !shouldStartPhyVlabAutoSync(
                forcedRouteId = forcedRouteId,
                nativeTabBarEnabled = nativeTabBarEnabled,
            )
        ) return@LaunchedEffect
        phyVlabModel.initialize(refreshFromNetwork = false)
        SyncTiming.measure("startup.physical-online") { phyVlabModel.refresh() }
    }

    // 进入主界面后静默检查一次更新（对齐原安卓启动时自动检测）：
    // 仅发现新版本才弹「前往下载」；失败或无更新不打扰用户。
    // settingsModel 与 login 同生命周期、按 studentId remember，每次登录各弹一次。
    LaunchedEffect(settingsModel, entryLoggingIn) {
        if (entryLoggingIn) return@LaunchedEffect
        settingsModel.checkForUpdate(silentOnMiss = true)
    }

    // 检查结果弹窗放在整个壳内容之后渲染：发现新版本时无论当前在哪个页面都能看到
    // 「前往下载」，不依赖用户停留在设置页（设置页内按钮触发的结果也走同一弹窗）。
    val showGuideOnThisHost = !entryLoggingIn &&
        (forcedRouteId == null || forcedRouteId == AppSection.HOME.name)
    val showRedesignGuide = showGuideOnThisHost &&
        !settingsState.preferences.redesignGuideAcknowledged
    val showCitelGuide = showGuideOnThisHost && !showRedesignGuide &&
        !settingsState.preferences.citelGuideAcknowledged
    val showAssignmentAggregateGuide = showGuideOnThisHost && !showRedesignGuide && !showCitelGuide &&
        !settingsState.preferences.assignmentAggregateGuideAcknowledged
    val blockingGuideVisible = showRedesignGuide || showCitelGuide || showAssignmentAggregateGuide
    when {
        showRedesignGuide -> RedesignGuideDialog(settingsState.saveFailed, settingsModel::acknowledgeRedesignGuide)
        showCitelGuide -> CitelGuideDialog(settingsState.saveFailed, settingsModel::acknowledgeCitelGuide)
        showAssignmentAggregateGuide -> AssignmentAggregateGuideDialog(
            settingsState.saveFailed,
            settingsModel::acknowledgeAssignmentAggregateGuide,
        )
        else -> AppUpdateResultDialog(settingsState.updateCheck, settingsModel::dismissUpdateCheck, settingsModel::postponeUpdate)
    }
    partialSyncFailureDialogItems?.takeUnless { blockingGuideVisible }?.let { items ->
        PartialSyncFailureDialog(
            failedItems = items,
            onRetry = {
                partialSyncFailureDialogItems = null
                refresh()
            },
            onDismiss = { partialSyncFailureDialogItems = null },
        )
    }
    if (homeSyncDialogVisible && !blockingGuideVisible) {
        HomeSyncDetailsDialog(
            title = homeSyncDialogTitle(
                isLoggingIn = entryLoggingIn,
                isSyncing = homeSyncInProgress,
                hasFailures = homeSyncFailureItems.isNotEmpty(),
            ),
            items = homeSyncItems,
            canRetry = !entryLoggingIn && homeSyncFailureItems.isNotEmpty() && !homeSyncInProgress,
            onRetry = {
                homeSyncDialogVisible = false
                refresh()
            },
            onDismiss = { homeSyncDialogVisible = false },
        )
    }
    gradeState.pendingChangeNotice?.takeUnless { blockingGuideVisible }?.let { notice ->
        GradeChangeNoticeDialog(
            changes = notice,
            onDismiss = gradeModel::dismissChangeNotice,
            onOpenGrades = {
                gradeModel.dismissChangeNotice()
                navigateToSection(AppSection.GRADES)
            },
        )
    }

    // 页面骨架（顶栏 + 下拉刷新）包在各目的地内部而非 NavHost 外层：
    // 1) 外层容器若随当前页面切换（如“更多”页不可刷新、考试安排页可刷新），NavHost 会在
    //    转场瞬间移出/移入 composition 被销毁重建，表现为点击进出页面没有任何转场动画；
    // 2) 顶栏随页面一起参与转场才是原生观感——整个屏幕一起动，而非标题栏先跳变；
    // 3) 必须铺不透明背景：页面透明时转场中上下两层互相穿透（iOS 上会露出灰色窗口底色），
    //    看起来像层级重叠遮挡。
    @Composable
    fun DestinationPage(
        title: String,
        expanded: Boolean,
        refreshable: Boolean,
        /** 首页可先展示同步面板，再开始刷新；其它页沿用静默刷新。 */
        refreshAction: (() -> Unit)? = null,
        isRefreshing: Boolean,
        showBack: Boolean,
        modifier: Modifier,
        onBack: (() -> Unit)? = null,
        /** 该页返回动作不只是出栈（如写信需先取消草稿）：此时不交给宿主导航栏。 */
        ownsBackAction: Boolean = false,
        /** 空闲时右上角状态文案（如课表「已同步/未同步」）；登录中/同步中优先覆盖。 */
        idleStatusText: String? = null,
        /** 页面级动作，显示在同步状态胶囊旁（M12 课程表加入日历）。 */
        topBarAction: (@Composable () -> Unit)? = null,
        /**
         * 声明式的页面级动作（文字 + 回调）。与 [topBarAction] 的区别只在于：声明式的那份
         * 宿主能画进系统导航栏右侧，Compose lambda 那份画不了。
         */
        topBarActionLabel: String? = null,
        onTopBarActionClick: (() -> Unit)? = null,
        /** 状态胶囊点击动作；首页用于查看同步详情，失败时不在点击瞬间触发重试。 */
        onStatusClick: (() -> Unit)? = null,
        /** 未显式提供 [onStatusClick] 时，首页聚合同步失败可由此生成失败详情弹窗。 */
        syncFailureItems: List<String> = emptyList(),
        /**
         * 本页内容滚进原生导航栏后面（真原生模糊用）。
         *
         * 为 true 且原生标题栏接管时，不再用实心 Spacer 占位，改由滚动容器吃
         * [LocalTopBarClearance] 顶边距：列表从屏幕顶开始画、首项靠顶边距让开，
         * 滚起来后内容穿进栏后，原生玻璃才有东西可折射。默认 false：未迁移的页面
         * 保持原来的 Spacer 布局，零回归。
         */
        scrollUnderTopBar: Boolean = false,
        /**
         * 本页不吃「内容伸进原生栏」那条特例，改回真实布局内边距。
         *
         * 课程表「色块概览」这类**不可纵向滚动**的全览表格用它：整张表停在栏下方，
         * 另加 8.dp 呼吸（与底栏镜像）。
         */
        keepsTopBarInset: Boolean = false,
        /** 只压矮本页自绘标题栏，不改其它页。 */
        denseTopBar: Boolean = false,
        /**
         * 原生栏不随滚动变化（无透明 ↔ 玻璃过渡），栏保持顶部时的纯色样子。
         *
         * 给视口到不了栏后的页面用（课表/课件/教室详情/占用详情：固定头挡在列表上方，
         * 内容永远滚不进栏后，过渡只会凭空闪一下）。布局保持 Spacer 老样子，
         * 顶栏右侧同步状态照常进原生栏；与其他页互不影响，逐页开关。
         */
        staticTopBar: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        val effectiveRefreshAction = refreshAction ?: refresh
        // iOS 内容视口延伸到屏幕底，底部净空仅交给滚动内容末尾消费。
        // 宿主上报的是整个 tab bar frame 的高度，已经包含其底部安全区，不重复叠加。
        val reserveBottomBarSpace = !session.systemManagedContentBounds && !expanded && !showBack && !isPushedHostDestination
        val systemBottomInset = maxOf(
            WindowInsets.bottomSystemBars.asPaddingValues().calculateBottomPadding(),
            session.systemBottomInsetDp.dp,
        )
        val bottomContentClearance = destinationBottomClearance(
            expanded = expanded,
            hasBottomBar = reserveBottomBarSpace,
            barInset = compactBottomBarOverlayPadding,
            systemInset = systemBottomInset,
            contentGap = if (platform.family == PlatformFamily.Android) 0.dp else 8.dp,
        )
        // Android secondary pages draw behind the transparent gesture area;
        // their scroll content still consumes the system inset at its tail.
        val usesBottomUnderlap = platform.family == PlatformFamily.IOS ||
            (platform.family == PlatformFamily.Android && !reserveBottomBarSpace)
        val bottomLayoutInset = if (usesBottomUnderlap) 0.dp else bottomContentClearance
        Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(bottom = bottomLayoutInset),
            ) {
                // 紧凑/宽屏统一：标题 + 右上同步胶囊。宽屏不再在页内重复「同步××」按钮。
                val failureStatusClick = if (
                    idleStatusText in partialSyncFailureStatusTexts && syncFailureItems.isNotEmpty()
                ) {
                    { partialSyncFailureDialogItems = syncFailureItems }
                } else {
                    null
                }
                // 原生标题栏生效的两条路：被宿主 push 的二级页，以及玻璃壳的一级 tab 根页
                // （M17 之后一级页的紧凑原生标题与同步动作也交给系统导航栏）。
                // 返回动作不只是出栈的页（写信要先取消草稿）例外，它整条顶栏都留在页内。
                val nativeTitleBarActive =
                    useNativeTitleBar && !ownsBackAction && (showBack || nativeTabBarEnabled)
                // 刷新、同步状态、页面级动作现在都能进系统栏右侧：状态文案自己就是入口
                // （首页「同步失败」点开同步详情）也一并带过去，不再因为顶栏被撤掉而丢入口。
                // 只有仍由 Compose 绘制的自定义操作组没法声明成原生项，出现时整行仍留在页内。
                val statusClickHandler = onStatusClick ?: failureStatusClick
                val hostBarTakesOver = nativeTitleBarActive && topBarAction == null
                val refreshHandledByHost = hostBarTakesOver && refreshable
                val statusHandledByHost = hostBarTakesOver && (idleStatusText != null || entryLoggingIn)
                val extraHandledByHost = hostBarTakesOver && topBarActionLabel != null
                val keepsComposeTopBar = topBarAction != null ||
                    (topBarActionLabel != null && !extraHandledByHost) ||
                    (idleStatusText != null && !statusHandledByHost) ||
                    (refreshable && !refreshHandledByHost) ||
                    (entryLoggingIn && !hostBarTakesOver)
                // 声明式的页面动作：宿主接手就画进系统栏，否则由 Compose 顶栏自己画同一份。
                val declarativeActionLabel = topBarActionLabel
                val declarativeActionClick = onTopBarActionClick
                val composeAction: (@Composable () -> Unit)? = when {
                    topBarAction != null -> topBarAction
                    declarativeActionLabel == null ||
                        declarativeActionClick == null ||
                        extraHandledByHost -> null
                    else -> {
                        {
                            TopBarCalendarAction(
                                label = declarativeActionLabel,
                                onClick = declarativeActionClick,
                            )
                        }
                    }
                }
                if (nativeTitleBarActive) {
                    SideEffect { onNativeTitleChanged(title) }
                }
                val nativeSyncBusy = isRefreshing || sessionRecoveryInProgress
                val topBarTransitionHeight = 52.dp
                val topBarTransitionHeightPx = with(LocalDensity.current) { topBarTransitionHeight.toPx() }
                val topScrollTrackingActive = nativeTitleBarActive && !keepsComposeTopBar && !staticTopBar
                // 页面上报的真实滚动偏移（像素），见 LocalReportTopScroll。直接读列表状态，
                // fling/跳转/回顶都不会漂移；上报源只在 composition 里读它，重组开销与之前相当。
                // lambda 用 remember 稳住实例，避免每次重组都让消费方连带重组。
                val topScrollOffsetPx = remember(title) { mutableFloatStateOf(0f) }
                val reportTopScroll: (Float) -> Unit = remember(title) {
                    { offsetPx -> topScrollOffsetPx.floatValue = offsetPx }
                }
                // 真原生 underlap：接管 + opt-in 的页面跳过实心 Spacer，内容从屏幕顶开始画；
                // 顶边距取宿主实测栏高与状态栏二者的较大值，首帧（宿主未回报前）也不错位。
                // 非玻璃壳/未 opt-in 页面 clearance 恒 0，走原来的 Spacer/自绘顶栏，老样子。
                // keepsTopBarInset 的不可滚页面改回布局内边距（停在栏下 + 8.dp 呼吸）。
                val useTopUnderlap = topScrollTrackingActive && scrollUnderTopBar
                val measuredTopInset = if (session.systemManagedContentBounds) session.glassTopBarInsetDp.dp else maxOf(
                    WindowInsets.topSystemBars.asPaddingValues().calculateTopPadding(),
                    session.glassTopBarInsetDp.dp,
                )
                val topBarClearance = if (useTopUnderlap && !keepsTopBarInset) {
                    measuredTopInset
                } else {
                    0.dp
                }
                // Read the Compose scroll state during composition so page-reported offsets
                // reach the fallback glass surface. Native-scrolling pages instead supply
                // a consumer for actual UIKit deltas; no position estimates cross that bridge.
                val nativeScrollProgress =
                    if (topScrollTrackingActive && scrollUnderTopBar && topBarTransitionHeightPx > 0f) {
                        (topScrollOffsetPx.floatValue / topBarTransitionHeightPx).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                // Single primary scrolling pages share UIKit physics, including pushed
                // details. Static/split-panel pages retain their independent scrollers.
                val nativeScrollingEnabled = nativeTitleBarActive && hostBarTakesOver &&
                    platform.family == PlatformFamily.IOS && session.systemManagedContentBounds &&
                    useTopUnderlap && !keepsTopBarInset
                var nativeScrollController by remember(title) { mutableStateOf<NativeScrollController?>(null) }
                val reportNativeScrollController: (NativeScrollController?) -> Unit = remember(title) {
                    { nativeScrollController = it }
                }
                // Read during composition so range/end updates invalidate this host.
                val reportedNativeScrollController = nativeScrollController.takeIf { nativeScrollingEnabled }
                SideEffect {
                    val nativeBarAction = when {
                        !hostBarTakesOver -> null
                        entryLoggingIn || refreshable || idleStatusText != null || topBarActionLabel != null ->
                            NativeBarAction(
                                status = when {
                                    entryLoggingIn -> "登录中"
                                    nativeSyncBusy -> "同步中"
                                    else -> idleStatusText ?: ""
                                },
                                canRefresh = refreshable && !entryLoggingIn,
                                busy = entryLoggingIn || nativeSyncBusy,
                                label = when {
                                    entryLoggingIn -> "登录中"
                                    nativeSyncBusy -> "刷新中"
                                    else -> "刷新"
                                },
                                onClick = effectiveRefreshAction,
                                onStatusClick = statusClickHandler,
                                extraLabel = topBarActionLabel,
                                onExtraClick = onTopBarActionClick,
                                scrollProgress = nativeScrollProgress,
                                scrollController = reportedNativeScrollController,
                            )
                        else ->
                            // No visible bar item is needed, but UIKit still
                            // needs the edge state for a root such as More.
                            NativeBarAction(
                                status = "",
                                canRefresh = false,
                                busy = false,
                                label = "",
                                onClick = {},
                                scrollProgress = nativeScrollProgress,
                                scrollController = reportedNativeScrollController,
                            )
                    }
                    onNativeActionChanged(nativeBarAction)
                }
                if (useTopUnderlap) {
                    // 内容自己从屏幕顶画起，首项靠 LocalTopBarClearance 让开；这里不占位。
                } else if (nativeTitleBarActive && !keepsComposeTopBar) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(measuredTopInset),
                    )
                } else {
                    CompactAppTopBar(
                        title = if (nativeTitleBarActive) "" else title,
                        isRefreshing = isRefreshing || sessionRecoveryInProgress,
                        isLoggingIn = entryLoggingIn,
                        idleStatusText = idleStatusText,
                        statusAsText = title == AppSection.CITEL.title || title == AppSection.ASSIGNMENTS.title,
                        dense = denseTopBar,
                        action = composeAction.takeUnless { declarativeActionLabel == "今" },
                        leadingAction = composeAction.takeIf { declarativeActionLabel == "今" && platform.family == PlatformFamily.IOS },
                        titleAction = composeAction.takeIf { declarativeActionLabel == "今" && platform.family != PlatformFamily.IOS },
                        // 可刷新页：右上角状态胶囊旁放刷新按钮；不再下拉刷新（保平台原生过滚）。
                        onRefresh = if (refreshable) effectiveRefreshAction else null,
                        onStatusClick = onStatusClick ?: failureStatusClick,
                        onBack = if (showBack && !nativeTitleBarActive) {
                            onBack ?: popBackStack
                        } else {
                            null
                        },
                    )
                }
                // Scroll progress comes from the page's own list state
                // (LocalReportTopScroll), not from gesture accumulation: flings, snaps
                // and programmatic jumps all land in the list state itself, so the glass
                // persists until content truly leaves the bar.
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().then(
                        if (useTopUnderlap && keepsTopBarInset) {
                            Modifier.padding(top = measuredTopInset + 8.dp)
                        } else {
                            Modifier
                        },
                    ),
                ) {
                    CompositionLocalProvider(
                        LocalBottomBarClearance provides if (usesBottomUnderlap) bottomContentClearance else 0.dp,
                        LocalTopBarClearance provides topBarClearance,
                        LocalReportTopScroll provides reportTopScroll,
                        LocalNativeScrollControllerReporter provides reportNativeScrollController.takeIf { nativeScrollingEnabled },
                    ) {
                        content()
                    }
                    // 同步进度条悬浮在内容顶部，不参与 Column 布局，避免其出现时
                    // 把下方内容挤矮一丝。
                    // When UIKit owns the native title bar it also owns the
                    // busy indicator. Keep the Compose progress line only for
                    // fallback/self-drawn top bars; otherwise it duplicates the
                    // native spinner and makes the page look like a KMP overlay.
                    if ((isRefreshing || sessionRecoveryInProgress) &&
                        !(nativeTitleBarActive && !keepsComposeTopBar)
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    // Navigation 3 的目的地渲染器：compact/expanded 两套布局共用，
    // entryProvider 只负责把类型安全 route 交给这里，页面业务保持不变。
    @Composable
    fun SectionDestination(
        route: AppRoute,
        expanded: Boolean,
        modifier: Modifier,
        usesLegacySmartTransport: Boolean,
    ) {
        when (route) {
            AppSection.HOME -> DestinationPage(
                title = AppSection.HOME.title,
                topBarActionLabel = "今".takeUnless { homeTodaySelected },
                onTopBarActionClick = { homeTodayRequest += 1 },
                expanded = expanded,
                refreshable = true,
                refreshAction = {
                    homeSyncDialogVisible = true
                    refresh()
                },
                isRefreshing = homeSyncInProgress,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                // 与成绩/课表一致：同步态并入顶栏右上胶囊，勿只留孤图标。
                // 首页聚合邮件/校园卡自身失败才写「同步失败」；作业等切片失败写「部分同步失败」，
                // 避免作业红条把整个首页说成全挂。
                idleStatusText = homeIdleStatusText(
                    homeFailed = homeState.failure != null,
                    hasWaitingSource = homeSyncItems.any { it.state == HomeSyncItemState.WAITING },
                    homeworkFailed = homeworkState.failure != null,
                    examFailed = examState.failure != null,
                    courseFailed = courseState.failure != null,
                    phyVlabFailed = (phyVlabEnabled &&
                        (phyVlabState.failure != null || phyVlabState.casLoginRequired)) ||
                        (physicsLabEnabled && physicsLabState.failed) || (physicsDemoState.enabled && physicsDemoState.failed) || (citelState.enabled && citelState.failed),
                    hasAnySource = homeworkState.source != null ||
                        examState.source != null ||
                        courseState.source != null ||
                        homeState.status != null ||
                        (phyVlabEnabled && (
                            phyVlabState.courses.isNotEmpty() ||
                                phyVlabState.agendaEvents.isNotEmpty()
                            )),
                ),
                onStatusClick = { homeSyncDialogVisible = true },
                syncFailureItems = homeSyncFailureItems,
                scrollUnderTopBar = true,
            ) {
                CompositionLocalProvider(
                    team.bjtuss.bjtuselfservice.shared.feature.home.LocalHomeSchedule provides
                        team.bjtuss.bjtuselfservice.shared.feature.home.HomeSchedulePresentation(
                            courses = courseState.courses,
                            supplementalCourses = homeScheduleEvents,
                            academicWeeks = courseState.homeAcademicWeeks,
                            currentWeek = courseState.currentWeek,
                            today = homeworkState.now.date,
                            onOpenSchedule = { navigateToSection(AppSection.SCHEDULE) },
                            courseTypesByCode = gradeState.courseTypesByCode,
                        ),
                ) {
                    HomeWorkspace(
                        todayRequest = homeTodayRequest,
                        onTodaySelectedChanged = { homeTodaySelected = it },
                        model = homeModel,
                        platform = platform,
                        expanded = expanded,
                        mailboxUnread = mailboxUnread,
                        holdNetwork = entryLoggingIn,
                        homework = homeworkState.homework,
                        exams = examState.exams,
                        phyVlabEvents = (if (phyVlabEnabled) phyVlabState.agendaEvents else emptyList()) + citelEvents,
                        currentWeek = courseState.currentWeek,
                        academicWeeks = courseState.homeAcademicWeeks,
                        // 周数只在被校历确认后显示：确认前统一「日程加载中」，
                        // 拿到确切结果后一次显示最终值，不再出现中间值弹跳。
                        isWeekResolved = courseState.weekResolved,
                        now = homeworkState.now,
                        timeZone = homeworkState.timeZone,
                        isAgendaLoading = homeworkState.isLoading || examState.isLoading ||
                            courseState.isLoading || (phyVlabEnabled && phyVlabState.isLoading) || citelState.refreshing,
                        onOpenMailbox = { navigateToSection(AppSection.MAILBOX) },
                        onOpenHomework = { navigateToSection(AppSection.HOMEWORK) },
                        onOpenHomeworkDetail = { homework ->
                            val key = homework.stableKey()
                            homeworkModel.selectHomework(key)
                            scope.launch { team.bjtuss.bjtuselfservice.shared.feature.homework.loadHomeworkDetailsWithRecovery(homeworkModel, key, reauthenticateSession) }
                            if (useNativeSecondaryRoutes) {
                                onOpenNativeRoute(HOMEWORK_DETAIL_ROUTE_ID)
                            } else if (backStack.lastOrNull() != HomeworkDetailRoute) {
                                backStack.add(HomeworkDetailRoute)
                            }
                        },
                        onOpenExams = { exam -> openExamDetail(exam.id) },
                        onOpenPhyVlab = { event ->
                            if (event.id.startsWith("citel-")) {
                                // 与作业/考试一致：直接进入该 CITEL 作业的二级详情页。
                                val taskId = event.id.substringAfterLast('-').toIntOrNull()
                                val task = citelState.tasks.firstOrNull { it.id == taskId }
                                val model = session.citelModel
                                if (task != null && model != null) {
                                    model.showTask(task)
                                    if (useNativeSecondaryRoutes) {
                                        onOpenNativeRoute(CITEL_DETAIL_ROUTE_ID)
                                    } else if (backStack.lastOrNull() != CitelDetailRoute) {
                                        backStack.add(CitelDetailRoute)
                                    }
                                } else {
                                    navigateToSection(AppSection.CITEL)
                                }
                            } else {
                                // 首页事件可能来自任一物理在线课程；当前页 activities 只含所选课程。
                                val activity = phyVlabModel.findActivityForEvent(event)
                                if (activity != null) {
                                    phyVlabModel.showActivityDetails(activity)
                                    if (useNativeSecondaryRoutes) {
                                        onOpenNativeRoute(PHYVLAB_DETAIL_ROUTE_ID)
                                    } else if (backStack.lastOrNull() != PhyVlabDetailRoute) {
                                        backStack.add(PhyVlabDetailRoute)
                                    }
                                } else {
                                    event.eventUrl?.let { onOpenExternalUrl(upgradePhyVlabUrlToHttps(it)) }
                                }
                            }
                        },
                        changes = homeChanges,
                        onClearAllChanges = { scope.launch { homeChangeFeed.clear() } },
                        onClearChangeDomain = { domain -> scope.launch { homeChangeFeed.clear(domain) } },
                        onOpenChangeDomain = { domain -> navigateToSection(domain.toAppSection()) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            AppSection.GRADES -> DestinationPage(
                title = AppSection.GRADES.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = gradeState.isRefreshing,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
                // 与课表一致：同步态在顶栏右上，banner 内只放成绩摘要与筛选入口。
                idleStatusText = when {
                    gradeState.failure != null -> "同步失败"
                    gradeState.source == GradeContentSource.NETWORK -> "已同步"
                    gradeState.source == GradeContentSource.CACHE -> "已同步"
                    else -> "未同步"
                },
            ) {
                GradeWorkspace(
                    state = gradeState,
                    expanded = expanded,
                    model = gradeModel,
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.SCHEDULE -> DestinationPage(
                title = AppSection.SCHEDULE.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = courseState.isRefreshing || examState.isRefreshing || physicsLabState.refreshing || physicsDemoState.refreshing,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                // 同步状态放在顶栏右上；有失败横幅时不要仍显示「已同步」。
                idleStatusText = when {
                    courseState.failure != null -> "同步失败"
                    courseState.source == CourseScheduleContentSource.NETWORK -> "已同步"
                    courseState.source == CourseScheduleContentSource.CACHE -> "已同步"
                    else -> "未同步"
                },
                // 「添加到日历」声明成文字 + 回调，好让系统导航栏能直接画它。
                topBarActionLabel =
                    if (systemCalendarGateway.isAvailable) "添加到日历" else "导出",
                onTopBarActionClick = { showCourseCalendarExport = true },
                // 固定周表仅缩小表格内部；按日列表通过滚动内容末尾避让胶囊。
                // 顶栏 underlap 暂不启用：按日列表视口被固定的摘要/模式/日期三段头挡在栏下，
                // 内容到不了栏后；硬上只会把三段头顶进状态栏。等表头随滚 redesign 再议。
                // 且关闭滚动过渡：栏后永远是纯色，过渡只会凭空闪一下。
                staticTopBar = true,
            ) {
                CourseScheduleWorkspace(
                    state = scheduleStateWithEvents,
                    courseTypesByCode = gradeState.courseTypesByCode,
                    expanded = expanded,
                    model = courseScheduleModel,
                    fileGateway = homeworkFileGateway,
                    systemCalendarGateway = systemCalendarGateway,
                    showCalendarExportSheet = showCourseCalendarExport,
                    onDismissCalendarExport = { showCourseCalendarExport = false },
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize().then(
                        if (courseState.compactViewMode == CourseCompactViewMode.WEEK)
                            Modifier.padding(bottom = LocalBottomBarClearance.current)
                        else Modifier,
                    ),
                )
            }
            AppSection.EXAMS -> DestinationPage(
                title = AppSection.EXAMS.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = examState.isRefreshing,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
                // 与成绩/作业一致：同步态顶栏右上，banner 内放类型筛选入口。
                idleStatusText = when {
                    examState.failure != null -> "同步失败"
                    examState.source == ExamScheduleContentSource.NETWORK -> "已同步"
                    examState.source == ExamScheduleContentSource.CACHE -> "已同步"
                    else -> "未同步"
                },
            ) {
                ExamScheduleWorkspace(
                    state = examState,
                    expanded = expanded,
                    model = examScheduleModel,
                    fileGateway = homeworkFileGateway,
                    systemCalendarGateway = systemCalendarGateway,
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ExamDetailRoute -> DestinationPage(
                title = "考试详情",
                expanded = expanded,
                refreshable = false,
                isRefreshing = false,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
            ) {
                ExamScheduleDetailWorkspace(
                    state = examState,
                    model = examScheduleModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.ASSIGNMENTS -> DestinationPage(
                title = "作业", expanded = expanded, refreshable = true,
                refreshAction = { aggregateSyncDialogVisible = true; refresh() },
                isRefreshing = assignmentSync.any { it.busy }, idleStatusText = aggregateAssignmentSyncStatus(assignmentSync),
                onStatusClick = { aggregateSyncDialogVisible = true },
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier, scrollUnderTopBar = true,
            ) {
                AggregatedAssignmentWorkspace(aggregatedAssignments, assignmentSync, session.aggregateFilterStore,
                    onInitialize = {
                        homeworkModel.initialize(refreshFromNetwork = false)
                        if (phyVlabEnabled) phyVlabModel.initialize(refreshFromNetwork = false)
                        if (citelEnabled) session.citelModel?.let { model ->
                            model.setEnabled(true)
                            SyncTiming.measure("feature.citel") { model.refreshForAppEntry(0) }
                        }
                    }, onOpen = { item ->
                        val detailRoute = when (item.source) {
                            AssignmentSource.COURSE_PLATFORM -> {
                                item.homework?.let { homework ->
                                    homeworkModel.selectHomework(homework.stableKey())
                                    scope.launch { team.bjtuss.bjtuselfservice.shared.feature.homework.loadHomeworkDetailsWithRecovery(homeworkModel, homework.stableKey(), reauthenticateSession) }
                                }
                                HomeworkDetailRoute
                            }
                            AssignmentSource.PHYVLAB -> { item.physical?.let(phyVlabModel::showActivityDetails); PhyVlabDetailRoute }
                            AssignmentSource.CITEL -> { item.citel?.let { session.citelModel?.showTask(it) }; CitelDetailRoute }
                        }
                        val routeId = when (detailRoute) {
                            HomeworkDetailRoute -> HOMEWORK_DETAIL_ROUTE_ID
                            PhyVlabDetailRoute -> PHYVLAB_DETAIL_ROUTE_ID
                            else -> CITEL_DETAIL_ROUTE_ID
                        }
                        if (!expanded) {
                            if (useNativeSecondaryRoutes) onOpenNativeRoute(routeId) else backStack.add(detailRoute)
                        }
                    }, onRefreshSource = { source -> scope.launch { refreshAssignmentSource(source) } },
                    showSyncDetails = aggregateSyncDialogVisible, onDismissSyncDetails = { aggregateSyncDialogVisible = false },
                    modifier = Modifier.fillMaxSize(), expanded = expanded, fileGateway = homeworkFileGateway,
                    markdown = { item -> aggregateAssignmentMarkdown(item, homeworkState, phyVlabState, citelState) },
                    detailContent = { item ->
                        when (item.source) {
                            AssignmentSource.COURSE_PLATFORM -> HomeworkDetailWorkspace(homeworkModel, homeworkFileGateway,
                                reauthenticateSession, Modifier.fillMaxSize())
                            AssignmentSource.PHYVLAB -> PhyVlabDetailWorkspace(phyVlabModel, homeworkFileGateway,
                                onOpenExternalUrl, modifier = Modifier.fillMaxSize())
                            AssignmentSource.CITEL -> session.citelModel?.let { model ->
                                CitelDetailWorkspace(model, homeworkFileGateway, onOpenExternalUrl, modifier = Modifier.fillMaxSize())
                            }
                        }
                    })
            }
            AppSection.HOMEWORK -> DestinationPage(
                title = AppSection.HOMEWORK.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = homeworkState.isRefreshing,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
                // 与课表/成绩一致：同步态在顶栏右上，banner 内只放摘要与筛选入口。
                idleStatusText = when {
                    homeworkState.failure != null -> "同步失败"
                    homeworkState.source == HomeworkContentSource.NETWORK -> "已同步"
                    homeworkState.source == HomeworkContentSource.CACHE -> "已同步"
                    else -> "未同步"
                },
            ) {
                HomeworkWorkspace(
                    state = homeworkState,
                    expanded = expanded,
                    usesLegacySmartTransport = usesLegacySmartTransport,
                    legacyWarningVisible = usesLegacySmartTransportFor(platform.family) && !legacyWarningDismissed,
                    onDismissLegacyWarning = dismissLegacyWarning,
                    model = homeworkModel,
                    fileGateway = homeworkFileGateway,
                    onReauthenticate = reauthenticateSession,
                    onRefresh = refresh,
                    onOpenDetail = {
                        // 先写完选中再 push（onOpen 里已 selectHomework），避免详情页打开时为空。
                        if (useNativeSecondaryRoutes) {
                            onOpenNativeRoute(HOMEWORK_DETAIL_ROUTE_ID)
                        } else if (backStack.lastOrNull() != HomeworkDetailRoute) {
                            backStack.add(HomeworkDetailRoute)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.COURSEWARE -> DestinationPage(
                title = AppSection.COURSEWARE.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = coursewareState.isRefreshing,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                // 顶栏 underlap 暂不启用：引导行与课程标题行固定在列表上方，视口到不了栏后。
                // 且关闭滚动过渡：栏后永远是纯色，过渡只会凭空闪一下。
                staticTopBar = true,
                // 与成绩/作业一致：右上角「已同步」+ sync 同一胶囊，勿只留孤图标。
                idleStatusText = when {
                    coursewareState.failure != null -> "同步失败"
                    coursewareState.source == CoursewareContentSource.NETWORK -> "已同步"
                    coursewareState.source == CoursewareContentSource.CACHE -> "已同步"
                    else -> "未同步"
                },
            ) {
                CoursewareWorkspace(
                    state = coursewareState,
                    expanded = expanded,
                    usesLegacySmartTransport = usesLegacySmartTransport,
                    legacyWarningVisible = usesLegacySmartTransportFor(platform.family) && !legacyWarningDismissed,
                    onDismissLegacyWarning = dismissLegacyWarning,
                    model = coursewareModel,
                    fileGateway = homeworkFileGateway,
                    directoryGateway = coursewareDirectoryGateway,
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.CALENDAR -> DestinationPage(
                title = AppSection.CALENDAR.title,
                expanded = expanded,
                refreshable = false,
                isRefreshing = false,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
            ) {
                SchoolCalendarArticleWorkspace(
                    expanded = expanded,
                    onOpenArticle = { onOpenExternalUrl(SCHOOL_CALENDAR_ARTICLE_URL) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.REPORT_CARD_DOWNLOAD -> DestinationPage(
                title = AppSection.REPORT_CARD_DOWNLOAD.title,
                expanded = expanded,
                refreshable = false,
                isRefreshing = false,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
            ) {
                ReportCardDownloadWorkspace(
                    model = otherFunctionModel,
                    expanded = expanded,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.CLASSROOMS -> DestinationPage(
                title = AppSection.CLASSROOMS.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = classroomState.isLoading,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                idleStatusText = classroomIdleStatusText(classroomState),
                scrollUnderTopBar = true,
            ) {
                ClassroomWorkspace(
                    model = classroomModel,
                    expanded = expanded,
                    introBannerVisible = !classroomIntroBannerDismissed,
                    onDismissIntroBanner = dismissClassroomIntroBanner,
                    onOpenBuilding = {
                        // 先写完选中再 push，避免详情页打开时 selectedBuilding 仍为空。
                        if (useNativeSecondaryRoutes) {
                            onOpenNativeRoute(CLASSROOM_DETAIL_ROUTE_ID)
                        } else if (backStack.lastOrNull() != ClassroomDetailRoute) {
                            backStack.add(ClassroomDetailRoute)
                        }
                    },
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.CLASSROOM_OCCUPANCY -> DestinationPage(
                title = AppSection.CLASSROOM_OCCUPANCY.title,
                expanded = expanded,
                refreshable = false,
                isRefreshing = false,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
            ) {
                ClassroomOccupancyWorkspace(
                    model = classroomOccupancyModel,
                    expanded = expanded,
                    onOpenBuilding = {
                        // 先写完选中再 push，避免详情页打开时 selectedBuilding 仍为空。
                        if (useNativeSecondaryRoutes) {
                            onOpenNativeRoute(CLASSROOM_OCCUPANCY_DETAIL_ROUTE_ID)
                        } else if (backStack.lastOrNull() != ClassroomOccupancyDetailRoute) {
                            backStack.add(ClassroomOccupancyDetailRoute)
                        }
                    },
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ClassroomDetailRoute -> DestinationPage(
                // 二级页标题用教学楼名；顶栏返回即原生层级返回，页内不再放「返回教学楼」。
                title = classroomState.selectedBuilding ?: AppSection.CLASSROOMS.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = classroomState.isLoading,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                idleStatusText = classroomIdleStatusText(classroomState),
                // 顶栏 underlap 暂不启用：搜索框与筛选芯片固定在列表上方，视口到不了栏后。
                // 且关闭滚动过渡：栏后永远是纯色，过渡只会凭空闪一下。
                staticTopBar = true,
            ) {
                ClassroomBuildingWorkspace(
                    model = classroomModel,
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ClassroomOccupancyDetailRoute -> DestinationPage(
                // 二级页标题用教学楼名；顶栏返回即原生层级返回，页内不再放「返回教学楼」。
                title = classroomOccupancyState.selectedBuilding?.name ?: AppSection.CLASSROOM_OCCUPANCY.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = classroomOccupancyState.isLoading,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                // 顶栏 underlap 暂不启用：周/图例/星期三段筛选头固定在列表上方，视口到不了栏后。
                // 且关闭滚动过渡：栏后永远是纯色，过渡只会凭空闪一下。
                staticTopBar = true,
                denseTopBar = true,
            ) {
                ClassroomOccupancyBuildingWorkspace(
                    model = classroomOccupancyModel,
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            HomeworkDetailRoute -> DestinationPage(
                // 二级页顶栏固定显示「作业详情」，页内不再重复标题；返回即原生层级返回。
                title = "作业详情",
                expanded = expanded,
                refreshable = false,
                isRefreshing = false,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
            ) {
                HomeworkDetailWorkspace(
                    model = homeworkModel,
                    fileGateway = homeworkFileGateway,
                    onReauthenticate = reauthenticateSession,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.SETTINGS -> DestinationPage(
                title = AppSection.SETTINGS.title,
                expanded = expanded,
                refreshable = false,
                isRefreshing = false,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
            ) {
                SettingsWorkspace(
                    model = settingsModel,
                    accountName = "${profile.name} · ${profile.studentId}",
                    platform = platform,
                    expanded = expanded,
                    onLogout = onLogout,
                    modifier = Modifier.fillMaxSize(),
                    physicsLabConfigured = physicsLabState.configured,
                    physicsDemoConfigured = physicsDemoState.configured,
                    physicsDemoEnabled = physicsDemoState.enabled,
                    onPhysicsDemoEnabled = { enabled -> scope.launch { session.physicsDemoModel?.setEnabled(enabled) } },
                    onPhysicsDemoSettings = {
                        if (useNativeSecondaryRoutes) onOpenNativeRoute(PHYSICS_DEMO_SETTINGS_ROUTE_ID)
                        else backStack.add(PhysicsDemoSettingsRoute)
                    },
                    citelConfigured = citelState.configured,
                    onPhysicsLabSettings = {
                        if (useNativeSecondaryRoutes) onOpenNativeRoute(PHYSICS_LAB_SETTINGS_ROUTE_ID)
                        else backStack.add(PhysicsLabSettingsRoute)
                    },
                    onCitelSettings = {
                        if (useNativeSecondaryRoutes) onOpenNativeRoute(CITEL_SETTINGS_ROUTE_ID)
                        else backStack.add(CitelSettingsRoute)
                    },
                )
            }
            AppSection.MAILBOX -> DestinationPage(
                title = AppSection.MAILBOX.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = mailboxState == MailboxUiState.Preparing ||
                    mailboxReadyState?.isListLoading == true,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                onBack = mailboxBack,
                // 邮箱右上角只有一个可执行的列表刷新圆钮：不要再传 idleStatusText，
                // 否则状态圆圈也会画一个刷新 glyph，和刷新圆钮重复成两个。
                // 同步中时顶栏显示 KMP 风格「同步中」胶囊。
                topBarActionLabel = if (mailboxReadyState != null) "写信" else null,
                onTopBarActionClick = startMailboxComposeFromTopBar,
                modifier = modifier,
            ) {
                MailboxWorkspace(
                    model = mailboxModel,
                    expanded = expanded,
                    onRefresh = refresh,
                    onReauthenticate = reauthenticateSession,
                    fileGateway = homeworkFileGateway,
                    onOpenNativeDetail = if (useNativeSecondaryRoutes) {
                        { onOpenNativeRoute(MAILBOX_DETAIL_ROUTE_ID) }
                    } else {
                        null
                    },
                    onOpenNativeCompose = if (useNativeSecondaryRoutes) {
                        { onOpenNativeRoute(MAILBOX_COMPOSE_ROUTE_ID) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MailboxDetailRoute -> DestinationPage(
                title = "邮件详情",
                expanded = expanded,
                refreshable = false,
                isRefreshing = mailboxMessageLoading,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
            ) {
                MailboxWorkspace(
                    model = mailboxModel,
                    expanded = false,
                    nativeDetail = true,
                    onRefresh = refresh,
                    onReauthenticate = reauthenticateSession,
                    fileGateway = homeworkFileGateway,
                    onOpenNativeCompose = { onOpenNativeRoute(MAILBOX_COMPOSE_ROUTE_ID) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MailboxComposeRoute -> DestinationPage(
                title = if (mailboxReadyState?.compose?.draft?.isReply == true) "回复邮件" else "写信",
                expanded = expanded,
                refreshable = false,
                isRefreshing = false,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                onBack = {
                    scope.launch {
                        mailboxModel.cancelCompose()
                        popBackStack()
                    }
                },
                ownsBackAction = true,
                modifier = modifier,
            ) {
                MailboxComposeScreen(
                    model = mailboxModel,
                    onSent = popBackStack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppSection.PHYVLAB -> DestinationPage(
                title = AppSection.PHYVLAB.title,
                expanded = expanded,
                refreshable = true,
                isRefreshing = phyVlabState.isLoading,
                // 物理在线与首页/课表/作业平级，是完整底栏里的一级页；只有真正被
                // 非底栏路由 push 进来时才显示系统返回按钮。
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
                idleStatusText = when {
                    (phyVlabState.failure != null || phyVlabState.casLoginRequired) &&
                        phyVlabState.contentSource == PhyVlabContentSource.CACHE -> "同步失败·正显示缓存"
                    phyVlabState.failure != null || phyVlabState.casLoginRequired -> "同步失败"
                    phyVlabState.contentSource == PhyVlabContentSource.CACHE -> "未同步·缓存"
                    phyVlabState.contentSource == PhyVlabContentSource.NETWORK &&
                        phyVlabState.failure == null -> "已同步"
                    else -> "未同步"
                },
            ) {
                PhyVlabWorkspace(
                    model = phyVlabModel,
                    holdNetwork = entryLoggingIn,
                    fileGateway = homeworkFileGateway,
                    onRefresh = refresh,
                    showDetailSheet = !useNativeSecondaryRoutes,
                    onOpenCourse = { url -> onOpenExternalUrl(upgradePhyVlabUrlToHttps(url)) },
                    onOpenActivity = { url -> onOpenExternalUrl(upgradePhyVlabUrlToHttps(url)) },
                    onOpenActivityDetail = { activity ->
                        // 先写入选中作业再 push，详情页会基于同一 session model 读取并加载详情。
                        phyVlabModel.showActivityDetails(activity)
                        if (useNativeSecondaryRoutes) {
                            onOpenNativeRoute(PHYVLAB_DETAIL_ROUTE_ID)
                        }
                    },
                    onLogout = onLogout,
                    onRetryDetail = retryPhyVlabDetail,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PhyVlabDetailRoute -> DestinationPage(
                title = "物理作业详情",
                expanded = expanded,
                refreshable = true,
                isRefreshing = phyVlabState.isDetailLoading,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
            ) {
                PhyVlabDetailWorkspace(
                    model = phyVlabModel,
                    fileGateway = homeworkFileGateway,
                    onRetry = retryPhyVlabDetail,
                    onOpenActivity = { url -> onOpenExternalUrl(upgradePhyVlabUrlToHttps(url)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PhysicsDemoSettingsRoute -> DestinationPage(
                title = "演示实验账号设置", expanded = expanded, refreshable = false,
                isRefreshing = physicsDemoState.refreshing, showBack = true,
                modifier = modifier, scrollUnderTopBar = true,
            ) {
                session.physicsDemoModel?.let {
                    team.bjtuss.bjtuselfservice.shared.feature.phydemo.PhysicsDemoSettingsScreen(
                        it, Modifier.fillMaxSize(), onAccountSaved = popBackStack)
                }
            }
            PhysicsDemoRoute -> DestinationPage(
                title = "演示实验查询", expanded = expanded, refreshable = false,
                isRefreshing = physicsDemoState.refreshing, showBack = true,
                modifier = modifier, scrollUnderTopBar = true,
            ) {
                session.physicsDemoModel?.let {
                    team.bjtuss.bjtuselfservice.shared.feature.phydemo.PhysicsDemoScreen(it, Modifier.fillMaxSize())
                }
            }
            AppSection.PHYSICS_LAB, PhysicsLabSettingsRoute -> DestinationPage(
                title = if (route == PhysicsLabSettingsRoute) "物理实验账号设置" else "物理实验同步",
                expanded = expanded,
                refreshable = route == AppSection.PHYSICS_LAB && physicsLabEnabled,
                isRefreshing = route == AppSection.PHYSICS_LAB && physicsLabState.refreshing,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
            ) {
                session.physicsLabModel?.let { labModel ->
                    PhysicsLabSettings(
                        model = labModel,
                        modifier = Modifier.fillMaxSize(),
                        showTitle = false,
                        showAccountFields = route == PhysicsLabSettingsRoute,
                        showLabPreferences = route == AppSection.PHYSICS_LAB,
                        onAccountSaved = {
                            settingsModel.setPhysicsLabEnabled(true)
                            popBackStack()
                        },
                        onAccountCleared = { settingsModel.setPhysicsLabEnabled(false) },
                    )
                }
            }
            AppSection.CITEL -> DestinationPage(
                title = AppSection.CITEL.title,
                expanded = expanded,
                refreshable = citelState.enabled,
                isRefreshing = citelState.refreshing,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
                onStatusClick = { citelSyncDialogVisible = true },
                idleStatusText = when {
                    citelState.failed -> "同步失败" + if (citelState.fromCache) "·正显示缓存" else ""
                    citelState.fromCache -> "等待同步·正显示缓存"
                    citelState.lastSync != null -> "已同步"
                    else -> "未同步"
                },
            ) {
                session.citelModel?.let { model ->
                    team.bjtuss.bjtuselfservice.shared.feature.citel.CitelWorkspace(
                        model, entryLoggingIn, onOpenExternalUrl, Modifier.fillMaxSize(), fileGateway = homeworkFileGateway,
                        showDetailSheet = !useNativeSecondaryRoutes,
                        onOpenTask = {
                            if (useNativeSecondaryRoutes) onOpenNativeRoute(CITEL_DETAIL_ROUTE_ID)
                        })
                }
            }
            CitelDetailRoute -> DestinationPage(title = "作业详情", expanded = expanded, refreshable = true,
                isRefreshing = citelState.submissionBusy, showBack = true, modifier = modifier, scrollUnderTopBar = true) {
                session.citelModel?.let { model ->
                    team.bjtuss.bjtuselfservice.shared.feature.citel.CitelDetailWorkspace(model, homeworkFileGateway,
                        onOpenExternalUrl, entryLoggingIn, Modifier.fillMaxSize())
                }
            }
            CitelSettingsRoute -> DestinationPage(title = "CITEL 账号设置", expanded = expanded, refreshable = false,
                isRefreshing = false, showBack = true, modifier = modifier, scrollUnderTopBar = true) {
                session.citelModel?.let { model ->
                    team.bjtuss.bjtuselfservice.shared.feature.citel.CitelSettingsWorkspace(model, Modifier.fillMaxSize(), entryLoggingIn,
                        onSaved = {
                            settingsModel.setCitelEnabled(true)
                            popBackStack()
                        }, onCleared = { settingsModel.setCitelEnabled(false) })
                }
            }
            AppSection.MORE -> DestinationPage(
                title = AppSection.MORE.title,
                expanded = expanded,
                refreshable = false,
                isRefreshing = false,
                showBack = isPushedHostDestination || route !in compactBottomNavSections,
                modifier = modifier,
                scrollUnderTopBar = true,
            ) {
                MoreWorkspace(
                    preferences = settingsState.preferences,
                    physicsLabModel = session.physicsLabModel,
                    onOpenPhysicsDemo = session.physicsDemoModel?.takeIf { physicsDemoState.enabled }?.let { {
                        if (useNativeSecondaryRoutes) onOpenNativeRoute(PHYSICS_DEMO_ROUTE_ID)
                        else backStack.add(PhysicsDemoRoute)
                    } },
                    onOpenSection = { target -> navigateToSection(target) },
                    modifier = Modifier.fillMaxSize(),
                    platformFamily = platform.family,
                )
            }
        }
    }

    if (forcedRouteId != null) {
        SectionDestination(
            route = currentRoute,
            expanded = false,
            modifier = Modifier.fillMaxSize(),
            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
        )
    } else if (windowClass == WindowClass.Expanded) {
        Row(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.topSystemBars).padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AppSidebar(
                profile = profile,
                section = section,
                showPhyVlab = phyVlabEnabled,
                sections = compactBottomNavSections,
                onSectionSelected = { target -> navigateToSection(target) },
                // 随窗口比例伸缩，避免小窗时侧栏仍占固定 236dp 挤掉内容区。
                modifier = Modifier.weight(0.22f).fillMaxHeight(),
            )
            // 宽屏侧栏布局按 macOS/iPad 的并列工作区处理，不播放手机式 push/pop。
            NavDisplay(
                backStack = backStack,
                onBack = handleBack,
                modifier = Modifier.weight(0.78f).fillMaxHeight(),
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                predictivePopTransitionSpec = { _: Int ->
                    EnterTransition.None togetherWith ExitTransition.None
                },
                entryProvider = entryProvider {
                    entry<AppSection> { route ->
                        SectionDestination(
                            route = route,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                    entry<ClassroomDetailRoute> {
                        SectionDestination(
                            route = ClassroomDetailRoute,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                    entry<ClassroomOccupancyDetailRoute> {
                        SectionDestination(
                            route = ClassroomOccupancyDetailRoute,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                    entry<HomeworkDetailRoute> {
                        SectionDestination(
                            route = HomeworkDetailRoute,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                    entry<ExamDetailRoute> {
                        SectionDestination(
                            route = ExamDetailRoute,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                    entry<CitelSettingsRoute> {
                        SectionDestination(
                            route = CitelSettingsRoute,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                    entry<CitelDetailRoute> {
                        SectionDestination(
                            route = CitelDetailRoute,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                    entry<PhyVlabDetailRoute> {
                        SectionDestination(
                            route = PhyVlabDetailRoute,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                    entry<MailboxDetailRoute> {
                        SectionDestination(
                            route = MailboxDetailRoute,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                    entry<MailboxComposeRoute> {
                        SectionDestination(
                            route = MailboxComposeRoute,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                    entry<PhysicsDemoSettingsRoute> {
                        SectionDestination(route = PhysicsDemoSettingsRoute, expanded = false, modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family))
                    }
                    entry<PhysicsDemoRoute> {
                        SectionDestination(route = PhysicsDemoRoute, expanded = false, modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family))
                    }
                    entry<PhysicsLabSettingsRoute> {
                        SectionDestination(
                            route = PhysicsLabSettingsRoute,
                            expanded = true,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family),
                        )
                    }
                },
            )
        }
    } else {
        val reduceMotion = LocalReduceMotion.current
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
        val appleSpatialSpec = spring<androidx.compose.ui.unit.IntOffset>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 320f,
        )
        val appleInteractiveSpec = tween<androidx.compose.ui.unit.IntOffset>(
            durationMillis = 350,
            easing = LinearEasing,
        )
        val androidOffsetSpec = tween<androidx.compose.ui.unit.IntOffset>(
            durationMillis = 350,
            easing = androidPredictiveEasing,
        )
        val androidScaleSpec = tween<Float>(
            durationMillis = 350,
            easing = androidPredictiveEasing,
        )

        // 紧凑端一级 tab 显示底栏；更多子页与详情路由隐藏。底栏在 NavDisplay 外，tab 切换不重建。
        val showsCompactBottomBar = !nativeTabBarEnabled && currentRoute in compactBottomNavSections

        Box(modifier = Modifier.fillMaxSize()) {
            // Android 使用 Navigation 3 的 seekable 场景内核，并按 Google full-screen surface
            // predictive-back 规范让前景 100%→90%、后景 110%→100%，同时保留小幅横向预览。
            // iOS 使用可被 NavDisplay start-edge 手势 seek 的 UINavigationController 空间路径；
            // macOS 的紧凑窗口仍遵守桌面习惯，不播放手机式整页滑动。
            NavDisplay(
                backStack = backStack,
                onBack = handleBack,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    when {
                        nativeNavigationEnabled ->
                            EnterTransition.None togetherWith ExitTransition.None
                        reduceMotion ->
                            fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                        platform.family == PlatformFamily.Android ->
                            (slideInHorizontally(
                                initialOffsetX = { direction * it / 12 },
                                animationSpec = androidOffsetSpec,
                            ) + scaleIn(
                                initialScale = 0.96f,
                                animationSpec = androidScaleSpec,
                            ) + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { -direction * it / 20 },
                                    animationSpec = androidOffsetSpec,
                                ) + scaleOut(
                                    targetScale = 0.90f,
                                    animationSpec = androidScaleSpec,
                                ) + fadeOut(tween(280)))
                        platform.family == PlatformFamily.IOS ->
                            slideInHorizontally(
                                initialOffsetX = { direction * it },
                                animationSpec = appleSpatialSpec,
                            ) togetherWith slideOutHorizontally(
                                targetOffsetX = { -direction * it / 3 },
                                animationSpec = appleSpatialSpec,
                            )
                        else -> EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                popTransitionSpec = {
                    when {
                        nativeNavigationEnabled ->
                            EnterTransition.None togetherWith ExitTransition.None
                        reduceMotion ->
                            fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                        platform.family == PlatformFamily.Android ->
                            (slideInHorizontally(
                                initialOffsetX = { -direction * it / 20 },
                                animationSpec = androidOffsetSpec,
                            ) + scaleIn(
                                initialScale = 1.10f,
                                animationSpec = androidScaleSpec,
                            ) + fadeIn(tween(280))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { direction * it / 20 },
                                    animationSpec = androidOffsetSpec,
                                ) + scaleOut(
                                    targetScale = 0.90f,
                                    animationSpec = androidScaleSpec,
                                ) + fadeOut(tween(220)))
                        platform.family == PlatformFamily.IOS ->
                            slideInHorizontally(
                                initialOffsetX = { -direction * it / 3 },
                                animationSpec = appleSpatialSpec,
                            ) togetherWith slideOutHorizontally(
                                targetOffsetX = { direction * it },
                                animationSpec = appleSpatialSpec,
                            )
                        else -> EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                predictivePopTransitionSpec = { swipeEdge: Int ->
                    val gestureDirection = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
                    when {
                        nativeNavigationEnabled ->
                            EnterTransition.None togetherWith ExitTransition.None
                        reduceMotion ->
                            fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                        platform.family == PlatformFamily.Android ->
                            (slideInHorizontally(
                                initialOffsetX = { -gestureDirection * it / 20 },
                                animationSpec = androidOffsetSpec,
                            ) + scaleIn(
                                initialScale = 1.10f,
                                animationSpec = androidScaleSpec,
                            ) + fadeIn(tween(280))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { gestureDirection * it / 20 },
                                    animationSpec = androidOffsetSpec,
                                ) + scaleOut(
                                    targetScale = 0.90f,
                                    animationSpec = androidScaleSpec,
                                ) + fadeOut(tween(220)))
                        platform.family == PlatformFamily.IOS ->
                            slideInHorizontally(
                                initialOffsetX = { -gestureDirection * it / 3 },
                                animationSpec = appleInteractiveSpec,
                            ) togetherWith slideOutHorizontally(
                                targetOffsetX = { gestureDirection * it },
                                animationSpec = appleInteractiveSpec,
                            )
                        else -> EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                entryProvider = entryProvider {
                    entry<AppSection> { route ->
                        SectionDestination(
                            route = route,
                            expanded = false,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = false,
                        )
                    }
                    entry<ClassroomDetailRoute> {
                        SectionDestination(
                            route = ClassroomDetailRoute,
                            expanded = false,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = false,
                        )
                    }
                    entry<ClassroomOccupancyDetailRoute> {
                        SectionDestination(
                            route = ClassroomOccupancyDetailRoute,
                            expanded = false,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = false,
                        )
                    }
                    entry<HomeworkDetailRoute> {
                        SectionDestination(
                            route = HomeworkDetailRoute,
                            expanded = false,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = false,
                        )
                    }
                    entry<ExamDetailRoute> {
                        SectionDestination(
                            route = ExamDetailRoute,
                            expanded = false,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = false,
                        )
                    }
                    entry<CitelSettingsRoute> {
                        SectionDestination(
                            route = CitelSettingsRoute,
                            expanded = false,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = false,
                        )
                    }
                    entry<CitelDetailRoute> {
                        SectionDestination(
                            route = CitelDetailRoute,
                            expanded = false,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = false,
                        )
                    }
                    entry<PhyVlabDetailRoute> {
                        SectionDestination(
                            route = PhyVlabDetailRoute,
                            expanded = false,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = false,
                        )
                    }
                    entry<MailboxDetailRoute> {
                        SectionDestination(
                            route = MailboxDetailRoute,
                            expanded = false,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = false,
                        )
                    }
                    entry<MailboxComposeRoute> {
                        SectionDestination(
                            route = MailboxComposeRoute,
                            expanded = false,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = false,
                        )
                    }
                    entry<PhysicsDemoSettingsRoute> {
                        SectionDestination(route = PhysicsDemoSettingsRoute, expanded = false, modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family))
                    }
                    entry<PhysicsDemoRoute> {
                        SectionDestination(route = PhysicsDemoRoute, expanded = false, modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = usesLegacySmartTransportFor(platform.family))
                    }
                    entry<PhysicsLabSettingsRoute> {
                        SectionDestination(
                            route = PhysicsLabSettingsRoute,
                            expanded = false,
                            modifier = Modifier.fillMaxSize(),
                            usesLegacySmartTransport = false,
                        )
                    }
                },
            )
            if (showsCompactBottomBar && compactBottomBarOverlayPadding > 0.dp) {
                CompactBottomNavigation(
                    section = section,
                    sections = compactBottomNavSections,
                    onSectionSelected = { target -> navigateToSection(target) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

internal fun upgradePhyVlabUrlToHttps(url: String): String =
    if (url.startsWith("http://", ignoreCase = true)) "https://${url.substring(7)}" else url

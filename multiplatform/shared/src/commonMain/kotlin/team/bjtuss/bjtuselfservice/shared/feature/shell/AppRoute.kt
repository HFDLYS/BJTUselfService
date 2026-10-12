package team.bjtuss.bjtuselfservice.shared.feature.shell


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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleContentSource
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleContentSource
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkContentSource
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkDetailWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkScreenModel
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
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeKind
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeDomain
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeRecord

internal sealed interface AppRoute : NavKey

internal enum class AppSection(
    val title: String,
    val moreTitle: String = title,
) : AppRoute {
    HOME("首页"),
    GRADES("成绩"),
    SCHEDULE("课程表"),
    EXAMS("考试安排"),
    HOMEWORK("课程平台作业"),
    ASSIGNMENTS("作业"),
    COURSEWARE("课件下载"),
    CLASSROOM_OCCUPANCY("教室查询"),
    CLASSROOMS("教室人数估计"),
    MAILBOX("邮箱"),
    PHYVLAB("物理在线", "物理在线（仅能在校园网下访问）"),
    PHYSICS_LAB("物理实验同步"),
    CITEL("CITEL 作业"),
    CALENDAR("校历下载"),
    REPORT_CARD_DOWNLOAD("成绩单下载"),
    SETTINGS("设置"),
    MORE("应用"),
}

/** 紧凑布局底部导航直接暴露的一级入口；其余入口收进“应用”页。 */
internal fun bottomNavSections(showPhyVlab: Boolean): List<AppSection> = buildList {
    add(AppSection.HOME)
    add(AppSection.SCHEDULE)
    add(AppSection.GRADES)
    add(AppSection.HOMEWORK)
    if (showPhyVlab) add(AppSection.PHYVLAB)
    add(AppSection.MORE)
}

internal fun bottomNavigationCandidates(showPhyVlab: Boolean, showPhysicsLab: Boolean = false, showCitel: Boolean = false, aggregate: Boolean = false): List<AppSection> =
    AppSection.entries.filter {
        it !in setOf(AppSection.HOME, AppSection.MORE, AppSection.CLASSROOMS) &&
            (showPhyVlab || it != AppSection.PHYVLAB) &&
            (showPhysicsLab || it != AppSection.PHYSICS_LAB) &&
            (showCitel || it != AppSection.CITEL) &&
            (aggregate || it != AppSection.ASSIGNMENTS) &&
            (!aggregate || it !in setOf(AppSection.HOMEWORK, AppSection.PHYVLAB, AppSection.CITEL))
    }

internal fun bottomNavSections(preferences: team.bjtuss.bjtuselfservice.shared.cache.AppPreferences): List<AppSection> {
    val candidates = bottomNavigationCandidates(preferences.isPhyVlabEnabled, preferences.isPhysicsLabEnabled, preferences.isCitelEnabled, preferences.aggregateAssignments)
    fun mapped(id: String): String = when {
        preferences.aggregateAssignments && id in setOf("HOMEWORK", "PHYVLAB", "CITEL") -> "ASSIGNMENTS"
        !preferences.aggregateAssignments && id == "ASSIGNMENTS" -> "HOMEWORK"
        else -> id
    }
    val selected = preferences.bottomNavigationItems?.mapNotNull { id -> candidates.firstOrNull { it.name == mapped(id) } }
        ?.distinct()?.take(4)
        ?: bottomNavSections(preferences.isPhyVlabEnabled).mapNotNull { entry -> candidates.firstOrNull { it.name == mapped(entry.name) } }.distinct()
    return listOf(AppSection.HOME) + selected + AppSection.MORE
}

internal fun applicationSections(preferences: team.bjtuss.bjtuselfservice.shared.cache.AppPreferences): List<AppSection> =
    bottomNavigationCandidates(preferences.isPhyVlabEnabled, preferences.isPhysicsLabEnabled, preferences.isCitelEnabled, preferences.aggregateAssignments).filter { it !in bottomNavSections(preferences) }

/** 在底部导航中归属“更多”高亮的入口。 */
internal val MoreGroupSections = setOf(
    AppSection.EXAMS,
    AppSection.COURSEWARE,
    AppSection.CLASSROOMS,
    AppSection.CLASSROOM_OCCUPANCY,
    AppSection.MAILBOX,
    AppSection.PHYSICS_LAB,
    AppSection.CITEL,
    AppSection.ASSIGNMENTS,
    AppSection.CALENDAR,
    AppSection.REPORT_CARD_DOWNLOAD,
    AppSection.SETTINGS,
    AppSection.MORE,
)

internal const val LOGIN_SYNC_RETRY_DELAY_MILLIS = 700L
/** 教室详情的第三级路由：独立于一级/二级 section。 */
internal data object ClassroomDetailRoute : AppRoute
const val CLASSROOM_DETAIL_ROUTE_ID = "CLASSROOM_DETAIL"

/** 教室占用的第三级路由：独立于一级/二级 section，仿教室详情。 */
internal data object ClassroomOccupancyDetailRoute : AppRoute
const val CLASSROOM_OCCUPANCY_DETAIL_ROUTE_ID = "CLASSROOM_OCCUPANCY_DETAIL"

/** 作业详情的二级路由：独立于一级 section，仿教室详情。 */
internal data object HomeworkDetailRoute : AppRoute
const val HOMEWORK_DETAIL_ROUTE_ID = "HOMEWORK_DETAIL"

/** 首页考试安排详情：从首页返回时直接回到首页。 */
internal data object ExamDetailRoute : AppRoute
const val EXAM_DETAIL_ROUTE_ID = "EXAM_DETAIL"

/** 物理在线作业详情的二级路由：紧凑端仿作业详情，宽屏仍使用底部弹窗。 */
internal data object PhyVlabDetailRoute : AppRoute
const val PHYVLAB_DETAIL_ROUTE_ID = "PHYVLAB_DETAIL"
internal data object CitelDetailRoute : AppRoute
const val CITEL_DETAIL_ROUTE_ID = "CITEL_DETAIL"
internal data object CitelSettingsRoute : AppRoute
const val CITEL_SETTINGS_ROUTE_ID = "CITEL_SETTINGS"

/** 邮箱详情的二级路由：紧凑端使用平台原生 push，宽屏留在三栏阅读区。 */
internal data object MailboxDetailRoute : AppRoute
const val MAILBOX_DETAIL_ROUTE_ID = "MAILBOX_DETAIL"

/** 邮箱写信/回复二级路由：紧凑端使用平台原生 push，宽屏在当前邮箱内容区编辑。 */
internal data object MailboxComposeRoute : AppRoute
const val MAILBOX_COMPOSE_ROUTE_ID = "MAILBOX_COMPOSE"

/** 物理实验同步二级页：从应用目录进入，不使用弹窗。 */
internal data object PhysicsLabSettingsRoute : AppRoute
const val PHYSICS_LAB_SETTINGS_ROUTE_ID = "PHYSICS_LAB_SETTINGS"
internal data object PhysicsDemoRoute : AppRoute
const val PHYSICS_DEMO_ROUTE_ID = "PHYSICS_DEMO"
internal data object PhysicsDemoSettingsRoute : AppRoute
const val PHYSICS_DEMO_SETTINGS_ROUTE_ID = "PHYSICS_DEMO_SETTINGS"

/** Google predictive-back full-screen surface 的 SystemUI 插值。 */
internal val androidPredictiveEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

internal fun String.toAppRoute(): AppRoute? =
    if (this == CLASSROOM_DETAIL_ROUTE_ID) {
        ClassroomDetailRoute
    } else if (this == CLASSROOM_OCCUPANCY_DETAIL_ROUTE_ID) {
        ClassroomOccupancyDetailRoute
    } else if (this == HOMEWORK_DETAIL_ROUTE_ID) {
        HomeworkDetailRoute
    } else if (this == EXAM_DETAIL_ROUTE_ID) {
        ExamDetailRoute
    } else if (this == PHYVLAB_DETAIL_ROUTE_ID) {
        PhyVlabDetailRoute
    } else if (this == CITEL_DETAIL_ROUTE_ID) {
        CitelDetailRoute
    } else if (this == CITEL_SETTINGS_ROUTE_ID) {
        CitelSettingsRoute
    } else if (this == MAILBOX_DETAIL_ROUTE_ID) {
        MailboxDetailRoute
    } else if (this == MAILBOX_COMPOSE_ROUTE_ID) {
        MailboxComposeRoute
    } else if (this == PHYSICS_DEMO_SETTINGS_ROUTE_ID) {
        PhysicsDemoSettingsRoute
    } else if (this == PHYSICS_DEMO_ROUTE_ID) {
        PhysicsDemoRoute
    } else if (this == PHYSICS_LAB_SETTINGS_ROUTE_ID) {
        PhysicsLabSettingsRoute
    } else {
        AppSection.entries.firstOrNull { it.name == this }
    }

/** 邮箱页顶栏返回的目标；壳内详情回列表，原生详情页回邮箱上级页面。 */
internal enum class MailboxBackTarget {
    PARENT,
    LIST,
}

internal fun shouldHandleInlineMailboxBack(
    currentRouteIsMailbox: Boolean,
    mailboxInlineDetail: Boolean,
    mailboxInlineCompose: Boolean,
): Boolean = currentRouteIsMailbox && (mailboxInlineDetail || mailboxInlineCompose)

internal fun mailboxBackTarget(
    useNativeSecondaryRoutes: Boolean,
    hasSelectedMessage: Boolean,
    isMessageLoading: Boolean,
): MailboxBackTarget = if (
    !useNativeSecondaryRoutes &&
        (hasSelectedMessage || isMessageLoading)
) {
    MailboxBackTarget.LIST
} else {
    MailboxBackTarget.PARENT
}

/**
 * 紧凑端「更多」里的独立页面都走平台原生 push；邮箱列表、邮件详情和写信/回复
 * 分别对应 MAILBOX、MAILBOX_DETAIL、MAILBOX_COMPOSE，形成连续的原生页面层级。
 */
internal fun shouldOpenNativeSectionRoute(
    targetRouteId: String,
    useNativeSecondaryRoutes: Boolean,
    tabSections: List<AppSection> = nativeTabSections(),
): Boolean {
    if (!useNativeSecondaryRoutes || targetRouteId == AppSection.MORE.name) return false
    val route = targetRouteId.toAppRoute() ?: return false
    // 「更多」目录里的项由宿主压栈；真正位于原生 tab 的一级入口（包括开启后的物理在线）不压栈。
    return route !in tabSections
}

internal fun HomeChangeDomain.toAppSection(): AppSection = when (this) {
    HomeChangeDomain.GRADES -> AppSection.GRADES
    HomeChangeDomain.COURSES -> AppSection.SCHEDULE
    HomeChangeDomain.EXAMS -> AppSection.EXAMS
    HomeChangeDomain.HOMEWORK -> AppSection.HOMEWORK
    HomeChangeDomain.PHYVLAB -> AppSection.PHYVLAB
}

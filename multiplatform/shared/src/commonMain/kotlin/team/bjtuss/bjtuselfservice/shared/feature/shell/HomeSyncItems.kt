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

/**
 * 教室页顶栏空闲态文案。
 * 未选楼 / 尚未请求（Idle）返回 null：一级楼列表没有「同步」语义，不显示「未同步 ✓」。
 * Loading 时 isRefreshing 会盖成「同步中」；此处仍给「未同步」作兜底。
 */
internal fun classroomIdleStatusText(state: ClassroomUiState): String? = when (state.buildingState) {
    is ClassroomBuildingState.Failed -> "同步失败"
    is ClassroomBuildingState.Loaded -> "已同步"
    ClassroomBuildingState.Loading -> "未同步"
    ClassroomBuildingState.Idle -> null
}

internal val partialSyncFailureStatusTexts = setOf("部分同步失败", "同步失败")

internal fun buildHomeSyncItems(
    isLoggingIn: Boolean,
    homeBusy: Boolean,
    homeFailed: Boolean,
    homeReady: Boolean,
    gradeBusy: Boolean,
    gradeFailed: Boolean,
    gradeReady: Boolean,
    homeworkBusy: Boolean,
    homeworkFailed: Boolean,
    homeworkReady: Boolean,
    examBusy: Boolean,
    examFailed: Boolean,
    examReady: Boolean,
    courseBusy: Boolean,
    courseFailed: Boolean,
    courseReady: Boolean,
    phyVlabEnabled: Boolean,
    phyVlabBusy: Boolean,
    phyVlabFailed: Boolean,
    phyVlabReady: Boolean,
    physicsLabEnabled: Boolean = false,
    physicsLabBusy: Boolean = false,
    physicsLabFailed: Boolean = false,
    physicsLabReady: Boolean = false,
    citelEnabled: Boolean = false,
    citelBusy: Boolean = false,
    citelFailed: Boolean = false,
    citelReady: Boolean = false,
    physicsDemoEnabled: Boolean = false,
    physicsDemoBusy: Boolean = false,
    physicsDemoFailed: Boolean = false,
    physicsDemoReady: Boolean = false,
): List<HomeSyncItem> = buildList {
    add(
        homeSyncItem(
            title = "统一身份认证",
            busy = isLoggingIn,
            failed = false,
            ready = !isLoggingIn,
            busyDetail = "正在检查登录状态",
        ),
    )
    add(
        homeSyncItem(
            title = "首页账户状态",
            busy = homeBusy,
            failed = homeFailed,
            ready = homeReady,
            busyDetail = "正在读取邮箱与校园账户状态",
        ),
    )
    add(
        homeSyncItem(
            title = "成绩",
            busy = gradeBusy,
            failed = gradeFailed,
            ready = gradeReady,
            busyDetail = "正在同步成绩",
        ),
    )
    add(
        homeSyncItem(
            title = "课程表与校历周数",
            busy = courseBusy,
            failed = courseFailed,
            ready = courseReady,
            busyDetail = "正在同步课表并校准教学周",
        ),
    )
    add(
        homeSyncItem(
            title = "课程平台作业",
            busy = homeworkBusy,
            failed = homeworkFailed,
            ready = homeworkReady,
            busyDetail = "正在同步作业",
        ),
    )
    add(
        homeSyncItem(
            title = "考试安排",
            busy = examBusy,
            failed = examFailed,
            ready = examReady,
            busyDetail = "正在同步考试安排",
        ),
    )
    if (physicsLabEnabled) {
        add(homeSyncItem("物理实验", physicsLabBusy, physicsLabFailed, physicsLabReady,
            busyDetail = "正在读取已选实验（需要校园网）"))
    }
    if (physicsDemoEnabled) {
        add(homeSyncItem("演示实验", physicsDemoBusy, physicsDemoFailed, physicsDemoReady,
            busyDetail = "正在读取已预约演示实验（需要校园网）"))
    }
    if (citelEnabled) {
        add(homeSyncItem("CITEL 作业", citelBusy, citelFailed, citelReady,
            busyDetail = "正在读取作业折扣与截止时间"))
    }
    if (phyVlabEnabled) {
        add(
            homeSyncItem(
                title = "物理在线",
                busy = phyVlabBusy,
                failed = phyVlabFailed,
                ready = phyVlabReady,
                busyDetail = "正在同步物理在线安排",
            ),
        )
    }
}

internal fun homeSyncItem(
    title: String,
    busy: Boolean,
    failed: Boolean,
    ready: Boolean,
    busyDetail: String,
): HomeSyncItem {
    val state = when {
        failed -> HomeSyncItemState.FAILED
        busy -> HomeSyncItemState.SYNCING
        ready -> HomeSyncItemState.SUCCESS
        else -> HomeSyncItemState.WAITING
    }
    val detail = when (state) {
        HomeSyncItemState.FAILED -> "本轮同步失败"
        HomeSyncItemState.SYNCING -> busyDetail
        HomeSyncItemState.SUCCESS -> "已完成"
        HomeSyncItemState.WAITING -> "等待同步"
    }
    return HomeSyncItem(title = title, detail = detail, state = state)
}

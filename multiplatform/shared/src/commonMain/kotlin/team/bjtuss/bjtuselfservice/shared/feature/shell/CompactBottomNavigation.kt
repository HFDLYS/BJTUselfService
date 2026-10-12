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
 * 紧凑底栏：Material3 官方 [NavigationBar] + [NavigationBarItem]。
 * 挂在 NavDisplay 外层，一级 tab 切换时实例不销毁，按压/水波纹才能播完。
 * windowInsets 用 [WindowInsets.bottomSystemBars]（导航栏 ∪ 小窗手势条），由组件处理 Home Indicator / 手势条，
 * 避免自绘固定高度把标签裁切或与系统安全区叠错。
 */
@Composable
internal fun CompactBottomNavigation(
    section: AppSection,
    sections: List<AppSection>,
    onSectionSelected: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        windowInsets = WindowInsets.bottomSystemBars,
    ) {
        sections.forEach { item ->
            val selected =
                if (item == AppSection.MORE) section !in sections || section == AppSection.MORE else section == item
            NavigationBarItem(
                selected = selected,
                onClick = { onSectionSelected(item) },
                icon = {
                    // 固定 24.dp 图标盒，保证各 tab 标签基线一致。
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompactTabIcon(item)
                    }
                },
                label = {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                },
                alwaysShowLabel = true,
            )
        }
    }
}

/** 底部导航图标：与登录页密码眼睛一致用 Canvas 绘制，不引入图标库依赖。 */
@Composable
internal fun CompactTabIcon(section: AppSection) {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(24.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        when (section) {
            AppSection.HOME -> {
                // Home uses a house silhouette so it remains distinct from the app-directory grid.
                val left = 5.dp.toPx()
                val right = size.width - left
                val roofPeak = Offset(size.width / 2f, 3.dp.toPx())
                val roofLeft = Offset(3.dp.toPx(), 11.dp.toPx())
                val roofRight = Offset(size.width - 3.dp.toPx(), 11.dp.toPx())
                val wallTop = 9.dp.toPx()
                val bottom = size.height - 3.dp.toPx()
                drawLine(color, roofLeft, roofPeak, strokeWidth, cap = StrokeCap.Round)
                drawLine(color, roofPeak, roofRight, strokeWidth, cap = StrokeCap.Round)
                drawLine(color, Offset(left, wallTop), Offset(left, bottom), strokeWidth, cap = StrokeCap.Round)
                drawLine(color, Offset(right, wallTop), Offset(right, bottom), strokeWidth, cap = StrokeCap.Round)
                drawLine(color, Offset(left, bottom), Offset(right, bottom), strokeWidth, cap = StrokeCap.Round)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(10.dp.toPx(), 14.dp.toPx()),
                    size = Size(4.dp.toPx(), 7.dp.toPx()),
                    cornerRadius = CornerRadius(0.8.dp.toPx()),
                    style = Stroke(width = strokeWidth),
                )
            }
            AppSection.MORE -> {
                // 2×2 圆角方块
                val cell = 7.dp.toPx()
                val gap = 3.dp.toPx()
                val start = (size.width - cell * 2 - gap) / 2
                val radius = CornerRadius(2.dp.toPx())
                for (row in 0..1) {
                    for (col in 0..1) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(start + col * (cell + gap), start + row * (cell + gap)),
                            size = Size(cell, cell),
                            cornerRadius = radius,
                        )
                    }
                }
            }
            AppSection.SCHEDULE, AppSection.CALENDAR -> {
                // 日历：圆角外框 + 顶部分隔线 + 两个挂环
                val left = 4.dp.toPx()
                val top = 5.dp.toPx()
                val right = size.width - left
                val bottom = size.height - 4.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = strokeWidth),
                )
                val divider = top + 4.dp.toPx()
                drawLine(color, Offset(left, divider), Offset(right, divider), strokeWidth)
                drawLine(
                    color,
                    Offset(left + 4.5.dp.toPx(), top - 2.dp.toPx()),
                    Offset(left + 4.5.dp.toPx(), top + 2.dp.toPx()),
                    strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(right - 4.5.dp.toPx(), top - 2.dp.toPx()),
                    Offset(right - 4.5.dp.toPx(), top + 2.dp.toPx()),
                    strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            AppSection.GRADES -> {
                // 三根高度递增的柱条
                val barWidth = 3.4.dp.toPx()
                val baseY = size.height - 4.dp.toPx()
                val xs = listOf(5.dp.toPx(), 10.3.dp.toPx(), 15.6.dp.toPx())
                val heights = listOf(6.dp.toPx(), 10.dp.toPx(), 14.dp.toPx())
                xs.zip(heights).forEach { (x, h) ->
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, baseY - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(1.2.dp.toPx()),
                    )
                }
            }
            AppSection.HOMEWORK, AppSection.CITEL, AppSection.ASSIGNMENTS -> {
                // 便签框 + 对勾
                val left = 5.dp.toPx()
                val top = 4.dp.toPx()
                val right = size.width - 5.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = strokeWidth),
                )
                drawLine(
                    color,
                    Offset(left + 3.dp.toPx(), top + 7.5.dp.toPx()),
                    Offset(left + 6.dp.toPx(), top + 10.5.dp.toPx()),
                    strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(left + 6.dp.toPx(), top + 10.5.dp.toPx()),
                    Offset(right - 3.dp.toPx(), top + 5.dp.toPx()),
                    strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            AppSection.PHYVLAB, AppSection.PHYSICS_LAB -> {
                // 物理在线：简化烧杯/实验瓶图标。
                val left = 5.dp.toPx()
                val right = size.width - left
                val top = 4.dp.toPx()
                val neckBottom = 9.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                drawLine(color, Offset(9.dp.toPx(), top), Offset(9.dp.toPx(), neckBottom), strokeWidth)
                drawLine(color, Offset(15.dp.toPx(), top), Offset(15.dp.toPx(), neckBottom), strokeWidth)
                drawLine(color, Offset(7.dp.toPx(), top), Offset(17.dp.toPx(), top), strokeWidth, cap = StrokeCap.Round)
                drawLine(color, Offset(9.dp.toPx(), neckBottom), Offset(left, bottom), strokeWidth, cap = StrokeCap.Round)
                drawLine(color, Offset(15.dp.toPx(), neckBottom), Offset(right, bottom), strokeWidth, cap = StrokeCap.Round)
                drawLine(color, Offset(left, bottom), Offset(right, bottom), strokeWidth, cap = StrokeCap.Round)
                drawLine(
                    color,
                    Offset(6.5.dp.toPx(), 15.dp.toPx()),
                    Offset(17.5.dp.toPx(), 15.dp.toPx()),
                    strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawCircle(color, radius = 1.2.dp.toPx(), center = Offset(12.dp.toPx(), 12.dp.toPx()))
            }
            AppSection.MAILBOX -> {
                drawRoundRect(color, Offset(3.dp.toPx(), 5.dp.toPx()), Size(18.dp.toPx(), 14.dp.toPx()), CornerRadius(2.dp.toPx()), style = Stroke(strokeWidth))
                drawLine(color, Offset(3.dp.toPx(), 6.dp.toPx()), Offset(12.dp.toPx(), 13.dp.toPx()), strokeWidth)
                drawLine(color, Offset(12.dp.toPx(), 13.dp.toPx()), Offset(21.dp.toPx(), 6.dp.toPx()), strokeWidth)
            }
            AppSection.EXAMS -> {
                drawCircle(color, 8.dp.toPx(), style = Stroke(strokeWidth))
                drawLine(color, center, Offset(12.dp.toPx(), 6.dp.toPx()), strokeWidth, cap = StrokeCap.Round)
                drawLine(color, center, Offset(17.dp.toPx(), 14.dp.toPx()), strokeWidth, cap = StrokeCap.Round)
            }
            AppSection.CLASSROOM_OCCUPANCY, AppSection.CLASSROOMS -> {
                drawRect(color, Offset(4.dp.toPx(), 3.dp.toPx()), Size(16.dp.toPx(), 18.dp.toPx()), style = Stroke(strokeWidth))
                for (x in listOf(8f,16f)) for (y in listOf(7f,12f)) drawCircle(color, 1.dp.toPx(), Offset(x.dp.toPx(), y.dp.toPx()))
                drawRect(color, Offset(10.dp.toPx(), 16.dp.toPx()), Size(4.dp.toPx(), 5.dp.toPx()), style = Stroke(strokeWidth))
            }
            AppSection.SETTINGS -> {
                drawCircle(color, 7.dp.toPx(), style = Stroke(strokeWidth))
                drawCircle(color, 2.5.dp.toPx(), style = Stroke(strokeWidth))
                for (i in 0..7) {
                    val angle = i * PI / 4
                    drawLine(color, center + Offset((7 * kotlin.math.cos(angle)).toFloat().dp.toPx(), (7 * kotlin.math.sin(angle)).toFloat().dp.toPx()),
                        center + Offset((10 * kotlin.math.cos(angle)).toFloat().dp.toPx(), (10 * kotlin.math.sin(angle)).toFloat().dp.toPx()), strokeWidth)
                }
            }
            AppSection.COURSEWARE, AppSection.REPORT_CARD_DOWNLOAD -> {
                drawRoundRect(color, Offset(5.dp.toPx(), 3.dp.toPx()), Size(14.dp.toPx(), 18.dp.toPx()), CornerRadius(2.dp.toPx()), style = Stroke(strokeWidth))
                for (y in listOf(8f,12f,16f)) drawLine(color, Offset(8.dp.toPx(), y.dp.toPx()), Offset(16.dp.toPx(), y.dp.toPx()), strokeWidth)
                if (section == AppSection.COURSEWARE) drawLine(color, Offset(3.dp.toPx(), 6.dp.toPx()), Offset(3.dp.toPx(), 20.dp.toPx()), strokeWidth)
            }
            else -> {
                // 更多：三个圆点
                val radius = 1.7.dp.toPx()
                val cy = size.height / 2
                drawCircle(color, radius, Offset(5.5.dp.toPx(), cy))
                drawCircle(color, radius, Offset(size.width / 2, cy))
                drawCircle(color, radius, Offset(size.width - 5.5.dp.toPx(), cy))
            }
        }
    }
}

package team.bjtuss.bjtuselfservice.shared.feature.grade

import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalBottomBarClearance

import team.bjtuss.bjtuselfservice.shared.feature.common.WorkspaceEmptyState
import team.bjtuss.bjtuselfservice.shared.feature.common.WorkspaceLoadingState

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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheetOrAlert
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
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheet
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppErrorBanner
import team.bjtuss.bjtuselfservice.shared.feature.shell.sheetScrollContentPadding
import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalTopBarClearance
import team.bjtuss.bjtuselfservice.shared.feature.shell.TopScrollLazyColumn
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GradeWorkspace(
    state: GradeUiState,
    expanded: Boolean,
    model: GradeScreenModel,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    // 成绩页会和登录后的多路后台同步共用壳层；把昂贵的筛选/排序/加权计算
    // 固定在成绩输入变化时，避免无关状态重组时反复创建列表。
    val visibleGrades = remember(
        state.grades,
        state.selectedSemesters,
        state.excludedCourseTypes,
        state.courseTypesByCode,
        state.sortOrder,
    ) { state.visibleGrades }
    val gradeInfo = remember(
        state.grades,
        state.selectedSemesters,
        state.selectionMode,
        state.selectedGradeIds,
        state.courseTypesByCode,
        state.excludedCourseTypes,
    ) { state.gradeInfo }
    val gradeRows = remember(visibleGrades, state.courseTypesByCode) {
        visibleGrades.map { grade ->
            GradeRowData(
                id = grade.id,
                courseName = grade.displayCourseName(),
                semesterTeacher = "${grade.semester} · ${grade.courseTeacher.ifBlank { "教师信息未提供" }}",
                credits = "学分 ${grade.courseCredits}",
                score = grade.courseScore,
                scoreValue = scoreForSorting(grade.courseScore),
                courseType = state.courseTypeOf(grade),
            )
        }
    }

    // 原生栏 underlap 时外层不再做布局占位（作业页同款）：视口顶边贴屏幕顶。
    val topClearance = LocalTopBarClearance.current
    Column(
        modifier = if (expanded) {
            modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            // 与课表/作业紧凑顶距对齐，避免 banner 视觉偏大。
            modifier.padding(horizontal = 16.dp).padding(top = if (topClearance > 0.dp) 0.dp else 8.dp)
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 同步态在 DestinationPage 顶栏；此处不再放页内「同步成绩」。
        // 失败横幅：宽屏仍钉顶部；紧凑端 underlap 时收进列表首项（外层已跳过）。
        if (expanded || topClearance <= 0.dp) {
            state.failure?.let { failure ->
                GradeFailureBanner(
                    failure = failure,
                    hasContent = state.grades.isNotEmpty(),
                    onRetry = onRefresh,
                    onDismiss = model::dismissFailure,
                )
            }
        }

        when {
            // 短内容（加载/空）不滚动：留在栏下，老起笔位置（外层已无占位，这里补回）。
            state.isLoading && state.grades.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(top = 8.dp + topClearance),
            ) { GradeLoadingState() }
            state.grades.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(top = 8.dp + topClearance),
            ) { GradeEmptyState(onRefresh) }
            else -> {
                if (expanded) {
                    GradeSummaryCard(
                        state = state,
                        gradeInfo = gradeInfo,
                        visibleGradeCount = visibleGrades.size,
                        onOpenFilter = { showFilterSheet = true },
                    )
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        GradeList(
                            state = state,
                            rows = gradeRows,
                            model = model,
                            modifier = Modifier.weight(0.58f).fillMaxHeight(),
                        )
                        GradeDetailPanel(
                            grade = state.selectedGrade,
                            modifier = Modifier.weight(0.42f).fillMaxHeight(),
                        )
                    }
                } else {
                    // 紧凑端：Banner 放进 LazyColumn，与列表同一滚动体。
                    // 固定在列表上方时，iOS 橡皮筋只拉卡片、Banner 不动，会出现大空档并与下拉刷新抢手势。
                    GradeScrollableContent(
                        state = state,
                        rows = gradeRows,
                        gradeInfo = gradeInfo,
                        model = model,
                        onOpenFilter = { showFilterSheet = true },
                        onRetry = onRefresh,
                        onDismissFailure = model::dismissFailure,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    state.selectedGrade?.let { grade ->
                        // 单条成绩详情半屏就放得下 → 允许 medium 停靠（半屏透、全屏实）。
                        AppleSheet(
                            onDismissRequest = model::dismissGradeDetails,
                            title = "成绩详情",
                            scrollableBody = true,
                        ) {
                            val detailScrollState = rememberScrollState()
                            GradeDetailSheetBody(
                                grade = grade,
                                showTitle = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(detailScrollState)
                                    .desktopTouchScroll(detailScrollState)
                                    .padding(horizontal = 24.dp)
                                    .padding(bottom = 28.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        // iOS 按内容定高（超出可用高度时由 sheet 滚动）；其他平台仍全屏展开。
        val fitFilterSheet = currentPlatform().family == PlatformFamily.IOS
        AppleSheet(
            onDismissRequest = { showFilterSheet = false },
            title = "筛选与计算",
            needsFullHeight = !fitFilterSheet,
            scrollableBody = !fitFilterSheet,
            fitContentHeight = fitFilterSheet,
        ) {
            GradeFilterSheet(
                state = state,
                model = model,
                fitContentHeight = fitFilterSheet,
                onSortOrderSelected = { order ->
                    model.setSortOrder(order)
                    showFilterSheet = false
                },
            )
        }
    }
}

@Composable
private fun GradeSummaryCard(
    state: GradeUiState,
    gradeInfo: GradeInfoResult,
    visibleGradeCount: Int,
    onOpenFilter: () -> Unit,
) {
    val title = when (val info = gradeInfo) {
        GradeInfoResult.NoGrades -> if (state.selectionMode) {
            "请选择用于计算的课程"
        } else {
            "暂无可计算成绩"
        }
        is GradeInfoResult.Calculated -> info.formattedMessage
    }
    val semesterFiltered =
        state.semesterFilterForQuery.isNotEmpty() || state.excludedCourseTypes.isNotEmpty()
    val subtitle = when {
        state.selectionMode -> "自选 ${state.selectedGradeIds.size} 门 · 显示 $visibleGradeCount 门"
        semesterFiltered -> "筛选后 $visibleGradeCount 门 · 共 ${state.grades.size} 门"
        else -> "共 ${state.grades.size} 门课程"
    }

    // 尺寸与课表 CourseSummary 对齐：18dp 圆角、14/12 内边距、右侧 pill 操作。
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenFilter)
                    .padding(vertical = 2.dp, horizontal = 4.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.accessibleAlpha(0.78f),
                )
            }
            Surface(
                onClick = onOpenFilter,
                color = MaterialTheme.colorScheme.surface.accessibleAlpha(0.86f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.semantics { contentDescription = "筛选与排序" },
            ) {
                // 筛选 + 排序：sheet 同时承载性质/学期筛选与成绩排序，用双图标表达。
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterFunnelIcon(modifier = Modifier.size(18.dp))
                    RankBarsIcon(modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** 漏斗形筛选图标（自绘，不引入 material-icons）。 */
@Composable
private fun FilterFunnelIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        val left = 2.5.dp.toPx()
        val right = size.width - left
        val top = 3.dp.toPx()
        val midY = size.height * 0.48f
        val neckLeft = size.width * 0.42f
        val neckRight = size.width * 0.58f
        val bottom = size.height - 2.5.dp.toPx()
        // 上宽下窄的漏斗轮廓
        drawLine(tint, Offset(left, top), Offset(right, top), stroke, StrokeCap.Round)
        drawLine(tint, Offset(left, top), Offset(neckLeft, midY), stroke, StrokeCap.Round)
        drawLine(tint, Offset(right, top), Offset(neckRight, midY), stroke, StrokeCap.Round)
        drawLine(tint, Offset(neckLeft, midY), Offset(neckLeft, bottom), stroke, StrokeCap.Round)
        drawLine(tint, Offset(neckRight, midY), Offset(neckRight, bottom), stroke, StrokeCap.Round)
        drawLine(tint, Offset(neckLeft, bottom), Offset(neckRight, bottom), stroke, StrokeCap.Round)
    }
}

/** 高度递增柱条：表示成绩排序/排名。 */
@Composable
private fun RankBarsIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val barWidth = 3.2.dp.toPx()
        val gap = 2.4.dp.toPx()
        val baseY = size.height - 2.5.dp.toPx()
        val heights = listOf(6.dp.toPx(), 10.dp.toPx(), 14.dp.toPx())
        val totalWidth = barWidth * 3 + gap * 2
        val startX = (size.width - totalWidth) / 2f
        heights.forEachIndexed { index, h ->
            val x = startX + index * (barWidth + gap)
            drawRoundRect(
                color = tint,
                topLeft = Offset(x, baseY - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(1.1.dp.toPx()),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.GradeFilterSheet(
    state: GradeUiState,
    model: GradeScreenModel,
    onSortOrderSelected: (GradeSortOrder) -> Unit,
    /** sheet 按内容定高时由 sheet 负责滚动和底部安全区，这里不能再套 verticalScroll。 */
    fitContentHeight: Boolean = false,
) {
    val semesterOptions = state.semesterOptions
    val allSemestersSelected =
        semesterOptions.isNotEmpty() && state.selectedSemesters.containsAll(semesterOptions)
    val filterScrollState = rememberScrollState()

    Column(
        modifier = if (fitContentHeight) {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 16.dp)
        } else {
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(filterScrollState)
                .desktopTouchScroll(filterScrollState)
                .sheetScrollContentPadding()
        },
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // —— 学期：小胶囊，默认全选 ——
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "学期",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (!allSemestersSelected && semesterOptions.isNotEmpty()) {
                    TextButton(onClick = model::clearSemesterFilter) { Text("全选") }
                }
            }
            if (semesterOptions.isEmpty()) {
                Text(
                    "暂无学期数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    semesterOptions.forEach { semester ->
                        FilterChip(
                            selected = semester in state.selectedSemesters,
                            onClick = { model.toggleSemester(semester) },
                            label = { Text(semester) },
                        )
                    }
                }
            }
        }

        // —— 排序：上维度（圆角矩形）+ 下方向（胶囊，随维度切换）——
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "排序",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val byScore =
                state.sortOrder == GradeSortOrder.ASCENDING ||
                    state.sortOrder == GradeSortOrder.DESCENDING
            // 维度：圆角矩形，与下方方向胶囊区分层级。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !byScore,
                    onClick = { model.selectSortCategory(byScore = false) },
                    shape = RoundedCornerShape(10.dp),
                    label = { Text("默认顺序") },
                )
                FilterChip(
                    selected = byScore,
                    onClick = { model.selectSortCategory(byScore = true) },
                    shape = RoundedCornerShape(10.dp),
                    label = { Text("分数高低") },
                )
            }
            // 方向：胶囊形。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (byScore) {
                    FilterChip(
                        selected = state.sortOrder == GradeSortOrder.DESCENDING,
                        onClick = { onSortOrderSelected(GradeSortOrder.DESCENDING) },
                        shape = RoundedCornerShape(percent = 50),
                        label = { Text("从高到低") },
                    )
                    FilterChip(
                        selected = state.sortOrder == GradeSortOrder.ASCENDING,
                        onClick = { onSortOrderSelected(GradeSortOrder.ASCENDING) },
                        shape = RoundedCornerShape(percent = 50),
                        label = { Text("从低到高") },
                    )
                } else {
                    FilterChip(
                        selected = state.sortOrder == GradeSortOrder.ORIGINAL,
                        onClick = { onSortOrderSelected(GradeSortOrder.ORIGINAL) },
                        shape = RoundedCornerShape(percent = 50),
                        label = { Text("正序") },
                    )
                    FilterChip(
                        selected = state.sortOrder == GradeSortOrder.ORIGINAL_REVERSED,
                        onClick = { onSortOrderSelected(GradeSortOrder.ORIGINAL_REVERSED) },
                        shape = RoundedCornerShape(percent = 50),
                        label = { Text("逆序") },
                    )
                }
            }
            if (!byScore) {
                Text(
                    "正序与教务网页行序一致；逆序将整张列表倒排。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // —— 课程性质：筛选模式用 excluded；自选模式实时映射已选门数 ——
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (state.selectionMode) {
                    "按课程性质勾选（点击全选/取消该类）"
                } else {
                    "按课程性质筛选（点击可勾选或取消勾选）"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.courseTypesByCode == null) {
                Text(
                    "课程性质未同步，下拉刷新后可用。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        CourseType.REQUIRED to "必修",
                        CourseType.LIMITED to "限选",
                        CourseType.ELECTIVE to "任选",
                        CourseType.PHYSICAL_EDUCATION to "体育",
                        CourseType.UNKNOWN to "其他类别",
                    ).forEach { (type, label) ->
                        val total = state.courseTypeCounts[type] ?: 0
                        if (total <= 0) return@forEach
                        val colors = courseTypeColors(type)
                        if (state.selectionMode) {
                            // 自选：严格跟 selectedGradeIds。0 门时全部 NONE（0/n、未选色），
                            // 与筛选模式「默认全选」满色脱钩，避免「自选 0 门但性质仍全亮」。
                            val selState = state.selectionStateForType(type)
                            val selectedCount = state.selectedCountForType(type)
                            val isAll = selState == CourseTypeSelectionState.ALL
                            val isPartial = selState == CourseTypeSelectionState.PARTIAL
                            val visuallyOn = isAll || isPartial
                            FilterChip(
                                selected = visuallyOn,
                                onClick = { model.toggleTypeSelection(type) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when {
                                        isAll -> colors.container
                                        isPartial -> colors.container.copy(alpha = 0.55f)
                                        else -> colors.container
                                    },
                                    selectedLabelColor = colors.onContainer,
                                    // 未选：刻意更淡，和筛选「全选满色」区分开
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        .accessibleAlpha(0.22f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        .accessibleAlpha(0.42f),
                                ),
                                border = BorderStroke(
                                    width = if (visuallyOn) 1.5.dp else 1.dp,
                                    color = if (visuallyOn) {
                                        colors.onContainer.copy(alpha = if (isPartial) 0.28f else 0.42f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.accessibleAlpha(0.4f)
                                    },
                                ),
                                label = {
                                    Text(
                                        "$label $selectedCount/$total",
                                        fontWeight = if (isAll) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                            )
                        } else {
                            val included = type !in state.excludedCourseTypes
                            FilterChip(
                                selected = included,
                                onClick = { model.toggleCourseTypeIncluded(type) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.container,
                                    selectedLabelColor = colors.onContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        .accessibleAlpha(0.35f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        .accessibleAlpha(0.48f),
                                ),
                                border = BorderStroke(
                                    width = if (included) 1.5.dp else 1.dp,
                                    color = if (included) {
                                        colors.onContainer.copy(alpha = 0.42f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.accessibleAlpha(0.55f)
                                    },
                                ),
                                label = {
                                    Text(
                                        "$label $total",
                                        fontWeight = if (included) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        // —— 自由选择课程模式：列表逐门勾选；开关形态 ——
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "自选课程计算",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.55f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "自由选择课程模式",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "开启后列表出现勾选框，可任意点选课程。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.selectionMode,
                        onCheckedChange = model::setSelectionMode,
                    )
                }
            }

            if (state.selectionMode) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = model::selectAllVisible) { Text("全选当前") }
                    OutlinedButton(onClick = model::clearAllSelections) { Text("全部清空") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 紧凑成绩页：摘要 Banner + 列表（刷新在顶栏按钮，列表仅平台原生过滚）。 */
@Composable
private fun GradeScrollableContent(
    state: GradeUiState,
    rows: List<GradeRowData>,
    gradeInfo: GradeInfoResult,
    model: GradeScreenModel,
    onOpenFilter: () -> Unit,
    onRetry: () -> Unit = {},
    onDismissFailure: () -> Unit = {},
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    // CompositionLocal 不能在 LazyColumn 的 DSL 作用域里读，提到可组合上下文。
    val topClearance = LocalTopBarClearance.current
    // 真实偏移上报给壳层算玻璃浓度（手势累加会漂，读列表状态不会）。
    // 稳定 key 重排时 LazyColumn 会锚定旧 item，导致跳到列表尾；排序变化时回顶。
    LaunchedEffect(state.sortOrder) {
        listState.scrollToItem(0)
    }
    TopScrollLazyColumn(
        state = listState,
        modifier = modifier.desktopTouchScroll(listState),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(
            // 首项靠内部顶边距让开原生栏（外层已不再占位）：8.dp 还原起笔位置。
            top = 8.dp + topClearance,
            bottom = 20.dp + LocalBottomBarClearance.current,
        ),
    ) {
        // underlap 时失败横幅也收进列表（外层已跳过），平时保持外层旧布局。
        if (topClearance > 0.dp) {
            state.failure?.let { failure ->
                item(key = "grade-failure") {
                    GradeFailureBanner(
                        failure = failure,
                        hasContent = state.grades.isNotEmpty(),
                        onRetry = onRetry,
                        onDismiss = onDismissFailure,
                    )
                }
            }
        }
        item(key = "grade-summary") {
            GradeSummaryCard(
                state = state,
                gradeInfo = gradeInfo,
                visibleGradeCount = rows.size,
                onOpenFilter = onOpenFilter,
            )
        }
        if (rows.isEmpty()) {
            item(key = "grade-empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "当前筛选条件下没有成绩",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(rows, key = GradeRowData::id) { row ->
                GradeRow(
                    row = row,
                    selectionMode = state.selectionMode,
                    selectedForCalculation = row.id in state.selectedGradeIds,
                    selectedForDetails = row.id == state.selectedGradeId,
                    onOpen = { model.showGradeDetails(row.id) },
                    onSelectionChange = { selected -> model.setGradeSelected(row.id, selected) },
                )
            }
        }
    }
}

@Composable
private fun GradeList(
    state: GradeUiState,
    rows: List<GradeRowData>,
    model: GradeScreenModel,
    modifier: Modifier,
) {
    if (rows.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "当前筛选条件下没有成绩",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    // 宽屏 Android 只有少量固定成绩项。整列预布局一次，避免 LazyColumn 在首次
    // 触摸时批量创建下一组卡片；手机/桌面仍保留 LazyColumn 的按需布局。
    if (currentPlatform().family == PlatformFamily.Android) {
        val scrollState = rememberScrollState()
        Column(
            modifier = modifier
                .verticalScroll(scrollState)
                .desktopTouchScroll(scrollState)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rows.forEach { row ->
                GradeRow(
                    row = row,
                    selectionMode = state.selectionMode,
                    selectedForCalculation = row.id in state.selectedGradeIds,
                    selectedForDetails = row.id == state.selectedGradeId,
                    onOpen = { model.showGradeDetails(row.id) },
                    onSelectionChange = { selected -> model.setGradeSelected(row.id, selected) },
                )
            }
        }
    } else {
        val listState = rememberLazyListState()
        LaunchedEffect(state.sortOrder) {
            listState.scrollToItem(0)
        }
        LazyColumn(
            state = listState,
            modifier = modifier.desktopTouchScroll(listState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            items(rows, key = GradeRowData::id) { row ->
            GradeRow(
                row = row,
                selectionMode = state.selectionMode,
                selectedForCalculation = row.id in state.selectedGradeIds,
                selectedForDetails = row.id == state.selectedGradeId,
                onOpen = { model.showGradeDetails(row.id) },
                onSelectionChange = { selected -> model.setGradeSelected(row.id, selected) },
                )
            }
        }
    }
}

@Immutable
private data class GradeRowData(
    val id: Int,
    val courseName: String,
    val semesterTeacher: String,
    val credits: String,
    val score: String,
    val scoreValue: Int,
    val courseType: CourseType?,
)

@Composable
private fun GradeRow(
    row: GradeRowData,
    selectionMode: Boolean,
    selectedForCalculation: Boolean,
    selectedForDetails: Boolean,
    onOpen: () -> Unit,
    onSelectionChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selectedForDetails) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        row.courseName,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 性质未同步（null）或未知的课程不显示标签，避免把映射缺失误读成任选。
                    if (row.courseType != null && row.courseType != CourseType.UNKNOWN) {
                        val colors = courseTypeColors(row.courseType)
                        Text(
                            row.courseType.displayName(),
                            color = colors.onContainer,
                            modifier = Modifier
                                .background(colors.container, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Text(
                    row.semesterTeacher,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.credits,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                row.score,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = gradeScoreColor(row.scoreValue),
            )
            if (selectionMode) {
                Checkbox(
                    checked = selectedForCalculation,
                    onCheckedChange = onSelectionChange,
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "选择${row.courseName}用于计算"
                    },
                )
            }
        }
    }
}

@Composable
private fun GradeDetailPanel(grade: Grade?, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.54f),
        shape = RoundedCornerShape(22.dp),
    ) {
        if (grade == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "选择一门课程查看详情",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            GradeDetailContent(
                grade = grade,
                contentPadding = PaddingValues(22.dp),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 宽屏侧栏 / 弹层共用的成绩详情正文。 */
@Composable
private fun GradeDetailContent(
    grade: Grade,
    modifier: Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val detailScrollState = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(detailScrollState)
            .desktopTouchScroll(detailScrollState)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GradeDetailSheetBody(grade = grade)
    }
}

@Composable
private fun GradeDetailSheetBody(
    grade: Grade,
    showTitle: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showTitle) {
            Text(
                "成绩详情",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            grade.displayCourseName(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        DetailLine("学期", grade.semester)
        DetailLine("教师", grade.courseTeacher.ifBlank { "未提供" })
        DetailLine("学分", grade.courseCredits)
        DetailLine("成绩", grade.courseScore)
        if (grade.detail.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.45f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("组成与说明", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        formatGradeDetailForDisplay(grade.detail),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            Text(
                "教务系统未提供更多组成信息。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun GradeFailureBanner(
    failure: GradeSyncFailure,
    hasContent: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppErrorBanner(
        message = when (failure) {
            GradeSyncFailure.NETWORK -> if (hasContent) {
                "同步失败，正在显示本地缓存。"
            } else {
                "无法连接教务系统，请检查网络后重试。"
            }
            GradeSyncFailure.SESSION_EXPIRED -> "教务会话已失效，请点击右上角刷新重试登录。"
            GradeSyncFailure.MALFORMED_RESPONSE -> "教务成绩页面结构已变化，暂时无法解析。"
            GradeSyncFailure.CACHE -> "本地成绩缓存操作失败，当前选择可能未保存。"
        },
        onRetry = if (failure != GradeSyncFailure.CACHE) onRetry else null,
        onDismiss = onDismiss,
    )
}

@Composable
private fun GradeLoadingState() = WorkspaceLoadingState("正在读取本地成绩并连接教务系统…")

@Composable
private fun GradeEmptyState(onRefresh: () -> Unit) = WorkspaceEmptyState(
    title = "暂无成绩",
    description = "本地没有缓存，教务系统也没有返回可显示的成绩。",
    onRefresh = onRefresh,
)

@Composable
private fun gradeScoreColor(numeric: Int): Color = when {
    numeric in 60..100 -> MaterialTheme.colorScheme.primary
    numeric in 0..59 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun GradeChangeNoticeDialog(
    changes: List<HomeChangeRecord>,
    onDismiss: () -> Unit,
    onOpenGrades: () -> Unit,
) {
    val visible = changes.filterNot {
        it.kind == DataChangeKind.MODIFIED && it.beforeDetail == it.afterDetail
    }
    if (visible.isEmpty()) return
    val changeScrollState = rememberScrollState()
    AppleSheetOrAlert(
        onDismissRequest = onDismiss,
        title = "成绩变动",
        confirmLabel = "前往成绩",
        onConfirm = onOpenGrades,
        dismissLabel = "知道了",
        needsFullHeight = true,
    ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(changeScrollState)
                    .desktopTouchScroll(changeScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visible.forEach { change ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                when (change.kind) {
                                    DataChangeKind.ADDED -> "新增"
                                    DataChangeKind.MODIFIED -> "修改"
                                    DataChangeKind.DELETED -> "删除"
                                },
                                color = when (change.kind) {
                                    DataChangeKind.ADDED -> MaterialTheme.colorScheme.primary
                                    DataChangeKind.MODIFIED -> MaterialTheme.colorScheme.tertiary
                                    DataChangeKind.DELETED -> MaterialTheme.colorScheme.error
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                change.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            if (change.beforeDetail.isNotBlank()) {
                                Text(
                                    "原：${change.beforeDetail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (change.afterDetail.isNotBlank()) {
                                Text(
                                    "现：${change.afterDetail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
    }
}

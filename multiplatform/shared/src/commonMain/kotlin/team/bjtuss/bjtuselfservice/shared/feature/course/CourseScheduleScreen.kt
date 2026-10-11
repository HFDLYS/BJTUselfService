package team.bjtuss.bjtuselfservice.shared.feature.course

import team.bjtuss.bjtuselfservice.shared.domain.course.eventDates
import team.bjtuss.bjtuselfservice.shared.domain.course.eventDatesLabel

import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalBottomBarClearance

import team.bjtuss.bjtuselfservice.shared.feature.common.WorkspaceEmptyState
import team.bjtuss.bjtuselfservice.shared.feature.common.WorkspaceLoadingState
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheetOrAlert

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import team.bjtuss.bjtuselfservice.shared.domain.course.displayScheduleCourseName
import team.bjtuss.bjtuselfservice.shared.domain.course.displayTitleWithTeacher
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import team.bjtuss.bjtuselfservice.shared.accessibleAlpha
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.currentPlatform
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSyncFailure
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.coursesForWeek
import team.bjtuss.bjtuselfservice.shared.domain.course.displayCoursePlace
import team.bjtuss.bjtuselfservice.shared.domain.course.parseCourseWeeks
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.domain.grade.courseTypeForCourseName
import team.bjtuss.bjtuselfservice.shared.domain.grade.displayName
import team.bjtuss.bjtuselfservice.shared.calendar.SystemCalendarGateway
import team.bjtuss.bjtuselfservice.shared.feature.calendar.CourseCalendarExportSheet
import team.bjtuss.bjtuselfservice.shared.feature.grade.courseTypeColors
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheet
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppErrorBanner
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway

/** 详情/网格等完整日名。 */
private val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
/**
 * 紧凑端日选择：单字保证任意机型单行七等分；比「周一」或 Mon 更省宽。
 * 选中态用容器色区分，不依赖长文案。
 */
private val compactDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
private val compactSlotLabels = listOf(
    "1\n08:00",
    "2\n10:10",
    "3\n12:10",
    "4\n14:10",
    "5\n16:20",
    "6\n19:00",
    "7\n21:00",
)
private val slotLabels = listOf(
    "第一节\n08:00–09:50",
    "第二节\n10:10–12:00",
    "第三节\n12:10–14:00",
    "第四节\n14:10–16:00",
    "第五节\n16:20–18:10",
    "第六节\n19:00–20:50",
    "第七节\n21:00–21:50",
)
private const val MILLIS_PER_DAY = 86_400_000L

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
fun CourseScheduleWorkspace(
    state: CourseScheduleUiState,
    courseTypesByCode: Map<String, CourseType>?,
    expanded: Boolean,
    model: CourseScheduleScreenModel,
    fileGateway: HomeworkFileGateway,
    systemCalendarGateway: SystemCalendarGateway,
    showCalendarExportSheet: Boolean,
    onDismissCalendarExport: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier,
                                        ) {
    var showSchedulePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val weekScrollAccumulator = remember { CourseWeekScrollAccumulator() }
    val useFingerWeekPager = shouldUseFingerWeekPager(currentPlatform())

    // 只灌缓存；网络自动同步由 AuthenticatedAppShell 在登录成功后触发（可重试）。
    LaunchedEffect(model) {
        model.initialize(refreshFromNetwork = false)
    }

    Column(
        modifier = if (expanded) {
            modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 同步态在 DestinationPage 顶栏；此处不再放页内「同步课表」。
        state.failure?.let { failure ->
            CourseFailureBanner(
                failure = failure,
                hasContent = state.courses.isNotEmpty(),
                onRetry = onRefresh,
                onDismiss = model::dismissFailure,
            )
        }

        when {
            state.isLoading && state.courses.isEmpty() -> CourseLoadingState()
            else -> {
                if (expanded) {
                    CourseSummary(
                        state = state,
                        onOpenPicker = { showSchedulePicker = true },
                        onOpenDatePicker = { showDatePicker = true },
                    )
                    if (state.scheduleCourses.isEmpty() && !state.isNonTeachingWeek) {
                        CourseEmptyState(state.scheduleType, onRefresh)
                    } else {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (state.dateOutsideTeachingWeeks && state.selectedNonTeachingWeekStart == null) {
                                NonTeachingDateState(state.selectedDate, Modifier.weight(0.65f).fillMaxHeight())
                            } else {
                                val weekPaneModifier = Modifier
                                    .weight(0.65f)
                                    .fillMaxHeight()
                                Column(
                                    modifier = if (useFingerWeekPager) {
                                        weekPaneModifier
                                    } else {
                                        weekPaneModifier.courseWeekScrollNavigation(weekScrollAccumulator) { direction ->
                                            when (direction) {
                                                CourseWeekScrollDirection.PREVIOUS -> model.moveWeekBy(-1)
                                                CourseWeekScrollDirection.NEXT -> model.moveWeekBy(1)
                                            }
                                        }
                                    },
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CourseTypeLegend(mappingLoaded = courseTypesByCode != null)
                                        Spacer(Modifier.weight(1f))
                                        // 移动端只保留手指横滑；上一周/下一周按钮只留给宽屏/桌面。
                                        if (!useFingerWeekPager) {
                                            CourseWeekNavigationControls(
                                                canPrevious = model.canMoveWeekBy(-1),
                                                canNext = model.canMoveWeekBy(1),
                                                onPrevious = { model.moveWeekBy(-1) },
                                                onNext = { model.moveWeekBy(1) },
                                            )
                                        }
                                    }
                                    if (useFingerWeekPager) {
                                        ExpandedWeekPager(
                                            state = state,
                                            courseTypesByCode = courseTypesByCode,
                                            model = model,
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                        )
                                    } else {
                                        AnimatedContent(
                                            targetState = CourseScheduleWeekPage(
                                                teachingWeek = state.selectedWeek.takeIf {
                                                    state.selectedNonTeachingWeekStart == null
                                                },
                                                startDate = state.selectedNonTeachingWeekStart
                                                    ?: state.weekDate()?.startDate,
                                                isOverview = state.selectedWeek == 0 &&
                                                    state.selectedNonTeachingWeekStart == null,
                                            ),
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            transitionSpec = {
                                                val movingForward = courseWeekTransitionMovesForward(
                                                    initial = initialState,
                                                    target = targetState,
                                                )
                                                val pagingSpring = spring<IntOffset>(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow,
                                                )
                                                (slideInHorizontally(
                                                    animationSpec = pagingSpring,
                                                    initialOffsetX = { width -> if (movingForward) width else -width },
                                                ) + fadeIn(tween(180))) togetherWith
                                                    (slideOutHorizontally(
                                                        animationSpec = pagingSpring,
                                                        targetOffsetX = { width -> if (movingForward) -width else width },
                                                    ) + fadeOut(tween(140)))
                                            },
                                            label = "desktop-course-week",
                                        ) { page ->
                                            WeekGrid(
                                                courses = state.coursesForPage(page.teachingWeek, page.startDate),
                                                courseTypesByCode = courseTypesByCode,
                                                weekStartDate = page.startDate,
                                                selectedCourseId = state.selectedCourseId,
                                                onOpen = model::showCourseDetails,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                }
                            }
                            CourseDetailPanel(
                                course = state.selectedCourse,
                                modifier = Modifier.weight(0.35f).fillMaxHeight(),
                            )
                        }
                    }
                } else if (state.scheduleCourses.isEmpty() && !state.isNonTeachingWeek) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CourseSummary(
                            state = state,
                            compact = true,
                            onOpenPicker = { showSchedulePicker = true },
                            onOpenDatePicker = { showDatePicker = true },
                        )
                        CourseEmptyState(state.scheduleType, onRefresh)
                    }
                } else {
                    // 紧凑端：摘要 + 星期选择 + 日课表同一滚动体。
                    CourseCompactScrollableContent(
                        state = state,
                        courseTypesByCode = courseTypesByCode,
                        model = model,
                        onOpenPicker = { showSchedulePicker = true },
                        onOpenDatePicker = { showDatePicker = true },
                        onOpen = model::showCourseDetails,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    state.selectedCourse?.let { course ->
                        // 详情半屏就能放完 → 允许 medium 停靠（半屏透、全屏实）。
                        AppleSheet(
                            onDismissRequest = model::dismissCourseDetails,
                            title = "课程详情",
                            scrollableBody = true,
                        ) {
                            val detailScrollState = rememberScrollState()
                            CourseDetailContent(
                                course = course,
                                modifier = Modifier.fillMaxWidth()
                                    .verticalScroll(detailScrollState)
                                    .desktopTouchScroll(detailScrollState)
                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                                    .padding(bottom = 20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSchedulePicker) {
        // 教学周芯片有二十多个，半屏放不下 → 直接全屏展开。
        AppleSheet(
            onDismissRequest = { showSchedulePicker = false },
            title = "课表与周数",
            needsFullHeight = true,
        ) {
            val pickerScrollState = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(pickerScrollState)
                    .desktopTouchScroll(pickerScrollState)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "课表类型",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.scheduleType == CourseScheduleType.CURRENT,
                        onClick = { model.selectScheduleType(CourseScheduleType.CURRENT) },
                        label = { Text("本学期课表") },
                    )
                    FilterChip(
                        selected = state.scheduleType == CourseScheduleType.SELECTION,
                        onClick = { model.selectScheduleType(CourseScheduleType.SELECTION) },
                        label = { Text("选课课表") },
                    )
                }
                Text(
                    "教学周",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val weekPages = courseScheduleWeekPages(state)
                    FilterChip(
                        selected = state.selectedWeek == 0 && state.selectedNonTeachingWeekStart == null,
                        onClick = {
                            model.selectWeek(0)
                            showSchedulePicker = false
                        },
                        label = { Text("全部教学周") },
                    )
                    val datedPages = weekPages.filterNot { it.isOverview }
                    if (datedPages.isEmpty()) {
                        (1..COURSE_MAX_WEEK).forEach { week ->
                            FilterChip(
                                selected = state.selectedWeek == week &&
                                    state.selectedNonTeachingWeekStart == null,
                                onClick = {
                                    model.selectWeek(week)
                                    showSchedulePicker = false
                                },
                                label = { Text(courseWeekLabel(state, week)) },
                            )
                        }
                    } else {
                        datedPages.forEach { page ->
                            FilterChip(
                                selected = when {
                                    page.isNonTeachingWeek ->
                                        state.selectedNonTeachingWeekStart == page.startDate
                                    else -> state.selectedNonTeachingWeekStart == null &&
                                        state.selectedWeek == page.teachingWeek
                                },
                                onClick = {
                                    when {
                                        page.isNonTeachingWeek -> page.startDate?.let(model::selectNonTeachingWeek)
                                        else -> page.teachingWeek?.let(model::selectWeek)
                                    }
                                    showSchedulePicker = false
                                },
                                label = {
                                    CourseWeekChipLabel(
                                        primary = page.teachingWeek?.let { week ->
                                            courseWeekLabel(state, week)
                                        } ?: "非教学周",
                                        startDate = page.startDate,
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showDatePicker) {
        CourseDatePickerDialog(
            selectedDate = state.selectedDate,
            locateWeekOnly = expanded || state.compactViewMode == CourseCompactViewMode.WEEK,
            onSelect = { date ->
                model.selectDate(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showCalendarExportSheet) {
        // 导出面板要放课程选择 + 两个动作，半屏放不下 → 全屏展开。
        AppleSheet(
            onDismissRequest = onDismissCalendarExport,
            title = "添加到日历",
            needsFullHeight = true,
        ) {
            CourseCalendarExportSheet(
                courseState = state,
                fileGateway = fileGateway,
                systemCalendarGateway = systemCalendarGateway,
                onDone = onDismissCalendarExport,
            )
        }
    }
}

@Composable
private fun CourseSummary(
    state: CourseScheduleUiState,
    compact: Boolean = false,
    onOpenPicker: () -> Unit,
    onOpenDatePicker: () -> Unit,
) {
    val typeLabel = if (state.scheduleType == CourseScheduleType.CURRENT) "本学期课表" else "选课课表"
    if (compact) {
        val isOverview = state.compactViewMode == CourseCompactViewMode.WEEK
        val compactWeekLabel = when {
            state.isNonTeachingWeek -> "非教学周"
            state.selectedWeek == 0 -> "全部教学周"
            else -> "第 ${state.selectedWeek} 周"
        }
        val selectedDateSuffix = when {
            state.isNonTeachingWeek -> state.selectedNonTeachingWeekStart
                ?.let { " · ${it.displayWeekRange()}" }
                .orEmpty()
            !isOverview && state.selectedWeek > 0 -> state.selectedDate
                ?.let { " · ${it.displayChineseMonthDay()}" }
                .orEmpty()
            else -> ""
        }
        val subtitle = state.compactSummarySubtitle(includeToday = !isOverview)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenPicker)
                    .padding(horizontal = 2.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    "$typeLabel · $compactWeekLabel$selectedDateSuffix",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SummaryAction("前往日期", "选择日期并前往对应课程", onOpenDatePicker)
            Surface(
                onClick = onOpenPicker,
                color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.72f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.semantics { contentDescription = "切换课表类型与周次" },
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ExchangeArrowsIcon(modifier = Modifier.size(18.dp))
                }
            }
        }
        return
    }
    val weekLabel = when {
        state.isNonTeachingWeek -> state.selectedNonTeachingWeekStart
            ?.displayWeekRange()
            ?: state.selectedDate?.displayDate()
            ?: "非教学周"
        state.selectedWeek == 0 -> "全部教学周"
        else -> "第 ${state.selectedWeek} 周"
    }
    // 副行只保留当前教学周提示；条数对用户无意义，已去掉。
    val subtitle = state.semesterStatusSubtitle()

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
                    .clickable(onClick = onOpenPicker)
                    .padding(vertical = 2.dp, horizontal = 4.dp),
            ) {
                Text(
                    "$typeLabel·$weekLabel",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.accessibleAlpha(0.78f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SummaryAction("前往日期", "选择日期并前往对应教学周", onOpenDatePicker)
                Surface(
                    onClick = onOpenPicker,
                    color = MaterialTheme.colorScheme.surface.accessibleAlpha(0.86f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.semantics { contentDescription = "切换课表类型与周次" },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExchangeArrowsIcon(modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryAction(label: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.accessibleAlpha(0.86f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseDatePickerDialog(
    selectedDate: LocalDate?,
    locateWeekOnly: Boolean,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val isIos = currentPlatform().family == PlatformFamily.IOS
    val initialMillis = selectedDate?.toEpochDays()?.times(MILLIS_PER_DAY)
    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    var nativeSelectedDate by remember(selectedDate, today) {
        mutableStateOf(selectedDate ?: today)
    }
    val pickerState = if (!isIos) {
        rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    } else {
        null
    }
    AppleSheetOrAlert(
        onDismissRequest = onDismiss,
        title = "前往日期",
        confirmLabel = if (locateWeekOnly) "前往这一周" else "前往这一天",
        confirmEnabled = isIos || pickerState?.selectedDateMillis != null,
        onConfirm = {
            if (isIos) {
                onSelect(nativeSelectedDate)
            } else {
                pickerState?.selectedDateMillis?.let { millis ->
                    onSelect(LocalDate.fromEpochDays(millis.floorDiv(MILLIS_PER_DAY)))
                }
            }
        },
        todayLabel = if (isIos) "今天" else null,
        onToday = if (isIos) ({ onSelect(today) }) else null,
        dismissLabel = "取消",
        needsFullHeight = isIos,
        scrollableBody = isIos,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .then(if (isIos) Modifier.fillMaxHeight().verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (isIos) {
                IosNativeDatePicker(
                    selectedDate = nativeSelectedDate,
                    onDateSelected = { nativeSelectedDate = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                DatePicker(
                    state = requireNotNull(pickerState),
                    title = null,
                    showModeToggle = true,
                )
            }
            if (!isIos) {
                TextButton(
                    onClick = {
                        nativeSelectedDate = today
                        onSelect(today)
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("今天")
                }
            }
        }
    }
}

@Composable
private fun NonTeachingDateState(date: LocalDate?, modifier: Modifier) {
    Box(modifier = modifier.padding(20.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                date?.displayDate() ?: "所选日期",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "不在当前学期的教学周内",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                "可能是假期、考试周，或当前学期校历未覆盖这一天。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.accessibleAlpha(0.76f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** SwapHoriz 风格：上下两条对向箭头。 */
@Composable
private fun ExchangeArrowsIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val stroke = 1.9.dp.toPx()
        val left = 2.dp.toPx()
        val right = size.width - left
        val head = 4.5.dp.toPx()
        val topY = size.height * 0.34f
        val bottomY = size.height * 0.66f

        // 上箭头：左 → 右
        drawLine(tint, Offset(left, topY), Offset(right, topY), stroke, StrokeCap.Round)
        drawLine(tint, Offset(right - head, topY - head * 0.7f), Offset(right, topY), stroke, StrokeCap.Round)
        drawLine(tint, Offset(right - head, topY + head * 0.7f), Offset(right, topY), stroke, StrokeCap.Round)

        // 下箭头：右 → 左
        drawLine(tint, Offset(right, bottomY), Offset(left, bottomY), stroke, StrokeCap.Round)
        drawLine(tint, Offset(left + head, bottomY - head * 0.7f), Offset(left, bottomY), stroke, StrokeCap.Round)
        drawLine(tint, Offset(left + head, bottomY + head * 0.7f), Offset(left, bottomY), stroke, StrokeCap.Round)
    }
}

private fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

private fun LocalDate.displayMonthDay(): String = "${month.ordinal + 1}/${day}"

private fun LocalDate.displayChineseMonthDay(): String = "${month.ordinal + 1}月${day}日"

private fun LocalDate.displayDate(): String = "${year}年${month.ordinal + 1}月${day}日"

private fun LocalDate.displayWeekRange(): String {
    val end = plusDays(6)
    return "${displayMonthDay()}–${end.displayMonthDay()}"
}

private fun courseWeekLabel(state: CourseScheduleUiState, week: Int): String =
    if (state.scheduleType == CourseScheduleType.CURRENT && week == state.currentWeek) {
        "第${week}周（当前）"
    } else {
        "第${week}周"
    }

@Composable
private fun CourseWeekChipLabel(
    primary: String,
    startDate: LocalDate?,
) {
    if (startDate == null) {
        Text(primary)
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(primary, maxLines = 1)
            Text(
                startDate.displayWeekRange(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun CourseScheduleUiState.compactSummarySubtitle(includeToday: Boolean): String? {
    val currentWeekText = semesterStatusSubtitle()
    val todayText = if (includeToday) todayDate?.let { "今天是 ${it.displayChineseMonthDay()}" } else null
    return listOfNotNull(currentWeekText, todayText).joinToString("，").ifBlank { null }
}

/** Keep seven time slots readable in a short viewport; horizontal week paging stays independent. */
@Composable
private fun ReadableWeekGridViewport(modifier: Modifier, fitAllSlots: Boolean = false, content: @Composable (Modifier) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
        // Normal portrait and unfolded views fit all seven slots. Only genuinely
        // short landscape viewports retain a larger scrollable grid.
        val gridHeight = if (fitAllSlots) maxOf(maxHeight, 42.dp + 28.dp * 7 * fontScale + 2.dp)
            else if (maxHeight >= 300.dp * fontScale) maxHeight
            else maxOf(maxHeight, 42.dp + 56.dp * 7 * fontScale + 2.dp)
        val scrollState = rememberScrollState()
        Box(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            content(Modifier.fillMaxWidth().height(gridHeight))
        }
    }
}

@Composable
private fun WeekGrid(
    courses: List<Course>,
    courseTypesByCode: Map<String, CourseType>?,
    weekStartDate: LocalDate?,
    selectedCourseId: Int?,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
    fitAllSlots: Boolean = false,
) {
    val byLocation = courses.groupBy(Course::courseLocationIndex)
    ReadableWeekGridViewport(modifier, fitAllSlots) { gridModifier ->
        Column(
            modifier = gridModifier.border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            ).padding(1.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(42.dp)) {
                Box(modifier = Modifier.width(78.dp).fillMaxHeight())
                dayLabels.forEachIndexed { index, day ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            weekStartDate?.let { start ->
                                Text(
                                    start.plusDays(index).displayMonthDay(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            repeat(7) { slot ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(
                        modifier = Modifier.width(78.dp).fillMaxHeight()
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            slotLabels[slot],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    repeat(7) { day ->
                        val location = slot * 8 + day + 1
                        CourseGridCell(
                            courses = byLocation[location].orEmpty(),
                            courseTypesByCode = courseTypesByCode,
                            selectedCourseId = selectedCourseId,
                            onOpen = onOpen,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NonTeachingWeekGrid(
    startDate: LocalDate?,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "非教学周",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                startDate?.let { "${it.displayChineseMonthDay()} 这一周" } ?: "校历未安排教学周",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "校历未安排课程，作业或考试安排仍可能出现在首页日程。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CourseGridCell(
    courses: List<Course>,
    courseTypesByCode: Map<String, CourseType>?,
    selectedCourseId: Int?,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (courses.isEmpty()) {
            Text("—", color = MaterialTheme.colorScheme.outlineVariant)
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                courses.forEach { course ->
                    val courseType = courseTypesByCode?.let { mapping ->
                        courseTypeForCourseName(course.courseId, mapping)
                    } ?: CourseType.UNKNOWN
                    val colors = scheduleCourseColors(course, courseType)
                    val isSelected = course.id == selectedCourseId
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        val showLocation = maxWidth >= 56.dp && maxHeight * course.eventSlotHeight >= 52.dp
                        Surface(
                            onClick = { onOpen(course.id) },
                            modifier = Modifier.fillMaxWidth().offset(y = maxHeight * course.eventSlotOffset)
                                .height(maxHeight * course.eventSlotHeight),
                            color = colors.container,
                            shape = RoundedCornerShape(7.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else colors.border,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    displayScheduleCourseName(course.courseName),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.onContainer,
                                )
                                if (showLocation) Text(
                                    displayCoursePlace(course.coursePlace),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.onContainer.accessibleAlpha(0.78f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseWeekNavigationControls(
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CourseWeekNavigationButton(
            label = "‹",
            contentDescription = "切换到上一个周位",
            enabled = canPrevious,
            onClick = onPrevious,
        )
        CourseWeekNavigationButton(
            label = "›",
            contentDescription = "切换到下一个周位",
            enabled = canNext,
            onClick = onNext,
        )
    }
}

@Composable
private fun CourseWeekNavigationButton(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(34.dp)
            .semantics { this.contentDescription = contentDescription },
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(999.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CompactDaySelector(selectedDay: Int, onSelect: (Int) -> Unit) {
    // 固定单行七等分，不随字号/机型折成两行（原 FlowRow +「周一」会在 Pro Max 上折行）。
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.48f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            compactDayLabels.forEachIndexed { index, label ->
                val selected = selectedDay == index
                Surface(
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = RoundedCornerShape(11.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 紧凑课表：
 * - 轻量控制栏 + 星期选择 **固定** 在上方（不随列表滚走）
 * - 下方 LazyColumn **占满剩余高度**，即使当天课很少也能在列表区域过滚/下拉刷新
 *
 * 以前把控制区塞进 LazyColumn：内容不满屏时整页几乎不能滑；内容超屏时控制区
 * 又跟着滚，观感都不对。
 */
@Composable
private fun CourseCompactScrollableContent(
    state: CourseScheduleUiState,
    courseTypesByCode: Map<String, CourseType>?,
    model: CourseScheduleScreenModel,
    onOpenPicker: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth >= 520.dp && maxWidth > maxHeight
        val landscapeControlsWidth = (maxWidth * 0.28f).coerceIn(210.dp, 280.dp)
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LandscapeCourseControls(
                    state = state, courseTypesByCode = courseTypesByCode, model = model,
                    onOpenPicker = onOpenPicker, onOpenDatePicker = onOpenDatePicker,
                    modifier = Modifier.width(landscapeControlsWidth).fillMaxHeight(),
                )
                val tableModifier = Modifier.weight(1f).fillMaxHeight()
                if (state.dateOutsideTeachingWeeks && state.selectedNonTeachingWeekStart == null) {
                    NonTeachingDateState(state.selectedDate, tableModifier)
                } else if (state.compactViewMode == CourseCompactViewMode.DAY) {
                    CompactDayPager(
                        courses = state.visibleCourses, courseTypesByCode = courseTypesByCode,
                        selectedDay = state.selectedDay, onSelectDay = model::selectDay, onOpen = onOpen,
                        modifier = tableModifier,
                    )
                } else {
                    CompactWeekPager(
                        state = state, courseTypesByCode = courseTypesByCode, model = model,
                        onOpen = onOpen, modifier = tableModifier, showControls = false, fitAllSlots = true,
                    )
                }
            }
        } else {
            val shortViewport = maxHeight < 300.dp
            val gridHeight = 42.dp + 56.dp * 7 * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f) + 50.dp
            Column(
                modifier = if (shortViewport) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CourseSummary(
                    state = state,
                    compact = true,
                    onOpenPicker = onOpenPicker,
                    onOpenDatePicker = onOpenDatePicker,
                )
                CompactViewModeSelector(state.compactViewMode, model::selectCompactViewMode)
                if (state.dateOutsideTeachingWeeks && state.selectedNonTeachingWeekStart == null) {
                    NonTeachingDateState(state.selectedDate, if (shortViewport) Modifier.fillMaxWidth().height(gridHeight) else Modifier.weight(1f).fillMaxWidth())
                } else if (state.compactViewMode == CourseCompactViewMode.DAY) {
                    CompactDaySelector(state.selectedDay, model::selectDay)
                    CompactDayPager(
                        courses = state.visibleCourses,
                        courseTypesByCode = courseTypesByCode,
                        selectedDay = state.selectedDay,
                        onSelectDay = model::selectDay,
                        onOpen = onOpen,
                        modifier = if (shortViewport) Modifier.fillMaxWidth().height(gridHeight) else Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
                    CompactWeekPager(
                        state = state,
                        courseTypesByCode = courseTypesByCode,
                        model = model,
                        onOpen = onOpen,
                        modifier = if (shortViewport) Modifier.fillMaxWidth().height(gridHeight) else Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeCourseControls(
    state: CourseScheduleUiState,
    courseTypesByCode: Map<String, CourseType>?,
    model: CourseScheduleScreenModel,
    onOpenPicker: () -> Unit,
    onOpenDatePicker: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(
            bottom = if (state.compactViewMode == CourseCompactViewMode.DAY) LocalBottomBarClearance.current else 0.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onOpenPicker)) {
            Text(if (state.scheduleType == CourseScheduleType.CURRENT) "本学期课表" else "选课课表",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(when {
                state.isNonTeachingWeek -> "非教学周"
                state.selectedWeek == 0 -> "全部教学周"
                else -> "第 ${state.selectedWeek} 周"
            }, style = MaterialTheme.typography.bodyMedium)
            state.compactSummarySubtitle(includeToday = state.compactViewMode == CourseCompactViewMode.DAY)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryAction("课表／周次", "切换课表类型与周次", onOpenPicker)
            SummaryAction("前往日期", "选择日期并前往对应课程", onOpenDatePicker)
        }
        CompactViewModeSelector(state.compactViewMode, model::selectCompactViewMode, showIcons = false)
        if (state.compactViewMode == CourseCompactViewMode.DAY) {
            CompactDaySelector(state.selectedDay, model::selectDay)
        }
        CourseTypeLegend(mappingLoaded = courseTypesByCode != null, modifier = Modifier.fillMaxWidth(), separateEvents = true)
        Text(if (state.compactViewMode == CourseCompactViewMode.WEEK) "左右滑动切换周数" else "左右滑动切换日期",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompactViewModeSelector(
    mode: CourseCompactViewMode,
    onSelect: (CourseCompactViewMode) -> Unit,
    showIcons: Boolean = true,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == CourseCompactViewMode.WEEK,
            onClick = { onSelect(CourseCompactViewMode.WEEK) },
            label = {
                if (showIcons) CompactViewModeLabel(
                    text = "色块概览",
                    icon = { CompactTableIcon(Modifier.size(17.dp)) },
                ) else Text("色块概览", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), maxLines = 1)
            },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = mode == CourseCompactViewMode.DAY,
            onClick = { onSelect(CourseCompactViewMode.DAY) },
            label = {
                if (showIcons) CompactViewModeLabel(
                    text = "课程列表",
                    icon = { CompactListIcon(Modifier.size(17.dp)) },
                ) else Text("课程列表", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), maxLines = 1)
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactViewModeLabel(
    text: String,
    icon: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        icon()
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompactListIcon(modifier: Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val dotRadius = size.minDimension * 0.08f
        val lineStart = size.width * 0.34f
        val lineEnd = size.width * 0.92f
        listOf(0.22f, 0.5f, 0.78f).forEach { fraction ->
            val y = size.height * fraction
            drawCircle(color, dotRadius, Offset(size.width * 0.12f, y))
            drawLine(color, Offset(lineStart, y), Offset(lineEnd, y), strokeWidth = size.height * 0.1f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun CompactTableIcon(modifier: Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val stroke = size.minDimension * 0.09f
        val left = stroke / 2f
        val right = size.width - stroke / 2f
        val top = stroke / 2f
        val bottom = size.height - stroke / 2f
        drawLine(color, Offset(left, top), Offset(right, top), stroke, StrokeCap.Round)
        drawLine(color, Offset(left, bottom), Offset(right, bottom), stroke, StrokeCap.Round)
        drawLine(color, Offset(left, top), Offset(left, bottom), stroke, StrokeCap.Round)
        drawLine(color, Offset(right, top), Offset(right, bottom), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width / 2f, top), Offset(size.width / 2f, bottom), stroke)
        drawLine(color, Offset(left, size.height / 2f), Offset(right, size.height / 2f), stroke)
    }
}

@Composable
private fun CompactDayPager(
    courses: List<Course>,
    courseTypesByCode: Map<String, CourseType>?,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    val pagerState = rememberPagerState(initialPage = selectedDay) { 7 }
    LaunchedEffect(pagerState.currentPage) {
        if (selectedDay != pagerState.currentPage) onSelectDay(pagerState.currentPage)
    }
    LaunchedEffect(selectedDay) {
        if (selectedDay != pagerState.currentPage) pagerState.scrollToPage(selectedDay)
    }
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = 1,
        pageSpacing = 12.dp,
    ) { day ->
        val byLocation = courses.groupBy(Course::courseLocationIndex)
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().desktopTouchScroll(listState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 18.dp + LocalBottomBarClearance.current),
        ) {
            items((0 until 7).toList(), key = { "day-$day-slot-$it" }) { slot ->
                val location = slot * 8 + day + 1
                DayScheduleSlotRow(
                    slotLabel = slotLabels[slot],
                    slotCourses = byLocation[location].orEmpty(),
                    courseTypesByCode = courseTypesByCode,
                    onOpen = onOpen,
                )
            }
        }
    }
}

@Composable
private fun ExpandedWeekPager(
    state: CourseScheduleUiState,
    courseTypesByCode: Map<String, CourseType>?,
    model: CourseScheduleScreenModel,
    modifier: Modifier,
) {
    val pages = remember(state.academicWeeks, state.selectedNonTeachingWeekStart) {
        courseScheduleWeekPages(state)
    }
    val selectedPage = pages.indexOfFirst { page ->
        when {
            page.isOverview -> state.selectedWeek == 0 && state.selectedNonTeachingWeekStart == null
            page.isNonTeachingWeek -> page.startDate == state.selectedNonTeachingWeekStart
            else -> page.teachingWeek == state.selectedWeek
        }
    }.takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(initialPage = selectedPage) { pages.size }
    var programmaticTargetPage by remember { mutableStateOf<Int?>(null) }
    val latestPages by rememberUpdatedState(pages)
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(pagerState, pages) {
        var firstSettledEmission = true
        snapshotFlow { pagerState.settledPage }.collect { pageIndex ->
            // Replacing the semester/校历 can rebuild the page list while UIKit/Compose
            // still reports the old settled index. That first value is not a user swipe.
            if (firstSettledEmission) {
                firstSettledEmission = false
                if (pageIndex != selectedPage) return@collect
            }
            val target = programmaticTargetPage
            if (target != null) {
                if (pageIndex == target) programmaticTargetPage = null
                return@collect
            }
            val page = latestPages[pageIndex.coerceIn(latestPages.indices)]
            when {
                page.isOverview -> if (latestState.selectedWeek != 0 || latestState.selectedNonTeachingWeekStart != null) {
                    model.selectWeek(0)
                }
                page.isNonTeachingWeek -> page.startDate?.let { start ->
                    if (latestState.selectedNonTeachingWeekStart != start) model.selectNonTeachingWeek(start)
                }
                else -> page.teachingWeek?.let { week ->
                    if (latestState.selectedWeek != week || latestState.selectedNonTeachingWeekStart != null) {
                        model.selectWeek(week)
                    }
                }
            }
        }
    }
    LaunchedEffect(selectedPage, pages) {
        if (selectedPage != pagerState.currentPage && !pagerState.isScrollInProgress) {
            programmaticTargetPage = selectedPage
            pagerState.animateScrollToPage(selectedPage)
            if (pagerState.settledPage == selectedPage) programmaticTargetPage = null
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = 1,
        pageSpacing = 8.dp,
    ) { page ->
        val weekPage = pages[page]
        if (weekPage.isNonTeachingWeek) {
            WeekGrid(
                courses = state.coursesForPage(null, weekPage.startDate), courseTypesByCode = courseTypesByCode,
                weekStartDate = weekPage.startDate, selectedCourseId = state.selectedCourseId,
                onOpen = model::showCourseDetails, modifier = Modifier.fillMaxSize(),
            )
        } else {
            WeekGrid(
                courses = state.coursesForPage(weekPage.teachingWeek ?: 0, weekPage.startDate),
                courseTypesByCode = courseTypesByCode,
                weekStartDate = weekPage.startDate
                    ?: weekPage.teachingWeek?.let { state.weekDate(it)?.startDate },
                selectedCourseId = state.selectedCourseId,
                onOpen = model::showCourseDetails,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CompactWeekPager(
    state: CourseScheduleUiState,
    courseTypesByCode: Map<String, CourseType>?,
    model: CourseScheduleScreenModel,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
    showControls: Boolean = true,
    fitAllSlots: Boolean = false,
) {
    // Page 0 仍是“全部教学周”；其后的页面严格按校历自然周排序，
    // 中间缺失的周会显示为非教学周，而不是直接从第 3 周跳到第 4 周。
    val pages = remember(state.academicWeeks, state.selectedNonTeachingWeekStart) {
        courseScheduleWeekPages(state)
    }
    val selectedPage = pages.indexOfFirst { page ->
        when {
            page.isOverview -> state.selectedWeek == 0 && state.selectedNonTeachingWeekStart == null
            page.isNonTeachingWeek -> page.startDate == state.selectedNonTeachingWeekStart
            else -> page.teachingWeek == state.selectedWeek
        }
    }.takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(initialPage = selectedPage) { pages.size }
    var programmaticTargetPage by remember { mutableStateOf<Int?>(null) }
    val latestPages by rememberUpdatedState(pages)
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(pagerState, pages) {
        var firstSettledEmission = true
        snapshotFlow { pagerState.settledPage }.collect { pageIndex ->
            if (firstSettledEmission) {
                firstSettledEmission = false
                if (pageIndex != selectedPage) return@collect
            }
            val target = programmaticTargetPage
            if (target != null) {
                if (pageIndex == target) programmaticTargetPage = null
                return@collect
            }
            val page = latestPages[pageIndex.coerceIn(latestPages.indices)]
            when {
                page.isOverview -> if (latestState.selectedWeek != 0 || latestState.selectedNonTeachingWeekStart != null) {
                    model.selectWeek(0)
                }
                page.isNonTeachingWeek -> page.startDate?.let { start ->
                    if (latestState.selectedNonTeachingWeekStart != start) model.selectNonTeachingWeek(start)
                }
                else -> page.teachingWeek?.let { week ->
                    if (latestState.selectedWeek != week || latestState.selectedNonTeachingWeekStart != null) {
                        model.selectWeek(week)
                    }
                }
            }
        }
    }
    LaunchedEffect(selectedPage, pages) {
        if (selectedPage != pagerState.currentPage) {
            programmaticTargetPage = selectedPage
            pagerState.scrollToPage(selectedPage)
            if (pagerState.settledPage == selectedPage) programmaticTargetPage = null
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (showControls) Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CourseTypeLegend(mappingLoaded = courseTypesByCode != null, modifier = Modifier.weight(1f))
            Text(
                "滑动切换周数",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1,
            pageSpacing = 12.dp,
        ) { page ->
            val weekPage = pages[page]
            if (weekPage.isNonTeachingWeek) {
                WeekGrid(
                courses = state.coursesForPage(null, weekPage.startDate), courseTypesByCode = courseTypesByCode,
                weekStartDate = weekPage.startDate, selectedCourseId = state.selectedCourseId,
                onOpen = model::showCourseDetails, modifier = Modifier.fillMaxSize(),
                fitAllSlots = fitAllSlots,
            )
            } else {
                CompactWeekGrid(
                    courses = state.coursesForPage(weekPage.teachingWeek ?: 0, weekPage.startDate),
                    courseTypesByCode = courseTypesByCode,
                    aggregateAllWeeks = weekPage.isOverview,
                    weekStartDate = weekPage.startDate
                        ?: weekPage.teachingWeek?.let { state.weekDate(it)?.startDate },
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxSize(),
                    fitAllSlots = fitAllSlots,
                )
            }
        }
    }
}

@Composable
private fun CourseTypeLegend(
    mappingLoaded: Boolean,
    modifier: Modifier = Modifier,
    separateEvents: Boolean = false,
) {
    val types = listOf(
        CourseType.REQUIRED,
        CourseType.LIMITED,
        CourseType.ELECTIVE,
        CourseType.PHYSICAL_EDUCATION,
        CourseType.UNKNOWN,
    )
    val unknownLabel = if (mappingLoaded) "未知" else "未同步"
    val courseEntries = types.map { type ->
        (if (type == CourseType.UNKNOWN) unknownLabel else type.displayName()) to courseScheduleTypeColors(type)
    }
    val eventEntries = listOf("实验" to scheduleEventColors("physicslab"), "考试" to scheduleEventColors("exam"))
    val legendModifier = modifier.semantics {
        contentDescription = "课程性质图例：必修、限选、任选、体育、$unknownLabel、实验、考试"
    }
    if (separateEvents) {
        // 横屏左栏按语义分成课程性质与特殊安排，避免最后一枚标签孤零零换行。
        Column(legendModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CourseLegendRow(courseEntries, Modifier.fillMaxWidth())
            CourseLegendRow(eventEntries, Modifier.fillMaxWidth())
        }
    } else {
        CourseLegendRow(courseEntries + eventEntries, legendModifier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseLegendRow(
    entries: List<Pair<String, team.bjtuss.bjtuselfservice.shared.feature.grade.GradeTypeColors>>,
    modifier: Modifier,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        entries.forEach { (label, colors) ->
            Surface(
                color = colors.container,
                shape = RoundedCornerShape(7.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.onContainer,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CompactWeekGrid(
    courses: List<Course>,
    courseTypesByCode: Map<String, CourseType>?,
    aggregateAllWeeks: Boolean,
    weekStartDate: LocalDate?,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
    fitAllSlots: Boolean = false,
) {
    val byLocation = courses.groupBy(Course::courseLocationIndex)
    ReadableWeekGridViewport(modifier, fitAllSlots) { gridModifier ->
        Column(
            modifier = gridModifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(13.dp))
                .padding(1.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(38.dp)) {
                Box(modifier = Modifier.width(45.dp).fillMaxHeight())
                compactDayLabels.forEachIndexed { day, label ->
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            weekStartDate?.let { start ->
                                Text(
                                    start.plusDays(day).displayMonthDay(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            repeat(7) { slot ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(
                        modifier = Modifier.width(45.dp).fillMaxHeight()
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            compactSlotLabels[slot],
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                    repeat(7) { day ->
                        val location = slot * 8 + day + 1
                        CompactCourseGridCell(
                            courses = byLocation[location].orEmpty(),
                            courseTypesByCode = courseTypesByCode,
                            aggregateAllWeeks = aggregateAllWeeks,
                            onOpen = onOpen,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactCourseGridCell(
    courses: List<Course>,
    courseTypesByCode: Map<String, CourseType>?,
    aggregateAllWeeks: Boolean,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    val cellModifier = modifier
        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        .padding(2.dp)
    if (courses.size > 1) {
        // “全部教学周”把同一时间位置的单双周/交替课程并排分格；具体周页仍只显示当周课程。
        Row(
            modifier = cellModifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            courses.sortedWith(
                compareBy<Course>(
                    { parseCourseWeeks(it.courseTime).minOrNull() ?: Int.MAX_VALUE },
                    Course::courseId,
                    Course::id,
                ),
            ).forEach { course ->
                CompactCourseColorBlock(
                    course = course,
                    courseTypesByCode = courseTypesByCode,
                    onOpen = onOpen,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        return
    }
    Column(
        modifier = cellModifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        courses.forEach { course ->
            CompactCourseColorBlock(
                course = course,
                courseTypesByCode = courseTypesByCode,
                onOpen = onOpen,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CompactCourseColorBlock(
    course: Course,
    courseTypesByCode: Map<String, CourseType>?,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    // 与成绩页共用培养方案的“课程号 → 课程性质”映射；未同步/未命中才显示灰色未知。
    val courseType = courseTypesByCode?.let { mapping ->
        courseTypeForCourseName(course.courseId, mapping)
    } ?: CourseType.UNKNOWN
    val colors = scheduleCourseColors(course, courseType)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        Surface(
            onClick = { onOpen(course.id) },
            modifier = Modifier.fillMaxWidth().offset(y = maxHeight * course.eventSlotOffset)
                .height(maxHeight * course.eventSlotHeight).semantics {
                contentDescription = "${displayScheduleCourseName(course.courseName)}，${course.eventDatesLabel()} ${course.scheduleEventTime ?: course.courseTime}，${displayCoursePlace(course.coursePlace)}，点按查看详情"
            },
            color = colors.container,
            shape = RoundedCornerShape(5.dp),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border),
        ) {}
    }
}

@Composable
private fun DayScheduleSlotRow(
    slotLabel: String,
    slotCourses: List<Course>,
    courseTypesByCode: Map<String, CourseType>?,
    onOpen: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(
            slotLabel,
            modifier = Modifier.width(88.dp).padding(top = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (slotCourses.isEmpty()) {
                // 单层淡底 + 弱化文字，避免「外圈深、内块浅」的双层方框感。
                Text(
                    "无课",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.accessibleAlpha(0.55f),
                )
            } else {
                slotCourses.forEach { course ->
                    CourseListCard(course, courseTypesByCode, onOpen)
                }
            }
        }
    }
}

@Composable
private fun CourseListCard(
    course: Course,
    courseTypesByCode: Map<String, CourseType>?,
    onOpen: (Int) -> Unit,
) {
    // 底色与「色块概览」同源：培养方案课程号 → 课程性质 → 配色；未命中用 UNKNOWN 灰。
    val courseType = courseTypesByCode?.let { mapping ->
        courseTypeForCourseName(course.courseId, mapping)
    } ?: CourseType.UNKNOWN
    val colors = scheduleCourseColors(course, courseType)
    // 扁平 Surface：无 elevation 阴影描边，避免外圈偏深、正文区又叠浅色矩形的双层感。
    Surface(
        onClick = { onOpen(course.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.container,
        contentColor = colors.onContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(course.displayTitleWithTeacher(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                displayCoursePlace(course.coursePlace),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onContainer.accessibleAlpha(0.78f),
            )
            Text(
                course.scheduleEventTime?.let { "${course.eventDatesLabel()} · $it" } ?: course.courseTime,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onContainer.accessibleAlpha(0.72f),
            )
        }
    }
}

@Composable
private fun CourseDetailPanel(course: Course?, modifier: Modifier) {
    val detailScrollState = rememberScrollState()
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.54f),
        shape = RoundedCornerShape(22.dp),
    ) {
        if (course == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("选择一门课程查看详情", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            CourseDetailContent(
                course = course,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(detailScrollState)
                    .desktopTouchScroll(detailScrollState)
                    .padding(22.dp),
            )
        }
    }
}

@Composable
internal fun CourseDetailContent(course: Course, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(displayScheduleCourseName(course.courseName), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (course.scheduleEventKind == null) CourseDetailLine("编号", course.courseId)
        if (course.scheduleEventKind != "exam") CourseDetailLine("教师", course.courseTeacher.ifBlank { "未提供" })
        if (course.scheduleEventKind == null) CourseDetailLine("周次", course.courseTime)
        CourseDetailLine("地点", displayCoursePlace(course.coursePlace))
        val slot = course.courseLocationIndex / 8
        val day = course.courseLocationIndex % 8 - 1
        if (course.scheduleEventTime != null) {
            CourseDetailLine("日期", course.eventDates.joinToString("\n"))
            CourseDetailLine("时间", course.scheduleEventTime)
        } else {
            CourseDetailLine("时间", "${dayLabels.getOrElse(day) { "未知" }} · ${slotLabels.getOrElse(slot) { "未知" }.replace('\n', ' ')}")
        }
        CourseDetailLine(
            "类型",
            when (course.scheduleEventKind) {
                "physicslab" -> course.scheduleEventTypeLabel ?: "物理实验"
                "phydemo" -> "演示实验"
                "exam" -> "考试"
                else -> if (course.isCurrentSemester) "选课课表" else "本学期课表"
            },
        )
    }
}

@Composable
private fun CourseDetailLine(label: String, value: String) {
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
private fun CourseFailureBanner(
    failure: CourseScheduleSyncFailure,
    hasContent: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppErrorBanner(
        message = when (failure) {
            CourseScheduleSyncFailure.NETWORK -> if (hasContent) {
                "同步失败，正在显示本地课表。"
            } else {
                "无法连接教务系统，请检查网络后重试。"
            }
            CourseScheduleSyncFailure.SESSION_EXPIRED -> "教务会话已失效，请点击右上角刷新重试登录。"
            CourseScheduleSyncFailure.MALFORMED_RESPONSE -> "教务课表页面结构已变化，暂时无法解析。"
            CourseScheduleSyncFailure.CACHE -> "本地课表缓存操作失败。"
        },
        onRetry = if (failure != CourseScheduleSyncFailure.CACHE) onRetry else null,
        onDismiss = onDismiss,
    )
}

@Composable
private fun CourseLoadingState() = WorkspaceLoadingState("正在读取本地课表并连接教务系统…")

@Composable
private fun CourseEmptyState(type: CourseScheduleType, onRefresh: () -> Unit) = WorkspaceEmptyState(
    title = "暂无课表",
    description = if (type == CourseScheduleType.CURRENT) {
        "本地和教务系统均没有返回本学期课程。"
    } else {
        "教务系统当前没有可显示的选课课表。"
    },
    onRefresh = onRefresh,
)

@Composable
private fun scheduleEventColors(kind: String): team.bjtuss.bjtuselfservice.shared.feature.grade.GradeTypeColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (kind == "physicslab" || kind == "phydemo") {
        if (dark) team.bjtuss.bjtuselfservice.shared.feature.grade.GradeTypeColors(Color(0xFF3B2858), Color(0xFFDEC6F6))
        else team.bjtuss.bjtuselfservice.shared.feature.grade.GradeTypeColors(Color(0xFFEADBFA), Color(0xFF64368E))
    } else {
        if (dark) team.bjtuss.bjtuselfservice.shared.feature.grade.GradeTypeColors(Color(0xFF3D2A12), Color(0xFFEAA04B))
        else team.bjtuss.bjtuselfservice.shared.feature.grade.GradeTypeColors(Color(0xFFFFE6C8), Color(0xFFAF641C))
    }
}

@Composable
private fun courseScheduleTypeColors(type: CourseType): team.bjtuss.bjtuselfservice.shared.feature.grade.GradeTypeColors {
    if (type != CourseType.LIMITED) return courseTypeColors(type)

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        team.bjtuss.bjtuselfservice.shared.feature.grade.GradeTypeColors(
            container = Color(0xFF48243A),
            onContainer = Color(0xFFF0B2D2),
        )
    } else {
        team.bjtuss.bjtuselfservice.shared.feature.grade.GradeTypeColors(
            container = Color(0xFFF4D7E6),
            onContainer = Color(0xFF9C3F70),
        )
    }
}

@Composable
private fun scheduleCourseColors(course: Course, type: CourseType) =
    course.scheduleEventKind?.let { scheduleEventColors(it) } ?: courseScheduleTypeColors(type)

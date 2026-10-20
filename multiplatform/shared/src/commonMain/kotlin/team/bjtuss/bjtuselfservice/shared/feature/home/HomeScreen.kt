package team.bjtuss.bjtuselfservice.shared.feature.home

import team.bjtuss.bjtuselfservice.shared.domain.time.chineseLabel
import team.bjtuss.bjtuselfservice.shared.domain.time.deadlineDisplay
import team.bjtuss.bjtuselfservice.shared.domain.time.formatSchoolDeadline
import team.bjtuss.bjtuselfservice.shared.feature.shell.bottomSystemBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalBottomBarClearance

import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheet
import team.bjtuss.bjtuselfservice.shared.feature.shell.sheetScrollContentPadding
import team.bjtuss.bjtuselfservice.shared.feature.shell.TopScrollLazyColumn
import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalTopBarClearance
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.displayScheduleCourseName
import team.bjtuss.bjtuselfservice.shared.domain.course.displayTitleWithTeacher
import team.bjtuss.bjtuselfservice.shared.domain.course.classTimeRange
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseDetailContent
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.collect
import kotlin.time.Instant
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.PlatformInfo
import team.bjtuss.bjtuselfservice.shared.currentPlatform
import team.bjtuss.bjtuselfservice.shared.data.home.HomeStatusFailure
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxUnreadSummary
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeKind
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.AcademicWeekSlot
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.academicWeekSlots
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeAgenda
import team.bjtuss.bjtuselfservice.shared.domain.home.agendaWeekMarks
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeAgendaDay
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeDomain
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeRecord
import team.bjtuss.bjtuselfservice.shared.domain.home.matchExamChange
import team.bjtuss.bjtuselfservice.shared.domain.home.matchHomeworkChange
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeStatus
import team.bjtuss.bjtuselfservice.shared.domain.home.buildHomeAgenda
import team.bjtuss.bjtuselfservice.shared.domain.home.isHomeAgendaDayFullySubmitted
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
import team.bjtuss.bjtuselfservice.shared.domain.homework.isHomeworkSubmitted
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabEvent
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabEventKind
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseWeekScrollAccumulator
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseWeekScrollDirection
import team.bjtuss.bjtuselfservice.shared.feature.course.courseWeekScrollNavigation
import team.bjtuss.bjtuselfservice.shared.domain.home.HOME_MAX_TEACHING_WEEK
import team.bjtuss.bjtuselfservice.shared.domain.home.resolveHomeAgendaWeekStart
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll

@Composable
fun HomeWorkspace(
    model: HomeScreenModel,
    platform: PlatformInfo,
    expanded: Boolean,
    mailboxUnread: MailboxUnreadSummary? = null,
    homework: List<Homework>,
    exams: List<ExamSchedule>,
    phyVlabEvents: List<PhyVlabEvent> = emptyList(),
    currentWeek: Int,
    academicWeeks: List<OccupancyWeekDate> = emptyList(),
    /** 周数是否已由本学期校历确认；未确认时首页显示「日程加载中」。 */
    isWeekResolved: Boolean = true,
    now: LocalDateTime,
    timeZone: TimeZone,
    isAgendaLoading: Boolean,
    onOpenMailbox: () -> Unit,
    onOpenHomework: () -> Unit,
    onOpenHomeworkDetail: (Homework) -> Unit = {},
    onOpenExams: (ExamSchedule) -> Unit,
    onOpenPhyVlab: (PhyVlabEvent) -> Unit = {},
    changes: List<HomeChangeRecord>,
    onClearAllChanges: () -> Unit,
    onClearChangeDomain: (HomeChangeDomain) -> Unit,
    onOpenChangeDomain: (HomeChangeDomain) -> Unit,
    // 静默自动登录期间为 true：会话未就绪，初始化（含网络刷新）延后到登录完成。
    holdNetwork: Boolean = false,
    todayRequest: Int = 0,
    onTodaySelectedChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val pageListState = rememberLazyListState()
    LaunchedEffect(todayRequest) {
        if (todayRequest > 0) pageListState.scrollToItem(0)
    }
    var selectedChangeDomain by remember { mutableStateOf<HomeChangeDomain?>(null) }
    var selectedChangeCourse by remember { mutableStateOf<Course?>(null) }
    val homeSchedule = LocalHomeSchedule.current
    LaunchedEffect(model, holdNetwork) { if (!holdNetwork) team.bjtuss.bjtuselfservice.shared.logging.SyncTiming.measure("startup.home-status") { model.initialize() } }
    selectedChangeDomain?.let { domain ->
        HomeChangeDialog(
            domain = domain,
            changes = changes.filter { it.domain == domain },
            onDismiss = { selectedChangeDomain = null },
            onMarkRead = {
                selectedChangeDomain = null
                onClearChangeDomain(domain)
            },
            onOpenRecord = { record ->
                selectedChangeDomain = null
                openHomeChangeRecord(
                    record = record,
                    homework = homework,
                    exams = exams,
                    phyVlabEvents = phyVlabEvents,
                    courses = homeSchedule.courses + homeSchedule.supplementalCourses,
                    onOpenHomeworkDetail = onOpenHomeworkDetail,
                    onOpenExams = onOpenExams,
                    onOpenPhyVlab = onOpenPhyVlab,
                    onOpenCourse = { selectedChangeCourse = it },
                    onOpenChangeDomain = onOpenChangeDomain,
                )
            },
        )
    }
    selectedChangeCourse?.let { course ->
        AppleSheet(onDismissRequest = { selectedChangeCourse = null }, title = "课程详情", scrollableBody = true) {
            val detailScroll = rememberScrollState()
            CourseDetailContent(
                course,
                homeSchedule.staggeredClassTimes,
                Modifier.fillMaxWidth().verticalScroll(detailScroll).desktopTouchScroll(detailScroll)
                    .padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 20.dp),
            )
        }
    }

    // 作业模型每发一次状态都会重取秒级 now；日程只需要分钟精度。截到分钟后，同步期间
    // now 不再每次都变，各周日程的 remember 才不会在每次重组时整周重算。
    val agendaNow = remember(now.date, now.hour, now.minute) {
        LocalDateTime(now.date, LocalTime(now.hour, now.minute))
    }
    val status = state.status
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        TopScrollLazyColumn(
            state = pageListState,
            modifier = Modifier.fillMaxSize().desktopTouchScroll(pageListState),
            contentPadding = PaddingValues(
                start = if (expanded) 8.dp else 16.dp,
                end = if (expanded) 8.dp else 16.dp,
                // 原生栏 underlap 时首项靠这份顶边距让开，视口本身画到屏幕顶。
                top = 14.dp + LocalTopBarClearance.current,
                // 视口延伸到胶囊下方，滚到末尾时内容通过内部尾部净空让开胶囊。
                bottom = 14.dp + LocalBottomBarClearance.current,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.failure?.let { failure ->
                item(key = "home-failure") {
                    Text(
                        failure.message(state.status != null),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item(key = "home-now") {
                HomeNowSection(timeZone = timeZone)
            }
            if (expanded) {
                item(key = "home-agenda") {
                    HomeAgendaSection(
                        todayRequest = todayRequest,
                        onTodaySelectedChanged = onTodaySelectedChanged,
                        platform = platform,
                        homework = homework,
                        exams = exams,
                        phyVlabEvents = phyVlabEvents,
                        currentWeek = currentWeek,
                        academicWeeks = academicWeeks,
                        now = agendaNow,
                        timeZone = timeZone,
                        isLoading = isAgendaLoading,
                        expanded = expanded,
                        isWeekResolved = isWeekResolved,
                        onOpenHomework = onOpenHomework,
                        onOpenHomeworkDetail = onOpenHomeworkDetail,
                        onOpenExams = onOpenExams,
                        onOpenPhyVlab = onOpenPhyVlab,
                    )
                }
                item(key = "home-status-cards") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MailCard(status, mailboxUnread, onOpenMailbox, Modifier.weight(1f))
                    }
                }
            } else {
                // 紧凑页：本周日程放第一栏，新邮件保持原尺寸。
                item(key = "home-agenda") {
                    HomeAgendaSection(
                        todayRequest = todayRequest,
                        onTodaySelectedChanged = onTodaySelectedChanged,
                        platform = platform,
                        homework = homework,
                        exams = exams,
                        phyVlabEvents = phyVlabEvents,
                        currentWeek = currentWeek,
                        academicWeeks = academicWeeks,
                        now = agendaNow,
                        timeZone = timeZone,
                        isLoading = isAgendaLoading,
                        expanded = expanded,
                        isWeekResolved = isWeekResolved,
                        onOpenHomework = onOpenHomework,
                        onOpenHomeworkDetail = onOpenHomeworkDetail,
                        onOpenExams = onOpenExams,
                        onOpenPhyVlab = onOpenPhyVlab,
                    )
                }
                item(key = "home-mail-card") {
                    MailCard(status, mailboxUnread, onOpenMailbox, Modifier.fillMaxWidth())
                }
            }
            item(key = "home-change-feed") {
                HomeChangeFeedSection(
                    changes = changes,
                    onSelectDomain = { selectedChangeDomain = it },
                    onClearAll = onClearAllChanges,
                )
            }
        }
    }
}

private fun mondayOf(date: LocalDate): LocalDate =
    date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

/**
 * 首页自动跟随的目标周：用户手动选周后永不覆盖（异步结果不得把用户正在看的周顶掉）；
 * 未确认时不跟随；其它情况下只要目标与当前不同就跟随。
 *
 * 比较必须用整个槽（含周号）而不仅是开始日期：启动时无校历的回退槽与校历确认后的
 * 真实槽可能落在同一天（都是本周一），只比日期会把错误的第 1 周永远留在台面上。
 */
internal fun nextAutoFollowedWeekSlot(
    selectedSlot: AcademicWeekSlot,
    automaticSlot: AcademicWeekSlot?,
    weekWasManuallySelected: Boolean,
): AcademicWeekSlot? {
    if (weekWasManuallySelected) return null
    if (automaticSlot == null) return null
    return if (automaticSlot != selectedSlot) automaticSlot else null
}

/** 首页与课表共用校历时间轴；没有校历时保留旧的 1..30 周兜底。 */
private fun homeAgendaWeekSlots(
    currentWeek: Int,
    today: LocalDate,
    academicWeeks: List<OccupancyWeekDate>,
): List<AcademicWeekSlot> {
    val calendarSlots = academicWeekSlots(academicWeeks, HOME_MAX_TEACHING_WEEK)
    if (calendarSlots.isEmpty()) {
        return (1..HOME_MAX_TEACHING_WEEK).map { week ->
            AcademicWeekSlot(
                teachingWeek = week,
                startDate = resolveHomeAgendaWeekStart(currentWeek, week, today, academicWeeks),
            )
        }
    }
    val todayMonday = mondayOf(today)
    return if (calendarSlots.any { it.startDate == todayMonday }) {
        calendarSlots
    } else {
        // 当前日期可能落在校历范围之外；保留这一个自然周，避免首页变成
        // “没有周数”而丢掉当天的作业开始/截止事件。
        (calendarSlots + AcademicWeekSlot(null, todayMonday)).sortedBy(AcademicWeekSlot::startDate)
    }
}

@Composable
private fun MailCard(
    status: HomeStatus?,
    mailboxUnread: MailboxUnreadSummary?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    // 优先用邮箱实际未读数（Coremail 列表）；未探测到时回退 MIS 聚合值。
    val value = mailboxUnread?.let { if (it.capped) "${it.count}+" else "${it.count}" }
        ?: status?.newMailCount
        ?: "—"
    val hasUnread = mailboxUnread?.let { it.count > 0 } ?: (status?.hasNewMail == true)
    StatusCard(
        title = "新邮件",
        value = value,
        detail = if (hasUnread) "有新邮件，记得查看" else "当前 BJTU 邮箱状态",
        action = "查看邮箱",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    detail: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    HomeCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // 半宽卡片里按钮默认的水平内边距会挤压中文标签导致换行，收紧一些。
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text(action, maxLines = 1) }
        }
    }
}

@Composable
private fun HomeAgendaSection(
    todayRequest: Int,
    onTodaySelectedChanged: (Boolean) -> Unit,
    platform: PlatformInfo,
    homework: List<Homework>,
    exams: List<ExamSchedule>,
    phyVlabEvents: List<PhyVlabEvent>,
    currentWeek: Int,
    academicWeeks: List<OccupancyWeekDate>,
    now: LocalDateTime,
    timeZone: TimeZone,
    isLoading: Boolean,
    expanded: Boolean,
    /** 周数是否已由本学期校历确认；未确认时首页显示「日程加载中」。 */
    isWeekResolved: Boolean = true,
    onOpenHomework: () -> Unit,
    onOpenHomeworkDetail: (Homework) -> Unit,
    onOpenExams: (ExamSchedule) -> Unit,
    onOpenPhyVlab: (PhyVlabEvent) -> Unit,
) {
    val homeSchedule = LocalHomeSchedule.current
    val today = now.date
    // 校历确认之前不显示任何周数（含缓存值），统一显示「日程加载中」，
    // 拿到确切结果后一次性显示最终周；这样卡片不会随中间值反复弹跳。
    val weekValueIsPending = !isWeekResolved
    val dueSoonHomework = remember(homework, exams, phyVlabEvents, today, now, timeZone) {
        buildHomeAgenda(homework, exams, today, now, timeZone, phyVlabEvents)
            .dueSoonHomework
    }
    val weekSlots = remember(currentWeek, academicWeeks, today) {
        homeAgendaWeekSlots(currentWeek, today, academicWeeks)
    }
    val todayMonday = remember(today) { mondayOf(today) }
    val initialSlot = remember(currentWeek, academicWeeks, today) {
        when {
            currentWeek in 1..HOME_MAX_TEACHING_WEEK ->
                weekSlots.firstOrNull { it.teachingWeek == currentWeek }
                    ?: AcademicWeekSlot(currentWeek, resolveHomeAgendaWeekStart(currentWeek, currentWeek, today, academicWeeks))
            else -> weekSlots.firstOrNull { it.startDate == todayMonday }
                ?: AcademicWeekSlot(null, todayMonday)
        }
    }
    // Keep the first visible week stable while the calendar is still being
    // resolved. Otherwise a changing pager list can reuse the same page index
    // for a different week and look like an unwanted swipe on launch.
    val startupSlot = remember { initialSlot }
    var selectedSlot by remember { mutableStateOf(startupSlot) }
    var weekWasManuallySelected by remember { mutableStateOf(false) }
    val weekScrollAccumulator = remember { CourseWeekScrollAccumulator() }
    val useFingerWeekPager = platform.family == PlatformFamily.Android ||
        platform.family == PlatformFamily.IOS
    val selectedDates = remember { mutableStateMapOf<LocalDate, LocalDate>() }
    val selectedDateFor: (AcademicWeekSlot) -> LocalDate = { slot ->
        val weekStartDate = slot.startDate
        selectedDates[slot.startDate]?.takeIf { date ->
            date >= weekStartDate && date <= weekStartDate.plus(6, DateTimeUnit.DAY)
        } ?: if (today >= weekStartDate && today <= weekStartDate.plus(6, DateTimeUnit.DAY)) {
            today
        } else {
            weekStartDate
        }
    }
    val selectWeekFromUser: (AcademicWeekSlot) -> Unit = { slot ->
        weekWasManuallySelected = true
        selectedSlot = slot
    }
    LaunchedEffect(todayRequest) {
        if (todayRequest > 0) {
            val slot = weekSlots.firstOrNull { it.startDate == todayMonday }
                ?: AcademicWeekSlot(null, todayMonday)
            selectedDates[slot.startDate] = today
            selectedSlot = slot
            weekWasManuallySelected = true
        }
    }
    val adjacentWeekFor: (AcademicWeekSlot, Int) -> AcademicWeekSlot? = { slot, offset ->
        val index = weekSlots.indexOfFirst { it.startDate == slot.startDate }
        weekSlots.getOrNull(index + offset)
    }

    // 登录后/校历刷新可能先给出缓存周，再给出校历校准周；只有用户没有手动选周时，
    // 才让首页自动跟随这个更新，避免把用户正在看的周强行跳回第 1 周。
    // 周数尚未由校历确认时不自动跟随：否则会先跳到中间值、再跳到最终值。
    val automaticSlot = when {
        !isWeekResolved -> null
        currentWeek in 1..HOME_MAX_TEACHING_WEEK ->
            weekSlots.firstOrNull { it.teachingWeek == currentWeek }
        else -> weekSlots.firstOrNull { it.startDate == todayMonday }
    }
    LaunchedEffect(currentWeek, academicWeeks, isWeekResolved) {
        nextAutoFollowedWeekSlot(selectedSlot, automaticSlot, weekWasManuallySelected)?.let { slot ->
            selectedSlot = slot
        }
    }

    if (dueSoonHomework.isNotEmpty()) {
        HomeCard(modifier = Modifier.fillMaxWidth()) {
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DueSoonHomeworkSummary(dueSoonHomework, Modifier.weight(1f))
                    OutlinedButton(onClick = onOpenHomework) { Text("查看作业") }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DueSoonHomeworkSummary(dueSoonHomework)
                    OutlinedButton(onClick = onOpenHomework, modifier = Modifier.fillMaxWidth()) {
                        Text("查看作业")
                    }
                }
            }
        }
    }

    if (useFingerWeekPager && !isWeekResolved) {
        // 校历还在变时不用按页码记住位置的周页：页码会留在旧下标，
        // 后面的周列表一换，启动时就会看起来像被滑到第 1 周。
        // 点选日期仍使用下面的内容过渡；周数确认后再换成手指横滑页。
        val pendingSlot = if (todayRequest > 0) selectedSlot else startupSlot
        SideEffect {
            onTodaySelectedChanged(
                pendingSlot.startDate == todayMonday && selectedDateFor(pendingSlot) == today,
            )
        }
        HomeAgendaWeekCard(
            homework = homework,
            exams = exams,
            phyVlabEvents = phyVlabEvents,
            weekSlot = pendingSlot,
            selectedDate = selectedDateFor(pendingSlot),
            now = now,
            timeZone = timeZone,
            isLoading = isLoading,
            isWeekResolved = isWeekResolved,
            isWeekPending = weekValueIsPending,
            showWeekButtons = false,
            onOpenHomework = onOpenHomeworkDetail,
            onOpenExams = onOpenExams,
            onOpenPhyVlab = onOpenPhyVlab,
            previousWeek = null,
            nextWeek = null,
            onSelectWeek = selectWeekFromUser,
            onSelectDate = { date -> selectedDates[pendingSlot.startDate] = date },
            isDateCurrent = { date -> selectedDateFor(pendingSlot) == date },
            modifier = Modifier.fillMaxWidth(),
        )
    } else if (useFingerWeekPager) {
        // The pager follows the school-calendar timeline, so an internal holiday
        // is a real page between its surrounding teaching weeks.
        val pagerWeeks = weekSlots
        val pageForWeek: (AcademicWeekSlot) -> Int = { slot ->
            pagerWeeks.indexOfFirst { it.startDate == slot.startDate }.coerceAtLeast(0)
        }
        val weekForPage: (Int) -> AcademicWeekSlot = { page ->
            pagerWeeks[page.coerceIn(pagerWeeks.indices)]
        }
        val pagerTargetSlot = if (!weekWasManuallySelected) {
            automaticSlot ?: selectedSlot
        } else {
            selectedSlot
        }
        val pagerTargetPage = pageForWeek(pagerTargetSlot)
        val pagerState = rememberPagerState(initialPage = pagerTargetPage) {
            pagerWeeks.size
        }
        var pagerProgrammaticTargetPage by remember { mutableStateOf<Int?>(null) }
        val latestPagerWeeks by rememberUpdatedState(pagerWeeks)
        val latestSelectedSlot by rememberUpdatedState(selectedSlot)
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .collect { page ->
                    val programmaticTarget = pagerProgrammaticTargetPage
                    if (programmaticTarget != null) {
                        if (page == programmaticTarget) {
                            pagerProgrammaticTargetPage = null
                        }
                        return@collect
                    }
                    val weeks = latestPagerWeeks
                    if (weeks.isEmpty()) return@collect
                    val slot = weeks[page.coerceIn(weeks.indices)]
                    if (latestSelectedSlot.startDate != slot.startDate) {
                        weekWasManuallySelected = true
                        selectedSlot = slot
                    }
                }
        }
        LaunchedEffect(pagerTargetPage, pagerWeeks) {
            if (pagerTargetPage != pagerState.currentPage && !pagerState.isScrollInProgress) {
                // This scroll is caused by an arrow click, a current-week refresh,
                // or a changed holiday/week mapping; it must not be interpreted as
                // a new manual swipe by the settled-page observer above.
                pagerProgrammaticTargetPage = pagerTargetPage
                pagerState.scrollToPage(pagerTargetPage)
                if (pagerState.settledPage == pagerTargetPage) {
                    pagerProgrammaticTargetPage = null
                }
            }
        }
        // The pager contains only the week header and the seven-day calendar.
        // Its height is therefore stable while a horizontal gesture is in
        // progress; the selected-day agenda below is the only part allowed to
        // change height after the new week settles.
        var calendarHeightPx by remember(pagerWeeks) { mutableStateOf(0) }
        val density = LocalDensity.current
        val calendarHeightModifier = calendarHeightPx.takeIf { it > 0 }?.let { heightPx ->
            Modifier.height(with(density) { heightPx.toDp() })
        } ?: Modifier
        val settledPage = pagerState.settledPage.coerceIn(pagerWeeks.indices)
        val settledSlot = weekForPage(settledPage)
        val settledDate = selectedDateFor(settledSlot)
        SideEffect {
            onTodaySelectedChanged(settledSlot.startDate == todayMonday && settledDate == today)
        }
        val scheduleSwipeThresholdPx = with(density) { 56.dp.toPx() }
        var scheduleSwipeTargetPage by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(scheduleSwipeTargetPage) {
            val targetPage = scheduleSwipeTargetPage ?: return@LaunchedEffect
            if (
                targetPage in pagerWeeks.indices &&
                !pagerState.isScrollInProgress &&
                targetPage != pagerState.settledPage
            ) {
                pagerState.animateScrollToPage(targetPage)
            }
            scheduleSwipeTargetPage = null
        }

        HomeCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().then(calendarHeightModifier),
                    beyondViewportPageCount = 0,
                    pageSpacing = 12.dp,
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    val weekSlot = weekForPage(page)
                    val weekStartDate = weekSlot.startDate
                    val weekAgenda = remember(
                        homework,
                        exams,
                        phyVlabEvents,
                        today,
                        now,
                        timeZone,
                        weekStartDate,
                        homeSchedule,
                    ) {
                        buildHomeAgenda(
                            homework = homework,
                            exams = exams,
                            today = today,
                            now = now,
                            timeZone = timeZone,
                            phyVlabEvents = phyVlabEvents,
                            weekStartDate = weekStartDate,
                        ).withCourses(homeSchedule)
                    }
                    HomeAgendaCalendarContent(
                        weekSlot = weekSlot,
                        weekAgenda = weekAgenda,
                        today = today,
                        homework = homework,
                        exams = exams,
                        phyVlabEvents = phyVlabEvents,
                        isLoading = isLoading,
                        isWeekPending = weekValueIsPending,
                        showWeekButtons = false,
                        previousWeek = adjacentWeekFor(weekSlot, -1),
                        nextWeek = adjacentWeekFor(weekSlot, 1),
                        onSelectWeek = selectWeekFromUser,
                        selectedDate = selectedDateFor(weekSlot),
                        // A horizontal pager drag can end over a day cell. Do
                        // not turn that release point into a date click while
                        // the pager is still settling.
                        onSelectDate = { date ->
                            if (
                                !pagerState.isScrollInProgress &&
                                    pagerState.currentPage == pagerState.settledPage
                            ) {
                                selectedDates[weekSlot.startDate] = date
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                if (size.height > calendarHeightPx) {
                                    calendarHeightPx = size.height
                                }
                            },
                    )
                }
                val dayTransition = updateTransition(
                    targetState = settledSlot to settledDate,
                    label = "home-agenda-selected-day",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Keep the old affordance that a swipe can start in
                        // the details area as well as on the calendar. The
                        // pager still owns the calendar gesture; this handler
                        // only runs below it and never changes layout size.
                        .pointerInput(pagerState, pagerWeeks.size, scheduleSwipeThresholdPx) {
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDrag += dragAmount
                                },
                                onDragEnd = {
                                    val page = pagerState.settledPage
                                    val targetPage = when {
                                        totalDrag <= -scheduleSwipeThresholdPx -> page + 1
                                        totalDrag >= scheduleSwipeThresholdPx -> page - 1
                                        else -> page
                                    }
                                    if (targetPage in pagerWeeks.indices) {
                                        scheduleSwipeTargetPage = targetPage
                                    }
                                },
                                onDragCancel = { totalDrag = 0f },
                            )
                        },
                ) {
                    dayTransition.AnimatedContent(
                        transitionSpec = {
                            val direction = if (targetState.first.startDate >= initialState.first.startDate) 1 else -1
                            (
                                slideInVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = 420f,
                                    ),
                                ) { height -> direction * height / 3 } +
                                    fadeIn(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = 420f,
                                        ),
                                    )
                                ) togetherWith (
                                slideOutVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = 420f,
                                    ),
                                ) { height -> -direction * height / 3 } +
                                    fadeOut(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = 420f,
                                        ),
                                    )
                                ) using SizeTransform(
                                    clip = false,
                                    sizeAnimationSpec = { _, _ ->
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = 320f,
                                        )
                                    },
                                )
                        },
                    ) { (weekSlot, date) ->
                        val weekStartDate = weekSlot.startDate
                        val weekAgenda = remember(
                            homework,
                            exams,
                            phyVlabEvents,
                            today,
                            now,
                            timeZone,
                            weekStartDate,
                            homeSchedule,
                        ) {
                            buildHomeAgenda(
                                homework = homework,
                                exams = exams,
                                today = today,
                                now = now,
                                timeZone = timeZone,
                                phyVlabEvents = phyVlabEvents,
                                weekStartDate = weekStartDate,
                            ).withCourses(homeSchedule)
                        }
                        val selectedDay = weekAgenda.days.firstOrNull { it.date == date }
                            ?: weekAgenda.days.first()
                        AgendaSelectedDayContent(
                            day = selectedDay,
                            onOpenHomework = onOpenHomeworkDetail,
                            onOpenExams = onOpenExams,
                            onOpenPhyVlab = onOpenPhyVlab,
                            canNavigate = {
                                !dayTransition.isRunning &&
                                    dayTransition.currentState == dayTransition.targetState &&
                                    dayTransition.targetState == (weekSlot to date) &&
                                    selectedDateFor(weekSlot) == date &&
                                    selectedSlot == weekSlot &&
                                    !pagerState.isScrollInProgress &&
                                    scheduleSwipeTargetPage == null
                            },
                        )
                    }
                }
            }
        }
    } else {
        val weekStartDate = selectedSlot.startDate
        SideEffect {
            onTodaySelectedChanged(
                selectedSlot.startDate == todayMonday && selectedDateFor(selectedSlot) == today,
            )
        }
        HomeAgendaWeekCard(
            homework = homework,
            exams = exams,
            phyVlabEvents = phyVlabEvents,
            weekSlot = selectedSlot,
            selectedDate = selectedDateFor(selectedSlot),
            now = now,
            timeZone = timeZone,
            isLoading = isLoading,
            isWeekResolved = isWeekResolved,
            isWeekPending = weekValueIsPending,
            showWeekButtons = !useFingerWeekPager,
            onOpenHomework = onOpenHomeworkDetail,
            onOpenExams = onOpenExams,
            onOpenPhyVlab = onOpenPhyVlab,
            previousWeek = adjacentWeekFor(selectedSlot, -1),
            nextWeek = adjacentWeekFor(selectedSlot, 1),
            onSelectWeek = selectWeekFromUser,
            onSelectDate = { date -> selectedDates[selectedSlot.startDate] = date },
            isDateCurrent = { date ->
                selectedSlot.startDate == weekStartDate && selectedDateFor(selectedSlot) == date
            },
            modifier = Modifier
                .fillMaxWidth()
                .courseWeekScrollNavigation(weekScrollAccumulator) { direction ->
                    when (direction) {
                        CourseWeekScrollDirection.PREVIOUS -> adjacentWeekFor(selectedSlot, -1)?.let(selectWeekFromUser)
                        CourseWeekScrollDirection.NEXT -> adjacentWeekFor(selectedSlot, 1)?.let(selectWeekFromUser)
                    }
                },
        )
    }
}

@Composable
private fun HomeAgendaWeekCard(
    homework: List<Homework>,
    exams: List<ExamSchedule>,
    phyVlabEvents: List<PhyVlabEvent>,
    weekSlot: AcademicWeekSlot,
    selectedDate: LocalDate,
    now: LocalDateTime,
    timeZone: TimeZone,
    isLoading: Boolean,
    /** 周数是否已由本学期校历确认；未确认时明确显示「日程加载中」。 */
    isWeekResolved: Boolean = true,
    /** 周数还没着落（未确认且无缓存值）：标签显示「日程加载中」。 */
    isWeekPending: Boolean = false,
    /**
     * 是否显示「上一周 / 下一周」按钮。
     * 移动端只保留手指横滑（与课表一致），按钮只留给宽屏/桌面。
     */
    showWeekButtons: Boolean = true,
    onOpenHomework: (Homework) -> Unit,
    onOpenExams: (ExamSchedule) -> Unit,
    onOpenPhyVlab: (PhyVlabEvent) -> Unit,
    previousWeek: AcademicWeekSlot?,
    nextWeek: AcademicWeekSlot?,
    onSelectWeek: (AcademicWeekSlot) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    isDateCurrent: (LocalDate) -> Boolean,
    modifier: Modifier,
) {
    val homeSchedule = LocalHomeSchedule.current
    val today = now.date
    val weekStartDate = weekSlot.startDate
    val weekAgenda = remember(homework, exams, phyVlabEvents, today, now, timeZone, weekStartDate, homeSchedule) {
        buildHomeAgenda(
            homework = homework,
            exams = exams,
            today = today,
            now = now,
            timeZone = timeZone,
            phyVlabEvents = phyVlabEvents,
            weekStartDate = weekStartDate,
        ).withCourses(homeSchedule)
    }
    val selectedDay = weekAgenda.days.firstOrNull { it.date == selectedDate } ?: weekAgenda.days.first()

    val weekIsPending = isWeekPending || !isWeekResolved
    HomeCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeAgendaCalendarContent(
                weekSlot = weekSlot,
                weekAgenda = weekAgenda,
                today = today,
                homework = homework,
                exams = exams,
                phyVlabEvents = phyVlabEvents,
                isLoading = isLoading,
                isWeekPending = weekIsPending,
                showWeekButtons = showWeekButtons,
                previousWeek = previousWeek,
                nextWeek = nextWeek,
                onSelectWeek = onSelectWeek,
                selectedDate = selectedDay.date,
                onSelectDate = onSelectDate,
                modifier = Modifier.fillMaxWidth(),
            )
            val dayTransition = updateTransition(selectedDay.date, label = "home-agenda-day")
            dayTransition.AnimatedContent(
                transitionSpec = {
                    val direction = if (targetState >= initialState) 1 else -1
                    (
                        slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 420f,
                            ),
                        ) { width -> direction * width / 3 } +
                            fadeIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 420f,
                                ),
                            )
                        ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 420f,
                            ),
                        ) { width -> -direction * width / 3 } +
                            fadeOut(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 420f,
                                ),
                            )
                        ) using SizeTransform(
                            clip = false,
                            sizeAnimationSpec = { _, _ ->
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 320f,
                                )
                            },
                        )
                },
            ) { date ->
                val day = weekAgenda.days.firstOrNull { it.date == date } ?: selectedDay
                AgendaSelectedDayContent(
                    day, onOpenHomework, onOpenExams, onOpenPhyVlab,
                    canNavigate = {
                        !dayTransition.isRunning &&
                            dayTransition.currentState == dayTransition.targetState &&
                            dayTransition.targetState == date && isDateCurrent(date)
                    },
                )
            }
        }
    }
}

@Composable
internal fun HomeAgendaCalendarContent(
    weekSlot: AcademicWeekSlot,
    weekAgenda: HomeAgenda,
    today: LocalDate,
    homework: List<Homework>,
    exams: List<ExamSchedule>,
    phyVlabEvents: List<PhyVlabEvent>,
    isLoading: Boolean,
    isWeekPending: Boolean,
    showWeekButtons: Boolean,
    previousWeek: AcademicWeekSlot?,
    nextWeek: AcademicWeekSlot?,
    onSelectWeek: (AcademicWeekSlot) -> Unit,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        isWeekPending -> "日程加载中"
                        weekSlot.isNonTeachingWeek -> "非教学周"
                        else -> "第 ${weekSlot.teachingWeek} 教学周"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!isWeekPending) {
                    Text(
                        text = when {
                            weekSlot.isNonTeachingWeek -> "校历未安排教学周，仍显示本周日程"
                            else -> "作业、考试与课表安排"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!isWeekPending) {
                Column(horizontalAlignment = Alignment.End) {
                    if (showWeekButtons) {
                        HomeWeekNavigationControls(
                            previousWeek = previousWeek,
                            nextWeek = nextWeek,
                            onPrevious = { previousWeek?.let(onSelectWeek) },
                            onNext = { nextWeek?.let(onSelectWeek) },
                        )
                    } else {
                        Text(
                            "左右滑动切周",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isLoading && homework.isEmpty() && exams.isEmpty() && phyVlabEvents.isEmpty()) {
                        Text("同步中", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            weekAgenda.days.forEach { day ->
                AgendaDayCell(
                    day = day,
                    selected = day.date == selectedDate,
                    isToday = day.date == today,
                    isLoading = isLoading || isWeekPending,
                    onClick = { onSelectDate(day.date) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            buildList {
                add("课程" to courseAgendaMarkColor())
                add("作业开始" to startingHomeworkMarkColor())
                add("待提交作业" to pendingHomeworkMarkColor())
                add("已提交作业" to submittedHomeworkMarkColor())
                if (weekAgenda.days.any { it.physicsLabCourses.isNotEmpty() }) {
                    add("实验" to labAgendaMarkColor())
                }
                if (weekAgenda.days.any { it.exams.isNotEmpty() }) {
                    add("考试" to examAgendaMarkColor())
                }
            }.forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Canvas(Modifier.size(5.dp)) { drawCircle(color) }
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AgendaSelectedDayContent(
    day: HomeAgendaDay,
    onOpenHomework: (Homework) -> Unit,
    onOpenExams: (ExamSchedule) -> Unit,
    onOpenPhyVlab: (PhyVlabEvent) -> Unit,
    canNavigate: () -> Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "${shortDate(day.date)} · ${weekdayName(day.date)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        AgendaDayDetails(day, onOpenHomework, onOpenExams, onOpenPhyVlab, canNavigate)
    }
}

@Composable
private fun HomeWeekNavigationControls(
    previousWeek: AcademicWeekSlot?,
    nextWeek: AcademicWeekSlot?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeWeekNavigationButton(
            label = "‹",
            contentDescription = previousWeek?.description() ?: "没有更早的教学周",
            enabled = previousWeek != null,
            onClick = onPrevious,
        )
        HomeWeekNavigationButton(
            label = "›",
            contentDescription = nextWeek?.description() ?: "没有更晚的教学周",
            enabled = nextWeek != null,
            onClick = onNext,
        )
    }
}

private fun AcademicWeekSlot.description(): String = teachingWeek?.let { "切换到第${it}教学周" }
    ?: "切换到非教学周"

@Composable
private fun HomeWeekNavigationButton(
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
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DueSoonHomeworkSummary(
    homework: List<Homework>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "48 小时内有 ${homework.size} 项作业截止",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
        )
        homework.take(3).forEach { item ->
            Text(
                "${displayScheduleCourseName(item.courseName)} · ${item.title} · ${formatSchoolDeadline(item.endTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (homework.size > 3) {
            Text(
                "另有 ${homework.size - 3} 项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 当天截止项全部已提交时使用固定浅绿底。 */
private val homeworkDayContainerColor = Color(0xFFDFF1DE)

@Composable
private fun courseAgendaMarkColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF589DE0) else Color(0xFF286EB8)

/** 紫色实验标记与课程蓝、作业红绿和考试橙保持清楚区分。 */
@Composable
private fun labAgendaMarkColor(): Color =
    team.bjtuss.bjtuselfservice.shared.feature.course.courseCategoryColors(
        team.bjtuss.bjtuselfservice.shared.feature.course.CourseColorCategory.LAB,
    ).onContainer

@Composable
private fun examAgendaMarkColor(): Color =
    team.bjtuss.bjtuselfservice.shared.feature.course.courseCategoryColors(
        team.bjtuss.bjtuselfservice.shared.feature.course.CourseColorCategory.EXAM,
    ).onContainer

/** 待提交作业用红色，与已提交的深绿区分。 */
@Composable
private fun pendingHomeworkMarkColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFE56B72) else Color(0xFFC53946)

@Composable
private fun submittedHomeworkMarkColor(): Color = Color(0xFF16723B)

@Composable
private fun startingHomeworkMarkColor(): Color = Color(0xFFD5A000)

@Composable
private fun AgendaDayCell(
    day: HomeAgendaDay,
    selected: Boolean,
    isToday: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val hasDeadline = day.homeworkDue.isNotEmpty() || day.phyVlabEvents.any {
        it.kind == PhyVlabEventKind.DEADLINE
    }
    val allSubmitted = isHomeAgendaDayFullySubmitted(day)
    val cellColor = when {
        allSubmitted -> homeworkDayContainerColor
        hasDeadline -> MaterialTheme.colorScheme.errorContainer
        selected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val cellContentColor = when {
        allSubmitted -> Color(0xFF244B30)
        hasDeadline -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = cellColor,
        contentColor = cellContentColor,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(weekdayShortName(day.date), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(day.date.day.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                color = cellContentColor.copy(alpha = 0.18f),
                thickness = 0.5.dp,
            )
            val marks = agendaWeekMarks(day)
            val eventMarks = listOfNotNull(
                marks.courses.takeIf { it > 0 }?.let {
                    AgendaCategoryCount("课程", it, courseAgendaMarkColor())
                },
                marks.labs.takeIf { it > 0 }?.let {
                    AgendaCategoryCount("实验", it, labAgendaMarkColor())
                },
                marks.exams.takeIf { it > 0 }?.let {
                    AgendaCategoryCount("考试", it, examAgendaMarkColor())
                },
            )
            val homeworkMarks = listOfNotNull(
                marks.startingHomework.takeIf { it > 0 }?.let { AgendaCategoryCount("作业开始", it, startingHomeworkMarkColor()) },
                marks.pendingHomework.takeIf { it > 0 }?.let {
                    AgendaCategoryCount("待提交作业", it, pendingHomeworkMarkColor())
                },
                marks.submittedHomework.takeIf { it > 0 }?.let {
                    AgendaCategoryCount("已提交作业", it, submittedHomeworkMarkColor())
                },
            )
            // 固定预留两行；只有一行时整行居中，加载占位也在同一中心。
            Box(
                modifier = Modifier.fillMaxWidth().height(agendaMarkRowHeight * 2),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading && eventMarks.isEmpty() && homeworkMarks.isEmpty()) {
                    Text("—", style = MaterialTheme.typography.labelSmall)
                } else if (day.eventCount == 0) {
                    Text("—", style = MaterialTheme.typography.labelSmall)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (eventMarks.isNotEmpty()) AgendaMarkRow(eventMarks)
                        if (homeworkMarks.isNotEmpty()) AgendaMarkRow(homeworkMarks)
                    }
                }
            }
        }
    }
}

private data class AgendaCategoryCount(val label: String, val count: Int, val color: Color)

/** 一行小点的固定高度。两行叠起来，有没有作业都一样高。 */
private val agendaMarkRowHeight = 13.dp

@Composable
private fun AgendaMarkRow(items: List<AgendaCategoryCount>) {
    Box(
        modifier = Modifier.fillMaxWidth().height(agendaMarkRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (items.isNotEmpty()) Row(
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (label, count, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.semantics { contentDescription = "$label $count" },
                ) {
                    Canvas(Modifier.size(4.dp)) { drawCircle(color) }
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgendaCourseIcon(tint: Color) {
    Canvas(Modifier.size(24.dp).semantics { contentDescription = "课程" }) {
        val scale = size.width / 24f
        val outline = Path().apply {
            moveTo(12f * scale, 5f * scale)
            cubicTo(9f * scale, 3f * scale, 5f * scale, 3f * scale, 2f * scale, 4f * scale)
            lineTo(2f * scale, 20f * scale)
            cubicTo(5f * scale, 19f * scale, 9f * scale, 19f * scale, 12f * scale, 21f * scale)
            cubicTo(15f * scale, 19f * scale, 19f * scale, 19f * scale, 22f * scale, 20f * scale)
            lineTo(22f * scale, 4f * scale)
            cubicTo(19f * scale, 3f * scale, 15f * scale, 3f * scale, 12f * scale, 5f * scale)
            close()
        }
        drawPath(outline, tint, style = Stroke(1.8f * scale))
        drawLine(tint, Offset(12f * scale, 5f * scale), Offset(12f * scale, 21f * scale), 1.8f * scale)
        for (y in listOf(8f, 11f, 14f)) {
            drawLine(tint, Offset(15f * scale, y * scale), Offset(20f * scale, (y - 1f) * scale), scale)
        }
    }
}

@Composable
private fun AgendaDayDetails(
    day: HomeAgendaDay,
    onOpenHomework: (Homework) -> Unit,
    onOpenExams: (ExamSchedule) -> Unit,
    onOpenPhyVlab: (PhyVlabEvent) -> Unit,
    canNavigate: () -> Boolean,
) {
    var selectedCourse by remember(day.date) { mutableStateOf<Course?>(null) }
    val staggered = LocalHomeSchedule.current.staggeredClassTimes
    if (day.eventCount == 0) {
        Text("当天没有课程、作业、考试或物理在线安排。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        day.homeworkStarting.forEach { item ->
            AgendaEventRow("开始", item.title, displayScheduleCourseName(item.courseName), { onOpenHomework(item) }, canNavigate)
        }
        day.homeworkDue.forEach { item ->
            AgendaEventRow(
                type = "截止",
                title = item.title,
                detail = "${displayScheduleCourseName(item.courseName)} · ${formatSchoolDeadline(item.endTime)}",
                onClick = { onOpenHomework(item) },
                canNavigate = canNavigate,
                done = isHomeworkSubmitted(item),
            )
        }
        day.exams.forEach { exam ->
            AgendaEventRow("考试", displayScheduleCourseName(exam.courseName), exam.examTimeAndPlace, { onOpenExams(exam) }, canNavigate)
        }
        day.phyVlabEvents.forEach { event ->
            AgendaEventRow(
                type = when {
                    event.id.startsWith("citel-discount-") -> "折扣开始"
                    event.id.startsWith("citel-start-") -> "CITEL开始"
                    event.id.startsWith("citel-") -> "CITEL截止"
                    event.kind == PhyVlabEventKind.START -> "物理开始"
                    else -> "物理截止"
                },
                title = event.title,
                detail = formatPhyVlabAgendaDate(event),
                onClick = { onOpenPhyVlab(event) },
                canNavigate = canNavigate,
                done = event.submitted,
            )
        }
        if (day.physicsLabCourses.isNotEmpty()) {
            Text("当天实验", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            day.physicsLabCourses.forEach { lab ->
                AgendaEventRow(
                    type = "实验",
                    title = lab.copy(courseName = lab.courseName.removeSuffix("（实验）")).displayTitleWithTeacher(),
                    detail = listOf(lab.scheduleEventTime.orEmpty(), team.bjtuss.bjtuselfservice.shared.domain.course.displayCoursePlace(lab.coursePlace))
                        .filter(String::isNotBlank).joinToString(" · "),
                    onClick = { selectedCourse = lab },
                    canNavigate = canNavigate,
                )
            }
        }
        if (day.courses.isNotEmpty()) {
            Text("当天课表", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            day.courses.forEach { course ->
                val time = course.classTimeRange(staggered)?.replace('-', '–').orEmpty()
                AgendaEventRow(
                    type = "课程",
                    markColor = team.bjtuss.bjtuselfservice.shared.feature.course.courseCategoryAccent(
                        team.bjtuss.bjtuselfservice.shared.feature.course.courseColorCategory(
                            course, LocalHomeSchedule.current.courseTypesByCode,
                        ),
                    ),
                    title = course.displayTitleWithTeacher(),
                    detail = listOf(time, team.bjtuss.bjtuselfservice.shared.domain.course.displayCoursePlace(course.coursePlace)).filter(String::isNotBlank).joinToString(" · "),
                    onClick = { selectedCourse = course },
                    canNavigate = canNavigate,
                )
            }
        }

    }
    selectedCourse?.let { course ->
        AppleSheet(onDismissRequest = { selectedCourse = null }, title = "课程详情", scrollableBody = true) {
            val detailScroll = rememberScrollState()
            CourseDetailContent(
                course,
                staggered,
                Modifier.fillMaxWidth().verticalScroll(detailScroll).desktopTouchScroll(detailScroll)
                    .padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun AgendaEventRow(
    type: String,
    title: String,
    detail: String,
    onClick: () -> Unit,
    canNavigate: () -> Boolean,
    done: Boolean = false,
    /** 课程行图标的类别色；为空时用分类默认色。 */
    markColor: Color? = null,
) {
    val typeColor = markColor ?: when {
        type == "课程" -> courseAgendaMarkColor()
        type == "考试" -> examAgendaMarkColor()
        type == "实验" -> labAgendaMarkColor()
        type.contains("截止") || type.endsWith("开始") ->
            if (done) submittedHomeworkMarkColor() else pendingHomeworkMarkColor()
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = { if (canNavigate()) onClick() },
        enabled = canNavigate(),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (type == "课程") {
                AgendaCourseIcon(typeColor)
            } else {
                Text(
                    type,
                    color = typeColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun weekdayShortName(date: LocalDate): String =
    listOf("一", "二", "三", "四", "五", "六", "日")[date.dayOfWeek.isoDayNumber - 1]

private fun weekdayName(date: LocalDate): String = "星期${weekdayShortName(date)}"

private fun formatPhyVlabAgendaDate(event: PhyVlabEvent): String {
    // 由作业截止生成的事件（物理在线活动、CITEL）：零点截止显示为前一天 24:00，与它所在的日程日一致。
    if (event.kind == PhyVlabEventKind.DEADLINE && (event.id.startsWith("activity-") || event.id.startsWith("citel-due-"))) {
        val shown = deadlineDisplay(Instant.fromEpochSeconds(event.dayTimestamp).toLocalDateTime(TimeZone.of("Asia/Shanghai")))
        return "${shown.date} ${shown.clock} · ${shown.date.dayOfWeek.chineseLabel()}"
    }
    if (Regex("·\\s*周[一二三四五六日]").containsMatchIn(event.dateText)) return event.dateText
    val weekday = runCatching {
        Instant.fromEpochSeconds(event.dayTimestamp)
            .toLocalDateTime(TimeZone.of("Asia/Shanghai"))
            .date
    }.getOrNull()?.let { date -> "周${weekdayShortName(date)}" }
    return if (weekday == null || event.dateText.isBlank()) {
        event.dateText
    } else {
        "${event.dateText} · $weekday"
    }
}

private fun shortDate(date: LocalDate): String = "${date.month.ordinal + 1}月${date.day}日"

@Composable
internal fun HomeChangeFeedSection(
    changes: List<HomeChangeRecord>,
    onSelectDomain: (HomeChangeDomain) -> Unit,
    onClearAll: () -> Unit,
) {
    // 过滤历史误报：原/现文案完全相同的「修改」不算变动。
    val meaningful = changes.filterNot {
        it.kind == DataChangeKind.MODIFIED && it.beforeDetail == it.afterDetail
    }
    if (meaningful.isEmpty()) return
    HomeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("数据变动", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "同步后发现 ${meaningful.size} 项变化",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClearAll) { Text("全部标记已读") }
            }
            HomeChangeDomain.entries.forEach { domain ->
                val domainChanges = meaningful.filter { it.domain == domain }
                if (domainChanges.isNotEmpty()) {
                    ChangeDomainRow(domain, domainChanges, onSelectDomain)
                }
            }
        }
    }
}

@Composable
private fun HomeChangeDialog(
    domain: HomeChangeDomain,
    changes: List<HomeChangeRecord>,
    onDismiss: () -> Unit,
    onMarkRead: () -> Unit,
    onOpenRecord: (HomeChangeRecord) -> Unit,
) {
    val isIos = currentPlatform().family == PlatformFamily.IOS
    val changeScrollState = rememberScrollState()
    val visible = changes.filterNot {
        it.kind == DataChangeKind.MODIFIED && it.beforeDetail == it.afterDetail && it.fields.isEmpty()
    }
    AppleSheet(
        onDismissRequest = onDismiss,
        title = "${domain.title}有更新",
        confirmLabel = if (isIos) "标记已读" else null,
        onConfirm = if (isIos) onMarkRead else null,
        dismissLabel = if (isIos) null else "关闭",
        needsFullHeight = false,
        scrollableBody = true,
    ) {
        if (isIos) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(changeScrollState)
                    .desktopTouchScroll(changeScrollState)
                    .padding(top = 44.dp)
                    .sheetScrollContentPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeChangeDialogCards(visible, onOpenRecord)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(changeScrollState)
                        .desktopTouchScroll(changeScrollState)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HomeChangeDialogCards(visible, onOpenRecord)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.bottomSystemBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onMarkRead) { Text("标记已读") }
                }
            }
        }
    }
}

@Composable
private fun HomeChangeDialogCards(
    visible: List<HomeChangeRecord>,
    onOpenRecord: (HomeChangeRecord) -> Unit,
) {
    if (visible.isEmpty()) {
        Text("检测到数量变化，点条目可打开详情。")
    } else {
        visible.forEach { change ->
            ChangeDetailCard(change, onClick = { onOpenRecord(change) })
        }
    }
}

private fun openHomeChangeRecord(
    record: HomeChangeRecord,
    homework: List<Homework>,
    exams: List<ExamSchedule>,
    phyVlabEvents: List<PhyVlabEvent>,
    courses: List<Course>,
    onOpenHomeworkDetail: (Homework) -> Unit,
    onOpenExams: (ExamSchedule) -> Unit,
    onOpenPhyVlab: (PhyVlabEvent) -> Unit,
    onOpenCourse: (Course) -> Unit,
    onOpenChangeDomain: (HomeChangeDomain) -> Unit,
) {
    when (record.domain) {
        HomeChangeDomain.HOMEWORK -> matchHomeworkChange(record, homework)?.let(onOpenHomeworkDetail)
        HomeChangeDomain.EXAMS -> matchExamChange(record, exams)?.let(onOpenExams)
        HomeChangeDomain.PHYVLAB ->
            phyVlabEvents.firstOrNull { it.title == record.title }?.let(onOpenPhyVlab)
        HomeChangeDomain.COURSES ->
            courses.firstOrNull { it.courseName == record.title }?.let(onOpenCourse)
        HomeChangeDomain.GRADES -> Unit
        HomeChangeDomain.CITEL, HomeChangeDomain.PHYSICS_LAB, HomeChangeDomain.PHYSICS_DEMO -> onOpenChangeDomain(record.domain)
    }
}

@Composable
private fun ChangeDomainRow(
    domain: HomeChangeDomain,
    changes: List<HomeChangeRecord>,
    onSelectDomain: (HomeChangeDomain) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = { onSelectDomain(domain) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${domain.title}：${changeCountSummary(changes)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "点开条目看详情",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = scheme.onSecondaryContainer)
        }
    }
}

@Composable
internal fun ChangeDetailCard(change: HomeChangeRecord, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChangeKindBadge(change.kind)
                Text(
                    change.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            change.displayFields().forEach { field ->
                ChangeFieldDiffRow(field, change.kind)
            }
        }
    }
}

@Composable
private fun ChangeKindBadge(kind: DataChangeKind) {
    val scheme = MaterialTheme.colorScheme
    val (label, container, content) = when (kind) {
        DataChangeKind.ADDED -> Triple("新增", scheme.primary, scheme.onPrimary)
        DataChangeKind.MODIFIED -> Triple("变更", scheme.tertiary, scheme.onTertiary)
        DataChangeKind.DELETED -> Triple("删除", scheme.error, scheme.onError)
    }
    Surface(shape = RoundedCornerShape(6.dp), color = container, contentColor = content) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChangeFieldDiffRow(field: team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeField, kind: DataChangeKind) {
    val scheme = MaterialTheme.colorScheme
    val changed = field.before != field.after
    val pair = kind == DataChangeKind.MODIFIED
    val emphasize = when (kind) {
        DataChangeKind.MODIFIED -> changed
        DataChangeKind.ADDED, DataChangeKind.DELETED -> true
    }
    val background = when {
        kind == DataChangeKind.ADDED -> scheme.primaryContainer
        kind == DataChangeKind.DELETED -> scheme.errorContainer
        emphasize -> scheme.tertiaryContainer
        else -> Color.Transparent
    }
    val valueColor = when {
        kind == DataChangeKind.ADDED -> scheme.onPrimaryContainer
        kind == DataChangeKind.DELETED -> scheme.onErrorContainer
        emphasize -> scheme.onTertiaryContainer
        else -> scheme.onSurface
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                field.label,
                modifier = Modifier.width(72.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (emphasize) valueColor else scheme.onSurfaceVariant,
            )
            Text(
                fieldValueText(field, kind, pair, changed),
                modifier = Modifier.weight(1f),
                style = if (emphasize) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (emphasize && pair) FontWeight.SemiBold else FontWeight.Normal,
                color = valueColor,
            )
        }
    }
}

private fun fieldValueText(
    field: team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeField,
    kind: DataChangeKind,
    pair: Boolean,
    changed: Boolean,
): String {
    fun shown(value: String) = value.ifBlank { "未填写" }
    return when {
        pair && changed -> "${shown(field.before)}  →  ${shown(field.after)}"
        kind == DataChangeKind.DELETED -> shown(field.before)
        else -> shown(field.after)
    }
}

private val DataChangeKind.label: String
    get() = when (this) {
        DataChangeKind.ADDED -> "新增"
        DataChangeKind.MODIFIED -> "变更"
        DataChangeKind.DELETED -> "删除"
    }

private fun changeCountSummary(changes: List<HomeChangeRecord>): String = buildList {
    DataChangeKind.entries.forEach { kind ->
        val count = changes.count { it.kind == kind }
        if (count > 0) add("${kind.label} $count")
    }
}.joinToString(" · ")

private fun HomeStatusFailure.message(hasCache: Boolean): String = when (this) {
    HomeStatusFailure.NETWORK -> if (hasCache) "网络不可用，正在显示上次状态。" else "无法连接 MIS 状态服务。"
    HomeStatusFailure.SESSION_EXPIRED -> if (hasCache) {
        "登录会话已失效，正在显示上次状态；请点击右上角刷新重试登录。"
    } else {
        "登录会话已失效，请点击右上角刷新重试登录。"
    }
    HomeStatusFailure.PARSE -> if (hasCache) "学校返回格式变化，正在显示上次状态。" else "无法读取学校返回的状态。"
    HomeStatusFailure.CACHE -> if (hasCache) "最新状态未能写入本地，仍显示上次状态。" else "无法保存最新状态。"
}

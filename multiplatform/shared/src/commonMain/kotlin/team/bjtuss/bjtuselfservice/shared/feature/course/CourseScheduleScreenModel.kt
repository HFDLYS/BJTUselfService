package team.bjtuss.bjtuselfservice.shared.feature.course

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSnapshot
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.ClassroomOccupancyRepository
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.academicWeekSlots
import team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLab
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.coursesForWeek
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder
import team.bjtuss.bjtuselfservice.shared.domain.change.recordSafely

/** 登录后自动同步：首轮 + 失败后再试 2 次（瞬时网络抖动常见）。 */
internal const val AUTO_SYNC_MAX_ATTEMPTS = 3
internal const val AUTO_SYNC_RETRY_DELAY_MILLIS = 700L
const val COURSE_MAX_WEEK = 30
internal const val COURSE_OVERVIEW_PAGE_COUNT = COURSE_MAX_WEEK + 1

/** 一张课表分页：第 0 页是全部教学周，后面按校历自然时间轴排列。 */
internal data class CourseScheduleWeekPage(
    val teachingWeek: Int? = null,
    val startDate: LocalDate? = null,
    val isOverview: Boolean = false,
) {
    val isNonTeachingWeek: Boolean
        get() = !isOverview && teachingWeek == null
}

/**
 * 课表分页不再把“教学周编号”当作连续的日历索引。
 * 校历缺失的自然周会变成独立的非教学周页；没有校历时保留旧的 1..30 兜底。
 */
internal fun courseScheduleWeekPages(state: CourseScheduleUiState): List<CourseScheduleWeekPage> {
    val calendarSlots = academicWeekSlots(state.academicWeeks, COURSE_MAX_WEEK)
    val pages = if (calendarSlots.isEmpty()) {
        (1..COURSE_MAX_WEEK).map { CourseScheduleWeekPage(teachingWeek = it) }
    } else {
        calendarSlots.map { slot ->
            CourseScheduleWeekPage(
                teachingWeek = slot.teachingWeek,
                startDate = slot.startDate,
            )
        }
    }.toMutableList()
    state.selectedNonTeachingWeekStart?.let { startDate ->
        if (pages.none { it.isNonTeachingWeek && it.startDate == startDate }) {
            pages += CourseScheduleWeekPage(startDate = startDate)
            pages.sortWith(compareBy<CourseScheduleWeekPage> { it.startDate == null }.thenBy { it.startDate })
        }
    }
    return listOf(CourseScheduleWeekPage(isOverview = true)) + pages
}

/** 概览 page 0 固定对应“全部教学周”，page 1..30 与教学周编号相同。 */
internal fun overviewPageForWeek(week: Int): Int {
    require(week in 0..COURSE_MAX_WEEK)
    return week
}

internal fun weekForOverviewPage(page: Int): Int {
    require(page in 0 until COURSE_OVERVIEW_PAGE_COUNT)
    return page
}

internal data class CourseScheduleCalendarMapping(
    val semesterLabel: String,
    val weeks: List<OccupancyWeekDate>,
)

/**
 * 返回校历中覆盖 [date] 的教学周。校历是按学期编号的，不能拿全局接口返回的
 * “第 1 周”直接覆盖一个已经处于续编周的当前学期。
 */
internal fun calendarWeekForDate(
    mapping: CourseScheduleCalendarMapping?,
    date: LocalDate,
): Int? = mapping?.weeks?.firstOrNull { week ->
    val start = week.startDate ?: return@firstOrNull false
    date >= start && date <= start.plus(6, DateTimeUnit.DAY)
}?.week?.takeIf { it in 1..COURSE_MAX_WEEK }

/**
 * 本学期课表使用教务当前学期；选课课表使用紧随其后的学期。
 * 若服务器已经把“当前学期”预切到尚未开学的学期，选课课表就使用该学期本身。
 * 找不到精确下一学期时返回空映射，宁可禁用导出，也不能复用旧学期日期。
 */
internal fun resolveCourseScheduleCalendarMappings(
    selectedSemesterLabel: String?,
    weekDates: Map<String, List<OccupancyWeekDate>>,
    today: LocalDate,
): Map<CourseScheduleType, CourseScheduleCalendarMapping> {
    val available = weekDates.mapNotNull { (label, weeks) ->
        val dated = weeks.filter { it.startDate != null }.sortedBy { it.startDate }
        if (dated.isEmpty()) null else CourseScheduleCalendarMapping(label, dated)
    }.associateBy(CourseScheduleCalendarMapping::semesterLabel)
    if (available.isEmpty()) return emptyMap()
    val current = selectedSemesterLabel?.let(available::get)
        ?: available.values.firstOrNull { mapping ->
            mapping.weeks.any { week ->
                val start = week.startDate ?: return@any false
                today >= start && today <= start.plus(6, DateTimeUnit.DAY)
            }
        }
        ?: available.values
            .filter { mapping -> mapping.weeks.firstNotNullOf(OccupancyWeekDate::startDate) <= today }
            .maxByOrNull { mapping -> mapping.weeks.firstNotNullOf(OccupancyWeekDate::startDate) }
        ?: available.values.minBy { mapping -> mapping.weeks.firstNotNullOf(OccupancyWeekDate::startDate) }

    val currentStart = current.weeks.firstNotNullOf(OccupancyWeekDate::startDate)
    val selection = if (currentStart > today) {
        // 教务在开学前已把下拉切到新学期时，选课课表就是这个尚未开始的学期。
        current
    } else {
        nextSemesterLabel(current.semesterLabel)?.let(available::get)
    }
    return buildMap {
        put(CourseScheduleType.CURRENT, current)
        if (selection != null) put(CourseScheduleType.SELECTION, selection)
    }
}

private fun nextSemesterLabel(label: String): String? {
    val match = Regex("^(\\d{4})-(\\d{4})-([12])$").matchEntire(label) ?: return null
    val firstYear = match.groupValues[1].toIntOrNull() ?: return null
    val secondYear = match.groupValues[2].toIntOrNull() ?: return null
    return when (match.groupValues[3]) {
        "1" -> "$firstYear-$secondYear-2"
        "2" -> "$secondYear-${secondYear + 1}-1"
        else -> null
    }
}

enum class CourseScheduleType {
    CURRENT,
    SELECTION,
}

enum class CourseScheduleContentSource {
    CACHE,
    NETWORK,
}

enum class CourseCompactViewMode {
    DAY,
    WEEK,
}

data class CourseScheduleUiState(
    val courses: List<Course> = emptyList(),
    val supplementalCourses: List<Course> = emptyList(),
    val physicsLabs: List<PhysicsLab> = emptyList(),
    val physicsDemoBookings: List<team.bjtuss.bjtuselfservice.shared.feature.phydemo.PhysicsDemoBooking> = emptyList(),
    val currentWeek: Int = 0,
    /**
     * 当前 [currentWeek] 是否已经由本学期校历确认过。
     *
     * - `true`：校历已按日期给出结论。当天落在教学周就是该周；落在教学周之间的空档
     *   （秋假、寒暑假）同样是结论，首页显示「非教学周」，不能再写「日程加载中」；
     * - `false`：手里只有未经验证的中间值，首页据此显示「日程加载中」并禁止自动跟随，
     *   避免把中间值当成最终周数展示后又跳一次。
     *
     * 远端 `getTimeList` / `room_view` 的裸周数永远不参与本字段，也永远不直接进 UI。
     */
    val weekResolved: Boolean = false,
    /**
     * 当前显示的周数是否来自“上次校历校准过的缓存”。
     *
     * 为 true 时首页可以先按 B 方案立即显示缓存值（最多在校历到达后再跳一次），
     * 为 false 且 [weekResolved] 仍为 false 时，首页没有任何可信值可显示，
     * 应整体显示「日程加载中」。
     */
    val hasCachedWeek: Boolean = false,
    val scheduleType: CourseScheduleType = CourseScheduleType.CURRENT,
    val selectedWeek: Int = 0,
    val selectedDay: Int = 0,
    val selectedCourseId: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val source: CourseScheduleContentSource? = null,
    val failure: CourseScheduleSyncFailure? = null,
    val followCurrentWeek: Boolean = true,
    val compactViewMode: CourseCompactViewMode = CourseCompactViewMode.WEEK,
    val calendarSemesterLabel: String? = null,
    val academicWeeks: List<OccupancyWeekDate> = emptyList(),
    val homeAcademicWeeks: List<OccupancyWeekDate> = emptyList(),
    val isCalendarLoading: Boolean = false,
    /**
     * 本学期校历没有拉到。课表缓存或网络快照仍可能成功；
     * 首页「课程表与校历周数」不能只凭课表来源显示已完成。
     */
    val calendarFailed: Boolean = false,
    val todayDate: LocalDate? = null,
    val selectedDate: LocalDate? = null,
    val dateOutsideTeachingWeeks: Boolean = false,
    /** 当前分页落在校历补出的非教学自然周时使用；0 周仍保留“全部教学周”语义。 */
    val selectedNonTeachingWeekStart: LocalDate? = null,
) {
    val scheduleCourses: List<Course>
        get() = courses.filter { course ->
            course.isCurrentSemester == (scheduleType == CourseScheduleType.SELECTION)
        } + supplementalCourses

    val visibleCourses: List<Course>
        get() = coursesForPage(if (isNonTeachingWeek) null else selectedWeek,
            selectedNonTeachingWeekStart ?: weekDate()?.startDate)

    val isNonTeachingWeek: Boolean
        get() = dateOutsideTeachingWeeks || selectedNonTeachingWeekStart != null

    val selectedCourse: Course?
        get() = (courses + supplementalCourses).firstOrNull { it.id == selectedCourseId }

    fun weekDate(week: Int = selectedWeek): OccupancyWeekDate? =
        academicWeeks.firstOrNull { it.week == week }

    fun dateFor(week: Int, dayIndex: Int): LocalDate? =
        weekDate(week)?.startDate?.plus(dayIndex, DateTimeUnit.DAY)

    /**
     * 副标题中的“当前周”提示：
     * - 本学期课表直接使用教务快照 currentWeek；
     * - 选课课表必须按自己的校历判断。学期未开始显示“学期尚未开始”，
     *   已开始则按校历计算当前周；快照 currentWeek 属于本学期，不能用于选课课表。
     */
    fun semesterStatusSubtitle(): String? {
        if (scheduleType == CourseScheduleType.CURRENT) {
            return if (currentWeek > 0) "当前第 $currentWeek 周" else null
        }
        val today = todayDate ?: return null
        val firstStart = academicWeeks.mapNotNull(OccupancyWeekDate::startDate).minOrNull()
            ?: return null
        if (today < firstStart) return "学期尚未开始"
        val currentSelectionWeek = academicWeeks.firstOrNull { week ->
            val start = week.startDate ?: return@firstOrNull false
            today >= start && today <= start.plus(6, DateTimeUnit.DAY)
        }?.week
        return currentSelectionWeek?.let { "当前第 $it 周" }
    }
}

class CourseScheduleScreenModel(
    private val repository: CourseScheduleRepository,
    private val changeRecorder: DataChangeRecorder<Course>? = null,
    private val calendarRepository: ClassroomOccupancyRepository? = null,
    private val todayProvider: () -> LocalDate = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    },
) {
    private val mutableState = MutableStateFlow(
        CourseScheduleUiState(
            todayDate = todayProvider(),
            // 没有校历源时不存在“待确认”状态，避免首页一直停在加载占位。
            weekResolved = calendarRepository == null,
        ),
    )
    val state: StateFlow<CourseScheduleUiState> = mutableState.asStateFlow()

    /** 本地缓存是否已灌入 UI。可在登录完成前执行。 */
    private var cacheLoaded = false
    /**
     * 网络自动同步是否已启动。必须在登录成功后由 shell 以 refreshFromNetwork=true 触发，
     * 避免静默登录未完成时白白失败多次。
     */
    private var networkAutoSyncStarted = false
    private var refreshInFlight = false
    private val calendarMutex = Mutex()
    private var calendarLoaded = false
    /** App 提供校历源时，远端当前周必须先经过日期校准才能覆盖缓存。 */
    private val calendarValidationEnabled = calendarRepository != null
    private var calendarMappings: Map<CourseScheduleType, CourseScheduleCalendarMapping> = emptyMap()

    /**
     * @param refreshFromNetwork false：只读缓存（页面打开即可）；true：登录成功后的自动同步（可重试）。
     * 两阶段可分别调用，且顺序无关：先缓存后网络，或直接网络（内部仍会先灌缓存）。
     */
    suspend fun initialize(refreshFromNetwork: Boolean = true) {
        if (!cacheLoaded) {
            cacheLoaded = true
            val cached = runCatching(repository::load).getOrNull()
            if (cached != null) {
                applySnapshot(
                    snapshot = cached,
                    source = if (cached.courses.isEmpty()) null else CourseScheduleContentSource.CACHE,
                    failure = null,
                )
            } else {
                mutableState.value = mutableState.value.copy(
                    isLoading = true,
                    failure = CourseScheduleSyncFailure.CACHE,
                )
            }
            if (!refreshFromNetwork) {
                mutableState.value = mutableState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
        if (refreshFromNetwork && !networkAutoSyncStarted) {
            networkAutoSyncStarted = true
            // 登录后自动同步：失败再试，避免首包抖动就弹「同步失败」。
            refreshWithRetry(maxAttempts = AUTO_SYNC_MAX_ATTEMPTS)
        }
    }

    /**
     * 手动刷新：单次请求。自动同步请用 [refreshWithRetry] 或 [initialize]。
     */
    suspend fun refresh() {
        if (refreshInFlight) return
        refreshInFlight = true
        val before = mutableState.value
        mutableState.value = before.copy(
            isLoading = before.courses.isEmpty(),
            isRefreshing = before.courses.isNotEmpty(),
            failure = null,
        )
        try {
            coroutineScope {
                // 校历与课程列表独立加载；慢接口不能把另一项卡在“等待同步”。
                // applySnapshot 仍只接受已由校历确认的周数，不展示远端裸周数。
                launch { ensureCalendarLoaded() }
                when (val result = repository.refresh()) {
                    is CourseScheduleRefreshResult.Success -> {
                        changeRecorder.recordSafely(before.courses, result.snapshot.courses)
                        applySnapshot(result.snapshot, CourseScheduleContentSource.NETWORK, null)
                    }
                    is CourseScheduleRefreshResult.Failure -> applySnapshot(
                        result.snapshot,
                        if (result.snapshot.courses.isEmpty()) null else CourseScheduleContentSource.CACHE,
                        result.reason,
                    )
                }
            }
        } finally {
            refreshInFlight = false
            // 协程取消时 applySnapshot 不会执行，须清掉 isRefreshing，否则首页 OR 聚合会一直「同步中」。
            val current = mutableState.value
            if (current.isRefreshing || current.isLoading) {
                mutableState.value = current.copy(isRefreshing = false, isLoading = false)
            }
        }
    }

    /**
     * 连续刷新最多 [maxAttempts] 次；任一次成功即停。
     * 用于登录后自动同步：中间失败不长期停留，最后一次失败才保留 failure 横幅。
     */
    suspend fun refreshWithRetry(
        maxAttempts: Int = AUTO_SYNC_MAX_ATTEMPTS,
        delayMillis: Long = AUTO_SYNC_RETRY_DELAY_MILLIS,
    ) {
        require(maxAttempts >= 1)
        repeat(maxAttempts) { index ->
            refresh()
            if (mutableState.value.failure == null) return
            if (index < maxAttempts - 1) delay(delayMillis)
        }
    }

    fun selectScheduleType(type: CourseScheduleType) {
        val current = mutableState.value
        if (type == current.scheduleType) return
        val targetWeek = if (type == CourseScheduleType.CURRENT) current.currentWeek else 0
        val calendar = calendarMappings[type]
        mutableState.value = current.copy(
            scheduleType = type,
            selectedWeek = targetWeek,
            // 进入本学期课表即恢复跟随；只有后续明确选周/选日期才冻结。
            followCurrentWeek = type == CourseScheduleType.CURRENT,
            selectedCourseId = null,
            calendarSemesterLabel = calendar?.semesterLabel,
            academicWeeks = calendar?.weeks.orEmpty(),
            selectedDate = calendar?.weeks
                ?.firstOrNull { it.week == targetWeek }
                ?.startDate
                ?.plus(current.selectedDay, DateTimeUnit.DAY),
            dateOutsideTeachingWeeks = false,
            selectedNonTeachingWeekStart = null,
        )
    }

    fun selectWeek(week: Int) {
        if (week !in 0..COURSE_MAX_WEEK) return
        val current = mutableState.value
        mutableState.value = current.copy(
            selectedWeek = week,
            followCurrentWeek = false,
            selectedCourseId = null,
            selectedDate = current.dateFor(week, current.selectedDay),
            dateOutsideTeachingWeeks = false,
            selectedNonTeachingWeekStart = null,
        )
    }

    fun selectNonTeachingWeek(startDate: LocalDate) {
        val current = mutableState.value
        mutableState.value = current.copy(
            selectedWeek = 0,
            followCurrentWeek = false,
            selectedCourseId = null,
            selectedDate = startDate,
            dateOutsideTeachingWeeks = false,
            selectedNonTeachingWeekStart = startDate,
        )
    }

    /** 桌面按钮/触摸板必须基于最新状态原子递进，不能使用 Modifier 首次组合时捕获的旧周数。 */
    fun moveWeekBy(offset: Int) {
        if (offset !in setOf(-1, 1)) return
        val current = mutableState.value
        val pages = courseScheduleWeekPages(current)
        val currentPage = when {
            current.selectedNonTeachingWeekStart != null -> pages.indexOfFirst {
                it.isNonTeachingWeek && it.startDate == current.selectedNonTeachingWeekStart
            }
            current.selectedWeek == 0 -> 0
            else -> pages.indexOfFirst { it.teachingWeek == current.selectedWeek }
        }.takeIf { it >= 0 } ?: 0
        val targetPage = (currentPage + offset).coerceIn(0, pages.lastIndex)
        val page = pages[targetPage]
        when {
            page.isOverview -> selectWeek(0)
            page.isNonTeachingWeek -> page.startDate?.let(::selectNonTeachingWeek)
            else -> page.teachingWeek?.let(::selectWeek)
        }
    }

    fun canMoveWeekBy(offset: Int): Boolean {
        if (offset !in setOf(-1, 1)) return false
        val current = mutableState.value
        val pages = courseScheduleWeekPages(current)
        val currentPage = when {
            current.selectedNonTeachingWeekStart != null -> pages.indexOfFirst {
                it.isNonTeachingWeek && it.startDate == current.selectedNonTeachingWeekStart
            }
            current.selectedWeek == 0 -> 0
            else -> pages.indexOfFirst { it.teachingWeek == current.selectedWeek }
        }.takeIf { it >= 0 } ?: 0
        return currentPage + offset in pages.indices
    }

    fun selectDay(day: Int) {
        if (day !in 0..6) return
        val current = mutableState.value
        mutableState.value = current.copy(
            selectedDay = day,
            selectedCourseId = null,
            selectedDate = current.dateFor(current.selectedWeek, day),
            dateOutsideTeachingWeeks = false,
        )
    }

    fun selectCompactViewMode(mode: CourseCompactViewMode) {
        mutableState.value = mutableState.value.copy(compactViewMode = mode, selectedCourseId = null)
    }

    /**
     * 日期选择会在本学期与选课学期两套校历中查找；命中另一学期时原子切换课表类型、
     * 学期和周次，防止先切类型再切周造成中间帧错位，也防止后到同步覆盖用户选择。
     */
    fun selectDate(date: LocalDate) {
        val current = mutableState.value
        val destination = sequenceOf(
            current.scheduleType,
            CourseScheduleType.entries.first { it != current.scheduleType },
        ).mapNotNull { type ->
            val mapping = calendarMappings[type] ?: return@mapNotNull null
            val week = mapping.weeks.firstOrNull { item ->
                val start = item.startDate ?: return@firstOrNull false
                date >= start && date <= start.plus(6, DateTimeUnit.DAY)
            } ?: return@mapNotNull null
            Triple(type, mapping, week)
        }.firstOrNull()
        mutableState.value = if (destination == null) {
            val monday = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
            current.copy(
                selectedWeek = 0,
                selectedDay = date.dayOfWeek.isoDayNumber - 1,
                selectedDate = date,
                dateOutsideTeachingWeeks = true,
                followCurrentWeek = false,
                selectedCourseId = null,
                selectedNonTeachingWeekStart = monday,
            )
        } else {
            val (scheduleType, mapping, week) = destination
            current.copy(
                scheduleType = scheduleType,
                selectedWeek = week.week,
                selectedDay = date.dayOfWeek.isoDayNumber - 1,
                selectedDate = date,
                dateOutsideTeachingWeeks = false,
                followCurrentWeek = false,
                selectedCourseId = null,
                calendarSemesterLabel = mapping.semesterLabel,
                academicWeeks = mapping.weeks,
                selectedNonTeachingWeekStart = null,
            )
        }
    }

    /**
     * 加载或补救本学期校历。
     *
     * 已经确认过的结果直接返回。映射还在、只是确认标记被课表快照清掉时，按现有映射重算，
     * 不再打一次网络。上次失败或从未成功时重新请求；仍然没有当前学期映射则记 [CourseScheduleUiState.calendarFailed]，
     * 方便首页刷新重试，而不是把「课表已有缓存」显示成校历完成。
     */
    suspend fun ensureCalendarLoaded() {
        val calendarRepository = calendarRepository ?: return
        if (calendarLoaded && mutableState.value.weekResolved) return
        calendarMutex.withLock {
            if (calendarLoaded && mutableState.value.weekResolved) return
            if (calendarMappings.containsKey(CourseScheduleType.CURRENT)) {
                publishCalendarResolution(todayProvider())
                calendarLoaded = true
                return
            }
            mutableState.value = mutableState.value.copy(
                isCalendarLoading = true,
                calendarFailed = false,
            )
            try {
                val (semesters, weekDates) = coroutineScope {
                    // 学期查询走登录会话队列，公开校历可以立即请求，不必跟着排队。
                    val semesters = async { calendarRepository.fetchSemesters() }
                    val weekDates = async { calendarRepository.fetchWeekDates() }
                    semesters.await() to weekDates.await()
                }
                val today = todayProvider()
                calendarMappings = resolveCourseScheduleCalendarMappings(
                    selectedSemesterLabel = semesters.selected?.label,
                    weekDates = weekDates,
                    today = today,
                )
                if (!calendarMappings.containsKey(CourseScheduleType.CURRENT)) {
                    // 空映射含网络失败被仓库吞成空表的情况。不把课表周数猜成已确认。
                    mutableState.value = mutableState.value.copy(
                        isCalendarLoading = false,
                        calendarFailed = true,
                    )
                    return
                }
                publishCalendarResolution(today)
                calendarLoaded = true
            } catch (error: kotlinx.coroutines.CancellationException) {
                mutableState.value = mutableState.value.copy(isCalendarLoading = false)
                throw error
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    isCalendarLoading = false,
                    calendarFailed = true,
                )
            }
        }
    }

    /** 用已经拿到的当前学期校历写回教学周。调用方须保证映射里有 [CourseScheduleType.CURRENT]。 */
    private fun publishCalendarResolution(today: LocalDate) {
        val current = mutableState.value
        val selectedCalendar = calendarMappings[current.scheduleType]
        val weeks = selectedCalendar?.weeks.orEmpty()
        // getTimeList/room_view 只作为刷新快照中的附带字段，不参与当前周展示；
        // 当前学期校历带有真实日期，当前周统一由它命中今天的日期决定，
        // 避免接口在学期切换边界返回第 1 周时污染首页和课表。
        val calendarCurrentWeek = calendarWeekForDate(
            mapping = calendarMappings[CourseScheduleType.CURRENT],
            date = today,
        )
        val currentCalendarAvailable = calendarMappings.containsKey(CourseScheduleType.CURRENT)
        val followCalendarCurrentWeek = current.scheduleType == CourseScheduleType.CURRENT &&
            current.followCurrentWeek
        val effectiveCurrentWeek = if (currentCalendarAvailable) {
            // 校历有当前学期映射时，当天不在教学周就明确为 0，不沿用旧周数。
            calendarCurrentWeek ?: 0
        } else {
            // 校历暂时不可用：保留上次校历校准过的缓存值，不接受任何远端猜测值。
            current.currentWeek.takeIf { it in 1..COURSE_MAX_WEEK } ?: 0
        }
        val effectiveSelectedWeek = if (followCalendarCurrentWeek) {
            calendarCurrentWeek ?: 0
        } else {
            current.selectedWeek
        }
        val currentWeekStart = today.minus(
            today.dayOfWeek.isoDayNumber - 1,
            DateTimeUnit.DAY,
        )
        if (calendarCurrentWeek != null && calendarCurrentWeek != current.currentWeek) {
            repository.reconcileCurrentWeek(calendarCurrentWeek)
        }
        val selectedDate = current.selectedDate
            ?.takeIf { date -> weeks.any { week ->
                val start = week.startDate ?: return@any false
                date >= start && date <= start.plus(6, DateTimeUnit.DAY)
            } }
            ?: weeks.firstOrNull { it.week == effectiveSelectedWeek }?.startDate
                ?.plus(current.selectedDay, DateTimeUnit.DAY)
            ?: if (followCalendarCurrentWeek && currentCalendarAvailable && calendarCurrentWeek == null) {
                today
            } else {
                current.selectedNonTeachingWeekStart
                    ?.let { current.selectedDate ?: it }
            }
        mutableState.value = current.copy(
            calendarSemesterLabel = selectedCalendar?.semesterLabel,
            academicWeeks = weeks,
            homeAcademicWeeks = calendarMappings[CourseScheduleType.CURRENT]?.weeks.orEmpty(),
            currentWeek = effectiveCurrentWeek,
            selectedWeek = effectiveSelectedWeek,
            // 有当前学期映射并按日期给出结论（含明确的非教学周）才算确认。
            weekResolved = currentCalendarAvailable,
            hasCachedWeek = calendarCurrentWeek != null || effectiveCurrentWeek in 1..COURSE_MAX_WEEK,
            isCalendarLoading = false,
            calendarFailed = false,
            todayDate = today,
            selectedDate = selectedDate,
            selectedNonTeachingWeekStart = if (
                followCalendarCurrentWeek && currentCalendarAvailable && calendarCurrentWeek == null
            ) {
                currentWeekStart
            } else {
                current.selectedNonTeachingWeekStart
            },
        )
    }

    fun showCourseDetails(courseId: Int) {
        mutableState.value = mutableState.value.copy(selectedCourseId = courseId)
    }

    fun dismissCourseDetails() {
        mutableState.value = mutableState.value.copy(selectedCourseId = null)
    }

    fun dismissFailure() {
        mutableState.value = mutableState.value.copy(failure = null)
    }

    private fun applySnapshot(
        snapshot: CourseScheduleSnapshot,
        source: CourseScheduleContentSource?,
        failure: CourseScheduleSyncFailure?,
    ) {
        val current = mutableState.value
        val today = todayProvider()
        val currentCalendarKnown = calendarMappings.containsKey(CourseScheduleType.CURRENT)
        val calendarCurrentWeek = calendarWeekForDate(
            mapping = calendarMappings[CourseScheduleType.CURRENT],
            date = today,
        )
        // getTimeList/room_view 在学期切换边界可能短暂返回“第 1 周”。
        // 校历还没加载时，不能把“不知道”写成 0，否则首页会先闪「非教学周」。
        // 校历已经给出当前学期后，当天落在空档就是确认过的 0 周；
        // 若这里改回未确认，秋假期间每次刷新都会把首页打回「日程加载中」。
        val effectiveCurrentWeek = when {
            !calendarValidationEnabled ->
                snapshot.currentWeek.takeIf { it in 1..COURSE_MAX_WEEK } ?: 0
            currentCalendarKnown -> calendarCurrentWeek ?: 0
            source == CourseScheduleContentSource.CACHE ->
                snapshot.currentWeek.takeIf { it in 1..COURSE_MAX_WEEK } ?: 0
            // 远端刷新到达时绝不能把服务器的裸周数写进 UI：那正是启动期间
            // 缓存值 -> 裸值 -> 校历值 反复跳变的来源。
            else -> current.currentWeek.takeIf { it in 1..COURSE_MAX_WEEK } ?: 0
        }
        // 当前学期校历已在手里才算确认，含“确定不是教学周”。
        // 缓存快照即便带着上次的周数，在校历到达前也只能算未确认。
        val weekResolved = !calendarValidationEnabled || currentCalendarKnown
        val shouldApplyCurrentWeek = current.scheduleType == CourseScheduleType.CURRENT &&
            current.followCurrentWeek &&
            weekSelectionIsTrustworthy(
                weekResolved = weekResolved,
                calendarValidationEnabled = calendarValidationEnabled,
                effectiveCurrentWeek = effectiveCurrentWeek,
            )
        val selectedWeek = if (shouldApplyCurrentWeek) effectiveCurrentWeek else current.selectedWeek
        val currentWeekStart = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        if (
            calendarValidationEnabled && effectiveCurrentWeek != snapshot.currentWeek
        ) {
            repository.reconcileCurrentWeek(effectiveCurrentWeek)
        }
        val visibleIds = snapshot.courses.mapTo(mutableSetOf(), Course::id)
        mutableState.value = current.copy(
            courses = snapshot.courses,
            currentWeek = effectiveCurrentWeek,
            weekResolved = weekResolved,
            calendarFailed = if (currentCalendarKnown) false else current.calendarFailed,
            hasCachedWeek = source == CourseScheduleContentSource.CACHE &&
                (snapshot.currentWeek in 1..COURSE_MAX_WEEK),
            selectedWeek = selectedWeek,
            // 缓存/网络快照都属于自动结果，不能把“跟随当前周”误关掉；
            // selectWeek/selectDate 才代表用户明确选择并会把它置 false。
            followCurrentWeek = current.followCurrentWeek,
            selectedCourseId = current.selectedCourseId?.takeIf(visibleIds::contains),
            isLoading = false,
            isRefreshing = false,
            source = source,
            failure = failure,
            todayDate = today,
            selectedDate = if (shouldApplyCurrentWeek) {
                if (effectiveCurrentWeek == 0) today else current.dateFor(effectiveCurrentWeek, current.selectedDay)
            } else {
                current.selectedDate
            },
            selectedNonTeachingWeekStart = if (shouldApplyCurrentWeek && effectiveCurrentWeek == 0) {
                currentWeekStart
            } else if (shouldApplyCurrentWeek) {
                null
            } else {
                current.selectedNonTeachingWeekStart
            },
        )
    }
}

/**
 * 能否把这个 [effectiveCurrentWeek] 当作“跟随当前周”的目标。
 *
 * 关键场景（2026-09-16 实机反馈）：本地缓存是第 2 周、校历还没加载完成时，
 * [effectiveCurrentWeek] 只有 2 而没有确认标志。此时绝不能把它当 0（非教学周）处理，
 * 否则首页会先显示「非教学周」，等校历到达再跳回第 2 周——正是要消除的反复跳变。
 */
internal fun weekSelectionIsTrustworthy(
    weekResolved: Boolean,
    calendarValidationEnabled: Boolean,
    effectiveCurrentWeek: Int,
): Boolean = when {
    !calendarValidationEnabled -> true
    weekResolved -> true
    // 未确认但手里是缓存下来的真实教学周：可以先跟随显示。
    else -> effectiveCurrentWeek in 1..COURSE_MAX_WEEK
}

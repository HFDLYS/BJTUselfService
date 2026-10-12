package team.bjtuss.bjtuselfservice.shared.feature.phyvlab

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabActivitiesResult
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabAssignmentDetailResult
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabCacheSnapshot
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabCachedAssignmentDetail
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabRemoteFailure
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabCoursesResult
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabEventsResult
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabRepository
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSessionProtocol
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSessionResult
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSubmissionResult
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.phyVlabDebug
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder
import team.bjtuss.bjtuselfservice.shared.domain.change.recordSafely
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabActivity
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabAssignmentDetail
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabCourse
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabEvent
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabEventKind
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import kotlin.time.Clock
import team.bjtuss.bjtuselfservice.shared.feature.assignment.AssignmentFilters
import team.bjtuss.bjtuselfservice.shared.feature.assignment.AssignmentFilterStore
import kotlin.time.Instant

/** 物理在线是学校平台，页面日期和 Moodle 日历均以北京时间为准。 */
private val PHYVLAB_TIME_ZONE = TimeZone.of("Asia/Shanghai")

enum class PhyVlabContentSource {
    NONE,
    CACHE,
    NETWORK,
}

data class PhyVlabUiState(
    val filters: AssignmentFilters = AssignmentFilters(),
    val courses: List<PhyVlabCourse> = emptyList(),
    val events: List<PhyVlabEvent> = emptyList(),
    /** 不受当前物理在线月视图限制，专供首页“本周安排”使用。 */
    val agendaEvents: List<PhyVlabEvent> = emptyList(),
    val monthLabel: String = "",
    val activities: List<PhyVlabActivity> = emptyList(),
    val selectedCourse: PhyVlabCourse? = null,
    val selectedActivity: PhyVlabActivity? = null,
    val assignmentDetail: PhyVlabAssignmentDetail? = null,
    val isDetailLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val detailFailure: PhyVlabSyncFailure? = null,
    val submissionFeedback: String? = null,
    val finalizationStatement: String? = null,
    val submissionRevision: Long = 0,
    val isLoading: Boolean = false,
    val failure: PhyVlabSyncFailure? = null,
    val failureDetail: String? = null,
    val casLoginRequired: Boolean = false,
    val contentSource: PhyVlabContentSource = PhyVlabContentSource.NONE,
    val cachedAtEpochMillis: Long? = null,
) {
    val hasLoaded: Boolean
        get() = failure != null || courses.isNotEmpty() || selectedCourse != null || casLoginRequired
}

class PhyVlabScreenModel(
    private val repository: PhyVlabRepository,
    private val sessionProtocol: PhyVlabSessionProtocol,
    /**
     * 可选的 App 内 CAS 恢复入口。物理在线的 MoodleSession 过期时，
     * 不把用户直接送去系统浏览器；恢复失败才显示重新登录提示。
     */
    private val reauthenticate: (suspend () -> Boolean)? = null,
    private val localDataSource: PhyVlabLocalDataSource? = null,
    /** 可选：刷新成功后把活动列表变化写入首页「数据变动」。 */
    private val changeRecorder: DataChangeRecorder<PhyVlabActivity>? = null,
    accountScope: String? = null,
    private val nowProvider: () -> Instant = { Clock.System.now() },
    private val filterStore: AssignmentFilterStore? = null,
) {
    private val mutableState = MutableStateFlow(PhyVlabUiState(filters = filterStore?.load() ?: AssignmentFilters()))
    val state: StateFlow<PhyVlabUiState> = mutableState.asStateFlow()
    private val refreshMutex = Mutex()
    private var preparedFinalization: Pair<Int, List<String>>? = null
    private var lastRequestedMonthSeconds: Long? = null
    private var networkAutoSyncStarted = false
    private var sessionReady = false
    private var cacheLoaded = false
    private val cacheAccountScope = accountScope?.trim()?.takeIf { it.isNotEmpty() }
    private val activitiesByCourse = mutableMapOf<Int, List<PhyVlabActivity>>()
    private var scheduleEvents: List<PhyVlabEvent> = emptyList()
    private val assignmentDetailsByActivity = mutableMapOf<ActivityCacheKey, PhyVlabAssignmentDetail>()

    /**
     * 登录完成后的自动同步与用户进入物理在线页共用同一个请求入口。
     * 关闭自动同步时不预取网络；用户打开物理在线页仍会按需刷新。
     */
    suspend fun initialize(refreshFromNetwork: Boolean = true) {
        loadCachedSnapshotIfNeeded()
        if (refreshFromNetwork && !networkAutoSyncStarted) {
            networkAutoSyncStarted = true
            refresh()
        }
    }

    suspend fun refresh() {
        if (!refreshMutex.tryLock()) return
        try {
            // 首页主动刷新、失败后的用户重试和物理在线页初始化都走这里。
            // 只要真正进入过一次网络刷新，本次登录会话内再次进入页面就只复用
            // 当前状态/缓存，不再因为 Workspace 重建而重复触发自动同步。
            networkAutoSyncStarted = true
            loadCachedSnapshotIfNeeded()
            phyVlabDebug("refresh start")
            sessionReady = false
            mutableState.value = mutableState.value.copy(isLoading = true, failure = null, casLoginRequired = false)
            when (val session = establishSessionWithRecovery()) {
                is PhyVlabSessionResult.Failed -> {
                    phyVlabDebug("session failed reason=${session.reason} detail=${session.detail ?: "none"}")
                    if (session.reason == PhyVlabRemoteFailure.SESSION_EXPIRED &&
                        mutableState.value.contentSource != PhyVlabContentSource.CACHE
                    ) {
                        // 即使恢复入口不可用/失败，也进入统一的“重新建立认证”状态，
                        // 不退回“没有课程→打开浏览器”的旧兜底路径。
                        mutableState.value = mutableState.value.copy(casLoginRequired = true)
                    }
                    publishFailure(session.reason.toUiFailure(), session.detail)
                    return
                }
                PhyVlabSessionResult.CasLoginRequired -> {
                    phyVlabDebug("session requires CAS login")
                    mutableState.value = mutableState.value.copy(casLoginRequired = true)
                    return
                }
                is PhyVlabSessionResult.Ready -> {
                    sessionReady = true
                    phyVlabDebug("session ready")
                }
            }
            // 不使用设备系统时区：在海外设备/模拟器上，学校平台的“本月”不能跨日漂移。
            val now = nowProvider().toLocalDateTime(PHYVLAB_TIME_ZONE)
            val monthStart = beijingMonthStartSeconds(year = now.year, month = now.month.ordinal + 1)
            val courses = when (val result = repository.fetchCourses()) {
                is PhyVlabCoursesResult.Success -> result.courses
                is PhyVlabCoursesResult.Failure -> {
                    publishFailure(result.reason)
                    return
                }
            }
            val selectedCourseId = mutableState.value.selectedCourse?.id
            val fetchedActivitiesByCourse = linkedMapOf<Int, List<PhyVlabActivity>>()
            val fetchedScheduleEvents = mutableListOf<PhyVlabEvent>()
            var activityFetchFailed = false

            // 课程页是服务端渲染的，作业的开放/到期时间比日历月视图更稳定；先在内存中
            // 组成完整快照，成功后一次性替换，网络失败时才能继续可靠地显示旧缓存。
            courses.forEach { course ->
                when (val result = repository.fetchCourseActivities(course)) {
                    is PhyVlabActivitiesResult.Success -> {
                        fetchedActivitiesByCourse[course.id] = result.activities
                        fetchedScheduleEvents += result.activities.flatMap { activity ->
                            listOfNotNull(activity.toStartEvent(), activity.toDeadlineEvent())
                        }
                        phyVlabDebug(
                            "course activities courseId=${course.id} count=${result.activities.size} " +
                                "starts=${result.activities.count { it.openTimestamp != null }} " +
                                "deadlines=${result.activities.count { it.dueTimestamp != null }}",
                        )
                    }
                    is PhyVlabActivitiesResult.Failure -> {
                        activityFetchFailed = true
                        phyVlabDebug("course activities failed courseId=${course.id} reason=${result.reason}")
                    }
                }
            }

            // 有旧内容时，任意一门课程读取失败都保留原快照；避免把“缓存”标签贴在半截新数据上。
            if (activityFetchFailed && (
                    mutableState.value.courses.isNotEmpty() ||
                        mutableState.value.contentSource == PhyVlabContentSource.CACHE
                    )
            ) {
                publishFailure(PhyVlabSyncFailure.NETWORK)
                return
            }

            val previousActivities = activitiesByCourse.values.flatten()
                .distinctBy { it.courseId to it.id }
            activitiesByCourse.clear()
            activitiesByCourse.putAll(fetchedActivitiesByCourse)
            if (!activityFetchFailed) {
                // 用同一 identity 去重后再 diff，避免同一活动在多门课缓存里重复计数。
                val afterActivities = fetchedActivitiesByCourse.values.flatten()
                    .distinctBy { it.courseId to it.id }
                changeRecorder.recordSafely(previousActivities, afterActivities)
            }
            scheduleEvents = resolveEventSubmitted(fetchedScheduleEvents.distinctBy(PhyVlabEvent::id))
            assignmentDetailsByActivity.keys.retainAll(
                activitiesByCourse.values.flatten().map { it.cacheKey() }.toSet(),
            )
            val selectedCourse = courses.firstOrNull { it.id == selectedCourseId } ?: courses.firstOrNull()
            val syncedAt = Clock.System.now().toEpochMilliseconds()
            mutableState.value = mutableState.value.copy(
                courses = courses,
                events = eventsForSelectedCourse(selectedCourse, monthStart),
                agendaEvents = scheduleEvents,
                monthLabel = monthLabelFor(monthStart),
                activities = selectedCourse?.let { activitiesByCourse[it.id].orEmpty() }.orEmpty(),
                selectedCourse = selectedCourse,
                selectedActivity = null,
                assignmentDetail = null,
                detailFailure = null,
                submissionFeedback = null,
                contentSource = if (activityFetchFailed) {
                    PhyVlabContentSource.NONE
                } else {
                    PhyVlabContentSource.NETWORK
                },
                cachedAtEpochMillis = syncedAt.takeIf { !activityFetchFailed },
                failure = PhyVlabSyncFailure.NETWORK.takeIf { activityFetchFailed },
                failureDetail = null,
            )
            lastRequestedMonthSeconds = monthStart
            if (!activityFetchFailed) persistCache(syncedAt)
            phyVlabDebug(
                "refresh data courses=${courses.size} activities=${activitiesByCourse.values.sumOf { it.size }} " +
                    "events=${scheduleEvents.size}",
            )
        } catch (error: CancellationException) {
            clearLoading()
            throw error
        } catch (_: Exception) {
            publishFailure(PhyVlabSyncFailure.NETWORK)
        } finally {
            clearLoading()
            refreshMutex.unlock()
        }
    }

    fun updateFilters(filters: AssignmentFilters) {
        filterStore?.save(filters)
        mutableState.value = mutableState.value.copy(filters = filters)
    }

    fun allActivities(): List<PhyVlabActivity> = activitiesByCourse.values.flatten().distinctBy { it.courseId to it.id }

    /** 首页事件 → 活动：先按 activity-<courseId>-<activityId>-… 的 id，再按 URL，跨所有课程查找。 */
    fun findActivityForEvent(event: PhyVlabEvent): PhyVlabActivity? {
        val activities = allActivities()
        val idParts = event.id.split("-")
        if (idParts.size >= 4 && idParts[0] == "activity") {
            val courseId = idParts[1].toIntOrNull()
            val activityId = idParts[2].toIntOrNull()
            activities.firstOrNull { it.courseId == courseId && it.id == activityId }?.let { return it }
        }
        return event.eventUrl?.let { url -> activities.firstOrNull { it.activityUrl == url } }
    }

    fun selectCourse(course: PhyVlabCourse) {
        mutableState.value = mutableState.value.copy(
            selectedCourse = course,
            activities = activitiesByCourse[course.id].orEmpty(),
            events = eventsForSelectedCourse(
                course,
                lastRequestedMonthSeconds ?: currentBeijingMonthStartSeconds(),
            ),
        )
    }

    fun showActivityDetails(activity: PhyVlabActivity) {
        preparedFinalization = null
        mutableState.value = mutableState.value.copy(
            selectedActivity = activity,
            // 先把上次成功读取的详情放进页面；网络请求随后只负责更新它，
            // 不让用户在等待会话/网络期间失去可用的作业要求和提交状态。
            assignmentDetail = assignmentDetailsByActivity[activity.cacheKey()],
            isDetailLoading = false,
            detailFailure = null,
            submissionFeedback = null,
        )
    }

    fun dismissActivityDetails() {
        preparedFinalization = null
        mutableState.value = mutableState.value.copy(
            selectedActivity = null,
            assignmentDetail = null,
            isDetailLoading = false,
            isSubmitting = false,
            detailFailure = null,
            submissionFeedback = null,
        )
    }

    suspend fun loadSelectedActivityDetail(force: Boolean = false) {
        val before = mutableState.value
        val activity = before.selectedActivity ?: return
        if (!force && before.isDetailLoading) return
        mutableState.value = before.copy(
            // assignmentDetail 可能来自本地快照；保留它，只把本次网络请求标成后台刷新。
            assignmentDetail = before.assignmentDetail
                ?: assignmentDetailsByActivity[activity.cacheKey()],
            isDetailLoading = true,
            detailFailure = null,
        )
        try {
            if (!ensureSessionForOperation()) {
                publishDetailFailure(activity, PhyVlabSyncFailure.SESSION_EXPIRED)
                return
            }
            var result = repository.fetchAssignmentDetail(activity)
            if (result is PhyVlabAssignmentDetailResult.Failure &&
                result.reason == PhyVlabSyncFailure.SESSION_EXPIRED
            ) {
                // 读取是幂等操作：会话在打开详情期间过期时，恢复后安全重试一次。
                sessionReady = false
                if (ensureSessionForOperation()) {
                    result = repository.fetchAssignmentDetail(activity)
                }
            }
            when (result) {
                is PhyVlabAssignmentDetailResult.Success -> {
                    phyVlabDebug("assignment detail model success canSubmit=${result.detail.canSubmit}")
                    mutableState.value = mutableState.value.copy(
                        assignmentDetail = result.detail,
                        isDetailLoading = false,
                        detailFailure = null,
                    )
                    assignmentDetailsByActivity[activity.cacheKey()] = result.detail
                    val completed = phyVlabAssignmentDetailHasSubmission(result.detail)
                    activitiesByCourse[activity.courseId] = activitiesByCourse[activity.courseId].orEmpty().map {
                        if (it.id == activity.id) it.copy(completed = completed) else it
                    }
                    scheduleEvents = resolveEventSubmitted(scheduleEvents)
                    mutableState.value = mutableState.value.copy(
                        activities = mutableState.value.activities.map { if (it.id == activity.id) it.copy(completed = completed) else it },
                        selectedActivity = mutableState.value.selectedActivity?.let { if (it.id == activity.id) it.copy(completed = completed) else it },
                        agendaEvents = scheduleEvents,
                        events = mutableState.value.selectedCourse?.let { eventsForSelectedCourse(it, lastRequestedMonthSeconds ?: currentBeijingMonthStartSeconds()) }
                            ?: scheduleEvents,
                    )
                    persistCache(mutableState.value.cachedAtEpochMillis ?: Clock.System.now().toEpochMilliseconds())
                }
                is PhyVlabAssignmentDetailResult.Failure -> {
                    phyVlabDebug("assignment detail model failure reason=${result.reason}")
                    publishDetailFailure(activity, result.reason)
                }
            }
        } catch (error: CancellationException) {
            mutableState.value = mutableState.value.copy(isDetailLoading = false)
            throw error
        } catch (_: Exception) {
            phyVlabDebug("assignment detail model exception")
            publishDetailFailure(activity, PhyVlabSyncFailure.NETWORK)
        }
    }

    suspend fun submitSelectedActivity(files: List<HomeworkFileContent>, removed: Set<String> = emptySet(), finalize: Boolean = false,
        filesChecked: Boolean = false) {
        if (mutableState.value.isSubmitting) return
        val activity = mutableState.value.selectedActivity ?: return
        val prepared = preparedFinalization
        preparedFinalization = null
        if (finalize && (!filesChecked || prepared?.first != activity.id || files.isNotEmpty() || removed.isNotEmpty())) {
            mutableState.value = mutableState.value.copy(submissionFeedback = "请先打开最终提交确认并核对文件，再确认提交。")
            return
        }
        if (files.isEmpty() && removed.isEmpty() && !finalize) return
        mutableState.value = mutableState.value.copy(isSubmitting = true, submissionFeedback = null)
        try {
            if (!ensureSessionForOperation()) {
                mutableState.value = mutableState.value.copy(
                    isSubmitting = false,
                    detailFailure = PhyVlabSyncFailure.SESSION_EXPIRED,
                    submissionFeedback = "物理在线会话已失效，请重新建立认证后再提交。",
                )
                return
            }
            // 提交是写操作：只在提交前恢复会话，不对一个不确定的 POST 结果自动重放，
            // 避免服务端已接收文件但响应丢失时产生重复提交。
            if (finalize) {
                val latest = (repository.fetchAssignmentDetail(activity) as? PhyVlabAssignmentDetailResult.Success)?.detail
                if (latest == null || mutableState.value.selectedActivity?.id != activity.id || !latest.canFinalize || latest.submittedFiles.map { it.fileName }.sorted() != prepared?.second) {
                    mutableState.value = mutableState.value.copy(isSubmitting = false,
                        submissionFeedback = "文件或提交状态已变化，或无法核对。请刷新后重新检查并确认提交。")
                    return
                }
            }
            when (val result = if (finalize) repository.finalizeAssignment(activity, mutableState.value.finalizationStatement) else repository.saveAssignment(activity, files, removed)) {
                PhyVlabSubmissionResult.Success -> {
                    mutableState.value = mutableState.value.copy(
                        submissionRevision = mutableState.value.submissionRevision + 1,
                        isSubmitting = false,
                        submissionFeedback = if (finalize) "已最终提交，正在核对状态。" else "文件已保存，尚未最终提交；可以继续修改，核对后请单独点击最终提交。",
                        assignmentDetail = null,
                    )
                    loadSelectedActivityDetail(force = true)
                }
                is PhyVlabSubmissionResult.Failure -> {
                    if (result.reason == PhyVlabSyncFailure.SESSION_EXPIRED) {
                        sessionReady = false
                    }
                    mutableState.value = mutableState.value.copy(
                        isSubmitting = false,
                        detailFailure = result.reason,
                        submissionFeedback = if (finalize) "最终提交结果未确认，请先刷新核对，不要直接重复提交。" else "文件保存未确认，请先刷新核对。",
                    )
                }
            }
        } catch (error: CancellationException) {
            mutableState.value = mutableState.value.copy(isSubmitting = false)
            throw error
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(
                isSubmitting = false,
                detailFailure = PhyVlabSyncFailure.NETWORK,
                submissionFeedback = "操作结果未确认，请先刷新核对；不会自动重复提交。",
            )
        }
    }

    suspend fun prepareFinalization(): Boolean {
        val activity = mutableState.value.selectedActivity ?: return false
        if (mutableState.value.isSubmitting) return false
        preparedFinalization = null
        mutableState.value = mutableState.value.copy(isSubmitting = true, submissionFeedback = null, finalizationStatement = null)
        return try {
            if (!ensureSessionForOperation()) return false
            val detail = (repository.fetchAssignmentDetail(activity) as? PhyVlabAssignmentDetailResult.Success)?.detail
                ?: throw IllegalStateException("Unable to read submission files")
            if (!detail.canFinalize || detail.submittedFiles.isEmpty()) {
                mutableState.value = mutableState.value.copy(submissionFeedback = "请先保存文件；已有文件且处于草稿状态才能最终提交。")
                return false
            }
            val statement = repository.finalizationStatement(activity)
            if (mutableState.value.selectedActivity?.id != activity.id) return false
            preparedFinalization = activity.id to detail.submittedFiles.map { it.fileName }.sorted()
            mutableState.value = mutableState.value.copy(finalizationStatement = statement, assignmentDetail = detail)
            true
        } catch (error: CancellationException) { throw error
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(submissionFeedback = "无法读取最终提交确认，请刷新或到网页核对。")
            false
        } finally { mutableState.value = mutableState.value.copy(isSubmitting = false) }
    }

    suspend fun loadSelectedActivities() {
        // 全量同步会逐门读取课程页；Compose 的选中课程副作用可能在第一门请求尚未完成时
        // 触发，这里直接等待全量同步的结果，避免同一课程产生重复网络请求。
        if (mutableState.value.isLoading) return
        val course = mutableState.value.selectedCourse ?: return
        activitiesByCourse[course.id]?.let { cached ->
            mutableState.value = mutableState.value.copy(activities = cached)
            return
        }
        refreshActivities(course)
    }

    suspend fun changeMonth(direction: Int) {
        val base = lastRequestedMonthSeconds ?: return
        val next = beijingMonthStartSecondsSafe(base, direction) ?: return
        try {
            when (val result = repository.fetchEvents(next)) {
                is PhyVlabEventsResult.Success -> {
                    scheduleEvents = resolveEventSubmitted(mergeEvents(scheduleEvents, result.events))
                    mutableState.value = mutableState.value.copy(
                        events = eventsForSelectedCourse(mutableState.value.selectedCourse, next),
                        agendaEvents = scheduleEvents,
                        monthLabel = monthLabelFor(next),
                        failure = null,
                        failureDetail = null,
                    )
                    lastRequestedMonthSeconds = next
                    persistCache(mutableState.value.cachedAtEpochMillis ?: Clock.System.now().toEpochMilliseconds())
                }
                is PhyVlabEventsResult.Failure -> publishFailure(result.reason)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            publishFailure(PhyVlabSyncFailure.NETWORK)
        }
    }

    private suspend fun refreshActivities(course: PhyVlabCourse) {
        try {
            when (val result = repository.fetchCourseActivities(course)) {
                is PhyVlabActivitiesResult.Success -> {
                    activitiesByCourse[course.id] = result.activities
                    val activityEvents = result.activities.flatMap { activity ->
                        listOfNotNull(activity.toStartEvent(), activity.toDeadlineEvent())
                    }
                    scheduleEvents = resolveEventSubmitted(
                        mergeEvents(
                            scheduleEvents.filterNot { it.id.startsWith("activity-${course.id}-") },
                            activityEvents,
                        ),
                    )
                    val month = lastRequestedMonthSeconds ?: currentBeijingMonthStartSeconds()
                    val refreshedAt = Clock.System.now().toEpochMilliseconds()
                    mutableState.value = mutableState.value.copy(
                        activities = result.activities,
                        events = eventsForSelectedCourse(course, month),
                        agendaEvents = scheduleEvents,
                        // 单门按需刷新不能宣称整份物理在线快照都已同步。
                        contentSource = if (mutableState.value.contentSource == PhyVlabContentSource.CACHE) {
                            PhyVlabContentSource.CACHE
                        } else {
                            PhyVlabContentSource.NETWORK
                        },
                        cachedAtEpochMillis = mutableState.value.cachedAtEpochMillis ?: refreshedAt,
                        failure = null,
                        failureDetail = null,
                    )
                    persistCache(mutableState.value.cachedAtEpochMillis ?: 0L)
                }
                is PhyVlabActivitiesResult.Failure -> publishFailure(result.reason)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            publishFailure(PhyVlabSyncFailure.NETWORK)
        }
    }

    private fun publishFailure(reason: PhyVlabSyncFailure, detail: String? = null) {
        mutableState.value = mutableState.value.copy(failure = reason, failureDetail = detail)
    }

    private fun publishDetailFailure(activity: PhyVlabActivity, reason: PhyVlabSyncFailure) {
        mutableState.value = mutableState.value.copy(
            assignmentDetail = mutableState.value.assignmentDetail
                ?: assignmentDetailsByActivity[activity.cacheKey()],
            isDetailLoading = false,
            detailFailure = reason,
        )
    }

    private fun loadCachedSnapshotIfNeeded() {
        if (cacheLoaded) return
        cacheLoaded = true
        val source = localDataSource ?: return
        val scope = cacheAccountScope ?: return
        val snapshot = runCatching { source.load(scope) }.getOrNull() ?: return
        if (snapshot.courses.isEmpty() && snapshot.activities.isEmpty() && snapshot.events.isEmpty() &&
            snapshot.assignmentDetails.isEmpty()
        ) {
            return
        }

        activitiesByCourse.clear()
        snapshot.activities.groupBy(PhyVlabActivity::courseId).forEach { (courseId, activities) ->
            activitiesByCourse[courseId] = activities
        }
        val activityEvents = snapshot.activities.flatMap { activity ->
            listOfNotNull(activity.toStartEvent(), activity.toDeadlineEvent())
        }
        assignmentDetailsByActivity.clear()
        snapshot.assignmentDetails.forEach { cached ->
            assignmentDetailsByActivity[ActivityCacheKey(cached.courseId, cached.activityId)] = cached.detail
        }
        // 先装详情再决议：缓存的提交信息也要能点亮事件。
        scheduleEvents = resolveEventSubmitted(mergeEvents(snapshot.events, activityEvents))
        val monthStart = currentBeijingMonthStartSeconds()
        val selectedCourse = snapshot.courses.firstOrNull()
        mutableState.value = mutableState.value.copy(
            courses = snapshot.courses,
            events = eventsForSelectedCourse(selectedCourse, monthStart),
            agendaEvents = scheduleEvents,
            monthLabel = monthLabelFor(monthStart),
            activities = selectedCourse?.let { activitiesByCourse[it.id].orEmpty() }.orEmpty(),
            selectedCourse = selectedCourse,
            contentSource = PhyVlabContentSource.CACHE,
            cachedAtEpochMillis = snapshot.savedAtEpochMillis.takeIf { it > 0L },
            failure = null,
            failureDetail = null,
        )
        lastRequestedMonthSeconds = monthStart
        phyVlabDebug(
            "cache loaded courses=${snapshot.courses.size} activities=${snapshot.activities.size} " +
                "events=${snapshot.events.size}",
        )
    }

    private fun persistCache(savedAtEpochMillis: Long) {
        val source = localDataSource ?: return
        val scope = cacheAccountScope ?: return
        val activities = activitiesByCourse.values
            .flatten()
            .distinctBy { activity -> activity.courseId to activity.id }
        val details = assignmentDetailsByActivity.map { (key, detail) ->
            PhyVlabCachedAssignmentDetail(
                courseId = key.courseId,
                activityId = key.activityId,
                // 缓存只用于离线阅读，不能把当前会话的上传能力带到下次启动。
                detail = detail.copy(canSubmit = false, canFinalize = false),
            )
        }
        runCatching {
            source.replace(
                scope,
                PhyVlabCacheSnapshot(
                    courses = mutableState.value.courses,
                    activities = activities,
                    events = scheduleEvents,
                    assignmentDetails = details,
                    savedAtEpochMillis = savedAtEpochMillis,
                ),
            )
        }.onFailure { error ->
            phyVlabDebug("cache save failed type=${error::class.simpleName}")
        }
    }

    private fun currentBeijingMonthStartSeconds(): Long {
        val now = nowProvider().toLocalDateTime(PHYVLAB_TIME_ZONE)
        return beijingMonthStartSeconds(now.year, now.month.ordinal + 1)
    }

    private fun clearLoading() {
        if (mutableState.value.isLoading) {
            mutableState.value = mutableState.value.copy(isLoading = false)
        }
    }

    /** 首页 agendaEvents 保留全部课程；页面列表不再展示单独的“安排”区块。 */
    private fun eventsForSelectedCourse(
        course: PhyVlabCourse?,
        monthStart: Long,
    ): List<PhyVlabEvent> {
        val visible = course?.let { selected ->
            val activityUrls = activitiesByCourse[selected.id]
                .orEmpty()
                .mapTo(mutableSetOf(), PhyVlabActivity::activityUrl)
            scheduleEvents.filter { event ->
                event.id.startsWith("activity-${selected.id}-") ||
                    event.eventUrl in activityUrls ||
                    event.title.startsWith("${selected.name} · ")
            }
        } ?: scheduleEvents
        return eventsForMonth(visible, monthStart)
    }

    /**
     * 日历事件本身不带提交状态；用活动 id 前缀或活动链接把它关联到已知活动，
     * 补上已做信号（课程页完成标记或缓存的详情提交信息任一成立即算已做）。
     * 关联不上时保持 false，不猜测。
     */
    private fun resolveEventSubmitted(events: List<PhyVlabEvent>): List<PhyVlabEvent> {
        val activities = activitiesByCourse.values.flatten()
        if (activities.isEmpty()) return events
        val submittedByUrl = activities.associate { it.activityUrl to submittedSignal(it) }
        // 活动派生事件只带了课程页标记；详情有提交信息时也要补上，所以已标记的也要重算。
        return events.map { event ->
            event.copy(submitted = findSubmittedActivity(event, activities, submittedByUrl))
        }
    }

    private fun submittedSignal(activity: PhyVlabActivity): Boolean =
        assignmentDetailsByActivity[activity.cacheKey()]?.let(::phyVlabAssignmentDetailHasSubmission) ?: activity.completed

    private fun findSubmittedActivity(
        event: PhyVlabEvent,
        activities: List<PhyVlabActivity>,
        submittedByUrl: Map<String, Boolean>,
    ): Boolean {
        // 活动派生事件 id 形如 activity-<courseId>-<activityId>-<due|start>。
        val idParts = event.id.split("-")
        if (idParts.size >= 4 && idParts[0] == "activity") {
            val courseId = idParts[1].toIntOrNull()
            val activityId = idParts[2].toIntOrNull()
            if (courseId != null && activityId != null) {
                activities.firstOrNull { it.courseId == courseId && it.id == activityId }
                    ?.let { return submittedSignal(it) }
            }
        }
        event.eventUrl?.let { url -> submittedByUrl[url]?.let { return it } }
        return false
    }

    private suspend fun establishSessionWithRecovery(): PhyVlabSessionResult {
        val initial = sessionProtocol.establishSession()
        val needsRecovery = initial == PhyVlabSessionResult.CasLoginRequired ||
            (initial is PhyVlabSessionResult.Failed &&
                initial.reason == PhyVlabRemoteFailure.SESSION_EXPIRED)
        val recovery = reauthenticate ?: return initial
        if (!needsRecovery) return initial

        phyVlabDebug("session recovery start")
        val recovered = runCatching { recovery() }.getOrDefault(false)
        phyVlabDebug("session recovery result=$recovered")
        if (!recovered) return initial
        return sessionProtocol.establishSession()
    }

    private suspend fun ensureSessionForOperation(): Boolean {
        if (sessionReady) return true
        return when (establishSessionWithRecovery()) {
            is PhyVlabSessionResult.Ready -> {
                sessionReady = true
                true
            }
            PhyVlabSessionResult.CasLoginRequired,
            is PhyVlabSessionResult.Failed,
            -> {
                sessionReady = false
                false
            }
        }
    }
}

private data class ActivityCacheKey(
    val courseId: Int,
    val activityId: Int,
)

private fun PhyVlabActivity.cacheKey(): ActivityCacheKey = ActivityCacheKey(courseId, id)

private fun PhyVlabActivity.toStartEvent(): PhyVlabEvent? = openTimestamp?.let { timestamp ->
    PhyVlabEvent(
        id = "activity-$courseId-$id-start",
        title = "$courseName · $title",
        dateText = openText.orEmpty(),
        dayTimestamp = timestamp,
        eventUrl = activityUrl,
        kind = PhyVlabEventKind.START,
        submitted = completed,
    )
}

private fun PhyVlabActivity.toDeadlineEvent(): PhyVlabEvent? = dueTimestamp?.let { timestamp ->
    PhyVlabEvent(
        id = "activity-$courseId-$id-due",
        title = "$courseName · $title",
        dateText = dueText.orEmpty(),
        dayTimestamp = timestamp,
        eventUrl = activityUrl,
        kind = PhyVlabEventKind.DEADLINE,
        submitted = completed,
    )
}

private fun mergeEvents(
    primary: List<PhyVlabEvent>,
    secondary: List<PhyVlabEvent>,
): List<PhyVlabEvent> = (primary + secondary)
    .groupBy { event ->
        "${event.kind.name}:${event.eventUrl ?: "id:${event.id}"}"
    }
    .values
    .map { duplicates ->
        // 同一事项的日历事件与活动事件可能各带一半信息；已做信号取或。
        duplicates.first().let { first ->
            if (duplicates.any(PhyVlabEvent::submitted)) first.copy(submitted = true) else first
        }
    }
    .sortedWith(compareBy<PhyVlabEvent> { it.dayTimestamp }.thenBy { it.title })

private fun eventsForMonth(events: List<PhyVlabEvent>, monthStart: Long): List<PhyVlabEvent> {
    val nextMonth = beijingMonthStartSecondsSafe(monthStart, 1) ?: return events
    return events.filter { it.dayTimestamp in monthStart until nextMonth }
        .sortedWith(compareBy<PhyVlabEvent> { it.dayTimestamp }.thenBy { it.title })
}

private fun monthLabelFor(timestamp: Long): String = kotlin.time.Instant.fromEpochSeconds(timestamp)
    .toLocalDateTime(TimeZone.of("Asia/Shanghai"))
    .let { dateTime -> "${dateTime.year}年${dateTime.month.ordinal + 1}月" }

private fun beijingMonthStartSeconds(year: Int, month: Int): Long {
    val daysBeforeMonth = when (month) {
        1 -> 0
        2 -> 31
        3 -> 59
        4 -> 90
        5 -> 120
        6 -> 151
        7 -> 181
        8 -> 212
        9 -> 243
        10 -> 273
        11 -> 304
        else -> 334
    }
    val leapDay = if (month > 2 && year.isLeapYear()) 1 else 0
    val daysSinceEpoch = (year - 1970) * 365 +
        ((year - 1) / 4 - 1969 / 4) -
        ((year - 1) / 100 - 1969 / 100) +
        ((year - 1) / 400 - 1969 / 400) +
        daysBeforeMonth +
        leapDay
    return daysSinceEpoch * 86400L - 8 * 3600L
}

private fun beijingMonthStartSecondsSafe(current: Long, direction: Int): Long? {
    val date = kotlin.time.Instant.fromEpochSeconds(current)
        .toLocalDateTime(PHYVLAB_TIME_ZONE).date
    val target = LocalDate(date.year, date.month, 1)
        .plus(direction, DateTimeUnit.MONTH)
    return beijingMonthStartSeconds(target.year, target.month.ordinal + 1)
}

private fun Int.isLeapYear(): Boolean = this % 400 == 0 || (this % 4 == 0 && this % 100 != 0)

private fun PhyVlabRemoteFailure.toUiFailure(): PhyVlabSyncFailure = when (this) {
    PhyVlabRemoteFailure.NETWORK -> PhyVlabSyncFailure.NETWORK
    PhyVlabRemoteFailure.PARSE -> PhyVlabSyncFailure.PARSE
    PhyVlabRemoteFailure.SESSION_EXPIRED -> PhyVlabSyncFailure.SESSION_EXPIRED
}

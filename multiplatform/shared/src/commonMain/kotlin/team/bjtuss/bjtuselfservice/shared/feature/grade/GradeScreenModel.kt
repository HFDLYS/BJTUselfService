package team.bjtuss.bjtuselfservice.shared.feature.grade

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeRepository
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.home.gradeChangeRecords
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder
import team.bjtuss.bjtuselfservice.shared.domain.change.recordSafely
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeInfoResult
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSortOrder
import team.bjtuss.bjtuselfservice.shared.domain.grade.calculateGradeInfo
import team.bjtuss.bjtuselfservice.shared.domain.grade.courseTypeOfGrade
import team.bjtuss.bjtuselfservice.shared.domain.grade.filterGradesBySemester
import team.bjtuss.bjtuselfservice.shared.domain.grade.filterGradesByType
import team.bjtuss.bjtuselfservice.shared.domain.grade.gradesForCalculation
import team.bjtuss.bjtuselfservice.shared.domain.grade.sortGrades
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeRecord

enum class GradeContentSource {
    CACHE,
    NETWORK,
}

@Immutable
data class GradeUiState(
    val grades: List<Grade> = emptyList(),
    val selectedGradeIds: Set<Int> = emptySet(),
    /**
     * 勾选的学期。默认在数据加载后填满全部学期；
     * 与 domain 约定对齐：传给筛选函数时「全选 / 空」都视为不过滤。
     */
    val selectedSemesters: Set<String> = emptySet(),
    /**
     * 排除的课程性质（未勾选的类别）。默认空 = 全部类别参与列表与加权。
     * 自选模式关闭时即可勾选/取消必修等胶囊，无需打开逐门勾选。
     */
    val excludedCourseTypes: Set<CourseType> = emptySet(),
    /** null = 性质映射未加载（从未同步成功），此时不应把全部课程当“其他类别”。 */
    val courseTypesByCode: Map<String, CourseType>? = null,
    /** 默认直接展示教务网页 ln+lr 行序。 */
    val sortOrder: GradeSortOrder = GradeSortOrder.ORIGINAL,
    val selectionMode: Boolean = false,
    val selectedGradeId: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val source: GradeContentSource? = null,
    val failure: GradeSyncFailure? = null,
    /** 本次刷新相对缓存新发现的变动；关闭弹窗后清空，不影响首页信息流。 */
    val pendingChangeNotice: List<HomeChangeRecord>? = null,
) {
    val semesterOptions: List<String>
        get() = grades.map(Grade::semester).filter(String::isNotBlank).distinct()

    /**
     * 交给 domain 的学期筛选：全选时传空集合（不过滤）；
     * 部分勾选传具体集合；一个都不勾时传占位，列表结果为空。
     */
    val semesterFilterForQuery: Set<String>
        get() {
            val options = semesterOptions.toSet()
            if (options.isEmpty()) return emptySet()
            if (selectedSemesters.containsAll(options)) return emptySet()
            return selectedSemesters
        }

    val visibleGrades: List<Grade>
        get() {
            // 有学期数据却一个未勾选：明确为空列表（与「全选=不过滤」区分）。
            if (semesterOptions.isNotEmpty() && selectedSemesters.isEmpty()) {
                return emptyList()
            }
            val bySemester = filterGradesBySemester(grades, semesterFilterForQuery)
            val byType = if (courseTypesByCode == null || excludedCourseTypes.isEmpty()) {
                bySemester
            } else {
                filterGradesByType(bySemester, courseTypesByCode, excludedCourseTypes)
            }
            return sortGrades(byType, sortOrder)
        }

    val gradeInfo: GradeInfoResult
        get() = calculateGradeInfo(
            gradesForCalculation(
                grades = grades,
                selectedSemesters = semesterFilterForQuery,
                isCourseSelectionMode = selectionMode,
                selectedGradeIds = selectedGradeIds,
                typeByCode = courseTypesByCode.orEmpty(),
                excludedTypes = excludedCourseTypes,
            ),
        )

    val selectedGrade: Grade?
        get() = grades.firstOrNull { it.id == selectedGradeId }

    /**
     * null = 映射未加载（未同步），课程不归属任何类别；非 null 时查不到课程号的按未知处理。
     */
    fun courseTypeOf(grade: Grade): CourseType? {
        val typeByCode = courseTypesByCode ?: return null
        return courseTypeOfGrade(grade, typeByCode)
    }

    val courseTypeCounts: Map<CourseType, Int>
        get() = if (courseTypesByCode == null) {
            emptyMap()
        } else {
            grades.groupingBy { grade -> courseTypeOf(grade) ?: CourseType.UNKNOWN }.eachCount()
        }

    /**
     * 某性质课程在自选模式下的三态：全部选中 / 部分选中 / 未选中。
     * UNKNOWN（其他类别）同样参与，避免“勾了未知课程却没有入口取消”的误导。
     */
    fun selectionStateForType(type: CourseType): CourseTypeSelectionState {
        val ofType = grades.filter { courseTypeOf(it) == type }
        if (ofType.isEmpty()) return CourseTypeSelectionState.NONE
        val selected = ofType.count { it.id in selectedGradeIds }
        return when {
            selected == 0 -> CourseTypeSelectionState.NONE
            selected == ofType.size -> CourseTypeSelectionState.ALL
            else -> CourseTypeSelectionState.PARTIAL
        }
    }

    /** 自选模式下某性质已勾选门数（用于胶囊计数 `已选/总数`）。 */
    fun selectedCountForType(type: CourseType): Int =
        grades.count { courseTypeOf(it) == type && it.id in selectedGradeIds }

    fun allSelectedForType(type: CourseType): Boolean =
        selectionStateForType(type) == CourseTypeSelectionState.ALL
}

enum class CourseTypeSelectionState {
    ALL,
    PARTIAL,
    NONE,
}

internal const val PROGRAM_ENSURE_MAX_ATTEMPTS = 3
internal const val GRADE_AUTO_SYNC_MAX_ATTEMPTS = 3
internal const val GRADE_AUTO_SYNC_RETRY_DELAY_MILLIS = 1_000L
internal const val PROGRAM_ENSURE_RETRY_DELAY_MILLIS = 700L

class GradeScreenModel(
    private val repository: GradeRepository,
    private val changeRecorder: DataChangeRecorder<Grade>? = null,
) {
    private val mutableState = MutableStateFlow(GradeUiState())
    val state: StateFlow<GradeUiState> = mutableState.asStateFlow()

    private var cacheLoaded = false
    private var networkAutoSyncStarted = false
    private var refreshInFlight = false
    private var programEnsureInFlight = false

    /**
     * @param refreshFromNetwork false 只读缓存；true 在登录成功后由 shell 触发自动同步。
     */
    suspend fun initialize(refreshFromNetwork: Boolean = true) {
        if (!cacheLoaded) {
            cacheLoaded = true
            val cached = runCatching(repository::load).getOrNull()
            if (cached != null) {
                val semesterOptions = cached.grades.map(Grade::semester).filter(String::isNotBlank).toSet()
                mutableState.value = mutableState.value.copy(
                    grades = cached.grades,
                    selectedGradeIds = cached.selectedGradeIds,
                    courseTypesByCode = cached.courseTypesByCode,
                    selectedSemesters = semesterOptions,
                    isLoading = cached.grades.isEmpty(),
                    source = if (cached.grades.isEmpty()) null else GradeContentSource.CACHE,
                    failure = null,
                )
            } else {
                mutableState.value = mutableState.value.copy(
                    isLoading = true,
                    failure = GradeSyncFailure.CACHE,
                )
            }
            if (!refreshFromNetwork) {
                mutableState.value = mutableState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
        if (refreshFromNetwork && !networkAutoSyncStarted) {
            networkAutoSyncStarted = true
            // 登录后自动同步：网络失败（含教务限流 503）再试，与课表/作业一致。
            refreshWithRetry()
        }
    }

    /**
     * 连续刷新最多 [maxAttempts] 次；只对 NETWORK 失败重试。会话失效交给应用壳恢复，
     * 解析失败重试也不会变好。
     */
    suspend fun refreshWithRetry(
        maxAttempts: Int = GRADE_AUTO_SYNC_MAX_ATTEMPTS,
        delayMillis: Long = GRADE_AUTO_SYNC_RETRY_DELAY_MILLIS,
    ) {
        require(maxAttempts >= 1)
        repeat(maxAttempts) { index ->
            refresh()
            if (mutableState.value.failure != GradeSyncFailure.NETWORK) return
            if (index < maxAttempts - 1) delay(delayMillis)
        }
    }

    suspend fun refresh() {
        if (refreshInFlight) return
        refreshInFlight = true
        val before = mutableState.value
        mutableState.value = before.copy(
            isLoading = before.grades.isEmpty(),
            isRefreshing = before.grades.isNotEmpty(),
            failure = null,
        )
        try {
            when (val result = repository.refresh()) {
                is GradeRefreshResult.Success -> {
                    changeRecorder.recordSafely(before.grades, result.snapshot.grades)
                    val notice = if (before.grades.isEmpty()) {
                        null
                    } else {
                        gradeChangeRecords(before.grades, result.snapshot.grades).ifEmpty { null }
                    }
                    applySnapshot(
                        grades = result.snapshot.grades,
                        selectedIds = result.snapshot.selectedGradeIds,
                        courseTypesByCode = result.snapshot.courseTypesByCode,
                        source = GradeContentSource.NETWORK,
                        failure = null,
                        pendingChangeNotice = notice,
                    )
                }
                is GradeRefreshResult.Failure -> applySnapshot(
                    grades = result.snapshot.grades,
                    selectedIds = result.snapshot.selectedGradeIds,
                    courseTypesByCode = result.snapshot.courseTypesByCode,
                    source = if (result.snapshot.grades.isEmpty()) null else GradeContentSource.CACHE,
                    failure = result.reason,
                )
            }
        } finally {
            refreshInFlight = false
            val current = mutableState.value
            if (current.isRefreshing || current.isLoading) {
                mutableState.value = current.copy(isRefreshing = false, isLoading = false)
            }
        }
    }

    /**
     * 映射缺失时单独补拉培养方案，不改成绩、不记变动。
     * 已有映射或正在拉取时直接返回。
     */
    suspend fun ensureProgramCourseTypes(
        maxAttempts: Int = PROGRAM_ENSURE_MAX_ATTEMPTS,
        delayMillis: Long = PROGRAM_ENSURE_RETRY_DELAY_MILLIS,
    ) {
        require(maxAttempts >= 1)
        if (mutableState.value.courseTypesByCode != null || programEnsureInFlight) return
        programEnsureInFlight = true
        try {
            repeat(maxAttempts) { index ->
                if (mutableState.value.courseTypesByCode != null) return
                val mapping = try {
                    repository.refreshProgramCourseTypes()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (mapping != null) {
                    mutableState.value = mutableState.value.copy(courseTypesByCode = mapping)
                    return
                }
                if (index < maxAttempts - 1) delay(delayMillis)
            }
        } finally {
            programEnsureInFlight = false
        }
    }

    fun dismissChangeNotice() {
        if (mutableState.value.pendingChangeNotice == null) return
        mutableState.value = mutableState.value.copy(pendingChangeNotice = null)
    }

    fun toggleSemester(semester: String) {
        val current = mutableState.value
        val next = if (semester in current.selectedSemesters) {
            current.selectedSemesters - semester
        } else {
            current.selectedSemesters + semester
        }
        mutableState.value = current.copy(selectedSemesters = next)
    }

    /** 恢复为全部学期勾选。 */
    fun clearSemesterFilter() {
        val current = mutableState.value
        mutableState.value = current.copy(selectedSemesters = current.semesterOptions.toSet())
    }

    /**
     * 切换某课程性质是否参与列表/加权（胶囊选中 = 参与）。
     * 不依赖自选模式。
     */
    fun toggleCourseTypeIncluded(type: CourseType) {
        val current = mutableState.value
        val excluded = current.excludedCourseTypes
        mutableState.value = current.copy(
            excludedCourseTypes = if (type in excluded) excluded - type else excluded + type,
        )
    }

    fun isCourseTypeIncluded(type: CourseType): Boolean =
        type !in mutableState.value.excludedCourseTypes

    fun cycleSortOrder() {
        val current = mutableState.value
        setSortOrder(
            when (current.sortOrder) {
                GradeSortOrder.ORIGINAL -> GradeSortOrder.ORIGINAL_REVERSED
                GradeSortOrder.ORIGINAL_REVERSED -> GradeSortOrder.DESCENDING
                GradeSortOrder.DESCENDING -> GradeSortOrder.ASCENDING
                GradeSortOrder.ASCENDING -> GradeSortOrder.ORIGINAL
            },
        )
    }

    fun setSortOrder(order: GradeSortOrder) {
        if (mutableState.value.sortOrder == order) return
        mutableState.value = mutableState.value.copy(sortOrder = order)
    }

    /**
     * 筛选面板：点左侧维度胶囊。已在该维度则不动方向；切维度时落到该维默认方向
     * （教务原序→正序 / 分数→从高到低）。
     */
    fun selectSortCategory(byScore: Boolean) {
        val current = mutableState.value.sortOrder
        val alreadyInCategory = if (byScore) {
            current == GradeSortOrder.ASCENDING || current == GradeSortOrder.DESCENDING
        } else {
            current == GradeSortOrder.ORIGINAL || current == GradeSortOrder.ORIGINAL_REVERSED
        }
        if (alreadyInCategory) return
        setSortOrder(
            if (byScore) GradeSortOrder.DESCENDING else GradeSortOrder.ORIGINAL,
        )
    }

    fun setSelectionMode(enabled: Boolean) {
        val current = mutableState.value
        if (current.selectionMode == enabled) return
        // 开关只控制列表勾选框与性质胶囊语义：
        // - 开启后：性质胶囊绑定 selectedGradeIds（0 门 → 全部 0/n 未选态，不会沿用「筛选全选」满色）
        // - 关闭后：性质胶囊回到 excludedCourseTypes 筛选语义
        // 不在此处改写 selectedGradeIds / excludedCourseTypes，避免误清用户已有自选。
        mutableState.value = current.copy(selectionMode = enabled)
    }

    fun toggleSelectionMode() {
        setSelectionMode(!mutableState.value.selectionMode)
    }

    fun setGradeSelected(gradeId: Int, selected: Boolean) {
        val current = mutableState.value
        val updated = if (selected) {
            current.selectedGradeIds + gradeId
        } else {
            current.selectedGradeIds - gradeId
        }
        persistSelection(current.grades, updated)
    }

    fun selectAllVisible() {
        val current = mutableState.value
        persistSelection(
            grades = current.grades,
            selectedIds = current.selectedGradeIds + current.visibleGrades.map(Grade::id),
        )
    }

    fun selectAllByType(type: CourseType) {
        val current = mutableState.value
        val idsOfType = current.grades
            .filter { current.courseTypeOf(it) == type }
            .map(Grade::id)
        if (idsOfType.isEmpty()) return
        persistSelection(
            grades = current.grades,
            selectedIds = current.selectedGradeIds + idsOfType,
        )
    }

    /**
     * 自选模式下点性质胶囊：
     * - 已全选 → 取消该类
     * - 未选 / 部分选 → 全选该类
     */
    fun toggleTypeSelection(type: CourseType) {
        when (mutableState.value.selectionStateForType(type)) {
            CourseTypeSelectionState.ALL -> deselectByType(type)
            CourseTypeSelectionState.PARTIAL,
            CourseTypeSelectionState.NONE,
            -> selectAllByType(type)
        }
    }

    fun deselectByType(type: CourseType) {
        val current = mutableState.value
        if (current.courseTypeCounts[type] == null) return
        runCatching {
            repository.clearSelectedCourseTypes(setOf(type))
        }.onSuccess { snapshot ->
            mutableState.value = current.copy(
                grades = snapshot.grades,
                selectedGradeIds = snapshot.selectedGradeIds,
                courseTypesByCode = snapshot.courseTypesByCode,
                failure = null,
            )
        }.onFailure {
            mutableState.value = current.copy(failure = GradeSyncFailure.CACHE)
        }
    }

    fun clearSelectedSemesters() {
        val current = mutableState.value
        if (current.selectedSemesters.isEmpty()) return
        runCatching {
            repository.clearSelectedSemesters(current.selectedSemesters)
        }.onSuccess { snapshot ->
            mutableState.value = current.copy(
                grades = snapshot.grades,
                selectedGradeIds = snapshot.selectedGradeIds,
                courseTypesByCode = snapshot.courseTypesByCode,
                failure = null,
            )
        }.onFailure {
            mutableState.value = current.copy(failure = GradeSyncFailure.CACHE)
        }
    }

    fun clearAllSelections() {
        val current = mutableState.value
        runCatching(repository::clearAllSelections).onSuccess { snapshot ->
            mutableState.value = current.copy(
                grades = snapshot.grades,
                selectedGradeIds = snapshot.selectedGradeIds,
                courseTypesByCode = snapshot.courseTypesByCode,
                failure = null,
            )
        }.onFailure {
            mutableState.value = current.copy(failure = GradeSyncFailure.CACHE)
        }
    }

    fun showGradeDetails(gradeId: Int) {
        mutableState.value = mutableState.value.copy(selectedGradeId = gradeId)
    }

    fun dismissGradeDetails() {
        mutableState.value = mutableState.value.copy(selectedGradeId = null)
    }

    fun dismissFailure() {
        mutableState.value = mutableState.value.copy(failure = null)
    }

    private fun persistSelection(grades: List<Grade>, selectedIds: Set<Int>) {
        val current = mutableState.value
        runCatching {
            repository.persistSelected(grades, selectedIds)
        }.onSuccess { snapshot ->
            mutableState.value = current.copy(
                grades = snapshot.grades,
                selectedGradeIds = snapshot.selectedGradeIds,
                courseTypesByCode = snapshot.courseTypesByCode,
                failure = null,
            )
        }.onFailure {
            mutableState.value = current.copy(failure = GradeSyncFailure.CACHE)
        }
    }

    private fun applySnapshot(
        grades: List<Grade>,
        selectedIds: Set<Int>,
        courseTypesByCode: Map<String, CourseType>?,
        source: GradeContentSource?,
        failure: GradeSyncFailure?,
        pendingChangeNotice: List<HomeChangeRecord>? = mutableState.value.pendingChangeNotice,
    ) {
        val current = mutableState.value
        val semesterOptions = grades.map(Grade::semester).filter(String::isNotBlank).toSet()
        // 首次或筛选结果被数据更新掏空时，默认勾选全部学期。
        val nextSemesters = (current.selectedSemesters intersect semesterOptions)
            .ifEmpty { semesterOptions }
        mutableState.value = current.copy(
            grades = grades,
            selectedGradeIds = selectedIds,
            // 内容相同就沿用当前实例：界面以它作 remember/LaunchedEffect 键，Compose 判等后不替换旧键，
            // 实例一旦换成“等值不同对象”，之后每次重组都要整表比较（iOS 真机同步期间占主线程约 1.6 秒）。
            courseTypesByCode = if (courseTypesByCode == current.courseTypesByCode) {
                current.courseTypesByCode
            } else {
                courseTypesByCode
            },
            selectedSemesters = nextSemesters,
            selectedGradeId = current.selectedGradeId?.takeIf { id -> grades.any { it.id == id } },
            isLoading = false,
            isRefreshing = false,
            source = source,
            failure = failure,
            pendingChangeNotice = pendingChangeNotice,
        )
    }
}

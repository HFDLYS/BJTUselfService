package team.bjtuss.bjtuselfservice.shared.data.grade

import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSelectionRecord
import team.bjtuss.bjtuselfservice.shared.domain.grade.gradeIdsForSelectionRecords
import team.bjtuss.bjtuselfservice.shared.domain.grade.selectionRecordsExcludingSemesters
import team.bjtuss.bjtuselfservice.shared.domain.grade.selectionRecordsExcludingTypes
import team.bjtuss.bjtuselfservice.shared.domain.grade.selectionRecordsForGradeIdsPreservingUnmatched

data class GradeSnapshot(
    val grades: List<Grade>,
    val selectedGradeIds: Set<Int>,
    /** null = 课程性质映射从未成功同步（培养方案未刷新/缓存为空/抓取失败且无旧数据）。 */
    val courseTypesByCode: Map<String, CourseType>? = null,
)

enum class GradeSyncFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
    CACHE,
}

sealed interface GradeRefreshResult {
    data class Success(val snapshot: GradeSnapshot) : GradeRefreshResult
    data class Failure(
        val snapshot: GradeSnapshot,
        val reason: GradeSyncFailure,
    ) : GradeRefreshResult
}

interface GradeLocalDataSource {
    fun grades(accountScope: String): List<Grade>
    fun selections(accountScope: String): List<GradeSelectionRecord>
    fun courseTypes(accountScope: String): Map<String, CourseType>?
    fun replaceSnapshot(
        accountScope: String,
        grades: List<Grade>,
        records: List<GradeSelectionRecord>,
        courseTypes: Map<String, CourseType>? = null,
    )
    fun replaceSelections(accountScope: String, records: List<GradeSelectionRecord>)
    fun replaceCourseTypes(accountScope: String, courseTypes: Map<String, CourseType>)
}

class CacheStoreGradeLocalDataSource(
    private val cacheStore: CacheStore,
) : GradeLocalDataSource {
    override fun grades(accountScope: String): List<Grade> = cacheStore.grades(accountScope)

    override fun selections(accountScope: String): List<GradeSelectionRecord> =
        cacheStore.gradeSelections(accountScope)

    /**
     * 培养方案正常时约 940 行；缓存表无行即从未成功同步过，返回 null 表示“未加载”，
     * 避免把全部课程误当成“其他类别”。
     */
    override fun courseTypes(accountScope: String): Map<String, CourseType>? {
        val raw = cacheStore.programCourseTypes(accountScope)
        if (raw.isEmpty()) return null
        return raw.mapNotNull { (courseId, storedText) ->
            courseTypeForStoredText(storedText)?.let { courseId to it }
        }.toMap()
    }

    override fun replaceSnapshot(
        accountScope: String,
        grades: List<Grade>,
        records: List<GradeSelectionRecord>,
        courseTypes: Map<String, CourseType>?,
    ) {
        cacheStore.replaceGradeSnapshot(
            accountScope = accountScope,
            grades = grades,
            selections = records,
            courseTypes = courseTypes?.mapNotNull { (courseId, courseType) ->
                courseType.storedText()?.let { courseId to it }
            }?.toMap(),
        )
    }

    override fun replaceSelections(accountScope: String, records: List<GradeSelectionRecord>) {
        cacheStore.replaceGradeSelections(accountScope, records)
    }

    override fun replaceCourseTypes(accountScope: String, courseTypes: Map<String, CourseType>) {
        cacheStore.replaceProgramCourseTypes(
            accountScope,
            courseTypes.mapNotNull { (courseId, courseType) ->
                courseType.storedText()?.let { courseId to it }
            }.toMap(),
        )
    }
}

interface GradeRepository {
    fun load(): GradeSnapshot
    suspend fun refresh(): GradeRefreshResult
    /** 只拉培养方案。成功写入并返回映射；失败返回 null，不改成绩缓存。 */
    suspend fun refreshProgramCourseTypes(): Map<String, CourseType>?
    fun persistSelected(grades: List<Grade>, selectedGradeIds: Set<Int>): GradeSnapshot
    fun clearSelectedSemesters(semesters: Set<String>): GradeSnapshot
    fun clearSelectedCourseTypes(courseTypes: Set<CourseType>): GradeSnapshot
    fun clearAllSelections(): GradeSnapshot
}

class DefaultGradeRepository(
    accountScope: String,
    private val local: GradeLocalDataSource,
    private val remote: GradeRemoteDataSource,
    private val programRemote: TrainingProgramRemoteDataSource,
) : GradeRepository {
    private val accountScope = accountScope.trim().also {
        require(it.isNotEmpty()) { "accountScope cannot be blank" }
    }

    override fun load(): GradeSnapshot = snapshot(
        grades = local.grades(accountScope),
        records = local.selections(accountScope),
    )

    override suspend fun refresh(): GradeRefreshResult {
        val fallback = runCatching(::load).getOrElse { GradeSnapshot(emptyList(), emptySet()) }
        val remoteGrades = try {
            remote.fetchGrades()
        } catch (error: CancellationException) {
            throw error
        } catch (error: GradeRemoteException) {
            return GradeRefreshResult.Failure(fallback, error.reason.toSyncFailure())
        } catch (_: Exception) {
            return GradeRefreshResult.Failure(fallback, GradeSyncFailure.NETWORK)
        }

        // 培养方案抓取失败仅降级：成绩照常替换，性质映射保留上一次成功的旧数据。
        val remoteCourseTypes = fetchRemoteCourseTypes()

        return try {
            val storedRecords = local.selections(accountScope)
            val temporaryGrades = remoteGrades.mapIndexed { index, grade ->
                grade.copy(id = index + 1)
            }
            val temporarySelectedIds = gradeIdsForSelectionRecords(temporaryGrades, storedRecords)
            val normalizedRecords = selectionRecordsForGradeIdsPreservingUnmatched(
                grades = temporaryGrades,
                storedRecords = storedRecords,
                selectedGradeIds = temporarySelectedIds,
            )
            local.replaceSnapshot(accountScope, remoteGrades, normalizedRecords, remoteCourseTypes)
            val persistedGrades = local.grades(accountScope)
            val selectedIds = gradeIdsForSelectionRecords(persistedGrades, normalizedRecords)
            GradeRefreshResult.Success(
                GradeSnapshot(persistedGrades, selectedIds, local.courseTypes(accountScope)),
            )
        } catch (_: Exception) {
            GradeRefreshResult.Failure(
                snapshot = runCatching(::load).getOrElse { fallback },
                reason = GradeSyncFailure.CACHE,
            )
        }
    }

    override suspend fun refreshProgramCourseTypes(): Map<String, CourseType>? {
        val remoteCourseTypes = fetchRemoteCourseTypes() ?: return null
        return try {
            local.replaceCourseTypes(accountScope, remoteCourseTypes)
            local.courseTypes(accountScope)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    override fun persistSelected(
        grades: List<Grade>,
        selectedGradeIds: Set<Int>,
    ): GradeSnapshot {
        val existing = local.selections(accountScope)
        val validIds = grades.mapTo(mutableSetOf()) { it.id }
        val selected = selectedGradeIds intersect validIds
        val records = selectionRecordsForGradeIdsPreservingUnmatched(
            grades = grades,
            storedRecords = existing,
            selectedGradeIds = selected,
        )
        local.replaceSelections(accountScope, records)
        return GradeSnapshot(grades, selected, local.courseTypes(accountScope))
    }

    override fun clearSelectedSemesters(semesters: Set<String>): GradeSnapshot {
        val grades = local.grades(accountScope)
        val records = selectionRecordsExcludingSemesters(
            records = local.selections(accountScope),
            semesters = semesters,
        )
        local.replaceSelections(accountScope, records)
        return snapshot(grades, records)
    }

    override fun clearSelectedCourseTypes(courseTypes: Set<CourseType>): GradeSnapshot {
        val grades = local.grades(accountScope)
        val records = selectionRecordsExcludingTypes(
            records = local.selections(accountScope),
            typeByCode = local.courseTypes(accountScope).orEmpty(),
            excludedTypes = courseTypes,
        )
        local.replaceSelections(accountScope, records)
        return snapshot(grades, records)
    }

    override fun clearAllSelections(): GradeSnapshot {
        local.replaceSelections(accountScope, emptyList())
        return GradeSnapshot(local.grades(accountScope), emptySet(), local.courseTypes(accountScope))
    }

    private suspend fun fetchRemoteCourseTypes(): Map<String, CourseType>? = try {
        programRemote.fetchCourseTypes()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    // 每次读快照都会从缓存重建课程性质表；内容没变就沿用上一份实例。界面层多处以它为
    // remember/LaunchedEffect 键，实例一换每次重组都要整表比较（iOS 同步期间实测占主线程）。
    private var lastCourseTypes: Map<String, CourseType>? = null

    private fun snapshot(
        grades: List<Grade>,
        records: List<GradeSelectionRecord>,
    ): GradeSnapshot {
        val loaded = local.courseTypes(accountScope)
        val courseTypes = if (loaded == lastCourseTypes) lastCourseTypes else loaded
        lastCourseTypes = courseTypes
        return GradeSnapshot(
            grades = grades,
            selectedGradeIds = gradeIdsForSelectionRecords(grades, records),
            courseTypesByCode = courseTypes,
        )
    }
}

private fun GradeRemoteFailure.toSyncFailure(): GradeSyncFailure = when (this) {
    GradeRemoteFailure.NETWORK -> GradeSyncFailure.NETWORK
    GradeRemoteFailure.SESSION_EXPIRED -> GradeSyncFailure.SESSION_EXPIRED
    GradeRemoteFailure.MALFORMED_RESPONSE -> GradeSyncFailure.MALFORMED_RESPONSE
}

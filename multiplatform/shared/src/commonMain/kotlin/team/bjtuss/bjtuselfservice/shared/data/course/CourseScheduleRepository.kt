package team.bjtuss.bjtuselfservice.shared.data.course

import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.domain.course.Course

data class CourseScheduleSnapshot(
    val courses: List<Course>,
    val currentWeek: Int,
)

enum class CourseScheduleSyncFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
    CACHE,
}

sealed interface CourseScheduleRefreshResult {
    data class Success(val snapshot: CourseScheduleSnapshot) : CourseScheduleRefreshResult
    data class Failure(
        val snapshot: CourseScheduleSnapshot,
        val reason: CourseScheduleSyncFailure,
    ) : CourseScheduleRefreshResult
}

interface CourseScheduleLocalDataSource {
    fun load(accountScope: String): CourseScheduleSnapshot
    fun replace(accountScope: String, snapshot: CourseScheduleSnapshot)
}

class CacheStoreCourseScheduleLocalDataSource(
    private val cacheStore: CacheStore,
) : CourseScheduleLocalDataSource {
    override fun load(accountScope: String): CourseScheduleSnapshot = CourseScheduleSnapshot(
        courses = cacheStore.courses(accountScope),
        currentWeek = cacheStore.courseCurrentWeek(accountScope),
    )

    override fun replace(accountScope: String, snapshot: CourseScheduleSnapshot) {
        cacheStore.replaceCourseSnapshot(accountScope, snapshot.courses, snapshot.currentWeek)
    }
}

interface CourseScheduleRepository {
    fun load(): CourseScheduleSnapshot
    suspend fun refresh(): CourseScheduleRefreshResult

    /**
     * 用按日期校准后的教学周更新缓存。旧的测试/替代仓库无需实现时保留原快照。
     */
    fun reconcileCurrentWeek(currentWeek: Int): CourseScheduleSnapshot = load()
}

class DefaultCourseScheduleRepository(
    accountScope: String,
    private val local: CourseScheduleLocalDataSource,
    private val remote: CourseScheduleRemoteDataSource,
    /** 远端数据成功写入缓存后调用（记录同步时间，供离线读取方判断新旧）。 */
    private val onSynced: () -> Unit = {},
) : CourseScheduleRepository {
    private val accountScope = accountScope.trim().also {
        require(it.isNotEmpty()) { "accountScope cannot be blank" }
    }

    override fun load(): CourseScheduleSnapshot = local.load(accountScope)

    override fun reconcileCurrentWeek(currentWeek: Int): CourseScheduleSnapshot {
        val fallback = runCatching(::load).getOrElse { CourseScheduleSnapshot(emptyList(), 0) }
        val corrected = fallback.copy(currentWeek = currentWeek)
        return runCatching {
            local.replace(accountScope, corrected)
            local.load(accountScope)
        }.getOrElse { fallback }
    }

    override suspend fun refresh(): CourseScheduleRefreshResult {
        val fallback = runCatching(::load).getOrElse { CourseScheduleSnapshot(emptyList(), 0) }
        val remoteSnapshot = try {
            remote.fetchSchedule()
        } catch (error: CancellationException) {
            throw error
        } catch (error: CourseScheduleRemoteException) {
            return CourseScheduleRefreshResult.Failure(fallback, error.reason.toSyncFailure())
        } catch (_: Exception) {
            return CourseScheduleRefreshResult.Failure(fallback, CourseScheduleSyncFailure.NETWORK)
        }

        val snapshot = CourseScheduleSnapshot(
            courses = remoteSnapshot.courses,
            currentWeek = remoteSnapshot.currentWeek,
        )
        return try {
            local.replace(accountScope, snapshot)
            runCatching(onSynced)
            CourseScheduleRefreshResult.Success(local.load(accountScope))
        } catch (_: Exception) {
            CourseScheduleRefreshResult.Failure(
                snapshot = runCatching(::load).getOrElse { fallback },
                reason = CourseScheduleSyncFailure.CACHE,
            )
        }
    }
}

private fun CourseScheduleRemoteFailure.toSyncFailure(): CourseScheduleSyncFailure = when (this) {
    CourseScheduleRemoteFailure.NETWORK -> CourseScheduleSyncFailure.NETWORK
    CourseScheduleRemoteFailure.SESSION_EXPIRED -> CourseScheduleSyncFailure.SESSION_EXPIRED
    CourseScheduleRemoteFailure.MALFORMED_RESPONSE -> CourseScheduleSyncFailure.MALFORMED_RESPONSE
}

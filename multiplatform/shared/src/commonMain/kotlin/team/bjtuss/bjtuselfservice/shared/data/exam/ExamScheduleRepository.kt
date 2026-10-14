package team.bjtuss.bjtuselfservice.shared.data.exam

import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule

data class ExamScheduleSnapshot(val exams: List<ExamSchedule>)

enum class ExamScheduleSyncFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
    CACHE,
}

sealed interface ExamScheduleRefreshResult {
    data class Success(val snapshot: ExamScheduleSnapshot) : ExamScheduleRefreshResult
    data class Failure(
        val snapshot: ExamScheduleSnapshot,
        val reason: ExamScheduleSyncFailure,
    ) : ExamScheduleRefreshResult
}

interface ExamScheduleLocalDataSource {
    fun load(accountScope: String): ExamScheduleSnapshot
    fun replace(accountScope: String, exams: List<ExamSchedule>)
}

class CacheStoreExamScheduleLocalDataSource(
    private val cacheStore: CacheStore,
) : ExamScheduleLocalDataSource {
    override fun load(accountScope: String): ExamScheduleSnapshot =
        ExamScheduleSnapshot(cacheStore.exams(accountScope))

    override fun replace(accountScope: String, exams: List<ExamSchedule>) {
        cacheStore.replaceExams(accountScope, exams)
    }
}

interface ExamScheduleRepository {
    fun load(): ExamScheduleSnapshot
    suspend fun refresh(): ExamScheduleRefreshResult
}

class DefaultExamScheduleRepository(
    accountScope: String,
    private val local: ExamScheduleLocalDataSource,
    private val remote: ExamScheduleRemoteDataSource,
    /** 远端数据成功写入缓存后调用（记录同步时间，供离线读取方判断新旧）。 */
    private val onSynced: () -> Unit = {},
) : ExamScheduleRepository {
    private val accountScope = accountScope.trim().also {
        require(it.isNotEmpty()) { "accountScope cannot be blank" }
    }

    override fun load(): ExamScheduleSnapshot = local.load(accountScope)

    override suspend fun refresh(): ExamScheduleRefreshResult {
        val fallback = runCatching(::load).getOrElse { ExamScheduleSnapshot(emptyList()) }
        val remoteExams = try {
            remote.fetchExams()
        } catch (error: CancellationException) {
            throw error
        } catch (error: ExamScheduleRemoteException) {
            return ExamScheduleRefreshResult.Failure(fallback, error.reason.toSyncFailure())
        } catch (_: Exception) {
            return ExamScheduleRefreshResult.Failure(fallback, ExamScheduleSyncFailure.NETWORK)
        }
        return try {
            local.replace(accountScope, remoteExams)
            runCatching(onSynced)
            ExamScheduleRefreshResult.Success(local.load(accountScope))
        } catch (_: Exception) {
            ExamScheduleRefreshResult.Failure(
                snapshot = runCatching(::load).getOrElse { fallback },
                reason = ExamScheduleSyncFailure.CACHE,
            )
        }
    }
}

private fun ExamScheduleRemoteFailure.toSyncFailure(): ExamScheduleSyncFailure = when (this) {
    ExamScheduleRemoteFailure.NETWORK -> ExamScheduleSyncFailure.NETWORK
    ExamScheduleRemoteFailure.SESSION_EXPIRED -> ExamScheduleSyncFailure.SESSION_EXPIRED
    ExamScheduleRemoteFailure.MALFORMED_RESPONSE -> ExamScheduleSyncFailure.MALFORMED_RESPONSE
}

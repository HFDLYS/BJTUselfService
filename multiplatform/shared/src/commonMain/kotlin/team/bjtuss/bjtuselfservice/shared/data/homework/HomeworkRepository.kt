package team.bjtuss.bjtuselfservice.shared.data.homework

import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkAttachment
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkDetail
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.domain.homework.SubmittedHomeworkAttachment

data class HomeworkSnapshot(val homework: List<Homework>)

enum class HomeworkSyncFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
    SECURE_CHANNEL_UNAVAILABLE,
    CACHE,

    /** 服务端明确拒绝了这次提交（如文件类型不支持、缺少作业 ID），与网络/解析失败区分。 */
    SUBMIT_REJECTED,
}

sealed interface HomeworkRefreshResult {
    data class Success(val snapshot: HomeworkSnapshot) : HomeworkRefreshResult
    data class Failure(
        val snapshot: HomeworkSnapshot,
        val reason: HomeworkSyncFailure,
    ) : HomeworkRefreshResult
}

sealed interface HomeworkDetailResult {
    data class Success(val detail: HomeworkDetail) : HomeworkDetailResult
    data class Failure(val reason: HomeworkSyncFailure) : HomeworkDetailResult
}

sealed interface HomeworkOperationResult<out T> {
    data class Success<T>(val value: T) : HomeworkOperationResult<T>

    data class Failure(
        val reason: HomeworkSyncFailure,
        /**
         * 服务端原文回绝原因（仅 [HomeworkSyncFailure.SUBMIT_REJECTED] 会带值）。
         * 其他失败保持 null，避免把内部解析标记带进界面。
         */
        val serverMessage: String? = null,
        /**
         * 网络类失败时的底层原因（异常类型 + 简短消息），用于让「检查网络」这类
         * 模糊文案可定位到连接超时 / 连接被拒 / 传输中断等具体环节。
         * 只包含异常类名与服务端错误文本，不含凭据、Cookie、文件名或正文。
         */
        val diagnostic: String? = null,
    ) : HomeworkOperationResult<Nothing>
}

interface HomeworkLocalDataSource {
    fun load(accountScope: String): HomeworkSnapshot
    fun replace(accountScope: String, homework: List<Homework>)
}

class CacheStoreHomeworkLocalDataSource(
    private val cacheStore: CacheStore,
) : HomeworkLocalDataSource {
    override fun load(accountScope: String): HomeworkSnapshot =
        HomeworkSnapshot(cacheStore.homework(accountScope))

    override fun replace(accountScope: String, homework: List<Homework>) {
        cacheStore.replaceHomework(accountScope, homework)
    }
}

interface HomeworkRepository {
    fun load(): HomeworkSnapshot
    suspend fun refresh(): HomeworkRefreshResult
    suspend fun loadDetail(homework: Homework): HomeworkDetailResult
    suspend fun loadSubmittedAttachments(
        homework: Homework,
    ): HomeworkOperationResult<List<SubmittedHomeworkAttachment>>
    suspend fun downloadTeacherAttachment(
        homeworkId: Int,
        attachment: HomeworkAttachment,
    ): HomeworkOperationResult<HomeworkFileContent>
    suspend fun downloadSubmittedAttachment(
        attachment: SubmittedHomeworkAttachment,
    ): HomeworkOperationResult<HomeworkFileContent>
    suspend fun submitHomework(
        homework: Homework,
        content: String,
        files: List<HomeworkFileContent>,
    ): HomeworkOperationResult<Unit>
    fun attachmentDownloadUrl(homeworkId: Int, attachmentId: Int): String
}

class DefaultHomeworkRepository(
    accountScope: String,
    private val local: HomeworkLocalDataSource,
    private val remote: HomeworkRemoteDataSource,
    /** 远端数据成功写入缓存后调用（记录同步时间，供离线读取方判断新旧）。 */
    private val onSynced: () -> Unit = {},
) : HomeworkRepository {
    private val accountScope = accountScope.trim().also {
        require(it.isNotEmpty()) { "accountScope cannot be blank" }
    }

    override fun load(): HomeworkSnapshot = local.load(accountScope)

    override suspend fun refresh(): HomeworkRefreshResult {
        val fallback = runCatching(::load).getOrElse { HomeworkSnapshot(emptyList()) }
        val remoteHomework = try {
            remote.fetchHomework()
        } catch (error: CancellationException) {
            throw error
        } catch (error: HomeworkRemoteException) {
            return HomeworkRefreshResult.Failure(fallback, error.reason.toSyncFailure())
        } catch (_: Exception) {
            return HomeworkRefreshResult.Failure(fallback, HomeworkSyncFailure.NETWORK)
        }
        return try {
            local.replace(accountScope, remoteHomework)
            runCatching(onSynced)
            HomeworkRefreshResult.Success(local.load(accountScope))
        } catch (_: Exception) {
            HomeworkRefreshResult.Failure(
                snapshot = runCatching(::load).getOrElse { fallback },
                reason = HomeworkSyncFailure.CACHE,
            )
        }
    }

    override suspend fun loadDetail(homework: Homework): HomeworkDetailResult = try {
        HomeworkDetailResult.Success(remote.fetchDetail(homework))
    } catch (error: CancellationException) {
        throw error
    } catch (error: HomeworkRemoteException) {
        HomeworkDetailResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        HomeworkDetailResult.Failure(HomeworkSyncFailure.NETWORK)
    }

    override suspend fun loadSubmittedAttachments(
        homework: Homework,
    ): HomeworkOperationResult<List<SubmittedHomeworkAttachment>> = remoteOperation {
        remote.fetchSubmittedAttachments(homework)
    }

    override suspend fun downloadTeacherAttachment(
        homeworkId: Int,
        attachment: HomeworkAttachment,
    ): HomeworkOperationResult<HomeworkFileContent> = remoteOperation {
        remote.downloadTeacherAttachment(homeworkId, attachment)
    }

    override suspend fun downloadSubmittedAttachment(
        attachment: SubmittedHomeworkAttachment,
    ): HomeworkOperationResult<HomeworkFileContent> = remoteOperation {
        remote.downloadSubmittedAttachment(attachment)
    }

    override suspend fun submitHomework(
        homework: Homework,
        content: String,
        files: List<HomeworkFileContent>,
    ): HomeworkOperationResult<Unit> {
        if (files.isEmpty()) return HomeworkOperationResult.Failure(HomeworkSyncFailure.MALFORMED_RESPONSE)
        return try {
            remote.submitHomework(homework, content, files)
            HomeworkOperationResult.Success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HomeworkRemoteException) {
            val reason = error.reason.toSyncFailure()
            HomeworkOperationResult.Failure(
                reason = reason,
                serverMessage = error.message?.takeIf {
                    reason == HomeworkSyncFailure.SUBMIT_REJECTED
                },
                diagnostic = error.diagnosticChain(),
            )
        } catch (error: Exception) {
            HomeworkOperationResult.Failure(
                reason = HomeworkSyncFailure.NETWORK,
                diagnostic = error.diagnosticChain(),
            )
        }
    }
    override fun attachmentDownloadUrl(homeworkId: Int, attachmentId: Int): String =
        remote.attachmentDownloadUrl(homeworkId, attachmentId)

    private suspend fun <T> remoteOperation(block: suspend () -> T): HomeworkOperationResult<T> = try {
        HomeworkOperationResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: HomeworkRemoteException) {
        HomeworkOperationResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        HomeworkOperationResult.Failure(HomeworkSyncFailure.NETWORK)
    }
}

private fun HomeworkRemoteFailure.toSyncFailure(): HomeworkSyncFailure = when (this) {
    HomeworkRemoteFailure.NETWORK -> HomeworkSyncFailure.NETWORK
    HomeworkRemoteFailure.SESSION_EXPIRED -> HomeworkSyncFailure.SESSION_EXPIRED
    HomeworkRemoteFailure.MALFORMED_RESPONSE -> HomeworkSyncFailure.MALFORMED_RESPONSE
    HomeworkRemoteFailure.SECURE_CHANNEL_UNAVAILABLE -> HomeworkSyncFailure.SECURE_CHANNEL_UNAVAILABLE
    HomeworkRemoteFailure.SUBMIT_REJECTED -> HomeworkSyncFailure.SUBMIT_REJECTED
}

/**
 * 只取异常链的类名与简短消息，用来把「请检查网络」定位到具体环节
 * （如 ConnectTimeoutException / SocketTimeoutException / SSLException）。
 * 不含 URL、Cookie、文件名或请求正文。
 */
private fun Throwable.diagnosticChain(): String? {
    val parts = mutableListOf<String>()
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 4) {
        val label = current::class.simpleName.orEmpty()
        val message = current.message
            ?.replace(Regex("[\\r\\n\\t]"), " ")
            ?.take(120)
            ?.takeIf(String::isNotBlank)
        parts += if (message == null) label else "$label: $message"
        current = current.cause
        depth += 1
    }
    return parts.filter(String::isNotBlank).joinToString(" <- ").takeIf(String::isNotEmpty)
}

package team.bjtuss.bjtuselfservice.shared.feature.citel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import team.bjtuss.bjtuselfservice.shared.feature.assignment.AssignmentFilters
import team.bjtuss.bjtuselfservice.shared.feature.assignment.AssignmentFilterStore
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.security.CredentialVault
import team.bjtuss.bjtuselfservice.shared.data.moodle.*
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent

data class CitelState(
    val enabled: Boolean = false,
    val username: String = "",
    val remember: Boolean = false,
    val tasks: List<CitelTask> = emptyList(),
    val refreshing: Boolean = false,
    val fromCache: Boolean = false,
    val message: String? = null,
    val failed: Boolean = false,
    val lastSync: Long? = null,
    val selectedTask: CitelTask? = null,
    val submission: MoodleAssignmentStatus? = null,
    val submissionBusy: Boolean = false,
    val submissionMessage: String? = null,
    val programmingOptions: CitelProgrammingOptions? = null,
    val programmingResult: CitelProgrammingResult? = null,
    val programmingPollingPaused: Boolean = false,
    val submissionRevision: Long = 0,
    val filters: AssignmentFilters = AssignmentFilters(),
    val configured: Boolean = false,
    val problems: Map<Int, CitelCachedProblem> = emptyMap(),
    val problemBusy: Boolean = false,
    val problemMessage: String? = null,
)

class CitelModel(
    private val scope: String,
    private val cache: CacheStore,
    private val vault: CredentialVault?,
    private val remote: CitelDataSource,
) {
    private val filterStore = AssignmentFilterStore(cache, scope, "citel")
    private val mutableState = MutableStateFlow(CitelState(username = scope, filters = filterStore.load()))
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var initialized = false
    private var credentials: Credentials? = null
    private var appSyncStarted = false
    private var lastAppEntryGeneration = -1L
    private var appSyncRunning = false

    suspend fun refreshForAppEntry(generation: Long) {
        if (appSyncRunning || (appSyncStarted && generation <= lastAppEntryGeneration)) return
        initialize()
        if (!state.value.enabled || appSyncRunning || (appSyncStarted && generation <= lastAppEntryGeneration)) return
        appSyncRunning = true
        try {
            refresh()
            appSyncStarted = true
            lastAppEntryGeneration = generation
        } finally { appSyncRunning = false }
    }

    suspend fun initialize() = mutex.withLock {
        if (initialized) return@withLock
        val remember = cache.metadata(scope, "citel.remember") == "true"
        credentials = try { if (remember) vault?.load() else null }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        val username = cache.metadata(scope, "citel.username")?.takeIf { it.isNotBlank() } ?: scope
        val tasks = runCatching { json.decodeFromString<List<CitelTask>>(cache.metadata(scope, "citel.tasks").orEmpty()) }.getOrDefault(emptyList())
        val lastSync = cache.metadata(scope, "citel.lastSync")?.toLongOrNull()
        mutableState.value = CitelState(cache.metadata(scope, "citel.enabled") == "true" && credentials?.isValid == true, username, remember,
            tasks, fromCache = lastSync != null || tasks.isNotEmpty(), lastSync = lastSync, filters = state.value.filters,
            configured = credentials?.isValid == true,
            problems = tasks.mapNotNull { task -> cache.citelProblem(scope, username, task.id)?.let { task.id to it } }.toMap())
        initialized = true
    }

    fun updateFilters(filters: AssignmentFilters) {
        filterStore.save(filters)
        mutableState.value = state.value.copy(filters = filters)
    }

    /** 密码留空表示继续使用同一 CITEL 账号的凭据；新账号必须输入密码。 */
    suspend fun setEnabled(enabled: Boolean) {
        initialize()
        mutex.withLock {
            if (enabled && !state.value.configured) return@withLock
            if (state.value.enabled == enabled) return@withLock
            cache.putMetadata(scope, "citel.enabled", enabled.toString())
            mutableState.value = state.value.copy(enabled = enabled, failed = false, message = null)
        }
    }

    /** 密码留空沿用同账号凭据，账号设置不再改变应用设置中的功能总开关。 */
    suspend fun saveAccount(username: String, password: String, remember: Boolean) {
        initialize()
        configure(username, password, remember, state.value.enabled)
    }

    /** Account setup validates/syncs even when the feature switch is off, without enabling it. */
    suspend fun saveAccountAndSync(username: String, password: String): Boolean {
        saveAccount(username, password, remember = true)
        if (state.value.failed || !state.value.configured) return false
        if (!state.value.enabled) refresh(includeDisabled = true)
        return !state.value.failed && state.value.configured
    }

    suspend fun clearConfiguration(): Boolean {
        initialize()
        return mutex.withLock {
            try { vault?.clear() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutableState.value = mutableState.value.copy(failed = true, message = "密码清除失败，请重试。")
                return@withLock false
            }
            credentials = null
            cache.putMetadata(scope, "citel.enabled", "false")
            cache.putMetadata(scope, "citel.remember", "false")
            mutableState.value = mutableState.value.copy(
                enabled = false,
                remember = false,
                configured = false,
                failed = false,
                message = "已清除密码并关闭 CITEL，账号已保留。",
            )
            true
        }
    }

    suspend fun configure(username: String, password: String, remember: Boolean, enabled: Boolean) {
        initialize()
        val saved = mutex.withLock {
            try {
                val account = username.trim()
                val next = if (password.isNotEmpty()) Credentials(account, password)
                    else credentials?.takeIf { it.username == account }
                if (next == null || !next.isValid) throw CitelFailure("请填写 CITEL 账号和密码。")
                if (remember && next != null) (vault ?: throw CitelFailure("当前平台安全存储不可用，请取消记住密码。" )).save(next)
                else vault?.clear()
                val changed = account != mutableState.value.username
                if (changed) {
                    cache.putMetadata(scope, "citel.tasks", "[]")
                    cache.putMetadata(scope, "citel.lastSync", "")
                }
                cache.putMetadata(scope, "citel.username", account)
                cache.putMetadata(scope, "citel.remember", remember.toString())
                cache.putMetadata(scope, "citel.enabled", enabled.toString())
                credentials = next
                mutableState.value = mutableState.value.copy(username = account, remember = remember, enabled = enabled,
                    tasks = if (changed) emptyList() else mutableState.value.tasks,
                    fromCache = if (changed) false else mutableState.value.fromCache,
                    lastSync = if (changed) null else mutableState.value.lastSync, failed = false, message = null,
                    configured = next.isValid, problems = if (changed) emptyMap() else mutableState.value.problems)
                true
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                mutableState.value = mutableState.value.copy(message = (e as? CitelFailure)?.userMessage ?: "CITEL 配置保存失败，请重试。", failed = true)
                false
            }
        }
        if (saved && enabled) refresh()
    }

    suspend fun refresh() {
        refresh(includeDisabled = false)
    }

    private suspend fun refresh(includeDisabled: Boolean) {
        initialize()
        mutex.withLock {
            if (!includeDisabled && !mutableState.value.enabled) return@withLock
            team.bjtuss.bjtuselfservice.shared.logging.AppLog.d("CitelSync", "refresh started")
            mutableState.value = mutableState.value.copy(refreshing = true, message = null, failed = false)
            try {
                val login = credentials ?: throw CitelFailure("请在应用设置的 CITEL 账号设置中输入密码；未记住密码时，重启后需要重新输入。")
                val tasks = withTimeout(90_000) { remote.fetch(login) }
                val time = Clock.System.now().epochSeconds
                cache.putMetadata(scope, "citel.tasks", json.encodeToString(tasks))
                cache.putMetadata(scope, "citel.lastSync", time.toString())
                // 内容没变就沿用旧列表实例：首页/聚合作业以它作 remember 键，等值的新实例会让每次重组都逐项比较。
                val current = mutableState.value
                mutableState.value = current.copy(tasks = if (tasks == current.tasks) current.tasks else tasks,
                    fromCache = false, lastSync = time, message = "已同步 ${tasks.size} 项作业。")
            } catch (e: TimeoutCancellationException) {
                fail("CITEL 连接超时，保留上次同步结果。")
            } catch (e: CancellationException) {
                fail("CITEL 同步已中断，正显示上次同步结果。")
                throw e
            } catch (e: Exception) { fail((e as? CitelFailure)?.userMessage ?: "CITEL 暂时无法连接，保留上次同步结果。")
            } finally {
                mutableState.value = mutableState.value.copy(refreshing = false)
                team.bjtuss.bjtuselfservice.shared.logging.AppLog.d("CitelSync", "refresh finished failed=${state.value.failed} cached=${state.value.fromCache} tasks=${state.value.tasks.size}")
            }
        }
    }

    private fun fail(message: String) {
        mutableState.value = mutableState.value.copy(fromCache = mutableState.value.lastSync != null || mutableState.value.tasks.isNotEmpty(), failed = true, message = message)
    }
    fun dismissTask() { if (!state.value.submissionBusy) mutableState.value = state.value.copy(selectedTask = null, submission = null, programmingResult = null, programmingOptions = null, submissionMessage = null) }
    fun showTask(task: CitelTask) { mutableState.value = state.value.copy(problemMessage = null, selectedTask = task, submission = null, programmingResult = null, programmingPollingPaused = false, programmingOptions = null, submissionMessage = null) }
    suspend fun selectTask(task: CitelTask) {
        initialize()
        mutex.withLock {
            mutableState.value = state.value.copy(selectedTask = task, submission = null, programmingResult = null, programmingPollingPaused = false, programmingOptions = null, submissionMessage = null, submissionBusy = true)
            try {
                if (!task.programming) mutableState.value = state.value.copy(submission = remote.submission(loginCredentials(), task))
                else {
                    var result = remote.programmingStatus(loginCredentials(), task)
                    val options = if (!result.accepted && !result.testing) remote.programmingOptions(loginCredentials(), task) else null
                    if (state.value.selectedTask?.id != task.id) return@withLock
                    if (options?.alreadyAccepted == true) result = result.copy(status = "AC: Accepted", accepted = true)
                    applyProgrammingResult(task, result)
                    mutableState.value = state.value.copy(programmingOptions = options?.takeUnless { it.alreadyAccepted })
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutableState.value = state.value.copy(submissionMessage = message(e))
            } finally { mutableState.value = state.value.copy(submissionBusy = false) }
        }
    }
    private fun applyProgrammingResult(task: CitelTask, result: CitelProgrammingResult) {
        val updated = task.copy(status = if (result.testing && result.status.isBlank()) "WJ: Waiting" else result.status,
            submitted = result.accepted)
        val tasks = state.value.tasks.map { if (it.id == task.id) updated else it }
        cache.putMetadata(scope, "citel.tasks", json.encodeToString(tasks))
        val selected = state.value.selectedTask?.id == task.id
        mutableState.value = state.value.copy(selectedTask = if (selected) updated else state.value.selectedTask, tasks = tasks,
            programmingResult = if (selected) result else state.value.programmingResult)
    }

    /** Polls only the selected task; never sends a submission POST. False pauses automatic refresh after errors. */
    suspend fun refreshProgrammingResult(taskId: Int): Boolean {
        if (state.value.submissionBusy) return true
        return mutex.withLock {
            val task = state.value.selectedTask?.takeIf { it.id == taskId && it.programming } ?: return@withLock false
            try {
                val result = remote.programmingStatus(loginCredentials(), task)
                if (state.value.selectedTask?.id != taskId) return@withLock false
                applyProgrammingResult(task, result)
                if (!result.accepted && !result.testing && state.value.programmingOptions == null) {
                    val options = remote.programmingOptions(loginCredentials(), task)
                    if (state.value.selectedTask?.id != taskId) return@withLock false
                    if (options.alreadyAccepted) applyProgrammingResult(task, result.copy(status = "AC: Accepted", accepted = true))
                    else mutableState.value = state.value.copy(programmingOptions = options)
                }
                mutableState.value = state.value.copy(submissionMessage = null, programmingPollingPaused = false)
                true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (state.value.selectedTask?.id == taskId) mutableState.value = state.value.copy(programmingPollingPaused = true,
                    submissionMessage = "评测状态刷新失败，自动刷新已暂停，请手动刷新重试。")
                false
            }
        }
    }
    suspend fun saveFiles(added: List<HomeworkFileContent>, removed: Set<String>) {
        if (state.value.submissionBusy) return
        mutex.withLock {
            val task = state.value.selectedTask ?: return@withLock
            mutableState.value = state.value.copy(submissionBusy = true, submissionMessage = null)
            try {
                val result = remote.saveFiles(loginCredentials(), task, added, removed)
                val updated = task.copy(submitted = result.submitted, status = result.status)
                val tasks = state.value.tasks.map { if (it.id == task.id) updated else it }
                cache.putMetadata(scope, "citel.tasks", json.encodeToString(tasks))
                mutableState.value = state.value.copy(tasks = tasks, selectedTask = updated, submission = result,
                    submissionRevision = state.value.submissionRevision + 1,
                    submissionMessage = "文件已保存，无需额外提交；平台允许编辑期间可以继续修改。")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutableState.value = state.value.copy(submissionMessage = message(e))
            } finally { mutableState.value = state.value.copy(submissionBusy = false) }
        }
    }
    /** Deliberately separate from selectTask and ordinary synchronization. */
    suspend fun fetchProblem() {
        if (state.value.problemBusy) return
        initialize()
        mutex.withLock {
            val task = state.value.selectedTask?.takeIf { it.programming } ?: return@withLock
            mutableState.value = state.value.copy(problemBusy = true, problemMessage = null)
            try {
                val problem = withTimeout(180_000) { remote.problem(loginCredentials(), task, withResults = true) }
                val cached = CitelCachedProblem(state.value.username, task.id, Clock.System.now().epochSeconds, problem)
                cache.saveCitelProblem(scope, cached)
                mutableState.value = state.value.copy(problems = state.value.problems + (task.id to cached),
                    problemMessage = "题目全文与用例已缓存，可离线复制或导出。")
            } catch (e: TimeoutCancellationException) {
                mutableState.value = state.value.copy(problemMessage = "获取全文超时，保留已缓存的题面。")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutableState.value = state.value.copy(problemMessage = (e as? CitelFailure)?.userMessage ?: "获取全文失败，保留已缓存的题面。")
            } finally { mutableState.value = state.value.copy(problemBusy = false) }
        }
    }

    private fun loginCredentials(): Credentials = credentials ?: throw CitelFailure("请先设置 CITEL 账号和密码。")
    suspend fun submitProgramming(files: List<HomeworkFileContent>, language: String) {
        if (state.value.submissionBusy) return
        mutex.withLock {
            val task = state.value.selectedTask ?: return@withLock
            if (!task.programming || state.value.programmingResult?.accepted == true || isCitelAccepted(task.status)) {
                mutableState.value = state.value.copy(submissionMessage = "已通过（AC），无需再次提交。")
                return@withLock
            }
            if (state.value.programmingResult?.testing == true) return@withLock
            if (files.size != 1) { mutableState.value = state.value.copy(submissionMessage = "每次请选择一个代码文件。"); return@withLock }
            mutableState.value = state.value.copy(submissionBusy = true, submissionMessage = null)
            try {
                val result = remote.submitProgramming(loginCredentials(), task, files.single(), language)
                applyProgrammingResult(task, result)
                mutableState.value = state.value.copy(
                    submissionRevision = state.value.submissionRevision + 1,
                    submissionMessage = "代码提交记录已增加，评测结果：${result.status.ifBlank { "等待评测" }}")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutableState.value = state.value.copy(submissionMessage = message(e))
            } finally { mutableState.value = state.value.copy(submissionBusy = false) }
        }
    }
    private fun message(error: Exception): String = when (error) {
        is CitelFailure -> error.userMessage
        is MoodleAssignmentFailure -> error.detail
        else -> "操作结果未确认，请刷新核对后重试。"
    }
}

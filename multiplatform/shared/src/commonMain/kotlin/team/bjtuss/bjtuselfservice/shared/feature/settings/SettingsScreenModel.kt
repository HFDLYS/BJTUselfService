package team.bjtuss.bjtuselfservice.shared.feature.settings

import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.update.AppUpdateChecker

sealed interface OfflineCacheActionState {
    data object Idle : OfflineCacheActionState
    data object Clearing : OfflineCacheActionState
    data object Cleared : OfflineCacheActionState
    data object Failed : OfflineCacheActionState
}

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState

    /** 已拿到最新 Release（无论有无更新）；[hasUpdate] 供 UI 决定是否突出显示。 */
    data class Done(
        val release: AppUpdateChecker.Release,
        val hasUpdate: Boolean,
    ) : UpdateCheckState

    data object Failed : UpdateCheckState
}

data class SettingsUiState(
    val preferences: AppPreferences,
    val cacheAction: OfflineCacheActionState = OfflineCacheActionState.Idle,
    val dataWipeAction: OfflineCacheActionState = OfflineCacheActionState.Idle,
    val saveFailed: Boolean = false,
    val updateCheck: UpdateCheckState = UpdateCheckState.Idle,
)

/**
 * 设置状态只保存普通偏好；凭据与 Cookie 仍由现有安全退出路径管理。
 * 浅深色始终跟随系统；[AppPreferences.dynamicColor] 仅 Android 设置页暴露开关。
 */
class SettingsScreenModel(
    initialPreferences: AppPreferences,
    private val persistPreferences: (AppPreferences) -> Boolean,
    private val clearAccountCache: () -> Boolean,
    private val wipeAllLocalData: suspend () -> Boolean,
    private val checkLatestRelease: suspend () -> AppUpdateChecker.Result,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val prepareUninstall: (suspend () -> Boolean)? = null,
) {
    private val mutableState = MutableStateFlow(SettingsUiState(initialPreferences.copy(
        autoSyncGrades = true, autoSyncHomework = true, autoSyncSchedule = true, autoSyncExams = true,
    )))
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()
    private var automaticUpdateChecked = false
    private var postponedUntilMillis = initialPreferences.updatePostponedUntilMillis

    /** 1.8.3 合并弹窗覆盖新版导航、CITEL、作业聚合三项，确认一次即全部记为已读。 */
    fun acknowledgeRelease183Guide() = updatePreferences {
        copy(redesignGuideAcknowledged = true, citelGuideAcknowledged = true, assignmentAggregateGuideAcknowledged = true)
    }
    fun acknowledgeRelease184Guide() = updatePreferences { copy(release184GuideAcknowledged = true) }

    fun setBottomNavigationItem(routeId: String, enabled: Boolean) {
        val current = mutableState.value.preferences
        val candidates = team.bjtuss.bjtuselfservice.shared.feature.shell.bottomNavigationCandidates(current.isPhyVlabEnabled, current.isPhysicsLabEnabled, current.isCitelEnabled, current.aggregateAssignments)
        if (candidates.none { it.name == routeId }) return
        val selected = team.bjtuss.bjtuselfservice.shared.feature.shell.bottomNavSections(current)
            .filter { it in candidates }.map { it.name }
        if (enabled && routeId !in selected && selected.size >= 4) return
        updatePreferences {
            copy(bottomNavigationItems = if (enabled) (selected + routeId).distinct() else selected - routeId)
        }
    }

    fun setBottomNavigationOrder(routeIds: List<String>): Boolean {
        val current = mutableState.value.preferences
        val candidates = team.bjtuss.bjtuselfservice.shared.feature.shell.bottomNavigationCandidates(current.isPhyVlabEnabled, current.isPhysicsLabEnabled, current.isCitelEnabled, current.aggregateAssignments)
        val selected = team.bjtuss.bjtuselfservice.shared.feature.shell.bottomNavSections(current)
            .filter { it in candidates }.map { it.name }
        // Reordering cannot add, remove, duplicate, or move the fixed entries.
        if (routeIds.size != selected.size || routeIds.toSet() != selected.toSet()) return false
        updatePreferences { copy(bottomNavigationItems = routeIds.toList()) }
        return !mutableState.value.saveFailed
    }

    fun moveBottomNavigationItem(routeId: String, direction: Int): Boolean {
        if (direction != -1 && direction != 1) return false
        val current = mutableState.value.preferences
        val selected = team.bjtuss.bjtuselfservice.shared.feature.shell.bottomNavSections(current)
            .filter { it.name != "HOME" && it.name != "MORE" }.map { it.name }.toMutableList()
        val index = selected.indexOf(routeId)
        val target = index + direction
        if (index < 0 || target !in selected.indices) return false
        selected.add(target, selected.removeAt(index))
        updatePreferences { copy(bottomNavigationItems = selected) }
        return !mutableState.value.saveFailed
    }

    fun setAutoSyncPhyVlab(enabled: Boolean) = updatePreferences {
        copy(autoSyncPhyVlab = enabled)
    }

    fun setShowPhyVlabInBottomNav(enabled: Boolean) = updatePreferences {
        copy(showPhyVlabInBottomNav = enabled)
    }

    fun setPhysicsLabEnabled(enabled: Boolean) = updatePreferences { copy(physicsLabEnabled = enabled) }
    fun setAggregateAssignments(enabled: Boolean) = updatePreferences { copy(aggregateAssignments = enabled) }
    fun setCitelEnabled(enabled: Boolean) = updatePreferences { copy(citelEnabled = enabled) }
    fun setStaggeredSecondSlot(enabled: Boolean) = updatePreferences { copy(staggeredSecondSlot = enabled) }

    fun setAgentGradesAllowed(allowed: Boolean) = updatePreferences { copy(agentGradesAllowed = allowed) }

    fun setPhyVlabEnabled(enabled: Boolean) = updatePreferences {
        copy(
            autoSyncPhyVlab = enabled,
            showPhyVlabInBottomNav = enabled,
        )
    }

    /** Android Material You 动态取色；其它平台设置页不展示，即使写入也无视觉效果。 */
    fun setDynamicColor(enabled: Boolean) = updatePreferences {
        copy(dynamicColor = enabled)
    }

    suspend fun clearOfflineCache() {
        if (mutableState.value.cacheAction == OfflineCacheActionState.Clearing) return
        mutableState.value = mutableState.value.copy(cacheAction = OfflineCacheActionState.Clearing)
        mutableState.value = mutableState.value.copy(
            cacheAction = if (runCatching(clearAccountCache).getOrDefault(false)) {
                OfflineCacheActionState.Cleared
            } else {
                OfflineCacheActionState.Failed
            },
        )
    }

    /**
     * 清除全部本地数据：所有账号的离线缓存、应用设置与系统安全存储中的登录信息。
     * 供桌面端卸载前的兜底清理（macOS 删除 .app 不会清 Application Support/Keychain）。
     */
    suspend fun clearAllLocalData() { runDataWipe(wipeAllLocalData) }

    suspend fun prepareForUninstall() { runDataWipe(prepareUninstall ?: return) }

    private suspend fun runDataWipe(action: suspend () -> Boolean) {
        if (mutableState.value.dataWipeAction == OfflineCacheActionState.Clearing) return
        mutableState.value = mutableState.value.copy(dataWipeAction = OfflineCacheActionState.Clearing)
        val wiped = try {
            action()
        } catch (_: Exception) {
            false
        }
        mutableState.value = mutableState.value.copy(
            dataWipeAction = if (wiped) OfflineCacheActionState.Cleared else OfflineCacheActionState.Failed,
            // 全量清除后持久化偏好已为空，回到默认值，避免界面继续显示旧设置。
            preferences = if (wiped) AppPreferences() else mutableState.value.preferences,
        )
    }

    fun dismissFeedback() {
        mutableState.value = mutableState.value.copy(
            cacheAction = OfflineCacheActionState.Idle,
            dataWipeAction = OfflineCacheActionState.Idle,
            saveFailed = false,
        )
    }

    /**
     * 触发更新检测；进行中不重复发起。结果写入 [SettingsUiState.updateCheck]。
     *
     * @param silentOnMiss 自动检测（进主界面后）为 true：无更新或失败时回到 Idle 不打扰用户；
     * 手动点「检查更新」为 false：始终弹结果（已最新/失败也明确告知）。
     */
    suspend fun checkForUpdate(silentOnMiss: Boolean = false) {
        if (mutableState.value.updateCheck == UpdateCheckState.Checking) return
        if (silentOnMiss) {
            if (automaticUpdateChecked || !mutableState.value.preferences.checkUpdate || nowMillis() < postponedUntilMillis) return
            automaticUpdateChecked = true
        }
        mutableState.value = mutableState.value.copy(updateCheck = UpdateCheckState.Checking)
        mutableState.value = mutableState.value.copy(
            updateCheck = when (val result = runCatching { checkLatestRelease() }.getOrNull()) {
                is AppUpdateChecker.Result.Success -> {
                    val hasUpdate = AppUpdateChecker.isNewer(result.release)
                    if ((!silentOnMiss || nowMillis() >= postponedUntilMillis) && (hasUpdate || !silentOnMiss)) {
                        UpdateCheckState.Done(release = result.release, hasUpdate = hasUpdate)
                    } else {
                        UpdateCheckState.Idle
                    }
                }
                else -> if (silentOnMiss) UpdateCheckState.Idle else UpdateCheckState.Failed
            },
        )
    }

    fun postponeUpdate() {
        if ((mutableState.value.updateCheck as? UpdateCheckState.Done)?.hasUpdate == true) {
            postponedUntilMillis = nowMillis() + 24L * 60 * 60 * 1000
            updatePreferences { copy(updatePostponedUntilMillis = postponedUntilMillis) }
        }
        dismissUpdateCheck()
    }

    /** 关闭更新结果（弹窗/提示），回到 Idle。 */
    fun dismissUpdateCheck() {
        mutableState.value = mutableState.value.copy(updateCheck = UpdateCheckState.Idle)
    }

    private fun updatePreferences(transform: AppPreferences.() -> AppPreferences) {
        val updated = mutableState.value.preferences.transform()
        val saved = runCatching { persistPreferences(updated) }.getOrDefault(false)
        mutableState.value = mutableState.value.copy(
            preferences = if (saved) updated else mutableState.value.preferences,
            saveFailed = !saved,
        )
    }
}

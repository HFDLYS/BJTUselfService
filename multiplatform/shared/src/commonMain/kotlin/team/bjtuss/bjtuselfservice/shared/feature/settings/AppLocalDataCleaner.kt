package team.bjtuss.bjtuselfservice.shared.feature.settings

import team.bjtuss.bjtuselfservice.shared.cache.CacheStore

/** Shared entry point for settings' full wipe and desktop uninstall preparation. */
interface AppLocalDataCleaner {
    suspend fun clearLocalData(cache: CacheStore, removeAgent: Boolean = false): Boolean
}
expect fun platformLocalDataCleaner(): AppLocalDataCleaner?

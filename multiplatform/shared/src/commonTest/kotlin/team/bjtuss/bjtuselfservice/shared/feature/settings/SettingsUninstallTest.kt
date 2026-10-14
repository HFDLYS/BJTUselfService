package team.bjtuss.bjtuselfservice.shared.feature.settings

import kotlinx.coroutines.runBlocking
import kotlin.test.*
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.update.AppUpdateChecker

class SettingsUninstallTest {
    @Test fun uninstallPreparationUsesDedicatedCallbackAndReportsFailureWithoutResettingPreferences() = runBlocking {
        var fullWipe = false
        var allowed = false
        val model = SettingsScreenModel(AppPreferences(agentGradesAllowed = true), { true }, { true },
            wipeAllLocalData = { fullWipe = true; true }, checkLatestRelease = { AppUpdateChecker.Result.Unavailable },
            prepareUninstall = { allowed })
        model.prepareForUninstall()
        assertFalse(fullWipe)
        assertEquals(OfflineCacheActionState.Failed, model.state.value.dataWipeAction)
        assertTrue(model.state.value.preferences.agentGradesAllowed)
        allowed = true
        model.prepareForUninstall()
        assertEquals(OfflineCacheActionState.Cleared, model.state.value.dataWipeAction)
        assertFalse(model.state.value.preferences.agentGradesAllowed)
    }

    @Test fun unavailableUninstallPreparationDoesNotFallBackToDestructiveFullWipe() = runBlocking {
        val model = SettingsScreenModel(AppPreferences(), { true }, { true },
            wipeAllLocalData = { error("must not wipe") }, checkLatestRelease = { AppUpdateChecker.Result.Unavailable })
        model.prepareForUninstall()
        assertEquals(OfflineCacheActionState.Idle,model.state.value.dataWipeAction)
    }
}

package team.bjtuss.bjtuselfservice.shared.feature.settings

import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.update.AppUpdateChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsScreenModelTest {
    @Test fun citelSwitchControlsItsEntryAndKeepsOtherFeatureSwitches() {
        var stored = AppPreferences(physicsLabEnabled = true, citelEnabled = true, bottomNavigationItems = listOf("CITEL"), aggregateAssignments = false)
        val model = model(initialPreferences = stored, persistPreferences = { stored = it; true })
        model.setCitelEnabled(false)
        assertFalse(stored.isCitelEnabled)
        assertTrue(stored.isPhysicsLabEnabled)
        assertTrue(stored.isPhyVlabEnabled)
        assertTrue(team.bjtuss.bjtuselfservice.shared.feature.shell.bottomNavSections(stored).none { it.name == "CITEL" })
        assertTrue(team.bjtuss.bjtuselfservice.shared.feature.shell.applicationSections(stored).none { it.name == "CITEL" })
        model.setCitelEnabled(true)
        assertTrue(team.bjtuss.bjtuselfservice.shared.feature.shell.bottomNavSections(stored).any { it.name == "CITEL" })
    }
    @Test
    fun release183ConfirmationRequiresSuccessfulSaveAndKeepsNavigation() {
        var canSave = false
        var stored = AppPreferences(bottomNavigationItems = listOf("MAILBOX", "EXAMS"))
        val model = model(initialPreferences = stored, persistPreferences = {
            if (canSave) stored = it
            canSave
        })
        assertTrue(model.state.value.preferences.release183GuidePending)
        model.acknowledgeRelease183Guide()
        assertTrue(model.state.value.preferences.release183GuidePending)
        assertTrue(model.state.value.saveFailed)
        canSave = true
        model.acknowledgeRelease183Guide()
        assertTrue(stored.redesignGuideAcknowledged)
        assertTrue(stored.citelGuideAcknowledged)
        assertTrue(stored.assignmentAggregateGuideAcknowledged)
        assertFalse(stored.release183GuidePending)
        assertFalse(model.state.value.saveFailed)
        assertEquals(listOf("MAILBOX", "EXAMS"), stored.bottomNavigationItems)
        assertFalse(stored.release184GuideAcknowledged)
        model.setDynamicColor(false)
        assertTrue(stored.redesignGuideAcknowledged)
        model.acknowledgeRelease184Guide()
        assertTrue(stored.release184GuideAcknowledged)
    }

    @Test
    fun release183GuideStaysPendingUntilAllOldGuidesAcknowledged() {
        assertFalse(AppPreferences(redesignGuideAcknowledged = true, citelGuideAcknowledged = true,
            assignmentAggregateGuideAcknowledged = true).release183GuidePending)
        assertTrue(AppPreferences(redesignGuideAcknowledged = true, citelGuideAcknowledged = true).release183GuidePending)
        assertTrue(AppPreferences(citelGuideAcknowledged = true, assignmentAggregateGuideAcknowledged = true).release183GuidePending)
    }

    private fun model(
        initialPreferences: AppPreferences = AppPreferences(),
        nowMillis: () -> Long = { 1_000L },
        persistPreferences: (AppPreferences) -> Boolean = { true },
        clearAccountCache: () -> Boolean = { true },
        clearAllLocalData: () -> Boolean = { true },
        checkLatestRelease: suspend () -> AppUpdateChecker.Result = {
            AppUpdateChecker.Result.Unavailable
        },
    ) = SettingsScreenModel(
        initialPreferences = initialPreferences,
        nowMillis = nowMillis,
        persistPreferences = persistPreferences,
        clearAccountCache = clearAccountCache,
        wipeAllLocalData = clearAllLocalData,
        checkLatestRelease = checkLatestRelease,
    )

    @Test
    fun physicsLabSwitchPersistsAndControlsPinningIndependently() {
        val saved = mutableListOf<AppPreferences>()
        val model = model(initialPreferences = AppPreferences(bottomNavigationItems = emptyList()),
            persistPreferences = { saved += it; true })
        model.setPhysicsLabEnabled(true)
        model.setBottomNavigationItem("PHYSICS_LAB", true)
        assertTrue(saved.last().isPhysicsLabEnabled)
        assertTrue(model.state.value.preferences.isPhyVlabEnabled)
        assertEquals(listOf("PHYSICS_LAB"), model.state.value.preferences.bottomNavigationItems)
        model.setPhysicsLabEnabled(false)
        assertFalse(model.state.value.preferences.isPhysicsLabEnabled)
        assertTrue(model.state.value.preferences.isPhyVlabEnabled)
        model.setPhysicsLabEnabled(true)
        assertEquals(listOf("PHYSICS_LAB"), model.state.value.preferences.bottomNavigationItems)
    }

    @Test
    fun physicsLabSwitchKeepsPreviousStateWhenSavingFails() {
        val model = model(initialPreferences = AppPreferences(physicsLabEnabled = true), persistPreferences = { false })
        model.setPhysicsLabEnabled(false)
        assertTrue(model.state.value.preferences.isPhysicsLabEnabled)
        assertTrue(model.state.value.saveFailed)
    }

    @Test
    fun freshPreferencesEnableEveryAutomaticSync() {
        val preferences = AppPreferences()

        assertTrue(preferences.autoSyncGrades)
        assertTrue(preferences.autoSyncHomework)
        assertTrue(preferences.autoSyncSchedule)
        assertTrue(preferences.autoSyncExams)
        assertTrue(preferences.autoSyncPhyVlab)
        assertTrue(preferences.showPhyVlabInBottomNav)
        assertTrue(preferences.isPhyVlabEnabled)
        assertTrue(preferences.aggregateAssignments)
        assertFalse(preferences.citelGuideAcknowledged)
        assertFalse(preferences.assignmentAggregateGuideAcknowledged)
    }

    @Test
    fun previousCoreSyncOptOutIsAlwaysEnabledOnRestore() {
        val model = model(initialPreferences = AppPreferences(
            autoSyncGrades = false, autoSyncHomework = false, autoSyncSchedule = false, autoSyncExams = false,
        ))
        assertTrue(model.state.value.preferences.autoSyncGrades)
        assertTrue(model.state.value.preferences.autoSyncHomework)
        assertTrue(model.state.value.preferences.autoSyncSchedule)
        assertTrue(model.state.value.preferences.autoSyncExams)
    }

    @Test
    fun dynamicColorTogglePersists() {
        val saved = mutableListOf<AppPreferences>()
        val model = model(
            initialPreferences = AppPreferences(dynamicColor = true),
            persistPreferences = { saved += it; true },
        )

        model.setDynamicColor(false)

        assertEquals(1, saved.size)
        assertFalse(model.state.value.preferences.dynamicColor)
        assertFalse(model.state.value.saveFailed)
    }

    @Test
    fun physicalOnlineMasterToggleControlsSyncAndBottomNav() {
        val saved = mutableListOf<AppPreferences>()
        val model = model(
            initialPreferences = AppPreferences(showPhyVlabInBottomNav = true),
            persistPreferences = { saved += it; true },
        )

        model.setPhyVlabEnabled(false)

        assertEquals(1, saved.size)
        assertFalse(model.state.value.preferences.autoSyncPhyVlab)
        assertFalse(model.state.value.preferences.showPhyVlabInBottomNav)
        assertFalse(model.state.value.preferences.isPhyVlabEnabled)
        assertFalse(model.state.value.saveFailed)
    }

    @Test
    fun failedOptionalPhysicsSyncSaveKeepsPreviousValueAndReportsFailure() {
        val model = model(persistPreferences = { false })

        model.setAutoSyncPhyVlab(false)

        assertTrue(model.state.value.preferences.autoSyncPhyVlab)
        assertTrue(model.state.value.saveFailed)
    }

    @Test
    fun successfulCacheClearReportsSuccessAndCanBeDismissed() {
        runBlocking {
            var calls = 0
            val model = model(clearAccountCache = {
                calls += 1
                true
            })

            model.clearOfflineCache()

            assertEquals(1, calls)
            assertIs<OfflineCacheActionState.Cleared>(model.state.value.cacheAction)
            model.dismissFeedback()
            assertIs<OfflineCacheActionState.Idle>(model.state.value.cacheAction)
        }
    }

    @Test
    fun cacheClearExceptionReportsFailure() {
        runBlocking {
            val model = model(clearAccountCache = { error("database") })

            model.clearOfflineCache()

            assertIs<OfflineCacheActionState.Failed>(model.state.value.cacheAction)
        }
    }

    @Test
    fun successfulFullWipeClearsDataAndResetsDisplayedPreferences() {
        runBlocking {
            var calls = 0
            val model = model(
                initialPreferences = AppPreferences(autoSyncGrades = false, dynamicColor = true),
                clearAllLocalData = {
                    calls += 1
                    true
                },
            )

            model.clearAllLocalData()

            assertEquals(1, calls)
            assertIs<OfflineCacheActionState.Cleared>(model.state.value.dataWipeAction)
            // 全量清除后持久化偏好为空，界面回默认值。
            assertEquals(AppPreferences(), model.state.value.preferences)
            model.dismissFeedback()
            assertIs<OfflineCacheActionState.Idle>(model.state.value.dataWipeAction)
        }
    }

    @Test
    fun fullWipeFailureKeepsPreviousPreferencesAndReportsFailure() {
        runBlocking {
            val model = model(
                initialPreferences = AppPreferences(autoSyncGrades = false),
                clearAllLocalData = { false },
            )

            model.clearAllLocalData()

            assertIs<OfflineCacheActionState.Failed>(model.state.value.dataWipeAction)
            assertTrue(model.state.value.preferences.autoSyncGrades)
        }
    }

    @Test
    fun newerRemoteReleaseReportsUpdateAndCanBeDismissed() {
        runBlocking {
            val newer = AppUpdateChecker.Release(
                tagName = "v9.9.9-KMP",
                body = "更新说明",
                htmlUrl = "https://github.com/${AppUpdateChecker.REPO}/releases/tag/v9.9.9-KMP",
            )
            val model = model(
                checkLatestRelease = { AppUpdateChecker.Result.Success(newer) },
            )

            model.checkForUpdate()

            val done = assertIs<UpdateCheckState.Done>(model.state.value.updateCheck)
            assertTrue(done.hasUpdate)
            assertEquals(newer, done.release)

            model.dismissUpdateCheck()
            assertIs<UpdateCheckState.Idle>(model.state.value.updateCheck)
        }
    }

    @Test
    fun sameVersionRemoteReleaseReportsNoUpdate() {
        runBlocking {
            val model = model(
                checkLatestRelease = {
                    AppUpdateChecker.Result.Success(
                        AppUpdateChecker.Release(
                            tagName = AppUpdateChecker.CURRENT_VERSION,
                            htmlUrl = "https://github.com/${AppUpdateChecker.REPO}/releases",
                        ),
                    )
                },
            )

            model.checkForUpdate()

            val done = assertIs<UpdateCheckState.Done>(model.state.value.updateCheck)
            assertFalse(done.hasUpdate)
        }
    }

    @Test
    fun unavailableOrThrowingUpdateCheckReportsFailure() {
        runBlocking {
            val unavailable = model(
                checkLatestRelease = { AppUpdateChecker.Result.Unavailable },
            )
            unavailable.checkForUpdate()
            assertIs<UpdateCheckState.Failed>(unavailable.state.value.updateCheck)

            val throwing = model(
                checkLatestRelease = { error("network") },
            )
            throwing.checkForUpdate()
            assertIs<UpdateCheckState.Failed>(throwing.state.value.updateCheck)
        }
    }

    @Test
    fun silentAutoCheckStaysIdleWithoutUpdateButStillSurfacesNewerRelease() {
        runBlocking {
            // 自动检测（silentOnMiss=true）：无更新 → 回 Idle，不打扰用户。
            val upToDate = model(
                checkLatestRelease = {
                    AppUpdateChecker.Result.Success(
                        AppUpdateChecker.Release(
                            tagName = AppUpdateChecker.CURRENT_VERSION,
                            htmlUrl = "https://github.com/${AppUpdateChecker.REPO}/releases",
                        ),
                    )
                },
            )
            upToDate.checkForUpdate(silentOnMiss = true)
            assertIs<UpdateCheckState.Idle>(upToDate.state.value.updateCheck)

            // 失败同样静默。
            val failed = model(
                checkLatestRelease = { AppUpdateChecker.Result.Unavailable },
            )
            failed.checkForUpdate(silentOnMiss = true)
            assertIs<UpdateCheckState.Idle>(failed.state.value.updateCheck)

            // 但有新版本时仍然弹。
            val newer = model(
                checkLatestRelease = {
                    AppUpdateChecker.Result.Success(
                        AppUpdateChecker.Release(
                            tagName = "v9.9.9-KMP",
                            htmlUrl = "https://github.com/${AppUpdateChecker.REPO}/releases/tag/v9.9.9-KMP",
                        ),
                    )
                },
            )
            newer.checkForUpdate(silentOnMiss = true)
            val done = assertIs<UpdateCheckState.Done>(newer.state.value.updateCheck)
            assertTrue(done.hasUpdate)
        }
    }
    @Test
    fun postponeSurvivesRecreationAndExpiresAtExactly24Hours(): Unit = runBlocking {
        var now = 1000L
        var stored = AppPreferences()
        var checks = 0
        val fetch: suspend () -> AppUpdateChecker.Result = {
            checks++
            AppUpdateChecker.Result.Success(AppUpdateChecker.Release(tagName = "v9.9.9", htmlUrl = "https://example.com/release"))
        }
        val first = model(nowMillis = { now }, persistPreferences = { stored = it; true }, checkLatestRelease = fetch)
        first.checkForUpdate(silentOnMiss = true)
        first.postponeUpdate()
        val until = 1000L + 24L * 60 * 60 * 1000
        assertEquals(until, stored.updatePostponedUntilMillis)
        now = until - 1
        val restored = model(initialPreferences = stored, nowMillis = { now }, checkLatestRelease = fetch)
        restored.checkForUpdate(silentOnMiss = true)
        assertEquals(1, checks)
        assertIs<UpdateCheckState.Idle>(restored.state.value.updateCheck)
        now = until
        restored.checkForUpdate(silentOnMiss = true)
        assertEquals(2, checks)
        assertIs<UpdateCheckState.Done>(restored.state.value.updateCheck)
    }

    @Test
    fun pageRecreationDoesNotRepeatAutomaticCheckAndManualCheckRemainsAvailable(): Unit = runBlocking {
        var calls = 0
        val model = model(checkLatestRelease = {
            calls++
            AppUpdateChecker.Result.Success(AppUpdateChecker.Release(tagName = "v9.9.9", htmlUrl = "https://example.com/release"))
        })
        model.checkForUpdate(silentOnMiss = true)
        model.postponeUpdate()
        repeat(6) { model.checkForUpdate(silentOnMiss = true) }
        assertEquals(1, calls)
        model.checkForUpdate()
        assertEquals(2, calls)
        assertIs<UpdateCheckState.Done>(model.state.value.updateCheck)
    }

    @Test
    fun bottomNavigationSelectionIsLimitedAndDoesNotDisableUnpinnedPhysics() {
        val model = model(initialPreferences = AppPreferences(aggregateAssignments = false))
        model.setBottomNavigationItem("MAILBOX", true)
        assertEquals(null, model.state.value.preferences.bottomNavigationItems)
        model.setBottomNavigationItem("SCHEDULE", false)
        model.setBottomNavigationItem("MAILBOX", true)
        assertEquals(listOf("GRADES", "HOMEWORK", "PHYVLAB", "MAILBOX"), model.state.value.preferences.bottomNavigationItems)
        model.setBottomNavigationItem("PHYVLAB", false)
        assertTrue(model.state.value.preferences.isPhyVlabEnabled)
        listOf("GRADES", "HOMEWORK", "MAILBOX").forEach { model.setBottomNavigationItem(it, false) }
        assertEquals(emptyList(), model.state.value.preferences.bottomNavigationItems)
        model.setBottomNavigationItem("HOME", false)
        assertEquals(emptyList(), model.state.value.preferences.bottomNavigationItems)
    }
    @Test
    fun reorderingPersistsOnlySelectedItemsAndKeepsBounds() {
        var stored = AppPreferences(aggregateAssignments = false)
        val model = model(initialPreferences = stored, persistPreferences = { stored = it; true })
        assertTrue(model.moveBottomNavigationItem("GRADES", -1))
        assertEquals(listOf("GRADES", "SCHEDULE", "HOMEWORK", "PHYVLAB"), stored.bottomNavigationItems)
        assertFalse(model.moveBottomNavigationItem("GRADES", -1))
        assertFalse(model.moveBottomNavigationItem("HOME", 1))
        assertFalse(model.moveBottomNavigationItem("MAILBOX", 1))
        assertTrue(model.moveBottomNavigationItem("HOMEWORK", 1))
        assertEquals(listOf("GRADES", "SCHEDULE", "PHYVLAB", "HOMEWORK"), stored.bottomNavigationItems)
        val restored = model(initialPreferences = stored)
        assertEquals(stored.bottomNavigationItems, restored.state.value.preferences.bottomNavigationItems)
    }

    @Test
    fun dragOrderAndMembershipSurviveReopeningSettings() {
        var stored = AppPreferences(aggregateAssignments = false)
        val first = model(initialPreferences = stored, persistPreferences = { stored = it; true })
        first.setBottomNavigationItem("SCHEDULE", false)
        first.setBottomNavigationItem("MAILBOX", true)
        assertTrue(first.setBottomNavigationOrder(listOf("MAILBOX", "PHYVLAB", "GRADES", "HOMEWORK")))
        val reopened = model(initialPreferences = stored)
        assertEquals(listOf("MAILBOX", "PHYVLAB", "GRADES", "HOMEWORK"), reopened.state.value.preferences.bottomNavigationItems)
    }

    @Test
    fun dragOrderRejectsChangedMembershipAndRollsBackFailedSave() {
        var saves = 0
        val model = model(initialPreferences = AppPreferences(aggregateAssignments = false), persistPreferences = { saves++; false })
        assertFalse(model.setBottomNavigationOrder(listOf("HOME", "SCHEDULE", "GRADES", "HOMEWORK")))
        assertFalse(model.setBottomNavigationOrder(listOf("SCHEDULE", "GRADES", "HOMEWORK", "HOMEWORK", "PHYVLAB")))
        assertEquals(0, saves)
        assertFalse(model.setBottomNavigationOrder(listOf("PHYVLAB", "HOMEWORK", "GRADES", "SCHEDULE")))
        assertEquals(1, saves)
        assertEquals(null, model.state.value.preferences.bottomNavigationItems)
        assertTrue(model.state.value.saveFailed)
    }

}

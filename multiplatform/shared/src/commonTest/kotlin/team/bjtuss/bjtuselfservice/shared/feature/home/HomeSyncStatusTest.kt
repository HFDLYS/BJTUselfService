package team.bjtuss.bjtuselfservice.shared.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.feature.shell.buildHomeSyncItems

class HomeSyncStatusTest {
    @Test fun citelStartupStateTracksScheduledWork() {
        fun item(busy: Boolean = false) = buildHomeSyncItems(
            isLoggingIn = false, homeBusy = false, homeFailed = false, homeReady = true,
            gradeBusy = false, gradeFailed = false, gradeReady = true,
            homeworkBusy = false, homeworkFailed = false, homeworkReady = true,
            examBusy = false, examFailed = false, examReady = true,
            courseBusy = false, courseFailed = false, courseReady = true,
            phyVlabEnabled = false, phyVlabBusy = false, phyVlabFailed = false, phyVlabReady = false,
            citelEnabled = true, citelBusy = busy,
        ).single { it.title == "CITEL 作业" }
        assertEquals("等待同步", item().detail)
        assertEquals(HomeSyncItemState.SYNCING, item(true).state)
        assertEquals("正在读取作业折扣与截止时间", item(true).detail)
    }

    @Test fun physicsItemsSitTogetherAndAllNeedCampusNetwork() {
        val items = buildHomeSyncItems(
            isLoggingIn = false, homeBusy = false, homeFailed = false, homeReady = true,
            gradeBusy = false, gradeFailed = false, gradeReady = true,
            homeworkBusy = false, homeworkFailed = false, homeworkReady = true,
            examBusy = false, examFailed = false, examReady = true,
            courseBusy = false, courseFailed = false, courseReady = true,
            phyVlabEnabled = true, phyVlabBusy = true, phyVlabFailed = false, phyVlabReady = false,
            physicsLabEnabled = true, physicsLabBusy = true,
            physicsDemoEnabled = true, physicsDemoBusy = true,
            citelEnabled = true,
        )
        val titles = items.map { it.title }
        val physics = listOf("物理实验", "演示实验", "物理在线")
        val start = titles.indexOf("物理实验")
        assertEquals(physics, titles.subList(start, start + 3))
        assertTrue(items.filter { it.title in physics }.all { "需要校园网" in it.detail })
    }

    @Test
    fun loginTakesPriorityOverSyncInDialogTitle() {
        assertEquals(
            "登录中",
            homeSyncDialogTitle(isLoggingIn = true, isSyncing = true, hasFailures = true),
        )
    }

    @Test
    fun syncTakesPriorityOverFailureWhileOtherModulesAreStillRunning() {
        assertEquals(
            "同步中",
            homeSyncDialogTitle(isLoggingIn = false, isSyncing = true, hasFailures = true),
        )
    }

    @Test
    fun failureIsShownOnlyAfterAllActiveSyncWorkStops() {
        assertEquals(
            "同步失败",
            homeSyncDialogTitle(isLoggingIn = false, isSyncing = false, hasFailures = true),
        )
    }
}

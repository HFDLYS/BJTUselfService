package team.bjtuss.bjtuselfservice.shared.data.home

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.*
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.domain.change.*
import team.bjtuss.bjtuselfservice.shared.domain.home.*
import team.bjtuss.bjtuselfservice.shared.feature.citel.*
import team.bjtuss.bjtuselfservice.shared.feature.phydemo.*
import team.bjtuss.bjtuselfservice.shared.feature.physicslab.*
import team.bjtuss.bjtuselfservice.shared.network.*
import team.bjtuss.bjtuselfservice.shared.security.CredentialVault

class IndependentAccountChangeFeedTest {
    @Test fun pendingAccountSwitchBaselineSurvivesRestartForCitelAndLabs() = runBlocking<Unit> {
        val cache = store()
        try {
            val feed = CacheStoreHomeChangeFeedRepository("fixture", cache)
            val vault = Vault()
            var fail = false
            var tasks = listOf(CitelTask(1, 1, "测试课程", "账号 A 作业", "", false))
            val remote = object : CitelDataSource {
                override suspend fun fetch(credentials: Credentials): List<CitelTask> {
                    if (fail) throw CitelFailure("fixture failure")
                    return tasks
                }
            }
            val citel = CitelModel("fixture", cache, vault, remote, citelChangeRecorder(feed))
            assertTrue(citel.saveAccountAndSync("account-a", "fixture"))
            citel.setEnabled(true)
            fail = true
            assertFalse(citel.saveAccountAndSync("account-b", "fixture"))
            tasks = listOf(tasks.single().copy(id = 2, title = "账号 B 作业"))
            fail = false
            val restoredCitel = CitelModel("fixture", cache, vault, remote, citelChangeRecorder(feed))
            restoredCitel.refresh()
            assertFalse(restoredCitel.state.value.failed)
            assertEquals(2, restoredCitel.state.value.tasks.single().id)
            assertTrue(feed.records.value.isEmpty())

            val labVault = Vault()
            var body = results("账号 A 实验")
            val transport = transport { request ->
                if (fail) throw SchoolNetworkException("fixture failure")
                if (request.url == PHYSICS_LAB_RESULTS) body else loginForm
            }
            val labs = PhysicsLabModel("fixture", cache, labVault, PhysicsLabRemote(transport), physicsLabChangeRecorder(feed))
            labs.configure("account-a", "fixture", true)
            fail = true
            labs.configure("account-b", "fixture", true)
            assertTrue(labs.state.value.failed)
            body = results("账号 B 实验")
            fail = false
            val restoredLabs = PhysicsLabModel("fixture", cache, labVault, PhysicsLabRemote(transport), physicsLabChangeRecorder(feed))
            restoredLabs.refresh()
            assertFalse(restoredLabs.state.value.failed)
            assertEquals("账号 B 实验", restoredLabs.state.value.labs.single().name)
            assertTrue(feed.records.value.isEmpty())
            assertEquals("false", cache.metadata("fixture", "physicslab.changeBaselinePending"))
        } finally { cache.close() }
    }

    @Test fun citelRefreshTracksFieldsAndProgrammingResultButNotFailuresOrAccountSwitches() = runBlocking<Unit> {
        val cache = store()
        try {
            val feed = CacheStoreHomeChangeFeedRepository("fixture", cache)
            val task = CitelTask(1, 2, "测试课程", "测试编程", "", true, dueTime = 1_800_000_000, status = "WA")
            var tasks = listOf(task)
            var fail = false
            val remote = object : CitelDataSource {
                override suspend fun fetch(credentials: Credentials): List<CitelTask> {
                    if (fail) throw CitelFailure("fixture failure")
                    return tasks
                }
                override suspend fun programmingStatus(credentials: Credentials, task: CitelTask) =
                    CitelProgrammingResult("AC: Accepted", true, emptyList(), false)
            }
            val model = CitelModel("fixture", cache, Vault(), remote, citelChangeRecorder(feed))
            assertTrue(model.saveAccountAndSync("account-a", "fixture"))
            assertTrue(feed.records.value.isEmpty())
            tasks = listOf(task.copy(dueTime = task.dueTime!! + 3600, discount = 0.8))
            model.refreshForAppEntry(1) // disabled features do not refresh
            model.setEnabled(true)
            model.refresh()
            val record = feed.records.value.single()
            assertEquals(HomeChangeDomain.CITEL, record.domain)
            assertEquals(setOf("截止时间", "倍率"), record.visibleFields().map { it.label }.toSet())
            model.selectTask(model.state.value.tasks.single())
            assertTrue(feed.records.value.last().visibleFields().any { it.label == "完成情况" && it.after == "已完成" })
            val count = feed.records.value.size
            fail = true
            model.refresh()
            assertTrue(model.state.value.failed)
            assertEquals(count, feed.records.value.size)
            fail = false
            tasks = listOf(task.copy(id = 7, title = "其他账号作业"))
            assertTrue(model.saveAccountAndSync("account-b", "fixture"))
            assertEquals(count, feed.records.value.size)
            tasks = emptyList()
            model.refresh()
            assertEquals(DataChangeKind.DELETED, feed.records.value.last().kind)
            assertEquals("其他账号作业", feed.records.value.last().title)
            assertEquals(feed.records.value, CacheStoreHomeChangeFeedRepository("fixture", cache).records.value)
            assertTrue(CacheStoreHomeChangeFeedRepository("different-scope", cache).records.value.isEmpty())
        } finally { cache.close() }
    }

    @Test fun labRefreshAndLocalDurationChangesReachFeedWhileFailureKeepsCachedResults() = runBlocking<Unit> {
        val cache = store()
        try {
            val feed = CacheStoreHomeChangeFeedRepository("fixture", cache)
            var body = results("测试实验")
            var fail = false
            val transport = transport { request ->
                if (fail) throw SchoolNetworkException("fixture failure")
                if (request.url == PHYSICS_LAB_RESULTS) body else loginForm
            }
            val model = PhysicsLabModel("fixture", cache, Vault(), PhysicsLabRemote(transport), physicsLabChangeRecorder(feed))
            model.configure("account-a", "fixture", true)
            assertFalse(model.state.value.failed)
            assertTrue(feed.records.value.isEmpty())
            body = body.replace("7312A", "7312B").replace("2026/11/26", "2026/11/27")
            model.refresh()
            assertEquals(DataChangeKind.MODIFIED, feed.records.value.single().kind)
            assertEquals(setOf("日期", "地点"), feed.records.value.single().visibleFields().map { it.label }.toSet())
            model.setTwoWeeks(model.state.value.labs.single(), true)
            assertTrue(feed.records.value.last().visibleFields().any { it.label == "周数" && it.after == "2" })
            val before = feed.records.value
            model.refresh() // local override survives and must not produce another change
            assertEquals(before, feed.records.value)
            fail = true
            model.refresh()
            assertTrue(model.state.value.failed)
            assertEquals(before, feed.records.value)
            fail = false
            body = "<table id='a_ASPxGridViewCourseList_DXMainTable'></table>"
            model.refresh()
            assertEquals(DataChangeKind.DELETED, feed.records.value.last().kind)
            assertTrue(model.state.value.labs.isEmpty())
        } finally { cache.close() }
    }

    @Test fun demoConfigureAndRefreshTrackChangesAndRebaselineANewIndependentAccount() = runBlocking<Unit> {
        val cache = store()
        try {
            val feed = CacheStoreHomeChangeFeedRepository("fixture", cache)
            var body = demoBookingFixture()
            var fail = false
            val transport = transport { request ->
                if (fail) throw SchoolNetworkException("fixture failure")
                if (request.url.endsWith("appointment/")) body else demoLoginFixture
            }
            val model = PhysicsDemoModel("fixture", cache, Vault(), PhysicsDemoRemote(transport), physicsDemoChangeRecorder(feed))
            assertTrue(model.configure("account-a", "fixture"))
            assertTrue(feed.records.value.isEmpty())
            body = demoBookingFixture(date = "2026-10-21")
            assertTrue(model.configure("account-a", ""))
            assertEquals(listOf("日期"), feed.records.value.single().visibleFields().map { it.label })
            fail = true
            model.refresh()
            assertTrue(model.state.value.failed)
            assertEquals(1, feed.records.value.size)
            fail = false
            body = demoBookingFixture("近代与综合")
            assertTrue(model.configure("account-b", "fixture"))
            assertEquals(1, feed.records.value.size)
            body = demoEmptyFixture
            model.refresh()
            assertEquals(DataChangeKind.DELETED, feed.records.value.last().kind)
            assertEquals(HomeChangeDomain.PHYSICS_DEMO, feed.records.value.last().domain)
            body = demoBookingFixture()
            model.refresh()
            assertEquals(DataChangeKind.ADDED, feed.records.value.last().kind)
            val records = feed.records.value
            model.refresh()
            assertEquals(records, feed.records.value)
            assertTrue(feed.clear(HomeChangeDomain.PHYSICS_DEMO))
            assertTrue(feed.records.value.isEmpty())
        } finally { cache.close() }
    }

    @Test fun recordersIgnoreTransportOnlyChangesAndNewDomainsSurviveCodec() = runBlocking<Unit> {
        val cache = store()
        try {
            val feed = CacheStoreHomeChangeFeedRepository("fixture", cache)
            val task = CitelTask(1, 1, "测试课程", "测试作业", "old", false)
            citelChangeRecorder(feed).record(listOf(task), listOf(task.copy(url = "new")))
            assertTrue(feed.records.value.isEmpty())
            val lab = PhysicsLab(LocalDate(2026, 10, 21), 1, "测试实验", "测试地点", "测试教师", 1)
            physicsLabChangeRecorder(feed).record(listOf(lab), listOf(lab.copy(teacher = "另一位教师")))
            val demo = PhysicsDemoBooking("光学", LocalDate(2026, 10, 21), 1)
            physicsDemoChangeRecorder(feed).record(listOf(demo), listOf(demo.copy(period = 2)))
            citelChangeRecorder(feed).record(listOf(task), listOf(task.copy(submitted = true, grade = "90")))
            assertEquals(setOf(HomeChangeDomain.CITEL, HomeChangeDomain.PHYSICS_LAB, HomeChangeDomain.PHYSICS_DEMO), feed.records.value.map { it.domain }.toSet())
            val snapshot = HomeChangeFeedSnapshot(HomeChangeDomain.entries.toSet(), feed.records.value)
            assertEquals(snapshot, decodeHomeChangeFeed(encodeHomeChangeFeed(snapshot)))
        } finally { cache.close() }
    }

    @Test fun failingChangeRecorderDoesNotTurnASuccessfulCitelRefreshIntoFailure() = runBlocking<Unit> {
        val cache = store()
        try {
            val model = CitelModel("fixture", cache, Vault(), object : CitelDataSource {
                override suspend fun fetch(credentials: Credentials) = emptyList<CitelTask>()
            }, DataChangeRecorder { _, _ -> error("fixture feed failure") })
            assertTrue(model.saveAccountAndSync("account-a", "fixture"))
            assertFalse(model.state.value.failed)
            assertNotNull(cache.metadata("fixture", "citel.lastSync"))
        } finally { cache.close() }
    }

    private fun store(): CacheStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CacheDatabaseSql.Schema.create(driver)
        return CacheStore(driver)
    }
    private fun transport(body: (SchoolHttpRequest) -> String) = object : SchoolHttpTransport {
        override fun clearSession() {}
        override suspend fun execute(request: SchoolHttpRequest) =
            SchoolHttpResponse(200, request.url, body = body(request).encodeToByteArray())
    }
    private class Vault : CredentialVault {
        private var value: Credentials? = null
        override suspend fun load() = value
        override suspend fun save(credentials: Credentials) { value = credentials }
        override suspend fun clear() { value = null }
    }
}

package team.bjtuss.bjtuselfservice.shared.agent

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.auth.LoginFailure
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.cache.JvmAesCacheValueProtector
import team.bjtuss.bjtuselfservice.shared.cache.openDesktopCacheStore
import team.bjtuss.bjtuselfservice.shared.security.AccountPreferences
import team.bjtuss.bjtuselfservice.shared.security.AccountSecurityStore
import team.bjtuss.bjtuselfservice.shared.security.CredentialVault

class AgentCacheUpdateTest {
    private val directory: File = Files.createTempDirectory("agent-sync").toFile()
    private val key = ByteArray(32) { (it * 7).toByte() }

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    // region 编排

    @Test
    fun refreshesInAppOrderAndCalendarAfterCourses() = runBlocking {
        val calls = mutableListOf<String>()
        val report = runAgentSync(
            targets = listOf(AgentSyncTarget.EXAMS, AgentSyncTarget.COURSES, AgentSyncTarget.GRADES),
            login = { calls += "login"; AgentLoginOutcome.Ok },
            refresh = { calls += it.wire; null },
            refreshCalendar = { calls += "calendar"; true },
        )
        assertEquals(listOf("login", "grades", "courses", "calendar", "exams"), calls)
        assertTrue(report.allOk)
        assertTrue(report.academicCalendarUpdated)
        assertEquals(AgentExit.OK, report.toOutput(json = true).exitCode)
    }

    @Test
    fun loginFailureSkipsEveryRefresh() = runBlocking {
        var refreshed = false
        val report = runAgentSync(
            targets = AgentSyncTarget.entries,
            login = { AgentLoginOutcome.ManualRequired(LoginFailure.CAPTCHA_RECOGNITION_FAILED) },
            refresh = { refreshed = true; null },
            refreshCalendar = { refreshed = true; true },
        )
        assertFalse(refreshed)
        val output = report.toOutput(json = true)
        assertEquals(AgentExit.LOGIN_FAILED, output.exitCode)
        assertTrue("manual_required" in output.stdout)
        assertTrue("手动登录" in output.stderr)
    }

    @Test
    fun partialFailureAndTimeoutDoNotStopOtherTargets() = runBlocking {
        val report = runAgentSync(
            targets = AgentSyncTarget.entries,
            login = { AgentLoginOutcome.Ok },
            refresh = { target ->
                when (target) {
                    AgentSyncTarget.HOMEWORK -> "session_expired"
                    AgentSyncTarget.EXAMS -> { delay(1_000); null }
                    AgentSyncTarget.GRADES -> error("boom")
                    else -> null
                }
            },
            refreshCalendar = { false },
            perTargetTimeoutMillis = 200,
        )
        assertEquals(null, report.results[AgentSyncTarget.COURSES])
        assertEquals("session_expired", report.results[AgentSyncTarget.HOMEWORK])
        assertEquals("超时", report.results[AgentSyncTarget.EXAMS])
        assertTrue(report.results[AgentSyncTarget.GRADES]!!.startsWith("失败"))
        val output = report.toOutput(json = false)
        assertEquals(AgentExit.PARTIAL, output.exitCode)
        assertTrue("课表：已更新" in output.stdout)
    }

    // endregion

    // region 关卡（全部在联网之前返回）

    @Test
    fun appRunningBlocksUpdate() {
        writeCache()
        val appLock = assertNotNull(holdAgentAppLock(directory))
        appLock.use {
            val output = runCacheUpdate(emptyList(), platform(remember = true))
            assertEquals(AgentExit.APP_RUNNING, output.exitCode)
            assertTrue("在 App 里刷新" in output.stderr)
        }
        assertNotNull(AgentAppLock.tryAcquire(directory)).close()
    }

    @Test
    fun notRememberedNeverClearsCredentials() {
        writeCache()
        val vault = FakeVault(Credentials("20990001", "secret"))
        val output = runCacheUpdate(emptyList(), platform(remember = false, vault = vault))
        assertEquals(AgentExit.LOGIN_FAILED, output.exitCode)
        assertTrue("记住密码" in output.stderr)
        assertFalse(vault.cleared, "命令行绝不能清除凭据")
        assertFalse("secret" in output.stdout + output.stderr)
    }

    @Test
    fun rememberedAccountMustMatchCache() {
        writeCache()
        val output = runCacheUpdate(emptyList(), platform(remember = true, vault = FakeVault(Credentials("20990009", "x"))))
        assertEquals(AgentExit.LOGIN_FAILED, output.exitCode)
        assertTrue("不一致" in output.stderr)
    }

    @Test
    fun unsupportedTargetsPointToApp() {
        val output = runCacheUpdate(listOf("citel"), platform(remember = true))
        assertEquals(AgentExit.USAGE, output.exitCode)
        assertTrue("打开 App" in output.stderr)
    }

    @Test
    fun missingCacheIsReportedWithoutCreatingDatabase() {
        val output = runCacheUpdate(emptyList(), platform(remember = true))
        assertEquals(AgentExit.NO_DATA, output.exitCode)
        assertFalse(File(directory, "bjtuselfservice_cache.db").exists())
    }

    @Test
    fun invalidOptionsNeverReadSecurityOrCreateLock() {
        val platform = AgentCliPlatform(directory, { error("must not read key") }, { error("must not read account") })
        assertEquals(AgentExit.USAGE, runCacheUpdate(listOf("--password", "secret"), platform).exitCode)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun failedGateReleasesLockAndPreservesCachedProfile() {
        writeCache()
        runCacheUpdate(emptyList(), platform(remember = false))
        assertNotNull(AgentAppLock.tryAcquire(directory)).close()
        val store = openExistingCacheStoreReadOnly(File(directory, "bjtuselfservice_cache.db"), key.copyOf())
        try { assertEquals("20990001", store.cachedProfile("20990001")?.studentId) } finally { store.close() }
    }

    // endregion

    private fun writeCache() {
        val handle = openDesktopCacheStore(directory, JvmAesCacheValueProtector(key.copyOf()))
        handle.store.saveCachedProfile(StudentProfile("测试同学", "20990001", "本科生", "测试学院"))
        handle.store.close()
    }

    private fun platform(remember: Boolean, vault: FakeVault = FakeVault(Credentials("20990001", "secret"))) = AgentCliPlatform(
        cacheDirectory = directory,
        loadCacheKey = { key.copyOf() },
        rememberedUsername = { "20990001" },
        securityStore = { AccountSecurityStore(vault, FakePreferences(remember)) },
        captchaRecognizer = { error("不应走到验证码识别") },
    )

    private class FakeVault(private val credentials: Credentials?) : CredentialVault {
        var cleared = false
        override suspend fun save(credentials: Credentials) = Unit
        override suspend fun load(): Credentials? = credentials
        override suspend fun clear() {
            cleared = true
        }
    }

    private class FakePreferences(private val remember: Boolean) : AccountPreferences {
        override suspend fun shouldRememberCredentials(): Boolean = remember
        override suspend fun setShouldRememberCredentials(enabled: Boolean) = Unit
        override suspend fun clearRememberCredentialsSetting() = Unit
    }
}

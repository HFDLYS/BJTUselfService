package team.bjtuss.bjtuselfservice.shared.agent

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.cache.JvmAesCacheValueProtector
import team.bjtuss.bjtuselfservice.shared.cache.openDesktopCacheStore

/** 只读打开：读得到 App 写的加密缓存；缺文件、缺密钥、版本不符都报错，且绝不改动/删除数据库。 */
class AgentReadOnlyCacheTest {
    private val directory: File = Files.createTempDirectory("agent-cache").toFile()
    private val key = ByteArray(32) { it.toByte() }

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun readsEncryptedCacheWrittenByApp() {
        writeAppCache()
        val database = File(directory, "bjtuselfservice_cache.db")
        val before = database.readBytes().contentHashCode()
        val store = openExistingCacheStoreReadOnly(database, key.copyOf())
        assertEquals("测试同学", store.cachedProfile("20990001")?.name)
        store.close()
        assertEquals(before, database.readBytes().contentHashCode())
    }

    @Test
    fun writesAreRejectedOnReadOnlyConnection() {
        writeAppCache()
        val store = openExistingCacheStoreReadOnly(File(directory, "bjtuselfservice_cache.db"), key.copyOf())
        assertFailsWith<Exception> { store.putMetadata("20990001", "k", "v") }
        store.close()
    }

    @Test
    fun schemaVersionMismatchAsksToOpenApp() {
        writeAppCache()
        val database = File(directory, "bjtuselfservice_cache.db")
        DriverManager.getConnection("jdbc:sqlite:${database.absolutePath}").use { it.createStatement().execute("PRAGMA user_version = 1") }
        val error = assertFailsWith<AgentCacheVersionException> { openExistingCacheStoreReadOnly(database, key.copyOf()) }
        assertTrue("打开一次新版 App" in error.message.orEmpty())
        assertTrue(database.isFile)
    }

    @Test
    fun missingCacheOrKeyIsReportedWithoutCreatingAnything() {
        val output = runAgentCommand(listOf("status"), providerFor(loadKey = { key.copyOf() }), kotlin.time.Clock.System.now())
        assertEquals(AgentExit.NO_DATA, output.exitCode)
        assertTrue(directory.listFiles().orEmpty().isEmpty(), "不应创建任何文件")

        writeAppCache()
        val noKey = providerFor(loadKey = { null }).load(LocalDate(2026, 11, 12))
        assertIs<AgentSnapshotResult.Unavailable>(noKey)
        val denied = providerFor(loadKey = { error("denied") }).load(LocalDate(2026, 11, 12))
        assertIs<AgentSnapshotResult.Unavailable>(denied)
        assertTrue(File(directory, "bjtuselfservice_cache.db").isFile)
    }

    @Test
    fun wrongKeyFailsWithoutTouchingDatabase() {
        writeAppCache()
        val database = File(directory, "bjtuselfservice_cache.db")
        val before = database.readBytes().contentHashCode()
        val result = providerFor(loadKey = { ByteArray(32) { 9 } }).load(LocalDate(2026, 11, 12))
        assertIs<AgentSnapshotResult.Unavailable>(result)
        assertEquals(before, database.readBytes().contentHashCode())
    }

    @Test
    fun picksRememberedAccountWhenSeveralProfilesExist() {
        writeAppCache(extraAccount = "20990002")
        val result = providerFor(loadKey = { key.copyOf() }, remembered = "20990002").load(LocalDate(2026, 11, 12))
        assertEquals("20990002", assertIs<AgentSnapshotResult.Ready>(result).snapshot.studentId)
        val unknown = providerFor(loadKey = { key.copyOf() }, remembered = null).load(LocalDate(2026, 11, 12))
        assertIs<AgentSnapshotResult.Unavailable>(unknown)
        assertFalse(unknown.reason.contains("2099"))
    }

    private fun providerFor(loadKey: () -> ByteArray?, remembered: String? = null): AgentSnapshotProvider =
        cacheSnapshotProvider(AgentCliPlatform(directory, loadKey, rememberedUsername = { remembered }))

    private fun writeAppCache(extraAccount: String? = null) {
        val handle = openDesktopCacheStore(directory, JvmAesCacheValueProtector(key.copyOf()))
        handle.store.saveCachedProfile(StudentProfile("测试同学", "20990001", "本科生", "测试学院"))
        extraAccount?.let { handle.store.saveCachedProfile(StudentProfile("另一位", it, "本科生", "测试学院")) }
        handle.store.close()
    }
}

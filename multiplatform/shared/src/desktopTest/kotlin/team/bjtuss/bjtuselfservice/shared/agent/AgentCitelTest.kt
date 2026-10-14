package team.bjtuss.bjtuselfservice.shared.agent

import java.io.File
import java.nio.file.Files
import kotlin.test.*
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.cache.JvmAesCacheValueProtector
import team.bjtuss.bjtuselfservice.shared.cache.openDesktopCacheStore
import team.bjtuss.bjtuselfservice.shared.feature.citel.*
import kotlinx.serialization.json.Json

class AgentCitelTest {
    @Test fun invalidArgsNeverTouchKeychain() {
        val directory = Files.createTempDirectory("agent-citel-gates").toFile()
        try {
            val platform = AgentCliPlatform(directory, { error("no key read") }, { error("no account read") })
            for (args in listOf(listOf("citel", "show", "0"), listOf("citel", "show", "1", "--password"), listOf("citel", "export", "1"))) {
                assertEquals(AgentExit.USAGE, runAgentCitel(args, platform).exitCode)
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun offlineShowAndExportUseCacheWithoutSecurityOrNetwork() {
        val directory = Files.createTempDirectory("agent-citel-offline").toFile()
        val key = ByteArray(32) { 7 }
        val task = CitelTask(1, 1, "fixture", "fixture", "$CITEL_BASE/mod/programming/view.php?id=1", true)
        try {
            val store = openDesktopCacheStore(directory, JvmAesCacheValueProtector(key.copyOf())).store
            val cached = CitelCachedProblem("fixture-citel", 1, 0, CitelProblem("fixture", "Grade: 1", "<p>fixture</p>", cases = listOf(CitelProblemCase(1,"test",input="1\n",expected="2\n"))))
            store.saveCachedProfile(StudentProfile("测试", "20990001", "本科", "测试"))
            store.putMetadata("20990001", "citel.username", "fixture-citel")
            store.putMetadata("20990001", "citel.tasks", Json.encodeToString(listOf(task)))
            store.saveCitelProblem("20990001", cached)
            store.close()
            File(directory,".cache-encryption-v1").writeText("fixture")
            val platform = AgentCliPlatform(directory, { key.copyOf() }, { error("single cached account") }, securityStore = { error("offline must not read credentials") })
            val shown = runAgentCitel(listOf("citel","show","1","--json"), platform)
            assertEquals(0, shown.exitCode)
            assertFalse("fixture-citel" in shown.stdout)
            assertTrue("题面" in shown.stdout)
            val destination = File(directory,"export")
            assertEquals(0, runAgentCitel(listOf("citel","export","1","--out",destination.path), platform).exitCode)
            assertEquals("1\n", File(destination,"tests/1.in").readText())
            assertEquals(AgentExit.USAGE, runAgentCitel(listOf("citel","export","1","--out",destination.path), platform).exitCode)
            holdAgentAppLock(directory)!!.use {
                assertEquals(AgentExit.APP_RUNNING, runAgentCitel(listOf("citel","show","1","--fetch"), platform).exitCode)
                assertEquals(0, runAgentCitel(listOf("citel","show","1"), platform).exitCode)
            }
        } finally { directory.deleteRecursively() }
    }
}

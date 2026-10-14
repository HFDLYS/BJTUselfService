package team.bjtuss.bjtuselfservice.shared.agent

import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.AbstractPreferences
import java.util.prefs.Preferences
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import team.bjtuss.bjtuselfservice.shared.cache.JvmAesCacheValueProtector
import team.bjtuss.bjtuselfservice.shared.cache.openDesktopCacheStore
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.security.*

class MacUninstallCleanerTest {
    @Test fun dryRunAndBadArgumentsHaveNoSideEffects() = temporaryHome { home ->
        val app = File(home,"fixture.app").apply { mkdir() }
        val cleaner = MacUninstallCleaner(home, app, { error("integration") }, { error("preferences") },
            clearSecrets = { error("Keychain") }, trash = { error("Trash") })
        assertTrue("默认仅预览" in cleaner.dryRun())
        assertFalse(cleaner.supportDirectory.exists())
        assertEquals(AgentExit.USAGE, runUninstallApp(listOf("uninstall-app","--unknown")) { error("must not create cleaner") }.exitCode)
        assertEquals(listOf("fixture.app"),home.list().orEmpty().toList())
    }

    @Test fun removesOnlyOwnTreePrefsAndManagedIntegrationThenTrashesLast() = temporaryHome { home ->
        val app = File(home,"fixture.app").apply { mkdir() }
        val prefs = MemoryPrefs()
        prefs.node("/team/bjtuss/bjtuselfservice/kmp/account").put("fixture","value")
        prefs.node("/unrelated-app").put("keep","yes")
        val integration = DesktopAgentIntegration(home, File(app,"Contents/MacOS/BJTUselfServiceKMP").path, false)
        assertIs<AgentInstallResult.Done>(integration.install(linkClaude=false))
        val events = mutableListOf<String>()
        val cleaner = MacUninstallCleaner(home, app, { integration }, { prefs }, clearSecrets = { full ->
            assertTrue(full); events += "secrets"
        }, trash = { file ->
            assertFalse(File(home,"Library/Application Support/BJTUselfServiceKMP").exists())
            assertFalse(prefs.nodeExists("/team/bjtuss/bjtuselfservice/kmp"))
            assertFalse(integration.status().installed)
            events += "trash"
            file.renameTo(File(home,"fixture-trash.app"))
        })
        val support = cleaner.supportDirectory.apply { mkdirs() }
        File(support,"bjtuselfservice_cache.db").writeText("fixture")
        File(support,"bjtuselfservice_cache.db-wal").writeText("fixture")
        File(support,"bjtuselfservice_cache.db-shm").writeText("fixture")
        File(home,"Library/Caches/BJTUselfServiceKMP").apply { mkdirs(); resolve("temporary").writeText("fixture") }
        File(home,"Library/Preferences/com.apple.java.util.prefs.plist").apply { parentFile.mkdirs(); writeText("unrelated shared file") }
        assertEquals(0, cleaner.uninstall().exitCode)
        assertEquals(listOf("secrets","trash"), events)
        assertEquals("yes",prefs.node("/unrelated-app").get("keep",null))
        assertEquals("unrelated shared file",File(home,"Library/Preferences/com.apple.java.util.prefs.plist").readText())
        assertTrue(File(home,"fixture-trash.app").exists())
    }

    @Test fun appLockAndUnknownBundleBlockBeforeAnyDeletion() = temporaryHome { home ->
        val app = File(home,"fixture.app").apply { mkdir() }
        val cleaner = MacUninstallCleaner(home,app,{ error("integration") },{ error("prefs") },clearSecrets={ error("secrets") },trash={ error("trash") })
        cleaner.supportDirectory.mkdirs()
        holdAgentAppLock(cleaner.supportDirectory)!!.use { assertEquals(AgentExit.APP_RUNNING, cleaner.uninstall().exitCode) }
        val unbundled = MacUninstallCleaner(home,null,{ error("integration") },{ error("prefs") },clearSecrets={ error("secrets") },trash={ error("trash") })
        assertEquals(AgentExit.NO_DATA, unbundled.uninstall().exitCode)
        assertTrue(app.exists())
    }

    @Test fun failedSecretDeletionPreservesAppAndReportsPartialFailure() = temporaryHome { home ->
        val app = File(home,"fixture.app").apply { mkdir() }
        val cleaner = MacUninstallCleaner(home,app,{ NoIntegration },{ MemoryPrefs() },clearSecrets={ error("fixture") },trash={ error("must not trash") })
        assertEquals(AgentExit.PARTIAL,cleaner.uninstall().exitCode)
        assertTrue(app.exists())
    }

    @Test fun symlinksCannotDeleteExternalDataOrCreateExternalLock() = temporaryHome { home ->
        val app = File(home,"fixture.app").apply { mkdir() }
        val outside = File(home,"external").apply { mkdir(); resolve("keep").writeText("yes") }
        val cleaner = MacUninstallCleaner(home,app,{ NoIntegration },{ MemoryPrefs() },clearSecrets={},trash={true})
        cleaner.supportDirectory.parentFile.mkdirs()
        Files.createSymbolicLink(cleaner.supportDirectory.toPath(),outside.toPath())
        assertEquals(AgentExit.PARTIAL,cleaner.uninstall().exitCode)
        assertFalse(File(outside,"app-running.lock").exists())
        Files.delete(cleaner.supportDirectory.toPath())
        cleaner.supportDirectory.mkdirs()
        Files.createSymbolicLink(File(cleaner.supportDirectory,"nested-link").toPath(),outside.toPath())
        assertEquals(0,cleaner.uninstall().exitCode)
        assertEquals("yes",File(outside,"keep").readText())
    }

    @Test fun liveSettingsWipeLeavesEmptyOpenDatabaseAndDoesNotTrashApp() = temporaryHome { home ->
        val prefs = MemoryPrefs()
        val key = ByteArray(32) { 7 }
        var full: Boolean? = null
        val cleaner = MacUninstallCleaner(home,null,{ NoIntegration },{ prefs },clearSecrets={full=it},trash={error("live UI must not trash")})
        val cache = openDesktopCacheStore(cleaner.supportDirectory, JvmAesCacheValueProtector(key)).store
        try {
            cache.saveCachedProfile(StudentProfile("测试","20990001","本科","测试"))
            assertTrue(runBlocking { cleaner.clearLocalData(cache,removeAgent=true) })
            assertNull(cache.cachedProfile("20990001"))
            assertEquals(false,full)
            assertTrue(File(cleaner.supportDirectory,"bjtuselfservice_cache.db").isFile)
        } finally { cache.close() }
    }

    @Test fun serviceWideDeletionAndLiveBagWipeUseOnlySyntheticKeychainServices() {
        if (!System.getProperty("os.name").startsWith("Mac",true)) return
        val service = "team.bjtuss.bjtuselfservice.kmp.uninstall-test.${UUID.randomUUID()}"
        val siblingService = "$service.sibling"
        val first = MacOsKeychainItem(service,"fixture-one")
        val second = MacOsKeychainItem(service,"fixture-two")
        val sibling = MacOsKeychainItem(siblingService,"fixture")
        val bag = MacOsSecretBag("$service.bag","fixture")
        try {
            first.save(byteArrayOf(1),CredentialVaultOperation.SAVE)
            second.save(byteArrayOf(2),CredentialVaultOperation.SAVE)
            sibling.save(byteArrayOf(3),CredentialVaultOperation.SAVE)
            MacOsKeychainItem(service,null).clear(CredentialVaultOperation.CLEAR)
            assertNull(first.load(CredentialVaultOperation.LOAD))
            assertNull(second.load(CredentialVaultOperation.LOAD))
            assertContentEquals(byteArrayOf(3),sibling.load(CredentialVaultOperation.LOAD))
            bag.put(MacOsSecretKeys.CACHE_KEY,byteArrayOf(7))
            bag.put(MacOsSecretKeys.CREDENTIALS,byteArrayOf(9))
            bag.clearPersonalData()
            assertNull(bag.get(MacOsSecretKeys.CREDENTIALS))
            assertContentEquals(byteArrayOf(7),bag.get(MacOsSecretKeys.CACHE_KEY))
            bag.clearAll()
            assertNull(bag.get(MacOsSecretKeys.CACHE_KEY))
        } finally {
            MacOsKeychainItem(service,null).clear(CredentialVaultOperation.CLEAR)
            sibling.clear(CredentialVaultOperation.CLEAR)
            bag.clearAll()
        }
    }

    @Test fun nativeTrashRoundTripUsesOnlyDisposableTemporaryBundle() = temporaryHome { home ->
        if (!System.getProperty("os.name").startsWith("Mac",true)) return@temporaryHome
        val fixture = File(home,"BJTU-uninstall-fixture-${UUID.randomUUID()}.app").apply { mkdir() }
        File(fixture,"fixture-marker").writeText("disposable")
        var trashed: File? = null
        try {
            assertTrue(moveMacAppToTrash(fixture) { trashed = it })
            assertFalse(fixture.exists())
            val target = assertNotNull(trashed)
            assertEquals("disposable",File(target,"fixture-marker").readText())
            Files.move(target.toPath(),fixture.toPath())
            assertTrue(fixture.exists())
        } finally {
            // Recover this test-owned bundle only; never enumerate or empty the user's Trash.
            trashed?.takeIf { it.exists() && !fixture.exists() }?.let { Files.move(it.toPath(),fixture.toPath()) }
        }
    }

    private fun temporaryHome(block: (File) -> Unit) {
        val home=Files.createTempDirectory("bjtu-uninstall-home").toFile()
        try { block(home) } finally { home.deleteRecursively() }
    }
    private object NoIntegration : AgentIntegration {
        override fun status(): AgentIntegrationStatus = error("not needed")
        override fun install(linkClaude:Boolean)=AgentInstallResult.Done("fixture")
        override fun uninstall()=AgentInstallResult.Done("fixture")
    }
    private class MemoryPrefs(parent: AbstractPreferences? = null, name:String="") : AbstractPreferences(parent,name) {
        private val values=mutableMapOf<String,String>()
        private val children=mutableMapOf<String,MemoryPrefs>()
        override fun putSpi(key:String,value:String) {values[key]=value}
        override fun getSpi(key:String):String?=values[key]
        override fun removeSpi(key:String) {values.remove(key)}
        override fun removeNodeSpi() { (parent() as? MemoryPrefs)?.children?.remove(name()) }
        override fun keysSpi():Array<String> = values.keys.toTypedArray()
        override fun childrenNamesSpi():Array<String> = children.keys.toTypedArray()
        override fun childSpi(name:String):AbstractPreferences = children.getOrPut(name) { MemoryPrefs(this,name) }
        override fun syncSpi()=Unit
        override fun flushSpi()=Unit
    }
}

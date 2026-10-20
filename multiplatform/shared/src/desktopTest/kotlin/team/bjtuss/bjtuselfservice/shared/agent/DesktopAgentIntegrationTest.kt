package team.bjtuss.bjtuselfservice.shared.agent

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 全部在临时 HOME 里进行，不碰用户真实的 ~/.agents、~/.claude 或 ~/.local/bin。 */
class DesktopAgentIntegrationTest {
    private val home: File = Files.createTempDirectory("agent-home").toFile()
    private val fakeApp: File = File(home, "Apps/交大自由行 KMP.app/Contents/MacOS/BJTUselfServiceKMP").apply {
        parentFile.mkdirs()
        // 假 App：把收到的参数原样打印，用来验证启动脚本的转发。
        writeText("#!/bin/sh\nfor a in \"\$@\"; do printf '%s|' \"\$a\"; done\n")
        setExecutable(true)
    }

    @AfterTest
    fun cleanUp() {
        home.deleteRecursively()
    }

    private fun integration(app: String? = fakeApp.absolutePath, version: String = "9.9.9") =
        DesktopAgentIntegration(home, app, isWindows = false, appVersion = version)

    @Test
    fun sourceRunIsNotInstallable() {
        val status = integration(app = null).status()
        assertFalse(status.installable)
        assertIs<AgentInstallResult.Failed>(integration(app = null).install())
        assertFalse(File(home, ".agents").exists())
    }

    @Test
    fun installWritesWorkingScriptAndSkill() {
        assertIs<AgentInstallResult.Done>(integration().install())
        val script = File(home, ".local/bin/bjtu")
        assertTrue(script.canExecute())
        val process = ProcessBuilder(script.absolutePath, "calendar", "--course", "数据 结构").redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        assertEquals(0, process.waitFor())
        assertEquals("agent|calendar|--course|数据 结构|", output)

        val skill = File(home, ".agents/skills/bjtuss/SKILL.md").readText()
        assertTrue(skill.startsWith("---\nname: bjtuss\n"))
        assertTrue("freshness" in skill && "4 小时" in skill && "judgeStatus" in skill)
        assertTrue("\"${script.absolutePath}\" <命令>" in skill)
        assertTrue("24:00" in skill && "enrollment: booked" in skill && "--json" in skill)

        val status = integration().status()
        assertTrue(status.installed)
        assertFalse(status.stale)
        assertEquals("9.9.9", status.installedVersion)
        assertEquals(ClaudeSkillLink.ABSENT, status.claude)
    }

    @Test
    fun staleWhenAppMovedOrUpgraded() {
        integration().install()
        assertTrue(integration(version = "10.0.0").status().stale)
        assertTrue(integration(app = "/elsewhere/BJTUselfServiceKMP").status().stale)
        integration(version = "10.0.0").install()
        assertFalse(integration(version = "10.0.0").status().stale)
    }

    @Test
    fun neverOverwritesOrRemovesForeignFiles() {
        val foreignScript = File(home, ".local/bin/bjtu").apply { parentFile.mkdirs(); writeText("#!/bin/sh\necho mine\n") }
        assertIs<AgentInstallResult.Failed>(integration().install())
        assertEquals("#!/bin/sh\necho mine\n", foreignScript.readText())
        foreignScript.delete()

        val foreignSkill = File(home, ".agents/skills/bjtuss").apply { mkdirs() }
        File(foreignSkill, "SKILL.md").writeText("someone else")
        assertIs<AgentInstallResult.Failed>(integration().install())
        assertEquals("someone else", File(foreignSkill, "SKILL.md").readText())

        integration().uninstall()
        assertTrue(File(foreignSkill, "SKILL.md").isFile)
    }

    @Test
    fun claudeSkillsSharedWithAgentsDirIsNotLinkedTwice() {
        File(home, ".agents/skills").mkdirs()
        File(home, ".claude").mkdirs()
        Files.createSymbolicLink(File(home, ".claude/skills").toPath(), File(home, ".agents/skills").toPath())
        integration().install()
        assertEquals(ClaudeSkillLink.SHARED, integration().status().claude)
        assertFalse(Files.isSymbolicLink(File(home, ".agents/skills/bjtuss").toPath()))
        integration().uninstall()
        assertFalse(File(home, ".agents/skills/bjtuss").exists())
        assertTrue(Files.isSymbolicLink(File(home, ".claude/skills").toPath()), "用户自己的软链接保持不动")
    }

    @Test
    fun linksIntoSeparateClaudeSkillsAndRemovesOnUninstall() {
        File(home, ".claude/skills/other-skill").mkdirs()
        integration().install()
        val link = File(home, ".claude/skills/bjtuss").toPath()
        assertTrue(Files.isSymbolicLink(link))
        assertEquals(ClaudeSkillLink.LINKED, integration().status().claude)
        integration().uninstall()
        assertFalse(Files.exists(link))
        assertFalse(File(home, ".local/bin/bjtu").exists())
        assertTrue(File(home, ".claude/skills/other-skill").isDirectory)
    }

    @Test
    fun occupiedClaudeSlotIsLeftAlone() {
        val occupied = File(home, ".claude/skills/bjtuss").apply { mkdirs() }
        File(occupied, "SKILL.md").writeText("other")
        assertIs<AgentInstallResult.Done>(integration().install())
        assertEquals(ClaudeSkillLink.NOT_LINKED, integration().status().claude)
        integration().uninstall()
        assertEquals("other", File(occupied, "SKILL.md").readText())
    }

    @Test
    fun windowsScriptUsesNativeCodePage() {
        val gbk = Charset.forName("GBK")
        val app = "C:\\Program Files\\交大自由行 KMP\\交大自由行 KMP.exe"
        val windows = DesktopAgentIntegration(home, app, isWindows = true, localAppData = File(home, "Local"),
            appVersion = "9.9.9", scriptCharset = gbk)
        assertIs<AgentInstallResult.Done>(windows.install(linkClaude = false))
        val script = File(home, "Local/BJTUselfServiceKMP/bin/bjtu.cmd")
        val text = String(script.readBytes(), gbk)
        assertTrue(text.startsWith("@echo off\r\n"))
        assertTrue("\"$app\" agent %*\r\n" in text)
        assertTrue(windows.status().installed)
        val skill = File(home, ".agents/skills/bjtuss/SKILL.md").readText()
        assertTrue("& \"${script.absolutePath}\" <命令>" in skill)

        val ascii = DesktopAgentIntegration(Files.createTempDirectory("agent-home2").toFile(), app, isWindows = true,
            appVersion = "9.9.9", scriptCharset = Charsets.US_ASCII)
        assertIs<AgentInstallResult.Failed>(ascii.install(linkClaude = false))
    }

    @Test
    fun installRemovesMarkedLegacySkillNameOnly() {
        val legacy = File(home, ".agents/skills/bjtu-self-service").apply { mkdirs() }
        File(legacy, "SKILL.md").writeText("old")
        File(legacy, ".installed-by-bjtuselfservice").writeText("version=1\n")
        val foreign = File(home, ".agents/skills/bjtu-other").apply { mkdirs() }
        integration().install()
        assertFalse(legacy.exists())
        assertTrue(foreign.exists())
        assertTrue(File(home, ".agents/skills/bjtuss/SKILL.md").isFile)
    }
}

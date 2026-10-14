package team.bjtuss.bjtuselfservice.shared.agent

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import team.bjtuss.bjtuselfservice.shared.update.AppUpdateChecker

private const val MARKER_FILE = ".installed-by-bjtuselfservice"
private const val SCRIPT_MARKER = "managed-by: bjtuselfservice"

actual fun platformAgentIntegration(): AgentIntegration? = DesktopAgentIntegration.forCurrentProcess()

/**
 * 只动自己写的文件：启动脚本带 [SCRIPT_MARKER] 注释，技能目录带 [MARKER_FILE]。
 * 目标位置已有别人的同名文件时拒绝覆盖，卸载也只删带标记的。
 */
class DesktopAgentIntegration(
    private val home: File,
    /** 当前 App 的可执行文件（jpackage 启动器）；null 表示从源码运行。 */
    private val appExecutable: String?,
    private val isWindows: Boolean,
    private val localAppData: File? = null,
    private val appVersion: String = AppUpdateChecker.CURRENT_VERSION,
    private val scriptCharset: Charset = Charsets.UTF_8,
) : AgentIntegration {

    private val agentsSkillsRoot = File(home, ".agents/skills")
    private val skillDir = File(agentsSkillsRoot, AGENT_SKILL_NAME)
    private val skillFile = File(skillDir, "SKILL.md")
    private val claudeDir = File(home, ".claude")
    private val claudeSkillsRoot = File(claudeDir, "skills")
    private val claudeSkill = File(claudeSkillsRoot, AGENT_SKILL_NAME)
    private val cliFile: File = if (isWindows) {
        File(localAppData ?: File(home, "AppData/Local"), "BJTUselfServiceKMP/bin/bjtu.cmd")
    } else {
        File(home, ".local/bin/bjtu")
    }

    override fun status(): AgentIntegrationStatus {
        val marker = readMarker(skillDir)
        val scriptApp = readScriptTarget()
        val installed = marker != null && scriptApp != null && skillFile.isFile
        return AgentIntegrationStatus(
            installable = appExecutable != null,
            installed = installed,
            stale = installed && (scriptApp != appExecutable || marker?.get("version") != appVersion),
            installedVersion = marker?.get("version"),
            skillPath = skillFile.absolutePath,
            cliPath = cliFile.absolutePath,
            claude = claudeState(),
            unavailableReason = if (appExecutable == null) "从源码或开发环境运行时无法安装，请使用打包后的 App。" else null,
        )
    }

    override fun install(linkClaude: Boolean): AgentInstallResult {
        val app = appExecutable ?: return AgentInstallResult.Failed("从源码或开发环境运行时无法安装，请使用打包后的 App。")
        if (cliFile.exists() && readScriptTarget() == null) {
            return AgentInstallResult.Failed("${cliFile.absolutePath} 已存在且不是本 App 写的，未覆盖。")
        }
        if (skillDir.exists() && readMarker(skillDir) == null) {
            return AgentInstallResult.Failed("${skillDir.absolutePath} 已存在且不是本 App 写的，未覆盖。")
        }
        return try {
            writeScript(app)
            skillDir.mkdirs()
            skillFile.writeText(renderAgentSkill(cliCommand(), isWindows, appVersion))
            writeMarker(skillDir)
            val claudeNote = if (linkClaude) linkClaudeSkill() else null
            AgentInstallResult.Done(listOfNotNull("已安装：${skillFile.absolutePath}", "命令：${cliFile.absolutePath}", claudeNote)
                .joinToString("\n"))
        } catch (error: Exception) {
            AgentInstallResult.Failed("安装失败：${error.message ?: error::class.simpleName}")
        }
    }

    override fun uninstall(): AgentInstallResult {
        val removed = mutableListOf<String>()
        return try {
            if (readScriptTarget() != null && cliFile.delete()) removed += cliFile.absolutePath
            if (isOurClaudeLink()) {
                deletePath(claudeSkill.toPath())
                removed += claudeSkill.absolutePath
            }
            if (readMarker(skillDir) != null && skillDir.deleteRecursively()) removed += skillDir.absolutePath
            AgentInstallResult.Done(if (removed.isEmpty()) "没有需要移除的文件。" else "已移除：\n" + removed.joinToString("\n"))
        } catch (error: Exception) {
            AgentInstallResult.Failed("移除失败：${error.message ?: error::class.simpleName}")
        }
    }

    /** Skill 里写的调用前缀：路径加引号；PowerShell 需要 `&` 才能执行带引号的路径。 */
    private fun cliCommand(): String = if (isWindows) "& \"${cliFile.absolutePath}\"" else "\"${cliFile.absolutePath}\""

    private fun writeScript(app: String) {
        cliFile.parentFile.mkdirs()
        if (isWindows) {
            val script = "@echo off\r\nrem $SCRIPT_MARKER app=$app\r\n\"$app\" agent %*\r\n"
            val encoder = scriptCharset.newEncoder()
            require(encoder.canEncode(script)) { "App 路径含有当前系统代码页无法表示的字符" }
            cliFile.writeBytes(script.toByteArray(scriptCharset))
        } else {
            cliFile.writeText("#!/bin/sh\n# $SCRIPT_MARKER app=$app\nexec \"$app\" agent \"\$@\"\n")
            runCatching {
                Files.setPosixFilePermissions(cliFile.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))
            }.getOrElse { cliFile.setExecutable(true) }
        }
    }

    /** 读启动脚本里记录的 App 路径；不是本 App 写的脚本返回 null。 */
    private fun readScriptTarget(): String? {
        if (!cliFile.isFile) return null
        val text = runCatching { String(cliFile.readBytes(), scriptCharset) }.getOrNull() ?: return null
        val line = text.lineSequence().firstOrNull { SCRIPT_MARKER in it } ?: return null
        return line.substringAfter("app=", "").trim().ifEmpty { null }
    }

    private fun writeMarker(dir: File) {
        File(dir, MARKER_FILE).writeText("version=$appVersion\napp=${appExecutable.orEmpty()}\n")
    }

    private fun readMarker(dir: File): Map<String, String>? {
        val file = File(dir, MARKER_FILE)
        if (!file.isFile) return null
        return file.readLines().mapNotNull { line ->
            line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
        }.toMap()
    }

    private fun claudeState(): ClaudeSkillLink = when {
        !claudeDir.isDirectory -> ClaudeSkillLink.ABSENT
        sharesAgentsSkills() -> ClaudeSkillLink.SHARED
        isOurClaudeLink() -> ClaudeSkillLink.LINKED
        else -> ClaudeSkillLink.NOT_LINKED
    }

    /** ~/.claude/skills 本身就是 ~/.agents/skills（软链接）时，装一处即可。 */
    private fun sharesAgentsSkills(): Boolean = runCatching {
        claudeSkillsRoot.exists() && agentsSkillsRoot.exists() &&
            claudeSkillsRoot.toPath().toRealPath() == agentsSkillsRoot.toPath().toRealPath()
    }.getOrDefault(false)

    private fun isOurClaudeLink(): Boolean {
        if (sharesAgentsSkills()) return false
        val path = claudeSkill.toPath()
        return when {
            Files.isSymbolicLink(path) -> runCatching {
                path.toRealPath() == skillDir.toPath().toRealPath()
            }.getOrDefault(Files.readSymbolicLink(path).toString() == skillDir.absolutePath)
            claudeSkill.isDirectory -> readMarker(claudeSkill) != null
            else -> false
        }
    }

    private fun linkClaudeSkill(): String? {
        if (!claudeDir.isDirectory) return null
        if (sharesAgentsSkills()) return "Claude Code 的 skills 目录与 ~/.agents/skills 相同，无需再链接。"
        val path = claudeSkill.toPath()
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !isOurClaudeLink()) {
            return "${claudeSkill.absolutePath} 已被占用，未链接到 Claude Code。"
        }
        deletePath(path)
        claudeSkillsRoot.mkdirs()
        return try {
            Files.createSymbolicLink(path, skillDir.toPath())
            "已链接到 Claude Code：${claudeSkill.absolutePath}"
        } catch (_: Exception) {
            // Windows 普通用户不能建软链接：复制一份（带标记，卸载时一起删）。
            skillDir.copyRecursively(claudeSkill, overwrite = true)
            "已复制到 Claude Code：${claudeSkill.absolutePath}"
        }
    }

    private fun deletePath(path: Path) {
        when {
            Files.isSymbolicLink(path) -> Files.delete(path)
            Files.isDirectory(path) -> path.toFile().deleteRecursively()
            Files.exists(path) -> Files.delete(path)
        }
    }

    companion object {
        fun forCurrentProcess(): DesktopAgentIntegration {
            val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
            return DesktopAgentIntegration(
                home = File(System.getProperty("user.home")),
                appExecutable = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile },
                isWindows = windows,
                localAppData = System.getenv("LOCALAPPDATA")?.let(::File),
                scriptCharset = if (windows) {
                    runCatching { Charset.forName(System.getProperty("native.encoding")) }.getOrDefault(Charset.defaultCharset())
                } else Charsets.UTF_8,
            )
        }
    }
}

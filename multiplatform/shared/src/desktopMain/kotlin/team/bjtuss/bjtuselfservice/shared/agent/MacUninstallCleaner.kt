package team.bjtuss.bjtuselfservice.shared.agent

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.feature.settings.AppLocalDataCleaner
import team.bjtuss.bjtuselfservice.shared.security.*

private const val APP_PREFS_NODE = "/team/bjtuss/bjtuselfservice/kmp"


/** App keeps its open encrypted database/key until exit; CLI removes storage only with the App lock. */
class MacUninstallCleaner internal constructor(
    private val home: File,
    private val bundle: File?,
    private val integration: () -> AgentIntegration,
    private val preferencesRoot: () -> Preferences,
    private val preferencesNode: String = APP_PREFS_NODE,
    private val clearSecrets: (full: Boolean) -> Unit,
    private val trash: (File) -> Boolean,
) : AppLocalDataCleaner {
    val supportDirectory: File get() = File(home, "Library/Application Support/BJTUselfServiceKMP")
    private val auxiliaryDirectories get() = listOf(File(home, "Library/Caches/BJTUselfServiceKMP"), File(home, "Library/Logs/BJTUselfServiceKMP"))

    /** No key reads, preferences access, mkdir, lock creation or writes. */
    fun dryRun(): String = buildString {
        appendLine("# 交大自由行卸载清理预览")
        appendLine()
        appendLine("默认仅预览；明确要删除本地数据时使用 agent uninstall-app --yes。")
        appendLine("- 删除：${supportDirectory.absolutePath}（缓存库、WAL/SHM、标记、锁）")
        auxiliaryDirectories.forEach { appendLine("- 删除（若存在）：${it.absolutePath}") }
        appendLine("- 删除本 App 偏好节点：$preferencesNode（保留共享 java.util.prefs plist）")
        appendLine("- 清除本 App Keychain secret bag 与全部旧服务条目（不读取内容）")
        appendLine("- 移除本 App 管理的 AI Skill、Claude 链接与启动脚本；保留同名外部文件")
        appendLine(if (bundle != null) "- 最后将当前 App 移到废纸篓：${bundle.absolutePath}（可恢复）" else "- 未识别当前 App 包，执行清理前会拒绝；请经 App 安装包启动器运行。")
    }

    override suspend fun clearLocalData(cache: CacheStore, removeAgent: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            cache.clearAll()
            removePreferences()
            clearSecrets(false)
            if (removeAgent) require(integration().uninstall() is AgentInstallResult.Done)
            true
        }.getOrDefault(false)
    }

    fun uninstall(): AgentOutput {
        if (bundle == null || !bundle.isDirectory) return AgentOutput("", "无法识别当前 App 包，请使用安装包启动器运行。", AgentExit.NO_DATA)
        if (Files.isSymbolicLink(supportDirectory.toPath())) return AgentOutput("", "缓存目录是外部符号链接，请人工检查后再清理。", AgentExit.PARTIAL)
        val lock = if (supportDirectory.isDirectory) AgentAppLock.tryAcquire(supportDirectory)
            ?: return AgentOutput("", "App 正在运行或刷新，请退出后再执行卸载清理。", AgentExit.APP_RUNNING) else null
        try {
            val failures = mutableListOf<String>()
            fun step(label: String, action: () -> Unit) {
                try { action() } catch (_: Exception) { failures += label }
            }
            step("移除 AI 助手") { require(integration().uninstall() is AgentInstallResult.Done) }
            step("清除应用偏好") { removePreferences() }
            step("清除 Keychain") { clearSecrets(true) }
            step("删除缓存目录") { deleteLocalTree(supportDirectory) }
            auxiliaryDirectories.forEach { directory -> step("删除 ${directory.name} 目录") { deleteLocalTree(directory) } }
            if (failures.isNotEmpty()) return AgentOutput("", "部分清理失败：${failures.joinToString("、")}。App 尚未移到废纸篓，可以重试。", AgentExit.PARTIAL)
            if (!trash(bundle)) return AgentOutput("", "本地数据已清理，但 App 移到废纸篓失败。请在 Finder 中手动移到废纸篓。", AgentExit.PARTIAL)
            return AgentOutput("本地数据与 AI 助手已清理，App 已移到废纸篓（可恢复）。\n")
        } finally { lock?.close() }
    }

    private fun removePreferences() {
        val root = preferencesRoot()
        if (root.nodeExists(preferencesNode)) root.node(preferencesNode).removeNode()
        root.flush()
    }

    companion object {
        fun production(): MacUninstallCleaner = MacUninstallCleaner(
            home = File(System.getProperty("user.home")),
            bundle = currentMacAppBundle(),
            integration = { DesktopAgentIntegration.forCurrentProcess() },
            preferencesRoot = { Preferences.userRoot() },
            clearSecrets = { full ->
                if (full) MacOsKeychainItem(MACOS_SECRET_BAG_SERVICE, null).clear(CredentialVaultOperation.CLEAR)
                else productionMacOsSecretBag.clearPersonalData()
                clearLegacyMacOsAppSecrets()
            },
            trash = { moveMacAppToTrash(it) },
        )
    }
}

/** Never follow a symlink into another user's directory/tree. */
internal fun deleteLocalTree(directory: File) {
    val root = directory.toPath()
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
    Files.walk(root).use { entries -> entries.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
}

internal fun currentMacAppBundle(): File? {
    val executable = System.getProperty("jpackage.app-path")?.let(::File) ?: return null
    if (executable.name != "BJTUselfServiceKMP" || executable.parentFile?.name != "MacOS" || executable.parentFile?.parentFile?.name != "Contents") return null
    val bundle = executable.parentFile.parentFile.parentFile ?: return null
    if (!bundle.name.endsWith(".app") || Files.isSymbolicLink(bundle.toPath())) return null
    val info = File(bundle, "Contents/Info.plist")
    if (!info.isFile) return null
    return runCatching {
        val process = ProcessBuilder("/usr/bin/plutil", "-extract", "CFBundleIdentifier", "raw", "-o", "-", info.absolutePath).redirectErrorStream(true).start()
        val id = process.inputStream.bufferedReader().readText().trim()
        bundle.takeIf { process.waitFor() == 0 && id == "team.bjtuss.bjtuselfservice.kmp.macos" }
    }.getOrNull()
}

internal fun runUninstallApp(args: List<String>, cleaner: () -> MacUninstallCleaner = { MacUninstallCleaner.production() }): AgentOutput {
    if (args.drop(1).any { it != "--yes" } || args.count { it == "--yes" } > 1) return AgentOutput("", "用法：agent uninstall-app [--yes]；默认仅预览。", AgentExit.USAGE)
    if (!System.getProperty("os.name").startsWith("Mac", true)) return AgentOutput("", "uninstall-app 当前仅支持 macOS。", AgentExit.NO_DATA)
    val instance = cleaner()
    return if ("--yes" in args) instance.uninstall() else AgentOutput(instance.dryRun())
}

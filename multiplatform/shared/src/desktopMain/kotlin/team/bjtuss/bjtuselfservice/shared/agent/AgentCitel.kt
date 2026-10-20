package team.bjtuss.bjtuselfservice.shared.agent

import java.io.File
import java.nio.file.Files
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import team.bjtuss.bjtuselfservice.shared.feature.citel.*
import team.bjtuss.bjtuselfservice.shared.network.createSchoolHttpTransport

internal data class AgentCitelOptions(val action: String, val id: Int, val fetch: Boolean, val json: Boolean, val out: File?)

/** All arguments validated before cache, locks or Keychain. */
internal fun parseAgentCitelOptions(args: List<String>): AgentCitelOptions {
    val action = args.getOrNull(1)?.takeIf { it in setOf("show", "export") }
        ?: throw AgentUsageException("citel 只支持 show <id> 或 export <id> --out <新目录>。")
    val id = args.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 } ?: throw AgentUsageException("模块 id 必须为正整数。")
    var fetch = false
    var json = false
    var destination: File? = null
    var i = 3
    while (i < args.size) {
        when (val option = args[i++]) {
            "--fetch" -> { if (fetch) throw AgentUsageException("--fetch 重复。"); fetch = true }
            "--json" -> { if (json) throw AgentUsageException("--json 重复。"); json = true }
            "--out" -> {
                if (action != "export" || destination != null) throw AgentUsageException("--out 仅用于 export，且只能指定一次。")
                val path = args.getOrNull(i++)?.takeIf { it.isNotBlank() && !it.startsWith("--") }
                    ?: throw AgentUsageException("--out 后需要目录路径。")
                destination = File(path).absoluteFile
                if (Files.exists(destination.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw AgentUsageException("导出目录已存在，请指定新目录。")
                if (destination.parentFile?.isDirectory != true) throw AgentUsageException("导出目录的父目录不存在。")
            }
            else -> throw AgentUsageException("未知选项：$option")
        }
    }
    if (action == "export" && destination == null) throw AgentUsageException("export 必须指定 --out <新目录>。")
    return AgentCitelOptions(action, id, fetch, json, destination)
}

internal fun runAgentCitel(args: List<String>, platform: AgentCliPlatform): AgentOutput {
    val options = try { parseAgentCitelOptions(args) } catch (e: AgentUsageException) {
        return AgentOutput("", e.message.orEmpty(), AgentExit.USAGE)
    }
    val lock = if (options.fetch) AgentAppLock.tryAcquire(platform.cacheDirectory)
        ?: return AgentOutput("", "App 正在运行，请在 App 获取全文或关闭 App 后再 --fetch。", AgentExit.APP_RUNNING) else null
    try {
        val database = File(platform.cacheDirectory, "bjtuselfservice_cache.db")
        if (!database.isFile || !File(platform.cacheDirectory, ".cache-encryption-v1").isFile) return AgentOutput("", "请先打开 App 登录并同步 CITEL。", AgentExit.NO_DATA)
        val key = runCatching(platform.loadCacheKey).getOrNull() ?: return AgentOutput("", "无法读取缓存密钥。", AgentExit.NO_DATA)
        val store = try { openExistingCacheStore(database, key, readOnly = !options.fetch) }
            catch (_: Exception) { return AgentOutput("", "无法打开缓存，请先打开 App 完成升级或同步。", AgentExit.NO_DATA) }
            finally { key.fill(0) }
        try {
            val scope = resolveAgentAccount(store, null) ?: resolveAgentAccount(store, platform.rememberedUsername())
                ?: return AgentOutput("", "无法确定缓存账号，请先打开 App 登录。", AgentExit.NO_DATA)
            val username = store.metadata(scope, "citel.username")?.takeIf { it.isNotBlank() }
                ?: return AgentOutput("", "没有 CITEL 账号设置。", AgentExit.NO_DATA)
            val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
            val task = runCatching { json.decodeFromString<List<CitelTask>>(store.metadata(scope, "citel.tasks").orEmpty()) }.getOrNull()
                ?.firstOrNull { it.id == options.id && it.programming } ?: return AgentOutput("", "缓存中没有这道编程题，请先同步 CITEL。", AgentExit.NO_DATA)
            var cached = store.citelProblem(scope, username, task.id)
            if (options.fetch) {
                if (store.metadata(scope, "citel.enabled") != "true" || store.metadata(scope, "citel.remember") != "true") return AgentOutput("", "请先在 App 启用 CITEL 并记住 CITEL 密码。", AgentExit.LOGIN_FAILED)
                val security = platform.securityStore?.invoke() ?: return AgentOutput("", "当前平台无法获取 CITEL 凭据。", AgentExit.LOGIN_FAILED)
                cached = runBlocking {
                    val credentials = security.citelVault?.invoke(scope)?.load()?.takeIf { it.isValid && it.username == username }
                        ?: throw CitelFailure("记住的 CITEL 账号与缓存不一致或无可用凭据。", true)
                    val problem = CitelRemote(createSchoolHttpTransport()).problem(credentials, task, withResults = true)
                    CitelCachedProblem(username, task.id, Clock.System.now().epochSeconds, problem).also { store.saveCitelProblem(scope, it) }
                }
            }
            val ready = cached ?: return AgentOutput("", "尚未缓存全文。经用户同意后加 --fetch，或在 App 获取题目全文。", AgentExit.NO_DATA)
            if (options.action == "export") {
                writeAgentCitelDirectory(options.out!!, task, ready)
                return AgentOutput(if (options.json) json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("schemaVersion", AGENT_SCHEMA_VERSION); put("command", "citel export"); put("moduleId", task.id); put("out", options.out.absolutePath)
                }) + "\n" else "已导出 problem.md 和 tests/：${options.out.absolutePath}\n")
            }
            return AgentOutput(if (options.json) json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("schemaVersion", AGENT_SCHEMA_VERSION); put("command", "citel show"); put("moduleId", task.id)
                put("fetchedAt", isoTime(ready.fetchedAt)); put("data", json.encodeToJsonElement(ready.problem)); put("markdown", citelProblemMarkdown(task, ready))
            }) + "\n" else citelProblemMarkdown(task, ready))
        } finally { store.close() }
    } catch (e: CitelFailure) {
        return AgentOutput("", e.userMessage, if (e.sessionExpired) AgentExit.LOGIN_FAILED else AgentExit.PARTIAL)
    } catch (_: Exception) {
        return AgentOutput("", "CITEL 获取或导出失败，原缓存保留，请重试。", AgentExit.PARTIAL)
    } finally { lock?.close() }
}

/** Stage the new tree in its parent; never replace existing files. */
internal fun writeAgentCitelDirectory(destination: File, task: CitelTask, cached: CitelCachedProblem) {
    val target = destination.toPath()
    require(!Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS))
    val staging = Files.createTempDirectory(target.parent, ".citel-export-")
    try {
        for (file in citelProblemFiles(task, cached)) {
            var parent = staging
            for (folder in file.relativeFolders) parent = parent.resolve(folder)
            Files.createDirectories(parent)
            Files.write(parent.resolve(file.content.fileName), file.content.bytes, java.nio.file.StandardOpenOption.CREATE_NEW)
        }
        Files.move(staging, target)
    } finally { staging.toFile().deleteRecursively() }
}

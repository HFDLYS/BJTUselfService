package team.bjtuss.bjtuselfservice.shared.agent

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import team.bjtuss.bjtuselfservice.shared.network.createSchoolHttpTransport

private const val APP_LOCK_FILE = "app-running.lock"

/**
 * App 运行期间持有的进程锁。命令行要写缓存、登录学校系统前先试这把锁：
 * 拿不到说明 App 开着，此时再登录可能把 App 的会话顶掉，改为提示用户在 App 里刷新。
 * App 拿不到锁（命令行正在同步）时照常启动，不阻塞界面。
 */
class AgentAppLock private constructor(private val file: RandomAccessFile, private val lock: FileLock) : AutoCloseable {
    override fun close() {
        runCatching { lock.release() }
        runCatching { file.close() }
    }

    companion object {
        fun tryAcquire(cacheDirectory: File): AgentAppLock? = runCatching {
            if (!cacheDirectory.isDirectory) return null
            val file = RandomAccessFile(File(cacheDirectory, APP_LOCK_FILE), "rw")
            val lock = runCatching { file.channel.tryLock() }.getOrNull()
            if (lock == null) {
                file.close()
                null
            } else AgentAppLock(file, lock)
        }.getOrNull()
    }
}

/** App 进程在启动时调用并一直持有（进程退出时系统自动释放）。 */
fun holdAgentAppLock(cacheDirectory: File): AgentAppLock? = AgentAppLock.tryAcquire(cacheDirectory)

private val reportJson = Json { prettyPrint = true }

/** `cache update [grades,courses,homework,exams] [--json]`：用记住的账号后台登录并刷新缓存。 */
internal fun runCacheUpdate(args: List<String>, platform: AgentCliPlatform): AgentOutput {
    val json = "--json" in args
    val requested = args.filter { !it.startsWith("--") }.flatMap { it.split(',') }.map(String::trim).filter(String::isNotEmpty)
    val targets = if (requested.isEmpty() || requested == listOf("all")) AgentSyncTarget.entries.toList() else {
        requested.map { name ->
            AgentSyncTarget.entries.firstOrNull { it.wire == name }
                ?: return AgentOutput("", "cache update 只支持：${AgentSyncTarget.entries.joinToString(",") { it.wire }}。" +
                    "物理在线、CITEL、物理实验和演示实验需要打开 App 刷新。", AgentExit.USAGE)
        }.distinct()
    }
    fun fail(reason: String, code: Int): AgentOutput =
        AgentOutput(if (json) reportJson.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("schemaVersion", AGENT_SCHEMA_VERSION); put("command", "cache update"); put("error", reason)
        }) + "\n" else "", reason, code)

    val securityFactory = platform.securityStore ?: return fail("当前平台不支持命令行刷新。", AgentExit.NO_DATA)
    val captchaFactory = platform.captchaRecognizer ?: return fail("当前平台不支持命令行刷新。", AgentExit.NO_DATA)
    val lock = AgentAppLock.tryAcquire(platform.cacheDirectory)
        ?: return fail("交大自由行 App 正在运行。请直接在 App 里刷新（命令行登录可能把 App 的会话顶掉）；或关闭 App 后再运行。", AgentExit.APP_RUNNING)
    return lock.use {
        val databaseFile = File(platform.cacheDirectory, "bjtuselfservice_cache.db")
        if (!databaseFile.isFile) return@use fail("本机还没有交大自由行的缓存。请先打开 App 登录一次。", AgentExit.NO_DATA)
        val key = try {
            platform.loadCacheKey()
        } catch (_: Exception) {
            null
        } ?: return@use fail("无法读取系统安全存储中的缓存密钥。", AgentExit.NO_DATA)
        val store = try {
            openExistingCacheStore(databaseFile, key, readOnly = false)
        } catch (error: AgentCacheVersionException) {
            return@use fail(error.message.orEmpty(), AgentExit.NO_DATA)
        } catch (_: Exception) {
            return@use fail("无法打开本地缓存数据库，请稍后重试。", AgentExit.NO_DATA)
        } finally {
            key.fill(0)
        }
        try {
            runBlocking {
                val security = securityFactory()
                // 只读：未勾选“记住密码”时不登录，也不清除任何凭据（App 的 restore() 会清，这里不能用）。
                if (!security.preferences.shouldRememberCredentials()) {
                    return@runBlocking fail("App 没有记住密码，命令行无法自动登录。请在 App 登录时勾选“记住密码”，或直接在 App 里刷新。", AgentExit.LOGIN_FAILED)
                }
                val credentials = runCatching { security.credentialVault?.load() }.getOrNull()?.takeIf { it.isValid }
                    ?: return@runBlocking fail("系统安全存储里没有可用的登录信息。请在 App 里登录一次。", AgentExit.LOGIN_FAILED)
                val account = resolveAgentAccount(store, credentials.username)
                if (account != credentials.username) {
                    return@runBlocking fail("记住的账号与本机缓存的账号不一致，请在 App 里重新登录。", AgentExit.LOGIN_FAILED)
                }
                val clock = Clock.System
                val graph = AgentSyncGraph(
                    studentId = account,
                    cache = store,
                    transport = createSchoolHttpTransport(),
                    captchaRecognizer = captchaFactory(),
                    platformFamily = team.bjtuss.bjtuselfservice.shared.currentPlatform().family,
                    nowEpochSeconds = { clock.now().epochSeconds },
                    today = { clock.now().toLocalDateTime(AGENT_TIME_ZONE).date },
                )
                val report = runAgentSync(
                    targets = targets,
                    login = { graph.login(credentials) },
                    refresh = graph::refresh,
                    refreshCalendar = graph::refreshCalendar,
                )
                report.toOutput(json)
            }
        } finally {
            runCatching(store::close)
        }
    }
}

internal fun AgentSyncReport.toOutput(json: Boolean): AgentOutput {
    val loginText = when (login) {
        AgentLoginOutcome.Ok -> "ok"
        AgentLoginOutcome.Network -> "network"
        is AgentLoginOutcome.ManualRequired -> "manual_required"
    }
    val exit = when {
        login is AgentLoginOutcome.ManualRequired || login == AgentLoginOutcome.Network -> AgentExit.LOGIN_FAILED
        allOk -> AgentExit.OK
        else -> AgentExit.PARTIAL
    }
    val stderr = when (login) {
        is AgentLoginOutcome.ManualRequired -> "自动登录失败（${login.reason.name.lowercase()}）：验证码多次识别失败或密码已变更，请打开 App 手动登录。"
        AgentLoginOutcome.Network -> "无法连接学校系统，请检查网络后重试。"
        AgentLoginOutcome.Ok -> if (allOk) "" else "部分数据刷新失败，失败项保留原缓存。"
    }
    if (json) {
        val body = buildJsonObject {
            put("schemaVersion", AGENT_SCHEMA_VERSION)
            put("command", "cache update")
            put("login", loginText)
            put("results", buildJsonObject {
                results.forEach { (target, failure) -> put(target.wire, failure ?: "ok") }
            })
            put("academicCalendar", if (academicCalendarUpdated) "updated" else "unchanged")
        }
        return AgentOutput(reportJson.encodeToString(JsonObject.serializer(), body) + "\n", stderr, exit)
    }
    val text = buildString {
        appendLine("# 刷新结果")
        appendLine()
        appendLine("- 登录：" + if (login == AgentLoginOutcome.Ok) "成功" else "失败")
        results.forEach { (target, failure) -> appendLine("- ${target.label}：${failure?.let { "失败（$it）" } ?: "已更新"}") }
        if (login == AgentLoginOutcome.Ok) appendLine("- 校历：" + if (academicCalendarUpdated) "已更新" else "未更新")
    }
    return AgentOutput(text, stderr, exit)
}

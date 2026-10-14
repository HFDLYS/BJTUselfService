package team.bjtuss.bjtuselfservice.shared.agent

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.sql.DriverManager
import java.util.Properties
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.cache.JvmAesCacheValueProtector
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.cache.loadExistingMacOsCacheKey
import team.bjtuss.bjtuselfservice.shared.security.MacOsSecretKeys
import team.bjtuss.bjtuselfservice.shared.security.decodeCredentialPayload
import team.bjtuss.bjtuselfservice.shared.security.productionMacOsSecretBag
import team.bjtuss.bjtuselfservice.shared.update.AppUpdateChecker

private const val CACHE_DATABASE_FILE_NAME = "bjtuselfservice_cache.db"
private const val CACHE_ENCRYPTION_MARKER = ".cache-encryption-v1"

/** 平台差异：缓存目录、缓存密钥、记住的登录账号（只取用户名，用于多账号时选默认）。 */
class AgentCliPlatform(
    val cacheDirectory: File,
    val loadCacheKey: () -> ByteArray?,
    val rememberedUsername: () -> String?,
)

fun macOsAgentCliPlatform(): AgentCliPlatform = AgentCliPlatform(
    cacheDirectory = File(System.getProperty("user.home"), "Library/Application Support/BJTUselfServiceKMP"),
    loadCacheKey = ::loadExistingMacOsCacheKey,
    rememberedUsername = {
        productionMacOsSecretBag.get(MacOsSecretKeys.CREDENTIALS)?.let(::decodeCredentialPayload)?.username
    },
)

/**
 * 命令行入口：Main 在任何 AWT 类加载前调用，返回进程退出码。
 * 只读打开缓存；绝不新建密钥、迁移或删除数据库（与 App 的打开流程不同）。
 */
fun runAgentCli(
    args: List<String>,
    platform: AgentCliPlatform,
    integration: () -> AgentIntegration = { DesktopAgentIntegration.forCurrentProcess() },
    out: PrintStream = utf8(System.out),
    err: PrintStream = utf8(System.err),
): Int {
    // sqlite-jdbc 依赖 SLF4J，未带实现时会往 stderr 打三行警告，干扰 agent 读取错误信息。
    System.setProperty("slf4j.internal.verbosity", "ERROR")
    val output = when (args.firstOrNull()) {
        "install", "uninstall", "doctor" -> runIntegrationCommand(args, platform, integration())
        else -> runAgentCommand(args, cacheSnapshotProvider(platform), Clock.System.now())
    }
    if (output.stdout.isNotEmpty()) out.print(output.stdout)
    if (output.stderr.isNotEmpty()) err.println(output.stderr)
    out.flush()
    err.flush()
    return output.exitCode
}

private fun utf8(stream: PrintStream) = PrintStream(stream, true, StandardCharsets.UTF_8)

internal fun cacheSnapshotProvider(platform: AgentCliPlatform): AgentSnapshotProvider = CacheAgentSnapshotProvider(platform)

private class CacheAgentSnapshotProvider(private val platform: AgentCliPlatform) : AgentSnapshotProvider {
    override fun load(today: LocalDate): AgentSnapshotResult {
        val databaseFile = File(platform.cacheDirectory, CACHE_DATABASE_FILE_NAME)
        if (!databaseFile.isFile || !File(platform.cacheDirectory, CACHE_ENCRYPTION_MARKER).isFile) {
            return AgentSnapshotResult.Unavailable("本机还没有交大自由行的缓存。请先打开 App 登录并同步一次。")
        }
        val key = try {
            platform.loadCacheKey()
        } catch (_: Exception) {
            return AgentSnapshotResult.Unavailable("无法读取系统安全存储中的缓存密钥（可能被拒绝访问）。")
        } ?: return AgentSnapshotResult.Unavailable("系统安全存储里没有缓存密钥。请先打开 App 登录一次。")
        val store = try {
            openExistingCacheStoreReadOnly(databaseFile, key)
        } catch (error: AgentCacheVersionException) {
            return AgentSnapshotResult.Unavailable(error.message.orEmpty())
        } catch (_: Exception) {
            return AgentSnapshotResult.Unavailable("无法读取本地缓存数据库（App 可能正在写入），请稍后重试。")
        } finally {
            key.fill(0)
        }
        return try {
            val account = resolveAgentAccount(store) { runCatching(platform.rememberedUsername).getOrNull() }
                ?: return AgentSnapshotResult.Unavailable("缓存里有多个账号，且无法确定当前登录的是哪一个。请在 App 里重新登录一次。")
            AgentSnapshotResult.Ready(
                snapshot = loadAgentSnapshot(store, account, today),
                gradesAllowed = runCatching { store.preferences().agentGradesAllowed }.getOrDefault(false),
                appVersion = AppUpdateChecker.CURRENT_VERSION,
            )
        } catch (_: Exception) {
            AgentSnapshotResult.Unavailable("缓存内容无法解密或解析。请打开 App 同步后重试。")
        } finally {
            runCatching(store::close)
        }
    }
}

class AgentCacheVersionException(message: String) : IllegalStateException(message)

/**
 * 只读连接：不建表、不迁移；schema 版本与本程序不一致时报错，让用户先打开 App 完成升级。
 * App 正在写入时 SQLite 最多等 3 秒。
 */
internal fun openExistingCacheStoreReadOnly(databaseFile: File, keyBytes: ByteArray): CacheStore {
    val url = "jdbc:sqlite:${databaseFile.absolutePath}"
    val properties = Properties().apply {
        setProperty("open_mode", "1") // SQLITE_OPEN_READONLY
        setProperty("busy_timeout", "3000")
    }
    val version = DriverManager.getConnection(url, properties).use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA user_version").use { result -> if (result.next()) result.getLong(1) else 0L }
        }
    }
    val expected = CacheDatabaseSql.Schema.version
    if (version != expected) {
        throw AgentCacheVersionException(
            if (version < expected) "缓存是旧版本格式。请先打开一次新版 App 完成升级。"
            else "缓存来自更新版本的 App。请更新命令行所在的 App。",
        )
    }
    val driver = JdbcSqliteDriver(url, properties)
    return CacheStore(driver, JvmAesCacheValueProtector(keyBytes))
}

private fun resolveAgentAccount(store: CacheStore, remembered: () -> String?): String? =
    resolveAgentAccount(store, null) ?: resolveAgentAccount(store, remembered())

/** `install [--no-claude]` / `uninstall` / `doctor`：不需要读缓存（doctor 除外，会顺带检查缓存是否可读）。 */
internal fun runIntegrationCommand(args: List<String>, platform: AgentCliPlatform, integration: AgentIntegration): AgentOutput =
    when (args.first()) {
        "install" -> integration.install(linkClaude = "--no-claude" !in args).toOutput()
        "uninstall" -> integration.uninstall().toOutput()
        else -> {
            val status = integration.status()
            val cache = cacheSnapshotProvider(platform).load(kotlinx.datetime.LocalDate(2000, 1, 1))
            val report = buildString {
                appendLine("# 交大自由行 AI 助手检查")
                appendLine()
                appendLine("- 安装状态：" + when {
                    !status.installable -> status.unavailableReason
                    !status.installed -> "未安装（在 App 设置“AI 助手”里安装，或运行 agent install）"
                    status.stale -> "已安装，但版本或 App 路径已变化，请重新安装"
                    else -> "已安装（${status.installedVersion}）"
                })
                appendLine("- Skill：${status.skillPath}")
                appendLine("- 命令：${status.cliPath}")
                appendLine("- Claude Code：" + when (status.claude) {
                    ClaudeSkillLink.ABSENT -> "未检测到 ~/.claude"
                    ClaudeSkillLink.SHARED -> "与 ~/.agents/skills 共用"
                    ClaudeSkillLink.LINKED -> "已链接"
                    ClaudeSkillLink.NOT_LINKED -> "未链接"
                })
                appendLine("- 缓存：" + when (cache) {
                    is AgentSnapshotResult.Ready -> "可读取"
                    is AgentSnapshotResult.Unavailable -> cache.reason
                })
            }
            val healthy = status.installed && !status.stale && cache is AgentSnapshotResult.Ready
            AgentOutput(report, exitCode = if (healthy) AgentExit.OK else AgentExit.NO_DATA)
        }
    }

private fun AgentInstallResult.toOutput(): AgentOutput = when (this) {
    is AgentInstallResult.Done -> AgentOutput(message + "\n")
    is AgentInstallResult.Failed -> AgentOutput("", reason, AgentExit.NO_DATA)
}

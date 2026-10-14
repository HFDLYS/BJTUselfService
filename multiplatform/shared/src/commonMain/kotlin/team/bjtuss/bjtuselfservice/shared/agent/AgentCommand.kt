package team.bjtuss.bjtuselfservice.shared.agent

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant
import team.bjtuss.bjtuselfservice.shared.feature.assignment.AssignmentSource

/** 命令行输出：stdout 给调用方（agent）解析，stderr 只放人能读的提示。 */
data class AgentOutput(val stdout: String, val stderr: String = "", val exitCode: Int = AgentExit.OK)

object AgentExit {
    const val OK = 0
    const val USAGE = 64
    /** 没有缓存，或无法确定账号：请先打开 App 登录并同步一次。 */
    const val NO_DATA = 2
    /** 用户在 App 里没有允许 Agent 读取这一类数据。 */
    const val NOT_ALLOWED = 5
    /** cache update：自动登录失败（验证码多次失败、密码变更、未记住密码或网络）。 */
    const val LOGIN_FAILED = 3
    /** cache update：部分数据源刷新失败，失败项保留原缓存。 */
    const val PARTIAL = 4
    /** cache update：App 正在运行，请在 App 里刷新。 */
    const val APP_RUNNING = 6
}

const val AGENT_SCHEMA_VERSION = 1

/** 打开缓存的结果由平台层提供；命令本身是纯逻辑，便于测试。 */
fun interface AgentSnapshotProvider {
    fun load(today: LocalDate): AgentSnapshotResult
}

sealed interface AgentSnapshotResult {
    data class Ready(val snapshot: AgentSnapshot, val gradesAllowed: Boolean, val appVersion: String) : AgentSnapshotResult
    data class Unavailable(val reason: String) : AgentSnapshotResult
}

val AGENT_USAGE = """
    用法: bjtu <命令> [选项]
      status                          版本、账号、缓存情况
      calendar [范围] [筛选]          聚合日程：课程、实验、考试、作业开放/折扣/截止
      courses [范围] [--course 关键词] 课程与实验
      homework [--pending] [筛选]     作业：课程平台、物理在线、CITEL
      exams
      grades                          需要在 App 设置里允许
      citel                           CITEL 任务列表（元数据）
      citel show <id> [--fetch] [--json]  缓存题面与用例；--fetch 才联网
      citel export <id> --out <新目录> [--fetch]  导出 problem.md 和 tests/N.in、N.out
      cache read <courses|homework|exams|grades|citel|calendar> [同上选项]
      cache update [grades,courses,homework,exams]   用记住的账号后台登录并刷新（App 开着时请在 App 里刷新）
      install [--no-claude] | uninstall | doctor   安装/移除 Skill 与启动脚本，检查环境（仅桌面端）
    范围: --today | --tomorrow | --week [N] | --date YYYY-MM-DD | --from YYYY-MM-DD --to YYYY-MM-DD
          （calendar 默认今天起 7 天；courses 默认本周）
    筛选（可组合，多值用逗号或重复参数）:
      --source course,phyvlab,citel   只看这些来源的作业
      --course 关键词                  课程名包含关键词（忽略大小写）
      --kind class,lab,exam,assignment_open,assignment_discount,assignment_due   仅 calendar
      --app-filter                    套用用户在 App 作业页存的课程筛选（默认不套用）
      --all                           连同物理实验课里没约上的实验一起列出
    注意: 北京时间 00:00 截止的作业算作前一天 24:00 截止（与 App 首页一致），JSON 里 agendaDate 为归属日、start/dueTime 为实际时刻。
    通用选项: --json  输出 JSON（给 agent 用）；默认输出 Markdown
""".trimIndent()

private val prettyJson = Json { prettyPrint = true }

fun runAgentCommand(args: List<String>, provider: AgentSnapshotProvider, now: Instant): AgentOutput {
    val json = "--json" in args
    val rest = args.filter { it != "--json" }
    val command = rest.firstOrNull() ?: return AgentOutput("", AGENT_USAGE, AgentExit.USAGE)
    val (name, options) = if (command == "cache") {
        if (rest.getOrNull(1) != "read") return AgentOutput("", "cache 只支持 read 和 update。\n$AGENT_USAGE", AgentExit.USAGE)
        val target = rest.getOrNull(2) ?: return AgentOutput("", AGENT_USAGE, AgentExit.USAGE)
        target to rest.drop(3)
    } else command to rest.drop(1)
    if (name in setOf("help", "--help", "-h")) return AgentOutput(AGENT_USAGE)
    if (name !in KNOWN_COMMANDS) return AgentOutput("", "未知命令：$name\n$AGENT_USAGE", AgentExit.USAGE)

    // 参数错误必须在碰缓存（和系统安全存储）之前就报出来。
    try {
        validateOptions(options)
    } catch (error: AgentUsageException) {
        return AgentOutput("", "${error.message}\n$AGENT_USAGE", AgentExit.USAGE)
    }
    val today = now.toLocalDateTime(AGENT_TIME_ZONE).date
    val ready = when (val result = provider.load(today)) {
        is AgentSnapshotResult.Unavailable -> return AgentOutput(
            if (json) envelope(name, now, JsonNull, error = result.reason).render() else "",
            result.reason, AgentExit.NO_DATA,
        )
        is AgentSnapshotResult.Ready -> result
    }
    val snapshot = ready.snapshot
    return try {
        when (name) {
            "status" -> render(json, name, now, statusJson(ready), statusMarkdown(ready), snapshot)
            "calendar" -> {
                val range = parseRange(options, today, snapshot)
                val calendarOptions = AgentCalendarOptions(kinds = parseKinds(options), homework = homeworkOptions(options))
                val items = agentCalendar(snapshot, range.first, range.second, now, calendarOptions)
                render(json, name, now, calendarJson(items, range, snapshot), calendarMarkdown(items, range), snapshot)
            }
            "courses" -> {
                val range = parseRange(options, today, snapshot, defaultWeek = true)
                val items = agentCalendar(snapshot, range.first, range.second, now, AgentCalendarOptions(
                    kinds = setOf(AgentCalendarKind.CLASS, AgentCalendarKind.LAB),
                    homework = homeworkOptions(options),
                ))
                render(json, name, now, calendarJson(items, range, snapshot), calendarMarkdown(items, range), snapshot)
            }
            "homework" -> {
                val items = agentHomework(snapshot, homeworkOptions(options).copy(pendingOnly = "--pending" in options), now)
                render(json, name, now, homeworkJson(items, snapshot), homeworkMarkdown(items), snapshot)
            }
            "exams" -> render(json, name, now, examsJson(snapshot), examsMarkdown(snapshot), snapshot)
            "grades" -> if (!ready.gradesAllowed) {
                val reason = "成绩默认不开放给 AI Agent。需要时请在 App 设置的“AI 助手”里允许读取成绩。"
                AgentOutput(if (json) envelope(name, now, JsonNull, error = reason).render() else "", reason, AgentExit.NOT_ALLOWED)
            } else render(json, name, now, gradesJson(snapshot), gradesMarkdown(snapshot), snapshot)
            "citel" -> render(json, name, now, citelJson(snapshot), citelMarkdown(snapshot), snapshot)
            else -> AgentOutput("", AGENT_USAGE, AgentExit.USAGE)
        }
    } catch (error: AgentUsageException) {
        AgentOutput("", "${error.message}\n$AGENT_USAGE", AgentExit.USAGE)
    }
}

private val KNOWN_COMMANDS = setOf("status", "calendar", "courses", "homework", "exams", "grades", "citel", "help", "--help", "-h")

class AgentUsageException(message: String) : IllegalArgumentException(message)

private fun render(json: Boolean, name: String, now: Instant, data: JsonElement, markdown: String, snapshot: AgentSnapshot): AgentOutput {
    val fresh = freshness(snapshot, name, now)
    return AgentOutput(if (json) envelope(name, now, data, freshness = fresh).render() else fresh.banner() + markdown)
}

private fun envelope(name: String, now: Instant, data: JsonElement, error: String? = null, freshness: Freshness? = null): JsonObject =
    buildJsonObject {
        put("schemaVersion", AGENT_SCHEMA_VERSION)
        put("command", name)
        put("generatedAt", isoTime(now.epochSeconds))
        put("timeZone", AGENT_TIME_ZONE.id)
        if (error != null) put("error", error)
        if (freshness != null) put("freshness", freshness.toJson())
        put("data", data)
    }

private fun JsonObject.render(): String = prettyJson.encodeToString(JsonObject.serializer(), this) + "\n"

// region 参数

internal fun parseRange(
    options: List<String>,
    today: LocalDate,
    snapshot: AgentSnapshot,
    defaultWeek: Boolean = false,
): Pair<LocalDate, LocalDate> {
    optionValue(options, "--date")?.let { val d = parseDate(it); return d to d }
    val from = optionValue(options, "--from")
    val to = optionValue(options, "--to")
    if (from != null || to != null) {
        val start = from?.let(::parseDate) ?: today
        val end = to?.let(::parseDate) ?: start
        if (end < start) throw AgentUsageException("--to 早于 --from")
        if (end.toEpochDays() - start.toEpochDays() > 180) throw AgentUsageException("日期范围最多 180 天")
        return start to end
    }
    if ("--today" in options) return today to today
    if ("--tomorrow" in options) return today.plus(1, DateTimeUnit.DAY).let { it to it }
    val weekIndex = options.indexOf("--week")
    if (weekIndex >= 0 || defaultWeek) {
        val explicit = options.getOrNull(weekIndex + 1)?.takeIf { weekIndex >= 0 && !it.startsWith("--") }
        if (explicit != null) {
            val week = explicit.toIntOrNull() ?: throw AgentUsageException("--week 后应为周次数字")
            val start = snapshot.academicWeeks.firstOrNull { it.week == week }?.startDate
                ?: throw AgentUsageException("没有第 $week 周的日期（校历未缓存）")
            return start to start.plus(6, DateTimeUnit.DAY)
        }
        val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        return monday to monday.plus(6, DateTimeUnit.DAY)
    }
    // 默认：今天起 7 天。
    return today to today.plus(6, DateTimeUnit.DAY)
}

/** 只做语法检查，不需要缓存数据。 */
private fun validateOptions(options: List<String>) {
    listOf("--date", "--from", "--to").forEach { name -> optionValue(options, name)?.let(::parseDate) }
    optionValues(options, "--source").forEach { parseSource(it) }
    optionValues(options, "--course")
    parseKinds(options)
    val weekIndex = options.indexOf("--week")
    if (weekIndex >= 0) {
        options.getOrNull(weekIndex + 1)?.takeIf { !it.startsWith("--") }?.let {
            if (it.toIntOrNull() == null) throw AgentUsageException("--week 后应为周次数字")
        }
    }
    val unknown = options.filter { it.startsWith("--") && it !in KNOWN_OPTIONS }
    if (unknown.isNotEmpty()) throw AgentUsageException("未知选项：${unknown.joinToString(" ")}")
}

private val KNOWN_OPTIONS = setOf(
    "--date", "--from", "--to", "--today", "--tomorrow", "--week", "--source", "--course", "--kind",
    "--pending", "--all", "--app-filter",
)

private fun homeworkOptions(options: List<String>) = AgentHomeworkOptions(
    sources = optionValues(options, "--source").map(::parseSource).toSet().ifEmpty { AssignmentSource.entries.toSet() },
    courseKeywords = optionValues(options, "--course"),
    applyAppFilter = "--app-filter" in options,
    includeUnbooked = "--all" in options,
)

private fun parseKinds(options: List<String>): Set<AgentCalendarKind> =
    optionValues(options, "--kind").map { value ->
        AgentCalendarKind.entries.firstOrNull { it.wire == value.lowercase() }
            ?: throw AgentUsageException("--kind 只能是 ${AgentCalendarKind.entries.joinToString(",") { it.wire }}")
    }.toSet().ifEmpty { AgentCalendarKind.entries.toSet() }

/** 可重复、可逗号分隔：`--course 物理 --course 数据结构` 或 `--source course,citel`。 */
private fun optionValues(options: List<String>, name: String): List<String> =
    options.indices.filter { options[it] == name }.flatMap { index ->
        val value = options.getOrNull(index + 1)?.takeIf { !it.startsWith("--") }
            ?: throw AgentUsageException("$name 缺少参数")
        value.split(',', '，').map(String::trim).filter(String::isNotEmpty)
    }

private fun optionValue(options: List<String>, name: String): String? {
    val index = options.indexOf(name)
    if (index < 0) return null
    return options.getOrNull(index + 1)?.takeIf { !it.startsWith("--") }
        ?: throw AgentUsageException("$name 缺少参数")
}

private fun parseDate(value: String): LocalDate =
    runCatching { LocalDate.parse(value) }.getOrNull() ?: throw AgentUsageException("日期格式应为 YYYY-MM-DD：$value")

private fun parseSource(value: String): AssignmentSource = when (value.lowercase()) {
    "course", "course_platform", "platform" -> AssignmentSource.COURSE_PLATFORM
    "phyvlab", "physics" -> AssignmentSource.PHYVLAB
    "citel" -> AssignmentSource.CITEL
    else -> throw AgentUsageException("--source 只能是 course、phyvlab 或 citel")
}

// endregion

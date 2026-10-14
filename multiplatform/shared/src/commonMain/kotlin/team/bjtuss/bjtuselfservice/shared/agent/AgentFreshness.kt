package team.bjtuss.bjtuselfservice.shared.agent

import kotlin.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 超过这个时长的缓存算“较旧”，Skill 要求 agent 先问用户是否刷新。 */
const val AGENT_STALE_AFTER_HOURS = 4

enum class AgentDataSource(val wire: String, val label: String) {
    COURSES("courses", "课表"),
    HOMEWORK("homework", "课程平台作业"),
    EXAMS("exams", "考试"),
    GRADES("grades", "成绩"),
    PHYVLAB("phyvlab", "物理在线"),
    CITEL("citel", "CITEL"),
    PHYSICS_DEMO("physicsDemo", "演示实验"),
}

/** 每个命令实际用到的数据源；只对这些给出新旧提示。 */
internal fun sourcesFor(command: String): List<AgentDataSource> = when (command) {
    "calendar" -> listOf(AgentDataSource.COURSES, AgentDataSource.EXAMS, AgentDataSource.HOMEWORK,
        AgentDataSource.PHYVLAB, AgentDataSource.CITEL, AgentDataSource.PHYSICS_DEMO)
    "courses" -> listOf(AgentDataSource.COURSES)
    "homework" -> listOf(AgentDataSource.HOMEWORK, AgentDataSource.PHYVLAB, AgentDataSource.CITEL)
    "exams" -> listOf(AgentDataSource.EXAMS)
    "grades" -> listOf(AgentDataSource.GRADES)
    "citel" -> listOf(AgentDataSource.CITEL)
    else -> AgentDataSource.entries
}

internal data class Freshness(
    val ages: List<Pair<AgentDataSource, Long?>>,
    val now: Instant,
) {
    /** 记录了时间且超过阈值。 */
    val stale: List<AgentDataSource> = ages.filter { (_, at) -> at != null && now.epochSeconds - at > AGENT_STALE_AFTER_HOURS * 3600L }.map { it.first }
    /** 没有同步时间记录。 */
    val unknown: List<AgentDataSource> = ages.filter { it.second == null }.map { it.first }
}

internal fun freshness(snapshot: AgentSnapshot, command: String, now: Instant): Freshness =
    Freshness(sourcesFor(command).filter { it in snapshot.syncedAt }.map { it to snapshot.syncedAt[it] }, now)

internal fun Freshness.toJson(): JsonElement = buildJsonObject {
    put("staleAfterHours", AGENT_STALE_AFTER_HOURS)
    put("sources", buildJsonObject {
        ages.forEach { (source, at) ->
            put(source.wire, buildJsonObject {
                if (at != null) {
                    put("syncedAt", isoTime(at))
                    put("ageHours", ((now.epochSeconds - at) / 360L) / 10.0)
                } else put("syncedAt", null as String?)
            })
        }
    })
    put("stale", buildJsonArray { stale.forEach { add(JsonPrimitive(it.wire)) } })
    put("unknown", buildJsonArray { unknown.forEach { add(JsonPrimitive(it.wire)) } })
}

/** Markdown 顶部提示；数据都新时返回空串。 */
internal fun Freshness.banner(): String {
    if (stale.isEmpty() && unknown.isEmpty()) return ""
    val parts = buildList {
        stale.forEach { source ->
            val hours = (now.epochSeconds - ages.first { it.first == source }.second!!) / 3600
            add("${source.label}（${hours} 小时前）")
        }
        unknown.forEach { add("${it.label}（同步时间未知）") }
    }
    return "> 缓存可能不是最新：${parts.joinToString("、")}。可先打开交大自由行 App 刷新后再查看。\n\n"
}

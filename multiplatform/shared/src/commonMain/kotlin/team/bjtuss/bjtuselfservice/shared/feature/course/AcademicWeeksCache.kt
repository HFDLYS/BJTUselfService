package team.bjtuss.bjtuselfservice.shared.feature.course

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate

/**
 * 当前学期“教学周 → 周一日期”映射。App 在线拿到校历后写入，离线读取方（AI Agent 命令行）
 * 据此把课表周次换成真实日期，不必再请求校历。
 */
internal const val ACADEMIC_WEEKS_CACHE_KEY = "academic_weeks_v1"

@Serializable
private data class AcademicWeeksPayload(val weeks: List<WeekPayload>)

@Serializable
private data class WeekPayload(val week: Int, val start: String)

private val weeksJson = Json { ignoreUnknownKeys = true }

internal fun encodeAcademicWeeks(weeks: List<OccupancyWeekDate>): String? {
    val dated = weeks.mapNotNull { week -> week.startDate?.let { WeekPayload(week.week, it.toString()) } }
    if (dated.isEmpty()) return null
    return weeksJson.encodeToString(AcademicWeeksPayload.serializer(), AcademicWeeksPayload(dated.sortedBy(WeekPayload::week)))
}

internal fun decodeAcademicWeeks(value: String?): List<OccupancyWeekDate> {
    if (value.isNullOrBlank()) return emptyList()
    val payload = runCatching { weeksJson.decodeFromString(AcademicWeeksPayload.serializer(), value) }.getOrNull()
        ?: return emptyList()
    return payload.weeks.mapNotNull { week ->
        val start = runCatching { LocalDate.parse(week.start) }.getOrNull() ?: return@mapNotNull null
        OccupancyWeekDate(week.week, monthDay(start), monthDay(start.plus(6, DateTimeUnit.DAY)), start)
    }
}

/** 内容变化时才写，避免每次重组都写库。 */
internal fun CacheStore.saveAcademicWeeksIfChanged(accountScope: String, weeks: List<OccupancyWeekDate>) {
    val encoded = encodeAcademicWeeks(weeks) ?: return
    if (metadata(accountScope, ACADEMIC_WEEKS_CACHE_KEY) == encoded) return
    putMetadata(accountScope, ACADEMIC_WEEKS_CACHE_KEY, encoded)
}

internal fun CacheStore.cachedAcademicWeeks(accountScope: String): List<OccupancyWeekDate> =
    decodeAcademicWeeks(metadata(accountScope, ACADEMIC_WEEKS_CACHE_KEY))

/**
 * 没有缓存校历时的退路：假定 [currentWeek] 就是 [today] 所在周，往前后推出 1..[maxWeek] 周。
 * 只有在 currentWeek 确实对应今天时才准确，调用方需把结果标成“推算”。
 */
internal fun inferAcademicWeeks(currentWeek: Int, today: LocalDate, maxWeek: Int = 30): List<OccupancyWeekDate> {
    if (currentWeek !in 1..maxWeek) return emptyList()
    val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    val firstMonday = monday.minus((currentWeek - 1) * 7, DateTimeUnit.DAY)
    return (1..maxWeek).map { week ->
        val start = firstMonday.plus((week - 1) * 7, DateTimeUnit.DAY)
        OccupancyWeekDate(week, monthDay(start), monthDay(start.plus(6, DateTimeUnit.DAY)), start)
    }
}

private fun monthDay(date: LocalDate): String = "${date.month.ordinal + 1}/${date.day}"

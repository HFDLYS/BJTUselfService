package team.bjtuss.bjtuselfservice.shared.domain.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import team.bjtuss.bjtuselfservice.shared.domain.homework.parseSchoolLocalDateTime

/**
 * 截止时间的显示规则：恰好 00:00 截止显示为前一天 24:00。
 * 老师布置“10 月 20 日 0 点截止”的本意是 19 日当天交完；首页日程按同一规则把它归到前一天
 * （见 `homeworkDueDate` 减 1 分钟取日期）。只改显示文字，倒计时、过期判断和排序仍按真实时刻。
 */
data class DeadlineDisplay(val date: LocalDate, val clock: String) {
    val isMidnight: Boolean get() = clock == "24:00"
}

private val schoolZone = TimeZone.of("Asia/Shanghai")

fun deadlineDisplay(dateTime: LocalDateTime): DeadlineDisplay =
    if (dateTime.hour == 0 && dateTime.minute == 0 && dateTime.second == 0) {
        DeadlineDisplay(dateTime.date.minus(1, DateTimeUnit.DAY), "24:00")
    } else {
        DeadlineDisplay(dateTime.date, "${dateTime.hour.pad()}:${dateTime.minute.pad()}")
    }

/** `2026-10-19 24:00`；与原有 `yyyy-MM-dd HH:mm` 文案同格式。 */
fun formatDeadline(dateTime: LocalDateTime): String = deadlineDisplay(dateTime).let { "${it.date} ${it.clock}" }

/** 学校接口的本地时间文本（如课程平台 `endTime`）；解析不了时原样返回。 */
fun formatSchoolDeadline(raw: String): String =
    raw.trim().takeIf(String::isNotEmpty)?.let(::parseSchoolLocalDateTime)?.let(::formatDeadline) ?: raw

/** Unix 秒（CITEL、物理在线时间戳），按北京时间显示。 */
fun formatDeadlineEpoch(epochSeconds: Long): String =
    formatDeadline(Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(schoolZone))

// 物理在线（Moodle）原文在日期和时刻之间可能夹着星期（`2026年10月20日 Tuesday 00:00`），与解析器口径一致。
private val chineseDatePattern = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日(?:\s+[^\d\s]+)?\s+(\d{1,2}):(\d{2})""")

/**
 * 物理在线的中文日期（`2026年10月20日 00:00`，后面可能带星期等原文），
 * 统一成 `2026年10月19日 24:00 · 周一`。解析不了时原样返回。
 */
fun formatChineseDeadline(value: String): String {
    val match = chineseDatePattern.find(value) ?: return value
    val (year, month, day, hour, minute) = match.destructured
    val dateTime = runCatching {
        LocalDateTime(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt())
    }.getOrNull() ?: return value
    val shown = deadlineDisplay(dateTime)
    val d = shown.date
    return "${d.year}年${(d.month.ordinal + 1).pad()}月${d.day.pad()}日 ${shown.clock} · ${d.dayOfWeek.chineseLabel()}"
}

fun DayOfWeek.chineseLabel(): String = when (this) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

private fun Int.pad(): String = toString().padStart(2, '0')

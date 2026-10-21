package team.bjtuss.bjtuselfservice.utils

import team.bjtuss.bjtuselfservice.entity.CourseEntity
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * RFC 5545 course schedule exporter.
 *
 * Dates are exported in UTC so iOS/Google/Outlook do not need a VTIMEZONE block.
 * Each course row becomes one weekly repeating event; weeks without class are
 * emitted as EXDATE values while the remaining weeks share one RRULE.
 */
object IcsExporter {
    private val STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val SLOT_TIMES = listOf(
        LocalTime.of(8, 0) to LocalTime.of(9, 50),
        LocalTime.of(10, 10) to LocalTime.of(12, 0),
        LocalTime.of(12, 10) to LocalTime.of(14, 0),
        LocalTime.of(14, 10) to LocalTime.of(16, 0),
        LocalTime.of(16, 20) to LocalTime.of(18, 10),
        LocalTime.of(19, 0) to LocalTime.of(20, 50),
        LocalTime.of(21, 0) to LocalTime.of(21, 50),
    )

    fun export(
        courses: List<CourseEntity>,
        currentWeek: Int,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): String {
        val stamp = now.withZoneSameInstant(ZoneOffset.UTC).format(STAMP_FORMAT)
        val weekOneMonday = termWeekOneMonday(currentWeek, now.toLocalDate())
        val builder = StringBuilder()
        builder.append("BEGIN:VCALENDAR\r\n")
            .append("VERSION:2.0\r\n")
            .append("PRODID:-//BJTU SelfService//Course Schedule//CN\r\n")
            .append("CALSCALE:GREGORIAN\r\n")
            .append("METHOD:PUBLISH\r\n")
            .append("X-WR-CALNAME:交大自由行课表\r\n")
            .append("X-WR-TIMEZONE:Asia/Shanghai\r\n")
            .append("X-WR-CALDESC:北京交通大学本学期课表\r\n")
            .append("REFRESH-INTERVAL;VALUE=DURATION:PT1H\r\n")
            .append("X-PUBLISHED-TTL:PT1H\r\n")

        courses.forEach { course ->
            val weeks = parseWeeks(course.courseTime)
            val day = course.courseLocationIndex % 8
            val slot = course.courseLocationIndex / 8
            if (weeks.isEmpty() || day !in 1..7 || slot !in SLOT_TIMES.indices) {
                return@forEach
            }
            val (startTime, endTime) = SLOT_TIMES[slot]
            val firstWeek = weeks.first()
            val lastWeek = weeks.last()
            val firstDate = weekOneMonday.plusWeeks((firstWeek - 1).toLong()).plusDays((day - 1).toLong())
            val lastDate = weekOneMonday.plusWeeks((lastWeek - 1).toLong()).plusDays((day - 1).toLong())
            val start = toUtc(firstDate, startTime)
            val end = toUtc(firstDate, endTime)
            val until = toUtc(lastDate, startTime)
            val missingWeeks = (firstWeek..lastWeek).filter { it !in weeks }
            val uid = "course-" + course.id + "-" + course.courseLocationIndex + "@bjtuselfservice"

            builder.append("BEGIN:VEVENT\r\n")
                .append("UID:").append(uid).append("\r\n")
                .append("DTSTAMP:").append(stamp).append("\r\n")
                .append("DTSTART:").append(start).append("\r\n")
                .append("DTEND:").append(end).append("\r\n")
            if (firstWeek == lastWeek) {
                builder.append("RRULE:FREQ=WEEKLY;COUNT=1\r\n")
            } else {
                builder.append("RRULE:FREQ=WEEKLY;UNTIL=").append(until).append("\r\n")
            }
            missingWeeks.forEach { week ->
                val date = weekOneMonday.plusWeeks((week - 1).toLong())
                    .plusDays((day - 1).toLong())
                builder.append("EXDATE:").append(toUtc(date, startTime)).append("\r\n")
            }
            builder.append("SUMMARY:").append(escape(cleanCourseName(course.courseName))).append("\r\n")
                .append("LOCATION:").append(escape(course.coursePlace)).append("\r\n")
                .append("DESCRIPTION:")
                .append(escape(course.courseTeacher + " · 第" + weeks.joinToString(",") + "周"))
                .append("\r\n")
                .append("STATUS:CONFIRMED\r\n")
                .append("TRANSP:OPAQUE\r\n")
                .append("BEGIN:VALARM\r\n")
                .append("ACTION:DISPLAY\r\n")
                .append("DESCRIPTION:上课提醒\r\n")
                .append("TRIGGER:-PT15M\r\n")
                .append("END:VALARM\r\n")
                .append("END:VEVENT\r\n")
        }
        builder.append("END:VCALENDAR\r\n")
        return foldLines(builder.toString())
    }

    internal fun parseWeeks(courseTime: String): List<Int> = runCatching {
        courseTime.replace("第", "")
            .replace("周", "")
            .replace("，", ",")
            .split(",")
            .flatMap { part ->
                val value = part.trim()
                val range = Regex("""(\d+)\s*-\s*(\d+)""").find(value)
                if (range != null) {
                    val start = range.groupValues[1].toInt()
                    val end = range.groupValues[2].toInt()
                    (minOf(start, end)..maxOf(start, end)).toList()
                } else {
                    value.toIntOrNull()?.let { listOf(it) } ?: emptyList()
                }
            }
            .filter { it > 0 }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())

    internal fun termWeekOneMonday(currentWeek: Int, today: LocalDate = LocalDate.now()): LocalDate {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.minusWeeks((currentWeek.coerceAtLeast(1) - 1).toLong())
    }

    private fun toUtc(date: LocalDate, time: LocalTime): String =
        date.atTime(time)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .withZoneSameInstant(ZoneOffset.UTC)
            .format(STAMP_FORMAT)

    private fun cleanCourseName(name: String): String =
        name.removeSuffix(" [本]").removeSuffix("[本]")
            .removeSuffix(" [研]").removeSuffix("[研]")

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")

    /** RFC 5545 uses 75-octet lines with a leading space on continuations. */
    private fun foldLines(input: String): String {
        val output = StringBuilder()
        input.removeSuffix("\r\n").split("\r\n").forEach { line ->
            if (line.isEmpty()) {
                output.append("\r\n")
                return@forEach
            }
            var count = 0
            var first = true
            line.forEach { char ->
                val bytes = char.toString().toByteArray(StandardCharsets.UTF_8).size
                if (count + bytes > 73) {
                    output.append("\r\n ")
                    count = 1
                    first = false
                }
                output.append(char)
                count += bytes
            }
            output.append("\r\n")
        }
        return output.toString()
    }
}

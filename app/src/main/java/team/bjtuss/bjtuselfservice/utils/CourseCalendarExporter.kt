package team.bjtuss.bjtuselfservice.utils

import team.bjtuss.bjtuselfservice.entity.CourseEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

internal val COURSE_SLOT_RANGES = listOf(
    "08:00" to "09:50", "10:10" to "12:00", "12:10" to "14:00",
    "14:10" to "16:00", "16:20" to "18:10", "19:00" to "20:50", "21:00" to "21:50",
)

internal data class CourseCalendar(
    val text: String,
    val eventCount: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val skippedCourses: List<String>,
)

/** Finite events preserve discontinuous and odd/even teaching weeks without recurrence guesses. */
internal object CourseCalendarExporter {
    private val schoolZone = ZoneId.of("Asia/Shanghai")
    private val utc = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun weeks(value: String): Set<Int> = CourseWeeks.parse(value)

    fun export(courses: List<CourseEntity>, firstMonday: LocalDate, generatedAt: Instant = Instant.now()): CourseCalendar {
        require(firstMonday.dayOfWeek == DayOfWeek.MONDAY) { "请选择第一教学周的周一" }
        require(firstMonday.year in 1..9998) { "学期年份无效" }
        val skipped = mutableListOf<String>()
        val events = sortedMapOf<String, Pair<LocalDate, List<String>>>()
        for (course in courses) {
            val day = course.courseLocationIndex % 8
            val slot = course.courseLocationIndex / 8
            val weeks = weeks(course.courseTime)
            if (course.courseLocationIndex < 0 || day !in 1..7 || slot !in COURSE_SLOT_RANGES.indices ||
                weeks.isEmpty() || course.courseName.isBlank()) {
                skipped += course.courseName.ifBlank { "未命名课程" }
                continue
            }
            val (start, end) = COURSE_SLOT_RANGES[slot]
            for (week in weeks) {
                val date = firstMonday.plusWeeks((week - 1).toLong()).plusDays((day - 1).toLong())
                val identity = "${course.courseId.ifBlank { course.courseName }}|$firstMonday|$week|$day|$slot"
                val uid = "${UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8))}@bjtuselfservice.local"
                val lines = listOf(
                    "BEGIN:VEVENT", "UID:$uid", "DTSTAMP:${utc.format(generatedAt)}",
                    "DTSTART:${utc.format(date.atTime(LocalTime.parse(start)).atZone(schoolZone))}",
                    "DTEND:${utc.format(date.atTime(LocalTime.parse(end)).atZone(schoolZone))}",
                    "SUMMARY:${escape(course.courseName)}", "LOCATION:${escape(course.coursePlace)}",
                    "DESCRIPTION:${escape("教师：${course.courseTeacher}\n第${week}周，第${slot + 1}节")}",
                    "TRANSP:OPAQUE", "END:VEVENT",
                )
                events[uid] = date to lines
            }
        }
        val ordered = events.values.sortedWith(compareBy({ it.first }, { it.second[3] }, { it.second[1] }))
        val lines = listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//BJTUselfService//Course Calendar//ZH-CN", "CALSCALE:GREGORIAN") +
            ordered.flatMap { it.second } + "END:VCALENDAR"
        return CourseCalendar(lines.joinToString("\r\n", postfix = "\r\n") { fold(it) },
            events.size, ordered.firstOrNull()?.first, ordered.lastOrNull()?.first, skipped)
    }

    private fun escape(text: String): String = text.replace("\\", "\\\\")
        .replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\\n")
        .replace(",", "\\,").replace(";", "\\;")
        .filter { it == '\t' || it.code >= 32 && it.code != 127 }

    private fun fold(line: String): String = buildString {
        var bytes = 0
        for (point in line.codePoints().toArray()) {
            val char = String(Character.toChars(point))
            val size = char.toByteArray(Charsets.UTF_8).size
            if (bytes + size > 75) { append("\r\n "); bytes = 1 }
            append(char)
            bytes += size
        }
    }
}

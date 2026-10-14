package team.bjtuss.bjtuselfservice.shared.domain.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.course.classTimeRange
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.parseCourseWeeks
import team.bjtuss.bjtuselfservice.shared.domain.course.displayCoursePlace
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule

private const val CALENDAR_TIME_ZONE = "Asia/Shanghai"
private const val DEFAULT_EXAM_MINUTES = 120

data class CalendarExportResult(
    val ics: String,
    val courseEventCount: Int,
    val examEventCount: Int,
    val skippedExamCount: Int,
    val events: List<AcademicCalendarEvent>,
    val physicsLabEventCount: Int = 0,
    val physicsDemoEventCount: Int = 0,
)

/** Beijing-local date window covering every natural week in an academic calendar. */
data class AcademicCalendarDateRange(
    val startLocal: String,
    val endLocal: String,
) {
    init {
        require(startLocal.isNotBlank() && endLocal.isNotBlank() && startLocal < endLocal)
    }
}

/** Full date window for replacing previously exported course occurrences in this academic term. */
fun academicCalendarDateRange(academicWeeks: List<OccupancyWeekDate>): AcademicCalendarDateRange? {
    val dates = academicWeeks.mapNotNull(OccupancyWeekDate::startDate)
    val first = dates.minOrNull() ?: return null
    val endExclusive = dates.maxOrNull()?.plus(7, DateTimeUnit.DAY) ?: return null
    return AcademicCalendarDateRange(
        startLocal = first.isoLocalTime(0, 0),
        endLocal = endExclusive.isoLocalTime(0, 0),
    )
}

enum class AcademicCalendarEventKind {
    COURSE,
    EXAM,
    PHYSICS_LAB,
    PHYSICS_DEMO,
}

data class AcademicCalendarEvent(
    val stableId: String,
    val kind: AcademicCalendarEventKind,
    val title: String,
    /** 北京时间、不带时区的 ISO 本地时间，例如 `2026-09-07T08:00:00`。 */
    val startLocal: String,
    val endLocal: String,
    val location: String,
    val notes: String,
    /** 课程使用周重复系列；考试与只有一个课次的课程为 null。 */
    val recurrence: AcademicCalendarRecurrence? = null,
)

/**
 * 每周重复系列。为了同时支持系统日历的“修改后续日程”和学校校历中的停课/单双周，
 * 先以每周规则覆盖首末课次，再把不实际开课的日期列为例外。
 */
data class AcademicCalendarRecurrence(
    /** 从首个课次起按自然周计算的总次数，包含随后被 [excludedStartLocals] 排除的周。 */
    val occurrenceCount: Int,
    /** 北京本地时间；系统日历写入后会把这些 occurrence 删除为重复系列例外。 */
    val excludedStartLocals: List<String>,
    /** 最后一个实际课次的结束时间，用于 EventKit 查询完整系列范围。 */
    val lastEndLocal: String,
)

data class ParsedExamCalendarTime(
    val date: LocalDate,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val location: String,
)

/**
 * 生成 RFC 5545 兼容的 iCalendar。课程写为 RRULE 周重复系列，并用 EXDATE 精确排除
 * 停课周/单双周，因此既能在系统日历中“修改后续日程”，也不会把自然周误当教学周。
 * 考试只在能可靠提取日期与开始时间时导出。
 */
fun generateAcademicCalendarIcs(
    courses: List<Course>,
    exams: List<ExamSchedule>,
    academicWeeks: List<OccupancyWeekDate>,
    weekRange: IntRange,
    generatedAt: Instant,
    calendarName: String = "北京交通大学日程",
    physicsLabs: List<team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLab> = emptyList(),
    physicsDemos: List<team.bjtuss.bjtuselfservice.shared.feature.phydemo.PhysicsDemoBooking> = emptyList(),
    /** 第二节错峰：逸夫教学楼/思源西楼按 10:30–12:20 导出（同一课程不同地点的变体分别判断）。 */
    staggeredSecondSlot: Boolean = false,
): CalendarExportResult {
    val stamp = generatedAt.toLocalDateTime(TimeZone.UTC).let { dateTime ->
        "${dateTime.date.compact()}T${dateTime.hour.twoDigits()}${dateTime.minute.twoDigits()}${dateTime.second.twoDigits()}Z"
    }
    val weekByNumber = academicWeeks.associateBy(OccupancyWeekDate::week)
    val events = mutableListOf<AcademicCalendarEvent>()
    var courseCount = 0
    var examCount = 0
    var skippedExamCount = 0

    val groupedCourses = courses.groupBy { course ->
        CourseSeriesVariantKey(
            courseId = course.courseId,
            locationIndex = course.courseLocationIndex,
            title = team.bjtuss.bjtuselfservice.shared.domain.course.displayScheduleCourseName(course.courseName),
            teacher = course.courseTeacher,
            place = displayCoursePlace(course.coursePlace),
            isSelectionSchedule = course.isCurrentSemester,
        )
    }
    groupedCourses.entries
        .groupBy { entry -> entry.key.baseKey() }
        .entries
        .sortedWith(compareBy({ it.key.courseId }, { it.key.locationIndex }))
        .forEach { (base, variants) ->
            val sortedVariants = variants.sortedWith(
                compareBy<Map.Entry<CourseSeriesVariantKey, List<Course>>>(
                    { entry -> entry.value.flatMap { parseCourseWeeks(it.courseTime) }.minOrNull() ?: Int.MAX_VALUE },
                    { it.key.place },
                    { it.key.teacher },
                    { it.key.title },
                ),
            )
            sortedVariants.forEachIndexed variantLoop@{ variantIndex, entry ->
                val slotIndex = base.locationIndex / 8
                val weekdayIndex = base.locationIndex % 8 - 1
                val time = entry.value.first().classTimeRange(staggeredSecondSlot)?.parseTimeRange() ?: return@variantLoop
                if (weekdayIndex !in 0..6) return@variantLoop
                val weeksAndDates = entry.value
                    .flatMap { parseCourseWeeks(it.courseTime) }
                    .asSequence()
                    .filter(weekRange::contains)
                    .distinct()
                    .mapNotNull { week ->
                        weekByNumber[week]?.startDate
                            ?.plus(weekdayIndex, DateTimeUnit.DAY)
                            ?.let { date -> CourseOccurrence(week, date) }
                    }
                    .sortedBy(CourseOccurrence::date)
                    .toList()
                if (weeksAndDates.isEmpty()) return@variantLoop

                val first = weeksAndDates.first()
                val last = weeksAndDates.last()
                val actualDates = weeksAndDates.mapTo(mutableSetOf(), CourseOccurrence::date)
                val excludedStarts = mutableListOf<String>()
                var recurrenceCount = 0
                var cursor = first.date
                while (cursor <= last.date) {
                    recurrenceCount += 1
                    if (cursor !in actualDates) {
                        excludedStarts += cursor.isoLocalTime(time.startHour, time.startMinute)
                    }
                    cursor = cursor.plus(7, DateTimeUnit.DAY)
                }
                val partSuffix = if (sortedVariants.size == 1) "" else "-part-${variantIndex + 1}"
                val recurrence = if (recurrenceCount > 1) {
                    AcademicCalendarRecurrence(
                        occurrenceCount = recurrenceCount,
                        excludedStartLocals = excludedStarts,
                        lastEndLocal = last.date.isoLocalTime(time.endHour, time.endMinute),
                    )
                } else {
                    null
                }
                events += AcademicCalendarEvent(
                    // 一个课程时间位置对应一个重复系列；展示字段变化时仍更新同一系列。
                    stableId = "course-${base.courseId.safeUidPart()}-$weekdayIndex-$slotIndex$partSuffix",
                    kind = AcademicCalendarEventKind.COURSE,
                    title = entry.key.title,
                    startLocal = first.date.isoLocalTime(time.startHour, time.startMinute),
                    endLocal = first.date.isoLocalTime(time.endHour, time.endMinute),
                    location = entry.key.place,
                    notes = buildString {
                        append("教师：")
                        append(entry.key.teacher.ifBlank { "未提供" })
                        append("；教学周：")
                        append(weeksAndDates.joinToString(",") { it.week.toString() })
                        append("；")
                        append(if (entry.key.isSelectionSchedule) "选课课表" else "本学期课表")
                    },
                    recurrence = recurrence,
                )
                courseCount += weeksAndDates.size
            }
        }

    exams.forEachIndexed { index, exam ->
        val parsed = parseExamCalendarTime(exam.examTimeAndPlace)
        if (parsed == null) {
            skippedExamCount += 1
            return@forEachIndexed
        }
        events += AcademicCalendarEvent(
            stableId = "exam-${exam.courseName.safeUidPart()}-${parsed.date.compact()}-$index",
            kind = AcademicCalendarEventKind.EXAM,
            title = "${exam.courseName}（${exam.examType.ifBlank { "考试" }}）",
            startLocal = parsed.date.isoLocalTime(parsed.startHour, parsed.startMinute),
            endLocal = parsed.date.isoLocalTime(parsed.endHour, parsed.endMinute),
            location = parsed.location,
            notes = listOf(
                exam.examType.takeIf(String::isNotBlank)?.let { "类型：$it" },
                exam.examStatus.takeIf(String::isNotBlank)?.let { "状态：$it" },
                exam.detail.takeIf(String::isNotBlank)?.let { "备注：$it" },
                "原始安排：${exam.examTimeAndPlace}",
            ).filterNotNull().joinToString("；"),
        )
        examCount += 1
    }

    var physicsLabCount = 0
    val datesInRange = academicWeeks.filter { it.week in weekRange }.mapNotNull { it.startDate }
    physicsLabs.forEach { lab ->
        val time = lab.timeRange?.parseTimeRange() ?: return@forEach
        lab.dates.forEach dateLoop@{ date ->
            if (datesInRange.isEmpty() || date < datesInRange.min() || date > datesInRange.max().plus(6, DateTimeUnit.DAY)) return@dateLoop
            events += AcademicCalendarEvent(
                // 归入课表管理范围：再次导入或关闭包含实验时，系统日历仅替换本应用的课表事件。
                stableId = "course-physicslab-${lab.date}-${lab.period}-${lab.name.safeUidPart()}-$date",
                kind = AcademicCalendarEventKind.PHYSICS_LAB,
                title = "${lab.name}（物理实验）",
                startLocal = date.isoLocalTime(time.startHour, time.startMinute),
                endLocal = date.isoLocalTime(time.endHour, time.endMinute),
                location = lab.location,
                notes = "教师：${lab.teacher}；第${lab.period}时段；共${lab.weekCount}周",
            )
            physicsLabCount += 1
        }
    }
    var physicsDemoCount = 0
    physicsDemos.distinct().forEach { demo ->
        val time = demo.timeRange.parseTimeRange() ?: return@forEach
        if (datesInRange.isEmpty() || demo.date < datesInRange.min() || demo.date > datesInRange.max().plus(6, DateTimeUnit.DAY)) return@forEach
        events += AcademicCalendarEvent(
            stableId = "course-phydemo-${demo.date}-${demo.period}-${demo.name.safeUidPart()}",
            kind = AcademicCalendarEventKind.PHYSICS_DEMO,
            title = "${demo.name}（演示实验）",
            startLocal = demo.date.isoLocalTime(time.startHour, time.startMinute),
            endLocal = demo.date.isoLocalTime(time.endHour, time.endMinute),
            location = "第七教学楼 ${demo.location}",
            notes = listOf("大学物理演示实验", demo.teacher.takeIf { it.isNotBlank() }?.let { "助教：$it" }).filterNotNull().joinToString("；"),
        )
        physicsDemoCount += 1
    }
    val ics = buildString {
        append("BEGIN:VCALENDAR\r\n")
        append("VERSION:2.0\r\n")
        append("PRODID:-//BJTU SelfService KMP//M12//ZH-CN\r\n")
        append("CALSCALE:GREGORIAN\r\n")
        append("METHOD:PUBLISH\r\n")
        append("X-WR-CALNAME:${calendarName.icsEscape()}\r\n")
        append("X-WR-TIMEZONE:$CALENDAR_TIME_ZONE\r\n")
        append(fixedChinaTimeZone())
        events.forEach { event -> append(calendarEvent(event, stamp)) }
        append("END:VCALENDAR\r\n")
    }
    return CalendarExportResult(ics, courseCount, examCount, skippedExamCount, events, physicsLabCount, physicsDemoCount)
}

/**
 * 宽容解析学校考试文案中的 `yyyy-MM-dd HH:mm[-HH:mm]` 与中文年月日变体。
 * 未提供结束时间时按两小时生成；不把无法确定日期/开始时间的文案猜成全天事件。
 */
fun parseExamCalendarTime(value: String): ParsedExamCalendarTime? {
    val dateMatch = EXAM_DATE.find(value) ?: return null
    val year = dateMatch.groupValues[1].toIntOrNull() ?: return null
    val month = dateMatch.groupValues[2].toIntOrNull() ?: return null
    val day = dateMatch.groupValues[3].toIntOrNull() ?: return null
    val date = try {
        LocalDate(year, month, day)
    } catch (_: IllegalArgumentException) {
        return null
    }
    val tail = value.substring(dateMatch.range.last + 1)
    val times = EXAM_CLOCK.findAll(tail).take(2).mapNotNull { match ->
        val hour = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val minute = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        if (hour !in 0..23 || minute !in 0..59) null else hour to minute
    }.toList()
    val start = times.firstOrNull() ?: return null
    val end = times.getOrNull(1) ?: addMinutes(start.first, start.second, DEFAULT_EXAM_MINUTES)
    val location = tail
        .replace(EXAM_CLOCK, " ")
        .replace(EXAM_TIME_JOINER, " ")
        .replace(EXAM_WEEKDAY, " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '—', '~', '～', '至', '，', ',')
    return ParsedExamCalendarTime(
        date = date,
        startHour = start.first,
        startMinute = start.second,
        endHour = end.first,
        endMinute = end.second,
        location = location,
    )
}

private val EXAM_DATE = Regex("(\\d{4})\\s*(?:[-/.]|年)\\s*(\\d{1,2})\\s*(?:[-/.]|月)\\s*(\\d{1,2})\\s*日?")
private val EXAM_CLOCK = Regex("(\\d{1,2})\\s*[:：]\\s*(\\d{2})")
private val EXAM_TIME_JOINER = Regex("\\s*(?:-|—|~|～|至)\\s*")
private val EXAM_WEEKDAY = Regex("[（(]\\s*(?:星期|周)?[一二三四五六日天]\\s*[)）]")

private data class TimeRange(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
)

private data class CourseSeriesBaseKey(
    val courseId: String,
    val locationIndex: Int,
)

private data class CourseSeriesVariantKey(
    val courseId: String,
    val locationIndex: Int,
    val title: String,
    val teacher: String,
    val place: String,
    val isSelectionSchedule: Boolean,
) {
    fun baseKey(): CourseSeriesBaseKey = CourseSeriesBaseKey(courseId, locationIndex)
}

private data class CourseOccurrence(
    val week: Int,
    val date: LocalDate,
)

private fun String.parseTimeRange(): TimeRange? {
    val matches = EXAM_CLOCK.findAll(this).take(2).toList()
    if (matches.size != 2) return null
    val values = matches.map { match ->
        (match.groupValues[1].toIntOrNull() ?: return null) to
            (match.groupValues[2].toIntOrNull() ?: return null)
    }
    return TimeRange(values[0].first, values[0].second, values[1].first, values[1].second)
}

private fun calendarEvent(event: AcademicCalendarEvent, stamp: String): String = buildString {
    append("BEGIN:VEVENT\r\n")
    append("UID:${event.stableId.icsEscape()}@bjtuss\r\n")
    append("DTSTAMP:$stamp\r\n")
    append("DTSTART;TZID=$CALENDAR_TIME_ZONE:${event.startLocal.toIcsLocal()}\r\n")
    append("DTEND;TZID=$CALENDAR_TIME_ZONE:${event.endLocal.toIcsLocal()}\r\n")
    event.recurrence?.let { recurrence ->
        append("RRULE:FREQ=WEEKLY;COUNT=${recurrence.occurrenceCount}\r\n")
        recurrence.excludedStartLocals.forEach { excluded ->
            append("EXDATE;TZID=$CALENDAR_TIME_ZONE:${excluded.toIcsLocal()}\r\n")
        }
    }
    append("SUMMARY:${event.title.icsEscape()}\r\n")
    if (event.location.isNotBlank()) append("LOCATION:${event.location.icsEscape()}\r\n")
    if (event.notes.isNotBlank()) append("DESCRIPTION:${event.notes.icsEscape()}\r\n")
    append("STATUS:CONFIRMED\r\n")
    append("END:VEVENT\r\n")
}

private fun fixedChinaTimeZone(): String = buildString {
    append("BEGIN:VTIMEZONE\r\n")
    append("TZID:$CALENDAR_TIME_ZONE\r\n")
    append("X-LIC-LOCATION:$CALENDAR_TIME_ZONE\r\n")
    append("BEGIN:STANDARD\r\n")
    append("TZOFFSETFROM:+0800\r\n")
    append("TZOFFSETTO:+0800\r\n")
    append("TZNAME:CST\r\n")
    append("DTSTART:19700101T000000\r\n")
    append("END:STANDARD\r\n")
    append("END:VTIMEZONE\r\n")
}

private fun String.icsEscape(): String = replace("\\", "\\\\")
    .replace("\r\n", "\\n")
    .replace("\n", "\\n")
    .replace(",", "\\,")
    .replace(";", "\\;")

private fun String.safeUidPart(): String = lowercase()
    .map { character -> if (character.isLetterOrDigit()) character else '-' }
    .joinToString("")
    .trim('-')
    .take(40)
    .ifBlank { "item" }

private fun LocalDate.compact(): String =
    "${year}${month.ordinal.plus(1).twoDigits()}${day.twoDigits()}"

private fun LocalDate.isoLocalTime(hour: Int, minute: Int): String =
    "$this" + "T${hour.twoDigits()}:${minute.twoDigits()}:00"

private fun String.toIcsLocal(): String = replace("-", "").replace(":", "")

private fun Int.twoDigits(): String = toString().padStart(2, '0')

private fun addMinutes(hour: Int, minute: Int, duration: Int): Pair<Int, Int> {
    val total = hour * 60 + minute + duration
    return (total / 60).coerceAtMost(23) to (total % 60)
}

package team.bjtuss.bjtuselfservice.shared.feature.course

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.eventDates
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.SLOT_TIME_RANGES
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.calendar.parseExamCalendarTime
import team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLab

/** 只在展示边界生成网格片段；原始课表与日历导出保持独立的数据模型。 */
fun scheduleEventCourses(exams: List<ExamSchedule>, labs: List<PhysicsLab>, weeks: List<OccupancyWeekDate>,
    demos: List<team.bjtuss.bjtuselfservice.shared.feature.phydemo.PhysicsDemoBooking> = emptyList()): List<Course> {
    val first = weeks.mapNotNull { it.startDate }.minOrNull() ?: return emptyList()
    val last = weeks.mapNotNull { it.startDate }.maxOrNull()?.plus(6, DateTimeUnit.DAY) ?: return emptyList()
    val result = mutableListOf<Course>()
    fun add(dates: List<LocalDate>, range: String, name: String, teacher: String, location: String, kind: String, typeLabel: String? = null) {
        val activeDates = dates.filter { it >= first && it <= last }
        val date = activeDates.firstOrNull() ?: return
        val start = minuteOfDay(range.substringBefore('-')) ?: return
        val end = minuteOfDay(range.substringAfter('-')) ?: return
        if (end <= start) return
        val eventWeeks = activeDates.map { eventDate ->
            weeks.firstOrNull { it.startDate?.let { first -> eventDate >= first && eventDate <= first.plus(6, DateTimeUnit.DAY) } == true }?.week ?: 0
        }.distinct().sorted()
        val id = -("$kind|${dates.first()}|$range|$name".hashCode() and Int.MAX_VALUE).coerceAtLeast(1)
        SLOT_TIME_RANGES.forEachIndexed { slot, time ->
            val slotStart = minuteOfDay(time.substringBefore('-')) ?: return@forEachIndexed
            val slotEnd = minuteOfDay(time.substringAfter('-')) ?: return@forEachIndexed
            if (start < slotEnd && end > slotStart) {
                result += Course(id, "$kind-$id", name, teacher, slot * 8 + date.dayOfWeek.isoDayNumber,
                    eventWeeks.joinToString(",") { "第${it}周" }, location, false, kind, dates.first().toString(), range,
                    ((maxOf(start, slotStart) - slotStart).toFloat() / (slotEnd - slotStart)),
                    ((minOf(end, slotEnd) - maxOf(start, slotStart)).toFloat() / (slotEnd - slotStart)),
                    scheduleEventDates = dates.map(LocalDate::toString), scheduleEventTypeLabel = typeLabel)
            }
        }
    }
    exams.forEach { exam ->
        val parsed = parseExamCalendarTime(exam.examTimeAndPlace) ?: return@forEach
        fun time(h: Int, m: Int) = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
        add(listOf(parsed.date), "${time(parsed.startHour, parsed.startMinute)}-${time(parsed.endHour, parsed.endMinute)}", "${team.bjtuss.bjtuselfservice.shared.domain.course.displayScheduleCourseName(exam.courseName)}（考试）", "", parsed.location, "exam")
    }
    labs.distinct().forEach { lab ->
        lab.timeRange?.let { range ->
            val suffix = if (lab.type.weekCount == 2) lab.type.label else "实验"
            add(lab.dates, range, "${lab.name}（$suffix）", lab.teacher, lab.location, "physicslab", lab.type.label)
        }
    }
    demos.distinct().forEach { demo ->
        add(listOf(demo.date), demo.timeRange, "${demo.name}（演示实验）", demo.teacher,
            "第七教学楼 ${demo.location}", "phydemo", "演示实验")
    }
    return result
}

internal fun minuteOfDay(time: String): Int? {
    val parts = time.split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return hour * 60 + minute
}

fun CourseScheduleUiState.coursesForPage(week: Int?, start: LocalDate?): List<Course> {
    if (week == 0 && start == null) return scheduleCourses
    val ordinary = if (week != null) team.bjtuss.bjtuselfservice.shared.domain.course.coursesForWeek(scheduleCourses.filter { it.scheduleEventKind == null }, week) else emptyList()
    val events = if (start != null) supplementalCourses.filter { it.eventDates.any { value -> LocalDate.parse(value).let { date -> date >= start && date <= start.plus(6, DateTimeUnit.DAY) } } }
        else team.bjtuss.bjtuselfservice.shared.domain.course.coursesForWeek(supplementalCourses, week ?: 0)
    return ordinary + events
}

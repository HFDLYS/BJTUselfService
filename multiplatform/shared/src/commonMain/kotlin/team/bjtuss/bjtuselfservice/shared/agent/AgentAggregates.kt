package team.bjtuss.bjtuselfservice.shared.agent

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import team.bjtuss.bjtuselfservice.shared.domain.homework.parseSchoolLocalDateTime
import team.bjtuss.bjtuselfservice.shared.feature.assignment.AggregatedAssignment
import team.bjtuss.bjtuselfservice.shared.feature.assignment.AssignmentSource
import team.bjtuss.bjtuselfservice.shared.feature.assignment.aggregateAssignments
import team.bjtuss.bjtuselfservice.shared.feature.course.scheduleEventCourses
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeSchedulePresentation
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabEnrollment
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.phyVlabEnrollment
import team.bjtuss.bjtuselfservice.shared.feature.widget.buildCourseWidgetSnapshot

/** 学校作息按北京时间；Agent 输出统一用这个时区，不随电脑时区漂移。 */
val AGENT_TIME_ZONE: TimeZone = TimeZone.of("Asia/Shanghai")

data class AgentHomeworkItem(
    val assignment: AggregatedAssignment,
    val title: String,
    val openTime: Long?,
    val enrollment: PhyVlabEnrollment,
    val matchedBooking: String?,
)

data class AgentHomeworkOptions(
    val pendingOnly: Boolean = false,
    val sources: Set<AssignmentSource> = AssignmentSource.entries.toSet(),
    /** 课程名包含任一关键词即保留（忽略大小写）；空表示不按课程筛。 */
    val courseKeywords: List<String> = emptyList(),
    /** true：套用用户在 App 作业页存的按课程筛选。默认不套用，由调用方（agent）自己筛。 */
    val applyAppFilter: Boolean = false,
    /** true：连同物理实验课里没约上的实验一起列出。 */
    val includeUnbooked: Boolean = false,
)

/** 三个来源的作业：课程平台、物理在线、CITEL；与 App 作业聚合页同一套数据。 */
fun agentHomework(snapshot: AgentSnapshot, options: AgentHomeworkOptions, now: Instant): List<AgentHomeworkItem> {
    val physical = snapshot.phyVlab?.activities.orEmpty()
    val aggregated = aggregateAssignments(snapshot.homework, physical, snapshot.citelTasks, AGENT_TIME_ZONE)
    return aggregated.asSequence()
        .filter { it.source in options.sources }
        .filter { !options.applyAppFilter || snapshot.assignmentFilters.forSource(it.source).matches(it.courseId) }
        .filter { matchesCourse(it.courseName, options.courseKeywords) }
        .map { assignment ->
            val enrollment = assignment.physical?.let { phyVlabEnrollment(it, snapshot.physicsLabs) }
            AgentHomeworkItem(
                assignment = assignment,
                title = assignment.homework?.title ?: assignment.physical?.title ?: assignment.citel?.title.orEmpty(),
                openTime = when {
                    assignment.homework != null -> parseSchoolLocalDateTime(assignment.homework.openDate)
                        ?.toInstant(AGENT_TIME_ZONE)?.epochSeconds
                    assignment.physical != null -> assignment.physical.openTimestamp
                    else -> assignment.citel?.openTime
                },
                enrollment = enrollment?.enrollment ?: PhyVlabEnrollment.NOT_APPLICABLE,
                matchedBooking = enrollment?.matchedBooking,
            )
        }
        .filter { options.includeUnbooked || it.enrollment != PhyVlabEnrollment.NOT_BOOKED }
        .filter { !options.pendingOnly || (!it.assignment.submitted && (it.assignment.dueTime?.let { due -> due > now.epochSeconds } != false)) }
        .sortedWith(compareBy({ it.assignment.dueTime?.let(::agendaSortKey) ?: Long.MAX_VALUE }, { it.title }))
        .toList()
}

internal fun matchesCourse(courseName: String, keywords: List<String>): Boolean =
    keywords.isEmpty() || keywords.any { courseName.contains(it.trim(), ignoreCase = true) }

/** 北京时间恰好 00:00 的截止，与首页一致归到前一天（相当于前一天 24:00）。 */
fun isMidnightDeadline(epochSeconds: Long): Boolean =
    Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(AGENT_TIME_ZONE).let { it.hour == 0 && it.minute == 0 && it.second == 0 }

/** 截止所属的日程日：零点截止算前一天。 */
fun deadlineAgendaDate(epochSeconds: Long): LocalDate {
    val date = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(AGENT_TIME_ZONE).date
    return if (isMidnightDeadline(epochSeconds)) date.minus(1, DateTimeUnit.DAY) else date
}

/** 排序用：零点截止排在前一天的最后。 */
private fun agendaSortKey(epochSeconds: Long): Long = if (isMidnightDeadline(epochSeconds)) epochSeconds - 1 else epochSeconds

enum class AgentCalendarKind(val wire: String) {
    CLASS("class"), LAB("lab"), EXAM("exam"),
    ASSIGNMENT_OPEN("assignment_open"), ASSIGNMENT_DISCOUNT("assignment_discount"), ASSIGNMENT_DUE("assignment_due"),
}

data class AgentCalendarItem(
    val kind: AgentCalendarKind,
    val title: String,
    /** Unix 秒，实际时刻。 */
    val start: Long,
    val end: Long? = null,
    val location: String? = null,
    val teacher: String? = null,
    val courseName: String? = null,
    val source: String? = null,
    val done: Boolean? = null,
    val week: Int? = null,
    /** 归属的日程日；零点截止为前一天，其余等于 start 的日期。 */
    val agendaDate: LocalDate,
    val midnightDeadline: Boolean = false,
) {
    internal val sortKey: Long get() = if (midnightDeadline) start - 1 else start
}

data class AgentCalendarOptions(
    val kinds: Set<AgentCalendarKind> = AgentCalendarKind.entries.toSet(),
    val homework: AgentHomeworkOptions = AgentHomeworkOptions(),
)

/** [from]..[to] 含首尾两天。课程、实验、考试按实际上课时间；作业按开放/折扣/截止时刻（零点截止归前一天）。 */
fun agentCalendar(
    snapshot: AgentSnapshot,
    from: LocalDate,
    to: LocalDate,
    now: Instant,
    options: AgentCalendarOptions = AgentCalendarOptions(),
): List<AgentCalendarItem> {
    require(to >= from) { "结束日期早于开始日期" }
    val dayCount = (to.toEpochDays() - from.toEpochDays()).toInt() + 1
    val events = scheduleEventCourses(snapshot.exams, snapshot.physicsLabs.orEmpty(), snapshot.academicWeeks, snapshot.physicsDemos.orEmpty())
    val today = now.toLocalDateTime(AGENT_TIME_ZONE).date
    val widget = buildCourseWidgetSnapshot(
        schedule = HomeSchedulePresentation(
            courses = snapshot.courses,
            academicWeeks = snapshot.academicWeeks,
            currentWeek = snapshot.currentWeek,
            today = today,
            supplementalCourses = events,
            staggeredClassTimes = snapshot.preferences.staggeredSecondSlot,
        ),
        startDate = from,
        generatedAtEpochSeconds = now.epochSeconds,
        dayCount = dayCount,
    )
    val sessions = widget.days.flatMap { day ->
        val date = LocalDate.parse(day.date)
        val midnight = date.atStartOfDayIn(AGENT_TIME_ZONE).epochSeconds
        day.sessions.map { session ->
            AgentCalendarItem(
                kind = when (session.kind) {
                    "exam" -> AgentCalendarKind.EXAM
                    "lab" -> AgentCalendarKind.LAB
                    else -> AgentCalendarKind.CLASS
                },
                title = session.name,
                start = midnight + session.startMinute * 60L,
                end = midnight + session.endMinute * 60L,
                location = session.place.ifBlank { null },
                teacher = session.teacher.ifBlank { null },
                week = day.week,
                agendaDate = date,
            )
        }
    }.filter { matchesCourse(it.title, options.homework.courseKeywords) }
    val assignments = agentHomework(snapshot, options.homework.copy(pendingOnly = false), now).flatMap { item ->
        val a = item.assignment
        buildList {
            fun add(kind: AgentCalendarKind, time: Long?) {
                if (time == null) return
                val midnightDue = kind == AgentCalendarKind.ASSIGNMENT_DUE && isMidnightDeadline(time)
                val day = if (midnightDue) deadlineAgendaDate(time) else Instant.fromEpochSeconds(time).toLocalDateTime(AGENT_TIME_ZONE).date
                if (day < from || day > to) return
                add(AgentCalendarItem(kind, item.title, time, courseName = a.courseName, source = a.source.wire,
                    done = a.submitted, agendaDate = day, midnightDeadline = midnightDue))
            }
            add(AgentCalendarKind.ASSIGNMENT_OPEN, item.openTime)
            a.citel?.discountTime?.takeIf { it != a.dueTime }?.let { add(AgentCalendarKind.ASSIGNMENT_DISCOUNT, it) }
            add(AgentCalendarKind.ASSIGNMENT_DUE, a.dueTime)
        }
    }
    return (sessions + assignments)
        .filter { it.kind in options.kinds }
        .sortedWith(compareBy({ it.agendaDate }, { it.sortKey }, { it.end ?: it.start }, { it.title }))
}

val AssignmentSource.wire: String
    get() = when (this) {
        AssignmentSource.COURSE_PLATFORM -> "course_platform"
        AssignmentSource.PHYVLAB -> "phyvlab"
        AssignmentSource.CITEL -> "citel"
    }

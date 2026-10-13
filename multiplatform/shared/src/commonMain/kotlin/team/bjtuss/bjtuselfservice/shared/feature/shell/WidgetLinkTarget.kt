package team.bjtuss.bjtuselfservice.shared.feature.shell

import kotlinx.datetime.LocalDate
import team.bjtuss.bjtuselfservice.shared.domain.calendar.parseExamCalendarTime
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.displayScheduleCourseName
import team.bjtuss.bjtuselfservice.shared.domain.course.eventDates
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.feature.widget.WidgetLink

/** 小组件链接落到应用里的具体位置。 */
internal sealed interface WidgetLinkTarget {
    data class Schedule(val date: LocalDate?) : WidgetLinkTarget
    /** 普通课程与实验：课程表定位到该日并打开详情 sheet。 */
    data class Course(val courseId: Int, val date: LocalDate?) : WidgetLinkTarget
    /** 考试：考试安排页的该考试详情。 */
    data class Exam(val examId: Int) : WidgetLinkTarget
}

/**
 * 把链接解析成目标。课程按课表 id 查找（含实验/考试事件）；考试事件再按“日期 + 课程名”对回考试安排。
 * 数据里找不到时返回 null，由调用方决定等待还是退回课程表。
 */
internal fun resolveWidgetLink(
    link: WidgetLink,
    courses: List<Course>,
    exams: List<ExamSchedule>,
): WidgetLinkTarget? = when (link) {
    is WidgetLink.Schedule -> WidgetLinkTarget.Schedule(link.date)
    is WidgetLink.Course -> {
        val course = courses.firstOrNull { it.id == link.id }
        when {
            course == null -> null
            course.scheduleEventKind == "exam" -> exams.firstOrNull { it.matchesExamEvent(course) }
                ?.let { WidgetLinkTarget.Exam(it.id) }
            else -> WidgetLinkTarget.Course(course.id, link.date)
        }
    }
}

/** 与 scheduleEventCourses 生成考试事件的规则一致：名称加“（考试）”，日期取解析出的考试日。 */
private fun ExamSchedule.matchesExamEvent(event: Course): Boolean {
    val date = parseExamCalendarTime(examTimeAndPlace)?.date ?: return false
    return date.toString() in event.eventDates &&
        "${displayScheduleCourseName(courseName)}（考试）" == event.courseName
}

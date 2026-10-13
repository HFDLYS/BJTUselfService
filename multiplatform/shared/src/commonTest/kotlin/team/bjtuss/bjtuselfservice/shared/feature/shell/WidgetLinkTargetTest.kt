package team.bjtuss.bjtuselfservice.shared.feature.shell

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.feature.course.scheduleEventCourses
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.feature.widget.WidgetLink

class WidgetLinkTargetTest {
    private val monday = LocalDate(2026, 10, 12)
    private val course = Course(courseId = "M1001001", courseName = "离散数学", courseTeacher = "教师",
        courseLocationIndex = 9, courseTime = "第1-16周", coursePlace = "思源楼,SY101", isCurrentSemester = false).copy(id = 42)

    @Test fun courseAndLabLinksOpenScheduleDetail() {
        val target = resolveWidgetLink(WidgetLink.Course(42, monday), listOf(course), emptyList())
        assertEquals(WidgetLinkTarget.Course(42, monday), target)
        assertEquals(WidgetLinkTarget.Schedule(monday), resolveWidgetLink(WidgetLink.Schedule(monday), emptyList(), emptyList()))
        assertNull(resolveWidgetLink(WidgetLink.Course(7, monday), listOf(course), emptyList()))
    }

    @Test fun examEventLinkOpensMatchingExamDetail() {
        val exams = listOf(
            ExamSchedule(id = 3, examType = "期末", courseName = "离散数学", examTimeAndPlace = "2026-10-14 14:00-16:00 思源楼 SY101",
                examStatus = "", detail = ""),
        )
        val weeks = listOf(OccupancyWeekDate(5, "10/12", "10/18", monday))
        val event = scheduleEventCourses(exams, emptyList(), weeks).first()
        assertEquals(WidgetLinkTarget.Exam(3), resolveWidgetLink(WidgetLink.Course(event.id, null), listOf(event), exams))
        assertNull(resolveWidgetLink(WidgetLink.Course(event.id, null), listOf(event), emptyList()))
    }
}

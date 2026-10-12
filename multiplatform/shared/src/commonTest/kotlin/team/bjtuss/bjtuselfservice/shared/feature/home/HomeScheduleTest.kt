package team.bjtuss.bjtuselfservice.shared.feature.home

import kotlinx.datetime.LocalDate
import kotlin.test.*
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate

class HomeScheduleTest {
    private fun course(name: String, index: Int, weeks: String = "第1-5周", selection: Boolean = false) =
        Course(courseId = name, courseName = name, courseTeacher = "教师", courseLocationIndex = index, courseTime = weeks, coursePlace = "教室", isCurrentSemester = selection)
    @Test fun officialTeachingWeeksExcludeHolidayAndSelectionScheduleAndSortPeriods() {
        val schedule = HomeSchedulePresentation(
            courses = listOf(course("下午", 25), course("上午", 1), course("下学期", 1, selection = true), course("别的星期", 2)),
            academicWeeks = listOf(OccupancyWeekDate(3, "9/21", "9/27", LocalDate(2026, 9, 21)), OccupancyWeekDate(4, "10/12", "10/18", LocalDate(2026, 10, 12))),
        )
        assertEquals(listOf("上午", "下午"), homeCoursesOnDate(schedule, LocalDate(2026, 10, 12)).map { it.courseName })
        assertTrue(homeCoursesOnDate(schedule, LocalDate(2026, 10, 5)).isEmpty())
    }
    @Test fun unsynchronizedWeekDoesNotShowAllCourses() {
        assertTrue(homeCoursesOnDate(HomeSchedulePresentation(courses = listOf(course("课程", 1))), LocalDate(2026, 10, 12)).isEmpty())
    }
    @Test fun newlySyncedPhysicsLabsAppearWithoutRebuildingTheHomeAgenda() {
        val date = LocalDate(2026, 11, 20)
        val weeks = listOf(
            OccupancyWeekDate(10, "11/16", "11/22", LocalDate(2026, 11, 16)),
            OccupancyWeekDate(11, "11/23", "11/29", LocalDate(2026, 11, 23)),
        )
        val schedule = HomeSchedulePresentation(academicWeeks = weeks, currentWeek = 10, today = date)
        val base = team.bjtuss.bjtuselfservice.shared.domain.home.buildHomeAgenda(
            emptyList(), emptyList(), date, kotlinx.datetime.LocalDateTime(2026, 11, 20, 9, 0), kotlinx.datetime.TimeZone.UTC,
        )
        assertEquals(0, base.withCourses(schedule).days.single { it.date == date }.eventCount)
        val lab = team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLab(date, 4, "专题测试实验", "实验楼", "测试教师", 2)
        val fragments = team.bjtuss.bjtuselfservice.shared.feature.course.scheduleEventCourses(emptyList(), listOf(lab), weeks)
        assertTrue(fragments.count { it.scheduleEventDate == date.toString() } > 1)
        val synced = schedule.copy(supplementalCourses = fragments)
        val day = base.withCourses(synced).days.single { it.date == date }
        assertEquals(1, day.physicsLabCourses.size)
        assertEquals(1, day.eventCount)
        assertTrue(day.courses.isEmpty())
        assertEquals("13:00-16:20", day.physicsLabCourses.single().scheduleEventTime)
        assertEquals("实验楼", day.physicsLabCourses.single().coursePlace)
        assertEquals("测试教师", day.physicsLabCourses.single().courseTeacher)
        assertEquals(1, homePhysicsLabsOnDate(synced, LocalDate(2026, 11, 27)).size)
        assertTrue(homePhysicsLabsOnDate(synced, LocalDate(2026, 11, 19)).isEmpty())
        assertEquals(0, base.withCourses(schedule).days.single { it.date == date }.eventCount)
    }

    @Test fun physicsDemoBookingsShareTheHomeExperimentEntry() {
        val date = LocalDate(2026, 10, 28)
        val weeks = listOf(OccupancyWeekDate(7, "10/26", "11/1", LocalDate(2026, 10, 26)))
        val demo = team.bjtuss.bjtuselfservice.shared.feature.phydemo.PhysicsDemoBooking("近代与综合", date, 5)
        val fragments = team.bjtuss.bjtuselfservice.shared.feature.course.scheduleEventCourses(emptyList(), emptyList(), weeks, listOf(demo))
        val schedule = HomeSchedulePresentation(academicWeeks = weeks, currentWeek = 7, today = date, supplementalCourses = fragments)
        val labs = homePhysicsLabsOnDate(schedule, date)
        assertEquals(1, labs.size)
        assertEquals("phydemo", labs.single().scheduleEventKind)
        assertEquals("18:30-20:10", labs.single().scheduleEventTime)
        assertTrue(homeCoursesOnDate(schedule, date).isEmpty())
    }

    @Test fun supplementalExamsDoNotBecomeLabsOrInflateOrdinaryCourseCounts() {
        val date = LocalDate(2026, 10, 12)
        val ordinary = course("课程", 1)
        val lab = course("实验", 25).copy(id = -1, scheduleEventKind = "physicslab", scheduleEventDate = date.toString(), scheduleEventTime = "13:20-15:50")
        val exam = lab.copy(id = -2, scheduleEventKind = "exam")
        val schedule = HomeSchedulePresentation(courses = listOf(ordinary), currentWeek = 3, today = date,
            supplementalCourses = listOf(lab, lab.copy(courseLocationIndex = 33), exam))
        assertEquals(listOf(ordinary), homeCoursesOnDate(schedule, date))
        assertEquals(listOf(lab), homePhysicsLabsOnDate(schedule, date))
    }

    @Test fun placeSplitsIntoWrappingSegmentsInsteadOfOneCrammedLine() {
        assertEquals(
            "SX501 · 思源西楼 · 海淀西校区",
            wrapFriendlyPlace("海淀西校区,思源西楼,SX501"),
        )
        assertEquals("实验楼", wrapFriendlyPlace("实验楼"))
    }
}

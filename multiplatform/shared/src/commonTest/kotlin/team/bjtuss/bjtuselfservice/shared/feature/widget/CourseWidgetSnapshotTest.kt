package team.bjtuss.bjtuselfservice.shared.feature.widget

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseColorCategory
import team.bjtuss.bjtuselfservice.shared.feature.course.tones
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeSchedulePresentation

class CourseWidgetSnapshotTest {
    private fun course(name: String, index: Int, weeks: String = "第1-5周", place: String = "海淀主校区,思源西楼,SX101") =
        Course(courseId = name, courseName = name, courseTeacher = "教师", courseLocationIndex = index,
            courseTime = weeks, coursePlace = place, isCurrentSemester = false)

    private val monday = LocalDate(2026, 10, 12)
    private val weeks = listOf(
        OccupancyWeekDate(4, "10/12", "10/18", monday),
        OccupancyWeekDate(5, "10/19", "10/25", LocalDate(2026, 10, 19)),
    )

    @Test fun expandsDaysWithWeeksAndSortsSessionsByTime() {
        val lab = course("物理实验", 25).copy(id = -1, scheduleEventKind = "physicslab",
            scheduleEventDate = monday.toString(), scheduleEventTime = "13:20-15:50")
        val schedule = HomeSchedulePresentation(
            courses = listOf(course("下午课", 25), course("上午课", 1), course("周二课", 2, weeks = "第5周")),
            supplementalCourses = listOf(lab),
            academicWeeks = weeks,
            today = monday,
        )
        val snapshot = buildCourseWidgetSnapshot(schedule, monday, generatedAtEpochSeconds = 1L, dayCount = 9)

        assertEquals(9, snapshot.days.size)
        val today = snapshot.days.first()
        assertEquals("2026-10-12", today.date)
        assertEquals(4, today.week)
        assertEquals(listOf("上午课", "物理实验", "下午课"), today.sessions.map { it.name })
        assertEquals(8 * 60, today.sessions[0].startMinute)
        assertEquals(9 * 60 + 50, today.sessions[0].endMinute)
        assertTrue(today.sessions[1].isLab)
        assertEquals(13 * 60 + 20, today.sessions[1].startMinute)
        // 第 4 周周二没有“周二课”，第 5 周周二才有。
        assertTrue(snapshot.days[1].sessions.isEmpty())
        assertEquals(listOf("周二课"), snapshot.days[8].sessions.map { it.name })
        assertEquals(5, snapshot.days[8].week)
    }

    @Test fun daysOutsideTheCalendarHaveNoWeekAndNoCourses() {
        val schedule = HomeSchedulePresentation(courses = listOf(course("课", 1)), academicWeeks = weeks, today = monday)
        val snapshot = buildCourseWidgetSnapshot(schedule, LocalDate(2026, 10, 26), generatedAtEpochSeconds = 1L, dayCount = 1)
        assertNull(snapshot.days.single().week)
        assertTrue(snapshot.days.single().sessions.isEmpty())
    }

    @Test fun placeDropsCampusAndColorMatchesPalette() {
        assertEquals("思源西楼 SX101", compactWidgetPlace("海淀主校区,思源西楼,SX101"))
        assertEquals("思源西楼 SX101", compactWidgetPlace("思源西楼，SX101"))
        assertEquals("线上", compactWidgetPlace("线上"))
        assertEquals("网络课", compactWidgetPlace("-,网络课"))
        assertEquals("网络课", compactWidgetPlace("海淀西校区,-,网络课"))
        assertEquals("主校区综合体育馆（篮排球馆）", compactWidgetPlace("主校区,新综合体育馆,主校区综合体育馆（篮排球馆）"))
    }

    @Test fun encodesStableJsonWithoutAccountData() {
        val schedule = HomeSchedulePresentation(courses = listOf(course("高等数学 [本]", 1)), academicWeeks = weeks, today = monday)
        val json = buildCourseWidgetSnapshot(schedule, monday, generatedAtEpochSeconds = 42L, dayCount = 1).encode()
        val decoded = Json.decodeFromString(CourseWidgetSnapshot.serializer(), json)
        assertEquals(CourseWidgetSnapshot.VERSION, decoded.version)
        assertEquals(42L, decoded.generatedAt)
        assertEquals("高等数学", decoded.days.single().sessions.single().name)
        assertTrue("\"startMinute\":480" in json)
    }

    @Test fun rangeStartsOnMondaySoTheWeekViewSeesEarlierDays() {
        assertEquals(monday to COURSE_WIDGET_DAYS, courseWidgetRange(monday))
        assertEquals(monday to COURSE_WIDGET_DAYS + 1, courseWidgetRange(LocalDate(2026, 10, 13)))
        assertEquals(monday to COURSE_WIDGET_DAYS + 6, courseWidgetRange(LocalDate(2026, 10, 18)))
    }

    @Test fun sessionsCarryScheduleColorKind() {
        val lab = course("物理实验", 25).copy(id = -1, scheduleEventKind = "physicslab",
            scheduleEventDate = monday.toString(), scheduleEventTime = "13:20-15:50")
        val schedule = HomeSchedulePresentation(
            courses = listOf(course("M1001001", 1), course("M2002002", 9), course("M3003003", 17)),
            supplementalCourses = listOf(lab), academicWeeks = weeks, today = monday,
        )
        val types = mapOf("M1001001" to CourseType.REQUIRED, "M2002002" to CourseType.PHYSICAL_EDUCATION)
        val kinds = buildCourseWidgetSnapshot(schedule, monday, 1L, dayCount = 1, courseTypesByCode = types)
            .days.single().sessions.associate { it.name to it.kind }
        assertEquals(mapOf("M1001001" to "required", "M2002002" to "pe", "M3003003" to "unknown", "物理实验" to "lab"), kinds)
        // 培养方案没同步时全是 unknown，实验仍单独着色。
        val noMapping = buildCourseWidgetSnapshot(schedule, monday, 1L, dayCount = 1).days.single().sessions
        assertEquals(setOf("unknown", "lab"), noMapping.map { it.kind }.toSet())
    }

    @Test fun snapshotCarriesSlotTimes() {
        val schedule = HomeSchedulePresentation(courses = emptyList(), academicWeeks = weeks, today = monday)
        val json = buildCourseWidgetSnapshot(schedule, monday, 1L, dayCount = 1).encode()
        assertTrue("\"slotTimes\":[\"08:00-09:50\"" in json)
    }

    @Test fun colorsComeFromCategoryAndExamsAreIncluded() {
        val exam = course("高等数学（考试）", 9).copy(id = -7, scheduleEventKind = "exam",
            scheduleEventDate = monday.toString(), scheduleEventTime = "14:00-16:00")
        val schedule = HomeSchedulePresentation(
            courses = listOf(course("M2002002", 1).copy(id = 42)),
            supplementalCourses = listOf(exam, exam.copy(courseLocationIndex = 17)),
            academicWeeks = weeks, today = monday,
        )
        val sessions = buildCourseWidgetSnapshot(schedule, monday, 1L, dayCount = 1,
            courseTypesByCode = mapOf("M2002002" to CourseType.PHYSICAL_EDUCATION)).days.single().sessions
        assertEquals(listOf("M2002002", "高等数学（考试）"), sessions.map { it.name })
        val (pe, examSession) = sessions
        assertEquals(CourseColorCategory.PHYSICAL_EDUCATION.tones.accent, pe.color)
        assertEquals(0x16A4A4L, pe.color)
        assertEquals("exam", examSession.kind)
        assertEquals(CourseColorCategory.EXAM.tones.accent, examSession.color)
    }

    @Test fun staggeredSessionsKeepRealTimesAndCarryTheShift() {
        val schedule = HomeSchedulePresentation(
            courses = listOf(course("错峰课", 9), course("普通课", 9, place = "海淀主校区,思源楼,SY101")),
            academicWeeks = weeks,
            today = monday,
            staggeredClassTimes = true,
        )
        val sessions = buildCourseWidgetSnapshot(schedule, monday, generatedAtEpochSeconds = 1L, dayCount = 1)
            .days.single().sessions.associateBy { it.name }
        assertEquals(10 * 60 + 30, sessions.getValue("错峰课").startMinute)
        assertEquals(12 * 60 + 20, sessions.getValue("错峰课").endMinute)
        assertEquals(20, sessions.getValue("错峰课").staggerMinutes)
        assertEquals(10 * 60 + 10, sessions.getValue("普通课").startMinute)
        assertEquals(0, sessions.getValue("普通课").staggerMinutes)
    }
}

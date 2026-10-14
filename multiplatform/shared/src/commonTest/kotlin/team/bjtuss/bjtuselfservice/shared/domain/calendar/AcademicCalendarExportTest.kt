package team.bjtuss.bjtuselfservice.shared.domain.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule

class AcademicCalendarExportTest {
    @Test
    fun academicCalendarDateRangeCoversTheFullAcademicTermIncludingNaturalWeekGaps() {
        val range = assertNotNull(
            academicCalendarDateRange(
                listOf(
                    week(1, LocalDate(2026, 9, 7)),
                    week(3, LocalDate(2026, 9, 21)),
                    week(4, LocalDate(2026, 10, 5)),
                ),
            ),
        )

        assertEquals("2026-09-07T00:00:00", range.startLocal)
        assertEquals("2026-10-12T00:00:00", range.endLocal)
        assertNull(academicCalendarDateRange(emptyList()))
    }

    @Test
    fun exportsOnlyTheSelectedTeachingWeeksAcrossAcademicCalendarGaps() {
        val result = generateAcademicCalendarIcs(
            courses = listOf(course("第1-4周")),
            exams = emptyList(),
            academicWeeks = listOf(
                week(1, LocalDate(2026, 9, 7)),
                week(2, LocalDate(2026, 9, 14)),
                // Autumn break is a missing natural week, not an invented teaching week.
                week(3, LocalDate(2026, 9, 28)),
                week(4, LocalDate(2026, 10, 5)),
            ),
            weekRange = 2..3,
            generatedAt = Instant.parse("2026-09-16T00:00:00Z"),
        )

        assertEquals(1, result.events.size)
        assertEquals("2026-09-14T08:00:00", result.events.single().startLocal)
        assertEquals(3, result.events.single().recurrence?.occurrenceCount)
        assertTrue("RRULE:FREQ=WEEKLY;COUNT=3" in result.ics)
        assertTrue("EXDATE;TZID=Asia/Shanghai:20260921T080000" in result.ics)
        assertFalse("20260907T080000" in result.ics)
        assertFalse("20261005T080000" in result.ics)
    }

    @Test
    fun parsesNumericAndChineseExamTimesWithoutEatingLocationCharacters() {
        val numeric = assertNotNull(parseExamCalendarTime("2026-01-10 08:00-10:00 思源101"))
        assertEquals(LocalDate(2026, 1, 10), numeric.date)
        assertEquals(8, numeric.startHour)
        assertEquals(10, numeric.endHour)
        assertEquals("思源101", numeric.location)

        val chinese = assertNotNull(parseExamCalendarTime("2026年1月12日（周一） 14:30 至 16:10 第一教学楼"))
        assertEquals("第一教学楼", chinese.location)
        assertEquals(16, chinese.endHour)
        assertEquals(10, chinese.endMinute)
    }

    @Test
    fun missingEndTimeDefaultsToTwoHoursButMalformedTextIsNotGuessed() {
        val parsed = assertNotNull(parseExamCalendarTime("2026/1/10 08:30 思源楼"))
        assertEquals(10, parsed.endHour)
        assertEquals(30, parsed.endMinute)
        assertNull(parseExamCalendarTime("时间地点另行通知"))
        assertNull(parseExamCalendarTime("2026-02-30 08:00 思源楼"))
    }

    @Test
    fun expandsOnlyRealTeachingWeeksAndBuildsTimedCourseAndExamEvents() {
        val result = generateAcademicCalendarIcs(
            courses = listOf(course(weekText = "第1-2周,第4周")),
            exams = listOf(exam("2026-01-10 08:00-10:00 思源101")),
            academicWeeks = listOf(
                week(1, LocalDate(2025, 9, 1)),
                week(2, LocalDate(2025, 9, 8)),
                // 第 3 周故意是校历停课空洞；课程也不含第 3 周。
                week(4, LocalDate(2025, 9, 22)),
            ),
            weekRange = 2..4,
            generatedAt = Instant.parse("2026-08-10T00:00:00Z"),
        )

        assertEquals(2, result.courseEventCount)
        assertEquals(1, result.examEventCount)
        assertEquals(0, result.skippedExamCount)
        assertTrue("DTSTART;TZID=Asia/Shanghai:20250908T080000" in result.ics)
        assertTrue("RRULE:FREQ=WEEKLY;COUNT=3" in result.ics)
        assertTrue("EXDATE;TZID=Asia/Shanghai:20250915T080000" in result.ics)
        assertTrue("DTSTART;TZID=Asia/Shanghai:20260110T080000" in result.ics)
        assertTrue("LOCATION:思源101" in result.ics)
        assertTrue(result.ics.endsWith("END:VCALENDAR\r\n"))
    }

    @Test
    fun exportingOnlyWeekFourKeepsOctoberFifthAndTeachingWeekNumber() {
        val result = generateAcademicCalendarIcs(
            courses = listOf(course("第4周")),
            exams = emptyList(),
            academicWeeks = listOf(
                // 2026 秋季第 4 教学周由学校校历映射到 10 月 5 日，而不是自然周 9 月 28 日。
                week(4, LocalDate(2026, 10, 5)),
            ),
            weekRange = 4..4,
            generatedAt = Instant.parse("2026-09-16T00:00:00Z"),
        )

        assertEquals(1, result.courseEventCount)
        assertEquals("2026-10-05T08:00:00", result.events.single().startLocal)
        assertTrue("DTSTART;TZID=Asia/Shanghai:20261005T080000" in result.ics)
        assertTrue("教学周：4" in result.ics)
        assertFalse("20260928" in result.ics)
        assertFalse("RRULE:" in result.ics)
    }

    @Test
    fun reportsUnparseableExamsInsteadOfCreatingAllDayEvents() {
        val result = generateAcademicCalendarIcs(
            courses = emptyList(),
            exams = listOf(exam("时间地点另行通知")),
            academicWeeks = emptyList(),
            weekRange = 1..30,
            generatedAt = Instant.parse("2026-08-10T00:00:00Z"),
        )
        assertEquals(0, result.examEventCount)
        assertEquals(1, result.skippedExamCount)
        assertFalse("BEGIN:VEVENT" in result.ics)
    }

    @Test
    fun courseStableIdsDoNotDependOnServerListOrder() {
        val first = course("第1周").copy(
            id = 1,
            courseId = "MATH-1",
            courseName = "高等数学",
            courseLocationIndex = 1,
            coursePlace = "思源101",
        )
        val second = course("第1周").copy(
            id = 2,
            courseId = "CS-2",
            courseName = "程序设计",
            courseLocationIndex = 10,
            coursePlace = "逸夫302",
        )
        val academicWeeks = listOf(week(1, LocalDate(2025, 9, 1)))

        val original = generateAcademicCalendarIcs(
            courses = listOf(first, second),
            exams = emptyList(),
            academicWeeks = academicWeeks,
            weekRange = 1..1,
            generatedAt = Instant.parse("2026-08-10T00:00:00Z"),
        )
        val reordered = generateAcademicCalendarIcs(
            courses = listOf(second, first),
            exams = emptyList(),
            academicWeeks = academicWeeks,
            weekRange = 1..1,
            generatedAt = Instant.parse("2026-08-10T00:00:00Z"),
        )

        assertEquals(original.events.map { it.stableId }.toSet(), reordered.events.map { it.stableId }.toSet())
    }

    @Test
    fun courseStableIdSurvivesRoomAndTeacherUpdates() {
        val before = course("第1周").copy(coursePlace = "思源101", courseTeacher = "张老师")
        val after = before.copy(coursePlace = "思源102", courseTeacher = "李老师")
        val academicWeeks = listOf(week(1, LocalDate(2025, 9, 1)))

        fun export(course: Course) = generateAcademicCalendarIcs(
            courses = listOf(course),
            exams = emptyList(),
            academicWeeks = academicWeeks,
            weekRange = 1..1,
            generatedAt = Instant.parse("2026-08-10T00:00:00Z"),
        ).events.single()

        assertEquals(export(before).stableId, export(after).stableId)
        assertEquals("思源102", export(after).location)
    }

    @Test
    fun courseCalendarLocationUsesRoomBuildingCampusOrder() {
        val exported = generateAcademicCalendarIcs(
            courses = listOf(course("第1周").copy(coursePlace = "海淀西校区,思源楼,SY101")),
            exams = emptyList(),
            academicWeeks = listOf(week(1, LocalDate(2025, 9, 1))),
            weekRange = 1..1,
            generatedAt = Instant.parse("2026-08-10T00:00:00Z"),
        )

        assertEquals("SY101-思源楼-海淀西校区", exported.events.single().location)
        assertTrue("LOCATION:SY101-思源楼-海淀西校区" in exported.ics)
    }

    @Test
    fun duplicateRowsMergeIntoOneEditableRecurringSeries() {
        val first = course("第1周").copy(id = 1)
        val second = first.copy(id = 2, courseTime = "第7周")
        val weeks = (1..7).map { number ->
            week(
                number,
                LocalDate(2026, 9, 7).plus((number - 1) * 7, kotlinx.datetime.DateTimeUnit.DAY),
            )
        }

        val result = generateAcademicCalendarIcs(
            courses = listOf(first, second),
            exams = emptyList(),
            academicWeeks = weeks,
            weekRange = 1..7,
            generatedAt = Instant.parse("2026-08-10T00:00:00Z"),
        )

        assertEquals(2, result.courseEventCount)
        val series = result.events.single()
        assertEquals(7, series.recurrence?.occurrenceCount)
        assertEquals(5, series.recurrence?.excludedStartLocals?.size)
        assertTrue("RRULE:FREQ=WEEKLY;COUNT=7" in result.ics)
        assertEquals(5, Regex("EXDATE;TZID=Asia/Shanghai").findAll(result.ics).count())
    }

    private fun week(number: Int, start: LocalDate) = OccupancyWeekDate(
        week = number,
        startMonthDay = "${start.month.ordinal + 1}/${start.day}",
        endMonthDay = "",
        startDate = start,
    )

    private fun course(weekText: String) = Course(
        id = 1,
        courseId = "MATH-1",
        courseName = "高等数学, A",
        courseTeacher = "张老师",
        courseLocationIndex = 1,
        courseTime = weekText,
        coursePlace = "思源101",
        isCurrentSemester = false,
    )

    private fun exam(time: String) = ExamSchedule(
        id = 2,
        examType = "期末考试",
        courseName = "高等数学",
        examTimeAndPlace = time,
        examStatus = "正常",
        detail = "座位 12",
    )

    @Test
    fun staggeredSecondSlotExportsTheLaterTimeOnlyWhenEnabled() {
        // 周一第二节：1 * 8 + 1。
        val course = course("第1周").copy(courseLocationIndex = 9, coursePlace = "海淀主校区,逸夫教学楼,YF302")
        fun export(staggered: Boolean) = generateAcademicCalendarIcs(
            courses = listOf(course),
            exams = emptyList(),
            academicWeeks = listOf(week(1, LocalDate(2025, 9, 1))),
            weekRange = 1..1,
            generatedAt = Instant.parse("2026-08-10T00:00:00Z"),
            staggeredSecondSlot = staggered,
        ).events.single()

        assertEquals("2025-09-01T10:30:00", export(true).startLocal)
        assertEquals("2025-09-01T12:20:00", export(true).endLocal)
        assertEquals("2025-09-01T10:10:00", export(false).startLocal)
    }
}

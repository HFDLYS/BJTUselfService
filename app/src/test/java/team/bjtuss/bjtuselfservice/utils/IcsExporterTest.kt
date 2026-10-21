package team.bjtuss.bjtuselfservice.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class IcsExporterTest {
    private fun course(
        id: Int = 1,
        locationIndex: Int = 1,
        weeks: String = "1-3,5",
    ) = CourseEntity(
        id = id,
        courseId = "C$id",
        courseName = "高等数学",
        courseTeacher = "张老师",
        courseLocationIndex = locationIndex,
        courseTime = weeks,
        coursePlace = "思源楼 SY107",
        isCurrentSemester = true,
    )

    @Test
    fun parseWeeksSupportsRangesAndCommas() {
        assertEquals(listOf(1, 2, 3, 5), IcsExporter.parseWeeks("1-3,5"))
        assertEquals(listOf(2, 4), IcsExporter.parseWeeks("第2,4周"))
    }

    @Test
    fun exportUsesUtcRruleAndExdate() {
        val ics = IcsExporter.export(
            courses = listOf(course()),
            currentWeek = 1,
            now = ZonedDateTime.of(2026, 10, 21, 19, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
        )
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.contains("VERSION:2.0\r\n"))
        assertTrue(ics.contains("DTSTART:20261019T000000Z"))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;UNTIL="))
        assertTrue(ics.contains("EXDATE:20261109T000000Z"))
        assertTrue(ics.contains("SUMMARY:高等数学"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
    }

    @Test
    fun weekOneMondayMovesWithCurrentWeek() {
        val today = LocalDate.of(2026, 10, 21)
        assertEquals(
            LocalDate.of(2026, 9, 14),
            IcsExporter.termWeekOneMonday(currentWeek = 6, today = today),
        )
    }
}

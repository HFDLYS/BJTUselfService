package team.bjtuss.bjtuselfservice.utils

import org.junit.Assert.*
import org.junit.Test
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import java.time.Instant
import java.time.LocalDate

class CourseCalendarExporterTest {
    @Test
    fun scrapingKeepsParityAfterWeekSuffixAndRejectsUnknownSuffix() {
        assertEquals("第1-16周（单）", CourseWeeks.extract("第1-16周（单） 教师：某教师"))
        assertEquals("1-8周(双)", CourseWeeks.extract("1-8周(双)"))
        assertEquals("1-16周", CourseWeeks.extract("1-16周 教师：某教师"))
        assertEquals("未知", CourseWeeks.extract("1-16周(未知)"))
        assertEquals("未知", CourseWeeks.extract("1/2周"))
    }

    private val monday = LocalDate.of(2026, 9, 14)
    private val stamp = Instant.parse("2026-10-23T00:00:00Z")
    private fun course(weeks: String = "1-2周", index: Int = 1, name: String = "测试课程") =
        CourseEntity(0, "TEST001", name, "测试教师", index, weeks, "思源楼101", true)

    @Test
    fun handlesDiscontinuousAndOddEvenWeeksWithoutInventingDates() {
        assertEquals(setOf(1, 3, 5, 8, 10), CourseCalendarExporter.weeks("第1-6周（单），8、10周"))
        assertEquals(setOf(2, 4, 6), CourseCalendarExporter.weeks("1至6周(双周)"))
        assertEquals(setOf(1, 2, 3, 5), CourseCalendarExporter.weeks("1～3,5周"))
        for (unknown in listOf("未知", "单周", "0-4周", "4-2周", "1-8周,未知", "1-999周")) {
            assertTrue("Must not guess weeks: $unknown", CourseCalendarExporter.weeks(unknown).isEmpty())
        }
    }

    @Test
    fun expandsActualDatesInShanghaiAndWritesUtcTimes() {
        val result = CourseCalendarExporter.export(listOf(course("1,3周")), monday, stamp)
        assertEquals(2, result.eventCount)
        assertEquals(monday, result.firstDate)
        assertEquals(LocalDate.of(2026, 9, 28), result.lastDate)
        assertTrue(result.text.contains("DTSTART:20260914T000000Z\r\nDTEND:20260914T015000Z"))
        assertTrue(result.text.contains("DTSTART:20260928T000000Z"))
        assertFalse(result.text.contains("20260921"))
        assertFalse(result.text.contains("RRULE"))
    }

    @Test
    fun mapsSundayAndLastSlotCorrectly() {
        val result = CourseCalendarExporter.export(listOf(course("1周", 55)), monday, stamp)
        assertEquals(LocalDate.of(2026, 9, 20), result.firstDate)
        assertTrue(result.text.contains("DTSTART:20260920T130000Z\r\nDTEND:20260920T135000Z"))
    }

    @Test
    fun keepsStableIdentifiersAcrossDatabaseReloadAndAvoidsDuplicateEvents() {
        val original = course()
        val first = CourseCalendarExporter.export(listOf(original), monday, stamp)
        val reload = CourseCalendarExporter.export(listOf(original.copy(id = 99, coursePlace = "新教室")), monday, stamp)
        fun ids(text: String) = text.lines().filter { it.startsWith("UID:") }
        assertEquals(ids(first.text), ids(reload.text))
        assertEquals(2, CourseCalendarExporter.export(listOf(original, original.copy(id = 99)), monday, stamp).eventCount)
    }

    @Test
    fun escapesTextAndFoldsUtf8WithoutBreakingCharactersOrInjectingProperties() {
        val name = "课程😀中文".repeat(20) + ",;\\\r\nBEGIN:VEVENT"
        val result = CourseCalendarExporter.export(listOf(course("1周", name = name)), monday, stamp)
        assertTrue(result.text.endsWith("END:VCALENDAR\r\n"))
        for (line in result.text.split("\r\n")) assertTrue(line.toByteArray(Charsets.UTF_8).size <= 75)
        val unfolded = result.text.replace("\r\n ", "")
        assertTrue(unfolded.contains("SUMMARY:" + "课程😀中文".repeat(20) + "\\,\\;\\\\\\nBEGIN:VEVENT"))
        assertEquals(1, unfolded.split("\r\n").count { it == "BEGIN:VEVENT" })
        assertFalse(result.text.contains('\uFFFD'))
        assertFalse(result.text.replace("\r\n", "").contains('\n'))
    }

    @Test
    fun reportsUnknownCoursesAndInvalidCellsInsteadOfExportingThem() {
        val result = CourseCalendarExporter.export(listOf(course(), course("未知"), course(index = 0), course(index = 56)), monday, stamp)
        assertEquals(2, result.eventCount)
        assertEquals(3, result.skippedCourses.size)
        assertEquals(0, CourseCalendarExporter.export(emptyList(), monday, stamp).eventCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun requiresAnExplicitFirstWeekMonday() {
        CourseCalendarExporter.export(listOf(course()), monday.plusDays(1), stamp)
    }
}

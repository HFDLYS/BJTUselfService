package team.bjtuss.bjtuselfservice.shared.feature.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate

class AcademicWeeksCacheTest {
    @Test
    fun roundTripKeepsWeekStartDates() {
        val weeks = listOf(
            OccupancyWeekDate(2, "9/14", "9/20", LocalDate(2026, 9, 14)),
            OccupancyWeekDate(1, "9/7", "9/13", LocalDate(2026, 9, 7)),
            OccupancyWeekDate(3, "9/21", "9/27", null),
        )
        val decoded = decodeAcademicWeeks(encodeAcademicWeeks(weeks))
        assertEquals(listOf(1, 2), decoded.map { it.week })
        assertEquals(LocalDate(2026, 9, 14), decoded[1].startDate)
        assertEquals("9/14", decoded[1].startMonthDay)
        assertEquals("9/20", decoded[1].endMonthDay)
    }

    @Test
    fun undatedOrBrokenPayloadIsEmpty() {
        assertNull(encodeAcademicWeeks(listOf(OccupancyWeekDate(1, "9/7", "9/13"))))
        assertTrue(decodeAcademicWeeks("not json").isEmpty())
        assertTrue(decodeAcademicWeeks(null).isEmpty())
    }

    @Test
    fun inferenceAnchorsCurrentWeekOnToday() {
        // 2026-11-12 是周四，当前第 10 周 → 第 1 周周一为 2026-09-07。
        val weeks = inferAcademicWeeks(10, LocalDate(2026, 11, 12))
        assertEquals(30, weeks.size)
        assertEquals(LocalDate(2026, 9, 7), weeks.first().startDate)
        assertEquals(LocalDate(2026, 11, 9), weeks.first { it.week == 10 }.startDate)
        assertTrue(inferAcademicWeeks(0, LocalDate(2026, 11, 12)).isEmpty())
    }
}

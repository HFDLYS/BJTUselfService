package team.bjtuss.bjtuselfservice.screen

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamExpirationTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test
    fun parsesExamDateFromTimeAndPlace() {
        assertEquals(
            LocalDate.of(2026, 10, 24),
            parseExamDate("2026/10/24 09:00-11:00 第九教学楼 中102"),
        )
    }

    @Test
    fun pastExamIsExpired() {
        assertTrue(isExamExpired("2026/10/17 09:00-11:00 逸夫教学楼 YF108", today))
    }

    @Test
    fun todayAndFutureExamsAreActive() {
        assertFalse(isExamExpired("2026/10/20 09:00-11:00 第九教学楼 中102", today))
        assertFalse(isExamExpired("2026/10/24 09:00-11:00 第九教学楼 中102", today))
    }

    @Test
    fun malformedDateIsNotExpired() {
        assertFalse(isExamExpired("待定", today))
    }
}

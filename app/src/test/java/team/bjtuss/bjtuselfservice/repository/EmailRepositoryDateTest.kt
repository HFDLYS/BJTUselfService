package team.bjtuss.bjtuselfservice.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailRepositoryDateTest {
    @Test
    fun newerMailDateIsGreater() {
        val old = EmailRepository.parseEmailDate("2026/8/15 10:00:00")
        val new = EmailRepository.parseEmailDate("2026/8/26 09:30:00")
        assertTrue(new > old)
    }

    @Test
    fun invalidDateFallsBackToZero() {
        assertEquals(0L, EmailRepository.parseEmailDate("unknown"))
        assertEquals(0L, EmailRepository.parseEmailDate(""))
    }
}

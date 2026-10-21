package team.bjtuss.bjtuselfservice.screen

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentCourseSlotTest {
    private fun at(hour: Int, minute: Int): Calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, 2026)
        set(Calendar.MONTH, Calendar.OCTOBER)
        set(Calendar.DAY_OF_MONTH, 20)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Test
    fun detectsCurrentCourseSlot() {
        val slot = getCurrentCourseSlot(at(10, 30))
        assertEquals(2, slot?.day)
        assertEquals(1, slot?.slot)
    }

    @Test
    fun doesNotHighlightBreakTime() {
        assertNull(getCurrentCourseSlot(at(10, 0)))
        assertNull(getCurrentCourseSlot(at(22, 30)))
    }

    @Test
    fun detectsAfternoonSlot() {
        val slot = getCurrentCourseSlot(at(13, 30))
        assertEquals(2, slot?.day)
        assertEquals(2, slot?.slot)
    }
}

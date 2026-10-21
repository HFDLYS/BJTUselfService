package team.bjtuss.bjtuselfservice.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService.ClassroomCapacity

class ClassroomCapacityServiceTest {
    @Test
    fun capacitySentinelMeansUnreadableInsteadOfFull() {
        val room = ClassroomCapacity("SD206", 105, 105)
        assertNull(room.readableUsed)
        assertNull(room.occupancyRatio)
    }

    @Test
    fun validCountPreservesOccupancy() {
        val room = ClassroomCapacity("SD102", 68, 5)
        assertEquals(5, room.readableUsed ?: -1)
        assertEquals(5.0 / 68, room.occupancyRatio!!, 0.000001)
    }

    @Test
    fun zeroIsAValidPeopleCount() {
        val room = ClassroomCapacity("SD207", 102, 0)
        assertEquals(0, room.readableUsed ?: -1)
        assertEquals(0.0, room.occupancyRatio!!, 0.0)
    }

    @Test
    fun invalidCountsAndCapacitiesAreUnknown() {
        listOf(
            ClassroomCapacity("A101", 100, 101),
            ClassroomCapacity("A102", 100, -1),
            ClassroomCapacity("A103", 0, 0),
            ClassroomCapacity("A104", -1, 0),
        ).forEach {
            assertNull(it.readableUsed)
            assertNull(it.occupancyRatio)
        }
    }
}

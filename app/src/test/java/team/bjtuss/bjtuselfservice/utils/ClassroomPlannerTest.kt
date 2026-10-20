package team.bjtuss.bjtuselfservice.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassroomPlannerTest {
    @Test
    fun choosesLowestFloorChangePath() {
        val rooms = listOf(
            PlannerRoom("A101", 50),
            PlannerRoom("B201", 60),
            PlannerRoom("A305", 40),
        )
        val free = mapOf(
            "A101" to listOf(0, 0, 1),
            "B201" to listOf(0, 1, 0),
            "A305" to listOf(1, 0, 0),
        )
        val result = ClassroomPlanner.plan(rooms, free, listOf(0, 1, 2), 0) as PlannerResult.Success
        assertEquals(1, result.floorChanges)
        assertEquals(3, result.steps.size)
    }

    @Test
    fun reportsFailureWhenNoRoomIsFree() {
        val result = ClassroomPlanner.plan(
            rooms = listOf(PlannerRoom("A101", 50)),
            slotsByRoom = mapOf("A101" to listOf(1, 1, 1)),
            slots = listOf(0),
        )
        assertTrue(result is PlannerResult.Failure)
    }
}

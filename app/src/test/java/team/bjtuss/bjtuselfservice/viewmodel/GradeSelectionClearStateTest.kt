package team.bjtuss.bjtuselfservice.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeSelectionClearStateTest {
    @Test
    fun successfulClearCanFinalizeCurrentStudentState() {
        assertTrue(
            shouldFinalizeGradeSelectionClear(
                clearingStudentId = "student-a",
                activeStudentId = "student-a",
                clearingGeneration = 3L,
                currentGeneration = 3L,
            )
        )
    }

    @Test
    fun staleClearCannotFinalizeAfterGenerationChanges() {
        assertFalse(
            shouldFinalizeGradeSelectionClear(
                clearingStudentId = "student-a",
                activeStudentId = "student-a",
                clearingGeneration = 3L,
                currentGeneration = 4L,
            )
        )
    }

    @Test
    fun previousStudentClearCannotFinalizeNewStudentState() {
        assertFalse(
            shouldFinalizeGradeSelectionClear(
                clearingStudentId = "student-a",
                activeStudentId = "student-b",
                clearingGeneration = 3L,
                currentGeneration = 3L,
            )
        )
    }

    @Test
    fun failedClearReloadsCurrentStudentRecords() {
        assertTrue(
            shouldReloadGradeSelectionsAfterClearFailure(
                clearingStudentId = "student-a",
                activeStudentId = "student-a",
                clearingGeneration = 3L,
                currentGeneration = 3L,
            )
        )
    }

    @Test
    fun failedClearCannotReloadAfterLogout() {
        assertFalse(
            shouldReloadGradeSelectionsAfterClearFailure(
                clearingStudentId = "student-a",
                activeStudentId = null,
                clearingGeneration = 3L,
                currentGeneration = 4L,
            )
        )
    }
}

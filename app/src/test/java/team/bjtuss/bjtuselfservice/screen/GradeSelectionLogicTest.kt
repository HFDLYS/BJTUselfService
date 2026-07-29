package team.bjtuss.bjtuselfservice.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.bjtuss.bjtuselfservice.entity.GradeEntity

class GradeSelectionLogicTest {
    @Test
    fun normalModeCalculatesOnlySelectedSemesters() {
        val grades = listOf(
            grade(id = 1, semester = "2025-2026-1", score = "A,95", credits = "2.0"),
            grade(id = 2, semester = "2025-2026-2", score = "C,69", credits = "2.0"),
        )

        val result = gradesForCalculation(
            gradeList = grades,
            selectedFilters = setOf("2025-2026-1"),
            isCourseSelectionMode = false,
            selectedGradeIds = emptySet(),
        )

        assertEquals(listOf(1), result.map { it.id })
    }

    @Test
    fun selectionModeKeepsFilteredOutSelectedGradesInCalculation() {
        val grades = listOf(
            grade(id = 1, semester = "2025-2026-1", score = "A,95", credits = "2.0"),
            grade(id = 2, semester = "2025-2026-2", score = "C,69", credits = "2.0"),
            grade(id = 3, semester = "2025-2026-2", score = "B,79", credits = "2.0"),
        )

        val result = gradesForCalculation(
            gradeList = grades,
            selectedFilters = setOf("2025-2026-1"),
            isCourseSelectionMode = true,
            selectedGradeIds = setOf(1, 2),
        )

        assertEquals(listOf(1, 2), result.map { it.id })
        val gradeInfo = calculateGradeInfo(result) as GradeInfoResult.Calculated
        assertEquals(82.0, gradeInfo.averageScore, 0.0)
    }

    @Test
    fun selectionModeWithNoSelectionsHasNoGrades() {
        val result = gradesForCalculation(
            gradeList = listOf(
                grade(id = 1, semester = "2025-2026-1", score = "A,95", credits = "2.0")
            ),
            selectedFilters = emptySet(),
            isCourseSelectionMode = true,
            selectedGradeIds = emptySet(),
        )

        assertEquals(GradeInfoResult.NoGrades, calculateGradeInfo(result))
    }

    @Test
    fun currentSemesterClearScopePreservesFilteredOutSelections() {
        val grades = listOf(
            grade(id = 1, semester = "2025-2026-1", score = "A,95", credits = "2.0"),
            grade(id = 2, semester = "2025-2026-2", score = "B,79", credits = "2.0"),
            grade(id = 3, semester = "2026-2027-1", score = "C,69", credits = "2.0"),
        )
        val selectedGradeIds = setOf(1, 2, 3)
        val currentFilterGradeIds = filterGradesBySemester(
            gradeList = grades,
            selectedFilters = setOf("2025-2026-1", "2025-2026-2"),
        ).map { it.id }.toSet()

        assertEquals(setOf(3), selectedGradeIds - currentFilterGradeIds)
    }

    @Test
    fun matchingResetGenerationPreservesRestoredSelectionUi() {
        assertFalse(
            shouldResetCourseSelectionUi(
                handledResetGeneration = 4L,
                currentResetGeneration = 4L,
            )
        )
    }

    @Test
    fun accountResetGenerationClearsRestoredSelectionUi() {
        assertTrue(
            shouldResetCourseSelectionUi(
                handledResetGeneration = 4L,
                currentResetGeneration = 5L,
            )
        )
    }

    private fun grade(
        id: Int,
        semester: String,
        score: String,
        credits: String,
    ) = GradeEntity(
        id = id,
        courseName = "12345678测试课程0000",
        courseTeacher = "测试教师",
        courseScore = score,
        courseCredits = credits,
        courseYear = semester,
        tag = semester,
        detail = "",
    )
}

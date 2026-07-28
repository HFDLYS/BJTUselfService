package team.bjtuss.bjtuselfservice.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.bjtuss.bjtuselfservice.entity.GradeEntity

class GradeSelectionUtilsTest {
    @Test
    fun gradeDataNeedsSync_ignoresRoomGeneratedIds() {
        val networkGrade = grade(id = 0, courseName = "课程A", score = "A,95")
        val localGrade = networkGrade.copy(id = 42)

        assertFalse(gradeDataNeedsSync(listOf(networkGrade), listOf(localGrade)))
    }

    @Test
    fun gradeDataNeedsSync_detectsChangedOrDifferentAccountGrades() {
        val localGrade = grade(id = 42, courseName = "课程A", score = "A,95")
        val changedGrade = localGrade.copy(id = 0, courseScore = "A-,88")
        val otherAccountGrade = grade(id = 0, courseName = "课程B", score = "B+,85")

        assertTrue(gradeDataNeedsSync(listOf(changedGrade), listOf(localGrade)))
        assertTrue(gradeDataNeedsSync(listOf(otherAccountGrade), listOf(localGrade)))
    }

    @Test
    fun restoresTheSameRecordWhenDatabaseIdsChange() {
        val originalGrades = listOf(
            grade(id = 1, semester = "2025-2026-1", score = "C,69"),
            grade(id = 2, semester = "2025-2026-1", score = "A,95"),
        )
        val records = selectionRecordsForGradeIds(originalGrades, setOf(2))
        val reloadedGrades = listOf(
            grade(id = 101, semester = "2025-2026-1", score = "C,69"),
            grade(id = 102, semester = "2025-2026-1", score = "A,95"),
        )

        assertEquals(
            setOf(102),
            gradeIdsForSelectionRecords(reloadedGrades, records),
        )
    }

    @Test
    fun keepsSelectionWhenScoreAndCreditsChange() {
        val originalGrades = listOf(
            grade(
                id = 1,
                semester = "2025-2026-1",
                score = "B,79",
                credits = "2.0",
            )
        )
        val records = selectionRecordsForGradeIds(originalGrades, setOf(1))
        val updatedGrades = listOf(
            grade(
                id = 101,
                semester = "2025-2026-1",
                score = "A,95",
                credits = "3.0",
            )
        )

        assertEquals(
            setOf(101),
            gradeIdsForSelectionRecords(updatedGrades, records),
        )
    }

    @Test
    fun sameNameRecordsFromDifferentSemestersRemainIndependent() {
        val originalGrades = listOf(
            grade(id = 1, semester = "2025-2026-1", score = "B,79"),
            grade(id = 2, semester = "2025-2026-2", score = "B,79"),
        )
        val records = selectionRecordsForGradeIds(originalGrades, setOf(2))
        val reloadedGrades = listOf(
            grade(id = 101, semester = "2025-2026-1", score = "B,79"),
            grade(id = 102, semester = "2025-2026-2", score = "B,79"),
        )

        assertEquals(
            setOf(102),
            gradeIdsForSelectionRecords(reloadedGrades, records),
        )
    }

    @Test
    fun removedGradesAreNotRestored() {
        val originalGrades = listOf(
            grade(id = 1, semester = "2025-2026-1", score = "B,79")
        )
        val records = selectionRecordsForGradeIds(originalGrades, setOf(1))

        assertEquals(
            emptySet<Int>(),
            gradeIdsForSelectionRecords(emptyList(), records),
        )
    }

    @Test
    fun emptySnapshotPreservesDormantSelectionRecords() {
        val originalGrades = listOf(
            grade(id = 1, courseName = "Course A", score = "B,79"),
        )
        val records = selectionRecordsForGradeIds(originalGrades, setOf(1))

        assertEquals(
            records,
            selectionRecordsForGradeIdsPreservingUnmatched(
                grades = emptyList(),
                storedRecords = records,
                selectedGradeIds = emptySet(),
            ),
        )
    }

    @Test
    fun partialSnapshotPreservesMissingSelectionAndRefreshesVisibleRecord() {
        val originalGrades = listOf(
            grade(
                id = 1,
                semester = "2025-2026-1",
                courseName = "Course A",
                score = "B,79",
            ),
            grade(
                id = 2,
                semester = "2025-2026-2",
                courseName = "Course B",
                score = "C,69",
            ),
        )
        val records = selectionRecordsForGradeIds(originalGrades, setOf(1, 2))
        val partialGrades = listOf(
            grade(
                id = 101,
                semester = "2025-2026-1",
                courseName = "Course A",
                score = "A,95",
            ),
        )

        val preservedRecords = selectionRecordsForGradeIdsPreservingUnmatched(
            grades = partialGrades,
            storedRecords = records,
            selectedGradeIds = setOf(101),
        )

        assertEquals(2, preservedRecords.size)
        assertTrue(
            preservedRecords.any {
                it.courseName == "Course A" && it.lastKnownScore == "A,95"
            }
        )
        assertTrue(preservedRecords.any { it.courseName == "Course B" })

        val recoveredGrades = listOf(
            grade(
                id = 201,
                semester = "2025-2026-1",
                courseName = "Course A",
                score = "A,95",
            ),
            grade(
                id = 202,
                semester = "2025-2026-2",
                courseName = "Course B",
                score = "C,69",
            ),
        )
        assertEquals(
            setOf(201, 202),
            gradeIdsForSelectionRecords(recoveredGrades, preservedRecords),
        )
    }

    @Test
    fun semesterClearRemovesVisibleAndDormantRecordsInSelectedSemesters() {
        val originalGrades = listOf(
            grade(
                id = 1,
                semester = "2025-2026-1",
                courseName = "Course A",
                score = "B,79",
            ),
            grade(
                id = 2,
                semester = "2025-2026-2",
                courseName = "Course B",
                score = "C,69",
            ),
        )
        val records = selectionRecordsForGradeIds(originalGrades, setOf(1, 2))

        val remainingRecords = selectionRecordsExcludingSemesters(
            records = records,
            semesters = setOf("2025-2026-1"),
        )

        assertEquals(listOf("2025-2026-2"), remainingRecords.map { it.semester })
    }

    private fun grade(
        id: Int,
        semester: String = "2025-2026-1",
        score: String,
        credits: String = "2.0",
        courseName: String = "12345678同名课程0000",
    ) = GradeEntity(
        id = id,
        courseName = courseName,
        courseTeacher = "测试教师",
        courseScore = score,
        courseCredits = credits,
        courseYear = semester,
        tag = semester,
        detail = "",
    )
}

package team.bjtuss.bjtuselfservice.utils

import team.bjtuss.bjtuselfservice.entity.GradeEntity
import team.bjtuss.bjtuselfservice.entity.GradeSelectionRecord

private data class GradeSelectionBase(
    val courseName: String,
    val courseTeacher: String,
    val courseYear: String,
    val semester: String,
)

private data class IndexedGradeSelection(
    val grade: GradeEntity,
    val record: GradeSelectionRecord,
)

private data class GradeSelectionMatches(
    val gradeIds: Set<Int>,
    val unmatchedRecords: List<GradeSelectionRecord>,
)

fun selectionRecordsForGradeIds(
    grades: List<GradeEntity>,
    selectedGradeIds: Set<Int>,
): List<GradeSelectionRecord> {
    return indexGradeSelections(grades)
        .filter { it.grade.id in selectedGradeIds }
        .map { it.record }
}

fun gradeIdsForSelectionRecords(
    grades: List<GradeEntity>,
    records: List<GradeSelectionRecord>,
): Set<Int> {
    return matchGradeSelectionRecords(grades, records).gradeIds
}

fun selectionRecordsForGradeIdsPreservingUnmatched(
    grades: List<GradeEntity>,
    storedRecords: List<GradeSelectionRecord>,
    selectedGradeIds: Set<Int>,
): List<GradeSelectionRecord> {
    val unmatchedRecords =
        matchGradeSelectionRecords(grades, storedRecords).unmatchedRecords
    return unmatchedRecords + selectionRecordsForGradeIds(grades, selectedGradeIds)
}

fun selectionRecordsExcludingSemesters(
    records: List<GradeSelectionRecord>,
    semesters: Set<String>,
): List<GradeSelectionRecord> {
    return records.filterNot { it.semester in semesters }
}

private fun matchGradeSelectionRecords(
    grades: List<GradeEntity>,
    records: List<GradeSelectionRecord>,
): GradeSelectionMatches {
    val indexedGrades = indexGradeSelections(grades)
    val selectedGradeIds = linkedSetOf<Int>()
    val unmatchedRecords = mutableListOf<GradeSelectionRecord>()

    records.forEach { storedRecord ->
        val candidates = indexedGrades.filter {
            it.grade.id !in selectedGradeIds && it.record.hasSameBaseAs(storedRecord)
        }
        val match = candidates.firstOrNull {
            it.record.occurrence == storedRecord.occurrence &&
                    it.record.lastKnownScore == storedRecord.lastKnownScore &&
                    it.record.lastKnownCredits == storedRecord.lastKnownCredits
        } ?: candidates.firstOrNull {
            it.record.lastKnownScore == storedRecord.lastKnownScore &&
                    it.record.lastKnownCredits == storedRecord.lastKnownCredits
        } ?: candidates.firstOrNull {
            it.record.occurrence == storedRecord.occurrence
        }
        if (match != null) {
            selectedGradeIds += match.grade.id
        } else {
            unmatchedRecords += storedRecord
        }
    }

    return GradeSelectionMatches(
        gradeIds = selectedGradeIds,
        unmatchedRecords = unmatchedRecords,
    )
}

fun gradeDataNeedsSync(
    networkGrades: List<GradeEntity>,
    localGrades: List<GradeEntity>,
): Boolean {
    val networkByCourseName = networkGrades.associateBy { it.courseName }
    val localByCourseName = localGrades.associateBy { it.courseName }

    if (networkGrades.any { it.courseName !in localByCourseName }) {
        return true
    }
    if (localGrades.any { it.courseName !in networkByCourseName }) {
        return true
    }

    return networkGrades.any { networkGrade ->
        val localGrade = localByCourseName[networkGrade.courseName]
            ?: return@any false
        networkGrade.copy(id = localGrade.id) != localGrade
    }
}

private fun indexGradeSelections(grades: List<GradeEntity>): List<IndexedGradeSelection> {
    val occurrenceByBase = mutableMapOf<GradeSelectionBase, Int>()
    return grades.map { grade ->
        val base = GradeSelectionBase(
            courseName = grade.courseName,
            courseTeacher = grade.courseTeacher,
            courseYear = grade.courseYear,
            semester = grade.tag,
        )
        val occurrence = occurrenceByBase.getOrDefault(base, 0)
        occurrenceByBase[base] = occurrence + 1
        IndexedGradeSelection(
            grade = grade,
            record = GradeSelectionRecord(
                courseName = base.courseName,
                courseTeacher = base.courseTeacher,
                courseYear = base.courseYear,
                semester = base.semester,
                lastKnownScore = grade.courseScore,
                lastKnownCredits = grade.courseCredits,
                occurrence = occurrence,
            )
        )
    }
}

private fun GradeSelectionRecord.hasSameBaseAs(other: GradeSelectionRecord): Boolean {
    return courseName == other.courseName &&
            courseTeacher == other.courseTeacher &&
            courseYear == other.courseYear &&
            semester == other.semester
}

package team.bjtuss.bjtuselfservice.shared.data.grade

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSelectionRecord
import team.bjtuss.bjtuselfservice.shared.domain.grade.selectionRecordsForGradeIds

class GradeRepositoryTest {
    @Test
    fun refreshReplacesCompleteSnapshotAndRestoresSelectionAfterIdsChange() = runBlocking {
        val original = grade(id = 7, score = "B,79")
        val local = FakeLocal(
            storedGrades = listOf(original),
            storedSelections = selectionRecordsForGradeIds(listOf(original), setOf(7)),
        )
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(listOf(grade(id = 0, score = "A,95"))),
            programRemote = FakeProgramRemote(),
        )

        val result = assertIs<GradeRefreshResult.Success>(repository.refresh())

        assertEquals(listOf(100), result.snapshot.grades.map(Grade::id))
        assertEquals(setOf(100), result.snapshot.selectedGradeIds)
        assertEquals("A,95", result.snapshot.grades.single().courseScore)
        assertEquals(listOf("student-a"), local.replacedSnapshotAccounts)
    }

    @Test
    fun remoteFailureKeepsCachedSnapshotUntouched() = runBlocking {
        val cached = grade(id = 42, name = "完整缓存")
        val local = FakeLocal(storedGrades = listOf(cached))
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(error = GradeRemoteException(GradeRemoteFailure.NETWORK)),
            programRemote = FakeProgramRemote(),
        )

        val result = assertIs<GradeRefreshResult.Failure>(repository.refresh())

        assertEquals(GradeSyncFailure.NETWORK, result.reason)
        assertEquals(listOf(cached), result.snapshot.grades)
        assertTrue(local.replacedSnapshotAccounts.isEmpty())
    }

    @Test
    fun localSnapshotFailureLeavesPreviousGradesAndSelectionsUntouched() = runBlocking {
        val cached = grade(id = 42, name = "完整缓存")
        val selected = selectionRecordsForGradeIds(listOf(cached), setOf(42))
        val local = FakeLocal(
            storedGrades = listOf(cached),
            storedSelections = selected,
            failSnapshotReplace = true,
        )
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(listOf(grade(id = 0, name = "远端新数据"))),
            programRemote = FakeProgramRemote(),
        )

        val result = assertIs<GradeRefreshResult.Failure>(repository.refresh())

        assertEquals(GradeSyncFailure.CACHE, result.reason)
        assertEquals(listOf(cached), result.snapshot.grades)
        assertEquals(setOf(42), result.snapshot.selectedGradeIds)
        assertEquals(listOf(cached), local.storedGrades)
        assertEquals(selected, local.storedSelections)
    }

    @Test
    fun programSuccessStoresMappingAndSnapshotCarriesIt() = runBlocking {
        val local = FakeLocal()
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(listOf(grade(id = 0, name = "C312009B高级英语视听说[04]"))),
            programRemote = FakeProgramRemote(mapOf("C312009B" to CourseType.ELECTIVE)),
        )

        val result = assertIs<GradeRefreshResult.Success>(repository.refresh())

        assertEquals(mapOf("C312009B" to CourseType.ELECTIVE), result.snapshot.courseTypesByCode)
        assertEquals(mapOf("C312009B" to CourseType.ELECTIVE), local.storedCourseTypes)
        assertEquals(mapOf("C312009B" to CourseType.ELECTIVE), repository.load().courseTypesByCode)
    }

    @Test
    fun programFailureStillReplacesGradesAndKeepsPreviousMapping() = runBlocking {
        val local = FakeLocal(storedCourseTypes = mapOf("C312009B" to CourseType.REQUIRED))
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(listOf(grade(id = 0, name = "远端新数据"))),
            programRemote = FakeProgramRemote(
                error = GradeRemoteException(GradeRemoteFailure.SESSION_EXPIRED),
            ),
        )

        val result = assertIs<GradeRefreshResult.Success>(repository.refresh())

        assertEquals(listOf("远端新数据"), result.snapshot.grades.map(Grade::courseName))
        assertEquals(mapOf("C312009B" to CourseType.REQUIRED), local.storedCourseTypes)
        assertEquals(mapOf("C312009B" to CourseType.REQUIRED), result.snapshot.courseTypesByCode)
        assertEquals(null, local.lastReplacedCourseTypes)
    }

    @Test
    fun refreshProgramCourseTypesWritesMappingWithoutTouchingGrades() = runBlocking {
        val cached = grade(id = 1, name = "缓存课")
        val local = FakeLocal(storedGrades = listOf(cached))
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(error = GradeRemoteException(GradeRemoteFailure.NETWORK)),
            programRemote = FakeProgramRemote(mapOf("C312009B" to CourseType.REQUIRED)),
        )

        val mapping = repository.refreshProgramCourseTypes()

        assertEquals(mapOf("C312009B" to CourseType.REQUIRED), mapping)
        assertEquals(listOf(cached), local.storedGrades)
        assertTrue(local.replacedSnapshotAccounts.isEmpty())
    }

    @Test
    fun refreshProgramCourseTypesFailureLeavesMappingUntouched() = runBlocking {
        val local = FakeLocal(storedCourseTypes = mapOf("C312009B" to CourseType.REQUIRED))
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(emptyList()),
            programRemote = FakeProgramRemote(
                error = GradeRemoteException(GradeRemoteFailure.NETWORK),
            ),
        )

        assertEquals(null, repository.refreshProgramCourseTypes())
        assertEquals(mapOf("C312009B" to CourseType.REQUIRED), local.storedCourseTypes)
    }

    @Test
    fun unsyncedProgramMappingLoadsAsNull() = runBlocking {
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = FakeLocal(storedGrades = listOf(grade(id = 1))),
            remote = FakeRemote(emptyList()),
            programRemote = FakeProgramRemote(),
        )

        assertEquals(null, repository.load().courseTypesByCode)
    }

    @Test
    fun unchangedProgramMappingKeepsSameInstanceAcrossLoads() = runBlocking {
        val local = FakeLocal(
            storedGrades = listOf(grade(id = 1)),
            storedCourseTypes = mapOf("C312009B" to CourseType.REQUIRED),
        )
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(emptyList()),
            programRemote = FakeProgramRemote(),
        )

        val first = repository.load().courseTypesByCode
        // 缓存每次读出新实例：内容相同就沿用旧实例，界面层的 remember 键不必整表比较。
        local.storedCourseTypes = mapOf("C312009B" to CourseType.REQUIRED)
        assertSame(first, repository.load().courseTypesByCode)

        local.storedCourseTypes = mapOf("C312009B" to CourseType.ELECTIVE)
        assertEquals(mapOf("C312009B" to CourseType.ELECTIVE), repository.load().courseTypesByCode)
    }

    @Test
    fun selectionActionsPreserveDormantRecordsAndCanClearScopedOrAll() {
        val first = grade(id = 1, name = "课程A", semester = "2025-2026-1")
        val second = grade(id = 2, name = "课程B", semester = "2025-2026-2")
        val dormant = GradeSelectionRecord(
            courseName = "暂不可见课程",
            courseTeacher = "教师",
            courseYear = "2024-2025-2",
            semester = "2024-2025-2",
            lastKnownScore = "B,79",
            lastKnownCredits = "2.0",
            occurrence = 0,
        )
        val local = FakeLocal(
            storedGrades = listOf(first, second),
            storedSelections = listOf(dormant),
        )
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(emptyList()),
            programRemote = FakeProgramRemote(),
        )

        val selected = repository.persistSelected(listOf(first, second), setOf(1, 2))
        assertEquals(setOf(1, 2), selected.selectedGradeIds)
        assertTrue(local.storedSelections.any { it.courseName == "暂不可见课程" })

        val semesterCleared = repository.clearSelectedSemesters(setOf("2025-2026-1"))
        assertEquals(setOf(2), semesterCleared.selectedGradeIds)
        assertTrue(local.storedSelections.any { it.courseName == "暂不可见课程" })

        assertTrue(repository.clearAllSelections().selectedGradeIds.isEmpty())
        assertTrue(local.storedSelections.isEmpty())
    }

    @Test
    fun clearingCourseTypesRemovesMatchingRecordsAndKeepsOthers() {
        val required = grade(id = 1, name = "C312009B高级英语视听说[04]")
        val elective = grade(id = 2, name = "S1100120A计算机导论[01]")
        val local = FakeLocal(
            storedGrades = listOf(required, elective),
            storedSelections = selectionRecordsForGradeIds(listOf(required, elective), setOf(1, 2)),
            storedCourseTypes = mapOf(
                "C312009B" to CourseType.REQUIRED,
                "S1100120A" to CourseType.ELECTIVE,
            ),
        )
        val repository = DefaultGradeRepository(
            accountScope = "student-a",
            local = local,
            remote = FakeRemote(emptyList()),
            programRemote = FakeProgramRemote(),
        )

        val cleared = repository.clearSelectedCourseTypes(setOf(CourseType.ELECTIVE))

        assertEquals(setOf(1), cleared.selectedGradeIds)
        assertTrue(local.storedSelections.none { it.courseName.startsWith("S1100120A") })
        assertTrue(local.storedSelections.any { it.courseName.startsWith("C312009B") })
    }

    private class FakeRemote(
        private val grades: List<Grade> = emptyList(),
        private val error: Exception? = null,
    ) : GradeRemoteDataSource {
        override suspend fun fetchGrades(): List<Grade> {
            error?.let { throw it }
            return grades
        }
    }

    private class FakeProgramRemote(
        private val courseTypes: Map<String, CourseType> = emptyMap(),
        private val error: Exception? = null,
    ) : TrainingProgramRemoteDataSource {
        override suspend fun fetchCourseTypes(): Map<String, CourseType> {
            error?.let { throw it }
            return courseTypes
        }
    }

    private class FakeLocal(
        var storedGrades: List<Grade> = emptyList(),
        var storedSelections: List<GradeSelectionRecord> = emptyList(),
        var storedCourseTypes: Map<String, CourseType>? = null,
        private val failSnapshotReplace: Boolean = false,
    ) : GradeLocalDataSource {
        val replacedSnapshotAccounts = mutableListOf<String>()
        var lastReplacedCourseTypes: Map<String, CourseType>? = emptyMap()

        override fun grades(accountScope: String): List<Grade> = storedGrades

        override fun selections(accountScope: String): List<GradeSelectionRecord> = storedSelections

        override fun courseTypes(accountScope: String): Map<String, CourseType>? = storedCourseTypes

        override fun replaceSnapshot(
            accountScope: String,
            grades: List<Grade>,
            records: List<GradeSelectionRecord>,
            courseTypes: Map<String, CourseType>?,
        ) {
            if (failSnapshotReplace) error("synthetic snapshot failure")
            replacedSnapshotAccounts += accountScope
            lastReplacedCourseTypes = courseTypes
            storedGrades = grades.mapIndexed { index, grade -> grade.copy(id = 100 + index) }
            storedSelections = records
            courseTypes?.let { storedCourseTypes = it }
        }

        override fun replaceSelections(
            accountScope: String,
            records: List<GradeSelectionRecord>,
        ) {
            storedSelections = records
        }

        override fun replaceCourseTypes(
            accountScope: String,
            courseTypes: Map<String, CourseType>,
        ) {
            storedCourseTypes = courseTypes
        }
    }

    private fun grade(
        id: Int,
        score: String = "B,79",
        name: String = "课程A",
        semester: String = "2025-2026-1",
    ) = Grade(
        id = id,
        courseName = name,
        courseTeacher = "教师",
        courseScore = score,
        courseCredits = "2.0",
        courseYear = semester,
        semester = semester,
    )
}

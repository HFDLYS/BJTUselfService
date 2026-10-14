package team.bjtuss.bjtuselfservice.shared.cache

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.data.grade.CacheStoreGradeLocalDataSource
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSelectionRecord
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework

class CacheStoreTest {
    @Test
    fun redesignMigrationRunsOnceAndPreservesLaterCustomizationAcrossReopens() {
        val directory = Files.createTempDirectory("bjtu-redesign-test-").toFile()
        try {
            val first = openDesktopCacheStore(directory, PlaintextCacheValueProtector).store
            try {
                val store = first
                store.savePreferences(AppPreferences(bottomNavigationItems = listOf("GRADES", "SCHEDULE"), dynamicColor = false))
                val migrated = store.prepareRedesignPreferences()
                assertEquals(emptyList(), migrated.bottomNavigationItems)
                assertFalse(migrated.redesignGuideAcknowledged)
                assertFalse(migrated.dynamicColor)
                store.savePreferences(migrated.copy(bottomNavigationItems = listOf("MAILBOX", "EXAMS")))
            } finally { first.close() }
            val second = openDesktopCacheStore(directory, PlaintextCacheValueProtector).store
            try {
                val store = second
                val pending = store.prepareRedesignPreferences()
                assertEquals(listOf("MAILBOX", "EXAMS"), pending.bottomNavigationItems)
                assertFalse(pending.redesignGuideAcknowledged)
                store.savePreferences(pending.copy(redesignGuideAcknowledged = true))
            } finally { second.close() }
            val third = openDesktopCacheStore(directory, PlaintextCacheValueProtector).store
            try {
                val store = third
                val restored = store.prepareRedesignPreferences()
                assertTrue(restored.redesignGuideAcknowledged)
                assertEquals(listOf("MAILBOX", "EXAMS"), restored.bottomNavigationItems)
                store.savePreferences(restored.copy(bottomNavigationItems = emptyList()))
                assertEquals(emptyList(), store.prepareRedesignPreferences().bottomNavigationItems)
                assertTrue(store.preferences().redesignGuideAcknowledged)
            } finally { third.close() }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun freshInstallAndLegacyDefaultBothMigrateToTwoTabs() {
        for (legacyDefault in listOf(false, true)) {
            val store = inMemoryStore()
            try {
                if (legacyDefault) store.savePreferences(AppPreferences())
                assertEquals(emptyList(), store.prepareRedesignPreferences().bottomNavigationItems)
                assertFalse(store.preferences().redesignGuideAcknowledged)
            } finally { store.close() }
        }
    }

    @Test
    fun physicsLabPreferencePreservesLegacySentinelAndExplicitValues() {
        val store = inMemoryStore()
        try {
            assertNull(store.preferences().physicsLabEnabled)
            store.putMetadata("student-a", "physicslab.enabled", "true")
            store.savePreferences(AppPreferences())
            assertNull(store.preferences().physicsLabEnabled)
            assertEquals("true", store.metadata("student-a", "physicslab.enabled"))
            store.savePreferences(AppPreferences(physicsLabEnabled = true, bottomNavigationItems = listOf("PHYSICS_LAB")))
            assertTrue(store.preferences().isPhysicsLabEnabled)
            assertEquals(listOf("PHYSICS_LAB"), store.preferences().bottomNavigationItems)
            store.savePreferences(store.preferences().copy(physicsLabEnabled = false))
            assertFalse(store.preferences().isPhysicsLabEnabled)
        } finally { store.close() }
    }

    @Test
    fun missingAggregateSettingDefaultsOnAndGuidesStayOffUntilAcknowledged() {
        val store = inMemoryStore()
        try {
            assertTrue(store.preferences().aggregateAssignments)
            assertFalse(store.preferences().citelGuideAcknowledged)
            assertFalse(store.preferences().assignmentAggregateGuideAcknowledged)
            store.savePreferences(AppPreferences(aggregateAssignments = false))
            assertFalse(store.preferences().aggregateAssignments)
            store.savePreferences(
                store.preferences().copy(
                    citelGuideAcknowledged = true,
                    assignmentAggregateGuideAcknowledged = true,
                ),
            )
            assertTrue(store.preferences().citelGuideAcknowledged)
            assertTrue(store.preferences().assignmentAggregateGuideAcknowledged)
            assertFalse(store.preferences().aggregateAssignments)
            assertFalse(store.preferences().release184GuideAcknowledged)
            store.savePreferences(store.preferences().copy(release184GuideAcknowledged = true))
            assertTrue(store.preferences().release184GuideAcknowledged)
        } finally { store.close() }
    }

    @Test
    fun navigationSelectionPreservesExplicitEmptyAndCanReturnToDefaults() {
        val store = inMemoryStore()
        try {
            store.savePreferences(AppPreferences(bottomNavigationItems = listOf("MAILBOX", "EXAMS")))
            assertEquals(listOf("MAILBOX", "EXAMS"), store.preferences().bottomNavigationItems)
            store.savePreferences(AppPreferences(bottomNavigationItems = emptyList()))
            assertEquals(emptyList(), store.preferences().bottomNavigationItems)
            store.savePreferences(AppPreferences())
            assertNull(store.preferences().bottomNavigationItems)
        } finally { store.close() }
    }

    @Test
    fun roundTripIsAccountScopedAndClearAccountKeepsGlobalPreferences() {
        val store = inMemoryStore()
        try {
            store.replaceGrades("student-a", listOf(sampleGrade("A")))
            store.replaceCourses("student-a", listOf(sampleCourse("A")))
            store.replaceExams("student-a", listOf(sampleExam("A")))
            store.replaceHomework("student-a", listOf(sampleHomework("A")))
            store.replaceGradeSelections("student-a", listOf(sampleSelection("A")))
            store.putMetadata("student-a", "grades_updated_at", "2026-07-30T12:00:00")

            store.replaceGrades("student-b", listOf(sampleGrade("B")))
            store.savePreferences(
                AppPreferences(
                    autoSyncGrades = true,
                    autoSyncHomework = true,
                    autoSyncPhyVlab = false,
                    currentWeek = 14,
                    checkUpdate = false,
                    updatePostponedUntilMillis = 123456789L,
                    dynamicColor = false,
                    theme = "Dark",
                ),
            )

            assertEquals(sampleGrade("A"), store.grades("student-a").single().copy(id = 0))
            assertEquals(sampleCourse("A"), store.courses("student-a").single().copy(id = 0))
            assertEquals(sampleExam("A"), store.exams("student-a").single().copy(id = 0))
            assertEquals(sampleHomework("A"), store.homework("student-a").single().copy(id = 0))
            assertEquals(listOf(sampleSelection("A")), store.gradeSelections("student-a"))
            assertEquals("2026-07-30T12:00:00", store.metadata("student-a", "grades_updated_at"))
            assertEquals("课程-B", store.grades("student-b").single().courseName)

            val preferences = store.preferences()
            assertTrue(preferences.autoSyncGrades)
            assertTrue(preferences.autoSyncHomework)
            assertFalse(preferences.autoSyncPhyVlab)
            assertEquals(14, preferences.currentWeek)
            assertFalse(preferences.checkUpdate)
            assertEquals(123456789L, preferences.updatePostponedUntilMillis)
            assertFalse(preferences.dynamicColor)
            assertEquals("Dark", preferences.theme)

            store.clearAccount("student-a")

            assertTrue(store.grades("student-a").isEmpty())
            assertTrue(store.courses("student-a").isEmpty())
            assertTrue(store.exams("student-a").isEmpty())
            assertTrue(store.homework("student-a").isEmpty())
            assertTrue(store.gradeSelections("student-a").isEmpty())
            assertNull(store.metadata("student-a", "grades_updated_at"))
            assertEquals("课程-B", store.grades("student-b").single().courseName)
            assertEquals("Dark", store.preferences().theme)
        } finally {
            store.close()
        }
    }

    @Test
    fun cachedProfileRoundTripAndClearAccount() {
        val store = inMemoryStore()
        try {
            assertNull(store.cachedProfile("student-a"))

            val profile = StudentProfile(
                name = "张三",
                studentId = "student-a",
                identity = "本科生",
                department = "计算机学院",
            )
            store.saveCachedProfile(profile)

            assertEquals(profile, store.cachedProfile("student-a"))
            assertNull(store.cachedProfile("student-b"))

            store.clearAccount("student-a")
            assertNull(store.cachedProfile("student-a"))
        } finally {
            store.close()
        }
    }

    @Test
    fun fileDatabaseRestoresAfterCloseAndReopen() = withTemporaryDirectory { directory ->
        val first = openDesktopCacheStore(directory, PlaintextCacheValueProtector)
        assertEquals(CacheOpenState.OPENED, first.state)
        first.store.replaceGrades("student-a", listOf(sampleGrade("restart")))
        first.store.putSetting("ordinary-setting", "kept")
        first.store.close()

        val reopened = openDesktopCacheStore(directory, PlaintextCacheValueProtector)
        try {
            assertEquals(CacheOpenState.OPENED, reopened.state)
            assertEquals("课程-restart", reopened.store.grades("student-a").single().courseName)
            assertEquals("kept", reopened.store.setting("ordinary-setting"))
        } finally {
            reopened.store.close()
        }
    }

    @Test
    fun versionOneDatabaseMigratesAndLegacyRowsCanBeClaimed() =
        withTemporaryDirectory { directory ->
            val databaseFile = File(directory, "bjtuselfservice_cache.db")
            val legacyDriver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
            createVersionOneSchema(legacyDriver)
            legacyDriver.execute(
                identifier = null,
                sql = """
                    INSERT INTO grade_cache (
                        course_name, course_teacher, course_score, course_credits,
                        course_year, semester, detail
                    ) VALUES ('旧课程', '旧教师', '90', '2.0', '2025-2026', '1', '')
                """.trimIndent(),
                parameters = 0,
            ).value
            legacyDriver.execute(null, "PRAGMA user_version = 1", 0).value
            legacyDriver.close()

            val migrated = openDesktopCacheStore(directory, PlaintextCacheValueProtector)
            try {
                assertEquals(CacheOpenState.OPENED, migrated.state)
                assertTrue(migrated.store.grades("student-a").isEmpty())

                migrated.store.claimLegacyAccountData("student-a")

                assertEquals("旧课程", migrated.store.grades("student-a").single().courseName)
                assertEquals(CacheDatabaseSql.Schema.version, 3L)
            } finally {
                migrated.store.close()
            }
        }

    @Test
    fun versionTwoDatabaseMigratesAndCourseTypeCacheBecomesAvailable() =
        withTemporaryDirectory { directory ->
            val databaseFile = File(directory, "bjtuselfservice_cache.db")
            val legacyDriver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
            createVersionTwoSchema(legacyDriver)
            legacyDriver.execute(
                identifier = null,
                sql = """
                    INSERT INTO grade_selection_cache (
                        account_scope, course_name, course_teacher, course_year, semester,
                        last_known_score, last_known_credits, occurrence
                    ) VALUES ('student-a', '旧课程', '旧教师', '2025-2026', '1', '90', '2.0', 0)
                """.trimIndent(),
                parameters = 0,
            ).value
            legacyDriver.execute(null, "PRAGMA user_version = 2", 0).value
            legacyDriver.close()

            val migrated = openDesktopCacheStore(directory, PlaintextCacheValueProtector)
            try {
                assertEquals(CacheOpenState.OPENED, migrated.state)
                assertEquals(
                    "旧课程",
                    migrated.store.gradeSelections("student-a").single().courseName,
                )

                migrated.store.replaceProgramCourseTypes("student-a", mapOf("C312009B" to "必修"))

                assertEquals(
                    mapOf("C312009B" to "必修"),
                    migrated.store.programCourseTypes("student-a"),
                )
                assertEquals(CacheDatabaseSql.Schema.version, 3L)
            } finally {
                migrated.store.close()
            }
        }

    @Test
    fun programCourseTypesRoundTripAndAccountCleanup() {
        val store = inMemoryStore()
        try {
            store.replaceProgramCourseTypes(
                "student-a",
                mapOf("C312009B" to "必修", "S1100120A" to "任选"),
            )
            store.replaceProgramCourseTypes("student-b", mapOf("M710033B" to "限选"))

            assertEquals(
                mapOf("C312009B" to "必修", "S1100120A" to "任选"),
                store.programCourseTypes("student-a"),
            )

            // 重复替换整体覆盖，不残留旧键。
            store.replaceProgramCourseTypes("student-a", mapOf("C312009B" to "限选"))
            assertEquals(mapOf("C312009B" to "限选"), store.programCourseTypes("student-a"))

            store.clearAccount("student-a")
            assertTrue(store.programCourseTypes("student-a").isEmpty())
            assertEquals(mapOf("M710033B" to "限选"), store.programCourseTypes("student-b"))

            store.clearAll()
            assertTrue(store.programCourseTypes("student-b").isEmpty())
        } finally {
            store.close()
        }
    }

    @Test
    fun corruptedFileIsDeletedAndRecreatedOnce() = withTemporaryDirectory { directory ->
        val databaseFile = File(directory, "bjtuselfservice_cache.db")
        databaseFile.writeText("not-a-sqlite-database")

        val recovered = openDesktopCacheStore(directory, PlaintextCacheValueProtector)
        try {
            assertEquals(CacheOpenState.RECOVERED_AFTER_RESET, recovered.state)
            assertEquals(0L, recovered.store.rowCount())
            assertNotEquals("not-a-sqlite-database", databaseFile.readText())
        } finally {
            recovered.store.close()
        }
    }

    @Test
    fun encryptedDesktopUpgradeResetsLegacyPlaintextCacheOnce() = withTemporaryDirectory { directory ->
        val legacy = openDesktopCacheStore(directory, PlaintextCacheValueProtector)
        legacy.store.putMetadata("student-a", "profile_name", "LEGACY-PERSONAL-DATA-5e9c")
        legacy.store.close()

        val protector = JvmAesCacheValueProtector(ByteArray(32) { (it + 1).toByte() })
        val upgraded = openDesktopCacheStore(directory, protector, cacheKeyCreated = true)
        try {
            assertEquals(CacheOpenState.MIGRATED_TO_ENCRYPTED, upgraded.state)
            assertEquals(0L, upgraded.store.rowCount())
            upgraded.store.putMetadata("student-a", "profile_name", "加密后的姓名")
        } finally {
            upgraded.store.close()
        }

        val reopened = openDesktopCacheStore(directory, protector)
        try {
            assertEquals(CacheOpenState.OPENED, reopened.state)
            assertEquals("加密后的姓名", reopened.store.metadata("student-a", "profile_name"))
        } finally {
            reopened.store.close()
        }
        val database = File(directory, "bjtuselfservice_cache.db")
        assertFalse("LEGACY-PERSONAL-DATA-5e9c" in database.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun encryptedGradesKeepTheExactSchoolRowOrderAfterReopen() = withTemporaryDirectory { directory ->
        val protector = JvmAesCacheValueProtector(ByteArray(32) { (it + 1).toByte() })
        val schoolRows = listOf(sampleGrade("first"), sampleGrade("second"), sampleGrade("third"))
        val first = openDesktopCacheStore(directory, protector, cacheKeyCreated = true)
        try {
            first.store.replaceGrades("student-a", schoolRows)
            assertEquals(schoolRows.map(Grade::courseName), first.store.grades("student-a").map(Grade::courseName))
        } finally {
            first.store.close()
        }

        val reopened = openDesktopCacheStore(directory, protector)
        try {
            assertEquals(
                schoolRows.map(Grade::courseName),
                reopened.store.grades("student-a").map(Grade::courseName),
            )
        } finally {
            reopened.store.close()
        }
    }

    @Test
    fun fullWipeRemovesDeletedTextFromDatabaseAndWalFiles() = withTemporaryDirectory { directory ->
        val marker = "AUDIT-PERSONAL-SECRET-7b4d85e6"
        val handle = openDesktopCacheStore(directory, PlaintextCacheValueProtector)
        try {
            handle.store.putMetadata("student-a", "private-note", marker)
            assertEquals(marker, handle.store.metadata("student-a", "private-note"))
            handle.store.clearAll()
            assertEquals(0L, handle.store.rowCount())
        } finally {
            handle.store.close()
        }
        listOf(
            File(directory, "bjtuselfservice_cache.db"),
            File(directory, "bjtuselfservice_cache.db-wal"),
            File(directory, "bjtuselfservice_cache.db-shm"),
        ).filter(File::exists).forEach { file ->
            assertFalse(marker in file.readBytes().toString(Charsets.UTF_8), file.name)
        }
    }

    @Test
    fun invalidScopeAndCorruptPreferenceFallbackAreSafe() {
        val store = inMemoryStore()
        try {
            store.putSetting("auto_sync_grades", "unexpected")
            store.putSetting("current_week", "999")
            store.putSetting("theme", "")

            val preferences = store.preferences()
            assertTrue(preferences.autoSyncGrades)
            assertEquals(56, preferences.currentWeek)
            assertEquals("System", preferences.theme)
        } finally {
            store.close()
        }
    }

    @Test
    fun gradeSnapshotReplacesGradesAndSelectionsTogether() {
        val store = inMemoryStore()
        try {
            store.replaceGradeSnapshot(
                accountScope = "student-a",
                grades = listOf(sampleGrade("snapshot")),
                selections = listOf(sampleSelection("snapshot")),
            )

            assertEquals("课程-snapshot", store.grades("student-a").single().courseName)
            assertEquals(
                listOf(sampleSelection("snapshot")),
                store.gradeSelections("student-a"),
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun gradeSnapshotReplacesCourseTypesOnlyWhenProvided() {
        val store = inMemoryStore()
        try {
            store.replaceGradeSnapshot(
                accountScope = "student-a",
                grades = listOf(sampleGrade("first")),
                selections = emptyList(),
                courseTypes = mapOf("C312009B" to "必修"),
            )
            assertEquals(mapOf("C312009B" to "必修"), store.programCourseTypes("student-a"))

            // 方案抓取失败时映射传 null：成绩照常替换，旧映射保留。
            store.replaceGradeSnapshot(
                accountScope = "student-a",
                grades = listOf(sampleGrade("second")),
                selections = emptyList(),
                courseTypes = null,
            )
            assertEquals("课程-second", store.grades("student-a").single().courseName)
            assertEquals(mapOf("C312009B" to "必修"), store.programCourseTypes("student-a"))

            store.replaceGradeSnapshot(
                accountScope = "student-a",
                grades = listOf(sampleGrade("third")),
                selections = emptyList(),
                courseTypes = mapOf("S1100120A" to "任选"),
            )
            assertEquals(mapOf("S1100120A" to "任选"), store.programCourseTypes("student-a"))
        } finally {
            store.close()
        }
    }

    @Test
    fun courseSnapshotKeepsCoursesAndCurrentWeekAccountScoped() {
        val store = inMemoryStore()
        try {
            store.replaceCourseSnapshot("student-a", listOf(sampleCourse("A")), 8)
            store.replaceCourseSnapshot("student-b", listOf(sampleCourse("B")), 12)

            assertEquals("课程-A", store.courses("student-a").single().courseName)
            assertEquals(8, store.courseCurrentWeek("student-a"))
            assertEquals("课程-B", store.courses("student-b").single().courseName)
            assertEquals(12, store.courseCurrentWeek("student-b"))
        } finally {
            store.close()
        }
    }

    @Test
    fun courseSnapshotKeepsSummerContinuationWeekThirty() {
        val store = inMemoryStore()
        try {
            store.replaceCourseSnapshot("student-a", listOf(sampleCourse("A")), 30)

            assertEquals(30, store.courseCurrentWeek("student-a"))
        } finally {
            store.close()
        }
    }

    @Test
    fun programMappingDataSourceIsNullUntilRowsExist() {
        val store = inMemoryStore()
        try {
            val local = CacheStoreGradeLocalDataSource(store)
            assertEquals(null, local.courseTypes("student-a"))

            store.replaceProgramCourseTypes("student-a", mapOf("C312009B" to "必修"))

            assertEquals(
                mapOf("C312009B" to CourseType.REQUIRED),
                local.courseTypes("student-a"),
            )
        } finally {
            store.close()
        }
    }

    private fun inMemoryStore(): CacheStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CacheDatabaseSql.Schema.create(driver).value
        return CacheStore(driver)
    }

    private fun createVersionOneSchema(driver: SqlDriver) {
        listOf(
            """
                CREATE TABLE grade_cache (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    course_name TEXT NOT NULL,
                    course_teacher TEXT NOT NULL,
                    course_score TEXT NOT NULL,
                    course_credits TEXT NOT NULL,
                    course_year TEXT NOT NULL,
                    semester TEXT NOT NULL,
                    detail TEXT NOT NULL DEFAULT ''
                )
            """,
            """
                CREATE TABLE course_cache (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    course_id TEXT NOT NULL,
                    course_name TEXT NOT NULL,
                    course_teacher TEXT NOT NULL,
                    course_location_index INTEGER NOT NULL,
                    course_time TEXT NOT NULL,
                    course_place TEXT NOT NULL,
                    is_current_semester INTEGER NOT NULL
                )
            """,
            """
                CREATE TABLE exam_cache (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    exam_type TEXT NOT NULL,
                    course_name TEXT NOT NULL,
                    exam_time_and_place TEXT NOT NULL,
                    exam_status TEXT NOT NULL,
                    detail TEXT NOT NULL
                )
            """,
            """
                CREATE TABLE homework_cache (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    up_id INTEGER NOT NULL,
                    id_sn_id INTEGER,
                    score TEXT NOT NULL,
                    user_id INTEGER NOT NULL,
                    course_id INTEGER NOT NULL,
                    course_name TEXT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    create_date TEXT NOT NULL,
                    end_time TEXT NOT NULL,
                    open_date TEXT NOT NULL,
                    status INTEGER NOT NULL,
                    submit_count INTEGER NOT NULL,
                    all_count INTEGER NOT NULL,
                    sub_status TEXT NOT NULL,
                    score_id INTEGER NOT NULL,
                    homework_type INTEGER NOT NULL
                )
            """,
        ).forEach { sql -> driver.execute(null, sql.trimIndent(), 0).value }
    }

    /** v2 = v1 迁移完成后的结构：各表带 account_scope，无课程性质缓存表。 */
    private fun createVersionTwoSchema(driver: SqlDriver) {
        createVersionOneSchema(driver)
        listOf(
            "ALTER TABLE grade_cache ADD COLUMN account_scope TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE course_cache ADD COLUMN account_scope TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE exam_cache ADD COLUMN account_scope TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE homework_cache ADD COLUMN account_scope TEXT NOT NULL DEFAULT ''",
            "CREATE INDEX grade_cache_account_index ON grade_cache(account_scope)",
            "CREATE INDEX course_cache_account_index ON course_cache(account_scope)",
            "CREATE INDEX exam_cache_account_index ON exam_cache(account_scope)",
            "CREATE INDEX homework_cache_account_index ON homework_cache(account_scope)",
            """
                CREATE TABLE grade_selection_cache (
                    account_scope TEXT NOT NULL,
                    course_name TEXT NOT NULL,
                    course_teacher TEXT NOT NULL,
                    course_year TEXT NOT NULL,
                    semester TEXT NOT NULL,
                    last_known_score TEXT NOT NULL,
                    last_known_credits TEXT NOT NULL,
                    occurrence INTEGER NOT NULL,
                    PRIMARY KEY (
                        account_scope,
                        course_name,
                        course_teacher,
                        course_year,
                        semester,
                        occurrence
                    )
                )
            """,
            """
                CREATE TABLE cache_metadata (
                    account_scope TEXT NOT NULL,
                    cache_key TEXT NOT NULL,
                    value TEXT NOT NULL,
                    PRIMARY KEY (account_scope, cache_key)
                )
            """,
            """
                CREATE TABLE app_setting (
                    setting_key TEXT NOT NULL PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """,
        ).forEach { sql -> driver.execute(null, sql.trimIndent(), 0).value }
    }

    private inline fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("bjtu-cache-test-").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun sampleGrade(suffix: String) = Grade(
        courseName = "课程-$suffix",
        courseTeacher = "教师-$suffix",
        courseScore = "90",
        courseCredits = "2.0",
        courseYear = "2025-2026",
        semester = "1",
        detail = "详情-$suffix",
    )

    private fun sampleCourse(suffix: String) = Course(
        courseId = "course-$suffix",
        courseName = "课程-$suffix",
        courseTeacher = "教师-$suffix",
        courseLocationIndex = 7,
        courseTime = "第1-16周",
        coursePlace = "教室-$suffix",
        isCurrentSemester = true,
    )

    private fun sampleExam(suffix: String) = ExamSchedule(
        examType = "期末考试",
        courseName = "课程-$suffix",
        examTimeAndPlace = "第18周 教室-$suffix",
        examStatus = "正常",
        detail = "详情-$suffix",
    )

    private fun sampleHomework(suffix: String) = Homework(
        upId = 1,
        idSnId = 2,
        score = "未评分",
        userId = 3,
        courseId = 4,
        courseName = "课程-$suffix",
        title = "作业-$suffix",
        content = "内容-$suffix",
        createDate = "2026-07-01 08:00",
        endTime = "2026-08-01 08:00",
        openDate = "2026-07-01 08:00",
        status = 1,
        submitCount = 0,
        allCount = 1,
        subStatus = "未提交",
        scoreId = 5,
        homeworkType = 0,
    )

    private fun sampleSelection(suffix: String) = GradeSelectionRecord(
        courseName = "课程-$suffix",
        courseTeacher = "教师-$suffix",
        courseYear = "2025-2026",
        semester = "1",
        lastKnownScore = "90",
        lastKnownCredits = "2.0",
        occurrence = 0,
    )
}

package team.bjtuss.bjtuselfservice.shared.cache

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.QueryResult
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSelectionRecord
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFilterPreferences
import team.bjtuss.bjtuselfservice.shared.domain.homework.decodeHomeworkFilterCourses
import team.bjtuss.bjtuselfservice.shared.domain.homework.decodeHomeworkSortOrder
import team.bjtuss.bjtuselfservice.shared.domain.homework.encodeHomeworkFilterCourses

data class AppPreferences(
    val autoSyncGrades: Boolean = true,
    val autoSyncHomework: Boolean = true,
    val autoSyncSchedule: Boolean = true,
    val autoSyncExams: Boolean = true,
    val autoSyncPhyVlab: Boolean = true,
    val currentWeek: Int = 0,
    val checkUpdate: Boolean = true,
    val updatePostponedUntilMillis: Long = 0L,
    val dynamicColor: Boolean = true,
    val theme: String = "System",
    val showPhyVlabInBottomNav: Boolean = true,
    val bottomNavigationItems: List<String>? = null,
    val redesignGuideAcknowledged: Boolean = false,
    val citelGuideAcknowledged: Boolean = false,
    val assignmentAggregateGuideAcknowledged: Boolean = false,
    /** 1.8.4 新功能必看弹窗（演示实验、桌面小组件、错峰上课）。 */
    val release184GuideAcknowledged: Boolean = false,
    /** 1.8.5 紧急修复说明弹窗（MIS 新认证、课程平台不可用、物理在线独立账号）。 */
    val emergencyFix185NoticeAcknowledged: Boolean = false,
    /** null migrates the previous account-scoped physicslab.enabled setting on session creation. */
    val physicsLabEnabled: Boolean? = null,
    val citelEnabled: Boolean? = null,
    val aggregateAssignments: Boolean = true,
    /** 第二节在逸夫教学楼/思源西楼按 10:30–12:20 显示（错峰上课）。 */
    val staggeredSecondSlot: Boolean = true,
    /** 是否允许本机 AI Agent 命令行读取成绩（默认否，其余数据由“AI 助手”安装后即可读）。 */
    val agentGradesAllowed: Boolean = false,
) {
    val isPhysicsLabEnabled: Boolean get() = physicsLabEnabled == true
    /** 1.8.3 的三个必看弹窗已合并为一个；三项都确认过的用户不再看到合并弹窗。 */
    val release183GuidePending: Boolean
        get() = !redesignGuideAcknowledged || !citelGuideAcknowledged || !assignmentAggregateGuideAcknowledged
    val isCitelEnabled: Boolean get() = citelEnabled == true

    /** 物理在线只有一个总开关：关闭时不自动同步，也不显示入口。 */
    val isPhyVlabEnabled: Boolean
        get() = autoSyncPhyVlab && showPhyVlabInBottomNav
}

enum class CacheOpenState {
    OPENED,
    RECOVERED_AFTER_RESET,
    MIGRATED_TO_ENCRYPTED,
}

data class CacheStoreHandle(
    val store: CacheStore,
    val state: CacheOpenState,
)

class CacheDatabaseOpenException(cause: Throwable) :
    IllegalStateException("无法打开本地缓存数据库。", cause)

/**
 * 普通业务缓存。不得向这里写入密码、Cookie、CSRF、CAPTCHA 或可复用会话。
 */
class CacheStore(
    private val driver: SqlDriver,
    private val protector: CacheValueProtector = PlaintextCacheValueProtector,
) {
    private val database = CacheDatabaseSql(driver)
    private val queries = database.cacheQueries

    fun rowCount(): Long = queries.countAllRows().executeAsOne()

    fun grades(accountScope: String): List<Grade> = queries.selectGradesByAccount(
        account_scope = protectedAccountScope(accountScope),
    ) { id, courseName, teacher, score, credits, year, semester, detail ->
        Grade(
            id = id.toIntChecked(),
            courseName = protector.unprotect(courseName),
            courseTeacher = protector.unprotect(teacher),
            courseScore = protector.unprotect(score),
            courseCredits = protector.unprotect(credits),
            courseYear = protector.unprotect(year),
            semester = protector.unprotect(semester),
            detail = protector.unprotect(detail),
        )
    }.executeAsList()

    fun replaceGrades(accountScope: String, grades: List<Grade>) {
        val scope = protectedAccountScope(accountScope)
        queries.transaction {
            replaceGradesInTransaction(scope, grades)
        }
    }

    /**
     * 成绩与自选记录属于同一个可见快照，必须在同一事务内替换。
     * 课程性质映射来自培养方案页：方案抓取成功时随快照整体替换，
     * 抓取失败（null）时保留上一次成功的旧映射不动。
     */
    fun replaceGradeSnapshot(
        accountScope: String,
        grades: List<Grade>,
        selections: List<GradeSelectionRecord>,
        courseTypes: Map<String, String>? = null,
    ) {
        val scope = protectedAccountScope(accountScope)
        queries.transaction {
            replaceGradesInTransaction(scope, grades)
            replaceGradeSelectionsInTransaction(scope, selections)
            if (courseTypes != null) {
                replaceProgramCourseTypesInTransaction(scope, courseTypes)
            }
        }
    }

    /** 课程号 → 课程性质中文原文（必修/限选/任选），枚举转换在 data 层完成。 */
    fun programCourseTypes(accountScope: String): Map<String, String> =
        queries.selectProgramCourseTypesByAccount(protectedAccountScope(accountScope))
            .executeAsList()
            .associate { protector.unprotect(it.course_id) to protector.unprotect(it.course_type) }

    fun replaceProgramCourseTypes(accountScope: String, courseTypes: Map<String, String>) {
        val scope = protectedAccountScope(accountScope)
        queries.transaction {
            replaceProgramCourseTypesInTransaction(scope, courseTypes)
        }
    }

    fun courses(accountScope: String): List<Course> = queries.selectCoursesByAccount(
        account_scope = protectedAccountScope(accountScope),
    ) { id, courseId, courseName, teacher, locationIndex, time, place, currentSemester ->
        Course(
            id = id.toIntChecked(),
            courseId = protector.unprotect(courseId),
            courseName = protector.unprotect(courseName),
            courseTeacher = protector.unprotect(teacher),
            courseLocationIndex = protector.unprotectNumber(locationIndex).toIntChecked(),
            courseTime = protector.unprotect(time),
            coursePlace = protector.unprotect(place),
            isCurrentSemester = protector.unprotectNumber(currentSemester) != 0L,
        )
    }.executeAsList()

    fun replaceCourses(accountScope: String, courses: List<Course>) {
        val scope = protectedAccountScope(accountScope)
        queries.transaction {
            replaceCoursesInTransaction(scope, courses)
        }
    }

    fun courseCurrentWeek(accountScope: String): Int =
        metadata(accountScope, COURSE_CURRENT_WEEK_KEY)
            ?.toIntOrNull()
            ?.takeIf { it in 0..COURSE_MAX_WEEK }
            ?: 0

    /** 课程行与它们对应的当前周提示属于同一个账号快照。 */
    fun replaceCourseSnapshot(accountScope: String, courses: List<Course>, currentWeek: Int) {
        val scope = protectedAccountScope(accountScope)
        val safeWeek = currentWeek.takeIf { it in 1..COURSE_MAX_WEEK } ?: 0
        queries.transaction {
            replaceCoursesInTransaction(scope, courses)
            queries.putMetadata(
                scope,
                protectedKey(COURSE_CURRENT_WEEK_KEY),
                protector.protect(safeWeek.toString()),
            )
        }
    }

    fun exams(accountScope: String): List<ExamSchedule> = queries.selectExamsByAccount(
        account_scope = protectedAccountScope(accountScope),
    ) { id, examType, courseName, timeAndPlace, status, detail ->
        ExamSchedule(
            id = id.toIntChecked(),
            examType = protector.unprotect(examType),
            courseName = protector.unprotect(courseName),
            examTimeAndPlace = protector.unprotect(timeAndPlace),
            examStatus = protector.unprotect(status),
            detail = protector.unprotect(detail),
        )
    }.executeAsList()

    fun replaceExams(accountScope: String, exams: List<ExamSchedule>) {
        val scope = protectedAccountScope(accountScope)
        queries.transaction {
            queries.deleteExamsByAccount(scope)
            exams.forEach { exam ->
                queries.insertExam(
                    scope,
                    protector.protect(exam.examType),
                    protector.protect(exam.courseName),
                    protector.protect(exam.examTimeAndPlace),
                    protector.protect(exam.examStatus),
                    protector.protect(exam.detail),
                )
            }
        }
    }

    fun homework(accountScope: String): List<Homework> = queries.selectHomeworkByAccount(
        account_scope = protectedAccountScope(accountScope),
    ) { id, upId, idSnId, score, userId, courseId, courseName, title, content,
        createDate, endTime, openDate, status, submitCount, allCount, subStatus,
        scoreId, homeworkType ->
        Homework(
            id = id.toIntChecked(),
            upId = protector.unprotectNumber(upId).toIntChecked(),
            idSnId = idSnId?.let(protector::unprotectNumber)?.toIntChecked(),
            score = protector.unprotect(score),
            userId = protector.unprotectNumber(userId).toIntChecked(),
            courseId = protector.unprotectNumber(courseId).toIntChecked(),
            courseName = protector.unprotect(courseName),
            title = protector.unprotect(title),
            content = protector.unprotect(content),
            createDate = protector.unprotect(createDate),
            endTime = protector.unprotect(endTime),
            openDate = protector.unprotect(openDate),
            status = protector.unprotectNumber(status).toIntChecked(),
            submitCount = protector.unprotectNumber(submitCount).toIntChecked(),
            allCount = protector.unprotectNumber(allCount).toIntChecked(),
            subStatus = protector.unprotect(subStatus),
            scoreId = protector.unprotectNumber(scoreId).toIntChecked(),
            homeworkType = protector.unprotectNumber(homeworkType).toIntChecked(),
        )
    }.executeAsList()

    fun replaceHomework(accountScope: String, homework: List<Homework>) {
        val scope = protectedAccountScope(accountScope)
        queries.transaction {
            queries.deleteHomeworkByAccount(scope)
            homework.forEach { item ->
                queries.insertHomework(
                    scope,
                    protector.protectNumber(item.upId.toLong()),
                    item.idSnId?.toLong()?.let(protector::protectNumber),
                    protector.protect(item.score),
                    protector.protectNumber(item.userId.toLong()),
                    protector.protectNumber(item.courseId.toLong()),
                    protector.protect(item.courseName),
                    protector.protect(item.title),
                    protector.protect(item.content),
                    protector.protect(item.createDate),
                    protector.protect(item.endTime),
                    protector.protect(item.openDate),
                    protector.protectNumber(item.status.toLong()),
                    protector.protectNumber(item.submitCount.toLong()),
                    protector.protectNumber(item.allCount.toLong()),
                    protector.protect(item.subStatus),
                    protector.protectNumber(item.scoreId.toLong()),
                    protector.protectNumber(item.homeworkType.toLong()),
                )
            }
        }
    }

    fun gradeSelections(accountScope: String): List<GradeSelectionRecord> =
        queries.selectGradeSelectionsByAccount(
            account_scope = protectedAccountScope(accountScope),
        ) { courseName, teacher, year, semester, score, credits, occurrence ->
            GradeSelectionRecord(
                courseName = protector.unprotect(courseName),
                courseTeacher = protector.unprotect(teacher),
                courseYear = protector.unprotect(year),
                semester = protector.unprotect(semester),
                lastKnownScore = protector.unprotect(score),
                lastKnownCredits = protector.unprotect(credits),
                occurrence = protector.unprotectNumber(occurrence).toIntChecked(),
            )
        }.executeAsList().sortedWith(
            compareBy<GradeSelectionRecord>(
                GradeSelectionRecord::courseYear,
                GradeSelectionRecord::semester,
                GradeSelectionRecord::courseName,
                GradeSelectionRecord::courseTeacher,
                GradeSelectionRecord::occurrence,
            ),
        )

    fun replaceGradeSelections(accountScope: String, records: List<GradeSelectionRecord>) {
        val scope = protectedAccountScope(accountScope)
        queries.transaction {
            replaceGradeSelectionsInTransaction(scope, records)
        }
    }

    /**
     * 作业筛选面板的上次状态（按账号存）：课程多选、隐藏过期/已提交、排序。
     * 退出登录清账号数据时一起清除；课程名对不上当前数据时读取方自行裁剪。
     */
    fun homeworkFilter(accountScope: String): HomeworkFilterPreferences {
        val scope = requireAccountScope(accountScope)
        return HomeworkFilterPreferences(
            selectedCourses = decodeHomeworkFilterCourses(metadata(scope, HOMEWORK_FILTER_COURSES_KEY)),
            hideExpired = metadata(scope, HOMEWORK_FILTER_HIDE_EXPIRED_KEY) == "true",
            hideSubmitted = metadata(scope, HOMEWORK_FILTER_HIDE_SUBMITTED_KEY) == "true",
            sortOrder = decodeHomeworkSortOrder(metadata(scope, HOMEWORK_FILTER_SORT_KEY)),
        )
    }

    fun saveHomeworkFilter(accountScope: String, preferences: HomeworkFilterPreferences) {
        val scope = requireAccountScope(accountScope)
        queries.transaction {
            putMetadata(scope, HOMEWORK_FILTER_COURSES_KEY, encodeHomeworkFilterCourses(preferences.selectedCourses))
            putMetadata(scope, HOMEWORK_FILTER_HIDE_EXPIRED_KEY, preferences.hideExpired.toString())
            putMetadata(scope, HOMEWORK_FILTER_HIDE_SUBMITTED_KEY, preferences.hideSubmitted.toString())
            putMetadata(scope, HOMEWORK_FILTER_SORT_KEY, preferences.sortOrder.name)
        }
    }

    fun metadataAccountScopes(key: String): List<String> =
        queries.selectMetadataAccountScopesForKey(protectedKey(key)).executeAsList().map(protector::unprotect)

    fun metadata(accountScope: String, key: String): String? =
        queries.selectMetadata(protectedAccountScope(accountScope), protectedKey(key))
            .executeAsOneOrNull()
            ?.let(protector::unprotect)

    fun putMetadata(accountScope: String, key: String, value: String) {
        queries.putMetadata(
            protectedAccountScope(accountScope),
            protectedKey(key),
            protector.protect(value),
        )
    }

    /**
     * 登录成功后缓存的最小档案快照：下次冷启动时，自动登录完成前先用它渲染主界面。
     */
    fun cachedProfile(accountScope: String): StudentProfile? {
        val scope = requireAccountScope(accountScope)
        val name = metadata(scope, PROFILE_NAME_KEY)?.takeIf(String::isNotBlank) ?: return null
        return StudentProfile(
            name = name,
            studentId = scope,
            identity = metadata(scope, PROFILE_IDENTITY_KEY).orEmpty(),
            department = metadata(scope, PROFILE_DEPARTMENT_KEY).orEmpty(),
        )
    }

    fun saveCachedProfile(profile: StudentProfile) {
        val scope = protectedAccountScope(profile.studentId)
        queries.transaction {
            queries.putMetadata(scope, protectedKey(PROFILE_NAME_KEY), protector.protect(profile.name))
            queries.putMetadata(scope, protectedKey(PROFILE_IDENTITY_KEY), protector.protect(profile.identity))
            queries.putMetadata(scope, protectedKey(PROFILE_DEPARTMENT_KEY), protector.protect(profile.department))
        }
    }

    fun setting(key: String): String? = queries.selectSetting(protectedKey(key))
        .executeAsOneOrNull()
        ?.let(protector::unprotect)

    fun putSetting(key: String, value: String) {
        queries.putSetting(protectedKey(key), protector.protect(value))
    }

    fun deleteSetting(key: String) {
        queries.deleteSetting(protectedKey(key))
    }

    /** This release transition is independent of version numbers and guide acknowledgement. */
    fun prepareRedesignPreferences(): AppPreferences {
        queries.transaction {
            if (!booleanSetting(SettingKey.REDESIGN_NAVIGATION_MIGRATED, false)) {
                putSetting(SettingKey.BOTTOM_NAVIGATION_ITEMS, "")
                putSetting(SettingKey.REDESIGN_GUIDE_ACKNOWLEDGED, "false")
                putSetting(SettingKey.REDESIGN_NAVIGATION_MIGRATED, "true")
            }
        }
        return preferences()
    }

    fun preferences(): AppPreferences = AppPreferences(
        autoSyncGrades = true,
        autoSyncHomework = true,
        autoSyncSchedule = true,
        autoSyncExams = true,
        physicsLabEnabled = setting(SettingKey.PHYSICS_LAB_ENABLED)?.toBooleanStrictOrNull(),
        citelEnabled = setting(SettingKey.CITEL_ENABLED)?.toBooleanStrictOrNull(),
        aggregateAssignments = setting(SettingKey.AGGREGATE_ASSIGNMENTS)?.toBooleanStrictOrNull() ?: true,
        staggeredSecondSlot = booleanSetting(SettingKey.STAGGERED_SECOND_SLOT, true),
        agentGradesAllowed = booleanSetting(SettingKey.AGENT_GRADES_ALLOWED, false),
        autoSyncPhyVlab = booleanSetting(SettingKey.AUTO_SYNC_PHYVLAB, true),
        currentWeek = setting(SettingKey.CURRENT_WEEK)?.toIntOrNull()?.coerceIn(0, 56) ?: 0,
        checkUpdate = booleanSetting(SettingKey.CHECK_UPDATE, true),
        updatePostponedUntilMillis = setting(SettingKey.UPDATE_POSTPONED_UNTIL)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        dynamicColor = booleanSetting(SettingKey.DYNAMIC_COLOR, true),
        theme = setting(SettingKey.THEME)?.takeIf(String::isNotBlank) ?: "System",
        showPhyVlabInBottomNav = booleanSetting(SettingKey.SHOW_PHYVLAB_IN_BOTTOM_NAV, true),
        bottomNavigationItems = setting(SettingKey.BOTTOM_NAVIGATION_ITEMS)?.takeUnless { it == "*" }?.split(',')?.filter(String::isNotBlank),
        redesignGuideAcknowledged = booleanSetting(SettingKey.REDESIGN_GUIDE_ACKNOWLEDGED, false),
        citelGuideAcknowledged = booleanSetting(SettingKey.CITEL_GUIDE_ACKNOWLEDGED, false),
        assignmentAggregateGuideAcknowledged = booleanSetting(SettingKey.ASSIGNMENT_AGGREGATE_GUIDE_ACKNOWLEDGED, false),
        release184GuideAcknowledged = booleanSetting(SettingKey.RELEASE_184_GUIDE_ACKNOWLEDGED, false),
        emergencyFix185NoticeAcknowledged = booleanSetting(SettingKey.EMERGENCY_FIX_185_NOTICE_ACKNOWLEDGED, false),
    )

    fun savePreferences(preferences: AppPreferences) {
        queries.transaction {
            putSetting(SettingKey.AUTO_SYNC_GRADES, preferences.autoSyncGrades.toString())
            putSetting(SettingKey.AUTO_SYNC_HOMEWORK, preferences.autoSyncHomework.toString())
            putSetting(SettingKey.AUTO_SYNC_SCHEDULE, preferences.autoSyncSchedule.toString())
            putSetting(SettingKey.AUTO_SYNC_EXAMS, preferences.autoSyncExams.toString())
            putSetting(SettingKey.PHYSICS_LAB_ENABLED, preferences.physicsLabEnabled?.toString() ?: "legacy")
            putSetting(SettingKey.CITEL_ENABLED, preferences.citelEnabled?.toString() ?: "legacy")
            putSetting(SettingKey.AGGREGATE_ASSIGNMENTS, preferences.aggregateAssignments.toString())
            putSetting(SettingKey.STAGGERED_SECOND_SLOT, preferences.staggeredSecondSlot.toString())
            putSetting(SettingKey.AGENT_GRADES_ALLOWED, preferences.agentGradesAllowed.toString())
            putSetting(SettingKey.AUTO_SYNC_PHYVLAB, preferences.autoSyncPhyVlab.toString())
            putSetting(SettingKey.CURRENT_WEEK, preferences.currentWeek.coerceIn(0, 56).toString())
            putSetting(SettingKey.CHECK_UPDATE, preferences.checkUpdate.toString())
            putSetting(SettingKey.UPDATE_POSTPONED_UNTIL, preferences.updatePostponedUntilMillis.coerceAtLeast(0L).toString())
            putSetting(SettingKey.DYNAMIC_COLOR, preferences.dynamicColor.toString())
            putSetting(SettingKey.THEME, preferences.theme.ifBlank { "System" })
            putSetting(SettingKey.SHOW_PHYVLAB_IN_BOTTOM_NAV, preferences.showPhyVlabInBottomNav.toString())
            putSetting(SettingKey.BOTTOM_NAVIGATION_ITEMS, preferences.bottomNavigationItems?.joinToString(",") ?: "*")
            putSetting(SettingKey.REDESIGN_GUIDE_ACKNOWLEDGED, preferences.redesignGuideAcknowledged.toString())
            putSetting(SettingKey.CITEL_GUIDE_ACKNOWLEDGED, preferences.citelGuideAcknowledged.toString())
            putSetting(SettingKey.ASSIGNMENT_AGGREGATE_GUIDE_ACKNOWLEDGED, preferences.assignmentAggregateGuideAcknowledged.toString())
            putSetting(SettingKey.RELEASE_184_GUIDE_ACKNOWLEDGED, preferences.release184GuideAcknowledged.toString())
            putSetting(SettingKey.EMERGENCY_FIX_185_NOTICE_ACKNOWLEDGED, preferences.emergencyFix185NoticeAcknowledged.toString())
        }
    }

    fun claimLegacyAccountData(accountScope: String) {
        if (!protector.isIdentity) return
        val scope = requireAccountScope(accountScope)
        queries.transaction {
            queries.claimLegacyGrades(scope)
            queries.claimLegacyCourses(scope)
            queries.claimLegacyExams(scope)
            queries.claimLegacyHomework(scope)
        }
    }

    fun clearAccount(accountScope: String) {
        // 独立实验账号是同步设置；清离线副本时保留标记，完整清空时再统一清安全存储。
        val labSettings = listOf("physicslab.enabled", "physicslab.configured", "physicslab.username", "physicslab.twoWeekOverrides",
            "phydemo.enabled", "phydemo.configured", "phydemo.username",
            "phyvlab.configured", "phyvlab.username",
            "citel.enabled", "citel.remember", "citel.username").mapNotNull { key ->
            metadata(accountScope, key)?.let { key to it }
        }
        val scope = protectedAccountScope(accountScope)
        queries.transaction {
            queries.deleteGradesByAccount(scope)
            queries.deleteCoursesByAccount(scope)
            queries.deleteExamsByAccount(scope)
            queries.deleteHomeworkByAccount(scope)
            queries.deleteGradeSelectionsByAccount(scope)
            queries.deleteProgramCourseTypesByAccount(scope)
            queries.deleteMetadataByAccount(scope)
            labSettings.forEach { (key, value) -> putMetadata(accountScope, key, value) }
        }
    }

    fun clearAll() {
        queries.transaction {
            queries.deleteAllGrades()
            queries.deleteAllCourses()
            queries.deleteAllExams()
            queries.deleteAllHomework()
            queries.deleteAllGradeSelections()
            queries.deleteAllProgramCourseTypes()
            queries.deleteAllMetadata()
            queries.deleteAllSettings()
        }
        // SQLite 默认不会立即擦除空闲页，WAL 也可能保留旧内容。清空后压缩
        // 主库并截断 WAL，使“清除全部本地数据”不只是逻辑删除。
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA wal_checkpoint(TRUNCATE)",
            mapper = { cursor ->
                check(cursor.next().value && cursor.getLong(0) == 0L) {
                    "无法截断本地缓存 WAL。"
                }
                QueryResult.Value(Unit)
            },
            parameters = 0,
            binders = null,
        ).value
        driver.execute(null, "VACUUM", 0).value
    }

    fun close() {
        driver.close()
    }

    private fun replaceGradesInTransaction(scope: String, grades: List<Grade>) {
        queries.deleteGradesByAccount(scope)
        grades.forEach { grade ->
            queries.insertGrade(
                scope,
                protector.protect(grade.courseName),
                protector.protect(grade.courseTeacher),
                protector.protect(grade.courseScore),
                protector.protect(grade.courseCredits),
                protector.protect(grade.courseYear),
                protector.protect(grade.semester),
                protector.protect(grade.detail),
            )
        }
    }

    private fun replaceGradeSelectionsInTransaction(
        scope: String,
        records: List<GradeSelectionRecord>,
    ) {
        queries.deleteGradeSelectionsByAccount(scope)
        records.forEach { record ->
            queries.insertGradeSelection(
                scope,
                protector.protectStable(record.courseName),
                protector.protectStable(record.courseTeacher),
                protector.protectStable(record.courseYear),
                protector.protectStable(record.semester),
                protector.protect(record.lastKnownScore),
                protector.protect(record.lastKnownCredits),
                protector.protectNumber(record.occurrence.toLong()),
            )
        }
    }

    private fun replaceProgramCourseTypesInTransaction(
        scope: String,
        courseTypes: Map<String, String>,
    ) {
        queries.deleteProgramCourseTypesByAccount(scope)
        courseTypes.forEach { (courseId, courseType) ->
            if (courseId.isBlank() || courseType.isBlank()) return@forEach
            queries.insertProgramCourseType(
                scope,
                protector.protectStable(courseId),
                protector.protect(courseType),
            )
        }
    }

    private fun replaceCoursesInTransaction(scope: String, courses: List<Course>) {
        queries.deleteCoursesByAccount(scope)
        courses.forEach { course ->
            queries.insertCourse(
                scope,
                protector.protect(course.courseId),
                protector.protect(course.courseName),
                protector.protect(course.courseTeacher),
                protector.protectNumber(course.courseLocationIndex.toLong()),
                protector.protect(course.courseTime),
                protector.protect(course.coursePlace),
                protector.protectNumber(if (course.isCurrentSemester) 1L else 0L),
            )
        }
    }

    private fun protectedAccountScope(value: String): String =
        protector.protectStable(requireAccountScope(value))

    private fun protectedKey(value: String): String = protector.protectStable(requireKey(value))

    private fun booleanSetting(key: String, default: Boolean): Boolean = when (setting(key)) {
        "true" -> true
        "false" -> false
        else -> default
    }
}

fun openCacheStoreWithRecovery(
    openDriver: () -> SqlDriver,
    deleteStorage: () -> Unit,
    protector: CacheValueProtector = PlaintextCacheValueProtector,
): CacheStoreHandle {
    fun openAndProbe(): CacheStore {
        val driver = openDriver()
        return try {
            CacheStore(driver, protector).also(CacheStore::rowCount)
        } catch (error: Exception) {
            runCatching(driver::close)
            throw error
        }
    }

    return try {
        CacheStoreHandle(openAndProbe(), CacheOpenState.OPENED)
    } catch (_: Exception) {
        try {
            deleteStorage()
            CacheStoreHandle(openAndProbe(), CacheOpenState.RECOVERED_AFTER_RESET)
        } catch (error: Exception) {
            throw CacheDatabaseOpenException(error)
        }
    }
}

private object SettingKey {
    const val AUTO_SYNC_GRADES = "auto_sync_grades"
    const val AUTO_SYNC_HOMEWORK = "auto_sync_homework"
    const val AUTO_SYNC_SCHEDULE = "auto_sync_schedule"
    const val AUTO_SYNC_EXAMS = "auto_sync_exams"
    const val PHYSICS_LAB_ENABLED = "physics_lab_enabled"
    const val CITEL_ENABLED = "citel_enabled"
    const val AGGREGATE_ASSIGNMENTS = "aggregate_assignments"
    const val STAGGERED_SECOND_SLOT = "staggered_second_slot"
    const val AGENT_GRADES_ALLOWED = "agent_grades_allowed"
    const val AUTO_SYNC_PHYVLAB = "auto_sync_phyvlab"
    const val CURRENT_WEEK = "current_week"
    const val CHECK_UPDATE = "check_update"
    const val UPDATE_POSTPONED_UNTIL = "update_postponed_until"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val THEME = "theme"
    const val BOTTOM_NAVIGATION_ITEMS = "bottom_navigation_items"
    const val REDESIGN_NAVIGATION_MIGRATED = "redesign_navigation_migrated"
    const val REDESIGN_GUIDE_ACKNOWLEDGED = "redesign_guide_acknowledged"
    const val CITEL_GUIDE_ACKNOWLEDGED = "citel_guide_acknowledged"
    const val ASSIGNMENT_AGGREGATE_GUIDE_ACKNOWLEDGED = "assignment_aggregate_guide_acknowledged"
    const val RELEASE_184_GUIDE_ACKNOWLEDGED = "release_184_guide_acknowledged"
    const val EMERGENCY_FIX_185_NOTICE_ACKNOWLEDGED = "emergency_fix_185_notice_acknowledged"
    const val SHOW_PHYVLAB_IN_BOTTOM_NAV = "show_phyvlab_in_bottom_nav"
}

private const val HOMEWORK_FILTER_COURSES_KEY = "homework_filter_courses"
private const val HOMEWORK_FILTER_HIDE_EXPIRED_KEY = "homework_filter_hide_expired"
private const val HOMEWORK_FILTER_HIDE_SUBMITTED_KEY = "homework_filter_hide_submitted"
private const val HOMEWORK_FILTER_SORT_KEY = "homework_filter_sort"
private const val COURSE_CURRENT_WEEK_KEY = "course_current_week"
private const val COURSE_MAX_WEEK = 30
private const val PROFILE_NAME_KEY = "profile_name"
private const val PROFILE_IDENTITY_KEY = "profile_identity"
private const val PROFILE_DEPARTMENT_KEY = "profile_department"

private fun requireAccountScope(value: String): String = value.trim().also {
    require(it.isNotEmpty()) { "accountScope 不能为空。" }
    require(it.length <= 128) { "accountScope 过长。" }
}

private fun requireKey(value: String): String = value.trim().also {
    require(it.isNotEmpty()) { "缓存键不能为空。" }
    require(it.length <= 128) { "缓存键过长。" }
}

private fun Long.toIntChecked(): Int {
    require(this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "数据库整数越界。" }
    return toInt()
}

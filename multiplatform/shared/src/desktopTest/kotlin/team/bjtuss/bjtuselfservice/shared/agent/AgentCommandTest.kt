package team.bjtuss.bjtuselfservice.shared.agent

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.cache.SyncSource
import team.bjtuss.bjtuselfservice.shared.cache.markSynced
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabCacheSnapshot
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.encodePhyVlabCache
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabActivity
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabCourse
import team.bjtuss.bjtuselfservice.shared.feature.assignment.AggregateAssignmentFilters
import team.bjtuss.bjtuselfservice.shared.feature.assignment.AggregateCourseFilter
import team.bjtuss.bjtuselfservice.shared.feature.assignment.AssignmentSource
import team.bjtuss.bjtuselfservice.shared.feature.citel.CitelTask
import team.bjtuss.bjtuselfservice.shared.feature.course.saveAcademicWeeksIfChanged
import team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLab

/** 全部为虚构数据。时间点：2026-11-12（周四）09:00 北京时间，第 10 周。 */
class AgentCommandTest {
    private val student = "20990001"
    private val now = Instant.parse("2026-11-12T01:00:00Z")
    private val json = Json { encodeDefaults = true }

    @Test
    fun calendarMergesClassesLabsExamsAndDeadlinesInTimeOrder() {
        val output = run("calendar", "--today", "--json")
        assertEquals(AgentExit.OK, output.exitCode, output.stderr)
        val data = parse(output).getValue("data").jsonObject
        assertEquals("cached", data.str("academicCalendar"))
        val items = data.getValue("items").jsonArray.map { it.jsonObject }
        val summary = items.map { it.str("kind") + "|" + it.str("title") + "|" + it.str("start") }
        assertEquals(
            listOf(
                "class|数据结构|2026-11-12T08:00:00+08:00",
                // 第二节在逸夫教学楼：错峰 10:30。
                "class|计算机网络|2026-11-12T10:30:00+08:00",
                "lab|光纤特性和光信号传输（实验）|2026-11-12T13:00:00+08:00",
                "exam|离散数学（考试）|2026-11-12T14:00:00+08:00",
                "assignment_due|栈与队列|2026-11-12T23:59:00+08:00",
                "assignment_due|零点截止的作业|2026-11-13T00:00:00+08:00",
            ),
            summary,
        )
        assertEquals("citel", items[items.size - 2].str("source"))
    }

    @Test
    fun homeworkAggregatesThreeSourcesAndSkipsUnbookedExperiments() {
        val output = run("homework", "--json")
        val items = parse(output).getValue("data").jsonObject.getValue("items").jsonArray.map { it.jsonObject }
        val titles = items.map { it.str("source") + ":" + it.str("title") }
        assertTrue("course_platform:实验报告一" in titles)
        assertTrue("citel:栈与队列" in titles)
        assertTrue("phyvlab:34 光纤特性和光信号传输" in titles)
        assertTrue("phyvlab:第一章作业" in titles, "非物理实验课不过滤")
        assertFalse(titles.any { "光栅光谱仪" in it }, "没约的实验不算")
        val citel = items.first { it.str("title") == "栈与队列" }
        assertEquals("false", citel.str("submitted"))
        assertEquals("WA: Wrong Answer", citel.str("judgeStatus"))
        val booked = items.first { it.str("title") == "34 光纤特性和光信号传输" }
        assertEquals("booked", booked.str("enrollment"))
        assertEquals("光纤特性和光信号传输", booked.str("matchedBooking"))

        val all = run("homework", "--all", "--json")
        val allTitles = parse(all).getValue("data").jsonObject.getValue("items").jsonArray.map { it.jsonObject.str("title") }
        assertTrue(allTitles.any { "光栅光谱仪" in it })
    }

    @Test
    fun appFilterIsOptInAndAgentFiltersAreExposed() {
        fun titles(vararg args: String) = parse(run(*args, "--json")).getValue("data").jsonObject.getValue("items").jsonArray
            .map { it.jsonObject.str("title") }
        assertTrue("被隐藏的课的作业" in titles("homework"), "默认不套用 App 作业页筛选")
        assertFalse("被隐藏的课的作业" in titles("homework", "--app-filter"))
        assertEquals(listOf("实验报告一"), titles("homework", "--pending", "--source", "course", "--course", "甲"))
        assertEquals(listOf("栈与队列"), titles("homework", "--source", "citel,course", "--course", "乙"))

        val filters = parse(run("homework", "--json")).getValue("data").jsonObject.getValue("availableFilters").jsonObject
        assertTrue(filters.getValue("course").jsonArray.any { it.jsonPrimitive.content == "物理实验II_(2026秋)" })
        assertEquals("true", filters.getValue("appFilterActive").jsonPrimitive.content)
    }

    @Test
    fun midnightDeadlineBelongsToPreviousDay() {
        val data = parse(run("calendar", "--today", "--kind", "assignment_due", "--json")).getValue("data").jsonObject
        val items = data.getValue("items").jsonArray.map { it.jsonObject }
        val midnight = items.single { it.str("title") == "零点截止的作业" }
        assertEquals("2026-11-12", midnight.str("agendaDate"))
        assertEquals("2026-11-13T00:00:00+08:00", midnight.str("start"))
        assertEquals("true", midnight.str("midnightDeadline"))
        // 23:59 截止排在 24:00 之前。
        assertEquals(listOf("栈与队列", "零点截止的作业"), items.map { it.str("title") })
        assertTrue(data.getValue("notes").jsonArray.single().jsonPrimitive.content.contains("00:00"))

        val tomorrow = parse(run("calendar", "--tomorrow", "--kind", "assignment_due", "--json")).getValue("data").jsonObject
        assertTrue(tomorrow.getValue("items").jsonArray.none { it.jsonObject.str("title") == "零点截止的作业" })

        val markdown = run("calendar", "--today").stdout
        assertTrue("24:00 [作业截止] 零点截止的作业" in markdown)
        assertTrue("即次日 00:00" in markdown)
    }

    @Test
    fun kindAndCourseFiltersApplyToCalendar() {
        val items = parse(run("calendar", "--today", "--kind", "class", "--course", "网络", "--json"))
            .getValue("data").jsonObject.getValue("items").jsonArray.map { it.jsonObject.str("title") }
        assertEquals(listOf("计算机网络"), items)
        assertEquals(AgentExit.USAGE, run("calendar", "--kind", "party").exitCode)
        assertEquals(AgentExit.USAGE, run("homework", "--course").exitCode)
    }

    @Test
    fun freshnessFlagsSourcesOlderThanFourHours() {
        val homework = parse(run("homework", "--json")).getValue("freshness").jsonObject
        assertEquals(listOf("homework"), homework.getValue("stale").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("6.0", homework.getValue("sources").jsonObject.getValue("homework").jsonObject.str("ageHours"))
        // 物理在线快照没写 savedAt、CITEL 没有 lastSync：同步时间未知。
        assertEquals(listOf("phyvlab", "citel"), homework.getValue("unknown").jsonArray.map { it.jsonPrimitive.content })

        val courses = parse(run("courses", "--json")).getValue("freshness").jsonObject
        assertTrue(courses.getValue("stale").jsonArray.isEmpty())
        assertTrue(courses.getValue("unknown").jsonArray.isEmpty())
        assertFalse(run("courses").stdout.startsWith(">"), "数据都新时不加提示")

        val markdown = run("homework").stdout
        assertTrue(markdown.startsWith("> 缓存可能不是最新：课程平台作业（6 小时前）"))
    }

    @Test
    fun gradesStayHiddenUntilAllowed() {
        val denied = run("grades", "--json")
        assertEquals(AgentExit.NOT_ALLOWED, denied.exitCode)
        assertFalse("线性代数" in denied.stdout)

        val allowed = run("grades", "--json", gradesAllowed = true)
        assertEquals(AgentExit.OK, allowed.exitCode)
        assertTrue("线性代数" in allowed.stdout)
    }

    @Test
    fun statusMasksStudentIdAndNeverPrintsProfileName() {
        val output = run("status", "--json")
        val data = parse(output).getValue("data").jsonObject
        assertEquals("20****01", data.str("account"))
        assertFalse(student in output.stdout)
        assertFalse("测试同学" in output.stdout)
    }

    @Test
    fun usageErrorsAndMissingCache() {
        assertEquals(AgentExit.USAGE, run().exitCode)
        assertEquals(AgentExit.USAGE, run("nope").exitCode)
        assertEquals(AgentExit.USAGE, run("calendar", "--from", "2026-13-01").exitCode)
        assertEquals(AgentExit.USAGE, run("homework", "--source", "mail").exitCode)
        // 参数错误时不得读取缓存（否则可能触发系统安全存储授权）。
        var loads = 0
        val counting = AgentSnapshotProvider { loads++; AgentSnapshotResult.Unavailable("x") }
        listOf(listOf("calendar", "--from", "2026-13-01"), listOf("homework", "--source", "mail"),
            listOf("calendar", "--week", "x"), listOf("homework", "--bogus")).forEach { args ->
            assertEquals(AgentExit.USAGE, runAgentCommand(args, counting, now).exitCode, args.toString())
        }
        assertEquals(0, loads)
        val missing = runAgentCommand(listOf("calendar", "--json"), { AgentSnapshotResult.Unavailable("没有缓存") }, now)
        assertEquals(AgentExit.NO_DATA, missing.exitCode)
        assertEquals("没有缓存", parse(missing).str("error"))
    }

    @Test
    fun cacheReadIsAnAlias() {
        val direct = run("exams", "--json").stdout
        val alias = run("cache", "read", "exams", "--json").stdout
        assertEquals(direct, alias)
        val exam = parse(run("exams", "--json")).getValue("data").jsonArray.single().jsonObject
        assertEquals("2026-11-12", exam.str("date"))
        assertEquals("14:00", exam.str("start"))
    }

    @Test
    fun weekOptionUsesCachedAcademicCalendar() {
        val data = parse(run("calendar", "--week", "11", "--json")).getValue("data").jsonObject
        assertEquals("2026-11-16", data.str("from"))
        assertEquals("2026-11-22", data.str("to"))
    }

    @Test
    fun markdownIsReadable() {
        val output = run("calendar", "--today")
        assertTrue("\n# 日程 2026-11-12 ~ 2026-11-12\n" in output.stdout)
        assertTrue(output.stdout.startsWith("> 缓存可能不是最新"))
        assertTrue("10:30–12:20 [课程] 计算机网络" in output.stdout)
    }

    // region fixture

    private fun run(vararg args: String, gradesAllowed: Boolean = false): AgentOutput {
        val store = fixtureStore()
        return runAgentCommand(args.toList(), { today ->
            AgentSnapshotResult.Ready(loadAgentSnapshot(store, student, today), gradesAllowed, "test")
        }, now)
    }

    private fun fixtureStore(): CacheStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CacheDatabaseSql.Schema.create(driver)
        val store = CacheStore(driver)
        store.saveCachedProfile(StudentProfile(name = "测试同学", studentId = student, identity = "本科生", department = "测试学院"))
        store.savePreferences(AppPreferences(citelEnabled = true, physicsLabEnabled = true, staggeredSecondSlot = true))
        store.saveAcademicWeeksIfChanged(student, (1..20).map { week ->
            val start = LocalDate(2026, 9, 7).let { kotlinx.datetime.LocalDate.fromEpochDays(it.toEpochDays() + (week - 1) * 7) }
            OccupancyWeekDate(week, "", "", start)
        })
        store.replaceCourseSnapshot(student, listOf(
            course(1, "数据结构", slot = 0, place = "海淀西校区,思源楼,SY101"),
            course(2, "计算机网络", slot = 1, place = "海淀西校区,逸夫教学楼,YF301"),
        ), currentWeek = 10)
        store.markSynced(student, SyncSource.COURSES, now.epochSeconds - 3600)
        store.markSynced(student, SyncSource.HOMEWORK, now.epochSeconds - 6 * 3600)
        store.replaceExams(student, listOf(ExamSchedule(1, "期中", "离散数学", "2026-11-12 14:00-16:00 思源101", "已安排", "")))
        store.replaceGrades(student, listOf(Grade(1, "线性代数", "教师", "95", "3", "2025-2026", "1")))
        store.replaceHomework(student, listOf(
            homework(1, 10, "测试课程甲", "实验报告一", end = "2026-11-20 23:59"),
            homework(2, 20, "被隐藏的课", "被隐藏的课的作业", end = "2026-11-21 23:59"),
            homework(3, 10, "测试课程甲", "零点截止的作业", end = "2026-11-13 00:00", submitted = true),
        ))
        store.putMetadata(student, "assignment.aggregate.filters", json.encodeToString(
            AggregateAssignmentFilters(platformCourses = mapOf(AssignmentSource.COURSE_PLATFORM to AggregateCourseFilter(false, setOf(10)))),
        ))
        store.putMetadata(student, "physicslab.configured", "true")
        store.putMetadata(student, "physicslab.results", json.encodeToString(listOf(
            PhysicsLab(LocalDate(2026, 11, 12), 4, "光纤特性和光信号传输", "7305", "教师甲", 1),
        )))
        store.putMetadata(student, "citel.tasks", json.encodeToString(listOf(
            CitelTask(7, 173, "测试课程乙", "栈与队列", "https://citel.bjtu.edu.cn/mlsv3/mod/programming/view.php?id=7",
                programming = true, openTime = epoch("2026-11-01T00:00:00Z"), dueTime = epoch("2026-11-12T15:59:00Z"),
                discountTime = epoch("2026-11-12T15:59:00Z"), status = "WA: Wrong Answer", grade = "12"),
        )))
        val lab = PhyVlabCourse(5, "物理实验II_(2026秋)", "", 0, "https://phyvlab.bjtu.edu.cn/course/view.php?id=5")
        val lecture = PhyVlabCourse(6, "大学物理II_(2026秋)", "", 0, "https://phyvlab.bjtu.edu.cn/course/view.php?id=6")
        store.putMetadata(student, "phyvlab_snapshot_v1", encodePhyVlabCache(PhyVlabCacheSnapshot(
            courses = listOf(lab, lecture),
            activities = listOf(
                activity(51, lab, "34 光纤特性和光信号传输", due = "2026-11-26T15:59:00Z"),
                activity(52, lab, "33 光栅光谱仪测光谱（难度系数1.1）", due = "2026-11-26T15:59:00Z"),
                activity(61, lecture, "第一章作业", due = "2026-11-30T15:59:00Z"),
            ),
            events = emptyList(),
        )))
        return store
    }

    private fun course(id: Int, name: String, slot: Int, place: String) = Course(
        id = id, courseId = "C$id", courseName = name, courseTeacher = "教师", courseLocationIndex = slot * 8 + 4,
        courseTime = "第1-16周", coursePlace = place, isCurrentSemester = false,
    )

    private fun homework(id: Int, courseId: Int, courseName: String, title: String, end: String, submitted: Boolean = false) = Homework(
        id = id, upId = id, idSnId = null, score = "", userId = 1, courseId = courseId, courseName = courseName,
        title = title, content = "", createDate = "2026-11-01 00:00", endTime = end, openDate = "2026-11-01 00:00",
        status = 1, submitCount = 0, allCount = 30, subStatus = if (submitted) "已提交" else "未提交", scoreId = 0, homeworkType = 2,
    )

    private fun activity(id: Int, course: PhyVlabCourse, title: String, due: String) = PhyVlabActivity(
        id = id, courseId = course.id, courseName = course.name, title = title, activityType = "assign",
        activityUrl = "https://phyvlab.bjtu.edu.cn/mod/assign/view.php?id=$id", dueTimestamp = epoch(due),
    )

    private fun epoch(iso: String): Long = Instant.parse(iso).epochSeconds

    private fun parse(output: AgentOutput): JsonObject = Json.parseToJsonElement(output.stdout).jsonObject

    private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

    // endregion
}

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
            ),
            summary,
        )
        assertEquals("citel", items.last().str("source"))
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
        val booked = items.first { it.str("title") == "34 光纤特性和光信号传输" }
        assertEquals("booked", booked.str("enrollment"))
        assertEquals("光纤特性和光信号传输", booked.str("matchedBooking"))

        val all = run("homework", "--all", "--json")
        val allTitles = parse(all).getValue("data").jsonObject.getValue("items").jsonArray.map { it.jsonObject.str("title") }
        assertTrue(allTitles.any { "光栅光谱仪" in it })
        assertTrue("被隐藏的课的作业" in allTitles)
    }

    @Test
    fun homeworkHonorsSavedCourseFilterAndPendingFlag() {
        val titles = parse(run("homework", "--json")).getValue("data").jsonObject.getValue("items").jsonArray
            .map { it.jsonObject.str("title") }
        assertFalse("被隐藏的课的作业" in titles, "沿用 App 里存的课程筛选")

        val pending = parse(run("homework", "--pending", "--source", "course", "--json"))
            .getValue("data").jsonObject.getValue("items").jsonArray.map { it.jsonObject.str("title") }
        assertEquals(listOf("实验报告一"), pending)
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
        assertTrue(output.stdout.startsWith("# 日程 2026-11-12 ~ 2026-11-12"))
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
        store.replaceExams(student, listOf(ExamSchedule(1, "期中", "离散数学", "2026-11-12 14:00-16:00 思源101", "已安排", "")))
        store.replaceGrades(student, listOf(Grade(1, "线性代数", "教师", "95", "3", "2025-2026", "1")))
        store.replaceHomework(student, listOf(
            homework(1, 10, "测试课程甲", "实验报告一", end = "2026-11-20 23:59"),
            homework(2, 20, "被隐藏的课", "被隐藏的课的作业", end = "2026-11-21 23:59"),
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
                discountTime = epoch("2026-11-12T15:59:00Z")),
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

    private fun homework(id: Int, courseId: Int, courseName: String, title: String, end: String) = Homework(
        id = id, upId = id, idSnId = null, score = "", userId = 1, courseId = courseId, courseName = courseName,
        title = title, content = "", createDate = "2026-11-01 00:00", endTime = end, openDate = "2026-11-01 00:00",
        status = 1, submitCount = 0, allCount = 30, subStatus = "未提交", scoreId = 0, homeworkType = 2,
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

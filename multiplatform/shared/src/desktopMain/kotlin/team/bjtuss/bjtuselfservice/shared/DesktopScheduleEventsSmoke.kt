package team.bjtuss.bjtuselfservice.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.datetime.LocalDate
import team.bjtuss.bjtuselfservice.shared.cache.*
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.calendar.UnavailableSystemCalendarGateway
import team.bjtuss.bjtuselfservice.shared.files.UnavailableHomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.feature.course.*
import team.bjtuss.bjtuselfservice.shared.feature.physicslab.*
import team.bjtuss.bjtuselfservice.shared.feature.phydemo.*
import team.bjtuss.bjtuselfservice.shared.feature.settings.*
import team.bjtuss.bjtuselfservice.shared.feature.shell.MoreWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppSection
import team.bjtuss.bjtuselfservice.shared.data.course.*
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.network.*

/** Synthetic local-only UI probe. No user credentials, external requests, or persistent database. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopScheduleEventsSmoke(initialDark: Boolean = false, initialExpanded: Boolean = true, initialTab: String = "课表") {
    var dark by remember { mutableStateOf(initialDark) }
    var expanded by remember { mutableStateOf(initialExpanded) }
    var tab by remember { mutableStateOf(initialTab) }
    var export by remember { mutableStateOf(false) }
    val weeks = remember { listOf(OccupancyWeekDate(1, "11/23", "11/29", LocalDate(2026, 11, 23)), OccupancyWeekDate(2, "11/30", "12/6", LocalDate(2026, 11, 30))) }
    val ordinary = remember { listOf(Course(1, "C312009B", "测试课程", "测试教师", 28, "第1-2周", "测试楼101", false)) }
    val labs = remember { listOf(PhysicsLab(LocalDate(2026, 11, 26), 4, "专题实验", "实验室", "测试教师", 2)) }
    val demos = remember { listOf(PhysicsDemoBooking("光学", LocalDate(2026, 11, 26), 3, "测试助教"),
        PhysicsDemoBooking("近代与综合", LocalDate(2026, 11, 30), 6, "测试助教")) }
    val exams = remember { listOf(ExamSchedule(examType = "期末", courseName = "测试课程", examTimeAndPlace = "2026-11-26 14:30-16:10 测试楼", examStatus = "", detail = "")) }
    val model = remember { CourseScheduleScreenModel(object : CourseScheduleRepository {
        override fun load() = CourseScheduleSnapshot(ordinary, 1)
        override suspend fun refresh() = CourseScheduleRefreshResult.Success(load())
    }) }
    val modelState by model.state.collectAsState()
    val settings = remember { SettingsScreenModel(AppPreferences(), { true }, { true }, { true }, { team.bjtuss.bjtuselfservice.shared.update.AppUpdateChecker.Result.Unavailable }) }
    val settingsState by settings.state.collectAsState()
    val cache = remember {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CacheDatabaseSql.Schema.create(driver)
        CacheStore(driver)
    }
    DisposableEffect(cache) { onDispose { cache.close() } }
    val labModel = remember { PhysicsLabModel("fixture", cache, null, PhysicsLabRemote(object : SchoolHttpTransport {
        override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse = error("Network disabled in UI probe")
        override fun clearSession() {}
    })) }
    PlatformAppTheme(useDarkTheme = dark, dynamicColorEnabled = false) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
                    listOf("课表", "应用", "设置").forEach { label -> TextButton({ tab = label }) { Text(label) } }
                    TextButton({ dark = !dark }) { Text(if (dark) "浅色" else "深色") }
                    TextButton({ expanded = !expanded }) { Text(if (expanded) "窄屏" else "宽屏") }
                    TextButton({ export = true }) { Text("导出日历") }
                }
                when (tab) {
                    "应用" -> MoreWorkspace(settingsState.preferences, { if (it == AppSection.SETTINGS) tab = "设置" }, Modifier.fillMaxSize(), labModel, onOpenPhysicsDemo = { tab = "演示实验" })
                    "演示实验" -> PhysicsDemoResults(PhysicsDemoState(username = "fixture-demo", configured = true, enabled = true,
                        bookings = demos, message = "已查询 2 个预约。", synced = true), {}, {}, {}, Modifier.fillMaxSize())
                    "物理实验" -> PhysicsLabSettingsForm(
                        PhysicsLabState(enabled = true, labs = (1..4).map { index ->
                            PhysicsLab(LocalDate(2026, 11, index), 4, "测试实验 $index", "测试实验室", "测试教师", 1)
                        }, message = "已同步 4 个实验。"),
                        username = "fixture-account", password = "fixture-password", ready = true,
                        onUsername = {}, onPassword = {}, onSave = {},
                    )
                    "日历" -> team.bjtuss.bjtuselfservice.shared.feature.calendar.CourseCalendarExportSheet(
                        modelState.copy(courses = ordinary, academicWeeks = weeks, physicsLabs = labs, physicsDemoBookings = demos),
                        UnavailableHomeworkFileGateway, UnavailableSystemCalendarGateway, {},
                    )
                    "设置" -> SettingsWorkspace(settings, "测试账号", currentPlatform(), expanded, {}, Modifier.fillMaxSize())
                    else -> CourseScheduleWorkspace(
                        state = modelState.copy(courses = ordinary, isLoading = false, currentWeek = 1, weekResolved = true,
                            academicWeeks = weeks, selectedWeek = modelState.selectedWeek.takeIf { it > 0 } ?: 1,
                            calendarSemesterLabel = "2026-2027-1", todayDate = LocalDate(2026, 11, 26),
                            supplementalCourses = scheduleEventCourses(exams, labs, weeks, demos), physicsLabs = labs, physicsDemoBookings = demos),
                        courseTypesByCode = mapOf("C312009B" to CourseType.REQUIRED), expanded = expanded,
                        model = model, fileGateway = UnavailableHomeworkFileGateway, systemCalendarGateway = UnavailableSystemCalendarGateway,
                        showCalendarExportSheet = export, onDismissCalendarExport = { export = false }, onRefresh = {}, modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

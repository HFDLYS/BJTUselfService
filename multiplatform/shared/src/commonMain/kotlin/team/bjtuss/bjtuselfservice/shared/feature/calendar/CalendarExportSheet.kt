package team.bjtuss.bjtuselfservice.shared.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Clock
import team.bjtuss.bjtuselfservice.shared.calendar.SystemCalendarBatch
import team.bjtuss.bjtuselfservice.shared.calendar.SystemCalendarFailure
import team.bjtuss.bjtuselfservice.shared.calendar.SystemCalendarGateway
import team.bjtuss.bjtuselfservice.shared.calendar.SystemCalendarInstallResult
import team.bjtuss.bjtuselfservice.shared.domain.calendar.AcademicCalendarDateRange
import team.bjtuss.bjtuselfservice.shared.domain.calendar.CalendarExportResult
import team.bjtuss.bjtuselfservice.shared.domain.calendar.academicCalendarDateRange
import team.bjtuss.bjtuselfservice.shared.domain.calendar.generateAcademicCalendarIcs
import team.bjtuss.bjtuselfservice.shared.domain.calendar.parseExamCalendarTime
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.feature.course.COURSE_MAX_WEEK
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleType
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleUiState
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileSaveResult

/** 课表允许整学期批量加入；系统日历名严格跟随用户指定的两种课表名称。 */
@Composable
fun CourseCalendarExportSheet(
    courseState: CourseScheduleUiState,
    fileGateway: HomeworkFileGateway,
    systemCalendarGateway: SystemCalendarGateway,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxWeek = courseState.academicWeeks.maxOfOrNull { it.week } ?: COURSE_MAX_WEEK
    val calendarName = if (courseState.scheduleType == CourseScheduleType.CURRENT) {
        "本学期课表"
    } else {
        "选课课表"
    }
    var includePhysicsLabs by remember { mutableStateOf(true) }
    var includePhysicsDemos by remember { mutableStateOf(true) }
    var startWeek by remember { mutableStateOf(1) }
    var endWeek by remember(maxWeek) { mutableStateOf(maxWeek) }
    var working by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val hasCalendar = courseState.academicWeeks.any { it.startDate != null }
    val canPrepare = courseState.scheduleCourses.isNotEmpty() && hasCalendar
    val firstCalendarDate = courseState.academicWeeks.minByOrNull { it.week }?.startDate

    CalendarSheetLayout(
        title = "导出${calendarName}到日历",
        working = working,
        feedback = feedback,
        onCancelOrClose = {
            if (working) {
                job?.cancel()
                working = false
                feedback = "已取消。"
            } else {
                onDone()
            }
        },
        modifier = modifier,
    ) {
        Text("教学周范围", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        WeekRangeStepper(
            startWeek = startWeek,
            endWeek = endWeek,
            maxWeek = maxWeek,
            weekDates = courseState.academicWeeks,
            onStartWeek = { startWeek = it.coerceIn(1, endWeek) },
            onEndWeek = { endWeek = it.coerceIn(startWeek, maxWeek) },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("包含物理实验", Modifier.weight(1f))
            androidx.compose.material3.Switch(includePhysicsLabs, { includePhysicsLabs = it }, enabled = !working)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("包含演示实验", Modifier.weight(1f))
            androidx.compose.material3.Switch(includePhysicsDemos, { includePhysicsDemos = it }, enabled = !working)
        }
        if (hasCalendar) {
            ExportHint(
                "校历学期：${courseState.calendarSemesterLabel ?: "未标注"}" +
                    (firstCalendarDate?.let { "；第 1 周从 $it 开始。" } ?: "。"),
                false,
            )
        }
        when {
            courseState.scheduleCourses.isEmpty() -> ExportHint("当前课表没有可加入的课程。", true)
            !hasCalendar && courseState.scheduleType == CourseScheduleType.SELECTION -> ExportHint(
                "还没有取得选课课表对应的下一学期校历；为避免写入旧学期，已禁用导出。",
                true,
            )
            !hasCalendar -> ExportHint("还没有取得当前学期校历，暂时不能可靠生成课程日期。", true)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (fileGateway.isAvailable) {
                OutlinedButton(
                    enabled = canPrepare && !working,
                    onClick = {
                        feedback = null
                        working = true
                        job = scope.launch {
                            val generated = courseState.generateCourseExport(startWeek..endWeek, calendarName, includePhysicsLabs, includePhysicsDemos)
                            feedback = saveIcs(fileGateway, "$calendarName.ics", generated)
                            working = false
                        }
                    },
                ) { Text("另存 .ics") }
            }
            if (systemCalendarGateway.isAvailable) {
                Button(
                    enabled = canPrepare && !working,
                    onClick = {
                        feedback = null
                        working = true
                        job = scope.launch {
                            val generated = courseState.generateCourseExport(startWeek..endWeek, calendarName, includePhysicsLabs, includePhysicsDemos)
                            feedback = installSystemCalendar(
                                gateway = systemCalendarGateway,
                                name = calendarName,
                                colorHex = "#FF2D55",
                                generated = generated,
                                managedCourseRange = academicCalendarDateRange(courseState.academicWeeks),
                                successSubject = calendarName,
                            )
                            working = false
                        }
                    },
                ) { WorkingButtonLabel(working, "导入日历") }
            } else if (!fileGateway.isAvailable) {
                ExportHint("当前平台没有可用的日历或文件能力。", true)
            }
        }
    }
}

/** 考试只允许当前这一场，避免服务端增删改不同步时批量写入旧安排。 */
@Composable
fun SingleExamCalendarSheet(
    exam: ExamSchedule,
    fileGateway: HomeworkFileGateway,
    systemCalendarGateway: SystemCalendarGateway,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var working by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val generated = remember(exam) {
        generateAcademicCalendarIcs(
            courses = emptyList(),
            exams = listOf(exam),
            academicWeeks = emptyList(),
            weekRange = 1..COURSE_MAX_WEEK,
            generatedAt = Clock.System.now(),
            calendarName = "考试安排",
        )
    }
    val parseable = generated.examEventCount == 1
    val parsedTime = remember(exam.examTimeAndPlace) { parseExamCalendarTime(exam.examTimeAndPlace) }

    CalendarSheetLayout(
        title = "导出考试到日历",
        working = working,
        feedback = feedback,
        onCancelOrClose = {
            if (working) {
                job?.cancel()
                working = false
                feedback = "已取消。"
            } else {
                onDone()
            }
        },
        modifier = modifier,
    ) {
        CalendarConfirmationRow("考试名称", exam.courseName.ifBlank { "未提供" })
        CalendarConfirmationRow(
            "考试时间",
            parsedTime?.let {
                "${it.date.displayChineseDate()} " +
                    "${it.startHour.twoDigits()}:${it.startMinute.twoDigits()}–" +
                    "${it.endHour.twoDigits()}:${it.endMinute.twoDigits()}"
            } ?: exam.examTimeAndPlace.ifBlank { "未提供" },
        )
        CalendarConfirmationRow("地点", parsedTime?.location?.ifBlank { "未提供" } ?: "无法识别")
        if (!parseable) {
            ExportHint("这场考试的日期或开始时间不完整，暂时不能加入日历。", true)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (fileGateway.isAvailable) {
                OutlinedButton(
                    enabled = parseable && !working,
                    onClick = {
                        feedback = null
                        working = true
                        job = scope.launch {
                            feedback = saveIcs(fileGateway, "${exam.courseName}-考试.ics", generated)
                            working = false
                        }
                    },
                ) { Text("另存 .ics") }
            }
            if (systemCalendarGateway.isAvailable) {
                Button(
                    enabled = parseable && !working,
                    onClick = {
                        feedback = null
                        working = true
                        job = scope.launch {
                            feedback = installSystemCalendar(
                                gateway = systemCalendarGateway,
                                name = "考试安排",
                                colorHex = "#FF3B30",
                                generated = generated,
                            )
                            working = false
                        }
                    },
                ) { WorkingButtonLabel(working, "导入日历") }
            } else if (!fileGateway.isAvailable) {
                ExportHint("当前平台没有可用的日历或文件能力。", true)
            }
        }
    }
}

@Composable
private fun CalendarSheetLayout(
    title: String,
    working: Boolean,
    feedback: String?,
    onCancelOrClose: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        content()
        feedback?.let { ExportHint(it, false) }
        TextButton(onClick = onCancelOrClose, modifier = Modifier.align(Alignment.End)) {
            Text(if (working) "取消" else "关闭")
        }
    }
}

private fun CourseScheduleUiState.generateCourseExport(
    range: IntRange,
    calendarName: String,
    includePhysicsLabs: Boolean,
    includePhysicsDemos: Boolean,
): CalendarExportResult = generateAcademicCalendarIcs(
    courses = scheduleCourses.filter { it.scheduleEventKind == null },
    physicsLabs = if (includePhysicsLabs) physicsLabs else emptyList(),
    physicsDemos = if (includePhysicsDemos) physicsDemoBookings else emptyList(),
    exams = emptyList(),
    academicWeeks = academicWeeks,
    weekRange = range,
    generatedAt = Clock.System.now(),
    calendarName = calendarName,
)

private suspend fun saveIcs(
    gateway: HomeworkFileGateway,
    fileName: String,
    generated: CalendarExportResult,
): String = when (
    gateway.saveFile(
        HomeworkFileContent(
            fileName = fileName,
            contentType = "text/calendar; charset=utf-8",
            bytes = generated.ics.encodeToByteArray(),
        ),
    )
) {
    HomeworkFileSaveResult.Saved -> if (generated.courseEventCount > 0) {
        "已保存 ${generated.courseEventCount} 个课次、${generated.physicsLabEventCount} 次物理实验、${generated.physicsDemoEventCount} 次演示实验，共 ${generated.events.size} 个日程。"
    } else {
        "已保存 ${generated.events.size} 个日程。"
    }
    HomeworkFileSaveResult.Cancelled -> "已取消保存，没有残留文件。"
    is HomeworkFileSaveResult.Failed -> "保存失败，请稍后重试。"
}

private suspend fun installSystemCalendar(
    gateway: SystemCalendarGateway,
    name: String,
    colorHex: String,
    generated: CalendarExportResult,
    managedCourseRange: AcademicCalendarDateRange? = null,
    successSubject: String? = null,
): String = when (
    val result = gateway.install(
        listOf(SystemCalendarBatch(name, colorHex, generated.events, managedCourseRange)),
    )
) {
    is SystemCalendarInstallResult.Installed ->
        calendarInstallSuccessMessage(
            calendarName = name,
            successSubject = successSubject,
            insertedCount = result.insertedEventCount,
            updatedCount = result.updatedEventCount,
        )
    SystemCalendarInstallResult.Cancelled -> "已取消，没有改动系统日历。"
    is SystemCalendarInstallResult.Failed -> when (result.reason) {
        SystemCalendarFailure.PERMISSION_DENIED -> "没有日历权限；可在系统设置中允许后重试。"
        SystemCalendarFailure.UNAVAILABLE -> "当前系统日历不可用。"
        SystemCalendarFailure.IO -> "写入系统日历失败，请稍后重试。"
    }
}

internal fun calendarInstallSuccessMessage(
    calendarName: String,
    successSubject: String?,
    insertedCount: Int,
    updatedCount: Int,
): String {
    val prefix = successSubject?.let { "${it}已导入日历" } ?: "已导入日历"
    return "$prefix“$calendarName”，新增 $insertedCount 项，更新 $updatedCount 项。"
}

@Composable
private fun CalendarConfirmationRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun kotlinx.datetime.LocalDate.displayChineseDate(): String =
    "${year}年${month.ordinal + 1}月${day}日"

private fun Int.twoDigits(): String = toString().padStart(2, '0')

@Composable
private fun WorkingButtonLabel(working: Boolean, idleText: String) {
    if (working) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text("正在处理", modifier = Modifier.padding(start = 8.dp))
    } else {
        Text(idleText)
    }
}

@Composable
private fun WeekRangeStepper(
    startWeek: Int,
    endWeek: Int,
    maxWeek: Int,
    weekDates: List<OccupancyWeekDate>,
    onStartWeek: (Int) -> Unit,
    onEndWeek: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WeekStepper(
            label = "起始",
            week = startWeek,
            date = weekDates.firstOrNull { it.week == startWeek },
            canDecrease = startWeek > 1,
            canIncrease = startWeek < endWeek,
            onChange = onStartWeek,
        )
        WeekStepper(
            label = "结束",
            week = endWeek,
            date = weekDates.firstOrNull { it.week == endWeek },
            canDecrease = endWeek > startWeek,
            canIncrease = endWeek < maxWeek,
            onChange = onEndWeek,
        )
    }
}

@Composable
private fun WeekStepper(
    label: String,
    week: Int,
    date: OccupancyWeekDate?,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("$label：第 $week 周", style = MaterialTheme.typography.bodyLarge)
            date?.let {
                Text(
                    "${it.startMonthDay}-${it.endMonthDay}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = canDecrease, onClick = { onChange(week - 1) }) { Text("−") }
            OutlinedButton(enabled = canIncrease, onClick = { onChange(week + 1) }) { Text("+") }
        }
    }
}

@Composable
private fun ExportHint(message: String, isError: Boolean) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

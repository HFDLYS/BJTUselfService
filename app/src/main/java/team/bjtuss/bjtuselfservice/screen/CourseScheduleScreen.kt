package team.bjtuss.bjtuselfservice.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import team.bjtuss.bjtuselfservice.utils.IcsExporter
import team.bjtuss.bjtuselfservice.utils.Utils
import team.bjtuss.bjtuselfservice.viewmodel.DataChange
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

data class MenuItem(
    val content: @Composable () -> Unit,
    val onClick: () -> Unit
)

private val CAMPUS_BUILDINGS = listOf(
    "第十七号教学楼",
    "思源楼",
    "思源西楼",
    "思源东楼",
    "第九教学楼",
    "第八教学楼",
    "第五教学楼",
    "逸夫教学楼",
    "机械楼",
    "东区二教",
    "东区一教",
)

private val COURSE_SLOT_RANGES = listOf(
    "08:00" to "09:50",
    "10:10" to "12:00",
    "12:10" to "14:00",
    "14:10" to "16:00",
    "16:20" to "18:10",
    "19:00" to "20:50",
    "21:00" to "21:50",
)

data class CurrentCourseSlot(val day: Int, val slot: Int)

private fun cleanCourseName(name: String): String =
    name.removeSuffix(" [本]")
        .removeSuffix("[本]")
        .removeSuffix(" [研]")
        .removeSuffix("[研]")

internal fun getCurrentCourseSlot(calendar: Calendar = Calendar.getInstance()): CurrentCourseSlot? {
    val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val calendarDay = calendar.get(Calendar.DAY_OF_WEEK)
    val day = if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1
    COURSE_SLOT_RANGES.forEachIndexed { index, (start, end) ->
        val startMinutes = start.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
        val endMinutes = end.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
        if (nowMinutes >= startMinutes && nowMinutes < endMinutes) {
            return CurrentCourseSlot(day, index)
        }
    }
    return null
}

@Composable
fun GradeTopMenu(menuItemList: List<MenuItem>) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options"
            )
        }

        // 菜单弹出
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }  // s关闭菜单
        ) {
            menuItemList.forEach { item ->
                DropdownMenuItem(
                    text = item.content,
                    onClick = {
                        expanded = false  // 点击后关闭菜单
                        item.onClick()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScheduleScreen(
    mainViewModel: MainViewModel,
    navController: NavController,
) {
    val courseScheduleViewModel = mainViewModel.courseScheduleViewModel
    val classroomViewModel = mainViewModel.classroomViewModel
    val storedCurrentWeek by DataStoreRepository.getCurrentWeek().collectAsState(0)
    LaunchedEffect(Unit) {
        courseScheduleViewModel.syncDataAndClearChange()
        classroomViewModel.refreshClassroomMap()
    }
    LaunchedEffect(storedCurrentWeek) {
        if (storedCurrentWeek <= 0) {
            classroomViewModel.refreshClassroomMap()
        }
    }
    val courseChangeList: List<DataChange<CourseEntity>> by courseScheduleViewModel.changeList.collectAsState()

    LaunchedEffect(courseChangeList) {
        courseScheduleViewModel.syncDataAndClearChange()
    }

    val weekDays = listOf("", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    val courseTimes = listOf(
        "第一节\n08:00\n09:50",
        "第二节\n10:10\n12:00",
        "第三节\n12:10\n14:00",
        "第四节\n14:10\n16:00",
        "第五节\n16:20\n18:10",
        "第六节\n19:00\n20:50",
        "第七节\n21:00\n21:50"
    )

    var expanded by remember { mutableStateOf(false) }

    var currentTerm by rememberSaveable { mutableStateOf(false) }
    val allWeeks = listOf("全部") + (1..26).map { "第${it}周" }
    var showWeekSelector by rememberSaveable { mutableStateOf(false) }
    val classroomMap by classroomViewModel.classroomMap.collectAsState()
    val currentWeek = storedCurrentWeek.takeIf { it > 0 }
        ?: classroomMap["nowWeek"]?.firstOrNull()
        ?: 0
    var selectedWeek by rememberSaveable { mutableIntStateOf(0) }
    var shouldFollowCurrentWeek by rememberSaveable { mutableStateOf(true) }
    var nowTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowTick++
        }
    }
    val currentSlot = remember(nowTick) { getCurrentCourseSlot() }
    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()
    var icsContent by remember { mutableStateOf<String?>(null) }
    val createIcs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        val content = icsContent
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(content.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "课表已导出", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败：" + (it.message ?: "未知错误"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val courseList by
        if (currentTerm) {
            courseScheduleViewModel.currentTermCourseList.collectAsState()
        } else {
            courseScheduleViewModel.nextTermCourseList.collectAsState()
        }

    LaunchedEffect(currentTerm, currentWeek, shouldFollowCurrentWeek) {
        if (!currentTerm && shouldFollowCurrentWeek && currentWeek != 0) {
            selectedWeek = currentWeek
            shouldFollowCurrentWeek = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row {
                        if (!currentTerm) {
                            Text(
                                text = "本学期课表",
                            )
                        } else {
                            Text(
                                text = "选课课表",
                            )
                        }
                        if (selectedWeek != 0) {
                            Text(
                                text = "「第${selectedWeek}周」",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp
                            )
                        }
                    }
                        },
                actions = {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )

                        GradeTopMenu(
                            menuItemList = listOf(
                                MenuItem(
                                    content = { Text("切换学期") },
                                    onClick = {
                                        currentTerm = !currentTerm
                                        selectedWeek = if (!currentTerm) {
                                            currentWeek
                                        } else {
                                            0
                                        }
                                        shouldFollowCurrentWeek = !currentTerm && currentWeek == 0
                                    }
                                ),
                                MenuItem(
                                    content = { Text("选择周数") },
                                    onClick = { showWeekSelector = true }
                                ),
                                MenuItem(
                                    content = { Text("导出课表 (.ics)") },
                                    onClick = {
                                        exportScope.launch {
                                            val courses = runCatching {
                                                AppDatabase.getInstance().courseEntityDao()
                                                    .getCurrentSemesterCourseListBySemester(true)
                                            }.getOrDefault(emptyList())
                                            if (courses.isEmpty()) {
                                                Toast.makeText(context, "暂无可导出的课程", Toast.LENGTH_SHORT).show()
                                            } else {
                                                icsContent = IcsExporter.export(courses, currentWeek)
                                                createIcs.launch("BJTU课表.ics")
                                            }
                                        }
                                    }
                                )
                            )
                        )
                        if (showWeekSelector) {
                            AlertDialog(
                                onDismissRequest = { showWeekSelector = false },
                                title = { Text("选择周数") },
                                text = {
                                    LazyColumn {
                                        allWeeks.forEachIndexed { index, week ->
                                            item {
                                                Text(
                                                    text = week,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (index == selectedWeek) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent)
                                                        .clickable {
                                                            selectedWeek = index
                                                            shouldFollowCurrentWeek = false
                                                            showWeekSelector = false
                                                        }
                                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (index == selectedWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(onClick = { showWeekSelector = false }) {
                                        Text("关闭")
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->


        CourseScheduleContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            courseList = courseList,
            weekDays = weekDays,
            courseTimes = courseTimes,
            selectedWeek = selectedWeek,
            currentSlot = currentSlot,
            highlightCurrent = !currentTerm && currentWeek > 0 && selectedWeek == currentWeek,
            onOpenClassroom = { target ->
                val pieces = target.split("|", limit = 2)
                val building = pieces.getOrNull(0).orEmpty()
                val room = pieces.getOrNull(1).orEmpty()
                navController.navigate(
                    "ClassroomDetection/" + android.net.Uri.encode(building) +
                        "?room=" + android.net.Uri.encode(room)
                )
            },
            classroomMap = classroomMap,
        )
    }
}

@Composable
fun CourseScheduleContent(
    modifier: Modifier = Modifier,
    courseList: List<List<CourseEntity>>,
    weekDays: List<String>,
    courseTimes: List<String>,
    selectedWeek: Int,
    currentSlot: CurrentCourseSlot? = null,
    highlightCurrent: Boolean = false,
    onOpenClassroom: (String) -> Unit = {},
    classroomMap: Map<String, List<Int>> = emptyMap(),
) {

    Column(modifier = modifier) {
        val currentCourses = currentSlot?.let { slot ->
            courseList.getOrNull(slot.slot * weekDays.size + slot.day)
                ?.filter { course ->
                    selectedWeek == 0 || parseCourseTime(course.courseTime).contains(selectedWeek)
                }
        }.orEmpty()
        if (highlightCurrent) {
            val bannerText = when {
                currentSlot == null -> "当前无课"
                currentCourses.isEmpty() -> "当前第" + (currentSlot.slot + 1) + "节 无课"
                else -> "当前第" + (currentSlot.slot + 1) + "节 课程：" +
                    currentCourses.joinToString("、") { cleanCourseName(it.courseName) }
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = bannerText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // 表头：星期
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            weekDays.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        // 课程表主体
        Row(modifier = Modifier.weight(1f)) {
            // 时间列
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
            ) {
                courseTimes.forEach { time ->
                    Box(
                        modifier = Modifier
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = time,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // 课程网格
            CourseScheduleGrid(
                courseList = courseList,
                nowWeek = selectedWeek,
                currentSlot = currentSlot,
                highlightCurrent = highlightCurrent,
                onOpenClassroom = onOpenClassroom,
                classroomMap = classroomMap,
            )
        }
    }
}

@Composable
fun CourseScheduleGrid(
    courseList: List<List<CourseEntity>>,
    nowWeek: Int,
    currentSlot: CurrentCourseSlot? = null,
    highlightCurrent: Boolean = false,
    onOpenClassroom: (String) -> Unit = {},
    classroomMap: Map<String, List<Int>> = emptyMap(),
) {
    val weekDays: Int = 7
    val courseTimes: Int = 7
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(courseTimes) { timeSlot ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                repeat(weekDays) { day ->
                    val index = timeSlot * (weekDays + 1) + (day + 1)
                    val courses = courseList.getOrNull(index)
                    val isCurrent = highlightCurrent &&
                        currentSlot != null &&
                        currentSlot.slot == timeSlot &&
                        currentSlot.day == day + 1

                    CourseCell(
                        courses = courses,
                        modifier = Modifier.weight(1f),
                        nowWeek = nowWeek,
                        isCurrent = isCurrent,
                        onOpenClassroom = onOpenClassroom,
                        classroomMap = classroomMap,
                    )
                }
            }
        }
    }
}

fun parseCourseTime(courseTime: String): List<Int> {
    try {
        val courseTime2 = courseTime.replace("第", "").replace("周", "")
        val courseTimeList = mutableListOf<Int>()
        val timeList = courseTime2.split(",")
        for (time in timeList) {
            if (time.contains("-")) {
                val timeRange = time.split("-")
                for (i in timeRange[0].toInt()..timeRange[1].toInt()) {
                    courseTimeList.add(i)
                }
            } else {
                courseTimeList.add(time.toInt())
            }
        }
        return courseTimeList
    } catch (e: Exception) {
        return emptyList()
    }
}

@Composable
fun CourseCell(
    courses: List<CourseEntity>?,
    modifier: Modifier = Modifier,
    nowWeek: Int,
    isCurrent: Boolean = false,
    onOpenClassroom: (String) -> Unit = {},
    classroomMap: Map<String, List<Int>> = emptyMap(),
) {
    var showDetailedCourseInformationDialog by remember { mutableStateOf(false) }
    var coursesFiltered: List<CourseEntity>? = null
    coursesFiltered = if (nowWeek != 0) {
        courses?.filter { course ->
            val courseTimeList = parseCourseTime(course.courseTime)
            courseTimeList.contains(nowWeek)
        }
    } else {
        courses
    }
    val hasCurrentCourse = isCurrent && !coursesFiltered.isNullOrEmpty()
    Box(
        modifier = modifier
            .border(
                width = if (hasCurrentCourse) 2.dp else 0.5.dp,
                color = if (hasCurrentCourse) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            .background(
                when {
                    hasCurrentCourse -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    coursesFiltered != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
            )
            .clickable(
                onClick = {
                    if (!coursesFiltered.isNullOrEmpty())
                    showDetailedCourseInformationDialog = true
                }
            ),
        contentAlignment = Alignment.Center
    ) {

        Column(modifier = Modifier.fillMaxSize()) {
            if (hasCurrentCourse) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "现在",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            coursesFiltered?.let {
                var count = coursesFiltered.size
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    coursesFiltered.forEach {
                        val backgroundColor = Color(
                            android.graphics.Color.parseColor(
                                Utils.generateRandomColor(
                                    it.courseId,
                                    isSystemInDarkTheme()
                                )
                            )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight((1.0/count).toFloat())
                                .background(backgroundColor),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = it.courseName,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = it.coursePlace,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        count--
                    }
                }
            }
        }
    }
    if (showDetailedCourseInformationDialog) {
        coursesFiltered?.let {
            DetailedCourseInformationDialog(
                courses = it,
                onDismissRequest = {
                    showDetailedCourseInformationDialog = false
                },
                onOpenClassroom = onOpenClassroom,
                classroomMap = classroomMap,
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedCourseInformationDialog(
    courses: List<CourseEntity>,
    onDismissRequest: () -> Unit = {},
    onOpenClassroom: (String) -> Unit = {},
    classroomMap: Map<String, List<Int>> = emptyMap(),
) {

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        content = {
            // Dialog background with padding and rounded corners
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.background,
                        shape = MaterialTheme.shapes.medium
                    )
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp) // Adjusted spacing between items
                ) {
                    // Title of the dialog
                    item {
                        Text(
                            text = "课程信息",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(courses.size) { index ->
                        val course = courses[index]
                        // Card for each course details
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(
                                Color(
                                    android.graphics.Color.parseColor(
                                        Utils.generateRandomColor(
                                            course.courseId,
                                            isSystemInDarkTheme()
                                        )
                                    )
                                )
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Course details with icons
                                CourseDetailRow("课程编号", course.courseId, Icons.Default.Code)
                                CourseDetailRow("课程名称", course.courseName, Icons.Default.Book)
                                CourseDetailRow("教师", course.courseTeacher, Icons.Default.Person)
                                CourseDetailRow(
                                    "上课时间",
                                    course.courseTime,
                                    Icons.Default.Schedule
                                )
                                CourseLocationRow(course, classroomMap, onOpenClassroom)
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun CourseLocationRow(
    course: CourseEntity,
    classroomMap: Map<String, List<Int>>,
    onOpenClassroom: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val parts = course.coursePlace.split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
    val building = parts.firstOrNull { part ->
        CAMPUS_BUILDINGS.any { part.contains(it) }
    }
    val room = parts.lastOrNull()?.takeIf { it.matches(Regex("[A-Za-z]+\\d+.*")) }
    val currentSlot = getCurrentClassIndex()
    val slots = room?.let { classroomMap[it] }
    val freeNow = if (currentSlot >= 0 && slots != null) {
        slots.getOrNull(currentSlot) == 0
    } else {
        null
    }
    val statusColor = when {
        currentSlot < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        freeNow == true -> Color(0xFF34A853)
        freeNow == false -> Color(0xFFEA4335)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when {
        currentSlot < 0 -> "当前是课间，暂无教室状态"
        freeNow == true -> "该教室当前空闲"
        freeNow == false -> "该教室当前有课"
        else -> "暂无该教室的教室数据"
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = "上课地点",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "上课地点",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = course.coursePlace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(statusColor, androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(statusText, color = statusColor, fontWeight = FontWeight.Medium)
                    }
                },
                onClick = {
                    expanded = false
                    if (building != null) onOpenClassroom(building + "|" + (room ?: ""))
                },
            )
            if (building != null) {
                DropdownMenuItem(
                    text = { Text("查看 " + building + " 教室人数评估") },
                    onClick = {
                        expanded = false
                        onOpenClassroom(building + "|" + (room ?: ""))
                    },
                )
            }
        }
    }
}

@Composable
fun CourseDetailRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}



package team.bjtuss.bjtuselfservice.screen

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.utils.Utils
import team.bjtuss.bjtuselfservice.viewmodel.DataChange
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel

data class MenuItem(
    val content: @Composable () -> Unit,
    val onClick: () -> Unit
)

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
            onDismissRequest = { expanded = false }  // 关闭菜单
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
) {
    val courseScheduleViewModel = mainViewModel.courseScheduleViewModel
    val classroomViewModel = mainViewModel.classroomViewModel
    LaunchedEffect(Unit) {
        courseScheduleViewModel.syncDataAndClearChange()
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

    var currentTerm by remember { mutableStateOf(false) }
    val allWeeks = listOf("全部") + (1..26).map { "第${it}周" }
    var showWeekSelector by remember { mutableStateOf(false) }
    val classroomMap = classroomViewModel.classroomMap.collectAsState()
    var selectedWeek by remember { mutableIntStateOf(classroomMap.value["nowWeek"]?.get(0) ?: 0) }
    val courseList by
    if (currentTerm) courseScheduleViewModel.currentTermCourseList.collectAsState() else courseScheduleViewModel.nextTermCourseList.collectAsState()

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
                                            classroomMap.value["nowWeek"]?.get(0) ?: 0
                                        } else {
                                            0
                                        }
                                    }
                                ),
                                MenuItem(
                                    content = { Text("选择周数") },
                                    onClick = { showWeekSelector = true }
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
            selectedWeek = selectedWeek
        )
    }
}

@Composable
fun CourseScheduleContent(
    modifier: Modifier = Modifier,
    courseList: List<List<CourseEntity>>,
    weekDays: List<String>,
    courseTimes: List<String>,
    selectedWeek: Int
) {

    Column(modifier = modifier) {
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
                nowWeek = selectedWeek
            )
        }
    }
}

@Composable
fun CourseScheduleGrid(
    courseList: List<List<CourseEntity>>,
    nowWeek: Int
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

                    CourseCell(
                        courses = courses,
                        modifier = Modifier.weight(1f),
                        nowWeek = nowWeek
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
) {
    var showDetailedCourseInformationDialog by remember { mutableStateOf(false) }
    var courses2: List<CourseEntity>? = null
    courses2 = if (nowWeek != 0) {
        courses?.filter { course ->
            val courseTimeList = parseCourseTime(course.courseTime)
            courseTimeList.contains(nowWeek)
        }
    } else {
        courses
    }
    Box(
        modifier = modifier
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            .background(
                when {
                    courses2 != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
            )
            .clickable(
                onClick = {
                    if (!courses2.isNullOrEmpty())
                    showDetailedCourseInformationDialog = true
                }
            ),
        contentAlignment = Alignment.Center
    ) {

        courses2?.let {
            var count = courses2.size
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                courses2.forEach {
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
    if (showDetailedCourseInformationDialog) {
        courses?.let {
            DetailedCourseInformationDialog(courses = it, onDismissRequest = {
                showDetailedCourseInformationDialog = false
            })
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedCourseInformationDialog(
    courses: List<CourseEntity>,
    onDismissRequest: () -> Unit = {}
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
                                CourseDetailRow("上课地点", course.coursePlace, Icons.Default.Place)
                            }
                        }
                    }
                }
            }
        }
    )
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



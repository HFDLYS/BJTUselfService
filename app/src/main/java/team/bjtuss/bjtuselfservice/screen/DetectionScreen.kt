package team.bjtuss.bjtuselfservice.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.component.RotatingImageLoader
import team.bjtuss.bjtuselfservice.utils.ClassroomPlanner
import team.bjtuss.bjtuselfservice.utils.PlannerResult
import team.bjtuss.bjtuselfservice.utils.PlannerRoom
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService.ClassroomCapacity
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService.getClassroomCapacity
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun BuildingScreen(navController: NavController) {
    val buildingList = listOf(
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
        "东区一教"
    )
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Apartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "教学楼",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "按楼层查看空闲教室",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(buildingList.size) { index ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        onClick = {
                            navController.navigate("ClassroomDetection" + "/${buildingList[index]}")
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Apartment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = buildingList[index],
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val CLASS_TIME_LABELS = listOf(
    "第1节 08:00-10:00",
    "第2节 10:00-12:20",
    "第3节 12:20-14:10",
    "第4节 14:10-16:10",
    "第5节 16:10-18:10",
    "第6节 19:00-21:00",
    "第7节 21:00-22:00",
)

private fun slotLabel(index: Int): String = CLASS_TIME_LABELS.getOrNull(index) ?: "课间"

private fun currentTimeLabel(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun statusColor(status: Int?, fallback: Color): Color = when (status) {
    0 -> Color(0xFF34A853)
    1, 2 -> Color(0xFFEA4335)
    3 -> Color(0xFF1A73E8)
    4 -> Color(0xFF0F9D58)
    5 -> Color(0xFFF9AB00)
    else -> fallback
}

fun getCurrentClassIndex(): Int {
    val courseTimes = listOf(
        "第一节\n08:00\n10:00",
        "第二节\n10:00\n12:20",
        "第三节\n12:20\n14:10",
        "第四节\n14:10\n16:10",
        "第五节\n16:10\n18:10",
        "第六节\n19:00\n21:00",
        "第七节\n21:00\n22:00"
    )

    val currentTime = Calendar.getInstance()
    val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
    val currentMinute = currentTime.get(Calendar.MINUTE)

    for (i in courseTimes.indices) {
        val times = courseTimes[i].split("\n")
        val startTime = times[1]
        val endTime = times[2]

        val (startHour, startMinute) = startTime.split(":").map { it.toInt() }
        val (endHour, endMinute) = endTime.split(":").map { it.toInt() }

        val startTotalMinutes = startHour * 60 + startMinute
        val endTotalMinutes = endHour * 60 + endMinute
        val currentTotalMinutes = currentHour * 60 + currentMinute

        if (currentTotalMinutes in startTotalMinutes..endTotalMinutes) {
            return i
        }
    }

    return -1
}

@Composable
fun ClassroomScreen(
    buildingName: String,
    mainViewModel: MainViewModel,
    initialRoom: String = "",
) {
    val classroomViewModel = mainViewModel.classroomViewModel
    val classroomMap by classroomViewModel.classroomMap.collectAsState()
    val buildingInfo = remember { mutableStateOf<ClassroomCapacityService.BuildingInfo?>(null) }
    var filterExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("教室名") }
    var sortOrder by remember { mutableStateOf(false) }
    var onlyCurrentFree by remember { mutableStateOf(false) }
    var selectedSlots by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var minCapacity by remember { mutableStateOf(0) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showPlannerDialog by remember { mutableStateOf(false) }
    var plannerResult by remember { mutableStateOf<PlannerResult?>(null) }
    var expandedFloors by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var nowTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowTick++
        }
    }
    val currentSlot = remember(nowTick) { getCurrentClassIndex() }

    val showDialog = remember { mutableStateOf(false) }
    val selectedClassroom = remember { mutableStateOf<String>("") }
    // 异步获取教室容量信息
    LaunchedEffect(buildingName) {
        getClassroomCapacity(buildingName).thenAccept {
            buildingInfo.value = it
        }
    }
    LaunchedEffect(buildingInfo.value, initialRoom) {
        val info = buildingInfo.value ?: return@LaunchedEffect
        if (initialRoom.isBlank() || selectedClassroom.value.isNotBlank()) return@LaunchedEffect
        val room = info.ClassroomList.firstOrNull {
            it.RoomName.equals(initialRoom, ignoreCase = true)
        } ?: return@LaunchedEffect
        expandedFloors = expandedFloors + ClassroomPlanner.floorOf(room.RoomName)
        selectedClassroom.value = room.RoomName
        showDialog.value = true
    }
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // 显示标题和基础信息
            buildingInfo.value?.let { info ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = buildingName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                    )
                    Text(
                        text = "数据时间 " + info.EffectiveDateStart.take(16),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(onClick = {}, label = { Text("现在 " + currentTimeLabel()) })
                        AssistChip(
                            onClick = {},
                            label = { Text(if (currentSlot >= 0) slotLabel(currentSlot) else "课间") },
                        )
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    "当前空闲 " + info.ClassroomList.count { classroom ->
                                        if (currentSlot >= 0) classroomMap[classroom.RoomName]?.getOrNull(currentSlot) == 0
                                        else classroom.Capacity > 0 && classroom.Used < classroom.Capacity
                                    } + " 间"
                                )
                            },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !onlyCurrentFree && selectedSlots.isEmpty() && minCapacity == 0,
                            onClick = {
                                onlyCurrentFree = false
                                selectedSlots = emptySet()
                                minCapacity = 0
                            },
                            label = { Text("全部") },
                        )
                        FilterChip(
                            selected = onlyCurrentFree,
                            onClick = {
                                onlyCurrentFree = !onlyCurrentFree
                                selectedSlots = emptySet()
                            },
                            label = { Text("当前空闲") },
                        )
                        AssistChip(
                            onClick = { showFilterDialog = true },
                            label = { Text(if (selectedSlots.isEmpty()) "筛选" else "已选 " + selectedSlots.size + " 节") },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                        AssistChip(
                            onClick = {
                                val slots = if (selectedSlots.isEmpty()) {
                                    if (currentSlot >= 0) setOf(currentSlot) else emptySet()
                                } else selectedSlots
                                plannerResult = if (slots.isEmpty()) {
                                    PlannerResult.Failure("当前是课间，请先选择要规划的节次")
                                } else {
                                    ClassroomPlanner.plan(
                                        rooms = info.ClassroomList.map { PlannerRoom(it.RoomName, it.Capacity) },
                                        slotsByRoom = classroomMap,
                                        slots = slots.toList(),
                                        minCapacity = minCapacity,
                                    )
                                }
                                showPlannerDialog = true
                            },
                            label = { Text("规划") },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LegendDot(Color(0xFF34A853), "空闲")
                        LegendDot(Color(0xFFEA4335), "占用")
                        LegendDot(Color(0xFFF9AB00), "特殊")
                        Text(
                            "圆形描边 = 当前节",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            filterExpanded = true
                        }
                    ) {
                        Text(
                            text = selectedFilter,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    // 排序方式
                    DropdownMenu(
                        expanded = filterExpanded,
                        onDismissRequest = { filterExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.background)
                    ) {
                        val filterOptions = listOf("教室名", "占用率", "人数")
                        filterOptions.forEach { option ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedFilter = option
                                    filterExpanded = false
                                },
                                text = {
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            sortOrder = !sortOrder
                        },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            imageVector = when (sortOrder) {
                                true -> Icons.Default.ArrowUpward
                                false -> Icons.Default.ArrowDownward
                            },
                            contentDescription = "Sort Order"
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {

                    var filteredClassroomList = info.ClassroomList.filter { classroom ->
                        val slots = classroomMap[classroom.RoomName]
                        val freeNow = if (currentSlot >= 0) {
                            slots?.getOrNull(currentSlot) == 0
                        } else {
                            classroom.Capacity > 0 && classroom.Used < classroom.Capacity
                        }
                        classroom.Capacity >= minCapacity &&
                            (!onlyCurrentFree || freeNow) &&
                            (selectedSlots.isEmpty() || selectedSlots.all { slots?.getOrNull(it) == 0 })
                    }
                    filteredClassroomList = when (selectedFilter) {
                        "教室名" -> filteredClassroomList.sortedBy { it.RoomName }
                        "占用率" -> filteredClassroomList.sortedBy {
                            if (it.Capacity > 0) it.Used.toDouble() / it.Capacity else 0.0
                        }
                        "人数" -> filteredClassroomList.sortedBy { it.Used }
                        else -> filteredClassroomList
                    }
                    if (sortOrder) {
                        filteredClassroomList = filteredClassroomList.reversed()
                    }
                    if (filteredClassroomList.isEmpty()) {
                        item {
                            Text(
                                "没有符合条件的教室，试试减少节次或降低容量要求",
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val groupedByFloor = filteredClassroomList
                        .groupBy { ClassroomPlanner.floorOf(it.RoomName) }
                        .toSortedMap()
                    groupedByFloor.forEach { (floor, rooms) ->
                        item(key = "floor-" + floor) {
                            FloorHeader(
                                floor = floor,
                                rooms = rooms,
                                classroomMap = classroomMap,
                                currentSlot = currentSlot,
                                expanded = floor in expandedFloors,
                                onToggle = {
                                    expandedFloors = if (floor in expandedFloors) {
                                        expandedFloors - floor
                                    } else {
                                        expandedFloors + floor
                                    }
                                },
                            )
                        }
                        if (floor in expandedFloors) {
                            items(rooms, key = { it.RoomName }) { classroom ->
                                ClassroomCard(classroom, classroomMap, currentSlot) {
                                    selectedClassroom.value = classroom.RoomName
                                    showDialog.value = true
                                }
                            }
                        }
                    }
                }
                if (showDialog.value && selectedClassroom.value.isNotEmpty() && ClassroomCapacityService.ok) {
                    ClassroomViewDialog(
                        onDismiss = { showDialog.value = false },
                        buildingName = buildingName,
                        classroomName = selectedClassroom.value
                    )
                }
                if (showFilterDialog) {
                    var draftSlots by remember(showFilterDialog) { mutableStateOf(selectedSlots) }
                    var draftCapacity by remember(showFilterDialog) { mutableStateOf(minCapacity.toString()) }
                    AlertDialog(
                        onDismissRequest = { showFilterDialog = false },
                        title = { Text("筛选空闲教室") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("选择节次（可多选，要求这些节次全部空闲）", style = MaterialTheme.typography.bodySmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    repeat(7) { index ->
                                        FilterChip(
                                            selected = index in draftSlots,
                                            onClick = {
                                                draftSlots = if (index in draftSlots) draftSlots - index else draftSlots + index
                                            },
                                            label = { Text("第" + (index + 1) + "节", style = MaterialTheme.typography.labelSmall) },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = draftCapacity,
                                    onValueChange = { draftCapacity = it.filter(Char::isDigit) },
                                    label = { Text("最小容量") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text("当前仅包含今天的课表数据", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                selectedSlots = draftSlots
                                minCapacity = draftCapacity.toIntOrNull() ?: 0
                                onlyCurrentFree = false
                                showFilterDialog = false
                            }) { Text("应用") }
                        },
                        dismissButton = { TextButton(onClick = { showFilterDialog = false }) { Text("取消") } },
                    )
                }
                if (showPlannerDialog) {
                    AlertDialog(
                        onDismissRequest = { showPlannerDialog = false },
                        title = { Text("教室迁移规划") },
                        text = {
                            when (val result = plannerResult) {
                                is PlannerResult.Success -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("按楼层差最小生成路径", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    result.steps.forEach { step ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text((step.slot + 1).toString(), style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(step.room, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    ClassroomPlanner.floorOf(step.room).toString() + " 层 · " + slotLabel(step.slot),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Text("总换层数：" + result.floorChanges + " 层", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                is PlannerResult.Failure -> Text(result.reason, color = MaterialTheme.colorScheme.error)
                                null -> Text("暂无规划结果")
                            }
                        },
                        confirmButton = { TextButton(onClick = { showPlannerDialog = false }) { Text("知道了") } },
                    )
                }
            } ?: run {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 32.dp)
                ) {
                    RotatingImageLoader(
                        image = painterResource(id = R.drawable.loading_icon),
                        rotationDuration = 1000,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun FloorHeader(
    floor: Int,
    rooms: List<ClassroomCapacity>,
    classroomMap: Map<String, List<Int>>,
    currentSlot: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val freeCount = rooms.count { classroom ->
        if (currentSlot >= 0) {
            classroomMap[classroom.RoomName]?.getOrNull(currentSlot) == 0
        } else {
            classroom.Capacity > 0 && classroom.Used < classroom.Capacity
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (floor > 0) floor.toString() + " 层" else "其他教室",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = rooms.size.toString() + " 间 · 当前空闲 " + freeCount + " 间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ClassroomViewDialog(
    onDismiss: () -> Unit,
    buildingName: String,
    classroomName: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("教室详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("教学楼：$buildingName")
                Text("教室：$classroomName")
                Text(
                    "当前版本的教室占用详情需要在网页端查看。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

@Composable
fun ClassroomCard(
    classroom: ClassroomCapacity,
    classroomMap: Map<String, List<Int>>,
    currentSlot: Int = getCurrentClassIndex(),
    onClick: () -> Unit = {}
) {
    val slots = classroomMap[classroom.RoomName]
    val nowFree = when {
        currentSlot >= 0 -> slots?.getOrNull(currentSlot) == 0
        classroom.Capacity > 0 -> classroom.Used < classroom.Capacity
        else -> null
    }
    val statusLabel = when (nowFree) {
        true -> "当前空闲"
        false -> "当前有课"
        null -> "状态未知"
    }
    val tint = when (nowFree) {
        true -> Color(0xFF34A853)
        false -> Color(0xFFEA4335)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val ratio = if (classroom.Capacity > 0) {
        (classroom.Used.toFloat() / classroom.Capacity).coerceIn(0f, 1f)
    } else 0f

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = classroom.RoomName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = ClassroomPlanner.floorOf(classroom.RoomName).toString() + " 层 · 容量 " + classroom.Capacity + " 人",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tint.copy(alpha = 0.12f),
                ) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (classroom.Capacity > 0) "实时使用 " + classroom.Used + "/" + classroom.Capacity else "实时人数未知",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (currentSlot >= 0) slotLabel(currentSlot) else "课间",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (classroom.Capacity > 0) {
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = when {
                        ratio >= 1f -> Color(0xFFEA4335)
                        ratio >= 0.8f -> Color(0xFFF9AB00)
                        else -> Color(0xFF34A853)
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(7) { index ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(statusColor(slots?.getOrNull(index), MaterialTheme.colorScheme.surfaceVariant))
                                .border(
                                    width = if (currentSlot == index) 2.dp else 0.dp,
                                    color = if (currentSlot == index) MaterialTheme.colorScheme.error else Color.Transparent,
                                    shape = CircleShape,
                                ),
                        )
                        Text(
                            (index + 1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
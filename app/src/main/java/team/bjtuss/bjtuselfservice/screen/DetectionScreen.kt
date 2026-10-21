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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.component.RotatingImageLoader
import team.bjtuss.bjtuselfservice.reminder.ClassroomReservation
import team.bjtuss.bjtuselfservice.reminder.ClassroomReservationScheduler
import team.bjtuss.bjtuselfservice.reminder.ClassroomSubscription
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import team.bjtuss.bjtuselfservice.utils.ClassroomPlanner
import team.bjtuss.bjtuselfservice.utils.PlannerResult
import team.bjtuss.bjtuselfservice.utils.PlannerRoom
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService.ClassroomCapacity
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService.getClassroomCapacity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    1, 2, 3, 4, 5 -> Color(0xFFEA4335)
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
    var showPlanSetup by remember { mutableStateOf(false) }
    var planSelectedSlots by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var planCapacity by remember { mutableStateOf(0) }
    var planError by remember { mutableStateOf<String?>(null) }
    var plannerResult by remember { mutableStateOf<PlannerResult?>(null) }
    var expandedFloors by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var flashRoom by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
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
        if (initialRoom.isBlank()) return@LaunchedEffect
        val room = info.ClassroomList.firstOrNull {
            it.RoomName.equals(initialRoom, ignoreCase = true)
        } ?: return@LaunchedEffect
        val targetFloor = ClassroomPlanner.floorOf(room.RoomName)
        onlyCurrentFree = false
        selectedSlots = emptySet()
        minCapacity = 0
        selectedFilter = "教室名"
        sortOrder = false
        expandedFloors = setOf(targetFloor)

        val grouped = info.ClassroomList
            .sortedBy { it.RoomName }
            .groupBy { ClassroomPlanner.floorOf(it.RoomName) }
            .toSortedMap()
        var index = 0
        for ((floor, rooms) in grouped) {
            if (floor == targetFloor) {
                index += 1 + rooms.indexOfFirst { it.RoomName == room.RoomName }
                break
            }
            index += 1
        }
        delay(350)
        flashRoom = room.RoomName
        if (index > 0) {
            listState.animateScrollToItem(index)
        }
        delay(900)
        if (flashRoom == room.RoomName) flashRoom = null
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
                    }
                    AssistChip(
                        onClick = {
                            val currentHasFreeRoom = currentSlot >= 0 && (
                                classroomMap.isEmpty() || info.ClassroomList.any { classroom ->
                                    classroomMap[classroom.RoomName]?.getOrNull(currentSlot) == 0
                                }
                            )
                            planSelectedSlots = if (currentHasFreeRoom) setOf(currentSlot) else emptySet()
                            planCapacity = 0
                            planError = null
                            showPlanSetup = true
                        },
                        modifier = Modifier.padding(top = 4.dp),
                        label = { Text("教室迁移规划 · 选择课时") },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LegendDot(Color(0xFF34A853), "空闲")
                        LegendDot(Color(0xFFEA4335), "已排课")
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
                    state = listState,
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
                                ClassroomCard(
                                    classroom = classroom,
                                    classroomMap = classroomMap,
                                    currentSlot = currentSlot,
                                    highlighted = flashRoom == classroom.RoomName,
                                ) {
                                    selectedClassroom.value = classroom.RoomName
                                    showDialog.value = true
                                }
                            }
                        }
                    }
                }
                if (showDialog.value && selectedClassroom.value.isNotEmpty()) {
                    ClassroomViewDialog(
                        onDismiss = { showDialog.value = false },
                        buildingName = buildingName,
                        classroomName = selectedClassroom.value,
                        classroomMap = classroomMap,
                        classroom = buildingInfo.value?.ClassroomList?.firstOrNull {
                            it.RoomName == selectedClassroom.value
                        },
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
                                        val enabled = classroomMap.isEmpty() || info.ClassroomList.any { classroom ->
                                            classroomMap[classroom.RoomName]?.getOrNull(index) == 0
                                        }
                                        FilterChip(
                                            selected = index in draftSlots,
                                            enabled = enabled,
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
                if (showPlanSetup) {
                    val info = buildingInfo.value
                    if (info != null) {
                        fun hasFreeRoom(slot: Int): Boolean =
                            classroomMap.isEmpty() || info.ClassroomList.any { classroom ->
                                classroomMap[classroom.RoomName]?.getOrNull(slot) == 0
                            }
                        AlertDialog(
                            onDismissRequest = { showPlanSetup = false },
                            title = { Text("教室迁移规划") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("选择需要连续空课的节次（可多选）", style = MaterialTheme.typography.bodySmall)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        repeat(7) { index ->
                                            val enabled = hasFreeRoom(index)
                                            FilterChip(
                                                selected = index in planSelectedSlots,
                                                enabled = enabled,
                                                onClick = {
                                                    planSelectedSlots = if (index in planSelectedSlots) {
                                                        planSelectedSlots - index
                                                    } else {
                                                        planSelectedSlots + index
                                                    }
                                                    planError = null
                                                },
                                                label = { Text("第" + (index + 1) + "节", style = MaterialTheme.typography.labelSmall) },
                                            )
                                        }
                                    }
                                    if (planSelectedSlots.isEmpty()) {
                                        Text("灰色节次表示该教学楼所有教室都有课，不能选择", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedTextField(
                                        value = planCapacity.toString(),
                                        onValueChange = { planCapacity = it.filter(Char::isDigit).toIntOrNull() ?: 0 },
                                        label = { Text("最小容量") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    planError?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (planSelectedSlots.isEmpty()) {
                                        planError = "请至少选择一个节次"
                                    } else {
                                        plannerResult = ClassroomPlanner.plan(
                                            rooms = info.ClassroomList.map { PlannerRoom(it.RoomName, it.Capacity) },
                                            slotsByRoom = classroomMap,
                                            slots = planSelectedSlots.toList(),
                                            minCapacity = planCapacity,
                                        )
                                        showPlanSetup = false
                                        showPlannerDialog = true
                                    }
                                }) { Text("生成规划") }
                            },
                            dismissButton = { TextButton(onClick = { showPlanSetup = false }) { Text("取消") } },
                        )
                    }
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
    classroomName: String,
    classroomMap: Map<String, List<Int>>,
    classroom: ClassroomCapacity? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showReservationDialog by remember { mutableStateOf(false) }
    val slot = getCurrentClassIndex()
    val slots = classroomMap[classroomName]
    val status = if (slot >= 0) slots?.getOrNull(slot) else null
    val statusText = when {
        slot < 0 -> "课间"
        status == 0 -> "当前空闲"
        status != null -> "当前有课"
        else -> "暂无数据"
    }
    val statusColor = when {
        status == 0 -> Color(0xFF34A853)
        status != null -> Color(0xFFEA4335)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val reservations by DataStoreRepository.getClassroomReservations().collectAsState(emptyList())
    val roomReservations = reservations
        .filter {
            it.building == buildingName && it.room == classroomName &&
                runCatching { LocalDate.parse(it.date) >= LocalDate.now() }.getOrDefault(true)
        }
        .sortedBy { it.date + "-" + (it.slots.minOrNull() ?: 0) }
    val periodStarts = listOf("08:00", "10:10", "12:10", "14:10", "16:20", "19:00", "21:00")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(classroomName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    buildingName + " · " + ClassroomPlanner.floorOf(classroomName) + " 层",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                classroom?.let {
                    Text(
                        "容量 " + it.Capacity + " 人 · 实时 " + it.Used + "/" + it.Capacity,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "当前：$statusText",
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                    )
                    if (status != null) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "第" + (slot + 1) + "节",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider()
                Text("今日各节次", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                repeat(7) { index ->
                    val state = slots?.getOrNull(index)
                    val free = state == 0
                    val isNow = index == slot
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isNow) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "第" + (index + 1) + "节",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.width(52.dp),
                        )
                        Text(
                            periodStarts[index],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(52.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            when {
                                state == null -> "暂无数据"
                                free -> "空闲"
                                else -> "有课"
                            },
                            color = when {
                                free -> Color(0xFF34A853)
                                state != null -> Color(0xFFEA4335)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (roomReservations.isNotEmpty()) {
                    HorizontalDivider()
                    Text("我的预约", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    roomReservations.take(3).forEach { reservation ->
                        Text(
                            reservation.date.substringAfter("-").replace("-", "/") + " · " +
                                reservation.slots.sorted().joinToString("、") { "第" + (it + 1) + "节" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Button(
                    onClick = { showReservationDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("预约这间教室")
                }
                if (slot >= 0) {
                    TextButton(
                        onClick = {
                            val calendar = Calendar.getInstance()
                            val day = if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                                7
                            } else {
                                calendar.get(Calendar.DAY_OF_WEEK) - 1
                            }
                            scope.launch {
                                DataStoreRepository.addClassroomSubscription(
                                    ClassroomSubscription(
                                        buildingName,
                                        classroomName,
                                        day,
                                        slot,
                                        classroomMap[classroomName],
                                    )
                                )
                                Toast.makeText(context, "已订阅本节空闲提醒", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("订阅本节空闲提醒")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
    if (showReservationDialog) {
        ClassroomReservationDialog(
            buildingName = buildingName,
            classroomName = classroomName,
            classroomMap = classroomMap,
            onDismiss = { showReservationDialog = false },
        )
    }
}

@Composable
private fun ClassroomReservationDialog(
    buildingName: String,
    classroomName: String,
    classroomMap: Map<String, List<Int>>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedSlots by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    val dates = (0..6).map { LocalDate.now().plusDays(it.toLong()) }
    val slots = classroomMap[classroomName].orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("预约教室") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    buildingName + " " + classroomName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "选择日期",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    dates.forEach { date ->
                        FilterChip(
                            selected = selectedDate == date,
                            onClick = { selectedDate = date; error = null },
                            label = {
                                Text(
                                    when (date) {
                                        LocalDate.now() -> "今天"
                                        LocalDate.now().plusDays(1) -> "明天"
                                        else -> date.format(DateTimeFormatter.ofPattern("MM/dd"))
                                    }
                                )
                            },
                        )
                    }
                }
                Text("选择节次（按本周课表校验）", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(7) { index ->
                        val occupied = slots.getOrNull(index) != 0 && slots.isNotEmpty()
                        FilterChip(
                            selected = index in selectedSlots,
                            enabled = !occupied,
                            onClick = {
                                selectedSlots = if (index in selectedSlots) {
                                    selectedSlots - index
                                } else {
                                    selectedSlots + index
                                }
                                error = null
                            },
                            label = { Text("第" + (index + 1) + "节") },
                        )
                    }
                }
                Text(
                    "仅保存在本机，不会同步学校预约系统；开始前 10 分钟通过通知提醒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selectedSlots.isEmpty()) {
                    error = "请至少选择一个节次"
                    return@TextButton
                }
                val reservation = ClassroomReservation(
                    id = System.currentTimeMillis(),
                    building = buildingName,
                    room = classroomName,
                    date = selectedDate.toString(),
                    slots = selectedSlots.sorted(),
                )
                scope.launch {
                    DataStoreRepository.addClassroomReservation(reservation)
                    ClassroomReservationScheduler.schedule(context, reservation)
                    Toast.makeText(context, "已预约，开始前会提醒", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }) { Text("确认预约") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun ClassroomCard(
    classroom: ClassroomCapacity,
    classroomMap: Map<String, List<Int>>,
    currentSlot: Int = getCurrentClassIndex(),
    highlighted: Boolean = false,
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
    val cardColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(220),
        label = "classroom-highlight",
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.large,
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor),
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
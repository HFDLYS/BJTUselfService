package team.bjtuss.bjtuselfservice.screen

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.constant.ApiConstant.CLASSROOM_VIEW_URL
import team.bjtuss.bjtuselfservice.viewmodel.ClassroomViewModel
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService.ClassroomCapacity
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService.getClassroomCapacity
import java.nio.charset.StandardCharsets
import java.util.Calendar

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
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "请择欲往之教学楼",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        ),
                        textAlign = TextAlign.Center
                    )
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
                        Box(
                            modifier = Modifier.padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = buildingList[index],
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
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
    classroomViewModel: ClassroomViewModel
) {
    val classroomMap by classroomViewModel.classroomMap.collectAsState()
    val buildingInfo = remember { mutableStateOf<ClassroomCapacityService.BuildingInfo?>(null) }
    var filterExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("教室名") }
    var sortOrder by remember { mutableStateOf(false) }

    val showDialog = remember { mutableStateOf(false) }
    val selectedClassroom = remember { mutableStateOf<String>("") }
    // 异步获取教室容量信息
    LaunchedEffect(buildingName) {
        getClassroomCapacity(buildingName).thenAccept {
            buildingInfo.value = it
        }
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
                        text = info.BuildingName,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 1.dp),
                    )
                    Text(
                        text = "有效期：${info.EffectiveDateStart} 至 ${info.EffectiveDateEnd}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { filterExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Filter Options"
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

                    var filteredClassroomList = when (selectedFilter) {
                        "教室名" -> info.ClassroomList.sortedBy { it.RoomName }
                        "占用率" -> info.ClassroomList.sortedBy { it.Used.toDouble() / it.Capacity }
                        "人数" -> info.ClassroomList.sortedBy { it.Used }
                        else -> info.ClassroomList
                    }
                    if (sortOrder) {
                        filteredClassroomList = filteredClassroomList.reversed()
                    }
                    items(info.ClassroomList.size) { index ->
                        val classroom = filteredClassroomList[index]
                        ClassroomCard(classroom, classroomMap) {
                            selectedClassroom.value = classroom.RoomName
                            showDialog.value = true
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ClassroomViewDialog(
    onDismiss: () -> Unit,
    buildingName: String,
    classroomName: String
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AndroidView(
                modifier = Modifier
                    .height(130.dp)
                    .align(Alignment.CenterHorizontally),
                factory = { context ->
                    WebView(context).apply {
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                return false
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                                Log.d("WebView", message?.message() ?: "")
                                return super.onConsoleMessage(message)
                            }
                        }

                        val postData = "buildi=$buildingName&classrooms=$classroomName"
                        val postDataBytes = postData.toByteArray(StandardCharsets.UTF_8)

                        postUrl(CLASSROOM_VIEW_URL, postDataBytes)
                        // loadUrl("https://www.google.com")
                    }
                },
                update = {
                    val postData = "buildi=$buildingName&classrooms=$classroomName"
                    val postDataBytes = postData.toByteArray(StandardCharsets.UTF_8)

                    it.postUrl(CLASSROOM_VIEW_URL, postDataBytes)
                },
            )
        }
    }
}

@Composable
fun ClassroomCard(
    classroom: ClassroomCapacity,
    classroomMap: Map<String,List<Int>>,
    onClick: () -> Unit = {}
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(bottom = 16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
    ) {
        Column  (
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = classroom.RoomName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = "使用：${classroom.Used}/${classroom.Capacity}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            val progress = if (classroom.Capacity > 0) {
                classroom.Used.toFloat() / classroom.Capacity.toFloat()
            } else {
                0f
            }

            if (classroom.Used < classroom.Capacity) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface,
                )
            } else {
                Text(
                    text = "无法读取本教室人数",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                repeat(7) { index ->
                    var color = MaterialTheme.colorScheme.surface
                    if (classroomMap.contains(classroom.RoomName)) {
                        when (classroomMap[classroom.RoomName]!![index]) {
                            0 -> color = MaterialTheme.colorScheme.surface
                            1 -> color = Color(0xffe46868)
                            2 -> color = Color(0xff9e6868)
                            3 -> color = Color(0xff394ed6)
                            4 -> color = Color(0xff77bf6d)
                            5 -> color = Color(0xffd8cc56)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (getCurrentClassIndex() == index) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                    if (index < 6) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}
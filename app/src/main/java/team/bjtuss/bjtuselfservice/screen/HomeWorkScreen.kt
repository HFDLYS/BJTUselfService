package team.bjtuss.bjtuselfservice.screen

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.room.util.TableInfo
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun HomeWorkScreen(mainViewModel: MainViewModel) {
    LaunchedEffect(Unit) {
        mainViewModel.homeworkViewModel.syncDataAndClearChange()
    }
    val homeworkList by mainViewModel.homeworkViewModel.homeworkList.collectAsState()

    HomeworkList(homeworkList)
}

@Composable
fun HomeworkList(homeworkList: List<HomeworkEntity>) {
    var filterExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("所有课程") }
    var isFilterOutOfDate by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(SortOrder.DESCENDING) }
    var filteredList by remember { mutableStateOf(homeworkList) }
    filteredList = homeworkList.filter { homework ->
        val isValidDate = try {
            LocalDateTime.parse(homework.endTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                .isAfter(LocalDateTime.now())
        } catch (_: Exception) {
            true
        }

        val dateCondition = !isFilterOutOfDate || isValidDate
        val courseCondition = selectedFilter == "所有课程" || homework.courseName == selectedFilter

        dateCondition && courseCondition
    }
    filteredList = if (sortOrder == SortOrder.ASCENDING) {
        filteredList.sortedBy { it.endTime }
    } else {
        filteredList.sortedByDescending { it.endTime }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            HomeworkSummaryCard(filteredList)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        filterExpanded = true
                    },
                ) {
                    val transText = if (selectedFilter.length > 5) {
                        selectedFilter.take(4) + "..."
                    } else {
                        selectedFilter
                    }
                    Text(
                        text = transText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                DropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false }
                ) {
                    val filterOptions = mutableListOf("所有课程")
                    filterOptions.addAll(homeworkList.map { it.courseName }.distinct())
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

                Row {
                    Button(
                        onClick = {
                            isFilterOutOfDate = !isFilterOutOfDate
                        }
                    ) {
                        Text(
                            text = if (isFilterOutOfDate) "悟已往之不谏" else "展示全部",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    IconButton(
                        onClick = {
                            sortOrder = if (sortOrder == SortOrder.ASCENDING) {
                                SortOrder.DESCENDING
                            } else {
                                SortOrder.ASCENDING
                            }
                        }
                    ) {
                        Icon(
                            imageVector = when (sortOrder) {
                                SortOrder.ASCENDING -> Icons.Default.ArrowUpward
                                SortOrder.DESCENDING -> Icons.Default.ArrowDownward
                                SortOrder.ORIGINAL -> Icons.Default.Sort
                            },
                            contentDescription = "Sort Order"
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredList.size) { index ->
                    val homework = filteredList[index]
                    HomeworkItemCard(homework)
                }
            }
        }
    }
}

@Composable
fun HomeworkSummaryCard(homeworkList: List<HomeworkEntity>) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val now = LocalDateTime.now()
    var countForDeadline = 0
    homeworkList.forEach {
        try {
            if (ChronoUnit.HOURS.between(now, LocalDateTime.parse(it.endTime, formatter)) in 0..48) {
                if (it.subStatus != "已提交"){
                    countForDeadline++
                }
            }
        } catch (_: Exception) {}
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "作业安排：${homeworkList.size}项",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            )
            if (countForDeadline > 0) {
                Text(
                    text = "其中有${countForDeadline}项作业已经迫在眉睫！",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.error
                    )
                )
            }
        }
    }
}

@Composable
fun HomeworkItemCard(homework: HomeworkEntity) {
    var showDialog by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                showDialog = true
            },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row (
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ){
            Column(
                modifier = Modifier.weight(0.7f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = homework.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = homework.courseName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Create Date",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "开放时间: ${homework.openDate}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "截止时间: ${homework.endTime}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AddTask,
                        contentDescription = "subStatus",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "提交人数: ${homework.submitCount}/${homework.allCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Column (
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {

                val icon = if (homework.subStatus == "已提交") {
                    Icons.Default.Check to Color.Green
                } else {
                    Icons.Default.Close to Color.Red
                }

                Icon(
                    imageVector = icon.first,
                    contentDescription = null,
                    tint = icon.second,
                    modifier = Modifier.size(36.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${homework.subStatus}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
    if (showDialog) {
        ShowHtmlDialog({ showDialog = false }, homework.content)
    }
}

@Composable
fun ShowHtmlDialog(onDismiss: () -> Unit, htmlContent: String) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.4f)
                    .align(Alignment.CenterHorizontally),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true  // 启用JavaScript
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.textZoom = 300
                        webViewClient = WebViewClient()
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
                }
            )
        }
    }
}
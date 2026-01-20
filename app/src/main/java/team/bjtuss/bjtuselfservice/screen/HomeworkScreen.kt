package team.bjtuss.bjtuselfservice.screen

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturb
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.component.HomeworkUploader
import team.bjtuss.bjtuselfservice.component.downloadHomeworkFile
import team.bjtuss.bjtuselfservice.component.getHomeworkGrade
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import team.bjtuss.bjtuselfservice.error
import team.bjtuss.bjtuselfservice.primary
import team.bjtuss.bjtuselfservice.primaryContainer
import team.bjtuss.bjtuselfservice.repository.DatabaseRepository
import team.bjtuss.bjtuselfservice.statemanager.AppState
import team.bjtuss.bjtuselfservice.statemanager.AppStateManager
import team.bjtuss.bjtuselfservice.viewmodel.DataChange
import team.bjtuss.bjtuselfservice.viewmodel.HomeworkViewModel
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun HomeworkScreen(mainViewModel: MainViewModel) {
    LaunchedEffect(Unit) {
        mainViewModel.homeworkViewModel.syncDataAndClearChange()
    }
    val homeworkList by mainViewModel.homeworkViewModel.homeworkList.collectAsState()
    val homeworkChangeList: List<DataChange<HomeworkEntity>> by mainViewModel.homeworkViewModel.changeList.collectAsState()

    LaunchedEffect(homeworkChangeList) {
        mainViewModel.homeworkViewModel.syncDataAndClearChange()
    }

    HomeworkList(homeworkList)
}

@Composable
fun HomeworkList(homeworkList: List<HomeworkEntity>) {
    var filterExpanded by remember { mutableStateOf(false) }
    var selectedFilters by remember { mutableStateOf(setOf<String>()) }
    var isFilterOutOfDate by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(SortOrder.ORIGINAL) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }

    fun parseEndTime(endTime: String): LocalDateTime? = try {
        LocalDateTime.parse(endTime, formatter)
    } catch (_: Exception) {
        null
    }

    val filteredList = homeworkList.filter { homework ->
        val isValidDate = parseEndTime(homework.endTime)?.isAfter(LocalDateTime.now()) ?: true
        val dateCondition = !isFilterOutOfDate || isValidDate
        val courseCondition = selectedFilters.isEmpty() || selectedFilters.contains(homework.courseName)
        dateCondition && courseCondition
    }

    val sortedList = when (sortOrder) {
        SortOrder.ORIGINAL -> filteredList
        SortOrder.ASCENDING -> filteredList.sortedBy { parseEndTime(it.endTime) ?: LocalDateTime.MAX }
        SortOrder.DESCENDING -> filteredList.sortedByDescending { parseEndTime(it.endTime) ?: LocalDateTime.MIN }
    }


    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                        onClick = { filterExpanded = true },
                    ) {
                        Text(
                            text = if (selectedFilters.isEmpty()) "请选择课程" else "已选：${selectedFilters.size}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    DropdownMenu(
                        expanded = filterExpanded,
                        onDismissRequest = { filterExpanded = false }
                    ) {
                        val filterOptions = homeworkList.map { it.courseName }.distinct()
                        if (filterOptions.isEmpty()) {
                            DropdownMenuItem(
                                onClick = { filterExpanded = false },
                                text = {
                                    Text(
                                        text = "暂无可用筛选条件",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        } else {
                            filterOptions.forEach { option ->
                                val isChecked = selectedFilters.contains(option)
                                DropdownMenuItem(
                                    onClick = {
                                        selectedFilters = if (isChecked) selectedFilters - option else selectedFilters + option
                                    },
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = option,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selectedFilters = if (checked) selectedFilters + option else selectedFilters - option
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                onClick = {
                                    selectedFilters = emptySet()
                                    filterExpanded = false
                                },
                                text = {
                                    Text(
                                        text = "清空选择",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
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
                                sortOrder = when (sortOrder) {
                                    SortOrder.ORIGINAL -> SortOrder.ASCENDING
                                    SortOrder.ASCENDING -> SortOrder.DESCENDING
                                    SortOrder.DESCENDING -> SortOrder.ORIGINAL
                                }
                            }
                        ) {
                            Icon(
                                imageVector = when (sortOrder) {
                                    SortOrder.ORIGINAL -> Icons.Default.Sort
                                    SortOrder.ASCENDING -> Icons.Default.ArrowUpward
                                    SortOrder.DESCENDING -> Icons.Default.ArrowDownward
                                },
                                contentDescription = "Sort Order"
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    state = listState
                ) {
                    items(sortedList.size) { index ->
                        val homework = sortedList[index]
                        HomeworkItemCard(homework)
                    }
                }
            }

            AnimatedVisibility(
                visible = listState.firstVisibleItemScrollOffset > 300 || listState.firstVisibleItemIndex > 0,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "滚动到顶部",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
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
            if (ChronoUnit.HOURS.between(
                    now,
                    LocalDateTime.parse(it.endTime, formatter)
                ) in 0..48
            ) {
                if (it.subStatus != "已提交") {
                    countForDeadline++
                }
            }
        } catch (_: Exception) {
        }
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

data class HomeworkStatusInfo(
    val icon: ImageVector,
    val statusColor: Color,
    val backgroundColor: Color,
    val isSubmitted: Boolean
)


@Composable
fun getHomeworkStatusInfo(
    subStatus: String,
): HomeworkStatusInfo {
    return when (subStatus) {
        "已提交" -> HomeworkStatusInfo(
            icon = Icons.Rounded.CheckCircle,
            statusColor = primary,
            backgroundColor = primaryContainer,
            isSubmitted = true
        )

        else -> HomeworkStatusInfo(
            icon = Icons.Rounded.DoNotDisturb,
            statusColor = error,
            backgroundColor = MaterialTheme.colorScheme.errorContainer,
            isSubmitted = false
        )
    }

}


@Composable
fun HomeworkItemCard(homework: HomeworkEntity) {
    var showHtmlDialog by remember { mutableStateOf(false) }
    var showUploadHomeworkDialog by remember { mutableStateOf(false) }
    val appState by AppStateManager.appState.collectAsState()


    val canGetGrade = remember {
        homework.scoreId != 0
    }

//    LaunchedEffect(homework.upId, homework.idSnId, canGetGrade) {
//        if (canGetGrade) {
//            // 在协程中安全地调用 suspend 函数
//            val result = try {
//                getHomeworkGrade(homework)
//            } catch (e: Exception) {
//                "加载失败: ${e.message}"
//            }
//            AppDatabase.getInstance().homeworkEntityDao().update(
//                homework.copy(
//                    score = result
//                )
//            )
//
//        }
//    }


    // 计算截止时间是否临近
    val isDDLSoon = remember(homework.endTime) {
        try {
            ChronoUnit.HOURS.between(
                LocalDateTime.now(),
                LocalDateTime.parse(
                    homework.endTime,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                )
            ) in 0..48
        } catch (_: Exception) {
            false
        }
    }


    val (statusIcon, statusColor, backgroundColor, isSubmit) = getHomeworkStatusInfo(homework.subStatus)


    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                indication = rememberRipple(
                    bounded = true,
                    color = MaterialTheme.colorScheme.primary
                ),
                interactionSource = remember { MutableInteractionSource() }
            ) {
                showHtmlDialog = true
            },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 3.dp,
            pressedElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 顶部状态标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = homework.courseName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .background(
                            color = backgroundColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = homework.subStatus,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 作业标题
            Text(
                text = homework.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 信息部分
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 左侧信息栏
                Column(
                    modifier = Modifier.weight(0.7f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 时间信息
                    InfoItem(
                        icon = Icons.Filled.Schedule,
                        primaryText = "开放时间: ${homework.openDate}",
                        secondaryText = "截止时间: ${homework.endTime}",
                        isWarning = isDDLSoon
                    )

                    // 提交状态
                    InfoItem(
                        icon = Icons.Rounded.People,
                        primaryText = "提交人数: ${homework.submitCount}/${homework.allCount}",
                        showDivider = true
                    )



                    InfoItem(
                        icon = Icons.Rounded.Check,
                        primaryText = "批改状态: ${if (homework.scoreId != 0) "已批改" else "未批改"}",
                        showDivider = true
                    )

                    if (homework.scoreId != 0) {
                        InfoItem(
                            icon = Icons.Rounded.Stars,
                            primaryText = "分数: ${homework.score}",
                            showDivider = true
                        )
                    }


                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HomeworkActionButtons(
                homework = homework,
                isSubmit = isSubmit,
                appState = appState,
                onUploadClick = { showUploadHomeworkDialog = true },
            )
        }
    }

    if (showHtmlDialog) {
        ShowHtmlDialog({ showHtmlDialog = false }, homework.content)
    }
    if (showUploadHomeworkDialog) {
        UploadHomeDialog(homework) { showUploadHomeworkDialog = false }
    }

}


@Composable
private fun InfoItem(
    icon: ImageVector,
    primaryText: String,
    secondaryText: String? = null,
    isWarning: Boolean = false,
    showDivider: Boolean = false
) {
    Column {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (secondaryText != null) {
                    Text(
                        text = secondaryText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isWarning)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}


@Composable
fun UploadHomeDialog(homeworkEntity: HomeworkEntity, onDismiss: () -> Unit) {
    val selectedFiles = remember { mutableStateListOf<Uri>() }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedFiles.add(uri) }
    }

    val homeworkViewModel: HomeworkViewModel = viewModel()

    var content by remember { mutableStateOf<String>("") }
    var isUploading by remember { mutableStateOf(false) }
    var uploadResult by remember { mutableStateOf<String?>(null) }

    // Function to extract filename from URI
    fun getFileName(uri: Uri, context: Context): String {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(uri, null, null, null, null)

        return cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (nameIndex >= 0) it.getString(nameIndex) else "Unknown file"
        } ?: uri.lastPathSegment ?: "Unknown file"
    }

    val appState by AppStateManager.appState.collectAsState()

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "上传作业",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "作业描述",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                BetterTextField(
                    content = content,
                    onValueChange = { content = it },
                    label = "请输入作业描述",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
//                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "已选择的文件 (${selectedFiles.size})",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )

                    TextButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add File",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加文件", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (selectedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.FileUpload,
                                contentDescription = "No Files",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "未选择文件",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        items(selectedFiles) { uri ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.FileUpload,
                                        contentDescription = "File",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = getFileName(uri, context),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                IconButton(
                                    onClick = { selectedFiles.remove(uri) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (selectedFiles.indexOf(uri) < selectedFiles.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Display upload result if available
                uploadResult?.let {
                    val isSuccess = it.contains("success")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(
                                color = if (isSuccess)
                                    Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = "Status",
                                tint = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSuccess) "提交成功!" else "提交失败，请重试",
                                color = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),

                    ) {
                    Text("取消")
                }

                Button(
                    onClick = {
                        if (selectedFiles.isNotEmpty()) {
                            isUploading = true
                            val uploader = HomeworkUploader(homeworkEntity)

                            CoroutineScope(Dispatchers.IO).launch {
                                val responseBody = uploader.uploadHomework(
                                    selectedFiles,
                                    content = content.ifEmpty { "" }
                                )

                                withContext(Dispatchers.Main) {
                                    isUploading = false
                                    uploadResult = responseBody

                                    // Optionally close dialog on success
                                    if (responseBody.contains("success")) {
                                        // 由于有ViewModel中有mutex的存在，下面的是按顺序执行的
                                        // 如果没有mutex，会同时执行
                                        // viewmode刚创建的时候就会执行loadDataAndDetectChanges，因此同时执行的结果是
                                        // syncData同步的数据是刚创建时候的init中获取的数据，也就是同步的数据是提交作业前的数据，
                                        // 而不是同步下面这句代码执行后（提交后）的数据
                                        homeworkViewModel.loadDataAndDetectChanges()
                                        homeworkViewModel.syncDataAndClearChange()
                                        // Add delay before dismissing
//                                        delay(1000)
//                                        onDismiss()
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedFiles.isNotEmpty() && !isUploading && (appState != AppState.NetworkProgress),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        "上传作业",
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
            }
        },
        modifier = Modifier.background(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        properties = DialogProperties(dismissOnClickOutside = false)
    )
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
                    webView.loadDataWithBaseURL(
                        null,
                        htmlContent,
                        "text/html",
                        "utf-8",
                        null
                    )
                }
            )
        }
    }
}

@Composable
fun BetterTextField(
    content: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = ""
) {
    OutlinedTextField(

        value = content,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        singleLine = true,
//        isError = emergencyThresholdError != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = "阈值图标"
            )
        },
        shape = RoundedCornerShape(12.dp)
    )
}


@Composable

fun HomeworkActionButtons(
    homework: HomeworkEntity,
    isSubmit: Boolean,
    appState: AppState,
    onUploadClick: () -> Unit

) {


    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 上传作业按钮
            FilledTonalButton(
                onClick = onUploadClick,
                enabled = appState.canDownloadAndUpload(),
                modifier = Modifier.weight(1f),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 3.dp,
                ),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
                )
            ) {
                Text(
                    "上传作业",
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            // 下载作业按钮（使用美化后的组件）
            MaterialHomeworkDownloadButton(
                homework = homework,
                enabled = appState.canDownloadAndUpload() && isSubmit,
                modifier = Modifier.weight(1f)
            )
        }


    }
}


@Composable
fun MaterialHomeworkDownloadButton(
    homework: HomeworkEntity,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 下载状态
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        FilledTonalButton(
            onClick = {
                if (!isDownloading) {
                    isDownloading = true
                    downloadProgress = 0f
                    errorMessage = null

                    coroutineScope.launch {
                        try {
                            downloadHomeworkFile(
                                homework = homework,
                                onProgress = { progress ->
                                    downloadProgress = progress
                                },
                                onSuccess = { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    isDownloading = false
                                },
                                onError = { error ->
                                    errorMessage = error.message ?: "下载失败"
                                    isDownloading = false
                                    Toast.makeText(
                                        context,
                                        "下载失败: ${error.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        } catch (e: Exception) {
                            isDownloading = false
                        }
                    }
                }
            },
            shape = RoundedCornerShape(24.dp), // 胶囊形状
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 3.dp,
            ),
            enabled = !isDownloading && enabled,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isDownloading) "正在下载..." else "下载作业",
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        // 进度指示器
        AnimatedVisibility(
            visible = isDownloading,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp) // Material Design推荐高度
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // 错误信息
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            errorMessage?.let {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}



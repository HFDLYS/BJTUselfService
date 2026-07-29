package team.bjtuss.bjtuselfservice.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.entity.GradeEntity
import team.bjtuss.bjtuselfservice.utils.Utils
import team.bjtuss.bjtuselfservice.viewmodel.DataChange
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel


@Composable
fun GradeScreen(
    mainViewModel: MainViewModel
) {
    val gradeViewModel = mainViewModel.gradeViewModel
    LaunchedEffect(Unit) {
        gradeViewModel.syncDataAndClearChange()

    }
    val gradeList by gradeViewModel.gradeList.collectAsState()
    val selectedGradeIds by gradeViewModel.selectedGradeIds.collectAsState()
    val selectionUiResetGeneration by
        gradeViewModel.selectionUiResetGeneration.collectAsState()
    val gradeChangeList: List<DataChange<GradeEntity>> by gradeViewModel.changeList.collectAsState()
//    gradeViewModel.syncDataAndClearChange()
    LaunchedEffect(gradeChangeList) {
        gradeViewModel.syncDataAndClearChange()
    }

    GradeList(
        gradeList = gradeList,
        selectionUiResetGeneration = selectionUiResetGeneration,
        selectedGradeIds = selectedGradeIds,
        onGradeSelectedChange = gradeViewModel::setGradeSelected,
        onSelectGrades = gradeViewModel::selectGrades,
        onDeselectGradeSemesters = gradeViewModel::deselectGradesInSemesters,
        onClearSelectedGrades = gradeViewModel::clearSelectedGrades,
    )
}

fun calculateGradeInfo(grades: List<GradeEntity>): GradeInfoResult {
    val (totalScore, totalCredit) = grades.fold(0.0 to 0.0) { (accScore, accCredit), GradeEntity ->
        try {
            val scoreValue = GradeEntity.courseScore.split(",").getOrNull(1)?.toDoubleOrNull()
            val creditValue = GradeEntity.courseCredits.toDoubleOrNull()

            if (scoreValue != null && creditValue != null) {
                accScore + (scoreValue * creditValue) to accCredit + creditValue
            } else {
                accScore to accCredit
            }
        } catch (e: Exception) {
            accScore to accCredit
        }
    }

    return when {
        totalCredit == 0.0 -> GradeInfoResult.NoGrades
        else -> {
            val gpa = totalScore / totalCredit
            GradeInfoResult.Calculated(
                averageScore = gpa,
                formattedMessage = "你的加权平均分是 ${String.format("%.1f", gpa)}"
            )
        }
    }
}

sealed class GradeInfoResult {
    object NoGrades : GradeInfoResult()
    data class Calculated(
        val averageScore: Double,
        val formattedMessage: String
    ) : GradeInfoResult()
}

enum class SortOrder {
    ORIGINAL, ASCENDING, DESCENDING
}

private val semesterFilterSaver = Saver<Set<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it.toSet() },
)

private val sortOrderSaver = Saver<SortOrder, String>(
    save = { it.name },
    restore = { SortOrder.valueOf(it) },
)

internal fun shouldResetCourseSelectionUi(
    handledResetGeneration: Long,
    currentResetGeneration: Long,
): Boolean {
    return handledResetGeneration != currentResetGeneration
}

@Composable
private fun ResponsiveTopActionRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        val spacing = 8.dp.roundToPx()
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val availableWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            placeables.sumOf { it.width } +
                    spacing * (placeables.size - 1).coerceAtLeast(0)
        }

        val rows = mutableListOf<MutableList<Int>>()
        val rowWidths = mutableListOf<Int>()
        placeables.forEachIndexed { index, placeable ->
            val currentRow = rows.lastOrNull()
            val currentWidth = rowWidths.lastOrNull() ?: 0
            val requiredWidth =
                placeable.width + if (currentRow.isNullOrEmpty()) 0 else spacing
            if (currentRow != null && currentWidth + requiredWidth <= availableWidth) {
                currentRow += index
                rowWidths[rowWidths.lastIndex] = currentWidth + requiredWidth
            } else {
                rows += mutableListOf(index)
                rowWidths += placeable.width
            }
        }

        val rowHeights = rows.map { row ->
            row.maxOfOrNull { placeables[it].height } ?: 0
        }
        val contentHeight =
            rowHeights.sum() + spacing * (rowHeights.size - 1).coerceAtLeast(0)
        val layoutWidth = availableWidth.coerceIn(
            minimumValue = constraints.minWidth,
            maximumValue = constraints.maxWidth,
        )
        val layoutHeight = contentHeight.coerceIn(
            minimumValue = constraints.minHeight,
            maximumValue = constraints.maxHeight,
        )

        layout(layoutWidth, layoutHeight) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                val rowHeight = rowHeights[rowIndex]
                var x = 0
                row.forEachIndexed { itemIndex, placeableIndex ->
                    val placeable = placeables[placeableIndex]
                    val isSortButton = placeableIndex == placeables.lastIndex
                    val itemX = if (isSortButton) {
                        layoutWidth - placeable.width
                    } else {
                        x
                    }
                    placeable.placeRelative(
                        x = itemX,
                        y = y + (rowHeight - placeable.height) / 2,
                    )
                    if (!isSortButton) {
                        x += placeable.width
                        if (itemIndex < row.lastIndex) {
                            x += spacing
                        }
                    }
                }
                y += rowHeight + spacing
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradeList(
    gradeList: List<GradeEntity>,
    selectionUiResetGeneration: Long,
    selectedGradeIds: Set<Int>,
    onGradeSelectedChange: (Int, Boolean) -> Unit,
    onSelectGrades: (Set<Int>) -> Unit,
    onDeselectGradeSemesters: (Set<String>) -> Unit,
    onClearSelectedGrades: (() -> Unit) -> Unit,
) {
    var filterExpanded by remember { mutableStateOf(false) }
    var selectedFilters by rememberSaveable(stateSaver = semesterFilterSaver) {
        mutableStateOf(emptySet())
    }
    var sortOrder by rememberSaveable(stateSaver = sortOrderSaver) {
        mutableStateOf(SortOrder.ORIGINAL)
    }
    var isCourseSelectionMode by rememberSaveable { mutableStateOf(false) }
    var handledSelectionUiResetGeneration by rememberSaveable {
        mutableStateOf(selectionUiResetGeneration)
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectionUiResetGeneration) {
        if (shouldResetCourseSelectionUi(
                handledResetGeneration = handledSelectionUiResetGeneration,
                currentResetGeneration = selectionUiResetGeneration,
            )
        ) {
            isCourseSelectionMode = false
            filterExpanded = false
            selectedFilters = emptySet()
            sortOrder = SortOrder.ORIGINAL
        }
        handledSelectionUiResetGeneration = selectionUiResetGeneration
    }

    val filteredGradeList = filterGradesBySemester(gradeList, selectedFilters)
    val sortedGradeList = when (sortOrder) {
        SortOrder.ORIGINAL -> filteredGradeList
        SortOrder.ASCENDING -> filteredGradeList.sortedBy { getScoreGrade(it.courseScore) }
        SortOrder.DESCENDING -> filteredGradeList.sortedByDescending {
            getScoreGrade(it.courseScore)
        }
    }
    val gradesForCalculation = gradesForCalculation(
        gradeList = gradeList,
        selectedFilters = selectedFilters,
        isCourseSelectionMode = isCourseSelectionMode,
        selectedGradeIds = selectedGradeIds,
    )

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
                GpaCard(
                    grades = gradesForCalculation,
                    isCourseSelectionMode = isCourseSelectionMode,
                )
                ResponsiveTopActionRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    Button(
                        onClick = {
                            filterExpanded = true
                        }
                    ) {
                        Text(
                            text = if (selectedFilters.isEmpty()) {
                                "请选择学期"
                            } else {
                                "已选：${selectedFilters.size}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Button(
                        onClick = {
                            if (isCourseSelectionMode) {
                                isCourseSelectionMode = false
                                filterExpanded = false
                                selectedFilters = emptySet()
                                sortOrder = SortOrder.ORIGINAL
                            } else {
                                isCourseSelectionMode = true
                                sortOrder = SortOrder.ORIGINAL
                            }
                        }
                    ) {
                        Text(
                            text = if (isCourseSelectionMode) {
                                "退出自选课程"
                            } else {
                                "自选课程计算"
                            },
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

                Box {
                    DropdownMenu(
                        expanded = filterExpanded,
                        onDismissRequest = { filterExpanded = false }
                    ) {
                        val filterOptions = gradeList.map { it.tag }.distinct()
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
                                val isChecked = option in selectedFilters
                                DropdownMenuItem(
                                    onClick = {
                                        selectedFilters = if (isChecked) {
                                            selectedFilters - option
                                        } else {
                                            selectedFilters + option
                                        }
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
                                                    selectedFilters = if (checked) {
                                                        selectedFilters + option
                                                    } else {
                                                        selectedFilters - option
                                                    }
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
                }

                if (isCourseSelectionMode) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                onSelectGrades(filteredGradeList.map { it.id }.toSet())
                            }
                        ) {
                            Text(
                                text = "全选",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        if (selectedFilters.isNotEmpty()) {
                            Button(
                                onClick = {
                                    onDeselectGradeSemesters(selectedFilters)
                                }
                            ) {
                                Text(
                                    text = "清空本学期",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onClearSelectedGrades {
                                    filterExpanded = false
                                    selectedFilters = emptySet()
                                    sortOrder = SortOrder.ORIGINAL
                                }
                            }
                        ) {
                            Text(
                                text = "全部清空",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    state = listState
                ) {
                    items(sortedGradeList.size) { index ->
                        val gradeEntity = sortedGradeList[index]
                        GradeItemCard(
                            GradeEntity = gradeEntity,
                            isCourseSelectionMode = isCourseSelectionMode,
                            isSelected = gradeEntity.id in selectedGradeIds,
                            onSelectedChange = { selected ->
                                onGradeSelectedChange(gradeEntity.id, selected)
                            },
                        )
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

fun filterGradesBySemester(
    gradeList: List<GradeEntity>,
    selectedFilters: Set<String>,
): List<GradeEntity> {
    return if (selectedFilters.isEmpty()) {
        gradeList
    } else {
        gradeList.filter { it.tag in selectedFilters }
    }
}

fun gradesForCalculation(
    gradeList: List<GradeEntity>,
    selectedFilters: Set<String>,
    isCourseSelectionMode: Boolean,
    selectedGradeIds: Set<Int>,
): List<GradeEntity> {
    return if (isCourseSelectionMode) {
        gradeList.filter { it.id in selectedGradeIds }
    } else {
        filterGradesBySemester(gradeList, selectedFilters)
    }
}

fun getScoreGrade(scoreStr: String): Int {
    val cleanScore = scoreStr.replace(",", "").replace("[^0-9.]".toRegex(), "")
    return try {
        cleanScore.toDouble().toInt()
    } catch (e: NumberFormatException) {
        // 如果转换失败，可以返回一个默认等级或原始字符串
        -1
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GpaCard(
    grades: List<GradeEntity>,
    isCourseSelectionMode: Boolean,
) {
    val gradeInfo = calculateGradeInfo(grades)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
//            .graphicsLayer {
//                scaleX = scale
//                scaleY = scale
//            },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (gradeInfo) {
                is GradeInfoResult.NoGrades -> {
                    Text(
                        text = if (isCourseSelectionMode) {
                            "你的加权平均分是 -"
                        } else {
                            "成绩好像都没出来哦~"
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = if (isCourseSelectionMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (isCourseSelectionMode) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    )
                }

                is GradeInfoResult.Calculated -> {
                    Text(
                        text = gradeInfo.formattedMessage,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    AnimatedContent(
                        targetState = gradeInfo.averageScore,
                    ) { score ->
                        Text(
                            text = when {
                                score >= 92.5 -> "🫢 这位学霸会不会太猛了"
                                score >= 87.5 -> "🫡 鼓足干劲，力争上游，多快好省地，加油吧！！！"
                                score >= 82.5 -> "☺️ 还可以哦，再加把劲吧～"
                                score >= 70 -> "🥹 不错哦，继续努力"
                                score >= 60 -> "😃️ 得加把劲了，但或许已经够了？"
                                else -> "😱😱😱 同学你真得加油了啊"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = FontStyle.Italic
                            ),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GradeItemCard(
    GradeEntity: GradeEntity,
    isCourseSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
) {
    var showDetailedInformationDialog by remember { mutableStateOf(false) }
    val score = getScoreGrade(GradeEntity.courseScore)
    val cardColor = Color(Utils.calculateGradeColor(score.toDouble()))
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { showDetailedInformationDialog = true },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = GradeEntity.courseName.substring(
                        8,
                        GradeEntity.courseName.length - 4
                    ),
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
                        imageVector = Icons.Default.Person,
                        contentDescription = "Teacher",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = GradeEntity.courseTeacher,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = "学分",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = GradeEntity.courseCredits,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = GradeEntity.courseScore,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = cardColor
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
                if (isCourseSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = onSelectedChange,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
    }

    if (showDetailedInformationDialog) {
        GradeDetailDialog(
            GradeEntity = GradeEntity,
            onDismissRequest = { showDetailedInformationDialog = false } // 关闭对话框
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeDetailDialog(
    GradeEntity: GradeEntity,
    onDismissRequest: () -> Unit
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
                            text = "详情信息",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = GradeEntity.courseName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "教师: ${GradeEntity.courseTeacher}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "学分: ${GradeEntity.courseCredits}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "成绩: ${GradeEntity.courseScore}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = GradeEntity.detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = Int.MAX_VALUE,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

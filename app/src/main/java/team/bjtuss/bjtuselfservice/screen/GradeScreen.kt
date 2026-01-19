package team.bjtuss.bjtuselfservice.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
    val gradeChangeList: List<DataChange<GradeEntity>> by gradeViewModel.changeList.collectAsState()
//    gradeViewModel.syncDataAndClearChange()
    LaunchedEffect(gradeChangeList) {
        gradeViewModel.syncDataAndClearChange()
    }

    GradeList(
        gradeList = gradeList,
    )
}

fun calculateGradeInfo(grades: List<GradeEntity>): GradeInfoResult {
    val (totalScore, totalCredit) = grades.fold(0.0 to 0.0) { (accScore, accCredit), GradeEntity ->
        try {
            if (GradeEntity.detail.contains("缓考")) {
                accScore to accCredit
            } else {
                val scoreValue = GradeEntity.courseScore.split(",").getOrNull(1)?.toDoubleOrNull()
                val creditValue = GradeEntity.courseCredits.toDoubleOrNull()

                if (scoreValue != null && creditValue != null) {
                    accScore + (scoreValue * creditValue) to accCredit + creditValue
                } else {
                    accScore to accCredit
                }
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

@Composable
fun GradeList(
    gradeList: List<GradeEntity>
) {
    var filterExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("全部") }
    var sortOrder by remember { mutableStateOf(SortOrder.ORIGINAL) }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            GpaCard(gradeList, selectedFilter)
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
                // 筛选条件
                DropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                ) {
                    val filterOptions = mutableListOf("全部")
                    filterOptions += gradeList.map { it.tag }.filterNotNull().distinct()
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
                        sortOrder = when (sortOrder) {
                            SortOrder.ORIGINAL -> SortOrder.ASCENDING
                            SortOrder.ASCENDING -> SortOrder.DESCENDING
                            SortOrder.DESCENDING -> SortOrder.ORIGINAL
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterVertically)
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                val filteredGradeList = gradeList.filter {
                    when (selectedFilter) {
                        "全部" -> true
                        else -> it.tag == selectedFilter
                    }
                }
                val sortedGradeList = when (sortOrder) {
                    SortOrder.ORIGINAL -> filteredGradeList
                    SortOrder.ASCENDING -> filteredGradeList.sortedBy { getScoreGrade(it.courseScore) }
                    SortOrder.DESCENDING -> filteredGradeList.sortedByDescending { getScoreGrade(it.courseScore) }
                }
                items(sortedGradeList.size) { index ->
                    val gradeEntity = sortedGradeList[index]
                    GradeItemCard(
                        GradeEntity = gradeEntity,
                    )
                }
            }
        }
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
fun GpaCard(gradeList: List<GradeEntity>, selectedFilter: String) {
//    val infiniteTransition = rememberInfiniteTransition()
//    val scale by infiniteTransition.animateFloat(
//        initialValue = 1f,
//        targetValue = 1.05f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(500, easing = FastOutSlowInEasing),
//            repeatMode = RepeatMode.Reverse
//        ), label = ""
//    )
    val filteredGradeList = gradeList.filter {
        when (selectedFilter) {
            "全部" -> true
            else -> it.tag == selectedFilter
        }
    }
    val gradeInfo = calculateGradeInfo(filteredGradeList)
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
                        text = "成绩好像都没出来哦~",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                modifier = Modifier.weight(0.7f),
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = GradeEntity.courseScore,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = cardColor
                    )
                )
//                Text(
//                    text = scoreGrade,
//                    style = MaterialTheme.typography.bodySmall.copy(
//                        color = cardColor.copy(alpha = 0.7f)
//                    )
//                )
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

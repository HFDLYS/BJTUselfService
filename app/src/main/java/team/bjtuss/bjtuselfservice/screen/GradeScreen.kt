package team.bjtuss.bjtuselfservice.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.viewmodel.GradeViewModel
import kotlin.collections.mapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import team.bjtuss.bjtuselfservice.utils.Utils

@Composable
fun GradeScreen(
    gradeViewModel: GradeViewModel
) {
    val gradeList by gradeViewModel.gradeList.collectAsState()

    GradeList(
        gradeList = gradeList,
        gradeInfo = calculateGradeInfo(gradeList),
    )
}

fun calculateGradeInfo(grades: List<StudentAccountManager.Grade>): GradeInfoResult {
    val (totalScore, totalCredit) = grades.fold(0.0 to 0.0) { (accScore, accCredit), grade ->
        try {
            val scoreValue = grade.courseScore.split(",")[1].toDoubleOrNull() ?: 0.0
            val creditValue = grade.courseCredits.toDoubleOrNull() ?: 0.0

            accScore + (scoreValue * creditValue) to accCredit + creditValue
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

@Composable
fun GradeList(
    gradeList: List<StudentAccountManager.Grade>,
    gradeInfo: GradeInfoResult
) {

    fun getScoreGrade(scoreStr: String): Int {
        val cleanScore = scoreStr.replace(",", "").replace("[^0-9.]".toRegex(), "")
        return try {
            cleanScore.toDouble().toInt()
        } catch (e: NumberFormatException) {
            // 如果转换失败，可以返回一个默认等级或原始字符串
            -1
        }
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
            GpaCard(gradeInfo)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(gradeList.size) { index ->
                    val grade = gradeList[index]
                    val score = getScoreGrade(grade.courseScore)
                    val cardColor = Color(Utils.calculateGradeColor(score.toDouble()))
                    GradeItemCard(
                        grade = grade,
                        cardColor = cardColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GpaCard(gradeInfo: GradeInfoResult) {
//    val infiniteTransition = rememberInfiniteTransition()
//    val scale by infiniteTransition.animateFloat(
//        initialValue = 1f,
//        targetValue = 1.05f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(500, easing = FastOutSlowInEasing),
//            repeatMode = RepeatMode.Reverse
//        ), label = ""
//    )

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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    AnimatedContent(
                        targetState = gradeInfo.averageScore,
                        transitionSpec = {
                            slideInVertically { height -> height } togetherWith
                                    slideOutVertically { height -> -height }
                        }
                    ) { score ->
                        Text(
                            text = when {
                                score >= 90 -> "🏆 优秀！继续保持你的学术热情！"
                                score >= 80 -> "👍 很棒！你正在取得进步！"
                                score >= 60 -> "📚 继续努力，你可以做得更好！"
                                else -> "💪 不要放弃，提升的机会还很多！"
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
    grade: StudentAccountManager.Grade,
    cardColor: Color,
) {
    var showDetailedInformationDialog by remember { mutableStateOf(false) }

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
                    text = grade.courseName.substring(
                        8,
                        grade.courseName.length - 4
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
                        text = grade.courseTeacher,
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
                        text = "${grade.courseCredits}",
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
                    text = grade.courseScore,
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
            grade = grade,
            onDismissRequest = { showDetailedInformationDialog = false } // 关闭对话框
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeDetailDialog(
    grade: StudentAccountManager.Grade,
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
                                    text = grade.courseName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "教师: ${grade.courseTeacher}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "学分: ${grade.courseCredits}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "成绩: ${grade.courseScore}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = grade.detail,
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
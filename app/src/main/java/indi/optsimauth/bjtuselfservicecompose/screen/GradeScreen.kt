package indi.optsimauth.bjtuselfservicecompose.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import indi.optsimauth.bjtuselfservicecompose.StudentAccountManager
import indi.optsimauth.bjtuselfservicecompose.viewmodel.GradeViewModel
import kotlin.collections.mapOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle

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
    val scoreColors = mapOf(
        "A+" to Color(0xFF4CAF50),   // Bright Green
        "A" to Color(0xFF81C784),    // Light Green
        "A-" to Color(0xFF66BB6A),   // Medium Green
        "B+" to Color(0xFF64B5F6),   // Light Blue
        "B" to Color(0xFF2196F3),    // Vibrant Blue
        "B-" to Color(0xFF1E88E5),   // Medium Blue
        "C+" to Color(0xFFFFD54F),   // Soft Yellow
        "C" to Color(0xFFFFC107),    // Amber
        "C-" to Color(0xFFFFB300),   // Dark Amber
        "D+" to Color(0xFFFFA726),   // Light Orange
        "D" to Color(0xFFFF7043),    // Orange
        "F" to Color(0xFFF44336)     // Red
    )

    fun getScoreGrade(scoreStr: String): String {
        val cleanScore = scoreStr.replace(",", "").replace("[^0-9.]".toRegex(), "")
        return try {
            val score = cleanScore.toDouble()
            when {
                score >= 90 -> "A"
                score >= 85 -> "A-"
                score >= 81 -> "B+"
                score >= 78 -> "B"
                score >= 75 -> "B-"
                score >= 71 -> "C+"
                score >= 68 -> "C"
                score >= 65 -> "C-"
                score >= 61 -> "D+"
                score >= 60 -> "D"
                else -> "F"
            }
        } catch (e: NumberFormatException) {
            // 如果转换失败，可以返回一个默认等级或原始字符串
            "Unknown"
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
                    val scoreGrade = getScoreGrade(grade.courseScore)
                    val cardColor =
                        scoreColors[scoreGrade] ?: MaterialTheme.colorScheme.surfaceVariant

                    GradeItemCard(
                        grade = grade,
                        cardColor = cardColor,
                        scoreGrade = scoreGrade
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
    scoreGrade: String
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
}
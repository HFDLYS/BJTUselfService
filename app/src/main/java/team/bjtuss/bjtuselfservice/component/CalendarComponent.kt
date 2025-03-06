package team.bjtuss.bjtuselfservice.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.Week
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.yearMonth
import team.bjtuss.bjtuselfservice.background
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import team.bjtuss.bjtuselfservice.onSurface
import team.bjtuss.bjtuselfservice.primary
import team.bjtuss.bjtuselfservice.screen.ExamItemCard
import team.bjtuss.bjtuselfservice.screen.HomeworkItemCard
import team.bjtuss.bjtuselfservice.scrim
import team.bjtuss.bjtuselfservice.secondary
import team.bjtuss.bjtuselfservice.surface
import team.bjtuss.bjtuselfservice.surfaceContainer
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale


/**
 * Main calendar component that displays a week view with tasks
 */
@Composable
fun CalendarComponent(mainViewModel: MainViewModel) {
    val currentDate = LocalDate.now()
    val state = rememberWeekCalendarState(
        startDate = currentDate.minusDays(100),
        endDate = currentDate.plusDays(100),
        firstVisibleWeekDate = currentDate
    )

    val homeworkList = mainViewModel.homeworkViewModel.homeworkList.collectAsState()
    val examList = mainViewModel.examScheduleViewModel.examScheduleList.collectAsState()

    Box(
        modifier = Modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, color = secondary, shape = RoundedCornerShape(16.dp))
            .background(surface.copy(alpha = 0.9f))
    ) {
        val daysOfWeek = remember { daysOfWeek() }
        WeekCalendar(
            state = state,
            dayContent = { weekDay ->
                Day(weekDay.date, homeworkList.value, examList.value)
            },
            weekHeader = { week ->
                WeekHeader(week = week, daysOfWeek = daysOfWeek, mainViewModel = mainViewModel)
            }
        )
    }
}

/**
 * Calculates the week number based on a reference date and current week
 */
fun getWeekNumberSinceStart(
    currentWeek: Int,
    currentDate: LocalDate,
    targetDate: LocalDate,
    startOfWeek: DayOfWeek = DayOfWeek.MONDAY
): Int {
    val currentWeekStart = currentDate.with(TemporalAdjusters.previousOrSame(startOfWeek))
    val targetWeekStart = targetDate.with(TemporalAdjusters.previousOrSame(startOfWeek))
    val weeksBetween = ChronoUnit.WEEKS.between(currentWeekStart, targetWeekStart).toInt()
    return currentWeek + weeksBetween
}

/**
 * Header component displaying week information and days of week
 */
@Composable
private fun WeekHeader(week: Week, daysOfWeek: List<DayOfWeek>, mainViewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .background(surface)
            .padding(vertical = 8.dp)
    ) {
        val firstDayOfWeek = week.days.first().date
        val currentWeek = mainViewModel.statusViewModel.currentWeek.collectAsState()
        val weekNumber = getWeekNumberSinceStart(
            currentWeek.value,
            LocalDate.now(),
            firstDayOfWeek,
        )

        Text(
            text = "第 ${weekNumber} 教学周",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = primary
        )

        DaysOfWeekTitle(daysOfWeek = daysOfWeek)
    }
}

/**
 * Component representing a single day in the calendar
 */
@Composable
fun Day(
    date: LocalDate,
    homeworkList: List<HomeworkEntity> = emptyList(),
    examList: List<ExamScheduleEntity> = emptyList(),
) {
    val dayText = date.dayOfMonth.toString()
    val isToday = date == LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    // Filter tasks for this date
    val endHomeworkList = homeworkList.filter {
        date == LocalDateTime.parse(it.endTime, formatter).minusMinutes(1).toLocalDate()
    }
    val startHomeworkList = homeworkList.filter {
        date == LocalDateTime.parse(it.openDate, formatter).toLocalDate()
    }
    val currentDateExamList = examList.filter {
        try {
            val dateStr = it.examTimeAndPlace.split(" ")[0]
            date == LocalDate.parse(dateStr)
        } catch (e: Exception) {
            false
        }
    }

    val hasTasks = startHomeworkList.isNotEmpty() ||
            endHomeworkList.isNotEmpty() ||
            currentDateExamList.isNotEmpty()

    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        TaskDetailsDialog(
            date = date,
            startHomeworkList = startHomeworkList,
            endHomeworkList = endHomeworkList,
            examList = currentDateExamList,
            onDismiss = { showDialog = false }
        )
    }

    DayContent(
        dayText = dayText,
        isToday = isToday,
        startHomeworkList = startHomeworkList,
        endHomeworkList = endHomeworkList,
        examList = currentDateExamList,
        hasTasks = hasTasks,
        onDayClick = { showDialog = true }
    )
}

/**
 * Beautiful task details dialog
 */
@Composable
fun TaskDetailsDialog(
    date: LocalDate,
    startHomeworkList: List<HomeworkEntity>,
    endHomeworkList: List<HomeworkEntity>,
    examList: List<ExamScheduleEntity>,
    onDismiss: () -> Unit
) {
    val hasTasks = startHomeworkList.isNotEmpty() ||
            endHomeworkList.isNotEmpty() ||
            examList.isNotEmpty()

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
    val formattedDate = date.format(dateFormatter)
    val isToday = date == LocalDate.now()
    val dateDisplay = if (isToday) "$formattedDate (今天)" else formattedDate

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier,
        containerColor = surfaceContainer,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = dateDisplay,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(thickness = 1.dp, color = secondary.copy(alpha = 0.5f))
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                if (!hasTasks) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "今天没有任何任务 😊",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    // Summary section
                    item {
                        TaskSummarySection(
                            startHomeworkCount = startHomeworkList.size,
                            endHomeworkCount = endHomeworkList.size,
                            examCount = examList.size
                        )
                    }
                }

                // Homework starting today
                if (startHomeworkList.isNotEmpty()) {
                    item {
                        TaskSectionHeader(
                            title = "今天开始的作业",
                            color = Color(0xFF3F51B5),
                            count = startHomeworkList.size
                        )
                    }
                    items(startHomeworkList, key = { it.id }) { homework ->
                        HomeworkItemCard(homework)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Homework ending today
                if (endHomeworkList.isNotEmpty()) {
                    item {
                        TaskSectionHeader(
                            title = "今天截止的作业",
                            color = Color(0xFFE53935),
                            count = endHomeworkList.size
                        )
                    }
                    items(endHomeworkList, key = { it.id }) { homework ->
                        HomeworkItemCard(homework)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Exams today
                if (examList.isNotEmpty()) {
                    item {
                        TaskSectionHeader(
                            title = "今天的考试",
                            color = Color(0xFFFF9800),
                            count = examList.size
                        )
                    }
                    items(examList, key = { it.id }) { exam ->
                        ExamItemCard(exam)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "关闭",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

/**
 * Task summary section for the dialog
 */
@Composable
fun TaskSummarySection(
    startHomeworkCount: Int,
    endHomeworkCount: Int,
    examCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = surface.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "今日任务概览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (startHomeworkCount > 0) {
                TaskSummaryItem(
                    text = "有 $startHomeworkCount 个作业开始提交",
                    color = Color(0xFF3F51B5)
                )
            }

            if (endHomeworkCount > 0) {
                TaskSummaryItem(
                    text = "有 $endHomeworkCount 个作业即将截止",
                    color = Color(0xFFE53935)
                )
            }

            if (examCount > 0) {
                TaskSummaryItem(
                    text = "有 $examCount 场考试",
                    color = Color(0xFFFF9800)
                )
            }
        }
    }
}

/**
 * Individual task summary item
 */
@Composable
fun TaskSummaryItem(
    text: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Section header for task types in the dialog
 */
@Composable
fun TaskSectionHeader(
    title: String,
    color: Color,
    count: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(4.dp, 20.dp),
                color = color
            ) {}

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "$title ($count)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = color.copy(alpha = 0.3f))
    }
}

/**
 * Content of a single day cell in the calendar
 */
@Composable
private fun DayContent(
    dayText: String,
    isToday: Boolean,
    hasTasks: Boolean,
    onDayClick: () -> Unit,
    startHomeworkList: List<HomeworkEntity> = emptyList(),
    endHomeworkList: List<HomeworkEntity> = emptyList(),
    examList: List<ExamScheduleEntity> = emptyList(),
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(surface)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDayClick
            )
    ) {
        // Today indicator

        if (isToday) {
            val todayColor = primary.copy(alpha = 0.2f)
            Canvas(modifier = Modifier.fillMaxSize()) {

                drawCircle(
                    color = todayColor,
                    radius = size.minDimension / 2.5f,
                )
            }
        }

        // Task indicators
        MultiColorCircle(
            modifier = Modifier.fillMaxSize(),
            colors = getColorsOfWeekCalendar(startHomeworkList, endHomeworkList, examList),
            gapAngle = 6f
        )

        // Day number
        Text(
            text = dayText,
            color = if (isToday) primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isToday || hasTasks) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * Days of week header
 */
@Composable
private fun DaysOfWeekTitle(daysOfWeek: List<DayOfWeek>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .padding(bottom = 8.dp)
    ) {
        for (dayOfWeek in daysOfWeek) {
            Text(
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                fontWeight = FontWeight.Medium,
                color = if (dayOfWeek == LocalDate.now().dayOfWeek) primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
    HorizontalDivider(color = scrim)
}

/**
 * Circular task indicator displaying multiple colors for different task types
 */
@Composable
fun MultiColorCircle(
    modifier: Modifier = Modifier,
    colors: List<Color>,
    gapAngle: Float = 6f,
) {
    val segments = colors.size
    if (segments == 0) return

    Canvas(modifier = modifier) {
        val adjustedGapAngle = if (segments == 1) 0f else gapAngle
        val sweepAngle = (360f - segments * adjustedGapAngle) / segments
        val radius = size.minDimension / 2.58f
        val strokeWidth = (size.minDimension * 0.085f)

        for (i in 0 until segments) {
            drawArc(
                color = colors[i % colors.size],
                startAngle = i * (sweepAngle + adjustedGapAngle),
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = center.copy(x = center.x - radius, y = center.y - radius),
                size = size.copy(width = radius * 2, height = radius * 2),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

/**
 * Get colors for indicator circle based on task types
 */
fun getColorsOfWeekCalendar(
    startHomeworkList: List<HomeworkEntity>,
    endHomeworkList: List<HomeworkEntity>,
    examList: List<ExamScheduleEntity>
): List<Color> {
    val colors = mutableListOf<Color>()
    colors.addAll(List(startHomeworkList.size) { Color.Blue })
    colors.addAll(List(endHomeworkList.size) { Color.Red })
    colors.addAll(List(examList.size) { Color.Yellow })
    return colors
}
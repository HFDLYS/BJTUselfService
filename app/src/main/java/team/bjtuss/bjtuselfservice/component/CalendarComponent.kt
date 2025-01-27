package team.bjtuss.bjtuselfservice.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.Week
import com.kizitonwose.calendar.core.daysOfWeek
import team.bjtuss.bjtuselfservice.onSurface
import team.bjtuss.bjtuselfservice.primary
import team.bjtuss.bjtuselfservice.scrim
import team.bjtuss.bjtuselfservice.secondary
import team.bjtuss.bjtuselfservice.surface
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale


@Composable
fun CalendarComponent(mainViewModel: MainViewModel) {
    val currentDate = LocalDate.now()
    val state = rememberWeekCalendarState(
        startDate = currentDate.minusDays(400),
        endDate = currentDate.plusDays(200),
        firstVisibleWeekDate = currentDate
    )
    val daysOfWeek = remember { daysOfWeek() }

    Box(
        modifier = Modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, color = secondary, shape = RoundedCornerShape(8.dp))
    ) {
        WeekCalendar(state = state,
            dayContent = { date ->

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(surface)
                        .clip(CircleShape)
//                        .clickable(
//                            indication = null,
//                            interactionSource = remember { MutableInteractionSource() },
//                            onClick = onDayClick
//                        )
                ) {
                    val dayText = date.date.dayOfMonth.toString()
                    val isToday = date == currentDate
                    val textColor = if (isToday) primary else onSurface
                    Text(
                        text = dayText,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            },
            weekHeader = { week ->
                WeekHeader(week = week, daysOfWeek = daysOfWeek)
            })
    }
}


@Composable
private fun WeekHeader(week: Week, daysOfWeek: List<DayOfWeek>) {
    Column(modifier = Modifier.background(surface)) {
        val firstDayOfWeek = week.days.first().date
        val weekNumber = firstDayOfWeek.get(WeekFields.of(Locale.getDefault()).weekOfYear())
        Text(
            text = "第${weekNumber}周",
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = onSurface
        )
        DaysOfWeekTitle(daysOfWeek = daysOfWeek)
    }
}

@Composable
private fun DayContentLayout(
    dayText: String,
    isToday: Boolean,
    isFocused: Boolean,
    textColor: Color,
    onDayClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDayClick
            )
    ) {
        if (isToday) {
            val color = primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = color,
                    radius = size.minDimension / 2.35f,
                )
            }
        }

        if (isFocused) {
            val color = primary

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = color,
                    radius = size.minDimension / 2,
                    style = Stroke(width = size.minDimension * 0.14f)
                )
            }
        }

        Text(
            text = dayText,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.Center)
        )

    }
}

@Composable
private fun DaysOfWeekTitle(daysOfWeek: List<DayOfWeek>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
    ) {
        for (dayOfWeek in daysOfWeek) {
            Text(
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            )
        }
    }
    HorizontalDivider(color = scrim)
}

@Composable
fun MultiColorCircle(
    modifier: Modifier = Modifier,
    segments: Int,
    colors: List<Color>,
    gapAngle: Float = 6f,
) {
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

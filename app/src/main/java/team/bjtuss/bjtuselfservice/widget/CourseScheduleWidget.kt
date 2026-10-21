package team.bjtuss.bjtuselfservice.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import java.time.DayOfWeek
import java.time.LocalDate

class CourseNextWeekAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val currentWeek = try {
            withTimeoutOrNull(500L) {
                DataStoreRepository.getCurrentWeek().first()
            } ?: 1
        } catch (e: Exception) { 1 }
        
        updateAppWidgetState(context, glanceId) { prefs ->
            val key = intPreferencesKey("course_week_offset")
            val current = prefs[key] ?: 0
            if (currentWeek + current < 30) {
                prefs[key] = current + 1
            }
        }
        CourseScheduleWidget().update(context, glanceId)
    }
}

class CoursePrevWeekAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val currentWeek = try {
            withTimeoutOrNull(500L) {
                DataStoreRepository.getCurrentWeek().first()
            } ?: 1
        } catch (e: Exception) { 1 }
        
        updateAppWidgetState(context, glanceId) { prefs ->
            val key = intPreferencesKey("course_week_offset")
            val current = prefs[key] ?: 0
            if (currentWeek + current > 1) {
                prefs[key] = current - 1
            }
        }
        CourseScheduleWidget().update(context, glanceId)
    }
}

class CourseScheduleWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 180.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val currentWeek = try {
            withTimeoutOrNull(500L) {
                DataStoreRepository.getCurrentWeek().first()
            } ?: 1
        } catch (e: Exception) {
            1
        }
        
        val allCourses = try {
            withTimeoutOrNull(500L) {
                // false 表示查询“本学期课表”而不是“选课课表”
                AppDatabase.getInstance().courseEntityDao().getCurrentSemesterCourseListBySemester(false)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        provideContent {
            val prefs = currentState<Preferences>()
            val weekOffset = prefs[intPreferencesKey("course_week_offset")] ?: 0
            val size = LocalSize.current
            CourseScheduleWidgetContent(
                currentWeek = currentWeek,
                allCourses = allCourses,
                weekOffset = weekOffset,
                compact = size.width < 220.dp || size.height < 150.dp,
            )
        }
    }
}

@Composable
fun CourseScheduleWidgetContent(
    currentWeek: Int,
    allCourses: List<CourseEntity>,
    weekOffset: Int,
    compact: Boolean = false,
) {
    val displayWeek = currentWeek + weekOffset
    val thisWeekCourses = allCourses.filter { course ->
        var isThisWeek = false
        try {
            val timeStr = course.courseTime.replace("第", "").replace("周", "")
            val timeList = timeStr.split(",")
            for (time in timeList) {
                if (time.contains("-")) {
                    val range = time.split("-")
                    if (displayWeek in range[0].toInt()..range[1].toInt()) {
                        isThisWeek = true
                        break
                    }
                } else if (time.isNotEmpty() && time.toInt() == displayWeek) {
                    isThisWeek = true
                    break
                }
            }
        } catch (e: Exception) { }
        isThisWeek
    }

    val dayNames = listOf("", "一", "二", "三", "四", "五", "六", "日")
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF7986CB), 
        Color(0xFF4FC3F7), Color(0xFF4DB6AC), Color(0xFFAED581), 
        Color(0xFFFFB74D), Color(0xFFFF8A65)
    )
    val courseTimes = listOf(
        "08:00\n09:50",
        "10:10\n12:00",
        "12:10\n14:00",
        "14:10\n16:00",
        "16:20\n18:10",
        "19:00\n20:50",
        "21:00\n21:50"
    )

    if (compact) {
        val now = java.time.LocalDateTime.now()
        val today = now.dayOfWeek.value
        val todayCourses = thisWeekCourses.filter {
            it.courseLocationIndex % 8 == today &&
                it.courseLocationIndex / 8 in courseTimes.indices
        }
        val next = todayCourses
            .filter {
                val start = courseTimes[it.courseLocationIndex / 8]
                    .substringBefore("\n")
                    .split(":")
                val at = java.time.LocalTime.of(start[0].toInt(), start[1].toInt())
                at.isAfter(now.toLocalTime())
            }
            .minByOrNull { it.courseLocationIndex / 8 }
            ?: todayCourses.minByOrNull { it.courseLocationIndex / 8 }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(16.dp)
                .background(ColorProvider(Color(0xEEF3F4F6)))
                .padding(10.dp)
        ) {
            Text(
                text = "第 " + displayWeek + " 周",
                style = TextStyle(fontSize = 11.sp, color = ColorProvider(Color(0xFF005BAC))),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            if (next != null) {
                Text(
                    text = "下一节课",
                    style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color.Gray)),
                )
                Text(
                    text = next.courseName,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color(0xFF1F2937)),
                    ),
                    maxLines = 2,
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = courseTimes[next.courseLocationIndex / 8].replace("\n", " - ") +
                        " · " + next.coursePlace,
                    style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color(0xFF4B5563))),
                    maxLines = 2,
                )
            } else {
                Text(
                    text = "今天没有课",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color(0xFF1F2937)),
                    ),
                )
            }
        }
        return
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(16.dp)
            .background(ColorProvider(Color(0xEEF3F4F6)))
            .padding(8.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = " < ",
                modifier = GlanceModifier.clickable(actionRunCallback<CoursePrevWeekAction>()),
                style = TextStyle(
                    fontSize = 18.sp,
                    color = ColorProvider(Color(0xFF005BAC)),
                    fontWeight = FontWeight.Bold
                )
            )
            
            Text(
                text = "第 $displayWeek 周课表",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ColorProvider(Color(0xFF005BAC)),
                    textAlign = TextAlign.Center
                )
            )
            
            Text(
                text = " > ",
                modifier = GlanceModifier.clickable(actionRunCallback<CourseNextWeekAction>()),
                style = TextStyle(
                    fontSize = 18.sp,
                    color = ColorProvider(Color(0xFF005BAC)),
                    fontWeight = FontWeight.Bold
                )
            )
        }
        
        Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(modifier = GlanceModifier.width(36.dp))
            for (i in 1..7) {
                Text(
                    text = dayNames[i],
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color.Gray), textAlign = TextAlign.Center)
                )
            }
        }
        
        val slots = (0..6).toList()
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(slots) { timeSlot ->
                Row(modifier = GlanceModifier.fillMaxWidth().height(60.dp).padding(bottom = 2.dp)) {
                    Text(
                        text = courseTimes[timeSlot],
                        modifier = GlanceModifier.width(36.dp).padding(top = 8.dp),
                        style = TextStyle(fontSize = 9.sp, color = ColorProvider(Color.Gray), textAlign = TextAlign.Center)
                    )
                    
                    for (day in 1..7) {
                        val course = thisWeekCourses.find { it.courseLocationIndex % 8 == day && it.courseLocationIndex / 8 == timeSlot }
                        Box(
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (course != null) {
                                val colorIndex = kotlin.math.abs(course.courseName.hashCode()) % colors.size
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxSize()
                                        .cornerRadius(4.dp)
                                        .background(ColorProvider(colors[colorIndex]))
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = course.courseName,
                                        style = TextStyle(fontSize = 9.sp, color = ColorProvider(Color.White), textAlign = TextAlign.Center),
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

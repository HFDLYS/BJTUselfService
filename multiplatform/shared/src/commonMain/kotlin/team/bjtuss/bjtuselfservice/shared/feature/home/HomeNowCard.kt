package team.bjtuss.bjtuselfservice.shared.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.displayScheduleCourseName
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeNowSession
import team.bjtuss.bjtuselfservice.shared.domain.home.pickHomeNowSessions
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseDetailContent
import team.bjtuss.bjtuselfservice.shared.feature.course.courseCategoryAccent
import team.bjtuss.bjtuselfservice.shared.feature.course.courseColorCategory
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheet

@Composable
internal fun HomeNowSection(
    timeZone: TimeZone,
) {
    val schedule = LocalHomeSchedule.current
    var now by remember {
        mutableStateOf(Clock.System.now().toLocalDateTime(timeZone))
    }
    LaunchedEffect(timeZone) {
        while (true) {
            now = Clock.System.now().toLocalDateTime(timeZone)
            delay(30_000)
        }
    }
    val today = now.date
    val sessions = remember(schedule, today, now.hour, now.minute) {
        pickHomeNowSessions(
            homeCoursesOnDate(schedule, today),
            homePhysicsLabsOnDate(schedule, today),
            now,
        )
    }
    if (sessions.isEmpty()) return
    var selected by remember { mutableStateOf<Course?>(null) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sessions.forEach { session ->
            HomeNowCard(
                session,
                accent = courseCategoryAccent(courseColorCategory(session.course, schedule.courseTypesByCode)),
                onClick = { selected = session.course },
            )
        }
    }
    selected?.let { course ->
        AppleSheet(onDismissRequest = { selected = null }, title = "课程详情", scrollableBody = true) {
            val detailScroll = rememberScrollState()
            CourseDetailContent(
                course,
                Modifier.fillMaxWidth().verticalScroll(detailScroll).desktopTouchScroll(detailScroll)
                    .padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
internal fun HomeNowCard(
    session: HomeNowSession,
    /** 课程类别强调色（CourseCategoryPalette），与课程表、小组件一致。 */
    accent: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val status = when {
        session.ongoing && session.isLab -> "进行中"
        session.ongoing -> "正在上"
        session.isLab -> "下一场"
        else -> "下一节"
    }
    val place = wrapFriendlyPlace(session.course.coursePlace)
    val teacher = session.course.courseTeacher.trim()
    val title = displayScheduleCourseName(session.course.courseName).ifBlank { session.course.courseName }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface,
        border = if (session.ongoing) {
            BorderStroke(2.dp, accent)
        } else {
            BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f))
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StatusChip(status, session.ongoing, accent)
                }
                if (place.isNotBlank()) {
                    Text(
                        place,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                }
                if (teacher.isNotBlank()) {
                    Text(
                        teacher,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (session.timeRange.isNotBlank()) {
                    Text(
                        session.timeRange,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, ongoing: Boolean, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (ongoing) accent.copy(alpha = 0.16f) else scheme.surfaceVariant,
        contentColor = if (ongoing) accent else scheme.onSurfaceVariant,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** 校区/楼宇/教室拆开，方便窄屏换行，不再挤成一行被裁掉。 */
internal fun wrapFriendlyPlace(place: String): String {
    val parts = place.split(Regex("[,，]")).map(String::trim).filter { it.isNotBlank() }
    return if (parts.size >= 2) parts.reversed().joinToString(" · ") else place.trim()
}

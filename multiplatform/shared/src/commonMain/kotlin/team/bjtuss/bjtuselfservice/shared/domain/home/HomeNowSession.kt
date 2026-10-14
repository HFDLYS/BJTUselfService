package team.bjtuss.bjtuselfservice.shared.domain.home

import kotlinx.datetime.LocalDateTime
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.classTimeRange

/**
 * 首页顶部「现在该去哪」：正在上的课、进行中的实验，或今天还没开始的下一场。
 * 今天已经上完则不占位置。
 */
data class HomeNowSession(
    val course: Course,
    val timeRange: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val ongoing: Boolean,
    val isLab: Boolean,
)

fun pickHomeNowSessions(
    todayCourses: List<Course>,
    todayLabs: List<Course>,
    now: LocalDateTime,
    staggeredClassTimes: Boolean = false,
): List<HomeNowSession> {
    val minute = now.hour * 60 + now.minute
    val sessions = todayCourses.mapNotNull { it.toNowSession(isLab = false, minute, staggeredClassTimes) } +
        todayLabs.mapNotNull { it.toNowSession(isLab = true, minute, staggeredClassTimes) }
    val ongoing = sessions.filter { it.ongoing }.sortedBy { it.startMinutes }
    if (ongoing.isNotEmpty()) return ongoing
    return listOfNotNull(
        sessions.filter { it.startMinutes > minute }.minByOrNull { it.startMinutes },
    )
}

private fun Course.toNowSession(isLab: Boolean, minuteOfDay: Int, staggeredClassTimes: Boolean): HomeNowSession? {
    val range = if (isLab) {
        scheduleEventTime?.trim().orEmpty()
    } else {
        classTimeRange(staggeredClassTimes).orEmpty()
    }
    val (start, end) = parseHmRange(range) ?: return null
    return HomeNowSession(
        course = this,
        timeRange = range.replace('~', '–').replace('-', '–'),
        startMinutes = start,
        endMinutes = end,
        ongoing = minuteOfDay in start until end,
        isLab = isLab,
    )
}

internal fun parseHmRange(range: String): Pair<Int, Int>? {
    val parts = range.split('-', '–', '~').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return null
    val start = parseHm(parts[0]) ?: return null
    val end = parseHm(parts[1]) ?: return null
    if (end <= start) return null
    return start to end
}

private fun parseHm(value: String): Int? {
    val pieces = value.split(':')
    if (pieces.size < 2) return null
    val hour = pieces[0].toIntOrNull() ?: return null
    val minute = pieces[1].take(2).toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

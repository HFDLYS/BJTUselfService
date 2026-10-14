package team.bjtuss.bjtuselfservice.shared.feature.home

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.eventDates
import team.bjtuss.bjtuselfservice.shared.domain.course.parseCourseWeeks
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeAgenda

internal data class HomeSchedulePresentation(
    val courses: List<Course> = emptyList(),
    val academicWeeks: List<OccupancyWeekDate> = emptyList(),
    val currentWeek: Int = 0,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val supplementalCourses: List<Course> = emptyList(),
    /** 培养方案课程性质，决定课程配色；未同步时为 null，课程按“未知”着色。 */
    val courseTypesByCode: Map<String, team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType>? = null,
    /** 设置「错峰上课时间」：第二节在逸夫教学楼/思源西楼按 10:30–12:20 计。 */
    val staggeredClassTimes: Boolean = false,
    // 保持最后一个：探针页用尾随 lambda 传入。
    val onOpenSchedule: () -> Unit = {},
)

internal val LocalHomeSchedule = staticCompositionLocalOf { HomeSchedulePresentation() }

/** 日期所在教学周：优先查校历，没有校历时按当前周推算；不在 1..30 内视为非教学周。 */
internal fun homeTeachingWeekOn(schedule: HomeSchedulePresentation, date: LocalDate): Int? {
    val calendar = schedule.academicWeeks.filter { it.startDate != null }
    val week = if (calendar.isNotEmpty()) {
        calendar.firstOrNull { date >= it.startDate!! && date <= it.startDate.plus(6, DateTimeUnit.DAY) }?.week
    } else if (schedule.currentWeek > 0) {
        val monday = schedule.today.minus(schedule.today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        val offset = (date.toEpochDays() - monday.toEpochDays()).toInt()
        schedule.currentWeek + kotlin.math.floor(offset / 7.0).toInt()
    } else null
    return week?.takeIf { it in 1..30 }
}

internal fun homeCoursesOnDate(schedule: HomeSchedulePresentation, date: LocalDate): List<Course> {
    val week = homeTeachingWeekOn(schedule, date) ?: return emptyList()
    // Historical isCurrentSemester=true means the selection schedule, not the current schedule.
    return schedule.courses.filter {
        !it.isCurrentSemester && it.scheduleEventKind == null &&
            it.courseLocationIndex % 8 == date.dayOfWeek.isoDayNumber &&
            it.courseLocationIndex / 8 in 0..6 && week in parseCourseWeeks(it.courseTime)
    }.sortedWith(compareBy({ it.courseLocationIndex / 8 }, { it.courseName }))
}

/** Grid fragments of the same experiment share an id; the home agenda shows one actual event.
 *  Physics labs and demo experiments share the home "实验" entry. */
internal fun homePhysicsLabsOnDate(schedule: HomeSchedulePresentation, date: LocalDate): List<Course> =
    schedule.supplementalCourses.filter {
        (it.scheduleEventKind == "physicslab" || it.scheduleEventKind == "phydemo") && date.toString() in it.eventDates
    }.distinctBy(Course::id).sortedWith(compareBy({ it.scheduleEventTime }, { it.courseName }))

internal fun HomeAgenda.withCourses(schedule: HomeSchedulePresentation): HomeAgenda =
    copy(days = days.map {
        it.copy(courses = homeCoursesOnDate(schedule, it.date), physicsLabCourses = homePhysicsLabsOnDate(schedule, it.date))
    })

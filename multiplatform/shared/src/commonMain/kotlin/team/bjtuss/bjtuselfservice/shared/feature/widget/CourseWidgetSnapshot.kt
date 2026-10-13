package team.bjtuss.bjtuselfservice.shared.feature.widget

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.SLOT_TIME_RANGES
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.displayScheduleCourseName
import team.bjtuss.bjtuselfservice.shared.domain.course.eventDates
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseColorCategory
import team.bjtuss.bjtuselfservice.shared.feature.course.courseColorCategory
import team.bjtuss.bjtuselfservice.shared.feature.course.tones
import team.bjtuss.bjtuselfservice.shared.domain.home.parseHmRange
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeSchedulePresentation
import team.bjtuss.bjtuselfservice.shared.feature.home.homeCoursesOnDate
import team.bjtuss.bjtuselfservice.shared.feature.home.homePhysicsLabsOnDate
import team.bjtuss.bjtuselfservice.shared.feature.home.homeTeachingWeekOn

/**
 * iOS 课程小组件读取的本地快照（App Group 内的 JSON）。
 *
 * 小组件进程不能登录、也不跑 KMP 代码，所以主应用把“未来若干天每天上什么、几点到几点”
 * 预先展开成纯数据；小组件只负责按当前时间挑出正在上/下一节并排版。
 * 只含课程名、地点、教师和时间，不含账号、Cookie 或成绩。
 */
@Serializable
data class CourseWidgetSnapshot(
    val version: Int = VERSION,
    /** 生成时刻（Unix 秒），小组件据此提示“数据较旧”。 */
    val generatedAt: Long,
    val days: List<CourseWidgetDay>,
    /** 课表的大节时间（"HH:mm-HH:mm"），周视图小组件按它画行。 */
    val slotTimes: List<String> = SLOT_TIME_RANGES,
) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
data class CourseWidgetDay(
    /** ISO 日期 yyyy-MM-dd。 */
    val date: String,
    /** 教学周；非教学周或无法确定时为 null。 */
    val week: Int? = null,
    val sessions: List<CourseWidgetSession>,
)

@Serializable
data class CourseWidgetSession(
    val name: String,
    val place: String,
    val teacher: String,
    /** 当天分钟数，供小组件精确排时间线。 */
    val startMinute: Int,
    val endMinute: Int,
    val isLab: Boolean = false,
    /** 课程类别强调色（CourseCategoryPalette.accent），0xRRGGBB；色条、进行中描边用。 */
    val color: Long,
    /**
     * 课程类别，与课程表页图例一致：required/limited/elective/pe/unknown/lab/exam。
     * 培养方案还没同步时普通课程为 unknown。
     */
    val kind: String = CourseColorCategory.UNKNOWN.widgetKind,
)

/** 默认展开两周：周末/假期前后打开一次 App，小组件也能撑到下周。 */
const val COURSE_WIDGET_DAYS = 14

/** 快照从本周一开始，周视图小组件才能画出今天之前的几天；往后仍保证 [COURSE_WIDGET_DAYS] 天。 */
internal fun courseWidgetRange(today: LocalDate): Pair<LocalDate, Int> {
    val pastDays = today.dayOfWeek.isoDayNumber - 1
    return today.minus(pastDays, DateTimeUnit.DAY) to (pastDays + COURSE_WIDGET_DAYS)
}

internal fun buildCourseWidgetSnapshot(
    schedule: HomeSchedulePresentation,
    startDate: LocalDate,
    generatedAtEpochSeconds: Long,
    dayCount: Int = COURSE_WIDGET_DAYS,
    courseTypesByCode: Map<String, CourseType>? = null,
): CourseWidgetSnapshot = CourseWidgetSnapshot(
    generatedAt = generatedAtEpochSeconds,
    days = (0 until dayCount).map { offset ->
        val date = startDate.plus(offset, DateTimeUnit.DAY)
        val events = homePhysicsLabsOnDate(schedule, date) + homeExamsOnDate(schedule, date)
        val sessions = (homeCoursesOnDate(schedule, date) + events).mapNotNull { course ->
            course.toWidgetSession(courseColorCategory(course, courseTypesByCode))
        }
        CourseWidgetDay(
            date = date.toString(),
            week = homeTeachingWeekOn(schedule, date),
            sessions = sessions.sortedWith(compareBy({ it.startMinute }, { it.endMinute }, { it.name })),
        )
    },
)

private val widgetJson = Json { encodeDefaults = true }

fun CourseWidgetSnapshot.encode(): String = widgetJson.encodeToString(CourseWidgetSnapshot.serializer(), this)

/** 考试与实验一样按实际日期出现；网格片段共用 id，只取一条。 */
private fun homeExamsOnDate(schedule: HomeSchedulePresentation, date: LocalDate): List<Course> =
    schedule.supplementalCourses.filter { it.scheduleEventKind == "exam" && date.toString() in it.eventDates }
        .distinctBy(Course::id)

private fun Course.toWidgetSession(category: CourseColorCategory): CourseWidgetSession? {
    val isEvent = scheduleEventKind != null
    val range = if (isEvent) scheduleEventTime?.trim().orEmpty() else SLOT_TIME_RANGES.getOrNull(courseLocationIndex / 8).orEmpty()
    val (start, end) = parseHmRange(range) ?: return null
    return CourseWidgetSession(
        name = displayScheduleCourseName(courseName.trim()),
        place = compactWidgetPlace(coursePlace),
        teacher = courseTeacher.trim(),
        startMinute = start,
        endMinute = end,
        isLab = category == CourseColorCategory.LAB,
        color = category.tones.accent,
        kind = category.widgetKind,
    )
}

/**
 * 小组件空间很小：学校地点按“大地点,楼宇,教室”返回，去掉最外层校区，只留“楼宇 教室”；
 * 教室名已包含楼宇（如体育馆的场馆全称）时只留教室，“-”占位段丢弃。
 */
internal fun compactWidgetPlace(place: String): String {
    val parts = place.split(Regex("[,，]")).map(String::trim).filter(String::isNotBlank)
    val tail = (if (parts.size >= 3) parts.drop(1) else parts)
        .filter { it.trim('-', '－', '—').isNotEmpty() }
    if (tail.size == 2) {
        val (building, room) = tail
        if (building.length >= 3 && room.contains(building.takeLast(3))) return room
    }
    return tail.joinToString(" ")
}


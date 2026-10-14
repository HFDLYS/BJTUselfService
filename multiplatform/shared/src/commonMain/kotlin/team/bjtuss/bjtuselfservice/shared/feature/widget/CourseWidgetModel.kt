package team.bjtuss.bjtuselfservice.shared.feature.widget

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json

/**
 * 桌面小组件的展示逻辑（Android RemoteViews 用）。规则与 iOS 小组件
 * `CourseScheduleWidget/CourseWidgetData.swift`、`CourseWidgets.swift` 一一对应，改一边要同步另一边。
 *
 * 时间都按“日期 + 当天分钟数”处理（学校在北京时间，没有夏令时），不经过时区换算。
 */

private val widgetDecoder = Json { ignoreUnknownKeys = true }

/** 读快照；损坏或版本不兼容时返回 null，小组件按“没有快照”处理。 */
fun decodeCourseWidgetSnapshot(json: String): CourseWidgetSnapshot? =
    runCatching { widgetDecoder.decodeFromString(CourseWidgetSnapshot.serializer(), json) }.getOrNull()

/** 一节课落到具体日期上。 */
data class WidgetOccurrence(
    val session: CourseWidgetSession,
    val date: LocalDate,
) {
    val id: String get() = "${session.name}-$date-${session.startMinute}"

    fun isOngoing(now: LocalDateTime): Boolean = compare(now) == 0
    fun isFinished(now: LocalDateTime): Boolean = compare(now) > 0

    /** 负数：还没开始；0：正在上；正数：已经下课。 */
    private fun compare(now: LocalDateTime): Int {
        if (now.date < date) return -1
        if (now.date > date) return 1
        val minute = now.minuteOfDay
        return when {
            minute < session.startMinute -> -1
            minute < session.endMinute -> 0
            else -> 1
        }
    }

    /** 到下课还剩几分钟：不足一分钟的秒数向上取整成一分钟，即 end - 当前分钟，至少 1。 */
    fun minutesUntilEnd(now: LocalDateTime): Int = maxOf(1, session.endMinute - now.minuteOfDay)
}

data class WidgetDayPlan(
    val date: LocalDate,
    val week: Int?,
    val occurrences: List<WidgetOccurrence>,
)

data class WidgetUpNext(
    val primary: WidgetOccurrence,
    val secondary: WidgetOccurrence?,
    val isFutureDay: Boolean,
)

sealed interface CourseWidgetState {
    /** 没有快照：App 还没同步过课表，或已退出登录。 */
    data object SignedOut : CourseWidgetState
    /** 快照没覆盖到今天：太久没打开 App。 */
    data object Stale : CourseWidgetState
    data class Ready(val model: CourseWidgetDisplayModel) : CourseWidgetState
}

class CourseWidgetDisplayModel(
    val now: LocalDateTime,
    val today: WidgetDayPlan,
    /** 今天之后第一个有课的日子。 */
    val nextDay: WidgetDayPlan?,
    /** 本周一到周日；快照没覆盖到的日子是空的。 */
    val week: List<WidgetDayPlan>,
    /** 大节的开始/结束分钟。 */
    val slots: List<IntRange>,
) {
    val remainingToday: List<WidgetOccurrence> get() = today.occurrences.filter { !it.isFinished(now) }

    val weekIsEmpty: Boolean get() = week.all { it.occurrences.isEmpty() }

    /** 整周没课、明天也没课：显示休息提示，而不是把很久以后的课当“下一节”。 */
    val isRestPeriod: Boolean
        get() {
            if (!weekIsEmpty) return false
            val next = nextDay ?: return true
            return dayOffset(next.date, now.date) > 1
        }

    /** 今天没有待上的课时日期下方那一行。 */
    val todayStatus: String
        get() {
            val base = if (today.occurrences.isEmpty()) "今天没有课" else "今天已上完"
            val next = nextDay ?: return base
            val gap = dayOffset(next.date, now.date)
            return when {
                gap <= 1 -> base
                gap == 2 -> "$base · 明天无课"
                else -> "$base · 之后 ${gap - 1} 天无课"
            }
        }

    /** 正在上的那节 + 之后一节；今天上完了就取下一个有课日的前两节。 */
    val upNext: WidgetUpNext?
        get() {
            val remaining = remainingToday
            remaining.firstOrNull()?.let { return WidgetUpNext(it, remaining.getOrNull(1), false) }
            val day = nextDay ?: return null
            val first = day.occurrences.firstOrNull() ?: return null
            return WidgetUpNext(first, day.occurrences.getOrNull(1), true)
        }

    /** 今天不在教学周内写“非教学周”；在教学周里只是整周没排课时写“本周没有课”。 */
    val restTitle: String get() = if (today.week == null) "非教学周" else "本周没有课"
}

private val defaultSlotTimes = listOf(
    "08:00-09:50", "10:10-12:00", "12:10-14:00", "14:10-16:00",
    "16:20-18:10", "19:00-20:50", "21:00-21:50",
)

fun courseWidgetState(snapshot: CourseWidgetSnapshot?, now: LocalDateTime): CourseWidgetState {
    snapshot ?: return CourseWidgetState.SignedOut
    val todayKey = now.date.toString()
    val index = snapshot.days.indexOfFirst { it.date == todayKey }
    if (index < 0) return CourseWidgetState.Stale
    val today = snapshot.days[index].plan() ?: return CourseWidgetState.Stale
    val nextDay = snapshot.days.drop(index + 1).firstOrNull { it.sessions.isNotEmpty() }?.plan()
    val monday = now.date.minus(now.date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    val byKey = snapshot.days.associateBy { it.date }
    val week = (0 until 7).map { offset ->
        val date = monday.plus(offset, DateTimeUnit.DAY)
        byKey[date.toString()]?.plan() ?: WidgetDayPlan(date, null, emptyList())
    }
    return CourseWidgetState.Ready(CourseWidgetDisplayModel(now, today, nextDay, week, parseSlots(snapshot.slotTimes)))
}

private fun CourseWidgetDay.plan(): WidgetDayPlan? {
    val date = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    return WidgetDayPlan(date, week, sessions.map { WidgetOccurrence(it, date) })
}

internal fun parseSlots(times: List<String>?): List<IntRange> {
    val parsed = (times ?: defaultSlotTimes).mapNotNull { range ->
        val parts = range.split('-').map { part ->
            val hm = part.trim().split(':').mapNotNull { it.trim().toIntOrNull() }
            if (hm.size == 2) hm[0] * 60 + hm[1] else null
        }
        val start = parts.getOrNull(0) ?: return@mapNotNull null
        val end = parts.getOrNull(1) ?: return@mapNotNull null
        if (start < end) start..end else null
    }
    return if (parsed.isEmpty() && times != null) parseSlots(null) else parsed
}

/**
 * 小组件下一次需要重新排版的时刻。今天有课的时段（第一节前 1 小时到最后一节下课）逐分钟走，
 * “还剩 N 分钟”才准；其它时候只在下一个课程起止或零点，且最多 15 分钟后再看一次（兜底系统闹钟误差）。
 */
fun courseWidgetNextRefresh(snapshot: CourseWidgetSnapshot?, now: LocalDateTime): LocalDateTime {
    val nextMinute = now.date.atMinute(now.minuteOfDay + 1)
    val cap = now.date.atMinute(now.minuteOfDay + 15)
    val midnight = now.date.plus(1, DateTimeUnit.DAY).atMinute(0)
    val today = snapshot?.days?.firstOrNull { it.date == now.date.toString() }?.sessions.orEmpty()
    if (today.isNotEmpty()) {
        val first = today.minOf { it.startMinute }
        val last = today.maxOf { it.endMinute }
        if (now.minuteOfDay in (first - 60) until last) return nextMinute
        val upcoming = today.flatMap { listOf(it.startMinute - 60, it.startMinute, it.endMinute) }
            .filter { it > now.minuteOfDay }.minOrNull()
        if (upcoming != null) return minOf(now.date.atMinute(upcoming), cap)
    }
    return minOf(midnight, cap)
}

// MARK: 周视图排版（与 WeekCourseWidget.swift 的 WeekLayout 一致）

data class WidgetWeekBlock(
    val occurrence: WidgetOccurrence,
    val column: Int,
    val firstRow: Int,
    val lastRow: Int,
    val lane: Int,
    val laneCount: Int,
)

class WidgetWeekLayout(model: CourseWidgetDisplayModel) {
    val days: List<WidgetDayPlan>
    /** 显示哪些大节（下标对应 slots）。 */
    val rows: List<Int>
    val slots: List<IntRange> = model.slots
    val blocks: List<WidgetWeekBlock>
    val todayColumn: Int?

    init {
        // 周末没课且不是今天就不占列，给工作日更宽的色块。
        days = model.week.filterIndexed { index, day ->
            index < 5 || day.occurrences.isNotEmpty() || day.date == model.now.date
        }
        fun overlaps(occurrence: WidgetOccurrence, slot: IntRange): Boolean =
            occurrence.session.slotStartMinute < slot.last && occurrence.session.slotEndMinute > slot.first
        // 上午两节、下午两节始终显示，构成稳定骨架；午间、晚上的大节有课才显示。
        val core = setOf(0, 1, 3, 4)
        val used = days.flatMap { day ->
            day.occurrences.flatMap { occurrence -> slots.indices.filter { overlaps(occurrence, slots[it]) } }
        }.toSet()
        rows = slots.indices.filter { it in core || it in used }

        val result = mutableListOf<WidgetWeekBlock>()
        days.forEachIndexed { column, day ->
            val dayBlocks = day.occurrences.mapNotNull { occurrence ->
                val covered = rows.indices.filter { overlaps(occurrence, slots[rows[it]]) }
                if (covered.isEmpty()) null else WidgetWeekBlock(occurrence, column, covered.first(), covered.last(), 0, 1)
            }.toMutableList()
            // 同一时段冲突的课并排显示，不互相盖住。
            val laneEnds = mutableListOf<Int>()
            for (i in dayBlocks.indices) {
                val block = dayBlocks[i]
                val free = laneEnds.indexOfFirst { it < block.firstRow }
                dayBlocks[i] = if (free >= 0) {
                    laneEnds[free] = block.lastRow
                    block.copy(lane = free)
                } else {
                    laneEnds += block.lastRow
                    block.copy(lane = laneEnds.size - 1)
                }
            }
            for (i in dayBlocks.indices) {
                val block = dayBlocks[i]
                val overlapping = dayBlocks.filter { it.firstRow <= block.lastRow && it.lastRow >= block.firstRow }
                dayBlocks[i] = block.copy(laneCount = (overlapping.maxOfOrNull { it.lane } ?: 0) + 1)
            }
            result += dayBlocks
        }
        blocks = result
        todayColumn = days.indexOfFirst { it.date == model.now.date }.takeIf { it >= 0 }
    }
}

// MARK: 文案与格式

val LocalDateTime.minuteOfDay: Int get() = hour * 60 + minute

private fun LocalDate.atMinute(minute: Int): LocalDateTime {
    val dayShift = minute.floorDiv(24 * 60)
    val date = plus(dayShift, DateTimeUnit.DAY)
    val inDay = minute - dayShift * 24 * 60
    return LocalDateTime(date.year, date.month, date.day, inDay / 60, inDay % 60)
}

fun dayOffset(date: LocalDate, from: LocalDate): Int = (date.toEpochDays() - from.toEpochDays()).toInt()

fun widgetClock(minute: Int): String =
    "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

fun WidgetOccurrence.timeRange(): String = "${widgetClock(session.startMinute)} - ${widgetClock(session.endMinute)}"

fun widgetMonthDay(date: LocalDate): String = "${date.month.ordinal + 1}.${date.day.toString().padStart(2, '0')}"

private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun widgetWeekday(date: LocalDate): String = weekdayNames[date.dayOfWeek.isoDayNumber - 1]

/** 头部日期前的相对说法：只有“明天”“后天”。 */
fun relativeDayPrefix(date: LocalDate, now: LocalDate): String? = when (dayOffset(date, now)) {
    1 -> "明天"
    2 -> "后天"
    else -> null
}

/** 文案里称呼某天：今天/明天/后天、本周写周几、下周写“下周几”，再远写日期。 */
fun widgetDayName(date: LocalDate, now: LocalDate): String {
    when (dayOffset(date, now)) {
        0 -> return "今天"
        1 -> return "明天"
        2 -> return "后天"
    }
    val monday = { d: LocalDate -> d.minus(d.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY) }
    return when (dayOffset(monday(date), monday(now)) / 7) {
        0 -> widgetWeekday(date)
        1 -> "下" + widgetWeekday(date)
        else -> widgetMonthDay(date)
    }
}

/** 小空间只留教室号和老师，如“YF403 刘一”。 */
fun CourseWidgetSession.roomAndTeacher(): String {
    val room = place.split(' ').lastOrNull().orEmpty()
    return listOf(room, teacher).filter(String::isNotEmpty).joinToString(" ")
}

fun CourseWidgetSession.detailLine(): String = listOf(place, teacher).filter(String::isNotEmpty).joinToString("  ")

/** “13 分钟”“1 小时 5 分钟”；[short] 用于窄栏标签，超过 1 小时写“1 小时 5 分”。 */
fun widgetRemainingText(minutes: Int, short: Boolean = false): String {
    val value = maxOf(1, minutes)
    if (value < 60) return "$value 分钟"
    val hours = value / 60
    val rest = value % 60
    return if (rest == 0) "$hours 小时" else "$hours 小时 $rest 分${if (short) "" else "钟"}"
}

/** 排进周视图网格用的“名义时间”：错峰课仍归原大节。 */
val CourseWidgetSession.slotStartMinute: Int get() = startMinute - staggerMinutes
val CourseWidgetSession.slotEndMinute: Int get() = endMinute - staggerMinutes
val CourseWidgetSession.isStaggered: Boolean get() = staggerMinutes > 0

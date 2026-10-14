package team.bjtuss.bjtuselfservice.shared.feature.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

class CourseWidgetModelTest {
    private val monday = LocalDate(2026, 10, 12)

    private fun session(name: String, start: Int, end: Int, stagger: Int = 0) = CourseWidgetSession(
        name = name, place = "思源西楼 SX101", teacher = "教师", startMinute = start, endMinute = end,
        color = 0xD9574E, kind = "required", staggerMinutes = stagger,
    )

    private fun snapshot(vararg days: Pair<Int, List<CourseWidgetSession>>, week: Int? = 5) = CourseWidgetSnapshot(
        generatedAt = 1L,
        days = (0 until 14).map { offset ->
            CourseWidgetDay(monday.plus(offset, DateTimeUnit.DAY).toString(), week,
                days.firstOrNull { it.first == offset }?.second.orEmpty())
        },
    )

    private fun at(dayOffset: Int, hour: Int, minute: Int): LocalDateTime {
        val date = monday.plus(dayOffset, DateTimeUnit.DAY)
        return LocalDateTime(date.year, date.month, date.day, hour, minute)
    }

    private fun ready(snapshot: CourseWidgetSnapshot, now: LocalDateTime) =
        assertIs<CourseWidgetState.Ready>(courseWidgetState(snapshot, now)).model

    @Test fun missingOrOutdatedSnapshotsAreReported() {
        assertEquals(CourseWidgetState.SignedOut, courseWidgetState(null, at(0, 9, 0)))
        assertEquals(CourseWidgetState.Stale, courseWidgetState(snapshot(), at(30, 9, 0)))
        assertNull(decodeCourseWidgetSnapshot("{broken"))
        val decoded = decodeCourseWidgetSnapshot(snapshot(0 to listOf(session("课", 480, 590))).encode())
        assertEquals("课", decoded?.days?.first()?.sessions?.single()?.name)
    }

    @Test fun upNextFollowsTheDayAndCrossesToTheNextClassDay() {
        val data = snapshot(
            0 to listOf(session("早课", 480, 590), session("午课", 610, 720)),
            2 to listOf(session("周三课", 480, 590)),
        )
        val ongoing = ready(data, at(0, 9, 10))
        assertEquals("早课", ongoing.upNext?.primary?.session?.name)
        assertEquals("午课", ongoing.upNext?.secondary?.session?.name)
        assertTrue(ongoing.upNext!!.primary.isOngoing(ongoing.now))
        assertEquals(40, ongoing.upNext!!.primary.minutesUntilEnd(ongoing.now))

        val evening = ready(data, at(0, 20, 0))
        assertTrue(evening.upNext!!.isFutureDay)
        assertEquals("周三课", evening.upNext!!.primary.session.name)
        assertEquals("今天已上完 · 明天无课", evening.todayStatus)
        assertFalse(evening.isRestPeriod)
    }

    @Test fun emptyWeekWithoutClassTomorrowIsARestPeriod() {
        val holiday = ready(snapshot(week = null), at(0, 10, 0))
        assertTrue(holiday.isRestPeriod)
        assertEquals("非教学周", holiday.restTitle)
        assertEquals("本周没有课", ready(snapshot(), at(0, 10, 0)).restTitle)
    }

    @Test fun dayNamesAndFormatting() {
        val now = monday
        assertEquals("今天", widgetDayName(now, now))
        assertEquals("后天", widgetDayName(now.plus(2, DateTimeUnit.DAY), now))
        assertEquals("周五", widgetDayName(now.plus(4, DateTimeUnit.DAY), now))
        assertEquals("下周一", widgetDayName(now.plus(7, DateTimeUnit.DAY), now))
        assertEquals("10.26", widgetDayName(now.plus(14, DateTimeUnit.DAY), now))
        assertEquals("明天", relativeDayPrefix(now.plus(1, DateTimeUnit.DAY), now))
        assertNull(relativeDayPrefix(now.plus(3, DateTimeUnit.DAY), now))
        assertEquals("1 小时 5 分钟", widgetRemainingText(65))
        assertEquals("1 小时 15 分", widgetRemainingText(75, short = true))
        assertEquals("SX101 教师", session("课", 0, 1).roomAndTeacher())
    }

    @Test fun weekLayoutKeepsStaggeredClassInItsSlotAndSplitsConflicts() {
        val data = snapshot(
            0 to listOf(session("错峰", 630, 740, stagger = 20), session("冲突", 610, 720)),
            1 to listOf(session("实验", 13 * 60 + 20, 15 * 60 + 50)),
        )
        val layout = WidgetWeekLayout(ready(data, at(0, 8, 0)))
        assertEquals(listOf(0, 1, 2, 3, 4), layout.rows)
        val staggered = layout.blocks.single { it.occurrence.session.name == "错峰" }
        assertEquals(1, staggered.firstRow)
        assertEquals(1, staggered.lastRow)
        assertEquals(2, staggered.laneCount)
        val lab = layout.blocks.single { it.occurrence.session.name == "实验" }
        assertEquals(2 to 3, lab.firstRow to lab.lastRow)
        assertEquals(0, layout.todayColumn)
        assertEquals(5, layout.days.size)
    }

    @Test fun refreshTicksEveryMinuteDuringClassHoursOnly() {
        val data = snapshot(0 to listOf(session("早课", 480, 590)))
        assertEquals(at(0, 9, 11), courseWidgetNextRefresh(data, at(0, 9, 10)))
        assertEquals(at(0, 7, 0), courseWidgetNextRefresh(data, at(0, 6, 50)))
        assertEquals(at(0, 20, 15), courseWidgetNextRefresh(data, at(0, 20, 0)))
        assertEquals(at(1, 0, 0), courseWidgetNextRefresh(data, at(0, 23, 50)))
    }
}

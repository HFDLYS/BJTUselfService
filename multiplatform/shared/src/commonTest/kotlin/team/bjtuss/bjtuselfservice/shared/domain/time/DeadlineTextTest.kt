package team.bjtuss.bjtuselfservice.shared.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import team.bjtuss.bjtuselfservice.shared.domain.home.homeworkDueDate
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework

class DeadlineTextTest {
    @Test
    fun midnightShowsAsPreviousDay2400() {
        assertEquals("2026-10-19 24:00", formatSchoolDeadline("2026-10-20 00:00"))
        assertEquals("2026-10-19 24:00", formatSchoolDeadline("2026-10-20 00:00:00"))
        assertEquals("2026-12-31 24:00", formatSchoolDeadline("2027-01-01 00:00"))
        assertEquals("2026-02-28 24:00", formatSchoolDeadline("2026-03-01 00:00"))
    }

    @Test
    fun otherTimesUnchanged() {
        assertEquals("2026-10-25 23:59", formatSchoolDeadline("2026-10-25 23:59"))
        assertEquals("2026-10-20 00:01", formatSchoolDeadline("2026-10-20 00:01"))
        assertEquals("未提供", formatSchoolDeadline("未提供"))
        assertEquals("", formatSchoolDeadline(""))
    }

    @Test
    fun epochUsesBeijingTime() {
        val midnight = LocalDate(2026, 10, 20).atStartOfDayIn(TimeZone.of("Asia/Shanghai")).epochSeconds
        assertEquals("2026-10-19 24:00", formatDeadlineEpoch(midnight))
        assertEquals("2026-10-20 00:01", formatDeadlineEpoch(midnight + 60))
    }

    @Test
    fun chineseFormatShiftsDateAndWeekday() {
        assertEquals("2026年10月19日 24:00 · 周一", formatChineseDeadline("2026年10月20日 00:00"))
        assertEquals("2026年10月20日 23:59 · 周二", formatChineseDeadline("2026年10月20日 23:59 周二"))
        assertEquals("未公布", formatChineseDeadline("未公布"))
    }

    /** 显示的日期必须和首页日程归属的那一天一致。 */
    @Test
    fun matchesHomeAgendaDay() {
        listOf("2026-10-20 00:00", "2026-10-20 00:01", "2026-10-19 23:59").forEach { end ->
            val homework = Homework(upId = 1, idSnId = null, score = "", userId = 1, courseId = 1, courseName = "课",
                title = "作业", content = "", createDate = "", endTime = end, openDate = "", status = 0,
                submitCount = 0, allCount = 0, subStatus = "", scoreId = 0, homeworkType = 0)
            val agendaDay = homeworkDueDate(homework, TimeZone.of("Asia/Shanghai")).toString()
            assertEquals(agendaDay, formatSchoolDeadline(end).substringBefore(' '), end)
        }
    }
}

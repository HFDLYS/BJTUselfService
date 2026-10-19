package team.bjtuss.bjtuselfservice.shared.domain.home

import kotlin.test.Test
import kotlin.test.assertEquals
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeKind
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework

class HomeChangeFieldTest {
    @Test
    fun homeworkLegacyBlobSplitsIntoCourseDeadlineAndStatus() {
        val record = HomeChangeRecord(
            domain = HomeChangeDomain.HOMEWORK,
            kind = DataChangeKind.MODIFIED,
            title = "Audience Analysis",
            beforeDetail = "国际议题演讲与陈述 · 2026-10-11 00:00 · 未提交",
            afterDetail = "国际议题演讲与陈述 · 2026-10-11 00:00 · 已提交",
        )
        val fields = record.displayFields()
        assertEquals(listOf("课程", "截止时间", "状态"), fields.map { it.label })
        assertEquals("国际议题演讲与陈述", fields[0].after)
        assertEquals("2026-10-11 00:00", fields[1].after)
        assertEquals("未提交", fields[2].before)
        assertEquals("已提交", fields[2].after)
        assertEquals(1, fields.count { it.before != it.after })
        assertEquals(listOf("状态"), record.visibleFields().map { it.label })
        assertEquals("未提交", record.visibleFields().single().before)
        assertEquals("已提交", record.visibleFields().single().after)
    }

    @Test
    fun modifiedUnchangedNameAndDeadlineAreHidden() {
        val record = HomeChangeRecord(
            domain = HomeChangeDomain.HOMEWORK,
            kind = DataChangeKind.MODIFIED,
            title = "02-2 尝试封装学生列表类。",
            fields = listOf(
                HomeChangeField("课程", "面向对象程序设计 (C++)", "面向对象程序设计 (C++)"),
                HomeChangeField("分数", "未公布成绩", "未公布成绩"),
                HomeChangeField("状态", "未提交", "已提交"),
                HomeChangeField("截止时间", "2026-10-12 00:00", "2026-10-12 00:00"),
            ),
        )
        assertEquals(listOf("状态"), record.visibleFields().map { it.label })
    }

    @Test
    fun addedHomeworkLegacyBlobDoesNotCollapseToOneDetailField() {
        val record = HomeChangeRecord(
            domain = HomeChangeDomain.HOMEWORK,
            kind = DataChangeKind.ADDED,
            title = "02-2 尝试封装学生列表类。",
            afterDetail = "面向对象程序设计 (C++) · 2026-10-12 00:00 · 已提交",
        )
        assertEquals(
            listOf("课程", "截止时间", "状态"),
            record.displayFields().map { it.label },
        )
    }

    @Test
    fun storedFieldsWinOverLegacyBlob() {
        val record = HomeChangeRecord(
            domain = HomeChangeDomain.HOMEWORK,
            kind = DataChangeKind.MODIFIED,
            title = "作业",
            beforeDetail = "整段旧文案",
            afterDetail = "整段新文案",
            fields = listOf(
                HomeChangeField("课程", "课", "课"),
                HomeChangeField("状态", "未提交", "已提交"),
            ),
        )
        assertEquals(listOf("课程", "状态"), record.displayFields().map { it.label })
        assertEquals(listOf("状态"), record.visibleFields().map { it.label })
        assertEquals("已提交", record.displayFields().single { it.label == "状态" }.after)
    }

    @Test
    fun homeworkChangeMatchesTitleAndCourse() {
        val record = HomeChangeRecord(
            domain = HomeChangeDomain.HOMEWORK,
            kind = DataChangeKind.MODIFIED,
            title = "Audience Analysis",
            afterDetail = "国际议题演讲与陈述 · 2026-10-11 00:00 · 已提交",
        )
        val matched = matchHomeworkChange(
            record,
            listOf(
                homework("别的作业", "国际议题演讲与陈述"),
                homework("Audience Analysis", "国际议题演讲与陈述"),
                homework("Audience Analysis", "另一门课"),
            ),
        )
        assertEquals("国际议题演讲与陈述", matched?.courseName)
        assertEquals("Audience Analysis", matched?.title)
    }

    /** 新记录的截止时间是显示文字（前一天 24:00），也要匹配到同一份作业，而不是退回同名第一项。 */
    @Test
    fun homeworkChangeMatchesMidnightDeadlineDisplayText() {
        val record = HomeChangeRecord(
            domain = HomeChangeDomain.HOMEWORK,
            kind = DataChangeKind.MODIFIED,
            title = "实验",
            fields = listOf(
                HomeChangeField("课程", "课", "课"),
                HomeChangeField("截止时间", "2026-10-10 24:00", "2026-10-11 24:00"),
            ),
        )
        val matched = matchHomeworkChange(
            record,
            listOf(homework("实验", "课", upId = 1, endTime = "2026-10-11 00:00"),
                homework("实验", "课", upId = 2, endTime = "2026-10-12 00:00")),
        )
        assertEquals(2, matched?.upId)
    }

    private fun homework(title: String, course: String, upId: Int = title.hashCode(), endTime: String = "2026-10-11 00:00") = Homework(
            upId = upId,
            idSnId = null,
            score = "",
            userId = 0,
            courseId = 1,
            courseName = course,
            title = title,
            content = "",
            createDate = "",
            endTime = endTime,
            openDate = "",
            status = 0,
            submitCount = 0,
            allCount = 0,
            subStatus = "已提交",
            scoreId = 0,
            homeworkType = 0,
        )
}

package team.bjtuss.bjtuselfservice.shared.data.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeKind
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeDomain
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeRecord
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework

class HomeworkChangeRecorderTest {
    @Test
    fun modifiedHomeworkStoresFieldDiffs() = runBlocking {
        val feed = FakeFeed()
        val before = homework(subStatus = "未提交", score = "")
        val after = homework(subStatus = "已提交", score = "")
        homeworkChangeRecorder(feed).record(listOf(before), listOf(after))

        val record = feed.capturedChanges.single()
        assertEquals(HomeChangeDomain.HOMEWORK, record.domain)
        assertEquals(DataChangeKind.MODIFIED, record.kind)
        assertEquals("02-2 尝试封装学生列表类。", record.title)
        val byLabel = record.fields.associateBy { it.label }
        assertEquals("面向对象程序设计 (C++)", byLabel.getValue("课程").after)
        assertEquals("未公布成绩", byLabel.getValue("分数").after)
        assertEquals("未提交", byLabel.getValue("状态").before)
        assertEquals("已提交", byLabel.getValue("状态").after)
        assertEquals("2026-10-12 00:00", byLabel.getValue("截止时间").after)
    }

    private fun homework(subStatus: String, score: String) = Homework(
        upId = 2,
        idSnId = null,
        score = score,
        userId = 0,
        courseId = 1,
        courseName = "面向对象程序设计 (C++)",
        title = "02-2 尝试封装学生列表类。",
        content = "",
        createDate = "",
        endTime = "2026-10-12 00:00",
        openDate = "",
        status = 0,
        submitCount = 0,
        allCount = 0,
        subStatus = subStatus,
        scoreId = 0,
        homeworkType = 0,
    )

    private class FakeFeed : HomeChangeFeedRepository {
        val capturedChanges = mutableListOf<HomeChangeRecord>()
        override val records: StateFlow<List<HomeChangeRecord>> = MutableStateFlow(emptyList())
        override suspend fun acceptRefresh(
            domain: HomeChangeDomain,
            hadPreviousItems: Boolean,
            changes: List<HomeChangeRecord>,
        ): Boolean {
            capturedChanges.addAll(changes)
            return true
        }

        override suspend fun clear(domain: HomeChangeDomain?): Boolean = true
    }
}

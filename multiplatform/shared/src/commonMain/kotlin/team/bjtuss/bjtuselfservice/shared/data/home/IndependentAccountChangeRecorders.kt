package team.bjtuss.bjtuselfservice.shared.data.home

import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeDomain
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeField
import team.bjtuss.bjtuselfservice.shared.domain.time.formatDeadlineEpoch
import team.bjtuss.bjtuselfservice.shared.feature.citel.CitelTask
import team.bjtuss.bjtuselfservice.shared.feature.citel.citelDateText
import team.bjtuss.bjtuselfservice.shared.feature.citel.completed
import team.bjtuss.bjtuselfservice.shared.feature.phydemo.PhysicsDemoBooking
import team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLab

fun citelChangeRecorder(feed: HomeChangeFeedRepository): DataChangeRecorder<CitelTask> =
    independentChangeRecorder(feed, HomeChangeDomain.CITEL, { it.courseId to it.id }, CitelTask::title) { task ->
        listOf(
            "标题" to task.title,
            "课程" to task.courseName,
            "类型" to if (task.programming) "编程作业" else "书面作业",
            "开始时间" to task.openTime?.let(::citelDateText).orEmpty(),
            "截止时间" to task.dueTime?.let(::formatDeadlineEpoch).orEmpty(),
            "折扣时间" to task.discountTime?.let(::formatDeadlineEpoch).orEmpty(),
            "倍率" to task.discount?.toString().orEmpty(),
            "允许迟交" to task.allowLate?.let { if (it) "是" else "否" }.orEmpty(),
            "状态" to task.status,
            "完成情况" to if (task.completed) "已完成" else "未完成",
            "分数" to task.grade.orEmpty(),
        )
    }

fun physicsLabChangeRecorder(feed: HomeChangeFeedRepository): DataChangeRecorder<PhysicsLab> =
    independentChangeRecorder(feed, HomeChangeDomain.PHYSICS_LAB, PhysicsLab::name, PhysicsLab::name) { lab ->
        listOf(
            "日期" to lab.dates.joinToString("、"),
            "时间" to lab.timeRange.orEmpty(),
            "地点" to lab.location,
            "教师" to lab.teacher,
            "周数" to lab.weekCount.toString(),
        )
    }

fun physicsDemoChangeRecorder(feed: HomeChangeFeedRepository): DataChangeRecorder<PhysicsDemoBooking> =
    independentChangeRecorder(feed, HomeChangeDomain.PHYSICS_DEMO, PhysicsDemoBooking::name, PhysicsDemoBooking::name) { booking ->
        listOf(
            "日期" to booking.date.toString(),
            "时间" to booking.timeRange,
            "地点" to booking.location,
            "助教" to booking.teacher,
        )
    }

/** Compare displayed fields, ignoring URLs and other transport-only changes. */
private fun <T, K> independentChangeRecorder(
    feed: HomeChangeFeedRepository,
    domain: HomeChangeDomain,
    identity: (T) -> K,
    title: (T) -> String,
    values: (T) -> List<Pair<String, String>>,
): DataChangeRecorder<T> = changeRecorder(
    feed, domain, identity,
    equivalent = { before, after -> values(before) == values(after) },
    title = title,
    detail = { item -> values(item).joinToString(" · ") { (label, value) -> "$label：$value" } },
    fields = { before, after ->
        val old = before?.let(values).orEmpty().toMap()
        val new = after?.let(values).orEmpty().toMap()
        (old.keys + new.keys).map { label -> HomeChangeField(label, old[label].orEmpty(), new[label].orEmpty()) }
    },
)

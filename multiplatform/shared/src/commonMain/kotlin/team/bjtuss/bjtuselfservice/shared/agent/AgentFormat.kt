package team.bjtuss.bjtuselfservice.shared.agent

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant
import team.bjtuss.bjtuselfservice.shared.domain.calendar.parseExamCalendarTime
import team.bjtuss.bjtuselfservice.shared.domain.homework.typeLabel
import team.bjtuss.bjtuselfservice.shared.feature.citel.completed
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabEnrollment

// 输出格式：JSON 给 agent 解析，Markdown 给人看。只输出业务数据，不输出凭据、Cookie 或内部链接参数。

internal fun isoTime(epochSeconds: Long): String {
    val t = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(AGENT_TIME_ZONE)
    return "${t.date}T${t.hour.pad()}:${t.minute.pad()}:00+08:00"
}

private fun shortTime(epochSeconds: Long): String {
    val t = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(AGENT_TIME_ZONE)
    return "${t.date} ${t.hour.pad()}:${t.minute.pad()}"
}

private fun clock(epochSeconds: Long): String {
    val t = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(AGENT_TIME_ZONE)
    return "${t.hour.pad()}:${t.minute.pad()}"
}

private fun Int.pad(): String = toString().padStart(2, '0')

private fun JsonObjectBuilder.putTime(key: String, epochSeconds: Long?) {
    if (epochSeconds != null) put(key, isoTime(epochSeconds))
}

private fun JsonObjectBuilder.putIfNotNull(key: String, value: String?) {
    if (value != null) put(key, value)
}

/** 学号只露首尾，足够区分账号又不完整暴露。 */
internal fun maskStudentId(id: String): String =
    if (id.length <= 4) "*".repeat(id.length) else id.take(2) + "*".repeat(id.length - 4) + id.takeLast(2)

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

private fun weekday(date: LocalDate): String = "周" + WEEKDAYS[date.dayOfWeek.ordinal]

// region status

internal fun statusJson(ready: AgentSnapshotResult.Ready): JsonElement {
    val s = ready.snapshot
    return buildJsonObject {
        put("appVersion", ready.appVersion)
        put("account", maskStudentId(s.studentId))
        put("currentWeek", s.currentWeek)
        put("academicCalendar", if (s.academicWeeksInferred) "inferred" else "cached")
        put("counts", buildJsonObject {
            put("courses", s.courses.count { !it.isCurrentSemester })
            put("exams", s.exams.size)
            put("coursePlatformHomework", s.homework.size)
            put("phyvlabActivities", s.phyVlab?.activities?.size ?: 0)
            put("citelTasks", s.citelTasks.size)
            put("physicsLabBookings", s.physicsLabs?.size ?: 0)
            put("physicsDemoBookings", s.physicsDemos?.size ?: 0)
        })
        put("sources", buildJsonObject {
            put("phyvlab", s.phyVlabEnabled)
            put("citel", s.citelEnabled)
            put("physicsLab", s.physicsLabs != null)
            put("physicsDemo", s.physicsDemos != null)
            put("grades", ready.gradesAllowed)
        })
        s.phyVlab?.savedAtEpochMillis?.takeIf { it > 0 }?.let { putTime("phyvlabSavedAt", it / 1000) }
        putIfNotNull("citelLastSync", s.citelLastSync)
    }
}

internal fun statusMarkdown(ready: AgentSnapshotResult.Ready): String {
    val s = ready.snapshot
    return buildString {
        appendLine("# 交大自由行 · 缓存状态")
        appendLine()
        appendLine("- 版本：${ready.appVersion}")
        appendLine("- 账号：${maskStudentId(s.studentId)}")
        appendLine("- 当前教学周：${s.currentWeek.takeIf { it > 0 } ?: "未知"}" +
            if (s.academicWeeksInferred) "（校历未缓存，日期按当前周推算）" else "")
        appendLine("- 课程 ${s.courses.count { !it.isCurrentSemester }} 条，考试 ${s.exams.size} 条，课程平台作业 ${s.homework.size} 条")
        appendLine("- 物理在线：${if (s.phyVlabEnabled) "${s.phyVlab?.activities?.size ?: 0} 个活动" else "未启用"}")
        appendLine("- CITEL：${if (s.citelEnabled) "${s.citelTasks.size} 个任务" else "未启用"}")
        appendLine("- 物理实验约课：${s.physicsLabs?.let { "${it.size} 个" } ?: "未启用"}")
        appendLine("- 成绩：${if (ready.gradesAllowed) "已允许读取" else "未允许 Agent 读取"}")
    }
}

// endregion

// region calendar

internal fun calendarJson(items: List<AgentCalendarItem>, range: Pair<LocalDate, LocalDate>, snapshot: AgentSnapshot): JsonElement =
    buildJsonObject {
        put("from", range.first.toString())
        put("to", range.second.toString())
        put("academicCalendar", if (snapshot.academicWeeksInferred) "inferred" else "cached")
        put("items", JsonArray(items.map { item ->
            buildJsonObject {
                put("kind", item.kind.wire)
                put("title", item.title)
                putTime("start", item.start)
                putTime("end", item.end)
                putIfNotNull("location", item.location)
                putIfNotNull("teacher", item.teacher)
                putIfNotNull("courseName", item.courseName)
                putIfNotNull("source", item.source)
                item.done?.let { put("done", it) }
                item.week?.let { put("week", it) }
            }
        }))
    }

internal fun calendarMarkdown(items: List<AgentCalendarItem>, range: Pair<LocalDate, LocalDate>): String = buildString {
    appendLine("# 日程 ${range.first} ~ ${range.second}")
    if (items.isEmpty()) {
        appendLine()
        appendLine("（没有安排）")
        return@buildString
    }
    items.groupBy { Instant.fromEpochSeconds(it.start).toLocalDateTime(AGENT_TIME_ZONE).date }.forEach { (date, dayItems) ->
        appendLine()
        appendLine("## $date ${weekday(date)}")
        dayItems.forEach { item ->
            val time = item.end?.let { "${clock(item.start)}–${clock(it)}" } ?: clock(item.start)
            val label = when (item.kind) {
                AgentCalendarKind.CLASS -> "课程"
                AgentCalendarKind.LAB -> "实验"
                AgentCalendarKind.EXAM -> "考试"
                AgentCalendarKind.ASSIGNMENT_OPEN -> "作业开放"
                AgentCalendarKind.ASSIGNMENT_DISCOUNT -> "开始折扣"
                AgentCalendarKind.ASSIGNMENT_DUE -> "作业截止"
            }
            val detail = listOfNotNull(item.location, item.teacher, item.courseName,
                item.done?.let { if (it) "已提交" else "未提交" }).joinToString(" · ")
            appendLine("- $time [$label] ${item.title}" + if (detail.isNotEmpty()) " — $detail" else "")
        }
    }
}

// endregion

// region homework

internal fun homeworkJson(items: List<AgentHomeworkItem>, snapshot: AgentSnapshot): JsonElement = buildJsonObject {
    put("physicsLabBookingsAvailable", snapshot.physicsLabs != null)
    put("items", JsonArray(items.map { item ->
        val a = item.assignment
        buildJsonObject {
            put("source", a.source.wire)
            put("courseName", a.courseName)
            put("title", item.title)
            put("submitted", a.submitted)
            putTime("openTime", item.openTime)
            putTime("dueTime", a.dueTime)
            a.citel?.let { citel ->
                put("programming", citel.programming)
                putTime("discountTime", citel.discountTime?.takeIf { it != a.dueTime })
                citel.discount?.let { put("discount", it) }
                citel.allowLate?.let { put("allowLate", it) }
                putIfNotNull("grade", citel.grade)
                put("url", citel.url)
            }
            a.homework?.let { hw ->
                put("type", hw.typeLabel())
                put("submittedCount", hw.submitCount)
                put("totalCount", hw.allCount)
                if (hw.score.isNotBlank()) put("score", hw.score)
                if (hw.content.isNotBlank()) put("content", hw.content)
            }
            a.physical?.let { put("url", it.activityUrl) }
            if (item.enrollment != PhyVlabEnrollment.NOT_APPLICABLE) {
                put("enrollment", item.enrollment.name.lowercase())
                putIfNotNull("matchedBooking", item.matchedBooking)
            }
        }
    }))
}

internal fun homeworkMarkdown(items: List<AgentHomeworkItem>): String = buildString {
    appendLine("# 作业（${items.size} 项）")
    items.forEach { item ->
        val a = item.assignment
        appendLine()
        appendLine("## ${item.title}")
        appendLine("- 来源：${a.source.label} · ${a.courseName}")
        appendLine("- 状态：${if (a.submitted) "已提交" else "未提交"}")
        item.openTime?.let { appendLine("- 开放：${shortTime(it)}") }
        a.dueTime?.let { appendLine("- 截止：${shortTime(it)}") }
        when (item.enrollment) {
            PhyVlabEnrollment.BOOKED -> appendLine("- 已约实验：${item.matchedBooking}")
            PhyVlabEnrollment.UNVERIFIED -> appendLine("- 物理实验：没有约课缓存，无法确认是否需要做")
            else -> Unit
        }
    }
}

// endregion

// region exams / grades / citel

internal fun examsJson(snapshot: AgentSnapshot): JsonElement = JsonArray(snapshot.exams.map { exam ->
    val parsed = parseExamCalendarTime(exam.examTimeAndPlace)
    buildJsonObject {
        put("courseName", exam.courseName)
        put("type", exam.examType)
        put("status", exam.examStatus)
        put("timeAndPlace", exam.examTimeAndPlace)
        if (parsed != null) {
            put("date", parsed.date.toString())
            put("start", "${parsed.startHour.pad()}:${parsed.startMinute.pad()}")
            put("end", "${parsed.endHour.pad()}:${parsed.endMinute.pad()}")
            put("location", parsed.location)
        }
    }
})

internal fun examsMarkdown(snapshot: AgentSnapshot): String = buildString {
    appendLine("# 考试（${snapshot.exams.size} 项）")
    snapshot.exams.forEach { exam ->
        appendLine("- ${exam.courseName}（${exam.examType}）：${exam.examTimeAndPlace} · ${exam.examStatus}")
    }
}

internal fun gradesJson(snapshot: AgentSnapshot): JsonElement = JsonArray(snapshot.grades.map { grade ->
    buildJsonObject {
        put("courseName", grade.courseName)
        put("teacher", grade.courseTeacher)
        put("score", grade.courseScore)
        put("credits", grade.courseCredits)
        put("year", grade.courseYear)
        put("semester", grade.semester)
    }
})

internal fun gradesMarkdown(snapshot: AgentSnapshot): String = buildString {
    appendLine("# 成绩（${snapshot.grades.size} 门）")
    snapshot.grades.forEach { appendLine("- ${it.courseYear} ${it.semester} ${it.courseName}：${it.courseScore}（${it.courseCredits} 学分）") }
}

internal fun citelJson(snapshot: AgentSnapshot): JsonElement = buildJsonObject {
    put("enabled", snapshot.citelEnabled)
    putIfNotNull("lastSync", snapshot.citelLastSync)
    put("tasks", buildJsonArray {
        snapshot.citelTasks.sortedWith(compareBy({ it.dueTime ?: Long.MAX_VALUE }, { it.title })).forEach { task ->
            add(buildJsonObject {
                put("id", task.id)
                put("courseName", task.courseName)
                put("title", task.title)
                put("programming", task.programming)
                put("submitted", task.completed)
                putIfNotNull("status", task.status.ifBlank { null })
                putIfNotNull("grade", task.grade)
                putTime("openTime", task.openTime)
                putTime("discountTime", task.discountTime)
                putTime("dueTime", task.dueTime)
                task.discount?.let { put("discount", it) }
                task.allowLate?.let { put("allowLate", it) }
                put("url", task.url)
            })
        }
    })
}

internal fun citelMarkdown(snapshot: AgentSnapshot): String = buildString {
    if (!snapshot.citelEnabled) {
        appendLine("CITEL 未在 App 中启用。")
        return@buildString
    }
    appendLine("# CITEL 任务（${snapshot.citelTasks.size} 个）")
    snapshot.citelTasks.sortedWith(compareBy({ it.dueTime ?: Long.MAX_VALUE }, { it.title })).forEach { task ->
        val due = task.dueTime?.let { "截止 ${shortTime(it)}" } ?: "无截止时间"
        appendLine("- [${task.id}] ${task.courseName} · ${task.title}（${if (task.programming) "编程" else "书面"}，$due，${if (task.completed) "已提交" else "未提交"}）")
    }
}

// endregion

package team.bjtuss.bjtuselfservice.shared.data.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeKind
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeRecorder
import team.bjtuss.bjtuselfservice.shared.domain.change.detectDataChanges
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.course.displayCoursePlace
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeDomain
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeFeedSnapshot
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeField
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeRecord
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabActivity

private const val HOME_CHANGE_FEED_KEY = "home_change_feed_v1"
private const val MAX_RECORDS = 100

interface HomeChangeFeedRepository {
    val records: StateFlow<List<HomeChangeRecord>>
    suspend fun acceptRefresh(
        domain: HomeChangeDomain,
        hadPreviousItems: Boolean,
        changes: List<HomeChangeRecord>,
    ): Boolean
    suspend fun clear(domain: HomeChangeDomain? = null): Boolean
}

class CacheStoreHomeChangeFeedRepository(
    accountScope: String,
    private val cacheStore: CacheStore,
) : HomeChangeFeedRepository {
    private val accountScope = accountScope.trim().also {
        require(it.isNotEmpty()) { "accountScope cannot be blank" }
    }
    private val mutex = Mutex()
    private var snapshot = runCatching {
        cacheStore.metadata(this.accountScope, HOME_CHANGE_FEED_KEY)
            ?.let(::decodeHomeChangeFeed)
    }.getOrNull() ?: HomeChangeFeedSnapshot()
    private val mutableRecords = MutableStateFlow(snapshot.records)
    override val records: StateFlow<List<HomeChangeRecord>> = mutableRecords.asStateFlow()

    override suspend fun acceptRefresh(
        domain: HomeChangeDomain,
        hadPreviousItems: Boolean,
        changes: List<HomeChangeRecord>,
    ): Boolean = mutex.withLock {
        val alreadyBaselined = domain in snapshot.baselineDomains
        val shouldAppend = alreadyBaselined || hadPreviousItems
        val combined = if (shouldAppend) {
            (snapshot.records + changes).distinctBy(HomeChangeRecord::stableKey).takeLast(MAX_RECORDS)
        } else {
            snapshot.records
        }
        persist(snapshot.copy(baselineDomains = snapshot.baselineDomains + domain, records = combined))
    }

    override suspend fun clear(domain: HomeChangeDomain?): Boolean = mutex.withLock {
        val remaining = if (domain == null) emptyList() else snapshot.records.filterNot { it.domain == domain }
        persist(snapshot.copy(records = remaining))
    }

    private fun persist(updated: HomeChangeFeedSnapshot): Boolean = try {
        cacheStore.putMetadata(accountScope, HOME_CHANGE_FEED_KEY, encodeHomeChangeFeed(updated))
        snapshot = updated
        mutableRecords.value = updated.records
        true
    } catch (_: Exception) {
        false
    }
}

fun gradeChangeRecorder(feed: HomeChangeFeedRepository): DataChangeRecorder<Grade> =
    DataChangeRecorder { before, after ->
        feed.acceptRefresh(
            domain = HomeChangeDomain.GRADES,
            hadPreviousItems = before.isNotEmpty(),
            changes = gradeChangeRecords(before, after),
        )
    }

fun gradeChangeRecords(before: List<Grade>, after: List<Grade>): List<HomeChangeRecord> =
    detectDataChanges(
        before = before,
        after = after,
        identity = { listOf(it.courseName, it.courseTeacher, it.courseYear, it.semester) },
        equivalent = ::gradesSemanticallyEqual,
    ).map { change ->
        val displayItem = change.after ?: change.before ?: error("change has no item")
        HomeChangeRecord(
            domain = HomeChangeDomain.GRADES,
            kind = change.kind,
            title = displayItem.courseName.ifBlank { HomeChangeDomain.GRADES.title },
            beforeDetail = change.before?.let(::gradeChangeDetail).orEmpty(),
            afterDetail = change.after?.let(::gradeChangeDetail).orEmpty(),
            fields = gradeChangeFields(change.before, change.after),
        )
    }.filterNot { record ->
        record.kind == DataChangeKind.MODIFIED && record.beforeDetail == record.afterDetail
    }

internal fun gradeChangeDetail(grade: Grade): String {
    val base = "${grade.courseScore} · ${grade.courseCredits} 学分 · ${grade.semester}"
    val components = gradeComponentScores(grade.detail)
    if (components.isEmpty()) return base
    return base + " · " + components.entries.joinToString(" ") { "${it.key}${it.value}" }
}

private val gradeComponentLabels = listOf(
    "平时成绩",
    "期中成绩",
    "期末成绩",
    "实验成绩",
    "最终成绩",
    "总评成绩",
)

/** 从详情抽出分项数字/等级；空白、冒号、换行压掉后再比，避免 HTML 抖动。 */
internal fun gradeComponentScores(detail: String): Map<String, String> {
    if (detail.isBlank()) return emptyMap()
    val compact = detail.replace(Regex("[\\s:：]+"), "")
    val valuePattern = Regex("^(?:[0-9]+(?:\\.[0-9]+)?|[A-F][+-]?)")
    return buildMap {
        for (label in gradeComponentLabels) {
            val index = compact.indexOf(label)
            if (index < 0) continue
            val after = compact.substring(index + label.length)
            val value = valuePattern.find(after)?.value ?: continue
            put(label, value)
        }
    }
}

/**
 * 成绩信息流等价：忽略本地 id 与备注原文。
 * 总分/学分之外还比规范化后的平时/期中/期末/实验/最终/总评。
 */
internal fun gradesSemanticallyEqual(old: Grade, new: Grade): Boolean =
    old.courseName == new.courseName &&
        old.courseTeacher == new.courseTeacher &&
        old.courseScore == new.courseScore &&
        old.courseCredits == new.courseCredits &&
        old.courseYear == new.courseYear &&
        old.semester == new.semester &&
        gradeComponentScores(old.detail) == gradeComponentScores(new.detail)

internal fun gradeChangeFields(before: Grade?, after: Grade?): List<HomeChangeField> = buildList {
    add(HomeChangeField("成绩", before?.courseScore.orEmpty(), after?.courseScore.orEmpty()))
    add(HomeChangeField("学分", before?.courseCredits.orEmpty(), after?.courseCredits.orEmpty()))
    add(HomeChangeField("教师", before?.courseTeacher.orEmpty(), after?.courseTeacher.orEmpty()))
    add(HomeChangeField("学期", before?.semester.orEmpty(), after?.semester.orEmpty()))
    val labels = gradeComponentLabels.filter { label ->
        gradeComponentScores(before?.detail.orEmpty()).containsKey(label) ||
            gradeComponentScores(after?.detail.orEmpty()).containsKey(label)
    }
    for (label in labels) {
        add(
            HomeChangeField(
                label,
                gradeComponentScores(before?.detail.orEmpty())[label].orEmpty(),
                gradeComponentScores(after?.detail.orEmpty())[label].orEmpty(),
            ),
        )
    }
}

fun courseChangeRecorder(feed: HomeChangeFeedRepository): DataChangeRecorder<Course> =
    changeRecorder(
        feed = feed,
        domain = HomeChangeDomain.COURSES,
        identity = { listOf(it.courseId, it.isCurrentSemester, it.courseTime, it.courseLocationIndex) },
        equivalent = { old, new -> old.copy(id = 0) == new.copy(id = 0) },
        title = Course::courseName,
        detail = { "${it.courseTeacher} · ${it.courseTime} · ${displayCoursePlace(it.coursePlace)}" },
        fields = { before, after ->
            listOf(
                HomeChangeField("教师", before?.courseTeacher.orEmpty(), after?.courseTeacher.orEmpty()),
                HomeChangeField("时间", before?.courseTime.orEmpty(), after?.courseTime.orEmpty()),
                HomeChangeField("地点", before?.let { displayCoursePlace(it.coursePlace) }.orEmpty(), after?.let { displayCoursePlace(it.coursePlace) }.orEmpty()),
            )
        },
    )

fun examChangeRecorder(feed: HomeChangeFeedRepository): DataChangeRecorder<ExamSchedule> =
    changeRecorder(
        feed = feed,
        domain = HomeChangeDomain.EXAMS,
        identity = { listOf(it.examType, it.courseName) },
        equivalent = { old, new -> old.copy(id = 0) == new.copy(id = 0) },
        title = ExamSchedule::courseName,
        detail = { "${it.examType} · ${it.examTimeAndPlace} · ${it.examStatus}" },
        fields = { before, after ->
            listOf(
                HomeChangeField("类型", before?.examType.orEmpty(), after?.examType.orEmpty()),
                HomeChangeField("时间地点", before?.examTimeAndPlace.orEmpty(), after?.examTimeAndPlace.orEmpty()),
                HomeChangeField("状态", before?.examStatus.orEmpty(), after?.examStatus.orEmpty()),
            )
        },
    )

fun homeworkChangeRecorder(feed: HomeChangeFeedRepository): DataChangeRecorder<Homework> =
    changeRecorder(
        feed = feed,
        domain = HomeChangeDomain.HOMEWORK,
        identity = { listOf(it.courseName, it.upId) },
        equivalent = { old, new ->
            old.copy(id = 0, idSnId = null) == new.copy(id = 0, idSnId = null)
        },
        title = Homework::title,
        detail = { "${it.courseName} · ${it.endTime} · ${it.subStatus}" },
        fields = { before, after ->
            listOf(
                HomeChangeField("课程", before?.courseName.orEmpty(), after?.courseName.orEmpty()),
                HomeChangeField("分数", homeworkScoreLabel(before?.score), homeworkScoreLabel(after?.score)),
                HomeChangeField("状态", before?.subStatus.orEmpty(), after?.subStatus.orEmpty()),
                HomeChangeField("截止时间", before?.endTime.orEmpty(), after?.endTime.orEmpty()),
            )
        },
    )

fun phyvlabChangeRecorder(feed: HomeChangeFeedRepository): DataChangeRecorder<PhyVlabActivity> =
    changeRecorder(
        feed = feed,
        domain = HomeChangeDomain.PHYVLAB,
        identity = { listOf(it.courseId, it.id) },
        equivalent = { old, new -> old == new },
        title = PhyVlabActivity::title,
        detail = { activity ->
            buildString {
                append(activity.courseName)
                activity.dueText?.let { append(" · 截止 ").append(it) }
                append(" · ").append(if (activity.completed) "已完成" else "未完成")
            }
        },
        fields = { before, after ->
            listOf(
                HomeChangeField("课程", before?.courseName.orEmpty(), after?.courseName.orEmpty()),
                HomeChangeField("截止时间", before?.dueText.orEmpty(), after?.dueText.orEmpty()),
                HomeChangeField("状态", before?.let { if (it.completed) "已完成" else "未完成" }.orEmpty(), after?.let { if (it.completed) "已完成" else "未完成" }.orEmpty()),
            )
        },
    )

private fun homeworkScoreLabel(score: String?): String =
    score?.trim()?.takeIf { it.isNotEmpty() } ?: "未公布成绩"

private fun <T, K> changeRecorder(
    feed: HomeChangeFeedRepository,
    domain: HomeChangeDomain,
    identity: (T) -> K,
    equivalent: (T, T) -> Boolean,
    title: (T) -> String,
    detail: (T) -> String,
    fields: (T?, T?) -> List<HomeChangeField>,
): DataChangeRecorder<T> = DataChangeRecorder { before, after ->
    val records = detectDataChanges(before, after, identity, equivalent).map { change ->
        val displayItem = change.after ?: change.before ?: error("change has no item")
        HomeChangeRecord(
            domain = domain,
            kind = change.kind,
            title = title(displayItem).ifBlank { domain.title },
            beforeDetail = change.before?.let(detail).orEmpty(),
            afterDetail = change.after?.let(detail).orEmpty(),
            fields = fields(change.before, change.after),
        )
    }
        // 二次保险：展示文案完全一致的「修改」不进信息流（避免解析抖动误报）。
        .filterNot { record ->
            record.kind == DataChangeKind.MODIFIED &&
                record.beforeDetail == record.afterDetail
        }
    feed.acceptRefresh(domain, before.isNotEmpty(), records)
}

internal fun encodeHomeChangeFeed(snapshot: HomeChangeFeedSnapshot): String = buildString {
    writePart("2")
    writePart(snapshot.baselineDomains.joinToString(",", transform = HomeChangeDomain::name))
    writePart(snapshot.records.size.toString())
    snapshot.records.forEach { record ->
        writePart(record.domain.name)
        writePart(record.kind.name)
        writePart(record.title)
        writePart(record.beforeDetail)
        writePart(record.afterDetail)
        writePart(record.fields.size.toString())
        record.fields.forEach { field ->
            writePart(field.label)
            writePart(field.before)
            writePart(field.after)
        }
    }
}

internal fun decodeHomeChangeFeed(encoded: String): HomeChangeFeedSnapshot? = try {
    val reader = LengthPrefixedReader(encoded)
    val version = reader.read()
    if (version != "1" && version != "2") return null
    val baselines = reader.read().takeIf(String::isNotEmpty)?.split(',').orEmpty()
        .map { HomeChangeDomain.valueOf(it) }.toSet()
    val count = reader.read().toInt().takeIf { it in 0..MAX_RECORDS } ?: return null
    val records = buildList {
        repeat(count) {
            val domain = HomeChangeDomain.valueOf(reader.read())
            val kind = DataChangeKind.valueOf(reader.read())
            val title = reader.read()
            val beforeDetail = reader.read()
            val afterDetail = reader.read()
            val fields = if (version == "2") {
                val fieldCount = reader.read().toInt().takeIf { it in 0..32 } ?: return null
                List(fieldCount) {
                    HomeChangeField(reader.read(), reader.read(), reader.read())
                }
            } else {
                emptyList()
            }
            add(
                HomeChangeRecord(
                    domain = domain,
                    kind = kind,
                    title = title,
                    beforeDetail = beforeDetail,
                    afterDetail = afterDetail,
                    fields = fields,
                ),
            )
        }
    }
    if (!reader.finished) return null
    HomeChangeFeedSnapshot(baselines, records)
} catch (_: Exception) {
    null
}

private fun StringBuilder.writePart(value: String) {
    append(value.length).append(':').append(value)
}

private class LengthPrefixedReader(private val value: String) {
    private var index = 0
    val finished: Boolean get() = index == value.length

    fun read(): String {
        val colon = value.indexOf(':', index).takeIf { it >= index } ?: error("missing length")
        val length = value.substring(index, colon).toInt().takeIf { it >= 0 } ?: error("bad length")
        val start = colon + 1
        val end = start + length
        require(end <= value.length)
        index = end
        return value.substring(start, end)
    }
}

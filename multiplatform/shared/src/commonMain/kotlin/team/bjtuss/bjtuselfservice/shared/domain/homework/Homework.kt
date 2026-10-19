package team.bjtuss.bjtuselfservice.shared.domain.homework

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class Homework(
    val id: Int = 0,
    val upId: Int,
    val idSnId: Int?,
    val score: String,
    val userId: Int,
    val courseId: Int,
    val courseName: String,
    val title: String,
    val content: String,
    val createDate: String,
    val endTime: String,
    val openDate: String,
    val status: Int,
    val submitCount: Int,
    val allCount: Int,
    val subStatus: String,
    val scoreId: Int,
    val homeworkType: Int,
)

data class HomeworkAttachment(
    val id: Int,
    val fileName: String,
    val sizeBytes: Long,
    /**
     * 服务端附件路径仅用于组装受限下载请求；UI、日志和异常不得直接展示它。
     */
    val sourcePath: String,
)

private val attachmentExtensionPattern = Regex("[A-Za-z0-9]{1,8}")

/**
 * 课程平台 `picList.file_name` 常常不带扩展名（老师上传 `fig2.zip` 后只回 `fig2`），
 * 真实扩展名只在存储路径 `url` 里。与原 Android 版一致：从路径补扩展名，已带同一扩展名时不重复追加。
 */
fun homeworkAttachmentFileName(fileName: String, sourcePath: String): String {
    val extension = sourcePath.substringBefore('?').substringBefore('#')
        .substringAfterLast('/').substringAfterLast('\\')
        .substringAfterLast('.', "")
        .takeIf(attachmentExtensionPattern::matches)
        ?: return fileName
    return if (fileName.endsWith(".$extension", ignoreCase = true)) fileName else "$fileName.$extension"
}

data class HomeworkDetail(
    val content: String,
    val attachments: List<HomeworkAttachment>,
)

data class SubmittedHomeworkAttachment(
    val id: String,
    val fileName: String,
    /** 服务端路径只允许回传给受限下载端点，不得展示或持久化。 */
    val sourcePath: String,
)

class HomeworkFileContent(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
) {
    init {
        require(fileName.isNotBlank())
        require(contentType.isNotBlank())
    }

    override fun equals(other: Any?): Boolean = other is HomeworkFileContent &&
        fileName == other.fileName &&
        contentType == other.contentType &&
        bytes.contentEquals(other.bytes)

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    override fun toString(): String =
        "HomeworkFileContent(fileName=<redacted>, contentType=$contentType, bytes=${bytes.size})"
}

fun Homework.stableKey(): String = "$courseName\u0000$upId"

fun Homework.typeLabel(): String = when (homeworkType) {
    0 -> "平时作业"
    1 -> "课程设计"
    2 -> "实验报告"
    else -> "其他任务"
}

enum class HomeworkSortOrder {
    ORIGINAL,
    ASCENDING,
    DESCENDING,
}

/**
 * 作业筛选面板的上次状态：每次打开面板都要恢复成这样。
 * 普通 UI 偏好，不含账号、Cookie 或会话信息，可以进普通缓存。
 */
data class HomeworkFilterPreferences(
    val selectedCourses: Set<String> = emptySet(),
    val hideExpired: Boolean = false,
    val hideSubmitted: Boolean = false,
    val sortOrder: HomeworkSortOrder = HomeworkSortOrder.ORIGINAL,
)

/** 筛选偏好读写口；跨进程/重启 persistence 由调用方（缓存）提供，null 表示不持久化。 */
interface HomeworkFilterPreferencesStore {
    fun load(): HomeworkFilterPreferences
    fun save(preferences: HomeworkFilterPreferences)
}

private const val HOMEWORK_FILTER_MAX_COURSES = 200
private const val HOMEWORK_FILTER_MAX_COURSE_LENGTH = 128

private val homeworkFilterJson = Json { ignoreUnknownKeys = true }

/** 课程名可能含标点符号，用 JSON 数组存取；非法输入一律按空集处理。 */
internal fun encodeHomeworkFilterCourses(courses: Set<String>): String =
    homeworkFilterJson.encodeToString(
        ListSerializer(String.serializer()),
        courses.filter { it.isNotBlank() && it.length <= HOMEWORK_FILTER_MAX_COURSE_LENGTH }
            .take(HOMEWORK_FILTER_MAX_COURSES),
    )

internal fun decodeHomeworkFilterCourses(raw: String?): Set<String> {
    if (raw.isNullOrBlank()) return emptySet()
    return runCatching {
        homeworkFilterJson.decodeFromString(ListSerializer(String.serializer()), raw)
    }.getOrDefault(emptyList())
        .filter { it.isNotBlank() && it.length <= HOMEWORK_FILTER_MAX_COURSE_LENGTH }
        .take(HOMEWORK_FILTER_MAX_COURSES)
        .toSet()
}

internal fun decodeHomeworkSortOrder(raw: String?): HomeworkSortOrder =
    runCatching { HomeworkSortOrder.valueOf(raw?.trim().orEmpty()) }
        .getOrDefault(HomeworkSortOrder.ORIGINAL)

/** 解析服务端固定格式 yyyy-MM-dd HH:mm，不读取系统时钟。 */
fun parseSchoolLocalDateTime(value: String): LocalDateTime? = try {
    LocalDateTime.parse(value.replace(' ', 'T'))
} catch (_: IllegalArgumentException) {
    null
}

/** 已提交口径与详情页/提交链路一致：服务端状态或已存在提交记录均算已提交。 */
fun isHomeworkSubmitted(homework: Homework): Boolean =
    homework.subStatus == "已提交" || homework.idSnId != null

fun filterHomework(
    homework: List<Homework>,
    selectedCourses: Set<String>,
    hideExpired: Boolean,
    now: LocalDateTime,
    hideSubmitted: Boolean = false,
): List<Homework> = homework.filter { item ->
    val deadline = parseSchoolLocalDateTime(item.endTime)
    val hasValidDate = deadline?.let { it > now } ?: true
    val dateMatches = !hideExpired || hasValidDate
    val courseMatches = selectedCourses.isEmpty() || item.courseName in selectedCourses
    val submittedMatches = !hideSubmitted || !isHomeworkSubmitted(item)
    dateMatches && courseMatches && submittedMatches
}

/**
 * 截止时间排序。
 * - [HomeworkSortOrder.ASCENDING]：由近到远（截止更早的在前）
 * - [HomeworkSortOrder.DESCENDING]：由远到近（截止更晚的在前）
 *
 * 注意：历史上 ASC/DESC 与文案曾对调过，以本注释与 UI 文案为准。
 */
fun sortHomework(
    homework: List<Homework>,
    order: HomeworkSortOrder,
): List<Homework> = when (order) {
    HomeworkSortOrder.ORIGINAL -> homework
    // 由近到远：更早截止在前；无日期沉底。
    HomeworkSortOrder.ASCENDING -> homework.sortedWith(
        compareBy<Homework> { parseSchoolLocalDateTime(it.endTime) == null }
            .thenBy { parseSchoolLocalDateTime(it.endTime) },
    )
    // 由远到近：更晚截止在前；无日期沉底。
    HomeworkSortOrder.DESCENDING -> homework.sortedWith(
        compareBy<Homework> { parseSchoolLocalDateTime(it.endTime) == null }
            .thenByDescending { parseSchoolLocalDateTime(it.endTime) },
    )
}

/**
 * 保留原版“0..48 个完整小时且未提交”的口径；时区由调用层显式注入，便于测试。
 */
fun isHomeworkDueSoon(
    homework: Homework,
    now: LocalDateTime,
    timeZone: TimeZone,
): Boolean {
    if (homework.subStatus == "已提交") return false
    val deadline = parseSchoolLocalDateTime(homework.endTime) ?: return false
    val hours = (deadline.toInstant(timeZone) - now.toInstant(timeZone)).inWholeHours
    return hours in 0..48
}

fun dueSoonHomeworkCount(
    homework: List<Homework>,
    now: LocalDateTime,
    timeZone: TimeZone,
): Int = homework.count { isHomeworkDueSoon(it, now, timeZone) }

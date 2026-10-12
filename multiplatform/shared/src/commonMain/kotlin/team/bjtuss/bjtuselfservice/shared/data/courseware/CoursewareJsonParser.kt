package team.bjtuss.bjtuselfservice.shared.data.courseware

import team.bjtuss.bjtuselfservice.shared.data.homework.StrictJsonValue
import team.bjtuss.bjtuselfservice.shared.data.homework.arrayOrBlank
import team.bjtuss.bjtuselfservice.shared.data.homework.asObject
import team.bjtuss.bjtuselfservice.shared.data.homework.boolean
import team.bjtuss.bjtuselfservice.shared.data.homework.hasSuccessStatus
import team.bjtuss.bjtuselfservice.shared.data.homework.int
import team.bjtuss.bjtuselfservice.shared.data.homework.parseStrictJsonObject
import team.bjtuss.bjtuselfservice.shared.data.homework.string
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNodeKind

sealed interface CoursewareJsonParseResult<out T> {
    data class Success<T>(val value: T) : CoursewareJsonParseResult<T>
    data class Failure(val field: String) : CoursewareJsonParseResult<Nothing>
}

data class CoursewareDownloadTicket(
    val url: String,
    val downloadType: String,
)

fun parseCoursewareChildren(
    body: String,
    courseId: Int,
): CoursewareJsonParseResult<List<CoursewareNode>> = parseObject(body) { root ->
    // STATUS="2" 表示该课程当前没有课件资源（resList/bagList 均为空串），是合法空
    // 结果而非错误，与作业列表的“没有数据”容错对齐（原 Android 默认值容忍）。
    if (root.string("STATUS") == "2") return@parseObject CoursewareJsonParseResult.Success(emptyList())
    if (!root.hasSuccessStatus()) return@parseObject CoursewareJsonParseResult.Failure("STATUS")
    val folders = root.arrayOrBlank("bagList")
        ?: return@parseObject CoursewareJsonParseResult.Failure("bagList")
    val resources = root.arrayOrBlank("resList")
        ?: return@parseObject CoursewareJsonParseResult.Failure("resList")
    val nodes = mutableListOf<CoursewareNode>()
    for ((index, element) in folders.withIndex()) {
        val item = element.asObject()
            ?: return@parseObject CoursewareJsonParseResult.Failure("bagList[$index]")
        val id = item.int("id")
            ?: return@parseObject CoursewareJsonParseResult.Failure("bagList[$index].id")
        val name = item.string("bag_name").orEmpty()
        if (name.isBlank()) return@parseObject CoursewareJsonParseResult.Failure("bagList[$index].bag_name")
        nodes += CoursewareNode(
            id = id,
            courseId = courseId,
            name = name,
            kind = CoursewareNodeKind.FOLDER,
        )
    }
    for ((index, element) in resources.withIndex()) {
        val item = element.asObject()
            ?: return@parseObject CoursewareJsonParseResult.Failure("resList[$index]")
        val id = item.int("resId")
            ?: return@parseObject CoursewareJsonParseResult.Failure("resList[$index].resId")
        val name = item.string("rpName").orEmpty()
        val rpId = item.string("rpId").orEmpty()
        if (name.isBlank() || rpId.isBlank()) {
            return@parseObject CoursewareJsonParseResult.Failure("resList[$index].identity")
        }
        nodes += CoursewareNode(
            id = id,
            courseId = courseId,
            name = name,
            kind = CoursewareNodeKind.RESOURCE,
            rpId = rpId,
            extension = item.string("extName").orEmpty(),
            size = item.string("rpSize").orEmpty(),
            teacherName = item.string("teacherName").orEmpty(),
            inputTime = item.string("inputTime").orEmpty(),
            downloadCount = item.int("downloadNum") ?: 0,
            // 字段缺失时沿用旧行为（允许下载）；只有明确给出非 "2" 才视为未开放。
            downloadAllowed = item.string("stu_download")?.let { it.trim() == "2" } ?: true,
        )
    }
    CoursewareJsonParseResult.Success(nodes)
}

fun parseCoursewareDownloadTicket(body: String): CoursewareJsonParseResult<CoursewareDownloadTicket> =
    parseObject(body) { root ->
        val accepted = root.boolean("flag") ?: false
        val url = root.string("rpUrl").orEmpty()
        if (!accepted || url.isBlank()) {
            CoursewareJsonParseResult.Failure("downloadTicket")
        } else {
            CoursewareJsonParseResult.Success(
                CoursewareDownloadTicket(
                    url = url,
                    downloadType = root.string("download_type").orEmpty(),
                ),
            )
        }
    }

private inline fun <T> parseObject(
    body: String,
    transform: (Map<String, StrictJsonValue>) -> CoursewareJsonParseResult<T>,
): CoursewareJsonParseResult<T> = try {
    val root = parseStrictJsonObject(body)
        ?: return CoursewareJsonParseResult.Failure("root")
    transform(root)
} catch (_: Exception) {
    CoursewareJsonParseResult.Failure("json")
}

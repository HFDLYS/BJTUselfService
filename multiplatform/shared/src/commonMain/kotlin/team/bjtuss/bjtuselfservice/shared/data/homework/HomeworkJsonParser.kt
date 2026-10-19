package team.bjtuss.bjtuselfservice.shared.data.homework

import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkAttachment
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkDetail
import team.bjtuss.bjtuselfservice.shared.domain.homework.homeworkAttachmentFileName

data class SmartCourse(
    val id: Int,
    val name: String,
    val teacherId: Int?,
    val courseNumber: String = "",
    val groupId: String = "",
    val semesterCode: String = "",
)

data class HomeworkUploadReceipt(
    val fileNameNoExt: String,
    val fileExtName: String,
    val fileSize: String,
    val visitName: String,
)

sealed interface HomeworkJsonParseResult<out T> {
    data class Success<T>(val value: T) : HomeworkJsonParseResult<T>
    data class Failure(val field: String) : HomeworkJsonParseResult<Nothing>
}

fun parseSmartSessionId(body: String): HomeworkJsonParseResult<String> = parseObject(body) { root ->
    val sessionId = root.string("sessionId").orEmpty()
    if (sessionId.isBlank()) {
        HomeworkJsonParseResult.Failure("sessionId")
    } else {
        HomeworkJsonParseResult.Success(sessionId)
    }
}

fun parseCurrentSemesterCode(body: String): HomeworkJsonParseResult<String> = parseObject(body) { root ->
    val status = root.string("STATUS")
    if (!isSuccessOrEmptyStatus(status)) {
        return@parseObject HomeworkJsonParseResult.Failure("STATUS")
    }
    // 对齐 1.7.0：明确的空结果允许没有学期行；成功响应却缺字段不能当成空学期。
    val elements = root.arrayOrBlank("result")
    if (elements == null && status != "2") {
        return@parseObject HomeworkJsonParseResult.Failure("result")
    }
    val first = elements?.firstOrNull().asObject()
    val semester = first?.string("xqCode") ?: first?.string("xq_code").orEmpty()
    if (elements?.isNotEmpty() == true && semester.isBlank()) {
        return@parseObject HomeworkJsonParseResult.Failure("result.xqCode")
    }
    HomeworkJsonParseResult.Success(semester)
}

fun parseSmartCourses(body: String): HomeworkJsonParseResult<List<SmartCourse>> = parseObject(body) { root ->
    val status = root.string("STATUS")
    if (!isSuccessOrEmptyStatus(status)) {
        return@parseObject HomeworkJsonParseResult.Failure("STATUS")
    }
    // 明确的空结果允许没有 courseList；成功响应却缺字段必须视为异常，
    // 否则一次登录页/网关 HTML 被包装成 JSON 就会把本地作业快照清空。
    val elements = root.arrayOrBlank("courseList")
    if (elements == null && status != "2") {
        return@parseObject HomeworkJsonParseResult.Failure("courseList")
    }
    val courses = mutableListOf<SmartCourse>()
    for (element in elements.orEmpty()) {
        val item = element.asObject() ?: continue
        val id = item.int("id") ?: continue
        val name = item.string("name").orEmpty()
        if (name.isBlank()) continue
        courses += SmartCourse(
            id = id,
            name = name,
            teacherId = item.int("teacher_id"),
            courseNumber = item.string("course_num").orEmpty(),
            groupId = item.string("fz_id").orEmpty(),
            semesterCode = item.string("xq_code").orEmpty(),
        )
    }
    if (elements?.isNotEmpty() == true && courses.isEmpty()) {
        return@parseObject HomeworkJsonParseResult.Failure("courseList.identity")
    }
    HomeworkJsonParseResult.Success(courses)
}

fun parseHomeworkList(
    body: String,
    homeworkType: Int,
): HomeworkJsonParseResult<List<Homework>> = parseObject(body) { root ->
    val status = root.string("STATUS")
    if (!isSuccessOrEmptyStatus(status)) {
        return@parseObject HomeworkJsonParseResult.Failure("STATUS")
    }
    // 明确的无数据 STATUS 继续按旧客户端返回空列表；成功响应却缺字段必须保留为解析失败，
    // 交由 repository 回退缓存，不能把临时登录页/网关异常当成“确实没有作业”。
    val elements = root.arrayOrBlank("courseNoteList")
    if (elements == null && status != "2") {
        return@parseObject HomeworkJsonParseResult.Failure("courseNoteList")
    }
    val homework = mutableListOf<Homework>()
    for (element in elements.orEmpty()) {
        val item = element.asObject() ?: continue
        val upId = item.int("id") ?: continue
        val courseId = item.int("course_id") ?: continue
        val courseName = item.string("course_name").orEmpty()
        val title = item.string("title").orEmpty()
        if (courseName.isBlank() || title.isBlank()) continue
        homework += Homework(
            upId = upId,
            idSnId = item.int("snId"),
            score = item.string("stu_score").orEmpty(),
            userId = item.int("user_id") ?: 0,
            courseId = courseId,
            courseName = courseName,
            title = title,
            content = item.string("content").orEmpty(),
            createDate = item.string("create_date").orEmpty(),
            endTime = item.string("end_time").orEmpty(),
            openDate = item.string("open_date").orEmpty(),
            status = item.int("status") ?: 0,
            submitCount = item.int("submitCount") ?: 0,
            allCount = item.int("allCount") ?: 0,
            subStatus = item.string("subStatus").orEmpty(),
            scoreId = item.int("scoreId") ?: 0,
            homeworkType = homeworkType,
        )
    }
    if (elements?.isNotEmpty() == true && homework.isEmpty()) {
        return@parseObject HomeworkJsonParseResult.Failure("courseNoteList.identity")
    }
    HomeworkJsonParseResult.Success(homework)
}

fun parseHomeworkDetail(
    body: String,
    fallbackContent: String,
): HomeworkJsonParseResult<HomeworkDetail> = parseObject(body) { root ->
    if (!root.hasSuccessStatus()) return@parseObject HomeworkJsonParseResult.Failure("STATUS")
    val detail = root["homeWork"].asObject()
        ?: return@parseObject HomeworkJsonParseResult.Failure("homeWork")
    val attachments = root.arrayOrBlank("picList")
        ?: return@parseObject HomeworkJsonParseResult.Failure("picList")
    val parsedAttachments = mutableListOf<HomeworkAttachment>()
    for ((index, element) in attachments.withIndex()) {
        val item = element.asObject()
            ?: return@parseObject HomeworkJsonParseResult.Failure("picList[$index]")
        val id = item.int("id") ?: continue
        if (id == 0) continue
        val sourcePath = item.string("url").orEmpty()
        parsedAttachments += HomeworkAttachment(
            id = id,
            fileName = homeworkAttachmentFileName(
                item.string("file_name")
                    ?.replace('+', ' ')
                    ?.takeIf(String::isNotBlank)
                    ?: "附件 $id",
                sourcePath,
            ),
            sizeBytes = item.long("pic_size") ?: 0L,
            sourcePath = sourcePath,
        )
    }
    HomeworkJsonParseResult.Success(
        HomeworkDetail(
            content = detail.string("content").orEmpty().ifBlank { fallbackContent },
            attachments = parsedAttachments,
        ),
    )
}

/**
 * 学生上传端点 `homeworkUpload.shtml?noteId=` 的回执。
 *
 * 真实观察（2026-09-16，Chrome DevTools MCP 对照网页端）：
 * - 成功是 `STATUS=0` 加四个回执字段；
 * - 文件类型不合法是 `STATUS` 缺失 + 中文 `MSG`；
 * - 老师端点 `rpUpload.shtml` 会返回 `{"STATUS":"2","MSG":"学生角色无权限上传"}`。
 * 因此这里必须逐个字段校验，不能只看 HTTP 200。
 */
fun parseHomeworkUploadReceipt(body: String): HomeworkJsonParseResult<HomeworkUploadReceipt> =
    parseObject(body) { root ->
        val status = root.string("STATUS")
        val name = root.string("fileNameNoExt").orEmpty()
        val extension = root.string("fileExtName").orEmpty()
        val size = root.string("fileSize").orEmpty()
        val visitName = root.string("visitName").orEmpty()
        if (status != "0" || name.isBlank() || size.isBlank() || visitName.isBlank()) {
            HomeworkJsonParseResult.Failure(root.string("MSG")?.takeIf(String::isNotBlank) ?: "uploadReceipt")
        } else {
            HomeworkJsonParseResult.Success(
                HomeworkUploadReceipt(
                    fileNameNoExt = name,
                    fileExtName = extension,
                    fileSize = size,
                    visitName = visitName,
                ),
            )
        }
    }

/**
 * 提交端点 `sendStuHomeWorks` 的回执：成功为 `{"flag":"success"}`。
 *
 * 真实观察：`return_num={}` 时服务端返回 `{"flag":"bad"}`，且会把上一次提交记录
 * 一并清掉；空正文提交会先落库再回滚。因此必须按 `flag` 判定成功，不能沿用
 * 老 Android 1.7.0「只看 HTTP 2xx」的写法。
 */
fun parseHomeworkSubmitReceipt(body: String): HomeworkJsonParseResult<Unit> =
    parseObject(body) { root ->
        val flag = root.string("flag").orEmpty()
        val message = root.string("msg")?.takeIf(String::isNotBlank)
            ?: root.string("message")?.takeIf(String::isNotBlank)
        when {
            flag.equals("success", ignoreCase = true) -> HomeworkJsonParseResult.Success(Unit)
            else -> HomeworkJsonParseResult.Failure(message ?: "flag")
        }
    }

private inline fun <T> parseObject(
    body: String,
    transform: (Map<String, StrictJsonValue>) -> HomeworkJsonParseResult<T>,
): HomeworkJsonParseResult<T> = try {
    val root = parseStrictJsonObject(body)
        ?: return HomeworkJsonParseResult.Failure("root")
    transform(root)
} catch (_: Exception) {
    HomeworkJsonParseResult.Failure("json")
}

private fun isSuccessOrEmptyStatus(status: String?): Boolean =
    status.isNullOrBlank() || status == "0" || status == "2"

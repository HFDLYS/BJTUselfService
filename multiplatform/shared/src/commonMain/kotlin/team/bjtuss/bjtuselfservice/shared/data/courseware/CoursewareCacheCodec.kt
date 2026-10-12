package team.bjtuss.bjtuselfservice.shared.data.courseware

import team.bjtuss.bjtuselfservice.shared.util.jsonEscape

import team.bjtuss.bjtuselfservice.shared.data.homework.StrictJsonValue
import team.bjtuss.bjtuselfservice.shared.data.homework.arrayOrBlank
import team.bjtuss.bjtuselfservice.shared.data.homework.asObject
import team.bjtuss.bjtuselfservice.shared.data.homework.boolean
import team.bjtuss.bjtuselfservice.shared.data.homework.int
import team.bjtuss.bjtuselfservice.shared.data.homework.parseStrictJsonObject
import team.bjtuss.bjtuselfservice.shared.data.homework.string
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareCourse
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNodeKind
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareSnapshot

private const val COURSEWARE_CACHE_VERSION = 3
private const val MAX_COURSEWARE_CACHE_DEPTH = 32

fun encodeCoursewareSnapshot(snapshot: CoursewareSnapshot): String = buildString {
    append("{\"version\":")
    append(COURSEWARE_CACHE_VERSION)
    append(",\"courses\":[")
    snapshot.courses.forEachIndexed { index, course ->
        if (index > 0) append(',')
        appendCourse(course)
    }
    append("]}")
}

fun decodeCoursewareSnapshot(value: String): CoursewareSnapshot? = try {
    val root = parseStrictJsonObject(value) ?: return null
    val version = root.int("version") ?: return null
    if (version !in 1..COURSEWARE_CACHE_VERSION) return null
    val courseValues = root.arrayOrBlank("courses") ?: return null
    val courses = mutableListOf<CoursewareCourse>()
    val seenCourseIds = mutableSetOf<Int>()
    for (element in courseValues) {
        val course = decodeCourse(element.asObject() ?: return null, version) ?: return null
        if (!seenCourseIds.add(course.id)) return null
        courses += course
    }
    CoursewareSnapshot(courses)
} catch (_: Exception) {
    null
}

private fun decodeCourse(fields: Map<String, StrictJsonValue>, version: Int): CoursewareCourse? {
    val id = fields.int("id") ?: return null
    val name = fields.string("name").orEmpty()
    val courseNumber = fields.string("courseNumber").orEmpty()
    val groupId = fields.string("groupId").orEmpty()
    val semesterCode = fields.string("semesterCode").orEmpty()
    if (name.isBlank() || courseNumber.isBlank() || groupId.isBlank() || semesterCode.isBlank()) return null
    val childValues = fields.arrayOrBlank("children") ?: return null
    val seen = mutableSetOf<String>()
    val children = decodeNodes(childValues, courseId = id, depth = 0, seen = seen, version = version) ?: return null
    return CoursewareCourse(
        id = id,
        name = name,
        courseNumber = courseNumber,
        groupId = groupId,
        semesterCode = semesterCode,
        teacherId = fields.int("teacherId"),
        children = children,
        childrenLoaded = if (version < 3) true else fields.boolean("childrenLoaded") ?: return null,
    )
}

private fun decodeNodes(
    values: List<StrictJsonValue>,
    courseId: Int,
    depth: Int,
    seen: MutableSet<String>,
    version: Int,
): List<CoursewareNode>? {
    if (depth > MAX_COURSEWARE_CACHE_DEPTH) return null
    val nodes = mutableListOf<CoursewareNode>()
    for (element in values) {
        val fields = element.asObject() ?: return null
        val id = fields.int("id") ?: return null
        val name = fields.string("name").orEmpty()
        val kind = fields.string("kind")?.let { runCatching { CoursewareNodeKind.valueOf(it) }.getOrNull() }
            ?: return null
        if (name.isBlank()) return null
        val stableKey = "$courseId:${kind.name}:$id"
        if (!seen.add(stableKey)) return null
        val childValues = fields.arrayOrBlank("children") ?: return null
        if (kind == CoursewareNodeKind.RESOURCE && childValues.isNotEmpty()) return null
        val children = decodeNodes(childValues, courseId, depth + 1, seen, version) ?: return null
        val rpId = fields.string("rpId").orEmpty()
        if (kind == CoursewareNodeKind.RESOURCE && rpId.isBlank()) return null
        nodes += CoursewareNode(
            id = id,
            courseId = courseId,
            name = name,
            kind = kind,
            rpId = rpId,
            extension = fields.string("extension").orEmpty(),
            size = fields.string("size").orEmpty(),
            teacherName = fields.string("teacherName").orEmpty(),
            inputTime = fields.string("inputTime").orEmpty(),
            downloadCount = fields.int("downloadCount") ?: 0,
            downloadAllowed = fields.boolean("downloadAllowed") ?: true,
            children = children,
            childrenLoaded = if (kind == CoursewareNodeKind.RESOURCE) {
                true
            } else if (version == 1) {
                true
            } else {
                fields.boolean("childrenLoaded") ?: return null
            },
        )
    }
    return nodes
}

private fun StringBuilder.appendCourse(course: CoursewareCourse) {
    append('{')
    appendJsonField("id", course.id.toString(), quoted = false)
    append(',')
    appendJsonField("name", course.name)
    append(',')
    appendJsonField("courseNumber", course.courseNumber)
    append(',')
    appendJsonField("groupId", course.groupId)
    append(',')
    appendJsonField("semesterCode", course.semesterCode)
    append(',')
    if (course.teacherId == null) {
        append("\"teacherId\":null")
    } else {
        appendJsonField("teacherId", course.teacherId.toString(), quoted = false)
    }
    append(',')
    appendJsonField("childrenLoaded", course.childrenLoaded.toString(), quoted = false)
    append(",\"children\":[")
    course.children.forEachIndexed { index, node ->
        if (index > 0) append(',')
        appendNode(node)
    }
    append("]}")
}

private fun StringBuilder.appendNode(node: CoursewareNode) {
    append('{')
    appendJsonField("id", node.id.toString(), quoted = false)
    append(',')
    appendJsonField("name", node.name)
    append(',')
    appendJsonField("kind", node.kind.name)
    append(',')
    appendJsonField("rpId", node.rpId)
    append(',')
    appendJsonField("extension", node.extension)
    append(',')
    appendJsonField("size", node.size)
    append(',')
    appendJsonField("teacherName", node.teacherName)
    append(',')
    appendJsonField("inputTime", node.inputTime)
    append(',')
    appendJsonField("downloadCount", node.downloadCount.toString(), quoted = false)
    append(',')
    appendJsonField("downloadAllowed", node.downloadAllowed.toString(), quoted = false)
    append(',')
    appendJsonField("childrenLoaded", node.childrenLoaded.toString(), quoted = false)
    append(",\"children\":[")
    node.children.forEachIndexed { index, child ->
        if (index > 0) append(',')
        appendNode(child)
    }
    append("]}")
}

private fun StringBuilder.appendJsonField(
    name: String,
    value: String,
    quoted: Boolean = true,
) {
    append('"').append(name).append("\":")
    if (quoted) append('"').append(value.jsonEscape()).append('"') else append(value)
}

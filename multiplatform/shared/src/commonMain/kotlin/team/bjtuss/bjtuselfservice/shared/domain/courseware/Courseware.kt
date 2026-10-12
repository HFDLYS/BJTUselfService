package team.bjtuss.bjtuselfservice.shared.domain.courseware

enum class CoursewareNodeKind {
    FOLDER,
    RESOURCE,
}

data class CoursewareNode(
    val id: Int,
    val courseId: Int,
    val name: String,
    val kind: CoursewareNodeKind,
    val rpId: String = "",
    val extension: String = "",
    val size: String = "",
    val teacherName: String = "",
    val inputTime: String = "",
    val downloadCount: Int = 0,
    /** 智慧教学 `stu_download`：网页端仅在值为 "2" 时显示下载；其余为老师未开放学生下载，只能在线查看。 */
    val downloadAllowed: Boolean = true,
    val children: List<CoursewareNode> = emptyList(),
    val childrenLoaded: Boolean = true,
) {
    val stableKey: String
        get() = "$courseId:${kind.name}:$id"

    val isFolder: Boolean
        get() = kind == CoursewareNodeKind.FOLDER
}

data class CoursewareCourse(
    val id: Int,
    val name: String,
    val courseNumber: String,
    val groupId: String,
    val semesterCode: String,
    val teacherId: Int?,
    val children: List<CoursewareNode>,
    val childrenLoaded: Boolean = true,
) {
    val stableKey: String
        get() = "course:$id"
}

data class CoursewareSnapshot(
    val courses: List<CoursewareCourse>,
)

data class VisibleCoursewareNode(
    val node: CoursewareNode,
    val depth: Int,
)

fun visibleCoursewareTree(
    nodes: List<CoursewareNode>,
    expandedKeys: Set<String>,
): List<VisibleCoursewareNode> = buildList {
    fun appendLevel(level: List<CoursewareNode>, depth: Int) {
        level.forEach { node ->
            add(VisibleCoursewareNode(node, depth))
            if (node.isFolder && node.stableKey in expandedKeys) {
                appendLevel(node.children, depth + 1)
            }
        }
    }
    appendLevel(nodes, 0)
}

fun findCoursewareNode(
    nodes: List<CoursewareNode>,
    stableKey: String,
): CoursewareNode? {
    nodes.forEach { node ->
        if (node.stableKey == stableKey) return node
        findCoursewareNode(node.children, stableKey)?.let { return it }
    }
    return null
}

fun nodesAtCoursewarePath(
    course: CoursewareCourse,
    folderPath: List<String>,
): List<CoursewareNode>? {
    var nodes = course.children
    folderPath.forEach { key ->
        val folder = nodes.firstOrNull { it.stableKey == key && it.isFolder } ?: return null
        nodes = folder.children
    }
    return nodes
}

fun coursewarePathNames(
    course: CoursewareCourse,
    folderPath: List<String>,
): List<String>? {
    var nodes = course.children
    val names = mutableListOf<String>()
    folderPath.forEach { key ->
        val folder = nodes.firstOrNull { it.stableKey == key && it.isFolder } ?: return null
        names += folder.name
        nodes = folder.children
    }
    return names
}

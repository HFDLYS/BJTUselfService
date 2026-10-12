package team.bjtuss.bjtuselfservice.shared.data.courseware

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareCourse
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNodeKind
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareSnapshot

class CoursewareCacheCodecTest {
    @Test
    fun roundTripsNestedSnapshotAndEscapedNames() {
        val snapshot = CoursewareSnapshot(
            listOf(
                course(
                    children = listOf(
                        folder(
                            children = listOf(
                                resource(2, "讲义 \"第一讲\".pdf"),
                                resource(5, "仅在线查看.pptx").copy(downloadAllowed = false),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(snapshot, decodeCoursewareSnapshot(encodeCoursewareSnapshot(snapshot)))
    }

    @Test
    fun rejectsDuplicateStableKeysAndUnknownVersions() {
        val duplicate = CoursewareSnapshot(
            listOf(course(children = listOf(resource(2, "A.pdf"), resource(2, "B.pdf")))),
        )
        val unknownVersion = encodeCoursewareSnapshot(CoursewareSnapshot(emptyList()))
            .replace("\"version\":3", "\"version\":99")

        assertNull(decodeCoursewareSnapshot(encodeCoursewareSnapshot(duplicate)))
        assertNull(decodeCoursewareSnapshot(unknownVersion))
    }

    @Test
    fun migratesVersionOneFoldersAsFullyLoaded() {
        val legacy = encodeCoursewareSnapshot(
            CoursewareSnapshot(listOf(course(children = listOf(folder(children = emptyList()))))),
        )
            .replace("\"version\":3", "\"version\":1")
            .replace(",\"childrenLoaded\":true", "")

        val decoded = decodeCoursewareSnapshot(legacy)

        assertEquals(true, decoded?.courses?.single()?.children?.single()?.childrenLoaded)
    }

    @Test
    fun rejectsDuplicateCourseIds() {
        val first = course(children = listOf(resource(2, "A.pdf")))
        val duplicate = first.copy(name = "另一门课", children = listOf(resource(3, "B.pdf")))

        assertNull(
            decodeCoursewareSnapshot(
                encodeCoursewareSnapshot(CoursewareSnapshot(listOf(first, duplicate))),
            ),
        )
    }

    private fun course(children: List<CoursewareNode>) = CoursewareCourse(
        id = 17,
        name = "程序设计",
        courseNumber = "CS101",
        groupId = "G1",
        semesterCode = "2026-1",
        teacherId = 28,
        children = children,
    )

    private fun folder(children: List<CoursewareNode>) = CoursewareNode(
        id = 1,
        courseId = 17,
        name = "第一章",
        kind = CoursewareNodeKind.FOLDER,
        children = children,
    )

    private fun resource(id: Int, name: String) = CoursewareNode(
        id = id,
        courseId = 17,
        name = name,
        kind = CoursewareNodeKind.RESOURCE,
        rpId = "rp-$id",
        extension = "pdf",
        size = "2 MB",
        teacherName = "教师",
        inputTime = "2026-01-01",
        downloadCount = 3,
    )
}

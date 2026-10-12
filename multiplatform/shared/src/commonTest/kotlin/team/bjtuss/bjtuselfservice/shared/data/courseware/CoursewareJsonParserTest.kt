package team.bjtuss.bjtuselfservice.shared.data.courseware

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNodeKind

class CoursewareJsonParserTest {
    @Test
    fun parsesMixedFolderAndResourceRows() {
        val result = assertIs<CoursewareJsonParseResult.Success<*>>(
            parseCoursewareChildren(
                """{
                  "STATUS":"0",
                  "bagList":[{"id":1,"bag_name":"第一章"}],
                  "resList":[{"resId":2,"rpId":"rp-2","rpName":"讲义.pdf","extName":"pdf","rpSize":"2 MB","teacherName":"教师","inputTime":"2026-01-01","downloadNum":7}]
                }""".trimIndent(),
                courseId = 17,
            ),
        ).value as List<*>

        val folder = result[0] as team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode
        val resource = result[1] as team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode
        assertEquals(CoursewareNodeKind.FOLDER, folder.kind)
        assertEquals("讲义.pdf", resource.name)
        assertEquals("rp-2", resource.rpId)
        assertEquals(7, resource.downloadCount)
        // 字段缺失时保持可下载（旧接口 / fixture 兼容）。
        assertEquals(true, resource.downloadAllowed)
    }

    @Test
    fun studentDownloadFlagMatchesTheWebPage() {
        val nodes = assertIs<CoursewareJsonParseResult.Success<*>>(
            parseCoursewareChildren(
                """{"STATUS":"0","bagList":[],"resList":[
                  {"resId":2,"rpId":"rp-2","rpName":"音频.m4a","stu_download":"2"},
                  {"resId":3,"rpId":"rp-3","rpName":"课件.pptx","stu_download":"1"},
                  {"resId":4,"rpId":"rp-4","rpName":"视频.mp4","stu_download":1}
                ]}""".trimIndent(),
                courseId = 17,
            ),
        ).value as List<*>
        val flags = nodes.map { (it as team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode).downloadAllowed }
        assertEquals(listOf(true, false, false), flags)
    }

    @Test
    fun acceptsBlankListsAndParsesDownloadTicket() {
        val empty = assertIs<CoursewareJsonParseResult.Success<*>>(
            parseCoursewareChildren("""{"STATUS":"0","bagList":"","resList":""}""", 17),
        ).value as List<*>
        val ticket = assertIs<CoursewareJsonParseResult.Success<CoursewareDownloadTicket>>(
            parseCoursewareDownloadTicket(
                """{"flag":true,"rpUrl":"https://bksycenter.bjtu.edu.cn/resource/1","download_type":"file"}""",
            ),
        ).value

        assertEquals(emptyList<Any?>(), empty)
        assertEquals("https://bksycenter.bjtu.edu.cn/resource/1", ticket.url)
    }

    @Test
    fun malformedResourceDoesNotLeakBody() {
        val result = assertIs<CoursewareJsonParseResult.Failure>(
            parseCoursewareChildren(
                """{"STATUS":"0","bagList":[],"resList":[{"resId":2,"rpName":"敏感文件"}]}""",
                17,
            ),
        )

        assertEquals("resList[0].identity", result.field)
        assertFalse("敏感文件" in result.toString())
    }

    @Test
    fun treatsStatus2EmptyListsAsNoResourcesNotFailure() {
        // 服务器对“该课程没有课件资源”返回 STATUS="2" + resList/bagList 空串；
        // 与作业列表“没有数据”容错对齐，避免整批同步在第一门无资源课程处中断。
        val result = assertIs<CoursewareJsonParseResult.Success<List<*>>>(
            parseCoursewareChildren(
                """{"resList":"","bagList":"","STATUS":"2"}""",
                17,
            ),
        ).value

        assertEquals(emptyList<Any?>(), result)
    }
}

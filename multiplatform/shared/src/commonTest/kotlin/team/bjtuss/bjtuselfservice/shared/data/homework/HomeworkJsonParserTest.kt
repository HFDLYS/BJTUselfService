package team.bjtuss.bjtuselfservice.shared.data.homework

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomeworkJsonParserTest {
    @Test
    fun parsesSemesterCoursesAndAllHomeworkFields() {
        val semester = assertIs<HomeworkJsonParseResult.Success<String>>(
            parseCurrentSemesterCode("""{"STATUS":"0","result":[{"xqCode":"2026-1"}]}"""),
        )
        val courses = assertIs<HomeworkJsonParseResult.Success<List<SmartCourse>>>(
            parseSmartCourses(
                """{"STATUS":"0","courseList":[{"id":17,"name":"程序设计","teacher_id":28,"course_num":"CS101","fz_id":"G1","xq_code":"2026-1"}]}""",
            ),
        )
        val homework = assertIs<HomeworkJsonParseResult.Success<*>>(
            parseHomeworkList(
                """{
                    "STATUS":"0",
                    "courseNoteList":[{
                      "id":91,"snId":92,"stu_score":"95","user_id":7,
                      "course_id":17,"course_name":"程序设计","title":"第 1 次作业",
                      "content":"<p>要求</p>","create_date":"2026-07-01 08:00",
                      "end_time":"2026-08-01 20:00","open_date":"2026-07-01 09:00",
                      "status":1,"submitCount":20,"allCount":30,"subStatus":"已提交","scoreId":3
                    }]
                }""".trimIndent(),
                homeworkType = 2,
            ),
        ).value as List<*>

        assertEquals("2026-1", semester.value)
        assertEquals(
            SmartCourse(17, "程序设计", 28, "CS101", "G1", "2026-1"),
            courses.value.single(),
        )
        val item = homework.single() as team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
        assertEquals(91, item.upId)
        assertEquals(92, item.idSnId)
        assertEquals("95", item.score)
        assertEquals(2, item.homeworkType)
    }

    @Test
    fun acceptsBlankArraysAndParsesAttachmentMetadata() {
        val empty = assertIs<HomeworkJsonParseResult.Success<*>>(
            parseHomeworkList("""{"STATUS":"0","courseNoteList":""}""", 0),
        ).value as List<*>
        val detail = assertIs<HomeworkJsonParseResult.Success<*>>(
            parseHomeworkDetail(
                """{
                  "STATUS":"0","homeWork":{"content":"<p>请完成实验</p>"},
                  "picList":[{"id":8,"file_name":"实验+模板.docx","pic_size":2048,"url":"private/path"}]
                }""".trimIndent(),
                fallbackContent = "fallback",
            ),
        ).value as team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkDetail

        assertEquals(emptyList<Any?>(), empty)
        assertEquals("<p>请完成实验</p>", detail.content)
        assertEquals("实验 模板.docx", detail.attachments.single().fileName)
        assertEquals(2048L, detail.attachments.single().sizeBytes)
    }

    /** 老师附件的 `file_name` 不带扩展名时，从存储路径补回（真实案例：fig2 → .zip、fig3 → .pdf）。 */
    @Test
    fun restoresAttachmentExtensionFromStoragePath() {
        val detail = assertIs<HomeworkJsonParseResult.Success<*>>(
            parseHomeworkDetail(
                """{
                  "STATUS":"0","homeWork":{"content":""},
                  "picList":[
                    {"id":1,"file_name":"fig2","url":"upload/2026/10/abc123.zip"},
                    {"id":2,"file_name":"fig3","url":"upload/2026/10/def456.PDF?v=1"},
                    {"id":3,"file_name":"模板.docx","url":"upload/2026/10/x.docx"},
                    {"id":4,"file_name":"说明","url":"upload/2026/10/noext"},
                    {"id":5,"file_name":"v1.2","url":"upload/a.b/c.zip"}
                  ]
                }""".trimIndent(),
                fallbackContent = "",
            ),
        ).value as team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkDetail

        assertEquals(
            listOf("fig2.zip", "fig3.PDF", "模板.docx", "说明", "v1.2.zip"),
            detail.attachments.map { it.fileName },
        )
        assertEquals("upload/2026/10/abc123.zip", detail.attachments.first().sourcePath)
    }

    @Test
    fun skipsMissingIdentityWithoutLeakingBodyOrFailingTheList() {
        val result = assertIs<HomeworkJsonParseResult.Success<List<*>>>(
            parseHomeworkList(
                """{"STATUS":"0","courseNoteList":[
                    {"id":1,"course_id":2,"title":"敏感标题"},
                    {"id":3,"course_id":4,"course_name":"程序设计","title":"实验一"}
                ]}""",
                0,
            ),
        )

        val items = result.value
        assertEquals(1, items.size)
        val item = items.single() as team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
        assertEquals("实验一", item.title)
        kotlin.test.assertFalse("敏感标题" in result.toString())
    }

    @Test
    fun parsesUploadReceiptWithExtensionlessFile() {
        val receipt = assertIs<HomeworkJsonParseResult.Success<HomeworkUploadReceipt>>(
            parseHomeworkUploadReceipt(
                """{"STATUS":"0","fileNameNoExt":"README","fileExtName":"","fileSize":"12","visitName":"server-token"}""",
            ),
        ).value

        assertEquals("README", receipt.fileNameNoExt)
        assertEquals("", receipt.fileExtName)
        assertEquals("12", receipt.fileSize)
    }

    @Test
    fun rejectsUploadReceiptWhenServerStatusIsNotZero() {
        // 学生调用老师端点 rpUpload.shtml 的真实回执。
        val rejected = assertIs<HomeworkJsonParseResult.Failure>(
            parseHomeworkUploadReceipt("""{"STATUS":"2","MSG":"学生角色无权限上传"}"""),
        )
        assertEquals("学生角色无权限上传", rejected.field)

        // 文件类型不支持时回执没有 STATUS，正文是中文提示。
        val unsupported = assertIs<HomeworkJsonParseResult.Failure>(
            parseHomeworkUploadReceipt("上传文件类型不支持，请更换文件！"),
        )
        assertEquals("root", unsupported.field)
    }

    @Test
    fun acceptsOnlySuccessFlagFromSubmitReceipt() {
        assertIs<HomeworkJsonParseResult.Success<Unit>>(
            parseHomeworkSubmitReceipt("""{"flag":"success"}"""),
        )

        // return_num 取错值时服务端的真实回执：提交没有生效。
        val rejected = assertIs<HomeworkJsonParseResult.Failure>(
            parseHomeworkSubmitReceipt("""{"flag":"bad"}"""),
        )
        assertEquals("flag", rejected.field)
    }

    @Test
    fun treatsStatus2WithoutListAsEmptyHomeworkNotFailure() {
        // 服务器对“该课程当前类型没有作业”返回 STATUS="2" + message="没有数据"，
        // 原 Android 以默认值容错得到空列表；严格解析必须同样放行，避免整批中断。
        val result = assertIs<HomeworkJsonParseResult.Success<List<*>>>(
            parseHomeworkList(
                """{"page":1,"size":100,"currentRow":0,"total":0,"totalPage":0,"STATUS":"2","message":"没有数据"}""",
                0,
            ),
        ).value

        assertEquals(emptyList<Any?>(), result)
    }

    @Test
    fun treatsOtherNonSuccessStatusAsMalformedInsteadOfEmpty() {
        val result = assertIs<HomeworkJsonParseResult.Failure>(
            parseHomeworkList("""{"STATUS":"5","message":"系统异常"}""", 0),
        )
        assertEquals("STATUS", result.field)
    }

    @Test
    fun rejectsSuccessPayloadWithoutHomeworkList() {
        val result = assertIs<HomeworkJsonParseResult.Failure>(
            parseHomeworkList("""{"STATUS":"0","message":"暂时无法返回数据"}""", 0),
        )
        assertEquals("courseNoteList", result.field)
    }

    @Test
    fun emptySemesterAndPartialCourseListAreNotMalformed() {
        val emptySemester = assertIs<HomeworkJsonParseResult.Success<String>>(
            parseCurrentSemesterCode("""{"STATUS":"0","result":[]}"""),
        )
        val snakeSemester = assertIs<HomeworkJsonParseResult.Success<String>>(
            parseCurrentSemesterCode("""{"STATUS":"0","result":[{"xq_code":"2026-2"}]}"""),
        )
        val courses = assertIs<HomeworkJsonParseResult.Success<List<SmartCourse>>>(
            parseSmartCourses(
                """{"STATUS":"2","message":"没有数据","courseList":[
                    {"id":1},
                    {"id":17,"name":"程序设计"}
                ]}""",
            ),
        )

        assertEquals("", emptySemester.value)
        assertEquals("2026-2", snakeSemester.value)
        assertEquals(listOf(SmartCourse(17, "程序设计", null)), courses.value)
    }

    @Test
    fun rejectsNonJsonHomeworkPayloadWithoutLeakingBody() {
        val result = assertIs<HomeworkJsonParseResult.Failure>(
            parseHomeworkList("<html>敏感登录页</html>", 0),
        )
        assertEquals("root", result.field)
        kotlin.test.assertFalse("敏感登录页" in result.toString())
    }
}

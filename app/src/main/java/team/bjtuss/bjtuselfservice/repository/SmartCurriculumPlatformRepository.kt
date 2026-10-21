package team.bjtuss.bjtuselfservice.repository


import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.api.RequestKotlin
import team.bjtuss.bjtuselfservice.controller.NetworkRequestQueue
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import team.bjtuss.bjtuselfservice.jsonclass.Course
import team.bjtuss.bjtuselfservice.jsonclass.CourseJsonType
import team.bjtuss.bjtuselfservice.jsonclass.CourseResourceResponse
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareNode
import team.bjtuss.bjtuselfservice.jsonclass.HomeworkJsonType
import team.bjtuss.bjtuselfservice.jsonclass.SemesterJsonType
import team.bjtuss.bjtuselfservice.jsonclass.getArticleListJsonType
import team.bjtuss.bjtuselfservice.statemanager.AppStateManager
import team.bjtuss.bjtuselfservice.utils.KotlinUtils
import java.io.IOException

data class HomeworkAttachment(
    val id: Int,
    val fileName: String,
    val sizeBytes: Long,
    val sourcePath: String,
)

data class HomeworkDetail(
    val content: String,
    val attachments: List<HomeworkAttachment>,
)

object SmartCurriculumPlatformRepository {

    val client = StudentAccountManager.getInstance().client
    private val userAgent = StudentAccountManager.getInstance().userAgent
    val moshi = KotlinUtils.moshi
//    private val initializationDeferred = CompletableDeferred<Unit>()


    private var courseFromJson: CourseJsonType? = null

    var headersBuilder = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Accept", "application/json, text/javascript, */*; q=0.01")
        .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .add("Referer", "http://123.121.147.7:88")
        .add("X-Requested-With", "XMLHttpRequest")


    suspend fun initClient() {
        openPortalChain()
        getAndSetSessionIdInHeaders()
        val semesterFromJson = getSemesterTypeList()
        courseFromJson =
            semesterFromJson.result?.get(0)?.xqCode?.let { getCourseTypeList(xqCode = it) }
    }

    /**
     * Mirrors the browser sequence:
     * MIS (already logged in) -> bksy portal -> 智慧课程平台 jump.
     *
     * The shared OkHttp cookie jar carries the MIS cookies into the portal and
     * the portal cookies into the jump endpoint.
     */
    private suspend fun openPortalChain() = withContext(Dispatchers.IO) {
        visitPortal(
            url = "https://mis.bjtu.edu.cn/home/",
            referer = "https://mis.bjtu.edu.cn/",
            tag = "MIS home",
        )
        visitPortal(
            url = "https://bksy.bjtu.edu.cn/",
            referer = "https://mis.bjtu.edu.cn/home/",
            tag = "bksy",
        )
        visitPortal(
            url = "http://bksycenter.bjtu.edu.cn/NoMasterJumpPage.aspx?URL=jwcZhjx&FPC=page:jwcZhjx",
            referer = "https://bksy.bjtu.edu.cn/",
            tag = "smart-course jump",
        )
    }

    private fun visitPortal(url: String, referer: String, tag: String): String {
        val headers = Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .add("Referer", referer)
            .build()
        return client.newCall(
            Request.Builder().url(url).headers(headers).build()
        ).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d(
                "SCP",
                tag + " -> " + response.code + " " + response.request.url +
                    " title=" + Jsoup.parse(body).title()
            )
            response.request.url.toString()
        }
    }

    private suspend fun getSemesterTypeList(): SemesterJsonType {

        val semesterUrl =
            "http://123.121.147.7:88/ve/back/rp/common/teachCalendar.shtml?method=queryCurrentXq"

        val adapter = moshi.adapter(SemesterJsonType::class.java)

        return withContext(Dispatchers.IO) {
            RequestKotlin.get(semesterUrl, headersBuilder.build()).use {
                it.body?.source()?.let { source ->
                    try {
                        adapter.fromJson(source)
                    } catch (e: Exception) {
                        println("Failed to parse JSON: ${e.message}")
                        null
                    }
                } ?: throw IOException("Response body is null")
            }

        }
    }

    suspend fun getCourseTypeList(xqCode: String): CourseJsonType {
        val baseUrl =
            "http://123.121.147.7:88/ve/back/coursePlatform/course.shtml?method=getCourseList&pagesize=100&page=1&xqCode="
        val courseUrl = baseUrl + xqCode


        val adapter = moshi.adapter<CourseJsonType>(CourseJsonType::class.java)

        return withContext(Dispatchers.IO) {
            RequestKotlin.get(courseUrl, headersBuilder.build()).use {
                it.body?.source()?.let { source ->
                    try {
                        adapter.fromJson(source)
                    } catch (e: Exception) {
                        println("Failed to parse JSON: ${e.message}")
                        null
                    }
                } ?: throw IOException("Response body is null")
            }
        }
    }

    private suspend fun getHomeworkList(cId: String, subType: String): HomeworkJsonType {
        val baseUrl =
            "http://123.121.147.7:88/ve/back/coursePlatform/homeWork.shtml?method=getHomeWorkList&cId=${cId}&subType=${subType}&page=1&pagesize=100"


        val adapter = moshi.adapter(HomeworkJsonType::class.java)
        return withContext(Dispatchers.IO) {
            RequestKotlin.get(baseUrl, headersBuilder.build()).use { originalResponse ->
                // 1. 缓存响应体字节（自动关闭原始流）
                val cachedBytes = originalResponse.body?.bytes() ?: throw IOException("Empty body")

                // 2. 构建可重复读取的副本
                val copiedBody = cachedBytes.toResponseBody(originalResponse.body?.contentType())

                // 3. 使用缓存数据解析
                copiedBody.source().use { source ->
                    try {
                        // 调试：打印原始JSON
                        val rawJson = source.buffer.clone().readUtf8()
//                        println("Raw JSON: $rawJson")

                        adapter.fromJson(source)
                    } catch (e: Exception) {
                        // 异常时仍可安全读取缓存
//                        val errorJson = String(cachedBytes, Charsets.UTF_8)
//                        println("Parsing failed. Original JSON: $errorJson")
//                        println("Error: ${e.message}")
                        null
                    }
                }
            } ?: throw IOException("Response body is null")
        }
    }


    suspend fun getHomework(): List<HomeworkEntity> {

//        return NetworkRequestQueue.enqueueHighPriorityQueue("Homework") {
//            getHomeWorkListByHomeworkType(0)
//        }.getOrElse { emptyList() }
        return getHomeWorkListByHomeworkType(0)
    }

    suspend fun getHomeworkDetail(homework: HomeworkEntity): HomeworkDetail {
        val teacherId = courseFromJson?.courseList
            ?.firstOrNull { it.id == homework.courseId }
            ?.teacher_id
            ?: throw IOException("未找到课程对应的教师信息")
        val detailUrl = "http://123.121.147.7:88/ve/back/coursePlatform/homeWork.shtml"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("method", "queryStudentCourseNote")
            .addQueryParameter("id", homework.upId.toString())
            .addQueryParameter("courseId", homework.courseId.toString())
            .addQueryParameter("teacherId", teacherId.toString())
            .build()

        return withContext(Dispatchers.IO) {
            RequestKotlin.get(detailUrl.toString(), headersBuilder.build()).use { response ->
                if (!response.isSuccessful) {
                    throw IOException("获取作业详情失败：${response.code}")
                }
                val responseJson = JSONObject(
                    response.body?.string() ?: throw IOException("作业详情响应为空")
                )
                if (responseJson.optString("STATUS") != "0") {
                    throw IOException(responseJson.optString("message", "获取作业详情失败"))
                }
                val homeworkJson = responseJson.optJSONObject("homeWork")
                    ?: throw IOException("作业详情缺失")
                val attachments = responseJson.optJSONArray("picList")
                    ?.let { attachmentArray ->
                        buildList {
                            for (index in 0 until attachmentArray.length()) {
                                val attachmentJson = attachmentArray.optJSONObject(index)
                                    ?: continue
                                val attachmentId = attachmentJson.optInt("id")
                                if (attachmentId == 0) continue
                                add(
                                    HomeworkAttachment(
                                        id = attachmentId,
                                        fileName = attachmentJson
                                            .optString("file_name", "附件 $attachmentId")
                                            .replace('+', ' '),
                                        sizeBytes = attachmentJson.optLong("pic_size", 0L),
                                        sourcePath = attachmentJson.optString("url"),
                                    )
                                )
                            }
                        }
                    }
                    .orEmpty()
                HomeworkDetail(
                    content = homeworkJson.optString("content", homework.content),
                    attachments = attachments,
                )
            }
        }
    }

    fun getHomeworkAttachmentDownloadUrl(
        homeworkId: Int,
        attachmentId: Int,
    ): String {
        return "http://123.121.147.7:88/ve/back/coursePlatform/dataSynAction.shtml"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("method", "downLoadPic")
            .addQueryParameter("id", attachmentId.toString())
            .addQueryParameter("noteId", homeworkId.toString())
            .build()
            .toString()
    }

    suspend fun getCourseDesign(): List<HomeworkEntity> {
        return  getHomeWorkListByHomeworkType(1)

    }

    suspend fun getExperimentReport(): List<HomeworkEntity> {
        return getHomeWorkListByHomeworkType(2)
    }


    suspend fun getCourseList(): List<Course> {
        AppStateManager.awaitLoginState()
        return courseFromJson?.courseList ?: emptyList()
    }


    suspend fun generateCoursewareRootNode(course: Course): CoursewareNode {
        AppStateManager.awaitLoginState()
        val coursewareRootNode: CoursewareNode =
            CoursewareNode(id = 0, course = course)
        coursewareRootNode.children = generateChildrenNodeList(parentNode = coursewareRootNode)
        return coursewareRootNode
    }

    private suspend fun getAndSetSessionIdInHeaders() {
        val url =
            "http://123.121.147.7:88/ve/back/coursePlatform/message.shtml?method=getArticleList"
        withContext(Dispatchers.IO) {


            RequestKotlin.get(url, headersBuilder.build()).use { response ->
                if (!response.isSuccessful) {
                    throw IOException("获取sessionId失败，状态码: ${response.code}")
                }

                val body = response.body?.string() ?: throw IOException("Response body is null")
                Log.d(
                    "SCP",
                    "getArticleList -> " + response.code + " " + response.request.url +
                        " body=" + body.take(180)
                )
                moshi.adapter(getArticleListJsonType::class.java)
                    .fromJson(body)
                    ?.sessionId
                    ?.also { headersBuilder.add("sessionid", it) }
                    ?: throw IllegalStateException("SessionId not found in response")
            }
        }
    }

    private suspend fun generateChildrenNodeList(parentNode: CoursewareNode): List<CoursewareNode> {
        return withContext(Dispatchers.IO) {
            val course = parentNode.course
            val url =
                "http://123.121.147.7:88/ve/back/coursePlatform/courseResource.shtml?" +
                        "method=stuQueryUploadResourceForCourseList" +
                        "&courseId=${course.course_num}" +
                        "&cId=${course.course_num}" +
                        "&xkhId=${course.fz_id}" +
                        "&xqCode=${course.xq_code}" +
                        "&docType=1" +
                        "&up_id=${parentNode.id}" +
                        "&searchName="

            val adapter = moshi.adapter(CourseResourceResponse::class.java)
            val courseWareNodeList = RequestKotlin.get(url,headersBuilder.build()).use { response ->
                val responseContent = response.body?.string()
                responseContent?.let { str ->
                    try {

                        val jsonString = str
                            .replace("\"resList\"\\s*:\\s*\"\"".toRegex(), "\"resList\": []")
                            .replace("\"bagList\"\\s*:\\s*\"\"".toRegex(), "\"bagList\": []")
                        val courseResourceResponse = adapter.fromJson(jsonString)

                        // 处理 bagList
                        val bagNodes = courseResourceResponse?.bagList?.map { bag ->
                            CoursewareNode(
                                id = bag.id,
                                bag = bag,
                                course = parentNode.course
                            ).apply {
                                children = generateChildrenNodeList(this) // 递归生成子节点
                            }
                        } ?: emptyList()

                        // 处理 resList
                        val resNodes = courseResourceResponse?.resList?.map { res ->
                            CoursewareNode(
                                id = res.resId, // 假设 Res 类有 resId 字段
                                res = res,
                                course = parentNode.course
                            ) // 资源节点通常没有子节点
                        } ?: emptyList()

                        bagNodes + resNodes // 合并两类节点

                    } catch (e: Exception) {
                        println("Failed to parse JSON: ${e.message}")
                        emptyList()
                    }
                } ?: throw IOException("Response body is null")
            }
            courseWareNodeList

        }
    }


    private suspend fun getHomeWorkListByHomeworkType(homeworkType: Int): List<HomeworkEntity> {
        AppStateManager.awaitLoginState()

        val courses = courseFromJson?.courseList
        if (courses.isNullOrEmpty()) {
            throw IOException("课程平台会话未建立，无法获取作业")
        }
        val listFromJson = mutableListOf<HomeworkJsonType>()
        courses.forEach {
            val homeworkFromJson =
                getHomeworkList(
                    it.id.toString(),
                    homeworkType.toString()
                )
            listFromJson.add(homeworkFromJson)
        }


        val homeworkList = mutableListOf<HomeworkEntity>()

        listFromJson.forEach {
            it.courseNoteList?.forEach { homework ->
                val homeworkEntity = HomeworkEntity(
                    upId = homework.id,
                    idSnId = homework.snId,
                    score = homework.stu_score ?: "",
                    userId = 0,
                    courseId = homework.course_id,
                    courseName = homework.course_name,
                    title = homework.title,
                    content = homework.content ?: "",
                    createDate = homework.create_date,
                    endTime = homework.end_time,
                    openDate = homework.open_date,
                    status = homework.status,
                    submitCount = homework.submitCount,
                    allCount = homework.allCount,
                    subStatus = homework.subStatus,
                    scoreId = homework.scoreId ?: 0,
                    homeworkType = 0
                )
                homeworkList.add(homeworkEntity)
            }
        }
        return homeworkList

    }


    /**
     * 根据课程名获取教师工号
     */
    private suspend fun getTeacherWorkNumByCourse(course: Course): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "http://123.121.147.7:88/ve/back/coursePlatform/coursePlatform.shtml?" +
                        "method=toCoursePlatform&courseId=${course.course_num}&" +
                        "dataSource=1&" +
                        "cId=${course.id}&" +
                        "xkhId=${course.fz_id}&" +
                        "xqCode=${course.xq_code}"
                RequestKotlin.get(url,headersBuilder.build()).use { response ->
                    if (!response.isSuccessful) {
                        Log.e("TeacherWorkNum", "Request failed: ${response.code}")
                        return@withContext null
                    }

                    val html = response.body?.string()
                    if (html.isNullOrEmpty()) {
                        Log.e("TeacherWorkNum", "Empty response body")
                        return@withContext null
                    }

                    // 使用 Jsoup 解析 HTML
                    val document: Document = Jsoup.parse(html)
                    val teacherIdInput = document.select("input#teacherId").first()

                    if (teacherIdInput != null) {
                        val teacherIdValue = teacherIdInput.attr("value")
                        Log.d("TeacherWorkNum", "找到 teacherId, 其 value 为: $teacherIdValue")
                        return@withContext teacherIdValue
                    } else {
                        Log.w("TeacherWorkNum", "未找到 id 为 'teacherId' 的 input 元素")
                        return@withContext null
                    }
                }
            } catch (e: Exception) {
                Log.e("TeacherWorkNum", "获取教师工号失败", e)
                null
            }
        }
    }

    /**
     * 根据课程获取教学日历PDF下载URL
     */
    suspend fun getTeachingCalendarUrl(course: Course): String? {
        return withContext(Dispatchers.IO) {
            try {
                AppStateManager.awaitLoginState()

                // 首先获取教师工号
                val teacherId = getTeacherWorkNumByCourse(course)
                if (teacherId.isNullOrEmpty()) {
                    Log.e("TeachingCalendar", "无法获取教师工号")
                    return@withContext null
                }

                val url = "http://123.121.147.7:88/ve/back/coursePlatform/coursePlatform.shtml"


                RequestKotlin.get(url,headersBuilder.build()).use { response ->
                    if (!response.isSuccessful) {
                        Log.e("TeachingCalendar", "Request failed: ${response.code}")
                        return@withContext null
                    }

                    val html = response.body?.string()
                    if (html.isNullOrEmpty()) {
                        Log.e("TeachingCalendar", "Empty response body")
                        return@withContext null
                    }

                    // 使用 Jsoup 解析 HTML
                    val document: Document = Jsoup.parse(html)
                    val iframeElement = document.select("iframe#pdfIframe").first()

                    if (iframeElement != null) {
                        val iframeSrc = iframeElement.attr("src")
                        if (iframeSrc.isNotEmpty()) {
                            Log.d("TeachingCalendar", "提取到的iframe src地址为: $iframeSrc")

                            // 提取最后5个路径段
                            val pathSegments = iframeSrc.split("/")
                            if (pathSegments.size < 5) {
                                Log.e("TeachingCalendar", "URL路径段不足5个")
                                return@withContext null
                            }

                            val key = pathSegments.takeLast(5)
                            Log.d("TeachingCalendar", "提取到的key: $key")

                            // 构建最终的PDF下载地址
                            val pdfUrl = "http://123.121.147.7:1936/kk/rp/" + key.joinToString("/")
                            Log.d("TeachingCalendar", "最终的pdf下载地址为: $pdfUrl")

                            return@withContext pdfUrl
                        } else {
                            Log.w("TeachingCalendar", "找到了iframe元素，但没有src属性")
                            return@withContext null
                        }
                    } else {
                        Log.w("TeachingCalendar", "未找到指定的iframe元素")
                        return@withContext null
                    }
                }
            } catch (e: Exception) {
                Log.e("TeachingCalendar", "获取教学日历URL失败", e)
                null
            }
        }
    }

    /**
     * 下载教学日历PDF文件
     * @param course 课程信息
     * @param outputFile 输出文件路径
     * @return 是否下载成功
     */
    suspend fun downloadTeachingCalendarPdf(course: Course, outputFile: java.io.File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val pdfUrl = getTeachingCalendarUrl(course)
                if (pdfUrl.isNullOrEmpty()) {
                    Log.e("DownloadPDF", "无法获取PDF下载地址")
                    return@withContext false
                }

                RequestKotlin.get(pdfUrl,headersBuilder.build()).use { response ->
                    if (!response.isSuccessful) {
                        Log.e("DownloadPDF", "下载失败: ${response.code}")
                        return@withContext false
                    }

                    response.body?.byteStream()?.use { inputStream ->
                        outputFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    Log.d("DownloadPDF", "PDF下载成功: ${outputFile.absolutePath}")
                    true
                }
            } catch (e: Exception) {
                Log.e("DownloadPDF", "下载PDF文件失败", e)
                false
            }
        }
    }

    /**
     * 根据课程名称获取教学日历PDF下载URL (便利方法)
     */
    suspend fun getTeachingCalendarUrlByName(courseName: String): String? {
        val courseList = getCourseList()
        val course = courseList.find { it.name == courseName }
        return if (course != null) {
            getTeachingCalendarUrl(course)
        } else {
            Log.e("TeachingCalendar", "未找到课程: $courseName")
            null
        }
    }

    /**
     * 根据课程名称下载教学日历PDF (便利方法)
     */
    suspend fun downloadTeachingCalendarPdfByName(
        courseName: String,
        outputFile: java.io.File
    ): Boolean {
        val courseList = getCourseList()
        val course = courseList.find { it.name == courseName }
        return if (course != null) {
            downloadTeachingCalendarPdf(course, outputFile)
        } else {
            Log.e("DownloadPDF", "未找到课程: $courseName")
            false
        }
    }

}

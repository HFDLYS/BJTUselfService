package team.bjtuss.bjtuselfservice.repository


import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import team.bjtuss.bjtuselfservice.StudentAccountManager
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


object SmartCurriculumPlatformRepository {

    val client = StudentAccountManager.getInstance().client
    private val userAgent = StudentAccountManager.getInstance().userAgent
    val moshi = KotlinUtils.moshi
//    private val initializationDeferred = CompletableDeferred<Unit>()


    private var courseFromJson: CourseJsonType? = null

    private var headersBuilder = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Accept", "application/json, text/javascript, */*; q=0.01")
        .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .add("Referer", "http://123.121.147.7:88")
        .add("X-Requested-With", "XMLHttpRequest")

    suspend fun initClient() {


        val request1 = Request.Builder()
            .url("https://mis.bjtu.edu.cn/module/module/28/")
            .headers(headersBuilder.build())
            .build()

        val request2 = Request.Builder()
            .url("https://bksycenter.bjtu.edu.cn/NoMasterJumpPage.aspx?URL=jwcZhjx&FPC=page:jwcZhjx")
            .headers(headersBuilder.build())
            .build()



        println("9302190321")
        client.newCall(request1).execute()
        // client.newCall(request2).execute()
        getAndSetSessionIdInHeaders()
        val semesterFromJson = getSemesterTypeList()
        courseFromJson =
            semesterFromJson.result?.get(0)?.xqCode?.let { getCourseTypeList(xqCode = it) }
    }

    private suspend fun getSemesterTypeList(): SemesterJsonType {
        // 2. Visit the course list
        val semesterUrl =
            "http://123.121.147.7:88/ve/back/rp/common/teachCalendar.shtml?method=queryCurrentXq"

        val semesterRequest = Request.Builder()
            .url(semesterUrl)
            .headers(
                headersBuilder.build()
            )
            .build()
        val adapter = moshi.adapter(SemesterJsonType::class.java)

        return withContext(Dispatchers.IO) {
            client.newCall(semesterRequest).execute().use {
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

        val courseRequest = Request.Builder()
            .url(courseUrl)
            .headers(
                headersBuilder.build()
            )
            .build()

        val adapter = moshi.adapter<CourseJsonType>(CourseJsonType::class.java)

        return withContext(Dispatchers.IO) {
            client.newCall(courseRequest).execute().use {
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

        val homeworkRequest = Request.Builder()
            .url(baseUrl)
            .headers(
                headersBuilder.build()
            )
            .build()

        val adapter = moshi.adapter(HomeworkJsonType::class.java)
        return withContext(Dispatchers.IO) {
            client.newCall(homeworkRequest).execute().use { originalResponse ->
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

        return NetworkRequestQueue.enqueueHighPriority("Homework") {
            getHomeWorkListByHomeworkType(0)
        }.getOrElse { emptyList() }
    }

    suspend fun getCourseDesign(): List<HomeworkEntity> {
        return NetworkRequestQueue.enqueueHighPriority("CourseDesign") {
            getHomeWorkListByHomeworkType(1)
        }.getOrElse { emptyList() }
    }

    suspend fun getExperimentReport(): List<HomeworkEntity> {
        return NetworkRequestQueue.enqueueHighPriority("ExperimentReport") {
            getHomeWorkListByHomeworkType(2)
        }.getOrElse { emptyList() }
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


            client.newCall(
                Request.Builder()
                    .url(url)
                    .headers(headersBuilder.build())
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("获取sessionId失败，状态码: ${response.code}")
                }
                println("89732198321")
//                println(response.body!!.string())
                response.body?.source()?.let { source ->
                    moshi.adapter(getArticleListJsonType::class.java)
                        .fromJson(source)
                        ?.sessionId
                        ?.also { headersBuilder.add("sessionid", it) }
                        ?: throw IllegalStateException("SessionId not found in response")
                } ?: throw IOException("Response body is null")
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
            val request = Request.Builder()
                .url(url)
                .headers(
                    headersBuilder.build()
                )
                .build()
            val adapter = moshi.adapter(CourseResourceResponse::class.java)
            val courseWareNodeList = client.newCall(request).execute().use { response ->
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

        val listFromJson = mutableListOf<HomeworkJsonType>()
        courseFromJson?.courseList?.forEach {
            val homeworkFromJson =
                getHomeworkList(
                    it.id.toString(),
                    homeworkType.toString()
                )
            listFromJson.add(homeworkFromJson)
        }


        val processedList = mutableListOf<HomeworkEntity>()

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
                    scoreId = homework.scoreId  ?: 0,
                    homeworkType = 0
                )
                processedList.add(homeworkEntity)
            }
        }
        return processedList

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
                val request = Request.Builder()
                    .url(
                        url
                    )
                    .headers(headersBuilder.build())
                    .build()

                client.newCall(request).execute().use { response ->
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

                val request = Request.Builder()
                    .url(
                        "$url?" +
                                "method=toCoursePlatform&" +
                                "courseToPage=10436&" +
                                "courseId=${course.course_num}&" +
                                "dataSource=1&" +
                                "cId=${course.id}&" +
                                "xkhId=${course.fz_id}&" +
                                "xqCode=${course.xq_code}&" +
                                "teacherId=$teacherId"
                    )
                    .headers(headersBuilder.build())
                    .build()

                client.newCall(request).execute().use { response ->
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

                val request = Request.Builder()
                    .url(pdfUrl)
                    .headers(headersBuilder.build())
                    .build()

                client.newCall(request).execute().use { response ->
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

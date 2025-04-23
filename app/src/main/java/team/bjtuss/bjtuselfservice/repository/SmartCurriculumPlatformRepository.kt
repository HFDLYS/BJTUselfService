package team.bjtuss.bjtuselfservice.repository


import android.util.Log
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Types
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.controller.NetworkRequestQueue
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import team.bjtuss.bjtuselfservice.jsonclass.Course
import team.bjtuss.bjtuselfservice.jsonclass.CourseJsonType
import team.bjtuss.bjtuselfservice.jsonclass.CourseResourceResponse
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareNode
import team.bjtuss.bjtuselfservice.jsonclass.HomeworkJsonType
import team.bjtuss.bjtuselfservice.jsonclass.SemesterJsonType
import team.bjtuss.bjtuselfservice.statemanager.AppState
import team.bjtuss.bjtuselfservice.statemanager.AppStateManager
import team.bjtuss.bjtuselfservice.utils.KotlinUtils
import team.bjtuss.bjtuselfservice.utils.NetworkUtils
import java.io.IOException


object SmartCurriculumPlatformRepository {

    val client = StudentAccountManager.getInstance().client
    private val userAgent = StudentAccountManager.getInstance().userAgent
    val moshi = KotlinUtils.moshi
//    private val initializationDeferred = CompletableDeferred<Unit>()


    private var courseFromJson: CourseJsonType? = null


    suspend fun initClient() {

        val request1 = Request.Builder()
            .url("https://mis.bjtu.edu.cn/module/module/104/")
            .header("User-Agent", userAgent)
            .build()

        val request2 = Request.Builder()
            .url("https://bksycenter.bjtu.edu.cn/NoMasterJumpPage.aspx?URL=jwcZhjx&FPC=page:jwcZhjx")
            .header("User-Agent", userAgent)
            .build()

        client.newCall(request1).execute()
        client.newCall(request2).execute()
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
            .header(
                "User-Agent", userAgent
            )
            .build()
        val adapter = moshi.adapter(SemesterJsonType::class.java)

        return withContext(Dispatchers.IO) {
            client.newCall(semesterRequest).execute().use{
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

    private suspend fun getCourseTypeList(xqCode: String): CourseJsonType {
        val baseUrl =
            "http://123.121.147.7:88/ve/back/coursePlatform/course.shtml?method=getCourseList&pagesize=100&page=1&xqCode="
        val courseUrl = baseUrl + xqCode

        val courseRequest = Request.Builder()
            .url(courseUrl)
            .header(
                "User-Agent", userAgent
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
            .header(
                "User-Agent", userAgent
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
                .header("User-Agent", userAgent)
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
                    homeworkType = 0
                )
                processedList.add(homeworkEntity)
            }
        }
        return processedList

    }
}

package team.bjtuss.bjtuselfservice.repository


import android.util.Log
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import team.bjtuss.bjtuselfservice.jsonclass.Course
import team.bjtuss.bjtuselfservice.jsonclass.CourseJsonType
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareCatalog
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareNode
import team.bjtuss.bjtuselfservice.jsonclass.HomeworkJsonType
import team.bjtuss.bjtuselfservice.jsonclass.Node

import team.bjtuss.bjtuselfservice.jsonclass.SemesterJsonType
import team.bjtuss.bjtuselfservice.jsonclass.CourseResourceResponse
import team.bjtuss.bjtuselfservice.jsonclass.Res
import team.bjtuss.bjtuselfservice.utils.KotlinUtils
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type


object SmartCurriculumPlatformRepository {
    val client = StudentAccountManager.getInstance().client
    private val userAgent = StudentAccountManager.getInstance().userAgent
    val moshi = KotlinUtils.moshi


    private val initializationDeferred = CompletableDeferred<Unit>()


    private var courseFromJson: CourseJsonType? = null
    private var coursewareCatalogFromJson: CoursewareCatalog? = null

    init {
        initClient()
    }


    fun initClient() {

        var request1 = Request.Builder()
            .url("https://mis.bjtu.edu.cn/module/module/104/")
            .header("User-Agent", userAgent)
            .build()

        val request2 = Request.Builder()
            .url("https://bksycenter.bjtu.edu.cn/NoMasterJumpPage.aspx?URL=jwcZhjx&FPC=page:jwcZhjx")
            .header("User-Agent", userAgent)
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            client.newCall(request1).execute()
            client.newCall(request2).execute()


            val semesterFromJson = getSemesterTypeList()
            courseFromJson =
                semesterFromJson.result?.get(0)?.xqCode?.let { getCourseTypeList(xqCode = it) }


            initializationDeferred.complete(Unit)
        }
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
            val response = client.newCall(semesterRequest).execute()
            response.body?.source()?.let { source ->
                try {
                    adapter.fromJson(source)
                } catch (e: Exception) {
                    println("Failed to parse JSON: ${e.message}")
                    null
                }
            } ?: throw IOException("Response body is null")
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
            val response = client.newCall(courseRequest).execute()
            response.body?.source()?.let { source ->
                try {
                    adapter.fromJson(source)
                } catch (e: Exception) {
                    println("Failed to parse JSON: ${e.message}")
                    null
                }
            } ?: throw IOException("Response body is null")
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

        return NetworkRequestQueue.enqueue("getHomework") {
            getHomeWorkListByHomeworkType(0)
        }.getOrElse { emptyList() }
    }

    suspend fun getCourseDesign(): List<HomeworkEntity> {
        return NetworkRequestQueue.enqueue("getCourseDesign") {
            getHomeWorkListByHomeworkType(1)
        }.getOrElse { emptyList() }
    }

    suspend fun getExperimentReport(): List<HomeworkEntity> {
        return NetworkRequestQueue.enqueue("getExperimentReport") {
            getHomeWorkListByHomeworkType(2)
        }.getOrElse { emptyList() }
    }

    suspend fun getCurrentWeek(): Int {
        val url = "http://123.121.147.7:88/ve/back/coursePlatform/course.shtml?method=getTimeList"
        var request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()

        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val adapter = moshi.adapter<Map<String, Any>>(type)

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            response.body?.string()?.let {
                val jsonMap = adapter.fromJson(it)
                (jsonMap?.get("weekCode") as String).toIntOrNull()
            } ?: throw IOException("Response body is null")
        }
    }

    suspend fun getCourseList(): List<Course> {
        initializationDeferred.await()
        return courseFromJson?.courseList ?: emptyList()
    }

    suspend fun getCoursewareCatalog(course: Course): List<Node> {
        initializationDeferred.await()
        val url = "http://123.121.147.7:88/ve/back/coursePlatform/courseResource.shtml?" +
                "method=stuQueryCourseResourceBag" +
                "&courseId=${course.course_num}" +
                "&cId=${course.course_num}" +
                "&xkhId=${course.fz_id}" +
                "&xqCode=${course.xq_code}" +
                "&docType=1"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        val adapter = moshi.adapter(CoursewareCatalog::class.java)


        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            try {
                response.body?.string()?.let {
                    val coursewareCatalog = adapter.fromJson(it)
                    coursewareCatalog?.nodes ?: emptyList()
//                    coursewareCatalog?.nodes?.drop(1) ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e("getCoursewareCatalog", "Error parsing JSON: ${e.message}")
                emptyList()
            } ?: throw IOException("Response body is null")
        }
    }

    suspend fun getCourseResourceResponse(course: Course, upId: Int = 0): CourseResourceResponse {
        initializationDeferred.await()
        val url =
            "http://123.121.147.7:88/ve/back/coursePlatform/courseResource.shtml?" +
                    "method=stuQueryUploadResourceForCourseList" +
                    "&courseId=${course.course_num}" +
                    "&cId=${course.course_num}" +
                    "&xkhId=${course.fz_id}" +
                    "&xqCode=${course.xq_code}" +
                    "&docType=1" +
                    "&up_id=${upId}" +
                    "&searchName="

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        val adapter = moshi.adapter(CourseResourceResponse::class.java)

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            response.body?.source()?.let { source ->
                try {
                    adapter.fromJson(source)
                } catch (e: Exception) {
                    println("Failed to parse JSON: ${e.message}")
                    null
                }
            } ?: throw IOException("Response body is null")
        }
    }

    suspend fun generateCoursewareTree(course: Course): CoursewareNode {
        initializationDeferred.await()
        val coursewareRootNode: CoursewareNode = CoursewareNode(id = 0, course = course)
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
            if (parentNode.course.name == "数字信号处理") {
                println("Hello")
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()
            val adapter = moshi.adapter(CourseResourceResponse::class.java)
            val response = client.newCall(request).execute()
            val responseContent = response.body?.string()
            responseContent?.let { str ->
                try {

//                    val a = str
//                    println(a)
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
    }


    private suspend fun getHomeWorkListByHomeworkType(homeworkType: Int): List<HomeworkEntity> {
        initializationDeferred.await()

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

class StringOrListAdapter<T>(private val listAdapter: JsonAdapter<List<T>>) :
    JsonAdapter<List<T>>() {
    @FromJson
    override fun fromJson(reader: JsonReader): List<T>? {
        if (reader.peek() == JsonReader.Token.STRING) {
            reader.nextString() // 消费空字符串
            return emptyList() // 返回空列表
        }
        return listAdapter.fromJson(reader) // 正常解析数组
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: List<T>?) {
        if (value == null || value.isEmpty()) {
            writer.value("") // 写入空字符串
        } else {
            listAdapter.toJson(writer, value) // 正常写入数组
        }
    }
}
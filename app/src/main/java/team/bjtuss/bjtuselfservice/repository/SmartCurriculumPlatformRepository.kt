package team.bjtuss.bjtuselfservice.repository


import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import team.bjtuss.bjtuselfservice.jsonclass.CourseJsonType
import team.bjtuss.bjtuselfservice.jsonclass.HomeworkJsonType
import team.bjtuss.bjtuselfservice.jsonclass.SemesterJsonType
import java.io.IOException


object SmartCurriculumPlatformRepository {
    private val client = StudentAccountManager.getInstance().client
    private val userAgent =
        "User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0',"
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

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
            .url(courseUrl).header(
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
            val response = client.newCall(homeworkRequest).execute()
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


    private suspend fun getHomeWorkListByHomeworkType(homeworkType: Int): List<HomeworkEntity> {
        val semesterFromJson = getSemesterTypeList()
        val courseFromJson =
            getCourseTypeList(xqCode = semesterFromJson.result[0].xqCode)
        val listFromJson = mutableListOf<HomeworkJsonType>()
        courseFromJson.courseList.forEach {
            val homeworkFromJson =
                getHomeworkList(
                    it.id.toString(),
                    homeworkType.toString()
                )
            listFromJson.add(homeworkFromJson)
        }

        val processedList = mutableListOf<HomeworkEntity>()

        listFromJson.forEach {
            it.courseNoteList.forEach { homework ->
                val homeworkEntity = HomeworkEntity(
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
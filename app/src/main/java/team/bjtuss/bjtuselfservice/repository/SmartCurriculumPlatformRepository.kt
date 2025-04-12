package team.bjtuss.bjtuselfservice.repository


import android.util.Log
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.dao.CoursewareDao
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.BagEntity
import team.bjtuss.bjtuselfservice.entity.CoursewareCourseEntity
import team.bjtuss.bjtuselfservice.entity.CoursewareNodeEntity
import team.bjtuss.bjtuselfservice.entity.CoursewareNodeWithChildren
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import team.bjtuss.bjtuselfservice.entity.ResEntity
import team.bjtuss.bjtuselfservice.jsonclass.Bag
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


object SmartCurriculumPlatformRepository {
    private val coursewareDao = AppDatabase.getInstance().coursewareDao()
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

    suspend fun generateCoursewareRootNode(coursewareCourseEntity: CoursewareCourseEntity): CoursewareNode {
        initializationDeferred.await()
        val coursewareRootNode: CoursewareNode =
            CoursewareNode(id = 0, course = coursewareCourseEntity)
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

    suspend fun saveCoursewareNode(
        node: CoursewareNode,
        courseEntity: CoursewareCourseEntity? = null
    ): Int {
        // 1. 保存课程信息
        val course = courseEntity ?: CoursewareCourseEntity(
            fz_id = node.course.fz_id,
            course_num = node.course.course_num,
            xq_code = node.course.xq_code,
            name = node.course.name
        )

        val courseId = if (course.id == 0) {
            coursewareDao.insertCourse(course).toInt()
        } else {
            course.id
        }

        // 2. 将节点转换为实体并保存
        val nodeEntities = convertNodeToEntities(node, courseId)
        coursewareDao.insertNodes(nodeEntities)

        return courseId
    }


    suspend fun getCoursewareNodesByCourseId(courseId: Int): CoursewareNode {
        val courseEntity = coursewareDao.getCourseById(courseId) ?: return CoursewareNode()

        // 获取所有节点
        val allNodes = coursewareDao.getAllNodesByCourseId(courseId)

        // 构建树结构
        val rootNodes = buildNodeTree(allNodes, courseEntity)

        // 创建一个虚拟的根节点，包含所有根节点作为子节点
        return CoursewareNode(
            id = 0, // 虚拟ID
            course = courseEntity,
            children = rootNodes
        )
    }

    suspend fun generateCoursewareRootNodeList(
        coursewareNodeEntityList: List<CoursewareNodeEntity>
    ): List<CoursewareNode> {
        // 从节点实体列表中提取所有唯一课程ID，然后为每个课程ID构建一个树
        return coursewareNodeEntityList
            .map { it.courseId }
            .toSet()  // 去重，确保每个课程只处理一次
            .map { courseId ->
                getCoursewareNodesByCourseId(courseId)
            }
    }

    suspend fun deleteCourse(courseId: Int) {
        val courseEntity = coursewareDao.getCourseById(courseId) ?: return
        coursewareDao.deleteCourse(courseEntity)
    }

    fun convertNodeToEntities(
        node: CoursewareNode,
        courseId: Int,
        parentId: Int? = null
    ): List<CoursewareNodeEntity> {
        // 创建当前节点的实体
        val nodeEntity = CoursewareNodeEntity(
            id = if (node.id == 0) 0 else node.id, // 如果id为0，让数据库自动生成
            courseId = courseId,
            parentId = parentId,
            res = node.res?.let {
                ResEntity(
                    rpId = it.rpId,
                    resId = it.resId,
                    rpName = it.rpName
                )
            },
            bag = node.bag?.let { BagEntity(it.id) }
        )

        // 递归处理子节点
        val childEntities = if (node.children.isNotEmpty()) {
            // 需要先插入当前节点以获取其ID，然后才能处理子节点
            // 这里假设nodeEntity已经被插入数据库并获得了ID
            node.children.flatMap { childNode ->
                convertNodeToEntities(childNode, courseId, nodeEntity.id)
            }
        } else {
            emptyList()
        }

        // 返回当前节点和所有子节点的平铺列表
        return listOf(nodeEntity) + childEntities
    }

    private fun buildNodeTree(
        allNodes: List<CoursewareNodeEntity>,
        coursewareCourseEntity: CoursewareCourseEntity
    ): List<CoursewareNode> {
        // 按父节点ID分组
        val nodesByParent = allNodes.groupBy { it.parentId }

        // 获取根节点（parentId为null的节点）
        val rootNodes = nodesByParent[null] ?: return emptyList()

        // 递归构建树形结构
        return rootNodes.map { rootEntity ->
            buildNode(rootEntity, nodesByParent, coursewareCourseEntity)
        }
    }

    // 递归构建节点及其子节点
    private fun buildNode(
        entity: CoursewareNodeEntity,
        nodesByParent: Map<Int?, List<CoursewareNodeEntity>>,
        coursewareCourseEntity: CoursewareCourseEntity
    ): CoursewareNode {
        val childEntities = nodesByParent[entity.id] ?: emptyList()

        return CoursewareNode(
            id = entity.id,
            course = coursewareCourseEntity,
            res = entity.res?.let {
                Res(
                    rpId = it.rpId,
                    resId = it.resId,
                    rpName = it.rpName
                    // 其他属性保持默认值
                )
            },
            bag = entity.bag?.let {
                Bag(id = it.badId)  // 这里的badId应该与BagEntity中的属性名一致
            },
            children = childEntities.map { childEntity ->
                buildNode(childEntity, nodesByParent, coursewareCourseEntity)
            }
        )
    }

}

//class CoursewareRepository(private val coursewareDao: CoursewareDao) {
//
//    suspend fun saveCoursewareNode(
//        node: CoursewareNode,
//        courseEntity: CoursewareCourseEntity? = null
//    ): Int {
//        // 1. 保存课程信息
//        val course = courseEntity ?: CoursewareCourseEntity(
//            fz_id = node.course.fz_id,
//            course_num = node.course.course_num,
//            xq_code = node.course.xq_code,
//            name = node.course.name
//        )
//
//        val courseId = if (course.id == 0) {
//            coursewareDao.insertCourse(course).toInt()
//        } else {
//            course.id
//        }
//
//        // 2. 将节点转换为实体并保存
//        val nodeEntities = convertNodeToEntities(node, courseId)
//        coursewareDao.insertNodes(nodeEntities)
//
//        return courseId
//    }
//
//    suspend fun getCoursewareNodesByCourseId(courseId: Int): CoursewareNode {
//        val courseEntity = coursewareDao.getCourseById(courseId) ?: return CoursewareNode()
//
//        val course = CoursewareCourseEntity(
//            id = courseEntity.id,
//            fz_id = courseEntity.fz_id ?: "",
//            course_num = courseEntity.course_num ?: "",
//            xq_code = courseEntity.xq_code ?: "",
//            name = courseEntity.name
//        )
//
//        // 获取所有节点
//        val allNodes = coursewareDao.getAllNodesByCourseId(courseId)
//
//        // 构建树结构
//        val rootNodes = buildNodeTree(allNodes, course)
//
//        // 创建一个虚拟的根节点，包含所有根节点作为子节点
//        return CoursewareNode(
//            id = 0, // 虚拟ID
//            course = course,
//            children = rootNodes
//        )
//    }
//
//    suspend fun getCoursewareNodeById(nodeId: Int): CoursewareNode? {
//        val nodeEntity = coursewareDao.getNodeById(nodeId) ?: return null
//        val courseEntity = coursewareDao.getCourseById(nodeEntity.courseId) ?: return null
//
//        val course = Course(
//            id = courseEntity.id,
//            fz_id = courseEntity.fz_id ?: "",
//            course_num = courseEntity.course_num ?: "",
//            xq_code = courseEntity.xq_code ?: "",
//            name = courseEntity.name
//        )
//
//        // 获取所有相关节点
//        val allNodes = coursewareDao.getAllNodesByCourseId(nodeEntity.courseId)
//
//        // 构建以请求的节点为根的子树
//        return buildNodeSubtree(nodeEntity, allNodes, course)
//    }
//
//    suspend fun deleteCourse(courseId: Int) {
//        val courseEntity = coursewareDao.getCourseById(courseId) ?: return
//        coursewareDao.deleteCourse(courseEntity)
//    }
//
//    fun convertNodeToEntities(
//        node: CoursewareNode,
//        courseId: Int,
//        parentId: Int? = null
//    ): List<CoursewareNodeEntity> {
//        // 创建当前节点的实体
//        val nodeEntity = CoursewareNodeEntity(
//            id = if (node.id == 0) 0 else node.id, // 如果id为0，让数据库自动生成
//            courseId = courseId,
//            parentId = parentId,
//            res = node.res?.let {
//                ResEntity(
//                    rpId = it.rpId,
//                    resId = it.resId,
//                    rpName = it.rpName
//                )
//            },
//            bag = node.bag?.let { BagEntity(it.id) }
//        )
//
//        val result = mutableListOf(nodeEntity)
//
//        // 递归处理子节点
//        if (node.children.isNotEmpty()) {
//            node.children.forEach { childNode ->
//                result.addAll(convertNodeToEntities(childNode, courseId, node.id))
//            }
//        }
//
//        return result
//    }
//
//    // 将平铺的节点列表转换为树形结构
//    private fun buildNodeTree(
//        allNodes: List<CoursewareNodeEntity>,
//        course: CoursewareCourseEntity
//    ): List<CoursewareNode> {
//        // 按父节点ID分组
//        val nodesByParent = allNodes.groupBy { it.parentId }
//
//        // 获取根节点（parentId为null的节点）
//        val rootNodes = nodesByParent[null] ?: return emptyList()
//
//        // 递归构建树形结构
//        return rootNodes.map { rootEntity ->
//            buildNode(rootEntity, nodesByParent, course)
//        }
//    }
//
//    // 根据节点ID构建子树
//    private fun buildNodeSubtree(
//        nodeEntity: CoursewareNodeEntity,
//        allNodes: List<CoursewareNodeEntity>,
//        course: Course
//    ): CoursewareNode {
//        val nodesByParent = allNodes.groupBy { it.parentId }
//        return buildNode(nodeEntity, nodesByParent, course)
//    }
//
//    // 递归构建节点及其子节点
//    private fun buildNode(
//        entity: CoursewareNodeEntity,
//        nodesByParent: Map<Int?, List<CoursewareNodeEntity>>,
//        course: Course
//    ): CoursewareNode {
//        val childEntities = nodesByParent[entity.id] ?: emptyList()
//
//        return CoursewareNode(
//            id = entity.id,
//            course = course,
//            res = entity.res?.let {
//                Res(rpId = it.rpId, resId = it.resId, rpName = it.rpName)
//            },
//            bag = entity.bag?.let {
//                Bag(id = it.badId)
//            },
//            children = childEntities.map { childEntity ->
//                buildNode(childEntity, nodesByParent, course)
//            }
//        )
//    }
//}

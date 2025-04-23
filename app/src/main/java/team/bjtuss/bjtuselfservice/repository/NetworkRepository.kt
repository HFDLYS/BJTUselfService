package team.bjtuss.bjtuselfservice.repository

import android.util.Log
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.controller.NetworkRequestQueue
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity
import team.bjtuss.bjtuselfservice.entity.GradeEntity
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository.client
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository.moshi
import team.bjtuss.bjtuselfservice.utils.NetworkUtils


object NetworkRepository {
    private val requestQueue = NetworkRequestQueue
    private val studentAccountManager = StudentAccountManager.getInstance()


    suspend fun getClassroomMap(): Map<String, MutableList<Int>>? {
        val result = requestQueue.enqueueHighPriority("ClassroomMap") {

            val classroomMap = try {
                studentAccountManager.getClassroom().await()
            } catch (e: Exception) {
                Log.e("NetworkRepository", "Error fetching classroom map: ${e.message}")
                emptyMap()
            }
            classroomMap
        }
        return result.getOrElse { emptyMap() }
    }


    suspend fun getExamScheduleList(): List<ExamScheduleEntity> {
        val result = requestQueue.enqueueLowPriorityQueue("ExamSchedule") {
            try {
                val result = studentAccountManager.getExamSchedule().await()
                result
            } catch (e: Exception) {
                Log.e("NetworkRepository", "Error fetching exam schedule list: ${e.message}")
                DatabaseRepository.getExamScheduleList()
            }
        }
        return result.getOrElse { emptyList() }
    }

    suspend fun getCourseList(): List<CourseEntity> {
        val result = requestQueue.enqueueLowPriorityQueue("CourseList") {
            val courseListOfOneDim: MutableList<CourseEntity> = mutableListOf()
            val preCourseList = DatabaseRepository.getCourseList();
            // 当前学期课程
            val currentTermCourses = try {
                studentAccountManager.getCourseList(true).await()
            } catch (e: Exception) {
                Log.e("NetworkRepository", "Error fetching current term course list: ${e.message}")
                studentAccountManager.courseListMap[true] ?: emptyList()
            }

            // 下学期课程
            val nextTermCourses = try {
                studentAccountManager.getCourseList(false).await()
            } catch (e: Exception) {
                Log.e("NetworkRepository", "Error fetching next term course list: ${e.message}")
                studentAccountManager.courseListMap[false] ?: emptyList()
            }

            // 合并课程列表
            currentTermCourses.forEach { termCourses ->
                if (!termCourses.isNullOrEmpty()) {
                    courseListOfOneDim.addAll(termCourses)
                }
            }

            if (currentTermCourses.isEmpty()) {
                for (course in preCourseList) {
                    if (course.isCurrentSemester) {
                        courseListOfOneDim.add(course)
                    }
                }
            }

            nextTermCourses.forEach { termCourses ->
                if (!termCourses.isNullOrEmpty()) {
                    courseListOfOneDim.addAll(termCourses)
                }
            }

            if (nextTermCourses.isEmpty()) {
                for (course in preCourseList) {
                    if (!course.isCurrentSemester) {
                        courseListOfOneDim.add(course)
                    }
                }
            }


            courseListOfOneDim
        }
        return result.getOrElse { emptyList() }
    }

    suspend fun getGradeList(): List<GradeEntity> {
        val result = requestQueue.enqueueLowPriorityQueue("GradeList") {
            val lnGrades = try {
                studentAccountManager.getGrade("ln").await()
            } catch (e: Exception) {
                Log.e("NetworkRepository", "Error fetching ln grade list: ${e.message}")
                studentAccountManager.gradeMap["ln"] ?: emptyList()
            }
            val lrGrades = try {
                studentAccountManager.getGrade("lr").await()
            } catch (e: Exception) {
                Log.e("NetworkRepository", "Error fetching lr grade list: ${e.message}")
                studentAccountManager.gradeMap["lr"] ?: emptyList()
            }


            (lnGrades + lrGrades).distinctBy {
                Triple(it.courseName, it.courseScore, it.courseCredits)
            }
        }
        return result.getOrElse { emptyList() }
    }

    suspend fun loadCurrentWeek(): Int {
        val url = "http://123.121.147.7:88/ve/back/coursePlatform/course.shtml?method=getTimeList"
        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val adapter = moshi.adapter<Map<String, Any>>(type)
        return withContext(Dispatchers.IO) {
            val currentWeek =
                try {
                    NetworkUtils.get(client, url)
                } catch (e: Exception) {
                    Log.e(
                        "SmartCurriculumPlatformRepository",
                        "Failed to get current week: ${e.message}"
                    )
                    return@withContext 0
                }.let { str ->
                    try {
                        val jsonMap = adapter.fromJson(str)
                        (jsonMap?.get("weekCode") as String).toIntOrNull() ?: 0
                    } catch (e: Exception) {
                        println("Failed to parse JSON: ${e.message}")
                        0
                    }
                }
            DataStoreRepository.setCurrentWeek(currentWeek)
            currentWeek
        }
    }


}



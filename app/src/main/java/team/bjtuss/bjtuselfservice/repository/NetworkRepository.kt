package team.bjtuss.bjtuselfservice.repository

import android.util.Log
import androidx.lifecycle.LiveData
import kotlinx.coroutines.future.await
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.controller.NetworkRequestQueue
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity
import team.bjtuss.bjtuselfservice.entity.GradeEntity


object NetworkRepository {
    private val requestQueue = NetworkRequestQueue
    private val studentAccountManager = StudentAccountManager.getInstance()


    suspend fun getClassroomMap(): Map<String, MutableList<Int>>? {
        val result = requestQueue.enqueue {

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
        val result = requestQueue.enqueue {
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
        val result = requestQueue.enqueue {
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
        val result = requestQueue.enqueue {
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



}



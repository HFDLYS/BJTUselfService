package team.bjtuss.bjtuselfservice.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.entity.GradeEntity


object NetWorkRepository {
    private val studentAccountManager = StudentAccountManager.getInstance()


    private var _classroomMap =
        MutableStateFlow<MutableMap<String, List<Int>>>(mutableMapOf())
    val classroomMap: StateFlow<MutableMap<String, List<Int>>> = _classroomMap.asStateFlow()


    init {
        loadClassroomMap()

    }


    suspend fun getCurrentTermCourseList(): List<CourseEntity> {
        return withContext(Dispatchers.IO) {
            val currentTermCourseListOfTwoDim = studentAccountManager.getCourseList(true).await()
            val currentTermCourseListOfOneDim: MutableList<CourseEntity> = mutableListOf()
            currentTermCourseListOfTwoDim.forEach {
                if (it == null) {
                    return@forEach
                }
                if (it.isEmpty()) return@forEach
                it.forEach {
                    currentTermCourseListOfOneDim.add(it)
                }
            }
            currentTermCourseListOfOneDim
        }
    }

    suspend fun getNextTermCourseList(): List<CourseEntity> {
        return withContext(Dispatchers.IO) {
            val nextTermCourseListOfTwoDim = studentAccountManager.getCourseList(false).await()
            val nextTermCourseListOfOneDim: MutableList<CourseEntity> = mutableListOf()
            nextTermCourseListOfTwoDim.forEach {
                if (it == null || it.isEmpty()) return@forEach
                it.forEach {
                    nextTermCourseListOfOneDim.add(it)
                }
            }
            nextTermCourseListOfOneDim
        }
    }

    suspend fun getCourseList(): List<CourseEntity> {
        return withContext(Dispatchers.IO) {
            val currentTermCourseListOfTwoDim = async {
                try {
                    studentAccountManager.getCourseList(true).await()
                } catch (e: Exception) {
                    println("Error fetching current term course list: ${e.message}")
                    studentAccountManager.courseListMap[true] ?: emptyList()
                }
            }

            val nextTermCourseListOfTwoDim = async {
                try {
                    studentAccountManager.getCourseList(false).await()
                } catch (e: Exception) {
                    println("Error fetching next term course list: ${e.message}")
                    studentAccountManager.courseListMap[false] ?: emptyList()
                }
            }
            val courseListOfOneDim: MutableList<CourseEntity> = mutableListOf()
            currentTermCourseListOfTwoDim.await().forEach {
                if (it == null || it.isEmpty()) {
                    return@forEach
                }
                it.forEach {
                    courseListOfOneDim.add(it)
                }
            }
            nextTermCourseListOfTwoDim.await().forEach {
                if (it == null || it.isEmpty()) return@forEach
                it.forEach {
                    courseListOfOneDim.add(it)
                }
            }
            courseListOfOneDim
        }
    }


    fun loadClassroomMap() {
        try {
            studentAccountManager.getClassroom().thenAccept {
                _classroomMap.value.putAll(it)
            }
        } catch (e: Exception) {
            handleClassroomLoginError(e)
        }
    }


    suspend fun getGradeList(): List<GradeEntity> {
        val gradeList = withContext(Dispatchers.IO) {
            val lnGradesDeferred = async {
                try {
                    studentAccountManager.getGrade("ln").await()
                } catch (e: Exception) {
                    println("Error fetching ln grade list: ${e.message}")
                    studentAccountManager.gradeMap["ln"] ?: emptyList()
                }
            }

            val lrGradesDeferred = async {
                try {
                    studentAccountManager.getGrade("lr").await()
                } catch (e: Exception) {
                    println("Error fetching lr grade list: ${e.message}")
                    studentAccountManager.gradeMap["lr"] ?: emptyList()
                }
            }


            // Wait for both requests and combine results
            val lnGrades = lnGradesDeferred.await()
            val lrGrades = lrGradesDeferred.await()
            val combinedGrades = (lnGrades + lrGrades).distinctBy {
                Triple(it.courseName, it.courseScore, it.courseCredits)
            }
            combinedGrades

        }
        return gradeList
    }


    private fun handleClassroomLoginError(throwable: Throwable) {
        when (throwable.message) {
            "Not loginAa", "Not login" -> {
                studentAccountManager.loginAa().thenAccept {
                    if (it) {
                        loadClassroomMap()
                    }
                }
            }

            else -> _classroomMap = MutableStateFlow(mutableMapOf())
        }
    }

//    private fun handleGradeLoginError(throwable: Throwable) {
//        when (throwable.message) {
//            "Not loginAa", "Not login" -> {
//                val loginSuccessful = studentAccountManager.loginAa().thenAccept {
//                    if (it) {
//                        loadGradeList()
//                    }
//                }
//            }
//
//            else -> _gradeList.value = mutableListOf()
//        }
//    }
}


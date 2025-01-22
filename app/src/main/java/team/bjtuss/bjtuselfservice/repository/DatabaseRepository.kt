package team.bjtuss.bjtuselfservice.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity
import team.bjtuss.bjtuselfservice.entity.GradeEntity

object DatabaseRepository {
    private val gradeEntityDao =
        AppDatabase.getInstance().gradeEntityDao()
    private val courseEntityDao =
        AppDatabase.getInstance().courseEntityDao()
    private val examScheduleEntityDao =
        AppDatabase.getInstance().examScheduleEntityDao()

    private var _gradeList =
        MutableStateFlow<List<GradeEntity>>(mutableListOf())
    val gradeList: StateFlow<List<GradeEntity>> = _gradeList.asStateFlow()

    private var _examScheduleList =
        MutableStateFlow<List<ExamScheduleEntity>>(mutableListOf())
    val examScheduleList: StateFlow<List<ExamScheduleEntity>> = _examScheduleList.asStateFlow()

    private var _classroomMap =
        MutableStateFlow<Map<String, MutableList<Int>>>(mutableMapOf())
    val classroomMap: StateFlow<Map<String, MutableList<Int>>> = _classroomMap.asStateFlow()


    private var _currentTermCourseList =
        MutableStateFlow<List<List<CourseEntity>>>(mutableListOf())
    val currentTermCourseList: StateFlow<List<List<CourseEntity>>> =
        _currentTermCourseList.asStateFlow()


    private var _nextTermCourseList =
        MutableStateFlow<List<List<CourseEntity>>>(mutableListOf())
    val nextTermCourseList: StateFlow<List<List<CourseEntity>>> =
        _nextTermCourseList.asStateFlow()

    init {
        observeGradeList()
        observeCourseList(true)  // 本学期课程
        observeCourseList(false) // 下学期课程
        observeExamScheduleList()
    }


    fun observeGradeList() {
        CoroutineScope(Dispatchers.IO).launch {
            gradeEntityDao.getAll().collect {
                _gradeList.value = it
            }
        }
    }


    // 通用的课程列表观察方法
    private fun observeCourseList(isCurrentTerm: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            courseEntityDao.getCurrentSemesterCourseBySemester(isCurrentTerm)
                .collect { courseListOfOneDim ->
                    // 将单维列表转换为二维列表
                    val courseListOfTwoDim = List(56) { mutableListOf<CourseEntity>() }
                    courseListOfOneDim.forEach {
                        courseListOfTwoDim[it.courseLocationIndex].add(it)
                    }

                    // 更新相应的 StateFlow
                    if (isCurrentTerm) {
                        _currentTermCourseList.value = courseListOfTwoDim
                    } else {
                        _nextTermCourseList.value = courseListOfTwoDim
                    }
                }
        }
    }

    // 烤熟安排的观察方法
    private fun observeExamScheduleList() {
        CoroutineScope(Dispatchers.IO).launch {
            examScheduleEntityDao.getAll().collect {
                _examScheduleList.value = it
            }
        }
    }

    fun observeClassroomMap() {
        CoroutineScope(Dispatchers.IO).launch {

        }
    }


    suspend fun getCourseList(): List<CourseEntity> {
        return withContext(Dispatchers.IO) {
            courseEntityDao.getAll().first()
        }
    }


    suspend fun getGradeList(): List<GradeEntity> {
        return withContext(Dispatchers.IO) {
            gradeEntityDao.getAll().first()
        }
    }

    suspend fun getExamScheduleList(): List<ExamScheduleEntity> {
        return withContext(Dispatchers.IO) {
            examScheduleEntityDao.getAll().first()
        }
    }



}
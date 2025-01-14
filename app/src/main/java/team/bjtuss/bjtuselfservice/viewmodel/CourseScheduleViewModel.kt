package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.StudentAccountManager.Course
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CourseScheduleViewModel : ViewModel() {
    private val studentAccountManager = StudentAccountManager.getInstance()

    private var _currentTermCourseList =
        MutableStateFlow<MutableList<MutableList<Course>>>(mutableListOf())
    val currentTermCourseList: StateFlow<MutableList<MutableList<Course>>> =
        _currentTermCourseList.asStateFlow()

    private var _nextTermCourseList =
        MutableStateFlow<MutableList<MutableList<Course>>>(mutableListOf())
    val nextTermCourseList: StateFlow<MutableList<MutableList<Course>>> =
        _nextTermCourseList.asStateFlow()


    init {

        studentAccountManager.getCourseList(false).thenAccept { courseList ->
//            _currentTermCourseList.update { currentList ->
//                currentList.apply {
//                    courses.filterNotNull().forEach { add(it) }
//                }
//            }
            _currentTermCourseList.value.addAll(courseList)
        }

        studentAccountManager.getCourseList(true).thenAccept {
            _nextTermCourseList.value.addAll(it)
        }
    }


}
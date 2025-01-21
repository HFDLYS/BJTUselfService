package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainViewModel(
    val gradeViewModel: GradeViewModel,
    val courseScheduleViewModel: CourseScheduleViewModel,
    val classroomViewModel: ClassroomViewModel
) : ViewModel()


class MainViewModelFactory(
    private val gradeViewModel: GradeViewModel,
    private val courseScheduleViewModel: CourseScheduleViewModel,
    private val classroomViewModel: ClassroomViewModel
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(gradeViewModel, courseScheduleViewModel, classroomViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
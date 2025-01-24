package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainViewModel(
    val gradeViewModel: GradeViewModel,
    val courseScheduleViewModel: CourseScheduleViewModel,
    val examScheduleViewModel: ExamScheduleViewModel,
    val classroomViewModel: ClassroomViewModel,
    val homeworkViewModel: HomeworkViewModel,
    val statusViewModel: StatusViewModel,
) : ViewModel() {
    fun loadDataAndDetectChanges() {
        gradeViewModel.loadDataAndDetectChanges()
        courseScheduleViewModel.loadDataAndDetectChanges()
        examScheduleViewModel.loadDataAndDetectChanges()
        homeworkViewModel.loadDataAndDetectChanges()
        statusViewModel.loadData()
    }

    fun clearChange() {
        gradeViewModel.clearChange()
        courseScheduleViewModel.clearChange()
        examScheduleViewModel.clearChange()
        homeworkViewModel.clearChange()
    }
}


class MainViewModelFactory(
    private val gradeViewModel: GradeViewModel,
    private val courseScheduleViewModel: CourseScheduleViewModel,
    private val examScheduleViewModel: ExamScheduleViewModel,
    private val classroomViewModel: ClassroomViewModel,
    private val homeworkViewModel: HomeworkViewModel,
    private val statusViewModel: StatusViewModel
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                gradeViewModel,
                courseScheduleViewModel,
                examScheduleViewModel,
                classroomViewModel,
                homeworkViewModel,
                statusViewModel
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
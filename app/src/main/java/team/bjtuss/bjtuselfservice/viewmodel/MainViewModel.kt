package team.bjtuss.bjtuselfservice.viewmodel

import android.util.Printer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainViewModel(
    val gradeViewModel: GradeViewModel,
    val courseScheduleViewModel: CourseScheduleViewModel,
    val examScheduleViewModel: ExamScheduleViewModel,
    val homeworkViewModel: HomeworkViewModel
) : ViewModel() {
     fun loadDataAndDetectChanges() {
        gradeViewModel.loadDataAndDetectChanges()
        courseScheduleViewModel.loadDataAndDetectChanges()
        examScheduleViewModel.loadDataAndDetectChanges()
        homeworkViewModel.loadDataAndDetectChanges()
     }
}


class MainViewModelFactory(
    private val gradeViewModel: GradeViewModel,
    private val courseScheduleViewModel: CourseScheduleViewModel,
    private val examScheduleViewModel: ExamScheduleViewModel,
    private val homeworkViewModel: HomeworkViewModel
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                gradeViewModel,
                courseScheduleViewModel,
                examScheduleViewModel,
                homeworkViewModel
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
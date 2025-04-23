package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.statemanager.AppEvent
import team.bjtuss.bjtuselfservice.statemanager.AppEventManager


class MainViewModel(
    val gradeViewModel: GradeViewModel,
    val courseScheduleViewModel: CourseScheduleViewModel,
    val examScheduleViewModel: ExamScheduleViewModel,
    val classroomViewModel: ClassroomViewModel,
    val homeworkViewModel: HomeworkViewModel,
    val statusViewModel: StatusViewModel,
    val settingViewModel: SettingViewModel,
    val coursewareViewModel: CoursewareViewModel,
) : ViewModel() {

    init {
        viewModelScope.launch {
            AppEventManager.events.collectLatest {
                when (it) {
                    is AppEvent.DataSyncRequest -> {
                        println("这里执行了一次")
                        loadDataAndDetectChanges()
                    }

                    else -> {}
                }
            }
        }
    }

    fun loadDataAndDetectChanges() {
        statusViewModel.loadData()
        gradeViewModel.loadDataAndDetectChanges()
        courseScheduleViewModel.loadDataAndDetectChanges()
        examScheduleViewModel.loadDataAndDetectChanges()
        homeworkViewModel.loadDataAndDetectChanges()
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
    private val statusViewModel: StatusViewModel,
    private val settingViewModel: SettingViewModel,
    private val coursewareViewModel: CoursewareViewModel,

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
                statusViewModel,
                settingViewModel,
                coursewareViewModel,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
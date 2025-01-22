package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity
import team.bjtuss.bjtuselfservice.repository.DatabaseRepository
import team.bjtuss.bjtuselfservice.repository.NetWorkRepository

class ExamScheduleViewModel : BaseSyncViewModel<ExamScheduleEntity>(
    dataSyncManager = DefaultDataSyncManager<ExamScheduleEntity>(
        AppDatabase.getInstance().examScheduleEntityDao()
    ) { it.examType + it.courseName }
) {

    val examScheduleList: StateFlow<List<ExamScheduleEntity>> = DatabaseRepository.examScheduleList

    init {
        loadDataAndDetectChanges()
    }

    override suspend fun fetchNetworkData(): List<ExamScheduleEntity> {
        return NetWorkRepository.getExamScheduleList()
    }

    override suspend fun fetchLocalData(): List<ExamScheduleEntity> {
        return DatabaseRepository.getExamScheduleList()
    }

}
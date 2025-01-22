package team.bjtuss.bjtuselfservice.viewmodel

import kotlinx.coroutines.flow.StateFlow
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity
import team.bjtuss.bjtuselfservice.repository.DatabaseRepository
import team.bjtuss.bjtuselfservice.repository.NetworkRepository

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
        return NetworkRepository.getExamScheduleList()
    }

    override suspend fun fetchLocalData(): List<ExamScheduleEntity> {
        return DatabaseRepository.getExamScheduleList()
    }

}
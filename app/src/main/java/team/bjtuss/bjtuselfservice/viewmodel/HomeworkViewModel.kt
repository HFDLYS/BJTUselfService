package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import team.bjtuss.bjtuselfservice.jsonclass.HomeworkJsonType
import team.bjtuss.bjtuselfservice.repository.DatabaseRepository
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository

class HomeworkViewModel :
    BaseSyncViewModel<HomeworkEntity>(dataSyncManager = DefaultDataSyncManager<HomeworkEntity>(
        AppDatabase.getInstance().homeworkEntityDao()
    ) { Pair(it.courseName, it.title) }) {
    val homeworkList = DatabaseRepository.homeworkList

    init {
        loadDataAndDetectChanges()
    }

    override suspend fun fetchNetworkData(): List<HomeworkEntity> {
        return SmartCurriculumPlatformRepository.getHomework()

    }

    override suspend fun fetchLocalData(): List<HomeworkEntity> {
        return DatabaseRepository.getHomeworkList()
    }
}
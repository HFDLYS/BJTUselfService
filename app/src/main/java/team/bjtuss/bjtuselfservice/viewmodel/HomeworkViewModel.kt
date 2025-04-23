package team.bjtuss.bjtuselfservice.viewmodel

import kotlinx.coroutines.flow.StateFlow
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import team.bjtuss.bjtuselfservice.repository.DatabaseRepository
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository

class HomeworkViewModel :
    BaseSyncViewModel<HomeworkEntity>(dataSyncManager = DefaultDataSyncManager<HomeworkEntity>(
        dao = AppDatabase.getInstance().homeworkEntityDao(),
        inEqualitySelector = { networkData, localData ->
            localData.idSnId = networkData.idSnId
            networkData != localData

        },
    ) { Pair(it.courseName, it.title) }) {
    val homeworkList: StateFlow<List<HomeworkEntity>> = DatabaseRepository.homeworkList

    init {
//        loadDataAndDetectChanges()

    }

    override suspend fun fetchNetworkData(): List<HomeworkEntity> {

        return SmartCurriculumPlatformRepository.getHomework() + SmartCurriculumPlatformRepository.getCourseDesign() + SmartCurriculumPlatformRepository.getExperimentReport()

    }

    override suspend fun fetchLocalData(): List<HomeworkEntity> {
        return DatabaseRepository.getHomeworkList()
    }
}
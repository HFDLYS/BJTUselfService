package team.bjtuss.bjtuselfservice.viewmodel

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import team.bjtuss.bjtuselfservice.component.getHomeworkGrade
import team.bjtuss.bjtuselfservice.controller.NetworkRequestQueue
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
//
//    override suspend fun fetchNetworkData(): List<HomeworkEntity> {
//
//        return SmartCurriculumPlatformRepository.getHomework() + SmartCurriculumPlatformRepository.getCourseDesign() + SmartCurriculumPlatformRepository.getExperimentReport()
//
//    }

    override suspend fun fetchNetworkData(): List<HomeworkEntity> = coroutineScope {
        // 1. 串行获取所有作业列表
        val initialHomeworkList = SmartCurriculumPlatformRepository.getHomework() +
                SmartCurriculumPlatformRepository.getCourseDesign() +
                SmartCurriculumPlatformRepository.getExperimentReport()

        // 2. 将列表映射为一系列并发任务 (Deferred<HomeworkEntity>)
        val deferredHomeworks = initialHomeworkList.map { homework ->
            async {
                if (homework.scoreId != 0) {

                    val score = getHomeworkGrade(homework)

                    homework.copy(score = score)
                } else {
                    homework
                }
            }
        }

        // 3. 等待所有异步任务完成，并返回更新后的列表
        return@coroutineScope deferredHomeworks.awaitAll()
    }

    override suspend fun fetchLocalData(): List<HomeworkEntity> {
        return DatabaseRepository.getHomeworkList()
    }
}
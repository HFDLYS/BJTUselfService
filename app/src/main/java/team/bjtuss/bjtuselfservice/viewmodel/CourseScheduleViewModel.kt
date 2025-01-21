package team.bjtuss.bjtuselfservice.viewmodel

import kotlinx.coroutines.flow.StateFlow
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.repository.DatabaseRepository
import team.bjtuss.bjtuselfservice.repository.NetWorkRepository



class CourseScheduleViewModel : BaseSyncViewModel<CourseEntity>(
    dataSyncManager = DefaultDataSyncManager<CourseEntity>(
        AppDatabase.getInstance().courseEntityDao()
    ) {
        Pair(it.courseId, it.courseLocationIndex)
    }
) {

    val currentTermCourseList: StateFlow<List<List<CourseEntity>>> =
        DatabaseRepository.currentTermCourseList

    val nextTermCourseList: StateFlow<List<List<CourseEntity>>> =
        DatabaseRepository.nextTermCourseList


    init {
        loadDataAndDetectChanges()
    }

    override suspend fun fetchNetworkData(): List<CourseEntity> {
        return NetWorkRepository.getCourseList()
    }

    override suspend fun fetchLocalData(): List<CourseEntity> {
        return DatabaseRepository.getCourseList()
    }
}
package team.bjtuss.bjtuselfservice.viewmodel

import kotlinx.coroutines.flow.StateFlow
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.GradeEntity
import team.bjtuss.bjtuselfservice.repository.DatabaseRepository
import team.bjtuss.bjtuselfservice.repository.NetworkRepository


class GradeViewModel : BaseSyncViewModel<GradeEntity>(
    dataSyncManager = DefaultDataSyncManager<GradeEntity>(
        AppDatabase.getInstance().gradeEntityDao()
    ) { it.courseName }
) {

    val gradeList: StateFlow<List<GradeEntity>> = DatabaseRepository.gradeList

    init {
//        loadDataAndDetectChanges()
    }

    override suspend fun fetchNetworkData(): List<GradeEntity> {
        return NetworkRepository.getGradeList()
    }

    override suspend fun fetchLocalData(): List<GradeEntity> {
        return DatabaseRepository.getGradeList()
    }
}

//class GradeViewModel() : ViewModel() {
//
//    val gradeList: StateFlow<List<GradeEntity>> = DatabaseRepository.gradeList
//
//    private val _gradeChange = MutableStateFlow<List<DataChange<GradeEntity>>>(mutableListOf())
//    val gradeChange: StateFlow<List<DataChange<GradeEntity>>> = _gradeChange.asStateFlow()
//
//    private val gradeEntityDao = AppDatabase.getInstance().gradeEntityDao()
//
//    private val dataSyncManager =
//        DefaultDataSyncManager<GradeEntity>(gradeEntityDao) { it.courseName }
//
//
//    init {
//        loadDataAndDetectChanges()
//    }
//
//
//    fun loadDataAndDetectChanges() {
//
//        viewModelScope.launch {
//            val networkData = NetWorkRepository.getGradeList()
//            val localData = DatabaseRepository.getGradeList()
//            val changes = dataSyncManager.detectChanges(networkData, localData)
//            _gradeChange.value = changes
//
//        }
//    }
//
//    fun syncDataAndClearChange() {
//        viewModelScope.launch {
//            dataSyncManager.applyChanges(_gradeChange.value)
//            _gradeChange.value = emptyList()
//        }
//    }
//}

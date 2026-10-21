package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.MainApplication
import team.bjtuss.bjtuselfservice.repository.ClassroomOccupancyCache
import team.bjtuss.bjtuselfservice.repository.NetworkRepository.getClassroomMap

class ClassroomViewModel : ViewModel() {
    private var _classroomMap =
        MutableStateFlow<MutableMap<String, List<Int>>>(mutableMapOf())
    val classroomMap: StateFlow<MutableMap<String, List<Int>>> = _classroomMap.asStateFlow()
    private var refreshJob: Job? = null

    init {
        _classroomMap.value = ClassroomOccupancyCache.loadToday(
            MainApplication.appContext
        ).toMutableMap()
        viewModelScope.launch {
            while (isActive) {
                refreshClassroomMap()
                delay(5 * 60 * 1000L)
            }
        }
    }

    fun refreshClassroomMap() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val fetched = getClassroomMap().orEmpty()
            if (fetched.isNotEmpty()) {
                val normalized = fetched.mapValues { it.value.toList() }
                _classroomMap.value = normalized.toMutableMap()
                ClassroomOccupancyCache.saveToday(MainApplication.appContext, normalized)
            }
        }
    }
}

package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.repository.NetworkRepository.getClassroomMap

class ClassroomViewModel : ViewModel() {
    private var _classroomMap =
        MutableStateFlow<MutableMap<String, List<Int>>>(mutableMapOf())
    val classroomMap: StateFlow<MutableMap<String, List<Int>>> = _classroomMap.asStateFlow()

    init {
        viewModelScope.launch {
            val map = getClassroomMap() ?: emptyMap()
            _classroomMap.value = map.toMutableMap()
        }
    }
}
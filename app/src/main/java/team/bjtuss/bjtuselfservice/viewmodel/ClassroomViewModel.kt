package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.repository.NetWorkRepository

class ClassroomViewModel : ViewModel() {
    val classroomMap: StateFlow<Map<String, List<Int>>> =
        NetWorkRepository.classroomMap
}
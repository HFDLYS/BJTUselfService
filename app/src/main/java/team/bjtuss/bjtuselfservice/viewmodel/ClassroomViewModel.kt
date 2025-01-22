package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import team.bjtuss.bjtuselfservice.repository.NetworkRepository

class ClassroomViewModel : ViewModel() {
    val classroomMap: StateFlow<Map<String, List<Int>>> =
        NetworkRepository.classroomMap
}
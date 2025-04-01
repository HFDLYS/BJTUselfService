package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.StudentAccountManager.Status
import team.bjtuss.bjtuselfservice.repository.NetworkRequestQueue
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository

class StatusViewModel : ViewModel() {
    private val _status: MutableStateFlow<Status> = MutableStateFlow(Status())
    val status = _status.asStateFlow()
    private val _currentWeek: MutableStateFlow<Int> = MutableStateFlow(0)
    val currentWeek = _currentWeek.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {


        StudentAccountManager.getInstance().status.thenAccept {
            _status.value = it
        }

        viewModelScope.launch(Dispatchers.IO) {
            val week = SmartCurriculumPlatformRepository.getCurrentWeek()
            _currentWeek.value = week

        }


    }


}
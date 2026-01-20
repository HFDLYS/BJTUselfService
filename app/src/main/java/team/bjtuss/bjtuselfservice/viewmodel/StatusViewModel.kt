package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.StudentAccountManager.Status
import team.bjtuss.bjtuselfservice.repository.NetworkRepository

class StatusViewModel : ViewModel() {
    private val _status: MutableStateFlow<Status> = MutableStateFlow(Status())
    val status = _status.asStateFlow()

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
//            _currentWeek.value = week
//            DataStoreRepository.setCurrentWeek(week)
            NetworkRepository.loadCurrentWeek()
        }

        StudentAccountManager.getInstance().status.thenAccept {
            _status.value = it
        }




    }


}
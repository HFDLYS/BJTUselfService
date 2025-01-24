package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.StudentAccountManager.Status

class StatusViewModel : ViewModel() {
    private val _status: MutableStateFlow<Status> = MutableStateFlow(Status())
    val status = _status.asStateFlow()

    init {
        StudentAccountManager.getInstance().status.thenAccept {
            _status.value = it
        }
    }

    fun loadData() {
        StudentAccountManager.getInstance().status.thenAccept {
            _status.value = it
        }
    }


}
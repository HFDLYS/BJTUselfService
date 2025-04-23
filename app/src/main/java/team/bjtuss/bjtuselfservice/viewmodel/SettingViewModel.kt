package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository

class SettingViewModel : ViewModel() {
    private val _autoSyncGradeEnable = MutableStateFlow(false)
    val autoSyncGradeEnable = _autoSyncGradeEnable.asStateFlow()

    private val _autoSyncHomeworkEnable = MutableStateFlow(false)
    val autoSyncHomeworkEnable = _autoSyncHomeworkEnable.asStateFlow()

    private val _autoSyncScheduleEnable = MutableStateFlow(false)
    val autoSyncScheduleEnable = _autoSyncScheduleEnable.asStateFlow()

    private val _autoSyncExamEnable = MutableStateFlow(false)
    val autoSyncExamEnable = _autoSyncExamEnable.asStateFlow()

    private val _checkUpdateEnable = MutableStateFlow(true)
    val checkUpdateEnable = _checkUpdateEnable.asStateFlow()


    init {
        viewModelScope.launch {
            DataStoreRepository.getGradeAutoSyncOption().collect {
                _autoSyncGradeEnable.value = it
            }
        }

        viewModelScope.launch {
            DataStoreRepository.getHomeworkAutoSyncOption().collect {
                _autoSyncHomeworkEnable.value = it
            }
        }

        viewModelScope.launch {
            DataStoreRepository.getScheduleAutoSyncOption().collect {
                _autoSyncScheduleEnable.value = it
            }
        }

        viewModelScope.launch {
            DataStoreRepository.getExamAutoSyncOption().collect {
                _autoSyncExamEnable.value = it
            }
        }
        viewModelScope.launch {
            DataStoreRepository.getCheckUpdateOption().collect {
                _checkUpdateEnable.value = it
            }
        }
    }

    fun setGradeAutoSyncOption(enabled: Boolean) {
        viewModelScope.launch {
            DataStoreRepository.setGradeAutoSyncOption(enabled)
        }
    }

    fun setHomeworkAutoSyncOption(enabled: Boolean) {
        viewModelScope.launch {
            DataStoreRepository.setHomeworkAutoSyncOption(enabled)
        }
    }

    fun setScheduleAutoSyncOption(enabled: Boolean) {
        viewModelScope.launch {
            DataStoreRepository.setScheduleAutoSyncOption(enabled)
        }
    }

    fun setAutoSyncExamsEnable(enabled: Boolean) {
        viewModelScope.launch {
            DataStoreRepository.setExamsAutoSyncOption(enabled)
        }
    }

    fun setCheckUpdateEnable(enabled: Boolean) {
        viewModelScope.launch {
            DataStoreRepository.setCheckUpdateOption(enabled)
        }
    }
}
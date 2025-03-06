package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.repository.SettingRepository

class SettingViewModel : ViewModel() {
    private val _autoSyncGradeEnable = MutableStateFlow(false)
    val autoSyncGradeEnable = _autoSyncGradeEnable.asStateFlow()

    private val _autoSyncHomeworkEnable = MutableStateFlow(false)
    val autoSyncHomeworkEnable = _autoSyncHomeworkEnable.asStateFlow()

    private val _autoSyncScheduleEnable = MutableStateFlow(false)
    val autoSyncScheduleEnable = _autoSyncScheduleEnable.asStateFlow()

    private val _autoSyncExamEnable = MutableStateFlow(false)
    val autoSyncExamEnable = _autoSyncExamEnable.asStateFlow()

    init {
        viewModelScope.launch {
            SettingRepository.getGradeAutoSyncOption().collect {
                _autoSyncGradeEnable.value = it
            }
        }

        viewModelScope.launch {
            SettingRepository.getHomeworkAutoSyncOption().collect {
                _autoSyncHomeworkEnable.value = it
            }
        }

        viewModelScope.launch {
            SettingRepository.getScheduleAutoSyncOption().collect {
                _autoSyncScheduleEnable.value = it
            }
        }

        viewModelScope.launch {
            SettingRepository.getExamAutoSyncOption().collect {
                _autoSyncExamEnable.value = it
            }
        }
    }

    fun setGradeAutoSyncOption(enabled: Boolean) {
        viewModelScope.launch {
            SettingRepository.setGradeAutoSyncOption(enabled)
        }
    }

    fun setHomeworkAutoSyncOption(enabled: Boolean) {
        viewModelScope.launch {
            SettingRepository.setHomeworkAutoSyncOption(enabled)
        }
    }

    fun setScheduleAutoSyncOption(enabled: Boolean) {
        viewModelScope.launch {
            SettingRepository.setScheduleAutoSyncOption(enabled)
        }
    }

    fun setAutoSyncExamsEnable(enabled: Boolean) {
        viewModelScope.launch {
            SettingRepository.setExamsAutoSyncOption(enabled)
        }
    }
}
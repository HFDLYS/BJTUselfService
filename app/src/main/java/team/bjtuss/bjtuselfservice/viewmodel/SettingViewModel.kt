package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import team.bjtuss.bjtuselfservice.ui.theme.Theme


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

    private val _currentTheme: MutableStateFlow<Theme> = MutableStateFlow(Theme.System)
    val currentTheme = _currentTheme.asStateFlow()

    private val _dynamicColorEnable = MutableStateFlow(false)
    val dynamicColorEnable = _dynamicColorEnable.asStateFlow()


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
        viewModelScope.launch {
            DataStoreRepository.getTheme().collect {
                _currentTheme.value = when (it) {
                    Theme.Light.themeString -> Theme.Light
                    Theme.Dark.themeString -> Theme.Dark
                    Theme.System.themeString -> Theme.System
                    else -> Theme.System
                }
            }
        }
        viewModelScope.launch {
            DataStoreRepository.getDynamicColorOption().collect {
                _dynamicColorEnable.value = it
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

    fun setExamsAutoSyncOption(enabled: Boolean) {
        viewModelScope.launch {
            DataStoreRepository.setExamsAutoSyncOption(enabled)
        }
    }

    fun setCheckUpdateEnable(enabled: Boolean) {
        viewModelScope.launch {
            DataStoreRepository.setCheckUpdateOption(enabled)
        }
    }

    fun setThemeOption(theme: Theme) {
        viewModelScope.launch {
            DataStoreRepository.setThemeOption(theme.themeString)
        }
    }
    fun setDynamicColorOption(enabled: Boolean) {
        viewModelScope.launch {
            DataStoreRepository.setDynamicColorOption(enabled)
        }
    }
}
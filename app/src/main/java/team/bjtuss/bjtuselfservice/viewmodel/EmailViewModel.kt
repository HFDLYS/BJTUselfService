package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.repository.EmailRepository
import team.bjtuss.bjtuselfservice.repository.EmailSummary

sealed interface EmailUiState {
    data object Loading : EmailUiState
    data class Success(val emails: List<EmailSummary>) : EmailUiState
    data class Error(val message: String) : EmailUiState
}

class EmailViewModel : ViewModel() {
    private val _state = MutableStateFlow<EmailUiState>(EmailUiState.Loading)
    val state: StateFlow<EmailUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = EmailUiState.Loading
            runCatching { EmailRepository.loadInbox() }
                .onSuccess { _state.value = EmailUiState.Success(it) }
                .onFailure {
                    _state.value = EmailUiState.Error(it.message ?: "邮件加载失败")
                }
        }
    }
}

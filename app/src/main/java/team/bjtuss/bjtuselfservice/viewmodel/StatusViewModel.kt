package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import team.bjtuss.bjtuselfservice.repository.EmailRepository
import team.bjtuss.bjtuselfservice.statemanager.AuthenticatorManager
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

        refreshStatus()
    }

    fun refreshStatus() {
        StudentAccountManager.getInstance().status.thenAccept {
            _status.value = it
            refreshMailCount()
        }
    }

    fun refreshMailCount() {
        val account = AuthenticatorManager.credentials.value.username
        if (account.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = EmailRepository.unreadCount()
                if (AuthenticatorManager.credentials.value.username == account) {
                    _status.update { Status(count.toString(), it.EcardBalance, it.NetBalance) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Preserve the previous status if Coremail is temporarily unavailable.
                android.util.Log.w("MailStatus", "Unable to refresh unread count")
            }
        }
    }


}

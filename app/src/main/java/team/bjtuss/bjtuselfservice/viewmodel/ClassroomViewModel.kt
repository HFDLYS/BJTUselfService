package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.bjtuss.bjtuselfservice.StudentAccountManager

class ClassroomViewModel : ViewModel() {
    private val studentAccountManager = StudentAccountManager.getInstance()
    private var _classroomMap =
        MutableStateFlow<MutableMap<String, MutableList<Int>>>(mutableMapOf())
    val classroomMap: StateFlow<MutableMap<String, MutableList<Int>>> = _classroomMap.asStateFlow()

    init {
        loadClassroomMap()
    }

    fun loadClassroomMap() {
        try {
            studentAccountManager.getClassroom().thenAccept {
                _classroomMap.value.putAll(it)
            }
        } catch (e: Exception) {
            handleLoginError(e)
        }
    }

    private fun handleLoginError(throwable: Throwable) {
        when (throwable.message) {
            "Not loginAa", "Not login" -> {
                val loginSuccessful = studentAccountManager.loginAa().thenAccept {
                    if (it) {
                        loadClassroomMap()
                    }
                }
            }

            else -> _classroomMap = MutableStateFlow(mutableMapOf())
        }
    }
}
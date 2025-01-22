package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity

class ExamScheduleViewModel : ViewModel() {
    private val studentAccountManager = StudentAccountManager.getInstance()

    private var _examScheduleList =
        MutableStateFlow<MutableList<ExamScheduleEntity>>(mutableListOf())

    val examScheduleList: StateFlow<MutableList<ExamScheduleEntity>> =
        _examScheduleList.asStateFlow()

    init {
        loadExamScheduleList()
    }

    fun loadExamScheduleList() {
        _examScheduleList.value = mutableListOf()
        try {
            studentAccountManager.getExamSchedule().thenAccept {
                _examScheduleList.value.addAll(it)
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
                        loadExamScheduleList()
                    }
                }
            }
            else -> _examScheduleList.value = mutableListOf()
        }
    }
}
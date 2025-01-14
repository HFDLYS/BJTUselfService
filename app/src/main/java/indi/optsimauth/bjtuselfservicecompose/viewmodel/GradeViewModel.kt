package indi.optsimauth.bjtuselfservicecompose.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import indi.optsimauth.bjtuselfservicecompose.StudentAccountManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch

class GradeViewModel : ViewModel() {
    private val studentAccountManager = StudentAccountManager.getInstance()

    private var _gradeList =
        MutableStateFlow<MutableList<StudentAccountManager.Grade>>(mutableListOf())
    val gradeList: StateFlow<MutableList<StudentAccountManager.Grade>> = _gradeList.asStateFlow()


    init {
        loadGradeList()
    }

    fun loadGradeList() {
        try {
            studentAccountManager.getGrade("ln").thenAccept {
                _gradeList.value.addAll(it)
                it.forEach {
                    println("ln123 ${it.courseName}")
                }
            }
        } catch (e: Exception) {
            handleLoginError(e)
        }

        try {
            studentAccountManager.getGrade("lr").thenAccept {
                _gradeList.value.addAll(it)
                it.forEach {
                    println("lr123 ${it.courseName}")
                }
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
                        loadGradeList()
                    }
                }
            }

            else -> _gradeList.value = mutableListOf()
        }
    }

//    val isAaLogin: StateFlow<Boolean>
//        get() = _isAaLogin
}


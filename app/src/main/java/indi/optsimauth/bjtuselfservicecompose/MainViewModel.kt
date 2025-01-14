package indi.optsimauth.bjtuselfservicecompose

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

import androidx.lifecycle.viewmodel.compose.viewModel
import indi.optsimauth.bjtuselfservicecompose.StudentAccountManager.Grade

class MainViewModel : ViewModel() {
    //    private val _screenStatus = MutableStateFlow<ScreenStatus>(ScreenStatus.LoginScreen)
//    val screenStatus: StateFlow<ScreenStatus> = _screenStatus
//
//    fun setScreenStatus(screenStatus: ScreenStatus) {
//        _screenStatus.value = screenStatus
//    }
    private val _gradeList = mutableListOf<Grade>()
    var gradeList = _gradeList

//    private val gradeViewModel = ViewModelProvider(this).get(GradeViewModel::class.java)

    init {

    }

    private val _courseList = mutableListOf<StudentAccountManager.Course>()


}
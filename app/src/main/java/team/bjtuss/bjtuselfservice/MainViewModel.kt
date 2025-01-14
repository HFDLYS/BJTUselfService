package team.bjtuss.bjtuselfservice

import androidx.lifecycle.ViewModel

import team.bjtuss.bjtuselfservice.StudentAccountManager.Grade

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
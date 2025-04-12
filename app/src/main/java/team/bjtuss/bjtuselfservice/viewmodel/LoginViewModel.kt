package team.bjtuss.bjtuselfservice.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository

// Login View Model to handle login logic
// 定义密封类表示登录状态
sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Error(val message: String) : LoginState()
}


sealed class ScreenStatus {
    object AppScreen : ScreenStatus()
    object LoginScreen : ScreenStatus()
}


class Authenticator(private val studentAccountManager: StudentAccountManager) {

    suspend fun login(username: String, password: String): Result<Boolean> {
        return try {
            Log.d("Authenticator", "开始登录：$username")
            val isInitialLoginSuccessful = studentAccountManager.init(username, password).await()
            Log.d("Authenticator", "初始登录结果：$isInitialLoginSuccessful")

            if (isInitialLoginSuccessful) {
                val isXsMisLoginSuccessful = studentAccountManager.loginXsMis()
                isXsMisLoginSuccessful.thenAccept({
                    Log.d("Authenticator", "XSMIS登录结果：$it")
                })

                val isAaLoginSuccessful = studentAccountManager.loginAa().await()
                Log.d("Authenticator", "AA登录结果：$isAaLoginSuccessful")

                if (isAaLoginSuccessful) {
                    Result.success(true)
                } else {
                    Log.e("Authenticator", "AA登录失败")
                    Result.failure(Exception("AA登录失败"))
                }


            } else {
                Log.e("Authenticator", "初始登录失败")
                Result.failure(Exception("初始登录失败"))
            }
        } catch (e: Exception) {
            Log.e("Authenticator", "登录发生异常", e)
            Result.failure(e)
        }
    }

    suspend fun autoLogin(username: String, password: String): Result<Boolean> {
        return login(username, password)
    }
}

class LoginViewModel : ViewModel() {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _screenStatus = MutableStateFlow<ScreenStatus>(ScreenStatus.LoginScreen)
    val screenStatus: StateFlow<ScreenStatus> = _screenStatus.asStateFlow()

    private val authenticator = Authenticator(StudentAccountManager.getInstance())

    init {
        // 初始化时尝试自动登录
        autoLogin()
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val result = authenticator.login(username, password)
                if (result.isSuccess) {
                    DataStoreRepository.setCredentials(username, password)  // 将凭据存储到本地
                    _screenStatus.value = ScreenStatus.AppScreen
                } else {
                    _loginState.value =
                        LoginState.Error(result.exceptionOrNull()?.message ?: "登录失败")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "未知错误")
            }
        }
    }

    fun autoLogin() {
        viewModelScope.launch {

            try {
                val (username, password) = DataStoreRepository.getStoredCredentialsBlocking()
                if (username == "" || password == "") {
                    _loginState.value = LoginState.Idle
                    return@launch
                }
                _loginState.value = LoginState.Loading
                val result = authenticator.autoLogin(username, password)


                if (result.isSuccess) {
                    _screenStatus.value = ScreenStatus.AppScreen
                } else {
                    _loginState.value = LoginState.Error(result.exceptionOrNull()?.message ?: "自动登录失败")
                    LoginState.Error(result.exceptionOrNull()?.message ?: "自动登录失败")
                }

            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "自动登录异常")
            }
        }
    }

    fun setLoginStateIdle() {
        _loginState.value = LoginState.Idle
    }

    fun logout() {
        viewModelScope.launch {
            DataStoreRepository.clearCredentials()
            StudentAccountManager.getInstance().clearCookie()
            _loginState.value = LoginState.Idle
            _screenStatus.value = ScreenStatus.LoginScreen
        }
    }
}
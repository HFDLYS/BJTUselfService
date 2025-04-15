package team.bjtuss.bjtuselfservice.statemanager

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.controller.NetworkRequestQueue
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import team.bjtuss.bjtuselfservice.statemanager.AuthenticatorManager.credentials
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import team.bjtuss.bjtuselfservice.web.MisDataManager.login

// Login View Model to handle login logic
// 定义密封类表示登录状态


//class Authenticator(private val studentAccountManager: StudentAccountManager) {
//
//    suspend fun login(credentials: Credentials): Result<Boolean> {
//        val (username, password) = credentials
//        return try {
//            Log.d("Authenticator", "开始登录：$username")
//            val isInitialLoginSuccessful = studentAccountManager.init(username, password).await()
//            Log.d("Authenticator", "初始登录结果：$isInitialLoginSuccessful")
//
//            if (isInitialLoginSuccessful) {
//                val isXsMisLoginSuccessful = studentAccountManager.loginXsMis()
//                isXsMisLoginSuccessful.thenAccept({
//                    Log.d("Authenticator", "XSMIS登录结果：$it")
//                })
//
//                val isAaLoginSuccessful = studentAccountManager.loginAa().await()
//                Log.d("Authenticator", "AA登录结果：$isAaLoginSuccessful")
//
//                if (isAaLoginSuccessful) {
//                    Result.success(true)
//                } else {
//                    Log.e("Authenticator", "AA登录失败")
//                    Result.failure(Exception("AA登录失败"))
//                }
//
//
//            } else {
//                Log.e("Authenticator", "初始登录失败")
//                Result.failure(Exception("初始登录失败"))
//            }
//        } catch (e: Exception) {
//            Log.e("Authenticator", "登录发生异常", e)
//            Result.failure(e)
//        }
//    }
//}

data class Credentials(
    val username: String, val password: String
) {
    fun isValid(): Boolean {
        return username.isNotBlank() && password.isNotBlank()
    }
}


sealed class AppEvent {
    data class LoginRequest(val credentials: Credentials) : AppEvent()
    object LoginSuccess : AppEvent()
    object LoginFailed : AppEvent()
    data class LogoutRequest(val clearAllData: () -> Unit) : AppEvent()
    object DataSyncCompleted : AppEvent()
    object DataSyncRequest : AppEvent()
}

object AppEventManager {
    private val _events = MutableSharedFlow<AppEvent>()
    val events = _events.asSharedFlow()


    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    init {
        coroutineScope.launch {
            _events.collect { event ->
                processEvent(event)
            }
        }
    }

    fun sendEvent(event: AppEvent) {
        coroutineScope.launch {
            _events.emit(event)
        }
    }

    private fun processEvent(event: AppEvent) {
        when (event) {
            is AppEvent.LoginRequest -> {
                AppStateManager.updateState(AppState.Logging)
                AuthenticatorManager.login(event.credentials, onSuccess = {
                    sendEvent(AppEvent.LoginSuccess)
                })
            }
            // 执行登录逻辑...
            is AppEvent.LoginSuccess -> {
                AppStateManager.updateState(AppState.Idle)
                sendEvent(AppEvent.DataSyncRequest)
            }

            is AppEvent.LoginFailed -> {
                AppStateManager.updateState(AppState.Error)
            }

            is AppEvent.LogoutRequest -> {
                AppStateManager.updateState(AppState.Logout)
                event.clearAllData()
            }


            is AppEvent.DataSyncCompleted -> {
                AppStateManager.updateState(AppState.Idle)
                // 执行数据同步完成逻辑...
            }

            is AppEvent.DataSyncRequest -> {
                AppStateManager.updateState(AppState.NetworkProgress)
            }
        }
    }

    // 登录方法

}

object AuthenticatorManager {
    private val studentAccountManager = StudentAccountManager.getInstance()

    private var _credentials = MutableStateFlow(Credentials("", ""))
    val credentials: StateFlow<Credentials> = _credentials.asStateFlow()

    init {
        tryAutoLogin()
    }

    private fun tryAutoLogin() {
        CoroutineScope(Dispatchers.IO).launch {
            // 从存储中恢复凭据并尝试登录
            val storedCredentials = DataStoreRepository.getStoredCredentialsBlocking()
            if (storedCredentials.isValid()) {
                AppEventManager.sendEvent(AppEvent.LoginRequest(storedCredentials))
            }
        }
    }


    fun login(credentials: Credentials, onSuccess: () -> Unit) {
        // Validate credentials first
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AuthenticatorManager.updateCredentials(credentials)
                val result = AuthenticatorManager.login(credentials)

                if (result.isSuccess) {
                    // Only save credentials on successful login
                    DataStoreRepository.setCredentials(credentials)
                    SmartCurriculumPlatformRepository.initClient()
                    onSuccess()
                } else {
                    AppEventManager.sendEvent(AppEvent.LoginFailed)
                }
            } catch (e: Exception) {
                println("Login exception: ${e.message}") // Debug log
                AppEventManager.sendEvent(AppEvent.LoginFailed)
            }
        }
    }


    // 判断凭据是否有效
    private fun isValidCredentials(credentials: Credentials): Boolean {
        val (username, password) = credentials
        return username.isNotBlank() && password.isNotBlank()
    }

    fun updateCredentials(newCredentials: Credentials) {
        _credentials.value = newCredentials
    }

    suspend fun login(credentials: Credentials): Result<Boolean> {
        val (username, password) = credentials
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

    fun clearAllData(mainViewModel: MainViewModel) {
        StudentAccountManager.getInstance().clearCookie()
        // 清理数据
        AuthenticatorManager.updateCredentials(Credentials("", ""))
        mainViewModel.clearChange()
        CoroutineScope(Dispatchers.IO).launch {
            // 清除登录状态
            with(AppDatabase.getInstance()) {
                examScheduleEntityDao().deleteAll()
                gradeEntityDao().deleteAll()
                courseEntityDao().deleteAll()
                homeworkEntityDao().deleteAll()
                DataStoreRepository.clearCredentials()
                DataStoreRepository.setCredentials(Credentials("", ""))
            }
        }
    }
}


sealed class AppState {
    object Logout : AppState()
    object Logging : AppState()
    object Error : AppState()
    object Idle : AppState()
    object NetworkProgress : AppState()

    fun canDownloadCourseware(): Boolean {
        return when (this) {
            is Idle, is NetworkProgress -> true
            else -> false
        }
    }

    fun canDownloadAndUpload(): Boolean {
        return when (this) {
            is Idle, is NetworkProgress -> true
            else -> false
        }
    }

    fun loginFish(): Boolean {
        return when (this) {
            is Logout, Error, Logging -> false
            else -> true
        }
    }
}


object AppStateManager {

    private val _appState = MutableStateFlow<AppState>(AppState.Logout)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    fun updateState(state: AppState) {
        if (_appState.value == state) {
            return
        }
        _appState.value = state
    }


    // 登出方法


    // 替代 loginDeferred 的阻塞方法
    suspend fun awaitLoginState() {
        // 等待直到状态不是Logout或Logging
        appState.first {
            it.loginFish()
        }
    }

    // 等待特定状态的方法
    suspend fun awaitState(targetState: AppState) {
        appState.first { it == targetState }
    }

    // 等待直到满足条件的方法
    suspend fun awaitStateCondition(condition: (AppState) -> Boolean) {
        appState.first { condition(it) }
    }
}


@Composable
fun LoginDialog(credentials: Credentials) {
    var username by remember { mutableStateOf(credentials.username) }
    var password by remember { mutableStateOf(credentials.password) }
    val loginState by AppStateManager.appState.collectAsState()

    AlertDialog(
        onDismissRequest = {},
        title = null, // 移除默认标题，在自定义内容中添加更美观的标题
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 状态相关顶部提示
                when (loginState) {
                    AppState.Error -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp), verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "错误",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "登录失败，请检查账号密码",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    else -> {}
                }

                LoginForm(username = username,
                    password = password,
                    onUsernameChange = { username = it },
                    onPasswordChange = { password = it })
            }
        },
        confirmButton = {
            LoginButton(enabled = username.isNotBlank() && password.isNotBlank(), onClick = {
                AppEventManager.sendEvent(
                    AppEvent.LoginRequest(
                        Credentials(
                            username, password
                        )
                    )
                )

            })
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        properties = DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginForm(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp), contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()
        ) {
            // 应用图标
//            Box(
//                modifier = Modifier
//                    .size(80.dp)
//                    .background(
//                        color = surfaceContainerLowest,
//                        shape = CircleShape
//                    ),
//                contentAlignment = Alignment.Center
//            ) {
////                Icon(
////                    imageVector = Icons.Default.LocationOn,
////                    contentDescription = "应用图标",
////                    tint = Color.White,
////                    modifier = Modifier.size(48.dp)
////                )
//                Image(
//                    painter = painterResource(R.drawable.loading_icon),
//                    contentDescription = "Loading",
//                    modifier = Modifier
//                        .size(80.dp)  // 可调整大小
//                )
//
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))

            // 标题
            Text(
                text = "交大自由行",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 副标题
            Text(
                text = "让校园生活更便捷",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // 学号输入框
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("学号") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "学号图标",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            // 密码输入框
            var passwordVisibility by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisibility) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "密码图标",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    val image = if (passwordVisibility) Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff

                    IconButton(onClick = { passwordVisibility = !passwordVisibility }) {
                        Icon(
                            imageVector = image,
                            contentDescription = "切换密码可见性",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun LoginButton(
    enabled: Boolean, onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp, pressedElevation = 8.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = "登录图标",
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "登 录", style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ), color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun ErrorMessage(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(8.dp)
    )
}
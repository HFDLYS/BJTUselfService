package team.bjtuss.bjtuselfservice.viewmodel

import android.R.attr.password
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil.decode.DataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository

// Login View Model to handle login logic
// 定义密封类表示登录状态


class Authenticator(private val studentAccountManager: StudentAccountManager) {

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
}

data class Credentials(
    val username: String,
    val password: String
)

sealed class AppState {
    object Logout : AppState()
    object Logging : AppState()
    object Error : AppState()
    object LoggedIn : AppState()
}


object AppStateManager {

    var loginDeferred: CompletableDeferred<Unit> = CompletableDeferred()

    // 状态定义
    private val _credentials = MutableStateFlow<Credentials>(Credentials("", ""))
    val credentials: StateFlow<Credentials> = _credentials.asStateFlow()

    private val _appState = MutableStateFlow<AppState>(AppState.Logout)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            // 从存储中恢复凭据并尝试登录
            val storedCredentials = DataStoreRepository.getStoredCredentialsBlocking()
            if (isValidCredentials(storedCredentials)) {
                login(storedCredentials, {})
            }
        }
    }

    // 判断凭据是否有效
    private fun isValidCredentials(credentials: Credentials): Boolean {
        val (username, password) = credentials
        return username.isNotBlank() && password.isNotBlank()
    }

    // 登录方法
    fun login(credentials: Credentials, onLoginSuccess: () -> Unit) {
        println("Login attempt with credentials: $credentials")  // Debug log

        // Validate credentials first
        if (!isValidCredentials(credentials)) {
            _appState.value = AppState.Error
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            _appState.value = AppState.Logging

            try {
                // Update the credentials in the state BEFORE login attempt
                _credentials.value = credentials

                // Use the passed credentials for authentication
                val authenticator = Authenticator(StudentAccountManager.getInstance())
                val result = authenticator.login(credentials)

                if (result.isSuccess) {
                    // Only save credentials on successful login
                    DataStoreRepository.setCredentials(credentials)

                    _appState.value = AppState.LoggedIn
                    onLoginSuccess()
                    loginDeferred.complete(Unit)
                } else {
                    _appState.value = AppState.Error
                }
            } catch (e: Exception) {
                println("Login exception: ${e.message}")  // Debug log
                _appState.value = AppState.Error
            }
        }
    }

    // 登出方法
    fun logout(mainViewModel: MainViewModel) {
        StudentAccountManager.getInstance().clearCookie()
        // 清理数据
        _credentials.value = Credentials("", "")



        mainViewModel.clearChange()
        // 重置状态
        _appState.value = AppState.Logout


        // 重新初始化登录延迟对象
        loginDeferred = CompletableDeferred()
        println("协成开始")
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

        println("协成结束")

    }
}


@Composable
fun LoginDialog(credentials: Credentials, onLoginSuccess: () -> Unit) {

    var username by remember { mutableStateOf(credentials.username) }
    var password by remember { mutableStateOf(credentials.password) }
    val loginState by AppStateManager.appState.collectAsState()

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text =
                when (loginState) {
                    AppState.Logout -> "登录"
                    AppState.Error -> "登录失败"
                    else -> "你不应该看到这个页面"
                }
            )
        },
        text = {

            LoginForm(
                username = username,
                password = password,
                onUsernameChange = { username = it },
                onPasswordChange = { password = it }
            )
        },
        confirmButton = {
            LoginButton(
                enabled = username.isNotBlank() && password.isNotBlank(),
                onClick = {
                    AppStateManager.login(
                        Credentials(
                            username = username,
                            password = password
                        ),
                        onLoginSuccess
                    )
                }
            )
        },
    )
}


@Composable
fun LoginForm(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit
) {
    Column {
        // 标题
        Text(
            text = "交大自由行",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 学号输入框
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("学号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "学号图标"
                )
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 密码输入框
        var passwordVisibility by remember { mutableStateOf(false) }

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("密码") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisibility)
                VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "密码图标"
                )
            },
            trailingIcon = {
                val image = if (passwordVisibility)
                    Icons.Filled.Visibility
                else Icons.Filled.VisibilityOff

                IconButton(onClick = { passwordVisibility = !passwordVisibility }) {
                    Icon(
                        imageVector = image,
                        contentDescription = "切换密码可见性"
                    )
                }
            }
        )
    }
}

@Composable
fun LoginButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        Text(
            text = "登录",
            style = MaterialTheme.typography.bodyLarge
        )
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
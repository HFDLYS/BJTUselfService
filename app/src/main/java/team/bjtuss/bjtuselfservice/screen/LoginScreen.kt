package team.bjtuss.bjtuselfservice.screen

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.StudentAccountManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch


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
                val isAaLoginSuccessful = studentAccountManager.loginAa().await()
                Log.d("Authenticator", "AA登录结果：$isAaLoginSuccessful")

                if (isAaLoginSuccessful) {
                    Result.success(true)
                } else {
                    Log.e("Authenticator", "AA登录失败")
                    Result.failure(Exception("AA登录失败"))
                }

                val isXsMisLoginSuccessful = studentAccountManager.loginXsMis().await()
                Log.d("Authenticator", "XsMis登录结果：$isXsMisLoginSuccessful")

                if (isXsMisLoginSuccessful) {
                    Result.success(true)
                } else {
                    Log.e("Authenticator", "XsMis登录失败")
                    Result.failure(Exception("XsMis登录失败"))
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
                    SettingsRepository.setCredentials(username, password)  // 将凭据存储到本地
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
                val (username, password) = SettingsRepository.getStoredCredentialsBlocking()
                if (username == "" || password == "") {
                    _loginState.value = LoginState.Idle
                    return@launch
                }
                _loginState.value = LoginState.Loading
                val result = authenticator.autoLogin(username, password)
                if (result.isSuccess) {
                    _screenStatus.value = ScreenStatus.AppScreen
                }


                if (result.isSuccess) {
                    _screenStatus.value = ScreenStatus.AppScreen
                } else {
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
            SettingsRepository.clearCredentials()
            StudentAccountManager.getInstance().clearCookie()
            _loginState.value = LoginState.Idle
            _screenStatus.value = ScreenStatus.LoginScreen
        }
    }
}


// 登录界面
@Composable
fun LoginScreen(
    loginViewModel: LoginViewModel,

    ) {
    val loginState by loginViewModel.loginState.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // 自动登录
    LaunchedEffect(Unit) {
        if (loginState is LoginState.Idle) {
        } else {
            loginViewModel.autoLogin()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 登录表单组件
        LoginForm(
            username = username,
            password = password,
            onUsernameChange = { username = it },
            onPasswordChange = { password = it }
        )

//         错误消息显示
        when (loginState) {
            is LoginState.Error -> ErrorDialog(
                message = (loginState as LoginState.Error).message,  // 不需要显式转换
                onDismiss = {
                    loginViewModel.setLoginStateIdle()
                })

            is LoginState.Loading -> LoadingDialog()
            is LoginState.Idle -> {}
        }

        // 登录按钮
        LoginButton(
            enabled = username.isNotBlank() && password.isNotBlank() && loginState !is LoginState.Loading,
            onClick = {
                loginViewModel.login(username, password)
            }
        )
    }
}

@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "登录失败")
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = "确定")
            }
        }
    )
}

@Composable
fun LoadingDialog(
) {
    AlertDialog(
        modifier = Modifier.wrapContentSize(),
        onDismissRequest = {},
        title = {
            Text(text = "正在登录中...")
        },
        text = {
            RotatingImageLoader(
                image = painterResource(id = R.drawable.loading_icon),
                rotationDuration = 1000
            )
        },
        confirmButton = {}
    )

}

@Composable
fun RotatingImageLoader(
    modifier: Modifier = Modifier,
    image: Painter,
    rotationDuration: Int = 1000 // 旋转一周的时间，单位毫秒
) {
    // 创建一个可以无限循环的旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "rotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // 使用Box居中显示并应用旋转
    Box(
        modifier = modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = image,
            contentDescription = "Loading",
            modifier = Modifier
                .size(80.dp)  // 可调整大小
                .rotate(rotation)  // 应用旋转动画
        )
    }
}

@Composable
fun LoginForm(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit
) {
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

//package indi.optsimauth.bjtuselfservicecompose.screen
//
//import android.content.Context
//import android.util.Log
//import androidx.compose.animation.core.LinearEasing
//import androidx.compose.animation.core.RepeatMode
//import androidx.compose.animation.core.animateFloat
//import androidx.compose.animation.core.infiniteRepeatable
//import androidx.compose.animation.core.rememberInfiniteTransition
//import androidx.compose.animation.core.tween
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.size
//import androidx.compose.foundation.layout.wrapContentHeight
//import androidx.compose.foundation.layout.wrapContentSize
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.foundation.text.KeyboardOptions
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Lock
//import androidx.compose.material.icons.filled.Person
//import androidx.compose.material.icons.filled.Visibility
//import androidx.compose.material.icons.filled.VisibilityOff
//import androidx.compose.material3.AlertDialog
//import androidx.compose.material3.Button
//import androidx.compose.material3.CircularProgressIndicator
//import androidx.compose.material3.Icon
//import androidx.compose.material3.IconButton
//import androidx.compose.material3.LinearProgressIndicator
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.OutlinedTextField
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.Surface
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.collectAsState
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.rememberCoroutineScope
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.rotate
//import androidx.compose.ui.graphics.painter.Painter
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.text.input.KeyboardType
//import androidx.compose.ui.text.input.PasswordVisualTransformation
//import androidx.compose.ui.text.input.VisualTransformation
//import androidx.compose.ui.unit.dp
//import androidx.datastore.core.DataStore
//import androidx.datastore.preferences.core.Preferences
//import androidx.datastore.preferences.core.edit
//import androidx.datastore.preferences.core.stringPreferencesKey
//import androidx.datastore.preferences.preferencesDataStore
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import androidx.lifecycle.viewmodel.compose.viewModel
//import indi.optsimauth.bjtuselfservicecompose.StudentAccountManager
//import indi.optsimauth.bjtuselfservicecompose.ui.theme.BJTUselfServicecomposeTheme
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.flow.first
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.runBlocking
//import kotlinx.coroutines.withContext
//import indi.optsimauth.bjtuselfservicecompose.R
//import indi.optsimauth.bjtuselfservicecompose.screen.SettingViewModel
//import indi.optsimauth.bjtuselfservicecompose.screen.SettingsRepository
//import indi.optsimauth.bjtuselfservicecompose.utils.Utils.assetFilePath
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.future.await
//
//
//// Login View Model to handle login logic
//// 定义密封类表示登录状态
//sealed class LoginState {
//    object Idle : LoginState()
//    object Loading : LoginState()
//    data class Success(val username: String) : LoginState()
//    data class Error(val message: String) : LoginState()
//}
//
//
//
//
//class Authenticator(private val studentAccountManager: StudentAccountManager) {
//
//    suspend fun login(username: String, password: String): Result<Boolean> {
//        return try {
//            Log.d("Authenticator", "开始登录：$username")
//            val isInitialLoginSuccessful = studentAccountManager.init(username, password).await()
//            Log.d("Authenticator", "初始登录结果：$isInitialLoginSuccessful")
//
//            if (isInitialLoginSuccessful) {
//                val isAaLoginSuccessful = studentAccountManager.loginAa().await()
//                Log.d("Authenticator", "AA登录结果：$isAaLoginSuccessful")
//
//                if (isAaLoginSuccessful) {
//                    Result.success(true)
//                } else {
//                    Log.e("Authenticator", "AA登录失败")
//                    Result.failure(Exception("AA登录失败"))
//                }
//            } else {
//                Log.e("Authenticator", "初始登录失败")
//                Result.failure(Exception("初始登录失败"))
//            }
//        } catch (e: Exception) {
//            Log.e("Authenticator", "登录发生异常", e)
//            Result.failure(e)
//        }
//    }
//
//    suspend fun autoLogin(username: String, password: String): Result<Boolean> {
//        return login(username, password)
//    }
//}
//
//class LoginViewModel : ViewModel() {
//    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
//    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()
//
//    private val authenticator = Authenticator(StudentAccountManager.getInstance())
//
//    init {
//        // 初始化时尝试自动登录
//        autoLogin()
//    }
//
//    fun login(username: String, password: String) {
//        viewModelScope.launch {
//            _loginState.value = LoginState.Loading
//            try {
//                val result = authenticator.login(username, password)
//                if (result.isSuccess) {
//                    SettingsRepository.setCredentials(username, password)  // 将凭据存储到本地
//                    _loginState.value = LoginState.Success(username)
//                } else {
//                    _loginState.value =
//                        LoginState.Error(result.exceptionOrNull()?.message ?: "登录失败")
//                }
//            } catch (e: Exception) {
//                _loginState.value = LoginState.Error(e.message ?: "未知错误")
//            }
//        }
//    }
//
//    fun autoLogin() {
//        viewModelScope.launch {
//
//            try {
//                val (username, password) = SettingsRepository.getStoredCredentialsBlocking()
//                if (username == "" || password == "") {
//                    _loginState.value = LoginState.Idle
//                    return@launch
//                }
//                _loginState.value = LoginState.Loading
//                val result = authenticator.autoLogin(username, password)
//                _loginState.value = if (result.isSuccess) {
//                    LoginState.Success(username)  // 自动登录成功
//                } else {
//                    LoginState.Error(result.exceptionOrNull()?.message ?: "自动登录失败")
//                }
//            } catch (e: Exception) {
//                _loginState.value = LoginState.Error(e.message ?: "自动登录异常")
//            }
//        }
//    }
//
//    fun setLoginStateIdle() {
//        _loginState.value = LoginState.Idle
//    }
//
//    fun logout() {
//        viewModelScope.launch {
//            SettingsRepository.clearCredentials()
//            StudentAccountManager.getInstance().clearCookie()
//            _loginState.value = LoginState.Idle
//        }
//    }
//}
//
//
//// 登录界面
//@Composable
//fun LoginScreen(
//    loginViewModel: LoginViewModel,
//
//    ) {
//    val loginState by loginViewModel.loginState.collectAsState()
//    var username by remember { mutableStateOf("") }
//    var password by remember { mutableStateOf("") }
//
//    // 自动登录
//    LaunchedEffect(Unit) {
//        if (loginState is LoginState.Idle) {
//        } else {
//            loginViewModel.autoLogin()
//        }
//    }
//
////    // 处理登录状态
////    LaunchedEffect(loginState) {
////        when (val state = loginState) {
////            is LoginState.Success -> onLoginSuccess()
////            is LoginState.Error -> {
////                // 可以添加具体的错误处理逻辑
////            }
////
////            else -> {}
////        }
////    }
//
//    LoginScreenContent(
//        username = username,
//        password = password,
//        loginState = loginState,
//        onUsernameChange = { username = it },
//        onPasswordChange = { password = it },
//        onLoginClick = {
//            loginViewModel.login(username, password)
//        },
//        onErrorDialogDismiss = {
//            loginViewModel.setLoginStateIdle()
//        }
//    )
//}
//
//@Composable
//fun LoginScreenContent(
//    username: String,
//    password: String,
//    loginState: LoginState,
//    onUsernameChange: (String) -> Unit,
//    onPasswordChange: (String) -> Unit,
//    onLoginClick: () -> Unit,
//    onErrorDialogDismiss: () -> Unit
//) {
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        // 登录表单组件
//        LoginForm(
//            username = username,
//            password = password,
//            onUsernameChange = onUsernameChange,
//            onPasswordChange = onPasswordChange
//        )
//
////         错误消息显示
//        when (loginState) {
//            is LoginState.Error -> ErrorDialog(loginState.message, onDismiss = onErrorDialogDismiss)
//            is LoginState.Loading -> LoadingDialog()
//            else -> {}
//        }
//
//        // 登录按钮
//        LoginButton(
//            enabled = username.isNotBlank() && password.isNotBlank() && loginState !is LoginState.Loading,
//            onClick = onLoginClick
//        )
//    }
//}
//
//@Composable
//fun ErrorDialog(
//    message: String,
//    onDismiss: () -> Unit
//) {
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        title = {
//            Text(text = "登录失败")
//        },
//        text = {
//            Text(text = message)
//        },
//        confirmButton = {
//            Button(onClick = onDismiss) {
//                Text(text = "确定")
//            }
//        }
//    )
//}
//
//@Composable
//fun LoadingDialog(
//) {
//    AlertDialog(
//        modifier = Modifier.wrapContentSize(),
//        onDismissRequest = {},
//        title = {
//            Text(text = "正在登录中...")
//        },
//        text = {
//            RotatingImageLoader(
//                image = painterResource(id = R.drawable.loading_icon),
//                rotationDuration = 1000
//            )
//        },
//        confirmButton = {}
//    )
//
//}
//
//@Composable
//fun RotatingImageLoader(
//    modifier: Modifier = Modifier,
//    image: Painter,
//    rotationDuration: Int = 1000 // 旋转一周的时间，单位毫秒
//) {
//    // 创建一个可以无限循环的旋转动画
//    val infiniteTransition = rememberInfiniteTransition(label = "rotate")
//    val rotation by infiniteTransition.animateFloat(
//        initialValue = 0f,
//        targetValue = 360f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(rotationDuration, easing = LinearEasing),
//            repeatMode = RepeatMode.Restart
//        ),
//        label = "rotation"
//    )
//
//    // 使用Box居中显示并应用旋转
//    Box(
//        modifier = modifier
//            .wrapContentHeight()
//            .fillMaxWidth(),
//        contentAlignment = Alignment.Center
//    ) {
//        Image(
//            painter = image,
//            contentDescription = "Loading",
//            modifier = Modifier
//                .size(80.dp)  // 可调整大小
//                .rotate(rotation)  // 应用旋转动画
//        )
//    }
//}
//
//@Composable
//fun LoginForm(
//    username: String,
//    password: String,
//    onUsernameChange: (String) -> Unit,
//    onPasswordChange: (String) -> Unit
//) {
//    // 标题
//    Text(
//        text = "交大自由行",
//        style = MaterialTheme.typography.headlineMedium,
//        modifier = Modifier.padding(bottom = 16.dp)
//    )
//
//    // 学号输入框
//    OutlinedTextField(
//        value = username,
//        onValueChange = onUsernameChange,
//        label = { Text("学号") },
//        singleLine = true,
//        modifier = Modifier.fillMaxWidth(),
//        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
//        leadingIcon = {
//            Icon(
//                imageVector = Icons.Default.Person,
//                contentDescription = "学号图标"
//            )
//        }
//    )
//
//    Spacer(modifier = Modifier.height(8.dp))
//
//    // 密码输入框
//    var passwordVisibility by remember { mutableStateOf(false) }
//
//    OutlinedTextField(
//        value = password,
//        onValueChange = onPasswordChange,
//        label = { Text("密码") },
//        singleLine = true,
//        modifier = Modifier.fillMaxWidth(),
//        visualTransformation = if (passwordVisibility)
//            VisualTransformation.None
//        else PasswordVisualTransformation(),
//        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
//        leadingIcon = {
//            Icon(
//                imageVector = Icons.Default.Lock,
//                contentDescription = "密码图标"
//            )
//        },
//        trailingIcon = {
//            val image = if (passwordVisibility)
//                Icons.Filled.Visibility
//            else Icons.Filled.VisibilityOff
//
//            IconButton(onClick = { passwordVisibility = !passwordVisibility }) {
//                Icon(
//                    imageVector = image,
//                    contentDescription = "切换密码可见性"
//                )
//            }
//        }
//    )
//}
//
//@Composable
//fun LoginButton(
//    enabled: Boolean,
//    onClick: () -> Unit
//) {
//    Button(
//        onClick = onClick,
//        enabled = enabled,
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(top = 16.dp),
//        contentPadding = PaddingValues(12.dp)
//    ) {
//        Text(
//            text = "登录",
//            style = MaterialTheme.typography.bodyLarge
//        )
//    }
//}
//
//@Composable
//fun ErrorMessage(message: String) {
//    Text(
//        text = message,
//        color = MaterialTheme.colorScheme.error,
//        style = MaterialTheme.typography.bodyMedium,
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(vertical = 8.dp)
//            .background(
//                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
//                shape = RoundedCornerShape(4.dp)
//            )
//            .padding(8.dp)
//    )
//}


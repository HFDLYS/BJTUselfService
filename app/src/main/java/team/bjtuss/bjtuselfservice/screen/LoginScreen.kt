package team.bjtuss.bjtuselfservice.screen

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import team.bjtuss.bjtuselfservice.viewmodel.AppState
import team.bjtuss.bjtuselfservice.viewmodel.AppStateManager
import team.bjtuss.bjtuselfservice.viewmodel.LoginButton
import team.bjtuss.bjtuselfservice.viewmodel.LoginForm


// 登录界面
@Composable
fun LoginScreen(
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val appState by AppStateManager.appState.collectAsState()
    // 自动登录
//    LaunchedEffect(Unit) {
////        if (loginState is LoginState.Idle) {
////        } else {
////            loginViewModel.autoLogin()
////        }
//
//        if (appState !is AppState.Logout) {
////            AppStateManager.loginDeferred.complete(Unit)
//        }
//
//
//        username = DataStoreRepository.getStoredCredentialsBlocking().first
//        password = DataStoreRepository.getStoredCredentialsBlocking().second
//    }

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
//        when (loginState) {
//            is LoginState.Error -> ErrorDialog(
//                message = (loginState as LoginState.Error).message,  // 不需要显式转换
//                onDismiss = {
//                    loginViewModel.setLoginStateIdle()
//                },
//                onRetry = {
//                    loginViewModel.login(username, password)
//                }
//            )
//
//            is LoginState.Loading -> LoadingDialog()
//            is LoginState.Idle -> {}
//        }

        // 登录按钮
        LoginButton(
            enabled = username.isNotBlank() && password.isNotBlank(),
            onClick = {
//                AppStateManager.login(username, password)
            }
        )
    }
}

@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
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
            Button(
                onClick = onRetry
            ) {
                Text(text = "再试试？")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "算了")
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





package team.bjtuss.bjtuselfservice.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.MainApplication.Companion.appContext
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.database.AppDatabase

@Composable
fun SettingsItemCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    hPadding: Dp = 12.dp,
    vPadding: Dp = 14.dp,
    onClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        elevation = CardDefaults.elevatedCardElevation(
            6.dp
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = hPadding, vertical = vPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBasicLinkItem(
    title: Int,
    subtitle: String = "",
    icon: Int,
    link: String = "",
    onClick: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    SettingsItemCard(
        cornerRadius = 16.dp,
        onClick = {
            if (link.isNotBlank()) {
                uriHandler.openUri(link)
            } else onClick()
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = stringResource(id = title),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(id = title),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

object SettingsRepository {
    private val Context.dataStore by preferencesDataStore("settings")
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val PASSWORD_KEY = stringPreferencesKey("password")

    suspend fun setCredentials(username: String, password: String) {
        appContext.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = username
            preferences[PASSWORD_KEY] = password
        }
    }

    // 获取凭据（Flow 方式）
    fun getStoredCredentials(): Flow<Pair<String?, String?>> {
        return appContext.dataStore.data.map { preferences ->
            val username = preferences[USERNAME_KEY]
            val password = preferences[PASSWORD_KEY]
            username to password
        }
    }

    // 获取凭据（阻塞式同步获取）
    suspend fun getStoredCredentialsBlocking(): Pair<String, String> {
        return appContext.dataStore.data.first().let { preferences ->
            val username = preferences[USERNAME_KEY] ?: ""
            val password = preferences[PASSWORD_KEY] ?: ""
            username to password
        }
    }


    fun isCredentialsEmpty(): Flow<Boolean> {
        return appContext.dataStore.data.map { preferences ->
            val username = preferences[USERNAME_KEY]
            val password = preferences[PASSWORD_KEY]
            username == "" && password == ""
        }
    }

    suspend fun clearCredentials() {
        appContext.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = ""
            preferences[PASSWORD_KEY] = ""
        }
    }
}

class SettingViewModel : ViewModel() {
    private val _loginStatus = MutableStateFlow(false)
    val loginStatus: StateFlow<Boolean> = _loginStatus.asStateFlow()

    private val _credentials = MutableStateFlow<Pair<String?, String?>>(null to null)
    val credentials: StateFlow<Pair<String?, String?>> = _credentials.asStateFlow()

    init {
        viewModelScope.launch {
            SettingsRepository.getStoredCredentials().collect { storedCredentials ->
                _credentials.value = storedCredentials
                _loginStatus.value = !storedCredentials.first.isNullOrBlank()
                        && !storedCredentials.second.isNullOrBlank()
            }
        }
    }

    fun getCredentials(): Pair<String?, String?> {
        return runBlocking {
            withContext(Dispatchers.IO) {
                SettingsRepository.getStoredCredentials().first()
            }
        }
    }

    fun setCredentials(username: String, password: String) = viewModelScope.launch {
        SettingsRepository.setCredentials(username, password)
    }

    fun clearCredentials() = viewModelScope.launch {
        SettingsRepository.clearCredentials()
    }

}


@Composable
fun SettingScreen(loginViewModel: LoginViewModel) {

    val studentInfo = StudentAccountManager.getInstance().studentInfo
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        item {
            SettingsItemCard(
                onClick = {},
                content = {
                    studentInfo.value?.let {
                        Text(
                            text = it.stuName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            )
        }
        item {
            ClearLocalCacheItem()
        }
        item {
            Button(
                onClick = {
                    loginViewModel.logout()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Logout", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun ClearLocalCacheItem() {
    // 协程作用域，用于启动协程
    val scope = rememberCoroutineScope()
    // 是否显示确认弹窗
    var showConfirmationDialog by remember { mutableStateOf(false) }
    // 是否显示加载弹窗(或加载进度)
    var isLoading by remember { mutableStateOf(false) }

    // 如果需要显示确认弹窗
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = {
                // 用户点击对话框外区域或者返回键时的处理，可自行决定是否关闭弹窗
                showConfirmationDialog = false
            },
            title = {
                Text(text = "确认清除本地缓存？")
            },
            text = {
                Text(text = "此操作将清除您所有本地缓存数据且无法恢复，确定要继续吗？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        // 先关闭确认弹窗
                        showConfirmationDialog = false
                        // 在协程中执行挂起操作
                        scope.launch {
                            isLoading = true
                            try {
                                // 这里分别删除不同表数据，仅做演示
                                AppDatabase.getInstance().examScheduleEntityDao().deleteAll()
                                AppDatabase.getInstance().gradeEntityDao().deleteAll()
                                AppDatabase.getInstance().courseEntityDao().deleteAll()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // 根据需求处理异常
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        // 用户取消时关闭弹窗
                        showConfirmationDialog = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 如果需要显示加载进度(可替换成您自定义的 LoadingDialog)
    if (isLoading) {
        // 一种半透明遮罩 + 中心圆形进度指示器的示例
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                // 用于屏蔽背后控件点击
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    // 清除本地缓存的条目
    SettingsItemCard(
        onClick = {
            // 点击后弹出确认对话框
            showConfirmationDialog = true
        },
        content = {
            Text(
                text = "清除本地缓存",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    )
}



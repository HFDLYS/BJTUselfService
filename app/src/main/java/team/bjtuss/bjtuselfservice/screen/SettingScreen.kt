package team.bjtuss.bjtuselfservice.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.MainApplication.Companion.appContext
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.repository.SettingsRepository
import team.bjtuss.bjtuselfservice.repository.fetchLatestRelease
import team.bjtuss.bjtuselfservice.viewmodel.LoginViewModel
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale


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
fun SettingScreen(loginViewModel: LoginViewModel, mainViewModel: MainViewModel) {
    val studentInfo = StudentAccountManager.getInstance().studentInfo
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        item {
            SettingItemCard(
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
            CheckForUpdateSettingItemCard()
        }
        item {
            ClearLocalCacheItem(mainViewModel)
        }
        item {
            SettingLinkItem(
                title = "Github项目",
                subtitle = "",
                icon = R.drawable.ic_github,
                link = "https://github.com/HFDLYS/BJTUselfService"
            )
        }

        item {
            Button(
                onClick = {
                    loginViewModel.logout()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "退出账号", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun ClearLocalCacheItem(mainViewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = {
                showConfirmationDialog = false
            },
            title = {
                Text(text = "确认清除本地数据？")
            },
            text = {
                Text(text = "此操作将清除您所有本地数据且无法恢复，确定要继续吗？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmationDialog = false
                        scope.launch {
                            isLoading = true
                            try {
                                AppDatabase.getInstance().examScheduleEntityDao().deleteAll()
                                AppDatabase.getInstance().gradeEntityDao().deleteAll()
                                AppDatabase.getInstance().courseEntityDao().deleteAll()
                                AppDatabase.getInstance().homeworkEntityDao().deleteAll()
                                mainViewModel.clearChange()
                            } catch (e: Exception) {
                                e.printStackTrace()
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
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text("算乐")
                }
            }
        )
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    SettingItemCard(
        onClick = {
            showConfirmationDialog = true
        },
        content = {
            Text(
                text = "清除本地数据",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    )
}


@Composable
fun SettingItemCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val cornerRadius: Dp = 16.dp
    val hPadding: Dp = 12.dp
    val vPadding: Dp = 14.dp
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
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

@Composable
fun SettingItemCard(
    title: String,
    subtitle: String = "",
    icon: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val cornerRadius: Dp = 16.dp
    val hPadding: Dp = 12.dp
    val vPadding: Dp = 14.dp
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
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
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(
                        painter = painterResource(id = it),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingLinkItem(
    title: String,
    subtitle: String = "",
    icon: Int? = null,
    link: String = "",
) {
    val uriHandler = LocalUriHandler.current

    SettingItemCard(title = title, subtitle = subtitle, icon = icon) {
        if (link.isNotBlank()) {
            uriHandler.openUri(link)
        }
    }
}


@Composable
fun CheckForUpdateSettingItemCard() {
    val versionName =
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    var versionLatest by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var updateMarkdown by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf<String?>(null) }

    SettingItemCard(title = "检查更新", subtitle = versionName, icon = R.drawable.ic_code) {
        showDialog = true
        scope.launch {
            isChecking = true
            val release = fetchLatestRelease()
            updateMessage = release?.let {
                val instant = Instant.parse(it.publishedAt)
                val localDateTime = instant.atZone(ZoneId.systemDefault())
                "发布时间: ${
                    localDateTime.format(
                        DateTimeFormatter.ofPattern(
                            "yyyy年M月d日 HH:mm",
                            Locale.getDefault()
                        )
                    )
                }"
            } ?: "检查失败，请稍后再试"
            updateMarkdown = release?.body ?: ""
            versionLatest = release?.tagName ?: ""
            downloadUrl = release?.htmlUrl
            isChecking = false
        }

    }



    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                downloadUrl?.let { url ->
                    if (versionLatest.isNotEmpty() && (versionName < versionLatest)) {
                        Button(
                            onClick = {
                                showDialog = false
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                appContext.startActivity(intent)
                            }
                        ) {
                            Text("前往下载")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    if (versionLatest.isNotEmpty() && (versionName < versionLatest)) {
                        Text("人习于枸且非一日")
                    } else Text("关闭")
                }
            },
            text = {
                if (isChecking) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(100.dp)
                    ) {
                        RotatingImageLoader(
                            image = painterResource(id = R.drawable.loading_icon),
                            rotationDuration = 1000,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                } else {
                    Column {
                        if (versionLatest.isNotEmpty() && (versionName < versionLatest)) {
                            Text(
                                "发现新版本${versionLatest}！",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                updateMessage,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            LazyColumn {
                                item {
                                    MarkdownText(
                                        markdown = updateMarkdown
                                    )
                                }
                            }
                        } else {
                            Text(
                                "最新版本：${versionLatest}！",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                updateMessage,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            title = { Text("检查更新") }
        )
    }
}

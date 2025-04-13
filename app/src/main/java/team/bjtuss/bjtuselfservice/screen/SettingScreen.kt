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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.MainApplication.Companion.appContext
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.repository.fetchLatestRelease
import team.bjtuss.bjtuselfservice.viewmodel.AppStateManager
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale


@Composable
fun SettingScreen(mainViewModel: MainViewModel) {
    val studentInfo = StudentAccountManager.getInstance().studentInfo
    val settingViewModel = mainViewModel.settingViewModel

    // Collect all settings states at once
    val autoSyncGradeEnable by settingViewModel.autoSyncGradeEnable.collectAsState()
    val autoSyncHomeworkEnable by settingViewModel.autoSyncHomeworkEnable.collectAsState()
    val autoSyncScheduleEnable by settingViewModel.autoSyncScheduleEnable.collectAsState()
    val autoSyncExamEnable by settingViewModel.autoSyncExamEnable.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        // User profile section
        item {
            SettingItem(
                title = studentInfo.value?.stuName ?: "未登录",
            )
        }

        // App info section
        item {
            CheckForUpdateSettingItem()
        }

        item {
            ClearLocalCacheItem(mainViewModel)
        }

        item {
            LinkSettingItem(
                title = "Github项目",
                subtitle = "查看源代码",
                icon = R.drawable.ic_github,
                link = "https://github.com/HFDLYS/BJTUselfService"
            )
        }

        // Auto sync settings section
        item {
            Text(
                text = "自动同步设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        // Auto sync toggle items
        item {
            SwitchSettingItem(
                title = "自动同步成绩",
                checked = autoSyncGradeEnable,
                onCheckedChange = { settingViewModel.setGradeAutoSyncOption(it) }
            )
        }

        item {
            SwitchSettingItem(
                title = "自动同步作业",
                checked = autoSyncHomeworkEnable,
                onCheckedChange = { settingViewModel.setHomeworkAutoSyncOption(it) }
            )
        }

        item {
            SwitchSettingItem(
                title = "自动同步课表",
                checked = autoSyncScheduleEnable,
                onCheckedChange = { settingViewModel.setScheduleAutoSyncOption(it) }
            )
        }

        item {
            SwitchSettingItem(
                title = "自动同步考试",
                checked = autoSyncExamEnable,
                onCheckedChange = { settingViewModel.setAutoSyncExamsEnable(it) }
            )
        }

        // Logout button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { AppStateManager.logout(mainViewModel) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "退出账号",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
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
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text(text = "确认清除本地数据？") },
            text = { Text(text = "此操作将清除您所有本地数据且无法恢复，确定要继续吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmationDialog = false
                        scope.launch {
                            isLoading = true
                            try {
                                with(AppDatabase.getInstance()) {
                                    examScheduleEntityDao().deleteAll()
                                    gradeEntityDao().deleteAll()
                                    courseEntityDao().deleteAll()
                                    homeworkEntityDao().deleteAll()
                                }
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
                    Text("取消")
                }
            }
        )
    }

    // Loading overlay
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "正在清除数据...",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    SettingItem(
        title = "清除本地数据",
        onClick = { showConfirmationDialog = true }
    )
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String = "",
    icon: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    SettingItemBase(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(
                    painter = painterResource(id = it),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingItemBase(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSettingItem(
    title: String,
    subtitle: String = "",
    icon: Int? = null,
    link: String = "",
) {
    val uriHandler = LocalUriHandler.current

    SettingItemBase(
        onClick = { if (link.isNotBlank()) uriHandler.openUri(link) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(
                        painter = painterResource(id = it),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

        }
    }
}

@Composable
fun CheckForUpdateSettingItem() {
    val versionName = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    var versionLatest by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var updateMarkdown by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf<String?>(null) }

    SettingItemBase(
        onClick = {
            showDialog = true
            scope.launch {
                isChecking = true
                try {
                    val release = fetchLatestRelease()
                    release?.let {
                        val instant = Instant.parse(it.publishedAt)
                        val localDateTime = instant.atZone(ZoneId.systemDefault())
                        updateMessage = "发布时间: ${
                            localDateTime.format(
                                DateTimeFormatter.ofPattern(
                                    "yyyy年M月d日 HH:mm",
                                    Locale.getDefault()
                                )
                            )
                        }"
                        updateMarkdown = it.body ?: ""
                        versionLatest = it.tagName
                        downloadUrl = it.htmlUrl
                    } ?: run {
                        updateMessage = "检查失败，请稍后再试"
                    }
                } catch (e: Exception) {
                    updateMessage = "检查失败: ${e.message}"
                } finally {
                    isChecking = false
                }
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_code),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "检查更新",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = "当前版本: $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
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
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在检查更新...")
                        }
                    }
                } else {
                    Column {
                        if (versionLatest.isNotEmpty() && (versionName < versionLatest)) {
                            Text(
                                "发现新版本 $versionLatest",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                updateMessage,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                LazyColumn {
                                    item {
                                        MarkdownText(
                                            markdown = updateMarkdown
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                "当前版本: $versionName",
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (versionLatest.isNotEmpty()) {
                                Text(
                                    "最新版本: $versionLatest",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "您已经使用最新版本!",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(updateMessage)
                        }
                    }
                }
            },
            title = { Text("检查更新") }
        )
    }
}

@Composable
fun SwitchSettingItem(
    title: String,
    subtitle: String = "",
    icon: Int? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    SettingItemBase {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(
                        painter = painterResource(id = it),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}





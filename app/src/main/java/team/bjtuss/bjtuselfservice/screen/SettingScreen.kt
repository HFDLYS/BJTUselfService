package team.bjtuss.bjtuselfservice.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.MainApplication.Companion.appContext
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.reminder.ReminderScheduler
import team.bjtuss.bjtuselfservice.repository.fetchLatestRelease
import team.bjtuss.bjtuselfservice.statemanager.AppEvent
import team.bjtuss.bjtuselfservice.statemanager.AppEventManager
import team.bjtuss.bjtuselfservice.statemanager.AuthenticatorManager
import team.bjtuss.bjtuselfservice.ui.theme.Theme
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream


@Composable
fun SettingScreen(mainViewModel: MainViewModel) {
    val studentInfo = StudentAccountManager.getInstance().studentInfo
    val settingViewModel = mainViewModel.settingViewModel

    // Collect all settings states at once
    val autoSyncGradeEnable by settingViewModel.autoSyncGradeEnable.collectAsState()
    val autoSyncHomeworkEnable by settingViewModel.autoSyncHomeworkEnable.collectAsState()
    val autoSyncScheduleEnable by settingViewModel.autoSyncScheduleEnable.collectAsState()
    val autoSyncExamEnable by settingViewModel.autoSyncExamEnable.collectAsState()
    val checkUpdateEnable by settingViewModel.checkUpdateEnable.collectAsState()
    val currentTheme by settingViewModel.currentTheme.collectAsState()
    val dynamicColorEnable by settingViewModel.dynamicColorEnable.collectAsState()


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
                iconContent = {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
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

        item {
            Text(
                text = "主题",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        item {
            ThemeSelectionItem(
                currentTheme = currentTheme,
                onThemeSelected = { settingViewModel.setThemeOption(it) }
            )
        }
        item {
            SwitchSettingItem(
                title = "动态配色",
                subtitle = "配色跟随系统主题",
                iconContent = {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                checked = dynamicColorEnable,
                onCheckedChange = { settingViewModel.setDynamicColorOption(it) },
            )
        }

        item {
            CustomBackgroundSettingItem(settingViewModel)
        }

        item {
            ReminderSettingsCard(settingViewModel)
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
                iconContent = {
                    Icon(
                        imageVector =  Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                checked = autoSyncGradeEnable,
                onCheckedChange = { settingViewModel.setGradeAutoSyncOption(it) }
            )
        }

        item {
            SwitchSettingItem(
                title = "自动同步作业",
                iconContent = {
                    Icon(
                        imageVector =  Icons. AutoMirrored.Filled.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                checked = autoSyncHomeworkEnable,
                onCheckedChange = { settingViewModel.setHomeworkAutoSyncOption(it) }
            )
        }

        item {
            SwitchSettingItem(
                title = "自动同步课表",
                iconContent = {
                    Icon(
                        imageVector =  Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                checked = autoSyncScheduleEnable,
                onCheckedChange = { settingViewModel.setScheduleAutoSyncOption(it) }
            )
        }

        item {
            SwitchSettingItem(
                title = "自动同步考试",
                iconContent = {
                    Icon(
                        imageVector =  Icons.Default.Quiz,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary)
                },
                checked = autoSyncExamEnable,
                onCheckedChange = { settingViewModel.setExamsAutoSyncOption(it) }
            )
        }

        item {
            SwitchSettingItem(
                title = "打开更新提示",
                iconContent = {
                    Icon(
                        imageVector =  Icons.Default.Update,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                checked = checkUpdateEnable,
                onCheckedChange = { settingViewModel.setCheckUpdateEnable(it) }
            )
        }

        // Logout button
        item {
//            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    AppEventManager.sendEvent(AppEvent.LogoutRequest(clearAllData = {
                        AuthenticatorManager.clearAllData(
                            mainViewModel
                        )
                    }))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
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
fun ReminderSettingsCard(settingViewModel: team.bjtuss.bjtuselfservice.viewmodel.SettingViewModel) {
    val context = LocalContext.current
    val courseEnabled by settingViewModel.courseReminderEnabled.collectAsState()
    val courseLead by settingViewModel.courseReminderLeadMinutes.collectAsState()
    val examEnabled by settingViewModel.examReminderEnabled.collectAsState()
    val examLead by settingViewModel.examReminderLeadMinutes.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(courseEnabled, courseLead, examEnabled, examLead) {
        ReminderScheduler.reschedule(context)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("上课与考试提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "通知会以标准安卓通知发出，可由 HyperIsland 转成超级岛",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("课程提醒", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(
                        "上课前 " + formatLead(courseLead) + "提醒课程、教室和教师",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = courseEnabled,
                    onCheckedChange = {
                        requestNotificationPermission()
                        settingViewModel.setCourseReminderEnabled(it)
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5, 10, 15, 30, 60).forEach { minutes ->
                    FilterChip(
                        selected = courseLead == minutes,
                        onClick = {
                            requestNotificationPermission()
                            settingViewModel.setCourseReminderLeadMinutes(minutes)
                        },
                        label = { Text(formatLead(minutes)) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("考试提醒", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(
                        "考试前 " + formatLead(examLead) + "提醒考试和教室",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = examEnabled,
                    onCheckedChange = {
                        requestNotificationPermission()
                        settingViewModel.setExamReminderEnabled(it)
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(30, 60, 360, 1440, 2880).forEach { minutes ->
                    FilterChip(
                        selected = examLead == minutes,
                        onClick = {
                            requestNotificationPermission()
                            settingViewModel.setExamReminderLeadMinutes(minutes)
                        },
                        label = { Text(formatLead(minutes)) },
                    )
                }
            }
        }
    }
}

private fun formatLead(minutes: Int): String = when {
    minutes % 1440 == 0 -> (minutes / 1440).toString() + "天"
    minutes % 60 == 0 -> (minutes / 60).toString() + "小时"
    else -> minutes.toString() + "分钟"
}

@Composable
fun CustomBackgroundSettingItem(settingViewModel: team.bjtuss.bjtuselfservice.viewmodel.SettingViewModel) {
    val context = LocalContext.current
    val backgroundImageUri by settingViewModel.backgroundImageUri.collectAsState()

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()

    val cropLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val resultUri = com.yalantis.ucrop.UCrop.getOutput(result.data!!)
            if (resultUri != null) {
                settingViewModel.setBackgroundImageUri(resultUri.path ?: "")
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val destinationFile = java.io.File(context.filesDir, "custom_background_${System.currentTimeMillis()}.jpg")
            val destinationUri = android.net.Uri.fromFile(destinationFile)
            
            val options = com.yalantis.ucrop.UCrop.Options().apply {
                setCompressionQuality(100)
                setCompressionFormat(android.graphics.Bitmap.CompressFormat.PNG)
            }
            val intent = com.yalantis.ucrop.UCrop.of(uri, destinationUri)
                .withAspectRatio(screenWidth, screenHeight)
                .withOptions(options)
                .getIntent(context)
            cropLauncher.launch(intent)
        }
    }

    SettingItemBase(
        onClick = {
            if (backgroundImageUri.isNotEmpty()) {
                settingViewModel.setBackgroundImageUri("")
            } else {
                launcher.launch("image/*")
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
                    imageVector = Icons.Default.Brush,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "自定义背景",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (backgroundImageUri.isNotEmpty()) "点击清除背景" else "点击选择背景图片",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
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
                                mainViewModel.gradeViewModel
                                    .clearAllPersistedGradeSelections()
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
        iconContent = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        onClick = { showConfirmationDialog = true }
    )
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String = "",
    iconContent: @Composable (() -> Unit)? = null, // 改用可组合内容参数

    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    SettingItemBase(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            iconContent?.let {
                it.invoke()
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
    val versionName =
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
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
                                style = MaterialTheme.typography.titleLarge,
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
    iconContent: @Composable (() -> Unit)? = null, // 改用可组合内容参数
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    SettingItemBase(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 图标内容区域
                iconContent?.invoke()?.also {
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

@Composable
fun DropdownSettingItem(
    title: String,
    subtitle: String = "",
    icon: ImageVector? = null,
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // 保存下拉菜单锚点的引用

    SettingItemBase(
        onClick = { expanded = true }  // 移动点击事件到SettingItemBase
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)  // 给左侧内容一个权重
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(16.dp))

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

            // 选项内容和下拉箭头
            Box(
                modifier = Modifier.wrapContentSize(Alignment.TopEnd)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = selectedOption,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // 将下拉菜单与Box对齐
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    offset = DpOffset(0.dp, 0.dp)  // 调整菜单出现位置
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = option) },
                            onClick = {
                                onOptionSelected(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}


/**
 * 主题选择设置项
 */
@Composable
fun ThemeSelectionItem(
    currentTheme: Theme,
    onThemeSelected: (Theme) -> Unit
) {
    val themeOptions = listOf(Theme.Light, Theme.Dark, Theme.System)

    // 将Theme转换为显示名称
    val themeNameMap = mapOf(
        Theme.Light to "浅色",
        Theme.Dark to "深色",
        Theme.System to "跟随系统"
    )

    // 当前主题的名称
    val currentThemeName = themeNameMap[currentTheme] ?: "跟随系统"

    // 所有主题的名称列表
    val themeNames = themeOptions.map { themeNameMap[it] ?: "" }

    DropdownSettingItem(
        title = "主题设置",
//        subtitle = "选择应用的显示主题",
        icon = Icons.Default.Brush, // 可以添加一个主题图标的资源ID
        selectedOption = currentThemeName,
        options = themeNames,
        onOptionSelected = { selectedName ->
            // 根据选择的名称找到对应的Theme
            themeNameMap.entries.find { it.value == selectedName }?.key?.let { theme ->
                onThemeSelected(theme)
            }
        }
    )
}

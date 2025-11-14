package team.bjtuss.bjtuselfservice.screen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import team.bjtuss.bjtuselfservice.RouteManager
import team.bjtuss.bjtuselfservice.component.CalendarComponent
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity
import team.bjtuss.bjtuselfservice.entity.GradeEntity
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import team.bjtuss.bjtuselfservice.statemanager.AppEvent
import team.bjtuss.bjtuselfservice.statemanager.AppEventManager
import team.bjtuss.bjtuselfservice.statemanager.AppState
import team.bjtuss.bjtuselfservice.statemanager.AppStateManager
import team.bjtuss.bjtuselfservice.viewmodel.DataChange
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, mainViewModel: MainViewModel) {

    // 使用 mutableStateOf 来追踪刷新状态
//    var isRefreshing by remember { mutableStateOf(false) }

    val appState by AppStateManager.appState.collectAsState()
    val refreshState =
        rememberSwipeRefreshState(isRefreshing = appState == AppState.NetworkProgress)


    val gradeChangeList: List<DataChange<GradeEntity>> by mainViewModel.gradeViewModel.changeList.collectAsState()
    val courseChangeList: List<DataChange<CourseEntity>> by mainViewModel.courseScheduleViewModel.changeList.collectAsState()
    val examChangeList: List<DataChange<ExamScheduleEntity>> by mainViewModel.examScheduleViewModel.changeList.collectAsState()
    val homeworkChangeList: List<DataChange<HomeworkEntity>> by mainViewModel.homeworkViewModel.changeList.collectAsState()
    val homeworkList: List<HomeworkEntity> by mainViewModel.homeworkViewModel.homeworkList.collectAsState()
    val status by mainViewModel.statusViewModel.status.collectAsState()

    var selectedGradeChange by remember { mutableStateOf<DataChange<GradeEntity>?>(null) }
    var selectedHomeworkChange by remember { mutableStateOf<DataChange<HomeworkEntity>?>(null) }
    var selectedExamChange by remember { mutableStateOf<DataChange<ExamScheduleEntity>?>(null) }
    var showGradeDialog by remember { mutableStateOf(false) }
    var showHomeworkDialog by remember { mutableStateOf(false) }
    var showExamDialog by remember { mutableStateOf(false) }


    val autoSyncGradeEnable by mainViewModel.settingViewModel.autoSyncGradeEnable.collectAsState()
    val autoSyncHomeworkEnable by mainViewModel.settingViewModel.autoSyncHomeworkEnable.collectAsState()
    val autoSyncScheduleEnable by mainViewModel.settingViewModel.autoSyncScheduleEnable.collectAsState()
    val autoSyncExamEnable by mainViewModel.settingViewModel.autoSyncExamEnable.collectAsState()
    // 刷新处理函数
    val handleRefresh = {
        if (appState != AppState.NetworkProgress) {
            println("379183912")
            AppEventManager.sendEvent(AppEvent.DataSyncRequest)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status Info Section
        StatusInfo(
            ecardBalance = formatEcardBalance(status?.EcardBalance),
            netBalance = formatNetBalance(status?.NetBalance),
            newMailCount = formatNewMailCount(status?.NewMailCount),
            navController = navController
        )


        CalendarComponent(mainViewModel)


        Text(
            text = "ATTENTION!!!",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(start = 16.dp, top = 8.dp)
        )



        SwipeRefresh(
            state = refreshState,
            onRefresh = handleRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    HomeworkNoticeCard(homeworkList, navController)
                }

                // Grade Changes Section
                if (gradeChangeList.isNotEmpty()) {
                    if (autoSyncGradeEnable) {
                        mainViewModel.gradeViewModel.syncDataAndClearChange()
                    } else {
                        item {
                            ChangeSection(
                                title = "成绩单变动",
                                changes = gradeChangeList,
                                onItemClick = { change ->
                                    selectedGradeChange = change
                                    showGradeDialog = true
                                }
                            )
                        }
                    }
                }

                // Course Changes Section
                if (courseChangeList.isNotEmpty()) {
                    if (autoSyncScheduleEnable) {
                        mainViewModel.courseScheduleViewModel.syncDataAndClearChange()
                    } else {
                        item {
                            ChangeSection(
                                title = "课程表变动",
                                changes = courseChangeList,
                                onItemClick = { _ ->
                                    navController.navigate(RouteManager.CourseSchedule)
                                }
                            )
                        }
                    }
                }

                // Exam Changes Section
                if (examChangeList.isNotEmpty()) {
                    if (autoSyncExamEnable) {
                        mainViewModel.examScheduleViewModel.syncDataAndClearChange()
                    } else {
                        item {
                            ChangeSection(
                                title = "考试安排变动",
                                changes = examChangeList,
                                onItemClick = { change ->
                                    selectedExamChange = change
                                    showExamDialog = true
                                }
                            )
                        }
                    }

                }

                // Homework Changes Section
                if (homeworkChangeList.isNotEmpty()) {
                    if (autoSyncHomeworkEnable) {
                        mainViewModel.homeworkViewModel.syncDataAndClearChange()
                    } else {
                        item {
                            ChangeSection(
                                title = "作业变动",
                                changes = homeworkChangeList,
                                onItemClick = { change ->
                                    selectedHomeworkChange = change
                                    showHomeworkDialog = true
                                }
                            )
                        }
                    }
                }
            }

        }
    }

    // Dialogs
    if (showGradeDialog && selectedGradeChange != null) {
        DetailedChangeDialog(
            change = selectedGradeChange!!,
            onDismiss = { showGradeDialog = false },
            cardItem = { GradeItemCard(it) },
            onClick = { navController.navigate(RouteManager.Grade) }
        )
    }

    if (showHomeworkDialog && selectedHomeworkChange != null) {
        DetailedChangeDialog(
            change = selectedHomeworkChange!!,
            onDismiss = { showHomeworkDialog = false },
            cardItem = { HomeworkItemCard(it) },
            onClick = { navController.navigate(RouteManager.HomeWork) }
        )
    }

    if (showExamDialog && selectedExamChange != null) {
        DetailedChangeDialog(
            change = selectedExamChange!!,
            onDismiss = { showExamDialog = false },
            cardItem = { ExamItemCard(it) },
            onClick = { navController.navigate(RouteManager.ExamSchedule) }
        )
    }
}

@Composable
private fun <T> ChangeSection(
    title: String,
    changes: List<DataChange<T>>,
    onItemClick: (DataChange<T>) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp)
    )

    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        changes.forEach { change ->
            ChangeCard(
                dataChange = change,
                onClick = { onItemClick(change) }
            )
        }
    }
}

// 格式化工具函数
private fun formatEcardBalance(balance: String?): String {
    val balanceText = "校园卡余额：$balance"
    return if (balance?.toDoubleOrNull() ?: 0.0 < 20) {
        "$balanceText，该充了"
    } else {
        balanceText
    }
}

private fun formatNetBalance(balance: String?): String {
    val balanceText = "校园网余额：$balance"
    return if (balance == "0") {
        "$balanceText，😱没网了"
    } else {
        balanceText
    }
}

private fun formatNewMailCount(count: String?): String {
    val mailText = "新邮件：$count"
    return if (count != "0") {
        "$mailText，记得去看哦"
    } else {
        mailText
    }
}

@Composable
fun StatusInfo(
    ecardBalance: String,
    netBalance: String,
    newMailCount: String,
    navController: NavController
) {

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MailButton({
                Text(newMailCount, fontSize = 18.sp)
            }, navController)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            EcardButton({ Text(ecardBalance, fontSize = 18.sp) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetButton({ Text(netBalance, fontSize = 18.sp) })
        }
    }

}

@Composable
fun MailButton(content: @Composable () -> Unit, navController: NavController) {
    Button(
        onClick = {
            navController.navigate(RouteManager.Email)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "New Mail",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            content()
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun EcardButton(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = "Ecard Balance",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            content()
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }

    // 显示对话框
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("校园卡充值") },
            text = {
                Text("请注意，接下来即将转跳完美校园app\n确保自己已安装哦☺️")
            },
            confirmButton = {
                Button(
                    onClick = {
                        launchWanMeiCampusApp(context)
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("打开应用")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

fun shareToWeChat(context: Context) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "https://weixin.bjtu.edu.cn/pay/wap/network/recharge.html")
    }
    val chooser = Intent.createChooser(shareIntent, "请选择：“微信：发送给朋友”")

    try {
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "未找到“微信”app？？？？", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun NetButton(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "Net Balance",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            content()
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }

    // 显示充值提醒对话框
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("校园网续费") },
            text = {
                Text("不好意思直接转跳微信成本还是太高，不过\n注意：以下操作需微信绑定学校企业号\n请分享至微信，后打开（莫吐槽🙏）哦")
            },
            confirmButton = {
                Button(
                    onClick = {
                        shareToWeChat(context)
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("分享至微信")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}


// 尝试启动“完美校园”应用
fun launchWanMeiCampusApp(context: Context) {
    val intent = Intent().apply {
        component = ComponentName(
            "com.newcapec.mobile.ncp",
            "com.wanxiao.basebusiness.activity.SplashActivity"
        )
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "未找到“完美校园”app", Toast.LENGTH_LONG).show()
    }
}


@Composable
private fun HomeworkNoticeCard(
    homeworkList: List<HomeworkEntity>,
    navController: NavController
) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val now = LocalDateTime.now()
    var countForDeadline = 0
    val DDLList = mutableListOf<HomeworkEntity>()
    var showDetail by remember { mutableStateOf(false) }
    homeworkList.forEach {
        try {
            if (ChronoUnit.HOURS.between(
                    now,
                    LocalDateTime.parse(it.endTime, formatter)
                ) in 0..48
            ) {
                if (it.subStatus != "已提交") {
                    countForDeadline++
                    DDLList.add(it)
                }
            }
        } catch (_: Exception) {
        }
    }

    if (countForDeadline > 0) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            onClick = {
                showDetail = true
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onErrorContainer)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "有${countForDeadline}项作业已经迫在眉睫！",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View Details",
                    tint = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                )
            }
        }

    }
    if (showDetail) {
        DetailedDialog(
            title = "作业截止提醒",
            items = DDLList,
            onDismiss = { showDetail = false },
            cardItem = { HomeworkItemCard(it) },
            onClick = { navController.navigate(RouteManager.HomeWork) }
        )
    }
}

@Composable
private fun <T> ChangeCard(
    dataChange: DataChange<T>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        val (backgroundColor, textColor, icon) = when (dataChange) {
            is DataChange.Added -> Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                Icons.Default.Add
            )

            is DataChange.Modified -> Triple(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
                Icons.Default.Edit
            )

            is DataChange.Deleted -> Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                Icons.Default.Delete
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor
                )
                Text(
                    text = when (dataChange) {
                        is DataChange.Added -> "新增 ${dataChange.items.size}项"
                        is DataChange.Modified -> "变化 ${dataChange.items.size}项"
                        is DataChange.Deleted -> "删除 ${dataChange.items.size}项"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View Details",
                tint = textColor.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
fun <T> DetailedChangeDialog(
    change: DataChange<T>,
    onDismiss: () -> Unit,
    cardItem: @Composable (T) -> Unit,
    onClick: () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Dialog Header with count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (change) {
                            is DataChange.Added -> "新增详情"
                            is DataChange.Modified -> "变动详情"
                            is DataChange.Deleted -> "删除详情"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when (change) {
                            is DataChange.Added -> "${change.items.size}项"
                            is DataChange.Modified -> "${change.items.size}项"
                            is DataChange.Deleted -> "${change.items.size}项"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Dialog Content
                when (change) {
                    is DataChange.Added -> {
                        change.items.forEachIndexed { index, grade ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            cardItem(grade)
                        }
                    }

                    is DataChange.Modified -> {
                        change.items.forEachIndexed { index, (newGrade, oldGrade) ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            cardItem(newGrade)
                            cardItem(oldGrade)
                        }
                    }

                    is DataChange.Deleted -> {
                        change.items.forEachIndexed { index, grade ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            cardItem(grade)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 16.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                    ) {
                        Text("撤了", color = MaterialTheme.colorScheme.primary)
                    }

                    Button(
                        onClick = onClick,
                    ) {
                        Text("查看更多👀", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun <T> DetailedDialog(
    title: String,
    items: List<T>,
    onDismiss: () -> Unit,
    cardItem: @Composable (T) -> Unit,
    onClick: () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Dialog Header with count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${items.size}项",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Dialog Content
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    cardItem(item)
                }


                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 16.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                    ) {
                        Text("撤了", color = MaterialTheme.colorScheme.primary)
                    }

                    Button(
                        onClick = onClick,
                    ) {
                        Text("查看更多👀", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}


@Composable
private fun GradeChangeRow(
    fieldName: String,
    oldValue: String,
    newValue: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Text(
            text = fieldName,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = oldValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Changed to",
                modifier = Modifier.padding(horizontal = 8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = newValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
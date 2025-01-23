package team.bjtuss.bjtuselfservice.screen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.RouteManager
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity
import team.bjtuss.bjtuselfservice.entity.GradeEntity
import team.bjtuss.bjtuselfservice.jsonclass.HomeworkJsonType
import team.bjtuss.bjtuselfservice.repository.NetworkRepository
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import team.bjtuss.bjtuselfservice.utils.KotlinUtils
import team.bjtuss.bjtuselfservice.viewmodel.DataChange
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import kotlin.collections.component1
import kotlin.collections.component2

@Composable
fun HomeScreen(navController: NavController, mainViewModel: MainViewModel) {
    val studentAccountManager = StudentAccountManager.getInstance()

    val gradeChangeList: List<DataChange<GradeEntity>> by mainViewModel.gradeViewModel.changeList.collectAsState()
    val courseChangeList: List<DataChange<CourseEntity>> by mainViewModel.courseScheduleViewModel.changeList.collectAsState()
    val examScheduleChangeList: List<DataChange<ExamScheduleEntity>> by mainViewModel.examScheduleViewModel.changeList.collectAsState()

    var status by remember { mutableStateOf<StudentAccountManager.Status?>(null) }
    var selectedChange by remember { mutableStateOf<DataChange<GradeEntity>?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }

    studentAccountManager.status.thenAccept {
        status = it
    }

    NetworkRepository.getQueueStatus().observeForever {
        isRefreshing = it
    }

    // Status info formatting functions
    val ecardBalance = "校园卡余额：${status?.EcardBalance}".let {
        if (status?.EcardBalance?.toDoubleOrNull() ?: 0.0 < 20) {
            "$it，该充了"
        } else {
            it
        }
    }

    val netBalance = "校园网余额：${status?.NetBalance}".let {
        if (status?.NetBalance == "0") {
            "$it，😱没网了"
        } else {
            it
        }
    }

    val newMailCount = "新邮件：${status?.NewMailCount}".let {
        if (status?.NewMailCount != "0") {
            "$it，记得去看哦"
        } else {
            it
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {


        StatusInfo(ecardBalance, netBalance, newMailCount, navController)

        // Grade Changes Section
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
//            colors = CardDefaults.cardColors(
//                containerColor = MaterialTheme.colorScheme.surface,
//            )
        ) {
            Text(
                text = "最新变动",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.padding(start = 16.dp)
            )
            LazyColumn(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                if (gradeChangeList.isNotEmpty()) {
                    item {
                        Text(
                            text = "成绩单变动",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp)
                        )


                        Column(
                            modifier = Modifier,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            gradeChangeList.forEach { gradeChange ->
                                ChangeCard(
                                    dataChange = gradeChange,
                                    onClick = {
                                        selectedChange = gradeChange
                                        showDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                if (courseChangeList.isNotEmpty()) {
                    item {
                        Text(
                            text = "课程表变动",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp)
                        )

                        Column {
                            courseChangeList.forEach { courseChange ->
                                ChangeCard(
                                    dataChange = courseChange,
                                    onClick = {
                                        navController.navigate(RouteManager.CourseSchedule)
                                    }
                                )
                            }
                        }
                    }
                }

                if (examScheduleChangeList.isNotEmpty()) {
                    item {
                        Text(
                            text = "考试安排变动",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp)
                        )

                        Column {
                            examScheduleChangeList.forEach { examChange ->
                                ChangeCard(
                                    dataChange = examChange,
                                    onClick = {
                                        navController.navigate(RouteManager.ExamSchedule)
                                    }
                                )
                            }
                        }
                    }
                }


            }
            if (isRefreshing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 32.dp)
                ) {
                    RotatingImageLoader(
                        image = painterResource(id = R.drawable.loading_icon),
                        rotationDuration = 1000,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        Button(
            onClick = {
                mainViewModel.gradeViewModel.loadDataAndDetectChanges()
                mainViewModel.courseScheduleViewModel.loadDataAndDetectChanges()
                mainViewModel.examScheduleViewModel.loadDataAndDetectChanges()
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally),
            enabled = !isRefreshing
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "刷新",
                modifier = Modifier.size(18.dp)
            )
            Text("刷新",
                fontSize = 18.sp
            )
        }
    }

    if (showDialog && selectedChange != null) {
        DetailedGradeChangeDialog(
            change = selectedChange!!,
            onDismiss = { showDialog = false },
            navController = navController
        )
    }
}

@Composable
fun StatusInfo(
    ecardBalance: String,
    netBalance: String,
    newMailCount: String,
    navController: NavController
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
}

@Composable
fun MailButton(content: @Composable () -> Unit, navController: NavController) {
    Button(
        onClick = {
            navController.navigate(RouteManager.Email)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Email,
            contentDescription = "New Mail",
            tint = Color.Blue
        )
        Spacer(modifier = Modifier.width(8.dp))
        content()
    }
}

@Composable
fun EcardButton(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = {
            showDialog = true
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.AccountBalanceWallet,
            contentDescription = "Ecard Balance",
            tint = Color.Green
        )
        Spacer(modifier = Modifier.width(8.dp))
        content()
    }

    // 显示对话框
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("校园卡充值") },
            text = {
                Text("请注意，接下来即将转跳“完美校园”app\n确保自己已安装哦☺️")
            },
            confirmButton = {
                Button(onClick = {
                    launchWanMeiCampusApp(context)
                    showDialog = false
                }) {
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
        onClick = {
            showDialog = true
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = "Net Balance",
            tint = Color.Red
        )
        Spacer(modifier = Modifier.width(8.dp))
        content()
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
                Button(onClick = {
                    shareToWeChat(context)
                    showDialog = false
                }) {
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
        component = ComponentName("com.newcapec.mobile.ncp", "com.wanxiao.basebusiness.activity.SplashActivity")
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "未找到“完美校园”app", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun<T> ChangeCard(
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
fun DetailedGradeChangeDialog(
    change: DataChange<GradeEntity>,
    onDismiss: () -> Unit,
    navController: NavController
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            onClick = {
                navController.navigate(RouteManager.Grade)
            }
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
                            is DataChange.Added -> "新增成绩详情"
                            is DataChange.Modified -> "成绩变动详情"
                            is DataChange.Deleted -> "删除成绩详情"
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
                            DetailedGradeItem(grade)
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
                            ModifiedGradeItem(newGrade, oldGrade)
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
                            DetailedGradeItem(grade)
                        }
                    }
                }

                // Dialog Actions
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 16.dp)
                ) {
                    Text("关闭", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun DetailedGradeItem(grade: GradeEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.small
            )
            .padding(8.dp)
    ) {
        GradeInfoRow("课程名称", grade.courseName)
        GradeInfoRow("成绩", grade.courseScore)
        GradeInfoRow("学分", grade.courseCredits)
    }
}

@Composable
private fun ModifiedGradeItem(newGrade: GradeEntity, oldGrade: GradeEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Text(
            text = "课程：${newGrade.courseName}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                KotlinUtils.getDifferentFields(newGrade, oldGrade)
                    .forEach { (field, values) ->
                        GradeChangeRow(field, values.second.toString(), values.first.toString())
                    }
            }
        }
    }
}

@Composable
private fun GradeInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
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
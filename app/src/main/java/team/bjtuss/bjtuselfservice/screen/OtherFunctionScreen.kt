package team.bjtuss.bjtuselfservice.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold

import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.repository.OtherFunctionNetworkRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.utils.KotlinUtils

@Composable
fun OtherFunctionScreen() {
    // State management using remember and mutableStateOf
    var isEnglishGrade by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadType by remember { mutableStateOf("") }
    var showSuccessMessage by remember { mutableStateOf(false) }

    // Email subscription state variables
    var email by remember { mutableStateOf("") }
    var normalThreshold by remember { mutableStateOf("48") } // Default: 48 hours
    var emergencyThreshold by remember { mutableStateOf("24") } // Default: 24 hours
    var showEmailDialog by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var normalThresholdError by remember { mutableStateOf<String?>(null) }
    var emergencyThresholdError by remember { mutableStateOf<String?>(null) }
    var isSubscribing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header section with animation
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically()
                    ) {
                        Text(
                            text = "服务功能",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(vertical = 16.dp)
                                .animateContentSize()
                        )
                    }
                }

                // School Calendar Card
                item {
                    FunctionCard(
                        title = "校历下载",
                        description = "查看并下载学校最新的校历信息",
                        icon = Icons.Filled.School,
                        onClick = {
                            handleDownload(
                                coroutineScope,
                                "校历",
                                { downloadSchoolCalendar() },
                                { isDownloading = it },
                                { showSuccessMessage = it },
                                { downloadType = it }
                            )
                        }
                    )
                }

                // Grade List Card
                item {
                    FunctionCard(
                        title = "成绩单下载",
                        description = "下载个人学习成绩单，支持中英文版本",
                        icon = Icons.Filled.Description,
                        extraContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .animateContentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Language,
                                    contentDescription = "Language",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEnglishGrade) "英文版" else "中文版",
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = isEnglishGrade,
                                    onCheckedChange = { isEnglishGrade = it },
                                )
                            }
                        },
                        onClick = {
                            val type = if (isEnglishGrade) "英文成绩单" else "中文成绩单"
                            handleDownload(
                                coroutineScope,
                                type,
                                { downloadGradeList(isEnglishGrade) },
                                { isDownloading = it },
                                { showSuccessMessage = it },
                                { downloadType = it }
                            )
                        }
                    )
                }

                // Email subscription card
                item {
                    FunctionCard(
                        title = "作业提醒订阅",
                        functionStr = "订阅",
                        description = "订阅邮件通知，获取最新作业信息提醒",
                        icon = Icons.Filled.Email,
                        onClick = {
                            showEmailDialog = true
                        }
                    )
                }
            }

            // Overlays (loading and success messages)
            // Loading overlay
            AnimatedVisibility(
                visible = isDownloading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LoadingOverlay(message = "正在下载$downloadType...")
            }

            // Success message overlay
            AnimatedVisibility(
                visible = showSuccessMessage,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                SuccessMessageOverlay(
                    message = if (downloadType == "邮件订阅") "邮件订阅成功！" else "$downloadType 下载成功！",
                    isEmail = downloadType == "邮件订阅"
                )
            }

            // Email subscription dialog
            if (showEmailDialog) {
                EmailSubscriptionDialog(
                    email = email,
                    onEmailChange = { email = it; emailError = null },
                    emailError = emailError,
                    normalThreshold = normalThreshold,
                    onNormalThresholdChange = {
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                            normalThreshold = it
                            normalThresholdError = null
                        }
                    },
                    normalThresholdError = normalThresholdError,
                    emergencyThreshold = emergencyThreshold,
                    onEmergencyThresholdChange = {
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                            emergencyThreshold = it
                            emergencyThresholdError = null
                        }
                    },
                    emergencyThresholdError = emergencyThresholdError,
                    isSubscribing = isSubscribing,
//                    https://love.nimisora.icu/homework-notify/
                    onOpenUrlOfWebSubscription = {
                        uriHandler.openUri("https://love.nimisora.icu/homework-notify/")
                    },
                    onOpenUrlOfSetting = {
                        val studentId = StudentAccountManager.getInstance().stuId
                        uriHandler.openUri(
                            "https://love.nimisora.icu/homework-notify/person.html?token=" +
                                    KotlinUtils.encryptStudentId(studentId, KotlinUtils.SECRET_KEY)
                        )
                    },
                    onConfirm = {
                        val (valid, errors) = validateSubscriptionInputs(
                            email,
                            normalThreshold,
                            emergencyThreshold
                        )

                        emailError = errors.emailError
                        normalThresholdError = errors.normalThresholdError
                        emergencyThresholdError = errors.emergencyThresholdError

                        if (valid) {
                            isSubscribing = true
                            coroutineScope.launch {
                                try {
                                    val success = withContext(Dispatchers.IO) {
                                        subscribeEmailNotice(
                                            email = email,
                                            normalThresholdHours = normalThreshold.toInt(),
                                            emergencyThresholdHours = emergencyThreshold.toInt()
                                        )
                                    }

                                    isSubscribing = false
                                    if (success) {
                                        showEmailDialog = false
                                        // Reset form fields
                                        email = ""
                                        normalThreshold = "48"
                                        emergencyThreshold = "24"

                                        // Show success message
                                        downloadType = "邮件订阅"
                                        showSuccessMessage = true
                                        delay(3000)
                                        showSuccessMessage = false
                                    } else {
                                        emailError = "订阅失败，请稍后重试"
                                    }
                                } catch (e: Exception) {
                                    isSubscribing = false
                                    Toast.makeText(
                                        context,
                                        "订阅失败: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    onDismiss = {
                        if (!isSubscribing) {
                            showEmailDialog = false
                            // Reset error messages
                            emailError = null
                            normalThresholdError = null
                            emergencyThresholdError = null
                        }
                    }
                )
            }
        }
    }
}

/**
 * Handles download operations with proper coroutine management
 */
private fun handleDownload(
    coroutineScope: CoroutineScope,
    type: String,
    downloadAction: suspend () -> Unit,
    updateLoadingState: (Boolean) -> Unit,
    updateSuccessState: (Boolean) -> Unit,
    updateTypeState: (String) -> Unit
) {
    updateTypeState(type)
    updateLoadingState(true)

    coroutineScope.launch {
        try {
            withContext(Dispatchers.IO) {
                downloadAction()
            }
            updateLoadingState(false)
            updateSuccessState(true)
            delay(3000)
            updateSuccessState(false)
        } catch (e: Exception) {
            updateLoadingState(false)
            // Could add error handling here
        }
    }
}

/**
 * Validates subscription form inputs
 */
private fun validateSubscriptionInputs(
    email: String,
    normalThreshold: String,
    emergencyThreshold: String
): Pair<Boolean, ValidationErrors> {
    val errors = ValidationErrors()
    var valid = true

    // Validate email
    val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
    if (email.isBlank() || !email.matches(emailPattern.toRegex())) {
        errors.emailError = "请输入有效的邮箱地址"
        valid = false
    }

    // Validate normal threshold
    if (normalThreshold.isBlank()) {
        errors.normalThresholdError = "请输入普通提醒阈值"
        valid = false
    } else if (normalThreshold.toIntOrNull() == null || normalThreshold.toInt() < 0) {
        errors.normalThresholdError = "请输入有效的小时数"
        valid = false
    }

    // Validate emergency threshold
    if (emergencyThreshold.isBlank()) {
        errors.emergencyThresholdError = "请输入紧急提醒阈值"
        valid = false
    } else if (emergencyThreshold.toIntOrNull() == null || emergencyThreshold.toInt() < 0) {
        errors.emergencyThresholdError = "请输入有效的小时数"
        valid = false
    }

    // Validate thresholds relationship
    if (valid && normalThreshold.toInt() <= emergencyThreshold.toInt()) {
        errors.normalThresholdError = "普通提醒阈值应大于紧急提醒阈值"
        valid = false
    }

    return Pair(valid, errors)
}

data class ValidationErrors(
    var emailError: String? = null,
    var normalThresholdError: String? = null,
    var emergencyThresholdError: String? = null
)

@Composable
fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .size(200.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SuccessMessageOverlay(message: String, isEmail: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .shadow(4.dp, RoundedCornerShape(24.dp))
                .animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 6.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isEmail) Icons.Filled.MarkEmailRead else Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = message,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun EmailSubscriptionDialog(
    email: String,
    onEmailChange: (String) -> Unit,
    emailError: String?,
    normalThreshold: String,
    onNormalThresholdChange: (String) -> Unit,
    normalThresholdError: String?,
    emergencyThreshold: String,
    onEmergencyThresholdChange: (String) -> Unit,
    emergencyThresholdError: String?,
    isSubscribing: Boolean,
    onOpenUrlOfWebSubscription: () -> Unit,
    onOpenUrlOfSetting: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "订阅作业提醒",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "请填写以下信息以订阅邮件通知",
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Email input field
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("邮箱地址") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    singleLine = true,
                    isError = emailError != null,
                    supportingText = { emailError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Email,
                            contentDescription = "邮箱图标"
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                // Normal threshold input field
                OutlinedTextField(
                    value = normalThreshold,
                    onValueChange = onNormalThresholdChange,
                    label = { Text("普通提醒阈值 (小时)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    singleLine = true,
                    isError = normalThresholdError != null,
                    supportingText = {
                        normalThresholdError?.let { Text(it) }
                            ?: Text("作业截止前多少小时发送普通提醒")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = "阈值图标"
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                // Emergency threshold input field
                OutlinedTextField(
                    value = emergencyThreshold,
                    onValueChange = onEmergencyThresholdChange,
                    label = { Text("紧急提醒阈值 (小时)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    singleLine = true,
                    isError = emergencyThresholdError != null,
                    supportingText = {
                        emergencyThresholdError?.let { Text(it) }
                            ?: Text("作业截止前多少小时发送紧急提醒")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = "阈值图标"
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onOpenUrlOfWebSubscription,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("前往网页订阅")
                    }
                    TextButton(
                        onClick = onOpenUrlOfSetting,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("查看个人订阅设置")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSubscribing,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            ) {
                if (isSubscribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("处理中...")
                } else {
                    Text("确认订阅")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubscribing,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun FunctionCard(
    title: String,
    functionStr: String = "下载",
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 2f else 4f,
        animationSpec = tween(durationMillis = 200)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onClick
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        awaitRelease()
                        isPressed = false
                    }
                )
            },

        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            extraContent?.invoke()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Icon(
                        imageVector = if (functionStr == "订阅")
                            Icons.Filled.Email
                        else
                            Icons.Filled.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = functionStr)
                }
            }
        }
    }
}

/**
 * Improved download functions with suspend modifier for proper coroutine usage
 */
suspend fun downloadSchoolCalendar() {
    withContext(Dispatchers.IO) {
        OtherFunctionNetworkRepository.downloadSchoolCalendar()
    }
}

suspend fun downloadGradeList(isEnglish: Boolean) {
    withContext(Dispatchers.IO) {
        OtherFunctionNetworkRepository.downloadGradeList(isEnglish)
    }
}

/**
 * Improved subscription function that returns Boolean result
 */
suspend fun subscribeEmailNotice(
    email: String,
    normalThresholdHours: Int,
    emergencyThresholdHours: Int
): Boolean = withContext(Dispatchers.IO) {
    try {
        val studentAccountManager = StudentAccountManager.getInstance()
        val formBody = FormBody.Builder()
            .add("student_id", studentAccountManager.stuId)
            .add("email", email)
            .add("threshold_1", normalThresholdHours.toString())
            .add("threshold_2", emergencyThresholdHours.toString())
            .build()

        val request = Request.Builder()
            .url("https://love.nimisora.icu/homework-notify/process.php")
            .post(formBody)
            .build()

        val response = studentAccountManager.client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        responseBody.contains("success")
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
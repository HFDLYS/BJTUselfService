package team.bjtuss.bjtuselfservice.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.ModifierLocalConsumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.repository.NetworkRepository
import team.bjtuss.bjtuselfservice.repository.OtherFunctionNetworkRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OtherFunctionScreen() {
    var isEnglishGrade by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadType by remember { mutableStateOf("") }
    var showSuccessMessage by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header section
            item {
                Text(
                    text = "服务功能",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,

                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            // School Calendar Card
            item {
                FunctionCard(
                    title = "校历下载",
                    description = "查看并下载学校最新的校历信息",
                    icon = Icons.Filled.School,
                    onClick = {
                        isDownloading = true
                        downloadType = "校历"
                        coroutineScope.launch {
                            downloadSchoolCalendar()
                            isDownloading = false
                            showSuccessMessage = true
                            kotlinx.coroutines.delay(3000)
                            showSuccessMessage = false
                        }
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
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = "Language",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "英文版",
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
                        isDownloading = true
                        downloadType = if (isEnglishGrade) "英文成绩单" else "中文成绩单"
                        coroutineScope.launch {
                            downloadGradeList(isEnglishGrade)
                            isDownloading = false
                            showSuccessMessage = true
                            kotlinx.coroutines.delay(3000)
                            showSuccessMessage = false
                        }
                    }
                )
            }

            // More function cards can be added here...
        }

        // Loading overlay
        AnimatedVisibility(
            visible = isDownloading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .size(200.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在下载$downloadType...",
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }

        // Success message
        AnimatedVisibility(
            visible = showSuccessMessage,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                        .shadow(4.dp, RoundedCornerShape(24.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,

                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "$downloadType 下载成功！",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FunctionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(true) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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

                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        fontSize = 14.sp,

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
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "下载")
                }
            }
        }
    }
}

fun downloadSchoolCalendar() {
    CoroutineScope(Dispatchers.IO).launch {
        OtherFunctionNetworkRepository.downloadSchoolCalendar()
    }
}

fun downloadGradeList(isEnglish: Boolean) {
    CoroutineScope(Dispatchers.IO).launch {
        OtherFunctionNetworkRepository.downloadGradeList(isEnglish)
    }
}
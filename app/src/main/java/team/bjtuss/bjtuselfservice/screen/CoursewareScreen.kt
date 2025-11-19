package team.bjtuss.bjtuselfservice.screen

import android.R.attr.path
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.component.RotatingImageLoader
import team.bjtuss.bjtuselfservice.jsonclass.Course
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareDownloadPostRequestResponse
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareNode
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import team.bjtuss.bjtuselfservice.statemanager.AppStateManager
import team.bjtuss.bjtuselfservice.utils.DownloadUtil
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursewareScreen(mainViewModel: MainViewModel) {
    val coursewareViewModel = mainViewModel.coursewareViewModel
    val coursewareRootNodeList by coursewareViewModel.coursewareRootNodeList.collectAsState()
    val isLoading = coursewareRootNodeList.isEmpty()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Collect download progress from DownloadUtil
    val downloadProgressMap by DownloadUtil.downloadProgress.collectAsState()

    // 控制下载对话框显示的状态
    var showDownloadDialog by remember { mutableStateOf(false) }

    // 监听下载状态变化，自动显示对话框
//    LaunchedEffect(downloadProgressMap) {
//        if (downloadProgressMap.isNotEmpty()) {
//            showDownloadDialog = true
//        }
//    }

    Scaffold(topBar = {
        TopAppBar(title = {
            Text(
                "课程资源库", style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        })
    }) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    LoadingScreen()
                } else {
                    CoursewareTreeView(
                        coursewareRootNodeList = coursewareRootNodeList,
                        scrollState = scrollState
                    )

                    // 快速滚动到顶部的按钮
                    AnimatedVisibility(
                        visible = scrollState.value > 300,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        FloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    scrollState.animateScrollTo(0)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            elevation = FloatingActionButtonDefaults.elevation(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "滚动到顶部",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // 下载进度浮动指示器按钮 - 当有下载但对话框被关闭时显示
                    AnimatedVisibility(
                        visible = downloadProgressMap.isNotEmpty() && !showDownloadDialog,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        // 计算活跃下载数量
                        val activeDownloads = downloadProgressMap.count {
                            it.value.status == DownloadUtil.Status.DOWNLOADING ||
                                    it.value.status == DownloadUtil.Status.PENDING
                        }
                        val completedDownloads = downloadProgressMap.count {
                            it.value.status == DownloadUtil.Status.COMPLETED
                        }
                        val failedDownloads = downloadProgressMap.count {
                            it.value.status == DownloadUtil.Status.FAILED
                        }

                        FloatingActionButton(
                            onClick = { showDownloadDialog = true },
                            containerColor = when {
                                failedDownloads > 0 -> MaterialTheme.colorScheme.error
                                activeDownloads > 0 -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = "下载进度",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$activeDownloads 下载中 · $completedDownloads 完成 · $failedDownloads 失败",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Show download progress dialog if there are active downloads and dialog is set to show
                    if (downloadProgressMap.isNotEmpty() && showDownloadDialog) {
                        DownloadProgressDialog(
                            downloadStatusMap = downloadProgressMap,
                            onDismissRequest = { showDownloadDialog = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadProgressDialog(
    downloadStatusMap: Map<String, DownloadUtil.DownloadStatus>,
    onDismissRequest: () -> Unit
) {

    // 对话框可以随时关闭，不再自动关闭
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text("下载进度")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp) // 限制最大高度
            ) {
                val totalDownloads = downloadStatusMap.size
                val completedDownloads =
                    downloadStatusMap.count { it.value.status == DownloadUtil.Status.COMPLETED }
                val failedDownloads =
                    downloadStatusMap.count { it.value.status == DownloadUtil.Status.FAILED }

                // 下载项列表（可滚动区域）
                Box(
                    modifier = Modifier
                        .weight(1f) // 使用weight确保剩余空间给总结文本
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        downloadStatusMap.entries
                            .sortedWith(
                                compareBy<Map.Entry<String, DownloadUtil.DownloadStatus>> {
                                    when (it.value.status) {
                                        DownloadUtil.Status.DOWNLOADING -> 0
                                        DownloadUtil.Status.PENDING -> 1
                                        DownloadUtil.Status.FAILED -> 2
                                        DownloadUtil.Status.COMPLETED -> 3
                                    }
                                }.thenBy { it.value.filename }
                            )
                            .forEach { (_, status) ->
                                DownloadProgressItem(status)
                                if (downloadStatusMap.size > 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                    )
                                }
                            }
                    }
                }

                // 总结文本（固定在底部）
                Text(
                    text = "总计: $completedDownloads/$totalDownloads 完成, $failedDownloads 失败",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("关闭")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    DownloadUtil.clearAllCompletedDownloads()
                    onDismissRequest()
                }
            ) {
                Text(
                    "清除已完成/失败",
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
        }
    )
}

@Composable
fun DownloadProgressItem(status: DownloadUtil.DownloadStatus) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        // File name and path
        Text(
            text = status.filename,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (status.path.isNotEmpty()) {
            Text(
                text = "路径: Download/${status.path}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 显示错误信息（如果有）
        if (status.status == DownloadUtil.Status.FAILED && status.errorMessage.isNotEmpty()) {
            Text(
                text = "错误: ${status.errorMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Progress indicator
        LinearProgressIndicator(
            progress = { status.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = when (status.status) {
                DownloadUtil.Status.COMPLETED -> MaterialTheme.colorScheme.primary
                DownloadUtil.Status.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Status text and percentage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (status.status) {
                    DownloadUtil.Status.PENDING -> "准备中..."
                    DownloadUtil.Status.DOWNLOADING -> "下载中..."
                    DownloadUtil.Status.COMPLETED -> "下载完成"
                    DownloadUtil.Status.FAILED -> "下载失败"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (status.status) {
                    DownloadUtil.Status.COMPLETED -> MaterialTheme.colorScheme.primary
                    DownloadUtil.Status.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                }
            )

            Text(
                text = "${(status.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Simplified gradient to a solid background
            .background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RotatingImageLoader(
                image = painterResource(id = R.drawable.loading_icon),
                rotationDuration = 1000,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "正在加载课程资源\n第一次加载耗时较长，请耐心等待",
                modifier = Modifier

                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp  // 增加行高提升可读性
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),  // 降低对比度避免刺眼
                textAlign = TextAlign.Center,  // 居中文本
                maxLines = 2,  // 明确限制行数
                overflow = TextOverflow.Ellipsis  // 防止意外溢出
            )

        }
    }
}

@Composable
fun CoursewareTreeView(
    coursewareRootNodeList: List<CoursewareNode>,
    scrollState: ScrollState
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        coursewareRootNodeList.forEach {
            CoursewareTreeNode(node = it, path = "交大自由行下载目录")
        }
        Spacer(modifier = Modifier.height(72.dp))
    }

}


@Composable
fun CoursewareTreeNode(
    node: CoursewareNode,
    level: Int = 0,
    path: String = "",
) {
    var expanded by remember { mutableStateOf(level == -1) }
    val hasChildren = node.children.isNotEmpty()
    val appState by AppStateManager.appState.collectAsState()

    // 确定当前节点是否可点击
    val isClickable = hasChildren || (node.res != null && appState.canDownloadCourseware())

    // 扩展/折叠图标的旋转动画
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "Rotation"
    )

    // 卡片抬升动画效果 - 增强层次感
    val elevation by animateDpAsState(
        targetValue = when {
            expanded -> 4.dp
            isClickable -> 2.dp
            else -> 1.dp
        },
        animationSpec = tween(durationMillis = 200),
        label = "Elevation"
    )

    // 颜色方案优化
    val colorScheme = MaterialTheme.colorScheme

    // 背景颜色 - 优化层次感和视觉区分
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !isClickable -> colorScheme.surfaceVariant
            expanded -> colorScheme.primaryContainer
            else -> colorScheme.surface
        },
        animationSpec = tween(durationMillis = 300),
        label = "BackgroundColor"
    )

    // 边框颜色 - 增加视觉层次
    val borderColor by animateColorAsState(
        targetValue = when {
            expanded -> colorScheme.primary.copy(alpha = 0.4f)
            isClickable -> colorScheme.outline.copy(alpha = 0.2f)
            else -> colorScheme.outline.copy(alpha = 0.1f)
        },
        animationSpec = tween(durationMillis = 300),
        label = "BorderColor"
    )

    // 图标颜色 - 统一颜色体系
    val iconColor by animateColorAsState(
        targetValue = when {
            !isClickable -> colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            expanded -> colorScheme.primary
            else -> colorScheme.primary.copy(alpha = 0.8f)
        },
        animationSpec = tween(durationMillis = 300),
        label = "IconColor"
    )

    // 文本颜色 - 增强可读性和层次感
    val textColor by animateColorAsState(
        targetValue = when {
            !isClickable -> colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            expanded -> colorScheme.onPrimaryContainer
            else -> colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 300),
        label = "TextColor"
    )

    val nodeText = when {
        level == 0 -> node.course.name
        node.bag != null -> node.bag?.bag_name ?: "未命名文件夹"
        node.res != null -> node.res?.rpName ?: "未命名资源"
        else -> "未知项目"
    }

    Column(
        modifier = Modifier.padding(start = (level * 16).dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)

                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor,
                contentColor = backgroundColor
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = elevation,
                pressedElevation = elevation + 2.dp,
                // 增加焦点和悬停状态的阴影以增强交互感
                focusedElevation = elevation + 1.dp,
                hoveredElevation = elevation + 1.dp
            )
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = isClickable,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(
                            bounded = true,
                            color = colorScheme.primary.copy(alpha = 0.1f)
                        ),
                        onClick = {
                            if (hasChildren) {
                                expanded = !expanded
                            } else if (node.res != null && appState.canDownloadCourseware()) {
                                downloadCourseWareWithOKHttp(node = node, path = path) {}
                            }
                        }
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasChildren) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expanded) "折叠" else "展开",
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotationState),
                            tint = iconColor
                        )
                    }
                }

                // 节点图标区域
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when {
                        level == 0 -> Icons.Default.Book
                        hasChildren -> Icons.Default.Folder
                        node.res != null -> Icons.Default.Description
                        else -> Icons.Default.Folder
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = iconColor
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 节点文本


                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = nodeText,
                        style = when (level) {
                            0 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            1 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            else -> MaterialTheme.typography.bodyMedium
                        },
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 顶级节点副标题
                    if (level == 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${node.children.size} 个项目",
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                }


                // 按钮 1：下载课件（递归下载文件夹）
                if (hasChildren) {
                    FilledTonalIconButton(
                        onClick = {
                            downloadCourseWareWithOKHttpRecursion(
                                node = node,
                                path = "$path/${nodeText}",
                                level
                            )
                        },
                        enabled = appState.canDownloadCourseware(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                                alpha = 0.38f
                            ),
                            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                alpha = 0.38f
                            )
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "下载课件",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }


                if (hasChildren && level == 0) {
                    FilledTonalIconButton(
                        onClick = {
                            downloadTeachingCalendarWithOKHttp(node.course)
                        },
                        enabled = appState.canDownloadCourseware(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(
                                alpha = 0.38f
                            ),
                            disabledContentColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                alpha = 0.38f
                            )
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth, // 使用日历图标区分
                            contentDescription = "下载教学日历",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // 子节点展开动画
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(durationMillis = 300)
        ),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(durationMillis = 200)
        )
    ) {
        Column(
            modifier = Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            node.children.forEach { childNode ->
                CoursewareTreeNode(
                    node = childNode,
                    level = level + 1,
                    path = "$path/${nodeText}"
                )
            }
        }
    }

    // 顶级项目后添加分隔线
    if (level == 0) {
        Spacer(modifier = Modifier.height(8.dp))
        if (expanded) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = colorScheme.primary.copy(alpha = 0.1f)
            )
        }
    }
}


private fun downloadCourseWareWithOKHttpRecursion(node: CoursewareNode, path: String, level: Int) {
    if (node.children.isNotEmpty()) {
        node.children.forEach { childNode ->
            downloadCourseWareWithOKHttpRecursion(childNode, "$path", level + 1)
        }
    } else if (node.res != null) {
        downloadCourseWareWithOKHttp(node = node, path = path) {}
    }
}

private fun downloadTeachingCalendarWithOKHttp(
    course: Course
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {

            val url = SmartCurriculumPlatformRepository.getTeachingCalendarUrl(course)
            url?.let { url ->
                // Use the enhanced download utility with progress tracking
                DownloadUtil.downloadFileWithOkHttp(
                    url = url,
                    filename = "${course.name}_教学日历.pdf",
                    relativePath = "交大自由行下载目录/${course.name}"
                )
            }
        } catch (e: Exception) {
            Log.e("CoursewareDownload", "Download failed", e)
        } finally {
            withContext(Dispatchers.Main) {
            }
        }
    }
}


private fun downloadCourseWareWithOKHttp(
    node: CoursewareNode,
    path: String,
    onComplete: () -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url =
                "http://123.121.147.7:88/ve/back/resourceSpace.shtml?" + "method=rpinfoDownloadUrl" + "&rpId=${node.res?.rpId}"

            val request = Request.Builder().url(url).post(FormBody.Builder().build())
                .addHeader("Cookie", "your_session_cookie") // From login session
                .addHeader("User-Agent", "Your-App-Name/1.0").build()

            val adapter = SmartCurriculumPlatformRepository.moshi.adapter(
                CoursewareDownloadPostRequestResponse::class.java
            )

            val response =
                SmartCurriculumPlatformRepository.client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseContent = response.use {
                    it.body?.string()?.let {
                        adapter.fromJson(it)
                    }
                }

                responseContent?.let { content ->
                    val headRequest = Request.Builder().url(content.rpUrl).build()

                    val headResponse =
                        SmartCurriculumPlatformRepository.client.newCall(headRequest)
                            .execute()

                    val contentDisposition = headResponse.header("Content-Disposition")
                    val fileName = contentDisposition?.let {
                        it.split(";")[1].trim()
                    }?.split("=", limit = 2)?.last()
                    val prefix = fileName?.substringBeforeLast(".")
                    val postfix = fileName?.split(".")?.last() ?: "pdf"

                    val cleanFileName = "${URLDecoder.decode(prefix, "UTF-8")}.$postfix"

                    // Use the enhanced download utility with progress tracking
                    DownloadUtil.downloadFileWithOkHttp(
                        url = content.rpUrl,
                        filename = cleanFileName,
                        relativePath = path
                    )
                }
            } else {
                Log.e("CoursewareDownload", "Request failed: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("CoursewareDownload", "Download failed", e)
        } finally {
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}

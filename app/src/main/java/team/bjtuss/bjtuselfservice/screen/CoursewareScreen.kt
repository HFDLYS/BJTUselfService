package team.bjtuss.bjtuselfservice.screen

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.component.RotatingImageLoader
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareDownloadPostRequestResponse
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareNode
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import team.bjtuss.bjtuselfservice.statemanager.AppState
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
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(title = {
            Text(
                "课程资源库", style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
            // Removed top bar colors to use default theme colors
        )
    }) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isLoading) {
                LoadingScreen()
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    CoursewareTreeView(
                        coursewareRootNodeList = coursewareRootNodeList,
//                        scrollState = scrollState
                    )

                    // 快速滚动到顶部的按钮
                    AnimatedVisibility(
                        visible = scrollState.firstVisibleItemIndex > 3,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        FloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    scrollState.animateScrollToItem(0)
                                }
                            },
                            // Using a simple primary color for consistency
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
                }
            }
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
                "正在加载课程资源...", style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ), color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun CoursewareTreeView(
    coursewareRootNodeList: List<CoursewareNode>,
//    scrollState: LazyListState = rememberLazyListState()
) {
    val scrollState = rememberScrollState()
//    LazyColumn(
//        state = scrollState,
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(horizontal = 16.dp, vertical = 12.dp),
//        verticalArrangement = Arrangement.spacedBy(12.dp)
//    ) {
//        items(coursewareRootNodeList) { rootNode ->
//            CoursewareTreeNode(node = rootNode)
//        }
//
//        // 底部添加间距
//        item {
//            Spacer(modifier = Modifier.height(72.dp))
//        }
//    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        coursewareRootNodeList.forEach {
            CoursewareTreeNode(node = it)
        }
        Spacer(modifier = Modifier.height(72.dp))
    }

}

@Composable
fun CoursewareTreeNode(
    node: CoursewareNode, level: Int = 0
) {
    var expanded by remember { mutableStateOf(level == -1) } // 默认展开顶级节点
    var downloadProgress by remember { mutableStateOf(0f) }
    var showDownloadSnackbar by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val hasChildren = node.children.isNotEmpty()

    // 扩展/折叠图标的旋转动画
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "Rotation"
    )

    // 卡片抬升动画效果 - 简化高度变化
    val elevation by animateDpAsState(
        targetValue = if (expanded) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 200),
        label = "Elevation"
    )

    // 简化背景颜色 - 只区分展开与否，不基于层级
    val backgroundColor by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 300),
        label = "BackgroundColor"
    )


    val appState by AppStateManager.appState.collectAsState()

    Column(
        modifier = Modifier.padding(start = (level * 16).dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .shadow(elevation = elevation, shape = RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor,
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 3.dp, pressedElevation = 6.dp
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = appState == AppState.LoggedIn || hasChildren) {
                        if (hasChildren) {
                            expanded = !expanded
                        } else if (node.res != null) {
//                            showDownloadSnackbar = true

                            downloadResource(node) {
//                                isDownloading = false
//                                downloadProgress = 0f
                            }

                        }
                    }
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (hasChildren) {
                        // 使用统一的图标颜色
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
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // 节点图标区域 - 使用统一的图标颜色方案
                    Box(
                        modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center
                    ) {
                        val icon = when {
                            level == 0 -> Icons.Default.Book
                            hasChildren -> Icons.Default.Folder
                            node.res != null -> Icons.Default.Description
                            else -> Icons.Default.Folder
                        }

                        // 统一使用主色调，让界面看起来更协调
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 间隔
                    Spacer(modifier = Modifier.width(12.dp))

                    // 节点文本
                    val nodeText = when {
                        level == 0 -> node.course.name
                        node.bag != null -> node.bag?.bag_name ?: "未命名文件夹"
                        node.res != null -> node.res?.rpName ?: "未命名资源"
                        else -> "未知项目"
                    }

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
                            // 使用统一的文本颜色
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // 如果是顶级节点，添加一个副标题
                        if (level == 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${node.children.size} 个项目",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 下载指示器或下载按钮
                    if (!hasChildren && node.res != null) {
                        Box(
                            modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center
                        ) {
//                            if (isDownloading) {
//                                CircularProgressIndicator(
//                                    progress = { downloadProgress },
//                                    modifier = Modifier.size(28.dp),
//                                    strokeWidth = 2.dp,
//                                    color = MaterialTheme.colorScheme.primary
//                                )
//                            } else {


                            IconButton(
                                onClick = {
                                    showDownloadSnackbar = true
                                    downloadResource(node) {
                                    }

                                }, modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "下载",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                        }
                    }
                }

                // 显示下载进度条（仅在下载时）

            }
        }

        // 子节点展开动画
        AnimatedVisibility(
            visible = expanded, enter = expandVertically(
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ) + fadeIn(
                animationSpec = tween(durationMillis = 300)
            ), exit = shrinkVertically(
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
                        node = childNode, level = level + 1
                    )
                }
            }
        }

        // 在顶级项目后添加分隔线
        if (level == 0) {
            Spacer(modifier = Modifier.height(8.dp))
            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        }
    }

    // 处理下载通知
//    if (showDownloadSnackbar) {
//        val nodeText = when {
//            node.res != null -> node.res?.rpName ?: "资源"
//            else -> "文件"
//        }
//
//        LaunchedEffect(key1 = showDownloadSnackbar) {
//            // 使用Snackbar显示下载状态
//            val snackbarHostState = SnackbarHostState()
//            val snackbarResult = snackbarHostState.showSnackbar(
//                message = "正在下载: $nodeText",
//                actionLabel = "查看",
//                duration = SnackbarDuration.Short
//            )
//
//            if (snackbarResult == SnackbarResult.ActionPerformed) {
//                // 打开下载文件夹
//                // 可以实现查看下载内容的功能
//            }
//
//            showDownloadSnackbar = false
//        }
//    }
}

// 保持原有的下载逻辑
private fun downloadResource(node: CoursewareNode, onComplete: () -> Unit) {
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

            val response = SmartCurriculumPlatformRepository.client.newCall(request).execute()

            if (response.isSuccessful) {

                val responseContent = response.body?.string()?.let {
                    val a = it
                    println("a")

                    adapter.fromJson(it) }

                responseContent?.let { content ->
                    val headRequest = Request.Builder().url(content.rpUrl).build()

                    val headResponse =
                        SmartCurriculumPlatformRepository.client.newCall(headRequest).execute()

                    val contentDisposition = headResponse.header("Content-Disposition")
                    val fileName = contentDisposition?.let {
                        it.split(";")[1].trim()
                    }?.split("=", limit = 2)?.last()

                    val prefix = fileName?.split(".")?.first()
                    val postfix = fileName?.split(".")?.last() ?: "pdf"

                    DownloadUtil.downloadFile(
                        url = content.rpUrl,
                        title = URLDecoder.decode(prefix, "UTF-8"),
                        fileType = postfix,
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

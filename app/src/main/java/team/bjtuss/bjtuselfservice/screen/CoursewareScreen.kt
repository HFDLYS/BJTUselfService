package team.bjtuss.bjtuselfservice.screen

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import team.bjtuss.bjtuselfservice.MainApplication
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareDownloadPostRequestResponse
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareNode
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import team.bjtuss.bjtuselfservice.utils.DownloadUtil
import team.bjtuss.bjtuselfservice.utils.KotlinUtils
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import java.net.URL
import java.net.URLDecoder


@Composable
fun CoursewareScreen(mainViewModel: MainViewModel) {
    val coursewareViewModel = mainViewModel.coursewareViewModel
    val coursewareTree by coursewareViewModel.coursewareTree.collectAsState()
    val isLoading = coursewareTree.isEmpty()

    CoursewareScreenScaffold(
        isLoading = isLoading,
        content = {
            if (!isLoading) {
                CoursewareTreeView(coursewareTree = coursewareTree)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoursewareScreenScaffold(
    isLoading: Boolean,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Courseware Directory",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isLoading) {
                LoadingScreen()
            } else {
                content()
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Loading courseware...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CoursewareTreeView(coursewareTree: List<CoursewareNode>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(coursewareTree) { rootNode ->
            CoursewareTreeNode(node = rootNode)
        }
    }
}

@Composable
fun CoursewareTreeNode(
    node: CoursewareNode,
    level: Int = 0
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    val hasChildren = node.children.isNotEmpty()
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "Rotation"
    )

    Column(
        modifier = Modifier.padding(start = (level * 16).dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (level) {
                    0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    1 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (level == 0) 2.dp else 1.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (hasChildren) {
                            expanded = !expanded
                        } else {
                            if (!isDownloading) {
                                isDownloading = true
                                downloadResource(node) {
                                    isDownloading = false
                                }
                            }
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Arrow indicator for expandable items
                if (hasChildren) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotationState),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // Node icon based on type
                val (icon, tint) = when {
                    level == 0 -> Icons.Default.Book to MaterialTheme.colorScheme.primary
                    node.bag != null -> Icons.Default.Folder to MaterialTheme.colorScheme.secondary
                    node.res != null -> Icons.Default.Description to MaterialTheme.colorScheme.tertiary
                    else -> Icons.Default.Folder to MaterialTheme.colorScheme.onSurface
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = tint
                )

                // Node text
                val nodeText = when {
                    level == 0 -> node.course.name
                    node.bag != null -> node.bag?.bag_name ?: "Unnamed Bag"
                    node.res != null -> node.res?.rpName ?: "Unnamed Resource"
                    else -> "Unknown Item"
                }

                Text(
                    text = nodeText,
                    style = when (level) {
                        0 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        1 -> MaterialTheme.typography.bodyLarge
                        else -> MaterialTheme.typography.bodyMedium
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                )

                // Download indicator for leaf nodes
                if (!hasChildren && node.res != null) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Animated children expansion
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                node.children.forEach { childNode ->
                    CoursewareTreeNode(
                        node = childNode,
                        level = level + 1
                    )
                }
            }
        }

        // Add divider after top-level items
        if (level == 0) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun downloadResource(node: CoursewareNode, onComplete: () -> Unit) {
    // Keep the existing download logic, but with better structure
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = "http://123.121.147.7:88/ve/back/resourceSpace.shtml?" +
                    "method=rpinfoDownloadUrl" +
                    "&rpId=${node.res?.rpId}"

            val request = Request.Builder()
                .url(url)
                .post(FormBody.Builder().build())
                .addHeader("Cookie", "your_session_cookie") // From login session
                .addHeader("User-Agent", "Your-App-Name/1.0")
                .build()

            val adapter = SmartCurriculumPlatformRepository.moshi.adapter(
                CoursewareDownloadPostRequestResponse::class.java
            )

            val response = SmartCurriculumPlatformRepository.client
                .newCall(request)
                .execute()

            if (response.isSuccessful) {
                val responseContent = response.body
                    ?.string()
                    ?.let { adapter.fromJson(it) }

                responseContent?.let { content ->
                    val headRequest = Request.Builder()
                        .url(content.rpUrl)
                        .build()

                    val headResponse = SmartCurriculumPlatformRepository.client
                        .newCall(headRequest)
                        .execute()

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

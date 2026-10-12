package team.bjtuss.bjtuselfservice.shared.feature.courseware

import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalBottomBarClearance

import team.bjtuss.bjtuselfservice.shared.feature.common.WorkspaceEmptyState
import team.bjtuss.bjtuselfservice.shared.feature.common.WorkspaceLoadingState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import team.bjtuss.bjtuselfservice.shared.accessibleAlpha
import team.bjtuss.bjtuselfservice.shared.data.courseware.CoursewareOperationResult
import team.bjtuss.bjtuselfservice.shared.data.courseware.CoursewareSyncFailure
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareCourse
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.courseware.VisibleCoursewareNode
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileSaveResult
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheet
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppErrorBanner
import team.bjtuss.bjtuselfservice.shared.feature.shell.LegacySmartTransportWarning
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CoursewareWorkspace(
    state: CoursewareUiState,
    expanded: Boolean,
    usesLegacySmartTransport: Boolean = false,
    legacyWarningVisible: Boolean = false,
    onDismissLegacyWarning: () -> Unit = {},
    model: CoursewareScreenModel,
    fileGateway: HomeworkFileGateway,
    directoryGateway: CoursewareDirectoryGateway,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var showCoursePicker by remember { mutableStateOf(false) }
    var fileFeedback by remember { mutableStateOf<String?>(null) }
    var directoryJob by remember { mutableStateOf<Job?>(null) }
    var isCancellingDirectory by remember { mutableStateOf(false) }

    fun cancelDirectoryExport() {
        val job = directoryJob ?: return
        if (isCancellingDirectory) return
        isCancellingDirectory = true
        job.cancel()
        fileFeedback = "已请求取消目录导出，正在清理本次未完成目录。"
    }

    fun saveResource(node: CoursewareNode) {
        if (!fileGateway.isAvailable) {
            fileFeedback = "当前平台的系统保存面板尚未接入。"
            return
        }
        scope.launch {
            fileFeedback = null
            when (val downloaded = model.downloadResource(node.stableKey)) {
                is CoursewareOperationResult.Failure -> Unit
                is CoursewareOperationResult.Success -> {
                    fileFeedback = when (fileGateway.saveFile(downloaded.value)) {
                        HomeworkFileSaveResult.Saved -> "文件已保存。"
                        HomeworkFileSaveResult.Cancelled -> "已取消保存，没有写入文件。"
                        is HomeworkFileSaveResult.Failed -> "保存失败，请重新选择位置。"
                    }
                }
            }
        }
    }

    fun saveDirectory(stableKey: String?, directoryName: String) {
        if (directoryJob != null) {
            fileFeedback = "已有目录导出正在进行。"
            return
        }
        if (!directoryGateway.isDirectoryExportAvailable) {
            fileFeedback = "当前平台的系统目录导出面板尚未接入。"
            return
        }
        directoryJob = scope.launch {
            fileFeedback = null
            isCancellingDirectory = false
            try {
                when (val exported = model.exportDirectory(stableKey, directoryName, directoryGateway)) {
                    is CoursewareOperationResult.Failure -> Unit
                    is CoursewareOperationResult.Success -> {
                        fileFeedback = when (exported.value) {
                            HomeworkFileSaveResult.Saved -> model.state.value.directoryExportSkipped.let { skipped ->
                                if (skipped.isEmpty()) {
                                    "目录已完整导出。"
                                } else {
                                    "已导出可下载的文件；跳过 ${skipped.size} 个老师未开放下载的文件：" +
                                        skipped.take(3).joinToString("、") + if (skipped.size > 3) " 等。" else "。"
                                }
                            }
                            HomeworkFileSaveResult.Cancelled -> "已取消导出，没有写入目录。"
                            is HomeworkFileSaveResult.Failed -> "目录导出失败；没有报告为完整成功。"
                        }
                    }
                }
            } catch (_: CancellationException) {
                fileFeedback = "已取消目录导出；本次未完成目录已请求清理。"
            } finally {
                isCancellingDirectory = false
                directoryJob = null
            }
        }
    }

    LaunchedEffect(model) { model.initialize() }

    Box(
        modifier = if (expanded) {
            modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        // 同步态在 DestinationPage 顶栏；此处不再放页内「同步课件」。

        // 明文通道提示：仅在 shell/session 判定「本登录态尚未关闭」时显示一条可关闭横幅。
        if (legacyWarningVisible) {
            LegacySmartTransportWarning(onDismiss = onDismissLegacyWarning)
        }

        // 选中课程的细加载条叠在 Box 顶部，不占 Column 高度。
        if (state.directoryDownloadTotal > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { state.directoryDownloadCompleted.toFloat() / state.directoryDownloadTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isCancellingDirectory) {
                            "正在取消并清理不完整目录…"
                        } else {
                            "正在导出目录 ${state.directoryDownloadCompleted}/${state.directoryDownloadTotal}"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = ::cancelDirectoryExport,
                    ) { Text("取消") }
                }
            }
        }
        if (directoryJob != null && state.directoryDownloadTotal == 0) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isCancellingDirectory) "正在取消并清理不完整目录…" else "正在等待系统目录或写入文件…",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = ::cancelDirectoryExport,
                        enabled = !isCancellingDirectory,
                    ) { Text("取消") }
                }
            }
        }
        state.failure?.let { failure ->
            CoursewareFailureBanner(
                failure = failure,
                hasContent = state.courses.isNotEmpty(),
                onRetry = onRefresh,
                onDismiss = model::dismissFailure,
            )
        }
        state.fileFailure?.let { failure ->
            CoursewareFileFailureBanner(failure, model::dismissFileFailure)
        }
        fileFeedback?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { fileFeedback = null }) { Text("关闭") }
                }
            }
        }

        when {
            state.isLoading && state.courses.isEmpty() -> CoursewareLoadingState()
            state.courses.isEmpty() -> CoursewareEmptyState(onRefresh)
            expanded -> CoursewareExpandedWorkspace(
                state = state,
                model = model,
                fileGatewayAvailable = fileGateway.isAvailable,
                directoryGatewayAvailable = directoryGateway.isDirectoryExportAvailable,
                onDownload = ::saveResource,
                onExportDirectory = ::saveDirectory,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            else -> CoursewareCompactWorkspace(
                state = state,
                model = model,
                fileGatewayAvailable = fileGateway.isAvailable,
                directoryGatewayAvailable = directoryGateway.isDirectoryExportAvailable,
                onChooseCourse = { showCoursePicker = true },
                onDownload = ::saveResource,
                onExportDirectory = ::saveDirectory,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        }
        if (state.isSelectedCourseLoading && !state.isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
            )
        }
    }

    // 打开选课列表时补拉「数量未同步」的课程顶层目录。
    LaunchedEffect(showCoursePicker, state.courses.map { it.id to it.childrenLoaded }) {
        if (showCoursePicker && state.courses.any { !it.childrenLoaded }) {
            model.ensureCourseRootsLoaded()
        }
    }

    if (showCoursePicker) {
        // 课程列表可以很长，半屏放不下：needsFullHeight 让 iOS 卡片直接开到全屏。
        AppleSheet(
            onDismissRequest = { showCoursePicker = false },
            title = "选择课程",
            needsFullHeight = true,
            scrollableBody = true,
        ) {
            CoursewareCoursePickerList(
                courses = state.courses,
                selectedCourseId = state.selectedCourseId,
                loadingCourseIds = state.loadingCourseIds,
                onSelect = { course ->
                    scope.launch { model.selectCourse(course.id) }
                    showCoursePicker = false
                },
                modifier = Modifier.fillMaxWidth().then(
                    if (team.bjtuss.bjtuselfservice.shared.currentPlatform().family == team.bjtuss.bjtuselfservice.shared.PlatformFamily.IOS)
                        Modifier.weight(1f) else Modifier.heightIn(max = 560.dp),
                ),
            )
        }
    }
}

/** Shared list body also used by the offline native-sheet visual probe. */
@Composable
internal fun CoursewareCoursePickerList(
    courses: List<CoursewareCourse>,
    selectedCourseId: Int?,
    loadingCourseIds: Set<Int>,
    onSelect: (CoursewareCourse) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().desktopTouchScroll(listState),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalBottomBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(courses, key = CoursewareCourse::stableKey) { course ->
                CoursewareCourseRow(course, course.id == selectedCourseId, course.id in loadingCourseIds) { onSelect(course) }
            }
        }
        if (loadingCourseIds.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun CoursewareExpandedWorkspace(
    state: CoursewareUiState,
    model: CoursewareScreenModel,
    fileGatewayAvailable: Boolean,
    directoryGatewayAvailable: Boolean,
    onDownload: (CoursewareNode) -> Unit,
    onExportDirectory: (String?, String) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val courseListState = rememberLazyListState()
    val treeListState = rememberLazyListState()
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // 三栏随窗口比例伸缩：课程约 28% / 目录约 42% / 详情约 30%。
        ElevatedCard(modifier = Modifier.weight(0.28f).fillMaxHeight()) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("课程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyColumn(
                    state = courseListState,
                    modifier = Modifier.weight(1f).fillMaxWidth().desktopTouchScroll(courseListState),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(state.courses, key = CoursewareCourse::stableKey) { course ->
                        CoursewareCourseRow(
                            course = course,
                            selected = course.id == state.selectedCourseId,
                            loading = course.id in state.loadingCourseIds,
                            onClick = { scope.launch { model.selectCourse(course.id) } },
                        )
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.selectedCourse?.name.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            state.isSelectedCourseLoading -> "正在同步…"
                            state.selectedCourse?.childrenLoaded == false -> "点击课程加载"
                            else -> "${state.selectedCourse?.children?.size ?: 0} 个顶层项目"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            state.selectedCourse?.let { onExportDirectory(null, it.name) }
                        },
                        enabled = directoryGatewayAvailable && !state.isDownloading &&
                            state.selectedCourse?.childrenLoaded == true &&
                            state.selectedCourse?.children?.isNotEmpty() == true,
                    ) { Text("导出本课程") }
                }
                if (state.visibleTree.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                state.isSelectedCourseLoading -> "正在加载这门课程的课件…"
                                state.selectedCourse?.childrenLoaded == false -> "选择课程后加载课件"
                                else -> "这门课程暂无课件"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = treeListState,
                        modifier = Modifier.weight(1f).fillMaxWidth().desktopTouchScroll(treeListState),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 12.dp + LocalBottomBarClearance.current),
                    ) {
                        items(state.visibleTree, key = { it.node.stableKey }) { visible ->
                            CoursewareTreeRow(
                                visible = visible,
                                expanded = visible.node.stableKey in state.expandedFolderKeys,
                                selected = visible.node.stableKey == state.selectedNodeKey,
                                loading = visible.node.stableKey in state.loadingFolderKeys,
                                onClick = {
                                    if (visible.node.isFolder) {
                                        scope.launch { model.toggleExpanded(visible.node.stableKey) }
                                    } else {
                                        model.selectNode(visible.node.stableKey)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        CoursewareDetailPanel(
            course = state.selectedCourse,
            node = state.selectedNode,
            isDownloading = state.isDownloading,
            fileGatewayAvailable = fileGatewayAvailable,
            directoryGatewayAvailable = directoryGatewayAvailable,
            onDownload = onDownload,
            onExportDirectory = onExportDirectory,
            modifier = Modifier.weight(0.30f).fillMaxHeight(),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CoursewareCompactWorkspace(
    state: CoursewareUiState,
    model: CoursewareScreenModel,
    fileGatewayAvailable: Boolean,
    directoryGatewayAvailable: Boolean,
    onChooseCourse: () -> Unit,
    onDownload: (CoursewareNode) -> Unit,
    onExportDirectory: (String?, String) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val compactListState = rememberLazyListState()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 左：引导文案；右：选课胶囊（「点击此处选择课程」）。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "浏览课件",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (state.courses.isEmpty()) {
                        "同步后选择一门课程"
                    } else {
                        "共 ${state.courses.size} 门课，点右侧切换"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(
                onClick = onChooseCourse,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "点击此处选择课程",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 当前课程标题 + 导出本课程全部课件 + 数量。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.compactFolderPath.isNotEmpty()) {
                TextButton(onClick = { model.navigateCompactBack() }) { Text("返回") }
            }
            Text(
                compactPathTitle(state).ifBlank { "未选择课程" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.compactFolderPath.isEmpty()) {
                OutlinedButton(
                    onClick = { state.selectedCourse?.let { onExportDirectory(null, it.name) } },
                    enabled = directoryGatewayAvailable && !state.isDownloading &&
                        state.selectedCourse?.childrenLoaded == true &&
                        state.selectedCourse?.children?.isNotEmpty() == true,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "导出本课程全部课件",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                state.compactFolderPath.lastOrNull()?.let { folderKey ->
                    TextButton(
                        onClick = {
                            onExportDirectory(
                                folderKey,
                                state.compactPathNames.lastOrNull().orEmpty().ifBlank { "课件" },
                            )
                        },
                        enabled = directoryGatewayAvailable && !state.isDownloading,
                    ) { Text("导出") }
                }
            }
            Text(
                "${state.compactNodes.size} 项",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.compactNodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        state.isSelectedCourseLoading -> "正在加载这门课程的课件…"
                        state.selectedCourse?.childrenLoaded == false -> "正在准备课程目录…"
                        else -> "当前文件夹暂无课件"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = compactListState,
                modifier = Modifier.weight(1f).fillMaxWidth().desktopTouchScroll(compactListState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 18.dp + LocalBottomBarClearance.current),
            ) {
                items(state.compactNodes, key = CoursewareNode::stableKey) { node ->
                    CoursewareCompactNodeRow(
                        node = node,
                        loading = node.stableKey in state.loadingFolderKeys,
                        onClick = { scope.launch { model.openCompactNode(node.stableKey) } },
                    )
                }
            }
        }
    }

    state.selectedNode?.takeIf { !it.isFolder }?.let { node ->
        AppleSheet(
            onDismissRequest = { model.selectNode("") },
            title = "课件下载",
            // Details are a long, scrollable task. Full height keeps the
            // underlying Courseware navigation title out of the sheet chrome
            // and gives the final save button a real viewport to scroll in.
            needsFullHeight = true,
            scrollableBody = true,
        ) {
            val detailScrollState = rememberScrollState()
            CoursewareDetailSheetBody(
                course = state.selectedCourse,
                node = node,
                isDownloading = state.isDownloading,
                fileGatewayAvailable = fileGatewayAvailable,
                directoryGatewayAvailable = directoryGatewayAvailable,
                onDownload = onDownload,
                onExportDirectory = onExportDirectory,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (team.bjtuss.bjtuselfservice.shared.currentPlatform().family == team.bjtuss.bjtuselfservice.shared.PlatformFamily.IOS) Modifier.weight(1f) else Modifier.heightIn(max = 560.dp))
                    .verticalScroll(detailScrollState)
                    .desktopTouchScroll(detailScrollState)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
            )
        }
    }
}

@Composable
private fun CoursewareCourseRow(
    course: CoursewareCourse,
    selected: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(course.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    loading -> "正在加载…"
                    !course.childrenLoaded -> "数量未同步"
                    else -> "${course.children.size} 个顶层项目"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.accessibleAlpha(0.72f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun CoursewareTreeRow(
    visible: VisibleCoursewareNode,
    expanded: Boolean,
    selected: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val node = visible.node
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(start = (visible.depth * 18).dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (node.isFolder) if (expanded) "▾" else "▸" else "•", modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!node.isFolder) {
                    Text(
                        resourceMetadata(node),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (node.isFolder) {
                Text(
                    when {
                        loading -> "加载中"
                        !node.childrenLoaded -> "未展开"
                        else -> "${node.children.size}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun CoursewareCompactNodeRow(node: CoursewareNode, loading: Boolean, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (node.isFolder) {
                    "文件夹"
                } else {
                    formatCoursewareTypeBadge(node.extension)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(52.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(node.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (node.isFolder) {
                        when {
                            loading -> "正在加载目录…"
                            !node.childrenLoaded -> "点击加载目录"
                            else -> "${node.children.size} 个项目"
                        }
                    } else {
                        resourceMetadata(node)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CoursewareDetailPanel(
    course: CoursewareCourse?,
    node: CoursewareNode?,
    isDownloading: Boolean,
    fileGatewayAvailable: Boolean,
    directoryGatewayAvailable: Boolean,
    onDownload: (CoursewareNode) -> Unit,
    onExportDirectory: (String?, String) -> Unit,
    modifier: Modifier,
) {
    ElevatedCard(modifier = modifier) {
        if (node == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "选择文件或文件夹以查看详情",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            CoursewareDetailContent(
                course = course,
                node = node,
                isDownloading = isDownloading,
                fileGatewayAvailable = fileGatewayAvailable,
                directoryGatewayAvailable = directoryGatewayAvailable,
                onDownload = onDownload,
                onExportDirectory = onExportDirectory,
                contentPadding = PaddingValues(20.dp),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CoursewareDetailContent(
    course: CoursewareCourse?,
    node: CoursewareNode,
    isDownloading: Boolean,
    fileGatewayAvailable: Boolean,
    directoryGatewayAvailable: Boolean,
    onDownload: (CoursewareNode) -> Unit,
    onExportDirectory: (String?, String) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val detailScrollState = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(detailScrollState)
            .desktopTouchScroll(detailScrollState)
            .padding(contentPadding),
    ) {
        CoursewareDetailSheetBody(
            course = course,
            node = node,
            isDownloading = isDownloading,
            fileGatewayAvailable = fileGatewayAvailable,
            directoryGatewayAvailable = directoryGatewayAvailable,
            onDownload = onDownload,
            onExportDirectory = onExportDirectory,
        )
    }
}

@Composable
private fun CoursewareDetailSheetBody(
    course: CoursewareCourse?,
    node: CoursewareNode,
    isDownloading: Boolean,
    fileGatewayAvailable: Boolean,
    directoryGatewayAvailable: Boolean,
    onDownload: (CoursewareNode) -> Unit,
    onExportDirectory: (String?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (node.isFolder) "文件夹" else "课件文件",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(node.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        course?.let { DetailLine("课程", it.name) }
        if (node.isFolder) {
            DetailLine("包含", "${node.children.size} 个直接子项目")
            node.restrictedResourceCount().takeIf { it > 0 }?.let { count ->
                DetailLine("未开放下载", "$count 个文件（导出时跳过）")
            }
            Button(
                onClick = { onExportDirectory(node.stableKey, node.name) },
                enabled = directoryGatewayAvailable && !isDownloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isDownloading) "正在准备目录" else "导出此文件夹")
            }
            Text(
                if (directoryGatewayAvailable) {
                    "导出时会保留这个文件夹下的子目录层级。"
                } else {
                    "当前平台尚未提供系统目录导出面板。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (directoryGatewayAvailable) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        } else {
            node.size.takeIf { it.isNotBlank() }?.let { DetailLine("大小", formatCoursewareSize(it)) }
            DetailLine("类型", formatCoursewareTypeLabel(node.extension))
            node.teacherName.takeIf { it.isNotBlank() }?.let { DetailLine("上传教师", it) }
            node.inputTime.takeIf { it.isNotBlank() }?.let { DetailLine("上传时间", it) }
            DetailLine("下载次数", node.downloadCount.toString())
            if (!node.downloadAllowed) {
                DetailLine("下载权限", "老师未开放下载，只能在智慧教学网页中在线查看")
            }
            Button(
                onClick = { onDownload(node) },
                enabled = fileGatewayAvailable && !isDownloading && node.downloadAllowed,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        !node.downloadAllowed -> "未开放下载"
                        isDownloading -> "正在读取文件"
                        else -> "下载并选择保存位置"
                    },
                )
            }
            if (!fileGatewayAvailable) {
                Text(
                    "当前平台尚未提供系统保存面板。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CoursewareFailureBanner(
    failure: CoursewareSyncFailure,
    hasContent: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppErrorBanner(
        message = failureMessage(failure, hasContent),
        onRetry = if (failure != CoursewareSyncFailure.CACHE) onRetry else null,
        onDismiss = onDismiss,
    )
}

@Composable
private fun CoursewareFileFailureBanner(failure: CoursewareSyncFailure, onDismiss: () -> Unit) {
    AppErrorBanner(
        message = when (failure) {
            CoursewareSyncFailure.NETWORK -> "文件下载失败，请检查网络后重试。"
            CoursewareSyncFailure.SESSION_EXPIRED -> "登录会话已失效，请点击右上角刷新重试登录。"
            CoursewareSyncFailure.MALFORMED_RESPONSE -> "学校平台返回了无效的文件信息。"
            CoursewareSyncFailure.SECURE_CHANNEL_UNAVAILABLE -> "该资源地址不在允许的学校通道范围内。"
            CoursewareSyncFailure.CACHE -> "本地课件缓存不可用。"
            CoursewareSyncFailure.DOWNLOAD_NOT_ALLOWED -> "老师未开放下载，只能在智慧教学网页中在线查看。"
        },
        onDismiss = onDismiss,
    )
}

private fun failureMessage(failure: CoursewareSyncFailure, hasContent: Boolean): String = when (failure) {
    CoursewareSyncFailure.NETWORK -> if (hasContent) {
        "同步失败，正在显示本地课件缓存。"
    } else {
        "无法连接智慧教学平台，请检查网络后重试。"
    }
    CoursewareSyncFailure.SESSION_EXPIRED -> "智慧教学平台会话已失效，请点击右上角刷新重试登录。"
    CoursewareSyncFailure.MALFORMED_RESPONSE -> "学校课件数据结构已变化，暂时无法解析。"
    // 已授权明文后勿再写「没有 HTTPS」，与顶部授权 banner 矛盾。
    CoursewareSyncFailure.SECURE_CHANNEL_UNAVAILABLE -> "该资源地址不在允许的学校通道范围内。"
    CoursewareSyncFailure.CACHE -> "本地课件缓存操作失败。"
    CoursewareSyncFailure.DOWNLOAD_NOT_ALLOWED -> "老师未开放下载，只能在智慧教学网页中在线查看。"
}

@Composable
private fun CoursewareLoadingState() = WorkspaceLoadingState("正在读取本地课件并连接智慧教学平台…")

@Composable
private fun CoursewareEmptyState(onRefresh: () -> Unit) = WorkspaceEmptyState(
    title = "暂无课件",
    description = "本地没有缓存，智慧教学平台也没有返回可显示的课程资源。",
    onRefresh = onRefresh,
)

private fun compactPathTitle(state: CoursewareUiState): String = buildString {
    append(state.selectedCourse?.name.orEmpty())
    state.compactPathNames.forEach { name -> append(" / ").append(name) }
}

/** 只统计已加载的子树；未展开的子文件夹在导出前才会加载。 */
private fun CoursewareNode.restrictedResourceCount(): Int =
    children.sumOf { if (it.isFolder) it.restrictedResourceCount() else if (it.downloadAllowed) 0 else 1 }

private fun resourceMetadata(node: CoursewareNode): String = listOfNotNull(
    node.extension.takeIf { it.isNotBlank() }?.let(::formatCoursewareTypeBadge),
    node.size.takeIf { it.isNotBlank() }?.let(::formatCoursewareSize),
    node.teacherName.takeIf { it.isNotBlank() },
    "未开放下载".takeIf { !node.downloadAllowed },
).joinToString(" · ").ifBlank { "课程资源" }

/**
 * 智慧教学 `rpSize` 多为裸数字（如 `5.34`），单位按教务惯例视为 MB。
 * 若已带 B/KB/MB/GB 则原样展示。
 */
internal fun formatCoursewareSize(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    if (trimmed.contains(Regex("""(?i)(KB|MB|GB|TB|字节|\bB\b)"""))) return trimmed
    // 纯数字 / 小数
    if (trimmed.matches(Regex("""\d+(\.\d+)?"""))) return "$trimmed MB"
    return trimmed
}

/** 列表左侧短徽章：RAR / PDF / PPTX。 */
internal fun formatCoursewareTypeBadge(extension: String): String {
    val ext = extension.trim().uppercase()
    return if (ext.isBlank()) "文件" else ext
}

/** 详情「类型」长文案：RAR 压缩文件、PPTX 演示文件 等。 */
internal fun formatCoursewareTypeLabel(extension: String): String {
    val ext = extension.trim().uppercase()
    if (ext.isBlank()) return "未知类型"
    return when (ext) {
        "RAR" -> "RAR 压缩文件"
        "ZIP" -> "ZIP 压缩文件"
        "7Z" -> "7Z 压缩文件"
        "TAR", "GZ", "TGZ" -> "$ext 压缩文件"
        "PPT" -> "PPT 演示文件"
        "PPTX" -> "PPTX 演示文件"
        "PPS", "PPSX" -> "$ext 演示文件"
        "PDF" -> "PDF 文档"
        "DOC" -> "DOC 文档"
        "DOCX" -> "DOCX 文档"
        "RTF", "TXT", "MD" -> "$ext 文本"
        "XLS" -> "XLS 表格"
        "XLSX" -> "XLSX 表格"
        "CSV" -> "CSV 表格"
        "MP4", "MOV", "AVI", "MKV", "WMV" -> "$ext 视频"
        "MP3", "WAV", "AAC", "M4A", "FLAC" -> "$ext 音频"
        "PNG", "JPG", "JPEG", "GIF", "WEBP", "BMP", "HEIC" -> "$ext 图片"
        "DWG", "DXF" -> "$ext 图纸"
        "IPYNB" -> "Jupyter 笔记本"
        "PY", "JAVA", "C", "CPP", "H", "JS", "TS", "KT" -> "$ext 源码"
        else -> "$ext 文件"
    }
}

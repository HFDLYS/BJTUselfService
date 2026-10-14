package team.bjtuss.bjtuselfservice.shared.feature.homework

import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalBottomBarClearance

import team.bjtuss.bjtuselfservice.shared.feature.common.WorkspaceEmptyState
import team.bjtuss.bjtuselfservice.shared.feature.common.WorkspaceLoadingState

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.shared.accessibleAlpha
import team.bjtuss.bjtuselfservice.shared.util.schoolRichTextToPlainMultiline
import team.bjtuss.bjtuselfservice.shared.data.homework.HomeworkSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.homework.HomeworkOperationResult
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkAttachment
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkDetail
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkSortOrder
import team.bjtuss.bjtuselfservice.shared.domain.homework.SubmittedHomeworkAttachment
import team.bjtuss.bjtuselfservice.shared.domain.homework.stableKey
import team.bjtuss.bjtuselfservice.shared.domain.homework.typeLabel
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFilePickResult
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileSaveResult
import team.bjtuss.bjtuselfservice.shared.files.safeExportFileName
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheet
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheetOrAlert
import team.bjtuss.bjtuselfservice.shared.feature.shell.sheetScrollContentPadding
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppErrorBanner
import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalTopBarClearance
import team.bjtuss.bjtuselfservice.shared.feature.shell.TopScrollLazyColumn
import team.bjtuss.bjtuselfservice.shared.feature.shell.TopScrollColumn
import team.bjtuss.bjtuselfservice.shared.feature.shell.LegacySmartTransportWarning
import team.bjtuss.bjtuselfservice.shared.feature.shell.SessionRefreshCoordinator

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeworkWorkspace(
    state: HomeworkUiState,
    expanded: Boolean,
    usesLegacySmartTransport: Boolean = false,
    legacyWarningVisible: Boolean = false,
    onDismissLegacyWarning: () -> Unit = {},
    model: HomeworkScreenModel,
    fileGateway: HomeworkFileGateway,
    onReauthenticate: (suspend () -> Boolean)? = null,
    onRefresh: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    // 附件下载/上传状态与详情二级页共用同一套实现，见 HomeworkTransferState。
    val transfer = rememberHomeworkTransferState(model, fileGateway, onReauthenticate)
    var showFilterSheet by remember { mutableStateOf(false) }

    // 只灌缓存；网络自动同步由 shell 在登录成功后触发。
    LaunchedEffect(model) { model.initialize(refreshFromNetwork = false) }

    // 原生栏 underlap 时外层不再做布局占位：视口顶边贴屏幕顶，首项靠列表内部顶边距让开；
    // 否则内容止于栏底、栏后只剩纯色（之前无模糊的根因）。非 underlap 路径保持 8.dp 不变。
    val topClearance = LocalTopBarClearance.current
    val compactTopPad = if (topClearance > 0.dp) 0.dp else 8.dp
    Column(
        modifier = if (expanded) {
            modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        } else {
            modifier.padding(horizontal = 16.dp).padding(top = compactTopPad)
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 宽屏有独立列表/详情两栏，提示留在工作区顶部；紧凑端则放进
        // 同一个 LazyColumn，随作业列表一起滚动，避免红色 banner 固定遮挡正文。
        if ((expanded || state.homework.isEmpty()) && legacyWarningVisible) {
            LegacySmartTransportWarning(onDismiss = onDismissLegacyWarning)
        }

        // 同步进度条由 DestinationPage 钉在顶栏下，此处不再重复。
        // 失败横幅：宽屏仍钉在工作区顶部；紧凑端 underlap 时跟 legacy 提示一样收进列表第一项，
        // 否则它会以布局占位把列表视口顶下去、内容进不了栏后。
        if (expanded || LocalTopBarClearance.current <= 0.dp) {
            state.failure?.let { failure ->
                HomeworkFailureBanner(
                    failure = failure,
                    hasContent = state.homework.isNotEmpty(),
                    onRetry = onRefresh,
                    onDismiss = model::dismissFailure,
                )
            }
        }

        when {
            // 短内容（加载/空）不滚动：留在栏下，老起笔位置（外层已无占位，这里补回）。
            state.isLoading && state.homework.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(top = 8.dp + topClearance),
            ) { HomeworkLoadingState() }
            state.homework.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(top = 8.dp + topClearance),
            ) { HomeworkEmptyState(onRefresh) }
            else -> {
                if (expanded) {
                    // 与移动端一致：同步态在顶栏；Banner 内筛选入口；课程/过期/排序进 sheet。
                    HomeworkSummary(
                        state = state,
                        onOpenFilter = { showFilterSheet = true },
                    )
                    if (state.visibleHomework.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("当前筛选下没有作业", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { showFilterSheet = true }) { Text("调整筛选") }
                            }
                        }
                    } else {
                        // 宽屏固定四六开：列表 40% / 详情 60%，不再提供拖拽调宽。
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            HomeworkList(
                                homework = state.visibleHomework,
                                selectedKey = state.selectedHomeworkKey,
                                sortOrder = state.sortOrder,
                                onOpen = { key ->
                                    transfer.fileFeedback = null
                                    scope.launch { loadHomeworkDetailsWithRecovery(model, key, onReauthenticate) }
                                },
                                modifier = Modifier.weight(0.4f).fillMaxHeight(),
                            )
                            HomeworkDetailPanel(
                                homework = state.selectedHomework,
                                detail = state.detail,
                                submittedAttachments = state.submittedAttachments,
                                isLoading = state.isDetailLoading,
                                isSubmittedLoading = state.isSubmittedAttachmentsLoading,
                                failure = state.detailFailure,
                                fileFailure = state.fileFailure,
                                isFileTransferInProgress = state.isFileTransferInProgress,
                                fileGatewayAvailable = fileGateway.isAvailable,
                                fileFeedback = transfer.fileFeedback,
                                onDownloadTeacher = transfer::saveTeacherAttachment,
                                onDownloadSubmitted = transfer::saveSubmittedAttachment,
                                onUpload = transfer::openUpload,
                                onCopyMarkdown = { markdown ->
                                    transfer.copyMarkdown(markdown)
                                },
                                onSaveMarkdown = { markdown, fileName ->
                                    transfer.saveMarkdown(markdown, fileName)
                                },
                                isSubmitting = state.isSubmitting,
                                modifier = Modifier.weight(0.6f).fillMaxHeight(),
                            )
                        }
                    }
                } else {
                    // 紧凑端：Banner（含筛选按钮）+ 列表；点卡片先选中再 push 详情二级页。
                    HomeworkScrollableContent(
                        state = state,
                        legacyWarningVisible = legacyWarningVisible,
                        onDismissLegacyWarning = onDismissLegacyWarning,
                        onOpenFilter = { showFilterSheet = true },
                        onRetry = onRefresh,
                        onDismissFailure = model::dismissFailure,
                        onOpen = { key ->
                            transfer.fileFeedback = null
                            // 必须先同步写完选中再 push，否则详情页打开时 selectedHomework 仍为空；
                            // 详情的网络加载异步进行，不阻塞 push。
                            model.selectHomework(key)
                            scope.launch { loadHomeworkDetailsWithRecovery(model, key, onReauthenticate) }
                            onOpenDetail()
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        AppleSheet(
            onDismissRequest = { showFilterSheet = false },
            title = "作业筛选",
            needsFullHeight = true,
            scrollableBody = true,
        ) {
            HomeworkFilterSheet(state = state, model = model)
        }
    }

    HomeworkUploadDialog(transfer = transfer, isSubmitting = state.isSubmitting)
}

/** 作业附件下载/上传的共享传输状态：宽屏列表侧栏与紧凑详情二级页各持一份，逻辑一致。 */
private class HomeworkTransferState(
    private val model: HomeworkScreenModel,
    private val fileGateway: HomeworkFileGateway,
    private val reauthenticate: (suspend () -> Boolean)?,
    private val scope: CoroutineScope,
    private val copyText: (String) -> Unit,
) {
    var showUpload by mutableStateOf(false)
    var uploadFiles by mutableStateOf<List<HomeworkFileContent>>(emptyList())
    var uploadContent by mutableStateOf("")
    var uploadFeedback by mutableStateOf<String?>(null)
    var fileFeedback by mutableStateOf<String?>(null)
    var isRecoveringSession by mutableStateOf(false)

    fun copyMarkdown(markdown: String) {
        copyText(markdown)
        fileFeedback = "已复制为 Markdown"
    }

    fun saveMarkdown(markdown: String, suggestedName: String) {
        if (!fileGateway.isAvailable) {
            fileFeedback = "当前平台的系统保存面板尚未接入。"
            return
        }
        scope.launch {
            fileFeedback = null
            val file = HomeworkFileContent(
                fileName = safeExportFileName(suggestedName),
                contentType = "text/markdown",
                bytes = markdown.encodeToByteArray(),
            )
            fileFeedback = when (val result = fileGateway.saveFile(file)) {
                HomeworkFileSaveResult.Saved -> "Markdown 已保存"
                HomeworkFileSaveResult.Cancelled -> null
                is HomeworkFileSaveResult.Failed -> result.saveFeedback()
            }
        }
    }

    fun saveTeacherAttachment(attachmentId: Int) {
        if (!fileGateway.isAvailable) {
            fileFeedback = "当前平台的系统保存面板尚未接入。"
            return
        }
        scope.launch {
            fileFeedback = null
            when (val downloaded = model.downloadTeacherAttachment(attachmentId)) {
                is HomeworkOperationResult.Failure -> Unit
                is HomeworkOperationResult.Success -> {
                    fileFeedback = fileGateway.saveFile(downloaded.value).saveFeedback()
                }
            }
        }
    }

    fun saveSubmittedAttachment(attachmentId: String) {
        if (!fileGateway.isAvailable) {
            fileFeedback = "当前平台的系统保存面板尚未接入。"
            return
        }
        scope.launch {
            fileFeedback = null
            when (val downloaded = model.downloadSubmittedAttachment(attachmentId)) {
                is HomeworkOperationResult.Failure -> Unit
                is HomeworkOperationResult.Success -> {
                    fileFeedback = fileGateway.saveFile(downloaded.value).saveFeedback()
                }
            }
        }
    }

    fun openUpload() {
        if (!fileGateway.isAvailable) {
            fileFeedback = "当前平台的系统文件选择器尚未接入。"
            return
        }
        uploadFiles = emptyList()
        uploadContent = ""
        uploadFeedback = null
        showUpload = true
    }

    fun closeUpload() {
        if (model.state.value.isSubmitting) return
        showUpload = false
        uploadFiles = emptyList()
        uploadContent = ""
        uploadFeedback = null
    }

    fun pickUploadFiles() {
        scope.launch {
            when (val result = fileGateway.pickFiles()) {
                HomeworkFilePickResult.Cancelled -> Unit
                is HomeworkFilePickResult.Failed -> uploadFeedback = "无法读取所选文件，请重新选择。"
                is HomeworkFilePickResult.Selected -> {
                    uploadFiles = uploadFiles + result.files
                    uploadFeedback = null
                }
            }
        }
    }

    fun removeUploadFile(index: Int) {
        uploadFiles = uploadFiles.filterIndexed { itemIndex, _ -> itemIndex != index }
    }

    fun submitUpload() {
        if (uploadFiles.isEmpty()) {
            uploadFeedback = "请先选择至少一个文件。"
            return
        }
        scope.launch {
            uploadFeedback = null
            var result: HomeworkOperationResult<Unit>? = null
            var sessionExpired = false
            val recovery = SessionRefreshCoordinator(
                reauthenticate = reauthenticate,
                onRecoveryStateChanged = { isRecoveringSession = it },
            )
            recovery.run(
                operation = {
                    val attemptResult = model.submitHomework(uploadContent, uploadFiles)
                    result = attemptResult
                    sessionExpired = attemptResult is HomeworkOperationResult.Failure &&
                        attemptResult.reason == HomeworkSyncFailure.SESSION_EXPIRED
                },
                sessionExpired = { sessionExpired },
            )
            when (val finalResult = result ?: HomeworkOperationResult.Failure(HomeworkSyncFailure.NETWORK)) {
                is HomeworkOperationResult.Failure -> {
                    val reasonText = when (finalResult.reason) {
                        HomeworkSyncFailure.NETWORK -> "上传失败，请检查网络后重试。"
                        HomeworkSyncFailure.SESSION_EXPIRED -> "登录会话已失效，请点击右上角刷新重试登录。"
                        // 服务端原文（如「上传文件类型不支持，请更换文件！」）比通用文案更有用；
                        // 回执没有可读原因时仍要给出可执行的下一步。
                        HomeworkSyncFailure.SUBMIT_REJECTED -> finalResult.serverMessage
                            ?: "学校平台没有接受这次提交，请更换文件或稍后重试。"
                        HomeworkSyncFailure.MALFORMED_RESPONSE -> "学校平台没有确认提交成功，请稍后重试。"
                        HomeworkSyncFailure.SECURE_CHANNEL_UNAVAILABLE -> "该资源地址不在允许的学校通道范围内。"
                        HomeworkSyncFailure.CACHE -> "提交已停止，本地缓存不可用。"
                    }
                    uploadFeedback = finalResult.diagnostic
                        ?.let { diagnostic -> "$reasonText\n（原因：$diagnostic）" }
                        ?: reasonText
                }
                is HomeworkOperationResult.Success -> {
                    showUpload = false
                    uploadFiles = emptyList()
                    uploadContent = ""
                    fileFeedback = "作业已提交，列表和详情已刷新。"
                }
            }
        }
    }
}

@Composable
private fun rememberHomeworkTransferState(
    model: HomeworkScreenModel,
    fileGateway: HomeworkFileGateway,
    reauthenticate: (suspend () -> Boolean)?,
): HomeworkTransferState {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    return remember(model, fileGateway, clipboard, reauthenticate) {
        HomeworkTransferState(
            model = model,
            fileGateway = fileGateway,
            reauthenticate = reauthenticate,
            scope = scope,
            copyText = { text -> clipboard.setText(AnnotatedString(text)) },
        )
    }
}

/** 上传作业弹窗：iOS 走宿主原生 sheet，其他平台保留 Material 对话框。 */
@Composable
private fun HomeworkUploadDialog(
    transfer: HomeworkTransferState,
    isSubmitting: Boolean,
) {
    if (!transfer.showUpload) return
    AppleSheetOrAlert(
        onDismissRequest = transfer::closeUpload,
        title = "上传作业",
        confirmLabel = if (isSubmitting || transfer.isRecoveringSession) "正在提交" else "提交",
        onConfirm = transfer::submitUpload,
        confirmEnabled = transfer.uploadFiles.isNotEmpty() && !isSubmitting && !transfer.isRecoveringSession,
        dismissLabel = "取消",
        dismissEnabled = !isSubmitting,
        needsFullHeight = true,
    ) {
            UploadHomeworkContent(
                files = transfer.uploadFiles,
                content = transfer.uploadContent,
                feedback = transfer.uploadFeedback,
                isSubmitting = isSubmitting,
                onContentChange = { transfer.uploadContent = it },
                onPickFiles = transfer::pickUploadFiles,
                onRemoveFile = transfer::removeUploadFile,
            )
    }
}

/**
 * 紧凑端作业详情二级页内容（仿教室详情）。
 * 返回依赖顶栏/系统边缘手势，页内不再放返回按钮；顶栏固定显示「作业详情」，正文不再重复该标题。
 * 上传与附件下载与列表页共用同一套传输逻辑。
 */
@Composable
fun HomeworkDetailWorkspace(
    model: HomeworkScreenModel,
    fileGateway: HomeworkFileGateway,
    onReauthenticate: (suspend () -> Boolean)? = null,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val transfer = rememberHomeworkTransferState(model, fileGateway, onReauthenticate)
    val detailScrollState = rememberScrollState()
    // 真实偏移上报给壳层算玻璃浓度。
    TopScrollColumn(
        state = detailScrollState,
        modifier = modifier.desktopTouchScroll(detailScrollState),
        contentModifier = Modifier
            .padding(horizontal = 24.dp)
            // 原生栏 underlap 时视口顶边贴屏幕顶，首项靠这份顶边距让开（其余平台恒 0）。
            .padding(top = 12.dp + LocalTopBarClearance.current, bottom = 28.dp + LocalBottomBarClearance.current),
    ) {
        state.selectedHomework?.let { selected ->
            HomeworkDetailSheetBody(
                homework = selected,
                detail = state.detail,
                submittedAttachments = state.submittedAttachments,
                isLoading = state.isDetailLoading,
                isSubmittedLoading = state.isSubmittedAttachmentsLoading,
                failure = state.detailFailure,
                fileFailure = state.fileFailure,
                isFileTransferInProgress = state.isFileTransferInProgress,
                fileGatewayAvailable = fileGateway.isAvailable,
                fileFeedback = transfer.fileFeedback,
                onDownloadTeacher = transfer::saveTeacherAttachment,
                onDownloadSubmitted = transfer::saveSubmittedAttachment,
                onUpload = transfer::openUpload,
                isSubmitting = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                showHeading = false,
            )
        }
    }
    HomeworkUploadDialog(transfer = transfer, isSubmitting = state.isSubmitting)
}

@Composable
private fun HomeworkSummary(
    state: HomeworkUiState,
    onOpenFilter: (() -> Unit)? = null,
) {
    // 仅课程/过期/已提交过滤算「筛选」；改排序不算。
    val filtered = state.selectedCourses.isNotEmpty() || state.hideExpired || state.hideSubmitted
    val subtitle = buildString {
        append(
            if (state.dueSoonCount > 0) {
                "未来 48 小时内有 ${state.dueSoonCount} 项未提交"
            } else {
                "未来 48 小时内暂无临近截止项"
            },
        )
        if (filtered) {
            append(" · 已筛选")
        }
    }
    HomeworkSummaryBanner(state.visibleHomework.size, subtitle, onOpenFilter)
}

internal fun subtitleWithBoldFilterMarker(text: String): AnnotatedString {
    val marker = "已筛选"
    val index = text.lastIndexOf(marker)
    if (index < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, index))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(marker) }
        append(text.substring(index + marker.length))
    }
}

@Composable
internal fun HomeworkSummaryBanner(count: Int, subtitle: String, onOpenFilter: (() -> Unit)? = null) {
    // 与成绩/课表 Banner 对齐：摘要 + 右侧筛选 pill；同步态在顶栏右上。
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onOpenFilter != null) {
                            Modifier
                                .clickable(onClick = onOpenFilter)
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Text(
                    "${count} 项作业",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitleWithBoldFilterMarker(subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.accessibleAlpha(0.78f),
                )
            }
            if (onOpenFilter != null) {
                Surface(
                    onClick = onOpenFilter,
                    color = MaterialTheme.colorScheme.surface.accessibleAlpha(0.86f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.semantics { contentDescription = "筛选与排序" },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HomeworkFilterFunnelIcon(modifier = Modifier.size(18.dp))
                        HomeworkSortBarsIcon(modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeworkFilterFunnelIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        val left = 2.5.dp.toPx()
        val right = size.width - left
        val top = 3.dp.toPx()
        val midY = size.height * 0.48f
        val neckLeft = size.width * 0.42f
        val neckRight = size.width * 0.58f
        val bottom = size.height - 2.5.dp.toPx()
        drawLine(tint, Offset(left, top), Offset(right, top), stroke, StrokeCap.Round)
        drawLine(tint, Offset(left, top), Offset(neckLeft, midY), stroke, StrokeCap.Round)
        drawLine(tint, Offset(right, top), Offset(neckRight, midY), stroke, StrokeCap.Round)
        drawLine(tint, Offset(neckLeft, midY), Offset(neckLeft, bottom), stroke, StrokeCap.Round)
        drawLine(tint, Offset(neckRight, midY), Offset(neckRight, bottom), stroke, StrokeCap.Round)
        drawLine(tint, Offset(neckLeft, bottom), Offset(neckRight, bottom), stroke, StrokeCap.Round)
    }
}

@Composable
private fun HomeworkSortBarsIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        val left = 3.dp.toPx()
        val right = size.width - left
        val ys = listOf(size.height * 0.28f, size.height * 0.5f, size.height * 0.72f)
        val ends = listOf(right, right * 0.72f, right * 0.48f)
        ys.zip(ends).forEach { (y, endX) ->
            drawLine(tint, Offset(left, y), Offset(endX, y), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun HomeworkBehaviorFilters(state: HomeworkUiState, model: HomeworkScreenModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeworkDeadlineFilterChips(state = state, model = model)
        OutlinedButton(onClick = model::cycleSortOrder, modifier = Modifier.fillMaxWidth()) {
            Text(
                when (state.sortOrder) {
                    HomeworkSortOrder.ORIGINAL -> "截止时间 · 原顺序"
                    HomeworkSortOrder.ASCENDING -> "截止时间 · 由近到远"
                    HomeworkSortOrder.DESCENDING -> "截止时间 · 由远到近"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 截止时间：两个圆角矩形，互斥（显示全部 / 隐藏已过期），另加独立的隐藏已提交。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeworkDeadlineFilterChips(
    state: HomeworkUiState,
    model: HomeworkScreenModel,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = !state.hideExpired,
            onClick = { model.setHideExpired(false) },
            shape = RoundedCornerShape(10.dp),
            label = { Text("显示全部日期") },
        )
        FilterChip(
            selected = state.hideExpired,
            onClick = { model.setHideExpired(true) },
            shape = RoundedCornerShape(10.dp),
            label = { Text("隐藏已过期") },
        )
        FilterChip(
            selected = state.hideSubmitted,
            onClick = { model.setHideSubmitted(!state.hideSubmitted) },
            shape = RoundedCornerShape(10.dp),
            label = { Text("隐藏已提交") },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.HomeworkFilterSheet(
    state: HomeworkUiState,
    model: HomeworkScreenModel,
) {
    val filterScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(filterScrollState)
            .desktopTouchScroll(filterScrollState)
            .sheetScrollContentPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("筛选与排序", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "课程",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.courseOptions.isEmpty()) {
                Text(
                    "暂无课程数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.selectedCourses.isEmpty(),
                        onClick = model::clearCourseFilter,
                        label = { Text("全部") },
                    )
                    state.courseOptions.forEach { course ->
                        FilterChip(
                            selected = course in state.selectedCourses,
                            onClick = { model.toggleCourse(course) },
                            label = {
                                Text(course, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "截止时间",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HomeworkDeadlineFilterChips(state = state, model = model)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "排序",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.sortOrder == HomeworkSortOrder.ORIGINAL,
                    onClick = { model.setSortOrder(HomeworkSortOrder.ORIGINAL) },
                    shape = RoundedCornerShape(percent = 50),
                    label = { Text("原顺序") },
                )
                FilterChip(
                    selected = state.sortOrder == HomeworkSortOrder.ASCENDING,
                    onClick = { model.setSortOrder(HomeworkSortOrder.ASCENDING) },
                    shape = RoundedCornerShape(percent = 50),
                    label = { Text("由近到远") },
                )
                FilterChip(
                    selected = state.sortOrder == HomeworkSortOrder.DESCENDING,
                    onClick = { model.setSortOrder(HomeworkSortOrder.DESCENDING) },
                    shape = RoundedCornerShape(percent = 50),
                    label = { Text("由远到近") },
                )
            }
        }

    }
}

@Composable
private fun CourseFilterRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = selected,
                    role = Role.Checkbox,
                    onValueChange = { onClick() },
                )
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = null)
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** 紧凑作业页唯一纵向滚动体：Banner + 列表（筛选在 sheet）。 */
@Composable
private fun HomeworkScrollableContent(
    state: HomeworkUiState,
    legacyWarningVisible: Boolean = false,
    onDismissLegacyWarning: () -> Unit = {},
    onOpenFilter: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit = {},
    onDismissFailure: () -> Unit = {},
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    // CompositionLocal 不能在 LazyColumn 的 DSL 作用域里读，提到可组合上下文。
    val topClearance = LocalTopBarClearance.current
    // 真实偏移上报给壳层算玻璃浓度（手势累加会漂，读列表状态不会）。
    LaunchedEffect(state.sortOrder, state.hideExpired, state.hideSubmitted, state.selectedCourses) {
        listState.scrollToItem(0)
    }
    TopScrollLazyColumn(
        state = listState,
        modifier = modifier.desktopTouchScroll(listState),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(
            // 首项靠内部顶边距让开原生栏（外层已不再占位，见 Workspace）：8.dp 还原原来的
            // 列表起笔位置，clearance 让内容从栏底开始、滚起来穿进栏后供原生 blur 采样。
            top = 8.dp + topClearance,
            bottom = 18.dp + LocalBottomBarClearance.current,
        ),
    ) {
        // underlap 时失败横幅也收进列表（外层已跳过），平时保持外层旧布局，见 Workspace。
        if (topClearance > 0.dp) {
            state.failure?.let { failure ->
                item(key = "homework-failure") {
                    HomeworkFailureBanner(
                        failure = failure,
                        hasContent = state.homework.isNotEmpty(),
                        onRetry = onRetry,
                        onDismiss = onDismissFailure,
                    )
                }
            }
        }
        if (legacyWarningVisible) {
            item(key = "homework-legacy-warning") {
                LegacySmartTransportWarning(onDismiss = onDismissLegacyWarning)
            }
        }
        item(key = "homework-summary") {
            HomeworkSummary(state = state, onOpenFilter = onOpenFilter)
        }
        if (state.visibleHomework.isEmpty()) {
            item(key = "homework-empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("当前筛选下没有作业", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenFilter) { Text("调整筛选") }
                }
            }
        } else {
            items(state.visibleHomework, key = Homework::stableKey) { item ->
                HomeworkCard(
                    item = item,
                    // 紧凑端详情是独立二级页（原生 push），返回时没有可靠的 pop 回调可清选中；
                    // 选中高亮只在宽屏双栏有意义，这里一律不渲染，避免返回后旧选中颜色残留。
                    selected = false,
                    onOpen = onOpen,
                )
            }
        }
    }
}

@Composable
internal fun HomeworkCard(
    item: Homework,
    selected: Boolean,
    onOpen: (String) -> Unit,
    source: team.bjtuss.bjtuselfservice.shared.feature.assignment.AssignmentSource? = null,
) {
    val key = item.stableKey()
    ElevatedCard(
        onClick = { onOpen(key) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            team.bjtuss.bjtuselfservice.shared.feature.assignment.AssignmentCardHeader(
                item.courseName, item.title, team.bjtuss.bjtuselfservice.shared.domain.homework.isHomeworkSubmitted(item), source)
            HomeworkCardLine("类型", item.typeLabel())
            HomeworkCardLine("截止", item.endTime.ifBlank { "未提供" })
            HomeworkCardLine("提交人数", "${item.submitCount} / ${item.allCount}")
            if (item.score.isNotBlank()) HomeworkCardLine("评分", item.score)
        }
    }
}

@Composable
private fun HomeworkList(
    homework: List<Homework>,
    selectedKey: String?,
    sortOrder: HomeworkSortOrder,
    onOpen: (String) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(sortOrder) {
        listState.scrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.desktopTouchScroll(listState),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 18.dp + LocalBottomBarClearance.current),
    ) {
        items(homework, key = Homework::stableKey) { item ->
            HomeworkCard(
                item = item,
                selected = item.stableKey() == selectedKey,
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun HomeworkCardLine(label: String, value: String) {
    Text(
        "$label · $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HomeworkDetailPanel(
    homework: Homework?,
    detail: HomeworkDetail?,
    submittedAttachments: List<SubmittedHomeworkAttachment>,
    isLoading: Boolean,
    isSubmittedLoading: Boolean,
    failure: HomeworkSyncFailure?,
    fileFailure: HomeworkSyncFailure?,
    isFileTransferInProgress: Boolean,
    fileGatewayAvailable: Boolean,
    fileFeedback: String?,
    onDownloadTeacher: (Int) -> Unit,
    onDownloadSubmitted: (String) -> Unit,
    onUpload: () -> Unit,
    onCopyMarkdown: (String) -> Unit,
    onSaveMarkdown: (String, String) -> Unit,
    isSubmitting: Boolean,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.54f),
        shape = RoundedCornerShape(22.dp),
    ) {
        if (homework == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("选择一项作业查看要求和附件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            HomeworkDetailContent(
                homework = homework,
                detail = detail,
                submittedAttachments = submittedAttachments,
                isLoading = isLoading,
                isSubmittedLoading = isSubmittedLoading,
                failure = failure,
                fileFailure = fileFailure,
                isFileTransferInProgress = isFileTransferInProgress,
                fileGatewayAvailable = fileGatewayAvailable,
                fileFeedback = fileFeedback,
                onDownloadTeacher = onDownloadTeacher,
                onDownloadSubmitted = onDownloadSubmitted,
                onUpload = onUpload,
                onCopyMarkdown = onCopyMarkdown,
                onSaveMarkdown = onSaveMarkdown,
                isSubmitting = isSubmitting,
                contentPadding = PaddingValues(22.dp),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 宽屏侧栏用的可滚动作业详情。 */
@Composable
private fun HomeworkDetailContent(
    homework: Homework,
    detail: HomeworkDetail?,
    submittedAttachments: List<SubmittedHomeworkAttachment>,
    isLoading: Boolean,
    isSubmittedLoading: Boolean,
    failure: HomeworkSyncFailure?,
    fileFailure: HomeworkSyncFailure?,
    isFileTransferInProgress: Boolean,
    fileGatewayAvailable: Boolean,
    fileFeedback: String?,
    onDownloadTeacher: (Int) -> Unit,
    onDownloadSubmitted: (String) -> Unit,
    onUpload: () -> Unit,
    onCopyMarkdown: (String) -> Unit,
    onSaveMarkdown: (String, String) -> Unit,
    isSubmitting: Boolean,
    modifier: Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val scrollState = rememberScrollState()
    val markdown = remember(homework, detail, submittedAttachments) {
        homeworkDetailToMarkdown(homework, detail, submittedAttachments)
    }
    val markdownFileName = remember(homework) {
        safeExportFileName("${homework.courseName}-${homework.title}.md")
    }
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .desktopTouchScroll(scrollState)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "作业详情",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = { onCopyMarkdown(markdown) }) {
                Text("复制为 Markdown")
            }
            OutlinedButton(
                onClick = { onSaveMarkdown(markdown, markdownFileName) },
                enabled = fileGatewayAvailable,
            ) {
                Text("保存为 Markdown")
            }
        }
        HomeworkDetailSheetBody(
            homework = homework,
            detail = detail,
            submittedAttachments = submittedAttachments,
            isLoading = isLoading,
            isSubmittedLoading = isSubmittedLoading,
            failure = failure,
            fileFailure = fileFailure,
            isFileTransferInProgress = isFileTransferInProgress,
            fileGatewayAvailable = fileGatewayAvailable,
            fileFeedback = fileFeedback,
            onDownloadTeacher = onDownloadTeacher,
            onDownloadSubmitted = onDownloadSubmitted,
            onUpload = onUpload,
            isSubmitting = isSubmitting,
            showHeading = false,
        )
    }
}

internal fun homeworkDetailToMarkdown(
    homework: Homework,
    detail: HomeworkDetail?,
    submittedAttachments: List<SubmittedHomeworkAttachment>,
): String {
    val content = schoolRichTextToPlainMultiline(detail?.content).ifBlank { "老师未填写文字要求。" }
    val teacherAttachments = detail?.attachments.orEmpty()
    return buildString {
        appendLine("# ${homework.title}")
        appendLine()
        appendLine("- 课程：${homework.courseName}")
        appendLine("- 类型：${homework.typeLabel()}")
        appendLine("- 开放时间：${homework.openDate.ifBlank { "未提供" }}")
        appendLine("- 截止时间：${homework.endTime.ifBlank { "未提供" }}")
        appendLine("- 提交状态：${homework.subStatus.ifBlank { "未标明" }}")
        if (homework.score.isNotBlank()) appendLine("- 评分：${homework.score}")
        appendLine()
        appendLine("## 作业要求")
        appendLine()
        appendLine(content)
        appendLine()
        appendLine("## 老师提供的附件")
        appendLine()
        if (teacherAttachments.isEmpty()) {
            appendLine("- 暂无附件")
        } else {
            teacherAttachments.forEach { attachment ->
                appendLine("- ${attachment.fileName.ifBlank { "附件 ${attachment.id}" }}")
            }
        }
        if (homework.idSnId != null || homework.subStatus == "已提交") {
            appendLine()
            appendLine("## 我已提交的附件")
            appendLine()
            if (submittedAttachments.isEmpty()) {
                appendLine("- 没有找到已提交附件")
            } else {
                submittedAttachments.forEach { attachment ->
                    appendLine("- ${attachment.fileName.ifBlank { "附件 ${attachment.id}" }}")
                }
            }
        }
        appendLine()
    }.trimEnd() + "\n"
}

/** 作业详情正文（详情页与宽屏侧栏共用）；滚动由调用方 Modifier 提供。宽屏侧栏保留「作业详情」小标题，详情页由顶栏显示标题、正文不重复。 */
@Composable
private fun HomeworkDetailSheetBody(
    homework: Homework,
    detail: HomeworkDetail?,
    submittedAttachments: List<SubmittedHomeworkAttachment>,
    isLoading: Boolean,
    isSubmittedLoading: Boolean,
    failure: HomeworkSyncFailure?,
    fileFailure: HomeworkSyncFailure?,
    isFileTransferInProgress: Boolean,
    fileGatewayAvailable: Boolean,
    fileFeedback: String?,
    onDownloadTeacher: (Int) -> Unit,
    onDownloadSubmitted: (String) -> Unit,
    onUpload: () -> Unit,
    isSubmitting: Boolean,
    modifier: Modifier = Modifier,
    showHeading: Boolean = true,
) {
    val teacherAttachments = detail?.attachments.orEmpty()
    val showSubmittedSection = homework.idSnId != null || homework.subStatus == "已提交"
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showHeading) {
            Text(
                "作业详情",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(homework.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        HomeworkDetailLine("课程", homework.courseName)
        HomeworkDetailLine("类型", homework.typeLabel())
        HomeworkDetailLine("开放时间", homework.openDate.ifBlank { "未提供" })
        HomeworkDetailLine("截止时间", homework.endTime.ifBlank { "未提供" })
        HomeworkDetailLine("提交状态", homework.subStatus.ifBlank { "未标明" })
        if (homework.score.isNotBlank()) HomeworkDetailLine("评分", homework.score)
        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        failure?.let {
            Text(
                "详情同步失败，正在显示列表中已有的内容。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        fileFailure?.let {
            Text(
                "附件或提交状态读取失败，请稍后重试。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (isFileTransferInProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("正在获取附件…", style = MaterialTheme.typography.bodySmall)
        }
        fileFeedback?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text("作业要求", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            schoolRichTextToPlainMultiline(detail?.content)
                .ifBlank { "老师未填写文字要求。" },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text("老师提供的附件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (teacherAttachments.isEmpty()) {
            Text("暂无附件", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            teacherAttachments.forEach { attachment ->
                HomeworkAttachmentRow(
                    attachment = attachment,
                    downloadEnabled = fileGatewayAvailable && !isFileTransferInProgress,
                    onDownload = { onDownloadTeacher(attachment.id) },
                )
            }
        }
        if (showSubmittedSection) {
            Text("我已提交的附件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (isSubmittedLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (submittedAttachments.isEmpty()) {
                Text("没有找到已提交附件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                submittedAttachments.forEach { attachment ->
                    SubmittedHomeworkAttachmentRow(
                        attachment = attachment,
                        downloadEnabled = fileGatewayAvailable && !isFileTransferInProgress,
                        onDownload = { onDownloadSubmitted(attachment.id) },
                    )
                }
            }
        }
        FilledTonalButton(
            onClick = onUpload,
            enabled = fileGatewayAvailable && !isSubmitting && !isFileTransferInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSubmitting) "正在提交" else "上传作业")
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.45f),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                if (fileGatewayAvailable) {
                    "下载会先获取附件，再由系统保存面板选择位置；上传会先让你检查文件列表，再发送到学校平台。"
                } else {
                    "当前平台的系统文件面板仍在接入；不会静默写入文件，也不会把网络完成冒充为保存成功。"
                },
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UploadHomeworkContent(
    files: List<HomeworkFileContent>,
    content: String,
    feedback: String?,
    isSubmitting: Boolean,
    onContentChange: (String) -> Unit,
    onPickFiles: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uploadScrollState = rememberScrollState()
    Column(
        modifier = modifier
            .heightIn(max = 520.dp)
            .verticalScroll(uploadScrollState)
            .desktopTouchScroll(uploadScrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "先选择文件并检查列表，再提交到学校智慧教学平台。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            label = { Text("提交说明（可选）") },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
        )
        OutlinedButton(
            onClick = onPickFiles,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (files.isEmpty()) "选择文件" else "继续添加文件")
        }
        if (files.isEmpty()) {
            Text("尚未选择文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            files.forEachIndexed { index, file ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                file.bytes.size.toLong().toReadableSize(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onRemoveFile(index) }, enabled = !isSubmitting) { Text("移除") }
                    }
                }
            }
        }
        feedback?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (isSubmitting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("正在上传并提交，请勿关闭窗口。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HomeworkAttachmentRow(
    attachment: HomeworkAttachment,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(attachment.fileName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                attachment.sizeBytes.toReadableSize(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onDownload,
                enabled = downloadEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("下载")
            }
        }
    }
}

@Composable
private fun SubmittedHomeworkAttachmentRow(
    attachment: SubmittedHomeworkAttachment,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(attachment.fileName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "已提交附件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onDownload,
                enabled = downloadEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("下载")
            }
        }
    }
}

@Composable
private fun HomeworkDetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HomeworkFailureBanner(
    failure: HomeworkSyncFailure,
    hasContent: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppErrorBanner(
        message = when (failure) {
            HomeworkSyncFailure.NETWORK -> if (hasContent) {
                "同步失败，正在显示本地作业。"
            } else {
                "无法连接智慧教学平台，请检查网络后重试。"
            }
            HomeworkSyncFailure.SESSION_EXPIRED -> "智慧教学平台会话已失效，请点击右上角刷新重试登录。"
            HomeworkSyncFailure.MALFORMED_RESPONSE -> "智慧教学平台响应结构已变化，暂时无法解析。"
            // 已授权明文后仍可能因 URL 白名单失败；勿再写「没有 HTTPS」与顶栏授权提示打架。
            HomeworkSyncFailure.SECURE_CHANNEL_UNAVAILABLE ->
                "该资源地址不在允许的学校通道范围内。"
            HomeworkSyncFailure.CACHE -> "本地作业缓存操作失败。"
            // 提交被服务端回绝不会写进同步横幅，这里只是保证状态机完整可穷举。
            HomeworkSyncFailure.SUBMIT_REJECTED -> "提交被学校平台回绝，请查看上传窗口中的原因。"
        },
        onRetry = if (failure != HomeworkSyncFailure.CACHE) onRetry else null,
        onDismiss = onDismiss,
    )
}

@Composable
private fun HomeworkLoadingState() = WorkspaceLoadingState("正在读取本地作业并连接智慧教学平台…")

@Composable
private fun HomeworkEmptyState(onRefresh: () -> Unit) = WorkspaceEmptyState(
    title = "暂无作业",
    description = "本地没有缓存，智慧教学平台也没有返回可显示的任务。",
    onRefresh = onRefresh,
)

private fun Long.toReadableSize(): String = when {
    this <= 0L -> "大小未知"
    this < 1024L -> "$this B"
    this < 1024L * 1024L -> "${this / 1024L} KB"
    else -> "${this / (1024L * 1024L)} MB"
}

private fun HomeworkFileSaveResult.saveFeedback(): String? = when (this) {
    HomeworkFileSaveResult.Saved -> "附件已保存到你选择的位置。"
    HomeworkFileSaveResult.Cancelled -> null
    is HomeworkFileSaveResult.Failed -> "系统保存失败，请重新选择位置。"
}

/** A failed read must confirm expiry before attempting authentication recovery. */
internal suspend fun loadHomeworkDetailsWithRecovery(model: HomeworkScreenModel, key: String,
    reauthenticate: (suspend () -> Boolean)?) {
    val recovery = SessionRefreshCoordinator(reauthenticate = reauthenticate, sessionVerifiedAtStart = true)
    recovery.run(operation = { model.showDetails(key) }, sessionExpired = {
        model.state.value.selectedHomeworkKey == key && model.state.value.detailFailure == HomeworkSyncFailure.SESSION_EXPIRED
    })
}

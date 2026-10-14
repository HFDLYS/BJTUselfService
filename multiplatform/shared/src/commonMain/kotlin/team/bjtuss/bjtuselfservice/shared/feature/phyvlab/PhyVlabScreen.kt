package team.bjtuss.bjtuselfservice.shared.feature.phyvlab

import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalBottomBarClearance

import androidx.compose.foundation.Canvas
import team.bjtuss.bjtuselfservice.shared.feature.assignment.*
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkSummaryBanner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheetOrAlert
import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalTopBarClearance
import team.bjtuss.bjtuselfservice.shared.feature.shell.TopScrollLazyColumn
import team.bjtuss.bjtuselfservice.shared.feature.shell.TopScrollColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import team.bjtuss.bjtuselfservice.shared.accessibleAlpha
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSyncFailure
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabActivity
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabCourse
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheet
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFilePickResult
import team.bjtuss.bjtuselfservice.shared.files.UnavailableHomeworkFileGateway

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhyVlabWorkspace(
    model: PhyVlabScreenModel,
    modifier: Modifier = Modifier,
    // 静默自动登录期间不能抢先用空的 Ktor Cookie jar 建立 Moodle 会话。
    holdNetwork: Boolean = false,
    fileGateway: HomeworkFileGateway = UnavailableHomeworkFileGateway,
    /** 紧凑端详情走原生二级页时，根列表不再挂出 ModalBottomSheet。 */
    showDetailSheet: Boolean = true,
    onOpenCourse: (String) -> Unit = {},
    onOpenActivity: (String) -> Unit = {},
    onOpenActivityDetail: (PhyVlabActivity) -> Unit = { model.showActivityDetails(it) },
    onLogout: () -> Unit = {},
    /** 由应用壳提供会话感知刷新；独立预览/测试时回退到模型刷新。 */
    onRefresh: (() -> Unit)? = null,
    /** 详情失败时只重试详情请求，不重新清空整页选择状态。 */
    onRetryDetail: (() -> Unit)? = null,
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = onRefresh ?: fun() {
        scope.launch { model.refresh() }
    }
    val retryDetail: () -> Unit = onRetryDetail ?: fun() {
        scope.launch { model.loadSelectedActivityDetail(force = true) }
    }
    var showUpload by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showUploadConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var uploadFiles by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<HomeworkFileContent>>(emptyList()) }
    var uploadFeedback by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    val filters = state.filters
    val listState = rememberLazyListState()
    // 真实偏移上报给壳层算玻璃浓度（手势累加会漂，读列表状态不会）。
    val nowEpochSeconds = rememberPhyVlabNowEpochSeconds()
    val allActivities = model.allActivities()
    val displayedActivities = filterPhyVlabActivities(allActivities, filters, nowEpochSeconds)
    val dueSoon = displayedActivities.count { !it.completed && it.dueTimestamp?.let { due -> due in nowEpochSeconds..(nowEpochSeconds + 48 * 3600) } == true }
    val subtitle = (if (dueSoon > 0) "未来 48 小时内有 $dueSoon 项未提交" else "未来 48 小时内暂无临近截止项") +
        if (filters.active) " · 已筛选" else ""
    LaunchedEffect(filters) { listState.scrollToItem(0) }

    LaunchedEffect(model, holdNetwork) {
        if (!holdNetwork) model.initialize()
    }
    LaunchedEffect(state.selectedCourse) {
        state.selectedCourse?.let { model.loadSelectedActivities() }
    }
    LaunchedEffect(state.selectedActivity, showDetailSheet) {
        if (showDetailSheet && state.selectedActivity != null) model.loadSelectedActivityDetail()
    }
    LaunchedEffect(state.selectedActivity) {
        showUpload = false
        showUploadConfirm = false
        uploadFiles = emptyList()
        uploadFeedback = null
    }

    // 原生栏 underlap 时居中态保持老位置（外层无占位，这里按栏高补回）。
    val topClearance = LocalTopBarClearance.current
    Column(modifier = modifier.fillMaxSize()) {
        if (holdNetwork) {
            Box(
                Modifier.fillMaxSize().padding(top = topClearance),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator()
                    Text("正在完成统一身份认证…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return
        }
        if (state.isLoading && state.courses.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(top = topClearance),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return
        }
        val failureForBanner = state.failure ?: PhyVlabSyncFailure.SESSION_EXPIRED.takeIf {
            state.casLoginRequired && state.contentSource == PhyVlabContentSource.CACHE
        }
        // underlap 时横幅收进列表首项；其余路径保持外层旧布局（作业页同款）。
        if (topClearance <= 0.dp) {
            failureForBanner?.let { failure ->
                PhyVlabFailureBanner(
                    failure = failure,
                    hasCachedContent = state.contentSource == PhyVlabContentSource.CACHE,
                    cachedAtEpochMillis = state.cachedAtEpochMillis,
                    onRetry = refresh,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        // CAS 失效时如果仍有本地快照，继续展示只读缓存；用户可从右上角重试，
        // 不让校园网外的离线场景退化成空白/登录阻断页。
        if (state.casLoginRequired && state.contentSource != PhyVlabContentSource.CACHE) {
            // 短内容不滚动：留在栏下，老居中位置（外层无占位，这里按栏高补回）。
            Box(Modifier.fillMaxSize().padding(top = topClearance), contentAlignment = Alignment.Center) {
                PhyVlabCasLoginRequiredState(
                    onRetry = refresh,
                    onLogout = onLogout,
                )
            }
            return
        }
        if (state.courses.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(top = topClearance), contentAlignment = Alignment.Center) {
                PhyVlabEmptyState(
                    onRetry = refresh,
                    onOpenWeb = { onOpenCourse("https://phyvlab.bjtu.edu.cn/my/courses.php") },
                )
            }
            return
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val horizontalInset = when {
                maxWidth < 600.dp -> 16.dp
                maxWidth < 840.dp -> 12.dp
                else -> 0.dp
            }
            TopScrollLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .desktopTouchScroll(listState),
                contentPadding = PaddingValues(
                    // 首项靠内部顶边距让开原生栏：12.dp 还原起笔位置（外层已不再占位）。
                    start = horizontalInset,
                    top = 12.dp + topClearance,
                    end = horizontalInset,
                    bottom = 12.dp + LocalBottomBarClearance.current,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // underlap 时失败横幅收进列表首项（外层已去掉），平时不占位。
                if (topClearance > 0.dp) {
                    failureForBanner?.let { failure ->
                        item(key = "phyvlab-failure") {
                            PhyVlabFailureBanner(
                                failure = failure,
                                hasCachedContent = state.contentSource == PhyVlabContentSource.CACHE,
                                cachedAtEpochMillis = state.cachedAtEpochMillis,
                                onRetry = refresh,
                            )
                        }
                    }
                }
                item(key = "summary") {
                    HomeworkSummaryBanner(displayedActivities.size, subtitle, onOpenFilter = { showFilters = true })
                }
                if (displayedActivities.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (allActivities.isEmpty()) "暂无作业" else "当前筛选下没有作业", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showFilters = true }) { Text("调整筛选") }
                    }
                }
                items(displayedActivities, key = { "activity-${it.id}" }) { activity ->
                    PhyVlabActivityRow(
                        activity = activity,
                        nowEpochSeconds = nowEpochSeconds,
                        onOpen = { onOpenActivityDetail(activity) },
                    )
                }
            }
        }
    }

    if (showFilters) AppleSheet(onDismissRequest = { showFilters = false }, title = "作业筛选", needsFullHeight = true, scrollableBody = true) {
        AssignmentFilterSheet(state.courses.map { it.id to it.name }, filters, model::updateFilters)
    }

    if (showDetailSheet) state.selectedActivity?.let { activity ->
        // 实验详情含提交区与上传动作，半屏放不下：iOS 卡片直接开到全屏。
        AppleSheet(
            onDismissRequest = model::dismissActivityDetails,
            title = "物理作业详情",
            needsFullHeight = true,
        ) {
            PhyVlabAssignmentDetailContent(
                activity = activity,
                detail = state.assignmentDetail,
                isLoading = state.isDetailLoading,
                failure = state.detailFailure,
                feedback = state.submissionFeedback,
                fileGatewayAvailable = fileGateway.isAvailable,
                fileGateway = fileGateway,
                isSubmitting = state.isSubmitting,
                onSaveFiles = { added, removed -> scope.launch { model.submitSelectedActivity(added, removed) } },
                onFinalize = { scope.launch { model.submitSelectedActivity(emptyList(), finalize = true, filesChecked = true) } },
                onPrepareFinalization = model::prepareFinalization,
                finalizationStatement = state.finalizationStatement,
                submissionRevision = state.submissionRevision,
                nowEpochSeconds = nowEpochSeconds,
                onRetry = retryDetail,
                onUpload = {
                    uploadFiles = emptyList()
                    uploadFeedback = null
                    showUpload = true
                },
                onOpenWeb = { onOpenActivity(activity.activityUrl) },
            )
        }
    }

    if (showUpload) {
        PhyVlabUploadDialog(
            files = uploadFiles,
            feedback = uploadFeedback,
            isSubmitting = state.isSubmitting,
            onPickFiles = {
                scope.launch {
                    when (val result = fileGateway.pickFiles()) {
                        HomeworkFilePickResult.Cancelled -> Unit
                        is HomeworkFilePickResult.Failed -> uploadFeedback = "无法读取所选文件，请重新选择。"
                        is HomeworkFilePickResult.Selected -> {
                            uploadFiles = (uploadFiles + result.files).distinctBy { it.fileName }
                            uploadFeedback = null
                        }
                    }
                }
            },
            onRemoveFile = { index -> uploadFiles = uploadFiles.filterIndexed { i, _ -> i != index } },
            onSubmit = { showUploadConfirm = true },
            onDismiss = { if (!state.isSubmitting) showUpload = false },
        )
    }

    if (showUploadConfirm) {
        AppleSheetOrAlert(
            onDismissRequest = { if (!state.isSubmitting) showUploadConfirm = false },
            title = "确认保存物理在线作业文件？",
            confirmLabel = "保存文件",
            onConfirm = {
                showUploadConfirm = false
                showUpload = false
                scope.launch { model.submitSelectedActivity(uploadFiles) }
            },
            confirmEnabled = uploadFiles.isNotEmpty() && !state.isSubmitting,
            dismissLabel = "取消",
        ) {
            Text("将保存 ${uploadFiles.size} 个文件到“${state.selectedActivity?.title.orEmpty()}”的草稿。保存后仍可修改，核对文件后需单独点击最终提交。")
        }
    }
}

/** 紧凑端物理在线作业详情二级页；宽屏不使用此入口，仍由根列表弹出底部详情。 */
@Composable
fun PhyVlabDetailWorkspace(
    model: PhyVlabScreenModel,
    fileGateway: HomeworkFileGateway,
    onOpenActivity: (String) -> Unit,
    /** 由应用壳提供会话感知的详情重试；独立预览/测试时回退到模型重试。 */
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val retryDetail: () -> Unit = onRetry ?: fun() {
        scope.launch { model.loadSelectedActivityDetail(force = true) }
    }
    var showUpload by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showUploadConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var uploadFiles by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<HomeworkFileContent>>(emptyList()) }
    var uploadFeedback by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val nowEpochSeconds = rememberPhyVlabNowEpochSeconds()

    LaunchedEffect(model, state.selectedActivity) {
        if (state.selectedActivity != null) model.loadSelectedActivityDetail()
        showUpload = false
        showUploadConfirm = false
        uploadFiles = emptyList()
        uploadFeedback = null
    }

    val activity = state.selectedActivity
    if (activity == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未选择物理在线作业。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val selectedActivity = requireNotNull(activity)
        PhyVlabAssignmentDetailContent(
            activity = selectedActivity,
            detail = state.assignmentDetail,
            isLoading = state.isDetailLoading,
            failure = state.detailFailure,
            feedback = state.submissionFeedback,
            fileGatewayAvailable = fileGateway.isAvailable,
                fileGateway = fileGateway,
                isSubmitting = state.isSubmitting,
                onSaveFiles = { added, removed -> scope.launch { model.submitSelectedActivity(added, removed) } },
                onFinalize = { scope.launch { model.submitSelectedActivity(emptyList(), finalize = true, filesChecked = true) } },
                onPrepareFinalization = model::prepareFinalization,
                finalizationStatement = state.finalizationStatement,
                submissionRevision = state.submissionRevision,
            nowEpochSeconds = nowEpochSeconds,
            fullScreen = true,
            modifier = modifier,
            onRetry = retryDetail,
            onUpload = {
                uploadFiles = emptyList()
                uploadFeedback = null
                showUpload = true
            },
            onOpenWeb = { onOpenActivity(selectedActivity.activityUrl) },
        )
    }

    if (showUpload) {
        PhyVlabUploadDialog(
            files = uploadFiles,
            feedback = uploadFeedback,
            isSubmitting = state.isSubmitting,
            onPickFiles = {
                scope.launch {
                    when (val result = fileGateway.pickFiles()) {
                        HomeworkFilePickResult.Cancelled -> Unit
                        is HomeworkFilePickResult.Failed -> uploadFeedback = "无法读取所选文件，请重新选择。"
                        is HomeworkFilePickResult.Selected -> {
                            uploadFiles = (uploadFiles + result.files).distinctBy { it.fileName }
                            uploadFeedback = null
                        }
                    }
                }
            },
            onRemoveFile = { index -> uploadFiles = uploadFiles.filterIndexed { i, _ -> i != index } },
            onSubmit = { showUploadConfirm = true },
            onDismiss = { if (!state.isSubmitting) showUpload = false },
        )
    }

    if (showUploadConfirm) {
        AppleSheetOrAlert(
            onDismissRequest = { if (!state.isSubmitting) showUploadConfirm = false },
            title = "确认保存物理在线作业文件？",
            confirmLabel = "保存文件",
            onConfirm = {
                showUploadConfirm = false
                showUpload = false
                scope.launch { model.submitSelectedActivity(uploadFiles) }
            },
            confirmEnabled = uploadFiles.isNotEmpty() && !state.isSubmitting,
            dismissLabel = "取消",
        ) {
            Text("将保存 ${uploadFiles.size} 个文件到“${activity?.title.orEmpty()}”的草稿。保存后仍可修改，核对文件后需单独点击最终提交。")
        }
    }
}

@Composable
private fun PhyVlabCourseRow(course: PhyVlabCourse, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                if (course.category.isNotBlank()) {
                    Text(course.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (course.progressPercent > 0) {
                    Text("进度 ${course.progressPercent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

internal fun filterPhyVlabActivities(activities: List<PhyVlabActivity>, filters: AssignmentFilters, now: Long): List<PhyVlabActivity> {
    val visible = activities.filter { (filters.courses.isEmpty() || it.courseId in filters.courses) &&
        (!filters.hideSubmitted || !it.completed) && (!filters.hideExpired || it.dueTimestamp?.let { due -> due > now } != false) }
    return when (filters.sortOrder) {
        1 -> visible.sortedBy { it.dueTimestamp ?: Long.MAX_VALUE }
        2 -> visible.sortedByDescending { it.dueTimestamp ?: Long.MIN_VALUE }
        else -> visible
    }
}

@Composable
internal fun PhyVlabActivityRow(activity: PhyVlabActivity, nowEpochSeconds: Long, onOpen: () -> Unit, source: AssignmentSource? = null) {
    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            AssignmentCardHeader(activity.courseName, activity.title, activity.completed, source)
            Text("类型 · ${activity.activityType}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            activity.openText?.let { Text("开始 · ${formatPhyVlabDateTime(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            activity.dueText?.let { Text("截止 · ${formatPhyVlabDateTime(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private data class PhyVlabStatusPalette(
    val container: Color,
    val content: Color,
)

@Composable
private fun phyVlabActivityStatusPalette(
    state: PhyVlabActivityDeadlineState,
): PhyVlabStatusPalette {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (state) {
        PhyVlabActivityDeadlineState.SUBMITTED -> PhyVlabStatusPalette(
            container = if (darkTheme) Color(0xFF174D2A) else Color(0xFFD9F2DF),
            content = if (darkTheme) Color(0xFF9BE7AA) else Color(0xFF1C6B35),
        )
        PhyVlabActivityDeadlineState.LATE_SUBMITTED -> PhyVlabStatusPalette(
            container = if (darkTheme) Color(0xFF690005) else Color(0xFFFFDAD6),
            content = if (darkTheme) Color(0xFFFFB4AB) else Color(0xFF8C1D18),
        )
        PhyVlabActivityDeadlineState.DUE_SOON -> PhyVlabStatusPalette(
            container = if (darkTheme) Color(0xFF5B4500) else Color(0xFFFFEFC2),
            content = if (darkTheme) Color(0xFFFFD66B) else Color(0xFF7A4F00),
        )
        PhyVlabActivityDeadlineState.OVERDUE -> PhyVlabStatusPalette(
            container = if (darkTheme) Color(0xFF690005) else Color(0xFFFFDAD6),
            content = if (darkTheme) Color(0xFFFFB4AB) else Color(0xFF8C1D18),
        )
        PhyVlabActivityDeadlineState.UNKNOWN -> PhyVlabStatusPalette(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun phyVlabActivityStatusLabel(state: PhyVlabActivityDeadlineState): String = when (state) {
    PhyVlabActivityDeadlineState.SUBMITTED -> "已完成"
    PhyVlabActivityDeadlineState.LATE_SUBMITTED -> "逾期提交"
    PhyVlabActivityDeadlineState.DUE_SOON -> "未完成"
    PhyVlabActivityDeadlineState.OVERDUE -> "逾期未交"
    PhyVlabActivityDeadlineState.UNKNOWN -> "未完成"
}

private fun phyVlabActivityStatusDescription(state: PhyVlabActivityDeadlineState): String = when (state) {
    PhyVlabActivityDeadlineState.SUBMITTED -> "作业已完成"
    PhyVlabActivityDeadlineState.LATE_SUBMITTED -> "作业已提交，但提交时间晚于截止时间"
    PhyVlabActivityDeadlineState.DUE_SOON -> "作业未完成，尚未到截止时间"
    PhyVlabActivityDeadlineState.OVERDUE -> "作业已逾期且未完成"
    PhyVlabActivityDeadlineState.UNKNOWN -> "作业未完成，截止时间未知"
}

@Composable
private fun rememberPhyVlabNowEpochSeconds(): Long {
    var nowEpochSeconds by remember { mutableStateOf(Clock.System.now().epochSeconds) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowEpochSeconds = Clock.System.now().epochSeconds
        }
    }
    return nowEpochSeconds
}

@Composable
private fun PhyVlabTimeRow(
    label: String,
    value: String,
    valueColor: Color = LocalContentColor.current,
    valueFontWeight: FontWeight = FontWeight.Normal,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = valueFontWeight,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PhyVlabSortIcon(descending: Boolean) {
    val tint = MaterialTheme.colorScheme.onSecondaryContainer
    Canvas(Modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        val left = 2.dp.toPx()
        val barTop = 4.dp.toPx()
        val barGap = 6.dp.toPx()
        listOf(11.dp, 8.dp, 5.dp).forEachIndexed { index, length ->
            val y = barTop + index * barGap
            drawLine(
                color = tint,
                start = Offset(left, y),
                end = Offset(left + length.toPx(), y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        val arrowX = size.width - 4.dp.toPx()
        val arrowTop = 4.dp.toPx()
        val arrowBottom = size.height - 4.dp.toPx()
        if (descending) {
            drawLine(tint, Offset(arrowX, arrowTop), Offset(arrowX, arrowBottom), stroke, StrokeCap.Round)
            drawLine(tint, Offset(arrowX - 3.dp.toPx(), arrowBottom - 3.dp.toPx()), Offset(arrowX, arrowBottom), stroke, StrokeCap.Round)
            drawLine(tint, Offset(arrowX + 3.dp.toPx(), arrowBottom - 3.dp.toPx()), Offset(arrowX, arrowBottom), stroke, StrokeCap.Round)
        } else {
            drawLine(tint, Offset(arrowX, arrowBottom), Offset(arrowX, arrowTop), stroke, StrokeCap.Round)
            drawLine(tint, Offset(arrowX - 3.dp.toPx(), arrowTop + 3.dp.toPx()), Offset(arrowX, arrowTop), stroke, StrokeCap.Round)
            drawLine(tint, Offset(arrowX + 3.dp.toPx(), arrowTop + 3.dp.toPx()), Offset(arrowX, arrowTop), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun PhyVlabAssignmentDetailContent(
    activity: PhyVlabActivity,
    detail: team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabAssignmentDetail?,
    isLoading: Boolean,
    failure: PhyVlabSyncFailure?,
    feedback: String?,
    fileGatewayAvailable: Boolean,
    nowEpochSeconds: Long,
    onRetry: () -> Unit,
    onUpload: () -> Unit,
    onOpenWeb: () -> Unit,
    fullScreen: Boolean = false,
    modifier: Modifier = Modifier,
    fileGateway: HomeworkFileGateway = team.bjtuss.bjtuselfservice.shared.files.UnavailableHomeworkFileGateway,
    isSubmitting: Boolean = false,
    onSaveFiles: (List<HomeworkFileContent>, Set<String>) -> Unit = { _, _ -> onUpload() },
    onFinalize: () -> Unit = {},
    onPrepareFinalization: suspend () -> Boolean = { true },
    finalizationStatement: String? = null,
    submissionRevision: Long = 0,
) {
    var confirmFinal by remember(activity.id) { mutableStateOf(false) }


    var showFileEditor by remember(activity.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    // 真实偏移上报给壳层算玻璃浓度；只在全屏路由页生效（sheet 里复用不受影响）。
    val openedAt = activity.openText?.let(::formatPhyVlabDateTime)
    val dueAt = activity.dueText?.let(::formatPhyVlabDateTime)
    val submittedAt = detail?.submissionDateText?.let(::formatPhyVlabDateTime)
    val submitted = detail?.let { !it.isDraft && phyVlabAssignmentDetailHasSubmission(it) } ?: activity.completed
    val submissionTiming = phyVlabSubmissionTimingLabel(activity, detail)
    val deadlineState = phyVlabActivityDeadlineState(
        activity = activity,
        nowEpochSeconds = nowEpochSeconds,
        submitted = submitted,
        submittedAtEpochSeconds = detail?.submissionDateTimestamp,
    )
    TopScrollColumn(
        state = scrollState,
        enabled = fullScreen,
        modifier = modifier
            .then(if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().heightIn(max = 680.dp))
            .desktopTouchScroll(scrollState),
        contentModifier = Modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            // 原生栏 underlap 时视口顶边贴屏幕顶，首项靠这份顶边距让开。
            // 只在全屏路由页生效：sheet 里的复用不受影响（且 sheet 路径本就没有 clearance）。
            .padding(top = if (fullScreen) LocalTopBarClearance.current else 0.dp)
            .padding(bottom = if (fullScreen) LocalBottomBarClearance.current else 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(activity.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(activity.courseName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        if (openedAt != null || dueAt != null || submittedAt != null || detail != null) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // macOS 的 ModalBottomSheet 有约 640dp 的最大内容宽度；扣除 sheet 内边距后，
                // 约 560dp 已足够让两块各自拥有舒适的半宽。手机内容宽度会自然落入单列。
                val splitSections = maxWidth >= 560.dp && detail != null &&
                    (openedAt != null || dueAt != null || submittedAt != null)
                if (splitSections) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        PhyVlabTimeDetailSection(
                            openedAt = openedAt,
                            dueAt = dueAt,
                            submittedAt = submittedAt,
                            submitted = submitted,
                            submissionTiming = submissionTiming,
                            deadlineState = deadlineState,
                            modifier = Modifier.weight(1f),
                        )
                        PhyVlabSubmissionDetailSection(
                            page = detail,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (openedAt != null || dueAt != null || submittedAt != null) {
                            PhyVlabTimeDetailSection(
                                openedAt = openedAt,
                                dueAt = dueAt,
                                submittedAt = submittedAt,
                                submitted = submitted,
                                submissionTiming = submissionTiming,
                                deadlineState = deadlineState,
                            )
                        }
                        detail?.let { page ->
                            PhyVlabSubmissionDetailSection(page = page)
                        }
                    }
                }
            }
        }
        feedback?.let {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (isSubmitting) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                Text(
                    if (detail == null) {
                        "正在读取提交与批改状态…"
                    } else {
                        "正在更新提交与批改状态…"
                    },
                )
            }
        }
        failure?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (it) {
                            PhyVlabSyncFailure.NETWORK -> "详情读取失败，请检查网络。"
                            PhyVlabSyncFailure.PARSE -> "详情页面结构变化，暂时无法读取。"
                            PhyVlabSyncFailure.SESSION_EXPIRED -> "物理在线会话已失效，请点击右上角刷新重试登录。"
                        },
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
        detail?.let { page ->
            if (page.description.isNotBlank()) {
                PhyVlabDetailSection(
                    title = "作业要求",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text(page.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (page.submittedFiles.isNotEmpty()) {
                PhyVlabDetailSection(
                    title = if (page.isDraft || page.canFinalize) "已保存文件（草稿，尚未最终提交）" else "已提交文件",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    page.submittedFiles.forEach { file ->
                        Text("• " + file.fileName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (page.canSubmit) {
                Button(onClick = { showFileEditor = true }, enabled = fileGatewayAvailable && !isSubmitting,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (fileGatewayAvailable) "上传作业" else "当前平台未接入文件选择器")
                }
            }
            if (page.canFinalize) {
                FilledTonalButton(onClick = { scope.launch { if (onPrepareFinalization()) { confirmFinal = true } } }, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) { Text("最终提交") }
            }
        }
        FilledTonalButton(onClick = onOpenWeb, modifier = Modifier.fillMaxWidth()) {
            Text("在网页中打开")
        }
    }
    LaunchedEffect(submissionRevision) { showFileEditor = false }
    if (showFileEditor && detail != null) AppleSheet(onDismissRequest = { if (!isSubmitting) showFileEditor = false },
        title = "上传作业", needsFullHeight = true, scrollableBody = true) {
        val uploadScroll = rememberScrollState()
        Column(Modifier.fillMaxWidth().verticalScroll(uploadScroll).desktopTouchScroll(uploadScroll).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            team.bjtuss.bjtuselfservice.shared.feature.assignment.AssignmentFilesEditor(
                detail.submittedFiles.map { it.fileName }, fileGateway, isSubmitting,
                "先保存文件并检查，再最终提交。最终提交后不能修改文件。",
                "保存文件", onSave = onSaveFiles, revision = submissionRevision)
            feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
    if (confirmFinal) PhyVlabFinalizationDialog(
        title = activity.title,
        fileNames = detail?.submittedFiles?.map { it.fileName }.orEmpty(),
        statement = finalizationStatement,
        isSubmitting = isSubmitting,
        onDismiss = { confirmFinal = false },
        onConfirm = { confirmFinal = false; onFinalize() },
    )
}

@Composable
internal fun PhyVlabFinalizationDialog(title: String, fileNames: List<String>, statement: String?,
    isSubmitting: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var filesChecked by remember(title, fileNames) { mutableStateOf(false) }
    var statementAccepted by remember(title, statement) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("检查文件并最终提交？") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("最终提交后不能修改、替换或删除文件。取消后仍可返回修改草稿。")
                Text(title, fontWeight = FontWeight.SemiBold)
                Text("将最终提交以下 ${fileNames.size} 个文件：")
                fileNames.forEach { Text("• $it") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(filesChecked, { filesChecked = it }, enabled = !isSubmitting)
                    Text("我已核对全部文件，确认提交后不能再修改")
                }
                statement?.let {
                    Text(it)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(statementAccepted, { statementAccepted = it }, enabled = !isSubmitting)
                        Text("我已阅读并同意上述声明")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSubmitting && fileNames.isNotEmpty() && filesChecked &&
                (statement == null || statementAccepted)) { Text("确认最终提交") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("返回检查") } },
    )
}

@Composable
private fun PhyVlabTimeDetailSection(
    openedAt: String?,
    dueAt: String?,
    submittedAt: String?,
    submitted: Boolean,
    submissionTiming: String?,
    deadlineState: PhyVlabActivityDeadlineState,
    modifier: Modifier = Modifier,
) {
    val statusPalette = phyVlabActivityStatusPalette(deadlineState)
    PhyVlabDetailSection(
        modifier = modifier,
        title = "时间",
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        openedAt?.let { PhyVlabDetailLine("开放", it) }
        dueAt?.let {
            PhyVlabDetailLine(
                label = "截止",
                value = if (submitted) it else "$it（未提交）",
                valueColor = statusPalette.content,
                valueFontWeight = if (
                    deadlineState == PhyVlabActivityDeadlineState.OVERDUE ||
                    deadlineState == PhyVlabActivityDeadlineState.LATE_SUBMITTED
                ) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
            )
        }
        submittedAt?.let {
            PhyVlabDetailLine(
                label = "提交",
                value = buildString {
                    append(it)
                    submissionTiming?.let { timing ->
                        append("（")
                        append(timing)
                        append("）")
                    }
                },
                valueColor = statusPalette.content,
                valueFontWeight = if (deadlineState == PhyVlabActivityDeadlineState.LATE_SUBMITTED) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
            )
        }
    }
}

@Composable
private fun PhyVlabSubmissionDetailSection(
    page: team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabAssignmentDetail,
    modifier: Modifier = Modifier,
) {
    PhyVlabDetailSection(
        modifier = modifier,
        title = "提交与批改",
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        PhyVlabDetailLine("提交状态", phyVlabSubmissionStatusLabel(page))
        page.gradingStatus?.let { PhyVlabDetailLine("批改状态", it) }
        PhyVlabDetailLine("批改成绩", page.gradeText?.ifBlank { "未批改" } ?: "未批改")
        if (!page.feedbackText.isNullOrBlank()) {
            PhyVlabDetailLine("教师评语", page.feedbackText)
        }
    }
}

@Composable
private fun PhyVlabDetailSection(
    modifier: Modifier = Modifier,
    title: String,
    containerColor: Color,
    contentColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                content()
            },
        )
    }
}

@Composable
private fun PhyVlabDetailLine(
    label: String,
    value: String,
    valueColor: Color = LocalContentColor.current,
    valueFontWeight: FontWeight = FontWeight.Normal,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(alpha = 0.72f))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val baseStyle = MaterialTheme.typography.bodyLarge
            val maxFontSize = baseStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp
            val minFontSize = 13.sp
            var fontSize by remember(value, maxWidth, maxFontSize) {
                androidx.compose.runtime.mutableStateOf(maxFontSize)
            }
            Text(
                value,
                style = baseStyle.copy(fontSize = fontSize, fontWeight = valueFontWeight),
                color = valueColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                onTextLayout = { result ->
                    if (result.hasVisualOverflow && fontSize > minFontSize) {
                        val next = (fontSize.value - 1f).coerceAtLeast(minFontSize.value).sp
                        if (next != fontSize) fontSize = next
                    }
                },
            )
        }
    }
}

private val phyVlabUiDatePattern = Regex(
    """(\d{4})年(\d{1,2})月(\d{1,2})日\s+(\d{1,2}):(\d{2})""",
)

private fun formatPhyVlabDateTime(value: String): String {
    val match = phyVlabUiDatePattern.find(value) ?: return value
    val (year, month, day, hour, minute) = match.destructured
    val date = runCatching {
        LocalDate(year.toInt(), month.toInt(), day.toInt())
    }.getOrNull() ?: return value
    val normalized = buildString {
        append(year)
        append("年")
        append(month.padStart(2, '0'))
        append("月")
        append(day.padStart(2, '0'))
        append("日 ")
        append(hour.padStart(2, '0'))
        append(":")
        append(minute.padStart(2, '0'))
    }
    return normalized + " · " + date.dayOfWeek.chineseLabel()
}

private fun kotlinx.datetime.DayOfWeek.chineseLabel(): String = when (this) {
    kotlinx.datetime.DayOfWeek.MONDAY -> "周一"
    kotlinx.datetime.DayOfWeek.TUESDAY -> "周二"
    kotlinx.datetime.DayOfWeek.WEDNESDAY -> "周三"
    kotlinx.datetime.DayOfWeek.THURSDAY -> "周四"
    kotlinx.datetime.DayOfWeek.FRIDAY -> "周五"
    kotlinx.datetime.DayOfWeek.SATURDAY -> "周六"
    kotlinx.datetime.DayOfWeek.SUNDAY -> "周日"
}

@Composable
private fun PhyVlabUploadDialog(
    files: List<HomeworkFileContent>,
    feedback: String?,
    isSubmitting: Boolean,
    onPickFiles: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    AppleSheetOrAlert(
        onDismissRequest = onDismiss,
        title = "上传物理在线作业",
        confirmLabel = if (isSubmitting) "提交中" else "继续",
        onConfirm = onSubmit,
        confirmEnabled = files.isNotEmpty() && !isSubmitting,
        dismissLabel = "取消",
        dismissEnabled = !isSubmitting,
        needsFullHeight = true,
    ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(scrollState)
                    .desktopTouchScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("先选择文件，确认后才会发送到物理在线。", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onPickFiles, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Text(if (files.isEmpty()) "选择文件" else "继续添加文件")
                }
                if (files.isEmpty()) {
                    Text("尚未选择文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    files.forEachIndexed { index, file ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(file.fileName, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { onRemoveFile(index) }, enabled = !isSubmitting) { Text("移除") }
                        }
                    }
                }
                feedback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
    }
}

@Composable
private fun PhyVlabFailureBanner(
    failure: PhyVlabSyncFailure,
    hasCachedContent: Boolean,
    cachedAtEpochMillis: Long?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "失败原因：${when (failure) {
                        PhyVlabSyncFailure.NETWORK -> "网络问题。请确认已连接到校园网。"
                        PhyVlabSyncFailure.PARSE -> "页面结构变化，暂时无法读取。"
                        PhyVlabSyncFailure.SESSION_EXPIRED -> "会话失效，请点击右上角刷新重试登录。"
                    }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "仅校园网下同步",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (hasCachedContent) {
                    Text(
                        text = "当前显示本地缓存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = "缓存创建时间：${formatPhyVlabCacheTime(cachedAtEpochMillis) ?: "未记录"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

private fun formatPhyVlabCacheTime(epochMillis: Long?): String? {
    val dateTime = epochMillis?.let {
        runCatching {
            Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.of("Asia/Shanghai"))
        }.getOrNull()
    } ?: return null
    return buildString {
        append(dateTime.year)
        append("年")
        append((dateTime.month.ordinal + 1).toString().padStart(2, '0'))
        append("月")
        append(dateTime.day.toString().padStart(2, '0'))
        append("日 ")
        append(dateTime.hour.toString().padStart(2, '0'))
        append(":")
        append(dateTime.minute.toString().padStart(2, '0'))
    }
}

@Composable
private fun PhyVlabEmptyState(onRetry: () -> Unit, onOpenWeb: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("暂未找到物理在线课程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "课程和作业会在这里原生显示；如果尚未选课，可打开网页版完成选课。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = onRetry) { Text("重新同步") }
            TextButton(onClick = onOpenWeb) { Text("打开网页版选课") }
        }
    }
}

@Composable
private fun PhyVlabCasLoginRequiredState(onRetry: () -> Unit, onLogout: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("需要完成统一身份认证", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "物理在线需要单独建立 Moodle 会话。App 会先尝试复用当前统一身份认证；" +
                    "如果仍未成功，请点击右上角刷新再次尝试。不会自动跳转浏览器。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = onRetry) { Text("重新建立认证") }
            TextButton(onClick = onLogout) { Text("退出并重新登录") }
        }
    }
}

/**
 * 课程作业的默认顺序是“新的在上面”：Moodle 活动 ID 是当前课程页唯一且
 * 单调的活动顺序标识，直接反转解析器原本的正序，避免老师调整截止日期后
 * 把“新旧”误判成“截止日期先后”。
 */
internal fun orderPhyVlabActivities(
    activities: List<PhyVlabActivity>,
    descending: Boolean,
): List<PhyVlabActivity> {
    val ordered = activities.sortedWith(
        compareBy<PhyVlabActivity> { it.id }.thenBy { it.title },
    )
    return if (descending) ordered.asReversed() else ordered
}

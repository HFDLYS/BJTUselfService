package team.bjtuss.bjtuselfservice.shared.feature.citel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalTopBarClearance
import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalBottomBarClearance
import team.bjtuss.bjtuselfservice.shared.feature.shell.TopScrollLazyColumn
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheet
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll
import team.bjtuss.bjtuselfservice.shared.feature.assignment.*
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkSummaryBanner
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppErrorBanner
import team.bjtuss.bjtuselfservice.shared.feature.shell.TopScrollColumn
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.UnavailableHomeworkFileGateway

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CitelWorkspace(model: CitelModel, holdNetwork: Boolean, onOpen: (String) -> Unit, modifier: Modifier = Modifier,
    fileGateway: HomeworkFileGateway = UnavailableHomeworkFileGateway,
    showDetailSheet: Boolean = true, onOpenTask: ((CitelTask) -> Unit)? = null) {
    val state by model.state.collectAsState()
    val filters = state.filters
    val pendingOnly = filters.hideSubmitted
    val hideExpired = filters.hideExpired
    val selectedCourses = filters.courses
    val sortOrder = filters.sortOrder
    var showFilters by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(Clock.System.now().epochSeconds) }
    LaunchedEffect(model, holdNetwork) {
        model.initialize()
        if (!holdNetwork && model.state.value.enabled) model.refreshForAppEntry(0)
    }
    LaunchedEffect(Unit) { while (true) { now = Clock.System.now().epochSeconds; delay(30_000) } }
    val scroll = rememberLazyListState()
    val tasks = filteredCitelTasks(state.tasks.filter { selectedCourses.isEmpty() || it.courseId in selectedCourses }, pendingOnly, hideExpired, null, sortOrder, now)
    val topClearance = LocalTopBarClearance.current
    val bottomClearance = LocalBottomBarClearance.current
    val dueSoon = tasks.count { !it.completed && it.dueTime?.let { due -> due in now..(now + 48 * 3600) } == true }
    val subtitle = (if (dueSoon > 0) "未来 48 小时内有 $dueSoon 项未提交" else "未来 48 小时内暂无临近截止项") +
        if (filters.active) " · 已筛选" else ""
    LaunchedEffect(filters) { scroll.scrollToItem(0) }
    TopScrollLazyColumn(state = scroll, modifier = modifier.fillMaxSize().desktopTouchScroll(scroll),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp + topClearance,
            bottom = 24.dp + bottomClearance), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.message?.takeIf { state.failed }?.let { message ->
            item { AppErrorBanner(message, onRetry = { scope.launch { model.refresh() } }) }
        }
        item {
            HomeworkSummaryBanner(tasks.size, subtitle, onOpenFilter = { showFilters = true })
        }
        if (tasks.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when { state.refreshing -> "正在读取作业…"; state.tasks.isNotEmpty() -> "当前筛选下没有作业"; else -> "暂无作业" },
                    style = MaterialTheme.typography.titleMedium)
                if (state.tasks.isNotEmpty()) TextButton(onClick = { showFilters = true }) { Text("调整筛选") }
                else TextButton(onClick = { scope.launch { model.refresh() } }, enabled = !state.refreshing) { Text("刷新") }
            }
        }
        items(tasks, key = { it.id }) { task ->
            CitelTaskCard(task, onOpen = { model.showTask(task); onOpenTask?.invoke(task) })
        }
    }
    if (showDetailSheet) state.selectedTask?.let {
        AppleSheet(onDismissRequest = model::dismissTask, title = "作业详情", needsFullHeight = true, scrollableBody = true) {
            CitelDetailWorkspace(model, fileGateway, onOpen, holdNetwork)
        }
    }
    if (showFilters) AppleSheet(onDismissRequest = { showFilters = false }, title = "作业筛选", needsFullHeight = true, scrollableBody = true) {
        AssignmentFilterSheet(state.tasks.distinctBy { it.courseId }.map { it.courseId to it.courseName }, filters, model::updateFilters)
    }

}

internal fun filteredCitelTasks(tasks: List<CitelTask>, pendingOnly: Boolean, hideExpired: Boolean,
    courseId: Int?, sortOrder: Int, now: Long): List<CitelTask> {
    val filtered = tasks.filter { (!pendingOnly || !it.completed) &&
        (!hideExpired || it.dueTime?.let { due -> due > now } != false) && (courseId == null || it.courseId == courseId) }
    return when (sortOrder) {
        1 -> filtered.sortedBy { it.dueTime ?: Long.MAX_VALUE }
        2 -> filtered.sortedByDescending { it.dueTime ?: Long.MIN_VALUE }
        else -> filtered
    }
}

@Composable
internal fun CitelTaskCard(task: CitelTask, onOpen: () -> Unit, source: AssignmentSource? = null) {
    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            AssignmentCardHeader(task.courseName, task.title, task.completed, source, statusLabel = task.cardStatusText)
            CitelTaskLine("类型", if (task.programming) "编程作业" else "实验报告")
            if (task.programming) CitelTaskLine("评测", task.submissionStatusText())
            task.openTime?.let { CitelTaskLine("开始", citelDateText(it)) }
            task.discountTime?.let { CitelTaskLine("折扣", citelDateText(it) + (task.discount?.let { factor -> " · ×$factor" } ?: "")) }
            CitelTaskLine("截止", task.dueTime?.let(::citelDateText) ?: "未提供")
            task.grade?.let { CitelTaskLine(if (task.programming) "题目分值" else "评分", it) }
        }
    }
}

@Composable
private fun CitelTaskLine(label: String, value: String) {
    Text("$label · $value", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun CitelDetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CitelDetailWorkspace(model: CitelModel, fileGateway: HomeworkFileGateway, onOpen: (String) -> Unit,
    holdNetwork: Boolean = false, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val task = state.selectedTask ?: run { Text("未选择作业。"); return }
    var showUpload by remember(task.id) { mutableStateOf(false) }
    var now by remember { mutableStateOf(Clock.System.now().epochSeconds) }
    val result = state.programmingResult
    val accepted = result?.accepted ?: isCitelAccepted(task.status)
    val testing = result?.testing ?: isCitelTesting(task.status)
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(task.id, testing, holdNetwork, windowFocused, state.programmingPollingPaused) {
        if (holdNetwork || !windowFocused || !testing || state.programmingPollingPaused) return@LaunchedEffect
        while (model.state.value.selectedTask?.id == task.id && model.state.value.programmingResult?.testing == true) {
            delay(3_000)
            if (!model.refreshProgrammingResult(task.id)) break
        }
    }
    LaunchedEffect(task.id, holdNetwork) { if (!holdNetwork) model.selectTask(task) }
    LaunchedEffect(Unit) { while (true) { now = Clock.System.now().epochSeconds; delay(30_000) } }
    val detailScroll = androidx.compose.foundation.rememberScrollState()
    TopScrollColumn(state = detailScroll, modifier = modifier.fillMaxSize().desktopTouchScroll(detailScroll),
        contentModifier = Modifier.padding(horizontal = 24.dp)
            .padding(top = 12.dp + LocalTopBarClearance.current, bottom = 28.dp + LocalBottomBarClearance.current),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(task.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        CitelDetailLine("课程", task.courseName)
        CitelDetailLine("类型", if (task.programming) "编程作业" else "实验报告")
        CitelDetailLine("开放时间", task.openTime?.let(::citelDateText) ?: "未提供")
        task.discountTime?.let { CitelDetailLine("折扣开始", citelDateText(it) + (task.discount?.let { factor -> " · ×$factor" } ?: "")) }
        CitelDetailLine("截止时间", task.dueTime?.let(::citelDateText) ?: "未提供")
        CitelDetailLine("提交状态", result?.displayStatus ?: state.submission?.status ?: task.submissionStatusText())
        if (task.programming) {
            if (result != null) ProgrammingJudgeResult(result, autoRefreshing = testing && !holdNetwork && windowFocused && !state.programmingPollingPaused)
            else if (accepted) Text("已通过（AC），无需再次提交。", color = MaterialTheme.colorScheme.primary)
        }
        task.grade?.let { CitelDetailLine(if (task.programming) "题目分值" else "评分", it) }
        if (!task.completed) Text(task.deadlineStatus(now))
        state.submissionMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (state.submissionBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.programmingOptions != null && !accepted && !testing) {
            Button(onClick = { showUpload = true }, enabled = !state.submissionBusy && fileGateway.isAvailable,
                modifier = Modifier.fillMaxWidth()) { Text("上传代码") }
        }
        state.submission?.let { status ->
            if (status.files.isNotEmpty()) {
                Text("已提交文件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                status.files.forEach { file -> Text(file.name, style = MaterialTheme.typography.bodyMedium) }
            }
            if (status.editable) Button(onClick = { showUpload = true }, enabled = !state.submissionBusy && fileGateway.isAvailable,
                modifier = Modifier.fillMaxWidth()) { Text(if (status.files.isEmpty()) "上传作业" else "管理提交文件") }
            else Text("当前作业不允许修改。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { scope.launch { model.selectTask(task) } }, enabled = !state.submissionBusy) { Text("刷新提交状态") }
        FilledTonalButton(onClick = { onOpen(task.url) }, enabled = !state.submissionBusy, modifier = Modifier.fillMaxWidth()) { Text("在网页中打开") }
    }
    LaunchedEffect(state.submissionRevision) { showUpload = false }
    LaunchedEffect(accepted, testing) { if (accepted || testing) showUpload = false }
    if (showUpload) AppleSheet(onDismissRequest = { if (!state.submissionBusy) showUpload = false },
        title = if (task.programming) "上传代码" else "上传作业", needsFullHeight = true, scrollableBody = true) {
        val uploadScroll = androidx.compose.foundation.rememberScrollState()
        Column(Modifier.fillMaxWidth().verticalScroll(uploadScroll).desktopTouchScroll(uploadScroll).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.programmingOptions?.let { options ->
                var language by remember(task.id, options) { mutableStateOf(options.languages.keys.first()) }
                Text("编程语言", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.languages.forEach { (value, label) ->
                        FilterChip(language == value, { language = value }, enabled = !state.submissionBusy, label = { Text(label) })
                    }
                }
                AssignmentFilesEditor(emptyList(), fileGateway, state.submissionBusy,
                    "选择一个代码文件提交评测，平台以最新一次提交为准。", "提交代码",
                    onSave = { files, _ -> scope.launch { model.submitProgramming(files, language) } }, revision = state.submissionRevision)
            }
            state.submission?.let { status ->
                AssignmentFilesEditor(status.files.map { it.name }, fileGateway, state.submissionBusy,
                    "保存后即已提交，截止前可以添加、替换或移除文件。", "保存文件",
                    onSave = { added, removed -> scope.launch { model.saveFiles(added, removed) } }, revision = state.submissionRevision)
            }
            state.submissionMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
internal fun ProgrammingJudgeResult(result: CitelProgrammingResult, autoRefreshing: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            result.accepted -> Text("已通过（AC），无需再次提交。", color = MaterialTheme.colorScheme.primary)
            result.testing -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (autoRefreshing) "正在测试，每 3 秒自动刷新评测结果。" else "正在测试，自动刷新已暂停。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        result.totalTests?.let { total ->
            Text("测试用例：${result.passedTests ?: 0} / $total 通过" +
                (result.failedTests?.let { " · $it 未通过" } ?: ""), fontWeight = FontWeight.SemiBold)
        }
        if (result.totalTests == null && result.testCases.isEmpty() && !result.testing) {
            Text("平台尚未提供测试用例结果。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        result.testCases.forEach { test ->
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("测试用例 ${test.number} · ${when (test.passed) { true -> "通过"; false -> "未通过"; null -> "待判定" }}",
                        fontWeight = FontWeight.Medium)
                    Text(test.result.ifBlank { "暂无判题结果" }, style = MaterialTheme.typography.bodySmall)
                    if (test.timeUsed.isNotBlank() || test.memoryUsed.isNotBlank()) Text(
                        listOfNotNull(test.timeUsed.takeIf { it.isNotBlank() }?.let { "耗时 $it 秒" },
                            test.memoryUsed.takeIf { it.isNotBlank() }?.let { "内存 $it" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

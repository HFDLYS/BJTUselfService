package team.bjtuss.bjtuselfservice.shared.feature.shell


import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import team.bjtuss.bjtuselfservice.shared.LocalReduceMotion
import team.bjtuss.bjtuselfservice.shared.PlatformInfo
import team.bjtuss.bjtuselfservice.shared.WindowClass
import kotlin.math.PI
import team.bjtuss.bjtuselfservice.shared.accessibleAlpha
import team.bjtuss.bjtuselfservice.shared.data.grade.formatGradeDetailForDisplay
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppErrorBanner
import team.bjtuss.bjtuselfservice.shared.usesLegacySmartTransportFor
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.data.grade.GradeSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.home.HomeStatusFailure
import team.bjtuss.bjtuselfservice.shared.data.home.HomeChangeFeedRepository
import team.bjtuss.bjtuselfservice.shared.data.homework.HomeworkSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.courseware.CoursewareSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.exam.ExamScheduleSyncFailure
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.ClassroomOccupancySyncFailure
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleContentSource
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleContentSource
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkContentSource
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkDetailWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareContentSource
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.OtherFunctionScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.SCHOOL_CALENDAR_ARTICLE_URL
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.SchoolCalendarArticleWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.ReportCardDownloadWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomBuildingState
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomBuildingWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomUiState
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.classroomoccupancy.ClassroomOccupancyBuildingWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.classroomoccupancy.ClassroomOccupancyQueryState
import team.bjtuss.bjtuselfservice.shared.feature.classroomoccupancy.ClassroomOccupancyWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.settings.AppUpdateResultDialog
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxUiState
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxFailure
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxComposeScreen
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabDetailWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabContentSource
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSyncFailure
import team.bjtuss.bjtuselfservice.shared.feature.shell.SessionRefreshCoordinator
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeWorkspace
import team.bjtuss.bjtuselfservice.shared.feature.home.homeIdleStatusText
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeSyncItem
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeSyncItemState
import team.bjtuss.bjtuselfservice.shared.feature.home.homeSyncDialogTitle
import team.bjtuss.bjtuselfservice.shared.webview.openExternalUrl
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppCommand
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppCommandBus
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeInfoResult
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSortOrder
import team.bjtuss.bjtuselfservice.shared.domain.grade.displayCourseName
import team.bjtuss.bjtuselfservice.shared.domain.grade.displayName
import team.bjtuss.bjtuselfservice.shared.domain.grade.scoreForSorting
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeKind
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeDomain
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeRecord

/**
 * 紧凑顶栏。
 *
 * 内容区固定高度，避免「作业」有同步胶囊、「更多」无胶囊时标题上下漂。
 * 一级 tab 无返回：标题左缘统一 20.dp；二级页有返回时标题跟在箭头后。
 */
@Composable
internal fun CompactAppTopBar(
    title: String,
    isRefreshing: Boolean,
    isLoggingIn: Boolean = false,
    /** 非登录/非刷新时右上角文案（课表等页的「已同步/未同步」）；其它页保持 null。 */
    idleStatusText: String? = null,
    /** 非空时在状态文案旁显示刷新按钮（替代下拉刷新）。 */
    onRefresh: (() -> Unit)? = null,
    /** 状态胶囊的附加动作；通常用于查看聚合同步失败详情。 */
    onStatusClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    leadingAction: (@Composable () -> Unit)? = null,
    titleAction: (@Composable () -> Unit)? = null,
    statusAsText: Boolean = false,
    onBack: (() -> Unit)? = null,
    /** 教室查询详情把标题压矮，把高度留给教室列表。其它页保持原高度。 */
    dense: Boolean = false,
) {
    // 顶栏与页面背景同色：iOS 的 SwiftUI 根视图在状态栏下方铺的就是 background，
    // 顶栏若用 surface 会在状态栏下方露出一条浅色带子，破坏沉浸感。
    // iOS 的 Compose 宿主已改为全屏布局（原生 push 转场需要覆盖状态栏区域），
    // WindowInsets.statusBars 在 iOS 上恢复为真实值，顶栏统一应用状态栏（含 Android 小窗标题栏）内边距。
    // 内容行固定高度：大标题与右侧「已同步」胶囊垂直居中同一条线，各 tab 一致。
    val topBarContentHeight = if (dense) 40.dp else 52.dp
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.topSystemBars)
                .height(topBarContentHeight)
                .padding(start = if (onBack != null) 6.dp else 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 二级页返回箭头；一级 tab（首页/作业/更多…）无返回，标题左缘固定。
            if (onBack != null) {
                Surface(
                    onClick = onBack,
                    color = Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.size(topBarContentHeight),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "返回" },
                        contentAlignment = Alignment.Center,
                    ) {
                        BackChevron()
                    }
                }
                Spacer(modifier = Modifier.width(2.dp))
            }
            if (leadingAction != null) {
                leadingAction()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                title,
                style = if (dense) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (titleAction == null) Modifier.weight(1f) else Modifier,
            )
            if (titleAction != null) {
                Spacer(Modifier.width(8.dp))
                titleAction()
                Spacer(Modifier.weight(1f))
            }
            if (action != null) {
                action()
                Spacer(Modifier.width(8.dp))
            }
            // “登录中”优先于“同步中”，再回落到页面提供的空闲状态（如课表已同步）。
            // 状态与刷新是两个独立动作：左侧查看同步详情，右侧执行刷新。
            // 首页左侧可点，因此使用实色；其它页面只是状态展示，降低为灰色。
            val busyText = when {
                isLoggingIn -> "登录中"
                isRefreshing -> "同步中"
                else -> null
            }
            when {
                busyText != null -> {
                    TopBarBusyStatusCapsule(
                        text = busyText,
                        onClick = onStatusClick,
                    )
                }
                idleStatusText != null || onRefresh != null -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        idleStatusText?.let { status ->
                            if (statusAsText && onStatusClick != null) TextButton(onClick = onStatusClick) {
                                Text(status, style = MaterialTheme.typography.labelMedium)
                            }
                            else if (statusAsText) Text(status, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else TopBarStatusCircle(
                                text = status,
                                onClick = onStatusClick,
                                enabled = onStatusClick != null,
                            )
                        }
                        onRefresh?.let { refresh ->
                            TopBarRefreshCircle(
                                onClick = refresh,
                                enabled = !isRefreshing && !isLoggingIn,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TopBarBusyStatusCapsule(
    text: String,
    onClick: (() -> Unit)?,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.8.dp,
                color = LocalContentColor.current,
            )
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
    val modifier = Modifier.semantics {
        contentDescription = "$text，点按查看同步详情"
    }
    if (onClick == null) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.55f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(999.dp),
            modifier = modifier,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.55f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(999.dp),
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
internal fun TopBarStatusCircle(
    text: String,
    onClick: (() -> Unit)?,
    enabled: Boolean,
) {
    val iconTint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    }
    val modifier = Modifier
        .size(32.dp)
        .semantics {
            contentDescription = if (enabled) {
                "$text，点按查看同步详情"
            } else {
                text
            }
        }
    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (text == "已同步") {
                TopBarSyncedIcon(modifier = Modifier.size(16.dp), tint = iconTint)
            } else {
                Text(
                    text = if (text in partialSyncFailureStatusTexts) "!" else "i",
                    color = if (text in partialSyncFailureStatusTexts) {
                        MaterialTheme.colorScheme.error
                    } else {
                        iconTint
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
    if (enabled && onClick != null) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.68f),
            shape = CircleShape,
            modifier = modifier,
            content = content,
        )
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(0.30f),
            shape = CircleShape,
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
internal fun TopBarRefreshCircle(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceVariant.accessibleAlpha(if (enabled) 0.68f else 0.30f),
        shape = CircleShape,
        modifier = Modifier.size(32.dp).semantics { contentDescription = "刷新" },
    ) {
        TopBarRefreshIcon(
            modifier = Modifier.size(16.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
            },
        )
    }
}

@Composable
internal fun PartialSyncFailureDialog(
    failedItems: List<String>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppleSheetOrAlert(
        onDismissRequest = onDismiss,
        title = "部分同步失败",
        confirmLabel = "重试",
        onConfirm = onRetry,
        dismissLabel = "关闭",
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("以下内容本轮同步失败。可以稍后手动重试：")
            failedItems.forEach { item ->
                Text("• $item", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun HomeSyncDetailsDialog(
    title: String,
    items: List<HomeSyncItem>,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    // This is a status surface, not a long-form dialog. Keeping it on the
    // shared sheet path gives Android a bottom sheet, macOS a single flat
    // surface, and iOS the UIKit detent with its native header.
    AppleSheet(
        onDismissRequest = onDismiss,
        title = title,
        // iOS already supplies the native top-right X. Only expose an action
        // when there is something meaningful to do; do not create a second
        // “关闭” button beside the system close affordance.
        confirmLabel = if (canRetry) "重试" else null,
        onConfirm = if (canRetry) onRetry else null,
        dismissLabel = null,
        // 行数随启用的模块变化（6～8 行）：iOS 按列表实际高度展开，行数多到超出屏幕时封顶并滚动。
        // 滚动与底部安全区由 sheet 负责，列表本身不再套 verticalScroll。
        fitContentHeight = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            // The sheet itself supplies the single material surface. A second
            // rounded Surface here made the macOS panel look like a frame inside
            // a frame and reduced the usable width on Android.
            items.forEachIndexed { index, item ->
                HomeSyncDetailRow(item)
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 36.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.accessibleAlpha(0.7f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeSyncDetailRow(item: HomeSyncItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (item.state) {
                HomeSyncItemState.SYNCING -> CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 1.7.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                HomeSyncItemState.SUCCESS -> TopBarSyncedIcon(
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                HomeSyncItemState.FAILED -> Text(
                    "!",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                HomeSyncItemState.WAITING -> Text(
                    "•",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun TopBarCalendarAction(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 顶栏同步：双弧 + 两端箭头（Material/SF 风格的 sync，自绘不引入图标库）。
 * Compose 角度：0° 在右侧，顺时针为正。
 */
@Composable
internal fun TopBarRefreshIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.7.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        val r = size.minDimension * 0.34f
        val c = Offset(size.width / 2f, size.height / 2f)
        val topLeft = Offset(c.x - r, c.y - r)
        val arcSize = Size(r * 2, r * 2)
        val arrow = 3.4.dp.toPx()

        fun pointOnCircle(deg: Float): Offset {
            val rad = deg * PI / 180.0
            return Offset(
                c.x + r * kotlin.math.cos(rad).toFloat(),
                c.y + r * kotlin.math.sin(rad).toFloat(),
            )
        }

        /** 弧末端处画 V 形箭头，开口朝向切线（顺时针）。 */
        fun arrowAt(endDeg: Float) {
            val tip = pointOnCircle(endDeg)
            // 顺时针切线方向 = endDeg + 90°（Canvas 顺时针）
            val tangent = (endDeg + 90f) * PI / 180.0
            val tx = kotlin.math.cos(tangent).toFloat()
            val ty = kotlin.math.sin(tangent).toFloat()
            // 法向（指向圆心外侧的侧翼）
            val nx = -ty
            val ny = tx
            val back = Offset(tip.x - tx * arrow, tip.y - ty * arrow)
            drawLine(
                tint,
                tip,
                Offset(back.x + nx * arrow * 0.55f, back.y + ny * arrow * 0.55f),
                strokeWidth,
                StrokeCap.Round,
            )
            drawLine(
                tint,
                tip,
                Offset(back.x - nx * arrow * 0.55f, back.y - ny * arrow * 0.55f),
                strokeWidth,
                StrokeCap.Round,
            )
        }

        // 上半弧：约从右下扫到左上
        drawArc(
            color = tint,
            startAngle = -30f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        arrowAt(120f)

        // 下半弧：约从左上扫到右下
        drawArc(
            color = tint,
            startAngle = 150f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        arrowAt(300f)
    }
}

/** 顶栏「已同步」对勾：与 TopBarRefreshIcon 同粗细的 Canvas 自绘，不引入图标库。 */
@Composable
internal fun TopBarSyncedIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.7.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(
            tint,
            Offset(w * 0.20f, h * 0.54f),
            Offset(w * 0.42f, h * 0.74f),
            strokeWidth,
            StrokeCap.Round,
        )
        drawLine(
            tint,
            Offset(w * 0.42f, h * 0.74f),
            Offset(w * 0.80f, h * 0.28f),
            strokeWidth,
            StrokeCap.Round,
        )
    }
}

/** 返回箭头：与 MoreEntryChevron 同风格的 Canvas 左尖括号，不引入图标库。 */
@Composable
internal fun BackChevron() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(10.dp, 16.dp)) {
        val strokeWidth = 2.dp.toPx()
        val mid = size.height / 2
        drawLine(
            color,
            Offset(size.width - 2.dp.toPx(), 2.dp.toPx()),
            Offset(2.dp.toPx(), mid),
            strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(2.dp.toPx(), mid),
            Offset(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
            strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

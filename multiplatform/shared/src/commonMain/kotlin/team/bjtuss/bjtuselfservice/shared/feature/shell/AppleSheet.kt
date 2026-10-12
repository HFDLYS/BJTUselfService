package team.bjtuss.bjtuselfservice.shared.feature.shell

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.currentPlatform

/**
 * A shared sheet whose iOS presentation is owned by UIKit.
 *
 * On iOS the composition is hosted inside a real
 * `UISheetPresentationController`; UIKit therefore supplies the system
 * rounded surface, grabber, detent transition, background material and
 * interactive dismissal. Compose's Material sheet is only the non-iOS
 * fallback (and remains useful in previews/tests without a host controller).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    needsFullHeight: Boolean = false,
    scrollableBody: Boolean = false,
    /**
     * iOS 按正文实际高度展开（自定义 detent）。各平台都由 sheet 负责滚动和底部安全区，
     * content 里不要再套 verticalScroll；非 iOS 的底部 sheet 本来就按内容高度展开。
     */
    fitContentHeight: Boolean = false,
    title: String? = null,
    confirmLabel: String? = null,
    confirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    todayLabel: String? = null,
    todayEnabled: Boolean = true,
    onToday: (() -> Unit)? = null,
    dismissLabel: String? = null,
    dismissEnabled: Boolean = true,
    showDismissButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isIos = currentPlatform().family == PlatformFamily.IOS
    val presenter = if (isIos) LocalNativeSheetPresenter.current else null

    if (presenter != null) {
        // The UIKit sheet is a separate Compose root, so inherited MaterialTheme
        // locals do not cross the UIViewController boundary automatically. Capture
        // the current app scheme here; otherwise a dark-mode sheet falls back to
        // Compose's default light colors and looks unlike the system sheet.
        val appColorScheme = MaterialTheme.colorScheme
        val appTypography = MaterialTheme.typography
        val appShapes = MaterialTheme.shapes
        val currentContent = @Composable {
            MaterialTheme(
                colorScheme = appColorScheme,
                typography = appTypography,
                shapes = appShapes,
            ) {
                // ComposeUIViewController is a separate root from the app shell.
                // MaterialTheme carries the palette, but it does not provide a
                // LocalContentColor by itself. Without this provider, Text with
                // an unspecified color falls back to black over the native dark
                // glass surface.
                CompositionLocalProvider(LocalContentColor provides appColorScheme.onSurface) {
                    val density = LocalDensity.current
                    val horizontalBounds = if (LocalNativeSheetContentBoundsHandled.current) Modifier else
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    if (fitContentHeight) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .then(horizontalBounds)
                                .verticalScroll(rememberScrollState())
                                // verticalScroll 会把 fillMaxHeight 的最小高度传给内容；不清掉的话
                                // 量到的永远 ≥ 当前 sheet 高度，回报后 sheet 越长越高。
                                .wrapContentHeight(Alignment.Top)
                                // 底部安全区不计入 detent（UIKit 另加），所以量高度时排除在外。
                                .windowInsetsPadding(WindowInsets.bottomSystemBars)
                                .onSizeChanged { size ->
                                    presenter.setFittedContentHeight(with(density) { size.height.toDp().value })
                                }
                                .padding(top = 8.dp),
                            content = content,
                        )
                        return@CompositionLocalProvider
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 半屏 detent 也要吃满宿主高度，内部 verticalScroll 才有视口。
                            .fillMaxHeight()
                            .then(horizontalBounds)
                            // Short informational sheets also need a bounded scroll viewport in landscape.
                            .then(if (!needsFullHeight && !scrollableBody) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            // Scrollable bodies use UIKit's title-bar clearance and
                            // own their bottom inset inside the scrolling content.
                            .padding(top = if (scrollableBody) 0.dp else 8.dp)
                            .then(if (scrollableBody) Modifier else Modifier.windowInsetsPadding(WindowInsets.bottomSystemBars)),
                        content = content,
                    )
                }
            }
        }
        SideEffect {
            presenter.setScrollableBody(scrollableBody)
            presenter.update(
                content = currentContent,
                onDismissRequest = onDismissRequest,
                title = title,
                confirmLabel = confirmLabel,
                confirmEnabled = confirmEnabled,
                onConfirm = onConfirm,
                todayLabel = todayLabel,
                todayEnabled = todayEnabled,
                onToday = onToday,
                dismissLabel = dismissLabel,
                dismissEnabled = dismissEnabled,
                showDismissButton = showDismissButton,
            )
        }
        LaunchedEffect(presenter, needsFullHeight) {
            presenter.present(needsFullHeight)
        }
        DisposableEffect(presenter) {
            onDispose { presenter.dismiss() }
        }
        return
    }

    // Non-iOS fallback: keep the shared component usable on Android/desktop
    // and in previews without introducing a platform host dependency.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = true,
        containerColor = BottomSheetDefaults.ContainerColor,
        tonalElevation = 0.dp,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = { BottomSheetDefaults.DragHandle() },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (needsFullHeight) Modifier.fillMaxHeight() else Modifier),
            ) {
                if (title != null || confirmLabel != null || todayLabel != null ||
                    dismissLabel != null || showDismissButton
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        title?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (confirmLabel != null && onConfirm != null) {
                            TextButton(
                                onClick = onConfirm,
                                enabled = confirmEnabled,
                            ) { Text(confirmLabel) }
                        }
                        if (todayLabel != null && onToday != null) {
                            TextButton(
                                onClick = onToday,
                                enabled = todayEnabled,
                            ) { Text(todayLabel) }
                        }
                        if (dismissLabel != null) {
                            TextButton(
                                onClick = onDismissRequest,
                                enabled = dismissEnabled,
                            ) { Text(dismissLabel) }
                        } else if (showDismissButton) {
                            TextButton(onClick = onDismissRequest) { Text("关闭") }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (needsFullHeight) Modifier.weight(1f) else Modifier)
                        // 与 iOS 同义：fitContentHeight 的 content 不自带滚动和底部安全区，由 sheet 提供。
                        .then(
                            if (fitContentHeight) {
                                Modifier
                                    .verticalScroll(rememberScrollState())
                                    .windowInsetsPadding(WindowInsets.bottomSystemBars)
                            } else {
                                Modifier
                            },
                        ),
                    content = content,
                )
            }
        },
    )
}

/** Bottom inset lives in the scrolling content so a long sheet does not leave a blank home-indicator strip. */
@Composable
internal fun Modifier.sheetScrollContentPadding(): Modifier {
    val bottomInset = WindowInsets.bottomSystemBars.asPaddingValues().calculateBottomPadding()
    return padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp + bottomInset)
}

/** iOS uses the host-owned system sheet; other platforms keep a Material dialog. */
@Composable
fun AppleSheetOrAlert(
    onDismissRequest: () -> Unit,
    title: String?,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    todayLabel: String? = null,
    todayEnabled: Boolean = true,
    onToday: (() -> Unit)? = null,
    dismissLabel: String? = null,
    dismissEnabled: Boolean = true,
    showDismissButton: Boolean = true,
    needsFullHeight: Boolean = false,
    scrollableBody: Boolean = false,
    /** iOS 按正文高度展开；Android 对话框正文改为可滚动。两端 content 里都不要再套 verticalScroll。 */
    fitContentHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (currentPlatform().family == PlatformFamily.IOS) {
        // On iOS the title and actions are UIBarButtonItems in the native sheet
        // navigation bar. Compose supplies only the body; it does not imitate
        // the system sheet chrome with Material buttons.
        AppleSheet(
            onDismissRequest = onDismissRequest,
            needsFullHeight = needsFullHeight,
            scrollableBody = scrollableBody,
            fitContentHeight = fitContentHeight,
            title = title,
            confirmLabel = confirmLabel,
            confirmEnabled = confirmEnabled,
            onConfirm = onConfirm,
            todayLabel = todayLabel,
            todayEnabled = todayEnabled,
            onToday = onToday,
            dismissLabel = dismissLabel,
            dismissEnabled = dismissEnabled,
            showDismissButton = showDismissButton,
        ) {
            content()
        }
    } else {
        val fallbackConfirmLabel = confirmLabel ?: if (showDismissButton) "关闭" else null
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title?.let { { Text(it) } },
            // AlertDialog 的正文区高度受限但不滚动，内容超高（如 HyperOS 小窗）时按钮会被裁掉。
            text = {
                Column(if (fitContentHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier) { content() }
            },
            confirmButton = if (fallbackConfirmLabel != null) {
                {
                    TextButton(onClick = onConfirm ?: onDismissRequest) {
                        Text(fallbackConfirmLabel)
                    }
                }
            } else {
                {}
            },
            dismissButton = dismissLabel?.let { label ->
                { TextButton(onClick = onDismissRequest) { Text(label) } }
            },
        )
    }
}

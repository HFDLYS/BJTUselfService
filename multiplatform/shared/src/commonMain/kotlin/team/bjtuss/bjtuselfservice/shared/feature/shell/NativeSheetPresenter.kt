package team.bjtuss.bjtuselfservice.shared.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Host-owned sheet presentation.
 *
 * The shared UI supplies the sheet content, while the platform host owns the
 * presentation controller. This keeps UIKit responsible for the actual iOS
 * sheet chrome, detents, grabber, blur and dismissal gesture.
 */
interface NativeSheetPresenter {
    /** Scrollable sheets own their bottom inset inside the scroll content. */
    fun setScrollableBody(enabled: Boolean) {}

    fun update(
        content: @Composable () -> Unit,
        onDismissRequest: () -> Unit,
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
    )

    fun present(needsFullHeight: Boolean)

    /** 按内容定高的 sheet 回报正文高度（dp，iOS 上 1pt == 1dp）；宿主据此调整 detent。 */
    fun setFittedContentHeight(heightDp: Float) {}

    fun dismiss()
}

val LocalNativeSheetPresenter = compositionLocalOf<NativeSheetPresenter?> { null }
/** The modal host bounds its body using the sheet's own UIKit safe area. */
val LocalNativeSheetContentBoundsHandled = compositionLocalOf { false }

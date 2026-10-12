package team.bjtuss.bjtuselfservice.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIPresentationController
import platform.UIKit.UIViewController
import platform.UIKit.UINavigationController
import platform.UIKit.UIRectEdgeBottom
import platform.UIKit.UIRectEdgeNone
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import team.bjtuss.bjtuselfservice.shared.feature.shell.NativeSheetPresenter
import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalNativeSheetContentBoundsHandled

/**
 * Presents shared Compose content through UIKit's real sheet presentation
 * controller. The presenter is intentionally host-owned: the shared screen
 * only describes content and dismissal, while UIKit owns the sheet chrome and
 * interactive transition.
 */
@OptIn(
    ExperimentalForeignApi::class,
    ExperimentalComposeUiApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)
class IosNativeSheetPresenter(
    private val owner: () -> UIViewController,
) : NativeSheetPresenter {
    private var scrollableBody = false

    override fun setScrollableBody(enabled: Boolean) {
        if (scrollableBody == enabled) return
        scrollableBody = enabled
        sheetController?.let(::applyContentLayout)
    }

    private fun applyContentLayout(sheet: UIViewController) {
        val content = (sheet as? UINavigationController)?.topViewController ?: return
        // Keep the native header above the body, while allowing scrollable content
        // to draw under the home indicator. Compose puts that inset in its content.
        content.edgesForExtendedLayout = if (scrollableBody) UIRectEdgeBottom else UIRectEdgeNone
    }

    private var currentContent by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var dismissRequest: () -> Unit = {}
    private var title: String? = null
    private var confirmLabel: String? = null
    private var confirmEnabled: Boolean = true
    private var todayLabel: String? = null
    private var todayEnabled: Boolean = true
    private var dismissLabel: String? = null
    private var dismissEnabled: Boolean = true
    private var showDismissButton: Boolean = true
    private var confirmTarget: SheetActionTarget? = null
    private var todayTarget: SheetActionTarget? = null
    private var dismissTarget: SheetActionTarget? = null
    private var sheetController: UIViewController? = null
    private var sheetDelegate: SheetDelegate? = null

    override fun update(
        content: @Composable () -> Unit,
        onDismissRequest: () -> Unit,
        title: String?,
        confirmLabel: String?,
        confirmEnabled: Boolean,
        onConfirm: (() -> Unit)?,
        todayLabel: String?,
        todayEnabled: Boolean,
        onToday: (() -> Unit)?,
        dismissLabel: String?,
        dismissEnabled: Boolean,
        showDismissButton: Boolean,
    ) {
        currentContent = content
        dismissRequest = onDismissRequest
        this.title = title
        this.confirmLabel = confirmLabel
        this.confirmEnabled = confirmEnabled
        this.todayLabel = todayLabel
        this.todayEnabled = todayEnabled
        this.dismissLabel = dismissLabel
        this.dismissEnabled = dismissEnabled
        this.showDismissButton = showDismissButton
        confirmTarget = onConfirm?.let(::SheetActionTarget)
        todayTarget = onToday?.let(::SheetActionTarget)
        dismissTarget = if (showDismissButton) SheetActionTarget(onDismissRequest) else null
        sheetController?.let(::updateNativeHeader)
    }

    private var fittedContentHeight: Float? = null

    override fun setFittedContentHeight(heightDp: Float) {
        if (fittedContentHeight == heightDp) return
        fittedContentHeight = heightDp
        // 高度来自 Compose 的 onSizeChanged（布局进行中）；同步改 detent 会让宿主在测量中途
        // 改尺寸并崩溃（updateRootConstraints called while measuring），推迟到下一轮主线程。
        dispatch_async(dispatch_get_main_queue()) {
            val height = fittedContentHeight ?: return@dispatch_async
            sheetController?.let { BJTUSetSheetFittedHeight(it, height.toDouble()) }
        }
    }

    override fun present(needsFullHeight: Boolean) {
        val existing = sheetController
        if (existing != null) {
            BJTUConfigureSheetPresentation(existing, needsFullHeight)
            fittedContentHeight?.let { BJTUSetSheetFittedHeight(existing, it.toDouble()) }
            updateNativeHeader(existing)
            return
        }

        val contentController = ComposeUIViewController(
            configure = { opaque = false },
        ) {
            CompositionLocalProvider(LocalNativeSheetContentBoundsHandled provides true) {
                currentContent?.invoke()
            }
        }
        val sheet = BJTUCreateNativeSheetController(
            content = NativeSheetContentHost(contentController),
            title = title,
            confirmLabel = confirmLabel,
            confirmEnabled = confirmEnabled,
            confirmTarget = confirmTarget,
            todayLabel = todayLabel,
            todayEnabled = todayEnabled,
            todayTarget = todayTarget,
            dismissLabel = dismissLabel,
            dismissEnabled = dismissEnabled,
            dismissTarget = dismissTarget,
        ) ?: error("Unable to create native sheet controller")
        applyContentLayout(sheet)
        BJTUInstallNativeSheetMaterial(sheet)
        BJTUConfigureSheetPresentation(sheet, needsFullHeight)
        fittedContentHeight?.let { BJTUSetSheetFittedHeight(sheet, it.toDouble()) }

        val delegate = SheetDelegate { handleDismissed() }
        sheetDelegate = delegate
        sheetController = sheet
        owner().presentViewController(sheet, animated = true) {
            BJTUInstallNativeSheetMaterial(sheet)
        }
        BJTUAttachSheetPresentationDelegate(sheet, delegate)
    }

    private fun updateNativeHeader(sheet: UIViewController) {
        BJTUConfigureNativeSheetHeader(
            sheet = sheet,
            title = title,
            confirmLabel = confirmLabel,
            confirmEnabled = confirmEnabled,
            confirmTarget = confirmTarget,
            todayLabel = todayLabel,
            todayEnabled = todayEnabled,
            todayTarget = todayTarget,
            dismissLabel = dismissLabel,
            dismissEnabled = dismissEnabled,
            dismissTarget = dismissTarget,
        )
        applyContentLayout(sheet)
    }

    override fun dismiss() {
        val sheet = sheetController ?: return
        fittedContentHeight = null
        sheetController = null
        sheetDelegate = null
        confirmTarget = null
        todayTarget = null
        dismissTarget = null
        sheet.dismissViewControllerAnimated(flag = true, completion = null)
    }

    private fun handleDismissed() {
        fittedContentHeight = null
        sheetController = null
        sheetDelegate = null
        confirmTarget = null
        todayTarget = null
        dismissTarget = null
        dismissRequest()
    }

    private class SheetActionTarget(
        private val action: () -> Unit,
    ) : NSObject() {
        @ObjCAction
        fun invoke(sender: NSObject?) {
            action()
        }
    }

    private class SheetDelegate(
        private val onDidDismiss: () -> Unit,
    ) : NSObject(), UIAdaptivePresentationControllerDelegateProtocol {
        override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
            onDidDismiss()
        }
    }
}

/** Uses this presentation's insets, never the underlying tab/navigation bars. */
@OptIn(ExperimentalForeignApi::class)
private class NativeSheetContentHost(private val content: UIViewController) : UIViewController(null, null) {
    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(content)
        view.addSubview(content.view)
        content.didMoveToParentViewController(this)
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        val width = view.bounds.useContents { size.width }
        val height = view.bounds.useContents { size.height }
        val left = view.safeAreaInsets.useContents { left }
        val right = view.safeAreaInsets.useContents { right }
        content.view.setFrame(CGRectMake(left, 0.0, (width - left - right).coerceAtLeast(0.0), height))
    }
}

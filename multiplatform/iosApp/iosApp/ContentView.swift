import BJTUShared
import Foundation
import QuartzCore
import SwiftUI
import UIKit

// Keep this pair aligned with LightColors/DarkColors.background in shared App.kt.
private let appBackgroundUIColor = UIColor { traits in
    if traits.userInterfaceStyle == .dark {
        return UIColor(red: 16.0 / 255.0, green: 18.0 / 255.0, blue: 22.0 / 255.0, alpha: 1)
    }
    return UIColor(red: 244.0 / 255.0, green: 245.0 / 255.0, blue: 249.0 / 255.0, alpha: 1)
}
private let appBackgroundColor = Color(uiColor: appBackgroundUIColor)

/// 每个 Compose 宿主管线的公共约定（M10 回退壳与 M17 玻璃壳共用，行为必须一致）。
///
/// 本项目暂不开放实验性的 Compose iOS 无障碍语义树。iOS 26 的辅助功能客户端（含
/// 各类自动化查询）会在原生 push/pop 移除宿主控制器后继续查询已失效的 Compose
/// AccessibilityElement，在框架 cachedProperties 内崩溃。在 Swift 侧对整个 Compose
/// 宿主视图隐藏无障碍子树即可完全跳过该路径，视觉界面与原生导航手势不受影响。
///
/// Compose 首帧之前 UIKit 会先显示宿主底色；与页面背景保持一致可避免深色模式闪白。
///
/// 宿主只设置底色，不用计时遮罩覆盖 Compose：计数撤罩会使已滑入的新页先空白再突然显示内容。
private func configureComposeHost(_ controller: UIViewController) {
    controller.view.accessibilityElementsHidden = true
    pinComposeSurfaceColor(controller.view)
}

private func pinComposeSurfaceColor(_ view: UIView) {
    view.backgroundColor = appBackgroundUIColor
    // 转场合成器按 isOpaque 决定是否透出后面的白窗/黑窗。内容本身已经铺满底色，
    // 标成不透明可避免 iOS 26 玻璃推入时把后页加亮后再盖上本页。
    view.isOpaque = true
    pinMetalLayerBackground(view)
}

private func pinMetalLayerBackground(_ view: UIView) {
    let color = appBackgroundUIColor.resolvedColor(with: view.traitCollection).cgColor
    pinMetalLayers(in: view.layer, color: color)
    for child in view.subviews {
        pinMetalLayerBackground(child)
    }
}

private func pinMetalLayers(in layer: CALayer, color: CGColor) {
    if let metal = layer as? CAMetalLayer {
        metal.backgroundColor = color
    }
    for sublayer in layer.sublayers ?? [] {
        pinMetalLayers(in: sublayer, color: color)
    }
}

/// 边缘返回手势接管：Compose 的 Skia 层会吃掉左缘触摸，pop 手势必须优先于列表滚动/横滑。
/// 导航栏隐藏时 UIKit 默认会关掉 interactivePopGestureRecognizer（内部 delegate 认为
/// 没有返回按钮就不该 pop），因此栈深判定也要自己接管。
private func installInteractivePopGesture(
    on navigationController: UINavigationController
) {
    navigationController.interactivePopGestureRecognizer?.isEnabled = true
    navigationController.interactivePopGestureRecognizer?.delegate =
        navigationController as? UIGestureRecognizerDelegate
}

// MARK: - M10 回退壳（iOS 26 以下）

private final class NativeNavigationController: UINavigationController, UINavigationControllerDelegate, UIGestureRecognizerDelegate {
    private var authenticatedSession: AuthenticatedSession?
    private var appActiveObserver: NSObjectProtocol?
    private var appInactiveObserver: NSObjectProtocol?

    private func updateInteractivePopEnabled() {
        interactivePopGestureRecognizer?.isEnabled = viewControllers.count > 1
    }

    init(offlineSession: AuthenticatedSession? = nil) {
        super.init(nibName: nil, bundle: nil)
        delegate = self
        view.backgroundColor = appBackgroundUIColor
        setNavigationBarHidden(true, animated: false)
        installInteractivePopGesture(on: self)
        appInactiveObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.willResignActiveNotification, object: nil, queue: .main
        ) { [weak self] _ in
            self?.authenticatedSession?.notifyAppBecameInactive()
        }
        appActiveObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.authenticatedSession?.notifyAppBecameActive()
        }
        let rootController: UIViewController
        if let offlineSession {
            authenticatedSession = offlineSession
            rootController = OfflineLayoutProbeKt.OfflineLayoutRootViewController(session: offlineSession, onOpenRoute: { [weak self] routeId in
                self?.openNativeRoute(routeId)
            })
        } else {
        rootController = MainViewControllerKt.NativeMainViewController(
            onAuthenticatedSessionChanged: { [weak self] session in
                self?.authenticatedSession = session
                if session == nil, (self?.viewControllers.count ?? 0) > 1 {
                    self?.popToRootViewController(animated: false)
                    self?.updateInteractivePopEnabled()
                }
            },
            onOpenNativeRoute: { [weak self] routeId in
                self?.openNativeRoute(routeId)
            },
            nativeTabBarEnabled: false
        )
        }
        configureComposeHost(rootController)
        setViewControllers([rootController], animated: false)
        updateInteractivePopEnabled()
#if DEBUG
        if offlineSession != nil, let route = ProcessInfo.processInfo.arguments.first(where: { $0.hasPrefix("--route=") }) {
            DispatchQueue.main.async { [weak self] in self?.openNativeRoute(String(route.dropFirst(8))) }
        }
#endif
    }

    deinit {
        if let appInactiveObserver { NotificationCenter.default.removeObserver(appInactiveObserver) }
        if let appActiveObserver {
            NotificationCenter.default.removeObserver(appActiveObserver)
        }
    }

    @available(*, unavailable)
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func openNativeRoute(_ routeId: String) {
        guard let session = authenticatedSession else { return }
        guard topViewController?.restorationIdentifier != routeId else { return }
        let destination = MainViewControllerKt.NativeDestinationViewController(
            session: session,
            routeId: routeId,
            useNativeTitleBar: false,
            onOpenNativeRoute: { [weak self] childRouteId in
                self?.openNativeRoute(childRouteId)
            },
            onCloseNativeRoute: { [weak self] in
                self?.popViewController(animated: true)
            },
            // 回退壳不显示系统导航栏：标题回调留空，页面继续自绘顶栏，保持 M10 验收语义。
            onTitleChanged: { _ in },
            onActionChanged: { _ in },
            onSelectNativeTab: { _ in }
        )
        destination.restorationIdentifier = routeId
        configureComposeHost(destination)
        destination.loadViewIfNeeded()
        destination.view.frame = view.bounds
        destination.view.setNeedsLayout()
        destination.view.layoutIfNeeded()
        pinComposeSurfaceColor(destination.view)
        pushViewController(destination, animated: true)
    }

    // MARK: - UINavigationControllerDelegate

    func navigationController(
        _ navigationController: UINavigationController,
        didShow viewController: UIViewController,
        animated: Bool
    ) {
        // push/pop 动画结束后再同步开关，避免根页仍能半截手势卡住导航栈。
        updateInteractivePopEnabled()
        // 部分系统版本在 didShow 后会把 delegate 重置；每次确认仍由本类接管。
        installInteractivePopGesture(on: navigationController)
        updateInteractivePopEnabled()
    }

    // MARK: - UIGestureRecognizerDelegate

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard gestureRecognizer === interactivePopGestureRecognizer else { return true }
        // 根页禁止 pop；二级及以上（更多→设置/考试/课件…）允许 leading-edge 跟手返回。
        return viewControllers.count > 1
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        // 边缘返回优先：Compose 列表滚动/水平拖动须等 pop 手势失败后再开始，
        // 否则 Skia 层会吃掉左缘触摸，导致“更多”子页无法侧滑返回。
        gestureRecognizer === interactivePopGestureRecognizer
    }
}

// MARK: - M17 液态玻璃壳（iOS 26+）

/// 宿主导航栏绑定：Kotlin 首帧之后才回报动态标题（教学楼名等）与右侧动作，需要回填到已入栈的控制器，
/// 并让宿主重算导航栏显隐——空标题代表「本页自绘顶栏」，系统玻璃栏不该出现。
private final class NativeChromeBinding {
    weak var controller: UIViewController?
    private var lastGlassProgress: CGFloat = -1
    private var retainedActionTargets: [NativeBarActionTarget] = []
    private var actionTargets: [NativeBarActionRole: NativeBarActionTarget] = [:]
    private var actionButtons: [NativeBarActionRole: NativeBarIconButton] = [:]
    private var systemActionItems: [NativeBarActionRole: UIBarButtonItem] = [:]
    private var usesSystemBars: Bool { (controller?.navigationController as? TabRootNavigationController)?.usesSystemBars == true }
    private var lastActionLayoutKey: NativeBarActionLayoutKey?
    private var lastActionVertical: Bool?
    private var lastActionVisualKey: [String]?
    private var pendingTitle: String?
    private var pendingAction: NativeBarAction?
    private var hasPendingAction = false
    private var updateScheduled = false

    func apply(_ title: String) {
        guard !title.isEmpty else { return }
        pendingTitle = title
        scheduleFlush()
    }

    /// 同步状态、刷新与页面级动作由 Compose 声明、由系统导航栏渲染：不再把 Compose 胶囊
    /// 塞进标题栏。动作载体只在结构真正变化时重建；滚动时不重建控件，避免控件
    /// 被销毁再创建而闪烁。
    func apply(action: NativeBarAction?) {
        pendingAction = action
        hasPendingAction = true
        scheduleFlush()
    }

    private func scheduleFlush() {
        guard !updateScheduled else { return }
        updateScheduled = true
        // Title and action callbacks arrive from separate Compose SideEffects.
        // Coalesce them into one main-queue transaction so UIKit never renders
        // a centered title first and then animates it away when buttons arrive.
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.updateScheduled = false
            self.flushPendingChrome()
        }
    }

    private func flushPendingChrome() {
        guard let controller else { return }
        if let title = pendingTitle {
            pendingTitle = nil
            if controller.navigationItem.title != title { controller.navigationItem.title = title }
            if controller.navigationController?.topViewController === controller {
                (controller.navigationController as? NativeChromeHosting)?.setNativeTitle(title)
            }
        }
        if hasPendingAction {
            let action = pendingAction
            pendingAction = nil
            hasPendingAction = false
            applyAction(action)
        }
        (controller.navigationController as? NativeChromeHosting)?.refreshNavigationBarVisibility()
    }

    private func iconBarItem(
        title: String,
        symbolName: String,
        role: NativeBarActionRole,
        onClick: (() -> Void)?,
        enabled: Bool = true,
    ) -> UIBarButtonItem {
        if usesSystemBars {
            let target = onClick.map { makeActionTarget(role: role, onInvoke: $0) }
            let item = UIBarButtonItem(image: UIImage(systemName: symbolName), style: .plain, target: target, action: #selector(NativeBarActionTarget.invoke(_:)))
            item.title = title
            item.accessibilityLabel = title
            item.isEnabled = enabled && onClick != nil
            if #available(iOS 27.1, *) { item.axisBehavior = .verticalPreferred }
            systemActionItems[role] = item
            return item
        }
        let button = NativeBarIconButton(symbolName: symbolName)
        button.setVisualEnabled(enabled)
        if let onClick {
            let target = makeActionTarget(role: role, onInvoke: onClick)
            button.addTarget(
                target,
                action: #selector(NativeBarActionTarget.invoke(_:)),
                for: .touchUpInside,
            )
        }
        button.accessibilityLabel = title
        actionButtons[role] = button
        let item = UIBarButtonItem(customView: button)
        // NOTE: do NOT set sharesBackground = false here. Verified live (lldb + screenshot):
        // when every item opts out of the shared background, UIKit builds no background view
        // at all (_UIBarBackground stays empty) and the bar renders transparent no matter what
        // appearance is assigned. Default sharing gives the Settings-style frosted bar; the
        // icon itself stays a plain glyph, no nested capsules.
        return item
    }

    private func todayBarItem(vertical: Bool, onClick: @escaping () -> Void) -> UIBarButtonItem {
        if usesSystemBars && vertical {
            let target = makeActionTarget(role: .extra, onInvoke: onClick)
            let item = UIBarButtonItem(title: "今", style: .plain, target: target, action: #selector(NativeBarActionTarget.invoke(_:)))
            item.accessibilityLabel = "回到今天"
            item.setTitleTextAttributes([.font: UIFontMetrics(forTextStyle: .title3).scaledFont(for: UIFont.systemFont(ofSize: 22, weight: .semibold))], for: .normal)
            if #available(iOS 27.1, *) { item.axisBehavior = .verticalPreferred }
            return item
        }
        // Use the same square carrier and system bar background as the right-side
        // icons. A configured glass text button gets compressed into a capsule.
        let button = NativeBarIconButton(title: "今")
        button.accessibilityLabel = "回到今天"
        let target = makeActionTarget(role: .extra, onInvoke: onClick)
        button.addTarget(target, action: #selector(NativeBarActionTarget.invoke(_:)), for: .touchUpInside)
        actionButtons[.extra] = button
        return UIBarButtonItem(customView: button)
    }

    private func fixedActionSpacing() -> UIBarButtonItem {
        let item = UIBarButtonItem(barButtonSystemItem: .fixedSpace, target: nil, action: nil)
        item.width = 8
        return item
    }

    private func spinnerBarItem(title: String) -> UIBarButtonItem {
        let button = NativeSpinnerButton(type: .system)
        button.accessibilityLabel = title
        button.accessibilityValue = "进行中"
        button.isEnabled = false
        button.alpha = 0.72

        let spinner = UIActivityIndicatorView(style: .medium)
        spinner.color = .secondaryLabel
        spinner.startAnimating()
        spinner.translatesAutoresizingMaskIntoConstraints = false
        button.addSubview(spinner)
        NSLayoutConstraint.activate([
            spinner.centerXAnchor.constraint(equalTo: button.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: button.centerYAnchor),
            spinner.widthAnchor.constraint(equalToConstant: 20),
            spinner.heightAnchor.constraint(equalToConstant: 20),
        ])

        let item = UIBarButtonItem(customView: button)
        if usesSystemBars, #available(iOS 27.1, *) { item.axisBehavior = .verticalPreferred }
        // Same as iconBarItem: keep the shared bar background so _UIBarBackground is built.
        return item
    }

    private func makeActionTarget(
        role: NativeBarActionRole,
        onInvoke: @escaping () -> Void,
    ) -> NativeBarActionTarget {
        let target = NativeBarActionTarget(onInvoke: onInvoke)
        actionTargets[role] = target
        retainedActionTargets.append(target)
        return target
    }

    private func updateActionTargets(_ action: NativeBarAction) {
        actionTargets[.refresh]?.update(action.canRefresh ? action.onClick : nil)
        actionTargets[.status]?.update(action.onStatusClick)
        actionTargets[.extra]?.update(action.onExtraClick)
        actionTargets[.spinner]?.update(action.onStatusClick)
    }

    private func updateActionVisuals(_ action: NativeBarAction) {
        // Compose reports the bar on every recomposition (many per second while syncing).
        // Re-assigning identical images/titles to bar items relayouts UINavigationBar each
        // time; on device that cost ~40% of the main thread during sync.
        let visualKey = [action.status, action.label, action.extraLabel ?? ""]
        guard visualKey != lastActionVisualKey else { return }
        lastActionVisualKey = visualKey
        systemActionItems[.status]?.image = UIImage(systemName: symbolName(for: action.status, kind: .status))
        systemActionItems[.status]?.accessibilityLabel = action.status
        systemActionItems[.status]?.title = action.status
        systemActionItems[.refresh]?.title = action.label
        actionButtons[.status]?.update(
            symbolName: symbolName(for: action.status, kind: .status),
            accessibilityLabel: action.status,
        )
        actionButtons[.refresh]?.accessibilityLabel = action.label
        actionButtons[.extra]?.accessibilityLabel = action.extraLabel == "今" ? "回到今天" : action.extraLabel
    }

    private func symbolName(for label: String, kind: NativeBarActionItemKind) -> String {
        switch kind {
        case .refresh:
            return "arrow.clockwise"
        case .extra:
            if label.contains("写信") {
                return "square.and.pencil"
            }
            if label.localizedCaseInsensitiveContains("日历") {
                return "calendar.badge.plus"
            }
            if label.contains("导出") { return "square.and.arrow.up" }
            return "ellipsis.circle"
        case .status:
            if label.contains("失败") || label.contains("错误") {
                return "exclamationmark.triangle"
            }
            if label.contains("已同步") || label.contains("成功") || label.contains("完成") {
                return "checkmark.circle"
            }
            return "info.circle"
        }
    }

    private func applyAction(_ action: NativeBarAction?) {
        guard let controller else { return }
        (controller as? SafeAreaComposeHost)?.setScrollController(action?.scrollController)
        guard let action else {
            retainedActionTargets.removeAll()
            actionTargets.removeAll()
            actionButtons.removeAll()
            systemActionItems.removeAll()
            lastActionLayoutKey = nil
            lastActionVisualKey = nil
            UIView.performWithoutAnimation {
                controller.navigationItem.leftBarButtonItems = nil
                controller.navigationItem.leftItemsSupplementBackButton = false
                controller.navigationItem.rightBarButtonItems = nil
            }
            ensureTransparentBar()
            applyGlassProgress(0)
            return
        }
        let layoutKey = NativeBarActionLayoutKey(action)
        let vertical = usesSystemBars && nativeBarIsVertical(in: controller.traitCollection)
        if layoutKey == lastActionLayoutKey && lastActionVertical == vertical {
            // The only expected change here is scroll-driven glass/title progress or a
            // freshly captured Kotlin callback. Keep every UIKit view alive.
            updateActionTargets(action)
            updateActionVisuals(action)
            applyGlassProgress(CGFloat(action.scrollProgress))
            return
        }
        lastActionLayoutKey = layoutKey
        lastActionVertical = vertical
        lastActionVisualKey = nil
        retainedActionTargets.removeAll()
        actionTargets.removeAll()
        actionButtons.removeAll()
        systemActionItems.removeAll()
        var items: [UIBarButtonItem] = []
        var leftItems: [UIBarButtonItem] = []
        // Login and content sync share the same trailing spinner. Busy must
        // still draw when canRefresh is false (silent auto-login sets it so
        // the user cannot start another refresh mid-login).
        let refreshItem: UIBarButtonItem?
#if DEBUG
        let isBusy = action.busy || (ProcessInfo.processInfo.arguments.contains("--layout-smoke") && ProcessInfo.processInfo.arguments.contains("--chrome-busy"))
#else
        let isBusy = action.busy
#endif
        if isBusy {
            let title = action.label.isEmpty ? action.status : action.label
            refreshItem = spinnerBarItem(title: title.isEmpty ? "加载中" : title)
        } else if action.canRefresh {
            refreshItem = iconBarItem(
                title: action.label,
                symbolName: symbolName(for: action.label, kind: .refresh),
                role: .refresh,
                onClick: action.onClick,
            )
        } else {
            refreshItem = nil
        }
        let statusItem = action.status.isEmpty
            ? nil
            : iconBarItem(
                title: action.status,
                symbolName: symbolName(for: action.status, kind: .status),
                role: .status,
                onClick: action.onStatusClick,
                enabled: action.onStatusClick != nil,
            )

        if let statusItem, let refreshItem {
            // rightBarButtonItems is laid out from index 0 at the trailing
            // edge, so refresh is first and status follows after a fixed gap.
            items.append(refreshItem)
            if !usesSystemBars { items.append(fixedActionSpacing()) }
            items.append(statusItem)
        } else if let statusItem {
            items.append(statusItem)
        } else if let refreshItem {
            items.append(refreshItem)
        }
        if let extraLabel = action.extraLabel, let onExtraClick = action.onExtraClick {
            if extraLabel == "今" {
                leftItems.append(todayBarItem(vertical: vertical, onClick: onExtraClick))
            } else {
                leftItems.append(
                    iconBarItem(
                        title: extraLabel,
                        symbolName: symbolName(for: extraLabel, kind: .extra),
                        role: .extra,
                        onClick: onExtraClick,
                    )
                )
            }
        }
        if usesSystemBars {
            // In a horizontal bar, Today and export have their own leading placement so the
            // status/refresh group leaves a readable gap around the centered title.
            // Duo's vertical layout keeps all three in the same side group.
            let leadingExtra = !vertical && (action.extraLabel == "今" || action.extraLabel?.contains("导出") == true ||
                action.extraLabel?.contains("添加到日历") == true)
            if !leadingExtra {
                items.append(contentsOf: leftItems)
                leftItems.removeAll()
            }
        }
        UIView.performWithoutAnimation {
            controller.navigationItem.leftItemsSupplementBackButton = !leftItems.isEmpty
            controller.navigationItem.leftBarButtonItems = leftItems.isEmpty ? nil : leftItems
            controller.navigationItem.rightBarButtonItems = items.isEmpty ? nil : items
            if usesSystemBars {
                let ordered = Array(items.reversed())
                if vertical && action.extraLabel == "今", let today = ordered.first {
                    // Separate groups give Today its own system glass circle.
                    controller.navigationItem.trailingItemGroups = [
                        UIBarButtonItemGroup(barButtonItems: [today], representativeItem: nil),
                        UIBarButtonItemGroup(barButtonItems: Array(ordered.dropFirst()), representativeItem: nil),
                    ].filter { !$0.barButtonItems.isEmpty }
                } else {
                    controller.navigationItem.trailingItemGroups = ordered.isEmpty ? [] : [
                        UIBarButtonItemGroup(barButtonItems: ordered, representativeItem: nil)
                    ]
                }
            }
        }
        ensureTransparentBar()
        applyGlassProgress(CGFloat(action.scrollProgress))
    }

    /// Transparent bar background setup (titles only). Assigned on structural changes;
    /// the glass itself is a dedicated overlay driven per-frame below.
    ///
    /// Rationale, all verified live: manually built appearances ignore configureWith*
    /// on iOS 27, and sharesBackground=false suppresses _UIBarBackground entirely — so
    /// the appearance path can only do titles, never glass.
    ///
    /// Keep the title hierarchy stable. Compose's Skia scroll container is not
    /// a UIKit scroll view, so manually switching large-title display modes
    /// leaves UIKit's old large-title height behind. The title stays compact
    /// while the content moves underneath it.
    private func ensureTransparentBar() {
        guard !usesSystemBars, let controller, let navigationController = controller.navigationController else { return }
        let appearance = UINavigationBarAppearance()
        appearance.configureWithTransparentBackground()
        // Explicitly remove the default material too: a clear tint alone still
        // leaves UIKit's background effect visible at the unscrolled edge.
        appearance.backgroundEffect = nil
        appearance.backgroundColor = .clear
        appearance.shadowColor = .clear
        appearance.titleTextAttributes = [
            // The visible title is the single UIKit label pinned to the
            // navigation-bar center. Keep UINavigationItem.title populated for
            // UIKit's visibility/accessibility semantics, but hide its
            // collision-avoiding copy so it cannot flash or drift sideways.
            .foregroundColor: UIColor.clear,
            .font: UIFontMetrics(forTextStyle: .title3).scaledFont(
                for: UIFont.systemFont(ofSize: 21, weight: .semibold),
            ),
        ]
        appearance.titlePositionAdjustment = UIOffset(horizontal: 0, vertical: 1)
        UIView.performWithoutAnimation {
            navigationController.navigationBar.standardAppearance = appearance
            navigationController.navigationBar.scrollEdgeAppearance = appearance
            navigationController.navigationBar.compactAppearance = appearance
            navigationController.navigationBar.compactScrollEdgeAppearance = appearance
        }
    }

    /// Glass intensity follows scroll depth directly (0 top → 1). Direct tracking needs
    /// no animation: it cannot lag the finger, overshoot, or flash, and it is
    /// Reduce-Motion-safe (no autonomous motion). Epsilon cuts redundant writes.
    private func applyGlassProgress(_ progress: CGFloat) {
        guard let controller, let navigationController = controller.navigationController else { return }
        let reachesEndpoint = progress == 0 || progress == 1
        if progress != lastGlassProgress && (reachesEndpoint || abs(progress - lastGlassProgress) > 0.005) {
            lastGlassProgress = progress
            if let host = controller as? SafeAreaComposeHost {
                // The host merges this with UIKit rubber-band displacement Compose never sees.
                host.setComposeGlassProgress(progress)
            } else {
                (navigationController as? TabRootNavigationController)?.setTopGlassProgress(progress, for: controller)
            }
        }
    }


}

private enum NativeBarActionRole: Hashable {
    case refresh
    case status
    case extra
    case spinner
}

private struct NativeBarActionLayoutKey: Equatable {
    let hasStatus: Bool
    let canRefresh: Bool
    let busy: Bool
    let hasStatusAction: Bool
    let extraLabel: String?
    let hasExtraAction: Bool

    init(_ action: NativeBarAction) {
        hasStatus = !action.status.isEmpty
        canRefresh = action.canRefresh
        busy = action.busy
        hasStatusAction = action.onStatusClick != nil
        extraLabel = action.extraLabel
        hasExtraAction = action.onExtraClick != nil
    }
}

private final class NativeBarActionTarget: NSObject {
    private var onInvoke: (() -> Void)?

    init(onInvoke: @escaping () -> Void) {
        self.onInvoke = onInvoke
    }

    func update(_ onInvoke: (() -> Void)?) {
        self.onInvoke = onInvoke
    }

    @objc func invoke(_ sender: UIControl) {
        onInvoke?()
    }
}

private enum NativeBarActionItemKind {
    case refresh
    case status
    case extra
}

private final class NativeBarIconButton: UIButton {
    private var currentSymbolName: String?
    init(title: String) {
        super.init(frame: .zero)
        setTitle(title, for: .normal)
        setTitleColor(.label, for: .normal)
        titleLabel?.font = .systemFont(ofSize: 18, weight: .semibold)
        setVisualEnabled(true)
    }

    init(symbolName: String) {
        super.init(frame: .zero)
        update(symbolName: symbolName, accessibilityLabel: nil)
        setVisualEnabled(true)
    }

    func update(symbolName: String, accessibilityLabel: String?) {
        let symbolConfiguration = UIImage.SymbolConfiguration(
            pointSize: 18,
            weight: .semibold,
        )
        if symbolName != currentSymbolName {
            currentSymbolName = symbolName
            setImage(UIImage(systemName: symbolName, withConfiguration: symbolConfiguration), for: .normal)
        }
        if let accessibilityLabel, self.accessibilityLabel != accessibilityLabel {
            self.accessibilityLabel = accessibilityLabel
        }
    }

    func setVisualEnabled(_ enabled: Bool) {
        isEnabled = enabled
        alpha = enabled ? 1.0 : 0.46
        tintColor = enabled ? .label : .secondaryLabel
    }

    override var intrinsicContentSize: CGSize {
        CGSize(width: 32, height: 32)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

private final class NativeSpinnerButton: UIButton {
    override var intrinsicContentSize: CGSize {
        // Keep busy and idle action carriers equally compact so the title never
        // changes position when synchronization completes.
        CGSize(width: 32, height: 32)
    }
}

/// Scroll-edge Liquid Glass behind the native title, above Compose content.
///
/// Why an overlay instead of UINavigationBarAppearance.backgroundEffect (verified live):
/// manually built appearances ignore configureWith* on iOS 27, and
/// sharesBackground=false suppresses _UIBarBackground entirely. The overlay uses the
/// same Regular glass as the native sheets (see BJTUInstallNativeSheetMaterial).
/// Visibility follows the actual Compose scroll state. Duo's vertical system bar has no
/// scroll-edge effect by default, so this material is only used with horizontal bars.
private final class NativeNavigationBarGlassView: UIVisualEffectView {
    private var scrollProgress: CGFloat = 0
    var hasVisibleMaterial: Bool { scrollProgress > 0 }

    init() {
        super.init(effect: Self.barGlassEffect())
        backgroundColor = .clear
        isOpaque = false
        isUserInteractionEnabled = false
        autoresizingMask = [.flexibleWidth, .flexibleBottomMargin]
        // Restore 5ced798's continuous fade. Later effect-view masks changed
        // the material's reveal semantics and failed the user's device check.
        alpha = 0
        isHidden = true
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func setProgress(_ progress: CGFloat) {
        scrollProgress = min(max(progress, 0), 1)
        // One continuous scroll value drives the whole native glass surface,
        // exactly as setTopGlassAlpha did before the 1.8.1 mask changes.
        alpha = scrollProgress
        isHidden = !hasVisibleMaterial
    }

    private static func barGlassEffect() -> UIVisualEffect {
        if #available(iOS 26.0, *) {
            let glass = UIGlassEffect(style: .regular)
            glass.isInteractive = true
            // Pull the glass tone toward the page background so the bar doesn't read as
            // a separate bright band over dark content (or vice versa in light mode).
            // Explicit provider (not withAlphaComponent) so it keeps following traits.
            glass.tintColor = UIColor { traits in
                appBackgroundUIColor.resolvedColor(with: traits).withAlphaComponent(0.4)
            }
            return glass
        } else {
            return UIBlurEffect(style: .systemMaterial)
        }
    }
}

/// One native UILabel is overlaid on the navigation bar's own coordinate space.
/// `UINavigationItem.title` deliberately avoids this because UIKit shifts it to
/// avoid a long trailing action group; the app needs a title that stays centered
/// while those actions change asynchronously.
private final class NativeCenteredNavigationTitle: UILabel {
    override init(frame: CGRect) {
        super.init(frame: frame)
        font = UIFontMetrics(forTextStyle: .title3).scaledFont(
            for: UIFont.systemFont(ofSize: 21, weight: .semibold),
        )
        adjustsFontForContentSizeCategory = true
        textAlignment = .center
        numberOfLines = 1
        lineBreakMode = .byTruncatingTail
        textColor = .label
        isUserInteractionEnabled = false
        accessibilityTraits = .header
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

private func nativeBarIsVertical(in traits: UITraitCollection) -> Bool {
    if #available(iOS 27.1, *) { return traits.verticalBarEdge != .unspecified }
    return false
}

/// One physical vertical scroller. Compose's own vertical gesture/physics is disabled.
private final class NativePageScrollView: UIScrollView, UIGestureRecognizerDelegate {
    override func touchesShouldCancel(in view: UIView) -> Bool { true }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let hit = super.hitTest(point, with: event)
        // A touch that stops UIKit inertia belongs to the scroller, not a card
        // in the fixed Compose canvas underneath it.
        return isDecelerating && hit != nil ? self : hit
    }

    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard gestureRecognizer === panGestureRecognizer else { return true }
        let velocity = panGestureRecognizer.velocity(in: self)
        return abs(velocity.y) >= abs(velocity.x)
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        // Compose clicks must be cancelled when UIKit claims a vertical drag.
        // Horizontal paging still wins when our vertical pan declines to begin.
        otherGestureRecognizer is UIScreenEdgePanGestureRecognizer
    }
}

/// UIKit scrolls and animates chrome; the fixed-size Skia viewport renders consumed deltas.
private final class SafeAreaComposeHost: UIViewController, UIScrollViewDelegate {
    private let content: UIViewController
    private let session: AuthenticatedSession
    private var contentConstraints: [NSLayoutConstraint] = []
    private var contentLayoutVertical: Bool?
    private var nativeScrollView: NativePageScrollView?
    private var scrollController: NativeScrollController?
    private var renderedOffset: CGFloat = 0
    private var nativeRange: CGFloat = 0
    private var updatingRange = false
    private var aligningToStart = false
    /// Glass is driven by the larger of Compose's scroll progress and the UIKit
    /// rubber-band displacement. A page that fits the viewport never scrolls in
    /// Compose, yet pulling it up still slides content under the bar.
    private var composeGlassProgress: CGFloat = 0
    private var overscrollGlassProgress: CGFloat = 0
    private var pushedGlassProgress: CGFloat = -1
    var hasNativeScroller: Bool { scrollController != nil }

    func setComposeGlassProgress(_ progress: CGFloat) {
        composeGlassProgress = progress
        pushTopGlassProgress()
    }

    private func pushTopGlassProgress() {
        let progress = max(composeGlassProgress, overscrollGlassProgress)
        guard progress != pushedGlassProgress else { return }
        pushedGlassProgress = progress
        (navigationController as? TabRootNavigationController)?.setTopGlassProgress(progress, for: self)
    }

    private func updateOverscrollGlass(_ scrollView: UIScrollView) {
        // Same 52pt ramp as Compose's topBarTransitionHeight.
        let band = max(0, scrollView.contentOffset.y - renderedOffset)
        let progress = hasNativeScroller ? min(1, band / 52) : 0
        let reachesEndpoint = progress == 0 || progress == 1
        guard progress != overscrollGlassProgress,
              reachesEndpoint || abs(progress - overscrollGlassProgress) > 0.005 else { return }
        overscrollGlassProgress = progress
        pushTopGlassProgress()
    }

    init(content: UIViewController, session: AuthenticatedSession) {
        self.content = content
        self.session = session
        super.init(nibName: nil, bundle: nil)
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = appBackgroundUIColor
        addChild(content)
        content.view.translatesAutoresizingMaskIntoConstraints = false
        // Establish the final viewport before Compose's first action report. Never
        // reparent the Metal surface or resize it during a navigation transition.
        let scroller = NativePageScrollView(frame: view.bounds)
        scroller.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        scroller.contentInsetAdjustmentBehavior = .never
        scroller.showsVerticalScrollIndicator = false
        scroller.isDirectionalLockEnabled = true
        scroller.delaysContentTouches = true
        scroller.canCancelContentTouches = true
        scroller.panGestureRecognizer.cancelsTouchesInView = true
        scroller.alwaysBounceVertical = true
        scroller.isScrollEnabled = false
        scroller.delegate = self
        scroller.panGestureRecognizer.delegate = scroller
        scroller.backgroundColor = appBackgroundUIColor
        if #available(iOS 26.0, *) { scroller.topEdgeEffect.isHidden = true }
        nativeScrollView = scroller
        view.addSubview(scroller)
        scroller.addSubview(content.view)
        content.didMove(toParent: self)
        updateBarLayout()
    }

    func setScrollController(_ controller: NativeScrollController?) {
        loadViewIfNeeded()
        let wasEnabled = hasNativeScroller
        // An exact false edge transition represents a real Compose action such as
        // Today scrolling to the first item. A stale initial false snapshot does not.
        let returnedToTop = scrollController?.canScrollBackward == true &&
            controller?.canScrollBackward == false && renderedOffset > 0.5
        scrollController = controller
        if wasEnabled != hasNativeScroller {
            nativeScrollView?.isScrollEnabled = hasNativeScroller
            setContentScrollView(hasNativeScroller ? nativeScrollView : nil, for: .top)
            if !hasNativeScroller, let scroller = nativeScrollView {
                // Loading/empty content can remove the consumer. Drop its old range
                // and rubber-band displacement without changing the viewport.
                updatingRange = true
                renderedOffset = 0
                nativeRange = 0
                scroller.setContentOffset(.zero, animated: false)
                scroller.contentSize = scroller.bounds.size
                content.view.transform = .identity
                updatingRange = false
                overscrollGlassProgress = 0
                pushTopGlassProgress()
            }
            if let navigationController = navigationController as? TabRootNavigationController {
                navigationController.configureScrollMinimization(for: self, enabled: hasNativeScroller)
            }
        }
        if returnedToTop {
            renderedOffset = 0
            nativeScrollView?.setContentOffset(.zero, animated: false)
            content.view.transform = .identity
        }
        updateNativeRange()
        alignNativeOriginIfNeeded()
        updateTopEdgeEffect()
    }

    private func updateTopEdgeEffect() {
        // Compose reserves the title clearance inside its content; UIKit's
        // scroll view starts behind the bar even when the page is at its top.
        // Gate only the top edge, leaving the Duo sidebar and its native
        // minimization under UIKit's control. Negative rubber-band offsets
        // must remain clear as well.
        if #available(iOS 26.0, *), let scroller = nativeScrollView {
            // Content is under the bar whenever UIKit's offset is positive, including an
            // upward rubber band on a page too short to scroll in Compose.
            scroller.topEdgeEffect.isHidden = !hasNativeScroller || scroller.contentOffset.y <= 0.5
        }
    }

    private func alignNativeOriginIfNeeded() {
        guard let controller = scrollController, let scroller = nativeScrollView else { return }
        if !controller.canScrollBackward || renderedOffset > 0.5 { aligningToStart = false }
        guard !aligningToStart, controller.canScrollBackward,
              renderedOffset <= 0.5, abs(scroller.contentOffset.y) <= 0.5,
              !scroller.isTracking, !scroller.isDecelerating else { return }
        // A resized LazyColumn can preserve an old physical anchor while UIKit is
        // at zero. Fix only this idle origin mismatch, never a live native gesture.
        aligningToStart = true
        controller.alignToStart()
        content.view.transform = .identity
    }

    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        updateBarLayout()
    }
    private func updateBarLayout() {
        let vertical = nativeBarIsVertical(in: traitCollection)
        let itemStyle: UINavigationItem.ItemStyle = vertical ? .browser : .navigator
        if navigationItem.style != itemStyle { navigationItem.style = itemStyle }
        guard contentLayoutVertical != vertical || contentConstraints.isEmpty else { return }
        NSLayoutConstraint.deactivate(contentConstraints)
        guard let scroller = nativeScrollView else { return }
        // Identical constraints before and after the consumer arrives. All hosted
        // pages reserve the header within Compose, using the measured UIKit inset.
        contentConstraints = [
            content.view.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            content.view.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            content.view.topAnchor.constraint(equalTo: scroller.frameLayoutGuide.topAnchor),
            content.view.bottomAnchor.constraint(equalTo: scroller.frameLayoutGuide.bottomAnchor),
        ]
        NSLayoutConstraint.activate(contentConstraints)
        contentLayoutVertical = vertical
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateNativeRange()
        guard view.window != nil, navigationController?.topViewController === self else { return }
        let bottom = Float(view.safeAreaInsets.bottom)
        if abs(session.systemBottomInsetDp - bottom) > 0.5 { session.systemBottomInsetDp = bottom }
    }

    private func updateNativeRange() {
        guard !updatingRange, let controller = scrollController, let scroller = nativeScrollView else { return }
        let scale = max(1, traitCollection.displayScale)
        let height = max(1, scroller.bounds.height)
        if controller.maxOffsetPx >= 0 {
            nativeRange = CGFloat(controller.maxOffsetPx) / scale
        } else if controller.canScrollForward {
            // Virtualized content supplies exact consumed deltas and an exact end flag.
            // Reserve viewport-sized headroom; never infer an offset from item indices.
            nativeRange = max(nativeRange, renderedOffset + height * 2)
        } else {
            nativeRange = renderedOffset
        }
        let size = CGSize(width: max(1, scroller.bounds.width), height: height + nativeRange)
        if abs(scroller.contentSize.height - size.height) > 0.5 || scroller.contentSize.width != size.width {
            updatingRange = true
            scroller.contentSize = size
            updatingRange = false
        }

    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        guard !updatingRange, let controller = scrollController else { return }
        let scale = max(1, traitCollection.displayScale)
        let position = min(max(0, scrollView.contentOffset.y), nativeRange)
        let delta = position - renderedOffset
        if abs(delta) > 0.001 {
            let consumed = CGFloat(controller.consumeScroll(deltaPx: Float(delta * scale))) / scale
            renderedOffset += consumed
            if delta > 0 && consumed < delta - 0.5 {
                // Compose reports the actual end; let UIKit own the ensuing rubber band.
                nativeRange = renderedOffset
                updatingRange = true
                scrollView.contentSize.height = scrollView.bounds.height + nativeRange
                updatingRange = false
            }
        }
        // The displacement comes directly from UIKit's rubber band, with no app timer,
        // easing curve, accumulated gesture translation or independent animation.
        content.view.transform = CGAffineTransform(translationX: 0, y: -(scrollView.contentOffset.y - renderedOffset))
        updateTopEdgeEffect()
        updateOverscrollGlass(scrollView)
        if controller.maxOffsetPx < 0 && controller.canScrollForward && renderedOffset > nativeRange - scrollView.bounds.height {
            updateNativeRange()
        }
    }
}

private protocol NativeChromeHosting: AnyObject {
    func setNativeTitle(_ title: String)
    func refreshNavigationBarVisibility()
}

/// 单个一级入口的原生导航栈：根页面与 push 后的二级页都使用稳定的 UIKit 行内标题，
/// Compose 只负责正文，系统导航栏统一承载标题、同步状态和页面动作。
private final class TabRootNavigationController: UINavigationController, UINavigationControllerDelegate, UIGestureRecognizerDelegate, NativeChromeHosting {
    private let session: AuthenticatedSession
    private let tabRouteId: String
    private let selectTab: (String) -> Void
    let usesSystemBars: Bool
    private var centeredTitleLabel: NativeCenteredNavigationTitle?
    private var navigationBarHiddenState: Bool?
    /// 原生导航栏实际占掉的顶部高度（栏底 maxY，含状态栏），推给 Compose 做滚动内容顶边距；
    /// 内容经过横向玻璃，滚动方向驱动 iOS 27 原生栏划出。栏隐藏时推 0。
    private var pushedTopInset: CGFloat = -1
    private var headerGeometry: CGSize = .zero
    private var headerVertical: Bool?
    private var expandedHeaderHeight: CGFloat = 0
    private var expandedHeaderTop: CGFloat = 0
    private var headerTextCategory: UIContentSizeCategory?
    private var navigationBarGlassView: NativeNavigationBarGlassView?
    private var systemTitleVertical: Bool?
    private let glassState = NativeNavigationGlassState<ObjectIdentifier>()
    /// 二级页盖住底栏玻璃。玻璃留在原位，这里不负责把它收起或延后显示。
    var onNavigationWillShow: ((UINavigationController, UIViewController, Bool) -> Void)?

    init(
        session: AuthenticatedSession,
        tabRouteId: String,
        selectTab: @escaping (String) -> Void,
        usesSystemBars: Bool = false
    ) {
        self.session = session
        self.tabRouteId = tabRouteId
        self.selectTab = selectTab
        self.usesSystemBars = usesSystemBars
        super.init(nibName: nil, bundle: nil)
        delegate = self
        // 一级页固定使用一条紧凑的原生标题栏；正文滚动不改变导航栏高度，
        // 避免 Compose/UIKit 两套滚动模型互相错位。
        navigationBar.prefersLargeTitles = false
        installInteractivePopGesture(on: self)
    }

    @available(*, unavailable)
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func configureNativeBarMinimization(for item: UINavigationItem) {
        guard usesSystemBars else { return }
        if #available(iOS 27.0, *) {
            // Keep viewport geometry stable from creation, before Compose reports
            // its primary scroll consumer. UIKit alone animates the navigation bar.
            item.navigationBarMinimization.minimizationBehavior = .automatic
            item.navigationBarMinimization.safeAreaAdjustment = .disabled
        }
    }

    func configureScrollMinimization(for owner: UIViewController, enabled: Bool) {
        guard usesSystemBars, #available(iOS 27.0, *) else { return }
        let vertical = nativeBarIsVertical(in: traitCollection)
        var configuration = owner.navigationItem.navigationBarMinimization
        let oldBehavior = configuration.minimizationBehavior
        let oldAdjustment = configuration.safeAreaAdjustment
        configuration.minimizationBehavior = enabled && vertical ? .onScrollDown : .automatic
        configuration.safeAreaAdjustment = .disabled
        if oldBehavior != configuration.minimizationBehavior || oldAdjustment != configuration.safeAreaAdjustment {
            owner.navigationItem.navigationBarMinimization = configuration
        }
    }

    /// Compose 根控制器推迟到这里创建，冷启动不必同时付多份组合与 Metal 层的成本。
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = appBackgroundUIColor
        guard viewControllers.isEmpty else { return }
        navigationBar.clipsToBounds = false
        // Horizontal scroll-underlap uses NativeNavigationBarGlassView or the
        // native scroller's top edge. Keep the bar itself clear from its first
        // frame; Duo's sidebar material/minimization remains owned by UIKit.
        let glassView = NativeNavigationBarGlassView()
        navigationBarGlassView = glassView
        view.insertSubview(glassView, belowSubview: navigationBar)
        let appearance = UINavigationBarAppearance()
        appearance.configureWithTransparentBackground()
        if usesSystemBars {
            // Preserve UIKit's native side-bar material and scroll behavior.
            appearance.titleTextAttributes = [.font: UIFontMetrics(forTextStyle: .headline).scaledFont(for: UIFont.systemFont(ofSize: 20, weight: .semibold))]
        } else {
            // A clear color alone can retain the default material at rest.
            appearance.backgroundEffect = nil
            appearance.backgroundColor = .clear
            appearance.shadowColor = .clear
        }
        navigationBar.standardAppearance = appearance
        navigationBar.scrollEdgeAppearance = appearance
        navigationBar.compactAppearance = appearance
        navigationBar.compactScrollEdgeAppearance = appearance
        let binding = NativeChromeBinding()
        let composeRoot = MainViewControllerKt.NativeTabRootViewController(
            session: session,
            routeId: tabRouteId,
            onOpenNativeRoute: { [weak self] routeId in
                self?.openNativeRoute(routeId)
            },
            onCloseNativeRoute: { [weak self] in
                self?.popViewController(animated: true)
            },
            // 一级页的标题与右上胶囊都由系统栏承载：静态标题先放好，动态回报再覆盖。
            onTitleChanged: { title in
                DispatchQueue.main.async { binding.apply(title) }
            },
            onActionChanged: { action in
                DispatchQueue.main.async { binding.apply(action: action) }
            },
            onSelectNativeTab: { [weak self] routeId in
                self?.selectTab(routeId)
            }
        )
        let root = usesSystemBars ? SafeAreaComposeHost(content: composeRoot, session: session) : composeRoot
        if usesSystemBars { root.navigationItem.style = nativeBarIsVertical(in: traitCollection) ? .browser : .navigator }
        root.restorationIdentifier = tabRouteId
        let rootTitle = NativeShellKt.nativeRouteTitle(routeId: tabRouteId)
        root.navigationItem.title = rootTitle
        root.navigationItem.largeTitleDisplayMode = .never
        configureNativeBarMinimization(for: root.navigationItem)
        configureComposeHost(root)

        binding.controller = root
        setViewControllers([root], animated: false)
        setNativeTitle(rootTitle)
        updateInteractivePopEnabled()
    }

    func openNativeRouteFromHost(_ routeId: String) {
        openNativeRoute(routeId)
    }

    private func openNativeRoute(_ routeId: String) {
        guard topViewController?.restorationIdentifier != routeId else { return }
        let binding = NativeChromeBinding()
        let composeDestination = MainViewControllerKt.NativeDestinationViewController(
            session: session,
            routeId: routeId,
            useNativeTitleBar: true,
            onOpenNativeRoute: { [weak self] childRouteId in
                self?.openNativeRoute(childRouteId)
            },
            onCloseNativeRoute: { [weak self] in
                self?.popViewController(animated: true)
            },
            onTitleChanged: { title in
                DispatchQueue.main.async { binding.apply(title) }
            },
            onActionChanged: { action in
                DispatchQueue.main.async { binding.apply(action: action) }
            },
            onSelectNativeTab: { [weak self] targetRouteId in
                self?.selectTab(targetRouteId)
            }
        )
        let destination = usesSystemBars ? SafeAreaComposeHost(content: composeDestination, session: session) : composeDestination
        if usesSystemBars { destination.navigationItem.style = nativeBarIsVertical(in: traitCollection) ? .browser : .navigator }
        destination.restorationIdentifier = routeId
        let destinationTitle = NativeShellKt.nativeRouteTitle(routeId: routeId)
        destination.navigationItem.title = destinationTitle
        // 被 push 的页也使用行内标题，保持根页与二级页的高度和排版一致。
        destination.navigationItem.largeTitleDisplayMode = .never
        configureNativeBarMinimization(for: destination.navigationItem)
        configureComposeHost(destination)
        if usesSystemBars {
            destination.hidesBottomBarWhenPushed = true
        }
        binding.controller = destination
        setNativeTitle(destinationTitle)
        // 先把目的地按最终尺寸 layout 一次，让 Compose 在转场开始前就开始画，
        // 减少滑动过程中 Metal 还没首帧的空窗。
        destination.loadViewIfNeeded()
        destination.view.frame = view.bounds
        destination.view.setNeedsLayout()
        destination.view.layoutIfNeeded()
        pinComposeSurfaceColor(destination.view)
        pushViewController(destination, animated: true)
    }

    // MARK: - NativeChromeHosting

    func setNativeTitle(_ title: String) {
        if usesSystemBars && nativeBarIsVertical(in: traitCollection) {
            centeredTitleLabel?.isHidden = true
            return
        }
        guard !title.isEmpty else {
            centeredTitleLabel?.text = nil
            centeredTitleLabel?.isHidden = true
            return
        }
        let label: NativeCenteredNavigationTitle
        if let centeredTitleLabel {
            label = centeredTitleLabel
        } else {
            label = NativeCenteredNavigationTitle(frame: .zero)
            label.translatesAutoresizingMaskIntoConstraints = false
            navigationBar.addSubview(label)
            NSLayoutConstraint.activate([
                label.centerXAnchor.constraint(equalTo: navigationBar.centerXAnchor),
                label.centerYAnchor.constraint(equalTo: navigationBar.centerYAnchor, constant: 1),
                label.leadingAnchor.constraint(greaterThanOrEqualTo: navigationBar.leadingAnchor, constant: 16),
                label.trailingAnchor.constraint(lessThanOrEqualTo: navigationBar.trailingAnchor, constant: -16),
                label.heightAnchor.constraint(lessThanOrEqualToConstant: 44),
            ])
            centeredTitleLabel = label
        }
        // Same-value writes and subview reordering both invalidate the bar's layout.
        if label.text != title { label.text = title }
        if label.isHidden { label.isHidden = false }
        if navigationBar.subviews.last !== label { navigationBar.bringSubviewToFront(label) }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let vertical = usesSystemBars && nativeBarIsVertical(in: traitCollection)
        if usesSystemBars, systemTitleVertical != vertical {
            systemTitleVertical = vertical
            // UIKit can move its inline title to the leading edge to avoid a
            // long action group. Keep horizontal titles centered independently;
            // vertical layouts use the system's leading title representation.
            let appearance = navigationBar.standardAppearance.copy() as! UINavigationBarAppearance
            appearance.titleTextAttributes[.foregroundColor] = vertical ? UIColor.label : UIColor.clear
            navigationBar.standardAppearance = appearance
            navigationBar.scrollEdgeAppearance = appearance
            navigationBar.compactAppearance = appearance
            navigationBar.compactScrollEdgeAppearance = appearance
            setNativeTitle(topViewController?.navigationItem.title ?? "")
            if let topViewController {
                configureScrollMinimization(for: topViewController, enabled: (topViewController as? SafeAreaComposeHost)?.hasNativeScroller == true)
                restoreTopGlass(for: topViewController)
            }
        }
        let currentOwner = topViewController
        if let currentOwner {
            configureScrollMinimization(for: currentOwner, enabled: (currentOwner as? SafeAreaComposeHost)?.hasNativeScroller == true)
        }
        // Every system-bar host uses the same full-height viewport, including its
        // first frame. Compose consumes the header inset inside its list or spacer.
        // Duo can expose a top title while its reserved safe-area edge is on the
        // side. The safe-area top alone then misses the title's actual footprint.
        // Use UIKit's full bar fitting height, stable throughout minimization.
        let statusTop = currentOwner?.view.window?.safeAreaInsets.top ?? view.safeAreaInsets.top
        if !navigationBar.isHidden && (headerGeometry != view.bounds.size || headerVertical != vertical ||
            headerTextCategory != traitCollection.preferredContentSizeCategory || expandedHeaderHeight == 0) {
            headerGeometry = view.bounds.size
            headerVertical = vertical
            headerTextCategory = traitCollection.preferredContentSizeCategory
            expandedHeaderHeight = navigationBar.sizeThatFits(CGSize(width: view.bounds.width, height: 0)).height
            if let owner = currentOwner {
                let barRect = navigationBar.convert(navigationBar.bounds, to: owner.view)
                expandedHeaderTop = max(0, barRect.minY)
            }
        }
        let headerHeight = navigationBar.isHidden ? 0 : expandedHeaderHeight
        let topInset: CGFloat = usesSystemBars
            ? max(currentOwner?.view.safeAreaInsets.top ?? 0, expandedHeaderTop + headerHeight)
            : (navigationBar.isHidden ? 0 : navigationBar.frame.maxY)
        if let glassView = navigationBarGlassView {
            let glassBounds = CGRect(
                x: 0,
                y: 0,
                width: view.bounds.width,
                height: navigationBar.isHidden ? 0 : topInset,
            )
            glassView.frame = glassBounds
            glassView.isHidden = navigationBar.isHidden || vertical || !glassView.hasVisibleMaterial
            view.bringSubviewToFront(glassView)
            view.bringSubviewToFront(navigationBar)
        }
        if let centeredTitleLabel { navigationBar.bringSubviewToFront(centeredTitleLabel) }
        let ownsVisiblePage = !usesSystemBars || tabBarController?.selectedViewController === self
        if view.window != nil, ownsVisiblePage,
           abs(topInset - pushedTopInset) > 0.5 || abs(Float(topInset) - session.glassTopBarInsetDp) > 0.5 {
            pushedTopInset = topInset
            session.glassTopBarInsetDp = Float(topInset)
#if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--layout-metrics") {
                print("[LayoutMetrics] top=\(topInset) safe=\(currentOwner?.view.safeAreaInsets.top ?? 0) status=\(statusTop) header=\(headerHeight) bar=\(navigationBar.frame) viewport=\(currentOwner?.view.bounds ?? .zero)")
            }
#endif
        }
    }

    /// Horizontal bars retain their existing material. Vertical bars use UIKit's
    /// default layout; scroll updates never hide, move or resize the navigation bar.
    func setTopGlassProgress(_ progress: CGFloat, for owner: UIViewController) {
        if let visibleProgress = glassState.update(progress, for: ObjectIdentifier(owner)) {
            if (owner as? SafeAreaComposeHost)?.hasNativeScroller == true || (usesSystemBars && nativeBarIsVertical(in: traitCollection)) {
                navigationBarGlassView?.setProgress(0)
            } else {
                navigationBarGlassView?.setProgress(visibleProgress)
            }
        }
    }

    private func restoreTopGlass(for controller: UIViewController) {
        let progress = glassState.show(ObjectIdentifier(controller))
        if (controller as? SafeAreaComposeHost)?.hasNativeScroller == true || (usesSystemBars && nativeBarIsVertical(in: traitCollection)) {
            navigationBarGlassView?.setProgress(0)
        } else {
            navigationBarGlassView?.setProgress(progress)
        }
    }

    /// 只有一页例外不显示系统栏：没有标题的页（写信这类自绘返回的页）。一级 tab 根页现在也有标题，
    /// 所以不再按「是不是栈底」豁免。
    private func navigationBarShouldBeHidden(for viewController: UIViewController) -> Bool {
        viewController.navigationItem.title?.isEmpty != false
    }

    func refreshNavigationBarVisibility() {
        guard let top = topViewController else { return }
        let shouldHide = shouldHideBar(for: top)
        guard navigationBarHiddenState != shouldHide else { return }
        navigationBarHiddenState = shouldHide
        setNavigationBarHidden(shouldHide, animated: false)
    }

    private func shouldHideBar(for viewController: UIViewController) -> Bool {
        navigationBarShouldBeHidden(for: viewController)
    }

    private func updateInteractivePopEnabled() {
        interactivePopGestureRecognizer?.isEnabled = viewControllers.count > 1
    }

    // MARK: - UINavigationControllerDelegate

    func navigationController(
        _ navigationController: UINavigationController,
        willShow viewController: UIViewController,
        animated: Bool
    ) {
        restoreTopGlass(for: viewController)
        let shouldHide = shouldHideBar(for: viewController)
        navigationBarHiddenState = shouldHide
        setNavigationBarHidden(shouldHide, animated: animated)
        // 玻璃不跟着进二级页消失，也不等返回动画结束再出现。宿主让二级页盖在它上面。
        onNavigationWillShow?(navigationController, viewController, animated)
    }

    func navigationController(
        _ navigationController: UINavigationController,
        didShow viewController: UIViewController,
        animated: Bool
    ) {
        // A pop does not recreate Compose. Restore this page's own title and scroll state,
        // including a cancelled interactive pop, without requiring a new scroll event.
        glassState.retain(Set(viewControllers.map { ObjectIdentifier($0) }))
        restoreTopGlass(for: viewController)
        let shouldHide = shouldHideBar(for: viewController)
        navigationBarHiddenState = shouldHide
        setNavigationBarHidden(shouldHide, animated: false)
        setNativeTitle(viewController.navigationItem.title ?? "")
        updateInteractivePopEnabled()
        // 部分系统版本在 didShow 后会把 delegate 重置；每次确认仍由本类接管。
        installInteractivePopGesture(on: navigationController)
        updateInteractivePopEnabled()
    }

    // MARK: - UIGestureRecognizerDelegate

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard gestureRecognizer === interactivePopGestureRecognizer else { return true }
        return viewControllers.count > 1
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        gestureRecognizer === interactivePopGestureRecognizer
    }
}

/// 一级入口的系统玻璃 TabBar。tab 列表来自 Kotlin 的同一份 bottomNavSections，
/// 两端不会漂移；图标改用 SF Symbols，交给系统做选中态填充与玻璃着色。
private final class AppTabBarController: UIViewController, UITabBarDelegate, NativeTabsHosting {
    fileprivate static let symbolNames: [String: String] = [
        "HOME": "house",
        "SCHEDULE": "calendar",
        "GRADES": "list.bullet.rectangle",
        "HOMEWORK": "folder",
        "ASSIGNMENTS": "folder",
        "PHYVLAB": "atom",
        "CITEL": "curlybraces",
        "PHYSICS_LAB": "flask",
        "MORE": "square.grid.2x2",
        "EXAMS": "clock",
        "COURSEWARE": "books.vertical",
        "CLASSROOM_OCCUPANCY": "building.2",
        "MAILBOX": "envelope",
        "CALENDAR": "calendar",
        "REPORT_CARD_DOWNLOAD": "doc.text",
        "SETTINGS": "gearshape",
    ]

    private var tabRouteIds: [String] = []
    private var pendingItems: [NativeTabItem]
    private var selectedRouteId: String?
    private var activeController: TabRootNavigationController?
    private let nativeTabBar = UITabBar()
    private var tabStudentId: String?
    /// 玻璃底栏的真实占位高度要推给 Compose：宿主是全出血的，UIKit 不会把 tab bar 算进
    /// `WindowInsets.navigationBars`，而不可纵向滚动的全览表格（课程表色块概览）必须停在底栏上方。
    /// 弱引用即可：壳控制器已经强引用同一会话，这里只是取用，不该多持一份。
    private weak var session: AuthenticatedSession?
    private var pushedBottomInset: CGFloat = -1
    private var pushedSystemBottomInset: CGFloat = -1
    /// routeId → 该 tab 的导航栈。重配 tab 时按 routeId 复用，避免整条玻璃栏被拆掉、各 tab 返回栈丢失。
    private var controllersByRoute: [String: TabRootNavigationController] = [:]
    /// 二级页整页盖住玻璃时为 true。玻璃仍在原位，只是层级在页面下面。
    private var contentCoversTabBar = false
    /// 转场中玻璃被放进导航容器，夹在根页和二级页之间。这段时间不要重排它的 frame。
    private var tabBarBorrowed = false
    private var navigationTransitionGeneration = 0

    init(session: AuthenticatedSession) {
        pendingItems = NativeShellKt.nativeTabItems(session: session)
        tabStudentId = session.profile.studentId
        self.session = session
        super.init(nibName: nil, bundle: nil)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(appBackgroundColor)
        nativeTabBar.delegate = self
        nativeTabBar.itemPositioning = .fill
        nativeTabBar.isTranslucent = true
        nativeTabBar.autoresizingMask = [.flexibleWidth, .flexibleTopMargin]
        view.addSubview(nativeTabBar)
        guard let session else { return }
        apply(items: pendingItems, session: session)
#if DEBUG
        // 取证用：`simctl launch … --tab=SCHEDULE` 直接停在某个一级入口。模拟器没有无头点击的口子，
        // 而合成鼠标点击会抢用户焦点，所以把「切 tab」做成启动参数（与既有的 --security-smoke 同一套路）。
        for arg in ProcessInfo.processInfo.arguments where arg.hasPrefix("--tab=") {
            let routeId = String(arg.dropFirst("--tab=".count))
            select(routeId: routeId)
        }
        if ProcessInfo.processInfo.arguments.contains("--layout-smoke"),
           let route = ProcessInfo.processInfo.arguments.first(where: { $0.hasPrefix("--route=") }) {
            DispatchQueue.main.async { [weak self] in self?.activeController?.openNativeRouteFromHost(String(route.dropFirst(8))) }
        }
#endif
    }

    @available(*, unavailable)
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func reloadTabs(session: AuthenticatedSession) {
        // 登录流程会产出新的会话实例，而底栏高度是挂在实例上的状态：换实例时必须让去重失效，
        // 否则下一次布局过阈值才推、极端情况下永远不推，课程表又钻回底栏下面。
        if session !== self.session {
            self.session = session
            pushedBottomInset = -1
            pushedSystemBottomInset = -1
        }
        let items = NativeShellKt.nativeTabItems(session: session)
        let routeIds = items.map(\.routeId)
        let accountChanged = session.profile.studentId != tabStudentId
        tabStudentId = session.profile.studentId
        pendingItems = items
        guard isViewLoaded else { return }
        guard routeIds != tabRouteIds || accountChanged else { return }
        if accountChanged { removeAllControllers() }
        apply(items: items, session: session)
    }

    /// 一级入口整份交给 UIKit 的 UITabBar；不再裁成五项，也不创建系统 More 溢出页。
    private func apply(items: [NativeTabItem], session: AuthenticatedSession) {
        restoreTabBarToHost()
        let previousRouteId = selectedRouteId
        let nextRouteIds = items.map(\.routeId)
        for (routeId, controller) in controllersByRoute where !nextRouteIds.contains(routeId) {
            controller.onNavigationWillShow = nil
            controller.willMove(toParent: nil)
            if controller.isViewLoaded { controller.view.removeFromSuperview() }
            controller.removeFromParent()
        }
        controllersByRoute = controllersByRoute.filter { nextRouteIds.contains($0.key) }
        tabRouteIds = nextRouteIds
        for item in items where controllersByRoute[item.routeId] == nil {
            let controller = TabRootNavigationController(
                session: session,
                tabRouteId: item.routeId,
                selectTab: { [weak self] routeId in self?.select(routeId: routeId) }
            )
            controller.tabBarItem = UITabBarItem(
                title: item.title,
                image: UIImage(systemName: Self.symbolNames[item.routeId] ?? "circle"),
                selectedImage: nil
            )
            controller.onNavigationWillShow = { [weak self] navigationController, viewController, animated in
                self?.handleNavigationWillShow(navigationController, viewController, animated)
            }
            controllersByRoute[item.routeId] = controller
            addChild(controller)
            controller.didMove(toParent: self)
        }
        let nativeItems = items.map {
            UITabBarItem(
                title: $0.title,
                image: UIImage(systemName: Self.symbolNames[$0.routeId] ?? "circle"),
                selectedImage: nil
            )
        }
        let routeToSelect = previousRouteId.flatMap { nextRouteIds.contains($0) ? $0 : nil }
            ?? nextRouteIds.first
        // A preference change can remove the currently selected item (PHYVLAB).
        // Update the item list and selection in one animation-disabled transaction;
        // otherwise UITabBar briefly keeps its old selected item and visibly slides
        // to the removed tab before the fallback route is selected.
        selectedRouteId = routeToSelect
        UIView.performWithoutAnimation {
            // Clear the old item before replacing the collection. UIKit may
            // otherwise resolve the old index against the shorter list for one
            // layout pass, which is visible as a PHYVLAB -> fallback jump.
            nativeTabBar.selectedItem = nil
            nativeTabBar.setItems(nativeItems, animated: false)
            if let routeToSelect,
               let index = nextRouteIds.firstIndex(of: routeToSelect) {
                nativeTabBar.selectedItem = nativeItems[index]
            } else {
                nativeTabBar.selectedItem = nil
            }
        }
        if let routeToSelect {
            select(routeId: routeToSelect)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let intrinsicHeight = nativeTabBar.sizeThatFits(
            CGSize(width: view.bounds.width, height: CGFloat.greatestFiniteMagnitude)
        ).height
        // 玻璃一直占着原来的高度。二级页盖上来，不把高度收成 0，否则返回时要再长出来。
        let barHeight = max(80, intrinsicHeight + view.safeAreaInsets.bottom)
        if !tabBarBorrowed {
            nativeTabBar.isHidden = false
            nativeTabBar.frame = CGRect(
                x: 0,
                y: view.bounds.height - barHeight,
                width: view.bounds.width,
                height: barHeight
            )
            if let content = activeController?.view {
                content.frame = view.bounds
                if contentCoversTabBar {
                    view.insertSubview(content, aboveSubview: nativeTabBar)
                } else {
                    view.insertSubview(content, belowSubview: nativeTabBar)
                }
            }
        } else {
            activeController?.view.frame = view.bounds
        }

        if let session {
            let inset = barHeight
            if abs(inset - pushedBottomInset) > 0.5 {
                pushedBottomInset = inset
                session.glassTabBarBottomInsetDp = Float(inset)
            }
            // 根页使用完整 barHeight；二级页没有底栏，独立使用窗口安全区。
            let systemBottom = max(view.safeAreaInsets.bottom, view.window?.safeAreaInsets.bottom ?? 0)
            if abs(systemBottom - pushedSystemBottomInset) > 0.5 {
                pushedSystemBottomInset = systemBottom
                session.systemBottomInsetDp = Float(systemBottom)
            }
        }
    }

    /// 玻璃留在原位。涉及根页的转场里，把它夹在根页和二级页之间：二级页滑过时盖住它，
    /// 返回时它本来就露在下面。二级页之间的推进不把玻璃抬到中间。安卓是新 Activity 盖住底栏，
    /// 这里同一套，不做另一套消失动画。
    private func handleNavigationWillShow(
        _ navigationController: UINavigationController,
        _ viewController: UIViewController,
        _ animated: Bool
    ) {
        guard navigationController === activeController else { return }
        navigationTransitionGeneration += 1
        let generation = navigationTransitionGeneration
        let showingRoot = viewController === navigationController.viewControllers.first
        guard animated, let coordinator = navigationController.transitionCoordinator else {
            restoreTabBarToHost()
            contentCoversTabBar = !showingRoot
            view.setNeedsLayout()
            return
        }
        let fromIsRoot = coordinator.viewController(forKey: .from) === navigationController.viewControllers.first
        let toIsRoot = coordinator.viewController(forKey: .to) === navigationController.viewControllers.first
        if !fromIsRoot && !toIsRoot {
            restoreTabBarToHost()
            contentCoversTabBar = true
            view.setNeedsLayout()
            return
        }
        tabBarBorrowed = true
        placeTabBarBetweenPages(navigationController, coordinator)
        coordinator.animate(alongsideTransition: { [weak self] context in
            self?.placeTabBarBetweenPages(navigationController, context)
        }, completion: { [weak self] _ in
            guard let self, self.navigationTransitionGeneration == generation else { return }
            self.restoreTabBarToHost()
            let visibleIsRoot = navigationController.topViewController === navigationController.viewControllers.first
            self.contentCoversTabBar = !visibleIsRoot
            self.view.setNeedsLayout()
            self.view.layoutIfNeeded()
        })
    }

    private func placeTabBarBetweenPages(
        _ navigationController: UINavigationController,
        _ context: UIViewControllerTransitionCoordinatorContext
    ) {
        guard let fromView = context.viewController(forKey: .from)?.view,
              let toView = context.viewController(forKey: .to)?.view,
              let container = deepestCommonAncestor(fromView, toView) else { return }
        let toIsRoot = context.viewController(forKey: .to) === navigationController.viewControllers.first
        let rootView = toIsRoot ? toView : fromView
        let coverView = toIsRoot ? fromView : toView
        guard let rootBranch = directSubview(of: container, containing: rootView),
              let coverBranch = directSubview(of: container, containing: coverView),
              rootBranch !== coverBranch else { return }
        let frameInHost = nativeTabBar.convert(nativeTabBar.bounds, to: view)
        // UIKit owns the order of the transition cards and its dimming layer.
        // Move only our tab bar: moving the destination card immediately above it
        // can put that card below UIKit's dimming layer and darken the incoming page.
        container.insertSubview(nativeTabBar, belowSubview: coverBranch)
        nativeTabBar.frame = container.convert(frameInHost, from: view)
        nativeTabBar.isHidden = false
        if navigationController.navigationBar.isDescendant(of: container) {
            container.bringSubviewToFront(navigationController.navigationBar)
        }
        tabBarBorrowed = true
    }

    private func restoreTabBarToHost() {
        tabBarBorrowed = false
        if nativeTabBar.superview !== view {
            view.addSubview(nativeTabBar)
        }
    }

    private func deepestCommonAncestor(_ first: UIView, _ second: UIView) -> UIView? {
        var seen = Set<ObjectIdentifier>()
        var current: UIView? = first
        while let node = current {
            seen.insert(ObjectIdentifier(node))
            current = node.superview
        }
        current = second.superview
        while let node = current {
            if seen.contains(ObjectIdentifier(node)) { return node }
            current = node.superview
        }
        return nil
    }

    private func directSubview(of container: UIView, containing target: UIView) -> UIView? {
        var current: UIView? = target
        while let node = current {
            if node.superview === container { return node }
            current = node.superview
        }
        return nil
    }

    private func removeAllControllers() {
        restoreTabBarToHost()
        for controller in controllersByRoute.values {
            controller.onNavigationWillShow = nil
            controller.willMove(toParent: nil)
            if controller.isViewLoaded { controller.view.removeFromSuperview() }
            controller.removeFromParent()
        }
        controllersByRoute.removeAll()
        activeController = nil
        selectedRouteId = nil
    }

    /// Kotlin 侧「一级入口互跳」转成系统 tab 切换；非一级路由仍在当前 tab 的
    /// UINavigationController 中 push，保持系统返回入口。
    private func select(routeId: String) {
        if let index = tabRouteIds.firstIndex(of: routeId) {
            selectedRouteId = routeId
            let next = controllersByRoute[routeId]!
            navigationTransitionGeneration += 1
            restoreTabBarToHost()
            contentCoversTabBar = next.viewControllers.count > 1
            if activeController !== next {
                activeController?.view.removeFromSuperview()
                next.loadViewIfNeeded()
                next.view.frame = view.bounds
                if contentCoversTabBar {
                    view.insertSubview(next.view, aboveSubview: nativeTabBar)
                } else {
                    view.insertSubview(next.view, belowSubview: nativeTabBar)
                }
                activeController = next
            }
            nativeTabBar.selectedItem = nativeTabBar.items?[index]
            view.setNeedsLayout()
            return
        }
        activeController?.loadViewIfNeeded()
        activeController?.openNativeRouteFromHost(routeId)
    }

    func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) {
        guard let index = tabBar.items?.firstIndex(where: { $0 === item }),
              tabRouteIds.indices.contains(index)
        else { return }
        let routeId = tabRouteIds[index]
        if selectedRouteId == routeId,
           let controller = controllersByRoute[routeId],
           controller.viewControllers.count > 1 {
            controller.popToRootViewController(animated: true)
        } else {
            select(routeId: routeId)
        }
    }
}

/// 玻璃壳装配入口：登录与会话仍由一个 Compose 宿主管，登录后把一级入口交给
/// UIKit tab 容器，退出登录时收回。
///
/// 这里用 child VC 而不是导航栈切换：登录宿主管线必须始终留在窗口里。原生容器一旦
/// 把它的视图移出窗口，Compose 组合可能被回收，会话来源与 `onAuthenticatedSessionChanged`
/// 的时序就不再可控。被玻璃 TabBar 盖住的登录组合此时只渲染一个占位空白（Kotlin 侧
/// nativeTabBarEnabled 分支），成本可忽略。
private protocol NativeTabsHosting: AnyObject {
    func reloadTabs(session: AuthenticatedSession)
}

private func makeNativeTabs(session: AuthenticatedSession) -> UIViewController & NativeTabsHosting {
    if #available(iOS 27.0, *) { return AdaptiveAppTabBarController(session: session) }
    return AppTabBarController(session: session)
}

/// Standard containers own the axes, safe content rectangle and transitions on Duo.
@available(iOS 27.0, *)
private final class AdaptiveAppTabBarController: UITabBarController, NativeTabsHosting {
    private var session: AuthenticatedSession
    private var controllersByRoute: [String: TabRootNavigationController] = [:]
    private var tabsByRoute: [String: UITab] = [:]
    private var routeIds: [String] = []
    private var studentId: String

    init(session: AuthenticatedSession) {
        self.session = session
        studentId = session.profile.studentId
        super.init(nibName: nil, bundle: nil)
        session.systemManagedContentBounds = true
    }
    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = appBackgroundUIColor
        mode = .tabSidebar
        sidebar.preferredPlacement = .automatic
        reloadTabs(session: session)
#if DEBUG
        for arg in ProcessInfo.processInfo.arguments where arg.hasPrefix("--tab=") {
            select(routeId: String(arg.dropFirst(6)))
        }
        if ProcessInfo.processInfo.arguments.contains("--layout-smoke"),
           let route = ProcessInfo.processInfo.arguments.first(where: { $0.hasPrefix("--route=") }) {
            DispatchQueue.main.async { [weak self] in self?.select(routeId: String(route.dropFirst(8))) }
        }
#endif
    }

    func reloadTabs(session: AuthenticatedSession) {
        self.session = session
        session.systemManagedContentBounds = true
        guard isViewLoaded else { return }
        let items = NativeShellKt.nativeTabItems(session: session)
        let nextIds = items.map(\.routeId)
        let accountChanged = studentId != session.profile.studentId
        guard nextIds != routeIds || accountChanged else { return }
        let previousSelection = accountChanged ? nil : selectedTab?.identifier
        studentId = session.profile.studentId
        if accountChanged { controllersByRoute.removeAll(); tabsByRoute.removeAll() }
        controllersByRoute = controllersByRoute.filter { nextIds.contains($0.key) }
        tabsByRoute = tabsByRoute.filter { nextIds.contains($0.key) }
        for item in items where tabsByRoute[item.routeId] == nil {
            let controller = TabRootNavigationController(session: session, tabRouteId: item.routeId,
                selectTab: { [weak self] route in self?.select(routeId: route) }, usesSystemBars: true)
            controllersByRoute[item.routeId] = controller
            tabsByRoute[item.routeId] = UITab(title: item.title,
                image: UIImage(systemName: AppTabBarController.symbolNames[item.routeId] ?? "circle"),
                identifier: item.routeId) { _ in controller }
        }
        routeIds = nextIds
        UIView.performWithoutAnimation {
            tabs = nextIds.compactMap { tabsByRoute[$0] }
            selectedTab = previousSelection.flatMap { tabsByRoute[$0] } ?? tabs.first
        }
    }

    private func select(routeId: String) {
        if let tab = tabsByRoute[routeId] {
            selectedTab = tab
        } else if let route = selectedTab?.identifier, let navigation = controllersByRoute[route] {
            navigation.loadViewIfNeeded()
            navigation.openNativeRouteFromHost(routeId)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // Bodies are already constrained by UIKit. Only their remaining system
        // bottom safe area is needed for scrolling to the home indicator.
        session.glassTabBarBottomInsetDp = 0
    }
}

private final class LiquidGlassShellController: UIViewController {
    private var authenticatedSession: AuthenticatedSession?
    private var tabController: (UIViewController & NativeTabsHosting)?
    private var appActiveObserver: NSObjectProtocol?
    private var appInactiveObserver: NSObjectProtocol?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(appBackgroundColor)
        let root = MainViewControllerKt.NativeMainViewController(
            onAuthenticatedSessionChanged: { [weak self] session in
                self?.handle(session: session)
            },
            onOpenNativeRoute: { _ in },
            nativeTabBarEnabled: true
        )
        configureComposeHost(root)
        embed(root)
        appInactiveObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.willResignActiveNotification, object: nil, queue: .main
        ) { [weak self] _ in
            self?.authenticatedSession?.notifyAppBecameInactive()
        }
        appActiveObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.authenticatedSession?.notifyAppBecameActive()
        }
    }

    deinit {
        if let appInactiveObserver { NotificationCenter.default.removeObserver(appInactiveObserver) }
        if let appActiveObserver {
            NotificationCenter.default.removeObserver(appActiveObserver)
        }
    }

    /// Kotlin 的 SideEffect 会在每次重组时重复上报同一会话，所以只在实例变化时换引用、拆装 tab 控制器；
    /// 但每次上报都要过一遍 reloadTabs——偏好变化（物理在线开关）会在同一个会话实例上
    /// 长出或收起一级入口，只按实例去重会让设置里的开关看起来「没有反应」。
    private func handle(session: AuthenticatedSession?) {
        if session !== authenticatedSession {
            authenticatedSession = session
            if session != nil && tabController == nil {
                let tabs = makeNativeTabs(session: session!)
                tabController = tabs
                embed(tabs)
                // 一级入口集合在 Compose 里观测（「物理在线」开关会增减一项），UIKit 收不到快照变化，
                // 所以由会话上的回调显式回推一次重配，否则拨完开关底栏看起来毫无反应。
                session!.onNativeTabItemsChanged = { [weak self, weak tabs] _ in
                    DispatchQueue.main.async { [weak self, weak tabs] in
                        guard let self, let tabs, let session = self.authenticatedSession else { return }
                        tabs.reloadTabs(session: session)
                    }
                }
            } else if session == nil, let tabs = tabController {
                tabs.willMove(toParent: nil)
                tabs.view.removeFromSuperview()
                tabs.removeFromParent()
                tabController = nil
            }
        }
        if let session, let tabs = tabController {
            tabs.reloadTabs(session: session)
        }
    }

    private func embed(_ child: UIViewController) {
        addChild(child)
        let alreadyVisible = view.window != nil
        if alreadyVisible { child.beginAppearanceTransition(true, animated: false) }
        child.view.frame = view.bounds
        child.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(child.view)
        child.didMove(toParent: self)
        if alreadyVisible { child.endAppearanceTransition() }
    }
}

#if DEBUG
/// Uses the production navigation containers with an isolated, synthetic session.
private final class OfflineLayoutShellController: UIViewController {
    private var bootstrap: UIViewController?
    override func viewDidLoad() {
        super.viewDidLoad()
        let bootstrap = OfflineLayoutProbeKt.OfflineLayoutBootstrap { [weak self] session in
            DispatchQueue.main.async { self?.show(session) }
        }
        self.bootstrap = bootstrap
        embed(bootstrap)
    }
    private func show(_ session: AuthenticatedSession) {
        // Keep the source composition attached, as in the production login shell.
        // Removing the first Compose host can retire its scene's render lifecycle.
        guard children.count == 1 else { return }
        if #available(iOS 26.0, *), UIDevice.current.userInterfaceIdiom == .phone,
           !ProcessInfo.processInfo.arguments.contains("--fallback-shell") {
            embed(makeNativeTabs(session: session))
        } else {
            embed(NativeNavigationController(offlineSession: session))
        }
    }
    private func embed(_ child: UIViewController) {
        addChild(child)
        let alreadyVisible = view.window != nil
        if alreadyVisible { child.beginAppearanceTransition(true, animated: false) }
        child.view.frame = view.bounds
        child.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(child.view)
        child.didMove(toParent: self)
        if alreadyVisible { child.endAppearanceTransition() }
    }
}
#endif

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
#if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--layout-smoke") {
            return OfflineLayoutShellController()
        }
        if ProcessInfo.processInfo.arguments.contains("--courseware-picker-smoke") {
            return CoursewarePickerProbeViewControllerKt.CoursewarePickerProbeViewController()
        }
        if ProcessInfo.processInfo.arguments.contains("--new-migration-smoke") {
            return NewMigrationProbeViewControllerKt.NewMigrationProbeViewController()
        }
        if ProcessInfo.processInfo.arguments.contains("--security-smoke") {
            return SecuritySmokeViewControllerKt.SecuritySmokeViewController()
        }
        if ProcessInfo.processInfo.arguments.contains("--physicslab-sheet-smoke") {
            return NativeSheetSmokeViewControllerKt.NativeSheetSmokeViewController(physicsLab: true, updateNotes: false)
        }
        if ProcessInfo.processInfo.arguments.contains("--update-notes-smoke") {
            return NativeSheetSmokeViewControllerKt.NativeSheetSmokeViewController(physicsLab: false, updateNotes: true)
        }
        if ProcessInfo.processInfo.arguments.contains("--sheet-smoke") {
            return NativeSheetSmokeViewControllerKt.NativeSheetSmokeViewController(physicsLab: false, updateNotes: false)
        }
#endif
        // iOS 26 起把导航壳交给系统容器，由渲染栈自动应用 Liquid Glass；更低版本原样保留
        // M10 的 Compose 自绘壳层，行为不变。
        // 只接管 iPhone 紧凑端：iPad/宽窗口继续走共享三栏 NavDisplay 路径。用 idiom 而不是
        // size class 判定，是因为 size class 会随旋转变化，而壳层只能在装配时选一次。
        if #available(iOS 26.0, *), UIDevice.current.userInterfaceIdiom == .phone {
            return LiquidGlassShellController()
        }
        return NativeNavigationController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

struct ContentView: View {
    var body: some View {
        ZStack {
            // Keep the hosting surface continuous behind every system area.
            appBackgroundColor
                .ignoresSafeArea()
            ComposeView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                // Keep the Compose host at a stable full-screen size on every edge. A pushed
                // destination must also cover the status-bar region, or a native push transition
                // leaves a static strip above the moving card. Compose owns the login form's
                // IME padding only while fields are editable; SwiftUI must not retain a stale
                // keyboard safe area after Password AutoFill or foreground transitions.
                .ignoresSafeArea(.all)
        }
        // Apply edge-to-edge at the hosting boundary so neither the container nor keyboard safe
        // area can resize the root and expose a strip of the UIWindow background.
        .background(appBackgroundColor)
        .ignoresSafeArea(.all)
    }
}

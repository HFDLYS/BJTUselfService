#import <UIKit/UIKit.h>

static inline UIBarButtonItem *BJTUSheetButton(
    NSString *title,
    UIBarButtonItemStyle style,
    BOOL enabled,
    id target
) {
    UIBarButtonItem *item = [[UIBarButtonItem alloc] initWithTitle:title
        style:style target:target action:@selector(invoke:)];
    item.enabled = enabled;
    return item;
}

static inline void BJTUConfigureNativeSheetHeader(
    UIViewController *sheet,
    NSString *title,
    NSString *confirmLabel,
    BOOL confirmEnabled,
    id confirmTarget,
    NSString *todayLabel,
    BOOL todayEnabled,
    id todayTarget,
    NSString *dismissLabel,
    BOOL dismissEnabled,
    id dismissTarget
) {
    UINavigationController *navigation = (UINavigationController *)sheet;
    UIViewController *content = navigation.topViewController;
    if (content == nil) {
        return;
    }
    // The Compose child must respect the native navigation bar. Without this,
    // its first line paints under the bar and hides the system title while the
    // bar buttons remain visible above it.
    content.edgesForExtendedLayout = UIRectEdgeNone;
    content.extendedLayoutIncludesOpaqueBars = NO;
    content.view.backgroundColor = UIColor.clearColor;
    content.view.opaque = NO;
    navigation.view.backgroundColor = UIColor.clearColor;
    navigation.view.opaque = NO;
    // Every iOS sheet gets a real UIKit navigation bar so dismissal remains a
    // native close action even when the shared body does not supply a title.
    BOOL hasNativeHeader = title != nil || confirmLabel != nil || todayLabel != nil ||
        dismissLabel != nil || dismissTarget != nil;
    [navigation setNavigationBarHidden:!hasNativeHeader animated:NO];
    navigation.navigationBar.prefersLargeTitles = NO;
    navigation.navigationBar.translucent = YES;
    navigation.navigationBar.backgroundColor = UIColor.clearColor;
    content.navigationItem.largeTitleDisplayMode = UINavigationItemLargeTitleDisplayModeNever;
    // The navigation container already excludes its header from the body frame.
    content.additionalSafeAreaInsets = UIEdgeInsetsZero;
    content.navigationItem.title = title;
    navigation.navigationBar.topItem.title = title;
    navigation.title = title;
    navigation.navigationItem.title = title;
    navigation.navigationBar.titleTextAttributes = @{
        NSForegroundColorAttributeName: UIColor.labelColor,
    };
    UINavigationBarAppearance *barAppearance = [[UINavigationBarAppearance alloc] init];
    [barAppearance configureWithTransparentBackground];
    barAppearance.shadowColor = UIColor.clearColor;
    if (@available(iOS 26.0, *)) {
        barAppearance.backgroundEffect = [UIGlassEffect effectWithStyle:UIGlassEffectStyleClear];
    } else {
        barAppearance.backgroundEffect = [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemMaterial];
    }
    navigation.navigationBar.standardAppearance = barAppearance;
    navigation.navigationBar.scrollEdgeAppearance = barAppearance;
    navigation.navigationBar.compactAppearance = barAppearance;
    if (title != nil) {
        UILabel *nativeTitle = [[UILabel alloc] init];
        nativeTitle.text = title;
        nativeTitle.textColor = UIColor.labelColor;
        nativeTitle.font = [UIFont preferredFontForTextStyle:UIFontTextStyleHeadline];
        nativeTitle.textAlignment = NSTextAlignmentCenter;
        [nativeTitle sizeToFit];
        content.navigationItem.titleView = nativeTitle;
    } else {
        content.navigationItem.titleView = nil;
    }

    if (confirmLabel != nil && confirmTarget != nil) {
        content.navigationItem.leftBarButtonItem = BJTUSheetButton(
            confirmLabel, UIBarButtonItemStyleDone, confirmEnabled, confirmTarget);
    } else {
        content.navigationItem.leftBarButtonItem = nil;
    }

    NSMutableArray<UIBarButtonItem *> *rightItems = [NSMutableArray array];
    // UIKit lays out the first right item nearest the trailing edge. Keep
    // “取消” at the edge and put the native “今天” action immediately before it.
    if (dismissLabel != nil && dismissTarget != nil) {
        [rightItems addObject:BJTUSheetButton(
            dismissLabel, UIBarButtonItemStylePlain, dismissEnabled, dismissTarget)];
    } else if (hasNativeHeader && dismissTarget != nil) {
        [rightItems addObject:[[UIBarButtonItem alloc]
            initWithBarButtonSystemItem:UIBarButtonSystemItemClose
            target:dismissTarget
            action:@selector(invoke:)]];
    }
    if (todayLabel != nil && todayTarget != nil) {
        [rightItems addObject:BJTUSheetButton(
            todayLabel, UIBarButtonItemStylePlain, todayEnabled, todayTarget)];
    }
    content.navigationItem.rightBarButtonItem = nil;
    content.navigationItem.rightBarButtonItems = rightItems.count > 0 ? rightItems : nil;
}

static inline UIViewController *BJTUCreateNativeSheetController(
    UIViewController *content,
    NSString *title,
    NSString *confirmLabel,
    BOOL confirmEnabled,
    id confirmTarget,
    NSString *todayLabel,
    BOOL todayEnabled,
    id todayTarget,
    NSString *dismissLabel,
    BOOL dismissEnabled,
    id dismissTarget
) {
    UINavigationController *navigation = [[UINavigationController alloc] initWithRootViewController:content];
    BJTUConfigureNativeSheetHeader(
        navigation,
        title,
        confirmLabel,
        confirmEnabled,
        confirmTarget,
        todayLabel,
        todayEnabled,
        todayTarget,
        dismissLabel,
        dismissEnabled,
        dismissTarget
    );
    return navigation;
}

// Compose's Skia host does not inherit the presentation controller's visual
// effect as a background. Install UIKit's own Regular glass effect behind the
// host so the sheet remains translucent and readable without drawing a custom
// material.
static inline void BJTUInstallNativeSheetMaterial(UIViewController *content) {
    UIView *container = content.view;
    container.backgroundColor = UIColor.clearColor;
    container.opaque = NO;

    static const NSInteger kBJTUGlassMaterialTag = 0x42545547;
    if ([container viewWithTag:kBJTUGlassMaterialTag] != nil) {
        return;
    }

    UIVisualEffect *effect;
    if (@available(iOS 26.0, *)) {
        UIGlassEffect *glass = [UIGlassEffect effectWithStyle:UIGlassEffectStyleRegular];
        glass.interactive = YES;
        effect = glass;
    } else {
        effect = [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemMaterial];
    }
    UIVisualEffectView *material = [[UIVisualEffectView alloc] initWithEffect:effect];
    material.tag = kBJTUGlassMaterialTag;
    material.translatesAutoresizingMaskIntoConstraints = NO;
    material.userInteractionEnabled = NO;
    [container insertSubview:material atIndex:0];
    [NSLayoutConstraint activateConstraints:@[
        [material.leadingAnchor constraintEqualToAnchor:container.leadingAnchor],
        [material.trailingAnchor constraintEqualToAnchor:container.trailingAnchor],
        [material.topAnchor constraintEqualToAnchor:container.topAnchor],
        [material.bottomAnchor constraintEqualToAnchor:container.bottomAnchor],
    ]];
}

static inline void BJTUConfigureSheetPresentation(UIViewController *sheet, BOOL needsFullHeight) {
    sheet.modalPresentationStyle = UIModalPresentationPageSheet;
    // Let UIKit's presentation controller render the native sheet material.  A
    // Compose host view with its default opaque background would hide the blur/
    // Liquid Glass surface visible in the system sheet references.
    sheet.view.backgroundColor = UIColor.clearColor;
    sheet.view.opaque = NO;
    if (@available(iOS 15.0, *)) {
        UISheetPresentationController *presentation = sheet.sheetPresentationController;
        if (presentation == nil) {
            return;
        }
        if (needsFullHeight) {
            UISheetPresentationControllerDetent *largeDetent =
                UISheetPresentationControllerDetent.largeDetent;
            presentation.detents = @[largeDetent];
            if (@available(iOS 26.1, *)) {
                largeDetent.backgroundEffect =
                    [UIGlassEffect effectWithStyle:UIGlassEffectStyleRegular];
            }
            presentation.selectedDetentIdentifier = UISheetPresentationControllerDetentIdentifierLarge;
        } else {
            UISheetPresentationControllerDetent *mediumDetent =
                UISheetPresentationControllerDetent.mediumDetent;
            UISheetPresentationControllerDetent *largeDetent =
                UISheetPresentationControllerDetent.largeDetent;
            presentation.detents = @[
                mediumDetent,
                largeDetent,
            ];
            if (@available(iOS 26.1, *)) {
                UIVisualEffect *glassEffect =
                    [UIGlassEffect effectWithStyle:UIGlassEffectStyleRegular];
                mediumDetent.backgroundEffect = glassEffect;
                largeDetent.backgroundEffect = glassEffect;
            }
            presentation.selectedDetentIdentifier = UISheetPresentationControllerDetentIdentifierMedium;
        }
        presentation.prefersGrabberVisible = YES;
        presentation.prefersScrollingExpandsWhenScrolledToEdge = YES;
    }
}

/*
 * 按内容定高：单一自定义 detent，高度 = 原生标题栏底边 + Compose 量出的正文高度，
 * 超过系统允许的最大值时封顶（正文自带滚动）。detent 高度不含底部安全区。
 * 内容变高（例如出现结果提示）时再次调用，animateChanges 让 sheet 平滑长高。
 */
static inline void BJTUSetSheetFittedHeight(UIViewController *sheet, double contentHeight) {
    if (@available(iOS 16.0, *)) {
        UISheetPresentationController *presentation = sheet.sheetPresentationController;
        if (presentation == nil) {
            return;
        }
        UISheetPresentationControllerDetentIdentifier fittedIdentifier = @"team.bjtuss.sheet.fitted";
        __weak UIViewController *weakSheet = sheet;
        UISheetPresentationControllerDetent *fitted =
            [UISheetPresentationControllerDetent customDetentWithIdentifier:fittedIdentifier
                                                                   resolver:^CGFloat(id<UISheetPresentationControllerDetentResolutionContext> context) {
                CGFloat header = 56.0;
                UIViewController *strongSheet = weakSheet;
                if ([strongSheet isKindOfClass:UINavigationController.class]) {
                    CGFloat barBottom = CGRectGetMaxY(((UINavigationController *)strongSheet).navigationBar.frame);
                    if (barBottom > 0) {
                        header = barBottom;
                    }
                }
                return MIN(header + contentHeight, context.maximumDetentValue);
            }];
        if (@available(iOS 26.1, *)) {
            fitted.backgroundEffect = [UIGlassEffect effectWithStyle:UIGlassEffectStyleRegular];
        }
        [presentation animateChanges:^{
            presentation.detents = @[fitted];
            presentation.selectedDetentIdentifier = fittedIdentifier;
        }];
    }
}

static inline void BJTUAttachSheetPresentationDelegate(
    UIViewController *sheet,
    id<UIAdaptivePresentationControllerDelegate> delegate
) {
    sheet.presentationController.delegate = delegate;
}

import BJTUShared
import SwiftUI
import WidgetKit

@main
struct iOSApp: App {
    init() {
        // 共享层写完课程小组件快照后发此通知；WidgetKit 只有 Swift 接口，由宿主代为刷新。
        // 名称与 CourseWidgetStore.ios.kt 的 COURSE_WIDGET_DID_CHANGE_NOTIFICATION 一致。
        NotificationCenter.default.addObserver(
            forName: Notification.Name("BJTUCourseWidgetSnapshotDidChange"),
            object: nil,
            queue: .main
        ) { _ in
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // 小组件点击：链接交给共享层，登录后的壳层跳到对应课程/考试详情。
                .onOpenURL { url in
                    _ = WidgetLinkInbox.shared.offer(url: url.absoluteString)
                }
        }
    }
}

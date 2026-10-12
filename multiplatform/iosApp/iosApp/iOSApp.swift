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
        }
    }
}

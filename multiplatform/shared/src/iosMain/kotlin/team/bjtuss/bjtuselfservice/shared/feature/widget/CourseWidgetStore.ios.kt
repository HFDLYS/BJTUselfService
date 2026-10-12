package team.bjtuss.bjtuselfservice.shared.feature.widget

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToURL
import team.bjtuss.bjtuselfservice.shared.logging.AppLog

/** 与小组件扩展、entitlements 中的 App Group 一致。 */
const val COURSE_WIDGET_APP_GROUP = "group.team.bjtuss.bjtuselfservice.kmp"
const val COURSE_WIDGET_FILE_NAME = "course-widget.json"

/** Swift 宿主监听此通知并调用 WidgetCenter 刷新（WidgetKit 没有 Objective-C 接口）。 */
const val COURSE_WIDGET_DID_CHANGE_NOTIFICATION = "BJTUCourseWidgetSnapshotDidChange"

private var lastWrittenJson: String? = null

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun writeCourseWidgetSnapshot(json: String?) {
    if (json == lastWrittenJson && json != null) return
    val container = NSFileManager.defaultManager
        .containerURLForSecurityApplicationGroupIdentifier(COURSE_WIDGET_APP_GROUP)
    if (container == null) {
        AppLog.d("CourseWidget", "App Group container unavailable")
        return
    }
    val url = container.URLByAppendingPathComponent(COURSE_WIDGET_FILE_NAME) ?: return
    val ok = if (json == null) {
        NSFileManager.defaultManager.removeItemAtURL(url, error = null)
        true
    } else {
        val data = NSString.create(string = json).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        data.writeToURL(url, atomically = true)
    }
    if (!ok) {
        AppLog.d("CourseWidget", "Failed to write widget snapshot")
        return
    }
    lastWrittenJson = json
    NSNotificationCenter.defaultCenter.postNotificationName(COURSE_WIDGET_DID_CHANGE_NOTIFICATION, `object` = null)
}

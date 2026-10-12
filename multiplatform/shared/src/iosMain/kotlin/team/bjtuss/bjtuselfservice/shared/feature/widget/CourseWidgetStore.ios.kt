package team.bjtuss.bjtuselfservice.shared.feature.widget

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSISOLatin1StringEncoding
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSPropertyListImmutable
import platform.Foundation.NSPropertyListSerialization
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToURL
import team.bjtuss.bjtuselfservice.shared.logging.AppLog

/**
 * entitlements 里声明的 App Group。SideStore/AltStore 重签时会把它改成
 * `<原名>.<个人 Team ID>`，所以实际使用的标识要从安装包的描述文件里找（见 [resolveCourseWidgetContainer]）。
 * 小组件扩展 CourseWidgetData.swift 用同一规则。
 */
const val COURSE_WIDGET_APP_GROUP = "group.team.bjtuss.bjtuselfservice.kmp"
const val COURSE_WIDGET_FILE_NAME = "course-widget.json"

/** Swift 宿主监听此通知并调用 WidgetCenter 刷新（WidgetKit 没有 Objective-C 接口）。 */
const val COURSE_WIDGET_DID_CHANGE_NOTIFICATION = "BJTUCourseWidgetSnapshotDidChange"

private var lastWrittenJson: String? = null

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun writeCourseWidgetSnapshot(json: String?) {
    if (json == lastWrittenJson && json != null) return
    val container = resolveCourseWidgetContainer()
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

/** 依次尝试描述文件里以原名开头的 App Group 和原名本身，返回第一个可访问的共享容器。 */
@OptIn(ExperimentalForeignApi::class)
private fun resolveCourseWidgetContainer(): NSURL? {
    val candidates = (courseWidgetGroupsFromProfile(NSBundle.mainBundle.bundlePath) + COURSE_WIDGET_APP_GROUP).distinct()
    return candidates.firstNotNullOfOrNull {
        NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(it)
    }
}

/**
 * 读取安装包内 embedded.mobileprovision 的 Entitlements（CMS 签名包着一段 XML plist），
 * 返回原名或“原名.后缀”的 App Group。模拟器和 Xcode 直装可能没有该文件，返回空表。
 */
@OptIn(ExperimentalForeignApi::class)
internal fun courseWidgetGroupsFromProfile(bundlePath: String): List<String> {
    val data = NSData.dataWithContentsOfFile("$bundlePath/embedded.mobileprovision") ?: return emptyList()
    // Latin-1 按字节一一映射，能在二进制外壳里定位到明文 plist。
    val raw = NSString.create(data = data, encoding = NSISOLatin1StringEncoding)?.toString() ?: return emptyList()
    val start = raw.indexOf("<?xml")
    val end = raw.indexOf("</plist>", startIndex = maxOf(start, 0))
    if (start < 0 || end < 0) return emptyList()
    val plistData = NSString.create(string = raw.substring(start, end + "</plist>".length))
        .dataUsingEncoding(NSISOLatin1StringEncoding) ?: return emptyList()
    val plist = NSPropertyListSerialization.propertyListWithData(plistData, NSPropertyListImmutable, null, null) as? Map<*, *>
        ?: return emptyList()
    val groups = (plist["Entitlements"] as? Map<*, *>)?.get("com.apple.security.application-groups") as? List<*>
        ?: return emptyList()
    return groups.filterIsInstance<String>().filter {
        it == COURSE_WIDGET_APP_GROUP || it.startsWith("$COURSE_WIDGET_APP_GROUP.")
    }
}

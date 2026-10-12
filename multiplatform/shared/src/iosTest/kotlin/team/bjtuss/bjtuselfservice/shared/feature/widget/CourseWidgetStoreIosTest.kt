package team.bjtuss.bjtuselfservice.shared.feature.widget

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSISOLatin1StringEncoding
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class CourseWidgetStoreIosTest {
    private fun bundleWithProfile(groups: List<String>?): String {
        val dir = NSTemporaryDirectory() + "widget-profile-" + NSUUID().UUIDString
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        if (groups != null) {
            val items = groups.joinToString("") { "<string>$it</string>" }
            // 模拟 CMS 外壳：plist 前后都有二进制字节。
            val body = "0\u0082ÿ\u0001<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<plist version=\"1.0\"><dict><key>Entitlements</key><dict>" +
                "<key>com.apple.security.application-groups</key><array>$items</array>" +
                "</dict></dict></plist> \u0000ÿ"
            val data = NSString.create(string = body).dataUsingEncoding(NSISOLatin1StringEncoding)!!
            data.writeToFile("$dir/embedded.mobileprovision", atomically = true)
        }
        return dir
    }

    @Test fun findsSideStoreRenamedGroupAndIgnoresOthers() {
        val dir = bundleWithProfile(listOf("group.other.app", "$COURSE_WIDGET_APP_GROUP.ABCDE12345"))
        assertEquals(listOf("$COURSE_WIDGET_APP_GROUP.ABCDE12345"), courseWidgetGroupsFromProfile(dir))
    }

    @Test fun keepsOriginalGroupFromXcodeProfile() {
        val dir = bundleWithProfile(listOf(COURSE_WIDGET_APP_GROUP))
        assertEquals(listOf(COURSE_WIDGET_APP_GROUP), courseWidgetGroupsFromProfile(dir))
    }

    @Test fun missingProfileYieldsNoCandidates() {
        assertTrue(courseWidgetGroupsFromProfile(bundleWithProfile(null)).isEmpty())
        // 名字相似但不是“原名.后缀”的组不能误选。
        assertTrue(courseWidgetGroupsFromProfile(bundleWithProfile(listOf("${COURSE_WIDGET_APP_GROUP}x"))).isEmpty())
    }
}

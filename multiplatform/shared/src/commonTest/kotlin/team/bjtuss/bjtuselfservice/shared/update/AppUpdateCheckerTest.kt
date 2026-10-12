package team.bjtuss.bjtuselfservice.shared.update

import kotlin.test.Test
import kotlin.test.assertTrue

class AppUpdateCheckerTest {
    @Test
    fun kmpASuffixIsNewerThanPlainKmp() {
        assertTrue(AppUpdateChecker.compareVersions("v1.7.2-KMP-A", "1.7.2-KMP") > 0)
        assertTrue(AppUpdateChecker.compareVersions("1.7.2-KMP", "v1.7.2-KMP-A") < 0)
        assertTrue(AppUpdateChecker.compareVersions("v1.7.2-KMP-A", "1.7.2-KMP-A") == 0)
        assertTrue(AppUpdateChecker.compareVersions("v1.7.3-KMP-A", "1.7.3-KMP") > 0)
        assertTrue(AppUpdateChecker.compareVersions("1.7.3-A-KMP", "1.7.3-KMP") < 0)
        assertTrue(AppUpdateChecker.compareVersions("v1.7.3-KMP-B", "1.7.3-KMP-A") > 0)
        assertTrue(AppUpdateChecker.compareVersions("1.7.3-B", "1.7.3-KMP-A") < 0)
    }

    @Test
    fun bareASuffixIsOlderThanKmp() {
        // 1.7.2-A 的后缀按字典序小于 KMP，不能当热修版本号。
        assertTrue(AppUpdateChecker.compareVersions("1.7.2-A", "1.7.2-KMP") < 0)
    }

    @Test
    fun releaseVersionCanUpgradeThe173Base() {
        assertTrue(AppUpdateChecker.CURRENT_VERSION == "1.8.4-Beta-SheetFix-Widget")
        assertTrue(AppUpdateChecker.compareVersions(AppUpdateChecker.CURRENT_VERSION, "1.8.3-KMP") > 0)
        assertTrue(AppUpdateChecker.compareVersions(AppUpdateChecker.CURRENT_VERSION, "1.8.4-Alpha-phydemo-fix2") > 0)
        assertTrue(AppUpdateChecker.compareVersions("1.8.4-KMP", AppUpdateChecker.CURRENT_VERSION) > 0)
        assertTrue(AppUpdateChecker.compareVersions(AppUpdateChecker.CURRENT_VERSION, "1.8.3-Beta-ci") > 0)
        assertTrue(AppUpdateChecker.compareVersions(AppUpdateChecker.CURRENT_VERSION, "1.8.3-Alpha-ci") > 0)
        assertTrue(AppUpdateChecker.compareVersions(AppUpdateChecker.CURRENT_VERSION, "1.8.2-KMP") > 0)
        assertTrue(AppUpdateChecker.compareVersions(AppUpdateChecker.CURRENT_VERSION, "1.7.4-KMP") > 0)
        assertTrue(AppUpdateChecker.compareVersions("1.8.3-alpha-ci", "1.8.3-KMP") > 0)
        assertTrue(AppUpdateChecker.compareVersions("1.8.3-beta-ci", "1.8.3-KMP") > 0)
    }
}

package team.bjtuss.bjtuselfservice.kmp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import team.bjtuss.bjtuselfservice.shared.App
import team.bjtuss.bjtuselfservice.shared.cache.createAndroidCacheStore
import team.bjtuss.bjtuselfservice.shared.security.createAndroidAccountSecurityStore
import team.bjtuss.bjtuselfservice.shared.auth.AndroidOnnxCaptchaRecognizer
import team.bjtuss.bjtuselfservice.shared.logging.AppLog

class MainActivity : ComponentActivity() {
    private val refreshRate = AndroidRefreshRateController(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAuthenticatedSessionRegistry.installLifecycle(application)
        // 课表快照写入后通知桌面小组件重排（进程内一次即可，重复调用无副作用）。
        team.bjtuss.bjtuselfservice.shared.feature.widget.installAndroidCourseWidgetStore(this) {
            team.bjtuss.bjtuselfservice.kmp.widget.CourseWidgetUpdater.updateAll(it)
        }
        enableImmersiveEdgeToEdge()
        // 调试日志只在 debug 构建开启；release 保持关闭。
        AppLog.enabled = BuildConfig.DEBUG
        // 不要在此处预热 WebView：Chromium 会让 HyperOS 把应用标成「跟随应用内设置」并锁 60Hz。
        refreshRate.start()
        val accountSecurityStore = createAndroidAccountSecurityStore(this)
        val cacheStoreHandle = createAndroidCacheStore(this)
        val homeworkFileGateway = AndroidHomeworkFileGateway(this)
        val captchaRecognizer = AndroidOnnxCaptchaRecognizer(this)
        setContent {
            App(
                accountSecurityStore = accountSecurityStore,
                cacheStoreHandle = cacheStoreHandle,
                homeworkFileGateway = homeworkFileGateway,
                coursewareDirectoryGateway = homeworkFileGateway,
                captchaRecognizer = captchaRecognizer,
                nativeNavigationEnabled = true,
                onOpenExternalUrl = ::openExternalUrl,
                onOpenNativeRoute = { routeId ->
                    rememberSystemBottomInset()
                    startActivity(NativeDetailActivity.intentFor(this, routeId))
                },
                onAuthenticatedSessionChanged = AndroidAuthenticatedSessionRegistry::update,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        AndroidAuthenticatedSessionRegistry.notifyAppBecameActive()
        refreshRate.apply()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshRate.apply()
    }

    override fun onDestroy() {
        refreshRate.stop()
        if (isFinishing) AndroidAuthenticatedSessionRegistry.update(null)
        super.onDestroy()
    }

    private fun openExternalUrl(url: String) {
        openHttpsUrl(this, url)
    }
}

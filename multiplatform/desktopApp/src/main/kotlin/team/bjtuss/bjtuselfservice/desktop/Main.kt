package team.bjtuss.bjtuselfservice.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.awt.Desktop
import java.awt.desktop.AppReopenedListener
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.io.File
import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.App
import team.bjtuss.bjtuselfservice.shared.AuthenticatedSession
import team.bjtuss.bjtuselfservice.shared.registerDesktopCredentialWindowHandle
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognitionResult
import team.bjtuss.bjtuselfservice.shared.auth.DesktopCoreMlCaptchaRecognizer
import team.bjtuss.bjtuselfservice.shared.cache.createDesktopCacheStore
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppCommand
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppCommandBus
import team.bjtuss.bjtuselfservice.shared.security.createDesktopAccountSecurityStore
import team.bjtuss.bjtuselfservice.shared.system.DesktopWindowHandle
import team.bjtuss.bjtuselfservice.shared.system.DesktopWindowLifecycle
import team.bjtuss.bjtuselfservice.shared.DesktopSyncStatusSmoke

private const val CAPTCHA_VERIFICATION_ARGUMENT = "--verify-captcha-model="
private const val SYNC_STATUS_SMOKE_ARGUMENT = "--sync-status-smoke"

fun main(args: Array<String>) {
    if ("--sync-timing" in args) {
        // Enable only sanitized timing events; general business/debug logs stay off.
        team.bjtuss.bjtuselfservice.shared.logging.SyncTiming.sink = { println(it) }
    }
    if (SYNC_STATUS_SMOKE_ARGUMENT in args || "--schedule-events-smoke" in args || "--new-migration-smoke" in args) {
        application {
            Window(
                onCloseRequest = { exitApplication() },
                title = "同步状态 · 本地测试",
                state = rememberWindowState(width = if ("--narrow" in args) 420.dp else 1080.dp, height = 720.dp),
            ) {
                if ("--new-migration-smoke" in args) team.bjtuss.bjtuselfservice.shared.feature.shell.NewMigrationUiProbe() else if ("--schedule-events-smoke" in args) team.bjtuss.bjtuselfservice.shared.DesktopScheduleEventsSmoke() else DesktopSyncStatusSmoke()
            }
        }
        return
    }
    val captchaRecognizer = DesktopCoreMlCaptchaRecognizer()
    args.firstOrNull { it.startsWith(CAPTCHA_VERIFICATION_ARGUMENT) }?.let { argument ->
        val image = File(argument.removePrefix(CAPTCHA_VERIFICATION_ARGUMENT))
        require(image.isFile) { "验证码验证图片不存在：${image.absolutePath}" }
        when (val result = runBlocking { captchaRecognizer.recognize(image.readBytes()) }) {
            is CaptchaRecognitionResult.Success -> {
                println(
                    "captcha_model=ready expression=${result.value.expression} " +
                        "answer=${result.value.answer}",
                )
                return
            }
            is CaptchaRecognitionResult.Failed -> {
                error("captcha_model=failed reason=${result.reason}")
            }
        }
    }
    val accountSecurityStore = createDesktopAccountSecurityStore()
    val cacheStoreHandle = createDesktopCacheStore()

    try {
        application {
            val state = rememberWindowState(width = 1080.dp, height = 720.dp)
            val appCommandBus = remember { AppCommandBus() }
            val lifecycle = remember { DesktopWindowLifecycle() }
            val shellSubscribers by appCommandBus.subscriptionCount.collectAsState()
            val shellReady = shellSubscribers > 0
            val desktop = remember {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
            }
            val authenticatedSession = remember { mutableStateOf<AuthenticatedSession?>(null) }
            val reopenListener = remember(lifecycle) {
                AppReopenedListener {
                    lifecycle.reopenWindow()
                    authenticatedSession.value?.notifyAppBecameActive()
                }
            }

            DisposableEffect(desktop, reopenListener) {
                desktop?.addAppEventListener(reopenListener)
                onDispose { desktop?.removeAppEventListener(reopenListener) }
            }

            Window(
                onCloseRequest = { lifecycle.closeWindow() },
                title = "交大自由行 KMP",
                state = state,
            ) {
                val windowHandle = remember(window, desktop) {
                    object : DesktopWindowHandle {
                        override fun hide() {
                            window.isVisible = false
                        }

                        override fun showAndFocus() {
                            window.isVisible = true
                            window.toFront()
                            window.requestFocus()
                            desktop?.requestForeground(true)
                        }
                    }
                }
                DisposableEffect(lifecycle, windowHandle) {
                    lifecycle.attach(windowHandle)
                    onDispose { lifecycle.detach(windowHandle) }
                }
                val focusListener = remember(window) {
                    object : WindowFocusListener {
                        override fun windowGainedFocus(event: WindowEvent) {
                            authenticatedSession.value?.notifyAppBecameActive()
                        }

                        override fun windowLostFocus(event: WindowEvent) {
                            authenticatedSession.value?.notifyAppBecameInactive()
                        }
                    }
                }
                DisposableEffect(window, focusListener) {
                    window.addWindowFocusListener(focusListener)
                    onDispose { window.removeWindowFocusListener(focusListener) }
                }
                LaunchedEffect(window) {
                    registerDesktopCredentialWindowHandle(window.windowHandle)
                }
                DisposableEffect(window) {
                    onDispose { registerDesktopCredentialWindowHandle(0L) }
                }
                    MenuBar {
                        // 与壳层一级入口一致；考试/课件/教室/邮箱/设置走「更多」或菜单二级。
                        Menu("前往") {
                            Item("首页", onClick = { appCommandBus.send(AppCommand.NAVIGATE_HOME) }, enabled = shellReady)
                            Item("课程表", onClick = { appCommandBus.send(AppCommand.NAVIGATE_SCHEDULE) }, enabled = shellReady)
                            Item("成绩", onClick = { appCommandBus.send(AppCommand.NAVIGATE_GRADES) }, enabled = shellReady)
                            Item("作业", onClick = { appCommandBus.send(AppCommand.NAVIGATE_HOMEWORK) }, enabled = shellReady)
                            Separator()
                            Item("考试安排", onClick = { appCommandBus.send(AppCommand.NAVIGATE_EXAMS) }, enabled = shellReady)
                            Item("课件", onClick = { appCommandBus.send(AppCommand.NAVIGATE_COURSEWARE) }, enabled = shellReady)
                            Item("教室查询", onClick = { appCommandBus.send(AppCommand.NAVIGATE_CLASSROOM_OCCUPANCY) }, enabled = shellReady)
                            Item("邮箱", onClick = { appCommandBus.send(AppCommand.NAVIGATE_MAILBOX) }, enabled = shellReady)
                            Separator()
                            Item(
                                "设置…",
                                onClick = { appCommandBus.send(AppCommand.NAVIGATE_SETTINGS) },
                                shortcut = KeyShortcut(Key.Comma, meta = true),
                                enabled = shellReady,
                            )
                        }
                        Menu("数据") {
                            Item(
                                "刷新当前页面",
                                onClick = { appCommandBus.send(AppCommand.REFRESH_CURRENT) },
                                shortcut = KeyShortcut(Key.R, meta = true),
                                enabled = shellReady,
                            )
                        }
                    }
                    LaunchedEffect(Unit) {
                        window.minimumSize = Dimension(720, 520)
                    }
                    val homeworkFileGateway = remember(window) {
                        DesktopHomeworkFileGateway { window }
                    }
                    val systemCalendarGateway = remember { DesktopSystemCalendarGateway() }
                    App(
                        accountSecurityStore = accountSecurityStore,
                        cacheStoreHandle = cacheStoreHandle,
                        homeworkFileGateway = homeworkFileGateway,
                        coursewareDirectoryGateway = homeworkFileGateway,
                        systemCalendarGateway = systemCalendarGateway,
                        appCommandBus = appCommandBus,
                        captchaRecognizer = captchaRecognizer,
                        onAuthenticatedSessionChanged = { authenticatedSession.value = it },
                    )
            }
        }
    } finally {
        cacheStoreHandle.store.close()
    }
}

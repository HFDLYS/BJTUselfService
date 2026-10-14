package team.bjtuss.bjtuselfservice.windows

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Library
import com.sun.jna.Native
import java.awt.Dimension
import java.awt.Image
import java.awt.Toolkit
import java.awt.AWTEvent
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.App
import team.bjtuss.bjtuselfservice.shared.AuthenticatedSession
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognitionResult
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppCommandBus

private const val CAPTCHA_VERIFICATION_ARGUMENT = "--verify-captcha-model="
private const val CAPTCHA_LOGITS_ARGUMENT = "--dump-captcha-logits="

fun main(args: Array<String>) {
    val captchaRecognizer = WindowsTorchCaptchaRecognizer()
    args.firstOrNull { it.startsWith(CAPTCHA_LOGITS_ARGUMENT) }?.let { argument ->
        val image = File(argument.removePrefix(CAPTCHA_LOGITS_ARGUMENT))
        require(image.isFile) { "验证码验证图片不存在：${image.absolutePath}" }
        val logits = captchaRecognizer.recognizeRawLogits(image.readBytes())
            ?: error("captcha_logits=failed")
        println("captcha_logits=${logits.joinToString(",")}")
        captchaRecognizer.close()
        return
    }
    args.firstOrNull { it.startsWith(CAPTCHA_VERIFICATION_ARGUMENT) }?.let { argument ->
        val image = File(argument.removePrefix(CAPTCHA_VERIFICATION_ARGUMENT))
        require(image.isFile) { "验证码验证图片不存在：${image.absolutePath}" }
        when (val result = runBlocking { captchaRecognizer.recognize(image.readBytes()) }) {
            is CaptchaRecognitionResult.Success -> {
                println(
                    "captcha_model=ready expression=${result.value.expression} " +
                        "answer=${result.value.answer}",
                )
                captchaRecognizer.close()
                return
            }
            is CaptchaRecognitionResult.Failed -> {
                captchaRecognizer.close()
                error("captcha_model=failed reason=${result.reason}")
            }
        }
    }
    val accountSecurityStore = createWindowsAccountSecurityStore()
    val cacheStoreHandle = createWindowsCacheStore()

    try {
        application {
            val state = rememberWindowState(width = 1080.dp, height = 720.dp)
            val appCommandBus = remember { AppCommandBus() }
            val shellSubscribers by appCommandBus.subscriptionCount.collectAsState()
            val authenticatedSession = remember { mutableStateOf<AuthenticatedSession?>(null) }

            Window(
                onCloseRequest = ::exitApplication,
                title = "交大自由行 KMP",
                state = state,
                icon = appWindowIcon(),
            ) {
                LaunchedEffect(Unit) {
                    window.minimumSize = Dimension(720, 520)
                    applyWindowsDarkTitleBar(window.windowHandle)
                    // 多尺寸图标列表：Windows 按当前 DPI 选最合适尺寸，避免缩放模糊。
                    appWindowIconImages()?.let { images ->
                        window.iconImages = images
                    }
                }
                // Count completed content clicks only. Focus, mouse movement, wheel
                // scrolling and closing the native window never request a refresh.
                val clickListener = remember(window) {
                    AWTEventListener { event ->
                        val mouse = event as? MouseEvent
                        if (mouse?.id == MouseEvent.MOUSE_CLICKED &&
                            SwingUtilities.getWindowAncestor(mouse.component) === window) {
                            authenticatedSession.value?.notifyFunctionClicked()
                        }
                    }
                }
                DisposableEffect(window, clickListener) {
                    val toolkit = Toolkit.getDefaultToolkit()
                    toolkit.addAWTEventListener(clickListener, AWTEvent.MOUSE_EVENT_MASK)
                    onDispose { toolkit.removeAWTEventListener(clickListener) }
                }
                val homeworkFileGateway = remember { WindowsHomeworkFileGateway() }
                App(
                    accountSecurityStore = accountSecurityStore,
                    cacheStoreHandle = cacheStoreHandle,
                    homeworkFileGateway = homeworkFileGateway,
                    coursewareDirectoryGateway = homeworkFileGateway,
                    appCommandBus = appCommandBus,
                    captchaRecognizer = captchaRecognizer,
                    onAuthenticatedSessionChanged = { authenticatedSession.value = it },
                )
            }
        }
    } finally {
        captchaRecognizer.close()
        cacheStoreHandle.store.close()
    }
}

/** 窗口标题栏/任务栏图标：从资源加载品牌 PNG，失败时回退默认图标。 */
private fun appWindowIcon(): BitmapPainter? {
    val stream = appWindowIconResource() ?: return null
    return runCatching {
        BitmapPainter(ImageIO.read(stream).toComposeImageBitmap())
    }.getOrNull()
}

/** 窗口标题栏/任务栏图标：多尺寸图标列表（Windows 自动选最合适尺寸，各 DPI 清晰）。 */
private fun appWindowIconImages(): List<Image>? {
    val stream = appWindowIconResource() ?: return null
    val source = runCatching { ImageIO.read(stream) }.getOrNull() ?: return null
    return listOf(
        source.getScaledInstance(16, 16, Image.SCALE_SMOOTH),
        source.getScaledInstance(32, 32, Image.SCALE_SMOOTH),
        source.getScaledInstance(48, 48, Image.SCALE_SMOOTH),
    )
}

private fun appWindowIconResource(): java.io.InputStream? =
    Thread.currentThread().contextClassLoader.getResourceAsStream("app-icon.png")

/**
 * Windows 10/11 标题栏跟随系统深色：DWM 的 DWMWA_USE_IMMERSIVE_DARK_MODE
 * （属性 20，Windows 10 1809+；Windows 11 22H2 上 20/19 均可用）。
 * 不设置时标题栏始终用系统浅色主题（白底黑字），与应用内深色主题割裂。
 */
private fun applyWindowsDarkTitleBar(windowHandle: Long) {
    if (windowHandle == 0L) return
    runCatching {
        val darkMode = intArrayOf(1)
        Dwmapi.INSTANCE.DwmSetWindowAttribute(
            windowHandle,
            DWMWA_USE_IMMERSIVE_DARK_MODE,
            darkMode,
            Int.SIZE_BYTES,
        )
    }
}

private interface Dwmapi : Library {
    fun DwmSetWindowAttribute(
        hwnd: Long,
        attribute: Int,
        pvAttribute: IntArray,
        cbAttribute: Int,
    ): Int

    companion object {
        val INSTANCE: Dwmapi by lazy {
            Native.load("Dwmapi", Dwmapi::class.java)
        }
    }
}

private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

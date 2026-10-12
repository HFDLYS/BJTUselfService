package team.bjtuss.bjtuselfservice.shared.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheetOrAlert

internal enum class CampusAccountEntry { CAMPUS_CARD, NETWORK }

@Composable
internal fun CampusAccountEntryDialog(
    entry: CampusAccountEntry,
    family: PlatformFamily,
    onDismiss: () -> Unit,
) {
    var feedback by remember(entry) { mutableStateOf<String?>(null) }
    // 两个入口同一套外观：标题 + 关闭，正文依次为说明、二维码、操作按钮、结果提示。
    // iOS sheet 按正文高度展开；Android 对话框正文可滚动，小窗里按钮不会被裁掉。
    AppleSheetOrAlert(
        onDismissRequest = onDismiss,
        title = if (entry == CampusAccountEntry.CAMPUS_CARD) "校园卡充值" else "校园网充值",
        fitContentHeight = true,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            when (entry) {
                CampusAccountEntry.CAMPUS_CARD -> CampusCardEntryBody(family, onDismiss) { feedback = it }
                CampusAccountEntry.NETWORK -> {
                    EntryMessage(networkPaymentMessage(family))
                    QrCode(NETWORK_PAYMENT_QR_IMAGE, "北京交通大学卡网缴费微信二维码")
                    SaveQrAndScanButton(NETWORK_PAYMENT_QR_IMAGE, onFeedback = { feedback = it })
                }
            }
            QrFeedback(feedback)
        }
    }
}

@Composable
private fun CampusCardEntryBody(family: PlatformFamily, onDismiss: () -> Unit, onFeedback: (String) -> Unit) {
    val destination = campusCardDestination(family)
    val launchCampusApp = rememberCampusAppLauncher().takeIf { destination.offersAppLaunch }
    val clipboard = LocalClipboardManager.current
    EntryMessage(destination.message)
    QrCode(MINI_PROGRAM_QR_IMAGE, "完美校园微信小程序二维码")
    SaveQrAndScanButton(MINI_PROGRAM_QR_IMAGE, onFeedback)
    if (launchCampusApp != null) {
        Button(
            onClick = {
                if (launchCampusApp()) onDismiss() else onFeedback("未找到完美校园 App。可以扫码，或复制链接到微信打开。")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("打开完美校园 App") }
    }
    OutlinedButton(
        onClick = {
            clipboard.setText(AnnotatedString(WECHAT_MINI_PROGRAM_URL))
            onFeedback(CAMPUS_CARD_LINK_COPIED)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("复制链接，粘贴到微信打开") }
}

@Composable
private fun EntryMessage(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

private val MINI_PROGRAM_QR_IMAGE = QrCodeImage(WECHAT_MINI_PROGRAM_QR_MATRIX, quietZone = 3, fileName = "完美校园小程序二维码")

private val NETWORK_PAYMENT_QR_IMAGE = QrCodeImage(NETWORK_PAYMENT_QR_MATRIX, quietZone = 4, fileName = "校园网缴费二维码")

/** 手机端保存二维码并打开微信扫一扫；桌面端没有这个能力，提示用手机扫屏幕。 */
@Composable
private fun SaveQrAndScanButton(image: QrCodeImage, onFeedback: (String) -> Unit) {
    val scan = rememberQrCodeScanner()
    if (scan == null) {
        Text(
            "用手机微信扫描",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
        return
    }
    FilledTonalButton(
        onClick = { scan(image, onFeedback) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("保存二维码并打开微信扫一扫") }
}

@Composable
private fun QrFeedback(message: String?) {
    message ?: return
    Text(
        message,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun QrCode(image: QrCodeImage, description: String) {
    val matrix = image.matrix
    val quietZone = image.quietZone
    Surface(
        modifier = Modifier.size(200.dp).semantics { contentDescription = description },
        color = Color.White,
        shape = MaterialTheme.shapes.medium,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val moduleCount = matrix.size + quietZone * 2
            val moduleSize = minOf(size.width, size.height) / moduleCount
            val startX = (size.width - moduleSize * moduleCount) / 2f
            val startY = (size.height - moduleSize * moduleCount) / 2f
            matrix.forEachIndexed { row, values ->
                values.forEachIndexed { column, value ->
                    if (value == '1') {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(
                                startX + (column + quietZone) * moduleSize,
                                startY + (row + quietZone) * moduleSize,
                            ),
                            size = Size(moduleSize + 0.15f, moduleSize + 0.15f),
                        )
                    }
                }
            }
        }
    }
}

private fun networkPaymentMessage(family: PlatformFamily): String = when (family) {
    PlatformFamily.MacOS -> "请使用手机微信扫描二维码，进入校园网缴费页面。"
    else -> "保存二维码后，在微信扫一扫中点「相册」选择它，即可进入校园网缴费页面。"
}

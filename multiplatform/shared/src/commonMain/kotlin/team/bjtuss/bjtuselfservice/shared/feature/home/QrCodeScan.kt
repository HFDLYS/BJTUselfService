package team.bjtuss.bjtuselfservice.shared.feature.home

import androidx.compose.runtime.Composable

/** 要保存到相册的二维码：模块矩阵（'1' 为黑）、补足的静区模块数和不含扩展名的文件名。 */
internal class QrCodeImage(
    val matrix: List<String>,
    val quietZone: Int,
    val fileName: String,
)

/**
 * 把二维码画成图片存进相册，再打开微信扫一扫，用户在扫一扫里从相册选图。
 * 在微信内识别二维码不受 URL Link「一人一链」限制。
 * 结果以一句可直接显示的提示回调（主线程）；平台不支持时为 null。
 */
@Composable
internal expect fun rememberQrCodeScanner(): ((QrCodeImage, (String) -> Unit) -> Unit)?

/** 扫一扫打开后，在弹窗里告诉用户下一步。 */
internal const val QR_SAVED_AND_SCANNER_OPENED = "二维码已保存到相册。在扫一扫中点「相册」，选择这张图片即可。"

internal const val QR_SAVED_WECHAT_MISSING = "二维码已保存到相册，但没有找到微信。"

internal const val QR_SAVE_FAILED = "无法保存到相册。请先截图，再在微信扫一扫中点「相册」选择截图。"

/** 二维码图片的像素数据：每个模块 [pixelsPerModule] 像素，外圈补 [QrCodeImage.quietZone] 模块白边。 */
internal fun QrCodeImage.pixelSize(pixelsPerModule: Int): Int =
    (matrix.size + quietZone * 2) * pixelsPerModule

/** 遍历黑色模块的像素矩形（左、上、边长），平台各自填充。 */
internal inline fun QrCodeImage.forEachDarkModule(pixelsPerModule: Int, fill: (left: Int, top: Int, side: Int) -> Unit) {
    matrix.forEachIndexed { row, values ->
        values.forEachIndexed { column, value ->
            if (value == '1') {
                fill((column + quietZone) * pixelsPerModule, (row + quietZone) * pixelsPerModule, pixelsPerModule)
            }
        }
    }
}

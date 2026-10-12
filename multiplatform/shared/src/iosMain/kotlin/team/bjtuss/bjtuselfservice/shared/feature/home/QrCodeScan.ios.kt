package team.bjtuss.bjtuselfservice.shared.feature.home

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSURL
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIRectFill
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val PIXELS_PER_MODULE = 16
private const val WECHAT_SCANNER_URL = "weixin://scanqrcode"

private const val QR_PHOTO_PERMISSION_DENIED =
    "没有相册写入权限。可在「设置 › 交大自由行 KMP › 照片」中允许，或先截图再用微信扫一扫识别。"

/**
 * 只申请「仅添加照片」权限（Info.plist `NSPhotoLibraryAddUsageDescription`），看不到用户已有照片，
 * 所以无法删掉上次存的同一张图，重复保存会多一张。
 * `weixin://scanqrcode` 直接打开扫一扫，2026-10-12 iPhone 实测可用。
 */
@Composable
internal actual fun rememberQrCodeScanner(): ((QrCodeImage, (String) -> Unit) -> Unit)? = ::saveAndOpenScanner

private fun saveAndOpenScanner(image: QrCodeImage, onResult: (String) -> Unit) {
    val rendered = render(image)
    if (rendered == null) {
        onResult(QR_SAVE_FAILED)
        return
    }
    PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
        if (status != PHAuthorizationStatusAuthorized && status != PHAuthorizationStatusLimited) {
            onMain { onResult(QR_PHOTO_PERMISSION_DENIED) }
            return@requestAuthorizationForAccessLevel
        }
        PHPhotoLibrary.sharedPhotoLibrary().performChanges(
            { PHAssetChangeRequest.creationRequestForAssetFromImage(rendered) },
        ) { saved, _ ->
            onMain {
                // 没存上就不跳走：留在弹窗里，用户还能先截图。
                if (!saved) {
                    onResult(QR_SAVE_FAILED)
                } else {
                    openWeChatScanner { opened ->
                        onResult(if (opened) QR_SAVED_AND_SCANNER_OPENED else QR_SAVED_WECHAT_MISSING)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun render(image: QrCodeImage): UIImage? {
    val side = image.pixelSize(PIXELS_PER_MODULE).toDouble()
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(side, side), true, 1.0)
    return try {
        UIColor.whiteColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, side, side))
        UIColor.blackColor.setFill()
        image.forEachDarkModule(PIXELS_PER_MODULE) { left, top, size ->
            UIRectFill(CGRectMake(left.toDouble(), top.toDouble(), size.toDouble(), size.toDouble()))
        }
        UIGraphicsGetImageFromCurrentImageContext()
    } finally {
        UIGraphicsEndImageContext()
    }
}

private fun openWeChatScanner(onOpened: (Boolean) -> Unit) {
    val url = NSURL(string = WECHAT_SCANNER_URL)
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>()) { opened ->
        onOpened(opened)
    }
}

private fun onMain(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue(), block)
}

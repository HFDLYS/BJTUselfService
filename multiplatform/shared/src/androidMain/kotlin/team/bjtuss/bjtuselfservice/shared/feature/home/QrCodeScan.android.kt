package team.bjtuss.bjtuselfservice.shared.feature.home

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val WECHAT_PACKAGE = "com.tencent.mm"
private const val ALBUM_DIRECTORY = "交大自由行"
private const val PIXELS_PER_MODULE = 16

/**
 * Android 10+ 通过 MediaStore 写入「Pictures/交大自由行」，无需存储权限；同名旧图先删除，重复保存不堆积。
 * Android 9 写公共相册需要存储权限，这里不申请，提示用户先截图。
 * 扫一扫用微信内部入口（`LauncherUI.From.Scaner.Shortcut`），不是公开接口；启动失败退回微信首页。
 * 宿主 Manifest 需在 `<queries>` 声明 `com.tencent.mm`。
 */
@Composable
internal actual fun rememberQrCodeScanner(): ((QrCodeImage, (String) -> Unit) -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        { image, onResult ->
            val saved = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                runCatching { saveToAlbum(context, image) }.isSuccess
            // 没存上就不跳走：留在弹窗里，用户还能先截图。
            onResult(
                when {
                    !saved -> QR_SAVE_FAILED
                    openWeChatScanner(context) -> QR_SAVED_AND_SCANNER_OPENED
                    else -> QR_SAVED_WECHAT_MISSING
                },
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun saveToAlbum(context: Context, image: QrCodeImage) {
    val side = image.pixelSize(PIXELS_PER_MODULE)
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint().apply { color = Color.BLACK; isAntiAlias = false }
    image.forEachDarkModule(PIXELS_PER_MODULE) { left, top, size ->
        canvas.drawRect(left.toFloat(), top.toFloat(), (left + size).toFloat(), (top + size).toFloat(), paint)
    }

    val resolver = context.contentResolver
    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val relativePath = "${Environment.DIRECTORY_PICTURES}/$ALBUM_DIRECTORY/"
    val displayName = "${image.fileName}.png"
    // 只会命中本应用此前写入的同名图片（分区存储下看不到别人的文件）。
    resolver.delete(
        collection,
        "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
        arrayOf(displayName, relativePath),
    )
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = checkNotNull(resolver.insert(collection, values)) { "MediaStore insert failed" }
    try {
        resolver.openOutputStream(uri).use { stream ->
            check(stream != null && bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG write failed" }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    } finally {
        bitmap.recycle()
    }
}

private fun openWeChatScanner(context: Context): Boolean {
    val scanner = Intent()
        .setComponent(ComponentName(WECHAT_PACKAGE, "$WECHAT_PACKAGE.ui.LauncherUI"))
        .putExtra("LauncherUI.From.Scaner.Shortcut", true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (runCatching { context.startActivity(scanner) }.isSuccess) return true
    val launcher = context.packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE) ?: return false
    return runCatching { context.startActivity(launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

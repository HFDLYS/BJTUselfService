package team.bjtuss.bjtuselfservice.shared.feature.home

import androidx.compose.runtime.Composable

/** 桌面端用手机扫屏幕上的二维码，不保存图片。 */
@Composable
internal actual fun rememberQrCodeScanner(): ((QrCodeImage, (String) -> Unit) -> Unit)? = null

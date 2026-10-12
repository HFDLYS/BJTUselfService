package team.bjtuss.bjtuselfservice.shared.feature.home

import androidx.compose.runtime.Composable

/** iOS 不唤起完美校园 App：没有公开的 URL Scheme，只保留扫码与复制链接。 */
@Composable
internal actual fun rememberCampusAppLauncher(): (() -> Boolean)? = null

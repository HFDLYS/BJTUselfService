package team.bjtuss.bjtuselfservice.shared.feature.home

import androidx.compose.runtime.Composable

/**
 * 启动本机「完美校园」App 的动作；平台不支持时为 null。
 * 动作返回 false 表示没装或系统拒绝启动，调用方就地提示改用扫码或复制链接。
 */
@Composable
internal expect fun rememberCampusAppLauncher(): (() -> Boolean)?

package team.bjtuss.bjtuselfservice.shared.feature.shell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable

/*
 * HyperOS 小窗把顶部拖动条（三个点）和底部手势条报告为 captionBar，此时 statusBars 与
 * navigationBars 都是 0；只读后者会让标题栏、底栏压在系统控件下面。
 * 全屏时 captionBar 为 0，iOS 与桌面恒为 0，这两处数值与 statusBars/navigationBars 相同。
 */

/** 顶部系统栏：状态栏 ∪ 小窗标题栏。 */
internal val WindowInsets.Companion.topSystemBars: WindowInsets
    @Composable @NonRestartableComposable
    get() = statusBars.union(captionBar.only(WindowInsetsSides.Top))

/** 底部系统栏：导航栏 ∪ 小窗底部手势条。 */
internal val WindowInsets.Companion.bottomSystemBars: WindowInsets
    @Composable @NonRestartableComposable
    get() = navigationBars.union(captionBar.only(WindowInsetsSides.Bottom))

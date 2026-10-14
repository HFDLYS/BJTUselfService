package team.bjtuss.bjtuselfservice.shared.feature.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private const val GUIDE_WAIT_SECONDS = 3

@Composable
internal fun TimedGuideDialog(
    title: String,
    body: String,
    saveFailed: Boolean,
    onConfirm: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(GUIDE_WAIT_SECONDS) }
    LaunchedEffect(title) {
        remaining = GUIDE_WAIT_SECONDS
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(body)
                if (saveFailed) {
                    Text("确认保存失败，请重试。", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = remaining == 0) {
                Text(if (remaining > 0) "确认（$remaining）" else "确认")
            }
        },
    )
}

/** 1.8.3 原有的新版导航、CITEL、作业聚合三个弹窗合为一个。 */
@Composable
internal fun Release183GuideDialog(saveFailed: Boolean, onConfirm: () -> Unit) {
    TimedGuideDialog(
        title = "1.8.3 版本说明",
        body = "· 新版导航：底栏改为「首页」和「应用」。常用页请到「应用 → 设置 → 底栏显示」添加。\n\n" +
            "· 新增 CITEL：CITEL 作业已接入。请到「应用 → 设置」配置账号后再使用。\n\n" +
            "· 作业聚合：课程平台、物理在线和 CITEL 的作业统一显示在「作业」中。可在设置中关闭「作业聚合」。",
        saveFailed = saveFailed,
        onConfirm = onConfirm,
    )
}

@Composable
internal fun Release184GuideDialog(saveFailed: Boolean, onConfirm: () -> Unit) {
    TimedGuideDialog(
        title = "1.8.4 新增功能",
        body = "· 演示实验：首页日程和「应用 → 演示实验」显示已预约的大学物理演示实验。" +
            "请到「应用 → 设置」启用并填写演示实验账号，同步需要校园网。\n\n" +
            "· 桌面小组件：iOS 与 Android 新增「临近课程」「今日课程」「本周课表」，在桌面长按添加，同步课表后显示。\n\n" +
            "· 错峰上课：第二节在逸夫教学楼、思源西楼的课按 10:30–12:20 显示。可在设置中关闭「错峰上课时间」。",
        saveFailed = saveFailed,
        onConfirm = onConfirm,
    )
}

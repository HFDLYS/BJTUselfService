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

/** 1.8.5 紧急修复：学校 10 月 20 日切换 MIS 认证后的可用范围说明。 */
@Composable
internal fun EmergencyFix185NoticeDialog(saveFailed: Boolean, onConfirm: () -> Unit) {
    TimedGuideDialog(
        title = "1.8.5 紧急修复说明",
        body = "· 登录：已适配 10 月 20 日起启用的 MIS 新统一身份认证方式，课表、成绩、考试等教务系统相关功能可正常使用。\n\n" +
            "· 课程平台：学校系统迁移尚未完成，课程平台的作业和课件下载暂时无法使用。\n\n" +
            "· 物理在线：原统一认证登录路径已失效，请到「应用 → 设置 → 物理在线账号设置」填写物理在线的独立账号和密码。\n\n" +
            "· 验证码：新认证系统有新的验证码方式，适配尚待验证；登录遇到验证码问题请反馈。\n\n" +
            "· 本版本为紧急修复接入，AI-Agent 接入等新功能仍在开发中，其他功能有待验证。",
        saveFailed = saveFailed,
        onConfirm = onConfirm,
    )
}

/** 物理在线改用独立账号：升级时已自动关闭该功能，紧接紧急修复说明单独提示。 */
@Composable
internal fun PhyVlabAccountNoticeDialog(saveFailed: Boolean, onConfirm: () -> Unit) {
    TimedGuideDialog(
        title = "物理在线已自动关闭",
        body = "物理在线原来通过统一身份认证登录，该路径已失效，现改为输入物理在线的独立账号和密码。\n\n" +
            "为避免开关开着却无法同步，本次升级已自动关闭物理在线。\n\n" +
            "请到「应用 → 设置 → 物理在线账号设置」配置账号密码，保存成功后会自动重新打开；" +
            "也可以在配置后于「功能开关」中手动打开。",
        saveFailed = saveFailed,
        onConfirm = onConfirm,
    )
}

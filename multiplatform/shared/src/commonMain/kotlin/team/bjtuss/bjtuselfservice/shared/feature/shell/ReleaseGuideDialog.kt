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

@Composable
internal fun RedesignGuideDialog(saveFailed: Boolean, onConfirm: () -> Unit) {
    TimedGuideDialog(
        title = "新版导航",
        body = "底栏改为「首页」和「应用」。常用页请到「应用 → 设置 → 底栏显示」添加。",
        saveFailed = saveFailed,
        onConfirm = onConfirm,
    )
}

@Composable
internal fun CitelGuideDialog(saveFailed: Boolean, onConfirm: () -> Unit) {
    TimedGuideDialog(
        title = "新增 CITEL",
        body = "CITEL 作业已接入。请到「应用 → 设置」配置账号后再使用。",
        saveFailed = saveFailed,
        onConfirm = onConfirm,
    )
}

@Composable
internal fun AssignmentAggregateGuideDialog(saveFailed: Boolean, onConfirm: () -> Unit) {
    TimedGuideDialog(
        title = "各平台作业已聚合",
        body = "课程平台、物理在线和 CITEL 的作业统一显示在「作业」中。可在设置中关闭「作业聚合」。",
        saveFailed = saveFailed,
        onConfirm = onConfirm,
    )
}

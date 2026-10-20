package team.bjtuss.bjtuselfservice.shared.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.shared.agent.AGENT_SETUP_PROMPT
import team.bjtuss.bjtuselfservice.shared.agent.AgentInstallResult
import team.bjtuss.bjtuselfservice.shared.agent.AgentIntegration
import team.bjtuss.bjtuselfservice.shared.agent.AgentIntegrationStatus
import team.bjtuss.bjtuselfservice.shared.agent.ClaudeSkillLink

/**
 * 设置页“AI 助手”（仅桌面端）：一键把只读命令行和 Skill 装到本机，供 Claude Code、Codex 等 agent 使用。
 * 文件读写放后台线程；按下按钮立即进入“处理中”状态。
 */
@Composable
internal fun AgentAssistantCard(
    integration: AgentIntegration,
    gradesAllowed: Boolean,
    onGradesAllowed: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var status by remember { mutableStateOf<AgentIntegrationStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var copied by remember { mutableStateOf(false) }

    suspend fun refresh() {
        status = withContext(Dispatchers.Default) { runCatching { integration.status() }.getOrNull() }
    }
    LaunchedEffect(integration) { refresh() }

    fun run(action: () -> AgentInstallResult) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.Default) { action() }
            message = when (result) {
                is AgentInstallResult.Done -> result.message to false
                is AgentInstallResult.Failed -> result.reason to true
            }
            refresh()
            busy = false
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI 助手", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                status?.let { StatusChip(it) }
            }
            Text(
                "让本机的 Claude Code、Codex 等 AI 助手读取课表、日程、作业和考试（只读，App 关着也能用）。" +
                    "它们读到的内容会发给你所用的 AI 服务商。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val current = status
            if (current != null && !current.installable) {
                Text(current.unavailableReason.orEmpty(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { run { integration.install() } },
                    enabled = !busy && current?.installable == true,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(when {
                        busy -> "处理中…"
                        current?.installed == true -> "重新安装"
                        else -> "安装到本机"
                    })
                }
                if (current?.installed == true) {
                    OutlinedButton(onClick = { run { integration.uninstall() } }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("移除")
                    }
                }
            }
            message?.let { (text, error) ->
                Text(text, style = MaterialTheme.typography.bodySmall,
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (current?.installed == true) {
                Text(
                    "Skill：${current.skillPath}\n" + when (current.claude) {
                        ClaudeSkillLink.SHARED, ClaudeSkillLink.LINKED -> "Claude Code 可直接使用。"
                        ClaudeSkillLink.NOT_LINKED -> "Claude Code 未链接（~/.claude/skills 下已有同名目录）。"
                        ClaudeSkillLink.ABSENT -> "Codex 等读取 ~/.agents/skills 的工具可直接使用。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(AGENT_SETUP_PROMPT, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(AGENT_SETUP_PROMPT))
                            copied = true
                        }) { Text(if (copied) "已复制" else "复制") }
                    }
                }
                Text("新开一个 AI 助手会话，把上面这句话发给它即可。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("允许读取成绩")
                    Text("默认关闭。打开后 AI 助手可以读取全部成绩。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = gradesAllowed, onCheckedChange = onGradesAllowed)
            }
        }
    }
}

@Composable
private fun StatusChip(status: AgentIntegrationStatus) {
    val (label, warn) = when {
        !status.installable -> "不可用" to true
        !status.installed -> "未安装" to false
        status.stale -> "需要重新安装" to true
        else -> "已安装" to false
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(label, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

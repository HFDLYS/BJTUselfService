package team.bjtuss.bjtuselfservice.shared.feature.settings

import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalBottomBarClearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.shared.currentPlatform
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.PlatformInfo
import team.bjtuss.bjtuselfservice.shared.platformSupportsDynamicColor
import team.bjtuss.bjtuselfservice.shared.update.AppUpdateChecker
import team.bjtuss.bjtuselfservice.shared.update.ReleaseNoteBlock
import team.bjtuss.bjtuselfservice.shared.update.annotatedInlineMarkdown
import team.bjtuss.bjtuselfservice.shared.update.parseReleaseNotes
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheetOrAlert
import team.bjtuss.bjtuselfservice.shared.agent.platformAgentIntegration
import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalTopBarClearance
import team.bjtuss.bjtuselfservice.shared.feature.shell.TopScrollColumn

@Composable
internal fun AssignmentAggregationCard(
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
) = SettingSwitchCard("作业聚合", "聚合课程平台、CITEL和物理在线作业", enabled, onEnabled)

@Composable
internal fun StaggeredClassTimeCard(
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
) = SettingSwitchCard(
    "错峰上课时间",
    "第二节在逸夫教学楼（YF）、思源西楼（SX）上课时，按 10:30–12:20 显示",
    enabled,
    onEnabled,
)

@Composable
private fun SettingSwitchCard(
    title: String,
    detail: String,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabled)
        }
    }
}

@Composable
internal fun FeatureSwitchesCard(
    phyVlabEnabled: Boolean, physicsLabEnabled: Boolean, citelEnabled: Boolean,
    physicsLabConfigured: Boolean, citelConfigured: Boolean,
    onPhyVlabEnabled: (Boolean) -> Unit, onPhysicsLabEnabled: (Boolean) -> Unit, onCitelEnabled: (Boolean) -> Unit,
    onPhysicsLabSettings: () -> Unit, onCitelSettings: () -> Unit,
    physicsDemoEnabled: Boolean = false, physicsDemoConfigured: Boolean = false,
    onPhysicsDemoEnabled: (Boolean) -> Unit = {}, onPhysicsDemoSettings: () -> Unit = {},
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("功能开关", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FeatureSwitchRow("启用物理在线", phyVlabEnabled, false, onPhyVlabEnabled)
            FeatureSwitchRow("启用物理实验", physicsLabEnabled, physicsLabConfigured, onPhysicsLabEnabled, onPhysicsLabSettings)
            FeatureSwitchRow("启用演示实验", physicsDemoEnabled, physicsDemoConfigured, onPhysicsDemoEnabled, onPhysicsDemoSettings)
            FeatureSwitchRow("启用 CITEL", citelEnabled, citelConfigured, onCitelEnabled, onCitelSettings)
        }
    }
}

@Composable
private fun FeatureSwitchRow(name: String, enabled: Boolean, configured: Boolean,
    onEnabled: (Boolean) -> Unit, onSettings: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column {
                Text(name)
                if (onSettings != null && !configured) Text("请先配置账号和密码", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (configured) Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Text("已配置", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall,
                    maxLines = 1)
            }
        }
        if (onSettings != null) TextButton(onClick = onSettings,
            modifier = Modifier.semantics { contentDescription = "${name.removePrefix("启用").trim()}账号设置" }) { Text("设置") }
        Switch(checked = enabled, onCheckedChange = onEnabled, enabled = onSettings == null || configured || enabled)
    }
}

@Composable
fun SettingsWorkspace(
    model: SettingsScreenModel,
    accountName: String,
    platform: PlatformInfo,
    expanded: Boolean,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    physicsLabConfigured: Boolean = false,
    citelConfigured: Boolean = false,
    onPhysicsLabSettings: () -> Unit = {},
    onCitelSettings: () -> Unit = {},
    physicsDemoEnabled: Boolean = false,
    physicsDemoConfigured: Boolean = false,
    onPhysicsDemoEnabled: (Boolean) -> Unit = {},
    onPhysicsDemoSettings: () -> Unit = {},
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val pageScrollState = rememberScrollState()
    // 真实偏移上报给壳层算玻璃浓度（手势累加会漂，读列表状态不会）。

    if (confirmClear) {
        AppleSheetOrAlert(
            onDismissRequest = { confirmClear = false },
            title = "清除当前账号离线缓存？",
            confirmLabel = "清除缓存",
            onConfirm = {
                confirmClear = false
                scope.launch { model.clearOfflineCache() }
            },
            dismissLabel = "取消",
        ) {
            Text("成绩、课表、考试和作业的离线副本会被删除；不会退出账号。之后可从学校系统重新下载。")
        }
    }

    if (confirmWipe) {
        AppleSheetOrAlert(
            onDismissRequest = { confirmWipe = false },
            title = "清除全部本地数据？",
            confirmLabel = "全部清除",
            onConfirm = {
                confirmWipe = false
                scope.launch { model.clearAllLocalData() }
            },
            dismissLabel = "取消",
        ) {
            Text(
                "所有账号的离线缓存、应用设置和系统安全存储中的登录信息都会被删除，" +
                    "清理成功后将立即退出登录。适合卸载应用前彻底清理本机数据。"
            )
        }
    }

    // 检查结果弹窗提到 SettingsWorkspace 外层渲染：「前往下载」属于应用壳层导航动作，
    // 挂在页面里时用户不在设置页就永远看不到自动检测出的新版本提示。

    TopScrollColumn(
        state = pageScrollState,
        modifier = modifier.fillMaxSize().desktopTouchScroll(pageScrollState),
        contentModifier = Modifier.padding(
            horizontal = if (expanded) 8.dp else 16.dp,
            vertical = 14.dp,
        )
            // 原生栏 underlap 时视口顶边贴屏幕顶，首项靠这份顶边距让开（其余平台恒 0）。
            .padding(top = LocalTopBarClearance.current, bottom = LocalBottomBarClearance.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingCard("账户", accountName.ifBlank { "未登录" })

        AssignmentAggregationCard(state.preferences.aggregateAssignments, model::setAggregateAssignments)
        StaggeredClassTimeCard(state.preferences.staggeredSecondSlot, model::setStaggeredSecondSlot)
        FeatureSwitchesCard(state.preferences.isPhyVlabEnabled, state.preferences.isPhysicsLabEnabled,
            state.preferences.isCitelEnabled, physicsLabConfigured, citelConfigured,
            model::setPhyVlabEnabled, model::setPhysicsLabEnabled, model::setCitelEnabled,
            onPhysicsLabSettings, onCitelSettings, physicsDemoEnabled, physicsDemoConfigured, onPhysicsDemoEnabled, onPhysicsDemoSettings)
        // 本机 AI Agent 接入只在桌面端（macOS/Windows 共用 MacOS 家族）。
        val agentIntegration = remember(platform.family) {
            if (platform.family == PlatformFamily.MacOS) platformAgentIntegration() else null
        }
        if (agentIntegration != null) {
            AgentAssistantCard(agentIntegration, state.preferences.agentGradesAllowed, model::setAgentGradesAllowed)
        }
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("底栏显示", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                BottomNavigationSettings(state.preferences, model)
            }
        }

        // 动态取色设置仅用于支持此能力的平台。
        if (platformSupportsDynamicColor()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("动态取色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "打开后使用系统壁纸颜色（Material You）；关闭则使用应用默认配色。浅色/深色仍跟随系统。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            "动态取色",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.preferences.dynamicColor,
                            onCheckedChange = model::setDynamicColor,
                        )
                    }
                    if (state.saveFailed) {
                        Feedback("设置保存失败，请重试。", true, model::dismissFeedback)
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("版本与项目", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${platform.displayName} · KMP 迁移构建 v${AppUpdateChecker.CURRENT_VERSION}\n" +
                        "功能对齐基线：原安卓 v1.7.0",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { scope.launch { model.checkForUpdate() } },
                    enabled = state.updateCheck !is UpdateCheckState.Checking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.updateCheck is UpdateCheckState.Checking) "正在检查更新…" else "检查更新")
                }
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://github.com/HFDLYS/BJTUselfService") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("原作者 GitHub（安卓原版）")
                }
                OutlinedButton(
                    onClick = {
                        uriHandler.openUri("https://github.com/JasonZhang1225/BJTUselfService-KMP-Refreshed")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("本仓库 GitHub（Kotlin MultiPlatform跨平台）")
                }
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://github.com/optsimauth/BJTUselfServiceAIO") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("AIO 版 GitHub（Flutter 跨平台）")
                }
                Text(
                    "预发布阶段更新检测指向本仓库 GitHub Releases（含 pre-release）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("本地数据与会话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                when (state.cacheAction) {
                    OfflineCacheActionState.Clearing -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    OfflineCacheActionState.Cleared ->
                        Feedback("离线缓存已清除；账号仍保留。", false, model::dismissFeedback)
                    OfflineCacheActionState.Failed ->
                        Feedback("离线缓存清除失败，请稍后重试。", true, model::dismissFeedback)
                    OfflineCacheActionState.Idle -> Unit
                }
                when (state.dataWipeAction) {
                    OfflineCacheActionState.Clearing -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    OfflineCacheActionState.Cleared ->
                        Feedback("本地数据已全部清除；下次启动需重新登录。", false, model::dismissFeedback)
                    OfflineCacheActionState.Failed ->
                        Feedback("本地数据清除失败，请重试或参考卸载清理指引。", true, model::dismissFeedback)
                    OfflineCacheActionState.Idle -> Unit
                }
                // macOS/Windows 删除应用本体不会清 Application Support/注册表/Keychain，
                // 提供卸载前的应用内全量清理入口；移动端系统卸载本身即彻底清除。
                if (platform.family == PlatformFamily.MacOS) {
                    OutlinedButton(
                        onClick = { confirmWipe = true },
                        enabled = state.dataWipeAction != OfflineCacheActionState.Clearing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("清除全部本地数据")
                    }
                }
                if (expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CacheClearButton(
                            onClick = { confirmClear = true },
                            enabled = state.cacheAction != OfflineCacheActionState.Clearing,
                            modifier = Modifier.weight(1f),
                        )
                        LogoutButton(onClick = onLogout, modifier = Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CacheClearButton(
                            onClick = { confirmClear = true },
                            enabled = state.cacheAction != OfflineCacheActionState.Clearing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LogoutButton(onClick = onLogout, modifier = Modifier.fillMaxWidth())
                    }
                }
                // 仅桌面宽屏布局提示窗口行为；手机设置页不提 macOS。
                if (expanded && platform.family == PlatformFamily.MacOS) {
                    Text(
                        "关闭窗口只关闭窗口，不退出账号、不清除会话。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheClearButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text("清除离线缓存")
    }
}

/** GitHub `published_at` 是 ISO-8601（如 2026-08-09T13:24:35Z），弹窗里只展示日期；格式不符时回退原文。 */
private fun formatPublishedAt(iso: String): String =
    if (iso.length >= 10 && iso[4] == '-' && iso[7] == '-') iso.substring(0, 10) else iso

/**
 * 更新检测结果弹窗。应用壳（AuthenticatedAppShell）在 SettingsWorkspace 外层渲染它，
 * 因此自动/手动检查出的新版本提示不依赖用户停留在设置页。
 * 新版本 → 「前往下载」跳 GitHub 发布页；已最新/失败 → 轻量提示；Idle/Checking 不渲染。
 */
@Composable
fun AppUpdateResultDialog(check: UpdateCheckState, onDismiss: () -> Unit, onPostpone: () -> Unit = onDismiss) {
    val uriHandler = LocalUriHandler.current
    val releaseNotesScrollState = rememberScrollState()
    when (check) {
        is UpdateCheckState.Done -> {
            if (check.hasUpdate) {
                AppleSheetOrAlert(
                    onDismissRequest = onPostpone,
                    title = "发现新版本",
                    confirmLabel = "前往下载",
                    onConfirm = {
                        onDismiss()
                        uriHandler.openUri(check.release.htmlUrl)
                    },
                    dismissLabel = "暂不更新",
                    needsFullHeight = true,
                    scrollableBody = true,
                ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (currentPlatform().family == PlatformFamily.IOS) Modifier.weight(1f) else Modifier.heightIn(max = 560.dp))
                                .verticalScroll(releaseNotesScrollState)
                                .desktopTouchScroll(releaseNotesScrollState)
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(check.release.tagName, style = MaterialTheme.typography.titleMedium)
                            check.release.publishedAt?.let { publishedAt ->
                                Text(
                                    "发布时间：${formatPublishedAt(publishedAt)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            check.release.body?.takeIf { it.isNotBlank() }?.let { body ->
                                ReleaseNotesBody(body)
                            }
                        }
                }
            } else {
                AppleSheetOrAlert(
                    onDismissRequest = onDismiss,
                    title = "已是最新版本",
                    confirmLabel = "好",
                    onConfirm = onDismiss,
                ) {
                    Text("当前 v${AppUpdateChecker.CURRENT_VERSION} 已是最新发布（${check.release.tagName}）。")
                }
            }
        }
        UpdateCheckState.Failed -> {
            AppleSheetOrAlert(
                onDismissRequest = onDismiss,
                title = "检查更新失败",
                confirmLabel = "好",
                onConfirm = onDismiss,
            ) {
                Text("无法连接 GitHub，请检查网络后重试。")
            }
        }
        UpdateCheckState.Idle, UpdateCheckState.Checking -> Unit
    }
}

@Composable
private fun AutoSizeDialogTitle(text: String) {
    val baseStyle = MaterialTheme.typography.headlineSmall
    val startSize = baseStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 22.sp
    val minSize = 14.sp
    var fontSize by remember(text, startSize) { mutableStateOf(startSize) }
    Text(
        text = text,
        style = baseStyle.copy(fontSize = fontSize),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize > minSize) {
                val next = (fontSize.value - 1f).coerceAtLeast(minSize.value).sp
                if (next != fontSize) fontSize = next
            }
        },
    )
}

@Composable
private fun ReleaseNotesBody(markdown: String) {
    val blocks = remember(markdown) { parseReleaseNotes(markdown) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ReleaseNoteBlock.Heading -> InlineMarkdownText(
                    text = block.text,
                    style = when {
                        block.level <= 2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                is ReleaseNoteBlock.Paragraph -> InlineMarkdownText(
                    text = block.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is ReleaseNoteBlock.ListItems -> Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    block.items.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", style = MaterialTheme.typography.bodySmall)
                            InlineMarkdownText(
                                text = item,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                is ReleaseNoteBlock.Table -> ReleaseNotesTable(block)
            }
        }
    }
}

@Composable
private fun ReleaseNotesTable(table: ReleaseNoteBlock.Table) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        table.rows.forEach { row ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (table.headers.size == 2 && row.size == 2) {
                        InlineMarkdownText(
                            text = row[0],
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        InlineMarkdownText(
                            text = row[1],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        table.headers.zip(row).forEach { (header, cell) ->
                            InlineMarkdownText(
                                text = "$header：$cell",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = remember(text) { annotatedInlineMarkdown(text) },
        modifier = modifier,
        style = style.merge(fontWeight = fontWeight),
        color = color,
    )
}

@Composable
private fun LogoutButton(onClick: () -> Unit, modifier: Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text("退出并清除登录信息")
    }
}

@Composable
private fun SettingCard(title: String, value: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Feedback(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                message,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}

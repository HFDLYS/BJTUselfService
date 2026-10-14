package team.bjtuss.bjtuselfservice.shared.feature.citel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import team.bjtuss.bjtuselfservice.shared.PlatformAppTheme
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeKind
import team.bjtuss.bjtuselfservice.shared.domain.home.*
import team.bjtuss.bjtuselfservice.shared.feature.home.ChangeDetailCard
import team.bjtuss.bjtuselfservice.shared.feature.settings.*
import team.bjtuss.bjtuselfservice.shared.feature.shell.Release183GuideDialog
import team.bjtuss.bjtuselfservice.shared.feature.shell.Release184GuideDialog

/** Production Compose components with synthetic data; these are Windows renders, not iOS screenshots. */
class DuoBugFixVisualTest {
    private val output = File("build/reports/duo-bugfix-ui")

    @Test fun renderChangesAndAccountStates() = SwingUtilities.invokeAndWait {
        output.mkdirs()
        listOf(false, true).forEach { dark ->
            val scene = ImageComposeScene(1200, 840, density = Density(1f), coroutineContext = Dispatchers.Unconfined) {
                PlatformAppTheme(useDarkTheme = dark, dynamicColorEnabled = false) {
                    Surface(Modifier.fillMaxSize()) {
                        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("作业有更新", style = MaterialTheme.typography.headlineSmall)
                                ChangeDetailCard(HomeChangeRecord(HomeChangeDomain.HOMEWORK, DataChangeKind.MODIFIED,
                                    "02-2 尝试封装学生列表类。", fields = listOf(
                                        HomeChangeField("课程", "面向对象程序设计 (C++)", "面向对象程序设计 (C++)"),
                                        HomeChangeField("截止时间", "2026-10-12 00:00", "2026-10-12 00:00"),
                                        HomeChangeField("分数", "未公布成绩", "未公布成绩"),
                                        HomeChangeField("状态", "未提交", "已提交"),
                                    )), {})
                                Text("功能开关 · 尚未配置")
                                FeatureSwitchesCard(true, false, false, false, false, {}, {}, {}, {}, {})
                                Text("功能开关 · 已配置")
                                FeatureSwitchesCard(true, true, true, true, true, {}, {}, {}, {}, {})
                            }
                            Column(Modifier.weight(1f)) {
                                Text("CITEL · 已清除密码", style = MaterialTheme.typography.titleLarge)
                                IndependentAccountSettings("20260001", "", false, true, false, {}, {}, {},
                                    "已清除密码并关闭 CITEL，账号已保留。", onClear = {}, websiteUrl = "$CITEL_BASE/")
                            }
                            Column(Modifier.weight(1f)) {
                                Text("物理实验 · 默认学号", style = MaterialTheme.typography.titleLarge)
                                IndependentAccountSettings("20260001", "", false, true, false, {}, {}, {}, null,
                                    onClear = {}, websiteUrl = "http://wlsy.bjtu.edu.cn/")
                            }
                        }
                    }
                }
            }
            try {
                repeat(5) { scene.render(1_000_000_000L + it * 100_000_000L).close() }
                val image = scene.render(2_000_000_000L)
                try {
                    val data = image.encodeToData(EncodedImageFormat.PNG)!!
                    try { File(output, "components-${if (dark) "dark" else "light"}.png").writeBytes(data.bytes) }
                    finally { data.close() }
                } finally { image.close() }
            } finally { scene.close() }
        }
    }

    @Test fun renderProductionDialogs() = SwingUtilities.invokeAndWait {
        output.mkdirs()
        listOf(false, true).forEach { dark ->
            listOf("release183", "release184", "clear").forEach { kind ->
                val scene = ImageComposeScene(480, 640, density = Density(1f), coroutineContext = Dispatchers.Unconfined) {
                    PlatformAppTheme(useDarkTheme = dark, dynamicColorEnabled = false) {
                        Surface(Modifier.fillMaxSize()) {
                            when (kind) {
                                "release183" -> Release183GuideDialog(false, {})
                                "release184" -> Release184GuideDialog(false, {})
                                else -> ClearIndependentAccountDialog({}, {})
                            }
                        }
                    }
                }
                try {
                    repeat(5) { scene.render(1_000_000_000L + it * 100_000_000L).close() }
                    val image = scene.render(2_000_000_000L)
                    try {
                        val data = image.encodeToData(EncodedImageFormat.PNG)!!
                        try { File(output, "$kind-${if (dark) "dark" else "light"}.png").writeBytes(data.bytes) }
                        finally { data.close() }
                    } finally { image.close() }
                } finally { scene.close() }
            }
        }
    }
}
package team.bjtuss.bjtuselfservice.shared.data.home

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
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeChangeFeedSection

class IndependentAccountChangeFeedVisualTest {
    @Test fun renderNewDomainsInLightDarkAndLargeText() = SwingUtilities.invokeAndWait {
        val changes = listOf(
            HomeChangeRecord(HomeChangeDomain.CITEL, DataChangeKind.MODIFIED, "测试作业", "旧截止", "新截止", listOf(
                HomeChangeField("截止时间", "2026-10-21 24:00", "2026-10-23 24:00"),
                HomeChangeField("完成情况", "未完成", "已完成"),
                HomeChangeField("分数", "", "90"),
            )),
            HomeChangeRecord(HomeChangeDomain.PHYSICS_LAB, DataChangeKind.MODIFIED, "测试物理实验", "旧教室", "新教室", listOf(
                HomeChangeField("日期", "2026-10-21", "2026-10-21、2026-10-28"),
                HomeChangeField("地点", "测试实验室 A", "测试实验室 B"),
                HomeChangeField("周数", "1", "2"),
            )),
            HomeChangeRecord(HomeChangeDomain.PHYSICS_DEMO, DataChangeKind.ADDED, "光学", "", "新预约", listOf(
                HomeChangeField("日期", "", "2026-10-21"),
                HomeChangeField("时间", "", "14:10-15:50"),
                HomeChangeField("地点", "", "7418"),
            )),
        )
        val output = File("../../.artifacts/independent-feed-ui").apply { mkdirs() }
        for (dark in listOf(false, true)) {
            for (large in listOf(false, true)) {
                val mode = "${if (dark) "dark" else "light"}-${if (large) "130" else "100"}"
                val scene = ImageComposeScene(420, 1500, density = Density(1f, if (large) 1.3f else 1f), coroutineContext = Dispatchers.Unconfined) {
                    PlatformAppTheme(useDarkTheme = dark, dynamicColorEnabled = false) {
                        Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                HomeChangeFeedSection(changes, {}, {})
                                changes.forEach { change ->
                                    Text("${change.domain.title}有更新", style = MaterialTheme.typography.titleMedium)
                                    ChangeDetailCard(change) {}
                                }
                            }
                        }
                    }
                }
                try {
                    repeat(4) { scene.render(1_000_000_000L + it * 100_000_000L).close() }
                    val image = scene.render(2_000_000_000L)
                    try {
                        val data = image.encodeToData(EncodedImageFormat.PNG)!!
                        try { File(output, "$mode.png").writeBytes(data.bytes) } finally { data.close() }
                    } finally { image.close() }
                } finally { scene.close() }
            }
        }
    }
}

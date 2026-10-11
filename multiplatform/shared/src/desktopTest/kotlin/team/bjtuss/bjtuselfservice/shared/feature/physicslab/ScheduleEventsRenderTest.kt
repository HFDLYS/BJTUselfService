package team.bjtuss.bjtuselfservice.shared.feature.physicslab

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import java.io.File
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.DesktopScheduleEventsSmoke

/** Render the actual Compose components using synthetic state, without controlling a native app. */
class ScheduleEventsRenderTest {
    @Test fun renderWideAndCompactSchedulesAndConfigurationPages() {
        SwingUtilities.invokeAndWait {
            listOf(
                Triple("wide-light", 1080, "课表"), Triple("wide-dark", 1080, "课表"),
                Triple("compact-light", 390, "课表"), Triple("compact-dark", 390, "课表"),
                Triple("demo-query", 390, "演示实验"), Triple("demo-query-dark", 390, "演示实验"), Triple("demo-large-font", 390, "演示实验"), Triple("lab-settings", 390, "物理实验"), Triple("settings", 390, "设置"),
                Triple("calendar", 390, "日历"), Triple("more", 390, "更多"),
                Triple("lab-settings-dark", 390, "物理实验"), Triple("settings-dark", 390, "设置"),
                Triple("calendar-dark", 390, "日历"), Triple("more-dark", 390, "更多"),
                Triple("compact-large-font", 390, "课表"), Triple("lab-large-font", 390, "物理实验"),
            ).forEach { (name, width, tab) ->
                val scene = ImageComposeScene(width = width, height = 844, density = Density(1f, if ("large-font" in name) 1.3f else 1f), coroutineContext = Dispatchers.Unconfined) {
                    DesktopScheduleEventsSmoke(initialDark = "dark" in name, initialExpanded = width > 500, initialTab = tab)
                }
                try {
                    repeat(6) { scene.render(1_000_000_000L + it * 16_000_000L).close() }
                    val image = scene.render(1_200_000_000L)
                    try {
                        val data = image.encodeToData(EncodedImageFormat.PNG)!!
                        val file = File("build/reports/physicslab-ui/$name.png")
                        file.parentFile.mkdirs()
                        file.writeBytes(data.bytes)
                        assertTrue(file.length() > 1000)
                        data.close()
                    } finally { image.close() }
                } finally { scene.close() }
            }
        }
    }
}

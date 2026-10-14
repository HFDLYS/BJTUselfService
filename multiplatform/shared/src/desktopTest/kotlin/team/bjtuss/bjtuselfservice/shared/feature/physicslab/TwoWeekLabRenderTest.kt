package team.bjtuss.bjtuselfservice.shared.feature.physicslab

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.PlatformAppTheme
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.feature.course.*
import team.bjtuss.bjtuselfservice.shared.data.course.*
import team.bjtuss.bjtuselfservice.shared.calendar.UnavailableSystemCalendarGateway
import team.bjtuss.bjtuselfservice.shared.files.UnavailableHomeworkFileGateway

/** Real components with synthetic data; never installs or launches the simulator app. */
class TwoWeekLabRenderTest {
    @Test fun renderMergedOverviewDetailsAndDurationOptions() {
        val weeks = listOf(OccupancyWeekDate(13, "", "", LocalDate(2026, 12, 7)), OccupancyWeekDate(14, "", "", LocalDate(2026, 12, 14)))
        val labs = listOf(
            PhysicsLab(LocalDate(2026, 12, 8), 4, "超声专题", "7312B", "测试教师", 2),
            PhysicsLab(LocalDate(2026, 12, 10), 4, "声源定位的GPS模拟", "7312A", "测试教师", 2),
            PhysicsLab(LocalDate(2026, 12, 11), 4, "光纤特性和光信号传输", "7305", "测试教师", 1),
        )
        val courses = scheduleEventCourses(emptyList(), labs, weeks)
        SwingUtilities.invokeAndWait {
            for (page in listOf("overview", "specialty-detail", "design-detail", "duration-options")) {
                val scene = ImageComposeScene(width = 390, height = if (page == "duration-options") 1160 else 844,
                    density = Density(1f), coroutineContext = Dispatchers.Unconfined) {
                    PlatformAppTheme(useDarkTheme = true, dynamicColorEnabled = false) {
                        Surface(Modifier.fillMaxSize()) {
                            when (page) {
                                "duration-options" -> PhysicsLabSettingsForm(
                                    PhysicsLabState(enabled = true, labs = labs, message = "已同步 3 个实验。"),
                                    "示例账号", "", true, {}, {}, {},
                                )
                                "overview" -> {
                                    val model = remember { CourseScheduleScreenModel(object : CourseScheduleRepository {
                                        override fun load() = CourseScheduleSnapshot(emptyList(), 13)
                                        override suspend fun refresh() = CourseScheduleRefreshResult.Success(load())
                                    }) }
                                    CourseScheduleWorkspace(CourseScheduleUiState(supplementalCourses = courses, academicWeeks = weeks,
                                        physicsLabs = labs, selectedWeek = 0, isLoading = false, currentWeek = 13),
                                        emptyMap(), false, model, UnavailableHomeworkFileGateway, UnavailableSystemCalendarGateway,
                                        showCalendarExportSheet = false, onDismissCalendarExport = {}, onRefresh = {}, modifier = Modifier.fillMaxSize())
                                }
                                else -> CourseDetailContent(courses.first { it.scheduleEventTypeLabel == if (page == "specialty-detail") "专题实验" else "设计实验" },
                                    false, Modifier.fillMaxSize().padding(24.dp))
                            }
                        }
                    }
                }
                try {
                    repeat(8) { scene.render(1_000_000_000L + it * 16_000_000L).close() }
                    val image = scene.render(1_200_000_000L)
                    try {
                        val data = image.encodeToData(EncodedImageFormat.PNG)!!
                        val file = File("build/reports/two-week-labs/$page.png")
                        file.parentFile.mkdirs(); file.writeBytes(data.bytes); data.close()
                        assertTrue(file.length() > 1000)
                    } finally { image.close() }
                } finally { scene.close() }
            }
        }
    }
}

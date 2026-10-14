package team.bjtuss.bjtuselfservice.shared.feature.course

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import team.bjtuss.bjtuselfservice.shared.PlatformAppTheme
import team.bjtuss.bjtuselfservice.shared.calendar.UnavailableSystemCalendarGateway
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.course.CourseScheduleSnapshot
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.files.UnavailableHomeworkFileGateway

/** 合成课表渲染错峰色块、按日列表与详情，图片写到 build/reports/staggered-class-time/ 供人工查看。 */
class StaggeredClassTimeRenderTest {
    private fun course(id: Int, name: String, index: Int, place: String) = Course(
        id = id, courseId = "C$id", courseName = name, courseTeacher = "测试教师", courseLocationIndex = index,
        courseTime = "第1-16周", coursePlace = place, isCurrentSemester = false,
    )

    @Test fun renderStaggeredWeekGridDayListAndDetail() {
        val courses = listOf(
            course(1, "高等数学", 1, "海淀主校区,思源楼,SY101"),
            course(2, "数据结构", 9, "海淀主校区,思源楼,SY203"),
            course(3, "大学英语", 10, "海淀主校区,逸夫教学楼,YF302"),
            course(4, "线性代数", 11, "海淀西校区,思源西楼,SX501"),
            course(5, "程序设计", 18, "海淀主校区,第九教学楼,9201"),
            course(6, "体育", 19, "海淀主校区,综合体育馆,-"),
            course(7, "电路分析", 12, "海淀主校区,逸夫教学楼,YF105"),
            course(8, "概率统计", 20, "海淀主校区,逸夫教学楼,YF105"),
            // 周一第二节与普通课并存（单双周等），检验混排。
            course(9, "形势与政策", 9, "海淀主校区,逸夫教学楼,YF201"),
        )
        val weeks = listOf(OccupancyWeekDate(5, "", "", LocalDate(2026, 10, 12)))
        SwingUtilities.invokeAndWait {
            for ((page, dark) in listOf("week" to false, "week-dark" to true, "day" to false, "day-mixed" to false, "detail" to false, "week-off" to false)) {
                val scene = ImageComposeScene(width = 780, height = 1688, density = Density(2f),
                    coroutineContext = Dispatchers.Unconfined) {
                    PlatformAppTheme(useDarkTheme = dark, dynamicColorEnabled = false) {
                        CompositionLocalProvider(LocalStaggeredClassTimes provides (page != "week-off")) {
                            Surface(Modifier.fillMaxSize()) {
                                if (page == "detail") {
                                    CourseDetailContent(courses[2], true, Modifier.fillMaxSize().padding(24.dp))
                                } else {
                                    val model = remember {
                                        CourseScheduleScreenModel(object : CourseScheduleRepository {
                                            override fun load() = CourseScheduleSnapshot(courses, 5)
                                            override suspend fun refresh() = CourseScheduleRefreshResult.Success(load())
                                        })
                                    }
                                    CourseScheduleWorkspace(
                                        CourseScheduleUiState(courses = courses, academicWeeks = weeks, selectedWeek = 5,
                                            selectedDay = if (page == "day-mixed") 0 else 1, isLoading = false, currentWeek = 5, weekResolved = true,
                                            compactViewMode = if (page.startsWith("day")) CourseCompactViewMode.DAY else CourseCompactViewMode.WEEK),
                                        emptyMap(), false, model, UnavailableHomeworkFileGateway, UnavailableSystemCalendarGateway,
                                        showCalendarExportSheet = false, onDismissCalendarExport = {}, onRefresh = {},
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
                try {
                    repeat(8) { scene.render(1_000_000_000L + it * 16_000_000L).close() }
                    val image = scene.render(1_200_000_000L)
                    try {
                        val data = image.encodeToData(EncodedImageFormat.PNG)!!
                        val file = File("build/reports/staggered-class-time/$page.png")
                        file.parentFile.mkdirs(); file.writeBytes(data.bytes); data.close()
                        assertTrue(file.length() > 1000)
                    } finally { image.close() }
                } finally { scene.close() }
            }
        }
    }
}

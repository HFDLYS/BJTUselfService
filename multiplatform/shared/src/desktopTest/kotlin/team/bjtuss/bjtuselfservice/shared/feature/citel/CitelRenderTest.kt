package team.bjtuss.bjtuselfservice.shared.feature.citel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import team.bjtuss.bjtuselfservice.shared.PlatformAppTheme
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.data.moodle.*
import team.bjtuss.bjtuselfservice.shared.files.UnavailableHomeworkFileGateway

/** Render production UI with synthetic tasks; no browser, credentials, or network. */
class CitelRenderTest {
    @Test fun renderListAndSettingsInCompactWideDarkAndLargeType() {
        SwingUtilities.invokeAndWait {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            CacheDatabaseSql.Schema.create(driver)
            val cache = CacheStore(driver)
            val now = Clock.System.now().epochSeconds
            val tasks = listOf(
                CitelTask(1, 1, "数据结构 · 测试课程", "上机作业 · 顺序表", "$CITEL_BASE/mod/programming/view.php?id=1", true,
                    dueTime = now + 10 * 86400, discountTime = now + 7 * 86400, discount = 0.8, allowLate = true),
                CitelTask(2, 1, "数据结构 · 测试课程", "第 2 章 · 书面作业", "$CITEL_BASE/mod/assign/view.php?id=2", false,
                    dueTime = now + 2 * 86400, status = "Submitted for grading", submitted = true),
            )
            val model = CitelModel("fixture", cache, null, object : CitelDataSource {
                override suspend fun fetch(credentials: Credentials) = tasks
                override suspend fun problem(credentials: Credentials, task: CitelTask, withResults: Boolean) =
                    CitelProblem("顺序表练习", "Grade: 10 / Input:console", "<h4>输入说明</h4><p>读取整数 n 并输出平方。</p><pre>2\n4</pre>",
                        cases = listOf(CitelProblemCase(1, "公开样例", input = "2\n", expected = "4\n")))
                override suspend fun submission(credentials: Credentials, task: CitelTask) = MoodleAssignmentStatus("Submitted for grading", listOf(AssignmentFile("原实验报告.pdf")), true, false, true)
                override suspend fun programmingOptions(credentials: Credentials, task: CitelTask) = CitelProgrammingOptions(mapOf("0" to "gcc", "1" to "g++"), 65536)
            })
            runBlocking { model.configure("fixture", "fixture", false, true) }
            try {
                listOf("compact-light", "compact-dark", "large-font", "wide-light", "settings", "settings-large", "detail-report", "detail-report-large", "detail-programming", "detail-programming-dark", "detail-programming-large", "detail-programming-cached").forEach { name ->
                    if (name.startsWith("detail")) model.showTask(if (name.contains("programming")) tasks[0] else tasks[1]) else model.dismissTask()
                    if (name.endsWith("cached")) runBlocking { model.fetchProblem() }
                    val scene = ImageComposeScene(width = if (name.startsWith("wide")) 1000 else 390, height = 844,
                        density = Density(1f, if (name.contains("large")) 1.5f else 1f), coroutineContext = Dispatchers.Unconfined) {
                        PlatformAppTheme(useDarkTheme = name.contains("dark"), dynamicColorEnabled = false) {
                            Surface(Modifier.fillMaxSize()) {
                                if (name.startsWith("detail")) CitelDetailWorkspace(model, UnavailableHomeworkFileGateway, {})
                                else if (name.startsWith("settings")) CitelSettingsWorkspace(model)
                                else CitelWorkspace(model, false, {})
                            }
                        }
                    }
                    try {
                        repeat(8) { scene.render(1_000_000_000L + it * 100_000_000L).close() }
                        val rendered = scene.render(2_000_000_000L)
                        try {
                            val data = rendered.encodeToData(EncodedImageFormat.PNG)!!
                            val file = File("build/reports/citel-ui/$name.png")
                            file.parentFile.mkdirs()
                            file.writeBytes(data.bytes)
                            data.close()
                            assertTrue(file.length() > 1000)
                        } finally { rendered.close() }
                    } finally { scene.close() }
                }
            } finally { cache.close() }
        }
    }
}

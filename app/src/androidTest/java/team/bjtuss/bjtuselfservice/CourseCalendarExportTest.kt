package team.bjtuss.bjtuselfservice

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.screen.CourseCalendarExport
import team.bjtuss.bjtuselfservice.screen.CourseCalendarExportDialog
import java.io.File
import java.time.LocalDate

class CourseCalendarExportTest {
    @get:Rule val compose = createComposeRule()
    private val courses = listOf(CourseEntity(0, "TEST", "测试课程", "教师", 1, "1,3周", "教室", true))

    @Test
    fun emptyScheduleCannotBeSavedAsAnApparentlySuccessfulExport() {
        compose.setContent {
            MaterialTheme {
                CourseCalendarExportDialog(emptyList(), LocalDate.of(2026, 9, 14), onDismiss = {}, onExport = {})
            }
        }
        compose.onNodeWithText("当前没有可导出的课程，请先同步课表。").assertIsDisplayed()
        compose.onNodeWithText("保存 .ics").assertIsNotEnabled()
    }

    @Test
    fun rejectsNonMondayAndPreviewsExactEventCount() {
        compose.setContent {
            MaterialTheme { CourseCalendarExportDialog(courses, onDismiss = {}, onExport = {}) }
        }
        val field = compose.onNodeWithText("第一教学周的周一")
        field.performTextInput("2026-09-15")
        compose.onNodeWithText("保存 .ics").assertIsNotEnabled()
        field.performTextReplacement("2026-09-14")
        compose.onNodeWithText("共 2 次上课").assertIsDisplayed()
        compose.onNodeWithText("2026-09-14 至 2026-09-28").assertIsDisplayed()
        compose.onNodeWithText("保存 .ics").assertIsEnabled()
    }

    @Test
    fun documentResultWritesUtf8CalendarAndDeletesTemporarySnapshot() = exportWithResult(Activity.RESULT_OK)

    @Test
    fun cancelledDocumentSelectionDoesNotWriteAFile() = exportWithResult(Activity.RESULT_CANCELED)

    private fun exportWithResult(resultCode: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = File.createTempFile("calendar-test-", ".ics", context.cacheDir)
        val previous = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("course-calendar-") }.toSet()
        var returned = false
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                val intent = contract.createIntent(context, input)
                assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
                assertEquals("text/calendar", intent.type)
                returned = true
                dispatchResult(code, resultCode, Intent().setData(Uri.fromFile(destination)))
            }
        }
        try {
            compose.setContent {
                var visible by remember { mutableStateOf(true) }
                val owner = remember { object : ActivityResultRegistryOwner { override val activityResultRegistry = registry } }
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    MaterialTheme {
                        CourseCalendarExport(visible, courses, LocalDate.of(2026, 9, 14)) { visible = false }
                    }
                }
            }
            compose.onNodeWithText("保存 .ics").performClick()
            compose.waitUntil(10_000) {
                returned && context.cacheDir.listFiles().orEmpty()
                    .filter { it.name.startsWith("course-calendar-") }.toSet() == previous
            }
            val text = destination.readText(Charsets.UTF_8)
            if (resultCode == Activity.RESULT_OK) {
                assertTrue(text.contains("SUMMARY:测试课程"))
                assertEquals(2, text.lines().count { it == "BEGIN:VEVENT" })
            } else assertTrue(text.isEmpty())
        } finally { destination.delete() }
    }
}

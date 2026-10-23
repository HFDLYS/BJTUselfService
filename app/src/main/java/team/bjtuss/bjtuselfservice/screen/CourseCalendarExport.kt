package team.bjtuss.bjtuselfservice.screen

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.utils.CourseCalendar
import team.bjtuss.bjtuselfservice.utils.CourseCalendarExporter
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
internal fun CourseCalendarExport(
    visible: Boolean, courses: List<CourseEntity>, suggestedMonday: LocalDate?, onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Store only the private cache file name, so rotation/process recreation does not lose a pending export.
    var pendingFile by rememberSaveable { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        val file = pendingFile?.let { File(context.cacheDir, it) }
        pendingFile = null
        scope.launch(Dispatchers.Main.immediate) {
            try {
                if (uri != null) {
                    withContext(Dispatchers.IO) {
                        check(file?.isFile == true) { "导出内容已失效，请重新导出" }
                        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            file!!.inputStream().use { it.copyTo(output) }
                        } ?: error("无法写入所选位置")
                    }
                    Toast.makeText(context, "课表已导出", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败，请重新选择保存位置", Toast.LENGTH_LONG).show()
            } finally {
                file?.delete()
            }
        }
    }
    if (visible) {
        CourseCalendarExportDialog(courses, suggestedMonday, preparing, onDismiss) { calendar ->
            preparing = true
            scope.launch(Dispatchers.Main.immediate) {
                try {
                    val file = withContext(Dispatchers.IO) {
                        File.createTempFile("course-calendar-", ".ics", context.cacheDir).apply {
                            writeText(calendar.text, Charsets.UTF_8)
                        }
                    }
                    pendingFile = file.name
                    save.launch("课表-${calendar.firstDate}.ics")
                    onDismiss()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    pendingFile?.let { File(context.cacheDir, it).delete() }
                    pendingFile = null
                    Toast.makeText(context, "无法准备课表文件，请重试", Toast.LENGTH_LONG).show()
                } finally {
                    preparing = false
                }
            }
        }
    }
}

@Composable
internal fun CourseCalendarExportDialog(
    courses: List<CourseEntity>, suggestedMonday: LocalDate? = null, preparing: Boolean = false,
    onDismiss: () -> Unit, onExport: (CourseCalendar) -> Unit,
) {
    val context = LocalContext.current
    var mondayText by rememberSaveable { mutableStateOf(suggestedMonday?.toString().orEmpty()) }
    val monday = runCatching { LocalDate.parse(mondayText) }.getOrNull()
    val validMonday = monday?.takeIf { it.dayOfWeek == DayOfWeek.MONDAY && it.year in 1..9998 }
    val calendar = remember(courses, validMonday) {
        validMonday?.let { CourseCalendarExporter.export(courses, it) }
    }
    AlertDialog(
        onDismissRequest = { if (!preparing) onDismiss() },
        title = { Text("导出课表 (.ics)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("将导出这份课表的全部上课日期。请先确认学期开始日期，单双周以课表记录为准。")
                OutlinedTextField(
                    value = mondayText, onValueChange = { mondayText = it },
                    label = { Text("第一教学周的周一") }, placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true, isError = mondayText.isNotEmpty() && validMonday == null,
                    modifier = Modifier.fillMaxWidth(), enabled = !preparing,
                )
                TextButton(enabled = !preparing, onClick = {
                    val date = monday ?: LocalDate.now()
                    DatePickerDialog(context, { _, year, month, day ->
                        mondayText = LocalDate.of(year, month + 1, day).toString()
                    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }) { Text("选择日期") }
                if (validMonday == null) {
                    Text("请选择第一教学周的周一", color = MaterialTheme.colorScheme.error)
                } else if (calendar != null) {
                    Text("共 ${calendar.eventCount} 次上课")
                    if (calendar.eventCount > 0) Text("${calendar.firstDate} 至 ${calendar.lastDate}")
                    if (calendar.skippedCourses.isNotEmpty()) {
                        Text("${calendar.skippedCourses.size} 条课程记录缺少有效周次或时间，未纳入导出。",
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (calendar.eventCount == 0) Text("当前没有可导出的课程，请先同步课表。")
                }
            }
        },
        confirmButton = {
            Button(enabled = !preparing && (calendar?.eventCount ?: 0) > 0, onClick = { calendar?.let(onExport) }) {
                Text(if (preparing) "准备中" else "保存 .ics")
            }
        },
        dismissButton = { TextButton(enabled = !preparing, onClick = onDismiss) { Text("取消") } },
    )
}

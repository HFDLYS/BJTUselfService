package team.bjtuss.bjtuselfservice.shared.feature.widget

import android.content.Context
import java.io.File
import team.bjtuss.bjtuselfservice.shared.logging.AppLog

/** 快照文件名与 iOS App Group 里的同名；Android 放在应用私有目录，小组件与主应用同进程读取。 */
const val ANDROID_COURSE_WIDGET_FILE_NAME = "course-widget.json"

private var widgetContext: Context? = null
private var onWidgetSnapshotChanged: ((Context) -> Unit)? = null
private var lastWrittenJson: String? = null

/**
 * 由 androidApp 在 Activity 创建时调用：给快照写入一个应用级 Context，
 * 并在快照变化后通知桌面小组件重新排版（小组件实现在 androidApp 模块）。
 */
fun installAndroidCourseWidgetStore(context: Context, onChanged: (Context) -> Unit) {
    widgetContext = context.applicationContext
    onWidgetSnapshotChanged = onChanged
}

fun androidCourseWidgetSnapshotFile(context: Context): File = File(context.filesDir, ANDROID_COURSE_WIDGET_FILE_NAME)

/** 小组件侧读取；没有文件或解析失败返回 null。 */
fun readAndroidCourseWidgetSnapshot(context: Context): CourseWidgetSnapshot? {
    val file = androidCourseWidgetSnapshotFile(context)
    if (!file.isFile) return null
    return runCatching { file.readText() }.getOrNull()?.let(::decodeCourseWidgetSnapshot)
}

internal actual fun writeCourseWidgetSnapshot(json: String?) {
    if (json == lastWrittenJson && json != null) return
    val context = widgetContext ?: return
    val file = androidCourseWidgetSnapshotFile(context)
    val ok = runCatching {
        if (json == null) {
            file.delete()
        } else {
            // 先写临时文件再改名，小组件不会读到半个 JSON。
            val temp = File(file.parentFile, "$ANDROID_COURSE_WIDGET_FILE_NAME.tmp")
            temp.writeText(json)
            if (!temp.renameTo(file)) {
                file.delete()
                check(temp.renameTo(file))
            }
        }
    }.isSuccess
    if (!ok) {
        AppLog.d("CourseWidget", "Failed to write widget snapshot")
        return
    }
    lastWrittenJson = json
    runCatching { onWidgetSnapshotChanged?.invoke(context) }
}

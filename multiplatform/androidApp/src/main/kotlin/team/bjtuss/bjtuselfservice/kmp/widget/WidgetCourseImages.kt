package team.bjtuss.bjtuselfservice.kmp.widget

import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import team.bjtuss.bjtuselfservice.kmp.R

/**
 * Course accents are baked into separate drawable resources. Launcher processes therefore do not
 * need to recolor the same white drawable for every row, avoiding vendor color-filter/cache issues.
 */
internal class WidgetCourseImages(context: Context) {
    private data class Images(val color: Int, val bar: Int, val row: Int, val compactRow: Int)

    private val images = listOf(
        Images(R.color.widget_accent_required, R.drawable.widget_bar_required, R.drawable.widget_row_required, R.drawable.widget_compact_row_required),
        Images(R.color.widget_accent_limited, R.drawable.widget_bar_limited, R.drawable.widget_row_limited, R.drawable.widget_compact_row_limited),
        Images(R.color.widget_accent_elective, R.drawable.widget_bar_elective, R.drawable.widget_row_elective, R.drawable.widget_compact_row_elective),
        Images(R.color.widget_accent_pe, R.drawable.widget_bar_pe, R.drawable.widget_row_pe, R.drawable.widget_compact_row_pe),
        Images(R.color.widget_accent_unknown, R.drawable.widget_bar_unknown, R.drawable.widget_row_unknown, R.drawable.widget_compact_row_unknown),
        Images(R.color.widget_accent_lab, R.drawable.widget_bar_lab, R.drawable.widget_row_lab, R.drawable.widget_compact_row_lab),
        Images(R.color.widget_accent_exam, R.drawable.widget_bar_exam, R.drawable.widget_row_exam, R.drawable.widget_compact_row_exam),
    ).associateBy { context.getColor(it.color) }

    fun apply(views: RemoteViews, id: Int, color: Int, alpha: Int = 255, compact: Boolean = false) {
        val resources = images[color]
        val drawable = if (resources != null) {
            when {
                id == R.id.bar -> resources.bar
                compact -> resources.compactRow
                else -> resources.row
            }
        } else {
            // Preserve arbitrary colors from older snapshots; current category accents use resources.
            when {
                id == R.id.bar -> R.drawable.widget_bar
                compact -> R.drawable.widget_compact_row_background
                else -> R.drawable.widget_row_background
            }
        }
        views.setImageViewResource(id, drawable)
        // RemoteViews may be reapplied over views produced by an older app version. A transparent
        // SRC_ATOP filter leaves the resource color intact and neutralizes any previous tint.
        views.setInt(id, "setColorFilter", if (resources != null) Color.TRANSPARENT else color)
        // Always restore full opacity too: a recycled view may previously have been dimmed.
        views.setInt(id, "setImageAlpha", alpha)
    }
}

package team.bjtuss.bjtuselfservice.kmp.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import team.bjtuss.bjtuselfservice.kmp.MainActivity
import team.bjtuss.bjtuselfservice.kmp.R
import team.bjtuss.bjtuselfservice.shared.feature.widget.CourseWidgetState
import team.bjtuss.bjtuselfservice.shared.feature.widget.courseWidgetNextRefresh
import team.bjtuss.bjtuselfservice.shared.feature.widget.courseWidgetState
import team.bjtuss.bjtuselfservice.shared.feature.widget.readAndroidCourseWidgetSnapshot

/** 三种小组件，与 iOS 的 UpNext / Today / Week 一一对应。 */
enum class CourseWidgetKind { UP_NEXT, TODAY, WEEK }

abstract class CourseWidgetProvider : AppWidgetProvider() {
    abstract val kind: CourseWidgetKind

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        CourseWidgetUpdater.updateAll(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        CourseWidgetUpdater.update(context, manager, kind, id)
    }

    override fun onDisabled(context: Context) {
        // 最后一个同类小组件被移除；其它种类还在时闹钟由 updateAll 继续维护。
        CourseWidgetUpdater.updateAll(context)
    }
}

class UpNextCourseWidgetProvider : CourseWidgetProvider() {
    override val kind = CourseWidgetKind.UP_NEXT
}

class TodayCourseWidgetProvider : CourseWidgetProvider() {
    override val kind = CourseWidgetKind.TODAY
}

class WeekCourseWidgetProvider : CourseWidgetProvider() {
    override val kind = CourseWidgetKind.WEEK
}

/** 定时重排、系统时间/时区变化、应用更新后都重新排版一次。 */
class CourseWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CourseWidgetUpdater.updateAll(context)
    }
}

object CourseWidgetUpdater {
    private const val ACTION_REFRESH = "team.bjtuss.bjtuselfservice.kmp.widget.REFRESH"

    private fun providerClass(kind: CourseWidgetKind): Class<out CourseWidgetProvider> = when (kind) {
        CourseWidgetKind.UP_NEXT -> UpNextCourseWidgetProvider::class.java
        CourseWidgetKind.TODAY -> TodayCourseWidgetProvider::class.java
        CourseWidgetKind.WEEK -> WeekCourseWidgetProvider::class.java
    }

    /** 快照变化、闹钟到点时调用：所有种类全部重排，并安排下一次重排。 */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        var any = false
        CourseWidgetKind.entries.forEach { kind ->
            val ids = manager.getAppWidgetIds(ComponentName(context, providerClass(kind)))
            ids.forEach { update(context, manager, kind, it) }
            any = any || ids.isNotEmpty()
        }
        scheduleNext(context, any)
    }

    fun update(context: Context, manager: AppWidgetManager, kind: CourseWidgetKind, id: Int) {
        val views = runCatching { build(context, kind, manager.getAppWidgetOptions(id)) }.getOrNull() ?: return
        runCatching { manager.updateAppWidget(id, views) }
    }

    private fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    private fun build(context: Context, kind: CourseWidgetKind, options: Bundle): RemoteViews {
        val renderer = CourseWidgetRenderer(context)
        val state = courseWidgetState(readAndroidCourseWidgetSnapshot(context), now())
        val openApp = PendingIntent.getActivity(
            context,
            kind.ordinal,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun render(size: CourseWidgetSize, widthDp: Float, heightDp: Float): RemoteViews {
            val views = renderer.message(state) ?: run {
                val model = (state as CourseWidgetState.Ready).model
                when (kind) {
                    CourseWidgetKind.UP_NEXT -> renderer.upNext(model, size, widthDp, heightDp)
                    CourseWidgetKind.TODAY -> renderer.today(model, size, widthDp, heightDp)
                    CourseWidgetKind.WEEK -> renderer.week(model, widthDp, heightDp)
                }
            }
            views.setOnClickPendingIntent(android.R.id.background, openApp)
            return views
        }
        // Android 12+：桌面上报当前可能出现的每个尺寸（竖屏/横屏、荣耀折叠屏展开态等），各排一份，
        // 字号、行数都按真实宽高计算；桌面按当前格子挑最合适的那份。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val reported = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            val fallback = when (kind) {
                CourseWidgetKind.UP_NEXT -> listOf(SizeF(170f, 170f), SizeF(350f, 170f))
                CourseWidgetKind.TODAY -> listOf(SizeF(350f, 170f), SizeF(350f, 360f))
                CourseWidgetKind.WEEK -> listOf(SizeF(350f, 360f))
            }
            val actual = reported?.takeIf { it.isNotEmpty() }?.distinct() ?: fallback
            return RemoteViews(actual.associateWith { render(family(kind, it.width, it.height), it.width, it.height) })
        }
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).toFloat()
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 160).toFloat()
        return render(family(kind, width, height), width, height)
    }

    /** 宽不到 200dp 的临近课程用小号；高不到 230dp 的今日课程用中号。 */
    private fun family(kind: CourseWidgetKind, width: Float, height: Float): CourseWidgetSize = when (kind) {
        CourseWidgetKind.UP_NEXT -> if (width < 200f) CourseWidgetSize.SMALL else CourseWidgetSize.MEDIUM
        CourseWidgetKind.TODAY -> if (height < 230f) CourseWidgetSize.MEDIUM else CourseWidgetSize.LARGE
        CourseWidgetKind.WEEK -> CourseWidgetSize.LARGE
    }

    /**
     * 不申请精确闹钟权限：用非唤醒的 RTC 闹钟，息屏时不叫醒手机，亮屏后到期的那次立即补上。
     * 上课时段逐分钟（“还剩 N 分钟”），其它时候按下一次课程起止，最多 15 分钟一次。
     */
    private fun scheduleNext(context: Context, hasWidgets: Boolean) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, CourseWidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!hasWidgets) {
            alarm.cancel(intent)
            return
        }
        val zone = TimeZone.currentSystemDefault()
        val next = courseWidgetNextRefresh(readAndroidCourseWidgetSnapshot(context), now())
        alarm.set(AlarmManager.RTC, next.toInstant(zone).toEpochMilliseconds(), intent)
    }
}

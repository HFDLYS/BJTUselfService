package team.bjtuss.bjtuselfservice.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.MainActivity

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val content = intent.getStringExtra(ReminderScheduler.EXTRA_CONTENT).orEmpty()
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE) ?: "course"
        val notifId = intent.getIntExtra(
            ReminderScheduler.EXTRA_NOTIF_ID,
            (title + content).hashCode() and 0x7fffffff,
        )
        if (title.isBlank()) return

        val manager = NotificationManagerCompat.from(context)
        val channelId = if (type == "exam") "exam_reminder" else "course_reminder"
        ensureChannel(context, channelId, type)

        val openApp = PendingIntent.getActivity(
            context,
            1001,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            context,
            notifId,
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_DISMISS
                putExtra(ReminderScheduler.EXTRA_NOTIF_ID, notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(0, "查看", openApp)
            .addAction(0, "知道了", dismiss)
            .build()

        runCatching { manager.notify(notifId, notification) }
    }

    private fun ensureChannel(context: Context, channelId: String, type: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(channelId) != null) return
        val name = if (type == "exam") "考试提醒" else "课程提醒"
        manager.createNotificationChannel(
            NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "上课与考试前提醒"
                enableVibration(true)
            }
        )
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_DISMISS) return
        val id = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIF_ID, 0)
        if (id != 0) NotificationManagerCompat.from(context).cancel(id)
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { ReminderScheduler.reschedule(context.applicationContext) }
            pending.finish()
        }
    }
}

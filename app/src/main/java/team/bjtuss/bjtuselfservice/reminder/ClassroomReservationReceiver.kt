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
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.MainActivity
import team.bjtuss.bjtuselfservice.NotificationNavigation
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository

class ClassroomReservationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ClassroomReservationScheduler.ACTION_RESERVATION) return
        val raw = intent.getStringExtra(ClassroomReservationScheduler.EXTRA_RESERVATION) ?: return
        val reservation = runCatching {
            Gson().fromJson(raw, ClassroomReservation::class.java)
        }.getOrNull() ?: return

        ensureChannel(context)
        val route = "ClassroomDetection/" + android.net.Uri.encode(reservation.building) +
            "?room=" + android.net.Uri.encode(reservation.room)
        val open = PendingIntent.getActivity(
            context,
            reservation.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(NotificationNavigation.EXTRA_ROUTE, route)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getBroadcast(
            context,
            reservation.id.hashCode() xor 0x4c52,
            Intent(context, ClassroomReservationActionReceiver::class.java).apply {
                action = ACTION_CANCEL_RESERVATION
                putExtra(EXTRA_RESERVATION_ID, reservation.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val slots = reservation.slots.sorted().joinToString("、") { "第" + (it + 1) + "节" }
        val text = reservation.building + " " + reservation.room + " · " + slots + " · 现在可以开始自习了"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("教室预约 · " + reservation.room)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_mylocation, "去教室", open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消预约", cancel)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(reservation.id.hashCode(), notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "教室预约", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "预约教室开始前的提醒"
                    enableVibration(true)
                }
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "classroom_reservation"
        const val ACTION_CANCEL_RESERVATION = "team.bjtuss.bjtuselfservice.CANCEL_CLASSROOM_RESERVATION"
        const val EXTRA_RESERVATION_ID = "reservation_id"
    }
}

class ClassroomReservationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ClassroomReservationReceiver.ACTION_CANCEL_RESERVATION) return
        val id = intent.getLongExtra(ClassroomReservationReceiver.EXTRA_RESERVATION_ID, -1L)
        if (id < 0) return
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { DataStoreRepository.removeClassroomReservation(id) }
        }
        NotificationManagerCompat.from(appContext).cancel(id.hashCode())
    }
}

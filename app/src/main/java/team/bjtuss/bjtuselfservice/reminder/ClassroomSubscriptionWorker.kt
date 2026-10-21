package team.bjtuss.bjtuselfservice.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import team.bjtuss.bjtuselfservice.MainActivity
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import team.bjtuss.bjtuselfservice.repository.NetworkRepository
import java.time.LocalDate

class ClassroomSubscriptionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val subscriptions = DataStoreRepository.getClassroomSubscriptions().first()
        if (subscriptions.isEmpty()) return Result.success()

        val day = LocalDate.now().dayOfWeek.value
        val slot = currentSlotIndex()
        if (slot < 0) return Result.success()

        val map = runCatching { NetworkRepository.getClassroomMap() }.getOrNull()

        subscriptions.filter { it.day == day && it.slot == slot }.forEach { subscription ->
            val slots = map?.get(subscription.room)?.takeIf { it.isNotEmpty() }
                ?: subscription.slots.orEmpty()
            val free = slots.getOrNull(slot) == 0
            if (free) {
                notify(subscription)
                DataStoreRepository.removeClassroomSubscription(subscription)
            }
        }
        return Result.success()
    }

    private fun notify(subscription: ClassroomSubscription) {
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            applicationContext,
            3001,
            Intent(applicationContext, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("空教室提醒")
            .setContentText(
                subscription.building + " " + subscription.room + " 当前空闲了"
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    subscription.building + " " + subscription.room +
                        " 在你订阅的第 " + (subscription.slot + 1) + " 节已经空闲，可以去自习了。"
                )
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(subscription.room.hashCode() and 0x7fffffff, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "空教室提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
        }
    }

    private fun currentSlotIndex(): Int {
        val now = java.util.Calendar.getInstance()
        val minutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
            now.get(java.util.Calendar.MINUTE)
        val ranges = listOf(
            8 * 60 to 10 * 60,
            10 * 60 + 10 to 12 * 60,
            12 * 60 + 10 to 14 * 60,
            14 * 60 + 10 to 16 * 60,
            16 * 60 + 20 to 18 * 60 + 10,
            19 * 60 to 20 * 60 + 50,
            21 * 60 to 21 * 60 + 50,
        )
        return ranges.indexOfFirst { minutes >= it.first && minutes < it.second }
    }

    companion object {
        const val CHANNEL_ID = "classroom_subscription"
    }
}

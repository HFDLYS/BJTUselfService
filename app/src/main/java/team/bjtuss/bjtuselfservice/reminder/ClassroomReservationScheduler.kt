package team.bjtuss.bjtuselfservice.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object ClassroomReservationScheduler {
    const val ACTION_RESERVATION = "team.bjtuss.bjtuselfservice.CLASSROOM_RESERVATION"
    const val EXTRA_RESERVATION = "reservation_json"

    private val startTimes = listOf(
        LocalTime.of(8, 0),
        LocalTime.of(10, 10),
        LocalTime.of(12, 10),
        LocalTime.of(14, 10),
        LocalTime.of(16, 20),
        LocalTime.of(19, 0),
        LocalTime.of(21, 0),
    )
    private val gson = Gson()

    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val reservations = runCatching {
            DataStoreRepository.getClassroomReservations().first()
        }.getOrDefault(emptyList())
        reservations.forEach { schedule(context, it) }
    }

    suspend fun schedule(context: Context, reservation: ClassroomReservation) =
        withContext(Dispatchers.IO) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return@withContext
            val trigger = triggerAt(reservation) ?: return@withContext
            val pending = pendingIntent(context, reservation, PendingIntent.FLAG_UPDATE_CURRENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        }

    fun cancel(context: Context, reservation: ClassroomReservation) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, reservation, PendingIntent.FLAG_UPDATE_CURRENT)
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun triggerAt(reservation: ClassroomReservation): Long? {
        val date = runCatching { LocalDate.parse(reservation.date) }.getOrNull() ?: return null
        val firstSlot = reservation.slots.minOrNull() ?: return null
        if (firstSlot !in startTimes.indices) return null
        val event = LocalDateTime.of(date, startTimes[firstSlot])
        val trigger = event.minusMinutes(10)
        val now = LocalDateTime.now()
        return when {
            trigger.isAfter(now) -> trigger.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            event.isAfter(now) -> now.plusSeconds(2).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            else -> null
        }
    }

    private fun pendingIntent(
        context: Context,
        reservation: ClassroomReservation,
        flags: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        reservation.id.hashCode(),
        Intent(context, ClassroomReservationReceiver::class.java).apply {
            action = ACTION_RESERVATION
            putExtra(EXTRA_RESERVATION, gson.toJson(reservation))
        },
        flags or PendingIntent.FLAG_IMMUTABLE,
    )
}

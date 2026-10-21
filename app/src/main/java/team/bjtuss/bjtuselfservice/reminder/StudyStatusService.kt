package team.bjtuss.bjtuselfservice.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.MainActivity
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class StudyStatusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (job == null) {
            job = scope.launch {
                while (isActive) {
                    runCatching { updateNotification() }
                    delay(60_000)
                }
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        ensureChannel()
        val notification = buildNotification(
            title = "学习状态",
            content = "正在读取下一节课和考试安排",
            subText = null,
        )
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private suspend fun updateNotification() {
        val currentWeek = DataStoreRepository.getCurrentWeek().first()
        val courses = runCatching {
            AppDatabase.getInstance().courseEntityDao()
                .getCurrentSemesterCourseListBySemester(true)
        }.getOrDefault(emptyList())
        val exams = runCatching {
            AppDatabase.getInstance().examScheduleEntityDao().getAllList()
        }.getOrDefault(emptyList())
        val homework = runCatching {
            AppDatabase.getInstance().homeworkEntityDao().getAllList()
        }.getOrDefault(emptyList())

        val events = mutableListOf<StudyEvent>()
        if (currentWeek > 0) {
            courses.forEach { eventForCourse(it, currentWeek)?.let(events::add) }
        }
        exams.forEach { eventForExam(it)?.let(events::add) }
        homework.forEach { eventForHomework(it)?.let(events::add) }
        events.sortBy { it.at }

        val first = events.firstOrNull()
        if (first == null) {
            notify(
                buildNotification(
                    title = "学习状态",
                    content = "暂时没有即将到来的课程、考试或作业截止",
                    subText = null,
                )
            )
            return
        }
        val second = events.drop(1).firstOrNull()
        val countdown = formatCountdown(first.at)
        notify(
            buildNotification(
                title = first.title,
                content = countdown + " · " + first.detail,
                subText = second?.let { "接下来：" + it.title + " · " + formatCountdown(it.at) },
            )
        )
    }

    private fun eventForCourse(course: team.bjtuss.bjtuselfservice.entity.CourseEntity, currentWeek: Int): StudyEvent? {
        if (!parseWeeks(course.courseTime).contains(currentWeek)) return null
        val day = course.courseLocationIndex % 8
        val slot = course.courseLocationIndex / 8
        if (day !in 1..7 || slot !in START_TIMES.indices) return null
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val maxOffset = 7 - today.dayOfWeek.value
        var best: StudyEvent? = null
        for (offset in 0..maxOffset) {
            val date = today.plusDays(offset.toLong())
            if (date.dayOfWeek.value != day) continue
            val at = LocalDateTime.of(date, START_TIMES[slot])
            if (!at.isAfter(now)) continue
            val event = StudyEvent(
                title = "下一节课 · " + course.courseName,
                detail = at.toLocalTime().format(TIME_FORMAT) + " · " +
                    course.coursePlace + " · " + course.courseTeacher,
                at = at,
            )
            if (best == null || event.at.isBefore(best!!.at)) best = event
        }
        return best
    }

    private fun eventForExam(exam: team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity): StudyEvent? {
        val dateMatch = DATE.find(exam.examTimeAndPlace) ?: return null
        val date = runCatching {
            LocalDate.of(
                dateMatch.groupValues[1].toInt(),
                dateMatch.groupValues[2].toInt(),
                dateMatch.groupValues[3].toInt(),
            )
        }.getOrNull() ?: return null
        val time = TIME.find(exam.examTimeAndPlace)?.let {
            runCatching { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull()
        } ?: LocalTime.of(8, 0)
        val at = LocalDateTime.of(date, time)
        if (!at.isAfter(LocalDateTime.now())) return null
        return StudyEvent("下一场考试 · " + exam.courseName, exam.examTimeAndPlace, at)
    }

    private fun eventForHomework(homework: team.bjtuss.bjtuselfservice.entity.HomeworkEntity): StudyEvent? {
        val at = parseDateTime(homework.endTime) ?: return null
        if (!at.isAfter(LocalDateTime.now())) return null
        return StudyEvent(
            title = "作业截止 · " + homework.courseName,
            detail = homework.title + " · " + at.format(DATETIME_FORMAT),
            at = at,
        )
    }

    private fun parseDateTime(value: String): LocalDateTime? {
        if (value.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
        )
        patterns.forEach { pattern ->
            runCatching {
                LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern))
            }.getOrNull()?.let { return it }
            runCatching {
                LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern)).atStartOfDay()
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun formatCountdown(at: LocalDateTime): String {
        val duration = Duration.between(LocalDateTime.now(), at)
        val minutes = duration.toMinutes().coerceAtLeast(0)
        return when {
            minutes >= 1440 -> (minutes / 1440).toString() + "天" + ((minutes % 1440) / 60) + "小时"
            minutes >= 60 -> (minutes / 60).toString() + "小时" + (minutes % 60) + "分钟"
            else -> minutes.toString() + "分钟"
        }
    }

    private fun buildNotification(title: String, content: String, subText: String?) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    2001,
                    Intent(this, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    ),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

    private fun notify(notification: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "学习状态", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "下一节课、考试和作业截止倒计时"
                }
            )
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private data class StudyEvent(val title: String, val detail: String, val at: LocalDateTime)

    companion object {
        private const val CHANNEL_ID = "study_live_status"
        private const val NOTIFICATION_ID = 0x53545544
        private val START_TIMES = listOf(
            LocalTime.of(8, 0), LocalTime.of(10, 10), LocalTime.of(12, 10),
            LocalTime.of(14, 10), LocalTime.of(16, 20), LocalTime.of(19, 0),
            LocalTime.of(21, 0),
        )
        private val DATE = Regex("""(\d{4})/(\d{1,2})/(\d{1,2})""")
        private val TIME = Regex("""(\d{1,2}):(\d{2})""")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
        private val DATETIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        private fun parseWeeks(courseTime: String): List<Int> = runCatching {
            val text = courseTime.replace("第", "").replace("周", "")
            text.split(",").flatMap { part ->
                if (part.contains("-")) {
                    val range = part.split("-")
                    (range[0].toInt()..range[1].toInt()).toList()
                } else listOf(part.toInt())
            }
        }.getOrDefault(emptyList())
    }
}

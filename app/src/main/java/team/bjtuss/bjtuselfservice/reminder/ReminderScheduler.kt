package team.bjtuss.bjtuselfservice.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ReminderScheduler {
    const val ACTION_COURSE = "team.bjtuss.bjtuselfservice.COURSE_REMINDER"
    const val ACTION_EXAM = "team.bjtuss.bjtuselfservice.EXAM_REMINDER"
    const val ACTION_DISMISS = "team.bjtuss.bjtuselfservice.DISMISS_REMINDER"

    const val EXTRA_TITLE = "reminder_title"
    const val EXTRA_CONTENT = "reminder_content"
    const val EXTRA_TYPE = "reminder_type"
    const val EXTRA_NOTIF_ID = "reminder_notif_id"

    private val COURSE_START_TIMES = listOf(
        LocalTime.of(8, 0),
        LocalTime.of(10, 10),
        LocalTime.of(12, 10),
        LocalTime.of(14, 10),
        LocalTime.of(16, 20),
        LocalTime.of(19, 0),
        LocalTime.of(21, 0),
    )
    private val DATE = Regex("""(\d{4})/(\d{1,2})/(\d{1,2})""")
    private val TIME = Regex("""(\d{1,2}):(\d{2})""")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun reschedule(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return@withContext
        val courseEnabled = DataStoreRepository.getCourseReminderEnabled().first()
        val courseLead = DataStoreRepository.getCourseReminderLeadMinutes().first()
        val examEnabled = DataStoreRepository.getExamReminderEnabled().first()
        val examLead = DataStoreRepository.getExamReminderLeadMinutes().first()
        val currentWeek = DataStoreRepository.getCurrentWeek().first()

        val courses = runCatching {
            AppDatabase.getInstance().courseEntityDao()
                .getCurrentSemesterCourseListBySemester(true)
        }.getOrDefault(emptyList())
        val exams = runCatching {
            AppDatabase.getInstance().examScheduleEntityDao().getAllList()
        }.getOrDefault(emptyList())

        if (courseEnabled && currentWeek > 0) {
            courses.forEach { course ->
                scheduleCourse(alarmManager, appContext, course, currentWeek, courseLead)
            }
        } else {
            courses.forEach { course ->
                (0..6).forEach { offset ->
                    cancel(alarmManager, appContext, courseRequestCode(course, offset), ACTION_COURSE)
                }
            }
        }

        if (examEnabled) {
            exams.forEach { exam ->
                scheduleExam(alarmManager, appContext, exam, examLead)
            }
        } else {
            exams.forEach { exam ->
                cancel(alarmManager, appContext, examRequestCode(exam), ACTION_EXAM)
            }
        }
    }

    private fun scheduleCourse(
        alarmManager: AlarmManager,
        context: Context,
        course: CourseEntity,
        currentWeek: Int,
        leadMinutes: Int,
    ) {
        if (!parseWeeks(course.courseTime).contains(currentWeek)) return
        val index = course.courseLocationIndex
        val day = index % 8
        val slot = index / 8
        if (day !in 1..7 || slot !in COURSE_START_TIMES.indices) return
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val maxOffset = 7 - today.dayOfWeek.value
        for (offset in 0..maxOffset) {
            val date = today.plusDays(offset.toLong())
            if (date.dayOfWeek.value != day) continue
            val event = LocalDateTime.of(date, COURSE_START_TIMES[slot])
            val trigger = event.minusMinutes(leadMinutes.toLong())
            if (!trigger.isAfter(now)) continue
            val time = event.toLocalTime().format(TIME_FORMAT)
            val title = "即将上课 · " + course.courseName
            val content = time + " · " + course.coursePlace + " · " + course.courseTeacher
            schedule(
                alarmManager = alarmManager,
                context = context,
                triggerAt = trigger,
                requestCode = courseRequestCode(course, offset),
                type = "course",
                title = title,
                content = content,
            )
        }
    }

    private fun scheduleExam(
        alarmManager: AlarmManager,
        context: Context,
        exam: team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity,
        leadMinutes: Int,
    ) {
        val dateMatch = DATE.find(exam.examTimeAndPlace) ?: return
        val date = runCatching {
            LocalDate.of(
                dateMatch.groupValues[1].toInt(),
                dateMatch.groupValues[2].toInt(),
                dateMatch.groupValues[3].toInt(),
            )
        }.getOrNull() ?: return
        val timeMatch = TIME.find(exam.examTimeAndPlace)
        val time = timeMatch?.let {
            runCatching { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
                .getOrNull()
        } ?: LocalTime.of(8, 0)
        val event = LocalDateTime.of(date, time)
        val trigger = event.minusMinutes(leadMinutes.toLong())
        if (!trigger.isAfter(LocalDateTime.now())) return
        val title = "考试提醒 · " + exam.courseName
        val content = exam.examTimeAndPlace
        schedule(
            alarmManager = alarmManager,
            context = context,
            triggerAt = trigger,
            requestCode = examRequestCode(exam),
            type = "exam",
            title = title,
            content = content,
        )
    }

    private fun schedule(
        alarmManager: AlarmManager,
        context: Context,
        triggerAt: LocalDateTime,
        requestCode: Int,
        type: String,
        title: String,
        content: String,
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = if (type == "course") ACTION_COURSE else ACTION_EXAM
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_CONTENT, content)
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_NOTIF_ID, requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerMillis = triggerAt.atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        if (triggerMillis <= System.currentTimeMillis()) return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent,
            )
        }
    }

    private fun cancel(
        alarmManager: AlarmManager,
        context: Context,
        requestCode: Int,
        action: String,
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) alarmManager.cancel(pendingIntent)
    }

    private fun courseRequestCode(course: CourseEntity, offset: Int): Int =
        ("course:" + course.courseId + ":" + course.courseLocationIndex + ":" + offset)
            .hashCode() and 0x7fffffff

    private fun examRequestCode(exam: team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity): Int =
        ("exam:" + exam.courseName + ":" + exam.examTimeAndPlace)
            .hashCode() and 0x7fffffff

    private fun parseWeeks(courseTime: String): List<Int> {
        return runCatching {
            val text = courseTime.replace("第", "").replace("周", "")
            text.split(",").flatMap { part ->
                if (part.contains("-")) {
                    val range = part.split("-")
                    (range[0].toInt()..range[1].toInt()).toList()
                } else {
                    listOf(part.toInt())
                }
            }
        }.getOrDefault(emptyList())
    }
}

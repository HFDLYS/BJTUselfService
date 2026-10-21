package team.bjtuss.bjtuselfservice.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import team.bjtuss.bjtuselfservice.MainApplication.Companion.appContext
import team.bjtuss.bjtuselfservice.entity.GradeSelectionRecord
import team.bjtuss.bjtuselfservice.reminder.ClassroomReservation
import team.bjtuss.bjtuselfservice.reminder.ClassroomSubscription
import team.bjtuss.bjtuselfservice.statemanager.Credentials

object DataStoreRepository {
    private val Context.dataStore by preferencesDataStore("settings")
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val PASSWORD_KEY = stringPreferencesKey("password")

    // 新增自动同步设置Key
    private val SYNC_GRADES_KEY = booleanPreferencesKey("auto_sync_grades")
    private val SYNC_HOMEWORK_KEY = booleanPreferencesKey("auto_sync_homework")
    private val SYNC_SCHEDULE_KEY = booleanPreferencesKey("auto_sync_schedule")
    private val SYNC_EXAMS_KEY = booleanPreferencesKey("auto_sync_exams")
    private val CURRENT_WEEK_KEY = stringPreferencesKey("current_week")
    private val CHECK_UPDATE_KEY = booleanPreferencesKey("check_update")
    private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")

    private val THEME_KEY = stringPreferencesKey("theme")

    private val COURSEWARE_JSON = stringPreferencesKey("courseware_json")
    private val GRADE_SELECTIONS_BY_STUDENT_KEY =
        stringPreferencesKey("grade_selections_by_student")
    private val gson = Gson()
    private val gradeSelectionsMapType =
        object : TypeToken<Map<String, List<GradeSelectionRecord>>>() {}.type

    private val BACKGROUND_IMAGE_URI_KEY = stringPreferencesKey("background_image_uri")

    private val COURSE_REMINDER_ENABLED_KEY = booleanPreferencesKey("course_reminder_enabled")
    private val COURSE_REMINDER_LEAD_KEY = stringPreferencesKey("course_reminder_lead_minutes")
    private val EXAM_REMINDER_ENABLED_KEY = booleanPreferencesKey("exam_reminder_enabled")
    private val EXAM_REMINDER_LEAD_KEY = stringPreferencesKey("exam_reminder_lead_minutes")
    private val CLASSROOM_SUBSCRIPTIONS_KEY = stringPreferencesKey("classroom_subscriptions")
    private val CLASSROOM_RESERVATIONS_KEY = stringPreferencesKey("classroom_reservations")


    suspend fun setCredentials(credentials: Credentials) {
        appContext.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = credentials.username
            preferences[PASSWORD_KEY] = credentials.password
        }
    }

    // 获取凭据（Flow 方式）
    fun getStoredCredentials(): Flow<Credentials> {
        return appContext.dataStore.data.map { preferences ->
            val username = preferences[USERNAME_KEY]
            val password = preferences[PASSWORD_KEY]
            Credentials(username ?: "", password ?: "")
        }
    }

    // 获取凭据（阻塞式同步获取）
    suspend fun getStoredCredentialsBlocking(): Credentials {
        return appContext.dataStore.data.first().let { preferences ->
            val username = preferences[USERNAME_KEY] ?: ""
            val password = preferences[PASSWORD_KEY] ?: ""
            Credentials(username, password)
        }
    }


    suspend fun clearCredentials() {
        appContext.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = ""
            preferences[PASSWORD_KEY] = ""
        }
    }


    private suspend fun setAutoSyncOption(key: Preferences.Key<Boolean>, enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[key] = enabled
        }
    }

    suspend fun setAllAutoSyncOptions(settings: Map<Preferences.Key<Boolean>, Boolean>) {
        appContext.dataStore.edit { preferences ->
            settings.forEach { (key, value) ->
                preferences[key] = value
            }
        }
    }

    suspend fun setGradeAutoSyncOption(enabled: Boolean) {
        setAutoSyncOption(SYNC_GRADES_KEY, enabled)
    }

    suspend fun setHomeworkAutoSyncOption(enabled: Boolean) {

        setAutoSyncOption(SYNC_HOMEWORK_KEY, enabled)
    }

    suspend fun setScheduleAutoSyncOption(enabled: Boolean) {
        setAutoSyncOption(SYNC_SCHEDULE_KEY, enabled)
    }

    suspend fun setExamsAutoSyncOption(enabled: Boolean) {
        setAutoSyncOption(SYNC_EXAMS_KEY, enabled)
    }

    fun getGradeAutoSyncOption(): Flow<Boolean> {
        return appContext.dataStore.data.map { preferences ->
            preferences[SYNC_GRADES_KEY] ?: false
        }
    }

    fun getHomeworkAutoSyncOption(): Flow<Boolean> {
        return appContext.dataStore.data.map { preferences ->
            preferences[SYNC_HOMEWORK_KEY] ?: false
        }
    }

    fun getScheduleAutoSyncOption(): Flow<Boolean> {
        return appContext.dataStore.data.map { preferences ->
            preferences[SYNC_SCHEDULE_KEY] ?: false
        }
    }

    fun getExamAutoSyncOption(): Flow<Boolean> {
        return appContext.dataStore.data.map { preferences ->
            preferences[SYNC_EXAMS_KEY] ?: false
        }
    }

    fun getCoursewareJson(): Flow<String> {
        return appContext.dataStore.data.map { preferences ->
            preferences[COURSEWARE_JSON] ?: ""
        }
    }

    suspend fun setCoursewareJson(json: String) {
        appContext.dataStore.edit { preferences ->
            preferences[COURSEWARE_JSON] = json
        }
    }

    suspend fun getGradeSelections(studentId: String): List<GradeSelectionRecord> {
        if (studentId.isBlank()) {
            return emptyList()
        }
        val preferences = appContext.dataStore.data.first()
        return readGradeSelectionsMap(preferences)[studentId].orEmpty()
    }

    suspend fun setGradeSelections(
        studentId: String,
        records: List<GradeSelectionRecord>,
    ) {
        if (studentId.isBlank()) {
            return
        }
        appContext.dataStore.edit { preferences ->
            val selectionsByStudent = readGradeSelectionsMap(preferences).toMutableMap()
            if (records.isEmpty()) {
                selectionsByStudent.remove(studentId)
            } else {
                selectionsByStudent[studentId] = records
            }
            preferences[GRADE_SELECTIONS_BY_STUDENT_KEY] =
                gson.toJson(selectionsByStudent, gradeSelectionsMapType)
        }
    }

    suspend fun clearAllGradeSelections() {
        appContext.dataStore.edit { preferences ->
            preferences.remove(GRADE_SELECTIONS_BY_STUDENT_KEY)
            // Remove the account-scoped eligibility cache written by earlier test builds.
            preferences.remove(
                stringPreferencesKey("dual_grade_eligibility_by_student")
            )
        }
    }

    private fun readGradeSelectionsMap(
        preferences: Preferences,
    ): Map<String, List<GradeSelectionRecord>> {
        val json = preferences[GRADE_SELECTIONS_BY_STUDENT_KEY] ?: return emptyMap()
        return try {
            gson.fromJson<Map<String, List<GradeSelectionRecord>>>(
                json,
                gradeSelectionsMapType,
            ).orEmpty()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getCurrentWeek(): Flow<Int> {
        return appContext.dataStore.data.map { preferences ->
            preferences[CURRENT_WEEK_KEY] ?: ""
        }.map { weekString ->
            weekString.toIntOrNull() ?: 0
        }
    }

    suspend fun setCurrentWeek(week: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[CURRENT_WEEK_KEY] = week.toString()
        }
    }


    fun getCheckUpdateOption(): Flow<Boolean> {
        return appContext.dataStore.data.map { preferences ->
            preferences[CHECK_UPDATE_KEY] ?: true
        }
    }

    suspend fun setCheckUpdateOption(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[CHECK_UPDATE_KEY] = enabled
        }
    }


    fun getTheme(): Flow<String> {
        return appContext.dataStore.data.map { preferences ->
            preferences[THEME_KEY] ?: "System"
        }
    }

    suspend fun setThemeOption(theme: String) {
        appContext.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }

    fun getDynamicColorOption(): Flow<Boolean> {
        return appContext.dataStore.data.map { preferences ->
            preferences[DYNAMIC_COLOR_KEY] ?: true
        }
    }

    suspend fun setDynamicColorOption(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }

    }

    fun getBackgroundImageUri(): Flow<String> {
        return appContext.dataStore.data.map { preferences ->
            preferences[BACKGROUND_IMAGE_URI_KEY] ?: ""
        }
    }

    suspend fun setBackgroundImageUri(uri: String) {
        appContext.dataStore.edit { preferences ->
            preferences[BACKGROUND_IMAGE_URI_KEY] = uri
        }
    }

    fun getCourseReminderEnabled(): Flow<Boolean> =
        appContext.dataStore.data.map { it[COURSE_REMINDER_ENABLED_KEY] ?: true }

    suspend fun setCourseReminderEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[COURSE_REMINDER_ENABLED_KEY] = enabled }
    }

    fun getCourseReminderLeadMinutes(): Flow<Int> =
        appContext.dataStore.data.map {
            (it[COURSE_REMINDER_LEAD_KEY] ?: "15").toIntOrNull() ?: 15
        }

    suspend fun setCourseReminderLeadMinutes(minutes: Int) {
        appContext.dataStore.edit { it[COURSE_REMINDER_LEAD_KEY] = minutes.toString() }
    }

    fun getExamReminderEnabled(): Flow<Boolean> =
        appContext.dataStore.data.map { it[EXAM_REMINDER_ENABLED_KEY] ?: true }

    suspend fun setExamReminderEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[EXAM_REMINDER_ENABLED_KEY] = enabled }
    }

    fun getExamReminderLeadMinutes(): Flow<Int> =
        appContext.dataStore.data.map {
            (it[EXAM_REMINDER_LEAD_KEY] ?: "1440").toIntOrNull() ?: 1440
        }

    suspend fun setExamReminderLeadMinutes(minutes: Int) {
        appContext.dataStore.edit { it[EXAM_REMINDER_LEAD_KEY] = minutes.toString() }
    }

    fun getClassroomSubscriptions(): Flow<List<ClassroomSubscription>> =
        appContext.dataStore.data.map { preferences ->
            val raw = preferences[CLASSROOM_SUBSCRIPTIONS_KEY] ?: return@map emptyList()
            runCatching {
                gson.fromJson<List<ClassroomSubscription>>(
                    raw,
                    object : TypeToken<List<ClassroomSubscription>>() {}.type,
                )
            }.getOrDefault(emptyList())
        }

    suspend fun addClassroomSubscription(subscription: ClassroomSubscription) {
        val current = getClassroomSubscriptions().first().toMutableList()
        if (current.none {
                it.room == subscription.room && it.day == subscription.day && it.slot == subscription.slot
            }
        ) {
            current.add(subscription)
        }
        appContext.dataStore.edit {
            it[CLASSROOM_SUBSCRIPTIONS_KEY] = gson.toJson(current)
        }
    }

    suspend fun removeClassroomSubscription(subscription: ClassroomSubscription) {
        val current = getClassroomSubscriptions().first()
            .filterNot {
                it.room == subscription.room && it.day == subscription.day && it.slot == subscription.slot
            }
        appContext.dataStore.edit {
            it[CLASSROOM_SUBSCRIPTIONS_KEY] = gson.toJson(current)
        }
    }

    fun getClassroomReservations(): Flow<List<ClassroomReservation>> =
        appContext.dataStore.data.map { preferences ->
            val raw = preferences[CLASSROOM_RESERVATIONS_KEY] ?: return@map emptyList()
            runCatching {
                gson.fromJson<List<ClassroomReservation>>(
                    raw,
                    object : TypeToken<List<ClassroomReservation>>() {}.type,
                )
            }.getOrDefault(emptyList())
        }

    suspend fun addClassroomReservation(reservation: ClassroomReservation) {
        val current = getClassroomReservations().first()
            .filterNot { it.id == reservation.id }
            .plus(reservation)
        appContext.dataStore.edit {
            it[CLASSROOM_RESERVATIONS_KEY] = gson.toJson(current)
        }
    }

    suspend fun removeClassroomReservation(id: Long) {
        val current = getClassroomReservations().first().filterNot { it.id == id }
        appContext.dataStore.edit {
            it[CLASSROOM_RESERVATIONS_KEY] = gson.toJson(current)
        }
    }

}

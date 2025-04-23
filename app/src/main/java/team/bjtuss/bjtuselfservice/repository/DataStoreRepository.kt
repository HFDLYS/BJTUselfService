package team.bjtuss.bjtuselfservice.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import team.bjtuss.bjtuselfservice.MainApplication.Companion.appContext
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

    private val COURSEWARE_JSON = stringPreferencesKey("courseware_json")

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

}
package team.bjtuss.bjtuselfservice.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import team.bjtuss.bjtuselfservice.MainApplication.Companion.appContext

object SettingsRepository {
    private val Context.dataStore by preferencesDataStore("settings")
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val PASSWORD_KEY = stringPreferencesKey("password")

    suspend fun setCredentials(username: String, password: String) {
        appContext.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = username
            preferences[PASSWORD_KEY] = password
        }
    }

    // 获取凭据（Flow 方式）
    fun getStoredCredentials(): Flow<Pair<String?, String?>> {
        return appContext.dataStore.data.map { preferences ->
            val username = preferences[USERNAME_KEY]
            val password = preferences[PASSWORD_KEY]
            username to password
        }
    }

    // 获取凭据（阻塞式同步获取）
    suspend fun getStoredCredentialsBlocking(): Pair<String, String> {
        return appContext.dataStore.data.first().let { preferences ->
            val username = preferences[USERNAME_KEY] ?: ""
            val password = preferences[PASSWORD_KEY] ?: ""
            username to password
        }
    }


    fun isCredentialsEmpty(): Flow<Boolean> {
        return appContext.dataStore.data.map { preferences ->
            val username = preferences[USERNAME_KEY]
            val password = preferences[PASSWORD_KEY]
            username == "" && password == ""
        }
    }

    suspend fun clearCredentials() {
        appContext.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = ""
            preferences[PASSWORD_KEY] = ""
        }
    }



}
package indi.optsimauth.bjtuselfservicecompose.screen

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import indi.optsimauth.bjtuselfservicecompose.MainApplication.Companion.appContext
import indi.optsimauth.bjtuselfservicecompose.StudentAccountManager
import indi.optsimauth.bjtuselfservicecompose.web.MisDataManager.login
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Composable
fun SettingsItemCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    hPadding: Dp = 12.dp,
    vPadding: Dp = 14.dp,
    onClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        elevation = CardDefaults.elevatedCardElevation(
            6.dp
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = hPadding, vertical = vPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBasicLinkItem(
    title: Int,
    subtitle: String = "",
    icon: Int,
    link: String = "",
    onClick: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    SettingsItemCard(
        cornerRadius = 16.dp,
        onClick = {
            if (link.isNotBlank()) {
                uriHandler.openUri(link)
            } else onClick()
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = stringResource(id = title),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(id = title),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

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

class SettingViewModel : ViewModel() {
    private val _loginStatus = MutableStateFlow(false)
    val loginStatus: StateFlow<Boolean> = _loginStatus.asStateFlow()

    private val _credentials = MutableStateFlow<Pair<String?, String?>>(null to null)
    val credentials: StateFlow<Pair<String?, String?>> = _credentials.asStateFlow()

    init {
        viewModelScope.launch {
            SettingsRepository.getStoredCredentials().collect { storedCredentials ->
                _credentials.value = storedCredentials
                _loginStatus.value = !storedCredentials.first.isNullOrBlank()
                        && !storedCredentials.second.isNullOrBlank()
            }
        }
    }

    fun getCredentials(): Pair<String?, String?> {
        return runBlocking {
            withContext(Dispatchers.IO) {
                SettingsRepository.getStoredCredentials().first()
            }
        }
    }

    fun setCredentials(username: String, password: String) = viewModelScope.launch {
        SettingsRepository.setCredentials(username, password)
    }

    fun clearCredentials() = viewModelScope.launch {
        SettingsRepository.clearCredentials()
    }

}


@Composable
fun SettingScreen(loginViewModel: LoginViewModel) {

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        item {
            SettingsItemCard(
                onClick = {},
                content = {
                    Text(
                        text = "Account",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            )
        }
        item {
            Button(
                onClick = {
                    loginViewModel.logout()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Logout", fontSize = 18.sp)
            }
        }
    }
}




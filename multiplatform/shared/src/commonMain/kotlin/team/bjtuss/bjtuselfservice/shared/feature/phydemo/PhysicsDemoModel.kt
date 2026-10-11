package team.bjtuss.bjtuselfservice.shared.feature.phydemo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.security.CredentialVault

data class PhysicsDemoState(val username: String = "", val configured: Boolean = false, val enabled: Boolean = false,
    val bookings: List<PhysicsDemoBooking> = emptyList(), val refreshing: Boolean = false, val fromCache: Boolean = false,
    val failed: Boolean = false, val message: String? = null, val synced: Boolean = false, val lastSync: String? = null)

class PhysicsDemoModel(private val scope: String, private val cache: CacheStore,
    private val vault: CredentialVault?, private val remote: PhysicsDemoRemote) {
    private val mutableState = MutableStateFlow(PhysicsDemoState(username = scope))
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var initialized = false
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun initialize() = mutex.withLock {
        if (initialized) return@withLock
        val configured = cache.metadata(scope, "phydemo.configured") == "true"
        val creds = try { if (configured) vault?.load() else { vault?.clear(); null } }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        val cachedResults = cache.metadata(scope,"phydemo.results")
        val bookings = runCatching { json.decodeFromString<List<PhysicsDemoBooking>>(cachedResults.orEmpty()) }.getOrDefault(emptyList())
        mutableState.value = PhysicsDemoState(username = creds?.username ?: cache.metadata(scope,"phydemo.username") ?: scope,
            configured = creds?.isValid == true, enabled = creds?.isValid == true && cache.metadata(scope,"phydemo.enabled") == "true",
            bookings = bookings, fromCache = creds?.isValid == true && cachedResults != null,
            lastSync = cache.metadata(scope,"phydemo.lastSync"))
        initialized = true
    }

    suspend fun configure(username: String, password: String): Boolean {
        initialize()
        return mutex.withLock {
            mutableState.value = state.value.copy(refreshing = true, failed = false, message = null)
            try {
                val storage = vault ?: throw PhysicsDemoFailure("当前平台安全存储不可用。")
                val creds = if (password.isNotBlank()) Credentials(username.trim(),password)
                    else storage.load()?.takeIf { it.username == username.trim() }
                if (creds?.isValid != true) throw PhysicsDemoFailure("请填写演示实验系统的独立账号和密码。")
                // Validate first; a mistyped account never replaces existing credentials/results.
                val bookings = withTimeout(40_000) { remote.fetch(creds) }
                storage.save(creds)
                cache.putMetadata(scope,"phydemo.username",creds.username)
                cache.putMetadata(scope,"phydemo.configured","true")
                cache.putMetadata(scope,"phydemo.enabled","true")
                cache.putMetadata(scope,"phydemo.results",json.encodeToString(bookings))
                val updated = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai")).toString()
                cache.putMetadata(scope,"phydemo.lastSync",updated)
                mutableState.value = PhysicsDemoState(creds.username,true,true,bookings,message = "已查询 ${bookings.size} 个预约。", synced = true, lastSync = updated)
                true
            } catch (e: TimeoutCancellationException) {
                mutableState.value = state.value.copy(failed = true, message = "连接超时，请连接校园网后重试。")
                false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutableState.value = state.value.copy(failed = true, message = (e as? PhysicsDemoFailure)?.message ?: "演示实验查询或安全保存失败。")
                false
            } finally { mutableState.value = state.value.copy(refreshing = false) }
        }
    }

    suspend fun refresh() {
        initialize()
        mutex.withLock {
            if (!state.value.enabled || !state.value.configured) return@withLock
            mutableState.value = state.value.copy(refreshing = true,failed = false,message = null)
            try {
                val creds = vault?.load() ?: throw PhysicsDemoFailure("请配置演示实验账号。")
                val bookings = withTimeout(40_000) { remote.fetch(creds) }
                cache.putMetadata(scope,"phydemo.results",json.encodeToString(bookings))
                val updated = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai")).toString()
                cache.putMetadata(scope,"phydemo.lastSync",updated)
                mutableState.value = state.value.copy(bookings = bookings,fromCache = false,synced = true,lastSync = updated,message = "已查询 ${bookings.size} 个预约。")
            } catch (e: TimeoutCancellationException) {
                mutableState.value = state.value.copy(fromCache = true, failed = true,message = "连接超时，显示上次查询结果。")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutableState.value = state.value.copy(fromCache = true,failed = true,message = (e as? PhysicsDemoFailure)?.message ?: "请连接校园网，显示上次查询结果。")
            } finally { mutableState.value = state.value.copy(refreshing = false) }
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        initialize()
        mutex.withLock {
            if (enabled && !state.value.configured) return@withLock
            cache.putMetadata(scope,"phydemo.enabled",enabled.toString())
            mutableState.value = state.value.copy(enabled = enabled)
        }
        if (enabled) refresh()
    }

    suspend fun clearConfiguration() {
        initialize()
        mutex.withLock {
            try {
                vault?.clear()
                cache.putMetadata(scope,"phydemo.configured","false")
                cache.putMetadata(scope,"phydemo.enabled","false")
                cache.putMetadata(scope,"phydemo.results","[]")
                cache.putMetadata(scope,"phydemo.lastSync","")
                mutableState.value = PhysicsDemoState(username = state.value.username,message = "已清除密码和演示实验缓存。")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.value = state.value.copy(failed = true,message = "清除失败，请重试。") }
        }
    }
}

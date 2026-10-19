package team.bjtuss.bjtuselfservice.shared.security

import team.bjtuss.bjtuselfservice.shared.auth.Credentials

data class AccountSecurityStore(
    val credentialVault: CredentialVault?,
    val preferences: AccountPreferences,
    val physicsLabVault: ((String) -> CredentialVault)? = null,
    val citelVault: ((String) -> CredentialVault)? = null,
    val physicsDemoVault: ((String) -> CredentialVault)? = null,
    /** 物理在线（Moodle）独立账号：学校统一认证不再覆盖该站点。 */
    val phyVlabVault: ((String) -> CredentialVault)? = null,
)

interface AccountPreferences {
    suspend fun shouldRememberCredentials(): Boolean
    suspend fun setShouldRememberCredentials(enabled: Boolean)
    suspend fun clearRememberCredentialsSetting()
}

sealed interface CredentialRestoreResult {
    data object Empty : CredentialRestoreResult
    data object Unavailable : CredentialRestoreResult
    data class Restored(val credentials: Credentials) : CredentialRestoreResult
    data object Failed : CredentialRestoreResult
}

class AccountSecurityCoordinator(
    private val store: AccountSecurityStore,
) {
    fun physicsLabVault(accountScope: String): CredentialVault? = store.physicsLabVault?.invoke(accountScope)
    fun physicsDemoVault(accountScope: String): CredentialVault? = store.physicsDemoVault?.invoke(accountScope)
    fun citelVault(accountScope: String): CredentialVault? = store.citelVault?.invoke(accountScope)
    fun phyVlabVault(accountScope: String): CredentialVault? = store.phyVlabVault?.invoke(accountScope)

    val canStoreCredentials: Boolean get() = store.credentialVault != null

    /**
     * Keep the preference in sync with the checkbox immediately.  The credential
     * itself is still written only after a successful login, but opting out must
     * remove an old Keychain item right away rather than waiting for logout.
     */
    suspend fun setRememberCredentials(enabled: Boolean): Boolean {
        return if (enabled) {
            runCatching {
                store.preferences.setShouldRememberCredentials(true)
            }.isSuccess
        } else {
            clear()
        }
    }

    suspend fun restore(): CredentialRestoreResult {
        val vault = store.credentialVault ?: return CredentialRestoreResult.Unavailable
        val rememberCredentials = store.preferences.shouldRememberCredentials()
        if (!rememberCredentials) {
            // An explicit opt-out and a missing preference marker are both signed-out
            // states. On iOS the latter is also the exact state produced by uninstall:
            // NSUserDefaults is removed while Keychain survives. Never silently restore
            // a credential when the install-local marker is absent.
            return if (runCatching { vault.clear() }.isSuccess) {
                CredentialRestoreResult.Empty
            } else {
                CredentialRestoreResult.Failed
            }
        }

        return try {
            val credentials = vault.load()
            if (credentials == null) {
                runCatching { store.preferences.setShouldRememberCredentials(false) }
                CredentialRestoreResult.Empty
            } else {
                CredentialRestoreResult.Restored(credentials)
            }
        } catch (_: Exception) {
            runCatching { vault.clear() }
            runCatching { store.preferences.setShouldRememberCredentials(false) }
            CredentialRestoreResult.Failed
        }
    }

    suspend fun persistAfterLogin(
        credentials: Credentials,
        rememberCredentials: Boolean,
    ): Boolean {
        val vault = store.credentialVault
        if (!rememberCredentials || vault == null) {
            return clear()
        }

        return try {
            vault.save(credentials)
            store.preferences.setShouldRememberCredentials(true)
            true
        } catch (_: Exception) {
            runCatching { vault.clear() }
            runCatching { store.preferences.setShouldRememberCredentials(false) }
            false
        }
    }

    suspend fun clear(): Boolean {
        val vaultResult = runCatching { store.credentialVault?.clear() }
        val preferenceResult = runCatching {
            store.preferences.setShouldRememberCredentials(false)
        }
        return vaultResult.isSuccess && preferenceResult.isSuccess
    }

    /** Full local-data wipe: remove the preference marker itself, not merely set it false. */
    suspend fun purge(): Boolean {
        val vaultResult = runCatching { store.credentialVault?.clear() }
        val preferenceResult = runCatching {
            store.preferences.clearRememberCredentialsSetting()
        }
        return vaultResult.isSuccess && preferenceResult.isSuccess
    }
}

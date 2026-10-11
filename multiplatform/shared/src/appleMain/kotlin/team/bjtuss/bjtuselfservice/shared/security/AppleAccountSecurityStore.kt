package team.bjtuss.bjtuselfservice.shared.security

import platform.Foundation.NSUserDefaults

/**
 * iOS 与 macOS 原生共用同一套「Keychain 存密文 + NSUserDefaults 存记忆标记」。
 *
 * [accessibleAfterFirstUnlock] 必须由调用方按平台给：iOS 传 `true`（既定安全决定），
 * macOS 传 `false`（`kSecAttrAccessible` 在 Mac 上不适用）。
 */
fun createAppleAccountSecurityStore(
    accessibleAfterFirstUnlock: Boolean,
    credentialService: String = "team.bjtuss.bjtuselfservice.kmp.credentials",
    credentialAccount: String = "primary",
    rememberCredentialsKey: String = DEFAULT_REMEMBER_CREDENTIALS_KEY,
): AccountSecurityStore = AccountSecurityStore(
    credentialVault = AppleKeychainCredentialVault(
        service = credentialService,
        account = credentialAccount,
        accessibleAfterFirstUnlock = accessibleAfterFirstUnlock,
    ),
    preferences = AppleAccountPreferences(rememberCredentialsKey),
    physicsLabVault = { AppleKeychainCredentialVault(service = "$credentialService.physicslab", account = it, accessibleAfterFirstUnlock = accessibleAfterFirstUnlock) },
    physicsDemoVault = { AppleKeychainCredentialVault(service = "$credentialService.phydemo", account = it, accessibleAfterFirstUnlock = accessibleAfterFirstUnlock) },
    citelVault = { AppleKeychainCredentialVault(service = "$credentialService.citel", account = it, accessibleAfterFirstUnlock = accessibleAfterFirstUnlock) },
)

private class AppleAccountPreferences(
    private val rememberCredentialsKey: String,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AccountPreferences {
    override suspend fun shouldRememberCredentials(): Boolean =
        defaults.boolForKey(rememberCredentialsKey)

    override suspend fun setShouldRememberCredentials(enabled: Boolean) {
        defaults.setBool(enabled, forKey = rememberCredentialsKey)
    }

    override suspend fun clearRememberCredentialsSetting() {
        defaults.removeObjectForKey(rememberCredentialsKey)
    }
}

private const val DEFAULT_REMEMBER_CREDENTIALS_KEY = "remember_credentials"

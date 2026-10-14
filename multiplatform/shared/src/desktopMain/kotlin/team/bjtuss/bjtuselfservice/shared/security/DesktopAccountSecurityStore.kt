package team.bjtuss.bjtuselfservice.shared.security

import java.util.prefs.Preferences

fun createDesktopAccountSecurityStore(): AccountSecurityStore = AccountSecurityStore(
    credentialVault = MacOsBagBackedCredentialVault(
        bag = productionMacOsSecretBag,
        key = MacOsSecretKeys.CREDENTIALS,
        legacyService = LEGACY_CREDENTIALS_SERVICE,
        legacyAccount = LEGACY_PRIMARY_ACCOUNT,
    ),
    preferences = DesktopAccountPreferences(),
    physicsDemoVault = { accountScope ->
        MacOsBagBackedCredentialVault(
            bag = productionMacOsSecretBag,
            key = "phydemo:$accountScope",
            legacyService = LEGACY_PHYSICS_DEMO_SERVICE,
            legacyAccount = accountScope,
        )
    },
    physicsLabVault = { accountScope ->
        MacOsBagBackedCredentialVault(
            bag = productionMacOsSecretBag,
            key = MacOsSecretKeys.physicsLab(accountScope),
            legacyService = LEGACY_PHYSICS_LAB_SERVICE,
            legacyAccount = accountScope,
        )
    },
    citelVault = { accountScope ->
        MacOsBagBackedCredentialVault(
            bag = productionMacOsSecretBag,
            key = MacOsSecretKeys.citel(accountScope),
            legacyService = LEGACY_CITEL_SERVICE,
            legacyAccount = accountScope,
        )
    },
)

private class DesktopAccountPreferences : AccountPreferences {
    private val preferences get() = Preferences.userRoot().node(
        "/team/bjtuss/bjtuselfservice/kmp/account",
    )

    override suspend fun shouldRememberCredentials(): Boolean =
        preferences.getBoolean(REMEMBER_CREDENTIALS_KEY, false)

    override suspend fun setShouldRememberCredentials(enabled: Boolean) {
        preferences.putBoolean(REMEMBER_CREDENTIALS_KEY, enabled)
        preferences.flush()
    }

    override suspend fun clearRememberCredentialsSetting() {
        preferences.remove(REMEMBER_CREDENTIALS_KEY)
        preferences.flush()
    }

    private companion object {
        const val REMEMBER_CREDENTIALS_KEY = "remember_credentials"
    }
}

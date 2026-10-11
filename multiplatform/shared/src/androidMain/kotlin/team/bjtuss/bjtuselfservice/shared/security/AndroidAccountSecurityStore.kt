package team.bjtuss.bjtuselfservice.shared.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import team.bjtuss.bjtuselfservice.shared.auth.Credentials

fun createAndroidAccountSecurityStore(context: Context): AccountSecurityStore = AccountSecurityStore(
    credentialVault = AndroidKeystoreCredentialVault(context.applicationContext),
    preferences = AndroidAccountPreferences(context.applicationContext),
    physicsLabVault = { AndroidKeystoreCredentialVault(context.applicationContext, "team.bjtuss.bjtuselfservice.kmp.physicslab.$it", "secure_physicslab_$it") },
    physicsDemoVault = { AndroidKeystoreCredentialVault(context.applicationContext, "team.bjtuss.bjtuselfservice.kmp.phydemo.$it", "secure_phydemo_$it") },
    citelVault = { AndroidKeystoreCredentialVault(context.applicationContext, "team.bjtuss.bjtuselfservice.kmp.citel.$it", "secure_citel_$it") },
)

class AndroidKeystoreCredentialVault(
    context: Context,
    private val keyAlias: String = "team.bjtuss.bjtuselfservice.kmp.credentials.v1",
    preferenceName: String = "secure_account_credentials",
) : CredentialVault {
    private val preferences = context.getSharedPreferences(
        preferenceName,
        Context.MODE_PRIVATE,
    )

    override suspend fun save(credentials: Credentials) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val encrypted = cipher.doFinal(encodeCredentialPayload(credentials))
            val committed = preferences.edit()
                .putString(IV_KEY, cipher.iv.toBase64())
                .putString(PAYLOAD_KEY, encrypted.toBase64())
                .commit()
            if (!committed) throw IllegalStateException("Unable to commit encrypted credentials")
        } catch (error: Exception) {
            throw CredentialVaultException(CredentialVaultOperation.SAVE, cause = error)
        }
    }

    override suspend fun load(): Credentials? {
        val iv = preferences.getString(IV_KEY, null) ?: return null
        val payload = preferences.getString(PAYLOAD_KEY, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv.fromBase64()),
            )
            decodeCredentialPayload(cipher.doFinal(payload.fromBase64()))
                ?: throw IllegalStateException("Invalid credential payload")
        } catch (error: Exception) {
            throw CredentialVaultException(CredentialVaultOperation.LOAD, cause = error)
        }
    }

    override suspend fun clear() {
        val committed = preferences.edit().clear().commit()
        if (!committed) {
            throw CredentialVaultException(CredentialVaultOperation.CLEAR)
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val IV_KEY = "credential_iv"
        const val PAYLOAD_KEY = "credential_payload"
    }
}

private class AndroidAccountPreferences(context: Context) : AccountPreferences {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        "account_preferences",
        Context.MODE_PRIVATE,
    )

    override suspend fun shouldRememberCredentials(): Boolean =
        preferences.getBoolean(REMEMBER_CREDENTIALS_KEY, false)

    override suspend fun setShouldRememberCredentials(enabled: Boolean) {
        val committed = preferences.edit()
            .putBoolean(REMEMBER_CREDENTIALS_KEY, enabled)
            .commit()
        if (!committed) error("Unable to commit account preferences")
    }

    override suspend fun clearRememberCredentialsSetting() {
        if (!preferences.edit().remove(REMEMBER_CREDENTIALS_KEY).commit()) {
            error("Unable to clear account preferences")
        }
    }

    private companion object {
        const val REMEMBER_CREDENTIALS_KEY = "remember_credentials"
    }
}

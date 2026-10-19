package team.bjtuss.bjtuselfservice.windows

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import java.util.Base64
import java.security.SecureRandom
import java.util.prefs.Preferences
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.security.AccountPreferences
import team.bjtuss.bjtuselfservice.shared.security.AccountSecurityStore
import team.bjtuss.bjtuselfservice.shared.security.CredentialVault
import team.bjtuss.bjtuselfservice.shared.security.CredentialVaultException
import team.bjtuss.bjtuselfservice.shared.security.CredentialVaultOperation
import team.bjtuss.bjtuselfservice.shared.security.decodeCredentialPayload
import team.bjtuss.bjtuselfservice.shared.security.encodeCredentialPayload

/**
 * Windows 凭据保险库：DPAPI（CryptProtectData/CryptUnprotectData）按当前
 * Windows 用户加解密，密文（base64）与“是否记住”标记存 java.util.prefs。
 * 与 Android Keystore / Apple Keychain 同属「系统安全存储」语义；密钥由
 * Windows 用户配置文件派生，不落明文。
 */
fun createWindowsAccountSecurityStore(): AccountSecurityStore = AccountSecurityStore(
    credentialVault = WindowsDpapiCredentialVault(),
    preferences = WindowsAccountPreferences(),
    physicsLabVault = { WindowsDpapiCredentialVault(service = "team.bjtuss.bjtuselfservice.kmp.physicslab", account = it) },
    physicsDemoVault = { WindowsDpapiCredentialVault(service = "team.bjtuss.bjtuselfservice.kmp.phydemo", account = it) },
    citelVault = { WindowsDpapiCredentialVault(service = "team.bjtuss.bjtuselfservice.kmp.citel", account = it) },
    phyVlabVault = { WindowsDpapiCredentialVault(service = "team.bjtuss.bjtuselfservice.kmp.phyvlab", account = it) },
)

class WindowsDpapiCredentialVault(
    private val service: String = "team.bjtuss.bjtuselfservice.kmp.credentials",
    private val account: String = "primary",
) : CredentialVault {
    private val preferences = Preferences.userRoot().node(
        "/team/bjtuss/bjtuselfservice/kmp/credentials",
    ).node("$service/$account")

    override suspend fun save(credentials: Credentials) {
        try {
            val encrypted = Dpapi.cryptProtect(
                encodeCredentialPayload(credentials),
                ENTROPY_BYTES,
            )
            preferences.put(PAYLOAD_KEY, Base64.getEncoder().encodeToString(encrypted))
            preferences.flush()
        } catch (error: Exception) {
            throw CredentialVaultException(CredentialVaultOperation.SAVE, cause = error)
        }
    }

    override suspend fun load(): Credentials? {
        val payload = preferences.get(PAYLOAD_KEY, null) ?: return null
        return try {
            val decrypted = Dpapi.cryptUnprotect(
                Base64.getDecoder().decode(payload),
                ENTROPY_BYTES,
            )
            decodeCredentialPayload(decrypted)
                ?: throw IllegalStateException("Invalid credential payload")
        } catch (error: Exception) {
            throw CredentialVaultException(CredentialVaultOperation.LOAD, cause = error)
        }
    }

    override suspend fun clear() {
        try {
            preferences.remove(PAYLOAD_KEY)
            preferences.flush()
        } catch (error: Exception) {
            throw CredentialVaultException(CredentialVaultOperation.CLEAR, cause = error)
        }
    }

    private companion object {
        const val PAYLOAD_KEY = "credential_payload"
        val ENTROPY_BYTES: ByteArray =
            "team.bjtuss.bjtuselfservice.kmp.credentials.v1".encodeToByteArray()
    }
}

private class WindowsAccountPreferences : AccountPreferences {
    private val preferences = Preferences.userRoot().node(
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

internal data class WindowsCacheKey(
    val bytes: ByteArray,
    val created: Boolean,
)

/** Cache key is DPAPI-bound to the current Windows user; only ciphertext enters prefs. */
internal fun loadOrCreateWindowsCacheKey(
    preferences: Preferences = Preferences.userRoot().node(
        "/team/bjtuss/bjtuselfservice/kmp/cache",
    ),
): WindowsCacheKey {
    val entropy = "team.bjtuss.bjtuselfservice.kmp.cache-key.v1".encodeToByteArray()
    preferences.get(CACHE_KEY_PAYLOAD, null)?.let { encoded ->
        val key = Dpapi.cryptUnprotect(Base64.getDecoder().decode(encoded), entropy)
        require(key.size == 32) { "Invalid cache encryption key" }
        return WindowsCacheKey(key, created = false)
    }

    val key = ByteArray(32).also(SecureRandom()::nextBytes)
    val encrypted = Dpapi.cryptProtect(key, entropy)
    preferences.put(CACHE_KEY_PAYLOAD, Base64.getEncoder().encodeToString(encrypted))
    preferences.flush()
    return WindowsCacheKey(key, created = true)
}

/** 只读取已有缓存密钥，不新建（AI Agent 命令行用）；没有则返回 null。 */
internal fun loadExistingWindowsCacheKey(
    preferences: Preferences = Preferences.userRoot().node(
        "/team/bjtuss/bjtuselfservice/kmp/cache",
    ),
): ByteArray? {
    val encoded = preferences.get(CACHE_KEY_PAYLOAD, null) ?: return null
    val key = Dpapi.cryptUnprotect(
        Base64.getDecoder().decode(encoded),
        "team.bjtuss.bjtuselfservice.kmp.cache-key.v1".encodeToByteArray(),
    )
    require(key.size == 32) { "Invalid cache encryption key" }
    return key
}

private const val CACHE_KEY_PAYLOAD = "cache_key_payload"

/** JNA 绑定 crypt32 的 DPAPI；只在此文件内使用，业务层不接触 JNA。 */
internal object Dpapi {
    const val CRYPTPROTECT_UI_FORBIDDEN = 0x1

    fun cryptProtect(data: ByteArray, entropy: ByteArray): ByteArray {
        val input = DataBlob.from(data)
        val entropyBlob = DataBlob.from(entropy)
        val output = DataBlob()
        return try {
            val success = Crypt32.INSTANCE.CryptProtectData(
                input.pointer,
                null,
                entropyBlob.pointer,
                null,
                null,
                CRYPTPROTECT_UI_FORBIDDEN,
                output.pointer,
            )
            if (!success) {
                throw DpapiException("CryptProtectData failed: ${Native.getLastError()}")
            }
            output.readBytes()
        } finally {
            input.free()
            entropyBlob.free()
            output.free()
        }
    }

    fun cryptUnprotect(encrypted: ByteArray, entropy: ByteArray): ByteArray {
        val input = DataBlob.from(encrypted)
        val entropyBlob = DataBlob.from(entropy)
        val output = DataBlob()
        return try {
            val success = Crypt32.INSTANCE.CryptUnprotectData(
                input.pointer,
                null,
                entropyBlob.pointer,
                null,
                null,
                CRYPTPROTECT_UI_FORBIDDEN,
                output.pointer,
            )
            if (!success) {
                throw DpapiException("CryptUnprotectData failed: ${Native.getLastError()}")
            }
            output.readBytes()
        } finally {
            input.free()
            entropyBlob.free()
            output.free()
        }
    }
}

internal class DpapiException(message: String) : Exception(message)

private interface Crypt32 : Library {
    fun CryptProtectData(
        dataIn: Pointer,
        dataDescr: WString?,
        optionalEntropy: Pointer,
        reserved: Pointer?,
        promptStruct: Pointer?,
        flags: Int,
        dataOut: Pointer,
    ): Boolean

    fun CryptUnprotectData(
        dataIn: Pointer,
        dataDescr: Pointer?,
        optionalEntropy: Pointer,
        reserved: Pointer?,
        promptStruct: Pointer?,
        flags: Int,
        dataOut: Pointer,
    ): Boolean

    companion object {
        val INSTANCE: Crypt32 by lazy {
            Native.load("Crypt32", Crypt32::class.java)
        }
    }
}

/**
 * DATA_BLOB 结构：DWORD cbData（偏移 0，4 字节）+ 对齐填充 + BYTE* pbData
 * （偏移 8，x64 上 8 字节），共 16 字节。持有结构体与数据两段原生内存，
 * free() 必须同时释放两者。
 */
private class DataBlob {
    private var memory: Memory? = null
    private var data: Memory? = null

    val pointer: Pointer
        get() = memory ?: Memory(2L * Native.POINTER_SIZE).also { memory = it }

    fun readBytes(): ByteArray {
        val size = pointer.getInt(0)
        val data = pointer.getPointer(Native.POINTER_SIZE.toLong())
        return data.getByteArray(0, size)
    }

    fun free() {
        data?.let { it.clear() }
        memory?.let { it.clear() }
        data = null
        memory = null
    }

    companion object {
        fun from(bytes: ByteArray): DataBlob = DataBlob().also { blob ->
            val data = Memory(bytes.size.toLong())
            data.write(0, bytes, 0, bytes.size)
            blob.data = data
            blob.pointer.setInt(0, bytes.size)
            blob.pointer.setPointer(Native.POINTER_SIZE.toLong(), data)
        }
    }
}

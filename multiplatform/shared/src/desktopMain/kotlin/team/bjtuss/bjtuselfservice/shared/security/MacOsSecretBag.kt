package team.bjtuss.bjtuselfservice.shared.security

import team.bjtuss.bjtuselfservice.shared.auth.Credentials

internal const val MACOS_SECRET_BAG_SERVICE = "team.bjtuss.bjtuselfservice.kmp.secrets"
internal const val MACOS_SECRET_BAG_ACCOUNT = "primary"

internal const val LEGACY_CREDENTIALS_SERVICE = "team.bjtuss.bjtuselfservice.kmp.credentials"
internal const val LEGACY_CACHE_KEY_SERVICE = "team.bjtuss.bjtuselfservice.kmp.cache-key"
internal const val LEGACY_PHYSICS_LAB_SERVICE = "team.bjtuss.bjtuselfservice.kmp.physicslab"
internal const val LEGACY_PHYSICS_DEMO_SERVICE = "team.bjtuss.bjtuselfservice.kmp.credentials.phydemo"
internal const val LEGACY_CITEL_SERVICE = "team.bjtuss.bjtuselfservice.kmp.citel"
internal const val LEGACY_PRIMARY_ACCOUNT = "primary"

internal object MacOsSecretKeys {
    const val CREDENTIALS = "credentials"
    const val CACHE_KEY = "cache-key"

    fun physicsLab(accountScope: String): String = "physicslab:$accountScope"
    fun citel(accountScope: String): String = "citel:$accountScope"
}

/**
 * One generic-password Keychain item that holds every desktop secret.
 *
 * Ad-hoc upgrades change the app CDHash, so macOS prompts once per item whose ACL
 * still names the old binary. Keeping login, cache key, and extra accounts in a
 * single item stops that prompt count from growing with each new password.
 */
internal class MacOsSecretBag(
    service: String,
    account: String,
) {
    private val item = MacOsKeychainItem(service, account)
    private val lock = Any()

    fun get(key: String, legacy: MacOsKeychainItem? = null): ByteArray? = synchronized(lock) {
        val map = loadMap()
        map[key]?.copyOf()?.let { return it }
        val migrated = legacy?.load(CredentialVaultOperation.LOAD) ?: return null
        map[key] = migrated.copyOf()
        persist(map)
        runCatching { legacy.clear(CredentialVaultOperation.CLEAR) }
        migrated.copyOf()
    }

    fun put(key: String, value: ByteArray, legacy: MacOsKeychainItem? = null) = synchronized(lock) {
        val map = loadMap()
        map[key] = value.copyOf()
        persist(map)
        runCatching { legacy?.clear(CredentialVaultOperation.CLEAR) }
        Unit
    }

    fun remove(key: String, legacy: MacOsKeychainItem? = null) = synchronized(lock) {
        val map = loadMap()
        map.remove(key)
        persist(map)
        runCatching { legacy?.clear(CredentialVaultOperation.CLEAR) }
        Unit
    }

    /** Live settings wipe keeps only the encryption key for the already-open empty database. */
    fun clearPersonalData() = synchronized(lock) {
        val map = loadMap()
        persist(map.filterKeys { it == MacOsSecretKeys.CACHE_KEY })
    }

    fun clearAll() = synchronized(lock) {
        item.clear(CredentialVaultOperation.CLEAR)
    }

    private fun loadMap(): MutableMap<String, ByteArray> {
        val bytes = item.load(CredentialVaultOperation.LOAD) ?: return mutableMapOf()
        return decodeSecretBagPayload(bytes)
    }

    private fun persist(map: Map<String, ByteArray>) {
        if (map.isEmpty()) {
            item.clear(CredentialVaultOperation.CLEAR)
        } else {
            item.save(encodeSecretBagPayload(map), CredentialVaultOperation.SAVE)
        }
    }
}

internal val productionMacOsSecretBag: MacOsSecretBag by lazy {
    MacOsSecretBag(MACOS_SECRET_BAG_SERVICE, MACOS_SECRET_BAG_ACCOUNT)
}

internal class MacOsBagBackedCredentialVault(
    private val bag: MacOsSecretBag,
    private val key: String,
    private val legacyService: String,
    private val legacyAccount: String,
) : CredentialVault {
    private fun legacyItem() = MacOsKeychainItem(legacyService, legacyAccount)

    override suspend fun save(credentials: Credentials) {
        bag.put(key, encodeCredentialPayload(credentials), legacyItem())
    }

    override suspend fun load(): Credentials? {
        val bytes = bag.get(key, legacyItem()) ?: return null
        return decodeCredentialPayload(bytes)
            ?: throw CredentialVaultException(CredentialVaultOperation.LOAD, platformStatus = 0)
    }

    override suspend fun clear() {
        bag.remove(key, legacyItem())
    }
}

internal fun encodeSecretBagPayload(entries: Map<String, ByteArray>): ByteArray {
    require(entries.size <= MAX_SECRET_BAG_ENTRIES) { "Secret bag has too many keys" }
    val encoded = entries.map { (key, value) ->
        val keyBytes = key.encodeToByteArray()
        require(keyBytes.size in 1..MAX_SECRET_BAG_KEY_BYTES) { "Secret bag key is invalid" }
        require(value.size in 0..MAX_SECRET_BAG_VALUE_BYTES) { "Secret bag value is too large" }
        keyBytes to value
    }
    val payload = ByteArray(
        SECRET_BAG_MAGIC.size + 1 + 4 + encoded.sumOf { (key, value) -> 8 + key.size + value.size },
    )
    var offset = 0
    SECRET_BAG_MAGIC.copyInto(payload, offset)
    offset += SECRET_BAG_MAGIC.size
    payload[offset] = SECRET_BAG_VERSION
    offset += 1
    writeSecretBagInt(payload, offset, encoded.size)
    offset += 4
    for ((keyBytes, value) in encoded) {
        writeSecretBagInt(payload, offset, keyBytes.size)
        offset += 4
        keyBytes.copyInto(payload, offset)
        offset += keyBytes.size
        writeSecretBagInt(payload, offset, value.size)
        offset += 4
        if (value.isNotEmpty()) {
            value.copyInto(payload, offset)
            offset += value.size
        }
    }
    return payload
}

internal fun decodeSecretBagPayload(payload: ByteArray): MutableMap<String, ByteArray> {
    val header = SECRET_BAG_MAGIC.size + 1 + 4
    if (payload.size < header ||
        !payload.copyOfRange(0, SECRET_BAG_MAGIC.size).contentEquals(SECRET_BAG_MAGIC) ||
        payload[SECRET_BAG_MAGIC.size] != SECRET_BAG_VERSION
    ) {
        throw CredentialVaultException(CredentialVaultOperation.LOAD)
    }
    var offset = SECRET_BAG_MAGIC.size + 1
    val count = readSecretBagInt(payload, offset)
    offset += 4
    if (count !in 0..MAX_SECRET_BAG_ENTRIES) {
        throw CredentialVaultException(CredentialVaultOperation.LOAD)
    }
    val map = mutableMapOf<String, ByteArray>()
    repeat(count) {
        if (offset + 4 > payload.size) throw CredentialVaultException(CredentialVaultOperation.LOAD)
        val keyLen = readSecretBagInt(payload, offset)
        offset += 4
        if (keyLen !in 1..MAX_SECRET_BAG_KEY_BYTES || offset + keyLen + 4 > payload.size) {
            throw CredentialVaultException(CredentialVaultOperation.LOAD)
        }
        val key = try {
            payload.decodeToString(
                startIndex = offset,
                endIndex = offset + keyLen,
                throwOnInvalidSequence = true,
            )
        } catch (_: CharacterCodingException) {
            throw CredentialVaultException(CredentialVaultOperation.LOAD)
        }
        offset += keyLen
        val valueLen = readSecretBagInt(payload, offset)
        offset += 4
        if (valueLen !in 0..MAX_SECRET_BAG_VALUE_BYTES || offset + valueLen > payload.size) {
            throw CredentialVaultException(CredentialVaultOperation.LOAD)
        }
        val value = if (valueLen == 0) byteArrayOf() else payload.copyOfRange(offset, offset + valueLen)
        offset += valueLen
        if (key in map) throw CredentialVaultException(CredentialVaultOperation.LOAD)
        map[key] = value
    }
    if (offset != payload.size) throw CredentialVaultException(CredentialVaultOperation.LOAD)
    return map
}

private fun writeSecretBagInt(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value ushr 24).toByte()
    target[offset + 1] = (value ushr 16).toByte()
    target[offset + 2] = (value ushr 8).toByte()
    target[offset + 3] = value.toByte()
}

private fun readSecretBagInt(source: ByteArray, offset: Int): Int =
    ((source[offset].toInt() and 0xFF) shl 24) or
        ((source[offset + 1].toInt() and 0xFF) shl 16) or
        ((source[offset + 2].toInt() and 0xFF) shl 8) or
        (source[offset + 3].toInt() and 0xFF)

private val SECRET_BAG_MAGIC = "BJTUSBAG".encodeToByteArray()
private const val SECRET_BAG_VERSION: Byte = 1
private const val MAX_SECRET_BAG_ENTRIES = 64
private const val MAX_SECRET_BAG_KEY_BYTES = 256
private const val MAX_SECRET_BAG_VALUE_BYTES = 64 * 1024

/** Service-wide deletion removes old account-scoped entries even when no cache remains. */
internal fun clearLegacyMacOsAppSecrets() {
    listOf(LEGACY_CREDENTIALS_SERVICE, LEGACY_CACHE_KEY_SERVICE, LEGACY_PHYSICS_LAB_SERVICE,
        LEGACY_PHYSICS_DEMO_SERVICE, LEGACY_CITEL_SERVICE).forEach { service ->
        MacOsKeychainItem(service, null).clear(CredentialVaultOperation.CLEAR)
    }
}

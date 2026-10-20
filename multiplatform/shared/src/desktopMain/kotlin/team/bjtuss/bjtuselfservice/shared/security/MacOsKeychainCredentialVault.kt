package team.bjtuss.bjtuselfservice.shared.security

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import team.bjtuss.bjtuselfservice.shared.auth.Credentials

class MacOsKeychainCredentialVault(
    private val service: String = "team.bjtuss.bjtuselfservice.kmp.credentials",
    private val account: String = "primary",
) : CredentialVault {
    private val item = MacOsKeychainItem(service, account)

    override suspend fun save(credentials: Credentials) {
        item.save(encodeCredentialPayload(credentials), CredentialVaultOperation.SAVE)
    }

    override suspend fun load(): Credentials? {
        val bytes = item.load(CredentialVaultOperation.LOAD) ?: return null
        return decodeCredentialPayload(bytes)
            ?: throw vaultError(CredentialVaultOperation.LOAD, ERR_SEC_SUCCESS)
    }

    override suspend fun clear() {
        item.clear(CredentialVaultOperation.CLEAR)
    }
}

/** Generic-password Keychain item. Production secrets share one item via [MacOsSecretBag]. */
internal class MacOsKeychainItem(
    private val service: String,
    private val account: String?,
) {
    fun save(payload: ByteArray, operation: CredentialVaultOperation) {
        require(account != null)
        val clearStatus = withQuery { SecurityApi.INSTANCE.SecItemDelete(it) }
        if (clearStatus != ERR_SEC_SUCCESS && clearStatus != ERR_SEC_ITEM_NOT_FOUND) {
            throw vaultError(operation, clearStatus)
        }
        val status = withQuery(payload = payload) { SecurityApi.INSTANCE.SecItemAdd(it, null) }
        if (status != ERR_SEC_SUCCESS) throw vaultError(operation, status)
    }

    fun load(operation: CredentialVaultOperation): ByteArray? {
        require(account != null)
        val result = PointerByReference()
        val status = withQuery(returnData = true) {
            SecurityApi.INSTANCE.SecItemCopyMatching(it, result)
        }
        if (status == ERR_SEC_ITEM_NOT_FOUND) return null
        if (status != ERR_SEC_SUCCESS) throw vaultError(operation, status)

        val data = result.value ?: throw vaultError(operation, status)
        return try {
            val length = CoreFoundationApi.INSTANCE.CFDataGetLength(data)
            CoreFoundationApi.INSTANCE.CFDataGetBytePtr(data)
                ?.getByteArray(0, length.toInt())
                ?: byteArrayOf()
        } finally {
            CoreFoundationApi.INSTANCE.CFRelease(data)
        }
    }

    fun clear(operation: CredentialVaultOperation) {
        val status = withQuery { SecurityApi.INSTANCE.SecItemDelete(it) }
        if (status != ERR_SEC_SUCCESS && status != ERR_SEC_ITEM_NOT_FOUND) {
            throw vaultError(operation, status)
        }
    }

    private fun <T> withQuery(
        payload: ByteArray? = null,
        returnData: Boolean = false,
        block: (Pointer) -> T,
    ): T {
        val ownedValues = mutableListOf<Pointer>()
        fun cfString(value: String): Pointer = CoreFoundationApi.INSTANCE.createString(value)
            .also(ownedValues::add)
        fun cfData(value: ByteArray): Pointer = CoreFoundationApi.INSTANCE.createData(value)
            .also(ownedValues::add)

        val entries = mutableListOf(
            SecuritySymbols.kSecClass to SecuritySymbols.kSecClassGenericPassword,
            SecuritySymbols.kSecAttrService to cfString(service),
        )
        if (account != null) entries += SecuritySymbols.kSecAttrAccount to cfString(account)
        else entries += SecuritySymbols.kSecMatchLimit to SecuritySymbols.kSecMatchLimitAll
        if (payload != null) {
            entries += SecuritySymbols.kSecValueData to cfData(payload)
        }
        if (returnData) {
            entries += SecuritySymbols.kSecReturnData to CoreFoundationSymbols.kCFBooleanTrue
            entries += SecuritySymbols.kSecMatchLimit to SecuritySymbols.kSecMatchLimitOne
        }

        val dictionary = CoreFoundationApi.INSTANCE.CFDictionaryCreate(
            null,
            entries.map { it.first }.toTypedArray(),
            entries.map { it.second }.toTypedArray(),
            entries.size.toLong(),
            CoreFoundationSymbols.kCFTypeDictionaryKeyCallBacks,
            CoreFoundationSymbols.kCFTypeDictionaryValueCallBacks,
        ) ?: error("Unable to create macOS Keychain query")

        return try {
            block(dictionary)
        } finally {
            CoreFoundationApi.INSTANCE.CFRelease(dictionary)
            ownedValues.forEach(CoreFoundationApi.INSTANCE::CFRelease)
        }
    }
}

private interface SecurityApi : Library {
    fun SecItemAdd(attributes: Pointer, result: PointerByReference?): Int
    fun SecItemCopyMatching(query: Pointer, result: PointerByReference): Int
    fun SecItemDelete(query: Pointer): Int

    companion object {
        val INSTANCE: SecurityApi = Native.load(SECURITY_FRAMEWORK, SecurityApi::class.java)
    }
}

private interface CoreFoundationApi : Library {
    fun CFStringCreateWithCString(allocator: Pointer?, value: Pointer, encoding: Int): Pointer?
    fun CFDataCreate(allocator: Pointer?, bytes: Pointer, length: Long): Pointer?
    fun CFDataGetLength(data: Pointer): Long
    fun CFDataGetBytePtr(data: Pointer): Pointer?
    fun CFDictionaryCreate(
        allocator: Pointer?,
        keys: Array<Pointer>,
        values: Array<Pointer>,
        count: Long,
        keyCallbacks: Pointer,
        valueCallbacks: Pointer,
    ): Pointer?
    fun CFRelease(value: Pointer)

    fun createString(value: String): Pointer {
        val utf8 = value.encodeToByteArray()
        val memory = Memory(utf8.size.toLong() + 1)
        memory.write(0, utf8, 0, utf8.size)
        memory.setByte(utf8.size.toLong(), 0)
        return CFStringCreateWithCString(null, memory, CF_STRING_ENCODING_UTF8)
            ?: error("Unable to create CFString")
    }

    fun createData(value: ByteArray): Pointer {
        val memory = Memory(value.size.coerceAtLeast(1).toLong())
        if (value.isNotEmpty()) memory.write(0, value, 0, value.size)
        return CFDataCreate(null, memory, value.size.toLong())
            ?: error("Unable to create CFData")
    }

    companion object {
        val INSTANCE: CoreFoundationApi = Native.load(CORE_FOUNDATION_FRAMEWORK, CoreFoundationApi::class.java)
    }
}

private object SecuritySymbols {
    private val library = NativeLibrary.getInstance(SECURITY_FRAMEWORK)
    val kSecClass: Pointer = library.pointerConstant("kSecClass")
    val kSecClassGenericPassword: Pointer = library.pointerConstant("kSecClassGenericPassword")
    val kSecAttrService: Pointer = library.pointerConstant("kSecAttrService")
    val kSecAttrAccount: Pointer = library.pointerConstant("kSecAttrAccount")
    val kSecValueData: Pointer = library.pointerConstant("kSecValueData")
    val kSecReturnData: Pointer = library.pointerConstant("kSecReturnData")
    val kSecMatchLimit: Pointer = library.pointerConstant("kSecMatchLimit")
    val kSecMatchLimitAll: Pointer = library.pointerConstant("kSecMatchLimitAll")
    val kSecMatchLimitOne: Pointer = library.pointerConstant("kSecMatchLimitOne")
}

private object CoreFoundationSymbols {
    private val library = NativeLibrary.getInstance(CORE_FOUNDATION_FRAMEWORK)
    val kCFBooleanTrue: Pointer = library.pointerConstant("kCFBooleanTrue")
    val kCFTypeDictionaryKeyCallBacks: Pointer =
        library.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks")
    val kCFTypeDictionaryValueCallBacks: Pointer =
        library.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks")
}

private fun NativeLibrary.pointerConstant(name: String): Pointer =
    getGlobalVariableAddress(name).getPointer(0)

private fun vaultError(
    operation: CredentialVaultOperation,
    status: Int,
): CredentialVaultException = CredentialVaultException(operation, platformStatus = status)

private const val SECURITY_FRAMEWORK = "/System/Library/Frameworks/Security.framework/Security"
private const val CORE_FOUNDATION_FRAMEWORK =
    "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation"
private const val CF_STRING_ENCODING_UTF8 = 0x08000100
private const val ERR_SEC_SUCCESS = 0
private const val ERR_SEC_ITEM_NOT_FOUND = -25300

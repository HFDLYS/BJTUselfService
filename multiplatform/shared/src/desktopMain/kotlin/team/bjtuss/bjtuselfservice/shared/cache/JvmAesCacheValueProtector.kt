package team.bjtuss.bjtuselfservice.shared.cache

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import team.bjtuss.bjtuselfservice.shared.security.LEGACY_CACHE_KEY_SERVICE
import team.bjtuss.bjtuselfservice.shared.security.LEGACY_PRIMARY_ACCOUNT
import team.bjtuss.bjtuselfservice.shared.security.MacOsKeychainItem
import team.bjtuss.bjtuselfservice.shared.security.MacOsSecretKeys
import team.bjtuss.bjtuselfservice.shared.security.productionMacOsSecretBag

/** AES-256-GCM field protection used by the JVM desktop cache stores. */
class JvmAesCacheValueProtector(keyBytes: ByteArray) : CacheValueProtector {
    private val encryptionKey = SecretKeySpec(deriveKey(keyBytes, "encryption"), "AES")
    private val stableIvKey = SecretKeySpec(deriveKey(keyBytes, "stable-iv"), "HmacSHA256")
    private val numberKey = SecretKeySpec(deriveKey(keyBytes, "numbers"), "HmacSHA256")
    private val random = SecureRandom()

    init {
        require(keyBytes.size == KEY_SIZE_BYTES) { "Cache encryption key must be 256 bits" }
    }

    override fun protect(value: String): String = encrypt(value, randomIv(), MODE_RANDOM)

    override fun protectStable(value: String): String = encrypt(value, stableIv(value), MODE_STABLE)

    override fun unprotect(value: String): String {
        val parts = value.split(':', limit = 4)
        require(parts.size == 4 && parts[0] == PREFIX && parts[1] == VERSION) {
            "Unprotected value found in encrypted cache"
        }
        val iv = decoder.decode(parts[2])
        val ciphertext = decoder.decode(parts[3])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(AAD)
        return cipher.doFinal(ciphertext).toString(StandardCharsets.UTF_8)
    }

    // Eight-round Feistel permutation over 64-bit signed SQLite integers. It keeps
    // SQLDelight's INTEGER schema and exact round trips while hiding IDs and counts.
    // Equality and ordering by the database's own row ID remain observable.
    override fun protectNumber(value: Long): Long {
        var left = (value ushr 32).toInt()
        var right = value.toInt()
        repeat(NUMBER_ROUNDS) { round ->
            val next = left xor numberRound(right, round)
            left = right
            right = next
        }
        return combineNumberHalves(left, right)
    }

    override fun unprotectNumber(value: Long): Long {
        var left = (value ushr 32).toInt()
        var right = value.toInt()
        for (round in NUMBER_ROUNDS - 1 downTo 0) {
            val priorLeft = right xor numberRound(left, round)
            right = left
            left = priorLeft
        }
        return combineNumberHalves(left, right)
    }

    private fun numberRound(half: Int, round: Int): Int {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(numberKey)
        val bytes = byteArrayOf(
            round.toByte(),
            (half ushr 24).toByte(),
            (half ushr 16).toByte(),
            (half ushr 8).toByte(),
            half.toByte(),
        )
        val digest = mac.doFinal(bytes)
        return ((digest[0].toInt() and 0xff) shl 24) or
            ((digest[1].toInt() and 0xff) shl 16) or
            ((digest[2].toInt() and 0xff) shl 8) or
            (digest[3].toInt() and 0xff)
    }

    private fun combineNumberHalves(left: Int, right: Int): Long =
        (left.toLong() shl 32) or (right.toLong() and 0xffffffffL)

    private fun encrypt(value: String, iv: ByteArray, mode: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(AAD)
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return "$PREFIX:$VERSION:${encoder.encodeToString(iv)}:${encoder.encodeToString(ciphertext)}"
            .also { require(mode == MODE_RANDOM || mode == MODE_STABLE) }
    }

    private fun stableIv(value: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(stableIvKey)
        mac.update(MODE_STABLE.toByteArray(StandardCharsets.UTF_8))
        mac.update(0.toByte())
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)).copyOf(IV_SIZE_BYTES)
    }

    private fun randomIv(): ByteArray = ByteArray(IV_SIZE_BYTES).also(random::nextBytes)

    private companion object {
        const val PREFIX = "bjtu-cache"
        const val VERSION = "v1"
        const val MODE_RANDOM = "random"
        const val MODE_STABLE = "stable"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BYTES = 32
        const val IV_SIZE_BYTES = 12
        const val TAG_BITS = 128
        const val NUMBER_ROUNDS = 8
        val AAD: ByteArray = "$PREFIX:$VERSION".toByteArray(StandardCharsets.UTF_8)
        val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        val decoder: Base64.Decoder = Base64.getUrlDecoder()

        fun deriveKey(master: ByteArray, purpose: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(master, "HmacSHA256"))
            return mac.doFinal("bjtu-cache-v1:$purpose".toByteArray(StandardCharsets.UTF_8))
        }
    }
}

internal data class DesktopCacheKey(
    val bytes: ByteArray,
    val created: Boolean,
)

internal fun loadOrCreateMacOsCacheKey(): DesktopCacheKey {
    val legacy = MacOsKeychainItem(LEGACY_CACHE_KEY_SERVICE, LEGACY_PRIMARY_ACCOUNT)
    productionMacOsSecretBag.get(MacOsSecretKeys.CACHE_KEY, legacy)?.let { existing ->
        require(existing.size == 32) { "Invalid cache encryption key" }
        return DesktopCacheKey(existing, created = false)
    }
    val created = ByteArray(32).also(SecureRandom()::nextBytes)
    productionMacOsSecretBag.put(MacOsSecretKeys.CACHE_KEY, created, legacy)
    return DesktopCacheKey(created, created = true)
}

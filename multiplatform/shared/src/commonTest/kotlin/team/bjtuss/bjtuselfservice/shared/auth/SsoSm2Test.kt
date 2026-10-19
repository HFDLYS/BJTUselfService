package team.bjtuss.bjtuselfservice.shared.auth

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 夹具密钥由 sso.bjtu.edu.cn 页面自带的 sm-crypto（sm2.min.js）一次性生成，仅用于测试；
 * NODE_CIPHER 是该库对 FIXTURE_MESSAGE 的真实加密结果，用来独立验证曲线、KDF、SM3 和 C1C3C2 布局。
 */
class SsoSm2Test {
    @Test
    fun sm3MatchesStandardVectors() {
        assertEquals(
            "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0",
            Sm3.digest("abc".encodeToByteArray()).toHex(),
        )
        assertEquals(
            "debe9ff92275b8a138604889c18e5a4d6fdb70e5387e5765293dcba39c0c5732",
            Sm3.digest("abcd".repeat(16).encodeToByteArray()).toHex(),
        )
    }

    @Test
    fun publicKeyDerivedFromPrivateKeyMatchesSmCrypto() {
        val derived = Sm2Curve.toAffine(Sm2Curve.multiply(Sm2Curve.G, privateScalar()))!!
        val expected = Base64.decode(FIXTURE_PUBLIC_KEY)
        assertContentEquals(expected.copyOfRange(1, 33), Sm2Field.toBytes(derived.x))
        assertContentEquals(expected.copyOfRange(33, 65), Sm2Field.toBytes(derived.y))
    }

    @Test
    fun decryptsCiphertextProducedBySmCrypto() {
        assertEquals(FIXTURE_MESSAGE, decrypt(Base64.decode(NODE_CIPHER)).decodeToString())
    }

    @Test
    fun encryptedPasswordRoundTripsAndUsesSmCryptoLayout() {
        val cipher = Base64.decode(SsoSm2.encryptPassword(FIXTURE_MESSAGE, FIXTURE_PUBLIC_KEY))
        // C1(x||y, 64) + C3(32) + C2(len)，不带 04 前缀。
        assertEquals(64 + 32 + FIXTURE_MESSAGE.encodeToByteArray().size, cipher.size)
        assertEquals(FIXTURE_MESSAGE, decrypt(cipher).decodeToString())
    }

    @Test
    fun rejectsPointsOffTheCurve() {
        val bad = Base64.decode(FIXTURE_PUBLIC_KEY).also { it[64] = (it[64].toInt() xor 1).toByte() }
        val failure = runCatching { SsoSm2.encryptPassword("x", Base64.encode(bad)) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    private fun privateScalar(): IntArray = U256.fromBytes(Base64.decode(FIXTURE_PRIVATE_KEY))

    private fun decrypt(cipher: ByteArray): ByteArray {
        val c1 = Sm2Curve.affine(
            Sm2Field.fromBytes(cipher.copyOfRange(0, 32)),
            Sm2Field.fromBytes(cipher.copyOfRange(32, 64)),
        )
        val c3 = cipher.copyOfRange(64, 96)
        val c2 = cipher.copyOfRange(96, cipher.size)
        val shared = Sm2Curve.toAffine(Sm2Curve.multiply(c1, privateScalar()))!!
        val x2 = Sm2Field.toBytes(shared.x)
        val y2 = Sm2Field.toBytes(shared.y)
        val mask = sm2Kdf(x2 + y2, c2.size)
        val message = ByteArray(c2.size) { (c2[it].toInt() xor mask[it].toInt()).toByte() }
        assertContentEquals(c3, Sm3.digest(x2 + message + y2))
        return message
    }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private companion object {
        const val FIXTURE_MESSAGE = "fixture-Password_123!"
        const val FIXTURE_PRIVATE_KEY = "xvTitqraFE8IaR0A1mRBX7OD024adxJl3Z6tn7UGSlI="
        const val FIXTURE_PUBLIC_KEY =
            "BCNfHMe8JNA+F0xYeDk+8jpQX/u7gIRDbmOvxyzq5cUyaEGp88qjQT78wi2lAzmdPM6OVMEwDiqrRWqeeO+789s="
        const val NODE_CIPHER =
            "UIEP+/t+eev0UNqTlUNIFZzUsaqjQaWmNg7WNOVByPF2hzrndStvuEfJcFqvcv4T8E8AKLVcvt9n6tYkjPrw0kvbNI8rtpRtVfKVv8OUhOPSb0Viq61tu82u2xeJXT1QqnwnhCgIiaF/ThHqUsS2Tsnsg9rn"
    }
}

package team.bjtuss.bjtuselfservice.shared.auth

import kotlin.io.encoding.Base64

/** 平台安全随机数：Android/桌面用 SecureRandom，iOS 用 SecRandomCopyBytes。 */
internal expect fun secureRandomBytes(size: Int): ByteArray

/**
 * 统一身份认证（sso.bjtu.edu.cn）登录页用 sm-crypto 的 `sm2.encrypt(password, publicKey)`
 * 加密密码后再提交。与网页一致：公钥是 base64 的未压缩点（04||x||y），
 * 密文为 C1C3C2（mode=1）、不带 04 前缀、整体 base64。
 *
 * commonMain 没有大数库，这里只实现加密所需的最小 256 位模运算（Montgomery）和
 * Jacobian 坐标点运算。k 为一次性随机数；加密只在登录时执行一次，未做常量时间处理。
 */
internal object SsoSm2 {
    fun encryptPassword(password: String, publicKeyBase64: String): String {
        val key = Base64.decode(publicKeyBase64.trim())
        require(key.size == 65 && key[0] == 0x04.toByte()) { "Unsupported SM2 public key" }
        val publicPoint = Sm2Curve.affine(
            Sm2Field.fromBytes(key.copyOfRange(1, 33)),
            Sm2Field.fromBytes(key.copyOfRange(33, 65)),
        )
        require(Sm2Curve.isOnCurve(publicPoint)) { "SM2 public key is not on curve" }
        return Base64.encode(encrypt(password.encodeToByteArray(), publicPoint, ::randomScalar))
    }

    /** 测试入口：可注入固定 k，便于与 sm-crypto 互相解密校验。 */
    internal fun encrypt(
        message: ByteArray,
        publicPoint: Sm2Point,
        nextScalar: () -> IntArray,
    ): ByteArray {
        repeat(MAX_ATTEMPTS) {
            val k = nextScalar()
            val c1 = Sm2Curve.toAffine(Sm2Curve.multiply(Sm2Curve.G, k)) ?: return@repeat
            val shared = Sm2Curve.toAffine(Sm2Curve.multiply(publicPoint, k)) ?: return@repeat
            val x2 = Sm2Field.toBytes(shared.x)
            val y2 = Sm2Field.toBytes(shared.y)
            val mask = sm2Kdf(x2 + y2, message.size)
            if (message.isNotEmpty() && mask.all { it == 0.toByte() }) return@repeat
            val c2 = ByteArray(message.size) { (message[it].toInt() xor mask[it].toInt()).toByte() }
            val c3 = Sm3.digest(x2 + message + y2)
            return Sm2Field.toBytes(c1.x) + Sm2Field.toBytes(c1.y) + c3 + c2
        }
        error("SM2 encryption failed")
    }

    private fun randomScalar(): IntArray {
        while (true) {
            val candidate = U256.fromBytes(secureRandomBytes(32))
            if (!U256.isZero(candidate) && U256.compare(candidate, Sm2Curve.N) < 0) return candidate
        }
    }

    private const val MAX_ATTEMPTS = 8
}

internal fun sm2Kdf(z: ByteArray, length: Int): ByteArray {
    val output = ByteArray(length)
    var offset = 0
    var counter = 1
    while (offset < length) {
        val block = Sm3.digest(
            z + byteArrayOf(
                (counter ushr 24).toByte(),
                (counter ushr 16).toByte(),
                (counter ushr 8).toByte(),
                counter.toByte(),
            ),
        )
        val count = minOf(block.size, length - offset)
        block.copyInto(output, offset, 0, count)
        offset += count
        counter++
    }
    return output
}

/** 8 个 32 位小端肢体表示的 256 位无符号整数。 */
internal object U256 {
    const val LIMBS = 8

    fun fromHex(hex: String): IntArray {
        val padded = hex.padStart(64, '0')
        return IntArray(LIMBS) { index ->
            val end = 64 - index * 8
            padded.substring(end - 8, end).toLong(16).toInt()
        }
    }

    fun fromBytes(bytes: ByteArray): IntArray {
        require(bytes.size == 32)
        return IntArray(LIMBS) { index ->
            val base = 32 - (index + 1) * 4
            ((bytes[base].toInt() and 0xff) shl 24) or
                ((bytes[base + 1].toInt() and 0xff) shl 16) or
                ((bytes[base + 2].toInt() and 0xff) shl 8) or
                (bytes[base + 3].toInt() and 0xff)
        }
    }

    fun toBytes(value: IntArray): ByteArray = ByteArray(32).also { out ->
        for (index in 0 until LIMBS) {
            val limb = value[index]
            val base = 32 - (index + 1) * 4
            out[base] = (limb ushr 24).toByte()
            out[base + 1] = (limb ushr 16).toByte()
            out[base + 2] = (limb ushr 8).toByte()
            out[base + 3] = limb.toByte()
        }
    }

    fun isZero(value: IntArray): Boolean = value.all { it == 0 }

    fun compare(a: IntArray, b: IntArray): Int {
        for (index in LIMBS - 1 downTo 0) {
            val x = a[index].toUInt()
            val y = b[index].toUInt()
            if (x != y) return if (x < y) -1 else 1
        }
        return 0
    }

    fun bit(value: IntArray, index: Int): Boolean = (value[index / 32] ushr (index % 32)) and 1 == 1

    /** a + b，返回进位。 */
    fun addInto(out: IntArray, a: IntArray, b: IntArray): Long {
        var carry = 0L
        for (index in 0 until LIMBS) {
            val sum = (a[index].toLong() and MASK) + (b[index].toLong() and MASK) + carry
            out[index] = sum.toInt()
            carry = sum ushr 32
        }
        return carry
    }

    /** a - b，返回借位。 */
    fun subInto(out: IntArray, a: IntArray, b: IntArray): Long {
        var borrow = 0L
        for (index in 0 until LIMBS) {
            val diff = (a[index].toLong() and MASK) - (b[index].toLong() and MASK) - borrow
            out[index] = diff.toInt()
            borrow = if (diff < 0) 1L else 0L
        }
        return borrow
    }

    const val MASK = 0xffffffffL
}

/** SM2 素域 Fp 上的 Montgomery 运算；域元素在内部始终处于 Montgomery 形式。 */
internal object Sm2Field {
    val P = U256.fromHex("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFF")

    /** -P^{-1} mod 2^32。 */
    private val N0: Long = run {
        val p0 = P[0].toLong() and U256.MASK
        var inverse = 1L
        repeat(5) { inverse = (inverse * (2 - p0 * inverse)) and U256.MASK }
        (-inverse) and U256.MASK
    }

    /** R^2 mod P，R = 2^256。 */
    private val R2: IntArray = run {
        var value = IntArray(U256.LIMBS).also { it[0] = 1 }
        repeat(512) { value = addPlain(value, value) }
        value
    }

    val ONE: IntArray = toMont(IntArray(U256.LIMBS).also { it[0] = 1 })
    val ZERO: IntArray = IntArray(U256.LIMBS)

    private fun addPlain(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(U256.LIMBS)
        val carry = U256.addInto(out, a, b)
        if (carry != 0L || U256.compare(out, P) >= 0) U256.subInto(out, out, P)
        return out
    }

    fun add(a: IntArray, b: IntArray): IntArray = addPlain(a, b)

    fun sub(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(U256.LIMBS)
        if (U256.subInto(out, a, b) != 0L) U256.addInto(out, out, P)
        return out
    }

    fun mul(a: IntArray, b: IntArray): IntArray {
        val t = LongArray(U256.LIMBS + 2)
        for (i in 0 until U256.LIMBS) {
            var carry = 0L
            val ai = a[i].toLong() and U256.MASK
            for (j in 0 until U256.LIMBS) {
                val product = t[j] + ai * (b[j].toLong() and U256.MASK) + carry
                t[j] = product and U256.MASK
                carry = product ushr 32
            }
            var sum = t[U256.LIMBS] + carry
            t[U256.LIMBS] = sum and U256.MASK
            t[U256.LIMBS + 1] = sum ushr 32

            val m = (t[0] * N0) and U256.MASK
            var reduction = t[0] + m * (P[0].toLong() and U256.MASK)
            carry = reduction ushr 32
            for (j in 1 until U256.LIMBS) {
                reduction = t[j] + m * (P[j].toLong() and U256.MASK) + carry
                t[j - 1] = reduction and U256.MASK
                carry = reduction ushr 32
            }
            sum = t[U256.LIMBS] + carry
            t[U256.LIMBS - 1] = sum and U256.MASK
            t[U256.LIMBS] = t[U256.LIMBS + 1] + (sum ushr 32)
        }
        val out = IntArray(U256.LIMBS) { t[it].toInt() }
        if (t[U256.LIMBS] != 0L || U256.compare(out, P) >= 0) U256.subInto(out, out, P)
        return out
    }

    fun square(a: IntArray): IntArray = mul(a, a)

    fun toMont(a: IntArray): IntArray = mul(a, R2)

    fun fromMont(a: IntArray): IntArray = mul(a, IntArray(U256.LIMBS).also { it[0] = 1 })

    fun fromBytes(bytes: ByteArray): IntArray {
        val value = U256.fromBytes(bytes)
        require(U256.compare(value, P) < 0) { "Field element out of range" }
        return toMont(value)
    }

    fun toBytes(a: IntArray): ByteArray = U256.toBytes(fromMont(a))

    fun isZero(a: IntArray): Boolean = U256.isZero(a)

    fun equal(a: IntArray, b: IntArray): Boolean = a.contentEquals(b)

    /** 费马小定理求逆：a^(p-2)。 */
    fun invert(a: IntArray): IntArray {
        val exponent = IntArray(U256.LIMBS)
        U256.subInto(exponent, P, IntArray(U256.LIMBS).also { it[0] = 2 })
        var result = ONE
        for (bit in 255 downTo 0) {
            result = square(result)
            if (U256.bit(exponent, bit)) result = mul(result, a)
        }
        return result
    }
}

/** Jacobian 坐标点；z = 0 表示无穷远点。 */
internal class Sm2Point(val x: IntArray, val y: IntArray, val z: IntArray) {
    val isInfinity: Boolean get() = Sm2Field.isZero(z)
}

internal class Sm2AffinePoint(val x: IntArray, val y: IntArray)

internal object Sm2Curve {
    private val A = Sm2Field.toMont(U256.fromHex("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFC"))
    private val B = Sm2Field.toMont(U256.fromHex("28E9FA9E9D9F5E344D5A9E4BCF6509A7F39789F515AB8F92DDBCBD414D940E93"))
    val N = U256.fromHex("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFF7203DF6B21C6052B53BBF40939D54123")
    val G = affine(
        Sm2Field.toMont(U256.fromHex("32C4AE2C1F1981195F9904466A39C9948FE30BBFF2660BE1715A4589334C74C7")),
        Sm2Field.toMont(U256.fromHex("BC3736A2F4F6779C59BDCEE36B692153D0A9877CC62A474002DF32E52139F0A0")),
    )
    private val INFINITY = Sm2Point(Sm2Field.ONE, Sm2Field.ONE, Sm2Field.ZERO)

    fun affine(x: IntArray, y: IntArray): Sm2Point = Sm2Point(x, y, Sm2Field.ONE)

    fun isOnCurve(point: Sm2Point): Boolean {
        val affine = toAffine(point) ?: return false
        val left = Sm2Field.square(affine.y)
        val x2 = Sm2Field.square(affine.x)
        val right = Sm2Field.add(
            Sm2Field.add(Sm2Field.mul(x2, affine.x), Sm2Field.mul(A, affine.x)),
            B,
        )
        return Sm2Field.equal(left, right)
    }

    fun toAffine(point: Sm2Point): Sm2AffinePoint? {
        if (point.isInfinity) return null
        val zInv = Sm2Field.invert(point.z)
        val zInv2 = Sm2Field.square(zInv)
        return Sm2AffinePoint(
            Sm2Field.mul(point.x, zInv2),
            Sm2Field.mul(point.y, Sm2Field.mul(zInv2, zInv)),
        )
    }

    fun double(point: Sm2Point): Sm2Point {
        if (point.isInfinity || Sm2Field.isZero(point.y)) return INFINITY
        val y2 = Sm2Field.square(point.y)
        val s = Sm2Field.mul(times(4, point.x), y2)
        val z2 = Sm2Field.square(point.z)
        val x2 = Sm2Field.square(point.x)
        val m = Sm2Field.add(times(3, x2), Sm2Field.mul(A, Sm2Field.square(z2)))
        val x3 = Sm2Field.sub(Sm2Field.square(m), times(2, s))
        val y3 = Sm2Field.sub(Sm2Field.mul(m, Sm2Field.sub(s, x3)), times(8, Sm2Field.square(y2)))
        val z3 = Sm2Field.mul(times(2, point.y), point.z)
        return Sm2Point(x3, y3, z3)
    }

    fun add(p: Sm2Point, q: Sm2Point): Sm2Point {
        if (p.isInfinity) return q
        if (q.isInfinity) return p
        val z1z1 = Sm2Field.square(p.z)
        val z2z2 = Sm2Field.square(q.z)
        val u1 = Sm2Field.mul(p.x, z2z2)
        val u2 = Sm2Field.mul(q.x, z1z1)
        val s1 = Sm2Field.mul(p.y, Sm2Field.mul(q.z, z2z2))
        val s2 = Sm2Field.mul(q.y, Sm2Field.mul(p.z, z1z1))
        if (Sm2Field.equal(u1, u2)) {
            return if (Sm2Field.equal(s1, s2)) double(p) else INFINITY
        }
        val h = Sm2Field.sub(u2, u1)
        val r = Sm2Field.sub(s2, s1)
        val h2 = Sm2Field.square(h)
        val h3 = Sm2Field.mul(h2, h)
        val u1h2 = Sm2Field.mul(u1, h2)
        val x3 = Sm2Field.sub(Sm2Field.sub(Sm2Field.square(r), h3), times(2, u1h2))
        val y3 = Sm2Field.sub(Sm2Field.mul(r, Sm2Field.sub(u1h2, x3)), Sm2Field.mul(s1, h3))
        val z3 = Sm2Field.mul(h, Sm2Field.mul(p.z, q.z))
        return Sm2Point(x3, y3, z3)
    }

    fun multiply(point: Sm2Point, scalar: IntArray): Sm2Point {
        var result = INFINITY
        for (bit in 255 downTo 0) {
            result = double(result)
            if (U256.bit(scalar, bit)) result = add(result, point)
        }
        return result
    }

    private fun times(factor: Int, value: IntArray): IntArray {
        var result = value
        repeat(factor - 1) { result = Sm2Field.add(result, value) }
        return result
    }
}

/** GM/T 0004-2012 SM3 杂凑。 */
internal object Sm3 {
    private val IV = longArrayOf(
        0x7380166fL, 0x4914b2b9L, 0x172442d7L, 0xda8a0600L,
        0xa96f30bcL, 0x163138aaL, 0xe38dee4dL, 0xb0fb0e4eL,
    ).map(Long::toInt).toIntArray()

    fun digest(message: ByteArray): ByteArray {
        val bitLength = message.size.toLong() * 8
        val paddedLength = ((message.size + 9 + 63) / 64) * 64
        val padded = message.copyOf(paddedLength)
        padded[message.size] = 0x80.toByte()
        for (index in 0 until 8) {
            padded[paddedLength - 1 - index] = (bitLength ushr (index * 8)).toByte()
        }
        val v = IV.copyOf()
        val w = IntArray(68)
        val w1 = IntArray(64)
        for (block in 0 until paddedLength / 64) {
            val base = block * 64
            for (index in 0 until 16) {
                val offset = base + index * 4
                w[index] = ((padded[offset].toInt() and 0xff) shl 24) or
                    ((padded[offset + 1].toInt() and 0xff) shl 16) or
                    ((padded[offset + 2].toInt() and 0xff) shl 8) or
                    (padded[offset + 3].toInt() and 0xff)
            }
            for (index in 16 until 68) {
                w[index] = p1(w[index - 16] xor w[index - 9] xor w[index - 3].rotateLeft(15)) xor
                    w[index - 13].rotateLeft(7) xor w[index - 6]
            }
            for (index in 0 until 64) w1[index] = w[index] xor w[index + 4]
            var a = v[0]; var b = v[1]; var c = v[2]; var d = v[3]
            var e = v[4]; var f = v[5]; var g = v[6]; var h = v[7]
            for (j in 0 until 64) {
                val t = if (j < 16) 0x79cc4519 else 0x7a879d8a
                val ss1 = (a.rotateLeft(12) + e + t.rotateLeft(j % 32)).rotateLeft(7)
                val ss2 = ss1 xor a.rotateLeft(12)
                val ff = if (j < 16) a xor b xor c else (a and b) or (a and c) or (b and c)
                val gg = if (j < 16) e xor f xor g else (e and f) or (e.inv() and g)
                val tt1 = ff + d + ss2 + w1[j]
                val tt2 = gg + h + ss1 + w[j]
                d = c
                c = b.rotateLeft(9)
                b = a
                a = tt1
                h = g
                g = f.rotateLeft(19)
                f = e
                e = p0(tt2)
            }
            v[0] = v[0] xor a; v[1] = v[1] xor b; v[2] = v[2] xor c; v[3] = v[3] xor d
            v[4] = v[4] xor e; v[5] = v[5] xor f; v[6] = v[6] xor g; v[7] = v[7] xor h
        }
        return ByteArray(32).also { out ->
            v.forEachIndexed { index, word ->
                out[index * 4] = (word ushr 24).toByte()
                out[index * 4 + 1] = (word ushr 16).toByte()
                out[index * 4 + 2] = (word ushr 8).toByte()
                out[index * 4 + 3] = word.toByte()
            }
        }
    }

    private fun p0(x: Int): Int = x xor x.rotateLeft(9) xor x.rotateLeft(17)

    private fun p1(x: Int): Int = x xor x.rotateLeft(15) xor x.rotateLeft(23)
}

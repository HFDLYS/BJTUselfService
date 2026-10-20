package team.bjtuss.bjtuselfservice.web

import org.bouncycastle.asn1.gm.GMNamedCurves
import org.bouncycastle.crypto.engines.SM2Engine
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.params.ParametersWithRandom
import java.security.SecureRandom
import java.util.Base64

/**
 * SM2 encryption compatible with the school page's wrapper:
 *
 *   sm2.encrypt(password, publicKey)
 *     -> sm-crypto mode C1C3C2
 *     -> outputEncoding base64
 *     -> C1 is x || y without the 0x04 uncompressed-point prefix
 */
object Sm2Crypto {
    @JvmStatic
    fun encrypt(plainText: String, publicKeyBase64: String): String {
        val normalized = publicKeyBase64
            .replace("\\/", "/")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        val keyBytes = Base64.getDecoder().decode(normalized)
        val curve = GMNamedCurves.getByName("sm2p256v1")
        val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)
        val point = curve.curve.decodePoint(keyBytes)
        val publicKey = ECPublicKeyParameters(point, domain)

        val engine = SM2Engine(SM2Engine.Mode.C1C3C2)
        engine.init(true, ParametersWithRandom(publicKey, SecureRandom()))
        val input = plainText.toByteArray(Charsets.UTF_8)
        val encrypted = engine.processBlock(input, 0, input.size)

        val raw = if (encrypted.isNotEmpty() && encrypted[0] == 0x04.toByte()) {
            encrypted.copyOfRange(1, encrypted.size)
        } else {
            encrypted
        }
        return Base64.getEncoder().encodeToString(raw)
    }
}

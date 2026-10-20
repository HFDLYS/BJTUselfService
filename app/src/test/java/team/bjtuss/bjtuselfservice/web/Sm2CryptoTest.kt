package team.bjtuss.bjtuselfservice.web

import org.junit.Assert.assertEquals
import org.junit.Test

class Sm2CryptoTest {
    @Test
    fun encryptMatchesSchoolPageFormat() {
        val publicKey = "BI4oS9hOB37bD49HNCmIz3ij4QUU4AROOLDjDYTlEJu7uQf0O5BPIm0k9Iktf+JhwO/nEZLXZQpUY7vtnC5FmpA="
        val cipher = Sm2Crypto.encrypt("test", publicKey)
        println("CIPHER=$cipher")
        val raw = java.util.Base64.getDecoder().decode(cipher)
        // C1 (x||y, 64 bytes) + C3 (32 bytes) + C2 (4 bytes)
        assertEquals(100, raw.size)

        // The school page embeds the key inside JSON, where "/" is escaped.
        val escaped = publicKey.replace("/", "\\/")
        val escapedCipher = Sm2Crypto.encrypt("test", escaped)
        assertEquals(100, java.util.Base64.getDecoder().decode(escapedCipher).size)
    }
}

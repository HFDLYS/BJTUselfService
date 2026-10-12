package team.bjtuss.bjtuselfservice.shared.feature.home

import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampusCardDestinationTest {
    @Test
    fun onlyAndroidOffersOpeningTheCampusApp() {
        assertTrue(campusCardDestination(PlatformFamily.Android).offersAppLaunch)
        assertFalse(campusCardDestination(PlatformFamily.IOS).offersAppLaunch)
        assertFalse(campusCardDestination(PlatformFamily.MacOS).offersAppLaunch)
    }

    @Test
    fun everyPlatformExplainsScanningAndPastingIntoWeChat() {
        PlatformFamily.entries.forEach { family ->
            val message = campusCardDestination(family).message
            assertTrue("二维码" in message, "$family: $message")
            assertTrue("复制链接" in message, "$family: $message")
            assertFalse("系统默认浏览器" in message, "$family: $message")
        }
        assertTrue("微信" in CAMPUS_CARD_LINK_COPIED)
        assertEquals("https://wxaurl.cn/RLEw5IMZRKl", WECHAT_MINI_PROGRAM_URL)
    }

    @Test
    fun qrMatrixHasStableSquareModulesAndFinderPatterns() {
        assertEquals(31, WECHAT_MINI_PROGRAM_QR_MATRIX.size)
        assertTrue(WECHAT_MINI_PROGRAM_QR_MATRIX.all { row ->
            row.length == 31 && row.all { it == '0' || it == '1' }
        })
        assertEquals("01111111", WECHAT_MINI_PROGRAM_QR_MATRIX[1].take(8))
        assertEquals("01111111", WECHAT_MINI_PROGRAM_QR_MATRIX[7].take(8))
        assertEquals("0000000000000000000000000000000", WECHAT_MINI_PROGRAM_QR_MATRIX.last())
    }

    @Test
    fun networkPaymentQrMatrixMatchesTheUserProvidedVersionFourCode() {
        assertEquals(33, NETWORK_PAYMENT_QR_MATRIX.size)
        assertTrue(NETWORK_PAYMENT_QR_MATRIX.all { row ->
            row.length == 33 && row.all { it == '0' || it == '1' }
        })
        assertEquals("1111111", NETWORK_PAYMENT_QR_MATRIX.first().take(7))
        assertEquals("1111111", NETWORK_PAYMENT_QR_MATRIX.first().takeLast(7))
        assertEquals("1111111", NETWORK_PAYMENT_QR_MATRIX[26].take(7))
    }
}

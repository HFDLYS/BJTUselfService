package team.bjtuss.bjtuselfservice.shared.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals

class QrCodeImageTest {
    @Test
    fun darkModulesAreOffsetByTheQuietZone() {
        val image = QrCodeImage(listOf("10", "01"), quietZone = 2, fileName = "test")
        val rects = mutableListOf<Triple<Int, Int, Int>>()
        image.forEachDarkModule(pixelsPerModule = 10) { left, top, side -> rects += Triple(left, top, side) }

        assertEquals(60, image.pixelSize(10))
        assertEquals(listOf(Triple(20, 20, 10), Triple(30, 30, 10)), rects)
    }

    @Test
    fun savedImagesReuseTheMatricesShownOnScreen() {
        var miniProgramDark = 0
        QrCodeImage(WECHAT_MINI_PROGRAM_QR_MATRIX, 3, "a").forEachDarkModule(1) { _, _, _ -> miniProgramDark++ }
        assertEquals(WECHAT_MINI_PROGRAM_QR_MATRIX.sumOf { row -> row.count { it == '1' } }, miniProgramDark)
        // 31 模块（含 1 模块白边）+ 两侧各 3 模块，与弹窗渲染一致的 4 模块静区。
        assertEquals((31 + 6) * 16, QrCodeImage(WECHAT_MINI_PROGRAM_QR_MATRIX, 3, "a").pixelSize(16))
        assertEquals((33 + 8) * 16, QrCodeImage(NETWORK_PAYMENT_QR_MATRIX, 4, "b").pixelSize(16))
    }
}

package team.bjtuss.bjtuselfservice.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartLinkifierTest {
    @Test
    fun urlStopsBeforeChineseText() {
        assertEquals(
            listOf("https://a.bjtu.edu.cn/path"),
            SmartLinkifier.extractUrls("详见https://a.bjtu.edu.cn/path这是说明"),
        )
    }

    @Test
    fun urlKeepsQueryAndTrimsPunctuation() {
        assertEquals(
            listOf("https://a.bjtu.edu.cn/path?q=1"),
            SmartLinkifier.extractUrls("https://a.bjtu.edu.cn/path?q=1。"),
        )
        assertEquals(
            listOf("https://a.bjtu.edu.cn/a"),
            SmartLinkifier.extractUrls("（https://a.bjtu.edu.cn/a）。"),
        )
    }
}

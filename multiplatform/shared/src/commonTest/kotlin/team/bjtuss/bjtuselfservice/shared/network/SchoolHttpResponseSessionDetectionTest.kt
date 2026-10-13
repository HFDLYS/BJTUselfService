package team.bjtuss.bjtuselfservice.shared.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchoolHttpResponseSessionDetectionTest {
    @Test
    fun sameUrlLoginHtmlIsNotPassedToBusinessParsers() {
        val response = SchoolHttpResponse(
            statusCode = 200,
            finalUrl = "https://smart.bjtu.edu.cn/ve/back/coursePlatform/course.shtml",
            body = """
                <!doctype html>
                <form id="login"><input name="password" type="password"></form>
            """.trimIndent().encodeToByteArray(),
        )

        assertTrue(response.looksLikeSessionExpired())
    }

    @Test
    fun matchingIgnoresCaseAndLeadingWhitespaceWithoutCopyingBody() {
        fun page(body: String) = SchoolHttpResponse(
            statusCode = 200,
            finalUrl = "https://aa.bjtu.edu.cn/score/scores/stu/view/",
            body = body.encodeToByteArray(),
        )

        assertTrue(page("\n  <!DOCTYPE HTML><INPUT TYPE=\"PASSWORD\">").looksLikeSessionExpired())
        assertTrue(page("<p>Session Expired</p>").looksLikeSessionExpired())
        assertTrue(page("<div>请重新登录</div>").looksLikeSessionExpired())
        assertFalse(page("   ").looksLikeSessionExpired())
        assertFalse(page("<!doctype html><table><tr><td>成绩</td></tr></table>").looksLikeSessionExpired())
    }

    @Test
    fun normalApiJsonIsNotClassifiedAsSessionExpiry() {
        val response = SchoolHttpResponse(
            statusCode = 200,
            finalUrl = "https://smart.bjtu.edu.cn/ve/back/coursePlatform/course.shtml",
            body = "{\"rows\":[]}".encodeToByteArray(),
        )

        assertFalse(response.looksLikeSessionExpired())
    }
}

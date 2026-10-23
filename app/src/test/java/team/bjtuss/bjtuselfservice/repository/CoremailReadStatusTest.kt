package team.bjtuss.bjtuselfservice.repository

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CoremailReadStatusTest {
    @Test
    fun countsInboxUnreadRatherThanOtherFoldersOrTotalMessages() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":"S_OK","var":[
                {"id":3,"stats":{"unreadMessageCount":99}},
                {"id":1,"stats":{"unreadMessageCount":2,"messageCount":100}}
            ]}"""))
            assertEquals(2, CoremailReadStatus(OkHttpClient(), server.url("/coremail/s/json")).unreadCount("sid"))
            assertEquals("getAllFolders", server.takeRequest().requestUrl!!.queryParameter("func"))
        }
    }

    @Test
    fun missingInboxStatsMustNotTurnIntoZeroUnread() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":"S_OK","var":[{"id":1}]}"""))
            assertTrue(runCatching {
                CoremailReadStatus(OkHttpClient(), server.url("/coremail/s/json")).unreadCount("sid")
            }.isFailure)
        }
    }

    @Test
    fun sendsWebClientProtocolAndChangesOnlyReadFlag() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":"S_OK"}"""))
            CoremailReadStatus(OkHttpClient(), server.url("/coremail/s/json"))
                .markRead("test-session", "message\"with\\characters")
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("mbox:updateMessageInfos", request.requestUrl!!.queryParameter("func"))
            assertEquals("test-session", request.requestUrl!!.queryParameter("sid"))
            assertTrue(request.getHeader("Content-Type")!!.startsWith("text/x-json"))
            val json = Moshi.Builder().build().adapter(Map::class.java).fromJson(request.body.readUtf8())!!
            assertEquals(listOf("message\"with\\characters"), json["ids"])
            assertEquals(mapOf("flags" to mapOf("read" to true)), json["attrs"])
        }
    }

    @Test
    fun doesNotTreatServerRejectionOrInvalidResponseAsReadSuccess() {
        for (body in listOf("""{"code":"FR_INVALID_REQUEST"}""", "{}", "not json")) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(body))
                val result = runCatching {
                    CoremailReadStatus(OkHttpClient(), server.url("/coremail/s/json")).markRead("sid", "mid")
                }
                assertTrue("Rejected response must fail: $body", result.isFailure)
            }
        }
    }

    @Test
    fun httpFailureDoesNotReportSuccess() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            val result = runCatching {
                CoremailReadStatus(OkHttpClient(), server.url("/coremail/s/json")).markRead("sid", "mid")
            }
            assertTrue(result.exceptionOrNull() is IOException)
        }
    }
}

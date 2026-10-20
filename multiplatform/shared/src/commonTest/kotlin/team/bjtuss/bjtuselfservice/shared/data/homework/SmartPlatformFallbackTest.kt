package team.bjtuss.bjtuselfservice.shared.data.homework

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.network.*
import kotlin.test.*

class SmartPlatformFallbackTest {
    private val legacy = SmartPlatformEndpoint.LegacyHttp
    private val old = "https://mis.bjtu.edu.cn/module/module/28/"
    private val portal = "https://mis.bjtu.edu.cn/module/module/104/"
    private val home = "https://bksy.bjtu.edu.cn/login_introduce_t.html"
    private val bridge = "https://bksycenter.bjtu.edu.cn/NoMasterJumpPage.aspx?URL=jwcZhjx&FPC=page:jwcZhjx"

    @Test fun workingPrimaryNeverUsesFallback() = runBlocking {
        val requests = mutableListOf<String>()
        legacy.openSmartPlatform { requests += it.url; response("${legacy.apiOrigin}/ve/") }
        assertEquals(listOf(old), requests)
    }

    @Test fun casFailureUsesPortalAndFixedJavascriptLanding() = runBlocking {
        val requests = mutableListOf<SchoolHttpRequest>()
        val oauth = "https://bksycenter.bjtu.edu.cn/oauth2.ashx?code=fixture&state=fresh"
        val entry = "${legacy.apiOrigin}/ve?UN=fixture&G=fixture&ID=fixture&U=bksycenter.bjtu.edu.cn&pagecode="
        val bootstrap = "${legacy.apiOrigin}/ve/back/core/main/qx_kl.jsp"
        val index = "${legacy.apiOrigin}/ve/back/core/main/index.shtml?method=index&type=qxkt"
        val landing = "${legacy.apiOrigin}/ve/back/coursePlatform/coursePlatform.shtml?method=toCoursePlatformIndex&sessionId=fixture"
        val routes = mapOf(
            old to redirect(old, "https://cas.bjtu.edu.cn/o/authorize?client_id=old"),
            "https://cas.bjtu.edu.cn/o/authorize?client_id=old" to response("https://cas.bjtu.edu.cn/o/authorize?client_id=old", 404),
            portal to redirect(portal, "http://bksycenter.bjtu.edu.cn/oauth2.ashx?code=fixture&state=fresh"),
            oauth to redirect(oauth, home),
            home to response(home),
            bridge to redirect(bridge, entry),
            entry to redirect(entry, bootstrap),
            bootstrap to response(bootstrap),
            index to redirect(index, landing),
            landing to response(landing),
        )
        val settled = legacy.openSmartPlatform { request ->
            requests += request
            routes[request.url] ?: error("Unexpected fixture request")
        }
        assertEquals(landing, settled.finalUrl)
        assertEquals(routes.keys.toList(), requests.map { it.url })
        assertTrue(requests.all { it.method == SchoolHttpMethod.GET })
        assertFalse(requests.any { it.url.startsWith("http://bksycenter") })
        assertFalse(requests.any { it.headers["Referer"].orEmpty().contains("code=fixture") })
    }

    @Test fun primaryNetworkFailureAlsoFallsBack() = runBlocking {
        val requests = mutableListOf<String>()
        legacy.openSmartPlatform {
            requests += it.url
            when (it.url) {
                old -> error("offline primary")
                portal -> response(home)
                bridge -> response("${legacy.apiOrigin}/ve/")
                else -> error("Unexpected fixture request")
            }
        }
        assertEquals(listOf(old, portal, bridge), requests)
    }

    @Test fun expiredMisDoesNotTryPortal() = runBlocking {
        var requests = 0
        val error = assertFailsWith<SmartHandshakeException> {
            legacy.openSmartPlatform { requests++; response(old, 401) }
        }
        assertEquals(SmartHandshakeFailure.SESSION_EXPIRED, error.reason)
        assertEquals(1, requests)
    }

    @Test fun blankLegacyCasPageAlsoFallsBack() = runBlocking {
        val requested = mutableListOf<String>()
        legacy.openSmartPlatform {
            requested += it.url
            when (it.url) {
                old -> response("https://cas.bjtu.edu.cn/o/authorize?client_id=fixture")
                portal -> response(home)
                bridge -> response("${legacy.apiOrigin}/ve/")
                else -> error("Unexpected fixture request")
            }
        }
        assertEquals(listOf(old, portal, bridge), requested)
    }

    @Test fun portalLoginPageIsSessionExpired() = runBlocking {
        val error = assertFailsWith<SmartHandshakeException> {
            legacy.openSmartPlatform {
                if (it.url == old) response(old, 503) else response("https://sso.bjtu.edu.cn/sso/login")
            }
        }
        assertEquals(SmartHandshakeFailure.SESSION_EXPIRED, error.reason)
    }

    @Test fun httpsPolicyNeverUsesCleartextFallback() = runBlocking {
        var requests = 0
        assertFailsWith<SmartHandshakeException> {
            SmartPlatformEndpoint.VerifiedHttps.openSmartPlatform { requests++; response(old, 503) }
        }
        assertEquals(1, requests)
    }

    @Test fun fallbackRejectsUntrustedRedirectBeforeRequest() = runBlocking {
        for (target in listOf(
            "https://bksycenter.bjtu.edu.cn.evil.test/oauth2.ashx?code=fixture",
            "http://bksycenter.bjtu.edu.cn/admin",
            "https://bksycenter.bjtu.edu.cn:444/oauth2.ashx",
            "https://attacker@bksycenter.bjtu.edu.cn/oauth2.ashx",
            "http://123.121.147.7:1936/ve/",
            "https://bksy.bjtu.edu.cn/other.html",
        )) {
            val requested = mutableListOf<String>()
            assertFailsWith<SmartHandshakeException> {
                legacy.openSmartPlatform {
                    requested += it.url
                    if (it.url == old) response(old, 503) else redirect(portal, target)
                }
            }
            assertEquals(listOf(old, portal), requested)
        }
    }

    @Test fun redirectLoopIsBounded() = runBlocking {
        var requests = 0
        assertFailsWith<SmartHandshakeException> {
            legacy.openSmartPlatform {
                requests++
                if (it.url == old) response(old, 503) else redirect(portal, portal)
            }
        }
        assertEquals(14, requests)
    }

    @Test fun cancellationNeverStartsFallback() = runBlocking {
        var requests = 0
        assertFailsWith<CancellationException> {
            legacy.openSmartPlatform { requests++; throw CancellationException("fixture") }
        }
        assertEquals(1, requests)
    }

    private fun response(url: String, status: Int = 200) = SchoolHttpResponse(status, url)
    private fun redirect(from: String, to: String) = SchoolHttpResponse(302, from, mapOf("Location" to listOf(to)))
}

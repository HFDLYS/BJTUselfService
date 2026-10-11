package team.bjtuss.bjtuselfservice.shared.feature.phydemo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.network.*
import team.bjtuss.bjtuselfservice.shared.security.CredentialVault

class PhysicsDemoModelTest {
    private fun store():CacheStore {
        val driver=JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CacheDatabaseSql.Schema.create(driver)
        return CacheStore(driver)
    }
    @Test fun refreshFailurePreservesCacheAndSuccessfulEmptyClearsWithdrawnBooking()=runBlocking<Unit> {
        val cache=store()
        try {
            val transport=DemoTransport();val vault=DemoVault()
            val model=PhysicsDemoModel("scope-a",cache,vault,PhysicsDemoRemote(transport))
            assertTrue(model.configure("demo-a","fixture-password"))
            assertEquals(1,model.state.value.bookings.size)
            transport.fail=true;model.refresh()
            assertEquals(1,model.state.value.bookings.size)
            assertTrue(model.state.value.failed && model.state.value.fromCache)
            transport.fail=false;transport.empty=true;model.refresh()
            assertTrue(model.state.value.bookings.isEmpty())
            assertTrue(model.state.value.synced)
            assertFalse(model.state.value.failed)
            val restored=PhysicsDemoModel("scope-a",cache,vault,PhysicsDemoRemote(transport))
            restored.initialize()
            assertTrue(restored.state.value.bookings.isEmpty())
            assertTrue(restored.state.value.fromCache)
            assertNotNull(restored.state.value.lastSync)
        } finally { cache.close() }
    }
    @Test fun wrongNewAccountDoesNotReplaceSavedAccountAndSwitchingScopeCannotReadItsCache()=runBlocking<Unit> {
        val cache=store()
        try {
            val transport=DemoTransport();val vault=DemoVault()
            val model=PhysicsDemoModel("scope-a",cache,vault,PhysicsDemoRemote(transport))
            assertTrue(model.configure("demo-a","fixture-password"))
            val old=model.state.value.bookings
            transport.fail=true
            assertFalse(model.configure("demo-b","wrong-password"))
            assertEquals("demo-a",vault.credentials?.username)
            assertEquals(old,model.state.value.bookings)
            val different=PhysicsDemoModel("scope-b",cache,DemoVault(),PhysicsDemoRemote(transport))
            different.initialize()
            assertFalse(different.state.value.configured)
            assertTrue(different.state.value.bookings.isEmpty())
            transport.fail=false;transport.empty=true
            assertTrue(model.configure("demo-b","other-password"))
            assertEquals("demo-b",model.state.value.username)
            assertTrue(model.state.value.bookings.isEmpty())
        } finally { cache.close() }
    }
    @Test fun disabledFeatureNeverQueriesAndClearConfigurationSurvivesRestart()=runBlocking<Unit> {
        val cache=store()
        try {
            val transport=DemoTransport();val vault=DemoVault()
            val model=PhysicsDemoModel("scope-a",cache,vault,PhysicsDemoRemote(transport))
            model.setEnabled(true)
            assertEquals(0,transport.calls)
            assertFalse(model.state.value.enabled)
            assertTrue(model.configure("demo-a","fixture"))
            model.setEnabled(false)
            val calls=transport.calls
            model.refresh()
            assertEquals(calls,transport.calls)
            val restored=PhysicsDemoModel("scope-a",cache,vault,PhysicsDemoRemote(transport))
            restored.initialize()
            assertTrue(restored.state.value.configured)
            assertFalse(restored.state.value.enabled)
            assertEquals(1,restored.state.value.bookings.size)
            restored.clearConfiguration()
            assertNull(vault.credentials)
            assertTrue(restored.state.value.bookings.isEmpty())
            val cleared=PhysicsDemoModel("scope-a",cache,vault,PhysicsDemoRemote(transport))
            cleared.initialize()
            assertFalse(cleared.state.value.configured)
            assertFalse(cleared.state.value.enabled)
            assertEquals("demo-a",cleared.state.value.username)
            assertTrue(cleared.state.value.bookings.isEmpty())
        } finally { cache.close() }
    }
    @Test fun clearingOfflineCachePreservesCredentialsAndFeatureConfiguration()=runBlocking<Unit> {
        val cache=store()
        try {
            val transport=DemoTransport();val vault=DemoVault()
            val model=PhysicsDemoModel("scope-a",cache,vault,PhysicsDemoRemote(transport))
            assertTrue(model.configure("demo-a","fixture"))
            cache.clearAccount("scope-a")
            val restored=PhysicsDemoModel("scope-a",cache,vault,PhysicsDemoRemote(transport))
            restored.initialize()
            assertTrue(restored.state.value.configured && restored.state.value.enabled)
            assertTrue(restored.state.value.bookings.isEmpty())
            assertEquals("fixture",vault.credentials?.password)
            restored.refresh()
            assertEquals(1,restored.state.value.bookings.size)
        } finally { cache.close() }
    }
}

private class DemoVault:CredentialVault {
    var credentials:Credentials?=null
    override suspend fun load()=credentials
    override suspend fun save(credentials:Credentials) {this.credentials=credentials}
    override suspend fun clear() {credentials=null}
}
private class DemoTransport:SchoolHttpTransport {
    var calls=0;var fail=false;var empty=false
    override fun clearSession() {}
    override suspend fun execute(request:SchoolHttpRequest):SchoolHttpResponse {
        calls++
        if(fail) throw SchoolNetworkException("fixture network failure")
        val body=if(request.url.endsWith("appointment/")) {if(empty) demoEmptyFixture else demoBookingFixture()} else demoLoginFixture
        return SchoolHttpResponse(200,request.url,body=body.encodeToByteArray())
    }
}

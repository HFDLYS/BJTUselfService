package team.bjtuss.bjtuselfservice.shared.feature.phydemo

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.network.KtorSchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.schoolHttpEngineFactory

/** Opt-in read-only campus query using existing DPAPI credentials; no secret files or logs. */
class PhysicsDemoLiveTest {
    @Test fun liveLoginQueryAndRepeatQueryInTheKotlinClient()=runBlocking {
        val path=System.getenv("PHYSICS_DEMO_TEST_DPAPI_PATH") ?: return@runBlocking
        val python=System.getenv("PHYSICS_DEMO_TEST_PYTHON") ?: return@runBlocking
        val work=File(path).parentFile.parentFile.parentFile
        val process=ProcessBuilder(python,"-c",
            "import sys;from pathlib import Path;from phydemo_watch import protect;sys.stdout.buffer.write(protect(Path(sys.argv[1]).read_bytes(),True))",path)
            .directory(work).start()
        val raw=process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(10,TimeUnit.SECONDS) && process.exitValue()==0,"Local credential access failed")
        val values=Json.parseToJsonElement(raw).jsonObject
        val credentials=Credentials(values.getValue("username").jsonPrimitive.content,values.getValue("password").jsonPrimitive.content)
        val transport=KtorSchoolHttpTransport(schoolHttpEngineFactory())
        try {
            val remote=PhysicsDemoRemote(transport)
            val first=withTimeout(45_000) {remote.fetch(credentials)}
            val second=withTimeout(45_000) {remote.fetch(credentials)}
            assertEquals(first,second)
            assertTrue(first.all {it.name in physicsDemoNames && it.period in 1..6})
            println("phydemo_read_only_kotlin_verified=true repeat_query_verified=true count=${first.size}")
        } finally {transport.close()}
    }
}

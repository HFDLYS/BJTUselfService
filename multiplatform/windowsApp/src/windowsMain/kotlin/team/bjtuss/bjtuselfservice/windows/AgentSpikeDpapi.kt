package team.bjtuss.bjtuselfservice.windows

import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.auth.Credentials

/** Spike only (feat/ai-agent): DPAPI round trip on a dedicated node, never the real credentials. */
internal fun runWindowsDpapiSpike(): Int = runBlocking {
    val vault = WindowsDpapiCredentialVault(
        service = "team.bjtuss.bjtuselfservice.kmp.agent-spike",
        account = "probe",
    )
    val probe = Credentials("spike-user", "spike-${System.nanoTime()}")
    vault.save(probe)
    val matched = vault.load()?.let { it.username == probe.username && it.password == probe.password } == true
    vault.clear()
    val cleared = vault.load() == null
    println("{\"dpapiRoundTrip\":$matched,\"cleared\":$cleared}")
    if (matched && cleared) 0 else 1
}

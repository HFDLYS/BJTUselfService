package team.bjtuss.bjtuselfservice.shared.agent

import java.security.MessageDigest
import java.security.SecureRandom
import team.bjtuss.bjtuselfservice.shared.security.CredentialVaultOperation
import team.bjtuss.bjtuselfservice.shared.security.MacOsKeychainItem

/**
 * Spike only (feat/ai-agent): checks whether the packaged launcher can serve a headless CLI.
 * Uses a dedicated Keychain service and never touches the app's real secrets.
 */
private const val SPIKE_SERVICE = "team.bjtuss.bjtuselfservice.kmp.agent-spike"
private const val SPIKE_ACCOUNT = "probe"

fun runAgentSpikeProbe(args: List<String>): Int {
    val item = MacOsKeychainItem(SPIKE_SERVICE, SPIKE_ACCOUNT)
    return when (args.firstOrNull()) {
        "info" -> {
            val start = ProcessHandle.current().info().startInstant().orElse(null)
            val elapsed = start?.let { System.currentTimeMillis() - it.toEpochMilli() }
            println(
                "{\"headless\":${java.awt.GraphicsEnvironment.isHeadless()}," +
                    "\"appPath\":\"${System.getProperty("jpackage.app-path").orEmpty()}\"," +
                    "\"pid\":${ProcessHandle.current().pid()}," +
                    "\"msSinceProcessStart\":${elapsed ?: -1}}",
            )
            0
        }
        "keychain-write" -> {
            val nonce = ByteArray(16).also(SecureRandom()::nextBytes)
            item.save(nonce, CredentialVaultOperation.SAVE)
            println("{\"written\":true,\"sha256\":\"${nonce.sha256Prefix()}\"}")
            0
        }
        "keychain-read" -> {
            val bytes = item.load(CredentialVaultOperation.LOAD)
            println("{\"found\":${bytes != null},\"sha256\":\"${bytes?.sha256Prefix().orEmpty()}\"}")
            0
        }
        "keychain-clear" -> {
            item.clear(CredentialVaultOperation.CLEAR)
            println("{\"cleared\":true}")
            0
        }
        else -> {
            System.err.println("usage: agent spike info|keychain-write|keychain-read|keychain-clear")
            64
        }
    }
}

private fun ByteArray.sha256Prefix(): String =
    MessageDigest.getInstance("SHA-256").digest(this).take(6).joinToString("") { "%02x".format(it) }

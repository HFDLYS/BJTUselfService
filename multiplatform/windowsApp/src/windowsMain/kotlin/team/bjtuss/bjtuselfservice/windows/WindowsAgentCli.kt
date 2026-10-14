package team.bjtuss.bjtuselfservice.windows

import java.io.File
import kotlinx.coroutines.runBlocking
import team.bjtuss.bjtuselfservice.shared.agent.AgentCliPlatform

internal fun windowsAgentCliPlatform(): AgentCliPlatform = AgentCliPlatform(
    cacheDirectory = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "BJTUselfServiceKMP"),
    loadCacheKey = ::loadExistingWindowsCacheKey,
    rememberedUsername = { runBlocking { WindowsDpapiCredentialVault().load()?.username } },
    securityStore = ::createWindowsAccountSecurityStore,
    captchaRecognizer = { WindowsTorchCaptchaRecognizer() },
)

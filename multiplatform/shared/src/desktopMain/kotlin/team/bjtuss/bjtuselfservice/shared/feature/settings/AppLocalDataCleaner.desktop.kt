package team.bjtuss.bjtuselfservice.shared.feature.settings

import team.bjtuss.bjtuselfservice.shared.agent.MacUninstallCleaner

actual fun platformLocalDataCleaner(): AppLocalDataCleaner? =
    if (System.getProperty("os.name").startsWith("Mac", true)) MacUninstallCleaner.production() else null

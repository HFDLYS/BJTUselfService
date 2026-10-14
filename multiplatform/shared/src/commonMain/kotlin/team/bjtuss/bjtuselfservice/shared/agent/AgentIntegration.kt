package team.bjtuss.bjtuselfservice.shared.agent

/** 本机 AI Agent 接入（仅桌面端）：把启动脚本和 Skill 写到用户目录。 */
interface AgentIntegration {
    fun status(): AgentIntegrationStatus
    fun install(linkClaude: Boolean = true): AgentInstallResult
    fun uninstall(): AgentInstallResult
}

data class AgentIntegrationStatus(
    /** false：从源码/开发环境运行，拿不到可执行文件路径，不能安装。 */
    val installable: Boolean,
    val installed: Boolean,
    /** 已安装但启动脚本指向的 App 路径与当前不一致（App 被移动或升级到别处），需要重新安装。 */
    val stale: Boolean,
    val installedVersion: String?,
    val skillPath: String,
    val cliPath: String,
    val claude: ClaudeSkillLink,
    val unavailableReason: String? = null,
)

enum class ClaudeSkillLink {
    /** 没有 ~/.claude，不处理。 */
    ABSENT,
    /** ~/.claude/skills 本身就指向 ~/.agents/skills，装一处两边都能用。 */
    SHARED,
    LINKED,
    NOT_LINKED,
}

sealed interface AgentInstallResult {
    data class Done(val message: String) : AgentInstallResult
    data class Failed(val reason: String) : AgentInstallResult
}

/** 桌面端返回实现；移动端没有本机 agent 场景，返回 null。 */
expect fun platformAgentIntegration(): AgentIntegration?

package team.bjtuss.bjtuselfservice.shared.agent

/** 装到 `~/.agents/skills/<name>/` 的技能目录名，也是 SKILL.md 里的 name。 */
const val AGENT_SKILL_NAME = "bjtu-self-service"

/** 设置页展示、用户复制给任意 agent 的一句话。 */
const val AGENT_SETUP_PROMPT = "用 bjtu-self-service 技能看看我这周的课、日程和还没交的作业。"

/**
 * 生成 SKILL.md。[cliCommand] 是能直接在 shell 里执行的命令前缀（已按平台加好引号），
 * 例如 `"/Users/me/.local/bin/bjtu"`。内容只描述只读命令，不含任何账号信息。
 */
fun renderAgentSkill(cliCommand: String, isWindows: Boolean, appVersion: String): String = """
---
name: $AGENT_SKILL_NAME
description: 读取北京交通大学“交大自由行”App 在本机缓存的课表、聚合日程（课程/实验/考试/作业开放与截止）、作业（课程平台、物理在线、CITEL）、考试和 CITEL 任务。用户问“今天/这周有什么课”“有什么作业没交”“下周有什么安排”“某门课的作业”时使用。只读，不需要联网。
---

# 交大自由行（BJTU）本机数据

数据来自用户本机“交大自由行”App 的离线缓存（App $appVersion 安装时生成本文件）。App 不需要开着；但缓存只在用户打开 App 同步时更新。

## 怎么调用

```${if (isWindows) "powershell" else "bash"}
$cliCommand <命令> [选项] --json
```

- 始终加 `--json` 并解析 JSON；不加时输出给人看的 Markdown。
- 退出码：0 成功；2 本机没有缓存或无法确定账号（让用户打开 App 登录并同步一次）；5 用户没允许读取这类数据；64 参数错误（stderr 有用法）。
- 第一次调用可能弹出系统钥匙串授权，请让用户点“始终允许”。

## 命令

| 命令 | 用途 |
|---|---|
| `status` | App 版本、各数据源是否有缓存、当前教学周；先用它判断数据是否可用 |
| `calendar [范围] [筛选]` | **问“安排/日程/今天干什么”时优先用**：课程（含错峰时间）、物理实验、演示实验、考试、作业开放/折扣/截止，按时间排好 |
| `courses [范围] [--course 关键词]` | 只看课程与实验 |
| `homework [--pending] [筛选]` | 作业：课程平台、物理在线、CITEL 三个来源；`--pending` 只看未交且未过期的 |
| `exams` | 考试安排 |
| `citel` | CITEL 任务元数据（题目、截止、提交状态、链接） |
| `grades` | 成绩；默认不开放，返回退出码 5 时告诉用户可在 App 设置“AI 助手”里允许 |

范围：`--today`、`--tomorrow`、`--week`（本周）、`--week N`（第 N 教学周）、`--date YYYY-MM-DD`、`--from YYYY-MM-DD --to YYYY-MM-DD`（最多 180 天）。`calendar` 默认今天起 7 天，`courses` 默认本周。

筛选（可组合；多值用逗号或重复参数）：
- `--source course,phyvlab,citel`：作业来源（课程平台、物理在线、CITEL）。
- `--course 关键词`：课程名包含关键词。
- `--kind class,lab,exam,assignment_open,assignment_discount,assignment_due`：仅 `calendar`。
- `--app-filter`：套用用户在 App 作业页存的课程筛选（默认不套用）。
- `--all`：连同物理实验课里用户没约的实验一起列出。
- 每次 `calendar`/`homework` 的 JSON 里有 `availableFilters`，列出可用的来源、类型和全部课程名；先看它再决定怎么筛，不要猜课程名。

## 解读规则（重要）

1. **零点截止**：北京时间 00:00 截止的作业，算作**前一天 24:00** 截止，与 App 首页一致。JSON 里 `agendaDate`/`dueAgendaDate` 是归属日，`start`/`dueTime` 是实际时刻，`midnightDeadline: true` 标出这类。对用户说“周日晚 24 点前”，不要说“周一还能交”。
2. **时区**：所有时间都是北京时间（`+08:00`），不随电脑时区变化。
3. **物理实验**：物理在线的“物理实验”课把本学期全部实验都列成作业，学生只需交自己在选课系统约上的那几个。默认只列 `enrollment: booked` 的；`unverified` 表示没有约课缓存无法判断，列出来并提醒用户确认；不要把没约的实验当成待做作业。
4. **日期来源**：`academicCalendar: inferred` 表示校历未缓存、日期按当前周推算，可能有偏差，提醒用户打开 App 同步一次。
5. **数据新旧**：缓存可能不是最新的。用户问到“刚布置的”“刚交的”之类时，提醒以 App 或学校系统为准，必要时让用户打开 App 刷新。
6. **CITEL 编程题**：目前只有元数据（标题、截止、状态、链接），没有题面；需要题面时给出 `url` 让用户打开。

## 边界

- 只读。没有提交作业、交代码、发邮件、改设置的命令；不要尝试用其它方式代替用户提交。
- 输出里的课程、作业属于用户个人信息：只在本次对话中用于回答用户，不要写入代码仓库、公开文档或发给第三方。
- 不要读取 App 的数据库文件或系统钥匙串；只通过上面的命令获取数据。
""".trimStart()

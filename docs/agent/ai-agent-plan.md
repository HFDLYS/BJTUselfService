# AI Agent 接入计划（Mac / Windows）

## 背景

目标是让本机的 AI agent（Claude Code、Codex、Cursor 等能跑终端命令的 agent）读取交大自由行里的课表、作业、考试、CITEL 题目等数据；App 关着也能读缓存（cache-read），需要时刷新（cache-update）。在 Mac 上还要能做卸载前的清理。另外 CITEL 编程题的完整题面和测试用例要能导出成 Markdown，用户可以保存，agent 也能读。

**基底**：现在的 `main`（1f011bc7）还不是 Candidate3。Candidate3 在 `release/1.8.4`（adaf5e75），比 main 多 3 个提交。新分支从 `release/1.8.4` 开出 `feat/ai-agent`；等 release 合进 main 以后再 rebase 回 main。

**形态：Skill + CLI，这次不做 MCP。**
- 能跑 shell 的 agent 直接调 CLI 就够了。Skill 告诉 agent 有哪些命令、什么时候用、输出字段是什么意思。
- 这样只要维护一份实现、一种协议，测试也简单：命令行输入，JSON 输出。
- MCP 只对不能跑命令的纯聊天客户端有用（Claude Desktop 聊天、Cherry Studio）。以后需要的话加一个 `bjtu mcp` 子命令，用 stdio 包同一套命令，不在本计划内。
- Skill 装到 `~/.agents/skills/bjtu-self-service/`。可选再给 `~/.claude/skills/` 建一个软链接，因为 Claude Code 只读这个目录。

**凭据（用户已选）**：CLI 跟 App 用同一个 Keychain/DPAPI 条目自动登录，验证码交给现有的 Core ML / PyTorch 模型识别。凭据不出 CLI 进程，agent 只拿到数据。

## 已确认的关键事实

- **桌面缓存是加密的**：`shared/src/desktopMain/.../cache/DesktopCacheStore.kt` 用 Keychain 里的 `cache-key` 做 AES 加密。所以只读缓存也得先拿到 Keychain 密钥。
- **现有缓存打开逻辑对 CLI 有风险**：密钥不存在时，`createDesktopCacheStore` 会新建密钥并删掉旧缓存（`cacheKeyCreated` → `deleteDesktopCacheFiles`）。CLI 绝不能走这条路，必须另写一个只读打开：缺密钥就报错退出。
- **Keychain ACL 认的是 App 的二进制**：`MacOsSecretBag.kt` 的注释写明 ACL 绑定 CDHash。如果 CLI 用 bundle 里的 `java` 直接起，会弹授权框。所以 macOS 上 CLI 要通过 App 自己的启动器调用，即 `交大自由行 KMP.app/Contents/MacOS/<launcher> agent …`。这条路已有先例：`desktopApp/.../Main.kt:57` 的 `--verify-captcha-model=` 就是不开窗口、直接往 stdout 打印。
- **Windows 用的是 DPAPI**：它绑定用户而不是二进制，可以直接用 `runtime\bin\java.exe` 调 CLI，避开 GUI 子系统 exe 的 stdout 问题。缓存在 `windowsApp/.../WindowsCacheStore.kt`，位于 `%LOCALAPPDATA%`。
- **打开失败时会删库**：`openCacheStoreWithRecovery()`（`CacheStore.kt:562`）在第一次打开或探测出错时会整库删除。App 持有锁时 CLI 很可能碰上这种情况，所以 CLI 要自己建只读 JDBC 连接（`SQLiteConfig.setReadOnly(true)` 加 `busy_timeout`），不建表、不迁移。schema 版本（当前是 3）对不上时，提示“请先打开 App 完成升级”。值的解密复用 `JvmAesCacheValueProtector`，读取逻辑复用 `CacheStore` 的查询，只是把 driver 换成只读的。
- **Cookie 只存在内存里**（`KtorSchoolHttpTransport.kt:43`），所以每次 `cache update` 都要重新登录。后台登录可以直接复用 `SchoolSessionRecovery.attempt()` 和 `AutomaticLoginCoordinator`（最多 3 次，失败返回 `ManualRequired`），这两个都不依赖 UI。会话过期重试用 `SessionRefreshCoordinator`。
- **同步编排写在 UI 里**：现在是 `AuthenticatedAppShell.kt:920-1045` 里的一组 `LaunchedEffect`。先跑成绩，再并行跑作业、考试、课表，最后是 CITEL、物理等。CLI 要按同样的顺序直接调各 Repository 的 `refresh()`。
- **Repository 都在一个 Compose 函数里装配**：`feature/shell/AuthenticatedSessionFactory.kt` 的 `rememberAuthenticatedSession`（@Composable）。各个 Repository 本身是普通类（`DefaultCourseScheduleRepository(accountScope, CacheStore…LocalDataSource, School…RemoteDataSource)`），可以在 Compose 之外直接构造。
- **缓存表**：`Cache.sq` 里有 grade/course/exam/homework 的缓存，外加 `cache_metadata`（CITEL 任务以 JSON 存在 `citel.tasks` 这个 key 下）和 `app_setting`。
- **已有全量清除**：`SettingsScreenModel.clearAllLocalData()` 里有 `wipeAllLocalData`，注入位置在 `AuthenticatedSessionFactory.kt:220`。设置页只在 macOS 上显示“清除全部本地数据”按钮。
- **CITEL 现状**：`CitelRemote.fetch()` 已经逐个 GET 了 `mod/programming/view.php`，但 `parseCitelTaskDetail` 只留下日期、折扣、成绩，题面被丢掉了。`result.php` 每行的第 4–7 格（输入、期望输出、实际输出、stderr，里面可能套表格）也被故意跳过。题面在页面上的位置、提交前是否能看到样例、有没有预置代码，目前都没有取证。

## 方案

### CLI 命令（名称 `bjtu`，输出加 `--json`；不加时输出给人看的 Markdown）

| 命令 | 联网 | 说明 |
|---|---|---|
| `bjtu status` | 否 | App 版本、当前账号（只给脱敏学号）、各数据源上次同步时间 |
| `bjtu cache read <courses\|homework\|exams\|grades\|citel> [过滤参数]` | 否 | 只读缓存。别名：`bjtu courses --today/--week N/--date`、`bjtu homework --pending` |
| `bjtu calendar [--today\|--week [N]\|--from D --to D] [--ics <文件>]` | 否 | **聚合日程**：把所有带时间的东西排进同一条按时间排序的时间线（见下） |
| `bjtu homework [--pending] [--source course\|phyvlab\|citel] [--all]` | 否 | **三个来源聚合**：课程平台、物理在线、CITEL（见下） |
| `bjtu cache update [数据源…]` | 是 | 后台登录后同步，返回逐项成功/失败；部分失败时保留旧缓存 |
| `bjtu citel show <任务id>` / `bjtu citel export <id\|--all> --out <目录>` | 按需 | 题面和测试用例的 Markdown（见下） |
| `bjtu agent install\|uninstall\|doctor` | 否 | 安装/移除 Skill 与启动脚本，检查环境 |
| `bjtu uninstall [--yes]`（仅 macOS） | 否 | 卸载清理，默认 dry-run |

**`homework`：三个来源聚合**
- 直接复用 `feature/assignment/AggregatedAssignments.kt` 的 `aggregateAssignments(homework, physical, citel)` 和 `filterAggregateAssignments`，跟 App 里“作业”聚合页的结果保持一致。每条都带 `source` 字段。
- **物理在线只算用户选了的课**：`phyVlabModel.allActivities()` 会把平台上能看到的全部实验都带上，但用户真正要交的只是自己选了课的那部分。处理分两层：
  1. 默认套用用户在 App 里已经存下的按来源课程筛选（`AggregateAssignmentFilterStore`，即 `cache_metadata` 里的 `assignment.aggregate.filters`）。`--all` 可以忽略这层筛选。
  2. Spike 阶段要取证“已选课”怎么认：看物理在线是否区分“我的课程”和全部课程，或者能否跟 `course_cache` 里的课名或课程号对上。确认后写成纯函数 `phyVlabEnrolledActivities(...)`。App 首页和聚合页也换成这个函数，跟 CLI 用同一规则，避免列出跟用户无关的实验。没有证据之前，CLI 输出里给这些条目标 `enrollmentUnverified`，不猜测。

**`calendar`：聚合日程**
- 时间线的来源（都只读缓存）：
  - 课表：用 `course_current_week` 加今天的日期推出每周的日期，节次换成具体时间时套用 `StaggeredClassTime` 的错峰规则。复用小组件那份 `CourseWidgetModel` 的“某天课程”计算，保证跟小组件、首页一致。
  - 考试：`exam_cache`。
  - 三个来源作业的截止时间，同上面的聚合和已选课规则。
  - 物理在线日程事件：`phyvlab_snapshot_v1`。
  - CITEL：`citelAgendaEvents`。
  - 物理实验和演示实验的预约：`physicslab.results`、`phydemo.results`。
- 统一的模型 `AgentCalendarItem(kind, title, start, end?, location?, source, courseName?, done?)`，放在 commonMain，是纯函数，有单测。
- `--ics <文件>` 复用 `generateAcademicCalendarIcs`。这个函数需要 `academicWeeks`（周次对应的日期），而数据库里没有缓存，所以同样由当前周推算。推算规则跟课表共用一份，并补测试覆盖跨学期和非教学周。
- Skill 里写明：问“今天/这周有什么安排”时优先用 `calendar`，只问课表时才用 `courses`。

约束：
- 不提供任何写学校数据的命令（交作业、CITEL 提交、发邮件）。Skill 里也写明这一点。
- 输出里不出现密码、Cookie、令牌；不接受 `--password` 参数。
- 退出码：0 成功；2 没有缓存或没登录过；3 登录失败；4 部分同步失败。
- JSON 带 `schemaVersion: 1`。

### 代码结构

- `shared/src/commonMain/.../agent/`
  - `AgentDataGraph.kt`：在 Compose 之外构造各个 Repository。把 `rememberAuthenticatedSession` 里的构造抽成普通工厂函数，两边共用，避免重复装配。
  - `AgentCommand.kt`：参数解析和分派，纯逻辑。
  - `AgentDto.kt`：`@Serializable` 输出模型。
  - `AgentMarkdown.kt`：给人看的格式。
- `shared/src/desktopMain/.../agent/AgentCli.kt`：stdout、退出码和文件写入。实施前要确认 windowsApp 的 jvm target 能用到 shared 的 JVM 源集；用不到就把入口放进各自 app 模块。
- `desktopApp/.../Main.kt`、`windowsApp/.../Main.kt`：在 `main` 最前面判断，`args[0] == "agent"` 时先设 `java.awt.headless=true`（不出 Dock 图标），再交给 CLI，跳过 Compose。
- `DesktopCacheStore.kt` / `WindowsCacheStore.kt`：新增 `openExistingCacheStoreReadOnly`。只“加载”密钥（`loadOrCreate…CacheKey` 拆出一个只加载、不创建的版本），绝不新建密钥、不重置、不走 recovery 删库。`cache update` 需要写库时，在拿到文件锁之后用正常的读写连接，但同样不走“新建密钥”的分支。
- macOS 的验证码 helper 和模型在 `.app/Contents/Resources/Captcha/` 下，CLI 经由 App 启动器运行，所以能找到。从源码运行时用 `bjtu.captcha.helper` / `bjtu.captcha.model` 系统属性指定。
- 并发：在 Application Support 下放一个 `sync.lock` 文件锁（`FileChannel.tryLock`）。App 同步和 `cache update` 都要先拿到锁；SQLite 设 `busy_timeout`。已知风险：CLI 登录智慧教学可能把 App 的会话顶掉（memory 里已有同类痛点）。CLI 在 App 正在运行时提示这一点，不阻止。

### “一句话设定”（PR B）

- 设置页（只在 macOS/Windows 显示）新增“AI 助手”区块，一个按钮“安装到本机 Agent”，它会：
  1. 写启动脚本，内含当前 App 的绝对路径：macOS 写到 `~/.local/bin/bjtu`，Windows 写到 `%LOCALAPPDATA%\BJTUselfServiceKMP\bin\bjtu.cmd`。不改 PATH，因为 Skill 里写的是绝对路径。
  2. 写 Skill：`~/.agents/skills/bjtu-self-service/SKILL.md` 和 `references/commands.md`，从 App 内置模板填入路径。
  3. 可选勾选“同时链接到 `~/.claude/skills`”。
  4. 显示一句可复制的话，粘贴给任意 agent 即可，例如：“用 bjtu-self-service 技能看看我这周的课和没交的作业”。
- **数据范围开关**存在 `app_setting` 里：课表/作业/考试/CITEL 默认允许，成绩默认关闭。CLI 遵守这些开关。安装弹窗要写清楚：数据会发给 agent 背后的模型服务商。
- `bjtu agent install` 做的事情跟按钮相同，给没打开 UI 的情况用。App 升级或挪位置后，`doctor` 能检测出路径失效。

### CITEL 题面与测试用例 Markdown（PR D）

1. **取证（不合入）**：用户在 Chrome 登录 CITEL 后，用 CDP 只读查看一道编程题的 `view.php`、`submit.php`、`result.php` 结构（跟 2026-10-07 的调研方式一样）。确认题面选择器、样例在哪、预置代码、图片、Secure test 的表现。不推测接口。
2. **解析**：
   - 新增 `parseCitelProblem(html)`，得到 `CitelProblem(title, statementBlocks, samples, timeLimit, memoryLimit, languages, presetCode?)`。复用 `util/SchoolRichText.kt` 和 `PhyVlabHtmlParser.kt:250` 里 Moodle 简介的选择器。
   - 扩展 `parseCitelProgrammingTestResult`，读出第 4–7 格并处理嵌套表格。Secure test 标成“隐藏用例”，不编造内容。
   - 解析都放在 `parseOffMain` 里。题面直接从 `fetch()` 已经下载的 `view.php` 里一起解析，不增加请求；`result.php` 只在需要时请求。
3. **缓存**：存进 `cache_metadata`，key 为 `citel.problem.<id>`（走加密 protector）。不改 schema，这样 cache-read 能离线读。
4. **Markdown**：新增 `feature/citel/CitelProblemMarkdown.kt`（纯函数），内容依次为：元数据、题面、样例（输入/输出代码块）、各测试用例（输入、期望输出、我的输出、结果、耗时）。导出整个文件夹时生成 `problem.md`、`tests/1.in`、`tests/1.out` 和 `assets/` 下的图片，agent 可以直接拿来本地跑测试。
5. **UI**：`CitelScreen.kt` 的 `CitelDetailWorkspace` 加“复制 / 保存为 Markdown / 导出文件夹”。复用 `HomeworkFileGateway.saveFile` 和 `CoursewareDirectoryGateway`，四端都能用。
6. 修改 `AssignmentMarkdown.kt` 里 CITEL 导出“只给原链接”的旧决定：有已缓存的题面就写进去，没有就保留原链接。

### Mac 卸载清理（PR E）

- `bjtu uninstall` 默认 dry-run，只列出将要删除的项目；加 `--yes` 才执行。设置页现有的“清除全部本地数据”复用同一个清理器 `desktopApp/.../MacUninstallCleaner.kt`。
- 清理范围：
  - `~/Library/Application Support/BJTUselfServiceKMP`
  - 本应用自己的 `java.util.prefs` 节点。用 `node.removeNode()` 删除，不删共享的 `com.apple.java.util.prefs.plist`。
  - Keychain 里 `MACOS_SECRET_BAG_SERVICE` 和所有 `LEGACY_*` 服务
  - Caches/Logs 下的本应用目录（如果有）
  - `~/.local/bin/bjtu`、`~/.agents/skills/bjtu-self-service` 和 `~/.claude/skills` 下的链接
- `.app` 本体只通过 Finder 移到废纸篓（可以恢复），不做永久删除。

## 分阶段（每阶段一个 PR，合并由用户决定）

0. **开分支**：`git switch -c feat/ai-agent release/1.8.4`（`release/1.8.4` 跟 `mine/release/1.8.4` 一致，都是 adaf5e75）。把这份计划存成 `docs/agent/ai-agent-plan.md`，并在 `memory.md` 的“进行中”里登记。
   **Spike（不合入）**
   - 用打包后的 `.app` 跑 `Contents/MacOS/<launcher> agent status`，验证：管道 stdout 正常、没有 Dock 图标、Keychain 不弹框、冷启动耗时。
   - 验证 Windows 上 `java.exe` 加 classpath 的调用方式（只能在 CI 或用户的 Windows 机器上）。
   - 验证 Claude Code / Codex 是否读取 `~/.agents/skills`。
   - 做 CITEL 页面取证。
   - 物理在线“已选课”怎么识别的取证（真实缓存数据只在本机内存里看，不落盘、不截图）。
1. **PR A**：`AgentDataGraph`、只读缓存打开、`status` / `cache read` 和各别名、三个来源聚合的 `homework`、`calendar`（含 `--ics`）、JSON v1。物理在线已选课规则如果需要改 App 首页和聚合页，单独拆成一个小 PR 先合，CLI 再复用。
2. **PR B**：Skill 模板、启动脚本、`agent install|uninstall|doctor`、设置页“AI 助手”和数据范围开关。
3. **PR C**：`cache update`（后台登录、验证码最多自动识别 3 次、文件锁、部分失败的报告）。
4. **PR D**：CITEL 题面和测试用例解析、Markdown 导出（UI 和 CLI）。
5. **PR E**：Mac `bjtu uninstall` 清理。

## 验证

- **单测**：`./gradlew :shared:desktopTest`。新增的测试覆盖：命令解析、JSON 快照、`calendar` 聚合（各来源合并排序、错峰时间、跨周、非教学周、某个来源为空或损坏时其余照常输出）、`homework` 聚合（套用已存筛选、`--all`、物理在线未选课的条目被排除或标为 unverified）、只读打开（缺密钥时报错且不删文件）、范围开关、CITEL 题面和嵌套表格解析（虚构 fixture：正常、字段缺失、Secure test、登录过期）、Markdown 渲染、清理器（临时 HOME 加测试用 Keychain service，不碰真实数据）。
- **编译**：`:androidApp:assembleDebug`、`:desktopApp:compileKotlin`、`:windowsApp:compileKotlinWindows`、`:shared:compileKotlinIos{SimulatorArm64,Arm64}`。CITEL UI 改了共享代码，iOS 还要跑签名模拟器 xcodebuild。
- **端到端（macOS）**：
  - `createDistributable` 之后，在 bash 里用管道跑 `… agent status --json | jq` 和 `cache read courses --json`。用 Computer Use 截图确认没有 Dock 图标、没有 Keychain 弹框。
  - 按 AGENTS.md 已有授权，用 `MisSecret.md` 跑一次 `cache update` 的真实登录。
  - 在全新的 Claude Code / Codex 会话里问“我今天有什么课”，确认 agent 自己调用了 `bjtu`。
  - 全程只启动一个源码实例，不碰 `/Applications` 里的安装版。
- **卸载**：只在临时 HOME 里真删；真机只跑 dry-run。
- **未验证边界照实写**：Windows 真机（只有 CI 编译）、Secure test 的真实表现、其他 agent 客户端的兼容性。
- 每个阶段结束都更新 `memory.md` 的“进行中”。

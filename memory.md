# BJTUselfService 实时工作记忆

> 最后更新：2026-10-14。
> 当前分支：`feat/ai-agent-alpha-local`（基于本机 Candidate3 打包分支整合 PR A–E，未提交本地改动）。PR #21/#22/#24/#25/#26 仍未合并，#24/#25/#26 CI 全绿；本地 main 已与 `mine/main@1f011bc7` 对齐。
> KMP 本地应用版本：`1.8.5-AgentAlpha` / Build 38（macOS/Windows 数值版本 1.8.5）。已验证并替换 /Applications 安装版，程序已启动；Skill 已更新为 bjtuss、旧名已清理。正式 Latest 仍为 `v1.8.3-KMP`。
> 冻结原 Android：`v1.7.1` / versionCode 9（对照 `origin/main@7d94f74`）。
> 已完成工作的细节见 `history_full.md` 末尾日志。

## 进行中

- 本地 Agent Alpha 更新（2026-10-14）：基于 `feat/ai-agent` Candidate3，squash 整合 `feat/ai-agent-uninstall` A–E；保留原有 1.8.4 引导行为。统一为 `1.8.5-AgentAlpha` / Build 38；构建、/Applications 可恢复替换和新版 Skill 安装验证通过。不提交、不推送、不发布 Release。
  - 验证：完整 Gradle 矩阵含 `:shared:desktopTest :androidApp:assembleDebug :desktopApp:compileKotlin :windowsApp:compileKotlinWindows :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :desktopApp:packageDmg` 成功；890 测试零失败。签名 arm64 模拟器 xcodebuild `BUILD SUCCEEDED`。打包/安装 App 的 codesign --verify --deep --strict 通过，数值版本 1.8.5 / Build 38。
  - 安装验证：新 `/Applications/交大自由行 KMP.app` 已启动，安装版 `agent status --json` 退出 0，version=1.8.5-AgentAlpha，缓存读取成功；经 `~/.local/bin/bjtu help` 调用退出 0，含 CITEL show/export、cache update、uninstall-app。
  - Skill：新版安装器安装 `~/.agents/skills/bjtuss`（Claude 根目录共享同一软链接）；旧 `.agents/.claude` 下 `bjtu-self-service` 均不存在。Skill 内版本和全文/卸载命令已更新；安装版 doctor 退出 0，显示已安装 1.8.5-AgentAlpha、Claude 共用、缓存可读。未清账号、缓存或钥匙串；独立 Keychain 测试已随 desktopTest 通过。
  - 回退：旧 App 备份在 `.artifacts/app-backups/2026-10-14-211010/交大自由行 KMP.app`；测试/构建记录在 `.artifacts/agent-alpha-*`。未运行新版本的真实完整卸载、未安装 Android/iOS/Windows 包。

- AI Agent 接入（Skill + CLI，不做 MCP）：计划见 `docs/agent/ai-agent-plan.md`，Spike 记录见 `docs/agent/spike-2026-10-14.md`。PR A 在 `feat/ai-agent-cli`（从 main 开出，cherry-pick 自 `feat/ai-agent`；后者基于未合入的 `release/1.8.4`，保留不动）。
  - PR A 内容：`<App> agent <命令> [--json]`，macOS 和 Windows 一样，提供 `status`、`calendar`、`courses`、`homework`、`exams`、`grades`、`citel` 和 `cache read`。
    - `calendar` 聚合课程（含错峰）、物理实验、演示实验、考试，以及三个来源作业的开放/折扣/截止。
    - `homework` 聚合课程平台、物理在线、CITEL；物理实验课按约课结果过滤，标题去掉序号和“（难度系数…）”后匹配，未约的实验默认隐藏。
    - 成绩默认不开放（开关 `agent.scope.grades`）；学号脱敏；参数先于缓存读取校验。
    - 缓存只读打开，不新建密钥、不迁移、不删库，schema 版本不符时提示先打开 App。
    - App 新增写入 `academic_weeks_v1`，CLI 离线换算日期用；没有它时按当前周推算，并标 `inferred`。
  - 已验证：`:shared:desktopTest` 全过（新增 22 项）；四端编译通过；打包后的 macOS 启动器在临时 HOME 下跑 help/status/参数错误；Windows CI 冒烟测试全绿（GUI exe 经重定向句柄输出，DPAPI 正常）。
  - 真实数据已验证（2026-10-14）：把 `feat/ai-agent` 打包成 1.8.4-Candidate3 替换了 `/Applications` 里的 1.8.3（旧版在废纸篓）。用户打开新版、在钥匙串里允许后，用安装版启动器跑 `status`、`calendar --week`、`homework --pending` 都没再弹窗。错峰时间正确；物理实验 16 项里只留下约上的 4 项（含“超声专题”“声源定位的GPS模拟”），12 项没约的都过滤掉了；校历缓存已写入（日期不再是推算）。
  - 用户决定（2026-10-14）：零点截止与首页一致，归到前一天 24:00，JSON 给出 `agendaDate`、`midnightDeadline` 和 `notes` 提醒 agent；筛选交给 agent 自己做，默认不再套用 App 作业页的筛选（`--app-filter` 可以显式套用），提供 `--source`、`--course`、`--kind`，并输出 `availableFilters`；`--all` 只表示连同没约的实验一起列出。
  - 未验证：零点截止和筛选这次改动还没在真实数据上跑（安装版是改动前打的包）；App 首页和作业页还没用上“未约实验”过滤。
  - 本轮本地打包已将 macOS `packageBuildVersion` 与四端 Build 统一为 38。
  - PR B（`feat/ai-agent-skill`，叠在 PR A 上）：设置页“AI 助手”卡片，只在桌面端显示。功能有：一键安装、重新安装、移除；“允许读取成绩”开关（`AppPreferences.agentGradesAllowed`，默认关）；一句可复制的话，粘贴给 agent 即可用。命令行新增 `agent install [--no-claude]`、`uninstall`、`doctor`。
    - 安装位置：Skill 当前写到 `~/.agents/skills/bjtuss/SKILL.md`，旧 `bjtu-self-service` 仅清理带标记的本 App 文件。启动脚本在 macOS 是 `~/.local/bin/bjtu`，在 Windows 是 `%LOCALAPPDATA%\BJTUselfServiceKMP\bin\bjtu.cmd`，按系统代码页写；Skill 里写的是脚本的绝对路径，所以不用改 PATH。
    - 只动自己写的文件：脚本里带标记注释，Skill 目录里带标记文件。目标位置已有别人的同名文件时拒绝覆盖，卸载也只删带标记的。
    - Claude Code：`~/.claude/skills` 本身就是指向 `~/.agents/skills` 的软链接时不再另建；是普通目录时建软链接（Windows 建不了就复制一份）；同名位置被占用时不动。
    - 已验证：`:shared:desktopTest` 853 项全过（安装器 8 项都在临时 HOME 里跑，包括真的执行生成的脚本、检查参数转发）；四端编译通过。Windows CI 补了 install → 经 `bjtu.cmd` 调用 → uninstall 的检查。
    - 已验证（2026-10-14）：打包替换本机 App 后，用户在设置页一键安装成功，卡片深浅色都正常；用户新开 Codex 会话贴那句话，Codex 自己找到 `~/.agents/skills` 下的 Skill，调用了命令，正确答出本周课表、零点截止（按前一天 24 点说）、物理实验只算约上的 4 项、缓存时间。“允许读取成绩”是用户自己打开的。
    - 追加（同一 PR）：Skill 改名为 `bjtuss`（安装时清掉带标记的旧目录 `bjtu-self-service`）。课表、作业、考试、成绩同步成功后写 `synced_at_v1.<source>`（仓库的 `onSynced` 回调）；输出顶层加 `freshness`（超过 4 小时的进 `stale`，没有记录的进 `unknown`），Markdown 顶部加提示；Skill 规定 `stale` 不为空时先问用户要不要刷新。CITEL 输出加 `judgeStatus`，避免把“交了但没全对”说成“没交”。
  - App 零点截止显示的独立 PR #23 尚未合入；本轮 Alpha 保持已有 App 界面显示机制，CLI 的归日说明已包含。
  - PR C（`feat/ai-agent-sync`，叠在 PR B 上）：`cache update [grades,courses,homework,exams]`，用 App 记住的账号后台登录并刷新，顺序与 App 启动同步一致：成绩 → 课表（含校历和当前周）→ 作业 → 考试。
    - 复用仓库、`onSynced` 和首页变动记录，所以 App 的“变动记录”不会漏掉命令行同步进来的变化。登录流程与 `SchoolSessionRecovery` 相同，但保留失败原因。
    - 安全：
      - App 运行时持有 `app-running.lock`。命令行拿不到这把锁就退出码 6，让用户在 App 里刷新，避免两边各登录一次互相顶掉会话。
      - 写库前先拿锁；不建表、不迁移、不删库。
      - 只读“记住的密码”：不调用会清除凭据的 `AccountSecurityCoordinator.restore()`。记住的账号与缓存账号不一致时拒绝。
    - 退出码：3 登录失败，4 部分失败，6 App 在运行。Skill 规定：数据超过 4 小时时先问用户；同意后 App 关着就跑 `cache update`，App 开着就请用户在 App 里刷新；物理在线、CITEL 等只能在 App 里刷新。
    - 已验证：`:shared:desktopTest` 865 项全过（本轮新增参数拒绝、失败后释放锁与缓存保留回归）（编排、锁、四道关卡，其中包括“未记住密码时绝不清除凭据”）；四端编译通过。
    - 2026-10-14 追加验证：`:shared:desktopTest :androidApp:assembleDebug :desktopApp:compileKotlin :windowsApp:compileKotlinWindows :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :desktopApp:createDistributable` 成功；865 测试零失败。打包源码启动器真实 `agent cache update --json` 退出 0，login=ok，grades/courses/homework/exams 全 ok，academicCalendar=updated；未替换 /Applications 安装版；随后 status 的 freshness.stale 与 unknown 均为空。
    - 未验证：Windows 端验证码模型在 CLI 路径下加载；Windows 真实网络刷新。
  - PR D #25（`feat/ai-agent-citel`，基于 #24）：按需获取编程题全文与公开/结果页用例，写入加密 `citel.problem.<模块id>`（校验 CITEL 用户名，切换账号不复用旧题面）。普通同步/打开详情不下载 I/O；详情页可获取、复制、保存 Markdown、导出文件夹；聚合作业导出带缓存全文。CLI `citel show <id> [--fetch] [--json]` / `citel export <id> --out <新目录> [--fetch]`，默认离线，联网前拿 App 锁；导出暂存后移动，拒绝覆盖已有目录。
    - 2026-10-14 CDP 复核：题目页 4 行 ×5 格，结果页 6 行 ×13 格，I/O text/plain UTF-8 CRLF；结果页链接无 showasplaintext class，按 href 提取；以 a/test/type 匹配公开用例身份，避免 download 参数导致重复。
    - 验证：`:shared:desktopTest :androidApp:assembleDebug :desktopApp:compileKotlin :windowsApp:compileKotlinWindows :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :desktopApp:createDistributable` 成功，879 测试零失败；签名 arm64 模拟器 xcodebuild `BUILD SUCCEEDED`。fixture 渲染浅/深色、大字体、缓存详情已看图。
    - 打包启动器真实 `citel show 9265 --fetch --json` 退出 0：6 用例（4 公开，2 结果页），输入/期望输出完整；离线 show/export 退出 0，13 文件（problem.md + 6 对 I/O），逐文件字节与缓存一致。产物仅在忽略目录 `.artifacts/agent-d-real-export`。
    - Mac fixture UI 在 E 的 `--agent-ui-smoke` 中实际点击获取全文、复制反馈，窄宽/深浅色正常；源码测试包与安装版使用不同 Bundle ID，测试结束已退出。
    - 未验证：四端原生保存面板、iOS 实际详情页操作；Windows 实机 CITEL 登录；题面图片仍保留原链接，不离线下载 assets。
  - PR E #26（`feat/ai-agent-uninstall`，基于 D #25）：Mac `agent uninstall-app [--yes]` 默认无副作用预览，执行前检查 App 包与锁，清理缓存目录、自己的 Preferences 节点、所有本 App Keychain 服务与管理的 Skill/脚本，最后原生 Trash API 移 App 到废纸篓。设置页共用清理器，新增卸载前清理；运行中保留空数据库和其加密密钥，避免破坏仍打开的 SQLite，下次退出 App 后用 CLI 完成删除。
    - 验证：四端构建与 macOS createDistributable 成功，889 单测零失败（清理器 8 项：临时 HOME 删除、独立 Keychain 服务所有账号删除、偏好兄弟节点/共享 plist 保留、App 锁/未知包阻断、符号链接、部分失败保留 App、实时空库保留、临时 App 原生 Trash 往返恢复）。真实机仅运行 dry-run，输出在 `.artifacts/agent-e-dry-run.md`。
    - Computer Use：纯内存 fixture 源码测试页验证获取/复制、卸载前确认/取消、窄宽与深浅色；测试进程已退出，安装版未关闭或替换。额外设置入口 2 测试通过；签名 arm64 模拟器 xcodebuild `BUILD SUCCEEDED`。
    - 未验证：真实 App 完整卸载、真实设置清理后的重登录（按规则不删除真实用户数据）；iOS 实际详情交互、各端原生保存面板；Windows 不提供卸载清理。
## 当前痛点

- 同步卡顿：Beta3 已到可接受范围。剩余长帧为同步详情 sheet 弹出（约 50ms）、进入作业页（约 49ms）和原生滚动带来的导航栏布局；`checkSession` 改后台后未复采；壳层随任一状态流整体重组（约 0.4 秒/次同步），彻底解决需拆分壳层。
- 小组件：PR #8 的 ad-hoc 签名后，侧载包的小组件共享容器是否可读尚未在真机确认；周视图、中号两栏、脚注只经 CI 编译，未在模拟器浅/深色下查看，小屏 iPhone 周视图可能偏挤；“正在上/还剩 N 分钟”未在上课时段实机观察。
- 首页物理在线/CITEL 日程项进入二级详情只在代码层实现，模拟器只验证了演示实验。
- “保存二维码并扫一扫”未真机验证（Android MediaStore 保存/去重、扫码后进入小程序/缴费页）；iOS 每次保存会多一张；Android 二维码弹窗未在平板上看。
- HyperOS 小窗↔全屏来回切换未实测（adb 无法切换窗口模式）。
- 课件 15 分钟大文件超时未用 >30 秒真实下载验证；iOS/Android“未开放下载”标记只验证编译。
- 在 Chrome 登录智慧教学会顶掉 App 会话，App 自动恢复登录失败一次（需手动刷新），未排查。
- macOS 附件默认应用打开、iOS 保存面板未点验；`HomeworkFileGateway` 已被邮箱复用，可改名为通用文件网关。

## 已知限制（非缺陷，排查时先看这里）

- 校历公众号文章不能由第三方 App 直接在微信内打开（invalid source），走系统浏览器。
- 小米“跟随应用内设置”覆盖安装清不掉：装新包后卸载重装，或在“使用高刷新率的应用”里打开交大自由行 KMP。
- 本地 Gradle 偶发被 Maven Central 429 限流，此时测试与 iOS 编译交给 PR CI。

## 迁移期遗留（长期，无排期）

- iOS Keychain 合法签名往返、正式签名发布（无 Apple Developer Team）。
- 物理在线真实上传、Windows 触摸屏；邮箱真实发送/删除/移动/附件上传；真实作业上传、退出账号往返（等学校侧条件）。
- 验证码发布级正确率需至少 300 张独立留出集。

## 接下来

- AI Agent 叠层 PR 按 #21 → #22 → #24 → #25 → #26 审核合入；#24/#25 CI 全绿，#26 CI 全绿。合并由用户决定；本机已换为 1.8.5-AgentAlpha，本地整合改动未提交。

1. 在真机已有版本（SideStore 保留扩展）上验证小组件共享容器与各尺寸显示。
2. 准备 1.8.4 正式发布时：核对版本号清单（见 `AGENTS.md`）、写 `docs/releases/<tag>.md`，等待用户批准打标签。

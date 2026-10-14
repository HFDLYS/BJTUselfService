# BJTUselfService 实时工作记忆

> 最后更新：2026-10-14。
> 当前分支：`feat/ai-agent`（基于 `release/1.8.4`@`adaf5e75`）；main 为（`1f011bc7`，已含 PR #16 错峰上课、#17/#19 Android 小组件、#18 取消 PR 打 IPA、#20 去掉荣耀 honorcard）。最新打包标签 `debug-1.8.4-Candidate2`（`ea0f760f`，不含 #20）。
> KMP 应用版本：main 为 `1.8.4-Candidate2` / Build 36；`release/1.8.4` 为 `1.8.4-Candidate3` / Build 37（macOS/Windows 安装包数值版本 1.8.4）。正式 Latest 仍为 `v1.8.3-KMP`。
> 冻结原 Android：`v1.7.1` / versionCode 9（对照 `origin/main@7d94f74`）。
> 已完成工作的细节见 `history_full.md` 末尾日志。

## 进行中

- 1.8.4 发布准备（`release/1.8.4`，已推到 `mine`，未开 PR；用户 2026-10-14 表示暂不发正式版）：当前版本 `1.8.4-Candidate3` / Build 37（Android、iOS 宿主与小组件、`AppUpdateChecker` 及测试、Windows 注释；桌面/Windows 数值版本仍 1.8.4），打包标签 `debug-1.8.4-Candidate3`；正式发布时改为 `1.8.4-KMP` / Build 38（发布说明已按 38 写）；发布说明 `docs/releases/v1.8.4-KMP.md`。必看弹窗：原新版导航/CITEL/作业聚合三个合为「1.8.3 版本说明」（`AppPreferences.release183GuidePending`，三项任一未确认才显示，确认一次三项全记已读）；新增「1.8.4 新增功能」（演示实验、桌面小组件、错峰上课，`release_184_guide_acknowledged`），在 1.8.3 弹窗之后显示，均 3 秒倒计时。弹窗文件改名 `feature/shell/ReleaseGuideDialog.kt`。
  - 已验证：`:shared:desktopTest` 822 项全过；`:androidApp:assembleDebug`、`:desktopApp:compileKotlin`、`:windowsApp:compileKotlinWindows`、`:shared:compileKotlinIos{SimulatorArm64,Arm64}` 通过；`DuoBugFixVisualTest` 出图 `shared/build/reports/duo-bugfix-ui/release18{3,4}-{light,dark}.png` 已看过。
  - 未验证：真机/模拟器上从旧版本升级后的弹窗顺序；iOS Xcode 构建（只改 Info.plist，交 CI）。
- AI Agent 接入（`feat/ai-agent`，2026-10-14 从 `release/1.8.4`@adaf5e75 开出，未提交、未推送）：计划在 `docs/agent/ai-agent-plan.md`，用户已批准。形态是 Skill（装到 `~/.agents/skills/bjtu-self-service/`）+ CLI `bjtu`，不做 MCP；CLI 可以读 Keychain/DPAPI 凭据自动登录。命令包括 status、cache read/update、聚合日程 calendar、三个来源聚合的 homework（物理在线只算已选课）、CITEL 题面和测试用例导出 Markdown、Mac 卸载清理。分 Spike + PR A–E 推进。
  - 设计要点：macOS 上 CLI 必须经 App 启动器运行，因为 Keychain ACL 绑定 App 的 CDHash。缓存值有 AES 加密，CLI 要用只读连接打开，不能走 `createDesktopCacheStore` 或 `openCacheStoreWithRecovery`（它们会新建密钥、删库）。
  - Spike 已做（2026-10-14，记录在 `docs/agent/spike-2026-10-14.md`；探针代码 `shared/.../desktopMain/.../agent/AgentSpikeProbe.kt` 和 `Main.kt` 里的 `agent` 分派还没提交）：
    - macOS 打包后经启动器运行 CLI 可行：管道 stdout 正常、headless、没有 Dock 图标、同一二进制读 Keychain 不弹框，热启动约 0.1 s。jpackage 运行时里没有 `bin/java`，所以只能走启动器。
    - Windows 上同样没有 `java.exe`，要在 CI 上实测 GUI exe 的管道输出（推送需授权）。
    - 本机 `~/.claude/skills` 是指向 `~/.agents/skills` 的软链接，安装器要先判断，不能重复建链接。
    - CITEL：题面在 `#description`（TeX 原样）；公开用例在 `#pub-testcase-table`，原文要走 `testcase/download_io.php`，因为页内预览会截断；提交过的题在 `result.php` 能拿到全部用例的 I/O。
    - 物理在线：用户确认“物理实验”课列着全部实验，只算在 wlsy 约上的实验，匹配方式待用真实标题对照。
  - 下一步：PR A（只读缓存打开、`status`、`cache read`、`homework`、`calendar`）。

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

1. 在真机已装的 Beta3（SideStore 保留扩展）上验证 iOS 小组件共享容器与各尺寸显示。
2. Android 小组件真机观感：荣耀 AAK-AN00 / 小米 25091RP04C 上添加三种小组件，看裁切、字号、刷新；视情况优化显示（不接入荣耀服务卡片）。
3. 是否合入 `release/1.8.4`、何时打 `v1.8.4-KMP` 标签发 Release 由用户决定，暂不发布。

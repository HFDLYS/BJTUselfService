# BJTUselfService 实时工作记忆

> 最后更新：2026-10-14。
> 当前分支：`feat/ai-agent-sync`（PR #24，真实刷新及补充测试已通过）。main 为 `ea0f760f`，已含 PR #16/#17/#18/#19；荣耀 Candidate2 真机验证仍待反馈。
> KMP 应用版本：`1.8.4-Candidate2` / Build 36（macOS/Windows 安装包数值版本 1.8.4）。正式 Latest 仍为 `v1.8.3-KMP`。
> 冻结原 Android：`v1.7.1` / versionCode 9（对照 `origin/main@7d94f74`）。
> 已完成工作的细节见 `history_full.md` 末尾日志。

## 进行中

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
  - 打包注意：`desktopApp/build.gradle.kts` 写死了 `packageBuildVersion = "35"`，1.8.4 改版本号时漏了，正式发版前要改成和 Build 号一致。
  - PR B（`feat/ai-agent-skill`，叠在 PR A 上）：设置页“AI 助手”卡片，只在桌面端显示。功能有：一键安装、重新安装、移除；“允许读取成绩”开关（`AppPreferences.agentGradesAllowed`，默认关）；一句可复制的话，粘贴给 agent 即可用。命令行新增 `agent install [--no-claude]`、`uninstall`、`doctor`。
    - 安装位置：Skill 写到 `~/.agents/skills/bjtu-self-service/SKILL.md`。启动脚本在 macOS 是 `~/.local/bin/bjtu`，在 Windows 是 `%LOCALAPPDATA%\BJTUselfServiceKMP\bin\bjtu.cmd`，按系统代码页写；Skill 里写的是脚本的绝对路径，所以不用改 PATH。
    - 只动自己写的文件：脚本里带标记注释，Skill 目录里带标记文件。目标位置已有别人的同名文件时拒绝覆盖，卸载也只删带标记的。
    - Claude Code：`~/.claude/skills` 本身就是指向 `~/.agents/skills` 的软链接时不再另建；是普通目录时建软链接（Windows 建不了就复制一份）；同名位置被占用时不动。
    - 已验证：`:shared:desktopTest` 853 项全过（安装器 8 项都在临时 HOME 里跑，包括真的执行生成的脚本、检查参数转发）；四端编译通过。Windows CI 补了 install → 经 `bjtu.cmd` 调用 → uninstall 的检查。
    - 已验证（2026-10-14）：打包替换本机 App 后，用户在设置页一键安装成功，卡片深浅色都正常；用户新开 Codex 会话贴那句话，Codex 自己找到 `~/.agents/skills` 下的 Skill，调用了命令，正确答出本周课表、零点截止（按前一天 24 点说）、物理实验只算约上的 4 项、缓存时间。“允许读取成绩”是用户自己打开的。
    - 追加（同一 PR）：Skill 改名为 `bjtuss`（安装时清掉带标记的旧目录 `bjtu-self-service`）。课表、作业、考试、成绩同步成功后写 `synced_at_v1.<source>`（仓库的 `onSynced` 回调）；输出顶层加 `freshness`（超过 4 小时的进 `stale`，没有记录的进 `unknown`），Markdown 顶部加提示；Skill 规定 `stale` 不为空时先问用户要不要刷新。CITEL 输出加 `judgeStatus`，避免把“交了但没全对”说成“没交”。
  - 待做：App 全局把“次日 00:00 截止”显示为“前一天 24:00”（用户 2026-10-14 要求，与首页归日逻辑一致），单独从 main 开 PR。
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
  - 后续：PR D（CITEL 题面和用例导出 Markdown，只在做题或拉全文时下载 I/O）、PR E（Mac 卸载清理）。
- 荣耀小组件不可见修复（`fix/honor-widget-visibility`，PR 待合入）：带 `com.hihonor.widget.type=honorcard` 的 Candidate2 在荣耀 AAK-AN00（MagicOS 11.0.0.109 / Android 17）上服务卡片与经典小组件入口都找不到本应用；去掉三个 receiver 上的该 meta-data 后（`.artifacts/honor-no-honorcard-debug.apk`），`dumpsys appwidget` 识别三个 provider，用户确认桌面小组件列表里出现。结论：未配齐荣耀卡片所需配置时，honorcard 标记会让 MagicOS 把小组件整体隐藏；先走标准 AppWidget。小米 25091RP04C（Android 16）系统同样识别。未验证：荣耀上实际添加后的渲染、尺寸与刷新。荣耀「服务卡片/快服务」需在荣耀开发者平台智慧服务登记（签协议、SHA256 指纹、截图、审核），用户 2026-10-14 决定不做；后续只视情况优化安卓小组件显示。
- 第二节错峰上课（PR #16，`feat/staggered-second-slot`，CI 全绿，待合入）：第二节教室在逸夫教学楼（YF）/思源西楼（SX）时按 10:30–12:20。设置页新增开关「错峰上课时间」（默认开，`AppPreferences.staggeredSecondSlot`）。规则在 `domain/course/StaggeredClassTime.kt`（楼名或 `YF/SX+数字` 教室号；思源楼/思源东楼/威海逸夫楼不算；实验/考试不参与）。
  - 精确时间：课程详情（含“（错峰）”）、按日列表卡片与整节均错峰时的节次标签、首页“现在/下一节”与日程、iOS 小组件快照 start/end、日历导出。
  - 只下移不加节：课表网格色块顶边下移约 1/8 格（≤7dp）、底边压到格线；iOS 周视图按 `staggerMinutes` 归回原大节，同样微移。快照新增可选字段 `staggerMinutes`，旧小组件忽略。
  - 已验证：`:shared:desktopTest` 全过（含新增 `StaggeredClassTimeRenderTest`，合成课表出图于 `shared/build/reports/staggered-class-time/`，浅/深色、整节错峰、混排、关闭开关均人工看过）；iOS 签名模拟器 `xcodebuild` Debug（含小组件 Swift）`BUILD SUCCEEDED`；`:androidApp:assembleDebug`、`:desktopApp:compileKotlin`、`:windowsApp:compileKotlinWindows`、`:shared:compileKotlinIos{SimulatorArm64,Arm64}` 通过。
  - 未验证：真实课表里 YF/SX 地点文本格式（按既有 fixture `海淀西校区,思源西楼,SX501` 推断）；模拟器/真机中的设置页开关、首页卡片和 iOS 周视图小组件实际观感。
- Android 桌面小组件（`feat/android-widgets`，基于 PR #16 分支）：临近课程（2×2/4×2）、今日课程（4×2/4×4）、本周课表（4×4），与 iOS 同一份快照与版式规则。
  - 实现：展示逻辑在 shared `feature/widget/CourseWidgetModel.kt`（可测，与 Swift 一一对应）；快照写到应用私有目录 `files/course-widget.json`（`CourseWidgetStore.android.kt`），写入后回调 `CourseWidgetUpdater.updateAll`；渲染为原生 RemoteViews（`androidApp/.../kmp/widget/`），不引入 Glance 等新库。
  - 版式：以 iOS 尺寸为基准按实际宽高等比缩放字号（0.9–1.25），两栏共用字号，单行按估算宽度缩字；周视图按行高/列宽计算每块可放几行。深浅色走 values-night，类别文字色在 Android 12+ 用资源色。Android 12+ 按桌面上报的每个尺寸各排一份。
  - 刷新：非唤醒 RTC 闹钟，上课时段逐分钟、其它时间按课程起止且最多 15 分钟；不申请精确闹钟权限。
  - 荣耀：容器 16dp 圆角（Android 12+ 用系统圆角）；honorcard 标记已在 `fix/honor-widget-visibility` 去掉（见上）。
  - 同分支附带 iOS 中号“临近课程”溢出修复：栏标签/课名/地点/时间加 `minimumScaleFactor`，剩余时间窄栏短写“1 小时 15 分”。
  - 已验证：`:shared:desktopTest` 全过（新增 `CourseWidgetModelTest`）；`:androidApp:assembleDebug`、`lintDebug`、桌面/Windows/iOS Kotlin 编译通过；iOS 签名模拟器 xcodebuild 成功。Pixel 10 Pro XL 模拟器（API 37）用合成快照实测三种小组件、深浅色、错峰下移、分钟倒计时刷新。
  - 未验证：荣耀真机（服务中心入口、16dp 容器、折叠屏尺寸）；Android 12 以下系统；真实登录后由 App 写入快照的链路；iOS 溢出修复的实际观感（只编译）。
  - 排版教训（用户在模拟器截图发现裁切后修复）：RemoteViews 超出卡片的内容会被直接裁掉，不像 iOS 按行截断；中文字体行高实测约 1.7 倍字号（`line` 常量），估高要偏大；先 `shrinkToFit` 整体缩小保住完整版式，再决定是否丢次要内容（今日课程脚注）；小号“下一节”是核心信息，按“整体缩小 → 长课名压成一行 → 最后才丢它”让步（`planSmall`）。桌面上报高度比实际显示小约 30dp（保守下限），估高时补回 18dp（`SIZE_SLACK_DP`），否则两节课放得下也会被缩小留空；课名只超出可用宽度 15% 以内时缩字单行，别按两行估高；XML 与代码里不要各加一份外边距。
  - 备注：此前一版 Android 小组件 PR #15 因实现质量不达标被用户关闭（见其评论），远端分支已于 2026-10-14 删除；#17 为独立实现。
  - PR 状态：#17 CI 全绿（Android 编译、iOS 编译与测试、iOS 侧载 IPA）；用户验证后决定是否合并 #16、#17。

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

1. 在真机已装的 Beta3（SideStore 保留扩展）上验证小组件共享容器与各尺寸显示。
2. 准备 1.8.4 正式发布时：核对版本号清单（见 `AGENTS.md`）、写 `docs/releases/<tag>.md`，等待用户批准打标签。

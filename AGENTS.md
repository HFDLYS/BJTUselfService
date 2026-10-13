# BJTUselfService 协作与实现指南

本文件适用于参与本仓库的 AI 编码代理和开发者。KMP 迁移（M0–M17）已经收口，项目进入**按需迭代**阶段：每次改动是一个修复、一个小功能或一次发版，不再按大里程碑规划。当前工作状态见 `memory.md`，已完成工作的日志见 `history_full.md`；文档与当前代码或用户最新要求冲突时，以代码和用户为准，并向用户说明冲突，不自行扩大范围。

## 文档分工与读写规则

| 文档                | 角色         | 谁写                   | 写什么                                                                 | 不写什么                               |
| ------------------- | ------------ | ---------------------- | ---------------------------------------------------------------------- | -------------------------------------- |
| `AGENTS.md`       | 原则         | 人工维护               | 协作方式、仓库事实、架构边界、平台能力、安全红线、设计、测试、交付规则 | 进度、版本状态、验证结果、日期性事件   |
| `memory.md`       | 实时工作记忆 | 每次任务结束时更新     | 顶部摘要、进行中的事项、当前痛点、接下来要做的事                       | 已经合入/发布的完成细节（移入日志）    |
| `history_full.md` | 工作日志     | 一项工作合入或发版时追加 | 按日期追加的条目：做了什么、为什么、验证命令与结果、实机现象、未验证边界、决策 | 待办与计划                             |

**读写顺序**：

- 任务开始前：读 `AGENTS.md` → `memory.md`；`history_full.md` 不通读，只用 `rg` 查同类问题、同一功能的既往结论与证据。
- 任务进行中：未合入的改动只记在 `memory.md`“进行中”。
- 一项工作合入 `main`（PR 合并）或发出版本后：把对应内容从 `memory.md` 移出，在 `history_full.md` **末尾**追加一条日志；不改写旧条目（事实错误时追加更正条目）。
- `AGENTS.md` 由人工维护；AI 只在用户要求时修改，改动前后向用户说明。
- `docs/migration/`、`docs/security/` 等是当时的专题报告，可作参考，但不代表当前状态。

**日志条目格式**（`history_full.md`）：

```markdown
## 2026-10-13 课程类别色统一与同步卡顿修复（PR #11，1.8.4-Beta3）

- 改动：……（用户可见行为 + 关键实现位置）
- 原因/决策：……
- 验证：实际命令与结果、模拟器/真机现象。
- 未验证：……
```

同一天多项工作可分条；条目要写清分支/PR/标签/版本，方便以后检索。

## 开始任何任务前

1. 按上表读 `AGENTS.md`、`memory.md`，按需检索 `history_full.md`。
2. 运行 `git status --short` 和 `git branch --show-current`，识别并保护用户已有改动；需要最新代码时先 `git pull --ff-only`。
3. 阅读即将修改文件的调用链和对应测试，不只阅读目标函数。
4. 明确本次涉及哪些平台、验收命令是什么。
5. 多文件或行为变化先给出简短计划；用户已明确要求直接做时可以直接实施。

默认使用中文沟通。解释新概念时先说用途和行为，再给术语与实现细节。

## 仓库事实

- 仓库根部是冻结的原 Android 工程（`:app`，只读参考，旧 CI `release.yml`/`debug.yml` 仍在）。所有实际开发都在独立的 `multiplatform/` Gradle 工程里：`shared`（共享业务与 Compose UI）、`androidApp`、`desktopApp`（macOS）、`windowsApp`、`iosApp`（Xcode 宿主 + `CourseScheduleWidget` 小组件扩展）。
- 不修改 `app/`、根 `build.gradle.kts`、`settings.gradle.kts`、`gradle/`、Android Manifest、资源或现有测试；不把 KMP 模块 include 到根 `settings.gradle.kts`。
- 远端：`mine` = `JasonZhang1225/BJTUselfService-KMP-Refreshed`（日常推送、PR、Release 都在这里）；`origin` = 上游 `HFDLYS/BJTUselfService`，**不推送**。
- 分支流程：`main` 跟踪 `mine/main`。每项改动在 `feat/…`、`fix/…`、`perf/…` 分支开发，用户要求时推到 `mine` 并开 PR 合入 `mine/main`；合并由用户决定。PR 由 `kmp-security-check`（共享测试/编译）和 `kmp-package`（iOS 相关改动时打 Debug IPA）检查。
- 标签：`debug-*` 只触发 Actions 打包、不建 Release；`v*`（如 `v1.8.3-KMP`）打四端包并发 GitHub Release，发布说明放 `docs/releases/<tag>.md`。
- 改版本号要同步修改：`multiplatform/androidApp/build.gradle.kts`（versionName/versionCode）、`desktopApp/build.gradle.kts`（数值版本）、`windowsApp/build.gradle.kts`、`iosApp/iosApp/Info.plist`、`iosApp/CourseScheduleWidget/Info.plist`（版本与 Build 必须与宿主一致），以及 `shared/.../update/AppUpdateChecker.kt` 的 `CURRENT_VERSION` 和 `AppUpdateCheckerTest`。
- 技术栈：Kotlin Multiplatform + Compose Multiplatform/Material 3；Ktor；Ksoup 解析 HTML；`kotlinx.serialization`；SQLDelight 缓存；凭据走 Android Keystore / Apple Keychain / macOS `MacOsSecretBag` / Windows DPAPI；验证码 Android/Windows 用 PyTorch 模型，Apple 用 Core ML。
- 本机验证产物（截图、导出、诊断日志）放忽略目录 `.artifacts/`，不进 Git。

## 工作方式

### 检查优先

- 优先使用 `rg`、Gradle、`xcodebuild` 等只读或可复现命令检查工程。
- 命令行负责源码、构建和测试证据；Computer Use 与截图负责真实界面、交互和模拟器状态证据。涉及用户可见行为时两类证据都要考虑，不能只看编译结果。
- 使用 Computer Use 时先读取应用当前状态；每次操作后重新读取，不复用旧的元素索引。
- 性能问题先采样（Instruments/Time Profiler、`--sync-timing` 等）再改，改后复采对比，不凭猜测优化。
- 不根据函数名猜测学校接口或页面能力；协议问题用浏览器开发者工具或真实响应取证。

### 变更边界

- 每次只推进一个可验证的改动；不在修 A 时顺手重写无关的 B。
- 保留用户指定的机制、文字、目录名和未提交改动。
- 不擅自安装依赖、修改系统设置、登录其他账号、创建证书或接受协议；唯一预先授权的登录范围见下文“Computer Use 使用规则”。
- 不擅自 commit、tag、push、创建 PR 或发布；用户明确要求时才做。

### 计划与记录

- 计划写清：将修改/新增的文件、行为变化、平台差异、测试和回退方案。
- 不用“已完成”描述只编译、未运行验证的功能；本地无法构建而交给 CI 的，要写明。
- 遇到真实服务器、真机、签名或账号阻塞时，记录已经验证的边界，不伪造成功。
- `memory.md` 是动态记忆，不是只追加日志：每次任务结束都要增删改。已解决的痛点删除或改写；已合入的事项移进日志；同一事项不能同时出现在“进行中”和“接下来”。
- 分支、版本号、最新 Release、未提交文件或验证状态变化时，立即同步 `memory.md` 顶部摘要。

## KMP 架构规则

### commonMain 允许的内容

- 纯 Kotlin 数据模型和业务规则；Coroutines、Flow/StateFlow。
- 跨平台网络协议、序列化和解析逻辑；Repository 接口和用例；SQLDelight 定义。
- 共享 Compose UI、主题 token 和响应式布局逻辑。

### commonMain 禁止的内容

- `android.*`、Android `Context`、Activity、Intent、WebView、DownloadManager。
- JVM-only API（`CompletableFuture`、`java.io.File` 作为公共模型、Java 反射）。
- 直接调用 UIKit、AppKit、Swing/AWT；在共享业务对象中保存全局平台单例；依赖平台文件绝对路径。

平台差异使用接口、依赖注入或 `expect/actual` 隔离。只在真正的平台原语上使用 `expect/actual`；业务策略尽量用普通接口表达，便于测试。

### 状态、线程与生命周期

- 页面状态是不可变数据，事件是明确输入，副作用集中处理。
- 网络请求不能从 Composable 的普通重组路径启动。
- 学校响应的解码、HTML/JSON 解析和会话过期判断放后台调度器（参见 `network/ParseOffMain.kt`），不占主线程；网络请求本身保持原有串行语义。
- 内容不变时沿用旧实例，避免 Compose 因“等值新对象”反复整表比较或重组。
- 共享状态不持有 Activity、UIViewController、Window 或文件选择器实例；长生命周期协程必须有明确所有者和取消路径。
- macOS 关闭窗口与退出应用是不同生命周期；关闭最后一个窗口不等于清空会话或退出。

### 依赖收敛

- 新共享 JSON 模型用 `kotlinx.serialization`；不增加 Gson、Moshi、`org.json`。
- 引入库前检查 Android、iOS arm64、iOS simulator arm64、macOS、Windows 目标是否真实可构建；不凭印象锁版本，先核对 Kotlin、Compose Multiplatform、AGP、Gradle、Xcode 兼容矩阵。

## 平台能力规则

### WebView 与 Cookie

- 各平台网页组件分别实现，共享导航策略和 Cookie 数据协议。
- Cookie 同步限定域名、路径、安全属性和时机；不把所有 Cookie 注入任意网页。
- 外部链接与应用内网页边界明确；不绕过系统 TLS 安全警告。

### 文件与下载

- 共享层使用字节流、数据源/数据槽和元数据，不把 Android `Uri` 或 JVM `File` 暴露为公共 API。
- iOS 走系统文档选择/分享/导出；macOS 用保存/打开面板并支持键盘操作。
- 写文件前处理重名、取消、失败恢复、剩余空间和临时文件清理；测试不向用户真实 Downloads/Documents 写垃圾文件。

### 数据库和设置

- 用户名、密码、令牌和敏感 Cookie 必须进系统安全存储，不进 SQLite 或普通偏好。
- schema 变化必须有迁移测试，不以破坏性重建代替迁移。
- 退出登录区分“清除敏感会话”和“清除全部离线数据”，界面文字说清后果。

### iOS 小组件

- 小组件与主 App 通过 App Group 共享快照；SideStore 等侧载工具会改写 App Group 名称，因此组名必须从描述文件 Entitlements 动态解析并回退原名，不能写死。
- 快照只由主宿主生成，防抖并在后台线程构建与写入；不在每次重组或每个原生 Tab 宿主里各写一遍。
- 时间线条目数量要克制（真机有预渲染预算，超预算会退回占位图）。

### 验证码模型

- 首版路径永远是“显示图片 → 刷新 → 手动填写”；自动识别失败回到手动，最多 3 次，不无限重试。
- Apple Core ML 与 Android PyTorch 的预处理（`130×42`、RGB、CHW、`[0,1]`）、输出轴顺序、类别表和 CTC 解码必须对齐；改模型或预处理时，用同一份脱敏测试集逐层比较（像素、输入张量、logits、argmax、解码字符串、算式答案）。
- 仅能加载或执行模型不等于识别正确，没有正确率证据不得改变默认开关。

## 网络与安全红线

- 禁止新增“信任所有证书”或无条件 `hostnameVerifier = true`。
- HTTP 明文接口逐个登记用途、数据敏感性、可替代 HTTPS 地址和 Apple ATS 影响。
- 不在日志、异常、截图、测试 fixture 或提交中保存真实学号、密码、Cookie、令牌；调试日志在 release 可关闭，URL 查询参数和响应体脱敏。
- 不把真实账号数据发给第三方服务来帮助调试。
- 学校接口只能以不安全方式工作时，先报告阻塞和风险，由用户决定，不静默降低安全性。用户已于 2026-07-31 授权 macOS、并于 2026-08-01 授权 iOS 智慧教学会话使用旧 HTTP；该授权只覆盖 `123.121.147.7:88/ve/` 和教学日历 `123.121.147.7:1936/kk/rp/`，必须保持端点/路径白名单、内存会话脱敏和不可关闭的 UI 风险提示，不得扩大到 Android、MIS、AA、邮箱或其他 HTTP。

## Compose Multiplatform UI 规则

### 共享而不强求相同

- 共享设计语言、状态和核心组件，按窗口宽度、输入方式和平台习惯调整导航。
- iPhone 单列、触控、单手可达；iPad/宽窗口用列表—详情或侧栏；macOS 支持可调整窗口、菜单、悬停、滚动、右键和快捷键。
- 紧凑端二/三级页走平台原生容器（Android Activity、iOS `UINavigationController`）；iOS 弹层走 UIKit sheet，按内容定高的用 `fitContentHeight`。
- 颜色语义集中定义（例如课程类别色只来自 `CourseCategoryPalette`），不在各页面另起调色板。

### Apple Design 验收要求

- **响应**：按下立即有视觉反馈，不等网络请求完成。
- **可打断**：手势驱动的动画可在运动中被重新抓取或反向。
- **空间一致**：进入与退出沿同一路径；弹层、菜单、详情与触发源保持空间联系。
- **克制弹性**：普通出现/消失无弹跳；只有带动量的拖拽释放允许轻微弹性。
- **反馈完整**：状态、完成、警告、错误各有明确且不过度的反馈，错误尽量就地说明。
- **材料有层级**：半透明只用于表达浮层层级，不堆叠玻璃，不牺牲文字对比度。
- **排版可伸缩**：系统字体与动态字体，不以固定高度截断文字。
- **辅助功能**：减少动态效果、减少透明度、更高对比度、语义标签、键盘导航、足够点击区域。

默认动效倾向临界阻尼、无过冲、响应约 0.3–0.4 秒；映射到 Compose spring 参数后在模拟器/真机观察，不机械复制数字。

## 测试与验证

### 每次改动至少执行

- 业务逻辑/解析：`./gradlew :shared:desktopTest`（必要时加 `:shared:iosSimulatorArm64Test`，需 Java 21）。
- 共享代码：编译所有受影响目标（Android、iOS、macOS、Windows），不能只跑一个。
- Android：`:androidApp:assembleDebug`。
- iOS：签名的模拟器构建（见下文），涉及原生 API 或 UI 时在模拟器实际打开页面；Swift 改动必须经 Xcode 编译或明确交给 PR CI。
- macOS：构建并启动桌面应用，检查窄/宽窗口。
- UI：至少检查浅色/深色、加载/空/错误/正常状态和文字放大。
- 网络/解析：脱敏 fixture 覆盖正常、字段缺失、登录过期和服务错误响应。
- 本地 Gradle 因依赖源限流等原因无法构建时，说明原因，把验证交给 PR CI，并在结论中注明“仅 CI 验证”。

### 高风险区域的回归要点

- 课程表：首次取得当前周、缓存当前周、切换本学期/选课学期、手动选周后不被异步结果覆盖、非教学周、前往日期。
- 成绩：选择课程、按账号恢复、筛选和排序、清空选择、切换账号、窄窗口换行、异常成绩不参与计算。
- 登录：验证码加载中/失败/刷新、表单校验、紧凑与宽窗口、错误就地说明、静默自动登录与会话恢复。
- 同步：同步期间滑动不卡顿、部分失败时保留缓存并就地提示、校园网限定数据源（物理实验/演示实验/物理在线）的失败态。
- 小组件：快照内容与课表一致、浅/深色、各尺寸放不下时的计数文案、版本号与宿主一致、侧载后 App Group 可读。

### 结果表述

- “测试通过”必须附实际命令和结果。
- Mock/fixture 通过只证明解析和业务逻辑，不证明真实服务器登录。
- 模拟器通过不能自动证明真机文件权限、Keychain、网络、小组件或性能表现。
- debug 构建不能自动证明 release 签名和侧载安装。

## Computer Use 使用规则

- 适用：Android Studio/Xcode 状态、模拟器视觉与交互、系统权限弹窗。不适用：读源码、改文件、跑 Gradle/xcodebuild、查 Git。
- 用户已于 2026-07-29 明确授权：实际验证登录时，可以读取仓库根目录 `MisSecret.md`，直接填入本机原 Android App 以及本机 KMP 各端复现的既有北交大登录流程；凭据登录无需再次索取或让用户接管。
- 读取 `MisSecret.md` 时只把内容保存在执行所需的内存变量中，不用会回显内容的命令，不输出、复制到普通剪贴板、截图、记录或提交；不得填入浏览器、第三方服务或与本 App 无关的目标。
- 该授权不包含修改密码、创建新账号、扩大权限或接受协议。遇到意外权限、证书、签名、安装、协议或不同登录目标，仍须先请求确认。
- 在上述同一登录流程中，CAPTCHA 的获取、识别、填写和提交自动进行。
- 默认只执行只读或本地可恢复操作。提交作业、删除远端数据、充值、发送邮件等会改变外部状态的动作，必须针对该动作单独获得授权。
- 截图和录屏必须避开学号、姓名、成绩、邮件、Cookie、验证码等个人信息；未脱敏原图只保存在 `.artifacts/`，不进 Git。

### macOS 源码运行与多窗口隔离

- `desktopApp/.../desktop/Main.kt` 只有一个 `Window`；`AppReopenedListener` 只显示并聚焦已有窗口，不得改成在重开事件中新建窗口。涉及窗口生命周期的改动必须补 `DesktopWindowLifecycle` 测试。
- `./gradlew :desktopApp:run` 会启动独立 JVM；终端 `Ctrl-C` 可能只结束 Gradle，子 JVM 仍在后台。禁止并行或连续启动多个 `:desktopApp:run`。
- 启动前用 `ps -axo pid,ppid,command` 检查精确源码路径（`multiplatform/desktopApp/build/compose/binaries/main/app/BJTUselfServiceKMP.app`），验证期间最多一个源码实例。`/Applications/交大自由行 KMP.app` 是用户的安装版，不得关闭或结束。
- Computer Use 可能在目标未运行时自动启动应用，因此“Gradle 启动源码实例”与“Computer Use 自动启动”二选一；源码版与安装版同时存在时，交互验证前先解决实例歧义。
- 验证结束先结束 Gradle，再按精确路径检查并结束遗留源码 PID。禁止 `killall`、宽匹配 `pkill` 等可能命中安装版的命令。

## iOS 打包与安装

- iOS **模拟器**构建必须启用签名（`CODE_SIGNING_ALLOWED=YES`、`CODE_SIGNING_REQUIRED=YES`，团队 `34S53DC6T6`），否则丢钥匙串、每次重新登录；配置在 `multiplatform/iosApp/Configuration/Config.xcconfig`，`scripts/require-ios-signing.sh` 只拦截模拟器构建。
- 真机包是未签名 IPA（`generic/platform=iOS` + `CODE_SIGNING_ALLOWED=NO`），由用户用 SideStore 自签安装；CI 打包前用 `scripts/sign-ios-sideload-app.sh` 对嵌套 framework、小组件、主 App 依次 ad-hoc 签名并保留 App Group Entitlements。
- 安装前检查实际安装包的签名；安装后验证 Keychain 保存/读取/清除（使用独立测试项，不读取、修改或输出用户凭据）。仅构建成功或进程启动不能证明登录信息保存正常。
- 用户要求“这次不改／不重装”时，不重新打包、安装或替换正在运行的应用；附着采样（如 xctrace attach）不算重装。
- 新版 Xcode 的模拟器是 Device Hub.app，不是 Simulator.app。

## Git 与交付

- 不使用 `git reset --hard`、`git checkout --` 覆盖用户改动；不修改无关文件，不格式化整个仓库。
- 用户要求提交时，先给出提交信息草稿和将包含的文件；提交信息用中文，`feat:`/`fix:`/`perf:`/`docs:` 等前缀。
- 未经明确授权，不 commit、tag、push、force-push、创建 PR、合并或发 Release；不向 `origin` 推送。
- 发版时单独核对版本号（见“仓库事实”中的清单）、提交范围、测试、构建矩阵和发布说明，等待用户批准。
- 每次交付说明：完成的行为、修改文件、验证命令、未验证边界和下一步。

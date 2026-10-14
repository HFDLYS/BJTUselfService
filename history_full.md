# BJTUselfService KMP 工作日志

> 本文件是**只追加的工作日志**，记录已经合入 `main` 或已发版的工作：做了什么、为什么、验证命令与结果、实机现象、未验证边界和决策。它不是待办清单：当前状态与计划见 `memory.md`，原则与边界见 `AGENTS.md`。
>
> - **写入**：一项工作合入 `main`（PR 合并）或发出版本后，在文件**末尾**追加 `## YYYY-MM-DD 标题（分支/PR/版本）` 条目；不改写旧条目，事实有误时追加更正条目。
> - **检索**：不要通读，用 `rg` 按功能名、类名、版本号、PR 号查同类问题与既往证据。
> - **结构**：2026-10-09 之前是迁移期（M0–M17）按里程碑整理的归档，其中“决策记录”一节集中了迁移期决策；之后严格按日期顺序记录。
> - 本文件不含账号、密码、Cookie、令牌、真实验证码会话或任何敏感信息。迁移期条目里残留的“待办/待复验”句式只代表当时状态。

# 第一部分：迁移期归档（2026-07-29～2026-10-09）

## 未拆分的历史基线（M0 之前）

- 已检查现有仓库结构和主要依赖，确认它是 Kotlin/Java + Jetpack Compose 的原生 Android 应用。
- 已确定迁移路线：Kotlin Multiplatform + Compose Multiplatform，目标平台为 Android、iOS 和 macOS。
- 已从 `main@9d8da18` 创建并切换到本地 `ZJG` 分支（后作为分支创建点保留）。
- 已创建 `goal.md`（记录迁移范围、技术方向、里程碑和完成标准）与 `CLAUDE.md`（记录协作方式、平台边界、安全规则、Apple 设计原则和验证要求）。
- 已冻结现有 Android 工程：根工程不参与 KMP 改造；新实现只写 `multiplatform/`。

## M0：基线与工具链

- 2026-07-31：用户授权将根工程 `app/` 等工作树补齐到正式 `v1.7.0` 源码（壁纸/Haze、周视图小组件、作业附件、`upId` 同步身份、教室安排修复等）。本机保留 `compileSdk/targetSdk 36`、阿里云镜像与 `local-maven`，并为 ucrop 增加 JitPack；`MainActivity`/`SettingScreen` 采用 1.7.0 功能代码并仅补 `versionName` 空安全以便编译。`:app:assembleDebug` 在 Android Studio JBR 21 下 `BUILD SUCCESSFUL`。`multiplatform/` 未改动。提交 `e74d60a` 已推送到 fork `mine/main`。
- 已确认正式 Release `v1.7.0` 于 2026-07-29 发布，对应提交 `419313d`，不是草稿或预发布，发布资产包含 `BJTUSelfService-1.7.0_arm64-v8a.apk`。
- 已通过远端 compare 确认 `419313d` 是 `9d8da18` 的后代，领先 9 个提交且没有分叉；`ZJG@9d8da18` 只保留为分支创建点，发布与功能基线已调整为 `v1.7.0@419313d`。
- 已把 1.7.0 的成绩自选、课程表当前周修复、作业附件下载、作业同步修复、课程表小组件周视图、底部导航/壁纸/玻璃态优化和教室安排修复全部纳入 KMP 三端迁移与回归范围。
- 已下载并隔离保存官方 1.7.0 APK 与标签源码；APK SHA-256 为 `c48b4ddb8f2fdbbb30b546e9f67d34a12fdf4041861dc27b9318c70d428dad3f`，清单核验为 applicationId `team.bjtuss.bjtuselfservice`、versionCode 8、versionName `v1.7.0`、minSdk 28、target/compileSdk 34。
- 已核对工具链：Apple Silicon arm64、macOS 27.0、Xcode 26.6、Apple SDK 26.5、iOS 26.2/26.5 模拟器、Android Studio JBR 21.0.10、Temurin JDK 25、Android AVD `Pixel_10_Pro_XL`。
- 已使用 Android Studio JBR 21 运行 `:app:testDebugUnitTest` 和 `:app:assembleDebug`，结果 `BUILD SUCCESSFUL in 9s`；记录了 Kapt 对 Kotlin 2.0 回退 1.9 和 Gradle 9 弃用兼容警告。
- 已把官方 v1.7.0 APK 安装到 Pixel 10 Pro XL API 37.1 模拟器，并按用户动作时授权完成一次真实 CAPTCHA 登录；“正在登录...”结束后首页/应用/设置保持可用。
- 用户于 2026-07-30 表达持续授权：在本机原 Android App 与本机 iOS/macOS 移植版所复现的同一北交大登录流程中，后续获取、识别、填写和提交 CAPTCHA 不希望逐次确认；若执行环境存在不可豁免的 CAPTCHA 动作时确认规则，则只按其最小要求确认。账号来源、登录目标或凭据接收方发生变化时仍须重新确认。
- 已创建 `docs/migration/m0-baseline.md`，记录基线身份、工具链、构建证据和当前视觉观察。
- 已用稳定 Xcode 26.6 完成 KMP framework、SwiftUI 宿主、Swift 编译/链接、iOS 26.5 Simulator 安装与运行；本机 Xcode 26.6 对当前 M1 组合实测可用。
- 已基于 1.7.0 隔离源码和真实登录行为更新 `docs/migration/feature-matrix.md`；每项区分通过、部分验证、仅确认入口和未执行。

## M1：最小 KMP 骨架

- 已完成 M1：`multiplatform/` 使用 Kotlin 2.4.10、Compose Multiplatform 1.11.1、Material 3 `1.11.0-alpha07`、AGP 9.1.1 和 Gradle 9.3.1，采用 `shared + androidApp + desktopApp + iosApp` 独立结构。
- 已创建 `docs/migration/m1-result.md` 和三端脱敏证据图；`iosSimulatorArm64Test` 执行器曾长时间无输出后中止，不能写成通过，iOS 可运行性由 framework/Xcode build/真实首帧证明。
- `docs/migration/m1-frozen-boundary.md` 已完成交付复核：受保护 Android 根工程仍为 154 个跟踪文件、原有 6 条用户修改，M1 前后聚合 SHA-256 都是 `a2b8313387fa6902cb8297554a2724c63dfddcfdd800f5f536374e44bbe1c73f`。
- M1 后已复跑冻结根工程 `:app:testDebugUnitTest :app:assembleDebug`，结果 `BUILD SUCCESSFUL in 9s`。
- 已使用 Computer Use 保持 Android Studio Running Devices 在前台，并逐页打开首页、应用、设置、七个核心入口与安全二级流程。
- 已确认真实行为：邮箱 SSO 进入桌面式 Webmail；校园卡/校园网为确认弹层；成绩支持自选模式和详情；课程表有切换学期/14 周选择；作业详情含内容/附件及上传下载入口；课件可展开资源；教室为教学楼—教室两级；其他功能为校历和中英文成绩单。
- 已在 `docs/migration/visual-baseline/` 保存 19 张本地脱敏基线图及安全说明；目录没有 `raw` 文件。WebView 内容不使用可访问性文字框脱敏，邮箱图被整页覆盖；Dialog 背景额外覆盖。
- 已确认设置页的主题菜单、检查更新和清除数据确认层；没有改变开关、壁纸、主题或账号，也没有执行下载、上传、充值、分享、发送、清除或退出。
- 已只读检查 Android 验证码流程：当前输入为 `130×42`、RGB、CHW、`[0,1]`，模型输出按 8 个位置 × 15 类解码。
- 已决定 Apple 首版必须支持验证码图片展示、刷新和手动填写；Core ML 自动识别是非阻塞增强，不能只凭“模型可以运行”就启用。
- 已读取 Computer Use 和 Apple Design 技能，并把适用规则写入项目文档。

## M2：共享领域层

- 已完成 M2：在 `shared/commonMain` 建立无 Room/Android/JVM 依赖的成绩、课程、作业、考试模型，以及成绩计算/筛选/排序、稳定选中恢复、课程周次和作业日期规则。
- M2 使用官方 `kotlinx-datetime 0.8.0`；时间与时区由调用层注入，测试不依赖真实系统时钟。
- M2 已对照隔离的 `v1.7.0@419313d` 源码；成绩页和选中恢复规则与冻结分支对应实现无差异。
- `:shared:desktopTest` 共 19 个测试全部通过；`:shared:compileTestKotlinIosSimulatorArm64`、`:androidApp:assembleDebug` 和 `:shared:linkDebugFrameworkIosSimulatorArm64` 同次构建成功。

## M3：网络、登录与解析

- M3 已完成：共享登录状态机、HTTP transport、Ktor Android OkHttp/iOS Darwin/desktop CIO 引擎、内存 Cookie 会话和 CAS/MIS/AA HTML 解析已进入 `shared`。
- Ktor 使用 3.5.1、Ksoup 使用 0.2.6；没有复制 Android 的 trust-all TLS 与 hostname verifier。
- 登录协议对象已对密码、学号、CSRF、CAPTCHA、Cookie、响应正文和 URL 查询参数做字符串脱敏；Desktop 当前共 35 个测试全部通过，iOS Simulator 共享目标、Xcode App 与 Android APK 构建成功。
- M3 已接入共享 Compose 登录 UI；Android/iOS 紧凑窗口和 macOS 宽窗口首屏已用 Computer Use 实际验证并保存脱敏证据。
- iOS 26.5 与 Pixel 10 Pro XL Android 17 的 KMP App 都已通过真实 MIS→CAS 重定向、Cookie、HTML 隐藏字段、CAPTCHA 提交、MIS profile 解析与 AA module 10 联动，并显示“登录成功”。
- 真实验收发现 CAS POST 有时已建立 MIS Cookie 但最终响应地址仍停留在 CAS；当前协议会主动探测 MIS home，再决定是否失败，因此 iOS 最终版本已验证一次 CAPTCHA 提交直接成功。
- 真实验收发现 iPhone 紧凑高度下主登录按钮不易到达；紧凑介绍区和表单间距已调整，按钮现在直接出现在 iPhone 17 Pro 视口内。
- 真实凭据只在 Computer Use/临时本地输入脚本与本机移植版内短暂使用，随后清除变量、临时脚本和完整截图；泄密扫描为 0 个文件命中，成功页未保存为证据。
- 已发现并修复 KMP 客户端缺少浏览器 User-Agent 的协议差异（原 Android App 登录请求带桌面 Chrome UA）；修复已进 `KtorSchoolHttpTransport` 并通过测试与三端重建，但单独不能解决当前登录阻塞。

## M4：持久化与账户安全

- M4 已接入共享账户安全协调器和版本化凭据载荷；记住选项只在登录成功后保存，取消选中与退出登录都会清除凭据和普通偏好。
- Android 使用 Keystore AES/GCM 加密后只在私有 SharedPreferences 保存 IV 与密文，debug 合成凭据运行烟测为 `SECURITY_SMOKE_PASS`；该烟测 Activity 仅存在于 debug APK，release 清单只有正式 MainActivity。
- macOS 使用 Security.framework Keychain，JNA 5.17.0 只负责本机框架调用；合成凭据真实 Keychain 往返测试通过，自包含 App 使用 Temurin JDK 25 打包成功。
- iOS Keychain 实现已经通过 Simulator arm64 与真机 arm64 编译；未签名 Simulator 运行返回 `errSecMissingEntitlement (-34018)`，伪造 ad-hoc entitlement 的试验被拒绝启动并已完整回退，后续必须使用合法 Apple 签名身份验证。
- M4 普通缓存采用 SQLDelight 2.3.2，当前 schema v2 包含成绩、课程、考试、作业、成绩自选、账号元数据和全局设置七张表；业务缓存按 `account_scope` 隔离，密码、Cookie、CSRF、CAPTCHA 与可复用会话禁止写入 SQLite。
- 已实现真实 `v1 → v2` 迁移、旧数据登录后认领、关闭重开、损坏后单次重建、账号范围清理和全局设置容错；退出登录会清除当前账号的安全凭据、内存会话与普通缓存，不删除其他账号缓存或全局普通设置。
- M4 后 Desktop 共 43 个测试全部通过；Android debug/release、iOS Xcode arm64 Simulator、iOS 两架构共享编译和 macOS 自包含 App 均构建成功。Android、iOS 与 macOS 已分别真实创建数据库；iOS 和 macOS 完成终止/关闭后的二次启动，macOS SQLite 只读检查为 `quick_check=ok`、`user_version=2`。
- macOS 自包含运行时已补入 `java.sql`，数据库和 Keychain 工厂移出 Compose 重组路径；Computer Use 已确认首次启动与二次启动均显示完整宽窗口登录页。
- Android 首次显示新 UI 曾耗时约 23 秒；Ktor transport 改为惰性创建后，冷启动复测 `TotalTime 13065 ms`、`WaitTime 16158 ms`，有效但仍未达到理想首屏速度。
- `./gradlew projects`、`:shared:desktopTest`、`:androidApp:assembleDebug`、`:desktopApp:compileKotlin`、`:desktopApp:createDistributable`、`:desktopApp:packageDmg`、`:shared:linkDebugFrameworkIosSimulatorArm64` 与 Xcode Simulator build 均成功；DMG SHA-256 为 `8119d38cf44d2d3d45cc707c8db51d7416f71c00029165de3c3d7bcf5416dc0c`。
- Android KMP APK 已在 Pixel 10 Pro XL API 37.1 实际显示；iOS KMP APP 已在 iPhone 17 Pro iOS 26.5 实际显示；macOS 自包含 `.app` 已启动。三端显示同一份共享 Compose 页面，Computer Use 已验证平台名、紧凑/宽布局和按钮展开状态。
- 视觉验收发现并修复 Android `MissingResourceException`：新的 Android-KMP library target 必须设置 `androidResources.enable = true`，否则共享 Compose resources 不进入 APK。
- 视觉验收发现并修复 iOS `PlistSanityCheck` SIGABRT：`Info.plist` 必须包含 `CADisableMinimumFrameDurationOnPhone = true`。
- macOS 打包使用本机 Temurin JDK 25 的 `jpackage`，Java/Kotlin bytecode 对齐 JVM 21；分发包版本因 macOS 规则使用 `1.0.0`。

## M5：核心功能纵向迁移

### M5-0 首页与登录状态（含 M5.5 前置现状）

- M5 首页状态切片已完成首版：登录后默认进入首页，严格解析 `newmail_count`、`ecard_yuer`、`net_fee`，账号隔离缓存支持失败保留旧快照；邮件卡进入既有邮箱切片。
- 2026-08-01 用户指定 iOS 与 Android“完美校园”均用默认浏览器打开微信小程序链接 `https://wxaurl.cn/RLEw5IMZRKl`，macOS 显示同一链接的二维码并提示手机微信扫描；三端校园网续费统一由默认浏览器直接打开，不再分享或复制。
- iOS 实机确认：校园网进入 Safari 学校域名并在返回后保留会话；完美校园进入 `wxaurl.cn` 的“完美校园·小程序”落地页，用户确认落地正确且安装微信时会继续唤起小程序；因 Simulator 无微信，不宣称本次已在微信内打开（macOS 二维码与 Android 浏览器动作的独立复验尚未执行，见 memory 当前痛点）。
- 2026-07-31 登录页验证码显示放大：最小宽度 220dp、高度 96dp，改用 `ContentScale.Fit` 保留像素比例；macOS 自包含包已重建成功（`createDistributable` BUILD SUCCESSFUL，ad-hoc arm64）。旧包多次提交仍被 CAS 统一“登录未通过”；新包启动后 Computer Use 暂时无法附着新 Bundle ID `team.bjtuss.bjtuselfservice.kmp.macos`（list_apps 可见 isRunning=true，get_app_state 返回 Invalid app/timeout），因此放大后的验证码可读性与新会话作业/课件真实验收尚未完成。
- M5 首页周议程与 DDL 已接入：按 v1.7.0 的周一至周日口径聚合作业开始、截止（截止时间减 1 分钟归日）和考试日期，未提交且 0..48 小时截止的作业单独提醒；首页刷新并行协调状态、作业、考试和教学周既有模型。iPhone 七日按钮/日期切换/两条事件和 macOS 宽布局已用虚构数据做 Computer Use 视觉验证，期间发现并修复宽屏 DDL 全宽按钮；临时入口及示例值已清零，正式 iOS/macOS 重新显示登录页。新增 4 项测试后全量 Desktop 200 项零失败零错误零跳过，三端构建、Xcode host、macOS arm64 与严格签名检查通过。详见 `docs/migration/m5-home-agenda-result.md`。
- M5 首页四类数据变动信息流已接入：成绩、课程表、考试、作业刷新成功后按稳定身份比较旧/新快照，重复身份按出现次序配对，首次无缓存同步只建基线不误报全量新增；账号隔离未读记录可持久化、去重、限制 100 条并按域/全部清除，首页支持摘要、前后详情和真实页面跳转。2026-08-01 iOS 真实刷新后缓存计数为成绩 26、课程 43、考试 9、作业 4，信息流只有 1 个账号基线、无真实变化事件，`quick_check=ok`。因此刷新与空变化落地通过，但新增/修改/删除仍缺服务器真实样本。详见 `docs/migration/m5-home-changes-result.md` 与 `docs/migration/m7-apple-real-audit.md`。

### M5-1 成绩

- M5 第一个业务切片“成绩”已完成实现：登录后的 transport 与成绩 Repository 共享 Cookie 会话，顺序拉取 `ln/lr`，严格解析和去重；缓存优先、失败保留旧数据、账号隔离、选择恢复及退出清理均已接入。
- 成绩行与自选记录现由同一 SQLDelight 事务原子替换；本地写入失败测试证明原成绩与选择快照保持不变，避免第一张表已换而选择表失败的半更新。
- 成绩共享 UI 已覆盖同步/缓存/空/错误、学期多选、三态排序、自选课程加权平均和详情；iPhone 使用单列与底部 sheet，macOS 宽窗口使用侧栏和列表—详情。
- M5 后 Desktop 共 57 个测试零失败、零跳过；Android debug/release、iOS Simulator/arm64、macOS distributable 和 Xcode iPhone 17 Pro iOS 26.5 arm64 构建成功。release APK 仅含正式 MainActivity，凭据值在 `MisSecret.md` 外扫描命中为 0，冻结摘要仍为 `a2b8313387fa6902cb8297554a2724c63dfddcfdd800f5f536374e44bbe1c73f`。
- 最新 iOS/macOS M5 构建已实际启动并用 Computer Use 重新填入本地凭据、获取新 CAPTCHA；当时停在提交前，真实成绩同步与交互尚未宣称通过（后由 2026-08-01 M7 真实会话补齐）。

### M5-2 课程表

- M5 第二个业务切片“课程表”已实现：教师映射、本学期、选课课表和 HTTPS 当前周提示组成完整远端快照；课程与当前周按账号同事务缓存，失败保留旧快照。
- 登录后应用壳现支持成绩/课程表导航；iPhone 课表采用星期切换与纵向节次，macOS 采用七日网格和常驻详情。周选择按 v1.7.0 源码修正为“全部 + 1–26 周”，此前实机截图只同时显示了前 14 项。
- 课程表新增 12 项测试，全量 Desktop 现为 69 项并通过；Android debug/release、iOS 两架构、macOS distributable 和 Xcode Simulator 构建成功。
- 课程表 iOS 构建与 2026-07-30 聚合门禁均已通过；课程表真实数据运行与视觉验收当时待风控解除后的登录（后由 2026-08-01 M7 真实会话补齐）。

### M5-3 考试安排

- M5 第三个切片“考试安排”已完成：单一教务接口严格解析、缓存优先 Repository、类型筛选/详情状态、iPhone 卡片与底部详情、macOS 列表—详情，以及登录后第三个导航入口；8 项测试已进入 123 项全量门禁并通过。

### M5-4 作业列表与详情

- M5 第四个切片“作业列表与详情”已完成：智慧教学平台会话、学期/课程/三种任务/详情/附件元数据解析、缓存优先 Repository、课程多选、隐藏过期、三态截止时间排序、48 小时统计、iPhone 底部详情与 macOS 列表—详情，以及登录后第四个导航入口。用户先授权 macOS、后于 2026-08-01 授权 iOS 明文会话，远端采用平台注入策略：Android 只校验 HTTPS；iOS/macOS 只允许 `123.121.147.7:88/ve/`，`sessionid` 仅驻内存并参与请求字符串脱敏，页面常驻不可关闭风险提示。Computer Use 已成功填写新会话凭据与 CAPTCHA 答案，等待动作时提交确认。
- 教师附件元数据已进入详情；系统文件保存/预览、学生已交附件下载、作业上传和原生 HTML 容器明确留到 M6，当前 UI 不提供伪完成按钮。
- M5 作业任务核心测试、2 项文件网关契约测试及 multipart 脱敏测试均已通过（16 项）。

### M5-5 课件与课程资源

- M5 第五个切片“课件与课程资源”已写入工作区：严格 JSON/HTML 解析、账号范围嵌套缓存、完整递归树、单文件 ticket 下载、状态模型和登录后第五个导航入口均已接入。iPhone 使用课程选择和文件夹逐级进入，macOS 使用课程列—Outline—常驻详情。
- 课程与文件夹目录导出会保留相对层级，并已改为会话式流：先让用户选择 Android 文档树、iOS `UTTypeFolder` 或 macOS 目标目录并创建本次新根目录，再逐个下载和写入；全部成功才提交，失败或取消会回滚。三端共用安全导出名称和大小写不敏感冲突分配规则。用户先授权 macOS、后将授权扩展到 iOS；现 iOS/macOS 课件 API/资源只允许 `123.121.147.7:88/ve/`，教学日历只把 iframe 末五个安全路径段重建到 `:1936/kk/rp/`；Android 仍拒绝旧 HTTP。测试覆盖换主机/端口/协议和路径穿越。真实课件树/下载当时待新 macOS 登录会话（后由 2026-08-01 M6/M7 真实会话补齐）。详见 `docs/migration/m5-courseware-plan.md` 与 `docs/migration/m7-apple-real-audit.md`。

### M5-4/M5-5 2026-08-02 真机同步性能收尾

- iPhone 15 Pro Max 真机基线分段日志确认：课件旧实现需等待 15 门课程全部首层后才发布，完整刷新 12.143 秒，其中多门课程首层单次请求为 2.5～5.3 秒；作业旧实现串行执行 15 门课程×3 类型共 45 个列表请求并串行补 4 个得分，完整刷新 16.258 秒。
- 作业改为列表与得分各最多 3 个有界并发，去掉默认 80 ms 人工间隔，`awaitAll` 后仍按原请求描述顺序展平；并发上限、最终顺序和既有失败语义均有测试。真机清缓存复测完整刷新为 5.026 秒，另一轮为 5.140 秒，较基线约缩短 69%。
- 课件改为先返回并缓存课程目录，当前课程首层优先按需加载，切换其他课程时再加载对应首层，文件夹继续逐级按需加载；旧缓存树按课程身份保留，缓存格式升级到 v3，v1/v2 继续按完整加载语义迁移，完整目录导出前仍补齐目标课程与子树。清缓存两轮真机复测：目录 1.911/1.742 秒，当前课程首层 0.151/0.144 秒，从请求开始到首层数据齐约 2.06/1.89 秒。
- 成绩、作业、课表、考试四项自动同步对未保存设置统一默认开启，已保存的 `false` 不被覆盖。诊断完成后删除全部 `PerformanceTrace` expect/actual 与 `BJTU_PERF` 调用；最终无日志签名包通过 Desktop 全测、iOS Simulator 全测、KMP Android Debug 构建和 iPhone 覆盖安装。未提交、未打标签、未推送。

### M5-6 其他功能（校历 + 成绩单）

- M5 第六个切片“其他功能”（校历 + 中英文成绩单下载）已完成：校历走 `bksy.bjtu.edu.cn` 公开页面（聚合多 script 块解析 `url` 字段，单双引号均可，域名边界强制校验），成绩单走 `aa.bjtu.edu.cn` 会话接口（非 PDF 响应判会话失效）；下载产物经 `HomeworkFileGateway.saveFile` 系统面板保存，取消不显示红色错误。校历真实下载 9,744,588 字节。2026-08-01 iOS 有效会话已补齐中英文成绩单端到端：两个系统保存面板文件名分别为“中文成绩单”“英文成绩单”，均取消保存且恢复默认中文版。详见 `docs/migration/m5-other-function-plan.md`。
- 已修复中文 URL 未编码导致 404；2026-08-01 已用 iOS AA 会话补齐中英文成绩单真实下载与系统保存面板，不再保留“待风控解除”的阻塞。

### M5-7 教室人数评估

- M5 第七个切片“教室人数评估”已完成：11 栋教学楼、严格 JSON、名称/空位/容量筛选与四维排序、失败保留旧快照、快速切楼竞态保护、iPhone 两级列表与 macOS 列表—详情；应用壳新增第七入口。新增 14 项测试，全量 Desktop 166 项零失败，三端构建通过；真实网络验收思源楼 37 间教室（时间窗口 21:17:17—21:17:58）。HTTPS TLS 握手失败，故不加 ATS/Android cleartext 例外；iOS/Android 明确安全不可用，macOS 仅限精确明文 origin 并提示仅供参考。详见 `docs/migration/m5-classroom-plan.md`。

### M5-8 设置

- M5 第八个切片“设置”已完成首版：登录后新增设置入口；跟随系统/浅色/深色通过 SQLDelight 普通设置持久化并从根 `MaterialTheme` 即时生效；账号、v1.7.0 对齐基线、GitHub、当前账号离线缓存清理与安全退出路径已接入。自动同步、Material You、壁纸/Haze 和 Android APK 更新没有跨端生效链路，因此只显示边界说明，不提供假开关。新增 5 项测试，全量 Desktop 171 项零失败零跳过，正式源码三端构建和 Xcode host 均成功。
- 设置页 Computer Use 预览实际发现 iPhone 两个长按钮并排拥挤，已改为紧凑窗口纵向全宽、macOS 宽屏保持并排；iOS 深色切换与缓存确认框、macOS 浅色切换和关闭窗口后再次激活保持主题均通过。2026-08-01 iOS 真实账号缓存往返已执行：清除前成绩/课程/考试/作业为 26/43/9/4，确认清除后四类表、筛选和同步元数据均为 0，深色主题与会话保留；逐页重新同步后恢复 26/43/9/4，信息流仅重建无事件基线，`quick_check=ok`。退出账号仍留到当前会话不再需要时验证。详见 `docs/migration/m5-settings-plan.md`。

## M6：平台能力

### M6 文件/WebView/邮箱/macOS 菜单与窗口生命周期

- M6 作业文件共享协议已继续写入：`SchoolHttpRequest` 支持结构化 multipart，Ktor transport 发送二进制；远端支持教师附件下载、`piGaiDiv` 已交附件发现/下载，以及“临时上传回执 → `sendStuHomeWorks`”两阶段提交。
- 文件名、二进制正文、提交说明、上传回执列表、`sessionid` 和查询参数均不进入请求字符串；文件端点仍只允许学校 HTTPS origin，相关测试已通过。
- Android SAF、iOS `UIDocumentPickerViewController`、macOS 系统文件对话框三种文件网关已接入 `App`；共享详情可下载教师/已交附件，上传界面要求“选择并检查文件列表 → 提交”。
- 2026-08-01 M6 文件能力真实验收与 GB18030 修复：macOS 作业教师附件 DOCX 与课件 RAR 已真实落盘并验证文件头。iOS 随后暴露 Ktor common charset 不支持 GB18030、静默回退 UTF-8 的真实缺陷；改为 `decodeLegacyGb18030OrNull` expect/actual——Android/Desktop 用 JDK GB18030，iOS 用 CoreFoundation 编码常量与 Foundation NSString 解码。修复版实机 4 个中文附件名完整还原（照片 PNG、讲解脚本、视频、实验报告 DOCX），点击中文附件下载后 iOS 系统保存面板也显示正确文件名，随后取消未落盘。目标 GB18030 测试在 iOS Simulator 通过；教室数据源 legacy 可用性改为可注入后 iOS commonTest 全量 223 项全部通过。
- 2026-08-01 macOS 真实会话已完成下载侧验收：教师附件（任务书 20KB DOCX）经 NSSavePanel 落盘 20832 字节、真实 Word 头；课件微积分 d11_24 RAR 经 ticket→下载→NSSavePanel 落盘 5599196 字节、真实 v5 归档。iOS 作业、课件和中英文成绩单的系统保存面板均已用真实会话通过；各面板均在最终保存前取消。用户确认当前没有新布置、可用于上传验收的作业，因此真实上传延期到学校后续布置作业时再做，不制造作业或上传无关文件。详见 `docs/migration/m6-homework-files-plan.md` 与 `docs/migration/m5-courseware-plan.md`。
- M6 网页容器基础设施已完成：共享 `WebPageRequest`/`WebCookie`/`SchoolWebDomainPolicy`（仅允许 mis/cas/aa/bksycenter/dean 五个学校域名，Cookie 域名与页面域名必须匹配，强制 HTTPS，字符串化脱敏）、expect `SchoolWebView` 与三端 actual（iOS WKWebView+NSHTTPCookie 同步+非学校域名分流外部浏览器；Android WebView+CookieManager；macOS 按既定方案走系统浏览器引导卡）。新增 7 项域名校验测试，全量 Desktop 130 项测试零失败，三端构建与 Xcode build 全部成功。真实网页验收当时待登录风控解除（后由 2026-08-01 邮箱实机确认补齐）。
- M6 第一个具体网页页面“校内邮箱”已接入为第九入口：transport 只导出对 MIS module 26 生效的内存 Cookie，模型把 Domain 收窄到精确 `mis.bjtu.edu.cn`；网页策略新增 `mail.bjtu.edu.cn` 导航 host，同时拒绝 `.bjtu.edu.cn` 过宽父域并改用 Ktor URL host 解析。iOS 等全部 Cookie 写入 WKWebsiteDataStore 后才加载；Android 内嵌 WebView；macOS 系统浏览器不接收 App Cookie 并明示可能需要重新登录。2026-08-01 iOS 真实邮箱由用户确认肉眼正常且可用；网页自身仍是学校桌面布局，macOS 仍保留系统浏览器的平台差异。详见 `docs/migration/m6-mailbox-plan.md`。
- M6 macOS 原生菜单已接入：平台侧“前往”十个入口连接共享 `AppSection`，“数据”连接当前页面刷新，快捷键为 `⌘,` 设置和 `⌘R` 刷新。`AppCommandBus` 使用无重放 SharedFlow，未登录命令不排队；订阅数驱动菜单可用性。Computer Use 在最新正式构建展开菜单，确认登录页十个入口均由系统标记为 disabled，快捷键标签正确。新增 2 项测试后全量 Desktop 193 项零失败零错误零跳过，三端构建、Xcode host、macOS arm64 与严格签名检查通过。详见 `docs/migration/m6-macos-menu-plan.md`。
- M6 macOS 窗口生命周期已修正：关闭按钮只隐藏同一个原生窗口，不销毁 Compose 树或会话；JDK `AppReopenedListener` 在 Dock/系统重开时显示并聚焦原窗口；数据库只在 application 真正退出后的 `finally` 中关闭。Computer Use 用未持久化的“记住登录信息”勾选做无敏感标记，证明关窗后进程仍运行、重开后状态仍保留，恢复标记后 `⌘Q` 使进程终止；退出后 SQLite `quick_check=ok`、`user_version=2`。新增 3 项测试后全量 Desktop 196 项零失败零错误零跳过，三端构建、Xcode host、macOS arm64 与严格签名检查通过。详见 `docs/migration/m6-macos-lifecycle.md`。
- 课程表小组件不再属于当前 M6：2026-08-01 已按用户决定移除 iOS WidgetKit 扩展、App Group entitlement、共享快照发布和预览入口。等 Android/iOS/macOS 公版主应用完全稳定后，再依据 1.7.0 周视图基线另立切片设计、实现和验收。

## M7：Apple 设计与可访问性

- macOS 已完成首页和九入口首轮真实验收：成绩/课表/考试同步成功，教室真实数据、登录态原生菜单跳转、`⌘R`、主题 `Dark → System` 持久化、关窗不退出和重开保留页面均通过；作业/课件失败。数据库脱敏计数为成绩 26、课程 43、考试 9、作业 0、数据变动基线 1，`quick_check=ok`。详见 `docs/migration/m7-apple-real-audit.md`。
- 2026-07-31 iOS 跨日重试已按 Computer Use 动作时规则取得用户确认并提交新 CAPTCHA，学校仍返回统一“登录未通过”；等待可能令 CAPTCHA 会话过期，未连续提交第二张。
- M7 首批可访问性修复已闭环：真实 AX 树发现登录页保存凭据控件被拆成无名称按钮和文字，现把登录、作业课程筛选、教室空位筛选、成绩单语言四处开关改为单一有名称/状态、最小 48dp 控件，并为成绩自选框增加课程级标签。最新 macOS AX 为具名复选框；iOS AX 有完整名称并在切换时出现 `selected`，恢复后消失。Desktop 224 项测试与三端/Xcode 构建通过；后续运行态 VoiceOver 专项已按用户决定移出当前范围。
- 智慧教学真实安全边界已确认：v1.7.0 使用 `http://123.121.147.7:88`（教学日历另有 `:1936`）；假设的 HTTPS 域名同路径由 curl 与 CIO 均返回 404，旧端口无凭据 HTTP 探针可达。用户已明确授权 iOS/macOS 登录会话使用精确旧 HTTP；代码以平台/端点/路径白名单、iOS 固定 IP ATS 例外和常驻 UI 警告实现，Android 不放宽。224 项测试和三端构建通过；Computer Use 已填好新会话 CAPTCHA，但尚未获得本次提交确认，真实同步仍未执行。
- iPadOS 可访问性基线已补齐：iOS actual 监听系统内容大小、增强对比度、减弱动态效果和降低透明度通知；12 档 Dynamic Type 映射到 Compose 字号，最大无障碍字号下宽屏降为单栏，恢复默认后运行中即时回到双栏。减弱动态效果把 Material 空间动画改为 `snap` 并只保留 120–180ms 效果反馈；降低透明度让显式半透明层变为不透明。iPad Pro 11-inch (M5) / iPadOS 26.5 正式产物已完成四项系统偏好的 Computer Use 切换、视觉和 AX 现场复核，测试后开关均恢复；Computer Use 无法可靠注入 Simulator 触控拖动，用户随后将进一步最大字号手势专项移出当前范围。
- 2026-08-01 iOS 明文策略边界实机验收（iPhone 17 Pro iOS 26.5 Simulator，真实登录会话）：作业/课件在 VerifiedHttps 下正确拒绝 module 28 → 明文 `thirdLogin` 302 降级，明文请求未发出。发现并修正拒绝文案误报：握手停在未放行 3xx 原统一映射 NETWORK（"请检查网络"），现区分安全拒绝与网络故障、先抛 SECURE_CHANNEL_UNAVAILABLE；作业页实机显示"已拒绝降级到明文连接"、课件页"没有提供可验证的 HTTPS 通道"，不崩溃可返回。新增 stopsHandshakeAtPlainHttpRedirect 测试锁定；Desktop 全量、Android debug、iOS framework、macOS distributable 门禁全绿。iOS/Android 明文不放宽边界成立。
- 2026-08-01 用户随后明确授权 iOS 与 macOS 使用相同的智慧教学旧 HTTP 精确范围，暂不考虑 App Store 上架。端点更名并集中为 `AppleLegacyHttp`/`smartPlatformEndpointFor`：iOS/macOS 使用固定 `:88/ve/` 与 `:1936/kk/rp/`，Android 仍为 VerifiedHttps；iOS `Info.plist` 只为固定 IP `123.121.147.7` 添加 ATS 明文例外，端口/路径仍由共享白名单限制。Apple 紧凑全局顶部及相关页面常驻不可关闭的窃听/篡改风险提示。iPhone 17 Pro iOS 26.5 真实登录后，作业同步 4 项实验报告、课件同步 7 门资源树；微积分 d11_24 RAR 详情与 iOS 系统“文件”保存面板验证通过。Desktop 238 项、iOS Simulator 223 项零失败；Android debug、iOS framework、macOS distributable、Xcode App 全绿。
- 2026-08-01 App Store 加密合规：iOS `Info.plist` 与 macOS（jpackage 打包后经 `FinalizeMacDistributable` 用 PlistBuddy 写入并重签名）均声明 `ITSAppUsesNonExemptEncryption=false`（仅用系统 HTTPS/Keychain，属豁免加密），两端构建验证提取值均为 `false`。
- 2026-08-01 macOS 作业/课件明文链路端到端打通：逐跳诊断定位三层真实缺陷并修复——①MIS module 28 以裸 HTTP 302 指向明文 `thirdLogin`，Ktor 拒绝 HTTPS→HTTP 降级跟随，新增共享 `followSmartHandshakeRedirects` 逐跳白名单校验后手动跟随（明文跳限精确 apiOrigin，HTTPS 跳限 cas/mis 主机，降级不扩散白名单）；②会话经握手最后一跳 `ve/s.shtml` 的 `Set-Cookie: JSESSIONID` 下发，article 只返回文章列表无 `sessionId` 字段，改为优先从 transport Cookie 读 `JSESSIONID` 填 `sessionid` 头、article JSON 解析作回退；③服务器对"没作业/没课件"返回 `STATUS:"2"`（"没有数据"），KMP 严格解析曾当失败致整批中断，现对齐原 Android moshi 默认值容错识别为合法空列表；④课件递归拉取资源树时慢子文件夹请求触发默认超时，transport 显式配 connect 15s / socket/request 30s。真实验收：作业同步出人工智能基础及应用 4 项实验报告（全部已提交含截止时间/提交人数/评分），详情正文完整渲染；课件同步出 7 门课程资源树（微积分4项、概率论8项、毛概10项、两门空数据容错、另两门各1项），教学日历按钮随会话启用。

## M8：发布准备

- M8 Apple 发布元数据首轮闭环：iOS/iPadOS 和 macOS 正式图标、隐私清单均进入最终 `.app`；iOS 为实际使用的 `NSUserDefaults` 声明 CA92.1，并仅为智慧教学固定 IP 添加 ATS 例外，macOS 修正独立 Bundle ID、教育类目、最低 11.0 并以配置缓存兼容任务把清单放入 `Contents/Resources` 后更新 ad-hoc 封印。iPad 主屏 Computer Use 发现 v1 角色过小并完成 v2 放大复测；通用 Simulator Xcode `BUILD SUCCEEDED`，macOS arm64 自包含包严格签名检查通过。v1.7.0 仍只是功能基线，KMP 自身保持 iOS/Android/shared 0.1.0、macOS jpackage 1.0.0。Developer Team、真机、Archive、正式签名、公证和 App Store 隐私问卷仍未闭环，见 `memory.md` 当前痛点与 `docs/migration/m8-apple-release-readiness.md`。
- 2026-08-01 小组件范围调整：删除 `CourseScheduleWidget` target、宿主 WidgetKit 监听、App Group、共享课表 JSON 发布链路、预览入口和专项测试；发布脚本改为反向校验旧 `.appex` 不得残留。移除后 Desktop 233 项与 iOS Simulator 218 项测试零失败；Android debug/release、iOS framework、arm64 Xcode 宿主、macOS 自包含应用和 Apple 元数据脚本通过，最终 iOS `.app` 没有 `PlugIns` 目录；重新安装到 iPhone 17 Pro Simulator 后，Computer Use 确认既有登录页正常显示。公版完成前不要恢复这些代码或相关验收任务。
- 可编辑草稿已写入 `docs/migration/m8-release-notes-draft.md`；正式版本号、日期、支持/隐私网址和已知限制仍须发布者审阅，当前没有执行任何提交、标签、推送或发布操作。

## M10：平台原生导航与动效（2026-08-05）

- 背景：Navigation 3 `NavDisplay` 的 Compose 模拟动画经用户多轮主观验收判定"不够原生"（Android 只有渐变/缩放、前景动后景不动；iOS 也不如系统）。决定把紧凑端二/三级页导航交给平台原生容器，共享 `NavDisplay` 退居宽屏与回退路径。计划与强制语义见 `docs/migration/native-platform-navigation-plan.md`。
- 共享层：新增 `AuthenticatedSession` 集中登录后 profile、各 ScreenModel、偏好、文件网关与退出能力，`LoginRoute` 唯一发布与撤销会话；`AuthenticatedDestinationApp(routeId)` 渲染单个二级目的地；`isNativeDetailRoute` 白名单仅含 EXAMS/COURSEWARE/CLASSROOMS/CLASSROOM_DETAIL/MAILBOX/CALENDAR_DOWNLOAD/REPORT_CARD_DOWNLOAD/SETTINGS。`nativeNavigationEnabled` 时紧凑端二级页不再压共享 NavDisplay（转场 None）；底栏五个一级 tab（首页/课表/成绩/作业/更多）与 MoreGroupSections 除 MORE 外不相交，只清栈即时切换，永不触发原生 push。
- Android：新增 `NativeDetailActivity`（`enableOnBackInvokedCallback=true`，系统 cross-activity 与 predictive-back 动画，不拦截系统返回；`onStart/onStop` 观察会话，会话撤销即 finish）与进程内 `AndroidAuthenticatedSessionRegistry`（不落盘；registry 缺失时 detail 立即 finish 回根，不伪造会话）；`MainActivity` 发布会话、对二级路由 `startActivity`，并预热 WebView 内核。
- iOS：Swift 新增 `NativeNavigationController`（隐藏导航栏）：登录态经 `onAuthenticatedSessionChanged` 传入、登出 `popToRootViewController(animated: false)`；二级入口创建 Kotlin `NativeDestinationViewController` 并系统 push（`restorationIdentifier` 去重防连点），Compose 返回箭头接 `popViewController(animated: true)`，leading-edge 返回由 UIKit `interactivePopGestureRecognizer` 提供。
- 修复 iOS 26 启动/转场闪退：崩溃报告为 Compose iOS `AccessibilityElement.cachedProperties` 的 `EXC_BAD_ACCESS`（`KERN_INVALID_ADDRESS at 0x18`）——辅助功能客户端（含 Computer Use 自动化 AX 查询）在原生 push/pop 移除宿主控制器后继续查询已失效的 Compose 无障碍元素。查证 CMP 1.11.1 klib 无 `accessibilitySyncOptions`/`accessibilityEnabled` 公开 API，K/N UIKit 绑定也不暴露 `accessibilityElementsHidden`，最终在 Swift 侧对所有 Compose 宿主视图设 `view.accessibilityElementsHidden = true`（与"无障碍专项移出当前范围"决策一致）。代价：AX 自动化读不到 App 内容，iOS 界面验证只能靠截图/目视。
- 修复 iOS push 转场状态栏区域截开：SwiftUI 宿主此前只对底边 `ignoresSafeArea`，导航控制器被约束在状态栏下方，push 页面盖不住状态栏。改为四边全屏 `.ignoresSafeArea(.all)`；同步移除 `CompactAppTopBar` 中"iOS 不加 `statusBarsPadding`"的 2026-08-04 特判（该特判建立在旧宿主假设上，教训：宿主 inset 假设不要固化进共享平台特判）；紧凑登录页仅 iOS 补 `statusBarsPadding` 保持原居中布局；宿主底色对齐页面背景防深色首帧闪白。Android/macOS 行为不变。
- macOS：维持 JVM `desktopApp` 侧栏即时切换；SwiftUI 原生宿主未开始，旧桌面包保留。
- 验证：`:androidApp:assembleDebug`、`:shared:desktopTest`、iOS Simulator `xcodebuild` 构建/安装/运行全部通过，无新崩溃报告；用户目视确认 iOS push 转场（含状态栏覆盖修复）OK。工具边界：macOS 录屏隐私限制下 `simctl io screenshot` 与 Computer Use 截图读黑帧（App 实际正常）；Computer Use 坐标无法可靠起始于设备屏幕边缘，边缘手势与转场观感以用户目视/真机录屏为准。
- 同批修正：成绩课程性质映射 `courseTypesByCode` 改为可空，区分"从未成功同步培养方案"与"全部落入其他类别"，避免方案未刷新时误导分类筛选；`GradeRepository`/数据源/Model/测试同步调整。

## 紧凑壳、静默登录与列表 UI（2026-08-04～08-06）

### 紧凑端导航壳与静默自动登录（2026-08-04）

- Apple Design 框架：紧凑端底部 5 tab（首页/课程表/成绩/作业/更多）；顶栏大标题+同步指示；考试/课件/教室/邮箱/设置/校历与成绩单下载收进「更多」；明文 HTTP 风险提示仅在作业/课件等授权页出现。Android 明文策略与 iOS/macOS 对齐走 `LegacyHttp`/`networkSecurityConfig` 仅放行 `123.121.147.7`。
- 静默自动登录：登录成功写入最小档案快照（姓名/学号/身份/学院）到 CacheStore metadata；冷启动有凭据且有档案时跳过登录页，顶栏先「登录中」再「同步中」；`entryLoggingIn` 门控 ScreenModel 网络初始化，避免无会话请求；多次失败主界面引导弹窗回登录页；凭据恢复完成前不渲染，消除登录页闪帧。Android 模拟器冷启动截图验证；iOS 真机用户确认（需先有一次成功登录写档案）。
- 「更多」结构调整：校历下载/成绩单下载独立进根目录；子页顶栏返回箭头；「更多」子页隐藏底栏并以 `navigationBarsPadding()` 占位；设置页大标题仅宽屏显示。成绩单语言改为中/英分段按钮。
- 校历下载页增加「当前最新」文件名（`fetchCalendarFileName` 只解析不下载 PDF）；失败静默 null。
- 邮箱首进：Android 预热 WebView、iOS 常驻 prewarmedWebView，MailboxWorkspace 延迟约 450ms 再初始化，减轻转场卡顿。
- 本地四端分发包曾输出到根 `builtapps/`（gitignore）：1.7.0 原版 APK、KMP Android/iOS/macOS debug 或开发签名包，非正式发布。

### 成绩课程性质映射（2026-08-05）

- 成绩接口表格无课程性质列；性质从培养方案 `/training/training/program/` 列表与全部 `stuview/<id>/` 交叉比对（rowspan/colspan 课组跟踪），产出课程号→必修/限选/任选/体育映射，与成绩同事务落库表 `program_course_type_cache`（SQLDelight `2.sqm` v2→v3）。
- 自选模式五类三态 chips（必修/限选/任选/体育/其他类别）；体育因学校 PDF/方案页/成绩单口径不一致独立类别；课程号贪婪匹配吞字 bug 已修。desktopTest/assembleDebug 通过。开放问题见根目录 `体育课疑惑.md`。计划文 `docs/migration/m9-grade-course-type-plan.md`。

### 紧凑端列表 UI 与刷新策略（2026-08-06，提交 `c1bd8d1`）

- **成绩**：筛选 sheet 排序改为「维度圆角矩形 + 方向胶囊」；`ORIGINAL_REVERSED` 默认（教务原序倒排=从新到旧）；分数从高到低/从低到高；自由选择课程用 Switch；性质胶囊在自选模式下绑 `selectedGradeIds`，文案 `已选/总数`，0 门为全部未选淡色三态。
- **作业**：对齐课表/成绩——同步态顶栏右上；Banner 内筛选+排序图标开 sheet（课程、截止两矩形「显示全部日期」「隐藏已过期」、排序）；改排序不算「已筛选」；状态条与课表 Banner 风格对齐。
- **课表**：Banner 与星期行固定在列表外；下方 `LazyColumn` 用 `weight(1f)` 占满剩余高度，短内容也可过滚。
- **同步门控**：登录成功后才网络自动同步；Workspace 初始化只灌缓存；`entryLoggingIn` 门控 shell；成绩/作业/考试/课表 `initialize` 分缓存与网络两阶段。排序切换 LazyColumn 回顶。进度条钉在顶栏下。
- **去掉下拉刷新**：曾尝试 Material3 PTR、CMP 挂 UIRefreshControl、无 event handling 过滚等，均与系统过滚互抢或误触；根因是 Compose LazyColumn 主手势不在可稳定挂 `UIRefreshControl` 的 UIScrollView 上。产品决定删除全部分端 `AppPullToRefresh`，可刷新页在「已同步」旁圆形刷新按钮；保留平台原生 overscroll bounce。用户明确日后 SwiftUI 重写再做 `.refreshable`。
- 验证：`:shared` desktop / iOS Simulator / Android 编译与 grade 相关单测通过。真机目视刷新按钮与课表过滚仍待用户确认，未写成验收完成。

## 紧凑端 UI 真机验收与作业详情二级页（2026-08-07）

- **作业详情弃用 M3 `ModalBottomSheet`**：CMP iOS 上表面层与正文分层动画错位（文字/背景不同步）、`skipPartiallyExpanded` 全高顶状态栏、关手势又不能下滑；升 material3 1.12.0-alpha03 后真机仍有三处异常（背景滑满全屏、遮住底部两行文字、全屏回半屏时文字跳动）。曾自绘单节点整体平移弹层被用户否掉（坚持用原生组件）。最终仿 `CLASSROOM_DETAIL` 改**原生二级页**：点卡片先 `selectHomework`（从 `showDetails` 拆出的非 suspend 同步写选中）再 push，routeId `HOMEWORK_DETAIL`，Android `NativeDetailActivity` / iOS UIKit push 通用处理、平台侧零改动；上传对话框统一 `AlertDialog`；顶栏固定「作业详情」，正文不再重复标题。宽屏仍是列表+侧栏并排，行为不变。
- **依赖升级**：`composeMultiplatform 1.11.1→1.12.0-beta03`、`composeMaterial3 1.11.0-alpha07→1.12.0-alpha03`（为修 sheet 所升；sheet 弃用后升级保留，惠及筛选等其它 sheet；navigation3 仍 1.1.1，三端编译过）。
- **顶栏同步胶囊**：首页此前没传 `idleStatusText`，空闲时只剩孤 sync 图标；现聚合 home/homework/exam/course 的 failure 与 source 显示「已同步/同步失败/未同步」。空闲态图标由双弧 sync 改 Canvas 对勾（`TopBarSyncedIcon`，沿用自绘不引入图标库约定），各业务页胶囊同步生效。
- **课件**：选课弹窗 `skipPartiallyExpanded` false→true（修卡半高锚点、须点把手才展开、底部一截够不着；与全项目其它 sheet 对齐）；同步后并发预载各课顶层目录（`loadCoursesConcurrently`，Semaphore 限流 2 路、单课失败不拖垮整批、一次合并落库），`ensureInitialized` 加互斥防并发握手踩坏会话；文件大小单位格式化 + `CoursewareSizeFormatTest`。
- **教室**：搜索改 `BasicTextField` 居中；引导 Banner（请选择教学楼/人数仅供参考，登录态只显示一次）。**考试**：Banner 对齐成绩/作业，类型 chips 进 sheet，长类型名完整多行显示。
- **成绩详情解析与信息流防抖**：教务 `data-content` HTML 保留换行（`<br>`/块级结束标签→换行再去标签），旧缓存单行按「平时/期中/期末/实验/最终/总评成绩、备注」等字段名断行；抽出共用工具 `schoolRichTextToPlainMultiline`（+测试）。成绩信息流按业务字段判等，忽略本地 id 与详情 HTML 解析抖动；原/现展示文案相同的「修改」不进信息流、不展示（`GradeSemanticEqualityTest`）。
- **遗留**：课表课程详情弹窗仍是 `skipPartiallyExpanded=false`（全项目唯一一处），若用户反馈卡半高再对齐 true。
- **验证**：`:shared` desktop / iOS Simulator / Android 编译与 desktop 单测通过；**用户 iPhone 真机目视通过**（作业详情二级页跳转/返回、首页与各科「已同步 ✓」胶囊、课件选课弹窗展开）。

## M11：教室占用查询与壳层/教室体验修复（2026-08-07）

新功能，原冻结 Android 1.7.0 **没有**。入口「更多 → 校园 → 教室占用查询」，与第三方「教室人数估计」并存。

### 数据与解析

- 教务 `aa.bjtu.edu.cn/classroom/timeholdresult/room_view/?zc=<周>&jxlh=<楼ID>&page=1&perpage=500`（可选 `zxjxjhh` 学期）。HTML 表：行首教室号+容量，49 格（7 天×7 节）靠 `title`+`background-color` 映射占用类型。
- 色值与线上图例核对：`#e46868` 排课、`#9e6868` 调课、`#394ed6` 考试、`#77bf6d` 实验、`#d8cc56` 其他、`#fff` 空闲；展示层空闲改软绿 `#D8F5E2`/`#0D6B35` 与其它态拉开对比。
- **`jxlh` 必须传数字楼 ID**（传中文楼名线上返回空表头——单楼全空 bug 根因）。35 栋教学楼名单与 ID 取自线上 jxlh 下拉，顺序与线上一致（末几栋含外校区，按序列表不额外分区）。
- 学期下拉来自同页 `zxjxjhh`；当前学期多由脚本 `$("[name=zxjxjhh]").val(...)` 回填而非 `selected`，解析需脚本兜底。占用成功响应顺带解析学期，避免单独预取失败后弹层只剩「当前学期」。
- 周→日期：bksy `SemesterTranPage.aspx?noRemark=1` 的 **hidJson**（ASP.NET 动态渲染，静态表格无用）；标题 `hidTitle_<Id>` 只有 id 无 name。aa `zc` 跳过「休」列，第二学期夏季段从 19 续编。
- 节次时间：`SLOT_TIME_RANGES` 与 aa stuschedule 表头一致（第1节 08:00-09:50 … 第7节 21:00-21:50）。

### 交互与导航

- 紧凑端两级：一级教学楼列表，二级选中楼占用视图；routeId `CLASSROOM_OCCUPANCY` / `CLASSROOM_OCCUPANCY_DETAIL`，仿教室人数估计原生 push。
- 周/学期：`OccupancyWeekPickerSheet`（学期横滑 chips + 周 FlowRow，「本周（第N周）」快捷）；默认周跟课表 `currentWeek`。星期客户端筛选，课表同款「一二三四五六日」七等分。
- `selectBuilding` 只同步写状态再 push；查询由详情/工作区 `LaunchedEffect(selectedBuilding)` 在 Idle 时补发（避免点楼等网络、push 动画被拖）。
- 切周保留旧列表 + 顶栏/细进度；有失败/超时不清空已成功列表。

### 网络与「同步中」卡死

- 共享 `KtorSchoolHttpTransport` 会话锁保护 Cookie jar。bksy 校历与 aa 不同域：若走同一 `execute`，代理下 bksy 挂起会堵所有 aa 请求。新增 **`executePublic`**：独立客户端、不进会话 Mutex、更短超时；校历只走公开通道。
- 弹层选周真因（真机转一分钟、列表仍在、顶栏一直「同步中」）：`rememberCoroutineScope()` 在弹层内 `launch{selectWeek}` 后立刻 `onDismiss`，弹层销毁取消协程，`refreshing` 永不清除。**改详情页 `hostScope` 发起查询**；12s 超时必清进度。Live probe（本机登录）：思源西楼首查约 1.6s、切周约 1.4–2.2s、结束后 `isLoading=false`。
- `initialize` 不再预取学期/校历占锁；校历/学期空结果允许弹层再试。

### 同里程碑壳层与教室人数估计

- 底栏：从每个 `DestinationPage` 挪到 `NavDisplay` 外；一级 tab `yield()` 后再换栈，避免首次点 tab 水波纹被整页销毁掐断。
- 教室人数估计搜索：本地 draft + 约 180ms 防抖；`visibleClassrooms` 写入 state 时预计算，去掉 composition getter 重算。
- Android 恢复 `yaya.csoci.com` 明文（`network_security_config` + `classroomLegacyHttpAvailable=true`），人数估计可用。
- 更多校园分组顺序：教室占用查询 → 教室人数估计 → 邮箱；功能名「教室占用查询」。
- 多页 `ModalBottomSheet` 恢复 `sheetGesturesEnabled=true`（可下滑关闭）。
- iOS Xcode `embedAndSignAppleFrameworkForXcode`：Compose 1.12 `syncComposeResourcesForIos` 需要 `UNLOCALIZED_RESOURCES_FOLDER_PATH`；Build Phase 脚本缺省时从 `CONTENTS_FOLDER_PATH`/`FULL_PRODUCT_NAME`/`PRODUCT_NAME` 推导，去掉仅 `--no-daemon` 导致的慢冷启动。`gradle.properties` 增加 `android.experimental.disableCompileSdkChecks=true` 以过 compose 1.12 AAR compileSdk 37 元数据检查（本机 SDK 36.1）。

### 验证边界

- commonTest：classroomoccupancy 解析/ScreenModel、classroom 搜索缓存用例通过；三端编译路径与 assembleDebug 曾通过；Live probe 本机网络通过。
- 用户 iPhone 真机：占用查询进楼/切周/弹层、人数估计明文、空闲绿色与底栏反馈有多轮目视；代理下 bksy 日期仍可能空（不挡占用）。未做正式签名分发。

## 教室占用页筛选区重组（2026-08-10，worktree，未并 main）

- 背景：占用二级页筛选区信息密度不合理——节次时间 7 个大节默认全展开占 3 行，把高频操作的星期条挤到远离教室卡片；图例 7 项平铺且「其他/未知」语义重叠。用户截图给出方向，两轮确认定稿。
- 改动（仅 `ClassroomOccupancyScreen.kt`，不动 domain/data 层）：
  - 星期条 `OccupancyCompactDaySelector` 从 `ClassroomOccupancyFilters` 拆出，挪到图例下、紧贴教室卡片。
  - `OccupancyLegend` 重写为 `OccupancyLegendRow` 七等分：空闲/排课/调课/考试/实验/其他（合并未知兜底）6 项 + 第七格「时段▾」展开钮，与星期条同为七格视觉对齐；图例格为色块作背景、文字放进色块里（固定 40dp 高，避免 animateContentSize 内 fillMaxSize 撑满全屏）。
  - 节次时间 `SlotTimeRangesLegend` 默认收起，点「时段▾」展开。
  - 展开/收起动画：只用外层 `animateContentSize` 单一驱动高度，时段区仅做 `fadeIn`/`fadeOut`（不占高度），星期条与教室卡片作为同一整体平滑位移；早期嵌套 `AnimatedVisibility` 垂直动画因速率不同步、收起闪断被弃用。
  - `legendEntries()` 移除 `UNKNOWN` 项；`OccupancyKind.UNKNOWN` 兜底渲染保留（格子仍可能染未知色），仅图例不单列。
- 验证：`:shared:compileKotlinDesktop`、`desktopTest`、`:androidApp:assembleDebug`、`:shared:compileKotlinIosArm64` 与 `:shared:linkDebugFrameworkIosArm64`、xcodebuild 真机包均通过；用户 iPhone 15 Pro Max 真机多轮目视（图例样式、展开/收起动画速率与平滑度）确认通过。iOS 构建需 `DEVELOPER_DIR=/Applications/Xcode-beta.app/Contents/Developer`（本机仅 Xcode-beta 27.0，shell 默认指向 Command Line Tools 会导致 xcrun 失败）。
- 边界：另一 AI 正在 main 做 M12，本次仅 worktree 提交（最终 amend 为单提交 `710fafb`），后续由用户合并；goal.md 未改。

## M12 前置：M5.5/M5.6 收口与第一阶段发布（2026-08-09）

- M5.5 登录页 UI 优化 + 验证码自动识别、M5.6 用户手动确认全部 UI 与真实数据验证由用户确认完成；第一阶段（M0–M11 + M5.5 + M5.6）全部收口。
- KMP 三端发布 pre-release `1.7.1-KMP`，git tag `v1.7.1-KMP` 指向 `8498f32`；自该版本起取代 `v1.7.0` 成为新功能与回归验收的对照基线。
- 当前最优先转为 M12 课程表体验整合（a 日期跳转 / b 一周视图 / c 课程表与考试导出）、M13 物理在线接入；规划文档 `docs/migration/m12-course-schedule-plan.md`、`m13-phyvlab-integration-plan.md`。

## M12：课程表日期、一周表格与系统日历（2026-08-10～11，已完成）

- **日期与缓存**：课程表复用 M11 `hidJson` 校历，把 `OccupancyWeekDate` 从月日文字扩展为完整 `LocalDate`；日期选择原子更新周/星期，非教学周进入空态。Computer Use 在 iPhone 17 Pro 实测 2026-08-12 → 第 24 周周三。视觉验收还发现缓存第 23 周先到会错误关闭 `followCurrentWeek`，导致网络第 24 周不再跟随；现只有用户手选周/日期才冻结，缓存/网络自动结果保持跟随，并把支持范围从 26 周扩到 30 周以覆盖夏季续编。
- **课程浏览连续改进**：首版纵向七日卡片被用户否决，最终紧凑端改为带左侧图标的“概览表格 / 列表”，概览在左并作为默认；列表用七页横滑在当前周内切日，概览为一屏 7 天 × 7 节表格。用户进一步去掉表格上方重复周数，标题栏最终为左侧放大的五个性质色胶囊“必修/限选/任选/体育/未知”与右侧“滑动切换周数”。大日期 banner 删除，顶部日期改为“6月15日”中文月日。iPhone 与 Android 均完成真实视觉和手势复核。
- **课程性质与全部页**：色块不再按名称/哈希随机分配，而是复用成绩培养方案的课程号→必修/限选/任选/体育映射，课程号可剥离 `[04]` 等教学班后缀；真实缓存 `C108002B` 为必修，微积分色块已恢复红色，未知才灰色。“全部教学周”成为分页最左侧第 0 页，紧邻第 1 周，不再显示或滑动回当前周；同一格的单双周/交替课程按时间范围排序并排为半格。iPhone 17 Pro 已实测全部→第 1 周→全部双向滑动和半格详情。
- **课程日历**：课程表顶栏右上角在同步胶囊旁显示“加入日历”。当前课表类型按真实教学周逐条展开，北京时区，默认独立日历名严格为“本学期课表”/“选课课表”。iOS/macOS EventKit 创建或复用同名日历，notes 首行 `[BJTU-ID:...]` 用于更新；地点/教师/标题不进入稳定 ID，服务端顺序与展示字段变化不会产生新 UID。Apple 端保留 `.ics`，Android 走文件分享/保存。
- **考试边界**：用户明确考试服务端增删改可能滞后，禁止批量或合并导入。实现只在单场考试详情显示“加入日历”，每次生成 `listOf(exam)`，系统日历名“考试安排”，`.ics` 也只含当前一场；缺日期/开始时间时禁用，不猜全天事件。
- **macOS 真机 Calendar 验证**：打包 App 首次请求日历权限后创建 iCloud 日历“本学期课表”，新增 266 项；Calendar.app 侧栏与 2026-07-26 实际课程事件均可见。第二次加入结果新增 0、更新 266，去重/更新成立。未出现钥匙串密码框；按用户约定若出现则取消。
- **iOS EventKit 验证**：iPhone 17 Pro / iOS 26.5 Simulator 在用户手动登录后通过系统“完全访问日历”权限弹窗，真实创建“选课课表”并新增 226 项；相同范围第二次加入显示新增 0、更新 226，证明稳定标记幂等更新成立。
- **Android 真实导出验证**：Pixel 10 Pro XL 通过系统 DocumentsUI 保存“本学期课表.ics”，文件含 266 个 `VEVENT`、`Asia/Shanghai` 时区与正确结束标记。真实考试详情只出现单场“加入日历”，保存的“考试安排”文件恰好含 1 个 `VEVENT`，时间为 2026-07-01 09:00–11:00；未出现批量考试入口。
- **Xcode Beta**：`xcode-select -p` 仍是 `/Library/Developer/CommandLineTools`，没有改全局；`desktopApp` Core ML/Swift helper 的 `xcrun` 子进程在未显式设置时优先 `/Applications/Xcode-beta.app/Contents/Developer`。Xcode Beta iPhone 17 Pro/iOS 26.5 host build 成功。
- **门禁**：`AcademicCalendarExportTest` 覆盖真实周展开、中文/数字考试时间、非法日期不猜、稳定 ID 顺序/教室/教师变化；`CourseScheduleScreenModelTest` 覆盖缓存→网络当前周、手选冻结、日期与第 27 周续编。`:shared:desktopTest`、`:androidApp:assembleDebug`、Xcode Simulator host、`:desktopApp:packageDistributionForCurrentOS` 均成功；macOS app 内含 Calendar helper、日历用途说明且 `codesign --verify --deep --strict` 通过。
- **完成边界**：M12 已完成并保持未提交状态；未执行提交、标签、推送或发布。未来学校出现新的课程/考试时间文本时，仍需按“无法解析则禁用/跳过，不猜日期”的现有边界补回归样本。

## 决策记录

- 2026-07-29：采用 Kotlin Multiplatform + Compose Multiplatform。
- 2026-07-29：Android、iOS、macOS 都在目标范围内。
- 2026-07-29：从当时的 `main@9d8da18` 创建迁移分支 `ZJG`，该提交保留为分支创建点。
- 2026-07-29：正式 Release `v1.7.0@419313d` 发布；发布与功能对齐基线由 `9d8da18` 调整为 `419313d`，当前冻结 Android 根工程不因此自动合并或改写。
- 2026-07-29：冻结现有 Android 工程；KMP 在独立 `multiplatform/` 工程中并行实现，不修改 `app/` 和现有根构建配置。
- 2026-07-29：Apple 端允许共享 Compose UI，但必须做平台自适应，不能只放大 Android 页面。
- 2026-07-29：Apple 验证码先支持手动输入；自动识别作为非阻塞增强，必须通过端到端精度验证后再启用。
- 2026-07-30：普通缓存采用 SQLDelight 2.3.2，业务缓存按账号隔离，全局普通设置独立保留；数据库损坏只允许删除并重建一次，敏感凭据和可复用会话不得进入 SQLite。
- 2026-08-01：iOS/macOS 智慧教学旧 HTTP 精确放行（`:88/ve/` 与 `:1936/kk/rp/`），iOS ATS 只覆盖固定 IP，Android 继续拒绝；暂不考虑 App Store 上架。
- 2026-08-01：iOS/macOS 小组件（WidgetKit/App Group/共享快照）移出当前公版范围，公版主应用完成后再开发。
- 2026-08-01：进一步无障碍专项（VoiceOver、最大字号手势等）暂时移出当前范围，不作为完成门禁。
- 2026-08-01：真实作业上传延期到学校后续布置作业时再做；不制造作业或上传无关文件。
- 2026-08-05：紧凑端二/三级页导航交给平台原生容器（Android 系统 Activity、iOS UIKit `UINavigationController`），Compose `NavDisplay` 只保留宽屏/回退路径与一级 tab 容器；底栏一级 tab 永不触发原生 push。
- 2026-08-06：去掉 Compose 下拉刷新（与系统过滚互抢）；紧凑可刷新页改用顶栏圆形刷新按钮 + 平台原生 overscroll；原生 SwiftUI `.refreshable` 留到日后 SwiftUI 重写再做。
- 2026-08-09：第一阶段（M0–M11 + M5.5 + M5.6）全部收口，KMP 三端发布 pre-release `1.7.1-KMP`（git tag `v1.7.1-KMP`，`8498f32`）；自该版本起取代 `v1.7.0` 成为新功能对照基线，后续新功能走 M12+ 独立里程碑。M12 定为课程表体验整合（a 日期跳转 / b 一周视图 / c 课程表与考试导出），M13 为物理在线接入。

## M12 后续：选课校历、重复日程与桌面切周修复（2026-08-11）

- **旧学期导入根因与修复**：`CourseScheduleScreenModel` 原先只有一份共享 `academicWeeks`，切换到选课课表仍复用当前第二学期/暑假校历，导致下一学期课程写到旧日期。现按课表类型保存校历映射：本学期使用当前学期；选课课表使用语义上的下一学期（第二学期→下一学年第一学期）。下一学期校历缺失时禁用导出，不回退到旧学期。真实 macOS 导出弹层核对为 `2026-2027-1`，第 1 周从 `2026-09-07` 开始。
- **可批量修改后续课程**：课程不再展开成几百条独立事件，而是按课程号、星期和节次合并为 weekly recurrence；停课、单双周与不连续周通过例外 occurrence 保留，重复缓存行先合并。`.ics` 输出 `RRULE`/`EXDATE`；iOS/macOS EventKit 使用 weekly `EKRecurrenceRule`，更新已有系列时使用 future-events span，旧受管独立事件会按稳定标记迁移，用户自行创建的日程不删除。考试保持单条导入，不进入重复系列。
- **真实 EventKit 证据**：macOS 已登录开发包对“选课课表”首次导入显示新增 21 个系列、更新 0；第二次显示新增 0、更新 21。Calendar.app 侧栏出现“选课课表”。日历 App 被用户同时操作时 Computer Use 停止抢焦点，因此没有用自动化修改真实课程来弹出范围选择；重复规则与 future-events 更新由 EventKit 实现、ICS 断言和专项测试覆盖。
- **桌面课程表**：宽屏七日网格接入与成绩页相同的课程号→性质配色，未知才灰色；表格标题栏左侧显示必修/限选/任选/体育/未知图例，右上角新增上一周/下一周圆形按钮。Computer Use 实测第 24 周→第 23 周（8月3日）→第 24 周（8月10日），banner 与七列日期同步。
- **触摸板横滑**：桌面 `actual` 监听水平滚轮事件，累计 36px 才翻页；纵向分量占优不触发，同一段惯性在 180ms 安静间隔前只翻一周。Android/iOS `actual` 返回原 Modifier，避免桌面 API 污染移动端。Computer Use 对非原生滚动容器的水平 scroll 没有合成真实触摸板事件，因此自动化只覆盖按钮与状态机；真实触摸板手感保留用户复核边界。
- **桌面翻页动画**：按钮与触摸板横滑共用 `selectedWeek` 驱动的方向性 `AnimatedContent`；下一周从右向左、上一周反向，使用无回弹 spring 且可被连续操作打断。图例和切换按钮固定，旧周课程数据随旧表格滑出，避免动画期间新旧周日期与色块错配。重打包后 Computer Use 实测第 2 周→第 1 周，日期和课程内容同步切换；自动截图只覆盖动画完成态。
- **门禁**：`:shared:desktopTest`、`:shared:compileKotlinIosSimulatorArm64`、`:androidApp:assembleDebug`、`:desktopApp:createDistributable` 同次成功；macOS EventKit Swift helper 编译通过。新增测试覆盖下一学期选择、缺失禁用、重复行合并、RRULE/EXDATE、横滑方向/阈值/惯性锁。仍未提交、打标签、推送或发布。
- **系统 Xcode 选择更新**：2026-08-11 用户明确要求并在 macOS 管理员授权弹窗中手动确认，将全局 `xcode-select` 从 `/Library/Developer/CommandLineTools` 切换到 `/Applications/Xcode-beta.app/Contents/Developer`。切换后复核 `xcode-select -p` 为 Beta 路径，`xcrun xcodebuild -version` 为 Xcode 27.0（Build `27A5228h`），`xcrun --find simctl` 为 Beta 内的 `usr/bin/simctl`。

## M12 收口：紧凑文案、地点层级与 macOS 原生触摸板（2026-08-11）

- **日期语义分离**：概览表格不再保留“某一天”的选择概念，右侧统一显示“前往日期”，选择日期只前往所在教学周；列表模式仍定位到具体日期，标题在“第 x 周”后显示所选月日，小字显示当前周与今天日期。教学周弹层删除“全部位于第 1 周左侧”等实现说明。
- **日历确认文案**：课程页入口改为“添加到日历”；课程弹层标题按类型显示“导出本学期课表到日历”或“导出选课课表到日历”，删除重复规则说明，成功消息明确日历名与新增/更新数量。考试仍为单场入口，确认弹层直接展示考试名称、时间、地点，成功消息明确写入“考试安排”的数量。
- **地点层级**：服务端 `Course.coursePlace` 继续保存原值；UI、首页变化与课程日历导出在边界处按中英文逗号分段、整体倒序并用 `-` 连接。规则不依赖首段是否为“xx校区”，专项测试同时覆盖“海淀西校区，思源楼，SY101”和“研究生唐山研究院，教学楼，A101”。
- **触摸板原生化**：先后验证 Compose 距离累计方案会产生单次长滑跨多周、惯性尾流及 2～5 秒假冷却，最终改为打包 AppKit helper 读取 `NSEvent.phase`/`momentumPhase`。一次手指手势达到横向阈值后最多回调一页，抬手惯性完全忽略；下一次 `Began` 立即解锁，不再推测冷却时长。Compose 状态机仅作为 helper 缺失时回退；按钮与手势继续共用方向性、无回弹、可中断的 `AnimatedContent`。
- **本轮门禁**：`:shared:desktopTest` 与 `:desktopApp:createDistributable` 成功，打包 dylib 导出原生分页接口；`:androidApp:assembleDebug` 与 `:shared:compileKotlinIosSimulatorArm64` 同次成功。Mac 开发包已重启供用户实体触摸板复核；未提交、打标签、推送或发布。
- **方向校正**：首个 AppKit 包实机暴露 `scrollingDeltaX` 表示内容滚动方向、与页面导航方向相反；已只反转原生回调映射，保持 Compose 回退和移动端不变。旧进程明确退出后重新启动修正版，并增加桌面测试锁定“正 delta → 上一周、负 delta → 下一周”。

## M12 最终验收（2026-08-11）

- **跨学期前往日期**：日期选择不再局限于当前课表校历。模型优先匹配当前课表，再匹配另一课表；命中另一学期时原子更新课表类型、`calendarSemesterLabel`、`academicWeeks`、周次和日期。本学期选择 9 月日期会自动进入选课课表，选课课表选择当前学期日期可切回；两套校历都不包含的日期继续显示非教学周空态。专项测试覆盖双向往返，用户在 macOS 真实会话确认可用。
- **用户最终确认**：AppKit 原生触摸板在修正内容滚动方向后，双指向左进入下一周、向右返回上一周；长滑最多翻一周，连续手势不再出现 2～5 秒冷却。跨学期“前往日期”随后由用户确认通过，M12 获准最终验收并提交。

## M12 发布：1.7.2-KMP 三端 pre-release（2026-08-11）

- **范围**：在 M12（303fb08 + 补充 8482a63）基础上，并入 worktree 两项体验改动，发布 pre-release `1.7.2-KMP`，git tag `v1.7.2-KMP` 指向 `fff061f`（main 已推送 `mine`）。自该版本起 `1.7.2-KMP` 取代 `1.7.1-KMP` 成为对照基线。
- **worktree → main 合并**：worktree `claude/frosty-williams-5d71d3` 三提交经 merge `8d22931` 并入——教室占用页筛选区/图例重组（`0f234b3`）、设置页更新检测（`cebcdb1`）、版本号统一 1.7.2-KMP（`38b1ca8`）。合并仅 `memory.md` 一处内容冲突（两条 worktree 事项状态更新），代码文件全部自动合并无冲突。
- **应用内更新检测**（新增 `shared/update/AppUpdateChecker`，kotlinx.serialization）：指向本仓库 `JasonZhang1225/BJTUselfService-KMP-Refreshed`；用 `/releases` 列表而非 `/latest`（GitHub `/latest` 不返回 pre-release，而 1.7.x-KMP 正是 pre-release），取第一条非 draft；版本比较改数字段逐段比较（原版字符串比较会误判 1.7.10<1.7.9）。设置页「版本与项目」卡手动「检查更新」+ 进主界面后静默自动检测一次（`silentOnMiss`，仅新版本弹「前往下载」跳 `release.htmlUrl`，失败/已最新静默）；结果弹窗提升到 `AuthenticatedAppShell` 壳层，任意页面可见。
- **版本号统一**：`AppUpdateChecker.CURRENT_VERSION`、androidApp `versionName=1.7.2-KMP`/`versionCode 9→10`、iosApp `CFBundleShortVersionString=1.7.2-KMP`/`CFBundleVersion 9→10`、desktopApp `packageVersion=1.7.2`。
- **构建修复**：`desktopApp:packageDmg` 的 `checkRuntime` 报「'jpackage' is missing」——Android Studio JBR 21 不含 jpackage。在 `compose.desktop.application` 加 `javaHome` 指向系统完整 JDK（默认 temurin-25，可用 `DESKTOP_PACKAGE_JAVA_HOME` 覆盖），与守护进程 JDK 解耦；Kotlin 编译仍在 JBR 下跑（避免 KGP 在 JDK 25 下 `JavaVersion.parse("25.0.1")` 崩溃），只有运行时检测/jpackage 用完整 JDK。提交 `fff061f`。
- **验证**：合并后 main 上 `:shared:desktopTest` 373 测试 0 失败（含 CourseSchedule 28、Settings 9）；`:androidApp:assembleDebug`、`shared:linkDebugFrameworkIosArm64`、`desktopApp:packageDmg` 全部 `--rerun-tasks` 真跑通过（配置缓存对 M12 补充改动误判 UP-TO-DATE，Android/iOS 强制重编后确认）。iOS 真机包（iPhone 15 Pro Max，Apple Development 签名）由用户在 Xcode 命令行 `BUILD SUCCEEDED` 后实机安装验证。
- **产物**（本地上传 GitHub Release，Pre-release）：`BJTUSelfService-KMP-1.7.2-KMP-debug.apk`（350M）、`BJTUSelfService-KMP-1.7.2-KMP-iOS-unsigned.ipa`（45M，未签名需侧载自签）、`BJTUselfServiceKMP-1.7.2-KMP.dmg`（114M，中文名「交大自由行 KMP」）。
- **release notes**：由用户在 `Desktop/172.md` 微调定稿，正文含 M12 课程表升级 / 应用内更新检测 / 教室占用页 UI 三块；发布时去掉末尾给 AI 的草稿注释段。
- **同会话操作**：应用户要求用 gh 关闭 fork 上 PR #1（Windows desktop support / codex 分支）并删除远端分支 `codex/-1.7.1-kmp-windows`；M12 两个补充提交（`fca7010` 选课学期当前周、`50739ec` 今天按钮）应用户要求 squash 为单个 `8482a63`。

## 热修发布：1.7.2-KMP-A 三端 pre-release（2026-08-15）

- **范围**：在 `1.7.2-KMP` 上修课表培养方案冷启动、成绩变动弹窗、排序文案和限选配色。git tag `v1.7.2-KMP-A` 指向 `b85747f`（main 已推送 `mine`）。
- **提交**：`d12d54c` fix 四项体验；`b85747f` chore 版本标识 1.7.2-KMP-A。
- **版本**：`AppUpdateChecker.CURRENT_VERSION` / Android `versionName` / iOS `CFBundleShortVersionString` = `1.7.2-KMP-A`；`versionCode` / `CFBundleVersion` 10→11。desktop `packageVersion` 仍为 `1.7.2`（jpackage 只允许三段数字），`packageBuildVersion` = 11。未用 `1.7.2-A`：现有比较器后缀字典序下 `A` < `KMP`，会被当成更旧。
- **验证**：`:shared:desktopTest --tests '*AppUpdateChecker*' --tests '*SettingsScreenModel*'` 通过；Android `assembleDebug`、`desktopApp:packageDmg`、iOS `xcodebuild` Release `generic/platform=iOS` `CODE_SIGNING_ALLOWED=NO` 均成功。平板已确认成绩变动弹窗、新装分类色块与限选配色。
- **产物**（本地上传 GitHub Release，Pre-release）：`BJTUSelfService-KMP-1.7.2-KMP-A-debug.apk`（350M）、`BJTUSelfService-KMP-1.7.2-KMP-A-iOS-unsigned.ipa`（38M）、`BJTUselfServiceKMP-1.7.2-KMP-A.dmg`（114M）。
- **发布页**：https://github.com/JasonZhang1225/BJTUselfService-KMP-Refreshed/releases/tag/v1.7.2-KMP-A

## M14：Windows 桌面端移植（2026-08-16，windows-dev 分支）

- **目标与基线**：KMP Windows 桌面应用，UI 复用 commonMain 共享 Compose，功能对齐 `1.7.2-KMP-A` 之后 1 个提交（`93b3d0a`）；验证码自动识别复用原版 Android torch 模型。规划：`docs/migration/windows-port-plan.md`。
- **模块架构**：KMP 不允许同一模块声明多个 jvm target（`jvm() Kotlin Target Already Declared`），故 `shared` 不加 windows target；新增独立 `multiplatform/windowsApp`（`jvm("windows")` target）`implementation(project(":shared"))` 复用 shared desktop 产物（全部 commonMain 业务/UI + desktopMain 平台实现）。唯一 shared 改动：`Platform.desktop.kt` 按 `os.name` 返回 Windows/macOS displayName；`PlatformFamily` 枚举不修改，Windows 复用 `MacOS` 桌面 UI 分支。
- **平台实现**（windowsApp windowsMain）：
  - DPAPI 凭据保险库（JNA 绑 `Crypt32.CryptProtectData/CryptUnprotectData`，DATA_BLOB 16 字节结构，`WString` 描述参数；密文 base64 存 `java.util.prefs`）。实现中修复两处：结构字段顺序（先 cbData 后 pbData）与 `Memory(2*POINTER_SIZE)` 分配（初版 12 字节越界 `IndexOutOfBoundsException`）。
  - 缓存 `%LOCALAPPDATA%/BJTUselfServiceKMP/bjtuselfservice_cache.db`（SQLDelight JDBC，同套 `openCacheStoreWithRecovery`）。
  - `WindowsHomeworkFileGateway`：AWT FileDialog + 临时文件原子写（`ATOMIC_MOVE` 回退），实现作业上传/附件保存/课件目录导出。
  - 网页引导卡片 + 系统默认浏览器；Ktor CIO；GB18030 解码。
- **验证码（DJL 复用原版 torch 模型）**：`WindowsTorchCaptchaRecognizer` 用 `ai.djl.pytorch:pytorch-engine:0.33.0` + `pytorch-native-cpu:2.5.1:win-x86_64`（libtorch 2.5.1）加载 `multiplatform/androidApp/src/main/assets/BJTUCaptcha.pt`（23.6MB，23,599,429 字节）。预处理与 Android 完全一致：ImageIO → 130×42 双线性缩放 → RGB → CHW → `[0,1]`；输出 `8×1×15` logits 走共享 `decodeCaptchaLogits`（15 类字符表/8 时间步/CTC 折叠/0.55 置信度）。模型复制到 `windowsApp/src/windowsMain/resources/`，运行时解出到 `%TEMP%/bjtu-kmp-captcha/`。实现中修复：DJL 输出 NDArray 依附于 predictor 的 manager，须在 `manager.use{}` 内先 `toFloatArray()` 再关闭（否则 `Native resource has been released already`）。
- **logits 对齐验证**（验证码策略第 4/5/6 层）：Python torch 2.13.0+cpu（venv `bjtu-captcha-venv`）加载同一 `.pt` 作参考，与 Windows DJL 输出逐值比较：
  - 单图（seed 20260816）：max abs diff 0.000006、mean 0.00000139、argmax 逐时间步一致。
  - 三图（seeds 20260816/20260817/20260818）：max diff 0.000007/0.000012/0.000005，argmax 全部一致。
  - 诊断参数：`--verify-captcha-model=<图>`（表达式/答案）、`--dump-captcha-logits=<图>`（原始 120 个 logits）。
- **测试**：`windowsApp:windowsTest` 4 个测试通过（DPAPI 往返/清除/篡改防护/Preferences）；`shared:desktopTest` 385 个测试中 384 通过，唯一失败 `MacOsKeychainCredentialVaultTest.syntheticCredentialRoundTripUsesSystemKeychain` 为平台边界（Windows 无 `/System/Library/Frameworks/Security.framework`，`UnsatisfiedLinkError`，macOS 上不受影响）。关键业务屏模型测试（课程表 28、成绩、设置）全部通过。
- **打包与运行**：`createDistributable` app-image `BJTUselfServiceKMP.exe`（552KB launcher + runtime，免安装直接运行）；`packageExe` jpackage 安装器 `BJTUselfServiceKMP-1.7.2.exe`（113,475,584 字节，需完整 JDK——JBR 无 jlink/jpackage，本机用 Microsoft JDK 21.0.8 `C:/Users/zjg/jdk21/jdk-21.0.8+9`，`WINDOWS_PACKAGE_JAVA_HOME` 可覆盖）。打包后 EXE 启动窗口「交大自由行 KMP」，截图验证深色主题配色精确匹配共享 token（`0xFF101216` background / `0xFF17191D` surface）。
- **版本**：windowsApp `packageVersion=1.7.2`（jpackage 三段数字限制），窗口标题/应用名「交大自由行 KMP」，vendor `BJTUselfService Contributors`。
- **边界与待办**：24 张真实冒烟集样本不在本机（logits 已与 Python torch 逐值对齐证明同模型同语义，真实正确率评测待样本）；真实登录会话逐页复测待账号条件；Android/iOS 编译验证在 Mac/装有 SDK 环境补做（本机无 Android SDK）。未提交、未打标签、未推送。
- **图标与深色标题栏（2026-08-16 补充）**：
  - 图标：从 `branding/AppIconMaster-v2.png`（白色底主 logo）生成 Windows 图标——`BJTUselfServiceKMP.ico`（16/24/32/48/64/128/256 多尺寸圆角 RGBA）嵌入 EXE（`windows { iconFile }`），`app-icon.png`（64px 圆角 30% RGBA）作窗口标题栏图标（Compose `Window(icon)` + AWT `iconImages` 16/32/48 多尺寸让系统按 DPI 选最佳，避免缩放模糊）。圆角角落透明（alpha=0 露出标题栏/桌面），主体不透明白底。其他平台图标（branding xcassets、androidApp mipmap、desktopApp icns）零改动。
  - 深色标题栏：Windows 用 JNA 调 DWM `DwmSetWindowAttribute(DWMWA_USE_IMMERSIVE_DARK_MODE=20)`（`applyWindowsDarkTitleBar`，Compose `window.windowHandle`），标题栏跟随系统深色，不再白底；macOS desktopApp 加 `-Dapple.awt.application.appearance=system`（标题栏跟随系统外观，JDK 17+ 属性）。
  - 修复：窗口图标渲染尺寸从 32px 逻辑降到标准 16px 逻辑（250% DPI 下 40px 物理），logo 清晰度从 10px 提升到 14px 高。
- **Review 修复（2026-08-16）**：`WindowsTorchCaptchaRecognizer` 每次识别 `manager.use{}` 会关闭 base manager 导致第二次起全部失败——改为每次推理用 `holder.manager.newSubManager()`（predictor 输出依附于输入 NDArray 的 manager，关闭子 manager 不影响 holder）；模型懒加载加 `synchronized(modelLock)` 防并发重复加载；`DataBlob.free()` 同时释放结构与数据两段原生内存（原只清结构体）。新增 `WindowsTorchCaptchaRecognizerTest`（同进程连续 4 次 + 并发 4 路推理全过）。调试用 `System.err` 诊断输出已删除，保留 `--verify-captcha-model`/`--dump-captcha-logits` 诊断参数（与 macOS verify 参数同级）。

## 发布：1.7.3-KMP 四端 pre-release（2026-08-16）

- **范围**：M14 Windows 桌面端并入 main 后发布 `1.7.3-KMP`。git tag `v1.7.3-KMP` 指向 `84bf479`（`docs: 记录 Windows 移植 PR #2 合并与验收状态`）。用户在 Windows 创建 pre-release 并上传安装器；本机补打并上传其余三端。
- **版本**：`AppUpdateChecker.CURRENT_VERSION` / Android `versionName` / iOS `CFBundleShortVersionString` = `1.7.3-KMP`；`versionCode` / `CFBundleVersion` / desktop `packageBuildVersion` = 12。desktop/windows `packageVersion` = `1.7.3`（jpackage 只允许三段数字）。
- **本机构建**（JBR 21 编译，jpackage 走 Temurin 25；Xcode-beta 27.0 / `DEVELOPER_DIR=/Applications/Xcode-beta.app/Contents/Developer`）：
  - `./gradlew :androidApp:assembleDebug :desktopApp:packageDmg`：`BUILD SUCCESSFUL in 2m 16s`。APK `versionName=1.7.3-KMP` `versionCode=12`；macOS `.app` 显示名「交大自由行 KMP」、`CFBundleShortVersionString=1.7.3` / `CFBundleVersion=12`，`codesign --verify --deep --strict` 通过。
  - `xcodebuild` Release `generic/platform=iOS` `CODE_SIGNING_ALLOWED=NO`：`** BUILD SUCCEEDED **`（~12m）。IPA 来自 `交大自由行 KMP.app`，`CFBundleShortVersionString=1.7.3-KMP` / `CFBundleVersion=12`，无 `PlugIns`。
- **产物**（上传到既有 GitHub Release，Pre-release）：
  - `BJTUSelfService-KMP-1.7.3-KMP-debug.apk`（350M，367,313,770）
  - `BJTUSelfService-KMP-1.7.3-KMP-iOS-unsigned.ipa`（39M，40,918,470，未签名需侧载自签）
  - `BJTUselfServiceKMP-1.7.3-KMP.dmg`（114M，119,478,737）
  - `BJTUselfServiceKMP-1.7.3-KMP.exe`（108M，113,579,520，远端 Windows 上传）
- **发布页**：https://github.com/JasonZhang1225/BJTUselfService-KMP-Refreshed/releases/tag/v1.7.3-KMP
- **未做**：本机未实机安装复测 1.7.3 三端；未提交文档更新；未改 Release 正文。
- **macOS 显示名与 DMG 图标（同日补打）**：用户反馈 DMG 里应用名仍是 `BJTUselfServiceKMP`、桌面卷图标是 Java Duke。成品检查确认 Info.plist 已有中文 `CFBundleName`/`CFBundleDisplayName`，但 `.app` 文件名和卷名仍走 jpackage `packageName`，`.VolumeIcon.icns` 是 370KB Duke 而不是 69KB 吉祥物。不是 Applications 缓存。新增 `FinalizeMacDmg`：把包内应用改名为「交大自由行 KMP.app」、卷名改成「交大自由行 KMP」、卷图标换成 `BJTUselfServiceKMP-v2.icns`，并给 `.dmg` 文件写 Finder 图标。`packageDmg` 后自动跑。覆盖上传 `BJTUselfServiceKMP-1.7.3-KMP.dmg`。安装前请删掉旧的英文名副本，避免 Launch Services 继续显示旧名。

## Windows 安装器系统级路径、前台 UAC 与卸载清凭据（2026-08-16）

- **被撤回的方向**：曾把安装器改成 `perUserInstall` 以免 UAC。用户明确要求标准安装位置（`Program Files`），UAC 必须前台弹出；问题是后台闪烁，不是 UAC 本身。
- **现行做法**：`perUserInstall = false`，产出 EXE 与 MSI；推荐双击 MSI，由 msiexec 在启动时前台提权。不要改 jpackage Burn EXE 的嵌入清单（会毁掉安装包数据）。
- **卸载清数据**：账号密码在 `HKCU\Software\JavaSoft\Prefs\team\bjtuss\bjtuselfservice`，缓存在 `%LOCALAPPDATA%\BJTUselfServiceKMP`，都不在 `Program Files`。首次卸载 CustomAction 用 immediate `WixQuietExec`，实机日志 `0x80070057 Failed to get command line data`，动作被忽略。已改为 deferred + CustomActionData（`SetProperty` 与 CA 同名），命令在卸之前展开 `[LocalAppDataFolder]`。
- **作业/首页**：登录后作业自动同步最多 3 次；首页仅邮件/校园卡失败写「同步失败」，作业等切片失败写「部分同步失败」。
- **验证**：`:shared:desktopTest` 中 `HomeworkScreenModelTest`、`HomeIdleStatusTest` 通过；`:windowsApp:packageMsi` 成功；MSI 表含 `CleanupUserData` type 1089（deferred）。用户已实测旧 MSI 卸载后凭据仍在。

## 热修发布：1.7.3-KMP-A（2026-08-16）

- **版本号**：不用 `1.7.3-A-KMP`。比较器按后缀字典序，`A-KMP` < `KMP`，会被当成比 `1.7.3-KMP` 更旧；与 `1.7.2-KMP-A` 同一规则，采用 `1.7.3-KMP-A`。
- **范围**：撤回 `perUserInstall`；系统级 `Program Files`；推荐 MSI 前台 UAC；卸载 deferred 清 AppData 缓存与 Java Preferences；作业自动同步最多 3 次；首页切片失败改为「部分同步失败」。含 `1eb9939` 中文显示名。
- **版本字段**：`CURRENT_VERSION` / Android `versionName` / iOS `CFBundleShortVersionString` = `1.7.3-KMP-A`；`versionCode` / `CFBundleVersion` / `packageBuildVersion` = 13。jpackage `packageVersion` 仍为 `1.7.3`。
- **构建边界**：本机 Windows 只打 Windows MSI/EXE。Android / iOS / macOS 需 Mac 环境，本发布不上传旧三端包冒充新构建。
- **提交与 tag**：`6c69c85` chore 版本标识；tag `v1.7.3-KMP-A`。未能 squash 已推送的 `32955ad`（perUserInstall），该提交仍留在历史上，功能已被 `e7dda78` 撤回。
- **产物**（Pre-release）：`BJTUselfServiceKMP-1.7.3-KMP-A.msi`（112,914,242）、`BJTUselfServiceKMP-1.7.3-KMP-A.exe`（113,575,936）。
- **发布页**：https://github.com/JasonZhang1225/BJTUselfService-KMP-Refreshed/releases/tag/v1.7.3-KMP-A

## 热修发布：1.7.3-KMP-B（2026-08-17）

- **版本号**：用户口中的「1.7.3-B」落盘为 **`1.7.3-KMP-B`**。`AppUpdateChecker` 后缀字典序下 `1.7.3-B` < `1.7.3-KMP-A`，会被当成更旧。
- **范围**：课表当前周优先智慧教学 `getTimeList.weekCode`，失败回退教务 `room_view?zc=`；作业/课件学期空、单课 JSON 坏、`STATUS≠0` 不再整批 `MALFORMED`。新增 KMP GitHub Actions 打包。
- **版本字段**：`CURRENT_VERSION` / Android `versionName` / iOS `CFBundleShortVersionString` = `1.7.3-KMP-B`；`versionCode` / `CFBundleVersion` / `packageBuildVersion` = 14。jpackage `packageVersion` 仍为 `1.7.3`。
- **CI**：`.github/workflows/kmp-package.yml` 在 `v*-KMP*` 标签与 `workflow_dispatch` 上构建 Android debug APK、Windows MSI、macOS DMG、未签名 iOS IPA，并在 tag 上创建 pre-release。冻结根工程 `.github/workflows/release.yml` 增加 `!contains(ref, 'KMP')`，避免把 `:app` 1.7.0 打进 KMP Release。
- **提交与 tag**：`6e61e61` 教学周/作业修复；`7d85055` 版本标识与 Actions；`6f40abb` 修 macOS JDK 路径并拆 iOS job。首跑无 GitHub Release（release job 被跳过）。旧 tag 已删除，`v1.7.3-KMP-B` 改指含 CI 修复的提交后重推。
- **二跑（失败）**：Android、macOS DMG 成功。Windows WiX `light.exe` 311，中文 `packageName` 在英文 runner 变成 `????? KMP`。iOS 链接 `UIViewLayoutRegion`，Xcode 16.4 / iPhoneOS 18.5 没有该符号（Compose 1.12 需要 iOS 26 SDK）。
- **三跑**：Android、macOS DMG、iOS IPA 成功（Xcode 26.6 / iPhoneOS 26.5）。Windows 仍 light 311（中文 description）。随后一次 tag 重打补上 ASCII description。

---

## 1.7.4～1.7.6：M15 邮箱、作业提交修复与四端正式发布（2026-08-28～2026-09-17，自 memory.md 归档）

- **发布链**：`1.7.4-KMP`（四端 CI 产物、共用上传签名入库 GitHub Secrets、DMG/IPA 放 Downloads）→ `1.7.5-KMP`（版本统一，versionCode/Build 16）→ **`1.7.6-KMP` 正式发布**（run 35185935353 五 job 全绿：Android arm64 APK、Windows MSI、macOS DMG、iOS unsigned IPA、GitHub Release）。iOS Bundle ID `team.bjtuss.bjtuselfservice.kmp.ios`；Android 共用上传 keystore `~/.android/bjtu-kmp-upload.keystore`，证书 SHA-256 `5D0DABC3…C773`。
- **CI 打包收口**：Android action 只装 `platform-tools`；CI 恢复 ASCII 安装器元数据规避 WiX 311（本机保留中文默认名）；冻结旧 Android CI（`Build Debug APK`）改写 Maven 源为 Google/Central 且 KMP 提交不再触发。
- **M15 邮箱（自 `1.7.5` 保持）**：Coremail 传统前端 JSON 协议；宽屏文件夹—列表—阅读三栏/紧凑端列表→详情二级页；7 个 FID 文件夹；HTML 表格正文（Ksoup DOM 遍历）；写信/回复首版（`compose.jsp` + `mbox:compose action=deliver`，发送前确认）；真实发送/删除/移动/附件写操作与 Apple 真机端保留为后续风险验收项。
- **普通作业提交协议修复（2026-09-16）**：Chrome DevTools MCP 取证确认学生上传端点为 `homeworkUpload.shtml?noteId=<upId>`（`rpUpload.shtml` 为老师端点）；`sendStuHomeWorks` 回执校验 `flag=success`、`return_num` 传 `0`（`{}` 会得 `flag=bad` 并清掉上次提交记录）；去掉写请求的 AJAX/`sessionid`/Referer 头；上传/提交各一次瞬时网络重试 + 底层异常链文案；`SUBMIT_REJECTED` 透传服务端原文；提交接入 `SessionRefreshCoordinator`（最多两次重新认证）。`emulator-5554` 真实端到端验证：`提交人数 93/99`，docx 与中文名 PDF 均成功。回归测试 44 项通过。
- **首页教学周与周卡片（2026-09-16/17）**：`weekResolved`/`hasCachedWeek`，校历确认前显示「日程加载中」，远端裸周数不进 UI；移动端 `HorizontalPager` 手势切周、按钮仅宽屏；周/日卡片高度隔离与高度缓存刷新失效修复；**移除首页日程卡手工高度锁定与动画**（测量→写入→再测量闭环曾致模拟器 `system_server` 阻塞 11.7s、掉 124 帧、2.3G 内存）；周分页竞态区分自动跟随与手动选周。
- **会话恢复四端接入**：回前台对失效页自动重试一次；右上角刷新在内存凭据可用时最多重新认证两次，不自动跳登录。
- **其它**：物理在线详情缓存优先、详情路由与总开关、课件刷新/下载修复（`childrenLoaded` 失效）、课表模式文案、iOS 校历 `openURL`、Windows 宽屏侧栏复用物理在线开关、`DesktopTouchScroll` 平台门禁。
- **未验收边界（移交当前痛点）**：作业列表「提交人数 0/63」口径、iOS 真机签名/provisioning、M13 真实上传、验证码发布级准确率扩样、Windows MSI 卸载清凭据复测。

## 安全审计与修复里程碑（2026-09-18）

- **输入**：GLM 只读安全审计（2026-09-17），原文与修复进度归档于 `docs/security/BJTU-KMP-Security-Audit-GLM-2026-09-17.md`。用户指示：H1/H2/M1 暂缓；优先 M2/M3/M4；M5 要求本地与 CI 均可出**不改签名、可直接覆盖升级安装**的包，否则不修（已满足，故验证通过即收口）；总体要求现有用户覆盖升级即获修复、无需卸载重装。
- **M2（iOS/macOS Keychain 卸载残留）**：`AccountSecurityCoordinator.restore()` 在 `shouldRememberCredentials()` 为否时主动 `vault.clear()`。原理：iOS 卸载清 NSUserDefaults 而残留 Keychain，重装后启动即清残留；未采用审计建议的独立哨兵——新哨兵会把从未写入哨兵的升级用户误判为重装强制重登，复用既有标记则升级用户不受影响。macOS 无法检测卸载（Preferences 不随删除 .app 清除），由 M3 兜底。修改 `security/AccountSecurityStore.kt`；新增 commonTest `unrememberedStatePurgesResidualVaultContent`。
- **M3（桌面卸载残留）**：`SettingsScreenModel` 新增 `clearAllLocalData`（构造参数 `wipeAllLocalData: suspend () -> Boolean`，注意成员函数与构造属性同名会致编译错误，故异名）→ `cacheStore.clearAll()`（8 张表含 `app_setting`）+ `securityCoordinator.clear()`；设置页「本地数据与会话」卡片新增「清除全部本地数据」按钮（仅 `PlatformFamily.MacOS` 即 macOS/Windows 显示，因 Windows 复用 MacOS family），确认弹窗说明后果，成功后偏好回默认值；`LoginScreen` 装配接线。README 新增「卸载与本地数据清理」（macOS 手动清理路径：Application Support、Keychain 条目 `team.bjtuss.bjtuselfservice.kmp.credentials`、Preferences）。Windows MSI 卸载级清理为 2026-08-16 既有 deferred CustomAction `CleanupUserData`。新增 `SettingsScreenModelTest` 全量清除成功/失败两用例。
- **M4（iOS 备份排除）**：`createIosCacheStore()` 每次启动对两个候选路径的 db/`-wal`/`-shm` 设 `NSURLIsExcludedFromBackupKey`（`NSURL.setResourceValue`，尽力而为不阻断启动；WAL/SHM 可能晚于启动生成，故每次启动重设）。
- **M5（Android 签名，验证而非新修）**：代码侧为本地 commit `584d26c`（`requiredSigningSecret` 无默认回退，缺凭据即 fail）。本地验证：`~/.gradle/gradle.properties` 三凭据为强口令（非 `android`）；keystore 唯一条目别名 `androiddebugkey`（真实别名，非默认回退），证书指纹与已发布 APK 一致；`signingReport` 通过。CI：`kmp-package.yml` 从 Secrets `BJTU_ANDROID_KEYSTORE_BASE64` 还原同一 keystore 并注入三项口令。结论：本地/CI 同一签名身份，覆盖升级成立。
- **验证记录**：`desktopTest`（AccountSecurityCoordinatorTest + SettingsScreenModelTest）通过；`iosSimulatorArm64Test` 全量通过；`:androidApp:compileDebugKotlin` 通过；desktopTest 全量仅 `PackagingCiAsciiConfigTest` 2 例既有失败（stash 本轮改动后复现，属 1.7.6 打包收口遗留）。实机验证（iOS 卸载重装、iCloud 备份、桌面清除按钮 UI）未做。
- **交付状态**：全部改动在工作区未提交（等用户确认提交/发版节奏）；归档文档 `docs/security/…2026-09-17.md`、README、memory.md 已同步。

## M18：macOS 原生外观（2026-09-19 当晚，已取消，代码从未提交）

> 用户 2026-09-19 22:40 决定：**方向不认可，暂不做**。`macosArm64()` target、`shared/src/macosMain/`（18 文件）、
> `:macosApp` 模块、离线壳预览与 `docs/migration/m18-macos-native-shell-plan.md` **全部删除且不留备份**。
> 本节只留实测结论，避免将来重做时重复勘查；它不是待办。

- **做到过的程度**：`NSSplitViewController` + 真系统侧栏（`NSVisualEffectView(.sidebar)` + `NSTableView.style = .sourceList`
  + SF Symbols）+ 统一标题栏 `NSToolbar`（`sidebar.left` / 系统「边栏」标签）在本机真的起窗；真鼠标点行换目的地、
  窗口标题跟目的地；release 变体与 DMG 出过包（release Mach-O 52,769,280 B ≈50 MiB vs debug 85,777,664 B ≈82 MiB，
  DMG ≈17 MiB）。**从未**用真实凭据登录过原生版，眼验靠离线合成会话（假 profile + 抛 `SchoolNetworkException` 的
  transport + 独立缓存库 + `credentialVault = null` + 偏好不落盘）。
- **Compose 1.12.0-beta03 在 macosArm64 上没有「把 Compose 挂进调用方给的 NSView」的 seam**：
  `androidx.compose.ui.window.Window(title, size, content)` 在 macos 上是普通函数（不是 `@Composable`）能起整窗，
  但 `ComposeWindow` 类是 `private`、`contentView` 被它私有占用，其依赖的 `WindowInfoImpl` / `MacosTextInputService` /
  `NSEvent.toComposeEvent` / `registerSkikoComposeImplementation` 全是模块 internal。自写可嵌入宿主可行，
  不需要 fork Compose 也不需要 `-Xfriend`：`CanvasLayersComposeScene`（`@OptIn(InternalComposeUiApi::class)`）+
  `ComposeScene.setContent/sendPointerEvent/sendKeyEvent/render/size/close` + `FrameRecomposer`（跨分栏必须共享）+
  `SingleComposeSceneRenderingScope` + `SkiaLayer.attachTo`。`attachTo` **只能在 `viewDidMoveToWindow` 里做**，
  在 `loadView` 阶段 attach 会让 skiko `MacOsMetalRedrawer.<init>` 抛 NPE，K/N 导出函数不带 `@Throws` → `SIGABRT`。
- **硬伤（原生 Mac 包链接必失败）**：Apple 的 macOS 系统 libsqlite3 是 `SQLITE_OMIT_LOAD_EXTENSION` 编的，
  tbd 里没有 `sqlite3_enable_load_extension` / `sqlite3_load_extension`，而 SQLDelight 原生驱动
  （`co.touchlab:sqliter-driver` 的 cinterop）无条件引用 → 链接期缺符号。iOS 的系统 sqlite3 有这两个符号，所以 iOS 一直链得过。
  当晚用 `xcrun clang -c` 编的 compat `.c` 打桩（`enable`→OK、`load`→ERROR）验穿，**桩不能上生产**；
  正式方案要么自带 sqlite3（只需替这一个文件 + 两条 `linkerOpts`），要么放弃原生 target 继续走 JVM。
- **中文输入法无解**：键盘转发本身可通（公开工厂 `@InternalComposeUiApi fun KeyEvent(key, type, codePoint, is*Pressed, nativeEvent)`，
  `key` 用 Apple HID keyCode 即 `Key.DirectionLeft/Right/Down/Up` = 123/124/125/126，`codePoint` 取 `characters` 首单元，
  修饰键从 `modifierFlags` 来，`scene.sendKeyEvent` 返回 false 要回手 `super.keyDown(event)`；逐字符/空格/Shift 大写/退格/
  方向键/⌘A/Return 实测全通），但 `MacosTextInputService` 是 internal → **中文候选窗出不来**，对中文 App 是必答题。
- **K/N 调 AppKit 的实测坑**：`NSLog("…%@", kotlinString)` 变参位置直接 SIGTRAP（栈顶
  `objc_opt_respondsToSelector` ← `_NSDescriptionWithStringProxyFunc`），`toNSString` 三个 Apple target 都没有；
  `NSToolbar.items` 是只读 val，只能由 `NSToolbarDelegateProtocol` 现造项，而 `toolbar.delegate` 与 `NSApplication.delegate`
  都是**弱引用**（不宿主强持有就静默空栏 / 「窗口还在代理没了」）；`addChildViewController:` / `addChild:` / `selectRow:`
  在 macos_arm64 klib 里没有导出符号（字符串在 linkdata 里 ≠ 可用），选中要用 `selectRowIndexes(NSIndexSet.indexSetWithIndex(ULong), …)`；
  `setContentViewController:` 会按控制器 view 尺寸改窗口大小；面板从 contentView 降级成子视图后不再是 first responder
  （`mouseDown` 里要 `makeFirstResponder`）也不会自己铺满（显式 `setFrame` + 双向 autoresizing）。
  写法侧：`NS_ENUM` 常量是嵌套的而 `NS_OPTIONS` 是顶层 val 可用 `or`；缺 `@file:OptIn(ExperimentalForeignApi::class)` 时
  连 import 都报 `Unresolved reference`；`NSMakeRect` 参数名是 `w`/`h`；`NSWindow.restorable` 无可用签名要写
  `NSQuitAlwaysKeepsWindows` 域；`binaries.executable {}` 返回 `Unit`；`main()` 在包里必须显式 `entryPoint`。
- **`NSTableView` 单列侧栏两处实测**：列宽必须自己给（`NSTableColumn()` 默认宽度远小于侧栏，单元格被压窄后中文标题
  从**中段**截断，实测「教室人…」「成绩单…」）；行视图别用 `NSStackView`——SF Symbol 缺失时（`wifi.motion` macOS 没有）
  image 为 nil 会让整列错位，图标列定宽手摆 frame 才稳。
- **无障碍**：原生控件对 AX 可见（`AXSplitGroup → AXScrollArea + AXSplitter`），但 **Compose 内容对 macOS AX 不可见**
  （上游缺口，VoiceOver 等于没有），验证只能靠截图或 stdout 探针。
- **诚实性问题**：`App(captchaRecognizer = UnavailableCaptchaRecognizer)` 是默认值 → 原生 Mac 没有验证码自动识别，
  而登录页副标题写死「验证码将自动识别」，这句话在原生版是假的。
- **发布侧未做**：Developer ID 签名 + 公证始终没做（ad-hoc 包 Gatekeeper 会拦），需要用户的开发者账号。
  skiko/skia 的 `.o` 按 macOS 26 编，宿主写 `-target arm64-apple-macos13.0` 是假的最低版本；
  `linkReleaseExecutableMacosArm64` 单任务 >10 分钟。
- **连带产物（这个留下了）**：为了 iOS/macOS 共用凭据库与缓存库，`iosMain/{cache,security}/Ios*.kt` 提升成
  `appleMain/…/Apple*.kt`，`kSecAttrAccessible` 收成工厂参数 `accessibleAfterFirstUnlock: Boolean`
  （iOS 必须带 `AfterFirstUnlockThisDeviceOnly`，macOS 不能带）。iOS 侧调用点已改用 `createAppleCacheStore()` /
  `createAppleAccountSecurityStore(accessibleAfterFirstUnlock = true)`，因此这次改名**已随 M17 提交进树**，不是死代码。
- **缓存路径实测**：原生实例落 `~/Library/Application Support/databases/bjtuselfservice_cache.db`，
  JVM 版在 `~/Library/Application Support/BJTUselfServiceKMP/` 下 —— 两个不同文件，升级时缓存不会自动继承。
- **取证配方（与 M18 无关但仍有效）**：窗口被遮挡时按 id 截能拿到遮挡中的窗口内容 ——
  `screencapture -x -o -l<windowID> out.png`，id 用一小段 `swiftc` 脚本调 `CGWindowListCopyWindowInfo` 取
  （JXA 拿不到 `CFArray` 桥接、`python3 + ctypes` 那条段错误）；按 `kCGWindowOwnerName` 过滤会撞上安装版与开发实例同名，
  要按窗口标题过滤。klib 勘查必须 `grep -a` 且不套 `head`（当晚一次 `head -12` 让我误判 `NSToolbarDelegate` 未导出）。

## M17 Liquid：液态玻璃壳与 1.7.8-Liquid 四端发布（2026-09-19～09-21，自 memory.md 归档）

> 计划 `docs/migration/m17-apple-liquid-glass-shell-plan.md`；发布文档 `docs/releases/v1.7.8-Liquid.md`；
> 发布提交 `a109448`（分支 `Liquid`，已推送 `mine/Liquid`）。 Liquid iOS 主线已按用户要求标记完成。

- **发布链**：全平台 `1.7.8-Liquid`、Android/iOS/macOS build code `19`，jpackage 安装器保留三段数字 `1.7.8`。
  产物复制到 `/Users/zjg/Downloads`：macOS DMG（Apple release metadata + 严格 ad-hoc 签名校验通过）、
  Android `arm64-v8a` debug APK（安装 `Pixel_10_Pro_XL`，`dumpsys` 确认 versionCode 19）、
  iOS unsigned IPA（`xcodebuild -sdk iphoneos Release` + 无签名，仅 `Payload/`，需开发者证书重签名后才能装真机）。
- **原生 sheet 架构**：`AppleSheet.kt` 经 `LocalNativeSheetPresenter` 交给 `IosNativeSheetPresenter` +
  `UIKitSheetBridge.h/.def`，驱动真实 `UISheetPresentationController`（page sheet、medium/large detent、系统 grabber、
  交互式 dismiss）；Compose `ModalBottomSheet` 仅 Android/桌面回退。Compose root `opaque = false` 且独立 root 继承
  Material scheme/typography/shapes 防深色回退；`UIGlassEffectStyleRegular` 同用于 detent/background effect 与
  sheet 根 `UIVisualEffectView`，无自绘颜色/渐变/圆角；标题与确认/取消/关闭走 `UINavigationBar`/`UIBarButtonItem`，
  公共 API 可空标题 + `showDismissButton`。
- **sheet 覆盖面**：同步面板 `HomeSyncDetailsDialog`（iOS ≤6 行 medium、第 7 行自动展开 large）、课表日期
  `AppleSheetOrAlert`（UIKit `UIDatePicker`，原生「今天」紧邻「取消」）、成绩/课程/考试详情（考试 full-height 防按钮裁切）、
  课件详情全高 sheet、完美校园无标题 sheet（左侧蓝色「打开完美校园」+ 右侧关闭 X）、校园网充值、邮箱发送确认、
  物理在线提交/上传、设置清理/更新提示、验证码恢复与静默登录失败提示；非 iOS 保持 Material fallback 同语义。
- **原生导航栏**：一级 tab 根页与二级 push 页统一 UIKit 紧凑标题（动态 21pt 半粗、居中标签唯一承载）；
  动作统一 32×32 SF Symbol、结构键缓存滚动不重建；忙碌态原生 `UIActivityIndicatorView` 占真实槽位（点击打开同步面板）；
  日历动作在左侧；顶栏透明 `UINavigationBarAppearance`，按用户要求移除 Blur/渐变/alpha mask，`prefersLargeTitles = false`。
- **原生 tab 宿主**：`ContentView.swift` 用 `UITabBar` + 每项独立 `TabRootNavigationController` 绕过五项 More 限制；
  物理在线一级 tab（`bottomNavSections(true)` 含 PHYVLAB，More 仅总开关）；开关增删 tab 在关闭动画事务中同时更新
  item 集合与选中项；首页宿主启动物理在线自动同步。
- **登录与同步**：`entryLoggingIn` 发布为 `NativeBarAction`；首页刷新先开同步 sheet 再并行刷新；同步面板 iOS 仅右上角 X、
  有失败项才出现「重试」；native title-bar 页不再画 `LinearProgressIndicator`。
- **列表与课表细节**：作业明文提示成为 `LazyColumn` 首 item 随滚动离开；`AcademicWeekSlot` 把相邻教学周间的自然周
  展开为独立非教学周，贯通周数弹层/日期跳转/日历导出周范围/教室占用（非教学周只显占位框）；课表 Pager 只监听
  `settledPage` + 程序滚动目标标记，消除中间动画页写回造成的闪烁。
- **密码保存**：勾选状态即时同步安全存储、取消即清除；正式路径仅 `AppleKeychainCredentialVault`，无明文/NSUserDefaults 回退
  （未签名模拟器 Keychain `-34018` 属签名边界）；`AccountPreferences` 区分「明确关闭」与「旧版本无标记」，启动恢复并补写
  标记（含迁移回归测试）；烟测改用隔离 service/account/preferences key + synthetic fixture（此前 DEBUG `--security-smoke`
  误用生产 service 并在结束时清除，曾擦掉模拟器已存凭据）。
- **验证记录**：`:shared:desktopTest` 515 通过、`:shared:iosSimulatorArm64Test` 488 通过（须 Java 21；默认 Java 25.0.1
  触发旧 Kotlin/IntelliJ 解析器兼容错误）；iOS 27 Simulator 构建 + `--sheet-smoke`、`--security-smoke`
  （`SECURITY_SMOKE_PASS`）通过；Device Hub iPhone 18 Pro 已登录会话逐项复核 sheet/detent/标题/spinner/tab/完美校园/
  校园网充值/考试详情全高；Android 深色模式实机截图确认七行同步状态免滚动全可见；macOS `--sync-status-smoke` 通过。
  模拟器因签名身份更换替换了 App 容器，旧 Keychain 登录态不可复用，自动登录视觉复现待用户重登。全程未输入、输出或复制
  真实账号/密码/验证码。
- **未验收移交**：各类 sheet 最终审美、边缘返回、减少透明度、iOS 26 以下回退壳、iPad 宽屏、首页卡片交互待用户主观确认；
  unsigned IPA 未上真机。

## 安全复查（2026-09-21，只读，未改代码）

- GLM 只读复查 `1.7.8-Liquid`（网络层/本地存储/登出清理与卸载残留/依赖版本四路并行探查 + 联网 CVE 检索）：
  上轮（2026-09-17 审计 + 2026-09-18 修复）全部保持有效、无回归、无高危。剩余中危 4 项：M1 WebView 持久 Cookie
  登出不清理（Android `CookieManager` / iOS 持久 `WKWebsiteDataStore`）；M2 iOS 卸载后 Keychain 凭据残留（系统行为）；
  M3 macOS 无卸载清理（Application Support/Keychain/prefs 残留）；M4 桌面与 Windows 明文 SQLite 缓存（成绩/作业/档案）。
  低危 L1-L10（VACUUM、服务端登出、桌面日志开关、phyvlab replace 前缀化、Gradle wrapper 哈希、OkHttp 版本锁定等）。
  报告：`docs/security/BJTU-KMP-Security-Audit-GLM-2026-09-21.md`。修复由用户后续进行。


## 课程表考试、物理实验与同步调整（2026-10-05）

- 基线 `c82617c`；当前 worktree 新增物理实验账号安全存储、校园网只读同步/缓存、课表按日期与实际时段投影、实验/考试独立色块、日历包含实验选项；原四项自动同步永久开启。
- 参考项目周数规则与六个系统时段依据、改动范围及回退说明见 `docs/migration/physics-lab-schedule-result-2026-10-05.md`。只有一/两周规则仍需实际安排复核，没有将其宣称为服务端持续周数字段。
- 576 项共享/JVM 回归通过；实际 Kotlin 查询已选实验 4 个；Android debug、iOS 两目标、Windows、macOS Kotlin 编译和 iOS Simulator 完整工程构建通过。
- 实际 Compose 组件的 10 张合成图片已检查浅深色、宽窄和字体放大；临时源码 UI 进程清理。CUA 未能绑定临时应用，原生交互、安全存储新命名空间和系统日历实机验证保留为未验收边界。
- 所有改动未提交、未推送、未发布，应用版本保持 1.8.1 / Build 22，冻结根 Android 工程未修改。


### 2026-10-05：深浅色复查及安装包交付

- 按用户要求复查 14 张真实 Compose 组件渲染图；普通课程与实验/考试颜色统一取当前页面主题。576 项回归通过。
- Android Release APK、macOS DMG 及 iOS 真机 arm64 unsigned IPA 均成功打包至 Downloads；命名 `BJTUSelfService-KMP-1.8.1-physicslab-20261005-*`。
- DMG CRC 和包内签名、APK 签名、IPA ZIP 完整性与无签名状态验证通过；具体路径、字节数和 SHA-256 见 `docs/migration/physics-lab-schedule-result-2026-10-05.md` 的追加小节。没有提交、推送或发布。


### 2026-10-05 实验弹层、图例与导航模糊复修

- 确认 c82617c 工作树遗漏 audit 的 6f1ec08 玻璃渐显改动，仅移植相关实现，保留其他未提交工作。
- 实验表单补充网站凭据说明，调整原生弹层滚动与底部安全区；图例改“实验”，实验/考试增加描边并后置。
- iOS 导航按页面保存模糊进度，隐藏回调不再覆盖可见栏，返回/取消侧滑恢复目标状态。
- 共享 578 项、Swift 状态 35 检查通过，14 张合成 Compose 页面检查深浅色；Mac 锁屏导致原生触摸验收待完成。
- Downloads 已生成 physicslab-ui-fix-20261005 的 macOS DMG、Android APK、iOS unsigned IPA，1.8.1 / 22；构建及结构/签名状态校验通过。详见 physics-lab-schedule-result-2026-10-05.md。
- 无提交、推送、分支合并。


### 2026-10-05 教室人数估计短页模糊修复

用户确认前轮导航往返及 Android 测试正常。BuildingList 仅报逻辑列表偏移，未计入 iOS 短列表弹性位移；本轮复用首页实际位移算法，并确保 overscroll 仅渲染一次。共享 578 项、iOS 两目标编译与完整 Release 构建通过；Downloads 新增 classroom-glass-fix-20261005 unsigned IPA，arm64/CRC/版本/未签名校验通过。CUA 原生通道关闭，最终效果待 iPhone 复核；无提交或推送。


### 2026-10-05 功能合入 audit 并准备 main 收尾

用户确认最新 iOS 模糊修复实测无问题，授权提交推送、功能 → audit → main 合并和分支工作树清理。功能提交 67c2d0f，合入 audit 为 784d7ff；memory.md 冲突已整合，其他源码自动合并保留 audit 修复。合并后共享 596 项、Swift 35 检查以及各平台 Kotlin 编译通过。

收尾完成：audit → main 合并提交 e759c1c 已推送 mine/main，本地/远端 audit 与 codex/physics-lab-schedule 删除，fc51 工作树删除，主目录保留 main，安装包保留 Downloads。


### 2026-10-05 v1.8.1 四平台正式发布

按用户授权保存简版更新内容，保留旧版安装包表格和限制说明；发布说明提交/标签源码 0037a6c。Actions run 37262681002 的 APK、MSI、DMG、unsigned IPA 与发布任务全部成功。v1.8.1 已正式发布并设 Latest，四附件与 CI 产物大小/哈希一致，正文一致，包结构/签名状态检查通过。

## 2026-10-05：NewMigration 四项改造与本轮验收完成

在 `25bfeb5` 基础上创建用户指定的 `NewMigration` 分支。首页日程追加选中日期课表；官方教室占用入口统一并补匹配房间人数，保留既有占用分类；iOS 更新弹窗标题固定“发现新版本”、日志留边/完整滚动并移除重复渲染；暂不更新持久化抑制自动提示 24 小时；更多更名应用并改为图标网格，底栏支持固定首页/应用与最多四项自选，应用与底栏互斥。冻结原 Android 工程未改。

最终共享测试 610 项全部通过，Swift 导航玻璃回归 35 检查通过；Android、macOS、Windows、iOS arm64/模拟器共享编译通过，Xcode Simulator Debug 构建成功。Computer Use 实际检查 macOS 1080dp/420dp、iOS 窄屏、深浅色/1.5 倍文字；课程顺序和空日程、官方房间补人数和人数失败保留官方结果、底栏 2/6 项与网格互斥均通过。iOS 原生更新 sheet 的 45 段合成日志滚动到完整末尾，暂不更新实际关闭；24 小时边界及落盘恢复用注入时钟/CacheStore 测试验证。UIKit 配置描述项/重配链经源码、桥接规则与构建验证，合成导航视觉使用共享底栏；未声称真机、真实网络服务或 Windows 实际运行验收。

详细报告：`docs/migration/new-migration-result-2026-10-05.md`；本机完整日志在忽略目录 `.artifacts/NewMigration/evidence/`。改动未提交、未推送、未合并、未发布，版本保持 1.8.1 / 22。测试源码桌面和模拟器进程已结束，用户安装版保留。


## 2026-10-05：NewMigration 后续排序、AIO 鸣谢与三端本地包

用户提供的选课弹窗截图显示列表在中部截断、底下留白。移除选课列表的 72% 高度限制，以原生 sheet 可用空间为滚动视口，文件详情采用同一高度策略；iPhone 17 Pro / iOS 27 模拟器用 20 门合成课程实际滚动至最后的“验收标记”。底栏设置将已勾选项置顶，使用右侧拖动手柄保存顺序，保留 2–6 项限制；同一模拟器把“成绩”拖到“课程表”前，列表和底栏同步换序。设置与 README 新增 optsimauth 的 AIO 社区版仓库和贡献说明。正常 iPhone iOS 26+ 路径仍为 UIKit Liquid Glass 导航壳。

最终 Android Release APK、Mac DMG 与真机 arm64 iOS unsigned IPA 均成功构建并复制到 Downloads，旧文件保留。共享 611 测试全过，iOS Debug/Release xcodebuild 成功；APK v2 签名及原 KMP 证书指纹、DMG 校验和和包内签名、IPA CRC 与无签名结构均验证。详细文件名、字节数、SHA-256 和边界见 `docs/migration/new-migration-followup-2026-10-05.md`。之后源码已做本地初步提交，未推送或发布，版本保持 1.8.1 / Build 22。测试源码桌面和模拟器 app 已退出，用户原安装版未关闭。


## 2026-10-09：CITEL / Duo / MacPWDFix 合入 main

用户授权将近期开发分支整理并合入 `main`。本地 `main` 自 `100d795` 快进到 `codex/Duo` 尖端 `6663117`（16 个提交），随后记录清理并推送 `mine/main`。未推 `origin`，未打正式 `v*` 标签，未发 GitHub Release。

合入范围：

- NewMigration 此前已在 `main@100d795`。
- MacPWDFix：macOS 钥匙串合并为单一 `MacOsSecretBag` 条目。
- CITEL：作业接入、作业聚合、独立账号设置、首页正在上课、数据变动弹窗、编程作业 AC 判定。
- Duo：横屏/宽屏适配、并行同步、回顶遮挡、iOS 滑动误点、四平台闲置刷新（前台 15 分钟）、顶栏玻璃待滚动后再显示。
- 版本保持调试号 `1.8.3-alpha-ci` / Build 25；`debug-*` 标签只走 Actions 打包。

分支清理：删除本地/远端 `codex/CITEL`、`codex/Duo`，以及本地已合入的 `NewMigration`。备份分支 `backup/readme-before-squash-20261007` 与 `codex/Duo-before-cleanup-20261009` 含未进主线的独立提交，予以保留。


# 第二部分：日志（2026-10-10 起，按日期追加）

## 2026-10-10 教务限流、邮箱附件与 v1.8.3-KMP 正式发布

- 改动：修复教务限流导致首次登录同步失败；邮箱新增附件下载与预览（`4dffbab`）；宽屏邮件详情窗格恢复滚轮/触控板滚动；Android 附件“打开方式”与完美校园微信跳转修复（`d53c630`）；登录失败弹窗文案调整。Windows CI 安装器改用中文名。
- 调试包：`debug-1.8.3-Beta-ci`、`.2`（Windows 安装器名）、`.3`（邮件滚动），只出 Actions 产物。
- 发布：版本 `1.8.3-KMP` / Build 29，标签 `v1.8.3-KMP`（`7985a25`），CI run `38033203351`，发布说明 `docs/releases/v1.8.3-KMP.md`，设为 Latest。冻结 Android 的 `release.yml` 对该标签 skip，属预期。README 加入宣传图。

## 2026-10-11～12 物理演示实验、首页日程跳转与课件“未开放下载”（1.8.4-Alpha-phydemo-fix，Build 30）

- 演示实验（`f21744b`、`b91f20b`）：首页“当天实验”和“正在上”并入演示实验；登录后自动刷新改用与物理实验相同的门控，修复 iOS 原生 Tab 壳下从不自动刷新；查询页按日期排序，进入时本次会话未同步过则补刷一次。
- 首页日程跳转统一：物理在线跨课程查找活动后进二级详情（原只查当前课程，找不到就开网页）；CITEL 进对应作业二级详情（原聚合模式下跳作业一级页）。模拟器只验证了演示实验跳转。
- 课件下载（`56f8799`）：解析智慧教学 `stu_download`（网页端只有 "2" 显示下载）。未开放下载的文件在列表/详情标“未开放下载”并禁用按钮，不再发请求；批量导出跳过并列出被跳过文件，不再整体中止。课件、作业附件下载/上传整体超时放宽到 15 分钟（socket 仍 30 秒）。`--sync-timing` 失败时记录异常类名。Mac 源码版实测“国际议题”整课导出 28 个 M4A（85 MB），跳过 9 个。
- 未验证：15 分钟超时未用 >30 秒的真实下载验证；iOS/Android 的“未开放下载”标记只验证了编译。
- 标签 `debug-1.8.4-Alpha-phydemo-fix`，只出 Actions 产物。

## 2026-10-12 二维码入口重做、HyperOS 小窗与模拟器强制签名（1.8.4-Alpha-phydemo-fix2，Build 31）

- 完美校园入口：wxaurl 加密链接从 App 唤起微信会被“一人一链”绑定（平板换号实测），微信内点开/扫码不受限；明文 Scheme（appid `wx081d27880a575cb3`）运营方未开放。改为弹窗：二维码 + “保存二维码并打开微信扫一扫” + 复制链接，Android 另有“打开完美校园 App”。校园网缴费弹窗同样加“保存并扫一扫”。扫一扫入口：Android `LauncherUI.From.Scaner.Shortcut`（平板实测进 BaseScanUI），iOS `weixin://scanqrcode`（iPhone 实测进扫一扫）。iOS 模拟器实测只申请“添加照片”权限、保存成功、两张图 CIDetector 解码正确。
- 弹窗统一：两个二维码弹窗统一为“标题 + 关闭”+ 说明/二维码(200dp)/按钮/提示。`AppleSheet(OrAlert)` 新增 `fitContentHeight`：iOS 用自定义 detent 按正文高度展开（`BJTUSetSheetFittedHeight`，高度回报推迟到下一轮主线程，否则测量中改尺寸会崩溃），Android 对话框正文可滚动。顶栏“同步状态”sheet 也改用 `fitContentHeight`（原规则 >6 行直接全高）。iOS 模拟器浅/深色实测按内容定高、出提示后平滑长高，8 行同步状态刚好包住。`shared/build.gradle.kts` cinterop 改用 `includeDirs`，改 .h 会重新生成绑定。
- HyperOS 小窗遮挡：小窗下拖动条/手势条报告为 captionBar（顶 108px、底 57px），statusBars/navigationBars 为 0。新增 `topSystemBars`/`bottomSystemBars`（并上 captionBar），顶栏、底栏、AppleSheet、首页变更弹窗改用；iOS/桌面 captionBar 恒为 0。直接以小窗启动已实测正常；临时诊断日志已删除。
- 构建：iOS 模拟器构建强制签名以保留钥匙串（`cc0706f`，`require-ios-signing.sh`）。
- 文档：协作指南迁到 `AGENTS.md`，删除 `CLAUDE.md`。
- 未验证：小窗↔全屏来回切换（adb 无法切换窗口模式）；Android 二维码弹窗未在平板上看；“保存并扫一扫”的 Android MediaStore 保存/去重和扫码后进入小程序/缴费页未真机验证；iOS 每次保存会多一张（只有添加权限无法去重）。
- 标签 `debug-1.8.4-Alpha-phydemo-fix2`，只出 Actions 产物。

## 2026-10-12 课表 JSON 导出（本机数据操作，未改代码）

- 按用户要求运行 debug `:desktopApp:run --args=--sync-timing`，自动恢复登录并同步后，导出刷新版 JSON 到忽略目录 `.artifacts/schedule-export-2026-10-12/`：15 门课程、21 条排课规则、4 项物理实验/6 次实验、2 项演示实验，共 234 条实际日程（旧版 232 条保留）。实时读取 bksy 公开校历逐周映射，10 月 12 日为第 5 教学周；JSON 回读、唯一 ID、时间/周次映射和敏感字段排除校验通过，无缺失警告/时间冲突。源码 JVM 已按精确路径结束，安装版保留；`:desktopApp:createDistributable` 成功。
- 个人调整版（同一目录）：移除周六上午“改革开放史”网络课（8 次），把逸夫教学楼/思源西楼 10:10–12:00 的 5 条规则/56 次课改为 10:20–12:20；共 226 条日程，实验与教学周映射保留，统计与冲突重算，回读断言通过。不改应用或学校数据。

## 2026-10-12 弹窗竞态、前往日期半屏与 iOS 课程小组件（1.8.4-Beta-SheetFix-Widget，Build 32）

- iOS 弹窗偶发点不开：`IosNativeSheetPresenter` 同页共用一个呈现器，关闭动画中再 present 会被 UIKit 静默拒绝，而 Compose 状态已是“打开”，之后入口失效。现在关闭中收到的 present 等关闭完成再弹，打开动画中的 dismiss 等打开完成再关，present 被拒时重试（最多 10 次后复位）。模拟器实测“课表与周数”选周后立即重开正常。
- 课表“前往日期”iOS 改为 `fitContentHeight`（约半屏，按日历实际高度），Android 不变。
- iOS 课程小组件（用户要求重启小组件开发，取代 2026-08-01“小组件移出范围”的决定）：新 target `CourseScheduleWidget`（`…kmp.ios.widget`，iOS 17+），App Group `group.team.bjtuss.bjtuselfservice.kmp`，主 App 加 `iosApp.entitlements`。共享层 `feature/widget/` 生成 14 天快照 JSON（课程 + 物理/演示实验，去校区/`[本]`），会话壳随课表变化写入、退出登录清空；Swift 宿主收通知刷新 WidgetCenter。两款：“临近课程”小/中/锁屏矩形/行内，“今日课程”中/大；当天课上完后显示下一个有课日。模拟器真实数据浅/深色检查通过；`verify-apple-release-metadata.sh` 改为要求扩展存在且版本与宿主一致。
- 标签 `debug-1.8.4-Beta-SheetFix-Widget`（`090d7e0`），只出 Actions 产物。

## 2026-10-13 小组件适配 SideStore App Group、sheet 按内容定高（1.8.4-Beta-SheetFix-Widget-fix2，Build 33）

- 真机（SideStore“Keep App Extensions (Use Main Profile)”）小组件显示“暂无课表”：SideStore 重签把 App Group 改名为 `原名.TeamID`（源码 `FetchProvisioningProfilesOperation.adjustedGroupIdentifier`），写死的原名打不开容器。App 与小组件改为从 `embedded.mobileprovision` 的 Entitlements 找“原名或原名.后缀”的组，再回退原名；小组件区分“无法读取课表（App Group 未生效）”与“暂无课表（打开 App 同步）”。解析在本机真实描述文件与 iOS 单测（3 项）验证。
- iOS“课表与周数”“筛选与计算”改为 `fitContentHeight`，超出时由 sheet 滚动；Android/桌面仍全高。模拟器实测贴合内容，开关自选模式时平滑长高/缩回。
- 标签 `debug-1.8.4-Beta-SheetFix-Widget-fix2`（`a117ba6`），只出 Actions 产物。

## 2026-10-13 侧载 IPA 保留小组件 App Group 签名（PR #8，`fix/ios-widget-sideload-entitlements`）

- 用户反馈 fix2 真机仍显示“无法读取课表”，判断 CI 未签名包丢失了 App Group 声明。CI 在 IPA 压缩前对嵌套 framework、小组件、主 App 依次 ad-hoc 签名并保留两端 App Group Entitlements；新增 `scripts/sign-ios-sideload-app.sh`，校验实际可执行文件的权限、签名结构和宿主/扩展版本。`kmp-package.yml` 对 iOS 相关 PR 构建 Debug IPA，其他平台在 PR 中跳过；正式标签仍用 Release 配置，PR 不发 Release。
- 验证：静态检查通过，Apple 签名/构建由 PR CI 执行。
- 未验证：SideStore 保留扩展覆盖安装后共享容器是否可读。

## 2026-10-13 本周课表周视图小组件与统一头部（PR #9，`feat/ios-widget-week-view`）

- “今日课程”放不下时脚注分开统计“之后还有 N 节 · 时间 课名”与“已上完 M 节”。
- “临近课程”中号按 WakeUp 两栏样式重做（头部日期 + 右侧“第 N 周”，“当前 · 还剩…/接下来”，大字课名、地点、时间）。
- 新增大号“本周课表”周视图（`WeekCourseWidget.swift`）：按大节分行（1/2/4/5 固定，午间/晚上有课才显示）、周一到周日分列（周末无课且非今天时隐藏），色块用课程表页的课程性质底色 + 描边，冲突课并排，已下课变淡、进行中加粗描边。
- 所有尺寸统一 `WidgetHeader`：左“[明天] 日期 星期（红）[共 N 节]”，右加粗周数胶囊；周视图左上角只留月份。共享快照新增 `kind`（required/limited/elective/pe/unknown/lab，来自培养方案映射）与 `slotTimes`，快照改从本周一开始（往后仍 14 天）。
- 验证：本地 Gradle 被 Maven Central 429 限流，单测与 iOS Debug IPA 交给 PR 检查（`kmp-security-check` / `kmp-package`）。未在模拟器浅/深色下查看。

## 2026-10-13 小组件打磨（PR #10，`fix/ios-widget-polish`）

- 倒计时改为按分钟显示“还剩 N 分钟/1 小时 5 分钟”：只有“临近课程”对 3 小时内正在上的课每分钟一个时间线条目，之后用 `.after` 重取。曾对全部小组件铺 24 小时逐分钟条目，导致真机周视图预渲染超预算、退回灰色占位图。
- 中号两栏去掉课名缩放、左右字号一致；今日课程待上课程超过 2 节（中号）/6 节（大号）时改单行紧凑行（中号 4 行、大号 10 行，只显示开始时间）；中号不再列出已上完的课，只在脚注计数（“还有 1 节未显示 · 时间 课名”/“还有 N 节未上”）；压暗单行只用于已上完的课和明日预告。
- 小号只显示“教室 老师”；大号今日课程保留上过的灰色课，剩余空间用压暗单行预告下一个有课日，放不全时小标题写“剩余 N 节未显示”。
- 验证：Swift 未本地编译，交 PR CI。“正在上/还剩 N 分钟”未在上课时段实机观察。

## 2026-10-13 课程类别色统一与同步卡顿修复（PR #11，`feat/category-colors-widget-links`，1.8.4-Beta3 / Build 35）

- 课程配色：新增 `feature/course/CourseCategoryPalette.kt` 作为唯一来源（必修/限选/任选/体育/未知/实验/考试，浅深两套 + accent）。课程表色块、成绩页体育、首页“正在上”卡片、当天课表书本图标、小组件色条/色块全部改用类别色，删除按课程名哈希的调色板；体育改青绿 `#16A4A4`。
- 小组件点击跳转到对应详情做了又按用户要求撤回（仍只打开 App），保留考试显示与考试色/标记。同步状态里物理实验/演示实验/物理在线相邻，物理在线也标“需要校园网”。
- 小组件快照：`LaunchedEffect` 原先在 iOS 每个原生 Tab 宿主各跑一遍、在主线程构建约 20 天日程并编码写文件，同步期间随数据到达反复触发 WidgetKit 全量刷新。改为只在主宿主运行、1.5 秒防抖、构建与写入放 `Dispatchers.Default`，iOS 共享容器路径缓存。
- 同步期间滑动卡顿（第一轮）：iOS 模拟器 Debug + Time Profiler 采样，主线程约 45% 在解析学校响应（校历 `parseAcademicWeeks` 正则 14.8%、物理在线 Ksoup 约 14%、培养方案 4%，另有成绩/课表/考试），重组只占约 6%。新增 `network/ParseOffMain.kt`，各数据源的解码 + 解析和教务 `looksLikeSessionExpired` 放 `Dispatchers.Default`，网络请求仍在原调度器保持串行；物理在线握手页标记一次算齐。`LocalHomeSchedule`、首页事件列表、聚合作业改 remember，首页日程 `now` 截到分钟；成绩仓库课程性质表内容不变时沿用旧实例。复测 Ksoup 主线程样本 2142→3。
- 顶栏玻璃：作业二级页内容不满一屏时上拉回弹，内容滑到顶栏下却没有玻璃（玻璃只跟 Compose 滚动偏移，回弹位移只在 UIKit）。`SafeAreaComposeHost` 合并两者取大（52pt 渐变），系统顶边效果改按 contentOffset 判断。模拟器实测拖动中出现玻璃、松手清除；“应用”页正常滚动行为不变。
- 卡顿第二、三轮（真机 iPhone 17 Pro，SideStore 版，xctrace 附着不重装）：主线程还卡在 ①CITEL 每个作业详情页的 `looksLikeSessionExpired`（2.7 秒）与 `parseCitelTaskDetail`（1.8 秒），模拟器账号未开 CITEL 没覆盖到；②课程性质表整表比较 1.6 秒（Compose 判键相等时不替换旧键，表实例一旦换成等值新对象，之后每次重组都整表比较）；③GC 停顿约 1.4 秒。修复：CITEL 解析与会话判断放后台；成绩 `applySnapshot`、CITEL 刷新结果内容相同沿用旧实例；`looksLikeSessionExpired` 不再整页 trim/lowercase 复制（补用例）。Beta2 后复采：同步期主线程四成在 Core Animation 提交（UINavigationBar 重布局）；原生栏动作每次重组都重设相同图标/文字、标题每次 bringSubviewToFront，已在 `ContentView.swift` 去重。剩余栏布局主要随原生手指滚动（setContentOffset → 滚动边缘效果/最小化）发生，平均每帧主线程约 3~4ms。≥16ms 长帧剩 11 个：MIS `checkSession` 整页判断（30~40ms ×3，已改后台）、同步详情 sheet 弹出约 40~54ms、进入作业页约 49ms。
- 验证：`:shared:desktopTest` 808 项通过、`:androidApp:assembleDebug` 成功；版本经 Beta2（Build 34）到 `1.8.4-Beta3` / Build 35，本地 Release 未签名 IPA 已装真机。用户确认 Beta3 卡顿已到可接受范围。
- 未验证：`checkSession` 改后台后未再复采；壳层仍随任一状态流整体重组（约 0.4 秒/次同步）。

## 2026-10-13 迁移期收口：删除 goal.md，文档改为日志式

- 决策：KMP 迁移（M0–M17）已收口，项目转入按需迭代，不再按里程碑规划。按用户要求删除 `goal.md`；`AGENTS.md` 改写为当前协作规则（仓库事实、分支/PR/标签流程、版本号同步清单、小组件与主线程解析等新原则），去掉迁移期过时内容（`maildev` 分支、旧版本基线、开工前逐页盘点原 Android App 等）；`history_full.md` 改为只追加的工作日志；`memory.md` 只保留当前状态。
- `goal.md` 删除时各里程碑的最终状态（快照）：
  - 已完成：M0–M3、M5（首页与八个业务切片）、M5.5 登录页与验证码自动识别、M5.6 全部 UI 用户确认、M6 平台能力、M7 三端真实登录验收、M9 成绩课程性质映射、M10 平台原生导航、M11 教室占用查询、M12 课程表体验整合、M14 Windows 移植、M17 Liquid 玻璃壳。M18 macOS 原生外观已取消。
  - 主体完成但留有边界：M4（合法签名下 iOS Keychain 往返未做）；M13 物理在线（移动端登录后运行、真实账号上传、Windows 触摸屏未验收）；M15 邮箱（真实发送、删除、移动、附件上传和 iOS 真实登录后邮箱页未验收）；验证码自动识别默认开启，24 张冒烟集 Apple/Android 表达式 21/24、argmax 24/24 一致，发布级还需至少 300 张独立留出集。
  - 未开始/外部条件：M8 正式签名发布（Apple Developer Team、Archive、公证、隐私问卷）；M16 校园网 VPN 只做了入口可达性确认；真实数据变化样本、真实作业上传、退出账号往返等学校侧条件。
  - 当时已调整的旧决策：小组件已于 2026-10-12 重新启动开发（见上文）；进一步无障碍专项仍不作为门禁。

## 2026-10-13 iOS 小组件跨天文案与整周无课提示（PR #12，`fix/ios-widget-copy`，不改版本号）

- 小号「临近课程」：跨天头部写“明天/后天”时省掉日期，只留“明天 周三”；更远的日子写“10.16 周五”（修掉原来“周五 10.16 周五”的重复）。
- 中号「临近课程」：跨天时日期下方单开一行“今天已上完”，两栏标题从“今天已上完 · 第一节 / 接下来”改为“明天第一节 / 明天第二节”（后天、本周周几、“下周一”同理），只有一节时右栏写“明天只有一节课”。这一模式下课名字号不变，标签/地点/时间改 footnote 并收紧间距。
- 「今日课程」中/大号：跨天时日期下方同样加“今天已上完”，与小号、中号共用 `DayStatusLine`（caption2 + 勾选图标）；中号此时行距收紧、紧凑行少放一节。
- 明天没课、后天才有课：状态行补“· 明天无课”，空出更多天时写“· 之后 N 天无课”；头部为“后天 …”。
- 整周没课且明天也没课（`CourseWidgetModel.isRestPeriod`）：各尺寸（含锁屏矩形/行内、周视图空周）显示“非教学周 / Enjoy🎉”；今天在教学周内（`week != nil`）只是没排课时写“本周没有课”。头部保持今天，有下一次课时小字“下次上课 下周一 08:00 …”。
- 实现：`CourseWidgetData.swift` 新增 `weekIsEmpty`、`isRestPeriod`、`todayStatus`、`dayOffset`、`monday(of:)`；`CourseWidgets.swift` 用 `relativeDayPrefix`/`dayName` 取代 `dayLabel`，新增 `RestPeriod`；`WeekCourseWidget.swift` 空周提示。
- 验证：本地无 Swift 工具链；PR CI `iOS compile and macOS security tests`、`iOS sideload IPA`、`Android compile security check` 均通过。
- 未验证：未在模拟器/真机查看各尺寸实际排版；中号跨天模式高度为估算，需在小屏 iPhone 上确认不截断。

## 2026-10-14 四端打包北京时间（PR #14，`fix/package-beijing-time`，补记）

- 改动：`kmp-package.yml` 全局设置 `TZ=Asia/Shanghai` 与 JVM 时区；Windows 原生工具设置 `China Standard Time`，保留原中文编码参数。iOS 签名后统一暂存目录 mtime、复验签名，以 `zip -X` 去掉 UTC 扩展时间；APK/MSI/DMG 仅刷新输出文件 mtime，不重写签名包内容。打包日志显式打印北京时间与偏移量。
- 外层 ZIP：`upload-artifact@v4` 把文件时间按 UTC 写进 DOS 时间，单设 TZ 不够。四端通过 `scripts/stage-actions-artifact.py` 创建上传副本，仅副本 mtime 加 8 小时；原包保留真实 epoch，内容与签名不变。
- 验证：YAML 解析、9 个 Bash 步骤 `bash -n`、JVM 时区、模拟 IPA 的北京时间跨日/真实 epoch/ZIP 时间/无 UTC 扩展字段/符号链接/内容哈希均通过；首轮 CI `38429777141` 的 IPA 内 30 个条目均为北京时间，CRC 与签名复验通过。
- 未验证：第二轮 CI 外层 ZIP 时间的实际结果未在 memory 中记录；Windows/MSI、Android/APK、macOS/DMG 需后续四端打包确认。既有 Actions 产物与 GitHub 自身 UTC 运行时间不变。

## 2026-10-14 第二节错峰上课时间（PR #16，`feat/staggered-second-slot`）

- 改动：第二节教室在逸夫教学楼（YF）/思源西楼（SX）时按 10:30–12:20。设置页新增开关「错峰上课时间」（默认开，`AppPreferences.staggeredSecondSlot`）。规则在 `domain/course/StaggeredClassTime.kt`（楼名或 `YF/SX+数字` 教室号；思源楼/思源东楼/威海逸夫楼不算；实验/考试不参与）。
- 精确时间覆盖：课程详情（含“（错峰）”）、按日列表卡片与整节均错峰时的节次标签、首页“现在/下一节”与日程、iOS 小组件快照 start/end、日历导出。
- 只下移不加节：课表网格色块顶边下移约 1/8 格（≤7dp）、底边压到格线；iOS 周视图按 `staggerMinutes` 归回原大节，同样微移。快照新增可选字段 `staggerMinutes`，旧小组件忽略。
- 验证：`:shared:desktopTest` 全过（含新增 `StaggeredClassTimeRenderTest`，合成课表出图于 `shared/build/reports/staggered-class-time/`，浅/深色、整节错峰、混排、关闭开关均人工看过）；iOS 签名模拟器 `xcodebuild` Debug（含小组件 Swift）`BUILD SUCCEEDED`；`:androidApp:assembleDebug`、`:desktopApp:compileKotlin`、`:windowsApp:compileKotlinWindows`、`:shared:compileKotlinIos{SimulatorArm64,Arm64}` 通过；PR CI 全绿。
- 未验证：真实课表里 YF/SX 地点文本格式（按既有 fixture `海淀西校区,思源西楼,SX501` 推断）；模拟器/真机中的设置页开关、首页卡片和 iOS 周视图小组件实际观感。

## 2026-10-14 Android 桌面课表小组件（PR #17 `feat/android-widgets`，经 PR #19 `fix/merge-android-widgets` 补合入 main，1.8.4-Candidate2 / Build 36）

- 改动：临近课程（2×2/4×2）、今日课程（4×2/4×4）、本周课表（4×4），与 iOS 同一份快照与版式规则。展示逻辑在 shared `feature/widget/CourseWidgetModel.kt`（可测，与 Swift 一一对应）；快照写到应用私有目录 `files/course-widget.json`（`CourseWidgetStore.android.kt`），写入后回调 `CourseWidgetUpdater.updateAll`；渲染为原生 RemoteViews（`androidApp/.../kmp/widget/`），不引入 Glance 等新库。
- 版式：以 iOS 尺寸为基准按实际宽高等比缩放字号（0.9–1.25），两栏共用字号，单行按估算宽度缩字；周视图按行高/列宽计算每块可放几行。深浅色走 values-night，类别文字色在 Android 12+ 用资源色；Android 12+ 按桌面上报的每个尺寸各排一份；容器 16dp 圆角（Android 12+ 用系统圆角）。
- 刷新：非唤醒 RTC 闹钟，上课时段逐分钟、其它时间按课程起止且最多 15 分钟；不申请精确闹钟权限。
- 同分支附带 iOS 中号“临近课程”溢出修复：栏标签/课名/地点/时间加 `minimumScaleFactor`，剩余时间窄栏短写“1 小时 15 分”。
- 排版教训：RemoteViews 超出卡片的内容会被直接裁掉，不像 iOS 按行截断；中文字体行高实测约 1.7 倍字号（`line` 常量），估高要偏大；先 `shrinkToFit` 整体缩小保住完整版式，再决定是否丢次要内容（今日课程脚注）；小号“下一节”是核心信息，按“整体缩小 → 长课名压成一行 → 最后才丢它”让步（`planSmall`）。桌面上报高度比实际显示小约 30dp，估高时补回 18dp（`SIZE_SLACK_DP`）；课名只超出可用宽度 15% 以内时缩字单行；XML 与代码里不要各加一份外边距。
- 合并事故：#17 的 base 是 `feat/staggered-second-slot`，该分支在 #17 合并前 33 秒已通过 #16 合入 main，#17 的改动（`1f37b8e`）因此没有进入 main，`debug-1.8.4-Candidate` 的 APK 没有小组件。PR #19 无冲突补合 `1f37b8e`，并把版本改为 1.8.4-Candidate2（Android versionCode/iOS CFBundleVersion 35→36，`AppUpdateChecker.CURRENT_VERSION` 及测试）；打包标签 `debug-1.8.4-Candidate2`（`ea0f760f`）。教训：叠加分支的 PR 合并前先确认 base 仍是 main，或先把 base 改成 main。
- 背景：此前一版 Android 小组件 PR #15 因实现质量不达标被用户关闭，远端分支已删除；#17 为独立实现。
- 验证：`:shared:desktopTest` 全过（新增 `CourseWidgetModelTest`）；`:androidApp:assembleDebug`、`lintDebug`、桌面/Windows/iOS Kotlin 编译通过；iOS 签名模拟器 xcodebuild 成功。Pixel 10 Pro XL 模拟器（API 37）用合成快照实测三种小组件、深浅色、错峰下移、分钟倒计时刷新。Candidate2 debug 包 `adb install -r` 到荣耀 AAK-AN00 与小米 25091RP04C 成功。
- 未验证：Android 12 以下系统；真实登录后由 App 写入快照的链路；iOS 溢出修复的实际观感（只编译）。

## 2026-10-14 取消 PR 触发 iOS IPA 打包（PR #18，`fix/ci-no-pr-ios-package`）

- 改动：`kmp-package.yml` 的 `pull_request` 触发器按 `iosApp/**` 等路径触发，iOS 任务的 `if` 未排除 PR 事件，修改 iOS 相关文件的 PR 也会打 Debug IPA。移除 `pull_request` 触发器；打包改为推 `v*`/`debug-*` 标签或手动 `workflow_dispatch`。PR 检查只剩 `kmp-security-check`。
- 验证：仅 CI。之后 PR #20 只触发 `kmp-security-check` 的 2 项（Android 编译、iOS 编译与 macOS 测试），没有打包任务。

## 2026-10-14 去掉荣耀 honorcard 标记，修复 MagicOS 找不到小组件（PR #20，`fix/honor-widget-visibility`）

- 现象：带 `com.hihonor.widget.type=honorcard` 的 Candidate2 在荣耀 AAK-AN00（MagicOS 11.0.0.109 / Android 17）上，服务卡片与经典小组件入口都找不到本应用。
- 改动：删除 `AndroidManifest.xml` 三个小组件 receiver 上的 honorcard meta-data，只保留标准 AppWidget。
- 原因/决策：荣耀卡片除该标记外，还需在荣耀开发者平台「智慧服务」登记服务（签合作协议、包名与 versionCode/minSdk、SHA256 证书指纹、各规格 4 倍截图、AppWidgetProvider 类名、测试名单与审核）。荣耀文档称该标记不影响原有显示，但实测未登记时 MagicOS 会把小组件整体隐藏。用户决定不接入荣耀服务卡片，走标准小组件；后续只视情况优化 Android 小组件显示。若将来接入，审核要求还包括：系统裁圆角、不用透明背景、安全边距 ≥12dp、文字用 dp、列表按压反馈（浅色 5% 黑/深色 10% 白）、深色背景 `#262626`、未登录/加载/无网兜底、点击直达落地页。
- 验证：`./gradlew :androidApp:assembleDebug` 成功；`aapt2 dump xmltree` 确认 APK 无 hihonor、三个 provider 仍在；荣耀 `adb install -r` 后 `dumpsys appwidget` 识别三个 provider，用户确认桌面经典小组件列表可见；小米 25091RP04C（Android 16）同样识别。PR CI：Android 编译通过；用户合并时 iOS 编译与 macOS 测试仍在运行（结果未记录）。
- 未验证：荣耀上添加后的实际渲染、尺寸与刷新；折叠屏尺寸。


## 2026-10-14 Agent A–E 接受与本地 Alpha 部署（PR #21/#22/#24/#25/#26，1.8.5-AgentAlpha / Build 38）

- 合并事实：用户已接受全部 PR。#21 合入 main，#22 → feat/ai-agent-cli，#24 → feat/ai-agent-skill，#25 → feat/ai-agent-sync，#26 → feat/ai-agent-citel；不是五个全部进入 main。现由 feat/ai-agent-dev 汇总完整代码及这些合并记录，并纳入独立 #23 零点截止修复。
- 下列为实施与部署时的验证记录；其中“未提交/未合入/待审核”和旧分支名描述当时状态，现已由上述事实及 memory.md 的最新摘要取代。

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

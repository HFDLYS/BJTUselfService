# BJTUselfService KMP 实时工作记忆

> 最后更新：2026-10-13。
> 当前阶段：调试版 `1.8.4-Beta-SheetFix-Widget-fix2` / Build 33，推 `debug-` 标签只走 CI 打包，不发布。
> 当前分支：`main`。
> KMP 应用版本：`1.8.4-Beta-SheetFix-Widget-fix2` / Build 33（macOS/Windows 安装包数值版本 1.8.4）。
> 冻结原 Android：`v1.7.1` / versionCode 9（对照 `origin/main@7d94f74`）。
> 正式 Latest 仍为 `v1.8.3-KMP`。

## 已完成

- 2026-10-13（分支 `feat/category-colors-widget-links`，PR #11）①课程配色统一：新增 `feature/course/CourseCategoryPalette.kt` 作为唯一来源（必修/限选/任选/体育/未知/实验/考试，浅深两套 + accent），课程表色块、成绩页体育、首页“正在上”卡片、当天课表书本图标、小组件色条/色块全部改用类别色，删除按课程名哈希的调色板；体育改青绿 `#16A4A4`。②小组件点击跳转按用户要求撤回（仍只打开 App），小组件保留考试显示与考试色/标记。③同步状态：物理实验/演示实验/物理在线相邻，物理在线也标“需要校园网”。本地 Gradle 依赖解析失败，测试与 iOS 编译交 PR CI；③卡顿排查：小组件快照的 LaunchedEffect 原先在 iOS 每个原生 Tab 宿主各跑一遍、在主线程构建约 20 天日程并编码写文件、同步期间随数据接连到达反复触发 WidgetKit 全量刷新；改为只在主宿主运行、1.5 秒防抖、构建与写入放 Dispatchers.Default，iOS 共享容器路径缓存。是否就是用户感知的冷启动卡顿未经真机/Instruments 证实。
- 2026-10-13（分支 `fix/ios-widget-polish`）小组件打磨：倒计时改为按分钟“还剩 N 分钟/1 小时 5 分钟”（只有「临近课程」的时间线对 3 小时内正在上的课每分钟一条，之后 `.after` 重取；曾对全部小组件铺 24 小时逐分钟条目，导致真机周视图预渲染超预算退回灰色占位图）；中号两栏去掉课名缩放，左右字号一致；今日课程待上课程超过 2/6 节时改单行紧凑行（中号 4、大号 10 行，只显示开始时间）；中号不再列出已上完的课，只在脚注右侧计数，脚注“还有 1 节未显示 · 时间 课名”/“还有 N 节未上”，压暗单行只用于已上完的课和明日预告；小号只显示“教室 老师”；大号今日课程保留上过的灰色课，剩余空间用压暗单行预告下一个有课日（放不全写“还有 N 节”）。Swift 未本地编译，交 PR CI。
- 2026-10-13（未提交）iOS 小组件三项：①「今日课程」放不下时脚注改为分开统计——“之后还有 N 节 · 时间 课名”与“已上完 M 节”（原“还有 2 节”把已下课的也算进去，且没写出被挤掉的后续课）；②「临近课程」中号按 WakeUp 两栏样式重做（头部日期 + 右侧“第 N 周”，“当前 · 还剩…/接下来”，大字课名、地点、时间）；③新增大号「本周课表」周视图（新文件 `WeekCourseWidget.swift`，已登记 pbxproj）：按大节行（1/2/4/5 固定，午间/晚上有课才显示）、周一到周日列（周末无课且非今天隐藏），色块用课程表页按课程性质的底色+描边，冲突课并排，已下课变淡、进行中加粗描边。所有卡片（小/中/大/周视图）统一用 `WidgetHeader`：左“[明天] 日期 星期（红）[共 N 节]”，右加粗周数胶囊；周视图顶部同样加这条头部，左上角只留月份。共享快照新增 `kind`（required/limited/elective/pe/unknown/lab，来自培养方案映射）与 `slotTimes`，快照改从本周一开始（往后仍 14 天）。Swift 只在 Linux 上静态编写；本地 Gradle 被 Maven Central 429 限流，单测与 iOS Debug IPA 交给分支 `feat/ios-widget-week-view` 的 PR 检查（kmp-security-check / kmp-package）。
- 2026-10-13 云端签名排查：CI 在 IPA 压缩前为嵌套 framework、Widget、主 App 依次 ad-hoc 签名，保留两端 App Group Entitlements；新增 `sign-ios-sideload-app.sh` 校验实际可执行文件权限、签名结构及宿主/扩展版本。`kmp-package.yml` 新增 iOS 相关 PR 的 Debug IPA 构建，其他平台在 PR 打包流程跳过；正式标签仍用 Release，PR 不发 Release。静态检查已通过；Apple 签名/构建检查由 CI 执行，SideStore 安装后的共享权限仍待真机验证。应用版本保持 Build 33。
- 版本改为 `1.8.4-Beta-SheetFix-Widget-fix2`（Build 33），推 `mine/main` 与标签 `debug-1.8.4-Beta-SheetFix-Widget-fix2`，仅 Actions 产物。
- 小组件真机（SideStore「Keep App Extensions (Use Main Profile)」）显示“暂无课表”：SideStore 重签把 App Group 改名为 `原名.TeamID`（源码 `FetchProvisioningProfilesOperation.adjustedGroupIdentifier`），写死的原名打不开容器。App 与小组件改为从 `embedded.mobileprovision` 的 Entitlements 找“原名或原名.后缀”的组，再回退原名；小组件另分出“无法读取课表（App Group 未生效）”与“暂无课表（打开 App 同步）”。解析在本机真实描述文件与 iOS 单测（3 项）验证，真机待装 fix2 确认。
- iOS「课表与周数」「筛选与计算」改为按内容定高（`fitContentHeight`），超出时由 sheet 滚动；Android/桌面仍全高。模拟器实测两者贴合内容，开关自选模式时平滑长高/缩回。
- 版本改为 `1.8.4-Beta-SheetFix-Widget`（Build 32），提交弹窗竞态修复、前往日期半屏与 iOS 课程小组件，推 `mine/main` 与标签 `debug-1.8.4-Beta-SheetFix-Widget`，仅 Actions 产物、不建 Release。
- 2026-10-12 iOS 弹窗偶发点不开：`IosNativeSheetPresenter` 同页共用一个呈现器，关闭动画中再 present 会被 UIKit 静默拒绝，而 Compose 状态已是“打开”，之后入口失效。现在关闭中收到的 present 等关闭完成再弹，打开动画中的 dismiss 等打开完成再关，present 被拒时重试（最多 10 次后复位状态）。模拟器实测：课表与周数选周后立即重开正常弹出。课表「前往日期」iOS 改为 `fitContentHeight`（约半屏，按日历实际高度），Android 不变。
- 2026-10-12（用户要求重启小组件开发）iOS 课程小组件：新 target `CourseScheduleWidget`（`…kmp.ios.widget`，iOS 17+），App Group `group.team.bjtuss.bjtuselfservice.kmp`（沿用旧注册标识），主 App 加 `iosApp.entitlements`。共享层 `feature/widget/` 生成 14 天快照 JSON（课程+物理/演示实验，去校区/`[本]`，首页同色板），会话壳随课表变化写入、退出登录清空；Swift 宿主收通知刷新 WidgetCenter。两款：「临近课程」小/中/锁屏矩形/行内，「今日课程」中/大；当天课上完后显示下一个有课日并在日期头标明。模拟器真实数据浅/深色检查通过；`verify-apple-release-metadata.sh` 改为要求扩展存在且版本与宿主一致。
- 课表 JSON 个人调整版（同一忽略目录）：按用户要求移除周六上午「改革开放史」网络课（8 次），把逸夫教学楼/思源西楼 10:10–12:00 的 5 条排课规则/56 次课程改为 10:20–12:20；共 226 条日程，6 次物理实验和 2 次演示实验及教学周映射保留。同步调整规则/实际日程/选课缓存附带记录并重算统计和冲突；JSON 回读与针对性断言通过，原刷新版保留，不改应用/学校数据。

- 2026-10-12 按用户要求运行最新版 debug `:desktopApp:run --args=--sync-timing`，自动恢复真实登录并同步后导出刷新版 JSON 至忽略目录 `.artifacts/schedule-export-2026-10-12/`：15 门课程、21 条排课规则、4 项物理实验/6 次实验、2 项演示实验，共 234 条实际日程（旧版 232 条保留）。实时读取 bksy 公开校历逐周映射，10 月 12 日为第 5 教学周；JSON 回读、唯一 ID、时间/周次映射及敏感字段排除校验通过，无缺失警告/时间冲突。演示实验含实际 lastSync；课程/物理实验缓存无同步时间字段，注明启动自动同步后快照。源码 JVM 已精确结束、安装版保留；`:desktopApp:createDistributable` 构建成功，未重装或改源码。

- 版本改为 `1.8.4-Alpha-phydemo-fix2`（Build 31），提交本轮完美校园/二维码/小窗/按内容定高改动，推 `mine/main` 与标签 `debug-1.8.4-Alpha-phydemo-fix2`，仅 Actions 产物、不建 Release。HyperOS 小窗临时诊断日志已删除。

- 版本改为 `1.8.4-Alpha-phydemo-fix`（Build 30），推 `mine/main` 与标签 `debug-1.8.4-Alpha-phydemo-fix`，仅 Actions 产物、不建 Release。

- 版本改为 `1.8.3-KMP`（Build 29）。
- 已推 `mine/main` 与标签 `v1.8.3-KMP`（`7985a25`）。CI run `38033203351`。发布说明为仓库内 `docs/releases/v1.8.3-KMP.md`，本轮未再改正文。
- 本构建含教务限流与邮箱附件、安卓附件打开方式、完美校园微信跳转、登录失败弹窗文案。

- 演示实验（未提交）：首页「当天实验」与「正在上」并入演示实验；登录自动刷新改用与物理实验相同的门控（修 iOS 原生 Tab 下从不自动刷新）；查询页按日期排序、进入时会话内未同步则补刷一次。
- 首页日程跳转统一（未提交）：物理在线跨课程查找活动后进二级详情（原只查当前课程，找不到就开网页）；CITEL 进对应作业二级详情（原聚合模式下跳作业一级页）。

- 课件下载（未提交）：解析智慧教学 `stu_download`（网页端仅 "2" 显示下载）。未开放下载的文件在列表/详情标「未开放下载」并禁用按钮，单个下载不再访问网络；批量导出跳过并列出被跳过文件，不再整体中止。课件、作业附件下载/上传的整体超时放宽到 15 分钟（socket 30 秒不变）。`--sync-timing` 失败时多记异常类名。Mac 源码版实测「国际议题」整课导出 28 个 M4A（85 MB），跳过 9 个。

- 完美校园入口重做：wxaurl 加密链接从 App 唤起微信会被「一人一链」绑定（2026-10-12 平板换号实测），微信内点开/扫码不受限；明文 Scheme（appid `wx081d27880a575cb3`）运营方未开放。弹窗：二维码 + 「保存二维码并打开微信扫一扫」+ 复制链接，Android 另有「打开完美校园 App」。校园网缴费弹窗同样加保存并扫一扫。扫一扫入口：Android `LauncherUI.From.Scaner.Shortcut`（平板实测进 BaseScanUI），iOS `weixin://scanqrcode`（iPhone 实测进扫一扫）。iOS 模拟器实测：仅添加照片授权、保存、两张图 CIDetector 解码正确、无微信提示。
- 两个二维码弹窗统一为「标题 + 关闭」+ 说明/二维码(200dp)/按钮/提示；`AppleSheet(OrAlert)` 新增 `fitContentHeight`：iOS 用自定义 detent 按正文高度展开（`BJTUSetSheetFittedHeight`，高度回报推迟到下一轮主线程，否则测量中改尺寸崩溃），Android 对话框正文可滚动。iOS 模拟器浅/深色实测按内容定高、出提示后平滑长高。`shared/build.gradle.kts` cinterop 改用 `includeDirs`，改 .h 会重新生成绑定。
- 顶栏「同步状态」sheet 也改用 `fitContentHeight`（原规则：>6 行直接全高）；非 iOS 底部 sheet 在 fitContentHeight 下由 sheet 提供滚动与底部安全区，行为与原来一致。iOS 模拟器实测 8 行刚好包住。
- HyperOS 小窗遮挡：小窗下拖动条/手势条报告为 captionBar（顶 108px、底 57px），statusBars/navigationBars 为 0。新增 `topSystemBars`/`bottomSystemBars`（∪ captionBar），顶栏、底栏、AppleSheet、首页变更弹窗改用；iOS/桌面 captionBar 恒 0。直接小窗启动已实测正常。

## 当前注意事项

- 校历公众号文章不能由第三方 App 直接在微信内打开（invalid source），仍走系统浏览器。
- 小米「跟随应用内设置」覆盖安装清不掉。装新包后卸载重装，或在「使用高刷新率的应用」里打开交大自由行 KMP。
- 未推 `origin`。冻结 Android `release.yml` 对 `v1.8.3-KMP` 会 skip，属预期。

## 当前痛点

- 本轮小组件改动（周视图、中号两栏、脚注）未经 Xcode 编译、未在模拟器浅/深色下查看；周视图在小屏 iPhone 上文字可能偏挤。
- 小组件“正在上/还剩 N 分钟”状态只在代码层实现，未在上课时段实机观察；改版本号时须同时改 `iosApp/CourseScheduleWidget/Info.plist`。
- 用户反馈 fix2 真机小组件显示“无法读取课表”；当前 CI 未签名包可能丢失 App Group 声明。新增打包 ad-hoc 签名与权限门禁后，仍需用 SideStore 保留扩展覆盖安装、打开 App 同步，再验证共享容器。
- 小窗↔全屏来回切换未实测（adb 无法切换窗口模式）。
- Android 统一后的二维码弹窗（标题 + 关闭、可滚动正文）未在平板上看过。
- 「保存并扫一扫」最新包未装到平板/iPhone：Android MediaStore 保存与去重、扫一扫后从相册识别进入小程序/缴费页，均待真机验证；iOS 每次保存会多一张（仅添加权限无法去重）。

- 课件 15 分钟大文件超时放宽未用 >30 秒的真实下载验证；iOS/Android 课件「未开放下载」标记只验证编译。
- 在 Chrome 登录智慧教学会顶掉 App 会话，App 自动恢复登录失败一次（需手动刷新），未排查。

- macOS 附件默认应用打开、iOS 保存面板未点验。
- `HomeworkFileGateway` 已被邮箱复用，后续可改名为通用文件网关。

## 接下来

0. 实机点验首页物理在线/CITEL 日程项进入二级详情（本轮模拟器只验证了演示实验）。

1. 等 CI 四端产物和 GitHub Release。
2. 需要热修时再开 `1.8.3-KMP-A` 或下一版本。

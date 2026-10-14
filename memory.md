# BJTUselfService 实时工作记忆

> 最后更新：2026-10-14。
> 当前分支：`feat/android-widgets`（基于 `feat/staggered-second-slot`）。PR #16（错峰上课）、PR #17（Android 小组件）CI 全绿，等待用户验证后合并；打包标签 `debug-1.8.4-Beta-staggered` 成功。
> KMP 应用版本：`1.8.4-Beta3` / Build 35（macOS/Windows 安装包数值版本 1.8.4）。正式 Latest 仍为 `v1.8.3-KMP`。
> 冻结原 Android：`v1.7.1` / versionCode 9（对照 `origin/main@7d94f74`）。
> 已完成工作的细节见 `history_full.md` 末尾日志。

## 进行中

- 第二节错峰上课（PR #16，`feat/staggered-second-slot`，CI 全绿，待合入）：第二节教室在逸夫教学楼（YF）/思源西楼（SX）时按 10:30–12:20。设置页新增开关「错峰上课时间」（默认开，`AppPreferences.staggeredSecondSlot`）。规则在 `domain/course/StaggeredClassTime.kt`（楼名或 `YF/SX+数字` 教室号；思源楼/思源东楼/威海逸夫楼不算；实验/考试不参与）。
  - 精确时间：课程详情（含“（错峰）”）、按日列表卡片与整节均错峰时的节次标签、首页“现在/下一节”与日程、iOS 小组件快照 start/end、日历导出。
  - 只下移不加节：课表网格色块顶边下移约 1/8 格（≤7dp）、底边压到格线；iOS 周视图按 `staggerMinutes` 归回原大节，同样微移。快照新增可选字段 `staggerMinutes`，旧小组件忽略。
  - 已验证：`:shared:desktopTest` 全过（含新增 `StaggeredClassTimeRenderTest`，合成课表出图于 `shared/build/reports/staggered-class-time/`，浅/深色、整节错峰、混排、关闭开关均人工看过）；iOS 签名模拟器 `xcodebuild` Debug（含小组件 Swift）`BUILD SUCCEEDED`；`:androidApp:assembleDebug`、`:desktopApp:compileKotlin`、`:windowsApp:compileKotlinWindows`、`:shared:compileKotlinIos{SimulatorArm64,Arm64}` 通过。
  - 未验证：真实课表里 YF/SX 地点文本格式（按既有 fixture `海淀西校区,思源西楼,SX501` 推断）；模拟器/真机中的设置页开关、首页卡片和 iOS 周视图小组件实际观感。
- Android 桌面小组件（`feat/android-widgets`，基于 PR #16 分支）：临近课程（2×2/4×2）、今日课程（4×2/4×4）、本周课表（4×4），与 iOS 同一份快照与版式规则。
  - 实现：展示逻辑在 shared `feature/widget/CourseWidgetModel.kt`（可测，与 Swift 一一对应）；快照写到应用私有目录 `files/course-widget.json`（`CourseWidgetStore.android.kt`），写入后回调 `CourseWidgetUpdater.updateAll`；渲染为原生 RemoteViews（`androidApp/.../kmp/widget/`），不引入 Glance 等新库。
  - 版式：以 iOS 尺寸为基准按实际宽高等比缩放字号（0.9–1.25），两栏共用字号，单行按估算宽度缩字；周视图按行高/列宽计算每块可放几行。深浅色走 values-night，类别文字色在 Android 12+ 用资源色。Android 12+ 按桌面上报的每个尺寸各排一份。
  - 刷新：非唤醒 RTC 闹钟，上课时段逐分钟、其它时间按课程起止且最多 15 分钟；不申请精确闹钟权限。
  - 荣耀：receiver 加 `com.hihonor.widget.type=honorcard`；容器 16dp 圆角（Android 12+ 用系统圆角）。
  - 同分支附带 iOS 中号“临近课程”溢出修复：栏标签/课名/地点/时间加 `minimumScaleFactor`，剩余时间窄栏短写“1 小时 15 分”。
  - 已验证：`:shared:desktopTest` 全过（新增 `CourseWidgetModelTest`）；`:androidApp:assembleDebug`、`lintDebug`、桌面/Windows/iOS Kotlin 编译通过；iOS 签名模拟器 xcodebuild 成功。Pixel 10 Pro XL 模拟器（API 37）用合成快照实测三种小组件、深浅色、错峰下移、分钟倒计时刷新。
  - 未验证：荣耀真机（服务中心入口、16dp 容器、折叠屏尺寸）；Android 12 以下系统；真实登录后由 App 写入快照的链路；iOS 溢出修复的实际观感（只编译）。
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

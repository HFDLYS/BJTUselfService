# BJTUselfService KMP 实时工作记忆

> 最后更新：2026-10-12。
> 当前阶段：调试版 `1.8.4-Alpha-phydemo-fix2` / Build 31，推 `debug-` 标签只走 CI 打包，不发布。
> 当前分支：`main`。
> KMP 应用版本：`1.8.4-Alpha-phydemo-fix2` / Build 31（macOS/Windows 安装包数值版本 1.8.4）。
> 冻结原 Android：`v1.7.1` / versionCode 9（对照 `origin/main@7d94f74`）。
> 正式 Latest 仍为 `v1.8.3-KMP`。

## 已完成

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

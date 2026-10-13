# BJTUselfService 实时工作记忆

> 最后更新：2026-10-13。
> 当前分支：`fix/package-beijing-time`，基于 `mine/main@9dff5e6`（PR #13 已合入）；四端打包北京时间修复在 PR #14，待合入。
> KMP 应用版本：`1.8.4-Beta3` / Build 35（macOS/Windows 安装包数值版本 1.8.4）。正式 Latest 仍为 `v1.8.3-KMP`。
> 冻结原 Android：`v1.7.1` / versionCode 9（对照 `origin/main@7d94f74`）。
> 已完成工作的细节见 `history_full.md` 末尾日志。

## 进行中

- 四端打包北京时间：`kmp-package.yml` 全局设置 `TZ=Asia/Shanghai` 与 JVM 时区；Windows 原生工具设置 `China Standard Time`，保留原中文编码参数。iOS 签名后统一暂存目录 mtime、复验签名，以 `zip -X` 去掉 UTC 扩展时间；APK/MSI/DMG 仅刷新输出文件 mtime，不重写签名包内容。打包日志显式打印北京时间与偏移量。
  - 本地验证：YAML 解析、9 个 Bash 步骤 `bash -n`、JVM 时区、模拟 IPA 的北京时间跨日/真实 epoch/ZIP 时间/无 UTC 扩展字段/符号链接/内容哈希均通过，`git diff --check` 通过。
  - 首轮 CI：`38429777141` 成功；下载实际 IPA 验证 30 个条目的时间全部为北京时间 `2026-10-14 00:29:52`，压缩包 CRC 与签名复验通过。发现 `upload-artifact@v4` 的外层 ZIP 仍把 IPA 文件时间序列化为 UTC `2026-10-13 16:29:52`，单设 TZ 不足以修复。
  - 外层 ZIP 修复：四端通过 `scripts/stage-actions-artifact.py` 创建上传副本，仅该副本 mtime 加 8 小时，适配 upload-artifact 的 UTC DOS 时间编码；原包保留真实 epoch，内容与签名不变。四种扩展名的内容哈希、原 epoch、跨日时间与重复暂存验证通过，等待第二轮 CI 实际产物验证。
  - 未验证：Windows/MSI、Android/APK、macOS/DMG 需后续四端打包验证（当前 PR 只打 iOS）。仅修复后续产物，既有 Actions 产物与 GitHub 自身 UTC 运行时间不变。

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

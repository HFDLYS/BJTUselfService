# BJTUselfService 实时工作记忆

> 最后更新：2026-10-14。
> 当前分支：`feat/ai-agent-dev`（完整保存 Agent A–E、#23 与 Alpha 整合）；main@53bf642a 为 Candidate3 完整代码基线（仅 macOS Build 校正为 37）。本地和 mine 仅保留 main/dev，两条分支各自与远端对齐，main 是 dev 的祖先。
> KMP 源码版本：main 为 `1.8.4-Candidate3` / Build 37（桌面数值版本 1.8.4）；dev 为 `1.8.5-AgentAlpha` / Build 38（桌面数值版本 1.8.5）。/Applications 安装版仍为 Alpha，bjtuss 已安装；正式 Latest 仍为 `v1.8.3-KMP`。
> 冻结原 Android：`v1.7.1` / versionCode 9（对照 `origin/main@7d94f74`）。
> 已完成工作的细节见 `history_full.md` 末尾日志。

## 进行中

- Agent 后续开发在 `feat/ai-agent-dev`（1.8.5-AgentAlpha / Build 38），A–E 与 #23 完整保留；main 维持 Candidate3 基线。后续新的 Agent PR 以 dev 为基础，发布与合入 main 由用户决定。验证与未验证边界见日志末尾。

- `fix/deadline-24-attachment-ext`（基于 dev，PR 指向 `feat/ai-agent-dev`）：物理在线带星期原文（`日 Saturday 00:00`）的零点截止未改成前一天 24:00、CITEL 题目 Markdown 截止漏改、变化记录按截止匹配作业失效；课程平台老师附件 `file_name` 不带扩展名导致下载丢后缀（从存储路径补，路径也没有时按 Content-Type 补）。本地 Gradle 被 Maven Central 429 限流，仅 CI 验证；debug 包用 workflow_dispatch 触发。

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

- 后续 Agent 开发统一以 `feat/ai-agent-dev` 为基础；新的 PR 明确核对 base，避免旧叠层 PR 接受后未进入预期分支。

1. 在真机已有版本（SideStore 保留扩展）上验证小组件共享容器与各尺寸显示。
2. 准备 1.8.4 正式发布时：核对版本号清单（见 `AGENTS.md`）、写 `docs/releases/<tag>.md`，等待用户批准打标签。

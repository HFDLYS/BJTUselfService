# BJTUselfService macOS

"交大自由行" 的 SwiftUI / macOS 原生版本。完整移植 Android 端的 MIS / AA / 智慧课程平台功能，并新增抢课功能，针对 16:9 桌面窗口采用三栏布局 + Liquid Glass 视觉。

## 功能

- **首页** — 邮件/校园卡/校园网状态、待关注作业与同步变更汇总
- **成绩** — 加权平均分、学期筛选、排序、成绩详情
- **课程表** — 本学期/选课课表、周次切换、当前节高亮
- **考试安排** — 按类型筛选、详情面板
- **作业** — 智慧课程平台作业列表、内容预览、上传提交、已提交下载
- **课件** — 课程资源树形浏览、递归下载文件夹、教学日历 PDF
- **抢课** — 全校任选课/跨选课/其他院系专业课搜索、验证码获取与提交、退课
- **教室人数** — 实时教室占用率、空教室状态图
- **其他功能** — 校历/成绩单下载、作业邮件提醒订阅、校园网/校园卡入口
- **设置** — 账号登录/退出、主题切换、自动同步开关、更新检查、缓存清理

## 环境要求

- macOS 26.0+
- Apple Silicon（arm64）
- Xcode 16+（开发用）或直接用 Swift Package Manager

## 开发运行

```bash
git clone <repo-url>
cd BJTUselfService-macOS
swift build
swift run BJTUselfServiceMac
```

也可以用 Xcode 打开 `Package.swift` 运行。

## 打包发行（DMG）

```bash
bash Packaging/package.sh
```

产物：`build/BJTUselfServiceMac-1.0.0.dmg`

脚本自动完成：release 编译 → 预编译 CoreML 模型 → 组装 .app bundle → 签名 → 生成 DMG。

### 安装

1. 双击 DMG，将「交大自由行」拖入 Applications
2. 首次启动前在终端执行（ad-hoc 签名需要）：
   ```bash
   xattr -cr /Applications/BJTUselfServiceMac.app
   ```
3. 双击启动

> 拥有 Apple Developer ID 账号后，在 `package.sh` 中配置签名身份，脚本会自动检测并使用 Developer ID 签名。公证命令：
> ```bash
> xcrun notarytool submit build/BJTUselfServiceMac-1.0.0.dmg --apple-id YOU@EMAIL.com --team-id TEAMID --wait
> xcrun stapler staple build/BJTUselfServiceMac-1.0.0.dmg
> ```

## 技术架构

| 层 | 说明 |
|---|---|
| UI | SwiftUI + NavigationSplitView，macOS 26 Liquid Glass（旧系统回退 regularMaterial） |
| 网络 | `HTTPClient`（actor）+ `URLSession`，共享 `HTTPCookieStorage`，自动 SSL 信任 |
| 验证码 | CoreML CRNN 模型（从 Android PyTorch model.pt 转换），自动识别算式，失败回退手动输入 |
| 登录 | CAS SSO → MIS → AA → 智慧课程平台，三层 SSO 自动衔接 |
| 存储 | Keychain（账号密码）+ Application Support（缓存 JSON）+ UserDefaults（偏好设置） |
| 并发 | 全部 Service 类 `@MainActor`，`HTTPClient` 为 `actor`，Task 句柄可取消 |

## 目录结构

```
Sources/BJTUselfServiceMac/
├── App/                    — App 入口、AppModel（状态管理）
├── Models/                 — 数据模型
├── Services/               — 网络服务（MIS/AA/智慧课程平台/抢课/下载/验证码）
├── Stores/                 — 本地存储（Keychain/UserDefaults/文件缓存）
├── Views/                  — SwiftUI 视图
└── Resources/              — CoreML 验证码模型

Packaging/                  — Info.plist、打包脚本、图标
```

## 致谢

- 原始 Android 项目：[BJTUselfService](https://github.com/HFDLYS/BJTUselfService)
- 抢课逻辑参考：[Badguys](https://github.com/HFDLYS/Badguys)（Python → Swift 移植）

## 免责声明

仅供学习交流使用。请勿用于非法用途。

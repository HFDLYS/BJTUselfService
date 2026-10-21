# 🚄 交大自由行 (BJTU Self Service)

> 北京交通大学校园服务 Android 客户端 —— 让校园生活触手可及

[![Latest Release](https://img.shields.io/github/v/release/HFDLYS/BJTUselfService?style=flat-square&label=最新版本)](https://github.com/HFDLYS/BJTUselfService/releases/latest)
[![Android](https://img.shields.io/badge/Android-9.0%2B-brightgreen?style=flat-square&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/github/license/HFDLYS/BJTUselfService?style=flat-square)](LICENSE)

## 📖 项目简介

**交大自由行** 是一款专为北京交通大学师生打造的校园服务应用。通过自动登录 MIS 系统，将成绩查询、课程表、考试安排、作业管理、邮件查看等常用校园功能整合到一个简洁直观的界面中。

应用在**本地完成**登录页面和业务数据解析；账号密码保存在本机，登录时通过学校统一身份认证服务完成认证。

## ✨ 功能特性

### 🔐 智能登录
- 适配新版 MIS SSO 统一身份认证
- 本地 SM2 密码加密，适配学校登录协议
- 本地保存登录凭据，启动时自动登录

### 📚 学业管理
- **成绩查询** — 按学年筛选、排序，查看成绩详情，自动计算 GPA/均分
- **课程表** — 直观的课程表预览界面，支持本科生与研究生，高亮当前课程
- **考试日程** — 考试安排一览，变动自动提醒，已结束考试置灰展示
- **作业管理** — 查看、筛选、排序作业，支持作业上传与下载

### 📬 信息服务
- **校内邮箱** — 原生收件箱与邮件详情，支持正文链接、邮箱和号码快捷操作；当前展示最近 30 封收件箱邮件，暂不支持发信和附件下载
- **校园卡余额** — 实时查看一卡通余额
- **校园网余额** — 网络使用情况一目了然

### 📅 日历与提醒
- 日历视图整合作业截止日期与考试时间
- 邮箱订阅功能，自动抓取智慧课程平台作业/课程报告/实验
- 剩余时间不足阈值时自动发送邮件提醒

### 🏫 校园工具
- **教室人数识别** — 查看实时人数与教室占用详情，支持空闲教室筛选和楼层切换规划
- **校历下载** — 一键下载当前学年校历
- **成绩单下载** — 支持中英文成绩单快捷下载
- **应用内更新** — 启动时自动检测新版本

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────┐
│                   UI 层                      │
│         Jetpack Compose + Material 3         │
│   (Screen / Component / Navigation)          │
├─────────────────────────────────────────────┤
│                 逻辑层                       │
│          ViewModel (MVVM)                    │
│          Dagger Hilt (DI)                    │
├─────────────────────────────────────────────┤
│                 数据层                       │
│    Repository → DAO → Room Database          │
│    OkHttp3 + Jsoup (网络请求与 HTML 解析)     │
│    DataStore Preferences (本地配置)           │
├─────────────────────────────────────────────┤
│                 身份认证                     │
│      MIS SSO + Bouncy Castle (SM2)            │
└─────────────────────────────────────────────┘
```

### 主要依赖

| 类别 | 技术 |
|------|------|
| UI 框架 | Jetpack Compose + Material 3 |
| 依赖注入 | Dagger Hilt |
| 网络请求 | OkHttp3 |
| HTML 解析 | Jsoup |
| 本地数据库 | Room |
| 序列化 | Kotlin Serialization / Gson / Moshi |
| 身份认证 | MIS SSO / Bouncy Castle (SM2) |
| 日历组件 | Kizitonwose Calendar |
| 动画 | Lottie |

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK，compileSdk 34
- 设备/模拟器 Android 9.0 (API 28) 以上

### 构建步骤

```bash
# 1. 克隆仓库
git clone git@github.com:HFDLYS/BJTUselfService.git
cd BJTUselfService

# 2. 赋予 Gradle Wrapper 执行权限
chmod +x gradlew

# 3. 构建 Debug APK
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug

# 4. 安装到设备
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

或者直接用 **Android Studio** 打开项目，点击 ▶ Run 即可。

### CI/CD

项目配置了 GitHub Actions 自动化流程：
- **Debug 构建** — 每次推送自动构建验证
- **Release 发布** — 推送 `v*` 标签时自动构建、签名并发布 APK 到 GitHub Releases

## 📱 支持架构

当前仅打包 **arm64-v8a** 架构，适配绝大多数现代 Android 设备。

## 🔒 隐私与安全

- ✅ SSO 登录页面解析与密码加密在客户端完成
- ✅ 账号密码仅存储在本地设备
- ✅ 学业数据通过学校服务查询；教室人数及占用详情通过项目服务获取

## 🤝 贡献者

感谢以下所有为本项目做出贡献的朋友们：
- [optsimauth](https://github.com/optsimauth): 
  - 重构了整个项目的架构，优化了代码结构
  - 以及后续若干跟进
- [guh0613](https://github.com/guh0613)
  - 提供自动构建与发布
- [carolyn-sun](https://github.com/carolyn-sun)
  - 优化了工作流配置
- [NAPH130](https://github.com/NAPH130)
  - 修复若干 bug
- [B-Silva20](https://github.com/B-Silva20)
  - 提供了成绩自选课程计算
  - 适配新版 SSO 统一身份认证登录
- [wangxiaobo1747](https://github.com/wangxiaobo1747) 
  - 提供了自定义壁纸和桌面课程表小组件
- [Peterloses](https://github.com/Peterloses)
  - 优化底部导航、壁纸和玻璃态界面，改进课程表小组件
- [Dontplay0112](https://github.com/Dontplay0112)
  - 修复研究生获取课表时崩溃的问题
- [lsr0LSR](https://github.com/lsr0LSR)
  - 修复作业上传接口
- [Vannnesa](https://github.com/Vannnesa)
  - 重构应用界面，实现原生邮箱与教室规划，新增课程高亮及过期考试样式

# 1.8.5-EmergencyFix2 调试构建

版本：`1.8.5-EmergencyFix2`，Build 40。桌面安装器数值版本保持 `1.8.5`。
标签：`debug-1.8.5-EmergencyFix2`，仅触发四端打包并上传 Actions 产物，不创建 GitHub Release。

## 修改

- Android 平板小窗下，物理在线与演示实验账号设置可以正常打开并输入；补齐紧凑窗口原生页面路由。
- 首页“数据变动”补齐 CITEL、物理实验、演示实验缓存更新，首次同步建立基线，账号切换不产生旧账号差异提醒。
- Android 课程小组件七类颜色改用独立彩色资源，重置复用视图的滤镜与透明度，兼容旧快照颜色。
- 课程平台原入口失败时，自动通过 MIS 首页“11.本科教学服务平台 → 智慧课程平台”建立会话；作业、课件与课表当前周共用握手，保留教务当前周兜底。

## 验证与边界

- Android API 36 隔离模拟器验证四个独立账号设置入口、输入与物理在线/演示实验登录保存；检查浅深色、文字放大及窗口尺寸变化。
- 小组件原生 RemoteViews 七类配色与反复刷新校验通过，Pixel Launcher 添加组件、调整尺寸、切浅深色后颜色正常。原始 HyperOS 串色未在 AOSP 模拟器复现，仍需小米真机复测。
- 独立 KMP/Ktor 会话实际走课程回退，读取 15 条作业、15 门课程及首课 8 个课件目录项成功。未执行真实作业提交或文件上传。
- 本机检查通过：共享 JVM 测试 923 项零失败，Android debug 构建与 Windows 编译成功。完整命令及本地日志见工作区忽略目录 `.artifacts/emergencyfix2-checks.log`；排除 5 项已确认依赖 Unix 脚本/Windows 符号链接权限的既有桌面安装卸载测试。
- iOS 原生业务文件未修改；iOS 主应用与小组件版本同步为 Build 40。共享网络和首页逻辑四端生效，Apple 原生构建交由本次 Actions 验证。

## 本机检查命令

使用 JDK 21、本机 Android SDK、既有 debug key 覆盖脚本与仅本地生效的 5 项环境限制排除脚本：

```text
gradlew -p multiplatform -I .artifacts/android-account-debug-signing.gradle -I .artifacts/emergencyfix2-checks.gradle :shared:desktopTest :androidApp:assembleDebug :windowsApp:compileKotlinWindows
```

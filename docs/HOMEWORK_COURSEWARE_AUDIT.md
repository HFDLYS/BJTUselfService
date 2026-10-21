# 作业 / 课件数据链路复盘

## 1. 入口与会话

登录成功后，AppStateManager 调用 `SmartCurriculumPlatformRepository.initClient()`：

1. GET `https://mis.bjtu.edu.cn/module/module/28/`
   - 目前 302 到：
     `https://cas.bjtu.edu.cn/o/authorize?response_type=code&client_id=...&redirect_uri=http://123.121.147.7:88/oauth/token/callBack`
   - 目标是让 CAS 回调老课程平台，写入 SCP 的 JSESSIONID。
2. GET `http://123.121.147.7:88/ve/back/coursePlatform/message.shtml?method=getArticleList`
   - 原逻辑：从 JSON 取 `sessionId`，加到后续请求的 `sessionid` header。
3. GET `http://123.121.147.7:88/ve/back/rp/common/teachCalendar.shtml?method=queryCurrentXq`
   - 原逻辑：取当前学期 `xqCode`。
4. GET `http://123.121.147.7:88/ve/back/coursePlatform/course.shtml?method=getCourseList&pagesize=100&page=1&xqCode=<xqCode>`
   - 原逻辑：把课程列表缓存在 `courseFromJson`。

作业和课件都依赖第 4 步的 `courseFromJson`。

## 2. 作业原始 URL

- 作业列表（普通作业）：
  `http://123.121.147.7:88/ve/back/coursePlatform/homeWork.shtml?method=getHomeWorkList&cId=<courseId>&subType=0&page=1&pagesize=100`
- 课程设计：`subType=1`，同 URL。
- 实验报告：`subType=2`，同 URL。
- 作业详情：
  `http://123.121.147.7:88/ve/back/coursePlatform/homeWork.shtml?method=queryStudentCourseNote&id=<upId>&courseId=<courseId>&teacherId=<teacherId>`
- 作业附件上传：
  `http://123.121.147.7:88/ve/back/rp/common/homeworkUpload.shtml?noteId=<noteId>`
- 提交作业：
  `http://123.121.147.7:88/ve/back/course/courseWorkInfo.shtml?method=sendStuHomeWorks`

当前实现会按课程逐门请求，把所有 `courseNoteList` 拼成 `HomeworkEntity`，再写入 Room。

## 3. 课件原始 URL

- 课程资源树（递归）：
  `http://123.121.147.7:88/ve/back/coursePlatform/courseResource.shtml?method=stuQueryUploadResourceForCourseList&courseId=<course_num>&cId=<course_num>&xkhId=<fz_id>&xqCode=<xq_code>&docType=1&up_id=<parentId>&searchName=`
- 资源下载地址：
  `http://123.121.147.7:88/ve/back/resourceSpace.shtml?method=rpinfoDownloadUrl&rpId=<rpId>`
- 实际文件下载前缀：
  `http://123.121.147.7:1936/kk/rp/`

当前实现：
- `CoursewareViewModel.init` 先读 DataStore 缓存；
- 然后调用 `getCourseList()`，逐门课程递归生成 `CoursewareNode`；
- 最后把结果写回 DataStore。
- 关键缺陷：如果 `getCourseList()` 返回空（当前正是如此），代码会用空列表覆盖缓存，导致课件页一直空白。

## 4. 当前为什么两栏都不显示（证据）

- `module/28/` 最终落到 CAS 登录页；CAS 页面返回的配置里包含：
  `No static resource o/authorize`
- `getArticleList` 返回 `http://123.121.147.7:88/ve/Timeout.jsp`，标题“会话结束”，不是 JSON。
- `getTimeList` 返回 HTTP 500。
- `getCourseList` 同样无法在无 session 的情况下成功。
- 因此：
  - `courseFromJson == null`
  - 作业循环没有课程可遍历，返回空列表；
  - 课件循环没有课程可遍历，返回空树，并覆盖缓存。
- 这不是 Compose 页面渲染问题，而是老课程平台 SSO/会话链路失效。

## 5. 老平台登录细节

- CAS 登录页是标准 `form#loginForm`，字段包括 username/password 及 client_id/redirect_uri 等。
- 页面 JS 会调用 `sm2.encrypt(password, config.sm2.publicKey)`；直接提交明文密码会重新回到登录页。
- 当前页面中未稳定拿到 `config.sm2.publicKey`，且 CAS 的 `/o/authorize` 本身报静态资源错误，说明该 OAuth 入口可能已迁移或下线。
- 新入口 `module/104/` 也会落到 CAS，再跳 `bksycenter.bjtu.edu.cn/CommonLogin.aspx`，同样未建立会话。

## 6. 待确认的问题

1. 浏览器现在还能打开作业/课件吗？最终 URL 是哪个：
   - 老平台 `123.121.147.7:88`
   - 还是新平台 `bksycenter.bjtu.edu.cn`
2. 浏览器登录作业/课件时，是否经过 `cas.bjtu.edu.cn`，是否需要验证码/二次验证？
3. 能否提供一次“打开作业列表 + 打开课件列表”的 HAR（Chrome DevTools Network → Save all as HAR）？
   - 这是最快确认新接口、参数和 Cookie 的方式。
4. 如果老平台已迁移，是否接受把作业/课件整体改到新平台 API？

## 7. 邮件排序修复

`EmailRepository.loadInbox` 现在会在客户端按 `sentDate` 倒序排序：
- 服务端仍请求 `order=date&desc=1`；
- 客户端再按解析后的时间降序排序，避免 Coremail 返回顺序不稳定；
- 新增 `EmailRepositoryDateTest` 覆盖新旧日期比较与非法日期。

## 8. 补充：按“新网址 → 智慧课程平台”实测

用户实际路径是：

1. 打开 `https://bksy.bjtu.edu.cn/`（本科生院/新网址）
2. 点击页面中的“智慧课程平台”
3. 链接实际为：
   `http://bksycenter.bjtu.edu.cn/NoMasterJumpPage.aspx?URL=jwcZhjx&FPC=page:jwcZhjx`
4. bksycenter 再跳转到老平台 `http://123.121.147.7:88/ve/...`

浏览器能自动跳转成功，是因为浏览器里已经有 **bksycenter / bksy 的登录 Cookie**。

HTTP 客户端无这些 Cookie 时实测：

- 直接访问 `NoMasterJumpPage.aspx` → 最终停在
  `http://bksycenter.bjtu.edu.cn/CommonLogin.aspx`
- 不会到达 `qx_kl.jsp`
- 老平台查询接口随后返回 `Timeout.jsp`

因此，缺的不是“点击”这个动作，而是 bksy 的登录态。

### bksy 登录接口（从页面 JS 提取）

- 验证码图片：
  `https://bksycenter.bjtu.edu.cn/LoginAjax.aspx?createvc=true&random=...`
- 登录 POST：
  `https://bksycenter.bjtu.edu.cn/LoginAjax.aspx`
  参数：
  `username`、`password`、`verifyCode`、`type`（学生为 1）
- 返回 JSONP，`result=1` 表示成功；成功后写 bksycenter Cookie。
- 再访问 `NoMasterJumpPage.aspx`，即可进入老平台并建立 SCP 的 JSESSIONID。

### 下一步的最小改动

只替换会话建立：

1. 增加一次性“智慧课程平台登录”（学号/密码/验证码）；
2. 持久化 bksycenter Cookie；
3. 登录成功后访问 NoMasterJumpPage 跳转；
4. 继续调用原有 `getArticleList`、`getCourseList`、作业和课件接口；
5. 给作业/课件增加失败状态，禁止空列表覆盖缓存。

不需要改作业/课件的查询逻辑。


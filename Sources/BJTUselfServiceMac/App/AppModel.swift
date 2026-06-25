import Foundation

enum AppPhase: Equatable {
    case loggedOut
    case loggingIn
    case ready
    case refreshing
    case error(String)
}

enum Appearance: String, CaseIterable {
    case system
    case light
    case dark

    var label: String {
        switch self {
        case .system: return "跟随系统"
        case .light: return "浅色"
        case .dark: return "深色"
        }
    }
}

@MainActor
final class AppModel: ObservableObject {
    @Published var phase: AppPhase = .loggedOut
    @Published var studentInfo: StudentInfo?
    @Published var accountStatus = AccountStatus()
    @Published var cache: AppCache
    @Published var changeNotices: [ChangeNotice] = []
    @Published var coursewareRoots: [CoursewareNode] = []
    @Published var selectedBuilding = "第十七号教学楼"
    @Published var buildingInfo: BuildingInfo?
    @Published var downloads: [DownloadTaskStatus] = []
    @Published var latestRelease: GitHubRelease?
    @Published var lastMessage = ""
    @Published var showLoginSheet = false

    @Published var autoSyncGrades: Bool {
        didSet { store.autoSyncGrades = autoSyncGrades }
    }
    @Published var autoSyncHomework: Bool {
        didSet { store.autoSyncHomework = autoSyncHomework }
    }
    @Published var autoSyncSchedule: Bool {
        didSet { store.autoSyncSchedule = autoSyncSchedule }
    }
    @Published var autoSyncExams: Bool {
        didSet { store.autoSyncExams = autoSyncExams }
    }
    @Published var checkUpdate: Bool {
        didSet { store.checkUpdate = checkUpdate }
    }
    @Published var appearance: Appearance {
        didSet { store.appearance = appearance.rawValue }
    }

    let captchaCenter = CaptchaCenter()
    let http = HTTPClient()
    let store = LocalStore.shared
    let mis: MISService
    let smart: SmartCurriculumService
    let downloadsService: DownloadAndUpdateService
    let courseGrabber: CourseGrabbingService

    @Published var grabbableCourses: [GrabbableCourse] = []
    @Published var grabCaptcha: CaptchaInfo?
    @Published var grabResult = ""
    @Published var isGrabbing = false

    private var syncTask: Task<Void, Never>?

    static let buildingList = [
        "第十七号教学楼", "思源楼", "思源西楼", "思源东楼", "第九教学楼",
        "第八教学楼", "第五教学楼", "逸夫教学楼", "机械楼", "东区二教", "东区一教"
    ]

    init() {
        let loadedCache = store.loadCache()
        cache = loadedCache
        coursewareRoots = loadedCache.coursewareRoots
        autoSyncGrades = store.autoSyncGrades
        autoSyncHomework = store.autoSyncHomework
        autoSyncSchedule = store.autoSyncSchedule
        autoSyncExams = store.autoSyncExams
        checkUpdate = store.checkUpdate
        appearance = Appearance(rawValue: store.appearance) ?? .system

        let misService = MISService(http: http, captchaCenter: captchaCenter)
        mis = misService
        smart = SmartCurriculumService(http: http, mis: misService)
        downloadsService = DownloadAndUpdateService(http: http, mis: misService, smart: smart)
        courseGrabber = CourseGrabbingService(http: http, mis: misService)
    }

    func presentLogin() {
        showLoginSheet = true
    }

    func login(credentials: Credentials, refreshAfterLogin: Bool = true) async {
        phase = .loggingIn
        do {
            let info = try await mis.login(credentials: credentials)
            studentInfo = info
            KeychainStore.save(credentials)
            do {
                accountStatus = try await mis.fetchStatus()
            } catch {
                accountStatus = AccountStatus(newMailCount: "0", ecardBalance: "0", netBalance: "0")
                lastMessage = "状态获取失败：\(error.localizedDescription)"
            }
            phase = .ready
            lastMessage = "已登录：\(info.name)"
            showLoginSheet = false
            if refreshAfterLogin {
                await refreshAll()
            }
        } catch {
            phase = .error(error.localizedDescription)
            lastMessage = error.localizedDescription
        }
    }

    func logout() {
        syncTask?.cancel()
        syncTask = nil
        mis.clearSession()
        KeychainStore.clear()
        studentInfo = nil
        accountStatus = AccountStatus()
        phase = .loggedOut
        lastMessage = "已退出登录"
    }

    func refreshAll() async {
        guard phase != .loggingIn else { return }
        guard studentInfo != nil || KeychainStore.load() != nil else {
            lastMessage = "请先登录后再同步数据"
            presentLogin()
            return
        }
        syncTask?.cancel()
        phase = .refreshing
        var newCache = cache
        var notices: [ChangeNotice] = []
        var failures: [String] = []

        do {
            accountStatus = try await mis.fetchStatus()
        } catch {
            failures.append("状态：\(error.localizedDescription)")
        }

        do {
            let grades = try await mis.fetchGrades()
            notices += detectChanges(old: cache.grades, new: grades, category: .grade, title: { $0.courseName }, detail: { "\($0.courseScore) · \($0.courseCredits) 学分" })
            newCache.grades = grades
        } catch {
            failures.append("成绩：\(error.localizedDescription)")
        }

        do {
            let courses = try await mis.fetchCourses()
            notices += detectChanges(old: cache.courses, new: courses, category: .course, title: { $0.courseName }, detail: { "\($0.courseTime) \( $0.coursePlace)" })
            newCache.courses = courses
        } catch {
            failures.append("课表：\(error.localizedDescription)")
        }

        do {
            let exams = try await mis.fetchExams()
            notices += detectChanges(old: cache.exams, new: exams, category: .exam, title: { $0.courseName }, detail: { "\($0.examTimeAndPlace) · \($0.examStatus)" })
            newCache.exams = exams
        } catch {
            failures.append("考试：\(error.localizedDescription)")
        }

        do {
            newCache.classroomMap = try await mis.fetchClassroomMap()
        } catch {
            failures.append("空教室：\(error.localizedDescription)")
        }

        do {
            try await smart.prepare()
            do {
                newCache.currentWeek = try await smart.fetchCurrentWeek()
            } catch {
                failures.append("当前周：\(error.localizedDescription)")
            }
            let homework = try await smart.fetchHomework()
            notices += detectChanges(old: cache.homework, new: homework, category: .homework, title: { $0.title }, detail: { "\($0.courseName) · 截止 \($0.endTime)" })
            newCache.homework = homework
        } catch {
            failures.append("作业/智慧课程平台：\(error.localizedDescription)")
        }

        newCache.lastRefreshDate = Date()
        cache = newCache
        changeNotices = notices
        store.saveCache(newCache)
        phase = failures.isEmpty ? .ready : .error(failures.joined(separator: "\n"))
        lastMessage = failures.isEmpty ? "数据已同步" : failures.joined(separator: "\n")
    }

    func loadCoursewareTree() async {
        if !coursewareRoots.isEmpty { return }
        phase = .refreshing
        do {
            let roots = try await smart.buildCoursewareRoots()
            coursewareRoots = roots
            cache.coursewareRoots = roots
            store.saveCache(cache)
            phase = .ready
        } catch {
            phase = .error(error.localizedDescription)
            lastMessage = error.localizedDescription
        }
    }

    func loadBuilding(_ building: String? = nil) async {
        if let building { selectedBuilding = building }
        do {
            buildingInfo = try await mis.fetchBuildingInfo(selectedBuilding)
        } catch {
            lastMessage = error.localizedDescription
        }
    }

    func clearLocalCache() {
        store.clearCache()
        cache = AppCache()
        coursewareRoots = []
        changeNotices = []
        lastMessage = "本地缓存已清除"
    }

    func checkForUpdates() async {
        do {
            latestRelease = try await downloadsService.fetchLatestRelease()
        } catch {
            lastMessage = "检查更新失败：\(error.localizedDescription)"
        }
    }

    func downloadSchoolCalendar() async {
        await trackDownload(filename: "校历") {
            try await downloadsService.downloadSchoolCalendar()
        }
    }

    func downloadGradeTranscript(english: Bool) async {
        await trackDownload(filename: english ? "英文成绩单" : "中文成绩单") {
            try await downloadsService.downloadGradeTranscript(english: english)
        }
    }

    func downloadTeachingCalendar(for course: PlatformCourse) async {
        await trackDownload(filename: "\(course.name)_教学日历.pdf") {
            try await downloadsService.downloadTeachingCalendar(for: course)
        }
    }

    func downloadCourseware(_ node: CoursewareNode) async {
        let id = UUID().uuidString
        downloads.append(DownloadTaskStatus(id: id, filename: node.displayName, relativePath: node.displayName, progress: 0.1, state: .downloading, message: "下载中"))
        do {
            let urls = try await downloadsService.downloadCoursewareNode(node)
            updateDownload(id: id, progress: 1, state: .completed, message: "完成 \(urls.count) 个文件")
        } catch {
            updateDownload(id: id, progress: 1, state: .failed, message: error.localizedDescription)
        }
    }

    func downloadSubmittedHomework(_ homework: HomeworkItem) async {
        await trackDownload(filename: homework.title) {
            try await smart.downloadSubmittedHomework(homework)
        }
    }

    func uploadHomework(_ homework: HomeworkItem, fileURLs: [URL], content: String) async -> Bool {
        do {
            let response = try await smart.uploadHomework(homework, fileURLs: fileURLs, content: content)
            lastMessage = response.contains("success") ? "作业提交成功" : response
            await refreshAll()
            return response.contains("success")
        } catch {
            lastMessage = "作业提交失败：\(error.localizedDescription)"
            return false
        }
    }

    func subscribeHomeworkNotice(email: String, normal: Int, emergency: Int) async -> Bool {
        guard let studentID = studentInfo?.studentID else { return false }
        do {
            let success = try await downloadsService.subscribeHomeworkNotice(studentID: studentID, email: email, normalThresholdHours: normal, emergencyThresholdHours: emergency)
            lastMessage = success ? "邮件订阅成功" : "邮件订阅失败"
            return success
        } catch {
            lastMessage = error.localizedDescription
            return false
        }
    }

    func searchGrabbableCourses(type: CourseSelectType, keywords: [String]) async {
        isGrabbing = true
        grabResult = ""
        do {
            grabbableCourses = try await courseGrabber.searchCourses(type: type, keywords: keywords)
            if grabbableCourses.isEmpty {
                grabResult = "没有找到匹配的课程"
            }
        } catch {
            grabResult = "搜索失败：\(error.localizedDescription)"
        }
        isGrabbing = false
    }

    func refreshGrabCaptcha() async {
        do {
            grabCaptcha = try await courseGrabber.fetchCaptcha()
        } catch {
            grabResult = "验证码获取失败：\(error.localizedDescription)"
        }
    }

    func submitGrabbedCourses(ids: [String], captchaAnswer: String) async {
        guard let captcha = grabCaptcha else {
            grabResult = "请先获取验证码"
            return
        }
        guard !ids.isEmpty else {
            grabResult = "请至少选择一门课程"
            return
        }
        guard !captchaAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            grabResult = "请输入验证码"
            return
        }
        isGrabbing = true
        do {
            let result = try await courseGrabber.submitCourses(courseIDs: ids, captchaHash: captcha.hash, captchaAnswer: captchaAnswer)
            grabResult = result
            await refreshGrabCaptcha()
        } catch {
            grabResult = "抢课失败：\(error.localizedDescription)"
            await refreshGrabCaptcha()
        }
        isGrabbing = false
    }

    func deleteGrabbedCourse(id: String) async {
        isGrabbing = true
        do {
            let result = try await courseGrabber.deleteCourse(courseID: id)
            grabResult = result
        } catch {
            grabResult = "退课失败：\(error.localizedDescription)"
        }
        isGrabbing = false
    }

    private func trackDownload(filename: String, operation: () async throws -> URL) async {
        let id = UUID().uuidString
        downloads.append(DownloadTaskStatus(id: id, filename: filename, relativePath: "", progress: 0.1, state: .downloading, message: "下载中"))
        do {
            let url = try await operation()
            updateDownload(id: id, progress: 1, state: .completed, message: url.path)
            lastMessage = "已保存到 \(url.path)"
        } catch {
            updateDownload(id: id, progress: 1, state: .failed, message: error.localizedDescription)
            lastMessage = error.localizedDescription
        }
    }

    private func updateDownload(id: String, progress: Double, state: DownloadTaskStatus.State, message: String) {
        guard let index = downloads.firstIndex(where: { $0.id == id }) else { return }
        downloads[index].progress = progress
        downloads[index].state = state
        downloads[index].message = message
    }

    private func detectChanges<T: Identifiable & Equatable>(
        old: [T],
        new: [T],
        category: ChangeNotice.Category,
        title: (T) -> String,
        detail: (T) -> String
    ) -> [ChangeNotice] where T.ID: Hashable {
        let oldMap = old.reduce(into: [T.ID: T]()) { result, item in
            result[item.id] = item
        }
        let newMap = new.reduce(into: [T.ID: T]()) { result, item in
            result[item.id] = item
        }
        var notices: [ChangeNotice] = []

        for item in new where oldMap[item.id] == nil {
            notices.append(ChangeNotice(category: category, kind: .added, title: title(item), detail: detail(item)))
        }
        for item in new {
            if let oldItem = oldMap[item.id], oldItem != item {
                notices.append(ChangeNotice(category: category, kind: .modified, title: title(item), detail: detail(item)))
            }
        }
        for item in old where newMap[item.id] == nil {
            notices.append(ChangeNotice(category: category, kind: .deleted, title: title(item), detail: detail(item)))
        }
        return notices
    }
}

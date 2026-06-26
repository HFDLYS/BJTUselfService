import Foundation
import SwiftSoup

@MainActor
final class MISService {
    let http: HTTPClient
    private let captchaCenter: CaptchaCenter
    private let captchaSolver = CoreMLCaptchaSolver()

    private(set) var studentInfo: StudentInfo?
    private var credentials: Credentials?
    private var isMISLoggedIn = false
    private var isAALoggedIn = false
    private var isBKSYLoggedIn = false

    init(http: HTTPClient, captchaCenter: CaptchaCenter) {
        self.http = http
        self.captchaCenter = captchaCenter
    }

    var isMISReady: Bool { isMISLoggedIn }
    var canLogin: Bool { credentials != nil }
    var storedCredentials: Credentials? { credentials }

    func clearSession() {
        Task { await http.clearCookies() }
        studentInfo = nil
        credentials = nil
        isMISLoggedIn = false
        isAALoggedIn = false
        isBKSYLoggedIn = false
    }

    func login(credentials: Credentials) async throws -> StudentInfo {
        self.credentials = credentials
        if try await checkCookie(), let studentInfo {
            isMISLoggedIn = true
            return studentInfo
        }

        let info = try await performLogin(credentials: credentials)
        studentInfo = info
        isMISLoggedIn = true
        return info
    }

    func checkCookie() async throws -> Bool {
        let (_, response) = try await http.get(
            "https://mis.bjtu.edu.cn/home/",
            headers: ["Host": "mis.bjtu.edu.cn", "Referer": "https://mis.bjtu.edu.cn/home/"]
        )
        if response.url?.absoluteString == "https://mis.bjtu.edu.cn/home/" {
            let (data, _) = try await http.get("https://mis.bjtu.edu.cn/home/")
            studentInfo = try? parseStudentInfo(htmlString(data), studentID: credentials?.username ?? "")
            return true
        }
        return false
    }

    func ensureAA() async throws {
        if isAALoggedIn { return }
        if !isMISLoggedIn {
            guard let credentials else { throw AppError.loginRequired }
            _ = try await login(credentials: credentials)
        }

        let (data, _) = try await http.get(
            "https://mis.bjtu.edu.cn/module/module/10/",
            headers: ["Host": "mis.bjtu.edu.cn"]
        )
        let doc = try SwiftSoup.parse(htmlString(data))
        guard let action = try doc.select("form[id=redirect]").first()?.attr("action"), !action.isEmpty else {
            throw AppError.parseFailed("未找到 AA 跳转表单")
        }

        let (_, response) = try await http.get(
            action + "?",
            headers: ["Referer": "https://mis.bjtu.edu.cn/module/module/10/"]
        )
        guard response.url?.absoluteString == "https://aa.bjtu.edu.cn/notice/item/" else {
            throw AppError.badResponse("AA 登录跳转异常")
        }
        isAALoggedIn = true
    }

    func ensureBKSY() async throws {
        if isBKSYLoggedIn { return }
        if !isMISLoggedIn {
            guard let credentials else { throw AppError.loginRequired }
            _ = try await login(credentials: credentials)
        }

        let (_, response) = try await http.get("https://mis.bjtu.edu.cn/module/module/104/")
        guard response.url?.absoluteString == "https://bksy.bjtu.edu.cn/login_introduce_t.html" else {
            throw AppError.badResponse("本科生院入口跳转异常")
        }

        let (_, jumpResponse) = try await http.get("https://bksycenter.bjtu.edu.cn/NoMasterJumpPage.aspx?URL=jwcZhjx&FPC=page:jwcZhjx")
        guard jumpResponse.url?.absoluteString == "http://123.121.147.7:88/ve/back/core/main/qx_kl.jsp" else {
            throw AppError.badResponse("智慧课程平台登录失败")
        }
        isBKSYLoggedIn = true
    }

    func fetchStatus() async throws -> AccountStatus {
        if !isMISLoggedIn {
            guard let credentials else { throw AppError.loginRequired }
            _ = try await login(credentials: credentials)
        }
        let (data, _) = try await http.get(
            "https://mis.bjtu.edu.cn/osys_ajax_wrap/",
            headers: ["Host": "mis.bjtu.edu.cn"]
        )
        let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        return AccountStatus(
            newMailCount: String(describing: object?["newmail_count"] ?? "0"),
            ecardBalance: String(describing: object?["ecard_yuer"] ?? "0"),
            netBalance: String(describing: object?["net_fee"] ?? "0")
        )
    }

    func fetchGrades() async throws -> [Grade] {
        try await ensureAA()
        let first = try await fetchGrades(ctype: "ln")
        let second = try await fetchGrades(ctype: "lr")
        var seen = Set<String>()
        return (first + second).filter { grade in
            let key = "\(grade.courseName)-\(grade.courseScore)-\(grade.courseCredits)"
            return seen.insert(key).inserted
        }
    }

    func fetchExams() async throws -> [ExamScheduleItem] {
        try await ensureAA()
        let (data, _) = try await http.get(
            "https://aa.bjtu.edu.cn/examine/examplanstudent/stulist/",
            headers: ["Host": "aa.bjtu.edu.cn"]
        )
        let doc = try SwiftSoup.parse(htmlString(data))
        guard let table = try doc.select("tbody").first() else {
            throw AppError.parseFailed("考试安排表为空")
        }

        return try table.select("tr").array().compactMap { row in
            let cols = try row.select("td").array()
            guard cols.count > 5 else { return nil }
            return ExamScheduleItem(
                examType: try cols[1].text(),
                courseName: try cols[2].text(),
                examTimeAndPlace: try cols[3].text(),
                examStatus: try cols[4].text(),
                detail: try cols[5].text()
            )
        }
    }

    func fetchCourses() async throws -> [CourseScheduleItem] {
        try await ensureAA()
        let current = (try? await fetchCourseGrid(isCurrentTerm: true)) ?? []
        let next = (try? await fetchCourseGrid(isCurrentTerm: false)) ?? []
        return current + next
    }

    func fetchClassroomMap() async throws -> [String: [Int]] {
        try await ensureAA()
        let (_, firstResponse) = try await http.get(
            "https://aa.bjtu.edu.cn/classroomtimeholdresult/room_view/",
            headers: ["Host": "aa.bjtu.edu.cn"]
        )
        guard var urlString = firstResponse.url?.absoluteString,
              urlString.contains("https://aa.bjtu.edu.cn/classroomtimeholdresult/room_view/?zc=") else {
            throw AppError.parseFailed("无法获取当前周空教室表")
        }
        let currentWeek = URLComponents(string: urlString)?
            .queryItems?
            .first(where: { $0.name == "zc" })?
            .value
            .flatMap(Int.init) ?? 0
        urlString += "&page=1&perpage=500"

        let (data, _) = try await http.get(urlString, headers: ["Host": "aa.bjtu.edu.cn"])
        let doc = try SwiftSoup.parse(htmlString(data))
        guard let table = try doc.select("table").first() else {
            throw AppError.parseFailed("空教室表为空")
        }

        var result: [String: [Int]] = ["nowWeek": [currentWeek]]
        let rows = try table.select("tr").array()
        let weekday = Calendar.current.component(.weekday, from: Date())
        let index = weekday == 1 ? 6 : weekday - 2

        for row in rows.dropFirst(2) {
            let cols = try row.select("td").array()
            guard cols.count > 1 + 7 * index + 6 else { continue }
            let name = ((try? cols[0].text()) ?? "").components(separatedBy: " ").first ?? ""
            guard !name.isEmpty else { continue }
            var values: [Int] = []
            for slot in 0..<7 {
                let style = try cols[1 + 7 * index + slot].attr("style")
                values.append(classroomStatus(fromStyle: style))
            }
            result[name] = values
        }
        return result
    }

    func fetchBuildingInfo(_ buildingName: String) async throws -> BuildingInfo {
        let encoded = buildingName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? buildingName
        let (data, _) = try await http.get(API.classroomCapacity + encoded)
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let time = object["time"] as? [String],
              let rows = object["data"] as? [[Any]] else {
            throw AppError.parseFailed("教室人数接口返回异常")
        }

        let classrooms = rows.compactMap { row -> ClassroomCapacity? in
            guard row.count > 3 else { return nil }
            let roomName = String(describing: row[0])
            let used = Int(String(describing: row[2])) ?? 0
            let capacity = Int(String(describing: row[3])) ?? 0
            return ClassroomCapacity(roomName: roomName, capacity: capacity, used: used)
        }

        return BuildingInfo(
            buildingName: buildingName,
            classroomList: classrooms,
            effectiveDateStart: time.first ?? "",
            effectiveDateEnd: time.dropFirst().first ?? ""
        )
    }

    private func classroomStatus(fromStyle style: String) -> Int {
        switch style {
        case "background-color: #e46868": return 1
        case "background-color: #9e6868": return 2
        case "background-color: #394ed6": return 3
        case "background-color: #77bf6d": return 4
        case "background-color: #d8cc56": return 5
        default: return 0
        }
    }

    private func performLogin(credentials: Credentials) async throws -> StudentInfo {
        await http.clearCookies(forDomain: "cas.bjtu.edu.cn")

        let (firstData, firstResponse) = try await http.get(
            "https://mis.bjtu.edu.cn/auth/sso/?next=/",
            headers: ["Host": "mis.bjtu.edu.cn"]
        )

        let finalURL = firstResponse.url?.absoluteString ?? ""

        if finalURL == "https://mis.bjtu.edu.cn/home/" {
            return try parseStudentInfo(htmlString(firstData), studentID: credentials.username)
        }

        guard finalURL.contains("cas.bjtu.edu.cn") else {
            throw AppError.badResponse("CAS 跳转地址异常：\(finalURL)")
        }

        let nextValue = extractNextParameter(from: finalURL)
        var postComponents = URLComponents(string: "https://cas.bjtu.edu.cn/auth/login/")!
        postComponents.queryItems = [URLQueryItem(name: "next", value: nextValue)]
        let postURL = postComponents.url?.absoluteString ?? finalURL

        let casPageInfo = try await fetchCASLoginPage(casURL: finalURL)

        let modelAnswer = await captchaSolver.solve(imageData: casPageInfo.imageData)
        if let modelAnswer, !modelAnswer.isEmpty {
            if let info = try? await submitLogin(
                postURL: postURL,
                referer: finalURL,
                csrf: casPageInfo.csrf,
                captchaID: casPageInfo.captchaID,
                answer: modelAnswer,
                credentials: credentials
            ) {
                return info
            }
        }

        for _ in 0..<3 {
            let freshPage = try await fetchCASLoginPage(casURL: finalURL)
            let manualAnswer = await captchaCenter.requestAnswer(imageData: freshPage.imageData)
            guard let manualAnswer, !manualAnswer.isEmpty else {
                throw AppError.captchaRequired
            }

            if let info = try await submitLogin(
                postURL: postURL,
                referer: finalURL,
                csrf: freshPage.csrf,
                captchaID: freshPage.captchaID,
                answer: manualAnswer,
                credentials: credentials
            ) {
                return info
            }
        }

        throw AppError.badResponse("验证码多次错误，登录失败")
    }

    private struct CASPageInfo {
        let captchaID: String
        let csrf: String
        let imageData: Data
    }

    private func fetchCASLoginPage(casURL: String) async throws -> CASPageInfo {
        let (loginPageData, _) = try await http.get(
            casURL,
            headers: [
                "Host": "cas.bjtu.edu.cn",
                "Referer": "https://mis.bjtu.edu.cn/auth/sso/?next=/"
            ]
        )
        let loginDoc = try SwiftSoup.parse(htmlString(loginPageData))
        let captchaID = try loginDoc.select("input#id_captcha_0").attr("value")
        let csrf = try loginDoc.select("input[name=csrfmiddlewaretoken]").attr("value")
        guard !captchaID.isEmpty, !csrf.isEmpty else {
            throw AppError.parseFailed("CAS 登录页缺少验证码或 CSRF")
        }

        let (imageData, _) = try await http.get("https://cas.bjtu.edu.cn/image/\(captchaID)/")
        return CASPageInfo(captchaID: captchaID, csrf: csrf, imageData: imageData)
    }

    private func submitLogin(
        postURL: String,
        referer: String,
        csrf: String,
        captchaID: String,
        answer: String,
        credentials: Credentials
    ) async throws -> StudentInfo? {
        let (homeData, response) = try await http.postForm(
            postURL,
            headers: [
                "Referer": referer,
                "Origin": "https://cas.bjtu.edu.cn",
                "User-Agent": desktopUserAgent
            ],
            form: [
                "csrfmiddlewaretoken": csrf,
                "captcha_0": captchaID,
                "captcha_1": answer,
                "loginname": credentials.username,
                "password": credentials.password
            ]
        )

        let responseURL = response.url?.absoluteString ?? ""

        if responseURL.contains("mis.bjtu.edu.cn/home/") {
            return try parseStudentInfo(htmlString(homeData), studentID: credentials.username)
        }

        if responseURL.contains("cas.bjtu.edu.cn") {
            let errorDoc = try SwiftSoup.parse(htmlString(homeData))
            let errorText = (try? errorDoc.select(".alert-danger, .error, .form-error").first()?.text()) ?? ""
            if errorText.contains("密码") || errorText.contains("password") || errorText.contains("用户") || errorText.contains("账号") {
                throw AppError.badResponse("账号或密码错误")
            }
            return nil
        }

        throw AppError.badResponse("CAS 未跳转回 MIS 首页：\(responseURL)")
    }

    private func extractNextParameter(from casURL: String) -> String {
        guard let components = URLComponents(string: casURL),
              let nextItem = components.queryItems?.first(where: { $0.name == "next" }),
              let value = nextItem.value else {
            return "/"
        }
        return value
    }

    private func fetchGrades(ctype: String) async throws -> [Grade] {
        let (data, _) = try await http.get(
            "https://aa.bjtu.edu.cn/score/scores/stu/view/?page=1&perpage=500&ctype=\(ctype)",
            headers: ["Host": "aa.bjtu.edu.cn"]
        )
        let doc = try SwiftSoup.parse(htmlString(data))
        guard let table = try doc.select("table").first() else {
            throw AppError.parseFailed("成绩表为空")
        }

        return try table.select("tr").array().dropFirst().compactMap { row in
            let cols = try row.select("td").array()
            guard cols.count > 7 else { return nil }
            let year = cleanText(try cols[1].text())
            let courseName = cleanText(try cols[2].text())
            let courseGPA = cleanText(try cols[3].text()).isEmpty ? "0.0" : cleanText(try cols[3].text())
            let score = convertAndFormatGradeScore(cleanText(try cols[4].text()))
            let teacher = cleanText(try cols[6].text())
            let detailElement = try cols[7].select("span[data-content]").first()
            let detail: String
            if let raw = try detailElement?.attr("data-content"),
               let div = try SwiftSoup.parse(raw).select("div[style='width:200px;line-height:25px;']").first() {
                detail = try div.html()
                    .replacingOccurrences(of: "<br/>", with: "\n")
                    .replacingOccurrences(of: "<br />", with: "\n")
                    .replacingOccurrences(of: "<br>", with: "\n")
                    .replacingOccurrences(of: "\t", with: "")
                    .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
                    .trimmingCharacters(in: .whitespacesAndNewlines)
            } else {
                detail = ""
            }
            return Grade(
                courseName: courseName,
                courseTeacher: teacher,
                courseScore: score,
                courseCredits: courseGPA,
                courseYear: year,
                tag: year,
                detail: detail
            )
        }
    }

    private func fetchCourseGrid(isCurrentTerm: Bool) async throws -> [CourseScheduleItem] {
        let url = isCurrentTerm
            ? "https://aa.bjtu.edu.cn/course_selection/courseselecttask/schedule/"
            : "https://aa.bjtu.edu.cn/course_selection/courseselect/stuschedule/"
        let teachers = (try? await fetchTeachers()) ?? [:]
        let (data, _) = try await http.get(url, headers: ["Host": "aa.bjtu.edu.cn"])
        let doc = try SwiftSoup.parse(htmlString(data))
        guard let table = try doc.select("table").first() else {
            throw AppError.parseFailed("课程表为空")
        }

        var courses: [CourseScheduleItem] = []
        let rows = try table.select("tr").array().dropFirst()
        for (rowOffset, row) in rows.enumerated() {
            let cols = try row.select("td").array().dropFirst()
            for (columnOffset, col) in cols.enumerated() where cleanText((try? col.text()) ?? "").isEmpty == false {
                for child in col.children().array() {
                    guard let course = parseCourseElement(
                        child,
                        rowNumber: rowOffset + 1,
                        columnNumber: columnOffset + 1,
                        isCurrentTerm: isCurrentTerm,
                        teachers: teachers
                    ) else { continue }
                    courses.append(course)
                }
            }
        }
        return courses
    }

    private func parseCourseElement(_ child: Element, rowNumber: Int, columnNumber: Int, isCurrentTerm: Bool, teachers: [String: String]) -> CourseScheduleItem? {
        do {
            let rawIDAndName = isCurrentTerm
                ? (try child.select("span").first()?.html() ?? child.html())
                : (try child.html())
            let parts = rawIDAndName.components(separatedBy: "<br>")
            guard parts.count >= 2 else { return nil }

            let courseID = cleanText(try SwiftSoup.parse(parts[0]).text())
            let courseName: String
            if isCurrentTerm {
                courseName = cleanText(try SwiftSoup.parse(parts[1]).text())
            } else {
                courseName = cleanText(try SwiftSoup.parse(parts[1]).select("span").first()?.text() ?? SwiftSoup.parse(parts[1]).text())
            }

            let courseTime = cleanText(((try? child.select("div[style^=max-width]").first()?.text()) ?? "").components(separatedBy: "周").first.map { "\($0)周" } ?? "未知")
            let teacher: String
            if let parsed = try child.select("div[style^=max-width] i").first()?.text(), !parsed.isEmpty {
                teacher = parsed
            } else {
                let namePrefix = courseName.components(separatedBy: " ").first ?? courseName
                let idParts = courseID.components(separatedBy: " ")
                let key = idParts.count > 1 ? "\(namePrefix) \(String(idParts[1].dropFirst().prefix(2)))" : namePrefix
                teacher = teachers[key] ?? "?"
            }
            let place = cleanText((try? child.select("span.text-muted").first()?.text()) ?? "未知")

            return CourseScheduleItem(
                courseID: courseID,
                courseName: courseName,
                courseTeacher: teacher,
                courseLocationIndex: (rowNumber - 1) * Schedule.slotsPerDay + columnNumber,
                courseTime: courseTime,
                coursePlace: place.isEmpty ? "未知" : place,
                isCurrentSemester: isCurrentTerm
            )
        } catch {
            return nil
        }
    }

    private func fetchTeachers() async throws -> [String: String] {
        let (data, _) = try await http.get(
            "https://aa.bjtu.edu.cn/course_selection/courseselectabsent/absent_list/",
            headers: ["Host": "aa.bjtu.edu.cn"]
        )
        let doc = try SwiftSoup.parse(htmlString(data))
        guard let table = try doc.select("table").first() else { return [:] }
        var result: [String: String] = [:]
        for row in try table.select("tr").array().dropFirst(2) {
            let cols = try row.select("td").array()
            guard cols.count > 1 else { continue }
            var courseName = try cols[0].text()
            let nameParts = courseName.components(separatedBy: " ")
            if nameParts.count == 3 {
                courseName = "\(nameParts[0]) \(nameParts[1])"
            }
            result[courseName] = try cols[1].text()
        }
        return result
    }

    private func parseStudentInfo(_ html: String, studentID: String) throws -> StudentInfo {
        let doc = try SwiftSoup.parse(html)
        guard let nameRaw = try doc.select(".name_right > h3 > a").first()?.text() else {
            throw AppError.parseFailed("MIS 首页缺少学生姓名")
        }
        let name = nameRaw.components(separatedBy: "，").first ?? nameRaw
        let identity = (try? doc.select(".name_right .nr_con span:contains(身份)").first()?.text()) ?? studentID
        let department = (try? doc.select(".name_right .nr_con span:contains(部门)").first()?.text()) ?? ""
        return StudentInfo(
            name: name,
            studentID: identity.replacingOccurrences(of: "身份：", with: ""),
            department: department.replacingOccurrences(of: "部门：", with: ""),
            studentClass: studentID
        )
    }
}

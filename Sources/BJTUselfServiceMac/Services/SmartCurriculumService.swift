import Foundation
import SwiftSoup

@MainActor
final class SmartCurriculumService {
    private let http: HTTPClient
    private let mis: MISService
    private let decoder = JSONDecoder()

    private var sessionHeaders: [String: String] = [
        "User-Agent": desktopUserAgent,
        "Accept": "application/json, text/javascript, */*; q=0.01",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Referer": "http://123.121.147.7:88",
        "X-Requested-With": "XMLHttpRequest"
    ]

    private(set) var platformCourses: [PlatformCourse] = []
    private var prepared = false

    init(http: HTTPClient, mis: MISService) {
        self.http = http
        self.mis = mis
    }

    func prepare() async throws {
        if prepared { return }
        if !mis.isMISReady {
            guard mis.canLogin else { throw AppError.loginRequired }
            _ = try await mis.login(credentials: mis.storedCredentials!)
        }
        _ = try? await http.get("https://mis.bjtu.edu.cn/module/module/28/")
        try await setSessionID()
        let semester = try await fetchCurrentSemester()
        if let xqCode = semester.first?.xqCode {
            platformCourses = try await fetchPlatformCourses(xqCode: xqCode)
        }
        prepared = true
    }

    func fetchCurrentWeek() async throws -> Int {
        try await prepare()
        let (data, _) = try await http.get(
            "http://123.121.147.7:88/ve/back/coursePlatform/course.shtml?method=getTimeList",
            headers: sessionHeaders
        )
        let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        return Int(String(describing: object?["weekCode"] ?? "0")) ?? 0
    }

    func fetchHomework() async throws -> [HomeworkItem] {
        try await prepare()
        let homework = try await fetchHomework(type: 0)
        let courseDesign = try await fetchHomework(type: 1)
        let experiment = try await fetchHomework(type: 2)
        return homework + courseDesign + experiment
    }

    func buildCoursewareRoots() async throws -> [CoursewareNode] {
        try await prepare()
        var roots: [CoursewareNode] = []
        for course in platformCourses {
            let children = try await fetchChildren(parentID: 0, course: course)
            roots.append(CoursewareNode(course: course, resource: nil, bag: nil, children: children))
        }
        return roots
    }

    func teachingCalendarURL(for course: PlatformCourse) async throws -> URL {
        try await prepare()
        guard try await fetchTeacherID(for: course)?.isEmpty == false else {
            throw AppError.missingData("教师工号")
        }

        let (data, _) = try await http.get(
            "http://123.121.147.7:88/ve/back/coursePlatform/coursePlatform.shtml",
            headers: sessionHeaders
        )
        let doc = try SwiftSoup.parse(htmlString(data))
        guard let iframe = try doc.select("iframe#pdfIframe").first(),
              let src = try? iframe.attr("src"),
              src.isEmpty == false else {
            throw AppError.parseFailed("未找到教学日历 PDF")
        }

        let segments = src.split(separator: "/").map(String.init)
        guard segments.count >= 5 else { throw AppError.parseFailed("教学日历 URL 路径异常") }
        let key = segments.suffix(5).joined(separator: "/")
        guard let url = URL(string: "http://123.121.147.7:1936/kk/rp/\(key)") else {
            throw AppError.invalidURL(key)
        }
        return url
    }

    func coursewareDownloadURL(for resource: CoursewareResource) async throws -> (URL, String?) {
        try await prepare()
        let url = "http://123.121.147.7:88/ve/back/resourceSpace.shtml?method=rpinfoDownloadUrl&rpId=\(resource.rpID)"
        let (data, _) = try await http.postForm(url, headers: sessionHeaders, form: [:])
        let response = try decoder.decode(CoursewareDownloadResponse.self, from: data)
        guard let downloadURL = URL(string: response.rpURL) else {
            throw AppError.invalidURL(response.rpURL)
        }
        return (downloadURL, resource.rpName)
    }

    func downloadSubmittedHomework(_ homework: HomeworkItem) async throws -> URL {
        try await prepare()
        var components = URLComponents(string: "http://123.121.147.7:88/ve/back/course/courseWorkInfo.shtml")!
        components.queryItems = [
            URLQueryItem(name: "method", value: "piGaiDiv"),
            URLQueryItem(name: "upId", value: String(homework.upID)),
            URLQueryItem(name: "id", value: String(homework.idSnID ?? 0)),
            URLQueryItem(name: "score", value: homework.score),
            URLQueryItem(name: "uLevel", value: "1"),
            URLQueryItem(name: "type", value: "1"),
            URLQueryItem(name: "username", value: "null"),
            URLQueryItem(name: "userId", value: String(homework.userID))
        ]

        let (data, _) = try await http.get(components.url!.absoluteString, headers: sessionHeaders)
        let doc = try SwiftSoup.parse(htmlString(data))
        let items = try doc.select("div.homeworkContent").array()
        for item in items {
            let onclick = try item.attr("onclick")
            guard let match = onclick.range(of: #"\('([^']*)',\s*'([^']*)',\s*'([^']*)'\)"#, options: .regularExpression) else { continue }
            let matched = String(onclick[match])
            let values = matched
                .trimmingCharacters(in: CharacterSet(charactersIn: "()"))
                .components(separatedBy: ",")
                .map { $0.trimmingCharacters(in: CharacterSet(charactersIn: " '")) }
            guard values.count == 3 else { continue }

            var downloadComponents = URLComponents(string: "http://123.121.147.7:88/ve//downloadZyFj.shtml")!
            downloadComponents.queryItems = [
                URLQueryItem(name: "path", value: values[0]),
                URLQueryItem(name: "filename", value: values[1]),
                URLQueryItem(name: "id", value: values[2])
            ]
            let (fileData, _) = try await http.get(downloadComponents.url!.absoluteString, headers: sessionHeaders)
            let directory = documentsDownloadDirectory().appendingPathComponent(homework.courseName, isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let fileURL = directory.appendingPathComponent(sanitizedFileName(values[1]))
            try fileData.write(to: fileURL, options: [.atomic])
            return fileURL
        }
        throw AppError.missingData("未找到可下载的作业附件")
    }

    func uploadHomework(_ homework: HomeworkItem, fileURLs: [URL], content: String) async throws -> String {
        try await prepare()
        var fileInfo: [[String: String]] = []

        for fileURL in fileURLs {
            let fileData = try Data(contentsOf: fileURL)
            let (data, response) = try await http.postMultipart(
                "http://123.121.147.7:88/ve/back/rp/common/rpUpload.shtml",
                headers: sessionHeaders,
                parts: [.file(name: "file", filename: fileURL.lastPathComponent, data: fileData)]
            )
            guard (200..<300).contains(response.statusCode),
                  let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                throw AppError.badResponse("上传附件失败")
            }
            fileInfo.append([
                "fileNameNoExt": String(describing: object["fileNameNoExt"] ?? ""),
                "fileExtName": String(describing: object["fileExtName"] ?? ""),
                "fileSize": String(describing: object["fileSize"] ?? ""),
                "visitName": String(describing: object["visitName"] ?? ""),
                "pid": "",
                "ftype": "insert"
            ])
        }

        let fileListData = try JSONSerialization.data(withJSONObject: fileInfo)
        let fileList = String(decoding: fileListData, as: UTF8.self)
        let (data, _) = try await http.postForm(
            "http://123.121.147.7:88/ve/back/course/courseWorkInfo.shtml?method=sendStuHomeWorks",
            headers: sessionHeaders,
            form: [
                "content": content,
                "groupName": "",
                "groupId": "",
                "courseId": String(homework.courseID),
                "contentType": String(homework.homeworkType),
                "fz": "0",
                "jxrl_id": "",
                "fileList": fileList,
                "upId": String(homework.upID),
                "return_num": "",
                "isTeacher": "0"
            ]
        )
        return htmlString(data)
    }

    private func fetchTeacherID(for course: PlatformCourse) async throws -> String? {
        let url = "http://123.121.147.7:88/ve/back/coursePlatform/coursePlatform.shtml?method=toCoursePlatform&courseId=\(course.courseNum ?? "")&dataSource=1&cId=\(course.id)&xkhId=\(course.fzID ?? "")&xqCode=\(course.xqCode ?? "")"
        let (data, _) = try await http.get(url, headers: sessionHeaders)
        let doc = try SwiftSoup.parse(htmlString(data))
        return try doc.select("input#teacherId").first()?.attr("value")
    }

    private func fetchHomework(type: Int) async throws -> [HomeworkItem] {
        var result: [HomeworkItem] = []
        for course in platformCourses {
            let url = "http://123.121.147.7:88/ve/back/coursePlatform/homeWork.shtml?method=getHomeWorkList&cId=\(course.id)&subType=\(type)&page=1&pagesize=100"
            let (data, _) = try await http.get(url, headers: sessionHeaders)
            let cleaned = String(decoding: data, as: UTF8.self)
                .replacingOccurrences(of: #""courseNoteList"\s*:\s*"""#, with: #""courseNoteList":[]"#, options: .regularExpression)
            let response = try decoder.decode(HomeworkResponse.self, from: Data(cleaned.utf8))
            for note in response.courseNoteList ?? [] {
                result.append(note.toHomework(type: type))
            }
        }
        return result
    }

    private func setSessionID() async throws {
        let (data, response) = try await http.get(
            "http://123.121.147.7:88/ve/back/coursePlatform/message.shtml?method=getArticleList",
            headers: sessionHeaders
        )
        guard (200..<300).contains(response.statusCode) else {
            throw AppError.badResponse("获取 sessionId 失败：\(response.statusCode)")
        }
        let object = try decoder.decode(ArticleListResponse.self, from: data)
        sessionHeaders["sessionid"] = object.sessionID
    }

    private func fetchCurrentSemester() async throws -> [SemesterResult] {
        let (data, _) = try await http.get(
            "http://123.121.147.7:88/ve/back/rp/common/teachCalendar.shtml?method=queryCurrentXq",
            headers: sessionHeaders
        )
        return try decoder.decode(SemesterResponse.self, from: data).result ?? []
    }

    private func fetchPlatformCourses(xqCode: String) async throws -> [PlatformCourse] {
        let url = "http://123.121.147.7:88/ve/back/coursePlatform/course.shtml?method=getCourseList&pagesize=100&page=1&xqCode=\(xqCode)"
        let (data, _) = try await http.get(url, headers: sessionHeaders)
        return try decoder.decode(PlatformCourseResponse.self, from: data).courseList ?? []
    }

    private func fetchChildren(parentID: Int, course: PlatformCourse) async throws -> [CoursewareNode] {
        let url = "http://123.121.147.7:88/ve/back/coursePlatform/courseResource.shtml?method=stuQueryUploadResourceForCourseList&courseId=\(course.courseNum ?? "")&cId=\(course.courseNum ?? "")&xkhId=\(course.fzID ?? "")&xqCode=\(course.xqCode ?? "")&docType=1&up_id=\(parentID)&searchName="
        let (data, _) = try await http.get(url, headers: sessionHeaders)
        let cleaned = htmlString(data)
            .replacingOccurrences(of: #""resList"\s*:\s*"""#, with: #""resList":[]"#, options: .regularExpression)
            .replacingOccurrences(of: #""bagList"\s*:\s*"""#, with: #""bagList":[]"#, options: .regularExpression)
        let response = try decoder.decode(CourseResourceResponse.self, from: Data(cleaned.utf8))
        var nodes: [CoursewareNode] = []

        for bag in response.bagList ?? [] {
            let children = try await fetchChildren(parentID: bag.id, course: course)
            nodes.append(CoursewareNode(course: course, resource: nil, bag: bag, children: children))
        }
        for resource in response.resList ?? [] {
            nodes.append(CoursewareNode(course: course, resource: resource, bag: nil, children: []))
        }
        return nodes
    }
}

private struct ArticleListResponse: Decodable {
    var sessionID: String

    enum CodingKeys: String, CodingKey {
        case sessionID = "sessionId"
    }
}

private struct SemesterResponse: Decodable {
    var result: [SemesterResult]?
}

private struct SemesterResult: Decodable {
    var xqCode: String?
}

private struct PlatformCourseResponse: Decodable {
    var courseList: [PlatformCourse]?
}

private struct HomeworkResponse: Decodable {
    var courseNoteList: [CourseNoteDTO]?
}

private struct CourseNoteDTO: Decodable {
    var allCount: Int
    var courseID: Int
    var courseName: String
    var createDate: String
    var endTime: String
    var id: Int
    var openDate: String
    var scoreID: Int?
    var snID: Int?
    var status: Int
    var stuScore: String?
    var subStatus: String
    var submitCount: Int
    var title: String
    var content: String?
    var userID: Int?

    enum CodingKeys: String, CodingKey {
        case allCount
        case courseID = "course_id"
        case courseName = "course_name"
        case createDate = "create_date"
        case endTime = "end_time"
        case id
        case openDate = "open_date"
        case scoreID = "scoreId"
        case snID = "snId"
        case status
        case stuScore = "stu_score"
        case subStatus
        case submitCount
        case title
        case content
        case userID = "user_id"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        allCount = container.decodeLossyInt(forKey: .allCount) ?? 0
        courseID = container.decodeLossyInt(forKey: .courseID) ?? 0
        courseName = container.decodeLossyString(forKey: .courseName) ?? "未知课程"
        createDate = container.decodeLossyString(forKey: .createDate) ?? ""
        endTime = container.decodeLossyString(forKey: .endTime) ?? ""
        id = container.decodeLossyInt(forKey: .id) ?? 0
        openDate = container.decodeLossyString(forKey: .openDate) ?? ""
        scoreID = container.decodeLossyInt(forKey: .scoreID)
        snID = container.decodeLossyInt(forKey: .snID)
        status = container.decodeLossyInt(forKey: .status) ?? 0
        stuScore = container.decodeLossyString(forKey: .stuScore)
        subStatus = container.decodeLossyString(forKey: .subStatus) ?? "未知"
        submitCount = container.decodeLossyInt(forKey: .submitCount) ?? 0
        title = container.decodeLossyString(forKey: .title) ?? "未命名作业"
        content = container.decodeLossyString(forKey: .content)
        userID = container.decodeLossyInt(forKey: .userID)
    }

    func toHomework(type: Int) -> HomeworkItem {
        HomeworkItem(
            upID: id,
            idSnID: snID,
            score: stuScore ?? "",
            userID: userID ?? 0,
            courseID: courseID,
            courseName: courseName,
            title: title,
            content: content ?? "",
            createDate: createDate,
            endTime: endTime,
            openDate: openDate,
            status: status,
            submitCount: submitCount,
            allCount: allCount,
            subStatus: subStatus,
            scoreID: scoreID ?? 0,
            homeworkType: type
        )
    }
}

private struct CourseResourceResponse: Decodable {
    var bagList: [CoursewareBag]?
    var resList: [CoursewareResource]?
}

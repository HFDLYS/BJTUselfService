import Foundation

struct Credentials: Codable, Equatable {
    var username: String
    var password: String
}

struct StudentInfo: Codable, Equatable {
    var name: String
    var studentID: String
    var department: String
    var studentClass: String
}

struct AccountStatus: Codable, Equatable {
    var newMailCount: String = "查询中"
    var ecardBalance: String = "查询中"
    var netBalance: String = "查询中"
}

struct Grade: Identifiable, Codable, Equatable, Hashable {
    var id: String { "\(courseName)-\(courseCredits)-\(courseScore)-\(tag)" }
    var courseName: String
    var courseTeacher: String
    var courseScore: String
    var courseCredits: String
    var courseYear: String
    var tag: String
    var detail: String
}

struct CourseScheduleItem: Identifiable, Codable, Equatable, Hashable {
    var id: String { "\(courseID)-\(courseLocationIndex)-\(courseTime)-\(isCurrentSemester)" }
    var courseID: String
    var courseName: String
    var courseTeacher: String
    var courseLocationIndex: Int
    var courseTime: String
    var coursePlace: String
    var isCurrentSemester: Bool
}

struct ExamScheduleItem: Identifiable, Codable, Equatable, Hashable {
    var id: String { "\(courseName)-\(examType)-\(examTimeAndPlace)-\(detail)" }
    var examType: String
    var courseName: String
    var examTimeAndPlace: String
    var examStatus: String
    var detail: String
}

struct HomeworkItem: Identifiable, Codable, Equatable, Hashable {
    var id: Int { upID }
    var upID: Int
    var idSnID: Int?
    var score: String
    var userID: Int
    var courseID: Int
    var courseName: String
    var title: String
    var content: String
    var createDate: String
    var endTime: String
    var openDate: String
    var status: Int
    var submitCount: Int
    var allCount: Int
    var subStatus: String
    var scoreID: Int
    var homeworkType: Int
}

struct ClassroomCapacity: Identifiable, Codable, Equatable, Hashable {
    var id: String { roomName }
    var roomName: String
    var capacity: Int
    var used: Int
}

struct BuildingInfo: Codable, Equatable {
    var buildingName: String
    var classroomList: [ClassroomCapacity]
    var effectiveDateStart: String
    var effectiveDateEnd: String
}

struct PlatformCourse: Identifiable, Codable, Equatable, Hashable {
    var beginDate: String?
    var courseNum: String?
    var endDate: String?
    var fzID: String?
    var id: Int
    var name: String
    var teacherID: Int?
    var teacherName: String?
    var type: Int?
    var xqCode: String?

    enum CodingKeys: String, CodingKey {
        case beginDate = "begin_date"
        case courseNum = "course_num"
        case endDate = "end_date"
        case fzID = "fz_id"
        case id
        case name
        case teacherID = "teacher_id"
        case teacherName = "teacher_name"
        case type
        case xqCode = "xq_code"
    }

    init(beginDate: String? = nil, courseNum: String? = nil, endDate: String? = nil, fzID: String? = nil, id: Int = 0, name: String = "default value", teacherID: Int? = nil, teacherName: String? = nil, type: Int? = nil, xqCode: String? = nil) {
        self.beginDate = beginDate
        self.courseNum = courseNum
        self.endDate = endDate
        self.fzID = fzID
        self.id = id
        self.name = name
        self.teacherID = teacherID
        self.teacherName = teacherName
        self.type = type
        self.xqCode = xqCode
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        beginDate = container.decodeLossyString(forKey: .beginDate)
        courseNum = container.decodeLossyString(forKey: .courseNum)
        endDate = container.decodeLossyString(forKey: .endDate)
        fzID = container.decodeLossyString(forKey: .fzID)
        id = container.decodeLossyInt(forKey: .id) ?? 0
        name = container.decodeLossyString(forKey: .name) ?? "未命名课程"
        teacherID = container.decodeLossyInt(forKey: .teacherID)
        teacherName = container.decodeLossyString(forKey: .teacherName)
        type = container.decodeLossyInt(forKey: .type)
        xqCode = container.decodeLossyString(forKey: .xqCode)
    }
}

struct CoursewareBag: Identifiable, Codable, Equatable, Hashable {
    var id: Int
    var bagName: String
    var upID: Int

    enum CodingKeys: String, CodingKey {
        case id
        case bagName = "bag_name"
        case upID = "up_id"
    }

    init(id: Int, bagName: String, upID: Int) {
        self.id = id
        self.bagName = bagName
        self.upID = upID
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = container.decodeLossyInt(forKey: .id) ?? 0
        bagName = container.decodeLossyString(forKey: .bagName) ?? "未命名文件夹"
        upID = container.decodeLossyInt(forKey: .upID) ?? 0
    }
}

struct CoursewareResource: Identifiable, Codable, Equatable, Hashable {
    var id: Int { resID }
    var extName: String
    var playURL: String?
    var resID: Int
    var resURL: String
    var rpID: String
    var rpName: String
    var rpSize: String
    var teacherName: String

    enum CodingKeys: String, CodingKey {
        case extName
        case playURL = "play_url"
        case resID = "resId"
        case resURL = "res_url"
        case rpID = "rpId"
        case rpName
        case rpSize
        case teacherName
    }

    init(extName: String, playURL: String?, resID: Int, resURL: String, rpID: String, rpName: String, rpSize: String, teacherName: String) {
        self.extName = extName
        self.playURL = playURL
        self.resID = resID
        self.resURL = resURL
        self.rpID = rpID
        self.rpName = rpName
        self.rpSize = rpSize
        self.teacherName = teacherName
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        extName = container.decodeLossyString(forKey: .extName) ?? ""
        playURL = container.decodeLossyString(forKey: .playURL)
        resID = container.decodeLossyInt(forKey: .resID) ?? 0
        resURL = container.decodeLossyString(forKey: .resURL) ?? ""
        rpID = container.decodeLossyString(forKey: .rpID) ?? ""
        rpName = container.decodeLossyString(forKey: .rpName) ?? "未命名资源"
        rpSize = container.decodeLossyString(forKey: .rpSize) ?? ""
        teacherName = container.decodeLossyString(forKey: .teacherName) ?? ""
    }
}

struct CoursewareNode: Identifiable, Codable, Equatable, Hashable {
    var id: String {
        if let resource { return "res-\(resource.resID)" }
        if let bag { return "bag-\(bag.id)" }
        return "course-\(course.id)"
    }

    var course: PlatformCourse
    var resource: CoursewareResource?
    var bag: CoursewareBag?
    var children: [CoursewareNode]

    var displayName: String {
        if let resource { return resource.rpName }
        if let bag { return bag.bagName }
        return course.name
    }
}

struct DownloadTaskStatus: Identifiable, Codable, Equatable {
    enum State: String, Codable {
        case downloading
        case completed
        case failed
    }

    var id: String
    var filename: String
    var relativePath: String
    var progress: Double
    var state: State
    var message: String
}

struct ChangeNotice: Identifiable, Codable, Equatable {
    enum Category: String, Codable {
        case grade = "成绩"
        case course = "课程表"
        case exam = "考试"
        case homework = "作业"
    }

    enum Kind: String, Codable {
        case added = "新增"
        case modified = "修改"
        case deleted = "删除"
    }

    var id = UUID()
    var category: Category
    var kind: Kind
    var title: String
    var detail: String
}

struct AppCache: Codable, Equatable {
    var grades: [Grade] = []
    var courses: [CourseScheduleItem] = []
    var exams: [ExamScheduleItem] = []
    var homework: [HomeworkItem] = []
    var classroomMap: [String: [Int]] = [:]
    var coursewareRoots: [CoursewareNode] = []
    var currentWeek: Int = 0
    var lastRefreshDate: Date?
}

struct GitHubRelease: Codable, Equatable {
    var tagName: String
    var name: String?
    var body: String?
    var htmlURL: URL
    var publishedAt: String

    enum CodingKeys: String, CodingKey {
        case tagName = "tag_name"
        case name
        case body
        case htmlURL = "html_url"
        case publishedAt = "published_at"
    }
}

struct CoursewareDownloadResponse: Codable {
    var downloadType: String?
    var flag: Bool
    var html: String?
    var rpURL: String

    enum CodingKeys: String, CodingKey {
        case downloadType = "download_type"
        case flag
        case html
        case rpURL = "rpUrl"
    }
}

extension KeyedDecodingContainer {
    func decodeLossyString(forKey key: Key) -> String? {
        if let value = try? decode(String.self, forKey: key) { return value }
        if let value = try? decode(Int.self, forKey: key) { return String(value) }
        if let value = try? decode(Double.self, forKey: key) { return String(value) }
        if let value = try? decode(Bool.self, forKey: key) { return value ? "true" : "false" }
        return nil
    }

    func decodeLossyInt(forKey key: Key) -> Int? {
        if let value = try? decode(Int.self, forKey: key) { return value }
        if let value = try? decode(String.self, forKey: key) { return Int(value.trimmingCharacters(in: .whitespacesAndNewlines)) }
        if let value = try? decode(Double.self, forKey: key) { return Int(value) }
        return nil
    }
}

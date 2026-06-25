import AppKit
import Foundation
import SwiftSoup

enum AppError: LocalizedError {
    case invalidURL(String)
    case badResponse(String)
    case loginRequired
    case captchaRequired
    case parseFailed(String)
    case missingData(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL(let value): return "无效 URL：\(value)"
        case .badResponse(let value): return "请求失败：\(value)"
        case .loginRequired: return "需要先登录"
        case .captchaRequired: return "需要输入验证码"
        case .parseFailed(let value): return "解析失败：\(value)"
        case .missingData(let value): return "缺少数据：\(value)"
        }
    }
}

enum API {
    static let classroomCapacity = "http://yaya.csoci.com:2333/api/classnum/?building="
    static let classroomView = "http://yaya.csoci.com:2333/5bb358e5442b0b77d9b04d94efe423e349fbcb6f88c93ce4d2988e3fa126f3e4/classroom/"
    static let githubLatest = "https://api.github.com/repos/HFDLYS/BJTUselfService/releases/latest"
}

let desktopUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36 Edg/137.0.0.0"

enum Schedule {
    static let slotsPerDay = 8
    static let totalWeeks = 26
    static let urgentThresholdHours = 48
    static let maxDashboardItems = 6

    struct Period {
        let index: Int
        let label: String
        let startMinutes: Int
        let endMinutes: Int
    }

    static let periods: [Period] = [
        .init(index: 0, label: "第一节\n08:00-09:50", startMinutes: 8 * 60, endMinutes: 9 * 60 + 50),
        .init(index: 1, label: "第二节\n10:10-12:00", startMinutes: 10 * 60 + 10, endMinutes: 12 * 60),
        .init(index: 2, label: "第三节\n12:10-14:00", startMinutes: 12 * 60 + 10, endMinutes: 14 * 60),
        .init(index: 3, label: "第四节\n14:10-16:00", startMinutes: 14 * 60 + 10, endMinutes: 16 * 60),
        .init(index: 4, label: "第五节\n16:20-18:10", startMinutes: 16 * 60 + 20, endMinutes: 18 * 60 + 10),
        .init(index: 5, label: "第六节\n19:00-20:50", startMinutes: 19 * 60, endMinutes: 20 * 60 + 50),
        .init(index: 6, label: "第七节\n21:00-21:50", startMinutes: 21 * 60, endMinutes: 21 * 60 + 50)
    ]

    static func currentPeriodIndex() -> Int {
        let now = Calendar.current.dateComponents([.hour, .minute], from: Date())
        let minutes = (now.hour ?? 0) * 60 + (now.minute ?? 0)
        return periods.first { $0.startMinutes...$0.endMinutes ~= minutes }?.index ?? -1
    }
}

enum AAHeaders {
    static let base: [String: String] = [
        "Referer": "https://aa.bjtu.edu.cn/course_selection/courseselecttask/selects/",
        "Host": "aa.bjtu.edu.cn",
        "X-Requested-With": "XMLHttpRequest",
        "Accept": "*/*",
        "Accept-Language": "zh-CN,zh;q=0.9"
    ]

    static let submit: [String: String] = [
        "Referer": "https://aa.bjtu.edu.cn/course_selection/courseselecttask/selects/",
        "Host": "aa.bjtu.edu.cn",
        "Origin": "https://aa.bjtu.edu.cn",
        "X-Requested-With": "XMLHttpRequest",
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "Accept": "*/*",
        "Accept-Language": "zh-CN,zh;q=0.9"
    ]
}

func htmlString(_ data: Data) -> String {
    String(data: data, encoding: .utf8) ?? String(decoding: data, as: UTF8.self)
}

func cleanText(_ text: String) -> String {
    text.replacingOccurrences(of: "\n", with: "")
        .replacingOccurrences(of: "\t", with: "")
        .replacingOccurrences(of: " ", with: "")
        .trimmingCharacters(in: .whitespacesAndNewlines)
}

func calculateCaptchaExpression(_ expression: String) -> String? {
    let normalized = expression
        .replacingOccurrences(of: "×", with: "*")
        .replacingOccurrences(of: "x", with: "*")
        .replacingOccurrences(of: "X", with: "*")
        .replacingOccurrences(of: "－", with: "-")
        .replacingOccurrences(of: "=", with: "")
        .filter { "0123456789+-*".contains($0) }

    let pattern = #"^(\d+)([+\-*])(\d+)$"#
    guard let match = normalized.range(of: pattern, options: .regularExpression) else { return nil }
    let matched = String(normalized[match])
    let scanner = Scanner(string: matched)
    var left = 0
    var right = 0
    guard scanner.scanInt(&left), let op = scanner.scanCharacter(), scanner.scanInt(&right) else { return nil }

    switch op {
    case "+": return String(left + right)
    case "-": return String(left - right)
    case "*": return String(left * right)
    default: return nil
    }
}

func convertAndFormatGradeScore(_ input: String) -> String {
    let gradeToScore: [String: Int] = [
        "A": 95, "A-": 87, "B+": 83, "B": 79, "B-": 76,
        "C+": 73, "C": 69, "C-": 66, "D+": 63, "D": 60, "F": 30
    ]

    if let score = Int(input) {
        let grade: String
        switch score {
        case 90...: grade = "A"
        case 85..<90: grade = "A-"
        case 81..<85: grade = "B+"
        case 78..<81: grade = "B"
        case 75..<78: grade = "B-"
        case 71..<75: grade = "C+"
        case 68..<71: grade = "C"
        case 65..<68: grade = "C-"
        case 61..<65: grade = "D+"
        case 60: grade = "D"
        default: grade = "F"
        }
        return "\(grade),\(score)"
    }

    if let score = gradeToScore[input] {
        return "\(input),\(score)"
    }
    return "-,-"
}

func numericScore(_ value: String) -> Int {
    let cleaned = value.replacingOccurrences(of: ",", with: "")
    let digits = cleaned.filter { $0.isNumber || $0 == "." }
    return Int(Double(digits) ?? -1)
}

func gradeColor(for score: Int) -> NSColor {
    guard score >= 0 else { return .secondaryLabelColor }
    if score < 60 { return NSColor.systemRed }
    let proportion = Double(score - 60) / 40.0
    let green = CGFloat(max(0, min(1, proportion)))
    return NSColor(calibratedRed: 1 - green, green: green * 0.75, blue: 0.08, alpha: 1)
}

func stableCourseColor(_ courseID: String, darkMode: Bool) -> NSColor {
    var hash = UInt64(1469598103934665603)
    for byte in courseID.utf8 {
        hash ^= UInt64(byte)
        hash &*= 1099511628211
    }

    func component(_ shift: UInt64, base: CGFloat, span: CGFloat) -> CGFloat {
        let value = CGFloat((hash >> shift) & 0xff) / 255
        return base + value * span
    }

    if darkMode {
        return NSColor(calibratedRed: component(0, base: 0.14, span: 0.25), green: component(8, base: 0.15, span: 0.25), blue: component(16, base: 0.18, span: 0.28), alpha: 1)
    }
    return NSColor(calibratedRed: component(0, base: 0.68, span: 0.24), green: component(8, base: 0.72, span: 0.22), blue: component(16, base: 0.78, span: 0.18), alpha: 1)
}

func parseCourseWeeks(_ courseTime: String) -> Set<Int> {
    let cleaned = courseTime.replacingOccurrences(of: "第", with: "").replacingOccurrences(of: "周", with: "")
    var weeks = Set<Int>()
    for part in cleaned.split(separator: ",") {
        if part.contains("-") {
            let pair = part.split(separator: "-").compactMap { Int($0) }
            if pair.count == 2 {
                for week in pair[0]...pair[1] { weeks.insert(week) }
            }
        } else if let week = Int(part) {
            weeks.insert(week)
        }
    }
    return weeks
}

func documentsDownloadDirectory() -> URL {
    let base = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first!
    let directory = base.appendingPathComponent("交大自由行下载目录", isDirectory: true)
    try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    return directory
}

func sanitizedFileName(_ name: String) -> String {
    let invalid = CharacterSet(charactersIn: "/\\?%*|\"<>:")
    return name.components(separatedBy: invalid).joined(separator: "_")
}

func decodeContentDispositionFilename(_ value: String?) -> String? {
    guard let value else { return nil }
    let pieces = value.components(separatedBy: ";").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
    for piece in pieces {
        if piece.lowercased().hasPrefix("filename*="), let idx = piece.firstIndex(of: "=") {
            let raw = String(piece[piece.index(after: idx)...])
            return raw.replacingOccurrences(of: "UTF-8''", with: "").removingPercentEncoding
        }
        if piece.lowercased().hasPrefix("filename="), let idx = piece.firstIndex(of: "=") {
            return String(piece[piece.index(after: idx)...]).trimmingCharacters(in: CharacterSet(charactersIn: "\"")).removingPercentEncoding
        }
    }
    return nil
}

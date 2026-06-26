import Foundation
import SwiftSoup

struct GrabbableCourse: Identifiable, Hashable {
    var id: String { courseID }
    var courseID: String
    var courseCode: String
    var courseName: String
    var rawText: String
}

struct CaptchaInfo {
    var hash: String
    var imageData: Data
}

enum CourseSelectType: String, CaseIterable, Identifiable {
    case school = "全校任选课"
    case cross = "跨选课"
    case others = "其他院系专业课"

    var id: String { rawValue }

    var urlValue: String {
        switch self {
        case .school: return "school"
        case .cross: return "cross"
        case .others: return "others"
        }
    }
}

@MainActor
final class CourseGrabbingService {
    private let http: HTTPClient
    private let mis: MISService

    init(http: HTTPClient, mis: MISService) {
        self.http = http
        self.mis = mis
    }

    func fetchCaptcha() async throws -> CaptchaInfo {
        try await mis.ensureAA()

        let (data, _) = try await http.get(
            "https://aa.bjtu.edu.cn/captcha/refresh/",
            headers: AAHeaders.base
        )

        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let hash = object["key"] as? String, !hash.isEmpty else {
            throw AppError.parseFailed("验证码 key 获取失败")
        }

        let (imageData, _) = try await http.get(
            "https://aa.bjtu.edu.cn/captcha/image/\(hash)/",
            headers: AAHeaders.base
        )

        return CaptchaInfo(hash: hash, imageData: imageData)
    }

    func searchCourses(type: CourseSelectType, keywords: [String]) async throws -> [GrabbableCourse] {
        try await mis.ensureAA()

        let urlString = "https://aa.bjtu.edu.cn/course_selection/courseselecttask/selects_action/?zxjxjhh=&kch=&action=load&iframe=\(type.urlValue)&submit=+%E6%9F%A5+%E8%AF%A2+&has_advance_query=&page=1&perpage=8000"

        let (data, _) = try await http.get(urlString, headers: AAHeaders.base)
        let doc = try SwiftSoup.parse(htmlString(data))
        guard let table = try doc.select("table").first() else {
            throw AppError.parseFailed("未找到课程表")
        }

        var results: [GrabbableCourse] = []
        let rows = try table.select("tr").array()
        for row in rows {
            let cols = try row.select("td").array()
            guard cols.count >= 3 else { continue }
            guard let input = try cols[0].select("input").first(),
                  let courseID = try? input.attr("value"), !courseID.isEmpty else { continue }

            let courseCode = try cols[1].text()
            let courseName = try cols[2].text()
            let fullText = "\(courseCode) \(courseName)"

            if keywords.isEmpty {
                results.append(GrabbableCourse(courseID: courseID, courseCode: courseCode, courseName: courseName, rawText: fullText))
            } else {
                for keyword in keywords {
                    let cleanedKeyword = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !cleanedKeyword.isEmpty && fullText.localizedCaseInsensitiveContains(cleanedKeyword) {
                        results.append(GrabbableCourse(courseID: courseID, courseCode: courseCode, courseName: courseName, rawText: fullText))
                        break
                    }
                }
            }
        }
        return results
    }

    func submitCourses(courseIDs: [String], captchaHash: String, captchaAnswer: String) async throws -> String {
        try await mis.ensureAA()

        let checkboxs = courseIDs.joined(separator: ",")
        let (data, response) = try await http.postForm(
            "https://aa.bjtu.edu.cn/course_selection/courseselecttask/selects_action/?action=submit",
            headers: AAHeaders.submit,
            form: [
                "checkboxs": checkboxs,
                "hashkey": captchaHash,
                "answer": captchaAnswer
            ]
        )

        let setCookie = response.value(forHTTPHeaderField: "Set-Cookie") ?? ""
        if let message = extractMessage(from: setCookie) {
            let decoded = try? await analyzeMessage(message)
            return decoded ?? "选课已提交，请查看结果"
        }

        let body = htmlString(data)
        if body.contains("成功") { return "选课成功" }
        if body.contains("失败") || body.contains("错误") { return "选课失败，请重试" }
        return "选课已提交"
    }

    func deleteCourse(courseID: String) async throws -> String {
        try await mis.ensureAA()

        let (_, response) = try await http.postForm(
            "https://aa.bjtu.edu.cn/course_selection/courseselecttask/selects_action/?action=delete",
            headers: AAHeaders.submit,
            form: ["select_id": courseID]
        )

        let setCookie = response.value(forHTTPHeaderField: "Set-Cookie") ?? ""
        if let message = extractMessage(from: setCookie) {
            let decoded = try? await analyzeMessage(message)
            return decoded ?? "退课已提交，请查看结果"
        }
        return "退课已提交"
    }

    private func analyzeMessage(_ message: String) async throws -> String {
        var headers = AAHeaders.base
        headers["Cookie"] = "messages=\(message)"

        let (data, _) = try await http.get(
            "https://aa.bjtu.edu.cn/course_selection/courseselecttask/selects/",
            headers: headers
        )

        let html = htmlString(data)
        let pattern = #"message\s*\+=\s*"(.*?)(<br/>|<br>|");"#
        if let regex = try? NSRegularExpression(pattern: pattern, options: [.dotMatchesLineSeparators]) {
            let nsString = html as NSString
            let matches = regex.matches(in: html, range: NSRange(location: 0, length: nsString.length))
            var decoded = ""
            for match in matches {
                if match.numberOfRanges > 1 {
                    decoded += decodeUnicodeEscape(nsString.substring(with: match.range(at: 1)))
                }
            }
            return decoded.isEmpty ? "操作完成" : decoded
        }
        return "操作完成"
    }

    private func extractMessage(from setCookie: String) -> String? {
        guard setCookie.contains("messages=") else { return nil }
        let pattern = "messages=([^;]+)"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let nsString = setCookie as NSString
        let matches = regex.matches(in: setCookie, range: NSRange(location: 0, length: nsString.length))
        if let match = matches.first, match.numberOfRanges > 1 {
            return nsString.substring(with: match.range(at: 1))
        }
        return nil
    }

    private func decodeUnicodeEscape(_ string: String) -> String {
        var result = string
        let pattern = #"\\u([0-9a-fA-F]{4})"#
        if let regex = try? NSRegularExpression(pattern: pattern) {
            let nsString = result as NSString
            let matches = regex.matches(in: result, range: NSRange(location: 0, length: nsString.length)).reversed()
            for match in matches {
                if match.numberOfRanges > 1 {
                    let hex = nsString.substring(with: match.range(at: 1))
                    if let code = UInt32(hex, radix: 16), let scalar = Unicode.Scalar(code) {
                        result = (result as NSString).replacingCharacters(in: match.range, with: String(scalar))
                    }
                }
            }
        }
        return result
    }
}

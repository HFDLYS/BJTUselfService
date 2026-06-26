import AppKit
import Foundation
import SwiftSoup

@MainActor
final class DownloadAndUpdateService {
    private let http: HTTPClient
    private let mis: MISService
    private let smart: SmartCurriculumService
    private let decoder = JSONDecoder()

    init(http: HTTPClient, mis: MISService, smart: SmartCurriculumService) {
        self.http = http
        self.mis = mis
        self.smart = smart
    }

    func fetchLatestRelease() async throws -> GitHubRelease {
        let (data, _) = try await http.get(API.githubLatest)
        return try decoder.decode(GitHubRelease.self, from: data)
    }

    func downloadSchoolCalendar() async throws -> URL {
        try await mis.ensureBKSY()
        let (data, _) = try await http.get(
            "https://bksy.bjtu.edu.cn/Admin/SemesterTranPage.aspx?noRemark=1",
            headers: ["User-Agent": desktopUserAgent]
        )
        let html = htmlString(data)
        let doc = try SwiftSoup.parse(html)
        let script = try doc.select("script").html()
        guard let listRange = script.range(of: #"\[[\s\S]*\]"#, options: .regularExpression) else {
            throw AppError.parseFailed("校历页面未找到资源列表")
        }
        let list = String(script[listRange])
        guard let urlRange = list.range(of: #"url\s*:\s*"([^"]+)""#, options: .regularExpression) else {
            throw AppError.parseFailed("校历页面未找到下载地址")
        }
        let matched = String(list[urlRange])
        let postfix = matched.components(separatedBy: "\"").dropFirst().first ?? ""
        let url = "https://bksy.bjtu.edu.cn\(postfix)"
        return try await downloadFile(urlString: url, preferredName: "校历")
    }

    func downloadGradeTranscript(english: Bool) async throws -> URL {
        try await mis.ensureAA()
        let type = english ? "card_en_sign" : "card_cn_sign"
        let title = english ? "英文成绩单.pdf" : "中文成绩单.pdf"
        let url = "https://aa.bjtu.edu.cn/score/scorecard/stu/5201314/download_pdf/?type=\(type)&has_advance_query="
        return try await downloadFile(urlString: url, preferredName: title)
    }

    func downloadTeachingCalendar(for course: PlatformCourse) async throws -> URL {
        let url = try await smart.teachingCalendarURL(for: course)
        return try await downloadFile(urlString: url.absoluteString, preferredName: "\(course.name)_教学日历.pdf", subdirectory: course.name)
    }

    func downloadCoursewareNode(_ node: CoursewareNode, basePath: String = "") async throws -> [URL] {
        if let resource = node.resource {
            let (downloadURL, suggestedName) = try await smart.coursewareDownloadURL(for: resource)
            let targetName = suggestedName ?? resource.rpName
            let file = try await downloadFile(urlString: downloadURL.absoluteString, preferredName: targetName, subdirectory: basePath)
            return [file]
        }

        var urls: [URL] = []
        let nextPath = [basePath, node.displayName].filter { !$0.isEmpty }.joined(separator: "/")
        for child in node.children {
            urls += try await downloadCoursewareNode(child, basePath: nextPath)
        }
        return urls
    }

    func subscribeHomeworkNotice(studentID: String, email: String, normalThresholdHours: Int, emergencyThresholdHours: Int) async throws -> Bool {
        let (data, _) = try await http.postForm(
            "https://love.nimisora.icu/homework-notify/process.php",
            form: [
                "student_id": studentID,
                "email": email,
                "threshold_1": String(normalThresholdHours),
                "threshold_2": String(emergencyThresholdHours)
            ]
        )
        return String(decoding: data, as: UTF8.self).contains("success")
    }

    func openNetworkRechargePage() {
        if let url = URL(string: "https://weixin.bjtu.edu.cn/pay/wap/network/recharge.html") {
            NSWorkspace.shared.open(url)
        }
    }

    func openEcardRechargeHint() {
        if let url = URL(string: "https://webvpn.bjtu.edu.cn/") {
            NSWorkspace.shared.open(url)
        }
    }

    private func downloadFile(urlString: String, preferredName: String, subdirectory: String = "") async throws -> URL {
        let (data, response) = try await http.get(urlString)
        let filename = decodeContentDispositionFilename(response.value(forHTTPHeaderField: "Content-Disposition"))
            ?? preferredName
        var directory = documentsDownloadDirectory()
        if !subdirectory.isEmpty {
            directory = directory.appendingPathComponent(sanitizedFileName(subdirectory), isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        }
        let fileURL = directory.appendingPathComponent(sanitizedFileName(filename))
        try data.write(to: fileURL, options: [.atomic])
        return fileURL
    }
}

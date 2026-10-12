import Foundation
import WidgetKit

// 与 shared/.../feature/widget/CourseWidgetStore.ios.kt 保持一致。
let courseWidgetAppGroup = "group.team.bjtuss.bjtuselfservice.kmp"
let courseWidgetFileName = "course-widget.json"

// MARK: - 快照（主应用写入的 JSON）

struct CourseWidgetSnapshot: Codable {
    let version: Int
    let generatedAt: Int64
    let days: [CourseWidgetDay]
}

struct CourseWidgetDay: Codable {
    let date: String
    let week: Int?
    let sessions: [CourseWidgetSession]
}

struct CourseWidgetSession: Codable, Hashable {
    let name: String
    let place: String
    let teacher: String
    let startMinute: Int
    let endMinute: Int
    let isLab: Bool
    let color: Int64
}

enum CourseWidgetStore {
    enum LoadResult {
        case loaded(CourseWidgetSnapshot)
        /// 共享容器打不开：安装时 App Group 没生效。
        case noSharedContainer
        /// 容器正常但还没有快照：App 还没同步过课表，或已退出登录。
        case noSnapshot

        var isLoaded: Bool {
            if case .loaded = self { return true }
            return false
        }
    }

    static func load() -> LoadResult {
        let containers = sharedContainers()
        guard !containers.isEmpty else { return .noSharedContainer }
        // SideStore 等重签工具会改 App Group 名；多个候选都试，取有快照的那个。
        for container in containers {
            let url = container.appendingPathComponent(courseWidgetFileName)
            if let data = try? Data(contentsOf: url),
               let snapshot = try? JSONDecoder().decode(CourseWidgetSnapshot.self, from: data) {
                return .loaded(snapshot)
            }
        }
        return .noSnapshot
    }

    private static func sharedContainers() -> [URL] {
        var bundles = [Bundle.main.bundleURL]
        // 扩展位于 宿主.app/PlugIns/xxx.appex，宿主的描述文件也读一遍。
        let host = Bundle.main.bundleURL.deletingLastPathComponent().deletingLastPathComponent()
        if host.pathExtension == "app" { bundles.append(host) }
        var candidates = bundles.flatMap(groupsFromProfile(in:))
        candidates.append(courseWidgetAppGroup)
        var seen = Set<String>()
        return candidates
            .filter { seen.insert($0).inserted }
            .compactMap { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: $0) }
    }

    /// 与 CourseWidgetStore.ios.kt 的 courseWidgetGroupsFromProfile 同一规则：
    /// SideStore/AltStore 把 App Group 重命名为“原名.TeamID”，从 embedded.mobileprovision 里找出来。
    private static func groupsFromProfile(in bundle: URL) -> [String] {
        let url = bundle.appendingPathComponent("embedded.mobileprovision")
        guard let data = try? Data(contentsOf: url),
              let raw = String(data: data, encoding: .isoLatin1),
              let start = raw.range(of: "<?xml"),
              let end = raw.range(of: "</plist>", range: start.lowerBound..<raw.endIndex),
              let plistData = String(raw[start.lowerBound..<end.upperBound]).data(using: .isoLatin1),
              let plist = try? PropertyListSerialization.propertyList(from: plistData, format: nil) as? [String: Any],
              let entitlements = plist["Entitlements"] as? [String: Any],
              let groups = entitlements["com.apple.security.application-groups"] as? [String] else {
            return []
        }
        return groups.filter { $0 == courseWidgetAppGroup || $0.hasPrefix(courseWidgetAppGroup + ".") }
    }
}

// MARK: - 某一时刻要展示什么

/// 一节课落到具体日期上的时间段。
struct CourseOccurrence: Hashable, Identifiable {
    let session: CourseWidgetSession
    let start: Date
    let end: Date
    var id: String { "\(session.name)-\(start.timeIntervalSince1970)" }

    func isOngoing(at now: Date) -> Bool { start <= now && now < end }
    func isFinished(at now: Date) -> Bool { end <= now }
}

/// 一天的课，带日期与教学周。
struct CourseDayPlan {
    let date: Date
    let week: Int?
    let occurrences: [CourseOccurrence]
}

enum CourseWidgetState {
    /// 共享容器打不开：安装方式没有保留 App Group。
    case noSharedContainer
    /// 没有快照：App 还没同步过课表，或已退出登录。
    case signedOut
    /// 快照没覆盖到今天：太久没打开 App。
    case stale
    case ready(CourseWidgetModel)
}

struct CourseWidgetModel {
    let now: Date
    let today: CourseDayPlan
    /// 今天之后第一个有课的日子（最多看 snapshot 覆盖范围）。
    let nextDay: CourseDayPlan?

    var ongoing: CourseOccurrence? { today.occurrences.first { $0.isOngoing(at: now) } }

    /// 今天还没结束的课（含正在上的）。
    var remainingToday: [CourseOccurrence] { today.occurrences.filter { !$0.isFinished(at: now) } }

    /// 临近课程：正在上的那节 + 之后一节；今天上完了就取下一个有课日的前两节。
    var upNext: (primary: CourseOccurrence, secondary: CourseOccurrence?, isFutureDay: Bool)? {
        let remaining = remainingToday
        if let first = remaining.first {
            return (first, remaining.dropFirst().first, false)
        }
        if let day = nextDay, let first = day.occurrences.first {
            return (first, day.occurrences.dropFirst().first, true)
        }
        return nil
    }
}

enum CourseWidgetCalendar {
    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        calendar.locale = Locale(identifier: "zh_CN")
        return calendar
    }

    static func dayKey(_ date: Date) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    static func date(fromKey key: String) -> Date? {
        let pieces = key.split(separator: "-").compactMap { Int($0) }
        guard pieces.count == 3 else { return nil }
        return calendar.date(from: DateComponents(year: pieces[0], month: pieces[1], day: pieces[2]))
    }

    static func plan(for day: CourseWidgetDay) -> CourseDayPlan? {
        guard let start = date(fromKey: day.date) else { return nil }
        let occurrences = day.sessions.map { session in
            CourseOccurrence(
                session: session,
                start: start.addingTimeInterval(TimeInterval(session.startMinute * 60)),
                end: start.addingTimeInterval(TimeInterval(session.endMinute * 60))
            )
        }
        return CourseDayPlan(date: start, week: day.week, occurrences: occurrences)
    }

    static func state(load: CourseWidgetStore.LoadResult, now: Date) -> CourseWidgetState {
        switch load {
        case .noSharedContainer: return .noSharedContainer
        case .noSnapshot: return .signedOut
        case .loaded(let snapshot): return state(snapshot: snapshot, now: now)
        }
    }

    static func state(snapshot: CourseWidgetSnapshot?, now: Date) -> CourseWidgetState {
        guard let snapshot else { return .signedOut }
        let todayKey = dayKey(now)
        guard let index = snapshot.days.firstIndex(where: { $0.date == todayKey }),
              let today = plan(for: snapshot.days[index]) else {
            return .stale
        }
        let nextDay = snapshot.days[(index + 1)...]
            .first { !$0.sessions.isEmpty }
            .flatMap(plan(for:))
        return .ready(CourseWidgetModel(now: now, today: today, nextDay: nextDay))
    }

    /// 小组件需要重新排版的时刻：每节课开始/结束、每天零点。
    static func refreshDates(snapshot: CourseWidgetSnapshot?, from now: Date) -> [Date] {
        var dates: Set<Date> = [now]
        let startOfToday = calendar.startOfDay(for: now)
        for offset in 1...2 {
            if let midnight = calendar.date(byAdding: .day, value: offset, to: startOfToday) {
                dates.insert(midnight)
            }
        }
        let horizon = now.addingTimeInterval(48 * 3600)
        for day in snapshot?.days ?? [] {
            guard let plan = plan(for: day) else { continue }
            for occurrence in plan.occurrences {
                for moment in [occurrence.start, occurrence.end] where moment > now && moment < horizon {
                    dates.insert(moment)
                }
            }
        }
        return dates.sorted()
    }
}

// MARK: - 时间线

struct CourseWidgetEntry: TimelineEntry {
    let date: Date
    let state: CourseWidgetState
}

struct CourseWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> CourseWidgetEntry {
        CourseWidgetEntry(date: .now, state: CourseWidgetCalendar.state(snapshot: .preview, now: .now))
    }

    func getSnapshot(in context: Context, completion: @escaping (CourseWidgetEntry) -> Void) {
        let now = Date.now
        var load = CourseWidgetStore.load()
        // 添加小组件的预览里没有真实数据时，用示例课程展示样式。
        if context.isPreview, !load.isLoaded { load = .loaded(.preview) }
        completion(CourseWidgetEntry(date: now, state: CourseWidgetCalendar.state(load: load, now: now)))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CourseWidgetEntry>) -> Void) {
        let now = Date.now
        let load = CourseWidgetStore.load()
        var snapshot: CourseWidgetSnapshot?
        if case .loaded(let loaded) = load { snapshot = loaded }
        let entries = CourseWidgetCalendar.refreshDates(snapshot: snapshot, from: now).map { date in
            CourseWidgetEntry(date: date, state: CourseWidgetCalendar.state(load: load, now: date))
        }
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

// MARK: - 预览数据（不含任何真实账号信息）

extension CourseWidgetSnapshot {
    static var preview: CourseWidgetSnapshot {
        let calendar = CourseWidgetCalendar.calendar
        let today = calendar.startOfDay(for: .now)
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: today) ?? today
        func session(_ name: String, _ place: String, _ teacher: String, _ start: Int, _ end: Int, _ color: Int64) -> CourseWidgetSession {
            CourseWidgetSession(name: name, place: place, teacher: teacher, startMinute: start, endMinute: end, isLab: false, color: color)
        }
        let sessions = [
            session("高等数学", "思源楼 101", "张老师", 8 * 60, 9 * 60 + 50, 0x4C8BF5),
            session("数据结构", "逸夫楼 302", "李老师", 10 * 60 + 10, 12 * 60, 0x2E9E6B),
            session("大学物理", "思源西楼 205", "王老师", 14 * 60 + 10, 16 * 60, 0xE0674F),
        ]
        return CourseWidgetSnapshot(
            version: 1,
            generatedAt: Int64(Date.now.timeIntervalSince1970),
            days: [
                CourseWidgetDay(date: CourseWidgetCalendar.dayKey(today), week: 5, sessions: sessions),
                CourseWidgetDay(date: CourseWidgetCalendar.dayKey(tomorrow), week: 5, sessions: Array(sessions.prefix(2))),
            ]
        )
    }
}

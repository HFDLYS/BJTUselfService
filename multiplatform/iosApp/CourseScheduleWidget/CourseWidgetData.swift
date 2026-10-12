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
    static func load() -> CourseWidgetSnapshot? {
        guard let container = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: courseWidgetAppGroup
        ) else { return nil }
        let url = container.appendingPathComponent(courseWidgetFileName)
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(CourseWidgetSnapshot.self, from: data)
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
    /// 没有快照：还没登录或已退出。
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
        // 添加小组件的预览里没有真实数据时，用示例课程展示样式。
        let snapshot = CourseWidgetStore.load() ?? (context.isPreview ? .preview : nil)
        completion(CourseWidgetEntry(date: now, state: CourseWidgetCalendar.state(snapshot: snapshot, now: now)))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CourseWidgetEntry>) -> Void) {
        let now = Date.now
        let snapshot = CourseWidgetStore.load()
        let entries = CourseWidgetCalendar.refreshDates(snapshot: snapshot, from: now).map { date in
            CourseWidgetEntry(date: date, state: CourseWidgetCalendar.state(snapshot: snapshot, now: date))
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

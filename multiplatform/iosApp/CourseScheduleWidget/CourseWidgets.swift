import SwiftUI
import WidgetKit

@main
struct CourseWidgetBundle: WidgetBundle {
    var body: some Widget {
        UpNextCourseWidget()
        TodayCourseWidget()
    }
}

// MARK: - 临近课程

struct UpNextCourseWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "UpNextCourseWidget", provider: CourseWidgetProvider()) { entry in
            UpNextWidgetView(entry: entry)
                .containerBackground(for: .widget) { WidgetPalette.background }
        }
        .configurationDisplayName("临近课程")
        .description("显示正在上的课和下一节课。")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline])
    }
}

struct UpNextWidgetView: View {
    let entry: CourseWidgetEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        switch entry.state {
        case .noSharedContainer:
            WidgetMessage(title: "无法读取课表", detail: "安装时需保留 App Group", compact: family.isAccessory)
        case .signedOut:
            WidgetMessage(title: "暂无课表", detail: "打开 App 同步课表后显示", compact: family.isAccessory)
        case .stale:
            WidgetMessage(title: "课表需要更新", detail: "打开 App 刷新课表", compact: family.isAccessory)
        case .ready(let model):
            switch family {
            case .accessoryInline: UpNextInline(model: model)
            case .accessoryRectangular: UpNextRectangular(model: model)
            case .systemMedium: UpNextMedium(model: model)
            default: UpNextSmall(model: model)
            }
        }
    }
}

private struct UpNextSmall: View {
    let model: CourseWidgetModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            UpNextHeader(model: model, showWeek: false)
            if let upNext = model.upNext {
                if upNext.isFutureDay {
                    SectionLabel(text: model.today.occurrences.isEmpty ? "今天没有课" : "今天的课已上完")
                }
                CourseBlock(occurrence: upNext.primary, now: model.now, style: .detailed)
                if let secondary = upNext.secondary {
                    CourseBlock(occurrence: secondary, now: model.now, style: .titleOnly)
                }
                Spacer(minLength: 0)
            } else {
                Spacer(minLength: 0)
                EmptyDay(text: model.today.occurrences.isEmpty ? "今天没有课" : "今天的课已上完")
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct UpNextMedium: View {
    let model: CourseWidgetModel

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            UpNextHeader(model: model, showWeek: true)
            if let upNext = model.upNext {
                HStack(alignment: .top, spacing: 14) {
                    column(
                        title: upNext.isFutureDay
                            ? (model.today.occurrences.isEmpty ? "今天没有课 · 第一节" : "今天的课已上完 · 第一节")
                            : (upNext.primary.isOngoing(at: model.now) ? "当前" : "下一节"),
                        occurrence: upNext.primary
                    )
                    Divider()
                    if let secondary = upNext.secondary {
                        column(title: "接下来", occurrence: secondary)
                    } else {
                        VStack(alignment: .leading, spacing: 6) {
                            SectionLabel(text: "接下来")
                            Text(upNext.isFutureDay ? "当天只有这一节" : "今天没有更多课了")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            } else {
                Spacer(minLength: 0)
                EmptyDay(text: model.today.occurrences.isEmpty ? "今天没有课，好好休息" : "今天的课已上完")
                    .frame(maxWidth: .infinity)
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func column(title: String, occurrence: CourseOccurrence) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            SectionLabel(text: title)
            CourseBlock(occurrence: occurrence, now: model.now, style: .detailed)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct UpNextRectangular: View {
    let model: CourseWidgetModel

    var body: some View {
        if let upNext = model.upNext {
            let occurrence = upNext.primary
            VStack(alignment: .leading, spacing: 1) {
                Text(upNext.isFutureDay
                     ? "\(dayLabel(occurrence.start, now: model.now)) \(timeRange(occurrence))"
                     : (occurrence.isOngoing(at: model.now) ? "正在上 · 至 \(clock(occurrence.end))" : "下一节 · \(timeRange(occurrence))"))
                    .font(.caption2)
                    .widgetAccentable()
                Text(occurrence.session.name)
                    .font(.headline)
                    .lineLimit(1)
                Text(occurrence.session.place.isEmpty ? occurrence.session.teacher : occurrence.session.place)
                    .font(.caption)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            Text(model.today.occurrences.isEmpty ? "今天没有课" : "今天的课已上完")
                .font(.headline)
        }
    }
}

private struct UpNextInline: View {
    let model: CourseWidgetModel

    var body: some View {
        if let upNext = model.upNext {
            let occurrence = upNext.primary
            let prefix = upNext.isFutureDay ? "\(dayLabel(occurrence.start, now: model.now)) " : ""
            Text("\(prefix)\(clock(occurrence.start)) \(occurrence.session.name)")
        } else {
            Text("今天没有更多课了")
        }
    }
}

// MARK: - 今日课程

struct TodayCourseWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "TodayCourseWidget", provider: CourseWidgetProvider()) { entry in
            TodayWidgetView(entry: entry)
                .containerBackground(for: .widget) { WidgetPalette.background }
        }
        .configurationDisplayName("今日课程")
        .description("按时间顺序显示今天的课程，上完后显示下一个有课的日子。")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

struct TodayWidgetView: View {
    let entry: CourseWidgetEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        switch entry.state {
        case .noSharedContainer:
            WidgetMessage(title: "无法读取课表", detail: "安装时需保留 App Group", compact: false)
        case .signedOut:
            WidgetMessage(title: "暂无课表", detail: "打开 App 同步课表后显示", compact: false)
        case .stale:
            WidgetMessage(title: "课表需要更新", detail: "打开 App 刷新课表", compact: false)
        case .ready(let model):
            content(model)
        }
    }

    private var capacity: Int { family == .systemLarge ? 6 : 2 }

    @ViewBuilder
    private func content(_ model: CourseWidgetModel) -> some View {
        // 今天的课都上完了：直接看下一个有课的日子，比一列灰掉的课更有用。
        let showsNextDay = model.remainingToday.isEmpty && model.nextDay != nil
        let plan = showsNextDay ? model.nextDay! : model.today
        // 空间不够时先丢掉已经下课的，保证正在上和接下来的课可见。
        let visible: [CourseOccurrence] = {
            if showsNextDay || plan.occurrences.count <= capacity { return Array(plan.occurrences.prefix(capacity)) }
            let firstActive = plan.occurrences.firstIndex { !$0.isFinished(at: model.now) } ?? plan.occurrences.count
            let start = max(0, min(firstActive, plan.occurrences.count - capacity))
            return Array(plan.occurrences[start..<min(start + capacity, plan.occurrences.count)])
        }()
        let hidden = plan.occurrences.count - visible.count

        VStack(alignment: .leading, spacing: family == .systemLarge ? 8 : 6) {
            HStack(alignment: .firstTextBaseline) {
                DateHeader(plan: plan, showWeek: true, prefix: showsNextDay ? dayLabel(plan.date, now: model.now) : nil)
                Spacer(minLength: 4)
                if !plan.occurrences.isEmpty {
                    Text("共 \(plan.occurrences.count) 节")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            if plan.occurrences.isEmpty {
                Spacer(minLength: 0)
                EmptyDay(text: "今天没有课，好好休息")
                    .frame(maxWidth: .infinity)
                Spacer(minLength: 0)
            } else {
                ForEach(visible) { occurrence in
                    TodayRow(occurrence: occurrence, now: model.now, roomy: family == .systemLarge)
                }
                if hidden > 0 {
                    Text("还有 \(hidden) 节，打开 App 查看")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct TodayRow: View {
    let occurrence: CourseOccurrence
    let now: Date
    let roomy: Bool

    var body: some View {
        let color = Color(hex: occurrence.session.color)
        let finished = occurrence.isFinished(at: now)
        let ongoing = occurrence.isOngoing(at: now)
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 2)
                .fill(color)
                .frame(width: 4)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(occurrence.session.name)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    if ongoing { Badge(text: "进行中", color: color) }
                    if occurrence.session.isLab { Badge(text: "实验", color: .secondary) }
                }
                Text(detailLine(occurrence.session))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 4)
            VStack(alignment: .trailing, spacing: 0) {
                Text(clock(occurrence.start))
                Text(clock(occurrence.end)).foregroundStyle(.secondary)
            }
            .font(.caption.monospacedDigit())
        }
        .padding(.vertical, roomy ? 6 : 4)
        .padding(.trailing, 8)
        .padding(.leading, 0)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(color.opacity(ongoing ? 0.22 : 0.12))
        )
        .opacity(finished ? 0.45 : 1)
        .fixedSize(horizontal: false, vertical: true)
    }
}

// MARK: - 公共组件

enum WidgetPalette {
    static let background = Color(uiColor: .systemBackground)
    static let weekday = Color(red: 0.91, green: 0.27, blue: 0.33)
}

private struct DateHeader: View {
    let plan: CourseDayPlan
    let showWeek: Bool
    var prefix: String? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            if let prefix {
                Text(prefix).font(.footnote.weight(.semibold))
            }
            Text(monthDay(plan.date))
                .font(.footnote.weight(.medium).monospacedDigit())
            Text(weekday(plan.date))
                .font(.footnote.weight(.semibold))
                .foregroundStyle(WidgetPalette.weekday)
            if showWeek, let week = plan.week {
                Text("第\(week)周")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .lineLimit(1)
    }
}

/// 临近课程的日期头：显示的是哪天的课就写哪天，跨天时加“明天/后天”。
private struct UpNextHeader: View {
    let model: CourseWidgetModel
    let showWeek: Bool

    var body: some View {
        if let upNext = model.upNext, upNext.isFutureDay, let day = model.nextDay {
            DateHeader(plan: day, showWeek: showWeek, prefix: dayLabel(day.date, now: model.now))
        } else {
            DateHeader(plan: model.today, showWeek: showWeek)
        }
    }
}

private struct SectionLabel: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.caption2.weight(.medium))
            .foregroundStyle(.secondary)
            .lineLimit(1)
    }
}

private enum CourseBlockStyle { case detailed, titleOnly }

private struct CourseBlock: View {
    let occurrence: CourseOccurrence
    let now: Date
    let style: CourseBlockStyle

    var body: some View {
        HStack(alignment: .top, spacing: 6) {
            RoundedRectangle(cornerRadius: 1.5)
                .fill(Color(hex: occurrence.session.color))
                .frame(width: 3)
            VStack(alignment: .leading, spacing: 2) {
                Text(occurrence.session.name)
                    .font(style == .detailed ? .headline : .subheadline.weight(.semibold))
                    .lineLimit(style == .detailed ? 2 : 1)
                    .minimumScaleFactor(0.85)
                if style == .detailed {
                    let detail = detailLine(occurrence.session)
                    if !detail.isEmpty {
                        Text(detail)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Text(timeRange(occurrence))
                        .font(.caption.monospacedDigit())
                    if occurrence.isOngoing(at: now) {
                        (Text("还剩 ") + Text(occurrence.end, style: .relative))
                            .font(.caption2)
                            .foregroundStyle(Color(hex: occurrence.session.color))
                            .lineLimit(1)
                    }
                } else {
                    Text(timeRange(occurrence))
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

private struct Badge: View {
    let text: String
    let color: Color
    var body: some View {
        Text(text)
            .font(.system(size: 9, weight: .semibold))
            .padding(.horizontal, 4)
            .padding(.vertical, 1)
            .foregroundStyle(color)
            .background(Capsule().strokeBorder(color.opacity(0.6), lineWidth: 0.5))
            .fixedSize()
    }
}

private struct EmptyDay: View {
    let text: String
    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: "checkmark.circle")
                .font(.title3)
                .foregroundStyle(.secondary)
            Text(text)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
    }
}

private struct WidgetMessage: View {
    let title: String
    let detail: String
    let compact: Bool
    var body: some View {
        if compact {
            Text(detail)
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Image(systemName: "calendar")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                Spacer(minLength: 0)
                Text(title).font(.headline)
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }
}

// MARK: - 格式化

private func clock(_ date: Date) -> String {
    let parts = CourseWidgetCalendar.calendar.dateComponents([.hour, .minute], from: date)
    return String(format: "%02d:%02d", parts.hour ?? 0, parts.minute ?? 0)
}

private func timeRange(_ occurrence: CourseOccurrence) -> String {
    "\(clock(occurrence.start)) - \(clock(occurrence.end))"
}

private func monthDay(_ date: Date) -> String {
    let parts = CourseWidgetCalendar.calendar.dateComponents([.month, .day], from: date)
    return String(format: "%d.%02d", parts.month ?? 0, parts.day ?? 0)
}

private func weekday(_ date: Date) -> String {
    let names = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"]
    let index = CourseWidgetCalendar.calendar.component(.weekday, from: date) - 1
    return names[max(0, min(index, 6))]
}

/// “明天”“后天”，更远的显示星期。
private func dayLabel(_ date: Date, now: Date) -> String {
    let calendar = CourseWidgetCalendar.calendar
    let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: now), to: calendar.startOfDay(for: date)).day ?? 0
    switch days {
    case 0: return "今天"
    case 1: return "明天"
    case 2: return "后天"
    default: return weekday(date)
    }
}

private func detailLine(_ session: CourseWidgetSession) -> String {
    [session.place, session.teacher].filter { !$0.isEmpty }.joined(separator: "  ")
}

private extension WidgetFamily {
    var isAccessory: Bool {
        switch self {
        case .accessoryInline, .accessoryRectangular, .accessoryCircular: return true
        default: return false
        }
    }
}

private extension Color {
    init(hex: Int64) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

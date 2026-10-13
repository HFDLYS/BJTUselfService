import SwiftUI
import WidgetKit

@main
struct CourseWidgetBundle: WidgetBundle {
    var body: some Widget {
        UpNextCourseWidget()
        TodayCourseWidget()
        WeekCourseWidget()
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
            UpNextHeader(model: model, compact: true)
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

/// 中号：左“当前/下一节”、右“接下来”两栏，大字课名 + 地点 + 时间。
private struct UpNextMedium: View {
    let model: CourseWidgetModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            UpNextHeader(model: model)
            if let upNext = model.upNext {
                HStack(alignment: .top, spacing: 14) {
                    column(title: primaryTitle(upNext), occurrence: upNext.primary)
                    Divider()
                    if let secondary = upNext.secondary {
                        column(title: Text("接下来"), occurrence: secondary)
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            ColumnLabel(text: Text("接下来"))
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

    private func primaryTitle(_ upNext: (primary: CourseOccurrence, secondary: CourseOccurrence?, isFutureDay: Bool)) -> Text {
        if upNext.isFutureDay {
            return Text(model.today.occurrences.isEmpty ? "今天没有课 · 第一节" : "今天已上完 · 第一节")
        }
        if upNext.primary.isOngoing(at: model.now) {
            return Text("当前 · 还剩 \(remainingText(until: upNext.primary.end, now: model.now))")
        }
        return Text("下一节")
    }

    private func column(title: Text, occurrence: CourseOccurrence) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ColumnLabel(text: title)
            HStack(alignment: .top, spacing: 8) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(Color(hex: occurrence.session.color))
                    .frame(width: 4)
                VStack(alignment: .leading, spacing: 2) {
                    Text(occurrence.session.name)
                        .font(.title3.weight(.bold))
                        .lineLimit(1)
                    let detail = occurrence.session.place.isEmpty ? occurrence.session.teacher : occurrence.session.place
                    if !detail.isEmpty {
                        Text(detail)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Text(timeRange(occurrence))
                        .font(.subheadline.monospacedDigit())
                        .lineLimit(1)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ColumnLabel: View {
    let text: Text
    var body: some View {
        text
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .lineLimit(1)
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

    /// 普通两行式卡片能放几节；还没上的课比这多时改用单行紧凑行，能放下更多。
    private var capacity: Int { family == .systemLarge ? 6 : 2 }
    private var compactCapacity: Int { family == .systemLarge ? 10 : 4 }

    @ViewBuilder
    private func content(_ model: CourseWidgetModel) -> some View {
        // 今天的课都上完了：直接看下一个有课的日子，比一列灰掉的课更有用。
        let showsNextDay = model.remainingToday.isEmpty && model.nextDay != nil
        let plan = showsNextDay ? model.nextDay! : model.today
        let firstActive = showsNextDay ? 0 : (plan.occurrences.firstIndex { !$0.isFinished(at: model.now) } ?? plan.occurrences.count)
        let pending = plan.occurrences.count - firstActive
        let compact = pending > capacity
        let limit = compact ? compactCapacity : capacity
        // 中号只看还没上的课，上完的在脚注右侧计数；大号保留上完的课（变灰）。
        // 空间不够时先丢掉已经下课的，保证正在上和接下来的课可见。
        let visible: [CourseOccurrence] = {
            if family != .systemLarge {
                return Array(plan.occurrences[firstActive...].prefix(limit))
            }
            if plan.occurrences.count <= limit { return plan.occurrences }
            let start = max(0, min(firstActive, plan.occurrences.count - limit))
            return Array(plan.occurrences[start..<min(start + limit, plan.occurrences.count)])
        }()
        // 被挤掉的课分两类：已经下课的（不重要）和还没上的（必须提示）。
        let visibleIDs = Set(visible.map(\.id))
        let hiddenUpcoming = plan.occurrences.filter { !visibleIDs.contains($0.id) && !$0.isFinished(at: model.now) }
        let hiddenFinished = plan.occurrences.filter { !visibleIDs.contains($0.id) && $0.isFinished(at: model.now) }.count
        // 大号卡片今天的课放完后还有空位：在下方用轻量单行预告下一个有课日，放得下几节显示几节。
        let preview: (day: CourseDayPlan, rows: [CourseOccurrence])? = {
            guard family == .systemLarge, !showsNextDay, let next = model.nextDay, !next.occurrences.isEmpty else { return nil }
            // 估算行高单位：普通行≈2.4，紧凑行≈1，分区标题与脚注各≈1，大号总共约 15。
            var used = Double(visible.count) * (compact ? 1 : 2.4)
            if !hiddenUpcoming.isEmpty || hiddenFinished > 0 { used += 1 }
            let room = Int(15 - used - 1.2)
            guard room >= 1 else { return nil }
            // 放不全时最后一行让给“还有 N 节”。
            let count = next.occurrences.count <= room ? room : max(1, room - 1)
            return (next, Array(next.occurrences.prefix(count)))
        }()

        VStack(alignment: .leading, spacing: compact ? 4 : (family == .systemLarge ? 8 : 6)) {
            WidgetHeader(
                plan: plan,
                prefix: showsNextDay ? dayLabel(plan.date, now: model.now) : nil,
                detail: plan.occurrences.isEmpty ? nil : "共 \(plan.occurrences.count) 节"
            )
            if plan.occurrences.isEmpty {
                Spacer(minLength: 0)
                EmptyDay(text: "今天没有课，好好休息")
                    .frame(maxWidth: .infinity)
                Spacer(minLength: 0)
            } else {
                if visible.isEmpty {
                    Spacer(minLength: 0)
                    EmptyDay(text: "今天的课已上完")
                        .frame(maxWidth: .infinity)
                }
                ForEach(visible) { occurrence in
                    if compact {
                        CompactTodayRow(occurrence: occurrence, now: model.now)
                    } else {
                        TodayRow(occurrence: occurrence, now: model.now, roomy: family == .systemLarge)
                    }
                }
                if !hiddenUpcoming.isEmpty || hiddenFinished > 0 {
                    HiddenSummary(upcoming: hiddenUpcoming, finished: hiddenFinished)
                }
                if let preview {
                    NextDayPreview(day: preview.day, rows: preview.rows, now: model.now)
                        .padding(.top, 4)
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

/// 还没上的课很多时用的单行：色条 + 课名 + 教室 + 起止时间。
private struct CompactTodayRow: View {
    let occurrence: CourseOccurrence
    let now: Date

    var body: some View {
        let color = Color(hex: occurrence.session.color)
        let ongoing = occurrence.isOngoing(at: now)
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 1.5)
                .fill(color)
                .frame(width: 3, height: 14)
            Text(occurrence.session.name)
                .font(.footnote.weight(.semibold))
                .lineLimit(1)
                .layoutPriority(1)
            if ongoing { Badge(text: "进行中", color: color) }
            Text(roomAndTeacher(occurrence.session))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Spacer(minLength: 4)
            Text(clock(occurrence.start))
                .font(.caption.monospacedDigit())
                .foregroundStyle(ongoing ? .primary : .secondary)
                .fixedSize()
        }
        .padding(.vertical, 3)
        .padding(.horizontal, 6)
        .background(
            RoundedRectangle(cornerRadius: 7, style: .continuous)
                .fill(color.opacity(ongoing ? 0.22 : 0.1))
        )
        .opacity(occurrence.isFinished(at: now) ? 0.45 : 1)
    }
}

/// 大号卡片底部的下一个有课日预告：小标题 + 无底色单行，整体压暗，不和今天的课抢视线。
private struct NextDayPreview: View {
    let day: CourseDayPlan
    let rows: [CourseOccurrence]
    let now: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                let label = dayLabel(day.date, now: now)
                Text(label == weekday(day.date) ? label : "\(label) \(weekday(day.date))")
                    .font(.caption.weight(.semibold))
                Text("共 \(day.occurrences.count) 节")
                    .font(.caption2)
                Rectangle()
                    .fill(.separator)
                    .frame(height: 0.5)
            }
            .foregroundStyle(.secondary)
            ForEach(rows) { occurrence in
                HStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 1.5)
                        .fill(Color(hex: occurrence.session.color).opacity(0.7))
                        .frame(width: 3, height: 12)
                    Text(occurrence.session.name)
                        .font(.caption.weight(.medium))
                        .lineLimit(1)
                        .layoutPriority(1)
                    Text(roomAndTeacher(occurrence.session))
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    Text(timeRange(occurrence))
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.secondary)
                        .fixedSize()
                }
                .foregroundStyle(.secondary)
                .padding(.leading, 6)
            }
            if day.occurrences.count > rows.count {
                Text("还有 \(day.occurrences.count - rows.count) 节")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .padding(.leading, 15)
            }
        }
    }
}

/// 列表放不下时的脚注：之后还有的课写出时间和名字，已下课的只计数。
private struct HiddenSummary: View {
    let upcoming: [CourseOccurrence]
    let finished: Int

    var body: some View {
        HStack(spacing: 6) {
            if let first = upcoming.first {
                Group {
                    if upcoming.count == 1 {
                        Text("还有 1 节未显示 · \(clock(first.start)) \(first.session.name)")
                    } else {
                        Text("还有 \(upcoming.count) 节未上")
                    }
                }
                .foregroundStyle(.primary)
                .lineLimit(1)
            }
            Spacer(minLength: 4)
            if finished > 0 {
                Text("已上完 \(finished) 节")
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .fixedSize()
            }
        }
        .font(.caption2)
    }
}

// MARK: - 公共组件

enum WidgetPalette {
    static let background = Color(uiColor: .systemBackground)
    static let weekday = Color(red: 0.91, green: 0.27, blue: 0.33)
}

/// 所有卡片统一的头部：左边“[明天] 日期 星期”（星期红色），右边教学周胶囊。
struct WidgetHeader: View {
    let plan: CourseDayPlan
    var prefix: String? = nil
    /// 日期后的补充信息，如“共 4 节”。
    var detail: String? = nil
    var compact = false

    var body: some View {
        let font: Font = compact ? .footnote : .subheadline
        HStack(alignment: .center, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                if let prefix {
                    Text(prefix).font(font.weight(.semibold))
                }
                Text(monthDay(plan.date))
                    .font(font.weight(.semibold).monospacedDigit())
                Text(weekday(plan.date))
                    .font(font.weight(.semibold))
                    .foregroundStyle(WidgetPalette.weekday)
                if let detail {
                    Text(detail)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .lineLimit(1)
            .minimumScaleFactor(compact ? 0.75 : 0.85)
            Spacer(minLength: 4)
            if let week = plan.week {
                WeekBadge(week: week, size: compact ? 11 : 13)
            }
        }
    }
}

/// 教学周标签：数字加大加粗，放在浅色胶囊里，比日期更醒目。
struct WeekBadge: View {
    let week: Int
    let size: CGFloat

    var body: some View {
        (Text("第 ").font(.system(size: size * 0.8, weight: .medium))
            + Text("\(week)").font(.system(size: size * 1.25, weight: .heavy).monospacedDigit())
            + Text(" 周").font(.system(size: size * 0.8, weight: .medium)))
            .foregroundStyle(.primary)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, size * 0.55)
            .padding(.vertical, size * 0.12)
            .background(Capsule().fill(Color.primary.opacity(0.1)))
    }
}

/// 临近课程的日期头：显示的是哪天的课就写哪天，跨天时加“明天/后天”。
private struct UpNextHeader: View {
    let model: CourseWidgetModel
    var compact = false

    var body: some View {
        if let upNext = model.upNext, upNext.isFutureDay, let day = model.nextDay {
            WidgetHeader(plan: day, prefix: dayLabel(day.date, now: model.now), compact: compact)
        } else {
            WidgetHeader(plan: model.today, compact: compact)
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
                    let detail = roomAndTeacher(occurrence.session)
                    if !detail.isEmpty {
                        Text(detail)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Text(timeRange(occurrence))
                        .font(.caption.monospacedDigit())
                    if occurrence.isOngoing(at: now) {
                        Text("还剩 \(remainingText(until: occurrence.end, now: now))")
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

struct EmptyDay: View {
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

struct WidgetMessage: View {
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

func clock(_ date: Date) -> String {
    let parts = CourseWidgetCalendar.calendar.dateComponents([.hour, .minute], from: date)
    return String(format: "%02d:%02d", parts.hour ?? 0, parts.minute ?? 0)
}

private func timeRange(_ occurrence: CourseOccurrence) -> String {
    "\(clock(occurrence.start)) - \(clock(occurrence.end))"
}

func monthDay(_ date: Date) -> String {
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

/// 小空间只留教室号和老师：地点“楼宇 教室”取最后一段，如“YF403 刘一”。
private func roomAndTeacher(_ session: CourseWidgetSession) -> String {
    let room = session.place.split(separator: " ").last.map(String.init) ?? ""
    return [room, session.teacher].filter { !$0.isEmpty }.joined(separator: " ")
}

/// “13 分钟”“1 小时 5 分钟”：按整分钟向上取整，不显示秒。
func remainingText(until end: Date, now: Date) -> String {
    let minutes = max(1, Int((end.timeIntervalSince(now) / 60).rounded(.up)))
    if minutes < 60 { return "\(minutes) 分钟" }
    let hours = minutes / 60, rest = minutes % 60
    return rest == 0 ? "\(hours) 小时" : "\(hours) 小时 \(rest) 分钟"
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

extension Color {
    init(hex: Int64) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

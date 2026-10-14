import SwiftUI
import WidgetKit

// MARK: - 本周课表（大号周视图）

struct WeekCourseWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WeekCourseWidget", provider: CourseWidgetProvider()) { entry in
            WeekWidgetView(entry: entry)
                .containerBackground(for: .widget) { WidgetPalette.background }
        }
        .configurationDisplayName("本周课表")
        .description("按大节显示本周一到周日的课程，配色与课程表页一致。")
        .supportedFamilies([.systemLarge])
        .contentMarginsDisabled()
    }
}

struct WeekWidgetView: View {
    let entry: CourseWidgetEntry

    var body: some View {
        switch entry.state {
        case .noSharedContainer:
            WidgetMessage(title: "无法读取课表", detail: "安装时需保留 App Group", compact: false).padding()
        case .signedOut:
            WidgetMessage(title: "暂无课表", detail: "打开 App 同步课表后显示", compact: false).padding()
        case .stale:
            WidgetMessage(title: "课表需要更新", detail: "打开 App 刷新课表", compact: false).padding()
        case .ready(let model):
            VStack(spacing: 6) {
                WidgetHeader(plan: model.today)
                    .padding(.leading, 6)
                WeekGrid(layout: WeekLayout(model: model), now: model.now, emptyTitle: restTitle(model))
            }
            .padding(EdgeInsets(top: 14, leading: 8, bottom: 10, trailing: 12))
        }
    }
}

// MARK: - 排版计算（纯数据，便于推理）

/// 一节课在网格里的位置：第几列、占哪些行、并排时第几道。
struct WeekBlock: Identifiable {
    let occurrence: CourseOccurrence
    let column: Int
    let firstRow: Int
    let lastRow: Int
    var lane = 0
    var laneCount = 1
    var id: String { "\(column)-\(occurrence.id)" }
}

struct WeekLayout {
    let days: [CourseDayPlan]
    /// 显示哪些大节（下标对应 slots）。
    let rows: [Int]
    let slots: [ClosedRange<Int>]
    let blocks: [WeekBlock]
    let todayColumn: Int?

    init(model: CourseWidgetModel) {
        let calendar = CourseWidgetCalendar.calendar
        let slots = model.slots
        // 周末没课且不是今天就不占列，给工作日更宽的色块。
        let allDays = model.week
        let days = allDays.enumerated().filter { index, day in
            index < 5 || !day.occurrences.isEmpty || calendar.isDate(day.date, inSameDayAs: model.now)
        }.map(\.element)

        func overlaps(_ occurrence: CourseOccurrence, _ slot: ClosedRange<Int>) -> Bool {
            occurrence.session.slotStartMinute < slot.upperBound && occurrence.session.slotEndMinute > slot.lowerBound
        }
        // 上午两节、下午两节始终显示，构成稳定骨架；午间、晚上的大节有课才显示。
        let core: Set<Int> = [0, 1, 3, 4]
        let used = Set(days.flatMap { day in
            day.occurrences.flatMap { occurrence in slots.indices.filter { overlaps(occurrence, slots[$0]) } }
        })
        let rows = slots.indices.filter { core.contains($0) || used.contains($0) }

        var blocks: [WeekBlock] = []
        for (column, day) in days.enumerated() {
            var dayBlocks: [WeekBlock] = day.occurrences.compactMap { occurrence in
                let covered = rows.indices.filter { overlaps(occurrence, slots[rows[$0]]) }
                guard let first = covered.first, let last = covered.last else { return nil }
                return WeekBlock(occurrence: occurrence, column: column, firstRow: first, lastRow: last)
            }
            // 同一时段冲突的课并排显示，不互相盖住。
            var laneEnds: [Int] = []
            for index in dayBlocks.indices {
                let block = dayBlocks[index]
                if let free = laneEnds.firstIndex(where: { $0 < block.firstRow }) {
                    dayBlocks[index].lane = free
                    laneEnds[free] = block.lastRow
                } else {
                    dayBlocks[index].lane = laneEnds.count
                    laneEnds.append(block.lastRow)
                }
            }
            for index in dayBlocks.indices {
                let block = dayBlocks[index]
                let overlapping = dayBlocks.filter { $0.firstRow <= block.lastRow && $0.lastRow >= block.firstRow }
                dayBlocks[index].laneCount = (overlapping.map(\.lane).max() ?? 0) + 1
            }
            blocks += dayBlocks
        }

        self.days = days
        self.rows = rows
        self.slots = slots
        self.blocks = blocks
        self.todayColumn = days.firstIndex { calendar.isDate($0.date, inSameDayAs: model.now) }
    }
}

// MARK: - 视图

private struct WeekGrid: View {
    let layout: WeekLayout
    let now: Date
    /// 整周没课时网格中央的标题。
    var emptyTitle = "本周没有课"

    private let axisWidth: CGFloat = 30
    private let headerHeight: CGFloat = 30
    private let gap: CGFloat = 3

    var body: some View {
        GeometryReader { proxy in
            let columns = max(layout.days.count, 1)
            let columnWidth = (proxy.size.width - axisWidth - gap * CGFloat(columns)) / CGFloat(columns)
            let rowHeight = (proxy.size.height - headerHeight - gap * CGFloat(layout.rows.count)) / CGFloat(max(layout.rows.count, 1))

            ZStack(alignment: .topLeading) {
                // 今天整列淡淡高亮。
                if let today = layout.todayColumn {
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(Color.primary.opacity(0.06))
                        .frame(width: columnWidth + gap, height: proxy.size.height)
                        .offset(x: axisWidth + CGFloat(today) * (columnWidth + gap) + gap / 2)
                }

                cornerLabel
                    .frame(width: axisWidth, height: headerHeight)

                ForEach(Array(layout.days.enumerated()), id: \.offset) { column, day in
                    DayHeader(date: day.date, isToday: column == layout.todayColumn)
                        .frame(width: columnWidth, height: headerHeight)
                        .offset(x: axisWidth + gap + CGFloat(column) * (columnWidth + gap))
                }

                ForEach(Array(layout.rows.enumerated()), id: \.offset) { row, slot in
                    SlotLabel(index: slot, range: layout.slots[slot], compact: rowHeight < 34)
                        .frame(width: axisWidth, height: rowHeight)
                        .offset(y: headerHeight + gap + CGFloat(row) * (rowHeight + gap))
                }

                ForEach(layout.blocks) { block in
                    let laneWidth = (columnWidth - gap * CGFloat(block.laneCount - 1)) / CGFloat(block.laneCount)
                    let rows = CGFloat(block.lastRow - block.firstRow + 1)
                    // 错峰课不多占一行：顶边微微下移，底边越过行间空隙一半，读起来是“稍晚开始、稍晚结束”。
                    let shift: CGFloat = block.occurrence.session.isStaggered ? min(rowHeight * 0.125, 6) : 0
                    let overhang: CGFloat = shift > 0 ? min(shift, gap / 2) : 0
                    WeekCourseCell(occurrence: block.occurrence, now: now, narrow: block.laneCount > 1)
                        .frame(width: laneWidth, height: rows * rowHeight + (rows - 1) * gap - shift + overhang)
                        .offset(
                            x: axisWidth + gap + CGFloat(block.column) * (columnWidth + gap) + CGFloat(block.lane) * (laneWidth + gap),
                            y: headerHeight + gap + CGFloat(block.firstRow) * (rowHeight + gap) + shift
                        )
                }

                if layout.blocks.isEmpty {
                    // 与临近课程/今日课程的休息提示同一文案：“非教学周 / Enjoy🎉”。
                    VStack(spacing: 4) {
                        Text(emptyTitle).font(.title3.weight(.semibold))
                        Text("Enjoy🎉").font(.headline).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.leading, axisWidth)
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        }
    }

    private var cornerLabel: some View {
        let month = CourseWidgetCalendar.calendar.component(.month, from: now)
        return Text("\(month)月")
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(.secondary)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
    }
}

private struct DayHeader: View {
    let date: Date
    let isToday: Bool

    var body: some View {
        let calendar = CourseWidgetCalendar.calendar
        let names = ["日", "一", "二", "三", "四", "五", "六"]
        let weekday = names[max(0, min(calendar.component(.weekday, from: date) - 1, 6))]
        VStack(spacing: 1) {
            Text(weekday)
                .font(.system(size: 12, weight: isToday ? .bold : .medium))
                .foregroundStyle(isToday ? WidgetPalette.weekday : .secondary)
            Text("\(calendar.component(.day, from: date))")
                .font(.system(size: 11, weight: isToday ? .bold : .regular).monospacedDigit())
                .foregroundStyle(isToday ? .primary : .secondary)
        }
        .lineLimit(1)
    }
}

private struct SlotLabel: View {
    let index: Int
    let range: ClosedRange<Int>
    let compact: Bool

    var body: some View {
        VStack(spacing: 0) {
            Text("\(index + 1)")
                .font(.system(size: 13, weight: .semibold).monospacedDigit())
            Text(String(format: "%02d:%02d", range.lowerBound / 60, range.lowerBound % 60))
                .font(.system(size: 8).monospacedDigit())
                .foregroundStyle(.secondary)
            if !compact {
                Text(String(format: "%02d:%02d", range.upperBound / 60, range.upperBound % 60))
                    .font(.system(size: 8).monospacedDigit())
                    .foregroundStyle(.tertiary)
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.8)
    }
}

/// 课表页“色块概览”同款：按课程性质的深/浅底色 + 半透明描边，文字用同色系前景。
private struct WeekCourseCell: View {
    let occurrence: CourseOccurrence
    let now: Date
    let narrow: Bool
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let colors = ScheduleKindColors.of(occurrence.session, dark: colorScheme == .dark)
        let ongoing = occurrence.isOngoing(at: now)
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        VStack(alignment: .leading, spacing: 2) {
            Text(occurrence.session.name)
                .font(.system(size: narrow ? 9 : 10.5, weight: .semibold))
                .lineLimit(4)
                .layoutPriority(1)
            if !occurrence.session.place.isEmpty {
                Text(occurrence.session.place)
                    .font(.system(size: narrow ? 8 : 9))
                    .opacity(0.8)
                    .lineLimit(3)
            }
            Spacer(minLength: 0)
        }
        .foregroundStyle(colors.content)
        .minimumScaleFactor(0.85)
        .padding(.horizontal, 3)
        .padding(.vertical, 4)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(shape.fill(colors.container))
        .overlay(shape.strokeBorder(colors.content.opacity(ongoing ? 0.95 : 0.55), lineWidth: ongoing ? 1.5 : 0.5))
        .clipShape(shape)
        // 已经下课的淡一些，视线自然落到接下来的课上。
        .opacity(occurrence.isFinished(at: now) ? 0.5 : 1)
    }
}

/// 与 shared 的 CourseCategoryPalette（课程表色块底色/文字色）保持一致。
struct ScheduleKindColors {
    let container: Color
    let content: Color

    static func of(_ session: CourseWidgetSession, dark: Bool) -> ScheduleKindColors {
        let kind = session.kind ?? (session.isLab ? "lab" : "unknown")
        // (浅色 container, 浅色 content), (深色 container, 深色 content)
        let pair: ((Int64, Int64), (Int64, Int64)) = switch kind {
        case "required": ((0xF6D2D0, 0x7D1F1A), (0x4A1F1D, 0xF2B9B4))
        case "limited": ((0xF4D7E6, 0x9C3F70), (0x48243A, 0xF0B2D2))
        case "elective": ((0xDCE4FC, 0x2F4DB5), (0x223065, 0xC3CDFA))
        case "pe": ((0xD3F0EE, 0x0E6B69), (0x163B3B, 0x9EDDD9))
        case "lab": ((0xEADBFA, 0x64368E), (0x3B2858, 0xDEC6F6))
        case "exam": ((0xFFE6C8, 0xAF641C), (0x3D2A12, 0xEAA04B))
        default: ((0xE7E7E9, 0x5F6368), (0x333438, 0xC6C7CB))
        }
        let (container, content) = dark ? pair.1 : pair.0
        return ScheduleKindColors(container: Color(hex: container), content: Color(hex: content))
    }
}

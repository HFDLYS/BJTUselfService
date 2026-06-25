import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var model: AppModel
    var searchText: String

    private var urgentHomework: [HomeworkItem] {
        model.cache.homework.filter { homework in
            guard homework.subStatus != "已提交",
                  let deadline = homeworkDeadline(homework.endTime) else { return false }
            let hours = Calendar.current.dateComponents([.hour], from: Date(), to: deadline).hour ?? 9999
            return (0...Schedule.urgentThresholdHours).contains(hours)
        }
        .sorted { ($0.endTime) < ($1.endTime) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                if model.studentInfo == nil {
                    LoginPromptCard {
                        model.presentLogin()
                    }
                }

                HStack(alignment: .top, spacing: 14) {
                    MetricTile(title: "新邮件", value: formatMail(model.accountStatus.newMailCount), systemImage: "envelope.badge", tint: .blue)
                    MetricTile(title: "校园卡", value: formatBalance(model.accountStatus.ecardBalance), systemImage: "creditcard", tint: .green)
                    MetricTile(title: "校园网", value: formatBalance(model.accountStatus.netBalance), systemImage: "wifi", tint: .orange)
                }

                HStack(alignment: .top, spacing: 18) {
                    VStack(alignment: .leading, spacing: 12) {
                        SectionHeader(title: "待关注", subtitle: "作业、考试和同步变更")
                        if urgentHomework.isEmpty && model.changeNotices.isEmpty {
                            CompactEmptyRow(text: "目前没有 48 小时内截止的未提交作业，也没有新的同步变更。")
                        } else {
                            ForEach(urgentHomework.prefix(Schedule.maxDashboardItems - 1)) { homework in
                                HomeworkCompactRow(homework: homework)
                            }
                            ForEach(model.changeNotices.prefix(Schedule.maxDashboardItems)) { notice in
                                ChangeNoticeRow(notice: notice)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .topLeading)

                    VStack(alignment: .leading, spacing: 12) {
                        SectionHeader(title: "最近日程", subtitle: "考试和作业截止时间")
                        let events = recentEvents()
                        if events.isEmpty {
                            CompactEmptyRow(text: "同步后会在这里汇总考试和作业时间。")
                        } else {
                            ForEach(events.prefix(9), id: \.self) { event in
                                HStack {
                                    Image(systemName: event.contains("考试") ? "checklist.checked" : "clock")
                                        .foregroundStyle(event.contains("考试") ? .purple : .orange)
                                    Text(event)
                                        .lineLimit(1)
                                    Spacer()
                                }
                                .padding(10)
                                .liquidGlassPanel(radius: 12)
                            }
                        }
                    }
                    .frame(width: 390, alignment: .topLeading)
                }
            }
            .padding(22)
        }
        .background(Color(nsColor: .windowBackgroundColor))
    }

    private func recentEvents() -> [String] {
        let homeworkEvents = model.cache.homework.map { "作业 · \($0.courseName) · \($0.endTime)" }
        let examEvents = model.cache.exams.map { "考试 · \($0.courseName) · \($0.examTimeAndPlace)" }
        return (homeworkEvents + examEvents).filter { searchText.isEmpty || $0.localizedCaseInsensitiveContains(searchText) }
    }

    private func formatMail(_ count: String) -> String {
        count == "0" ? "无新邮件" : "\(count) 封"
    }

    private func formatBalance(_ value: String) -> String {
        value.isEmpty ? "0" : value
    }
}

struct GradesView: View {
    @EnvironmentObject private var model: AppModel
    var searchText: String
    @State private var selectedID: Grade.ID?
    @State private var selectedTerms = Set<String>()
    @State private var sort: SortMode = .original

    private var terms: [String] {
        Array(Set(model.cache.grades.map(\.tag))).sorted().reversed()
    }

    private var filtered: [Grade] {
        var items = model.cache.grades
        if !searchText.isEmpty {
            items = items.filter { $0.courseName.localizedCaseInsensitiveContains(searchText) || $0.courseTeacher.localizedCaseInsensitiveContains(searchText) }
        }
        if !selectedTerms.isEmpty {
            items = items.filter { selectedTerms.contains($0.tag) }
        }
        switch sort {
        case .original: return items
        case .ascending: return items.sorted { numericScore($0.courseScore) < numericScore($1.courseScore) }
        case .descending: return items.sorted { numericScore($0.courseScore) > numericScore($1.courseScore) }
        }
    }

    private var selected: Grade? {
        filtered.first { $0.id == selectedID } ?? filtered.first
    }

    var body: some View {
        HStack(spacing: 0) {
            VStack(spacing: 12) {
                GradeSummaryCard(grades: filtered)
                HStack {
                    Menu {
                        ForEach(terms, id: \.self) { term in
                            Toggle(term, isOn: Binding(
                                get: { selectedTerms.contains(term) },
                                set: { isOn in
                                    if isOn { selectedTerms.insert(term) } else { selectedTerms.remove(term) }
                                }
                            ))
                        }
                        Divider()
                        Button("清空筛选") { selectedTerms.removeAll() }
                    } label: {
                        Label(selectedTerms.isEmpty ? "全部学期" : "\(selectedTerms.count) 个学期", systemImage: "line.3.horizontal.decrease.circle")
                    }
                    .liquidGlassButton()

                    Picker("排序", selection: $sort) {
                        Label("原始", systemImage: "line.3.horizontal").tag(SortMode.original)
                        Label("升序", systemImage: "arrow.up").tag(SortMode.ascending)
                        Label("降序", systemImage: "arrow.down").tag(SortMode.descending)
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 220)
                    Spacer()
                }
                List(filtered, selection: $selectedID) { grade in
                    GradeRow(grade: grade)
                        .tag(grade.id)
                }
                .listStyle(.inset)
            }
            .padding(18)
            .frame(minWidth: 520)

            Divider()

            GradeDetailPanel(grade: selected)
                .frame(width: 380)
        }
    }
}

struct ExamsView: View {
    @EnvironmentObject private var model: AppModel
    var searchText: String
    @State private var selectedID: ExamScheduleItem.ID?
    @State private var filter = "全部"

    private var types: [String] {
        ["全部"] + Array(Set(model.cache.exams.map(\.examType))).sorted()
    }

    private var filtered: [ExamScheduleItem] {
        model.cache.exams.filter { item in
            (filter == "全部" || item.examType == filter)
            && (searchText.isEmpty || item.courseName.localizedCaseInsensitiveContains(searchText) || item.detail.localizedCaseInsensitiveContains(searchText))
        }
    }

    var body: some View {
        HStack(spacing: 0) {
            VStack(spacing: 12) {
                SummaryBanner(title: "考试安排：\(filtered.count) 项", subtitle: filter)
                Picker("类型", selection: $filter) {
                    ForEach(types, id: \.self) { Text($0).tag($0) }
                }
                .pickerStyle(.segmented)
                List(filtered, selection: $selectedID) { exam in
                    ExamRow(exam: exam).tag(exam.id)
                }
                .listStyle(.inset)
            }
            .padding(18)
            .frame(minWidth: 520)

            Divider()

            DetailPane(title: selectedExam?.courseName ?? "考试详情", systemImage: "checklist.checked") {
                if let exam = selectedExam {
                    LabeledContent("类型", value: exam.examType)
                    LabeledContent("时间地点", value: exam.examTimeAndPlace)
                    LabeledContent("状态", value: exam.examStatus)
                    LabeledContent("详情", value: exam.detail)
                } else {
                    Text("选择一项考试查看详情。").foregroundStyle(.secondary)
                }
            }
            .frame(width: 380)
        }
    }

    private var selectedExam: ExamScheduleItem? {
        filtered.first { $0.id == selectedID } ?? filtered.first
    }
}

enum SortMode: String, CaseIterable {
    case original
    case ascending
    case descending
}

struct SectionHeader: View {
    var title: String
    var subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.title2.weight(.semibold))
            Text(subtitle)
                .font(.callout)
                .foregroundStyle(.secondary)
        }
    }
}

struct CompactEmptyRow: View {
    var text: String

    var body: some View {
        Text(text)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .liquidGlassPanel(radius: 14)
    }
}

struct ChangeNoticeRow: View {
    var notice: ChangeNotice

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(color)
                .frame(width: 26)
            VStack(alignment: .leading, spacing: 2) {
                Text("\(notice.category.rawValue) · \(notice.kind.rawValue)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(notice.title)
                    .font(.callout.weight(.medium))
                    .lineLimit(1)
                Text(notice.detail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
        }
        .padding(12)
        .liquidGlassPanel(radius: 14)
    }

    private var icon: String {
        switch notice.category {
        case .grade: return "chart.bar.doc.horizontal"
        case .course: return "calendar"
        case .exam: return "checklist.checked"
        case .homework: return "doc.text"
        }
    }

    private var color: Color {
        switch notice.kind {
        case .added: return .green
        case .modified: return .orange
        case .deleted: return .red
        }
    }
}

struct HomeworkCompactRow: View {
    var homework: HomeworkItem

    var body: some View {
        HStack {
            Image(systemName: "clock.badge.exclamationmark")
                .foregroundStyle(.red)
            VStack(alignment: .leading) {
                Text(homework.title)
                    .font(.callout.weight(.medium))
                    .lineLimit(1)
                Text("\(homework.courseName) · 截止 \(homework.endTime)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
        }
        .padding(12)
        .liquidGlassPanel(radius: 14)
    }
}

struct GradeSummaryCard: View {
    var grades: [Grade]

    private var weightedAverage: Double? {
        var scoreSum = 0.0
        var creditSum = 0.0
        for grade in grades {
            let score = Double(numericScore(grade.courseScore))
            guard score >= 0, let credits = Double(grade.courseCredits) else { continue }
            scoreSum += score * credits
            creditSum += credits
        }
        guard creditSum > 0 else { return nil }
        return scoreSum / creditSum
    }

    var body: some View {
        SummaryBanner(
            title: weightedAverage.map { "加权平均分 \(String(format: "%.1f", $0))" } ?? "成绩好像都没出来哦",
            subtitle: "共 \(grades.count) 门课程"
        )
    }
}

struct SummaryBanner: View {
    var title: String
    var subtitle: String

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.title2.weight(.bold))
                Text(subtitle)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(16)
        .liquidGlassPanel(radius: 18)
    }
}

struct GradeRow: View {
    var grade: Grade

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(grade.courseName)
                    .font(.callout.weight(.medium))
                    .lineLimit(1)
                Text("\(grade.courseTeacher) · \(grade.courseCredits) 学分 · \(grade.tag)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Text(grade.courseScore)
                .font(.headline)
                .foregroundStyle(Color(nsColor: gradeColor(for: numericScore(grade.courseScore))))
        }
        .padding(.vertical, 5)
    }
}

struct GradeDetailPanel: View {
    var grade: Grade?

    var body: some View {
        DetailPane(title: grade?.courseName ?? "成绩详情", systemImage: "chart.bar.doc.horizontal") {
            if let grade {
                LabeledContent("教师", value: grade.courseTeacher)
                LabeledContent("学分", value: grade.courseCredits)
                LabeledContent("成绩", value: grade.courseScore)
                LabeledContent("学期", value: grade.courseYear)
                if !grade.detail.isEmpty {
                    Divider()
                    Text(grade.detail)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)
                }
            } else {
                Text("选择一门课程查看成绩明细。")
                    .foregroundStyle(.secondary)
            }
        }
    }
}

struct ExamRow: View {
    var exam: ExamScheduleItem

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(exam.courseName)
                .font(.callout.weight(.semibold))
                .lineLimit(1)
            Text("\(exam.examType) · \(exam.examStatus)")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(exam.examTimeAndPlace)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .padding(.vertical, 5)
    }
}

struct DetailPane<Content: View>: View {
    var title: String
    var systemImage: String
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 10) {
                    Image(systemName: systemImage)
                        .font(.title2)
                        .foregroundStyle(.tint)
                    Text(title)
                        .font(.title3.weight(.semibold))
                        .lineLimit(3)
                        .minimumScaleFactor(0.78)
                }
                Divider()
                content
            }
            .padding(18)
        }
        .background(.thinMaterial)
    }
}

func homeworkDeadline(_ text: String) -> Date? {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd HH:mm"
    return formatter.date(from: text)
}

struct LoginPromptCard: View {
    var onLogin: () -> Void

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: "person.badge.key.fill")
                .font(.title)
                .foregroundStyle(.tint)
                .frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 4) {
                Text("登录后查看完整数据")
                    .font(.headline)
                Text("成绩、课表、作业、课件等数据需要登录 MIS 账号后同步。")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            Spacer()
            Button("去登录", action: onLogin)
                .liquidGlassButton(prominent: true)
        }
        .padding(18)
        .liquidGlassPanel(radius: 18)
    }
}

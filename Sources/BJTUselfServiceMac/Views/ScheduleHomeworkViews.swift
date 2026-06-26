import SwiftUI

struct CourseScheduleView: View {
    @EnvironmentObject private var model: AppModel
    var searchText: String
    @State private var currentTerm = true
    @State private var selectedWeek = 0
    @State private var selectedCourse: CourseScheduleItem?

    private let weekdays = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
    private let times = Schedule.periods.map(\.label)

    private var courses: [CourseScheduleItem] {
        model.cache.courses.filter { course in
            course.isCurrentSemester == currentTerm
            && (searchText.isEmpty || course.courseName.localizedCaseInsensitiveContains(searchText) || course.courseTeacher.localizedCaseInsensitiveContains(searchText))
            && (selectedWeek == 0 || parseCourseWeeks(course.courseTime).contains(selectedWeek))
        }
    }

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Picker("课表类型", selection: $currentTerm) {
                    Text("选课课表").tag(true)
                    Text("本学期课表").tag(false)
                }
                .pickerStyle(.segmented)
                .frame(width: 220)

                Picker("周数", selection: $selectedWeek) {
                    Text("全部").tag(0)
                    ForEach(1...Schedule.totalWeeks, id: \.self) { week in
                        Text("第 \(week) 周").tag(week)
                    }
                }
                .frame(width: 150)

                if model.cache.currentWeek > 0 {
                    Button("当前第 \(model.cache.currentWeek) 周") {
                        selectedWeek = model.cache.currentWeek
                    }
                    .liquidGlassButton()
                }
                Spacer()
            }
            .padding(.horizontal, 18)
            .padding(.top, 14)

            GeometryReader { geometry in
                let timeColumnWidth: CGFloat = 96
                let cellWidth = max(96, (geometry.size.width - timeColumnWidth - 36) / 7)
                VStack(spacing: 0) {
                    HStack(spacing: 0) {
                        Text("")
                            .frame(width: timeColumnWidth, height: 40)
                        ForEach(weekdays, id: \.self) { day in
                            Text(day)
                                .font(.callout.weight(.semibold))
                                .frame(width: cellWidth, height: 40)
                                .background(.thinMaterial)
                        }
                    }
                    ScrollView([.vertical, .horizontal]) {
                        HStack(alignment: .top, spacing: 0) {
                            VStack(spacing: 0) {
                                ForEach(times, id: \.self) { time in
                                    Text(time)
                                        .font(.caption)
                                        .multilineTextAlignment(.center)
                                        .foregroundStyle(.secondary)
                                        .frame(width: timeColumnWidth, height: 92)
                                        .background(.thinMaterial)
                                        .border(.separator.opacity(0.35))
                                }
                            }
                            VStack(spacing: 0) {
                                ForEach(0..<7, id: \.self) { row in
                                    HStack(spacing: 0) {
                                        ForEach(0..<7, id: \.self) { day in
                                            let index = row * 8 + day + 1
                                            CourseCellView(
                                                courses: courses.filter { $0.courseLocationIndex == index },
                                                selectedCourse: $selectedCourse
                                            )
                                            .frame(width: cellWidth, height: 92)
                                            .border(.separator.opacity(0.35))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 18)
            }
        }
        .sheet(item: $selectedCourse) { course in
            DetailPane(title: course.courseName, systemImage: "calendar") {
                LabeledContent("课程编号", value: course.courseID)
                LabeledContent("教师", value: course.courseTeacher)
                LabeledContent("周数", value: course.courseTime)
                LabeledContent("地点", value: course.coursePlace)
            }
            .frame(width: 420, height: 320)
        }
    }
}

struct CourseCellView: View {
    var courses: [CourseScheduleItem]
    @Binding var selectedCourse: CourseScheduleItem?
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        if courses.isEmpty {
            Rectangle()
                .fill(Color.clear)
        } else {
            VStack(spacing: 1) {
                ForEach(courses) { course in
                    Button {
                        selectedCourse = course
                    } label: {
                        VStack(spacing: 2) {
                            Text(course.courseName)
                                .font(.caption2.weight(.semibold))
                                .lineLimit(2)
                                .multilineTextAlignment(.center)
                            Text(course.coursePlace)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .padding(4)
                        .background(Color(nsColor: stableCourseColor(course.courseID, darkMode: colorScheme == .dark)))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

struct HomeworkView: View {
    @EnvironmentObject private var model: AppModel
    var searchText: String
    @State private var selectedID: HomeworkItem.ID?
    @State private var selectedCourses = Set<String>()
    @State private var hideExpired = false
    @State private var sort: SortMode = .original
    @State private var showUpload = false

    private var courseFilters: [String] {
        Array(Set(model.cache.homework.map(\.courseName))).sorted()
    }

    private var filtered: [HomeworkItem] {
        var items = model.cache.homework
        if !searchText.isEmpty {
            items = items.filter { $0.title.localizedCaseInsensitiveContains(searchText) || $0.courseName.localizedCaseInsensitiveContains(searchText) }
        }
        if !selectedCourses.isEmpty {
            items = items.filter { selectedCourses.contains($0.courseName) }
        }
        if hideExpired {
            items = items.filter { homeworkDeadline($0.endTime).map { $0 > Date() } ?? true }
        }
        switch sort {
        case .original: return items
        case .ascending: return items.sorted { ($0.endTime) < ($1.endTime) }
        case .descending: return items.sorted { ($0.endTime) > ($1.endTime) }
        }
    }

    private var selected: HomeworkItem? {
        filtered.first { $0.id == selectedID } ?? filtered.first
    }

    var body: some View {
        HStack(spacing: 0) {
            VStack(spacing: 12) {
                SummaryBanner(title: "作业安排：\(filtered.count) 项", subtitle: urgentCountText)
                HStack {
                    Menu {
                        ForEach(courseFilters, id: \.self) { course in
                            Toggle(course, isOn: Binding(
                                get: { selectedCourses.contains(course) },
                                set: { checked in
                                    if checked { selectedCourses.insert(course) } else { selectedCourses.remove(course) }
                                }
                            ))
                        }
                        Divider()
                        Button("清空筛选") { selectedCourses.removeAll() }
                    } label: {
                        Label(selectedCourses.isEmpty ? "全部课程" : "\(selectedCourses.count) 门课程", systemImage: "line.3.horizontal.decrease.circle")
                    }
                    .liquidGlassButton()

                    Toggle("隐藏已过期", isOn: $hideExpired)
                        .toggleStyle(.switch)

                    Picker("排序", selection: $sort) {
                        Text("原始").tag(SortMode.original)
                        Text("早到晚").tag(SortMode.ascending)
                        Text("晚到早").tag(SortMode.descending)
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 220)
                    Spacer()
                }
                List(filtered, selection: $selectedID) { homework in
                    HomeworkRow(homework: homework).tag(homework.id)
                }
                .listStyle(.inset)
            }
            .padding(18)
            .frame(minWidth: 560)

            Divider()

            HomeworkDetailPanel(homework: selected, showUpload: $showUpload)
                .frame(width: 420)
        }
        .sheet(isPresented: $showUpload) {
            if let homework = selected {
                HomeworkUploadView(homework: homework)
                    .environmentObject(model)
            }
        }
    }

    private var urgentCountText: String {
        let count = filtered.filter { homework in
            guard homework.subStatus != "已提交",
                  let date = homeworkDeadline(homework.endTime) else { return false }
            let hours = Calendar.current.dateComponents([.hour], from: Date(), to: date).hour ?? 999
            return (0...Schedule.urgentThresholdHours).contains(hours)
        }.count
        return count > 0 ? "\(count) 项即将截止" : "当前筛选范围内没有紧急作业"
    }
}

struct HomeworkRow: View {
    var homework: HomeworkItem

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack {
                Text(homework.title)
                    .font(.callout.weight(.semibold))
                    .lineLimit(1)
                Spacer()
                Text(homework.subStatus)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(homework.subStatus == "已提交" ? .green : .red)
            }
            Text(homework.courseName)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("开放 \(homework.openDate) · 截止 \(homework.endTime)")
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .padding(.vertical, 5)
    }
}

struct HomeworkDetailPanel: View {
    @EnvironmentObject private var model: AppModel
    var homework: HomeworkItem?
    @Binding var showUpload: Bool
    @State private var showHTML = false

    var body: some View {
        DetailPane(title: homework?.title ?? "作业详情", systemImage: "doc.text") {
            if let homework {
                LabeledContent("课程", value: homework.courseName)
                LabeledContent("开放时间", value: homework.openDate)
                LabeledContent("截止时间", value: homework.endTime)
                LabeledContent("提交人数", value: "\(homework.submitCount)/\(homework.allCount)")
                LabeledContent("提交状态", value: homework.subStatus)
                LabeledContent("批改状态", value: homework.scoreID == 0 ? "未批改" : "已批改")
                if homework.scoreID != 0 {
                    LabeledContent("分数", value: homework.score)
                }
                Divider()
                HStack {
                    Button("查看内容") { showHTML = true }
                    Button("上传作业") { showUpload = true }
                        .liquidGlassButton(prominent: true)
                    Button("下载已提交") {
                        Task { await model.downloadSubmittedHomework(homework) }
                    }
                    .disabled(homework.subStatus != "已提交")
                }
            } else {
                Text("选择一项作业查看详情。").foregroundStyle(.secondary)
            }
        }
        .sheet(isPresented: $showHTML) {
            if let homework {
                HTMLPreview(html: homework.content)
                    .frame(minWidth: 720, minHeight: 460)
            }
        }
    }
}

struct HomeworkUploadView: View {
    @EnvironmentObject private var model: AppModel
    var homework: HomeworkItem
    @Environment(\.dismiss) private var dismiss
    @State private var content = ""
    @State private var fileURLs: [URL] = []
    @State private var picking = false
    @State private var uploading = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("上传作业")
                .font(.title2.weight(.semibold))
            Text(homework.title)
                .foregroundStyle(.secondary)
                .lineLimit(2)

            TextField("作业描述", text: $content, axis: .vertical)
                .lineLimit(4...8)
                .textFieldStyle(.roundedBorder)

            HStack {
                Button("添加文件") { picking = true }
                Text("\(fileURLs.count) 个文件")
                    .foregroundStyle(.secondary)
                Spacer()
            }

            List(fileURLs, id: \.self) { url in
                HStack {
                    Image(systemName: "doc")
                    Text(url.lastPathComponent)
                    Spacer()
                    Button {
                        fileURLs.removeAll { $0 == url }
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(height: 150)

            HStack {
                Button("取消") { dismiss() }
                Spacer()
                Button(uploading ? "上传中..." : "上传") {
                    uploading = true
                    Task {
                        let ok = await model.uploadHomework(homework, fileURLs: fileURLs, content: content)
                        uploading = false
                        if ok { dismiss() }
                    }
                }
                .disabled(fileURLs.isEmpty || uploading)
                .liquidGlassButton(prominent: true)
            }
        }
        .padding(24)
        .frame(width: 560, height: 480)
        .fileImporter(isPresented: $picking, allowedContentTypes: [.data], allowsMultipleSelection: true) { result in
            if let urls = try? result.get() {
                fileURLs.append(contentsOf: urls)
            }
        }
    }
}

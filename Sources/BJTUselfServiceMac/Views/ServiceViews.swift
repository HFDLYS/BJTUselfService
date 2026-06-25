import SwiftUI

struct CoursewareView: View {
    @EnvironmentObject private var model: AppModel
    var searchText: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                SectionHeader(title: "课程资源库", subtitle: "课件、文件夹和教学日历")
                Spacer()
                Button {
                    Task { await model.loadCoursewareTree() }
                } label: {
                    Label("加载课件", systemImage: "arrow.down.circle")
                }
                .liquidGlassButton(prominent: model.coursewareRoots.isEmpty)
            }
            .padding([.top, .horizontal], 18)

            if model.coursewareRoots.isEmpty {
                EmptyStateView(title: "还没有课件树", subtitle: "点击加载课件。第一次加载会遍历所有课程，可能需要稍等。", systemImage: "folder.badge.questionmark")
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        ForEach(filteredRoots) { node in
                            CoursewareNodeView(node: node, level: 0)
                                .environmentObject(model)
                        }
                    }
                    .padding(18)
                }
            }
        }
    }

    private var filteredRoots: [CoursewareNode] {
        guard !searchText.isEmpty else { return model.coursewareRoots }
        return model.coursewareRoots.filter { root in
            root.displayName.localizedCaseInsensitiveContains(searchText)
            || root.children.description.localizedCaseInsensitiveContains(searchText)
        }
    }
}

struct CoursewareNodeView: View {
    @EnvironmentObject private var model: AppModel
    var node: CoursewareNode
    var level: Int
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                if node.children.isEmpty {
                    Image(systemName: node.resource == nil ? "folder" : "doc")
                        .foregroundStyle(node.resource == nil ? .blue : .secondary)
                        .frame(width: 22)
                } else {
                    Button {
                        withAnimation(.snappy) { expanded.toggle() }
                    } label: {
                        Image(systemName: expanded ? "chevron.down" : "chevron.right")
                    }
                    .buttonStyle(.plain)
                    .frame(width: 22)
                    Image(systemName: level == 0 ? "book.closed" : "folder")
                        .foregroundStyle(level == 0 ? .purple : .blue)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(node.displayName)
                        .font(level == 0 ? .headline : .callout)
                        .lineLimit(2)
                    if level == 0 {
                        Text("\(node.children.count) 个项目")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else if let resource = node.resource, !resource.rpSize.isEmpty {
                        Text(resource.rpSize)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                if level == 0 {
                    Button {
                        Task { await model.downloadTeachingCalendar(for: node.course) }
                    } label: {
                        Image(systemName: "calendar.badge.arrow.down")
                    }
                    .help("下载教学日历")
                }
                Button {
                    Task { await model.downloadCourseware(node) }
                } label: {
                    Image(systemName: node.resource == nil ? "square.and.arrow.down.on.square" : "square.and.arrow.down")
                }
                .help(node.resource == nil ? "递归下载文件夹" : "下载资源")
            }
            .padding(12)
            .padding(.leading, CGFloat(level) * 18)
            .liquidGlassPanel(radius: 14)

            if expanded {
                ForEach(node.children) { child in
                    CoursewareNodeView(node: child, level: level + 1)
                        .environmentObject(model)
                }
            }
        }
        .onAppear {
            if level == 0 { expanded = false }
        }
    }
}

struct ClassroomsView: View {
    @EnvironmentObject private var model: AppModel
    var searchText: String
    @State private var sort = "教室名"
    @State private var descending = false
    @State private var previewRoom: ClassroomCapacity?

    private var classrooms: [ClassroomCapacity] {
        var items = model.buildingInfo?.classroomList ?? []
        if !searchText.isEmpty {
            items = items.filter { $0.roomName.localizedCaseInsensitiveContains(searchText) }
        }
        switch sort {
        case "占用率":
            items.sort { occupancy($0) < occupancy($1) }
        case "人数":
            items.sort { $0.used < $1.used }
        default:
            items.sort { $0.roomName < $1.roomName }
        }
        return descending ? items.reversed() : items
    }

    var body: some View {
        HStack(spacing: 0) {
            List(AppModel.buildingList, id: \.self, selection: $model.selectedBuilding) { building in
                Text(building).tag(building)
            }
            .frame(width: 220)
            .onChange(of: model.selectedBuilding) { _, newValue in
                Task { await model.loadBuilding(newValue) }
            }

            Divider()

            VStack(spacing: 12) {
                if let info = model.buildingInfo {
                    SummaryBanner(title: info.buildingName, subtitle: "有效期：\(info.effectiveDateStart) 至 \(info.effectiveDateEnd)")
                } else {
                    SummaryBanner(title: model.selectedBuilding, subtitle: "点击刷新读取教室人数")
                }
                HStack {
                    Picker("排序", selection: $sort) {
                        Text("教室名").tag("教室名")
                        Text("占用率").tag("占用率")
                        Text("人数").tag("人数")
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 240)
                    Button {
                        descending.toggle()
                    } label: {
                        Label("方向", systemImage: descending ? "arrow.down" : "arrow.up")
                    }
                    .liquidGlassButton()
                    Spacer()
                    Button("刷新") {
                        Task { await model.loadBuilding() }
                    }
                    .liquidGlassButton(prominent: true)
                }

                List(classrooms) { room in
                    ClassroomRow(room: room, classroomMap: model.cache.classroomMap) {
                        previewRoom = room
                    }
                }
                .listStyle(.inset)
            }
            .padding(18)
        }
        .task {
            if model.buildingInfo == nil {
                await model.loadBuilding()
            }
        }
        .sheet(item: $previewRoom) { room in
            ClassroomPreview(buildingName: model.selectedBuilding, roomName: room.roomName)
                .frame(width: 640, height: 260)
        }
    }

    private func occupancy(_ room: ClassroomCapacity) -> Double {
        guard room.capacity > 0 else { return 1 }
        return Double(room.used) / Double(room.capacity)
    }
}

struct ClassroomRow: View {
    var room: ClassroomCapacity
    var classroomMap: [String: [Int]]
    var onPreview: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(room.roomName)
                    .font(.callout.weight(.semibold))
                Spacer()
                Text(room.capacity > 0 ? "\(room.used)/\(room.capacity)" : "无法读取")
                    .foregroundStyle(room.used < room.capacity ? Color.accentColor : Color.red)
            }
            ProgressView(value: room.capacity > 0 ? Double(room.used) / Double(room.capacity) : 1)
            HStack(spacing: 8) {
                ForEach(0..<7, id: \.self) { index in
                    Circle()
                        .fill(statusColor(classroomMap[room.roomName]?[safe: index] ?? 0))
                        .frame(width: 14, height: 14)
                        .overlay(Circle().stroke(index == currentClassIndex() ? Color.red : Color.secondary.opacity(0.35), lineWidth: 2))
                }
                Spacer()
                Button("查看识别图") { onPreview() }
                    .buttonStyle(.borderless)
            }
        }
        .padding(.vertical, 6)
    }

    private func statusColor(_ status: Int) -> Color {
        switch status {
        case 1: return Color(red: 0.89, green: 0.41, blue: 0.41)
        case 2: return Color(red: 0.62, green: 0.41, blue: 0.41)
        case 3: return Color(red: 0.22, green: 0.31, blue: 0.84)
        case 4: return Color(red: 0.47, green: 0.75, blue: 0.43)
        case 5: return Color(red: 0.85, green: 0.80, blue: 0.34)
        default: return .clear
        }
    }
}

struct ClassroomPreview: View {
    var buildingName: String
    var roomName: String

    private var request: URLRequest? {
        guard let url = URL(string: API.classroomView) else { return nil }
        let body = "buildi=\(buildingName)&classrooms=\(roomName)&token=BJTUSelfService"
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.httpBody = Data(body.utf8)
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        return req
    }

    var body: some View {
        if let request {
            MacWebView(request: request)
        } else {
            EmptyStateView(title: "教室识别图不可用", subtitle: "URL 构造失败", systemImage: "xmark.octagon")
        }
    }
}

struct EmailView: View {
    @EnvironmentObject private var model: AppModel
    @State private var cookies: [HTTPCookie] = []

    var body: some View {
        if let url = URL(string: "https://mis.bjtu.edu.cn/module/module/26/") {
            MacWebView(request: URLRequest(url: url), cookies: cookies)
                .task {
                    if let cookieURL = URL(string: "https://mis.bjtu.edu.cn/") {
                        cookies = await model.http.cookies(for: cookieURL)
                    }
                }
        } else {
            EmptyStateView(title: "邮箱地址异常", subtitle: "无法构造 MIS 邮箱入口。", systemImage: "envelope.badge.shield.half.filled")
        }
    }
}

struct OtherFunctionsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var englishTranscript = false
    @State private var email = ""
    @State private var normalThreshold = "48"
    @State private var emergencyThreshold = "24"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                SectionHeader(title: "服务功能", subtitle: "下载、订阅和充值入口")

                FunctionTile(title: "校历下载", subtitle: "下载本科生院发布的校历文件", systemImage: "calendar.badge.arrow.down") {
                    Task { await model.downloadSchoolCalendar() }
                }

                FunctionTile(title: "成绩单下载", subtitle: englishTranscript ? "英文版 PDF" : "中文版 PDF", systemImage: "doc.richtext") {
                    Task { await model.downloadGradeTranscript(english: englishTranscript) }
                } accessory: {
                    Toggle("英文版", isOn: $englishTranscript)
                        .toggleStyle(.switch)
                }

                VStack(alignment: .leading, spacing: 12) {
                    Label("作业提醒订阅", systemImage: "bell.badge")
                        .font(.headline)
                    TextField("邮箱", text: $email)
                        .textFieldStyle(.roundedBorder)
                    HStack {
                        TextField("普通提醒小时", text: $normalThreshold)
                            .textFieldStyle(.roundedBorder)
                        TextField("紧急提醒小时", text: $emergencyThreshold)
                            .textFieldStyle(.roundedBorder)
                        Button("订阅") {
                            Task {
                                _ = await model.subscribeHomeworkNotice(email: email, normal: Int(normalThreshold) ?? 48, emergency: Int(emergencyThreshold) ?? 24)
                            }
                        }
                        .disabled(email.isEmpty)
                        .liquidGlassButton(prominent: true)
                    }
                }
                .padding(16)
                .liquidGlassPanel(radius: 16)

                HStack(spacing: 12) {
                    Button {
                        model.downloadsService.openNetworkRechargePage()
                    } label: {
                        Label("校园网续费", systemImage: "wifi")
                    }
                    .liquidGlassButton()
                    Button {
                        model.downloadsService.openEcardRechargeHint()
                    } label: {
                        Label("校园卡入口", systemImage: "creditcard")
                    }
                    .liquidGlassButton()
                }

                if !model.downloads.isEmpty {
                    SectionHeader(title: "下载状态", subtitle: "保存在 Downloads/交大自由行下载目录")
                    ForEach(model.downloads) { item in
                        DownloadStatusRow(status: item)
                    }
                }
            }
            .padding(22)
        }
    }
}

struct FunctionTile<Accessory: View>: View {
    var title: String
    var subtitle: String
    var systemImage: String
    var action: () -> Void
    @ViewBuilder var accessory: Accessory

    init(title: String, subtitle: String, systemImage: String, action: @escaping () -> Void, @ViewBuilder accessory: () -> Accessory = { EmptyView() }) {
        self.title = title
        self.subtitle = subtitle
        self.systemImage = systemImage
        self.action = action
        self.accessory = accessory()
    }

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage)
                .font(.title2)
                .foregroundStyle(.tint)
                .frame(width: 34)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(subtitle).foregroundStyle(.secondary)
            }
            Spacer()
            accessory
            Button("执行", action: action)
                .liquidGlassButton(prominent: true)
        }
        .padding(16)
        .liquidGlassPanel(radius: 16)
    }
}

struct DownloadStatusRow: View {
    var status: DownloadTaskStatus

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(status.filename).font(.callout.weight(.medium))
                Spacer()
                Text(status.state.rawValue)
                    .foregroundStyle(status.state == .failed ? .red : .secondary)
            }
            ProgressView(value: status.progress)
            Text(status.message)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)
        }
        .padding(12)
        .liquidGlassPanel(radius: 14)
    }
}

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showClearConfirm = false

    var body: some View {
        Form {
            Section("账号") {
                if let info = model.studentInfo {
                    LabeledContent("当前用户", value: info.name)
                    LabeledContent("学号", value: info.studentID)
                    LabeledContent("院系", value: info.department)
                    Button("退出账号", role: .destructive) { model.logout() }
                } else {
                    LabeledContent("当前用户", value: "未登录")
                    Button("登录 MIS 账号") {
                        model.presentLogin()
                    }
                    .liquidGlassButton(prominent: true)
                }
            }

            Section("外观") {
                Picker("主题", selection: $model.appearance) {
                    ForEach(Appearance.allCases, id: \.self) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
            }

            Section("自动同步") {
                Toggle("自动同步成绩", isOn: $model.autoSyncGrades)
                Toggle("自动同步作业", isOn: $model.autoSyncHomework)
                Toggle("自动同步课表", isOn: $model.autoSyncSchedule)
                Toggle("自动同步考试", isOn: $model.autoSyncExams)
                Toggle("打开更新提示", isOn: $model.checkUpdate)
            }

            Section("维护") {
                Button("检查更新") {
                    Task { await model.checkForUpdates() }
                }
                if let release = model.latestRelease {
                    LabeledContent("最新版本", value: release.tagName)
                    Link("打开发布页", destination: release.htmlURL)
                }
                Button("清除本地缓存", role: .destructive) {
                    showClearConfirm = true
                }
            }

            Section("项目") {
                if let url = URL(string: "https://github.com/HFDLYS/BJTUselfService") {
                    Link("GitHub 项目", destination: url)
                }
            }
        }
        .formStyle(.grouped)
        .padding()
        .confirmationDialog("确认清除本地数据？", isPresented: $showClearConfirm) {
            Button("清除", role: .destructive) { model.clearLocalCache() }
            Button("取消", role: .cancel) {}
        }
    }
}

private func currentClassIndex() -> Int {
    Schedule.currentPeriodIndex()
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

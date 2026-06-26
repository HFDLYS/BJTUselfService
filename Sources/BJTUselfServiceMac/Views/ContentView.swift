import AppKit
import SwiftUI

enum AppSection: String, CaseIterable, Identifiable {
    case dashboard = "首页"
    case grades = "成绩"
    case schedule = "课程表"
    case exams = "考试安排"
    case homework = "作业"
    case courseware = "课件"
    case courseGrabbing = "抢课"
    case classrooms = "教室人数"
    case email = "邮箱"
    case downloads = "其他功能"
    case settings = "设置"

    var id: String { rawValue }

    var symbol: String {
        switch self {
        case .dashboard: return "house"
        case .grades: return "chart.bar.doc.horizontal"
        case .schedule: return "calendar"
        case .exams: return "checklist.checked"
        case .homework: return "doc.text"
        case .courseware: return "folder"
        case .courseGrabbing: return "target"
        case .classrooms: return "person.3"
        case .email: return "envelope"
        case .downloads: return "square.and.arrow.down"
        case .settings: return "gearshape"
        }
    }
}

struct ContentView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var captchaCenter: CaptchaCenter
    @State private var selection: AppSection? = .dashboard
    @State private var searchText = ""

    var body: some View {
        NavigationSplitView {
            sidebar
        } detail: {
            selectedView
                .navigationTitle(selection?.rawValue ?? "交大自由行")
                .toolbar { toolbar }
                .searchable(text: $searchText, placement: .toolbar, prompt: "搜索课程、作业、教室")
                .modifier(SearchToolbarIfAvailable())
        }
        .overlay(alignment: .top) { phaseToast }
        .sheet(isPresented: $model.showLoginSheet) {
            LoginSheet()
                .environmentObject(model)
                .environmentObject(captchaCenter)
                .interactiveDismissDisabled(true)
        }
        .sheet(item: captchaSheetItem) { challenge in
            CaptchaSheet(challenge: challenge)
                .interactiveDismissDisabled(true)
        }
        .onAppear {
            activateApplicationWindow()
        }
    }

    private var sidebar: some View {
        List(selection: $selection) {
            Section("概览") {
                ForEach([AppSection.dashboard], id: \.self) { item in
                    Label(item.rawValue, systemImage: item.symbol).tag(Optional(item))
                }
            }
            Section("学习") {
                ForEach([.grades, .schedule, .exams, .homework, .courseware, .courseGrabbing] as [AppSection], id: \.self) { item in
                    Label(item.rawValue, systemImage: item.symbol).tag(Optional(item))
                }
            }
            Section("服务") {
                ForEach([.classrooms, .email, .downloads, .settings] as [AppSection], id: \.self) { item in
                    Label(item.rawValue, systemImage: item.symbol).tag(Optional(item))
                }
            }
        }
        .navigationTitle("交大自由行")
        .navigationSplitViewColumnWidth(min: 190, ideal: 220, max: 260)
    }

    @ViewBuilder
    private var selectedView: some View {
        switch selection ?? .dashboard {
        case .dashboard: DashboardView(searchText: searchText)
        case .grades: GradesView(searchText: searchText)
        case .schedule: CourseScheduleView(searchText: searchText)
        case .exams: ExamsView(searchText: searchText)
        case .homework: HomeworkView(searchText: searchText)
        case .courseware: CoursewareView(searchText: searchText)
        case .courseGrabbing: CourseGrabbingView()
        case .classrooms: ClassroomsView(searchText: searchText)
        case .email: EmailView()
        case .downloads: OtherFunctionsView()
        case .settings: SettingsView()
        }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItemGroup {
            if model.studentInfo == nil {
                Button {
                    model.presentLogin()
                } label: {
                    Label("登录", systemImage: "person.badge.key")
                }
                .liquidGlassButton(prominent: true)
            } else {
                Button {
                    Task { await model.refreshAll() }
                } label: {
                    Label("同步", systemImage: "arrow.clockwise")
                }
                .help("同步全部数据")
            }

            Button {
                selection = .settings
            } label: {
                Label("设置", systemImage: "gearshape")
            }
        }
    }

    private var captchaSheetItem: Binding<CaptchaChallenge?> {
        Binding(
            get: { model.showLoginSheet ? nil : captchaCenter.challenge },
            set: { captchaCenter.challenge = $0 }
        )
    }

    @ViewBuilder
    private var phaseToast: some View {
        switch model.phase {
        case .loggingIn:
            ProgressToast(text: "正在登录...")
        case .refreshing:
            ProgressToast(text: "正在同步...")
        default:
            EmptyView()
        }
    }
}

private struct SearchToolbarIfAvailable: ViewModifier {
    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(macOS 26.0, *) {
            content.searchToolbarBehavior(.automatic)
        } else {
            content
        }
    }
}

struct ProgressToast: View {
    var text: String

    var body: some View {
        VStack {
            HStack(spacing: 10) {
                ProgressView()
                    .controlSize(.small)
                Text(text)
                    .font(.callout.weight(.medium))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .liquidGlassPanel(radius: 22)
            .padding(.top, 18)
            Spacer()
        }
        .transition(.move(edge: .top).combined(with: .opacity))
    }
}

struct LoginSheet: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var captchaCenter: CaptchaCenter
    @Environment(\.dismiss) private var dismiss
    @State private var username = ""
    @State private var password = ""
    @FocusState private var focusedField: Field?

    private enum Field {
        case username
        case password
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 4) {
                Text("登录 MIS")
                    .font(.title2.weight(.semibold))
                Text("账号密码只保存在本机 Keychain。")
                    .foregroundStyle(.secondary)
            }

            TextField("学号", text: $username)
                .textFieldStyle(.roundedBorder)
                .focused($focusedField, equals: .username)
                .onSubmit { focusedField = .password }
                .disabled(isBusy)

            SecureField("密码", text: $password)
                .textFieldStyle(.roundedBorder)
                .focused($focusedField, equals: .password)
                .onSubmit { submit() }
                .disabled(isBusy)

            if case .error(let message) = model.phase {
                Text(message)
                    .font(.callout)
                    .foregroundStyle(.red)
                    .fixedSize(horizontal: false, vertical: true)
            }

            HStack {
                if isBusy {
                    ProgressView()
                        .controlSize(.small)
                    Text("正在登录...")
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button("取消") {
                    cancelLogin()
                }
                .disabled(isBusy)
                Button("登录") {
                    submit()
                }
                .disabled(username.isEmpty || password.isEmpty || isBusy)
                .liquidGlassButton(prominent: true)
                .keyboardShortcut(.defaultAction)
            }
        }
        .padding(24)
        .frame(width: 390)
        .liquidGlassPanel(radius: 24)
        .onAppear {
            activateApplicationWindow()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                focusedField = .username
            }
        }
        .sheet(item: captchaSheetItem) { challenge in
            CaptchaSheet(challenge: challenge)
                .interactiveDismissDisabled(true)
        }
    }

    private var isBusy: Bool {
        if case .loggingIn = model.phase { return true }
        return false
    }

    private var captchaSheetItem: Binding<CaptchaChallenge?> {
        Binding(
            get: { captchaCenter.challenge },
            set: { captchaCenter.challenge = $0 }
        )
    }

    private func submit() {
        guard !username.isEmpty, !password.isEmpty, !isBusy else { return }
        Task {
            await model.login(credentials: Credentials(username: username, password: password))
        }
    }

    private func cancelLogin() {
        model.showLoginSheet = false
        model.phase = .loggedOut
    }
}

struct CaptchaSheet: View {
    @ObservedObject var challenge: CaptchaChallenge
    @FocusState private var answerFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("请输入验证码结果")
                .font(.title3.weight(.semibold))
            if let image = NSImage(data: challenge.imageData) {
                Image(nsImage: image)
                    .resizable()
                    .interpolation(.none)
                    .scaledToFit()
                    .frame(height: 78)
                    .padding(8)
                    .background(.white, in: RoundedRectangle(cornerRadius: 10))
            }
            TextField("算式结果", text: $challenge.answer)
                .textFieldStyle(.roundedBorder)
                .focused($answerFocused)
                .onSubmit { challenge.submit() }
            HStack {
                Button("取消") { challenge.cancel() }
                Spacer()
                Button("提交") { challenge.submit() }
                    .disabled(challenge.answer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .liquidGlassButton(prominent: true)
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(22)
        .frame(width: 360)
        .liquidGlassPanel(radius: 22)
        .onAppear {
            activateApplicationWindow()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                answerFocused = true
            }
        }
    }
}

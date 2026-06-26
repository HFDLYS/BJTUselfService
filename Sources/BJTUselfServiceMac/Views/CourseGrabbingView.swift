import AppKit
import SwiftUI

struct CourseGrabbingView: View {
    @EnvironmentObject private var model: AppModel
    @State private var selectType: CourseSelectType = .school
    @State private var keywordText = ""
    @State private var selectedIDs = Set<String>()
    @State private var captchaAnswer = ""
    @State private var deleteCourseID = ""
    @State private var hasSearched = false

    private var keywords: [String] {
        keywordText
            .components(separatedBy: "\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    var body: some View {
        HStack(spacing: 0) {
            VStack(spacing: 12) {
                SummaryBanner(title: "抢课", subtitle: "选择课程类型，输入关键词，获取验证码后提交")

                HStack(spacing: 12) {
                    Picker("课程类型", selection: $selectType) {
                        ForEach(CourseSelectType.allCases) { type in
                            Text(type.rawValue).tag(type)
                        }
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 360)
                    Spacer()
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("关键词（每行一个，留空则列出全部）")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    TextEditor(text: $keywordText)
                        .frame(height: 60)
                        .font(.callout)
                        .padding(4)
                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 8))
                    HStack {
                        Button {
                            Task {
                                await model.searchGrabbableCourses(type: selectType, keywords: keywords)
                                hasSearched = true
                            }
                        } label: {
                            Label("搜索课程", systemImage: "magnifyingglass")
                        }
                        .liquidGlassButton(prominent: true)
                        .disabled(model.isGrabbing)

                        if model.isGrabbing {
                            ProgressView().controlSize(.small)
                            Text("查询中...")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                }

                if hasSearched {
                    if model.grabbableCourses.isEmpty {
                        EmptyStateView(title: "没有找到课程", subtitle: "尝试更换关键词或课程类型", systemImage: "magnifyingglass.circle")
                    } else {
                        List(model.grabbableCourses) { course in
                            GrabableCourseRow(course: course, isSelected: selectedIDs.contains(course.id)) {
                                if selectedIDs.contains(course.id) {
                                    selectedIDs.remove(course.id)
                                } else {
                                    selectedIDs.insert(course.id)
                                }
                            }
                        }
                        .listStyle(.inset)
                    }
                } else {
                    EmptyStateView(title: "准备抢课", subtitle: "选择课程类型和关键词后点击搜索", systemImage: "studentdesk")
                }
            }
            .padding(18)
            .frame(minWidth: 560)

            Divider()

            VStack(alignment: .leading, spacing: 16) {
                SectionHeader(title: "提交选课", subtitle: "验证码和选课提交")

                if model.studentInfo == nil {
                    EmptyStateView(title: "需要登录", subtitle: "抢课功能需要先登录 MIS 账号", systemImage: "person.badge.key")
                        .frame(maxHeight: .infinity)
                } else {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("已选 \(selectedIDs.count) 门").font(.headline)
                            Spacer()
                            Button("清空选择") {
                                selectedIDs.removeAll()
                            }
                            .buttonStyle(.borderless)
                            .disabled(selectedIDs.isEmpty)
                        }

                        captchaSection

                        HStack {
                            Button {
                                Task { await model.submitGrabbedCourses(ids: Array(selectedIDs), captchaAnswer: captchaAnswer) }
                            } label: {
                                Label("提交抢课", systemImage: "checkmark.circle.fill")
                            }
                            .liquidGlassButton(prominent: true)
                            .disabled(selectedIDs.isEmpty || captchaAnswer.isEmpty || model.isGrabbing || model.grabCaptcha == nil)

                            if model.isGrabbing {
                                ProgressView().controlSize(.small)
                            }
                        }

                        if !model.grabResult.isEmpty {
                            Text(model.grabResult)
                                .font(.callout)
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 10))
                        }
                    }

                    Divider()

                    VStack(alignment: .leading, spacing: 10) {
                        SectionHeader(title: "退课", subtitle: "输入课程号退课")
                        HStack {
                            TextField("课程号", text: $deleteCourseID)
                                .textFieldStyle(.roundedBorder)
                            Button {
                                Task {
                                    await model.deleteGrabbedCourse(id: deleteCourseID.trimmingCharacters(in: .whitespacesAndNewlines))
                                }
                            } label: {
                                Label("退课", systemImage: "trash")
                            }
                            .liquidGlassButton()
                            .disabled(deleteCourseID.isEmpty || model.isGrabbing)
                        }
                    }
                    Spacer()
                }
            }
            .padding(18)
            .frame(width: 380)
        }
    }

    @ViewBuilder
    private var captchaSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("验证码").font(.headline)
                Spacer()
                Button {
                    Task { await model.refreshGrabCaptcha() }
                } label: {
                    Label("刷新", systemImage: "arrow.clockwise")
                }
                .buttonStyle(.borderless)
            }

            if let captcha = model.grabCaptcha, let image = NSImage(data: captcha.imageData) {
                Image(nsImage: image)
                    .resizable()
                    .interpolation(.none)
                    .scaledToFit()
                    .frame(height: 60)
                    .padding(6)
                    .background(.white, in: RoundedRectangle(cornerRadius: 8))
            } else {
                RoundedRectangle(cornerRadius: 8)
                    .fill(.thinMaterial)
                    .frame(height: 60)
                    .overlay(Text("点击刷新获取验证码").font(.caption).foregroundStyle(.secondary))
            }

            TextField("验证码答案", text: $captchaAnswer)
                .textFieldStyle(.roundedBorder)
        }
    }
}

private struct GrabableCourseRow: View {
    var course: GrabbableCourse
    var isSelected: Bool
    var onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isSelected ? .green : .secondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(course.courseName)
                        .font(.callout.weight(.medium))
                        .lineLimit(1)
                    Text("\(course.courseCode) · ID: \(course.courseID)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }
        }
        .buttonStyle(.plain)
    }
}

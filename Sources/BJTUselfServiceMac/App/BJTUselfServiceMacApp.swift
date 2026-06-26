import AppKit
import SwiftUI

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationWillFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        activateApplicationWindow()
    }
}

@main
struct BJTUselfServiceMacApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup("交大自由行") {
            ContentView()
                .environmentObject(model)
                .environmentObject(model.captchaCenter)
                .frame(minWidth: 1060, minHeight: 650)
                .preferredColorScheme(colorScheme)
                .task {
                    activateApplicationWindow()
                    if model.checkUpdate {
                        await model.checkForUpdates()
                    }
                }
        }
        .defaultSize(width: 1280, height: 720)
        .commands {
            CommandGroup(after: .appInfo) {
                Button("同步全部数据") {
                    Task { await model.refreshAll() }
                }
                .keyboardShortcut("r", modifiers: [.command])
            }
        }
    }

    private var colorScheme: ColorScheme? {
        switch model.appearance {
        case .light: return .light
        case .dark: return .dark
        case .system: return nil
        }
    }
}

func activateApplicationWindow() {
    NSApp.setActivationPolicy(.regular)
    DispatchQueue.main.async {
        NSApp.activate(ignoringOtherApps: true)
        if let window = NSApp.windows.first {
            window.makeKeyAndOrderFront(nil)
            window.center()
        }
    }
}

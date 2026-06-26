import AppKit
import SwiftUI
import WebKit

struct MacWebView: NSViewRepresentable {
    var request: URLRequest
    var cookies: [HTTPCookie] = []

    func makeNSView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = true
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.navigationDelegate = context.coordinator
        applyCookies(to: view) {
            view.load(request)
        }
        return view
    }

    func updateNSView(_ nsView: WKWebView, context: Context) {
        applyCookies(to: nsView) {
            if nsView.url != request.url {
                nsView.load(request)
            }
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    private func applyCookies(to view: WKWebView, completion: @escaping () -> Void) {
        let store = view.configuration.websiteDataStore.httpCookieStore
        guard !cookies.isEmpty else {
            completion()
            return
        }

        let group = DispatchGroup()
        for cookie in cookies {
            group.enter()
            store.setCookie(cookie) {
                group.leave()
            }
        }
        group.notify(queue: .main, execute: completion)
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard navigationAction.navigationType == .linkActivated,
                  let url = navigationAction.request.url,
                  url.host?.contains("bjtu.edu.cn") == false else {
                decisionHandler(.allow)
                return
            }
            NSWorkspace.shared.open(url)
            decisionHandler(.cancel)
        }
    }
}

struct HTMLPreview: NSViewRepresentable {
    var html: String

    func makeNSView(context: Context) -> WKWebView {
        let view = WKWebView()
        view.loadHTMLString(html, baseURL: nil)
        return view
    }

    func updateNSView(_ nsView: WKWebView, context: Context) {
        nsView.loadHTMLString(html, baseURL: nil)
    }
}

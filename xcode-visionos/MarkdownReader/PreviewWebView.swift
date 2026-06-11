import SwiftUI
import WebKit

/// The rendered-Markdown preview: a `WKWebView` that loads the HTML shell
/// once and then updates the content in place via `mdApp.update()`, so the
/// scroll position survives re-renders, zooming and theme switches.
struct PreviewWebView: UIViewRepresentable {

    @ObservedObject var model: AppModel

    func makeCoordinator() -> Coordinator {
        Coordinator(model: model)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "mdOpenLink")

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.navigationDelegate = context.coordinator
        context.coordinator.webView = webView

        let shell = HtmlPageBuilder.buildShell(theme: model.theme, fontScale: model.fontScale)
        webView.loadHTMLString(shell, baseURL: nil)

        model.previewBridge = context.coordinator
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        // State changes flow through the PreviewBridge methods; nothing to do.
    }

    // ---------------------------------------------------------- coordinator

    @MainActor
    final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler, PreviewBridge {
        weak var webView: WKWebView?
        private let model: AppModel
        private var pageReady = false
        /// Markdown queued while the shell was still loading.
        private var pendingMarkdown: String?

        init(model: AppModel) {
            self.model = model
        }

        // The shell finished loading: render the current document and apply
        // the persisted display preferences.
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            pageReady = true
            render(pendingMarkdown ?? model.markdown)
            pendingMarkdown = nil
            setScrollbarHidden(model.scrollbarHidden)
        }

        // External links are opened by the system browser, never navigated
        // to inside the preview (the page itself blocks them too).
        func userContentController(_ userContentController: WKUserContentController,
                                   didReceive message: WKScriptMessage) {
            guard message.name == "mdOpenLink",
                  let href = message.body as? String,
                  let url = URL(string: href) else { return }
            UIApplication.shared.open(url)
        }

        private func evaluate(_ js: String, completion: ((Any?) -> Void)? = nil) {
            webView?.evaluateJavaScript(js) { result, _ in
                completion?(result)
            }
        }

        // ----------------------------------------------------- PreviewBridge

        func render(_ markdown: String) {
            guard pageReady else {
                pendingMarkdown = markdown
                return
            }
            evaluate("mdApp.update(\(HtmlPageBuilder.jsLiteral(markdown)));")
        }

        func setTheme(_ theme: AppTheme) {
            evaluate("mdApp.setTheme(\(theme == .dark ? "true" : "false"));")
        }

        func setFontScale(_ scale: Double) {
            let percent = Int((scale * 100).rounded())
            evaluate("mdApp.setFontScale(\(percent));")
        }

        func setScrollbarHidden(_ hidden: Bool) {
            evaluate("mdApp.setScrollbarHidden(\(hidden ? "true" : "false"));")
        }

        func scrollToRatio(_ ratio: Double) {
            evaluate("mdApp.scrollToRatio(\(ratio));")
        }

        func findAll(_ query: String, completion: @escaping (Int) -> Void) {
            evaluate("mdApp.findAll(\(HtmlPageBuilder.jsLiteral(query)));") { result in
                completion((result as? NSNumber)?.intValue ?? 0)
            }
        }

        func findNext(completion: @escaping (Int) -> Void) {
            evaluate("mdApp.findNext();") { result in
                completion((result as? NSNumber)?.intValue ?? 0)
            }
        }

        func findPrev(completion: @escaping (Int) -> Void) {
            evaluate("mdApp.findPrev();") { result in
                completion((result as? NSNumber)?.intValue ?? 0)
            }
        }

        func findClear() {
            evaluate("mdApp.findClear();")
        }
    }
}

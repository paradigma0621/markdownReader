import Foundation

/// Builds the single HTML shell loaded into the preview's `WKWebView`.
///
/// Everything is inlined from resources bundled in the app, so rendering works
/// 100% offline: the content CSS (light/dark themes), `marked.js` for
/// Markdown -> HTML conversion, `highlight.js` (+ GitHub themes) for code
/// syntax highlighting, and the in-page engine (`app.js`) that handles content
/// updates, anchor navigation, find highlighting and ratio-preserving zoom.
///
/// The page is loaded once; subsequent renders call `mdApp.update()` so the
/// scroll position is preserved across re-renders (no full page reload).
enum HtmlPageBuilder {

    static func buildShell(theme: AppTheme, fontScale: Double) -> String {
        let baseCss = readResource("markdown", "css")
        let hlLight = readResource("github.min", "css")
        let hlDark = readResource("github-dark.min", "css")
        let markedJs = readResource("marked.min", "js")
        let hljsJs = readResource("highlight.min", "js")
        let appJs = readResource("app", "js")

        let themeClass = theme == .dark ? "theme-dark" : "theme-light"
        let lightMedia = theme == .dark ? "not all" : "all"
        let darkMedia = theme == .dark ? "all" : "not all"
        let fontPercent = Int((fontScale * 100).rounded())

        return """
        <!DOCTYPE html>
        <html lang="en" class="\(themeClass)">
        <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <style id="hl-light" media="\(lightMedia)">\(hlLight)</style>
            <style id="hl-dark" media="\(darkMedia)">\(hlDark)</style>
            <style>\(baseCss)</style>
            <style id="md-font">:root { font-size: \(fontPercent)%; }</style>
        </head>
        <body>
            <article id="content" class="markdown-body"></article>
            <script>\(markedJs)</script>
            <script>\(hljsJs)</script>
            <script>\(appJs)</script>
        </body>
        </html>
        """
    }

    /// JSON-encodes a Swift string into a JS string literal so arbitrary
    /// Markdown (quotes, newlines, backslashes) can be passed to `mdApp`.
    static func jsLiteral(_ s: String) -> String {
        let data = (try? JSONSerialization.data(withJSONObject: [s])) ?? Data("[\"\"]".utf8)
        let array = String(decoding: data, as: UTF8.self)
        return String(array.dropFirst().dropLast()) // strip the [ ] wrapper
    }

    private static func readResource(_ name: String, _ ext: String) -> String {
        guard let url = Bundle.main.url(forResource: name, withExtension: ext),
              let text = try? String(contentsOf: url, encoding: .utf8) else {
            assertionFailure("Missing bundled resource: \(name).\(ext)")
            return ""
        }
        return text
    }
}

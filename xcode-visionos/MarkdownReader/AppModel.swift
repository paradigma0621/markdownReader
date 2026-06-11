import SwiftUI
import Combine

enum AppTheme: String {
    case light, dark

    var toggled: AppTheme { self == .light ? .dark : .light }
}

/// Central state of the Markdown reader, shared by the toolbar, the editor
/// and the preview. Mirrors the responsibilities of the JavaFX `MainView`.
@MainActor
final class AppModel: ObservableObject {

    static let minScale = 0.6
    static let maxScale = 2.4
    static let scaleStep = 0.1

    // ------------------------------------------------------------- document

    /// Currently loaded Markdown text (source of truth for editing/saving).
    @Published var markdown: String = ""
    @Published var fileName: String = "Untitled"
    @Published var dirty = false
    @Published var editMode = false

    // ------------------------------------------------------------- display

    @Published var theme: AppTheme = .light {
        didSet { UserDefaults.standard.set(theme.rawValue, forKey: "theme") }
    }
    @Published var fontScale: Double = 1.0 {
        didSet { UserDefaults.standard.set(fontScale, forKey: "fontScale") }
    }
    /// Whether the preview's scrollbar is hidden. Scrolling keeps working
    /// (gaze/pinch and keyboard) — only the bar itself is invisible.
    @Published var scrollbarHidden = false {
        didSet { UserDefaults.standard.set(scrollbarHidden, forKey: "scrollbarHidden") }
    }

    // ------------------------------------------------------------- find bar

    @Published var findBarVisible = false
    @Published var findQuery = ""
    /// Match counter shown next to the find field ("3/12", "No matches", "").
    @Published var findStatus = ""

    /// All match ranges found in the editor for the current query.
    var editorFindMatches: [NSRange] = []
    /// Index of the currently selected editor match (-1 = none).
    var editorFindIndex = -1
    /// Total occurrences highlighted in the preview (0 = none/fresh query).
    var previewFindTotal = 0
    /// 1-based index of the current preview match.
    var previewFindIndex = 0

    // ------------------------------------------------------- view bridges

    /// Set by the preview view; executes JS against the loaded page.
    var previewBridge: PreviewBridge?
    /// Set by the editor view; drives selection/undo/highlights natively.
    var editorBridge: EditorBridge?

    private var renderDebounce: Timer?

    init() {
        let defaults = UserDefaults.standard
        if let saved = defaults.string(forKey: "theme"), let t = AppTheme(rawValue: saved) {
            theme = t
        }
        let savedScale = defaults.double(forKey: "fontScale")
        if savedScale > 0 { fontScale = min(Self.maxScale, max(Self.minScale, savedScale)) }
        scrollbarHidden = defaults.bool(forKey: "scrollbarHidden")
        loadWelcomeSample()
    }

    // ------------------------------------------------------------- actions

    func loadWelcomeSample() {
        if let url = Bundle.main.url(forResource: "welcome", withExtension: "md"),
           let text = try? String(contentsOf: url, encoding: .utf8) {
            markdown = text
            fileName = "welcome.md"
            dirty = false
        }
    }

    func open(text: String, name: String) {
        markdown = text
        fileName = name
        dirty = false
        editorBridge?.setText(text)
        previewBridge?.render(text)
        refreshFindAfterContentChange()
    }

    func newDocument() {
        open(text: "", name: "Untitled")
        editMode = true
    }

    /// Called by the editor on every text change; re-renders the preview with
    /// a small debounce to avoid re-parsing on each keystroke.
    func editorTextChanged(_ text: String) {
        markdown = text
        dirty = true
        renderDebounce?.invalidate()
        renderDebounce = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                self.previewBridge?.render(self.markdown)
            }
        }
    }

    func toggleEditMode() {
        editMode.toggle()
        if editMode {
            editorBridge?.setText(markdown)
            // Re-run the editor search so matches/highlights exist in the now
            // visible editor.
            if findBarVisible { runFind(findQuery) }
        } else if findBarVisible {
            runFind(findQuery)
        }
    }

    func toggleTheme() {
        theme = theme.toggled
        previewBridge?.setTheme(theme)
    }

    // Zoom keeps the same passage in view: the JS side preserves the scroll
    // position as a fraction of the scrollable range across the font change.
    func changeScale(_ delta: Double) {
        let next = min(Self.maxScale, max(Self.minScale, fontScale + delta))
        fontScale = (next * 100).rounded() / 100
        previewBridge?.setFontScale(fontScale)
    }

    func resetScale() {
        fontScale = 1.0
        previewBridge?.setFontScale(fontScale)
    }

    func toggleScrollbar() {
        scrollbarHidden.toggle()
        previewBridge?.setScrollbarHidden(scrollbarHidden)
    }

    func undoEdit() {
        guard editMode else { return }
        editorBridge?.undo()
    }

    /// Editor scrolled to `fraction` (0..1): keep the preview aligned.
    func editorScrolled(toFraction fraction: Double) {
        guard editMode else { return }
        previewBridge?.scrollToRatio(fraction)
    }

    // ------------------------------------------------------------- find

    func openFindBar() {
        findBarVisible = true
        if !findQuery.isEmpty { runFind(findQuery) }
    }

    func closeFindBar() {
        findBarVisible = false
        findStatus = ""
        editorFindMatches = []
        editorFindIndex = -1
        previewFindTotal = 0
        previewFindIndex = 0
        editorBridge?.clearFindHighlights()
        previewBridge?.findClear()
    }

    /// Runs a fresh search in whichever view is currently active.
    func runFind(_ query: String) {
        findQuery = query
        editorFindMatches = []
        editorFindIndex = -1
        previewFindTotal = 0
        previewFindIndex = 0

        guard !query.isEmpty else {
            findStatus = ""
            editorBridge?.clearFindHighlights()
            previewBridge?.findClear()
            return
        }

        if editMode {
            searchInEditor(query)
        } else {
            previewBridge?.findAll(query) { [weak self] total in
                guard let self else { return }
                self.previewFindTotal = total
                self.previewFindIndex = total > 0 ? 1 : 0
                self.findStatus = total == 0 ? "No matches" : "1/\(total)"
            }
        }
    }

    func findNext() { findStep(backwards: false) }
    func findPrev() { findStep(backwards: true) }

    private func findStep(backwards: Bool) {
        guard !findQuery.isEmpty else { return }
        if editMode {
            guard !editorFindMatches.isEmpty else { return }
            let count = editorFindMatches.count
            editorFindIndex = backwards
                ? (editorFindIndex - 1 + count) % count
                : (editorFindIndex + 1) % count
            selectCurrentEditorMatch()
        } else {
            if previewFindTotal == 0 {
                runFind(findQuery)
                return
            }
            let action: (@escaping (Int) -> Void) -> Void = backwards
                ? { self.previewBridge?.findPrev(completion: $0) }
                : { self.previewBridge?.findNext(completion: $0) }
            action { [weak self] index in
                guard let self else { return }
                self.previewFindIndex = index
                self.findStatus = "\(index)/\(self.previewFindTotal)"
            }
        }
    }

    private func searchInEditor(_ query: String) {
        let text = markdown as NSString
        let lowerText = text.lowercased as NSString
        let lowerQuery = query.lowercased()
        var pos = 0
        var matches: [NSRange] = []
        while pos < lowerText.length {
            let range = lowerText.range(
                of: lowerQuery,
                range: NSRange(location: pos, length: lowerText.length - pos))
            if range.location == NSNotFound { break }
            matches.append(range)
            pos = range.location + 1
        }
        editorFindMatches = matches
        if matches.isEmpty {
            findStatus = "No matches"
            editorBridge?.clearFindHighlights()
        } else {
            editorFindIndex = 0
            editorBridge?.highlightMatches(matches)
            selectCurrentEditorMatch()
        }
    }

    private func selectCurrentEditorMatch() {
        guard editorFindIndex >= 0, editorFindIndex < editorFindMatches.count else { return }
        editorBridge?.selectMatch(at: editorFindIndex, in: editorFindMatches)
        findStatus = "\(editorFindIndex + 1)/\(editorFindMatches.count)"
    }

    /// Re-applies the search after the rendered content was replaced — the
    /// DOM-based highlights live in the old nodes (mirrors the JavaFX app).
    func refreshFindAfterContentChange() {
        if findBarVisible && !findQuery.isEmpty {
            runFind(findQuery)
        }
    }

    // ------------------------------------------------------------- status

    var statusText: String {
        let words = markdown.split(whereSeparator: { $0.isWhitespace }).count
        let lines = markdown.isEmpty ? 0 : markdown.components(separatedBy: "\n").count
        return "\(fileName)  •  \(words) words  •  \(lines) lines"
    }
}

/// Implemented by the preview view (WKWebView wrapper).
@MainActor
protocol PreviewBridge: AnyObject {
    func render(_ markdown: String)
    func setTheme(_ theme: AppTheme)
    func setFontScale(_ scale: Double)
    func setScrollbarHidden(_ hidden: Bool)
    func scrollToRatio(_ ratio: Double)
    func findAll(_ query: String, completion: @escaping (Int) -> Void)
    func findNext(completion: @escaping (Int) -> Void)
    func findPrev(completion: @escaping (Int) -> Void)
    func findClear()
}

/// Implemented by the editor view (UITextView wrapper).
@MainActor
protocol EditorBridge: AnyObject {
    func setText(_ text: String)
    func undo()
    func highlightMatches(_ ranges: [NSRange])
    func selectMatch(at index: Int, in ranges: [NSRange])
    func clearFindHighlights()
}

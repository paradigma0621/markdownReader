import SwiftUI
import UIKit

/// The Markdown source editor: a `UITextView` wrapper that provides
/// native undo (Cmd+Z via the system UndoManager), find-match selection
/// and highlighting, and editor -> preview scroll synchronization.
struct EditorTextView: UIViewRepresentable {

    @ObservedObject var model: AppModel

    func makeCoordinator() -> Coordinator {
        Coordinator(model: model)
    }

    func makeUIView(context: Context) -> UITextView {
        let textView = UITextView()
        textView.delegate = context.coordinator
        textView.font = .monospacedSystemFont(ofSize: 15, weight: .regular)
        textView.autocorrectionType = .no
        textView.autocapitalizationType = .none
        textView.smartQuotesType = .no
        textView.smartDashesType = .no
        textView.spellCheckingType = .no
        textView.alwaysBounceVertical = true
        textView.backgroundColor = .clear
        textView.textContainerInset = UIEdgeInsets(top: 16, left: 12, bottom: 16, right: 12)
        textView.text = model.markdown

        context.coordinator.textView = textView
        model.editorBridge = context.coordinator
        return textView
    }

    func updateUIView(_ uiView: UITextView, context: Context) {
        // Text changes flow through the EditorBridge; nothing to do here.
    }

    // ---------------------------------------------------------- coordinator

    @MainActor
    final class Coordinator: NSObject, UITextViewDelegate, EditorBridge {
        weak var textView: UITextView?
        private let model: AppModel
        /// Prevents the scroll-sync from firing on programmatic scrolls
        /// (e.g. scrolling a find match into view), avoiding feedback loops.
        private var suppressScrollSync = false
        /// Debounces editor -> preview scroll sync so rapid scrolling does
        /// not flood the web view with JS calls.
        private var scrollSyncTimer: Timer?
        private var highlightedRanges: [NSRange] = []

        init(model: AppModel) {
            self.model = model
        }

        // ----------------------------------------------------- delegate

        func textViewDidChange(_ textView: UITextView) {
            model.editorTextChanged(textView.text ?? "")
        }

        func scrollViewDidScroll(_ scrollView: UIScrollView) {
            guard !suppressScrollSync, scrollView.isTracking || scrollView.isDecelerating
                    || scrollView.isDragging else { return }
            scheduleScrollSync(scrollView)
        }

        private func scheduleScrollSync(_ scrollView: UIScrollView) {
            scrollSyncTimer?.invalidate()
            let max = scrollView.contentSize.height - scrollView.bounds.height
            guard max > 0 else { return }
            let fraction = Swift.min(1, Swift.max(0, scrollView.contentOffset.y / max))
            scrollSyncTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: false) { [weak self] _ in
                Task { @MainActor in
                    self?.model.editorScrolled(toFraction: fraction)
                }
            }
        }

        // ----------------------------------------------------- EditorBridge

        func setText(_ text: String) {
            textView?.text = text
            highlightedRanges = []
        }

        func undo() {
            textView?.undoManager?.undo()
        }

        func highlightMatches(_ ranges: [NSRange]) {
            guard let textView else { return }
            clearFindHighlights()
            highlightedRanges = ranges
            let storage = textView.textStorage
            storage.beginEditing()
            for range in ranges where NSMaxRange(range) <= storage.length {
                storage.addAttributes([
                    .backgroundColor: UIColor.systemYellow.withAlphaComponent(0.45),
                ], range: range)
            }
            storage.endEditing()
        }

        func selectMatch(at index: Int, in ranges: [NSRange]) {
            guard let textView, index >= 0, index < ranges.count else { return }
            let range = ranges[index]
            guard NSMaxRange(range) <= (textView.text as NSString).length else { return }
            suppressScrollSync = true
            textView.selectedRange = range
            textView.scrollRangeToVisible(range)
            DispatchQueue.main.async { [weak self] in
                self?.suppressScrollSync = false
            }
        }

        func clearFindHighlights() {
            guard let textView, !highlightedRanges.isEmpty else { return }
            let storage = textView.textStorage
            let full = NSRange(location: 0, length: storage.length)
            storage.beginEditing()
            storage.removeAttribute(.backgroundColor, range: full)
            storage.endEditing()
            highlightedRanges = []
        }
    }
}

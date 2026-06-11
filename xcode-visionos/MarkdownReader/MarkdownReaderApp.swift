import SwiftUI

/// Markdown Reader for Apple Vision Pro — a visionOS port of the JavaFX
/// Markdown Reader. The app opens as a single resizable window containing
/// the editor/preview split.
@main
struct MarkdownReaderApp: App {
    var body: some Scene {
        WindowGroup {
            NavigationStack {
                ContentView()
            }
        }
        .defaultSize(width: 1280, height: 900)
    }
}

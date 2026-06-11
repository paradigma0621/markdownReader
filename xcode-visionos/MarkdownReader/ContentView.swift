import SwiftUI
import UniformTypeIdentifiers

/// Main window: toolbar, optional find bar, editor + preview split and a
/// status bar. Mirrors the JavaFX `MainView` layout, minus the left index
/// pane (intentionally removed) and with visionOS interaction conventions.
struct ContentView: View {

    @StateObject private var model = AppModel()
    @State private var showingImporter = false
    @State private var showingExporter = false
    @FocusState private var findFieldFocused: Bool

    private static let markdownTypes: [UTType] = {
        var types: [UTType] = [.plainText, .text]
        if let md = UTType(filenameExtension: "md") { types.insert(md, at: 0) }
        if let markdown = UTType(filenameExtension: "markdown") { types.append(markdown) }
        return types
    }()

    var body: some View {
        VStack(spacing: 0) {
            if model.findBarVisible {
                findBar
            }
            GeometryReader { geo in
                HStack(spacing: 0) {
                    if model.editMode {
                        EditorTextView(model: model)
                            .frame(width: geo.size.width * 0.45)
                        Divider()
                    }
                    PreviewWebView(model: model)
                }
            }
            statusBar
        }
        .toolbar { toolbarContent }
        .background(hiddenShortcuts)
        .fileImporter(isPresented: $showingImporter,
                      allowedContentTypes: Self.markdownTypes) { result in
            handleImport(result)
        }
        .fileExporter(isPresented: $showingExporter,
                      document: MarkdownFileDocument(text: model.markdown),
                      contentType: .plainText,
                      defaultFilename: model.fileName) { result in
            if case .success = result { model.dirty = false }
        }
        .navigationTitle("\(model.dirty ? "● " : "")\(model.fileName) — Markdown Reader")
    }

    // ---------------------------------------------------------------- bars

    private var findBar: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
            TextField("Find…", text: $model.findQuery)
                .textFieldStyle(.roundedBorder)
                .frame(maxWidth: 280)
                .focused($findFieldFocused)
                .onChange(of: model.findQuery) { _, query in
                    model.runFind(query)
                }
                .onSubmit { model.findNext() }
            Text(model.findStatus)
                .font(.callout)
                .foregroundStyle(.secondary)
                .frame(minWidth: 80, alignment: .leading)
            Button { model.findPrev() } label: { Image(systemName: "chevron.up") }
                .help("Previous match")
            Button { model.findNext() } label: { Image(systemName: "chevron.down") }
                .help("Next match (Enter)")
            Spacer()
            Button { model.closeFindBar() } label: { Image(systemName: "xmark") }
                .help("Close find bar (Esc)")
                .keyboardShortcut(.escape, modifiers: [])
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(.thinMaterial)
    }

    private var statusBar: some View {
        HStack {
            Text(model.statusText)
                .font(.footnote)
                .foregroundStyle(.secondary)
            Spacer()
            Text("\(Int((model.fontScale * 100).rounded()))%")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .background(.thinMaterial)
    }

    // ------------------------------------------------------------- toolbar

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItemGroup(placement: .topBarLeading) {
            Button { showingImporter = true } label: {
                Label("Open", systemImage: "folder")
            }
            .help("Open document (Cmd+O)")
            .keyboardShortcut("o", modifiers: .command)

            Button { model.newDocument() } label: {
                Label("New", systemImage: "plus")
            }
            .help("New document (Cmd+N)")
            .keyboardShortcut("n", modifiers: .command)

            Button { model.toggleEditMode() } label: {
                Label("Edit", systemImage: model.editMode ? "pencil.circle.fill" : "pencil.circle")
            }
            .help("Edit text (Cmd+E)")
            .keyboardShortcut("e", modifiers: .command)

            Button { showingExporter = true } label: {
                Label("Save", systemImage: "square.and.arrow.down")
            }
            .help("Save (Cmd+S)")
            .keyboardShortcut("s", modifiers: .command)
        }

        ToolbarItemGroup(placement: .topBarTrailing) {
            Button { model.changeScale(-AppModel.scaleStep) } label: {
                Label("Zoom out", systemImage: "minus.magnifyingglass")
            }
            .help("Zoom out (Cmd+-)")
            .keyboardShortcut("-", modifiers: .command)

            Button { model.changeScale(AppModel.scaleStep) } label: {
                Label("Zoom in", systemImage: "plus.magnifyingglass")
            }
            .help("Zoom in (Cmd++)")
            .keyboardShortcut("=", modifiers: .command)

            Button { model.openFindBar(); findFieldFocused = true } label: {
                Label("Find", systemImage: "magnifyingglass")
            }
            .help("Find text (Cmd+F)")
            .keyboardShortcut("f", modifiers: .command)

            Button { model.toggleScrollbar() } label: {
                Label("Scrollbar",
                      systemImage: model.scrollbarHidden
                          ? "rectangle.righthalf.inset.filled"
                          : "rectangle.righthalf.inset.filled.arrow.right")
            }
            .help("Show/hide the preview scrollbar (scrolling keeps working)")

            Button { model.toggleTheme() } label: {
                Label("Theme", systemImage: model.theme == .dark ? "sun.max" : "moon")
            }
            .help("Toggle light/dark theme (Cmd+T)")
            .keyboardShortcut("t", modifiers: .command)
        }
    }

    /// Invisible buttons providing the Ctrl-based variants of the shortcuts
    /// (the JavaFX app uses Ctrl; Apple platforms conventionally use Cmd —
    /// both work here) plus Cmd+0/Ctrl+0 zoom reset and Ctrl+Z undo.
    private var hiddenShortcuts: some View {
        Group {
            Button("") { model.changeScale(AppModel.scaleStep) }
                .keyboardShortcut("=", modifiers: .control)
            Button("") { model.changeScale(-AppModel.scaleStep) }
                .keyboardShortcut("-", modifiers: .control)
            Button("") { model.resetScale() }
                .keyboardShortcut("0", modifiers: .command)
            Button("") { model.resetScale() }
                .keyboardShortcut("0", modifiers: .control)
            Button("") { model.openFindBar(); findFieldFocused = true }
                .keyboardShortcut("f", modifiers: .control)
            Button("") { model.undoEdit() }
                .keyboardShortcut("z", modifiers: .control)
            Button("") { model.toggleEditMode() }
                .keyboardShortcut("e", modifiers: .control)
            Button("") { model.toggleTheme() }
                .keyboardShortcut("t", modifiers: .control)
        }
        .opacity(0)
        .frame(width: 0, height: 0)
        .accessibilityHidden(true)
    }

    // ------------------------------------------------------------- file IO

    private func handleImport(_ result: Result<URL, Error>) {
        guard case .success(let url) = result else { return }
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        if let text = try? String(contentsOf: url, encoding: .utf8) {
            model.open(text: text, name: url.lastPathComponent)
        }
    }
}

/// Minimal text document used by `.fileExporter` to save the Markdown source.
struct MarkdownFileDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.plainText] }

    var text: String

    init(text: String) {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else {
            throw CocoaError(.fileReadCorruptFile)
        }
        text = String(decoding: data, as: UTF8.self)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

#Preview {
    ContentView()
}

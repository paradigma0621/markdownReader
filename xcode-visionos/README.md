# Markdown Reader — Apple Vision Pro (visionOS)

SwiftUI/visionOS port of the JavaFX Markdown Reader in the repository root.
The Java project is untouched; this folder is self-contained.

## Requirements

- Xcode 16 or newer (the project uses file-system-synchronized groups)
- visionOS SDK (bundled with Xcode); runs on the visionOS Simulator or a
  real Apple Vision Pro (deployment target: visionOS 1.0)

## Build & run

1. Open `MarkdownReader.xcodeproj` in Xcode.
2. Select the **MarkdownReader** scheme and a visionOS destination
   (e.g. *Apple Vision Pro* simulator).
3. For a physical device, set your development team under
   *Signing & Capabilities* (signing style is Automatic).
4. Run (Cmd+R).

## Architecture

| File | Role |
| --- | --- |
| `MarkdownReaderApp.swift` | App entry point (single resizable window). |
| `ContentView.swift` | Toolbar, find bar, editor+preview split, status bar, shortcuts, file import/export. |
| `AppModel.swift` | Shared state and feature logic (find, zoom, theme, scroll sync). |
| `EditorTextView.swift` | `UITextView` wrapper: native undo, find selection/highlights, scroll-sync source. |
| `PreviewWebView.swift` | `WKWebView` wrapper: renders Markdown via the in-page engine. |
| `HtmlPageBuilder.swift` | Builds the offline HTML shell (CSS + marked.js + highlight.js + app.js inlined). |
| `Resources/app.js` | In-page engine: in-place re-render, anchor navigation, find engine, ratio-preserving zoom, scrollbar hiding. |

The preview page is loaded **once**; every re-render happens in place via
`mdApp.update()`, so the scroll position naturally survives content changes.

## Feature parity with the JavaFX app

- **Zoom keeps the reading position** — changing the font size preserves the
  scroll position as a fraction of the scrollable range (Cmd+`+` / Cmd+`-`,
  Cmd+`0` resets; Ctrl variants also work).
- **Anchor links** — clicking a TOC-style link (e.g. `[8. /voice (Gemini)](#8-voice-gemini)`)
  smoothly scrolls the preview to the heading. Heading ids use GitHub-style
  slugs; a slug-comparison fallback resolves hand-written fragments.
- **Undo in the editor** — Cmd+Z (native `UndoManager`) and Ctrl+Z.
- **Hidden scrollbar stays scrollable** — the toolbar toggle hides only the
  scrollbar track/thumb; gaze/pinch scrolling and keyboard navigation keep
  working.
- **No left index pane** — the layout has no index sidebar.
- **Find (Cmd+F / Ctrl+F)** — works in both views: the editor highlights and
  selects matches natively; the preview uses the same DOM-based highlight
  engine as the JavaFX app (amber hits, orange current match, wrap-around
  navigation, match counter). Enter = next, Esc = close.
- **Editor → preview scroll sync** — scrolling the editor keeps the preview
  at the same fraction of the document (debounced).

## Vision Pro specific behavior

- **Looking at a heading never changes its style.** Headings are rendered as
  non-interactive elements: no click handlers, no pointer cursor and no
  `:hover` rules, so visionOS does not draw its gaze hover effect on them.
- Keyboard shortcuts follow the Apple convention (Cmd); Ctrl variants are
  also registered for parity with the desktop app.
- Emojis are rendered by the system WebKit (no twemoji conversion needed).

## Not ported (out of scope for this target)

- Heading folding (Shift+Tab / collapse-expand all)
- F11 focus mode / F12 OS fullscreen (visionOS windows have no fullscreen)
- File watching with auto-reload (sandboxed file access)
- Drag & drop of files onto the window

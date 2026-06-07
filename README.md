# Markdown Reader 📝

A styled **Markdown** document viewer written in **Java 21** + **JavaFX 21**.
Converts Markdown (GitHub Flavored) into beautiful HTML and displays it in a `WebView`,
with light/dark theme, navigable table of contents, zoom, and automatic reload.

![Java](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)
![Maven](https://img.shields.io/badge/Maven-3.9-red)

## ✨ Features

| Feature | Description |
|---|---|
| **Full GFM** | tables, task lists, strikethrough, footnotes, autolinks, emojis, typography |
| **Light/dark theme** | switchable in real time (`Ctrl+T`), preference persisted |
| **Navigable TOC** | extracted from headings; click to scroll to section |
| **Zoom** | `Ctrl + scroll`, `Ctrl++`, `Ctrl+-`, `Ctrl+0` (reset) |
| **Auto-reload** | reopens the file when a change is detected on disk |
| **Drag and drop** | drop a `.md` file onto the window to open it |
| **Syntax highlighting** | via bundled highlight.js (works offline) |
| **"Back to top" button** | appears when scrolling |

## 🚀 How to run

Requires **JDK 21** and **Maven**. The simplest way:

```bash
./run.sh
```

The script automatically detects a JDK 21 installed via SDKMAN. Alternatively:

```bash
# Point JAVA_HOME to a JDK 21 and run:
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.9-amzn"
mvn clean javafx:run
```

Open a file directly from the command line:

```bash
mvn javafx:run -Djavafx.args="path/to/file.md"
```

## 📦 Build executable JAR

```bash
mvn clean package
java -jar target/markdown-reader-1.0.0.jar
```

> The "fat" JAR includes Java dependencies, but **not** the native JavaFX binaries.
> To run the JAR you need a JDK that includes JavaFX, or point the
> `--module-path` to the JavaFX SDK. For general use, prefer `mvn javafx:run`.

## ⌨️ Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+O` | Open document |
| `Ctrl+R` | Reload |
| `Ctrl+T` | Toggle theme |
| `Ctrl+B` | Show/hide table of contents |
| `Ctrl++` / `Ctrl+-` | Zoom in / out |
| `Ctrl+0` | Reset zoom |
| `Ctrl + scroll` | Zoom |
| `Shift+Tab` | Fold/unfold the heading section under the caret (in the editor) |
| Right-click a heading | Fold/unfold that section in the rendered preview |

### 🪗 Folding

Heading sections can be collapsed org-mode style. Folding is a view-only change:
the saved file always keeps the full text.

- **Editor:** put the caret on a heading line (`#`, `##`, …) and press `Shift+Tab`
  to collapse its section (everything up to the next heading of the same or higher
  level); press it again to expand. A `⋯` marks collapsed headings.
- **Preview:** right-click a heading in the styled output to fold/unfold it.
- **Collapse all / Expand all:** the `⊟` / `⊞` toolbar buttons fold or unfold every
  section at once (nesting is preserved, so you can re-expand level by level).

## 🏗️ Structure

```
src/main/java/com/markdownreader/
├── Launcher.java                  entry point
├── App.java                       JavaFX Application (Scene/Stage)
├── markdown/
│   ├── MarkdownRenderer.java      Markdown -> HTML (flexmark) + heading extraction
│   ├── HtmlPageBuilder.java       wraps HTML with CSS + highlight.js
│   ├── RenderResult.java          html + headings
│   └── Heading.java               table of contents entry
└── ui/
    ├── MainView.java              toolbar, sidebar, WebView, actions
    ├── HeadingFolder.java         org-mode style heading folding for the editor
    ├── FileWatcher.java           automatic reload
    └── Theme.java                 light/dark theme

src/main/resources/
├── css/app.css                    JavaFX UI stylesheet
├── web/markdown.css               rendered content stylesheet
└── sample/welcome.md              welcome document
```

## 🛠️ Technologies

- [JavaFX](https://openjfx.io/) 21 — UI and `WebView`
- [flexmark-java](https://github.com/vsch/flexmark-java) — Markdown parser/renderer
- [highlight.js](https://highlightjs.org/) — syntax highlighting (bundled, offline)
- [twemoji](https://github.com/jdecked/twemoji) — colored SVG emojis (bundled, offline)

## 📄 License

MIT

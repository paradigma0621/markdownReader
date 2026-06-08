package com.markdownreader.ui;

import com.markdownreader.markdown.Heading;
import com.markdownreader.markdown.HtmlPageBuilder;
import com.markdownreader.markdown.MarkdownRenderer;
import com.markdownreader.markdown.RenderResult;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Builds and controls the main UI of the Markdown viewer.
 */
public final class MainView {

    private static final double MIN_SCALE = 0.6;
    private static final double MAX_SCALE = 2.4;
    private static final double SCALE_STEP = 0.1;

    private final Stage stage;
    private final Preferences prefs = Preferences.userNodeForPackage(MainView.class);

    private final MarkdownRenderer renderer = new MarkdownRenderer();
    private final HtmlPageBuilder pageBuilder = new HtmlPageBuilder();

    private final BorderPane root = new BorderPane();
    private final WebView webView = new WebView();
    private final ListView<Heading> tocList = new ListView<>();
    private final Label statusLabel = new Label("No document open");
    private final Label zoomLabel = new Label("100%");
    private final VBox sidebar = new VBox();
    private final StackPane welcomePane = new StackPane();

    private final TextArea editorArea = new TextArea();
    private final HeadingFolder folder = new HeadingFolder();
    private final StackPane centerStack = new StackPane();
    private final SplitPane centerSplit = new SplitPane();
    private final PauseTransition previewDebounce = new PauseTransition(Duration.millis(200));

    private final DoubleProperty fontScale = new SimpleDoubleProperty(1.0);
    private Theme theme;
    private boolean sidebarVisible = true;
    private boolean focusMode = false;
    private boolean fullscreenMode = false;

    /** Top/bottom chrome, kept so it can be detached/restored in fullscreen mode. */
    private Node toolbar;
    private Node statusBar;

    private File currentFile;
    private long currentFileTimestamp;
    private FileWatcher fileWatcher;

    /** Currently loaded Markdown text (source of truth for editing/saving). */
    private String currentMarkdown = "";
    private boolean editMode = false;
    private boolean dirty = false;
    /** Prevents marking the document as modified when populating the editor from code. */
    private boolean suppressEditorListener = false;
    /** Scroll position to restore after the next preview reload ({@code < 0} = none). */
    private double pendingScrollRestore = -1;

    public MainView(Stage stage) {
        this.stage = stage;
        // Always start in the light theme, regardless of the last session's choice.
        this.theme = Theme.LIGHT;
        this.fontScale.set(prefs.getDouble("fontScale", 1.0));

        buildLayout();
        applyThemeToRoot();
        showWelcome();
        registerShortcuts();
        enableDragAndDrop();
        // Keep our layout in sync with the stage's fullscreen state, even when the
        // user leaves fullscreen with the default ESC key rather than F12.
        stage.fullScreenProperty().addListener((obs, was, isFull) -> applyFullscreenLayout(isFull));
        stage.setFullScreenExitHint("Press F12 or ESC to exit fullscreen");
        stage.setOnCloseRequest(e -> {
            if (!confirmDiscardChanges()) {
                e.consume();
            }
        });
    }

    public Region getRoot() {
        return root;
    }

    public void requestFocus() {
        webView.requestFocus();
    }

    // ---------------------------------------------------------------- layout

    private void buildLayout() {
        root.getStyleClass().add("app-root");
        toolbar = buildToolbar();
        statusBar = buildStatusBar();
        root.setTop(toolbar);
        root.setCenter(buildCenter());
        root.setLeft(buildSidebar());
        root.setBottom(statusBar);
    }

    private Node buildToolbar() {
        Button openBtn = iconButton("📂", "Open document  (Ctrl+O)", e -> openFileDialog());
        Button reloadBtn = iconButton("↻", "Reload  (Ctrl+R)", e -> reloadCurrent());
        Button sidebarBtn = iconButton("☰", "Show/hide table of contents  (Ctrl+B)", e -> toggleSidebar());

        Button newBtn = iconButton("✚", "New document  (Ctrl+N)", e -> newDocument());
        Button editBtn = iconButton("✎", "Edit text  (Ctrl+E)", e -> toggleEditMode());
        editBtn.setId("edit-button");
        Button saveBtn = iconButton("💾", "Save  (Ctrl+S)", e -> save());
        Button focusModeBtn = iconButton("⛶", "Hide editor – full preview  (F11)", e -> toggleFocusMode());
        focusModeBtn.setId("focus-mode-button");
        Button fullscreenBtn = iconButton("⤢", "Fullscreen – markdown only  (F12)", e -> toggleFullscreen());
        fullscreenBtn.setId("fullscreen-button");

        Button collapseAllBtn = iconButton("⊟", "Collapse all heading sections", e -> collapseAll());
        collapseAllBtn.setId("collapse-all-button");
        Button expandAllBtn = iconButton("⊞", "Expand all heading sections", e -> expandAll());
        expandAllBtn.setId("expand-all-button");

        Button zoomOutBtn = iconButton("−", "Zoom out  (Ctrl+-)", e -> changeScale(-SCALE_STEP));
        Button zoomInBtn = iconButton("+", "Zoom in  (Ctrl++)", e -> changeScale(SCALE_STEP));
        zoomLabel.getStyleClass().add("zoom-label");
        zoomLabel.setOnMouseClicked(e -> resetScale());
        Tooltip.install(zoomLabel, new Tooltip("Click to reset zoom (100%)"));

        Button themeBtn = iconButton(themeGlyph(), "Toggle light/dark theme  (Ctrl+T)", e -> toggleTheme());
        themeBtn.setId("theme-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label title = new Label("Markdown Reader");
        title.getStyleClass().add("app-title");

        HBox bar = new HBox(8,
                openBtn, reloadBtn, sidebarBtn,
                separator(), newBtn, editBtn, saveBtn, focusModeBtn, fullscreenBtn,
                separator(), collapseAllBtn, expandAllBtn,
                separator(), zoomOutBtn, zoomLabel, zoomInBtn,
                spacer, title, separator(), themeBtn);
        bar.getStyleClass().add("toolbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 14, 8, 14));
        return bar;
    }

    private Node buildCenter() {
        webView.setContextMenuEnabled(false);
        webView.getEngine().setJavaScriptEnabled(true);
        // Re-install the JS bridge on every page load so a right-click on a heading
        // in the rendered preview can fold/unfold its section.
        webView.getEngine().getLoadWorker().stateProperty().addListener((o, was, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                installPreviewFoldBridge();
                restorePendingScroll();
            }
        });

        centerStack.getChildren().setAll(webView, welcomePane);
        centerStack.getStyleClass().add("content-area");

        editorArea.getStyleClass().add("editor-area");
        editorArea.setWrapText(true);
        editorArea.setPromptText("Type your Markdown here…");
        editorArea.textProperty().addListener((obs, old, txt) -> onEditorTextChanged(txt));
        // Shift+Tab folds/unfolds the heading section under the caret (org-mode style).
        editorArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleEditorFold);

        // Updates the preview with a small delay to avoid re-rendering on every keystroke.
        previewDebounce.setOnFinished(e -> refreshPreview());

        // In read mode, the SplitPane contains only the preview (full width).
        // When editing, the editor is inserted on the left.
        centerSplit.getItems().setAll(centerStack);
        centerSplit.getStyleClass().add("editor-split");
        return centerSplit;
    }

    private Node buildSidebar() {
        Label header = new Label("Table of Contents");
        header.getStyleClass().add("sidebar-header");

        tocList.getStyleClass().add("toc-list");
        tocList.setPlaceholder(new Label("No headings"));
        tocList.setCellFactory(list -> new TocCell());
        tocList.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null && !sel.id().isEmpty()) {
                scrollToAnchor(sel.id());
            }
        });
        VBox.setVgrow(tocList, Priority.ALWAYS);

        sidebar.getChildren().setAll(header, tocList);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(280);
        sidebar.setMinWidth(220);
        return sidebar;
    }

    private Node buildStatusBar() {
        statusLabel.getStyleClass().add("status-label");
        HBox bar = new HBox(statusLabel);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(5, 14, 5, 14));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void showWelcome() {
        Label icon = new Label("📝");
        icon.getStyleClass().add("welcome-icon");
        Label title = new Label("Markdown Reader");
        title.getStyleClass().add("welcome-title");
        Label subtitle = new Label("Open a .md file or drag it onto this window");
        subtitle.getStyleClass().add("welcome-subtitle");
        Button openBtn = new Button("Open document");
        openBtn.getStyleClass().add("primary-button");
        openBtn.setOnAction(e -> openFileDialog());

        VBox box = new VBox(14, icon, title, subtitle, openBtn);
        box.setAlignment(Pos.CENTER);
        welcomePane.getChildren().setAll(box);
        welcomePane.getStyleClass().add("welcome-pane");
        welcomePane.setVisible(true);

        // Also shows the embedded welcome document in the WebView (behind).
        loadEmbeddedSample();
    }

    // ------------------------------------------------------------- actions

    public void openFileDialog() {
        if (!confirmDiscardChanges()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Markdown document");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown", "*.mdown", "*.mkd"),
                new FileChooser.ExtensionFilter("All files", "*.*"));

        String last = prefs.get("lastDir", System.getProperty("user.home"));
        File lastDir = new File(last);
        if (lastDir.isDirectory()) {
            chooser.setInitialDirectory(lastDir);
        }

        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            openFile(file);
        }
    }

    public void openFile(File file) {
        try {
            String markdown = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            this.currentFile = file;
            this.currentFileTimestamp = file.lastModified();
            this.currentMarkdown = markdown;
            this.dirty = false;
            prefs.put("lastDir", file.getParent() == null ? "" : file.getParent());

            syncEditorText(markdown);
            refreshPreview();
            welcomePane.setVisible(false);
            updateTitle();
            updateStatus(file, markdown);
            watchFile(file);
        } catch (IOException ex) {
            statusLabel.setText("Error reading: " + file.getName() + " (" + ex.getMessage() + ")");
        }
    }

    private void reloadCurrent() {
        if (currentFile != null && currentFile.isFile()) {
            if (!confirmDiscardChanges()) {
                return;
            }
            openFile(currentFile);
        }
    }

    private void refreshPreview() {
        refreshPreview(false);
    }

    /**
     * Renders the preview from the current (possibly folded) editor view, so collapsed
     * sections are hidden in the styled output as well.
     *
     * @param preserveScroll when {@code true}, the current scroll position is captured
     *        and restored after the reload, so folding/unfolding doesn't jump back to
     *        the top of the document.
     */
    private void refreshPreview(boolean preserveScroll) {
        if (preserveScroll) {
            try {
                Object y = webView.getEngine().executeScript("window.scrollY");
                pendingScrollRestore = (y instanceof Number n) ? n.doubleValue() : -1;
            } catch (Exception e) {
                pendingScrollRestore = -1;
            }
        }
        RenderResult result = renderer.render(folder.displayText(editorArea.getText()));
        String page = pageBuilder.build(result.html(), theme, fontScale.get());
        webView.getEngine().loadContent(page, "text/html");
        tocList.getItems().setAll(result.headings());
    }

    /** Restores the scroll position captured before a fold-driven preview reload. */
    private void restorePendingScroll() {
        if (pendingScrollRestore >= 0) {
            try {
                webView.getEngine().executeScript(
                        "window.scrollTo(0, " + (long) pendingScrollRestore + ");");
            } catch (Exception ignored) {
                // page not ready
            }
            pendingScrollRestore = -1;
        }
    }

    private void loadEmbeddedSample() {
        try (var in = MainView.class.getResourceAsStream("/sample/welcome.md")) {
            if (in != null) {
                String md = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                this.currentMarkdown = md;
                syncEditorText(md);
                refreshPreview();
            }
        } catch (IOException ignored) {
            // sample is optional
        }
    }

    // ------------------------------------------------------------- editing

    private void toggleEditMode() {
        setEditMode(!editMode);
    }

    private void setEditMode(boolean on) {
        if (on == editMode) {
            return;
        }
        editMode = on;
        if (on) {
            // Entering edit mode starts from the fully expanded document; keep the
            // preview in sync (it may have been showing folded sections).
            syncEditorText(currentMarkdown);
            refreshPreview();
            if (!focusMode && !centerSplit.getItems().contains(editorArea)) {
                centerSplit.getItems().add(0, editorArea);
                centerSplit.setDividerPositions(0.45);
            }
            welcomePane.setVisible(false);
            if (!focusMode) {
                editorArea.requestFocus();
            }
        } else {
            centerSplit.getItems().remove(editorArea);
        }
        updateEditButton();
        updateTitle();
    }

    private void onEditorTextChanged(String txt) {
        if (suppressEditorListener) {
            return;
        }
        // The editor view may have collapsed heading sections; the source of truth
        // for rendering and saving is always the fully expanded text.
        currentMarkdown = folder.expand(txt);
        dirty = true;
        previewDebounce.playFromStart();
        updateTitle();
    }

    /** Sets the editor text without triggering the "modified" cycle. */
    private void syncEditorText(String txt) {
        suppressEditorListener = true;
        folder.reset(); // fresh text starts fully expanded
        editorArea.setText(txt);
        suppressEditorListener = false;
    }

    /**
     * Folds or unfolds the heading section under the caret on Shift+Tab. Collapsing
     * only changes the view, not the document content, so it never marks the document
     * as modified.
     */
    private void handleEditorFold(KeyEvent e) {
        if (e.getCode() != KeyCode.TAB || !e.isShiftDown()
                || e.isControlDown() || e.isAltDown() || e.isMetaDown()) {
            return;
        }
        if (applyFold(folder.toggle(editorArea.getText(), editorArea.getCaretPosition()))) {
            e.consume();
        }
    }

    /** Folds every heading section (toolbar button). */
    private void collapseAll() {
        applyFold(folder.collapseAll(editorArea.getText(), editorArea.getCaretPosition()));
    }

    /** Expands every heading section (toolbar button). */
    private void expandAll() {
        applyFold(folder.expandAll(editorArea.getText(), editorArea.getCaretPosition()));
    }

    /** Toggles the {@code nth} heading of the rendered preview (right-click bridge). */
    private void toggleHeadingByIndex(int nth) {
        applyFold(folder.toggleNth(editorArea.getText(), nth));
    }

    /**
     * Applies a new folded view to the editor and refreshes the preview. Folding is a
     * view change only, so the editor listener is suppressed and the document is not
     * marked dirty. Returns whether anything was applied.
     */
    private boolean applyFold(HeadingFolder.Result r) {
        if (r == null) {
            return false;
        }
        suppressEditorListener = true;
        editorArea.setText(r.text());
        editorArea.positionCaret(r.caret());
        suppressEditorListener = false;
        refreshPreview(true); // keep the preview where it is; only the section changes
        return true;
    }

    /**
     * Exposes {@link #toggleHeadingByIndex(int)} to the preview's JavaScript so a
     * right-click on a heading toggles its fold. Must be public for the WebView bridge.
     */
    public final class PreviewFoldBridge {
        public void toggleHeading(int index) {
            toggleHeadingByIndex(index);
        }
    }

    private final PreviewFoldBridge previewFoldBridge = new PreviewFoldBridge();

    /**
     * Installs the JS bridges into the freshly loaded page: the fold bridge (right/left
     * click on a heading folds its section) and the in-document anchor navigation
     * handler (clicking a {@code #fragment} link scrolls to the matching heading).
     */
    private void installPreviewFoldBridge() {
        try {
            JSObject window = (JSObject) webView.getEngine().executeScript("window");
            window.setMember("mdFold", previewFoldBridge);
            webView.getEngine().executeScript(PREVIEW_FOLD_JS);
            webView.getEngine().executeScript(ANCHOR_NAV_JS);
        } catch (Exception ignored) {
            // page not ready or scripting unavailable
        }
    }

    /**
     * Makes every heading in the preview clickable to fold/unfold its section. Headings
     * are identified by their position (0-based) among all headings, which matches the
     * order the folder uses on the same view text. A left click is used (reliable and
     * discoverable via the pointer cursor); a right click works too as a fallback.
     */
    private static final String PREVIEW_FOLD_JS =
            "(function() {"
            + "  function toggle(idx, e) {"
            + "    if (e) { e.preventDefault(); e.stopPropagation(); }"
            + "    if (idx >= 0 && window.mdFold) { window.mdFold.toggleHeading(idx); }"
            + "  }"
            + "  var hs = document.querySelectorAll('h1,h2,h3,h4,h5,h6');"
            + "  for (var i = 0; i < hs.length; i++) {"
            + "    (function(h, idx) {"
            + "      h.style.cursor = 'pointer';"
            + "      h.title = 'Click to fold/unfold this section';"
            + "      h.addEventListener('click', function(e) { toggle(idx, e); });"
            + "      h.addEventListener('contextmenu', function(e) { toggle(idx, e); });"
            + "    })(hs[i], i);"
            + "  }"
            + "})();";

    /**
     * Makes in-document anchor links ({@code <a href="#fragment">}) scroll the preview to
     * the matching heading. JavaFX's WebView loads the page via {@code loadContent} with no
     * base URL, and native {@code #fragment} navigation is unreliable in that mode, so the
     * jump is performed explicitly here.
     *
     * <p>Resolution is resilient: it tries {@code getElementById(fragment)} first (the
     * heading ids generated by flexmark match the GitHub-style slugs users write), then
     * {@code getElementsByName}, and finally a slug comparison against every heading's text
     * (so a hand-written fragment still resolves even if it differs slightly). The slug
     * mirrors flexmark's algorithm: lowercase, drop characters other than letters, digits,
     * spaces and hyphens, then turn spaces into hyphens (repeated hyphens are NOT collapsed).
     *
     * <p>Anchors that are a heading's own self-link are skipped on purpose: clicking a
     * heading must keep folding its section (handled by {@link #PREVIEW_FOLD_JS}).
     */
    private static final String ANCHOR_NAV_JS =
            "(function() {"
            + "  function slugify(s) {"
            + "    return (s || '').toLowerCase().trim()"
            + "      .replace(/[^\\p{L}\\p{N} -]/gu, '')"
            + "      .replace(/ /g, '-');"
            + "  }"
            + "  function findTarget(frag) {"
            + "    if (!frag) { return null; }"
            + "    var el = document.getElementById(frag);"
            + "    if (el) { return el; }"
            + "    var named = document.getElementsByName(frag);"
            + "    if (named && named.length) { return named[0]; }"
            + "    var hs = document.querySelectorAll('h1,h2,h3,h4,h5,h6');"
            + "    for (var i = 0; i < hs.length; i++) {"
            + "      if (slugify(hs[i].textContent) === frag) { return hs[i]; }"
            + "    }"
            + "    return null;"
            + "  }"
            + "  var links = document.querySelectorAll('a[href^=\"#\"]');"
            + "  for (var i = 0; i < links.length; i++) {"
            + "    var a = links[i];"
            + "    if (a.closest('h1,h2,h3,h4,h5,h6')) { continue; }"
            + "    a.addEventListener('click', function(e) {"
            + "      var href = this.getAttribute('href') || '';"
            + "      var frag;"
            + "      try { frag = decodeURIComponent(href.slice(1)); }"
            + "      catch (err) { frag = href.slice(1); }"
            + "      var t = findTarget(frag);"
            + "      if (t) {"
            + "        e.preventDefault();"
            + "        e.stopPropagation();"
            + "        t.scrollIntoView({ behavior: 'smooth', block: 'start' });"
            + "      }"
            + "    });"
            + "  }"
            + "})();";

    /** Saves the current text to the open file; prompts for a destination if none exists. */
    private void save() {
        String content = editMode ? folder.expand(editorArea.getText()) : currentMarkdown;
        File target = currentFile;
        if (target == null) {
            target = chooseSaveFile();
            if (target == null) {
                return; // user cancelled
            }
        }
        try {
            Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
            currentFile = target;
            currentMarkdown = content;
            currentFileTimestamp = target.lastModified();
            dirty = false;
            prefs.put("lastDir", target.getParent() == null ? "" : target.getParent());
            welcomePane.setVisible(false);
            updateTitle();
            updateStatus(target, content);
            watchFile(target);
        } catch (IOException ex) {
            statusLabel.setText("Error saving: " + target.getName() + " (" + ex.getMessage() + ")");
        }
    }

    private File chooseSaveFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Markdown document");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown", "*.mdown", "*.mkd"));
        chooser.setInitialFileName("untitled.md");

        String last = prefs.get("lastDir", System.getProperty("user.home"));
        File lastDir = new File(last);
        if (lastDir.isDirectory()) {
            chooser.setInitialDirectory(lastDir);
        }
        return chooser.showSaveDialog(stage);
    }

    /** Creates an empty document already in edit mode. */
    private void newDocument() {
        if (!confirmDiscardChanges()) {
            return;
        }
        if (fileWatcher != null) {
            fileWatcher.stop();
            fileWatcher = null;
        }
        currentFile = null;
        currentFileTimestamp = 0;
        currentMarkdown = "";
        dirty = false;
        syncEditorText("");
        refreshPreview();
        welcomePane.setVisible(false);
        setEditMode(true);
        statusLabel.setText("New document (unsaved)");
        updateTitle();
    }

    /**
     * When there are unsaved edits, asks whether the user wants to save, discard,
     * or cancel. Returns {@code true} if the action that replaces the current
     * content may proceed.
     */
    private boolean confirmDiscardChanges() {
        if (!dirty) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle("Unsaved edits");
        String name = currentFile != null ? currentFile.getName() : "untitled document";
        alert.setHeaderText("There are unsaved changes in " + name + ".");
        alert.setContentText("What would you like to do?");

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discardBtn = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(saveBtn, discardBtn, cancelBtn);

        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() == cancelBtn) {
            return false;
        }
        if (choice.get() == saveBtn) {
            save();
            // If saving was cancelled (e.g. "Save as" aborted), don't proceed.
            return !dirty;
        }
        return true; // Discard
    }

    private void updateTitle() {
        String name = currentFile != null ? currentFile.getName() : "Untitled";
        String mark = dirty ? "● " : "";
        String mode = editMode ? "  [edit]" : "";
        stage.setTitle(mark + name + mode + " — Markdown Reader");
    }

    private void updateEditButton() {
        Node btn = root.lookup("#edit-button");
        if (btn instanceof Button b) {
            if (editMode) {
                if (!b.getStyleClass().contains("active")) {
                    b.getStyleClass().add("active");
                }
            } else {
                b.getStyleClass().remove("active");
            }
        }
    }

    // ------------------------------------------------------------ theme/zoom

    private void toggleTheme() {
        theme = theme.toggled();
        prefs.put("theme", theme.name());
        applyThemeToRoot();
        rerenderCurrent();
        Node btn = root.lookup("#theme-button");
        if (btn instanceof Button b) {
            b.setText(themeGlyph());
        }
    }

    private void applyThemeToRoot() {
        root.getStyleClass().removeAll("theme-light", "theme-dark");
        root.getStyleClass().add(theme == Theme.DARK ? "theme-dark" : "theme-light");
    }

    private String themeGlyph() {
        return theme == Theme.DARK ? "☀" : "☾"; // sun / moon
    }

    private void changeScale(double delta) {
        double next = clamp(fontScale.get() + delta, MIN_SCALE, MAX_SCALE);
        fontScale.set(Math.round(next * 100) / 100.0);
        prefs.putDouble("fontScale", fontScale.get());
        zoomLabel.setText(Math.round(fontScale.get() * 100) + "%");
        rerenderCurrent();
    }

    private void resetScale() {
        fontScale.set(1.0);
        prefs.putDouble("fontScale", 1.0);
        zoomLabel.setText("100%");
        rerenderCurrent();
    }

    private void rerenderCurrent() {
        // Re-renders in-memory content (preserves unsaved edits and the current fold
        // state); unlike reloadCurrent(), which re-reads the file from disk.
        refreshPreview();
    }

    private void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        if (!focusMode) {
            root.setLeft(sidebarVisible ? sidebar : null);
        }
    }

    private void toggleFocusMode() {
        setFocusMode(!focusMode);
    }

    private void setFocusMode(boolean on) {
        if (on == focusMode) {
            return;
        }
        focusMode = on;
        // Sidebar: hidden in focus mode, restored according to sidebarVisible when exiting
        root.setLeft((!focusMode && sidebarVisible) ? sidebar : null);
        if (focusMode) {
            centerSplit.getItems().remove(editorArea);
        } else if (editMode && !centerSplit.getItems().contains(editorArea)) {
            centerSplit.getItems().add(0, editorArea);
            centerSplit.setDividerPositions(0.45);
        }
        updateFocusModeButton();
    }

    private void updateFocusModeButton() {
        Node btn = root.lookup("#focus-mode-button");
        if (btn instanceof Button b) {
            if (focusMode) {
                if (!b.getStyleClass().contains("active")) {
                    b.getStyleClass().add("active");
                }
            } else {
                b.getStyleClass().remove("active");
            }
        }
    }

    private void toggleFullscreen() {
        // Driving the stage property triggers the fullScreenProperty listener,
        // which performs the actual layout changes via applyFullscreenLayout().
        stage.setFullScreen(!stage.isFullScreen());
    }

    /**
     * Enters/exits "total fullscreen": OS fullscreen with every piece of chrome
     * removed (toolbar, status bar, sidebar and editor), leaving only the rendered
     * Markdown visible. Exiting restores the layout the user had before.
     */
    private void applyFullscreenLayout(boolean on) {
        if (on == fullscreenMode) {
            return;
        }
        fullscreenMode = on;
        if (on) {
            root.setTop(null);
            root.setBottom(null);
            root.setLeft(null);
            centerSplit.getItems().remove(editorArea);
            welcomePane.setVisible(false);
            root.getStyleClass().add("fullscreen-mode");
            webView.requestFocus();
        } else {
            root.setTop(toolbar);
            root.setBottom(statusBar);
            root.setLeft((!focusMode && sidebarVisible) ? sidebar : null);
            if (editMode && !focusMode && !centerSplit.getItems().contains(editorArea)) {
                centerSplit.getItems().add(0, editorArea);
                centerSplit.setDividerPositions(0.45);
            }
            root.getStyleClass().remove("fullscreen-mode");
        }
        updateFullscreenButton();
    }

    private void updateFullscreenButton() {
        Node btn = root.lookup("#fullscreen-button");
        if (btn instanceof Button b) {
            if (fullscreenMode) {
                if (!b.getStyleClass().contains("active")) {
                    b.getStyleClass().add("active");
                }
            } else {
                b.getStyleClass().remove("active");
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private void scrollToAnchor(String id) {
        // Escapes single quotes in the id for JS.
        String safe = id.replace("\\", "\\\\").replace("'", "\\'");
        String js = "var el = document.getElementById('" + safe + "');"
                + "if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'start' }); }";
        try {
            webView.getEngine().executeScript(js);
        } catch (Exception ignored) {
            // document still loading
        }
    }

    private void updateStatus(File file, String markdown) {
        int words = markdown.isBlank() ? 0 : markdown.trim().split("\\s+").length;
        int lines = markdown.isEmpty() ? 0 : (int) markdown.lines().count();
        statusLabel.setText("%s  •  %d words  •  %d lines"
                .formatted(file.getAbsolutePath(), words, lines));
    }

    private void registerShortcuts() {
        root.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                // Scene-level filters (capture phase): run BEFORE the WebView
                // handles the event natively. Without this, neither keyboard shortcuts nor
                // Ctrl+scroll zoom fire while the WebView is focused.
                scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
                scene.addEventFilter(ScrollEvent.SCROLL, this::handleScrollZoom);
            }
        });
    }

    /** Zoom with Ctrl + mouse wheel. Scrolling without Ctrl proceeds normally (not consumed). */
    private void handleScrollZoom(ScrollEvent e) {
        if (e.isControlDown() && e.getDeltaY() != 0) {
            changeScale(e.getDeltaY() > 0 ? SCALE_STEP : -SCALE_STEP);
            e.consume();
        }
    }

    /**
     * Handles keyboard shortcuts. Accepts the various physical keys that produce
     * {@code +} and {@code -} (number row and numpad) so that zoom works
     * regardless of keyboard layout.
     */
    private void handleShortcut(KeyEvent e) {
        if (e.getCode() == KeyCode.F11 && !e.isShortcutDown() && !e.isAltDown() && !e.isShiftDown()) {
            toggleFocusMode();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.F12 && !e.isShortcutDown() && !e.isAltDown() && !e.isShiftDown()) {
            toggleFullscreen();
            e.consume();
            return;
        }
        if (!e.isShortcutDown()) { // Ctrl on Windows/Linux, Cmd on macOS
            return;
        }
        switch (e.getCode()) {
            case PLUS, ADD, EQUALS -> changeScale(SCALE_STEP);   // Ctrl++ / Ctrl+=
            case MINUS, SUBTRACT -> changeScale(-SCALE_STEP);    // Ctrl+-
            case DIGIT0, NUMPAD0 -> resetScale();                // Ctrl+0
            case N -> newDocument();
            case O -> openFileDialog();
            case R -> reloadCurrent();
            case T -> toggleTheme();
            case B -> toggleSidebar();
            case E -> toggleEditMode();
            case S -> save();
            default -> {
                return; // not a known shortcut: don't consume the event
            }
        }
        e.consume();
    }

    private void enableDragAndDrop() {
        root.setOnDragOver(this::onDragOver);
        root.setOnDragDropped(this::onDragDropped);
    }

    private void onDragOver(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles() && isMarkdown(db.getFiles().get(0))) {
            event.acceptTransferModes(TransferMode.COPY);
            root.getStyleClass().add("drag-active");
        }
        event.consume();
    }

    private void onDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean done = false;
        if (db.hasFiles()) {
            File file = db.getFiles().get(0);
            if (isMarkdown(file) && confirmDiscardChanges()) {
                openFile(file);
                done = true;
            }
        }
        root.getStyleClass().remove("drag-active");
        event.setDropCompleted(done);
        event.consume();
    }

    private static boolean isMarkdown(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".markdown")
                || name.endsWith(".mdown") || name.endsWith(".mkd")
                || name.endsWith(".txt");
    }

    private void watchFile(File file) {
        if (fileWatcher != null) {
            fileWatcher.stop();
        }
        fileWatcher = new FileWatcher(file, () -> {
            long ts = file.lastModified();
            // Do not reload over unsaved edits.
            if (ts != currentFileTimestamp && !dirty) {
                Platform.runLater(this::reloadCurrent);
            }
        });
        fileWatcher.start();
    }

    private Button iconButton(String glyph, String tooltip, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button b = new Button(glyph);
        b.getStyleClass().add("tool-button");
        b.setTooltip(new Tooltip(tooltip));
        b.setOnAction(action);
        b.setFocusTraversable(false);
        return b;
    }

    private Region separator() {
        Region r = new Region();
        r.getStyleClass().add("toolbar-separator");
        return r;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** TOC cell with indentation proportional to the heading level. */
    private static final class TocCell extends ListCell<Heading> {
        @Override
        protected void updateItem(Heading item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                getStyleClass().removeAll("toc-h1", "toc-h2", "toc-h3", "toc-h4", "toc-h5", "toc-h6");
            } else {
                setText(item.text());
                setPadding(new Insets(4, 8, 4, 8 + (item.level() - 1) * 14));
                getStyleClass().removeAll("toc-h1", "toc-h2", "toc-h3", "toc-h4", "toc-h5", "toc-h6");
                getStyleClass().add("toc-h" + Math.min(item.level(), 6));
            }
        }
    }
}

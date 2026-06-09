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
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
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
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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

    /** Editor font-size bounds and default (in CSS pixels). */
    private static final int MIN_EDITOR_FONT = 8;
    private static final int MAX_EDITOR_FONT = 40;
    private static final int DEFAULT_EDITOR_FONT = 14;
    /** Default editor font family — matches the first entry in the app.css monospace stack. */
    private static final String DEFAULT_EDITOR_FONT_FAMILY = "monospace";

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
    /** Debounces editor→preview scroll sync so rapid scrolling does not flood the WebEngine. */
    private final PauseTransition scrollSyncDebounce = new PauseTransition(Duration.millis(50));

    private final DoubleProperty fontScale = new SimpleDoubleProperty(1.0);
    private Theme theme;
    private boolean sidebarVisible = true;
    private boolean focusMode = false;
    private boolean fullscreenMode = false;

    /** Editor font size in pixels (persisted, applied live to {@link #editorArea}). */
    private int editorFontSize;
    /** Editor font family name (persisted, applied live to {@link #editorArea}). */
    private String editorFontFamily;
    /** Whether the preview's vertical scrollbar is shown in F12 fullscreen mode. */
    private boolean f12PreviewScrollbar;

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
    /**
     * Prevents editor→preview scroll sync from firing while the editor's scrollbar is
     * moved programmatically (e.g. by {@link #scrollEditorToLine}), avoiding a feedback loop.
     */
    private boolean suppressScrollSync = false;
    /** Ensures the editor's vertical ScrollBar listener is registered at most once. */
    private boolean editorScrollListenerAttached = false;
    /** Scroll position to restore after the next preview reload ({@code < 0} = none). */
    private double pendingScrollRestore = -1;
    /**
     * Scroll fraction (0..1 of the scrollable range) to restore after the next preview
     * reload ({@code < 0} = none). Used by zoom, where the font-size change alters the
     * document height, so an absolute scrollY would not keep the same passage in view.
     */
    private double pendingScrollRatio = -1;

    // ─── Find-bar state ───────────────────────────────────────────────────────
    /** The overlay bar shown when the user presses Ctrl+F. */
    private HBox findBar;
    /** Text input inside the find bar. */
    private TextField findField;
    /** Label showing match count (e.g. "3/12" or "No matches"). */
    private Label findMatchLabel;
    /** Whether the find bar is currently visible. */
    private boolean findBarVisible = false;
    /** All match ranges [start, end) found in the editor for the current query. */
    private List<int[]> editorFindMatches = new ArrayList<>();
    /** Index into {@link #editorFindMatches} for the currently highlighted match (-1 = none). */
    private int editorFindIndex = -1;
    /** Total occurrences found in the preview for the current query (0 = not yet computed). */
    private int previewFindTotal = 0;
    /** 1-based index of the currently highlighted preview match (0 = not yet positioned). */
    private int previewFindIndex = 0;

    /** How the scroll position should be preserved across a preview reload. */
    private enum ScrollMode {
        /** Reset to the top of the document. */
        NONE,
        /** Restore the exact {@code window.scrollY} (used by heading folding). */
        ABSOLUTE,
        /** Restore the same fraction of the scrollable range (used by zoom). */
        RATIO
    }

    public MainView(Stage stage) {
        this.stage = stage;
        // Restore the theme from the last session; default to LIGHT if no pref exists.
        String savedTheme = prefs.get("theme", Theme.LIGHT.name());
        try {
            this.theme = Theme.valueOf(savedTheme);
        } catch (IllegalArgumentException ignored) {
            this.theme = Theme.LIGHT;
        }
        this.fontScale.set(prefs.getDouble("fontScale", 1.0));
        this.editorFontSize = clampFont(prefs.getInt("editorFontSize", DEFAULT_EDITOR_FONT));
        this.editorFontFamily = prefs.get("editorFontFamily", DEFAULT_EDITOR_FONT_FAMILY);
        this.f12PreviewScrollbar = prefs.getBoolean("f12PreviewScrollbar", true);

        buildLayout();
        applyThemeToRoot();
        applyEditorFontSize();
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
        // Wrap the center split together with the (initially hidden) find bar in a VBox
        // so the bar docks at the top of the content area when opened.
        Node centerContent = buildCenter();
        Node findBarNode = buildFindBar();
        VBox centerWrapper = new VBox(findBarNode, centerContent);
        VBox.setVgrow(centerContent, Priority.ALWAYS);
        root.setCenter(centerWrapper);
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

        Button settingsBtn = iconButton("⚙", "Settings", e -> openSettings());
        settingsBtn.setId("settings-button");

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
                spacer, title, separator(), settingsBtn, themeBtn);
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
                // The page just reloaded; re-apply the F12 scrollbar preference so the
                // injected style survives folding/zoom-driven preview reloads.
                applyF12Scrollbar();
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

        // Pushes the editor's scroll fraction to the preview, throttled.
        scrollSyncDebounce.setOnFinished(e -> pushEditorScrollToPreview());

        // Once the TextArea skin is live the vertical ScrollBar exists in the scene graph.
        // Register the scroll-sync value listener at that point (at most once).
        editorArea.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(this::attachEditorScrollListener);
            }
        });

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

    /**
     * Builds the find bar that overlays the top of the content area when Ctrl+F is
     * pressed. The bar is initially invisible and takes no layout space
     * ({@code managed=false}). Call {@link #openFindBar()} / {@link #closeFindBar()} to
     * show or hide it.
     */
    private Node buildFindBar() {
        findField = new TextField();
        findField.setPromptText("Find…");
        findField.setPrefWidth(220);
        findField.getStyleClass().add("find-field");

        findMatchLabel = new Label();
        findMatchLabel.getStyleClass().add("find-match-label");
        findMatchLabel.setMinWidth(72);

        Button prevBtn = iconButton("↑", "Previous match  (Shift+Enter)", e -> findPrev());
        Button nextBtn = iconButton("↓", "Next match  (Enter)", e -> findNext());
        Button closeBtn = iconButton("✕", "Close find bar  (Escape)", e -> closeFindBar());

        findBar = new HBox(6, findField, findMatchLabel, prevBtn, nextBtn, separator(), closeBtn);
        findBar.getStyleClass().add("find-bar");
        findBar.setAlignment(Pos.CENTER_LEFT);
        findBar.setPadding(new Insets(5, 10, 5, 10));
        findBar.setVisible(false);
        findBar.setManaged(false);

        // Live search as the user types
        findField.textProperty().addListener((obs, old, query) -> onFindQueryChanged(query));

        // Enter = next match, Shift+Enter = previous, Escape = close the bar
        findField.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER -> {
                    if (e.isShiftDown()) findPrev();
                    else findNext();
                    e.consume();
                }
                case ESCAPE -> {
                    // Also handled by the scene filter; this is a belt-and-suspenders guard.
                    closeFindBar();
                    e.consume();
                }
                default -> { /* other keys are handled by the TextField normally */ }
            }
        });

        return findBar;
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
        refreshPreview(ScrollMode.NONE);
    }

    /**
     * Renders the preview from the current (possibly folded) editor view, so collapsed
     * sections are hidden in the styled output as well.
     *
     * @param mode how the scroll position should be preserved across the reload:
     *        {@link ScrollMode#NONE} jumps to the top, {@link ScrollMode#ABSOLUTE}
     *        keeps the exact {@code scrollY} (folding) and {@link ScrollMode#RATIO}
     *        keeps the same fraction of the scrollable range (zoom, where the
     *        document height changes with the font size).
     */
    private void refreshPreview(ScrollMode mode) {
        captureScroll(mode);
        RenderResult result = renderer.render(folder.displayText(editorArea.getText()));
        String page = pageBuilder.build(result.html(), theme, fontScale.get());
        webView.getEngine().loadContent(page, "text/html");
        tocList.getItems().setAll(result.headings());
    }

    /**
     * Captures the current scroll state so it can be re-applied once the new content
     * has laid out. Exactly one pending restore is armed at a time.
     */
    private void captureScroll(ScrollMode mode) {
        pendingScrollRestore = -1;
        pendingScrollRatio = -1;
        try {
            if (mode == ScrollMode.ABSOLUTE) {
                double y = scriptNumber("window.scrollY");
                pendingScrollRestore = Double.isNaN(y) ? -1 : y;
            } else if (mode == ScrollMode.RATIO) {
                double y = scriptNumber("window.scrollY");
                double scrollHeight = scriptNumber("document.documentElement.scrollHeight");
                double clientHeight = scriptNumber("window.innerHeight");
                pendingScrollRatio = scrollRatio(y, scrollHeight, clientHeight);
            }
        } catch (Exception e) {
            pendingScrollRestore = -1;
            pendingScrollRatio = -1;
        }
    }

    /**
     * Restores the scroll position captured before the last preview reload. Folding
     * restores the exact pixel offset; zoom restores the same fraction of the (now
     * differently sized) document, computed against the new layout height.
     */
    private void restorePendingScroll() {
        if (pendingScrollRatio >= 0) {
            try {
                double scrollHeight = scriptNumber("document.documentElement.scrollHeight");
                double clientHeight = scriptNumber("window.innerHeight");
                double target = scrollTargetForRatio(pendingScrollRatio, scrollHeight, clientHeight);
                webView.getEngine().executeScript("window.scrollTo(0, " + (long) target + ");");
            } catch (Exception ignored) {
                // page not ready
            }
            pendingScrollRatio = -1;
        }
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

    /** Runs a JS expression and returns its numeric value, or {@code NaN} if not a number. */
    private double scriptNumber(String js) {
        Object r = webView.getEngine().executeScript(js);
        return (r instanceof Number n) ? n.doubleValue() : Double.NaN;
    }

    /**
     * Fraction (0..1) of the scrollable range currently scrolled. Returns 0 when the
     * document is not taller than the viewport (nothing to scroll), guarding against
     * division by zero.
     */
    static double scrollRatio(double scrollY, double scrollHeight, double clientHeight) {
        double max = scrollHeight - clientHeight;
        if (Double.isNaN(max) || max <= 0) {
            return 0.0;
        }
        return clamp(scrollY / max, 0.0, 1.0);
    }

    /**
     * Absolute scroll offset that places {@code ratio} of the (new) scrollable range
     * into view. Inverse of {@link #scrollRatio}; returns 0 when there is nothing to
     * scroll.
     */
    static double scrollTargetForRatio(double ratio, double scrollHeight, double clientHeight) {
        double max = scrollHeight - clientHeight;
        if (Double.isNaN(max) || max <= 0) {
            return 0.0;
        }
        return clamp(ratio, 0.0, 1.0) * max;
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
            // Ensure the scroll-sync listener is attached now that the editor is in the
            // scene (skin may already be live; runLater waits for layout to complete).
            Platform.runLater(this::attachEditorScrollListener);
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
        refreshPreview(ScrollMode.ABSOLUTE); // keep the preview where it is; only the section changes
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
     * Exposes the preview→editor synchronization to the page's JavaScript. A double-click
     * on rendered content opens the editor at the matching source line; scrolling the
     * preview keeps the editor's visible position aligned. Must be public for the bridge.
     */
    public final class PreviewEditBridge {
        /** Double-click: switch to edit mode and move the caret to {@code line}. */
        public void openAtLine(int line) {
            openEditorAtLine(line);
        }

        /** Scroll: keep the open editor aligned with {@code line} (no caret/focus change). */
        public void alignToLine(int line) {
            alignEditorToLine(line);
        }
    }

    private final PreviewEditBridge previewEditBridge = new PreviewEditBridge();

    /** Installs the JS bridges (fold + editor sync + in-document anchor navigation) into the loaded page. */
    private void installPreviewFoldBridge() {
        try {
            JSObject window = (JSObject) webView.getEngine().executeScript("window");
            window.setMember("mdFold", previewFoldBridge);
            window.setMember("mdEdit", previewEditBridge);
            webView.getEngine().executeScript(PREVIEW_FOLD_JS);
            webView.getEngine().executeScript(PREVIEW_SYNC_JS);
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
     * Wires preview→editor synchronization in the loaded page:
     * <ul>
     *   <li>a double-click on non-heading content opens the editor at the source line of
     *       the nearest element carrying {@code data-source-line} (headings keep their
     *       fold behavior, so a double-click there never both folds and opens the editor);</li>
     *   <li>scrolling reports the top-most visible block's source line so the editor can
     *       follow, throttled via {@code requestAnimationFrame}.</li>
     * </ul>
     */
    private static final String PREVIEW_SYNC_JS =
            "(function() {"
            + "  function hit(el) {"
            + "    while (el && el.nodeType === 1) {"
            + "      if (el.hasAttribute('data-source-line')) {"
            + "        var n = parseInt(el.getAttribute('data-source-line'), 10);"
            + "        if (!isNaN(n)) { return { line: n, el: el }; }"
            + "      }"
            + "      el = el.parentNode;"
            + "    }"
            + "    return null;"
            + "  }"
            + "  function isHeading(el) {"
            + "    return /^h[1-6]$/i.test(el.tagName || '');"
            + "  }"
            + "  document.addEventListener('dblclick', function(e) {"
            + "    var h = hit(e.target);"
            + "    if (h && !isHeading(h.el) && window.mdEdit) { window.mdEdit.openAtLine(h.line); }"
            + "  });"
            + "  var ticking = false;"
            + "  function report() {"
            + "    ticking = false;"
            + "    if (!window.mdEdit) { return; }"
            + "    var els = document.querySelectorAll('[data-source-line]');"
            + "    for (var i = 0; i < els.length; i++) {"
            + "      var r = els[i].getBoundingClientRect();"
            + "      if (r.bottom > 0) {"
            + "        var n = parseInt(els[i].getAttribute('data-source-line'), 10);"
            + "        if (!isNaN(n)) { window.mdEdit.alignToLine(n); }"
            + "        return;"
            + "      }"
            + "    }"
            + "  }"
            + "  window.addEventListener('scroll', function() {"
            + "    if (!ticking) { ticking = true; requestAnimationFrame(report); }"
            + "  });"
            + "})();";

    /**
     * Opens the editor at the source line that produced the double-clicked preview element.
     * Leaves fullscreen/focus modes if needed so the editor is visible. When coming from a
     * folded read-mode view, the clicked line is first translated to the expanded document,
     * since entering edit mode unfolds everything.
     */
    private void openEditorAtLine(int previewLine) {
        if (previewLine < 0) {
            return;
        }
        if (fullscreenMode) {
            stage.setFullScreen(false);
        }
        if (focusMode) {
            setFocusMode(false);
        }
        int targetLine;
        if (editMode) {
            // The editor already shows the same view the preview was rendered from.
            targetLine = previewLine;
        } else {
            targetLine = folder.expandedLineOf(editorArea.getText(), previewLine);
            setEditMode(true); // expands all folds and re-renders the preview
        }
        moveEditorToLine(targetLine);
    }

    /**
     * Keeps the open editor's scroll position aligned with the preview without moving the
     * caret or stealing focus (so it never marks the document as modified). No-op when the
     * editor is not visible.
     */
    private void alignEditorToLine(int previewLine) {
        if (!editMode || focusMode || fullscreenMode || previewLine < 0) {
            return;
        }
        scrollEditorToLine(previewLine);
    }

    /** Moves the caret to the start of {@code line} and scrolls it into view. */
    private void moveEditorToLine(int line) {
        String text = editorArea.getText();
        editorArea.positionCaret(offsetOfLine(text, line));
        editorArea.requestFocus();
        // Defer the scroll until the skin has laid out the (possibly just re-rendered) text.
        Platform.runLater(() -> scrollEditorToLine(line));
    }

    /** Scrolls the editor so {@code line} is roughly at the top, via its vertical scrollbar. */
    private void scrollEditorToLine(int line) {
        long total = editorArea.getText().lines().count();
        if (total <= 1) {
            return;
        }
        if (editorArea.lookup(".scroll-bar:vertical") instanceof ScrollBar bar && bar.isVisible()) {
            double fraction = clamp((double) line / (total - 1), 0.0, 1.0);
            // Suppress the scroll-sync listener so this programmatic move does not feed
            // back into the preview (the listener fires synchronously inside setValue).
            suppressScrollSync = true;
            bar.setValue(bar.getMin() + fraction * (bar.getMax() - bar.getMin()));
            Platform.runLater(() -> suppressScrollSync = false);
        }
    }

    /**
     * Registers a value listener on the editor's vertical ScrollBar that drives
     * editor→preview scroll synchronization. Called after the TextArea skin is applied;
     * the {@link #editorScrollListenerAttached} flag ensures it runs at most once.
     *
     * <p>The vertical ScrollBar is used (rather than {@code scrollTopProperty}) because
     * it exposes a normalized 0..1 range via {@code getValue()/getMin()/getMax()} without
     * requiring access to the TextArea skin's internal layout measurements. The same
     * pattern is already used by {@link #scrollEditorToLine}.
     */
    private void attachEditorScrollListener() {
        if (editorScrollListenerAttached) {
            return;
        }
        Node node = editorArea.lookup(".scroll-bar:vertical");
        if (node instanceof ScrollBar bar) {
            bar.valueProperty().addListener((obs, wasVal, newVal) -> {
                if (editMode && !suppressScrollSync && !focusMode && !fullscreenMode) {
                    scrollSyncDebounce.playFromStart();
                }
            });
            editorScrollListenerAttached = true;
        }
    }

    /**
     * Reads the editor's current vertical scroll fraction (0..1) from its ScrollBar and
     * scrolls the preview to the same proportional position in the rendered document.
     * Uses the same ratio→pixel JS pattern as zoom-aware scroll restoration.
     *
     * <p>Mapping accuracy: fraction-of-total-height is a best approximation. A line-level
     * source-to-rendered mapping would need per-element offsets that are not reliably
     * available; the proportional approach keeps the gist of the edited passage in view.
     */
    private void pushEditorScrollToPreview() {
        Node node = editorArea.lookup(".scroll-bar:vertical");
        if (!(node instanceof ScrollBar bar)) {
            return;
        }
        double range = bar.getMax() - bar.getMin();
        double fraction = range <= 0 ? 0.0 : clamp((bar.getValue() - bar.getMin()) / range, 0.0, 1.0);
        try {
            double scrollHeight = scriptNumber("document.documentElement.scrollHeight");
            double clientHeight = scriptNumber("window.innerHeight");
            double target = scrollTargetForRatio(fraction, scrollHeight, clientHeight);
            webView.getEngine().executeScript("window.scrollTo(0, " + (long) target + ");");
        } catch (Exception ignored) {
            // WebView not ready or document not yet laid out
        }
    }

    /** Character offset of the start of the 0-based {@code line} within {@code text}. */
    private static int offsetOfLine(String text, int line) {
        int offset = 0;
        int current = 0;
        for (int i = 0; i < text.length() && current < line; i++) {
            if (text.charAt(i) == '\n') {
                current++;
                offset = i + 1;
            }
        }
        return Math.min(offset, text.length());
    }

    // -------------------------------------------------------------- settings

    /**
     * JS that injects a style element hiding the preview's vertical scrollbar.
     * Only the scrollbar track/thumb is hidden; overflow is intentionally left
     * as the default (auto/scroll) so mouse-wheel and keyboard scrolling keep
     * working even when the bar is invisible.
     */
    private static final String HIDE_SCROLLBAR_JS =
            "(function() {"
            + "  if (!document.getElementById('md-hide-sb')) {"
            + "    var s = document.createElement('style');"
            + "    s.id = 'md-hide-sb';"
            + "    s.textContent = '::-webkit-scrollbar{width:0;height:0;display:none}"
            + "html{scrollbar-width:none}';"
            + "    (document.head || document.documentElement).appendChild(s);"
            + "  }"
            + "})();";

    /** JS that removes the scrollbar-hiding style element, restoring the scrollbar. */
    private static final String SHOW_SCROLLBAR_JS =
            "(function() {"
            + "  var s = document.getElementById('md-hide-sb');"
            + "  if (s && s.parentNode) { s.parentNode.removeChild(s); }"
            + "})();";

    /**
     * Opens the modal Settings dialog. Each control applies and persists its value
     * immediately, so changes are visible live without an explicit "Apply" step.
     */
    private void openSettings() {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Settings");

        Label header = new Label("Settings");
        header.getStyleClass().add("settings-title");

        // (a) Editor font size -------------------------------------------------
        Label fontLabel = new Label("Editor font size");
        fontLabel.getStyleClass().add("settings-label");
        Spinner<Integer> fontSpinner = new Spinner<>(MIN_EDITOR_FONT, MAX_EDITOR_FONT, editorFontSize);
        fontSpinner.setEditable(true);
        fontSpinner.setPrefWidth(90);
        Label fontValue = new Label(editorFontSize + " px");
        fontValue.getStyleClass().add("settings-value");
        fontSpinner.valueProperty().addListener((obs, was, val) -> {
            if (val == null) {
                return;
            }
            editorFontSize = clampFont(val);
            prefs.putInt("editorFontSize", editorFontSize);
            applyEditorFontSize();
            fontValue.setText(editorFontSize + " px");
        });
        HBox fontRow = new HBox(12, fontSpinner, fontValue);
        fontRow.setAlignment(Pos.CENTER_LEFT);

        // (b) Editor font family -----------------------------------------------
        Label fontFamilyLabel = new Label("Editor font family");
        fontFamilyLabel.getStyleClass().add("settings-label");
        ComboBox<String> fontFamilyCombo = new ComboBox<>();
        fontFamilyCombo.getItems().setAll(Font.getFamilies());
        fontFamilyCombo.setValue(editorFontFamily);
        fontFamilyCombo.setPrefWidth(260);
        fontFamilyCombo.valueProperty().addListener((obs, was, val) -> {
            if (val == null || val.isBlank()) {
                return;
            }
            editorFontFamily = val;
            prefs.put("editorFontFamily", editorFontFamily);
            applyEditorFontSize();
        });

        // (c) F12 fullscreen preview scrollbar visibility ----------------------
        Label scrollbarLabel = new Label("F12 fullscreen preview");
        scrollbarLabel.getStyleClass().add("settings-label");
        CheckBox scrollbarCheck =
                new CheckBox("Show the preview scrollbar in F12 fullscreen mode");
        scrollbarCheck.setSelected(f12PreviewScrollbar);
        scrollbarCheck.selectedProperty().addListener((obs, was, sel) -> {
            f12PreviewScrollbar = sel;
            prefs.putBoolean("f12PreviewScrollbar", sel);
            applyF12Scrollbar();
        });

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("primary-button");
        closeBtn.setOnAction(e -> dialog.close());
        HBox actions = new HBox(closeBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(14,
                header,
                fontLabel, fontRow,
                fontFamilyLabel, fontFamilyCombo,
                new Separator(),
                scrollbarLabel, scrollbarCheck,
                new Separator(),
                actions);
        box.getStyleClass().add("settings-pane");
        box.setPadding(new Insets(22));
        // Reuse the application's CSS variables and theme so the dialog matches the
        // current light/dark look.
        box.getStyleClass().add("app-root");
        box.getStyleClass().add(theme == Theme.DARK ? "theme-dark" : "theme-light");

        Scene scene = new Scene(box);
        URL css = MainView.class.getResource("/css/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        dialog.setScene(scene);
        dialog.setMinWidth(380);
        dialog.showAndWait();
    }

    /** Applies the current editor font size and family to the {@link #editorArea} via inline CSS. */
    private void applyEditorFontSize() {
        editorArea.setStyle(
                "-fx-font-family: \"" + editorFontFamily + "\";"
                + " -fx-font-size: " + editorFontSize + "px;");
    }

    /**
     * Shows or hides the preview's vertical scrollbar to match the F12 preference.
     * The scrollbar is only hidden while in F12 fullscreen mode and the preference is
     * disabled; in every other situation the normal scrollbar is restored.
     */
    private void applyF12Scrollbar() {
        boolean hide = fullscreenMode && !f12PreviewScrollbar;
        try {
            webView.getEngine().executeScript(hide ? HIDE_SCROLLBAR_JS : SHOW_SCROLLBAR_JS);
        } catch (Exception ignored) {
            // page not ready or scripting unavailable; re-applied on next load
        }
    }

    private static int clampFont(int v) {
        return Math.max(MIN_EDITOR_FONT, Math.min(MAX_EDITOR_FONT, v));
    }

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
        rerenderPreservingRatio();
    }

    private void resetScale() {
        fontScale.set(1.0);
        prefs.putDouble("fontScale", 1.0);
        zoomLabel.setText("100%");
        rerenderPreservingRatio();
    }

    private void rerenderCurrent() {
        // Re-renders in-memory content (preserves unsaved edits and the current fold
        // state); unlike reloadCurrent(), which re-reads the file from disk.
        refreshPreview();
    }

    /**
     * Re-renders the in-memory content while keeping the reader anchored on the same
     * passage. Zoom changes the root font size (and thus the document height), so the
     * scroll position is preserved as a fraction of the scrollable range rather than an
     * absolute pixel offset, which would otherwise drift the content out of view.
     */
    private void rerenderPreservingRatio() {
        refreshPreview(ScrollMode.RATIO);
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
        // Hide/restore the preview scrollbar according to the F12 preference.
        applyF12Scrollbar();
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

    // ---------------------------------------------------------------- find bar

    /** Shows the find bar and moves keyboard focus to the search field. */
    private void openFindBar() {
        findBar.setVisible(true);
        findBar.setManaged(true);
        findBarVisible = true;
        Platform.runLater(() -> {
            findField.requestFocus();
            findField.selectAll();
        });
        // If there is already a query in the field (bar was used before), re-run it now.
        String current = findField.getText();
        if (current != null && !current.isEmpty()) {
            onFindQueryChanged(current);
        }
    }

    /** Hides the find bar and clears any find-related highlights/selection. */
    private void closeFindBar() {
        findBar.setVisible(false);
        findBar.setManaged(false);
        findBarVisible = false;
        findMatchLabel.setText("");
        editorFindMatches.clear();
        editorFindIndex = -1;
        previewFindTotal = 0;
        previewFindIndex = 0;
        // Clear the WebView text selection so highlighted text is deselected.
        try {
            webView.getEngine().executeScript(
                    "if (window.getSelection) { window.getSelection().removeAllRanges(); }");
        } catch (Exception ignored) {}
        // Return focus to the active view.
        if (editMode && !focusMode) {
            editorArea.requestFocus();
        } else {
            webView.requestFocus();
        }
    }

    /**
     * Called whenever the find-field text changes. Resets all match state and runs a
     * fresh search in whichever view is currently active.
     */
    private void onFindQueryChanged(String query) {
        editorFindMatches.clear();
        editorFindIndex = -1;
        previewFindTotal = 0;
        previewFindIndex = 0;

        if (query == null || query.isEmpty()) {
            findMatchLabel.setText("");
            try {
                webView.getEngine().executeScript(
                        "if (window.getSelection) { window.getSelection().removeAllRanges(); }");
            } catch (Exception ignored) {}
            return;
        }

        if (editMode) {
            searchInEditor(query);
        } else {
            searchInPreview(query, false);
        }
    }

    // -------------------------------------------------------- editor find

    /**
     * Finds all case-insensitive occurrences of {@code query} in the editor text,
     * populates {@link #editorFindMatches}, selects the first match, and updates the
     * match counter label.
     */
    private void searchInEditor(String query) {
        String text = editorArea.getText();
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int pos = 0;
        while (true) {
            int idx = lowerText.indexOf(lowerQuery, pos);
            if (idx < 0) break;
            editorFindMatches.add(new int[]{idx, idx + lowerQuery.length()});
            pos = idx + 1;
        }
        if (editorFindMatches.isEmpty()) {
            findMatchLabel.setText("No matches");
        } else {
            editorFindIndex = 0;
            selectEditorMatch();
            findMatchLabel.setText("1/" + editorFindMatches.size());
        }
    }

    /**
     * Selects the text range of the match at {@link #editorFindIndex} and scrolls it
     * into view.
     */
    private void selectEditorMatch() {
        if (editorFindMatches.isEmpty() || editorFindIndex < 0) return;
        int[] m = editorFindMatches.get(editorFindIndex);
        editorArea.selectRange(m[0], m[1]);
        int line = lineOfOffset(editorArea.getText(), m[0]);
        scrollEditorToLine(line);
    }

    /** Returns the 0-based line number that contains character offset {@code offset}. */
    private static int lineOfOffset(String text, int offset) {
        int line = 0;
        int bound = Math.min(offset, text.length());
        for (int i = 0; i < bound; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    /** Moves to the next match in the active view (wraps around). */
    private void findNext() {
        String query = findField.getText();
        if (query == null || query.isEmpty()) return;
        if (editMode) {
            if (editorFindMatches.isEmpty()) return;
            editorFindIndex = (editorFindIndex + 1) % editorFindMatches.size();
            selectEditorMatch();
            findMatchLabel.setText((editorFindIndex + 1) + "/" + editorFindMatches.size());
        } else {
            searchInPreview(query, false);
        }
    }

    /** Moves to the previous match in the active view (wraps around). */
    private void findPrev() {
        String query = findField.getText();
        if (query == null || query.isEmpty()) return;
        if (editMode) {
            if (editorFindMatches.isEmpty()) return;
            editorFindIndex =
                    (editorFindIndex - 1 + editorFindMatches.size()) % editorFindMatches.size();
            selectEditorMatch();
            findMatchLabel.setText((editorFindIndex + 1) + "/" + editorFindMatches.size());
        } else {
            searchInPreview(query, true);
        }
    }

    // ------------------------------------------------------- preview find

    /**
     * Searches in the WebView preview using {@code window.find()}, which is the most
     * reliable navigation API available in JavaFX's embedded WebKit. The total match
     * count is computed once per query (via a JS text scan) and cached in
     * {@link #previewFindTotal}. Navigation uses WebKit's native find with
     * {@code wrapAround=true} so it cycles continuously.
     *
     * <p>Caveat: {@code window.find()} is a non-standard legacy API that is available
     * in WebKit but not in all browsers. It works reliably here because JavaFX embeds
     * WebKit and this API has been supported there since early versions.
     *
     * @param query     the text to search (case-insensitive)
     * @param backwards {@code true} to move to the previous occurrence
     */
    private void searchInPreview(String query, boolean backwards) {
        try {
            // Count total matches only when entering a fresh query
            // (previewFindTotal == 0 means the count has been reset by onFindQueryChanged)
            if (previewFindTotal == 0) {
                previewFindTotal = countPreviewMatches(query);
                // Clear any existing selection so window.find() restarts from document top.
                webView.getEngine().executeScript(
                        "if (window.getSelection) { window.getSelection().removeAllRanges(); }");
            }
            if (previewFindTotal == 0) {
                findMatchLabel.setText("No matches");
                return;
            }
            // Pass the query via a JS member variable to avoid any escaping issues.
            JSObject win = (JSObject) webView.getEngine().executeScript("window");
            win.setMember("_mdFindQuery", query);
            Object result = webView.getEngine().executeScript(
                    "window.find(window._mdFindQuery, false, " + backwards + ", true)");
            boolean found = Boolean.TRUE.equals(result);
            if (found) {
                if (!backwards) {
                    // Advance index, wrapping from total back to 1.
                    previewFindIndex = previewFindIndex % previewFindTotal + 1;
                } else {
                    // Step backward, wrapping from 1 back to total.
                    previewFindIndex =
                            (previewFindIndex - 2 + previewFindTotal) % previewFindTotal + 1;
                }
                findMatchLabel.setText(previewFindIndex + "/" + previewFindTotal);
            } else {
                findMatchLabel.setText("No matches");
            }
        } catch (Exception ex) {
            findMatchLabel.setText("");
        }
    }

    /**
     * Counts how many times {@code query} (case-insensitive) appears in the preview's
     * visible text by scanning {@code document.body.innerText} via JavaScript.
     */
    private int countPreviewMatches(String query) {
        try {
            JSObject win = (JSObject) webView.getEngine().executeScript("window");
            win.setMember("_mdFindQuery", query);
            Object result = webView.getEngine().executeScript(
                    "(function() {"
                    + "  var text = document.body ? document.body.innerText : '';"
                    + "  var q = (window._mdFindQuery || '').toLowerCase();"
                    + "  if (!q) return 0;"
                    + "  var t = text.toLowerCase();"
                    + "  var count = 0, start = 0, idx;"
                    + "  while ((idx = t.indexOf(q, start)) >= 0) { count++; start = idx + 1; }"
                    + "  return count;"
                    + "})()");
            return result instanceof Number n ? n.intValue() : 0;
        } catch (Exception ex) {
            return 0;
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
        // Escape closes the find bar regardless of modifier state, before any other check.
        if (e.getCode() == KeyCode.ESCAPE && findBarVisible) {
            closeFindBar();
            e.consume();
            return;
        }
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
        // While the find field has focus, pass all Ctrl+letter events through so the
        // TextField can handle standard editing shortcuts (Ctrl+A, Ctrl+C, etc.)
        // and global app shortcuts (Ctrl+O, Ctrl+E, …) do not fire accidentally.
        if (findBarVisible && findField != null && findField.isFocused()) {
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
            case Z -> {
                // Undo the last edit in the editor. Only meaningful while the editor is
                // open; otherwise let the event propagate so the platform can handle it.
                if (!editMode) {
                    return;
                }
                editorArea.undo();
            }
            case F -> openFindBar();                             // Ctrl+F
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

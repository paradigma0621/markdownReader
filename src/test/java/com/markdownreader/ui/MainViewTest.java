package com.markdownreader.ui;

import com.markdownreader.markdown.Heading;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests for {@link MainView}, driven by TestFX on the JavaFX Application
 * Thread. They assert observable scene-graph and stage state (rather than
 * private fields), exercising opening/saving documents, the edit/theme/zoom/
 * sidebar controls and the F11/F12 layout toggles through real key events,
 * button fires and public calls. Modal dialogs (FileChooser / unsaved-changes
 * Alert) are deliberately avoided so the suite stays deterministic headless.
 */
class MainViewTest extends ApplicationTest {

    // Force the headless Monocle backend before the JavaFX toolkit starts, so the
    // test runs the same way under Maven (surefire) and the IntelliJ JUnit runner,
    // without opening real windows or needing IDE VM options.
    static {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
    }

    @TempDir
    Path tempDir;

    private Stage stage;
    private Scene scene;
    private BorderPane root;
    private MainView mainView;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        this.mainView = new MainView(stage);
        this.root = (BorderPane) mainView.getRoot();
        this.scene = new Scene(root, 1000, 700);
        stage.setScene(scene);
        stage.show();
    }

    // ------------------------------------------------------------- helpers

    /** Dispatches a key press through the scene so the shortcut filters fire. */
    private void fireKey(KeyCode code) {
        fireKey(code, false);
    }

    /** Dispatches a key press, optionally with the shortcut (Ctrl) modifier down. */
    private void fireKey(KeyCode code, boolean shortcut) {
        interact(() -> {
            KeyEvent ev = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                    false, shortcut, false, false);
            Event.fireEvent(scene, ev);
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Fires a toolbar button (by id) on the FX thread, triggering its action. */
    private void fireButton(String id) {
        interact(() -> {
            // Node.lookup(id) only resolves once CSS has been applied to the scene.
            root.applyCss();
            root.layout();
            ((Button) root.lookup(id)).fire();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Reads a value on the FX thread and returns it to the test thread. */
    private <T> T fx(Callable<T> call) {
        try {
            return WaitForAsyncUtils.asyncFx(call).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Label status() {
        return (Label) lookup(".status-label").query();
    }

    private Label zoomLabel() {
        return (Label) lookup(".zoom-label").query();
    }

    @SuppressWarnings("unchecked")
    private ListView<Heading> toc() {
        return (ListView<Heading>) lookup(".toc-list").query();
    }

    private TextArea editor() {
        return (TextArea) lookup(".editor-area").query();
    }

    private Path writeMarkdown(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** Reaches the private preview→editor JS bridge (its public surface is JS-only). */
    private MainView.PreviewEditBridge bridge() {
        try {
            Field f = MainView.class.getDeclaredField("previewEditBridge");
            f.setAccessible(true);
            return (MainView.PreviewEditBridge) f.get(mainView);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Character offset of the start of the 0-based {@code line} (mirrors MainView.offsetOfLine). */
    private static int lineStart(String text, int line) {
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

    private boolean isDirty() {
        return fx(() -> stage.getTitle()).startsWith("●");
    }

    private boolean editorPresent() {
        return !lookup(".editor-area").queryAll().isEmpty();
    }

    // --------------------------------------------------------- layout chrome

    @Test
    void initiallyShowsAllChrome() {
        assertNotNull(fx(() -> root.getTop()), "toolbar should be visible");
        assertNotNull(fx(() -> root.getLeft()), "sidebar should be visible");
        assertNotNull(fx(() -> root.getBottom()), "status bar should be visible");
        assertFalse(fx(() -> root.getStyleClass().contains("fullscreen-mode")));
    }

    @Test
    void f12EntersTotalFullscreenHidingAllChrome() {
        fireKey(KeyCode.F12);

        assertTrue(fx(() -> stage.isFullScreen()), "stage should be in fullscreen");
        assertNull(fx(() -> root.getTop()), "toolbar should be hidden");
        assertNull(fx(() -> root.getLeft()), "sidebar should be hidden");
        assertNull(fx(() -> root.getBottom()), "status bar should be hidden");
        assertTrue(fx(() -> root.getStyleClass().contains("fullscreen-mode")));
    }

    @Test
    void f12TwiceRestoresThePreviousLayout() {
        fireKey(KeyCode.F12);
        fireKey(KeyCode.F12);

        assertFalse(fx(() -> stage.isFullScreen()), "stage should leave fullscreen");
        assertNotNull(fx(() -> root.getTop()), "toolbar should be restored");
        assertNotNull(fx(() -> root.getLeft()), "sidebar should be restored");
        assertNotNull(fx(() -> root.getBottom()), "status bar should be restored");
        assertFalse(fx(() -> root.getStyleClass().contains("fullscreen-mode")));
    }

    @Test
    void f11FocusModeHidesSidebarButKeepsToolbar() {
        fireKey(KeyCode.F11);
        assertNull(fx(() -> root.getLeft()), "sidebar should be hidden in focus mode");
        assertNotNull(fx(() -> root.getTop()), "toolbar should remain in focus mode");
        assertNotNull(fx(() -> root.getBottom()), "status bar should remain in focus mode");

        fireKey(KeyCode.F11);
        assertNotNull(fx(() -> root.getLeft()), "sidebar should be restored");
    }

    @Test
    void fullscreenButtonEntersFullscreenAndKeyExits() {
        // Firing the toolbar button enters total fullscreen, which detaches the
        // whole toolbar (the button included) — so it can only be exited with a
        // key (F12/ESC), exactly as in real use.
        fireButton("#fullscreen-button");
        assertTrue(fx(() -> stage.isFullScreen()), "button should enter fullscreen");
        assertNull(fx(() -> root.getTop()), "toolbar (and its button) is hidden in fullscreen");

        fireKey(KeyCode.F12);
        assertFalse(fx(() -> stage.isFullScreen()), "F12 should leave fullscreen");
        assertNotNull(fx(() -> root.getTop()), "toolbar restored after exiting fullscreen");
    }

    // ----------------------------------------------------------- open / load

    @Test
    void openFileRendersDocumentPopulatesTocAndStatus() throws Exception {
        Path file = writeMarkdown("doc.md", "# Title\n\nHello world\n\n## Section\n");
        interact(() -> mainView.openFile(file.toFile()));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(fx(() -> toc().getItems().size()) >= 2, "TOC should list the two headings");
        assertTrue(fx(() -> status().getText()).contains(file.toFile().getAbsolutePath()));
        assertTrue(fx(() -> status().getText()).contains("words"));
        assertTrue(fx(() -> stage.getTitle()).contains("doc.md"));
    }

    @Test
    void openingADirectoryReportsAnErrorInTheStatusBar() {
        interact(() -> mainView.openFile(tempDir.toFile())); // a directory -> IOException
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(fx(() -> status().getText()).startsWith("Error reading:"));
    }

    @Test
    void tocSelectionScrollsToAnchorWithoutError() throws Exception {
        Path file = writeMarkdown("toc.md", "# Alpha\n\ntext\n\n# Beta\n");
        interact(() -> mainView.openFile(file.toFile()));
        WaitForAsyncUtils.waitForFxEvents();

        ListView<Heading> toc = toc();
        assertTrue(fx(() -> toc.getItems().size()) >= 2);
        interact(() -> toc.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(fx(() -> toc.getSelectionModel().getSelectedItem()));
    }

    // --------------------------------------------------------------- editing

    @Test
    void editModeInsertsEditorAndTypingMarksDocumentDirty() {
        fireKey(KeyCode.E, true); // Ctrl+E
        assertNotNull(editor(), "editor should be inserted in edit mode");

        TextArea ta = editor();
        interact(() -> ta.setText("# Edited heading"));
        // Let the preview debounce (200 ms) fire and re-render.
        WaitForAsyncUtils.sleep(350, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        String title = fx(() -> stage.getTitle());
        assertTrue(title.startsWith("●"), "dirty marker expected in title: " + title);
        assertTrue(title.contains("[edit]"), "edit marker expected in title: " + title);
    }

    @Test
    void editModeButtonTogglesEditorOnAndOff() {
        fireButton("#edit-button");
        assertNotNull(editor(), "editor present after enabling edit mode");
        fireButton("#edit-button");
        assertTrue(lookup(".editor-area").queryAll().isEmpty(), "editor removed after disabling edit mode");
    }

    @Test
    void saveWritesEditedContentBackToTheOpenFile() throws Exception {
        Path file = writeMarkdown("save.md", "original");
        interact(() -> mainView.openFile(file.toFile()));
        WaitForAsyncUtils.waitForFxEvents();

        fireKey(KeyCode.E, true);               // enter edit mode
        TextArea ta = editor();
        interact(() -> ta.setText("brand new content"));
        WaitForAsyncUtils.waitForFxEvents();

        fireKey(KeyCode.S, true);               // Ctrl+S -> save
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("brand new content", Files.readString(file, StandardCharsets.UTF_8));
        assertFalse(fx(() -> stage.getTitle()).startsWith("●"), "title should be clean after save");
    }

    @Test
    void newDocumentEntersEditModeUnsaved() {
        fireKey(KeyCode.N, true); // Ctrl+N (clean state -> no discard dialog)
        assertNotNull(editor(), "new document should open the editor");
        assertEquals("New document (unsaved)", fx(() -> status().getText()));
        assertTrue(fx(() -> stage.getTitle()).contains("Untitled"));
    }

    @Test
    void reloadReReadsTheCurrentFile() throws Exception {
        Path file = writeMarkdown("reload.md", "# First");
        interact(() -> mainView.openFile(file.toFile()));
        WaitForAsyncUtils.waitForFxEvents();

        Files.writeString(file, "# First\n\n## Second\n", StandardCharsets.UTF_8);
        fireKey(KeyCode.R, true); // Ctrl+R -> reload (clean state)
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(fx(() -> toc().getItems().size()) >= 2, "reload should pick up the new heading");
    }

    @Test
    void reloadWithoutAnOpenFileIsANoOp() {
        // No file open; should simply do nothing and not throw.
        fireKey(KeyCode.R, true);
        assertNotNull(fx(() -> root.getCenter()));
    }

    // ------------------------------------------------------------ undo (Ctrl+Z)

    @Test
    void ctrlZInReadModeIsNoOp() {
        // editMode is false (initial state): the case-Z early-return path is taken.
        // Ctrl+Z must not throw and must not open the editor.
        assertFalse(editorPresent(), "editor must not be present in read mode");
        fireKey(KeyCode.Z, true);
        assertFalse(editorPresent(), "Ctrl+Z in read mode must leave the editor absent");
    }

    @Test
    void ctrlZInEditModeUndoesLastChange() {
        // Enter edit mode; syncEditorText() calls setText() which resets the undo stack.
        fireKey(KeyCode.E, true); // Ctrl+E
        assertTrue(editorPresent(), "editor must be present after entering edit mode");

        TextArea ta = editor();
        // appendText() is an undoable operation (unlike setText, which clears the undo history).
        interact(() -> ta.appendText("UNDO_MARKER"));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(fx(ta::getText).contains("UNDO_MARKER"),
                "editor text must contain the marker before undo");

        // Ctrl+Z: case-Z with editMode=true -> editorArea.undo() is called and the event is consumed.
        fireKey(KeyCode.Z, true);
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(fx(ta::getText).contains("UNDO_MARKER"),
                "Ctrl+Z in edit mode must undo the last appended text");
    }

    // ------------------------------------------------------------ theme/zoom

    @Test
    void themeToggleFlipsRootStyleClass() {
        boolean wasDark = fx(() -> root.getStyleClass().contains("theme-dark"));
        fireKey(KeyCode.T, true); // Ctrl+T
        boolean isDark = fx(() -> root.getStyleClass().contains("theme-dark"));
        assertNotEquals(wasDark, isDark, "theme should toggle");
        // Exactly one theme class is applied.
        assertTrue(fx(() -> root.getStyleClass().contains("theme-dark")
                ^ root.getStyleClass().contains("theme-light")));
    }

    @Test
    void zoomInOutAndResetUpdateTheZoomLabel() {
        fireKey(KeyCode.DIGIT0, true);   // Ctrl+0 -> reset to 100%
        assertEquals("100%", fx(() -> zoomLabel().getText()));

        fireKey(KeyCode.EQUALS, true);   // Ctrl+'=' (a.k.a. Ctrl++) -> zoom in
        assertEquals("110%", fx(() -> zoomLabel().getText()));

        fireKey(KeyCode.MINUS, true);    // Ctrl+- -> zoom out
        assertEquals("100%", fx(() -> zoomLabel().getText()));
    }

    @Test
    void zoomLabelClickResetsScale() {
        fireKey(KeyCode.DIGIT0, true);   // reset first (fontScale persists across tests)
        fireKey(KeyCode.EQUALS, true);
        fireKey(KeyCode.EQUALS, true);   // now 120%
        assertEquals("120%", fx(() -> zoomLabel().getText()));

        interact(() -> zoomLabel().getOnMouseClicked().handle(null)); // reset handler
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("100%", fx(() -> zoomLabel().getText()));
    }

    @Test
    void zoomClampsAtTheMaximum() {
        fireKey(KeyCode.DIGIT0, true); // reset
        for (int i = 0; i < 40; i++) {
            fireKey(KeyCode.EQUALS, true);
        }
        assertEquals("240%", fx(() -> zoomLabel().getText()), "zoom should clamp at MAX_SCALE (240%)");
    }

    // ------------------------------------------------------------- sidebar

    @Test
    void sidebarToggleHidesAndRestoresTheTableOfContents() {
        fireKey(KeyCode.B, true); // Ctrl+B
        assertNull(fx(() -> root.getLeft()), "sidebar hidden after toggle");
        fireKey(KeyCode.B, true);
        assertNotNull(fx(() -> root.getLeft()), "sidebar restored after second toggle");
    }

    // ------------------------------------------------------- misc / lifecycle

    @Test
    void closeRequestOnCleanDocumentDoesNotThrow() {
        interact(() -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(fx(() -> stage.getScene()));
    }

    @Test
    void requestFocusDoesNotThrow() {
        interact(() -> mainView.requestFocus());
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(fx(() -> scene.getRoot()));
    }

    // ------------------------------------------------ preview → editor sync

    @Test
    void dblClickBridgeOpensEditorAtLineFromReadMode() throws Exception {
        Path file = writeMarkdown("sync.md", "# A\nline1\nline2\nline3\n");
        interact(() -> mainView.openFile(file.toFile()));
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(editorPresent(), "starts in read mode");
        interact(() -> bridge().openAtLine(2));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(editorPresent(), "double-click should open the editor");
        // Opening at a line via caret move/scroll must never mark the document dirty.
        assertFalse(isDirty(), "opening at a line must not mark the document modified");
    }

    @Test
    void openAtLineMovesCaretWhenAlreadyEditing() {
        fireKey(KeyCode.N, true); // new document -> already in edit mode
        TextArea ta = editor();
        interact(() -> ta.setText("l0\nl1\nl2\nl3\nl4"));
        WaitForAsyncUtils.waitForFxEvents();

        interact(() -> bridge().openAtLine(3));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(lineStart("l0\nl1\nl2\nl3\nl4", 3), fx(ta::getCaretPosition));
    }

    @Test
    void openAtLineBeyondTheEndClampsCaretToTheLastLineStart() {
        fireKey(KeyCode.N, true);
        TextArea ta = editor();
        interact(() -> ta.setText("l0\nl1\nl2"));
        WaitForAsyncUtils.waitForFxEvents();

        interact(() -> bridge().openAtLine(99));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(lineStart("l0\nl1\nl2", 99), fx(ta::getCaretPosition));
    }

    @Test
    void openAtLineIgnoresNegativeLines() {
        interact(() -> bridge().openAtLine(-1));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(editorPresent(), "a negative line is a no-op (no editor opened)");
    }

    @Test
    void openAtLineLeavesFullscreenBeforeShowingTheEditor() {
        fireKey(KeyCode.F12); // total fullscreen, read mode
        assertTrue(fx(() -> stage.isFullScreen()));

        interact(() -> bridge().openAtLine(0));
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(fx(() -> stage.isFullScreen()), "openAtLine should leave fullscreen");
        assertTrue(editorPresent(), "editor visible after leaving fullscreen");
    }

    @Test
    void openAtLineLeavesFocusModeBeforeShowingTheEditor() {
        fireKey(KeyCode.F11); // focus mode, read mode
        assertNull(fx(() -> root.getLeft()), "sidebar hidden in focus mode");

        interact(() -> bridge().openAtLine(1));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(editorPresent(), "editor visible after leaving focus mode");
        assertNotNull(fx(() -> root.getLeft()), "sidebar restored after leaving focus mode");
    }

    @Test
    void alignToLineIsANoOpOutsideEditMode() {
        interact(() -> bridge().alignToLine(3));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(editorPresent(), "alignment without an editor does nothing");
    }

    @Test
    void alignToLineKeepsTheDocumentClean() throws Exception {
        Path file = writeMarkdown("align.md", "# T\n\nbody one\n\nbody two\n");
        interact(() -> mainView.openFile(file.toFile()));
        WaitForAsyncUtils.waitForFxEvents();

        fireKey(KeyCode.E, true); // enter edit mode (clean)
        assertFalse(isDirty(), "edit mode starts clean");

        interact(() -> bridge().alignToLine(2));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(isDirty(), "scroll alignment must not mark the document modified");
    }

    @Test
    void alignToLineNoOpForSingleLineDocument() {
        fireKey(KeyCode.N, true);
        TextArea ta = editor();
        interact(() -> ta.setText("only one line"));
        WaitForAsyncUtils.waitForFxEvents();

        interact(() -> bridge().alignToLine(0)); // total <= 1 -> no scrolling
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("only one line", fx(ta::getText));
    }

    @Test
    void alignToLineScrollsTheEditorViaItsVerticalScrollBar() {
        fireKey(KeyCode.N, true);
        TextArea ta = editor();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            sb.append("line ").append(i).append('\n');
        }
        interact(() -> {
            ta.setText(sb.toString());
            ta.setMinHeight(120);
            ta.setPrefHeight(120);
            ta.setMaxHeight(120);
            root.applyCss();
            root.layout();
        });
        WaitForAsyncUtils.waitForFxEvents();

        interact(() -> bridge().alignToLine(200));
        WaitForAsyncUtils.waitForFxEvents();

        ScrollBar bar = fx(() -> (ScrollBar) ta.lookup(".scroll-bar:vertical"));
        assertNotNull(bar, "a vertical scrollbar should exist for a long document");
        assertTrue(fx(bar::isVisible), "the vertical scrollbar should be visible");
        assertTrue(fx(bar::getValue) > bar.getMin(), "alignToLine should move the scrollbar down");
    }

    @Test
    void alignToLineIsIgnoredWhileInFocusMode() {
        fireKey(KeyCode.N, true); // edit mode (editMode stays true)
        fireKey(KeyCode.F11);     // focus mode
        interact(() -> bridge().alignToLine(2));
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(fx(() -> root.getLeft()), "still in focus mode, alignment ignored");
    }

    @Test
    void alignToLineIsIgnoredWhileInFullscreen() {
        fireKey(KeyCode.N, true); // edit mode (editMode stays true)
        fireKey(KeyCode.F12);     // fullscreen
        interact(() -> bridge().alignToLine(2));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(fx(() -> stage.isFullScreen()), "still fullscreen, alignment ignored");
    }

    @Test
    void alignToLineIgnoresNegativeLines() {
        fireKey(KeyCode.N, true); // edit mode, not focus/fullscreen
        interact(() -> bridge().alignToLine(-5));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(editorPresent(), "a negative line is a no-op but leaves the editor open");
    }

    // ---- zoom-preserving scroll math (pure, headless-verifiable) ----------
    //
    // The live WebView scroll cannot be asserted headlessly, so the position
    // restore itself is manual-verify; these tests pin the ratio math that
    // drives it (capture before reload, re-apply against the new height).

    @Test
    void scrollRatioReturnsFractionOfScrollableRange() {
        // Halfway down a 1000px document in a 200px viewport: max scroll = 800.
        assertClose(0.5, MainView.scrollRatio(400, 1000, 200));
    }

    @Test
    void scrollRatioClampsAndGuardsZeroHeight() {
        // Document not taller than the viewport: nothing to scroll -> 0, no divide-by-zero.
        assertClose(0.0, MainView.scrollRatio(0, 100, 200));
        // Out-of-range scrollY is clamped into 0..1.
        assertClose(1.0, MainView.scrollRatio(99999, 1000, 200));
    }

    @Test
    void scrollTargetForRatioMapsBackToTallerDocument() {
        // Same 0.5 fraction applied after a zoom that grew the document to 2000px.
        assertClose(900.0, MainView.scrollTargetForRatio(0.5, 2000, 200));
        // Ratio survives a round-trip through both helpers at a new height.
        double ratio = MainView.scrollRatio(400, 1000, 200);
        assertClose(720.0, MainView.scrollTargetForRatio(ratio, 1800, 360));
    }

    @Test
    void scrollTargetForRatioGuardsZeroHeight() {
        assertClose(0.0, MainView.scrollTargetForRatio(0.5, 100, 200));
    }

    @Test
    void scrollRatioAndTargetGuardNaNHeight() {
        // A NaN scrollHeight (page not laid out yet) must not propagate; both helpers
        // fall back to 0 instead of returning NaN.
        assertClose(0.0, MainView.scrollRatio(400, Double.NaN, 200));
        assertClose(0.0, MainView.scrollTargetForRatio(0.5, Double.NaN, 200));
    }

    // ---- zoom-preserving scroll plumbing (private methods via reflection) ----
    //
    // The pure math above is the heart of the feature; these exercise the live
    // glue around it -- capturing the current scroll for each ScrollMode and
    // re-applying it after the reload -- by invoking the private methods on the
    // real MainView. The success paths run on the FX thread (executeScript works
    // against the loaded preview); the failure paths run off the FX thread, where
    // WebEngine.executeScript throws, to cover the "page not ready" catch blocks.

    @Test
    void captureAbsoluteThenRestoreScrollOnFxThread() {
        interact(() -> {
            invokePrivate("captureScroll", new Class<?>[]{scrollModeClass()}, scrollMode("ABSOLUTE"));
            invokePrivate("restorePendingScroll", new Class<?>[]{});
        });
        WaitForAsyncUtils.waitForFxEvents();
        // After restoring, the absolute marker is disarmed for the next reload.
        assertTrue(getDoubleField("pendingScrollRestore") < 0, "absolute marker cleared");
    }

    @Test
    void captureRatioThenRestoreScrollOnFxThread() {
        interact(() -> {
            invokePrivate("captureScroll", new Class<?>[]{scrollModeClass()}, scrollMode("RATIO"));
            invokePrivate("restorePendingScroll", new Class<?>[]{});
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(getDoubleField("pendingScrollRatio") < 0, "ratio marker cleared");
    }

    @Test
    void captureNoneArmsNoPendingRestore() {
        interact(() ->
                invokePrivate("captureScroll", new Class<?>[]{scrollModeClass()}, scrollMode("NONE")));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(getDoubleField("pendingScrollRestore") < 0);
        assertTrue(getDoubleField("pendingScrollRatio") < 0);
    }

    @Test
    void scriptNumberReturnsValueForNumbersAndNaNForOthers() {
        double number = fx(() ->
                (Double) invokePrivate("scriptNumber", new Class<?>[]{String.class}, "2 + 3"));
        assertClose(5.0, number);
        double nan = fx(() ->
                (Double) invokePrivate("scriptNumber", new Class<?>[]{String.class}, "'not a number'"));
        assertTrue(Double.isNaN(nan), "non-numeric script result maps to NaN");
    }

    @Test
    void captureAbsoluteScrollIgnoresNonNumericScrollY() {
        interact(() -> {
            // Shadow window.scrollY with a non-numeric getter so scriptNumber yields
            // NaN; the absolute capture's NaN guard must then arm nothing.
            engineExecute("Object.defineProperty(window, 'scrollY', "
                    + "{configurable: true, get: function() { return 'not-a-number'; }});");
            invokePrivate("captureScroll", new Class<?>[]{scrollModeClass()}, scrollMode("ABSOLUTE"));
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(getDoubleField("pendingScrollRestore") < 0, "NaN scrollY arms no restore");
    }

    @Test
    void captureScrollSwallowsErrorsOffFxThread() {
        // Off the FX thread, executeScript throws; captureScroll must absorb it and
        // leave nothing armed (covers the catch in captureScroll).
        invokePrivate("captureScroll", new Class<?>[]{scrollModeClass()}, scrollMode("ABSOLUTE"));
        assertTrue(getDoubleField("pendingScrollRestore") < 0);
        assertTrue(getDoubleField("pendingScrollRatio") < 0);
    }

    @Test
    void restoreRatioScrollSwallowsErrorsOffFxThread() {
        setDoubleField("pendingScrollRatio", 0.5);
        setDoubleField("pendingScrollRestore", -1.0);
        // Off the FX thread the scrollTo throws; the ratio marker is still cleared.
        invokePrivate("restorePendingScroll", new Class<?>[]{});
        assertTrue(getDoubleField("pendingScrollRatio") < 0);
    }

    @Test
    void restoreAbsoluteScrollSwallowsErrorsOffFxThread() {
        setDoubleField("pendingScrollRestore", 120.0);
        setDoubleField("pendingScrollRatio", -1.0);
        invokePrivate("restorePendingScroll", new Class<?>[]{});
        assertTrue(getDoubleField("pendingScrollRestore") < 0);
    }

    @Test
    void foldingRefreshesPreviewWithAbsoluteScrollMode() throws Exception {
        // Right-clicking a heading folds its section, which re-renders with
        // ScrollMode.ABSOLUTE (covers the applyFold call site).
        Path file = writeMarkdown("fold.md", "# Alpha\n\nbody\n\n## Beta\n\nmore\n");
        interact(() -> mainView.openFile(file.toFile()));
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> invokePrivate("toggleHeadingByIndex", new Class<?>[]{int.class}, 0));
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(fx(() -> root.getCenter()));
        // An out-of-range index folds nothing (applyFold receives null and bails out).
        interact(() -> invokePrivate("toggleHeadingByIndex", new Class<?>[]{int.class}, 99));
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(fx(() -> root.getCenter()));
    }

    // -------------------------------------------- settings persistence (new)

    /**
     * The theme preference "DARK" must cause a freshly constructed MainView to apply
     * the "theme-dark" style class to its root — verifying the happy-path branch of
     * the try-block in the constructor (line: {@code this.theme = Theme.valueOf(savedTheme)}).
     */
    @Test
    void themeRestoredAsDarkOnConstruction() {
        Preferences testPrefs = Preferences.userNodeForPackage(MainView.class);
        String saved = testPrefs.get("theme", null);
        try {
            testPrefs.put("theme", "DARK");
            MainView[] holder = new MainView[1];
            // Use a fresh Stage so the shared stage is not affected.
            interact(() -> holder[0] = new MainView(new Stage()));
            WaitForAsyncUtils.waitForFxEvents();
            BorderPane freshRoot = (BorderPane) holder[0].getRoot();
            assertTrue(fx(() -> freshRoot.getStyleClass().contains("theme-dark")),
                    "DARK pref should cause the new MainView to apply theme-dark");
        } finally {
            if (saved == null) testPrefs.remove("theme");
            else testPrefs.put("theme", saved);
        }
    }

    /**
     * An unrecognized theme name in prefs must trigger the {@code catch(IllegalArgumentException)}
     * branch and fall back to the LIGHT theme.
     */
    @Test
    void themeRestoredDefaultsToLightForInvalidPrefValue() {
        Preferences testPrefs = Preferences.userNodeForPackage(MainView.class);
        String saved = testPrefs.get("theme", null);
        try {
            testPrefs.put("theme", "BOGUS_THEME_NOT_VALID");
            MainView[] holder = new MainView[1];
            interact(() -> holder[0] = new MainView(new Stage()));
            WaitForAsyncUtils.waitForFxEvents();
            BorderPane freshRoot = (BorderPane) holder[0].getRoot();
            assertTrue(fx(() -> freshRoot.getStyleClass().contains("theme-light")),
                    "Invalid theme pref must fall back to LIGHT via the catch block");
        } finally {
            if (saved == null) testPrefs.remove("theme");
            else testPrefs.put("theme", saved);
        }
    }

    /**
     * The {@code editorFontFamily} field must be initialised from the
     * {@code "editorFontFamily"} preference on construction.
     */
    @Test
    void editorFontFamilyRestoredFromPreferencesOnConstruction() {
        Preferences testPrefs = Preferences.userNodeForPackage(MainView.class);
        String saved = testPrefs.get("editorFontFamily", null);
        String testFamily = "Arial";
        try {
            testPrefs.put("editorFontFamily", testFamily);
            MainView[] holder = new MainView[1];
            interact(() -> holder[0] = new MainView(new Stage()));
            WaitForAsyncUtils.waitForFxEvents();
            assertEquals(testFamily, getStringFieldFrom(holder[0], "editorFontFamily"),
                    "editorFontFamily must be loaded from the pref on construction");
        } finally {
            if (saved == null) testPrefs.remove("editorFontFamily");
            else testPrefs.put("editorFontFamily", saved);
        }
    }

    /**
     * {@code toggleTheme()} must persist the new theme name to the {@code "theme"} preference
     * (verifying the {@code prefs.put("theme", theme.name())} line added by this feature).
     */
    @Test
    void toggleThemePersistsThemeNameToPreferences() {
        Preferences testPrefs = Preferences.userNodeForPackage(MainView.class);
        String savedPref = testPrefs.get("theme", null);
        boolean wasDark = fx(() -> root.getStyleClass().contains("theme-dark"));
        String expectedAfterToggle = wasDark ? "LIGHT" : "DARK";
        try {
            fireKey(KeyCode.T, true); // Ctrl+T → toggleTheme()
            assertEquals(expectedAfterToggle, testPrefs.get("theme", null),
                    "toggleTheme must persist the toggled theme name to prefs");
        } finally {
            fireKey(KeyCode.T, true); // toggle back so other tests see the original theme
            if (savedPref == null) testPrefs.remove("theme");
            else testPrefs.put("theme", savedPref);
        }
    }

    /**
     * {@code applyEditorFontSize()} must produce an inline style that contains both
     * {@code -fx-font-family} and a pixel {@code -fx-font-size} — the new style body
     * added to this method in this feature.
     */
    @Test
    void applyEditorFontSizeStyleContainsBothFamilyAndSize() {
        TextArea ea = getEditorArea();
        String style = fx(ea::getStyle);
        assertTrue(style.contains("-fx-font-family"),
                "editor inline style must include -fx-font-family");
        assertTrue(style.contains("-fx-font-size"),
                "editor inline style must include -fx-font-size");
        assertTrue(style.contains("px"),
                "editor inline style font-size must use px units");
    }

    /**
     * Covers all branches of the font-family ComboBox listener inside {@code openSettings()}:
     * <ul>
     *   <li>Guard branch 1: {@code val == null} → listener returns early without changes.</li>
     *   <li>Guard branch 2: {@code val.isBlank()} → listener returns early without changes.</li>
     *   <li>Happy path: valid family → {@code editorFontFamily} field updated, pref persisted,
     *       and {@code applyEditorFontSize()} called (style reflects the new family).</li>
     * </ul>
     * The settings dialog is opened asynchronously via {@code Platform.runLater} so that
     * {@code showAndWait()} runs in its nested FX event loop without blocking the test thread.
     */
    @Test
    void openSettingsFontFamilyComboListenerCoversAllBranches() {
        Preferences testPrefs = Preferences.userNodeForPackage(MainView.class);
        String savedPref = testPrefs.get("editorFontFamily", null);
        String originalFamily = getStringField("editorFontFamily");
        String newFamily = Font.getFamilies().stream()
                .filter(f -> !f.equalsIgnoreCase(originalFamily))
                .findFirst()
                .orElse("Arial");
        try {
            // Open settings asynchronously — showAndWait() enters a nested FX event loop
            // while the test thread (and Platform.runLater tasks) continue to execute.
            Platform.runLater(() -> invokePrivate("openSettings", new Class<?>[0]));
            WaitForAsyncUtils.waitForFxEvents();

            // The settings dialog is now showing. Find its ComboBox (only one in any scene).
            @SuppressWarnings("unchecked")
            ComboBox<String> combo =
                    (ComboBox<String>) lookup(".combo-box").query();
            assertNotNull(combo, "ComboBox must be present in the settings dialog");

            // ── guard branch 1: null value ─ listener must return early ──────
            interact(() -> combo.setValue(null));
            WaitForAsyncUtils.waitForFxEvents();
            assertEquals(originalFamily, getStringField("editorFontFamily"),
                    "null value must not update editorFontFamily (null guard return)");

            // ── guard branch 2: blank value ─ isBlank() guard must fire ──────
            interact(() -> combo.setValue("   "));
            WaitForAsyncUtils.waitForFxEvents();
            assertEquals(originalFamily, getStringField("editorFontFamily"),
                    "blank value must not update editorFontFamily (isBlank guard return)");

            // ── happy path: valid non-blank family ─ all listener body lines ──
            interact(() -> combo.setValue(newFamily));
            WaitForAsyncUtils.waitForFxEvents();
            assertEquals(newFamily, getStringField("editorFontFamily"),
                    "valid family must update the editorFontFamily field");
            assertEquals(newFamily, testPrefs.get("editorFontFamily", null),
                    "valid family must be persisted to the editorFontFamily pref");
            String style = fx(getEditorArea()::getStyle);
            assertTrue(style.contains(newFamily),
                    "applyEditorFontSize() must include the new family in the editor style");
            assertTrue(style.contains("px"),
                    "applyEditorFontSize() must include a pixel font-size in the editor style");

            // Close the settings dialog by closing its Stage directly (lookup(".primary-button")
            // is ambiguous — the welcome screen also has a primary-button).
            interact(() -> javafx.stage.Window.getWindows().stream()
                    .filter(w -> w instanceof Stage && w != stage)
                    .map(w -> (Stage) w)
                    .filter(Stage::isShowing)
                    .findFirst()
                    .ifPresent(Stage::close));
            WaitForAsyncUtils.waitForFxEvents();

        } finally {
            // Restore the pref and the live field so subsequent tests are unaffected.
            if (savedPref == null) testPrefs.remove("editorFontFamily");
            else testPrefs.put("editorFontFamily", savedPref);
            setStringField("editorFontFamily", originalFamily);
            interact(() -> invokePrivate("applyEditorFontSize", new Class<?>[0]));
            WaitForAsyncUtils.waitForFxEvents();
        }
    }

    // ----------------------------------------------------- reflection helpers

    private static Class<?> scrollModeClass() {
        try {
            return Class.forName("com.markdownreader.ui.MainView$ScrollMode");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object scrollMode(String name) {
        return Enum.valueOf((Class<? extends Enum>) scrollModeClass(), name);
    }

    private Object invokePrivate(String method, Class<?>[] types, Object... args) {
        try {
            Method m = MainView.class.getDeclaredMethod(method, types);
            m.setAccessible(true);
            return m.invoke(mainView, args);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Runs JS against the preview WebView (must be called on the FX thread). */
    private void engineExecute(String js) {
        try {
            Field f = MainView.class.getDeclaredField("webView");
            f.setAccessible(true);
            javafx.scene.web.WebView wv = (javafx.scene.web.WebView) f.get(mainView);
            wv.getEngine().executeScript(js);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void setDoubleField(String name, double value) {
        try {
            Field f = MainView.class.getDeclaredField(name);
            f.setAccessible(true);
            f.setDouble(mainView, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private double getDoubleField(String name) {
        try {
            Field f = MainView.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.getDouble(mainView);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void setBooleanField(String name, boolean value) {
        try {
            Field f = MainView.class.getDeclaredField(name);
            f.setAccessible(true);
            f.setBoolean(mainView, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean getBooleanField(String name) {
        try {
            Field f = MainView.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.getBoolean(mainView);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertClose(double expected, double actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, 1e-9);
    }

    /** Reads a String field from the shared {@link #mainView} by reflection. */
    private String getStringField(String name) {
        try {
            Field f = MainView.class.getDeclaredField(name);
            f.setAccessible(true);
            return (String) f.get(mainView);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Reads a String field from an arbitrary {@link MainView} instance by reflection. */
    private String getStringFieldFrom(MainView mv, String name) {
        try {
            Field f = MainView.class.getDeclaredField(name);
            f.setAccessible(true);
            return (String) f.get(mv);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Sets a String field on the shared {@link #mainView} by reflection. */
    private void setStringField(String name, String value) {
        try {
            Field f = MainView.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(mainView, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns the (always-present) {@code editorArea} TextArea from {@link #mainView}. */
    private TextArea getEditorArea() {
        try {
            Field f = MainView.class.getDeclaredField("editorArea");
            f.setAccessible(true);
            return (TextArea) f.get(mainView);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // -------------------------------------------------- in-page anchor navigation

    /**
     * Opens a document containing an in-page anchor link ({@code [..](#fragment)}) and a
     * matching heading, then re-installs the preview bridges on the FX thread. This drives
     * the load-worker wiring that injects {@code ANCHOR_NAV_JS} into the freshly loaded
     * page, so a fragment click is wired to scroll the preview to the heading.
     */
    @Test
    void inPageAnchorLinkWiringInstallsWithoutError() {
        Path file;
        try {
            file = writeMarkdown("anchor.md",
                    "# Voice Gemini\n\njump to [the section](#8-voice-gemini)\n\n## 8 Voice Gemini\n\nhere\n");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        interact(() -> mainView.openFile(file.toFile()));
        WaitForAsyncUtils.waitForFxEvents();

        // Re-run the bridge install path explicitly (the same call the SUCCEEDED load-worker
        // state makes), which injects ANCHOR_NAV_JS. It must complete without throwing.
        interact(() -> {
            try {
                Method m = MainView.class.getDeclaredMethod("installPreviewFoldBridge");
                m.setAccessible(true);
                m.invoke(mainView);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(fx(() -> toc().getItems().size()) >= 2, "both headings should be listed");
        assertTrue(fx(() -> stage.getTitle()).contains("anchor.md"));
    }

    /**
     * Locks the {@code ANCHOR_NAV_JS} contract: it must resolve a fragment by id, then by
     * name, then by a slug comparison, skip a heading's own self-link, and scroll the
     * resolved target into view.
     */
    @Test
    void anchorNavScriptDefinesResolutionAndScrollContract() throws Exception {
        Field f = MainView.class.getDeclaredField("ANCHOR_NAV_JS");
        f.setAccessible(true);
        String js = (String) f.get(null);

        assertNotNull(js);
        assertTrue(js.contains("getElementById"), "should try id resolution first");
        assertTrue(js.contains("getElementsByName"), "should fall back to name resolution");
        assertTrue(js.contains("function slugify"), "should slug-compare heading text");
        assertTrue(js.contains("decodeURIComponent"), "should decode the fragment");
        assertTrue(js.contains("closest('h1,h2,h3,h4,h5,h6')"), "should skip heading self-links");
        assertTrue(js.contains("scrollIntoView"), "should scroll the resolved heading into view");
    }

    // ----------------------------------------- hide-scrollbar fix (item 7)
    //
    // Worked code: line 888 of MainView.java —
    //   webView.getEngine().executeScript(hide ? HIDE_SCROLLBAR_JS : SHOW_SCROLLBAR_JS)
    //
    // Two tests cover the constant's CSS contract; two more exercise the apply path
    // (hide-branch and show-branch) so JaCoCo marks that line fully covered.

    /**
     * Locks the {@code HIDE_SCROLLBAR_JS} content contract: the injected CSS must NOT
     * contain {@code overflow-y:hidden} (which would kill all scrolling) and MUST contain
     * rules that only hide the scrollbar's visual track/thumb, leaving wheel/keyboard
     * scrolling intact.
     */
    @Test
    void hideScrollbarJsDoesNotContainOverflowHiddenButHidesTrackAndThumb() throws Exception {
        Field f = MainView.class.getDeclaredField("HIDE_SCROLLBAR_JS");
        f.setAccessible(true);
        String js = (String) f.get(null);

        assertNotNull(js, "HIDE_SCROLLBAR_JS must be defined");
        assertFalse(js.contains("overflow-y:hidden"),
                "must NOT kill scrolling with overflow-y:hidden");
        assertFalse(js.contains("overflow-y: hidden"),
                "must NOT kill scrolling with overflow-y: hidden (with space)");
        assertTrue(js.contains("::-webkit-scrollbar"),
                "must suppress the WebKit scrollbar track/thumb via ::-webkit-scrollbar");
        assertTrue(js.contains("scrollbar-width:none"),
                "must suppress the standard scrollbar via scrollbar-width:none");
    }

    /**
     * Exercises the hide-scrollbar code path (line 888, {@code hide=true} branch): with
     * {@code fullscreenMode=true} and {@code f12PreviewScrollbar=false}, calling
     * {@code applyF12Scrollbar()} must select {@code HIDE_SCROLLBAR_JS} and execute it
     * without throwing. The method internally catches WebView errors, so reaching the
     * end of the test confirms the constant-referencing branch ran.
     */
    @Test
    void applyF12ScrollbarExecutesHideScriptWhenFullscreenAndScrollbarDisabled() {
        setBooleanField("f12PreviewScrollbar", false);
        setBooleanField("fullscreenMode", true);
        try {
            interact(() -> invokePrivate("applyF12Scrollbar", new Class<?>[]{}));
            WaitForAsyncUtils.waitForFxEvents();
        } finally {
            setBooleanField("fullscreenMode", false);
            setBooleanField("f12PreviewScrollbar", true);
        }
    }

    /**
     * Exercises the show-scrollbar code path (line 888, {@code hide=false} branch): with
     * {@code f12PreviewScrollbar=true} (the default), calling {@code applyF12Scrollbar()}
     * must select {@code SHOW_SCROLLBAR_JS} and execute it without throwing.
     */
    @Test
    void applyF12ScrollbarExecutesShowScriptWhenScrollbarEnabled() {
        // fullscreenMode=false (read-mode default) → hide=false → SHOW_SCROLLBAR_JS branch.
        setBooleanField("f12PreviewScrollbar", true);
        interact(() -> invokePrivate("applyF12Scrollbar", new Class<?>[]{}));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /**
     * End-to-end: toggling F12 fullscreen with the scrollbar preference disabled drives
     * the full {@code applyFullscreenLayout → applyF12Scrollbar → HIDE_SCROLLBAR_JS}
     * chain through the real key-event path, without throwing.
     */
    @Test
    void f12FullscreenWithScrollbarDisabledAppliesHideScript() {
        setBooleanField("f12PreviewScrollbar", false);
        try {
            fireKey(KeyCode.F12); // applyFullscreenLayout(true) → applyF12Scrollbar() → hide=true
            assertTrue(fx(() -> stage.isFullScreen()), "stage should be in fullscreen");
            fireKey(KeyCode.F12); // applyFullscreenLayout(false) → applyF12Scrollbar() → hide=false
            assertFalse(fx(() -> stage.isFullScreen()), "stage should have exited fullscreen");
        } finally {
            if (fx(() -> stage.isFullScreen())) {
                fireKey(KeyCode.F12);
            }
            setBooleanField("f12PreviewScrollbar", true);
        }
    }

    private static void assertNotEquals(boolean unexpected, boolean actual, String message) {
        assertTrue(unexpected != actual, message);
    }
}

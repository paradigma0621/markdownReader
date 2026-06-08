package com.markdownreader.ui;

import com.markdownreader.markdown.Heading;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

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

    private static void assertClose(double expected, double actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, 1e-9);
    }

    private static void assertNotEquals(boolean unexpected, boolean actual, String message) {
        assertTrue(unexpected != actual, message);
    }
}

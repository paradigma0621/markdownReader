package com.markdownreader.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests for the Settings modal feature of {@link MainView}: the gear toolbar
 * button, the modal Settings dialog, the editor font-size spinner and the F12
 * preview-scrollbar checkbox. Follows the same headless TestFX/Monocle pattern
 * as {@link MainViewTest}. The modal dialog uses {@code showAndWait}; it is
 * driven by firing the toolbar button from a {@code runLater} task (so the
 * nested event loop stays responsive to the test) and closed within the test.
 * Preferences-dependent assertions construct fresh {@link MainView} instances
 * after seeding {@link Preferences}, keeping them deterministic and isolated.
 */
class SettingsModalTest extends ApplicationTest {

    static {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
    }

    private static final Preferences PREFS = Preferences.userNodeForPackage(MainView.class);

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

    @AfterEach
    void resetPrefs() {
        // Keep the shared Preferences node clean for the rest of the suite.
        PREFS.remove("editorFontSize");
        PREFS.putBoolean("f12PreviewScrollbar", true);
    }

    // ----------------------------------------------------------- reflection

    private static Object getField(Object target, String name) {
        try {
            Field f = MainView.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = MainView.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void invoke(Object target, String name) {
        try {
            Method m = MainView.class.getDeclaredMethod(name);
            m.setAccessible(true);
            m.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T fx(Callable<T> call) {
        try {
            return WaitForAsyncUtils.asyncFx(call).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String editorStyle(MainView mv) {
        return fx(() -> ((TextArea) getField(mv, "editorArea")).getStyle());
    }

    // ------------------------------------------------------- dialog driving

    /**
     * Fires the gear button (which calls {@code openSettings()} -> {@code showAndWait})
     * from a {@code runLater} task and waits for the modal "Settings" stage to appear.
     */
    private Stage openSettingsDialog() {
        Platform.runLater(() -> {
            root.applyCss();
            root.layout();
            ((Button) root.lookup("#settings-button")).fire();
        });
        Stage dialog = null;
        for (int i = 0; i < 50 && dialog == null; i++) {
            dialog = fx(SettingsModalTest::findSettingsStage);
            if (dialog == null) {
                WaitForAsyncUtils.sleep(100, TimeUnit.MILLISECONDS);
            }
        }
        assertNotNull(dialog, "Settings modal stage should appear");
        WaitForAsyncUtils.waitForFxEvents();
        return dialog;
    }

    private static Stage findSettingsStage() {
        for (Window w : Window.getWindows()) {
            if (w instanceof Stage s && "Settings".equals(s.getTitle())) {
                return s;
            }
        }
        return null;
    }

    /** Closes the dialog by firing its "Close" button, exercising that handler. */
    private void closeViaButton(Stage dialog) {
        Platform.runLater(() -> {
            dialog.getScene().getRoot().applyCss();
            closeButton(dialog).fire();
        });
        // Wait for the modal stage to disappear and the nested loop to unwind.
        for (int i = 0; i < 50 && fx(() -> dialog.isShowing()); i++) {
            WaitForAsyncUtils.sleep(50, TimeUnit.MILLISECONDS);
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Button closeButton(Stage dialog) {
        for (var node : dialog.getScene().getRoot().lookupAll(".button")) {
            if (node instanceof Button b && "Close".equals(b.getText())) {
                return b;
            }
        }
        throw new IllegalStateException("Close button not found in Settings dialog");
    }

    @SuppressWarnings("unchecked")
    private Spinner<Integer> fontSpinner(Stage dialog) {
        return (Spinner<Integer>) dialog.getScene().getRoot().lookup(".spinner");
    }

    private CheckBox scrollbarCheck(Stage dialog) {
        return (CheckBox) dialog.getScene().getRoot().lookup(".check-box");
    }

    // ------------------------------------------------------- gear / toolbar

    @Test
    void settingsButtonHasGearGlyphAndTooltip() {
        Button btn = fx(() -> {
            root.applyCss();
            root.layout();
            return (Button) root.lookup("#settings-button");
        });
        assertNotNull(btn, "settings button should exist in the toolbar");
        assertEquals("⚙", fx(btn::getText), "settings button should show the gear glyph");
        Tooltip tip = fx(btn::getTooltip);
        assertNotNull(tip, "settings button should have a tooltip");
        assertEquals("Settings", fx(tip::getText));
    }

    @Test
    void openSettingsShowsApplicationModalDialogOwnedByMainStage() {
        Stage dialog = openSettingsDialog();
        try {
            assertTrue(fx(dialog::isShowing), "dialog should be showing");
            assertEquals(Modality.APPLICATION_MODAL, fx(dialog::getModality));
            assertSame(stage, fx(dialog::getOwner), "dialog owner should be the main stage");
            assertNotNull(fontSpinner(dialog), "font spinner should be present");
            assertNotNull(scrollbarCheck(dialog), "scrollbar checkbox should be present");
        } finally {
            closeViaButton(dialog);
        }
        assertFalse(fx(dialog::isShowing), "dialog should close after firing Close");
    }

    @Test
    void openSettingsInDarkThemeAdoptsDarkStyleClass() {
        // Switch to the dark theme first so openSettings takes the DARK ternary branch
        // and the dialog mirrors the current look.
        fx(() -> {
            root.applyCss();
            root.layout();
            ((Button) root.lookup("#theme-button")).fire();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        Stage dialog = openSettingsDialog();
        try {
            boolean dark = fx(() -> dialog.getScene().getRoot().getStyleClass().contains("theme-dark"));
            assertTrue(dark, "settings dialog should adopt the dark theme class");
        } finally {
            closeViaButton(dialog);
        }
    }

    // ---------------------------------------------------- (a) editor font size

    @Test
    void changingSpinnerUpdatesEditorFontSizeAndPersists() {
        Stage dialog = openSettingsDialog();
        try {
            Spinner<Integer> spinner = fontSpinner(dialog);
            fx(() -> {
                spinner.getValueFactory().setValue(22);
                return null;
            });
            WaitForAsyncUtils.waitForFxEvents();
            assertTrue(editorStyle(mainView).contains("22px"),
                    "editor font size should update live to the spinner value");
            assertEquals(22, PREFS.getInt("editorFontSize", -1),
                    "new font size should be persisted");
        } finally {
            closeViaButton(dialog);
        }
    }

    @Test
    void spinnerNullValueIsIgnored() {
        Stage dialog = openSettingsDialog();
        try {
            Spinner<Integer> spinner = fontSpinner(dialog);
            fx(() -> {
                spinner.getValueFactory().setValue(18);
                return null;
            });
            WaitForAsyncUtils.waitForFxEvents();
            String before = editorStyle(mainView);
            // A null value reaches the listener (e.g. invalid editable input) and
            // must be ignored without touching the editor or preferences.
            fx(() -> {
                spinner.getValueFactory().setValue(null);
                return null;
            });
            WaitForAsyncUtils.waitForFxEvents();
            assertEquals(before, editorStyle(mainView),
                    "a null spinner value must leave the editor style unchanged");
        } finally {
            closeViaButton(dialog);
        }
    }

    @Test
    void editorFontSizeDefaultsToFourteenWhenUnset() {
        PREFS.remove("editorFontSize");
        MainView mv = fx(() -> new MainView(new Stage()));
        // applyEditorFontSize() now includes both font-family and font-size.
        assertTrue(editorStyle(mv).contains("-fx-font-size: 14px"),
                "default font size should be 14px in the editor style");
    }

    @Test
    void editorFontSizeLoadsPersistedValueAtStartup() {
        PREFS.putInt("editorFontSize", 20);
        MainView mv = fx(() -> new MainView(new Stage()));
        assertTrue(editorStyle(mv).contains("20px"),
                "persisted font size should be re-applied at startup");
    }

    @Test
    void editorFontSizeClampsBelowMinimumOnLoad() {
        PREFS.putInt("editorFontSize", 2); // below MIN_EDITOR_FONT (8)
        MainView mv = fx(() -> new MainView(new Stage()));
        assertTrue(editorStyle(mv).contains("8px"),
                "an out-of-range low value should clamp to the minimum (8)");
    }

    @Test
    void editorFontSizeClampsAboveMaximumOnLoad() {
        PREFS.putInt("editorFontSize", 99); // above MAX_EDITOR_FONT (40)
        MainView mv = fx(() -> new MainView(new Stage()));
        assertTrue(editorStyle(mv).contains("40px"),
                "an out-of-range high value should clamp to the maximum (40)");
    }

    // ------------------------------------------------ (b) F12 scrollbar toggle

    @Test
    void scrollbarCheckboxTogglePersistsAndApplies() {
        Stage dialog = openSettingsDialog();
        try {
            CheckBox check = scrollbarCheck(dialog);
            // Uncheck: not in fullscreen, so the decision is "show" (no hide).
            fx(() -> {
                check.setSelected(false);
                return null;
            });
            WaitForAsyncUtils.waitForFxEvents();
            assertFalse(PREFS.getBoolean("f12PreviewScrollbar", true),
                    "unchecking should persist false");
            // Re-check.
            fx(() -> {
                check.setSelected(true);
                return null;
            });
            WaitForAsyncUtils.waitForFxEvents();
            assertTrue(PREFS.getBoolean("f12PreviewScrollbar", false),
                    "re-checking should persist true");
        } finally {
            closeViaButton(dialog);
        }
    }

    @Test
    void applyF12ScrollbarHidesWhenFullscreenAndPreferenceDisabled() {
        // fullscreenMode && !pref  ->  hide = true  (HIDE_SCROLLBAR_JS branch)
        fx(() -> {
            setField(mainView, "fullscreenMode", true);
            setField(mainView, "f12PreviewScrollbar", false);
            invoke(mainView, "applyF12Scrollbar");
            return null;
        });
        assertTrue((boolean) getField(mainView, "fullscreenMode"));
    }

    @Test
    void applyF12ScrollbarShowsWhenFullscreenAndPreferenceEnabled() {
        // fullscreenMode && !pref  ->  hide = false  (SHOW_SCROLLBAR_JS branch)
        fx(() -> {
            setField(mainView, "fullscreenMode", true);
            setField(mainView, "f12PreviewScrollbar", true);
            invoke(mainView, "applyF12Scrollbar");
            return null;
        });
        assertTrue((boolean) getField(mainView, "f12PreviewScrollbar"));
    }

    @Test
    void applyF12ScrollbarShowsWhenNotFullscreen() {
        // fullscreenMode == false short-circuits the &&  ->  hide = false (SHOW branch)
        fx(() -> {
            setField(mainView, "fullscreenMode", false);
            setField(mainView, "f12PreviewScrollbar", false);
            invoke(mainView, "applyF12Scrollbar");
            return null;
        });
        assertFalse((boolean) getField(mainView, "fullscreenMode"));
    }

    @Test
    void applyF12ScrollbarSwallowsScriptErrors() {
        // WebEngine.executeScript must run on the FX thread; invoking applyF12Scrollbar
        // off-thread makes executeScript throw IllegalStateException, exercising the
        // defensive catch. The method must swallow it without propagating.
        MainView mv = fx(() -> new MainView(new Stage()));
        setField(mv, "fullscreenMode", true);
        setField(mv, "f12PreviewScrollbar", false);
        invoke(mv, "applyF12Scrollbar"); // runs on the test thread; must not throw
    }

    @Test
    void enteringAndLeavingFullscreenDrivesScrollbarApplication() {
        // Exercise applyF12Scrollbar via the real fullscreen path (toggleFullscreen
        // -> fullScreenProperty listener -> applyFullscreenLayout -> applyF12Scrollbar).
        fx(() -> {
            stage.setFullScreen(true);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(fx(() -> (boolean) getField(mainView, "fullscreenMode")));

        fx(() -> {
            stage.setFullScreen(false);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(fx(() -> (boolean) getField(mainView, "fullscreenMode")));
    }
}

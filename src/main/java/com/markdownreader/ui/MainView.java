package com.markdownreader.ui;

import com.markdownreader.markdown.Heading;
import com.markdownreader.markdown.HtmlPageBuilder;
import com.markdownreader.markdown.MarkdownRenderer;
import com.markdownreader.markdown.RenderResult;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Monta e controla a interface principal do apresentador de Markdown.
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
    private final Label statusLabel = new Label("Nenhum documento aberto");
    private final Label zoomLabel = new Label("100%");
    private final VBox sidebar = new VBox();
    private final StackPane welcomePane = new StackPane();

    private final TextArea editorArea = new TextArea();
    private final StackPane centerStack = new StackPane();
    private final SplitPane centerSplit = new SplitPane();
    private final PauseTransition previewDebounce = new PauseTransition(Duration.millis(200));

    private final DoubleProperty fontScale = new SimpleDoubleProperty(1.0);
    private Theme theme;
    private boolean sidebarVisible = true;

    private File currentFile;
    private long currentFileTimestamp;
    private FileWatcher fileWatcher;

    /** Texto Markdown atualmente carregado (fonte de verdade para edição/salvar). */
    private String currentMarkdown = "";
    private boolean editMode = false;
    private boolean dirty = false;
    /** Evita marcar o documento como modificado ao popular o editor por código. */
    private boolean suppressEditorListener = false;

    public MainView(Stage stage) {
        this.stage = stage;
        this.theme = Theme.valueOf(prefs.get("theme", Theme.LIGHT.name()));
        this.fontScale.set(prefs.getDouble("fontScale", 1.0));

        buildLayout();
        applyThemeToRoot();
        showWelcome();
        registerShortcuts();
        enableDragAndDrop();
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
        root.setTop(buildToolbar());
        root.setCenter(buildCenter());
        root.setLeft(buildSidebar());
        root.setBottom(buildStatusBar());
    }

    private Node buildToolbar() {
        Button openBtn = iconButton("📂", "Abrir documento  (Ctrl+O)", e -> openFileDialog());
        Button reloadBtn = iconButton("↻", "Recarregar  (Ctrl+R)", e -> reloadCurrent());
        Button sidebarBtn = iconButton("☰", "Mostrar/ocultar sumário  (Ctrl+B)", e -> toggleSidebar());

        Button newBtn = iconButton("✚", "Novo documento  (Ctrl+N)", e -> newDocument());
        Button editBtn = iconButton("✎", "Editar texto  (Ctrl+E)", e -> toggleEditMode());
        editBtn.setId("edit-button");
        Button saveBtn = iconButton("💾", "Salvar  (Ctrl+S)", e -> save());

        Button zoomOutBtn = iconButton("−", "Diminuir zoom  (Ctrl+-)", e -> changeScale(-SCALE_STEP));
        Button zoomInBtn = iconButton("+", "Aumentar zoom  (Ctrl++)", e -> changeScale(SCALE_STEP));
        zoomLabel.getStyleClass().add("zoom-label");
        zoomLabel.setOnMouseClicked(e -> resetScale());
        Tooltip.install(zoomLabel, new Tooltip("Clique para redefinir o zoom (100%)"));

        Button themeBtn = iconButton(themeGlyph(), "Alternar tema claro/escuro  (Ctrl+T)", e -> toggleTheme());
        themeBtn.setId("theme-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label title = new Label("Markdown Reader");
        title.getStyleClass().add("app-title");

        HBox bar = new HBox(8,
                openBtn, reloadBtn, sidebarBtn,
                separator(), newBtn, editBtn, saveBtn,
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

        centerStack.getChildren().setAll(webView, welcomePane);
        centerStack.getStyleClass().add("content-area");

        editorArea.getStyleClass().add("editor-area");
        editorArea.setWrapText(true);
        editorArea.setPromptText("Digite seu Markdown aqui…");
        editorArea.textProperty().addListener((obs, old, txt) -> onEditorTextChanged(txt));

        // Atualiza o preview com um pequeno atraso para não re-renderizar a cada tecla.
        previewDebounce.setOnFinished(e -> renderMarkdown(currentMarkdown));

        // Em modo leitura, o SplitPane contém apenas o preview (largura total).
        // Ao editar, o editor é inserido à esquerda.
        centerSplit.getItems().setAll(centerStack);
        centerSplit.getStyleClass().add("editor-split");
        return centerSplit;
    }

    private Node buildSidebar() {
        Label header = new Label("Sumário");
        header.getStyleClass().add("sidebar-header");

        tocList.getStyleClass().add("toc-list");
        tocList.setPlaceholder(new Label("Sem títulos"));
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
        Label subtitle = new Label("Abra um arquivo .md ou arraste-o para esta janela");
        subtitle.getStyleClass().add("welcome-subtitle");
        Button openBtn = new Button("Abrir documento");
        openBtn.getStyleClass().add("primary-button");
        openBtn.setOnAction(e -> openFileDialog());

        VBox box = new VBox(14, icon, title, subtitle, openBtn);
        box.setAlignment(Pos.CENTER);
        welcomePane.getChildren().setAll(box);
        welcomePane.getStyleClass().add("welcome-pane");
        welcomePane.setVisible(true);

        // Mostra o documento de boas-vindas embutido também no WebView (atrás).
        loadEmbeddedSample();
    }

    // ------------------------------------------------------------- actions

    public void openFileDialog() {
        if (!confirmDiscardChanges()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir documento Markdown");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown", "*.mdown", "*.mkd"),
                new FileChooser.ExtensionFilter("Todos os arquivos", "*.*"));

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

            renderMarkdown(markdown);
            syncEditorText(markdown);
            welcomePane.setVisible(false);
            updateTitle();
            updateStatus(file, markdown);
            watchFile(file);
        } catch (IOException ex) {
            statusLabel.setText("Erro ao ler: " + file.getName() + " (" + ex.getMessage() + ")");
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

    private void renderMarkdown(String markdown) {
        RenderResult result = renderer.render(markdown);
        String page = pageBuilder.build(result.html(), theme, fontScale.get());
        webView.getEngine().loadContent(page, "text/html");
        tocList.getItems().setAll(result.headings());
    }

    private void loadEmbeddedSample() {
        try (var in = MainView.class.getResourceAsStream("/sample/welcome.md")) {
            if (in != null) {
                String md = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                this.currentMarkdown = md;
                renderMarkdown(md);
                syncEditorText(md);
            }
        } catch (IOException ignored) {
            // amostra é opcional
        }
    }

    // ------------------------------------------------------------- edição

    private void toggleEditMode() {
        setEditMode(!editMode);
    }

    private void setEditMode(boolean on) {
        if (on == editMode) {
            return;
        }
        editMode = on;
        if (on) {
            syncEditorText(currentMarkdown);
            if (!centerSplit.getItems().contains(editorArea)) {
                centerSplit.getItems().add(0, editorArea);
                centerSplit.setDividerPositions(0.45);
            }
            welcomePane.setVisible(false);
            editorArea.requestFocus();
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
        currentMarkdown = txt;
        dirty = true;
        previewDebounce.playFromStart();
        updateTitle();
    }

    /** Define o texto do editor sem disparar o ciclo de "modificado". */
    private void syncEditorText(String txt) {
        suppressEditorListener = true;
        editorArea.setText(txt);
        suppressEditorListener = false;
    }

    /** Salva o texto atual no arquivo aberto; pede um destino se não houver. */
    private void save() {
        String content = editMode ? editorArea.getText() : currentMarkdown;
        File target = currentFile;
        if (target == null) {
            target = chooseSaveFile();
            if (target == null) {
                return; // usuário cancelou
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
            statusLabel.setText("Erro ao salvar: " + target.getName() + " (" + ex.getMessage() + ")");
        }
    }

    private File chooseSaveFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar documento Markdown");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown", "*.mdown", "*.mkd"));
        chooser.setInitialFileName("sem-titulo.md");

        String last = prefs.get("lastDir", System.getProperty("user.home"));
        File lastDir = new File(last);
        if (lastDir.isDirectory()) {
            chooser.setInitialDirectory(lastDir);
        }
        return chooser.showSaveDialog(stage);
    }

    /** Cria um documento vazio já em modo de edição. */
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
        renderMarkdown("");
        welcomePane.setVisible(false);
        setEditMode(true);
        statusLabel.setText("Novo documento (não salvo)");
        updateTitle();
    }

    /**
     * Quando há edições não salvas, pergunta se o usuário quer salvar, descartar
     * ou cancelar. Retorna {@code true} se a ação que substitui o conteúdo atual
     * pode prosseguir.
     */
    private boolean confirmDiscardChanges() {
        if (!dirty) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle("Edições não salvas");
        String name = currentFile != null ? currentFile.getName() : "documento sem título";
        alert.setHeaderText("Há alterações não salvas em " + name + ".");
        alert.setContentText("O que você deseja fazer?");

        ButtonType saveBtn = new ButtonType("Salvar", ButtonBar.ButtonData.YES);
        ButtonType discardBtn = new ButtonType("Descartar", ButtonBar.ButtonData.NO);
        ButtonType cancelBtn = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(saveBtn, discardBtn, cancelBtn);

        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() == cancelBtn) {
            return false;
        }
        if (choice.get() == saveBtn) {
            save();
            // Se o salvamento foi cancelado (ex.: "Salvar como" abortado), não prossegue.
            return !dirty;
        }
        return true; // Descartar
    }

    private void updateTitle() {
        String name = currentFile != null ? currentFile.getName() : "Sem título";
        String mark = dirty ? "● " : "";
        String mode = editMode ? "  [edição]" : "";
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

    // ------------------------------------------------------------- theme/zoom

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
        return theme == Theme.DARK ? "☀" : "☾"; // sol / lua
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
        // Re-renderiza o conteúdo em memória (preserva edições não salvas);
        // diferente de reloadCurrent(), que relê o arquivo do disco.
        renderMarkdown(currentMarkdown);
    }

    private void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        root.setLeft(sidebarVisible ? sidebar : null);
    }

    // ------------------------------------------------------------- helpers

    private void scrollToAnchor(String id) {
        // Escapa aspas simples no id para o JS.
        String safe = id.replace("\\", "\\\\").replace("'", "\\'");
        String js = "var el = document.getElementById('" + safe + "');"
                + "if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'start' }); }";
        try {
            webView.getEngine().executeScript(js);
        } catch (Exception ignored) {
            // documento ainda carregando
        }
    }

    private void updateStatus(File file, String markdown) {
        int words = markdown.isBlank() ? 0 : markdown.trim().split("\\s+").length;
        int lines = markdown.isEmpty() ? 0 : (int) markdown.lines().count();
        statusLabel.setText("%s  •  %d palavras  •  %d linhas"
                .formatted(file.getAbsolutePath(), words, lines));
    }

    private void registerShortcuts() {
        root.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                // Filtros no nível da cena (fase de captura): rodam ANTES de o WebView
                // tratar o evento nativamente. Sem isso, nem os atalhos de teclado nem
                // o zoom por Ctrl+scroll disparam enquanto o WebView está em foco.
                scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
                scene.addEventFilter(ScrollEvent.SCROLL, this::handleScrollZoom);
            }
        });
    }

    /** Zoom com Ctrl + roda do mouse. Rolagem sem Ctrl segue normal (não consome). */
    private void handleScrollZoom(ScrollEvent e) {
        if (e.isControlDown() && e.getDeltaY() != 0) {
            changeScale(e.getDeltaY() > 0 ? SCALE_STEP : -SCALE_STEP);
            e.consume();
        }
    }

    /**
     * Trata os atalhos de teclado. Aceita as várias teclas físicas que produzem
     * {@code +} e {@code -} (linha numérica e teclado numérico) para que o zoom
     * funcione independentemente do layout do teclado.
     */
    private void handleShortcut(KeyEvent e) {
        if (!e.isShortcutDown()) { // Ctrl no Windows/Linux, Cmd no macOS
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
                return; // não é um atalho conhecido: não consome o evento
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
            // Não recarrega por cima de edições não salvas.
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

    /** Célula do sumário com recuo proporcional ao nível do título. */
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

package com.markdownreader.ui;

import java.io.File;

/**
 * Observa um arquivo em uma thread de segundo plano e dispara um callback
 * sempre que detecta mudança no horário de modificação.
 *
 * <p>Usa polling simples (a cada 700&nbsp;ms) por ser robusto frente a editores
 * que substituem o arquivo (em vez de gravar no mesmo inode), comportamento que
 * confunde o {@code WatchService} em algumas plataformas.
 */
final class FileWatcher {

    private static final long POLL_INTERVAL_MS = 700;

    private final File file;
    private final Runnable onChange;
    private volatile boolean running;
    private Thread thread;

    FileWatcher(File file, Runnable onChange) {
        this.file = file;
        this.onChange = onChange;
    }

    void start() {
        running = true;
        thread = new Thread(this::loop, "markdown-file-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void loop() {
        long lastSeen = file.lastModified();
        while (running) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            long current = file.lastModified();
            if (current != lastSeen) {
                lastSeen = current;
                onChange.run();
            }
        }
    }
}

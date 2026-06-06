package com.markdownreader.ui;

import java.io.File;

/**
 * Watches a file on a background thread and fires a callback whenever
 * a change in the modification timestamp is detected.
 *
 * <p>Uses simple polling (every 700&nbsp;ms) because it is robust against editors
 * that replace the file (instead of writing to the same inode), behavior that
 * confuses {@code WatchService} on some platforms.
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

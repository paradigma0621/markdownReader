package com.markdownreader.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FileWatcherTest {

    @TempDir
    Path tempDir;

    @Test
    void constructorAcceptsFileAndCallback() throws IOException {
        File file = tempDir.resolve("test.md").toFile();
        Files.createFile(file.toPath());
        // Should construct without throwing
        FileWatcher watcher = new FileWatcher(file, () -> {});
        assertNotNull(watcher);
    }

    @Test
    void startAndStopDoNotThrow() throws IOException {
        File file = tempDir.resolve("test.md").toFile();
        Files.createFile(file.toPath());

        FileWatcher watcher = new FileWatcher(file, () -> {});
        assertDoesNotThrow(() -> {
            watcher.start();
            watcher.stop();
        });
    }

    @Test
    void stopBeforeStartDoesNotThrow() throws IOException {
        File file = tempDir.resolve("test.md").toFile();
        Files.createFile(file.toPath());

        FileWatcher watcher = new FileWatcher(file, () -> {});
        assertDoesNotThrow(watcher::stop);
    }

    @Test
    void callbackInvokedWhenFileModificationTimeChanges() throws IOException, InterruptedException {
        File file = tempDir.resolve("test.md").toFile();
        Files.createFile(file.toPath());

        AtomicBoolean changed = new AtomicBoolean(false);
        FileWatcher watcher = new FileWatcher(file, () -> changed.set(true));
        watcher.start();

        // Let the watcher record the initial timestamp
        Thread.sleep(150);

        // Advance modification time by 2 seconds
        assertTrue(file.setLastModified(file.lastModified() + 2_000L), "Could not change file timestamp");

        // Wait longer than the 700 ms poll interval to allow detection
        Thread.sleep(1_000);

        watcher.stop();
        assertTrue(changed.get(), "Callback should have been invoked after file modification");
    }

    @Test
    void callbackNotInvokedWhenFileUnchanged() throws IOException, InterruptedException {
        File file = tempDir.resolve("test.md").toFile();
        Files.createFile(file.toPath());

        AtomicBoolean changed = new AtomicBoolean(false);
        FileWatcher watcher = new FileWatcher(file, () -> changed.set(true));
        watcher.start();

        // Wait more than one full poll cycle without changing the file
        Thread.sleep(900);

        watcher.stop();
        assertFalse(changed.get(), "Callback should NOT have been invoked when file is unchanged");
    }

    @Test
    void callbackInvokedOnEachDistinctChange() throws IOException, InterruptedException {
        File file = tempDir.resolve("test.md").toFile();
        Files.createFile(file.toPath());

        AtomicInteger changeCount = new AtomicInteger(0);
        FileWatcher watcher = new FileWatcher(file, changeCount::incrementAndGet);
        watcher.start();

        Thread.sleep(150);

        // First change
        assertTrue(file.setLastModified(file.lastModified() + 2_000L));
        Thread.sleep(900);

        // Second change
        assertTrue(file.setLastModified(file.lastModified() + 2_000L));
        Thread.sleep(900);

        watcher.stop();
        assertTrue(changeCount.get() >= 2, "Expected at least 2 callbacks, got: " + changeCount.get());
    }

    @Test
    void stopInterruptsWatcherThread() throws IOException, InterruptedException {
        File file = tempDir.resolve("test.md").toFile();
        Files.createFile(file.toPath());

        FileWatcher watcher = new FileWatcher(file, () -> {});
        watcher.start();
        Thread.sleep(150);
        watcher.stop();

        // Calling stop again should be safe
        assertDoesNotThrow(watcher::stop);
    }

    @Test
    void watcherIgnoresContentIfTimestampUnchanged() throws IOException, InterruptedException {
        File file = tempDir.resolve("test.md").toFile();
        Files.writeString(file.toPath(), "initial");

        AtomicBoolean changed = new AtomicBoolean(false);
        FileWatcher watcher = new FileWatcher(file, () -> changed.set(true));
        watcher.start();

        Thread.sleep(150);

        // Overwrite with same content but preserve timestamp
        long originalTs = file.lastModified();
        Files.writeString(file.toPath(), "modified");
        assertTrue(file.setLastModified(originalTs)); // restore timestamp

        Thread.sleep(900);

        watcher.stop();
        assertFalse(changed.get(), "Callback should not fire when timestamp is unchanged");
    }
}

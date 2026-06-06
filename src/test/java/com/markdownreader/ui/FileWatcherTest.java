package com.markdownreader.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FileWatcherTest {

    @Test
    void stopBeforeStartDoesNotThrow(@TempDir Path dir) throws IOException {
        File file = dir.resolve("test.md").toFile();
        Files.createFile(file.toPath());
        FileWatcher watcher = new FileWatcher(file, () -> {});
        assertDoesNotThrow(watcher::stop);
    }

    @Test
    void noCallbackWithoutFileChange(@TempDir Path dir) throws IOException, InterruptedException {
        File file = dir.resolve("unchanged.md").toFile();
        Files.writeString(file.toPath(), "initial content");

        AtomicInteger callCount = new AtomicInteger(0);
        FileWatcher watcher = new FileWatcher(file, callCount::incrementAndGet);
        watcher.start();

        // Wait slightly longer than two poll cycles (700 ms each)
        Thread.sleep(1600);
        watcher.stop();

        assertEquals(0, callCount.get(), "Callback must not fire when file is unchanged");
    }

    @Test
    void callbackFiredAfterFileModificationTimeChanges(@TempDir Path dir)
            throws IOException, InterruptedException {
        File file = dir.resolve("watched.md").toFile();
        Files.writeString(file.toPath(), "original");

        CountDownLatch latch = new CountDownLatch(1);
        FileWatcher watcher = new FileWatcher(file, latch::countDown);
        watcher.start();

        // Allow the watcher to record the initial timestamp before we change it
        Thread.sleep(200);

        // Advance the modification time by 2 seconds so the poll detects a difference
        long newTime = (file.lastModified() / 1000 + 2) * 1000L;
        assertTrue(file.setLastModified(newTime), "setLastModified must succeed");

        boolean detected = latch.await(3, TimeUnit.SECONDS);
        watcher.stop();
        assertTrue(detected, "FileWatcher must detect a change in modification time");
    }

    @Test
    void startAndStopAreIdempotentWithRespectToThread(@TempDir Path dir)
            throws IOException, InterruptedException {
        File file = dir.resolve("idempotent.md").toFile();
        Files.createFile(file.toPath());

        FileWatcher watcher = new FileWatcher(file, () -> {});
        watcher.start();
        Thread.sleep(100);
        watcher.stop();
        // Stopping twice must not throw
        assertDoesNotThrow(watcher::stop);
    }
}

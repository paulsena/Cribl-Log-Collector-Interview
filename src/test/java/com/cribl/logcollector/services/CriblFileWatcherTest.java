package com.cribl.logcollector.services;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * JUnit tests for  {@link CriblFileWatcherStreams}
 */
class CriblFileWatcherTest {

    private static final String TEST_FILE = "logs/test.txt";
    private static final int LINES_TO_READ = 5;

    private static final List<String> EXPECTED_REVERSED_LOG = new ArrayList<>(LINES_TO_READ);

    /**
     *  Our gold source of truth to compare against using an Apache Commons library that reads files in reverse
     */
    @BeforeAll
    static void setUp() throws IOException {
        int counter = 0;

        ReversedLinesFileReader reader = ReversedLinesFileReader.builder()
                .setFile(new File(TEST_FILE))
                .setCharset(StandardCharsets.UTF_8)
                .get();

        while (counter < LINES_TO_READ) {
            EXPECTED_REVERSED_LOG.add(reader.readLine());
            counter++;
        }
        reader.close();
    }

    @ParameterizedTest
    @MethodSource("getFileWatcherImplementations")
    void testCallable(ICriblFileWatcher watcher) {
        // Setup
        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {

            // Execution
            Future<List<String>> future = executorService.submit(watcher);

            // Assert
            List<String> logLines = Assertions.assertDoesNotThrow(() -> future.get());
            Assertions.assertEquals(LINES_TO_READ, logLines.size());
            Assertions.assertArrayEquals(EXPECTED_REVERSED_LOG.toArray(), logLines.toArray());
        }
    }

    @ParameterizedTest
    @MethodSource("getFileWatcherImplementations")
    void testReadFileLinesInReverse(ICriblFileWatcher watcher) throws Exception {
        // Setup

        // Execution
        List<String> logLines = watcher.call();

        // Assert
        Assertions.assertEquals(LINES_TO_READ, logLines.size());
        Assertions.assertArrayEquals(EXPECTED_REVERSED_LOG.toArray(), logLines.toArray());
    }

    @ParameterizedTest
    @MethodSource("getFileWatcherImplementations")
    void testReadFileLinesInReverseUsesCacheWhenFileNotModified(ICriblFileWatcher watcher) throws Exception {
        // Setup

        // Execution
        List<String> logLines = watcher.call();
        List<String> logLines2 = watcher.call();

        // Assert
        // Compare reference (pointer) here instead of arrayEquals to make sure it's the exact same object ref being returned
        Assertions.assertEquals(logLines, logLines2);
    }

    @Test
    void testInvalidFileException() {
        // Setup

        // Execution
        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> new CriblFileWatcherByteSeeker("FileDoesntExist", LINES_TO_READ));
        ResponseStatusException ex2 = Assertions.assertThrows(ResponseStatusException.class, () -> new CriblFileWatcherStreams("FileDoesntExist", LINES_TO_READ));

        // Assert
        Assertions.assertEquals("404 NOT_FOUND", ex.getStatusCode().toString());
        Assertions.assertEquals("404 NOT_FOUND", ex2.getStatusCode().toString());
    }

    private static List<ICriblFileWatcher> getFileWatcherImplementations() {
        return List.of(
                new CriblFileWatcherByteSeeker(TEST_FILE, LINES_TO_READ),
                new CriblFileWatcherStreams(TEST_FILE, LINES_TO_READ)
        );
    }
}
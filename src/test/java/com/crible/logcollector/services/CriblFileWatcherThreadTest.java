package com.crible.logcollector.services;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * JUnit tests for  {@link CriblFileWatcherThread}
 */
class CriblFileWatcherThreadTest {

    private static final String TEST_FILE = CriblFileWatcherService.PATH_PREFIX + "test.txt";
    private static final String LARGE_TEST_FILE = CriblFileWatcherService.PATH_PREFIX + "HDFS.log";
    private static final int LINES_TO_READ = 5;

    private static final List<String> EXPECTED_REVERSED_LOG = new ArrayList<>(LINES_TO_READ);

    /**
     *  Our gold source of truth to compare against using an Apache Commons library that reads files in reverse
     */
    @BeforeAll
    static void setUp() throws IOException {
        int counter = 0;

        ReversedLinesFileReader reader = ReversedLinesFileReader.builder()
                .setFile(new File(LARGE_TEST_FILE))
                .setCharset(StandardCharsets.UTF_8)
                .get();

        while (counter < LINES_TO_READ) {
            EXPECTED_REVERSED_LOG.add(reader.readLine());
            counter++;
        }
        reader.close();
    }

    @Test
    void testCallable() {
        // Setup
        CriblFileWatcherThread thread = new CriblFileWatcherThread(LARGE_TEST_FILE, LINES_TO_READ);
        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {

            // Execution
            Future<List<String>> future = executorService.submit(thread);

            // Assert
            List<String> logLines = Assertions.assertDoesNotThrow(() -> future.get());
            Assertions.assertEquals(LINES_TO_READ, logLines.size());
            Assertions.assertArrayEquals(EXPECTED_REVERSED_LOG.toArray(), logLines.toArray());
        }
    }

    @Test
    void testReadFileLinesInReverse() throws IOException {
        // Setup
        CriblFileWatcherThread thread = new CriblFileWatcherThread(LARGE_TEST_FILE, LINES_TO_READ);

        // Execution
        List<String> logLines = thread.readFileLinesInReverseWithStreams(LINES_TO_READ);

        // Assert
        Assertions.assertEquals(LINES_TO_READ, logLines.size());
        Assertions.assertArrayEquals(EXPECTED_REVERSED_LOG.toArray(), logLines.toArray());
    }

    @Test
    void testReadFileLinesInReverseUsesCacheWhenFileNotModified() throws IOException {
        // Setup
        CriblFileWatcherThread thread = new CriblFileWatcherThread(LARGE_TEST_FILE, LINES_TO_READ);
        Assertions.assertNotEquals(new File(LARGE_TEST_FILE).lastModified(), thread.lastKnownModified);

        // Execution
        List<String> logLines = thread.readFileLinesInReverseWithStreams(LINES_TO_READ);
        List<String> logLines2 = thread.readFileLinesInReverseWithStreams(LINES_TO_READ);

        // Assert
        Assertions.assertEquals(new File(LARGE_TEST_FILE).lastModified(), thread.lastKnownModified);
        // Compare reference (pointer) here instead of arrayEquals to make sure it's the exact same object ref being returned
        Assertions.assertEquals(logLines, logLines2);
    }
}
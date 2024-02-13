package com.cribl.logcollector.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * Main file watcher class that is callable by a thread pool and returns a future/promise when results are ready.
 * Keeps a reference on a file and last modified date so we can track when files change to support caching and file updates
 *
 * Assumes UTF 8 file encoding which is simple single byte per character encoding
 */
public class CriblFileWatcher implements Callable<List<String>> {

    private static final Logger logger = LogManager.getLogger(CriblFileWatcher.class);

    private final File logFile;
    private int maxLines;
    protected long lastKnownModified = 0;

    public CriblFileWatcher(String fileName, Integer maxLines) {
        this.logFile = new File(fileName);
        this.maxLines = maxLines;

        if (!this.logFile.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File does not exist on server: " + fileName);
        }

        logger.debug("Started new logger for file: " + fileName);
    }

    @Override
    public List<String> call() throws Exception {
        long timerStart = System.currentTimeMillis();

        List<String> result = readFileLinesInReverseWithStreams(this.maxLines);

        logger.debug("Read file {} in {} milliseconds", this.logFile.getName(), System.currentTimeMillis() - timerStart);
        return result;
    }

    /**
     * Reads very large files in reverse, line by line.
     * <p>
     * This function is performant on large files (gigabyte range) because it uses streams which are lazily evaluated until the line is read.
     * Also, we use Java's built in NIO (new input out) library which is a newer addition into the SDK as a performance improvement over the original IO libs.
     *
     * @param maxLines Maximum number of lines to return from file
     * @return A List of log lines that is terminated with a CR or CR LF
     * @throws IOException IO exception thrown if a file read error occurs
     */
    protected List<String> readFileLinesInReverseWithStreams(int maxLines) throws IOException {

        List<String> tailedLogLines = new ArrayList<>(maxLines);
        try (Stream<String> lines = Files.lines(logFile.toPath())) {

            Stream<String> reversedLines = lines.sorted(Comparator.reverseOrder());
            Iterator<String> iter = reversedLines.iterator();
            int lineCnt = 0;

            while (iter.hasNext() && lineCnt < this.maxLines) {
                String line = iter.next();
                tailedLogLines.add(line);
                lineCnt++;
            }
            lastKnownModified = logFile.lastModified();
        }
        return tailedLogLines;
    }

    public boolean hasFileBeenUpdated() {
        return lastKnownModified != logFile.lastModified();
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
    }
}

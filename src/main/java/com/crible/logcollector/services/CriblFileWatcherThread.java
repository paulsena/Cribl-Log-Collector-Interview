package com.crible.logcollector.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 * Assumes UTF 8 file encoding for simple single byte per character encoding
 */
public class CriblFileWatcherThread implements Callable<List<String>> {

    private static Logger logger = LogManager.getLogger(CriblFileWatcherThread.class);

    private File logFile;
    private int maxLines;
    protected long lastKnownModified = 0;
    private List<String> cachedLastLogLines;

    public CriblFileWatcherThread(String fileName, Integer maxLines) {
        this.logFile = new File(fileName);
        this.maxLines = maxLines;
        this.cachedLastLogLines = new ArrayList<>(maxLines);

        logger.info("Started new logger for file: " + fileName);
    }

    @Override
    public List<String> call() throws Exception {
        long timerStart = System.currentTimeMillis();

        List<String> result = readFileLinesInReverseWithStreams(this.maxLines);

        logger.info("Read file {} in {} milliseconds", this.logFile.getName(), System.currentTimeMillis() - timerStart);
        return result;
    }

    /**
     * Reads very large files in reverse, line by line.
     * <p>
     * This function is very performant on large files (gigabyte range) because it uses streams which are lazily evaluated until the line is read.
     * Also, we use Java's built in NIO (new input out) library which is a newer addition into the SDK as a performance improvement over the original IO libs.
     *
     * @param maxLines Maximum number of lines to return from file
     * @return A List of log lines that is terminated with a CR or CR LF
     * @throws IOException IO exception thrown if a file read error occurs
     */
    protected List<String> readFileLinesInReverseWithStreams(int maxLines) throws IOException {

        // If file was modified since last read, lets re-read the file. Otherwise, use cached list
        if (hasFileBeenUpdated()) {

            List<String> lastLogLines = new ArrayList<>(maxLines);
            try (Stream<String> lines = Files.lines(logFile.toPath())) {

                Stream<String> reversedLines = lines.sorted(Comparator.reverseOrder());
                Iterator<String> iter = reversedLines.iterator();
                int lineCnt = 0;

                while (iter.hasNext() && lineCnt < this.maxLines) {
                    String line = iter.next();
                    lastLogLines.add(line);
                    lineCnt++;
                }
                lastKnownModified = logFile.lastModified();
                cachedLastLogLines = lastLogLines;
            }
            return lastLogLines;
        }

        return cachedLastLogLines;
    }

    public boolean hasFileBeenUpdated() {
        return lastKnownModified != logFile.lastModified();
    }
}

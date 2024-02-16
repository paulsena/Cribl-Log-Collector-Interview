package com.cribl.logcollector.services;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class CriblFileWatcherByteSeeker implements ICriblFileWatcher {

    private static final Logger logger = LogManager.getLogger(CriblFileWatcherByteSeeker.class);

    private final File logFile;

    private int maxLines;

    private static final int BUFFER_SIZE = 4096; // 4KB

    public CriblFileWatcherByteSeeker(String fileName, int maxLines) {
        this.logFile = new File(fileName);
        this.maxLines = maxLines;

        if (!this.logFile.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File does not exist on server: " + fileName);
        }

        logger.debug("Started new logger for file: " + fileName);
    }

    @Override
    public List<String> call() throws IOException {
        long timerStart = System.currentTimeMillis();

        List<String> result = readFileLinesInReverse();

        logger.info("Read file {} in {} milliseconds", this.logFile.getName(), System.currentTimeMillis() - timerStart);
        return result;
    }

    public List<String> readFileLinesInReverse() throws IOException {
        List<String> logLines = new ArrayList<>(maxLines);

        try (RandomAccessFile file = new RandomAccessFile(this.logFile, "r")) {
            FileChannel channel = file.getChannel();
            long fileSize = channel.size();
            StringBuilder sb = new StringBuilder();
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

            // Set our starting read position. End of file - bufferSize
            long readPosition = NumberUtils.max(fileSize - BUFFER_SIZE, 0);

            while (readPosition < fileSize && readPosition >= 0) {
                // Read
                channel.position(readPosition);
                channel.read(buffer);
                buffer.flip();

                // Process the buffer from end to beginning
                for (int i = buffer.limit() - 1; i >= 0; i--) {
                    char ch = (char) buffer.get(i);
                    // If line return detected, reverse string b/c we've been reading backwards, and add to the list of results
                    if (ch == '\n') {
                        // Lets remove \r if it exists as well
                        // Windows uses \r\n  OSX/Linus uses \n
                        if (i > 1 && buffer.get(i-1) == '\r') {
                            i--;
                        }
                        String line = sb.reverse().toString();
                        // Skip over empty lines
                        if (!line.isEmpty()) {
                            logLines.add(line);
                        }
                        sb.setLength(0); // Fastest way to clear SB rather than init a new one
                        // Break out once we hit the max log lines we want tailed
                        if (logLines.size() >= maxLines) {
                            return logLines;
                        }
                    } else {
                        // No line return detected
                        sb.append(ch);
                    }
                }

                // We haven't hit our goal of lines to tail, keep going back in file
                readPosition -= BUFFER_SIZE;
            }

            if (!sb.isEmpty()) {
                logLines.add(sb.reverse().toString());
            }
        }

        return logLines;
    }

    @Override
    public boolean hasFileBeenUpdated() {
        return false;
    }

    @Override
    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
    }
}

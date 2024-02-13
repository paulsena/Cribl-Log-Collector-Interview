package com.crible.logcollector.services;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * In Spring services are a Singleton scope by default which is what we want
 */
@Service("CriblFileWatcherService")
public class CriblFileWatcherService {

    public static final String PATH_PREFIX = "data/";
    private static final int MAX_FILE_WATCHER_THREADS = 10;

    // We keep an in memory "cache" of our file watcher thread and it's promises returned when the client asks for files to watch
    private final Map<String, ImmutablePair<CriblFileWatcherThread, Future<List<String>>>> fileWatchers = new HashMap<>(MAX_FILE_WATCHER_THREADS);
    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_FILE_WATCHER_THREADS);

    /**
     * Main entry to retrieve tailed log files
     *
     * @param fileName
     * @param numEntries
     * @return
     */
    public Future<List<String>> getLogEntries(String fileName, Integer numEntries) {

        // TODO: For production scale, would be nice to have a rate limiter here. However, we return from cache when possible so not critical.

        ImmutablePair<CriblFileWatcherThread, Future<List<String>>> requestedFileWatcher = fileWatchers.get(fileName);

        if (requestedFileWatcher == null) {
            if (fileWatchers.size() < MAX_FILE_WATCHER_THREADS) {
                CriblFileWatcherThread newFileWatcher = new CriblFileWatcherThread(PATH_PREFIX + fileName, numEntries);
                Future<List<String>> future = executorService.submit(newFileWatcher);

                requestedFileWatcher = new ImmutablePair<>(newFileWatcher, future);
                fileWatchers.put(fileName, requestedFileWatcher);

                // File watcher thread will run and return the data with a promise
                return future;
            } else {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Reached maximum number of file watchers: " + MAX_FILE_WATCHER_THREADS);
                // FYI, we log in the global exception handler so we can keep track of when this happens for improvement purposes
                // TODO: An idea here; instead of having a fixed number of allowed file watchers. We can implement an LFU cache for file watchers and kill least read thread
                //  and instantiate new one when at capacity
            }
        } else {
            // Check file modified date against last known modified date. Only run expensive IO read if it has been modified since
            if (requestedFileWatcher.getKey().hasFileBeenUpdated()) {
                return executorService.submit(requestedFileWatcher.getKey());
            } else {
                // No file change. Return cached value
                return requestedFileWatcher.getValue();
            }
        }
    }

    public List<String> getFilteredLogEntries(String fileName, Integer numEntries, Optional<String> filterValue) throws ExecutionException, InterruptedException {

        List<String> fullResult = getLogEntries(fileName, numEntries).get();

        return filterValue.map(searchVal -> fullResult.stream().filter(entry -> entry.contains(searchVal)).collect(Collectors.toList())).orElse(fullResult);
    }
}


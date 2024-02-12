package com.cribl.logcollector.services;

import org.apache.commons.lang3.tuple.MutablePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
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
 * A singleton service, so we can cache all results of different file watcher threads and reuse them across all different web service requests.
 * This service starts a new file watcher when there is a cache miss and has a maximum number of watchers configurable to customize resource usage.
 *
 * In Spring services are a Singleton scope by default
 */
@Service("CriblFileWatcherService")
public class CriblFileWatcherService {

    @Autowired
    private Environment envProps;

    private static final int MAX_FILE_WATCHERS = 10;

    // We keep an in memory "cache" of our file watchers and it's promises of results returned. This is used to skip IO operations when modified date doesn't change since last run
    private final Map<String, MutablePair<CriblFileWatcher, Future<List<String>>>> fileWatchers = new HashMap<>(MAX_FILE_WATCHERS);
    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_FILE_WATCHERS);

    /**
     * Retrieves logs and filters based on a case-insensitive search per log line.
     * <p>
     * Uses a simple Java 8 Stream filter pattern to find log line matches. If this needed to be more robust, could use a
     * Filter design or Builder pattern to allow AND, OR conditions on groups of filter strings to build more complex queries
     */
    public List<String> getFilteredLogEntries(String fileName, Integer numEntries, Optional<String> filterValue) throws ExecutionException, InterruptedException {

        List<String> logEntries = getLogEntries(fileName, numEntries).get();

        return filterValue.map(searchVal -> logEntries.stream().filter(entry -> entry.toLowerCase().contains(searchVal.toLowerCase())).collect(Collectors.toList())).orElse(logEntries);
    }

    /**
     * Main entry to retrieve tailed log files
     *
     * @param fileName File name to retrieve
     * @param numEntries Maximum number of log entries to retrieve
     * @return Future (java's version of Promises) of a list of log entries once callable thread executes
     */
    public Future<List<String>> getLogEntries(String fileName, Integer numEntries) {

        MutablePair<CriblFileWatcher, Future<List<String>>> requestedFileWatcher = fileWatchers.get(fileName);

        if (requestedFileWatcher == null) {
            if (fileWatchers.size() < MAX_FILE_WATCHERS) {

                // Create new file watcher
                CriblFileWatcher newFileWatcher = new CriblFileWatcher(envProps.getProperty("com.cribl.logcollector.filepath") + fileName, numEntries);
                // Submit a watcher task to thread pool
                Future<List<String>> future = executorService.submit(newFileWatcher);
                fileWatchers.put(fileName, new MutablePair<>(newFileWatcher, future));

                // File watcher thread will run and return the data with a promise
                return future;
            } else {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Reached maximum number of file watchers: " + MAX_FILE_WATCHERS);
                // TODO: An idea here; instead of having a fixed number of allowed file watchers. We can implement an LFU cache for file watchers and kill least read watcher
                //  and instantiate new one when at capacity
            }
        } else {
            // Check file modified date against the last known modified date
            // Only run expensive IO read if it has been modified since
            if (requestedFileWatcher.getKey().hasFileBeenUpdated()) {
                // Resubmit task to thread pool
                Future<List<String>> future = executorService.submit(requestedFileWatcher.getKey());
                // Update cache
                requestedFileWatcher.setValue(future);

                return future;
            } else {
                // No file change. Return cached value
                return requestedFileWatcher.getValue();
            }
        }
    }
}

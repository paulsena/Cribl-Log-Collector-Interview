package com.cribl.logcollector.services;

import com.cribl.logcollector.services.filewatchers.ByteSeekerFileWatcherCallable;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import java.util.stream.Stream;

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

    private static final Logger logger = LogManager.getLogger(CriblFileWatcherService.class);


    private static final int MAX_FILE_WATCHERS = 10;

    // We keep an in memory "cache" of our file watchers and it's promises of results returned. This is used to skip IO operations when modified date doesn't change since last run
    private final Map<String, MutablePair<ICriblFileWatcher, Future<List<String>>>> fileWatchers = new HashMap<>(MAX_FILE_WATCHERS);
    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_FILE_WATCHERS);

    /**
     * Retrieves logs and filters based on a case-insensitive search per log line.
     * <p>
     * Uses a simple Java 8 Stream filter pattern to find log line matches. If this needed to be more robust, could use a
     * Filter design or Builder pattern to allow AND, OR conditions on groups of filter strings to build more complex queries
     */
    public List<String> getFilteredLogEntries(String fileName, Integer numEntries, Optional<String> filterValue) throws ExecutionException, InterruptedException {

        // Get log entries and limit to max num entries requested
        Stream<String> logEntriesStream = getLogEntries(fileName, numEntries).get().stream().limit(numEntries);

        if (filterValue.isPresent()) {
            return logEntriesStream.filter(entry -> entry.toLowerCase().contains(filterValue.get().toLowerCase())).collect(Collectors.toList());
        }

        return logEntriesStream.collect(Collectors.toList());
    }

    /**
     * Main entry to retrieve tailed log files
     *
     * @param fileName File name to retrieve
     * @param requestedNumEntries Maximum number of log entries to retrieve
     * @return Future (java's version of Promises) of a list of log entries once callable thread executes
     */
    public Future<List<String>> getLogEntries(String fileName, Integer requestedNumEntries) throws ExecutionException, InterruptedException {

        MutablePair<ICriblFileWatcher, Future<List<String>>> requestedFileWatcher = fileWatchers.get(fileName);

        if (requestedFileWatcher == null) {
            if (fileWatchers.size() < MAX_FILE_WATCHERS) {

                // Create new file watcher
                ICriblFileWatcher newFileWatcher = new ByteSeekerFileWatcherCallable(envProps.getProperty("com.cribl.logcollector.filepath") + fileName, requestedNumEntries);
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
            // Only run expensive IO read if it has been modified since or our cached result set is smaller than num entries to query
            if (requestedFileWatcher.getKey().hasFileBeenUpdated() || requestedFileWatcher.getValue().get().size() < requestedNumEntries) {
                // Resubmit task to thread pool
                ICriblFileWatcher fileWatcher = requestedFileWatcher.getKey();
                fileWatcher.setMaxLines(requestedNumEntries);
                Future<List<String>> future = executorService.submit(fileWatcher);
                // Update cache
                requestedFileWatcher.setValue(future);

                return future;
            } else {
                // No file change. Return cached value
                logger.debug("Cache hit, returning stored logs");
                return requestedFileWatcher.getValue();
            }
        }
    }
}

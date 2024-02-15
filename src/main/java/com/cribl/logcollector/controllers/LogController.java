package com.cribl.logcollector.controllers;

import com.cribl.logcollector.models.TailApiResponse;
import com.cribl.logcollector.services.CriblFileWatcherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

@RestController()
@RequestMapping("/cribl/log")
public class LogController {

    // Not allowed WS input characters. Pre-compiling a reg pattern once is much faster
    private static final Pattern NOT_ALLOWED_INPUT_CHARS = Pattern.compile("[/\\\\]");

    @Autowired
    private Environment envProps;

    // Spring dependency injection (Inversion of Control paradigm) to inject our service singleton bean
    @Autowired
    private CriblFileWatcherService fileWatcherService;

    /**
     * Main entry for our WS tail endpoint
     */
    @GetMapping("/tail")
    public TailApiResponse tail(@RequestParam(value = "filename", required = true) String fileName,
                                @RequestParam(value = "numEntries", defaultValue = "10") Integer numEntries,
                                @RequestParam(value = "filter", required = false) String filter) throws ExecutionException, InterruptedException {

        // Sanitize input strings. For filename this is important so to avoid slashes so a malicious user can't navigate to other directories using ../../ etc
        validateStringInput(fileName);
        validateStringInput(filter);
        validateNumEntriesRequested(numEntries);

        // Call our singleton service which contains cached file watchers
        List<String> logEntries = fileWatcherService.getFilteredLogEntries(fileName, numEntries, Optional.ofNullable(filter));

        return new TailApiResponse(logEntries, filter);
    }

    /**
     * Checks input string for any invalid characters using a precompiled RegExp pattern.
     * @param input String to validate
     * @throws ResponseStatusException Throws a friendly client displayable WS exception if validation fails
     */
    protected void validateStringInput(String input) throws ResponseStatusException {
        if (input != null && NOT_ALLOWED_INPUT_CHARS.matcher(input).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File name or filter input is invalid. Forward and blackwards slashes aren't allowed in filenames for security reasons");
        }
    }

    /**
     * Validates numEntries request param
     * In bigger production design, I would use Spring Validation framework w/ annotations or roll a custom one where this logic would be encapsulated by that in a different class
     *
     * @param requestedNumEntries Number of entries to tail from log
     */
    protected void validateNumEntriesRequested(Integer requestedNumEntries) {
        Integer maxTailLines = envProps != null ? Integer.parseInt(Objects.requireNonNull(envProps.getProperty("com.cribl.logcollector.maxTailLines"))) : 100;

        if (requestedNumEntries > maxTailLines || requestedNumEntries < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum number of log entries to to tail is 1 and maximum is: " + maxTailLines);
        }
    }

}

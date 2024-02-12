package com.cribl.logcollector.controllers;

import com.cribl.logcollector.models.TailApiResponse;
import com.cribl.logcollector.services.CriblFileWatcherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

@RestController()
@RequestMapping("/cribl/log")
public class LogController {

    // Not allowed WS input characters. Pre-compiling a reg pattern once is much faster
    private static final Pattern NOT_ALLOWED_INPUT_CHARS = Pattern.compile("[^\\w\\d\\s\\.]");


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

        // Call our singleton service which contains cached file watchers
        List<String> logEntriesPromise = fileWatcherService.getFilteredLogEntries(fileName, numEntries, Optional.ofNullable(filter));

        return new TailApiResponse(logEntriesPromise, filter);
    }

    /**
     * Checks input string for any invalid characters using a precompiled RegExp pattern.
     * @param input String to validate
     * @throws ResponseStatusException Throws a friendly client displayable WS exception if validation fails
     */
    protected void validateStringInput(String input) throws ResponseStatusException {
        if (input != null && NOT_ALLOWED_INPUT_CHARS.matcher(input).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File name or filter input is invalid. Only alphanumeric characters, numbers, spaces, and periods are allowed.");
        }
    }

}

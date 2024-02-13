package com.crible.logcollector.controllers;

import com.crible.logcollector.models.TailApiResponse;
import com.crible.logcollector.services.CriblFileWatcherService;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@RestController()
@RequestMapping("/cribl/log")
public class LogController {

    // Spring dependency injection (Inversion of Control paradigm) to inject our service singleton bean
    @Autowired
    private CriblFileWatcherService fileWatcherService;

    @GetMapping("/tail")
    public TailApiResponse tail(@RequestParam(value = "filename", required = true) String fileName,
                                @RequestParam(value = "numEntries", defaultValue = "5") Integer numEntries,
                                @RequestParam(value = "filter", required = false) String filter) throws ExecutionException, InterruptedException {

        // Using Jsoup library here to sanitize input strings to avoid XSS and injection type attacks
        String sanitizedFilename = Jsoup.clean(fileName, Safelist.none());
        Optional<String> sanitizedFilter = filter != null ? Optional.of(Jsoup.clean(filter, Safelist.none())) : Optional.empty();

        // Call our singleton service which contains cached file watchers
        List<String> logEntriesPromise = fileWatcherService.getFilteredLogEntries(sanitizedFilename, numEntries, sanitizedFilter);

        return new TailApiResponse(logEntriesPromise, sanitizedFilter.orElse(null));
    }
}

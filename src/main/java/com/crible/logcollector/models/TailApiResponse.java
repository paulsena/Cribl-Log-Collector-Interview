package com.crible.logcollector.models;

import java.util.List;

/**
 * Used to define Tail API response schema.
 * This object is used by Spring's framework to serialize data to JSON via the Jackson library
 */
public class TailApiResponse {
    private final List<String> logEntries;
    private final String filterUsed;

    public TailApiResponse(List<String> logEntries, String filterUsed) {
        this.logEntries = logEntries;
        this.filterUsed = filterUsed;
    }

    public List<String> getLogEntries() {
        return logEntries;
    }

    public String getFilterUsed() {
        return filterUsed;
    }
}

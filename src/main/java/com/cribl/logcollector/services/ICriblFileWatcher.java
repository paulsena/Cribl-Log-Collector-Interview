package com.cribl.logcollector.services;

import java.util.List;
import java.util.concurrent.Callable;

public interface ICriblFileWatcher extends Callable<List<String>> {

    boolean hasFileBeenUpdated();

    void setMaxLines(int maxLines);
}

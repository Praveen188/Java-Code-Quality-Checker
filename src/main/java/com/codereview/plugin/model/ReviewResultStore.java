package com.codereview.plugin.model;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-level service that stores review results in memory.
 * Shared between ReviewAction, ReviewAnnotator, and ReviewToolWindow.
 *
 * Key = absolute file path
 * Value = ReviewResult
 */
@Service
public final class ReviewResultStore {

    // filePath → ReviewResult
    private final Map<String, ReviewResult> results = new ConcurrentHashMap<>();

    // Listeners notified when results change (tool window, annotator)
    private final List<Runnable> changeListeners = new ArrayList<>();

    public static ReviewResultStore getInstance() {
        return ApplicationManager.getApplication().getService(ReviewResultStore.class);
    }

    public void store(ReviewResult result) {
        results.put(result.getFilePath(), result);
        notifyListeners();
    }

    public Optional<ReviewResult> get(String filePath) {
        return Optional.ofNullable(results.get(filePath));
    }

    public void clear(String filePath) {
        results.remove(filePath);
        notifyListeners();
    }

    public void clearAll() {
        results.clear();
        notifyListeners();
    }

    public boolean hasResults(String filePath) {
        return results.containsKey(filePath);
    }

    public Collection<ReviewResult> getAllResults() {
        return Collections.unmodifiableCollection(results.values());
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void notifyListeners() {
        ApplicationManager.getApplication().invokeLater(() ->
            changeListeners.forEach(Runnable::run));
    }
}

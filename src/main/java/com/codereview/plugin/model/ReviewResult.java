package com.codereview.plugin.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Holds all review issues for a single file.
 * Shared between the tool window panel and the inline annotator.
 */
public class ReviewResult {

    private final String filePath;
    private final List<ReviewIssue> issues;
    private final long reviewedAt;

    public ReviewResult(String filePath, List<ReviewIssue> issues) {
        this.filePath   = filePath;
        this.issues     = Collections.unmodifiableList(issues);
        this.reviewedAt = System.currentTimeMillis();
    }

    public String getFilePath()         { return filePath; }
    public List<ReviewIssue> getIssues(){ return issues; }
    public long getReviewedAt()         { return reviewedAt; }

    public int getCriticalCount() {
        return (int) issues.stream()
            .filter(i -> i.getSeverity() == ReviewIssue.Severity.CRITICAL).count();
    }

    public int getWarningCount() {
        return (int) issues.stream()
            .filter(i -> i.getSeverity() == ReviewIssue.Severity.WARNING).count();
    }

    public int getSuggestionCount() {
        return (int) issues.stream()
            .filter(i -> i.getSeverity() == ReviewIssue.Severity.SUGGESTION).count();
    }

    public List<ReviewIssue> getIssuesByCategory(ReviewIssue.Category category) {
        return issues.stream()
            .filter(i -> i.getCategory() == category)
            .collect(Collectors.toList());
    }

    public List<ReviewIssue> getIssuesForLine(int line) {
        return issues.stream()
            .filter(i -> i.getLine() == line)
            .collect(Collectors.toList());
    }

    public boolean isEmpty() { return issues.isEmpty(); }

    public String getSummary() {
        return String.format("[!!] %d Critical  [!] %d Warnings  [i] %d Suggestions",
            getCriticalCount(), getWarningCount(), getSuggestionCount());
    }
}

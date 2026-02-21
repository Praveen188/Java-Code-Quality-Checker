package com.codereview.plugin.model;

/**
 * Represents a single issue found during code quality check.
 */
public class ReviewIssue {

    public enum Severity {
        CRITICAL,
        WARNING,
        SUGGESTION
    }

    public enum Category {
        BUG("[Bug]"),
        SPELL_CHECK("[Spell Check]"),
        NAMING("[Naming]"),
        READABILITY("[Readability]"),
        JAVADOC("[Javadoc]");

        private final String label;
        Category(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final int      line;
    private final Severity severity;
    private final Category category;
    private final String   title;
    private final String   description;
    private final String   suggestion;
    private final String   fixedCode;

    public ReviewIssue(int line, Severity severity, Category category,
                       String title, String description,
                       String suggestion, String fixedCode) {
        this.line        = line;
        this.severity    = severity;
        this.category    = category;
        this.title       = title;
        this.description = description;
        this.suggestion  = suggestion;
        this.fixedCode   = fixedCode;
    }

    public int      getLine()        { return line; }
    public Severity getSeverity()    { return severity; }
    public Category getCategory()    { return category; }
    public String   getTitle()       { return title; }
    public String   getDescription() { return description; }
    public String   getSuggestion()  { return suggestion; }
    public String   getFixedCode()   { return fixedCode; }
    public boolean  hasFixedCode()   { return fixedCode != null && !fixedCode.isBlank(); }

    public String getSeverityIcon() {
        return switch (severity) {
            case CRITICAL   -> "[!!]";
            case WARNING    -> "[!]";
            case SUGGESTION -> "[i]";
        };
    }

    @Override
    public String toString() {
        return getSeverityIcon() + " " + category.getLabel()
            + " Line " + line + ": " + title;
    }
}

package com.codereview.plugin.ai;

import com.google.gson.*;
import com.codereview.plugin.model.ReviewIssue;
import com.codereview.plugin.model.ReviewResult;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls Claude API to review Java source code.
 * Returns a structured ReviewResult with parsed issues.
 */
public class ClaudeReviewClient {

    private static final String API_URL     = "https://api.anthropic.com/v1/messages";
    private static final String MODEL       = "claude-sonnet-4-20250514";
    private static final String API_VERSION = "2023-06-01";
    private static final int    MAX_TOKENS  = 4096;
    private static final int    TIMEOUT_MS  = 60_000;

    private final String apiKey;

    public ClaudeReviewClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public ReviewResult review(String javaSource, String filePath,
                                boolean checkBugs, boolean checkSpelling,
                                boolean checkReadability, boolean checkJavadoc)
            throws IOException {

        String prompt  = buildPrompt(javaSource, checkBugs, checkSpelling,
                                     checkReadability, checkJavadoc);
        String reqBody = buildRequestBody(prompt);

        HttpURLConnection conn = openConnection();
        sendRequest(conn, reqBody);

        int status = conn.getResponseCode();
        if (status != 200) {
            String err = readStream(conn.getErrorStream());
            throw new IOException("Claude API error " + status + ": " + err);
        }

        String response = readStream(conn.getInputStream());
        String json     = extractText(response);
        List<ReviewIssue> issues = parseIssues(json);
        return new ReviewResult(filePath, issues);
    }

    // ── Prompt ─────────────────────────────────────────────────────────────

    private String buildPrompt(String source, boolean bugs, boolean spelling,
                                boolean readability, boolean javadoc) {
        StringBuilder categories = new StringBuilder();
        if (bugs)        categories.append("- BUG: null pointer risks, unclosed resources, logic errors, wrong equals/hashCode\n");
        if (spelling)    categories.append("- SPELL_CHECK: typos in method/variable/class names and comments\n");
        if (spelling)    categories.append("- NAMING: meaningless names (x,temp,data,obj), misleading names, poor abbreviations — suggest context-aware better names\n");
        if (readability) categories.append("- READABILITY: methods over 20 lines, nesting over 3 levels, magic numbers, complex conditions, long parameter lists\n");
        if (javadoc)     categories.append("- JAVADOC: missing Javadoc on public methods/classes, incomplete @param/@return tags\n");

        return """
            You are an expert Java code reviewer. Review the Java source code below and return \
            a JSON array of issues found.

            REVIEW CATEGORIES TO CHECK:
            %s
            SEVERITY LEVELS:
            - CRITICAL: bugs, logic errors that could cause failures
            - WARNING: spell check issues, bad naming, missing Javadoc
            - SUGGESTION: readability improvements, style suggestions

            RESPONSE FORMAT — return ONLY this JSON, nothing else, no markdown:
            [
              {
                "line": <1-based line number>,
                "severity": "CRITICAL" | "WARNING" | "SUGGESTION",
                "category": "BUG" | "SPELL_CHECK" | "NAMING" | "READABILITY" | "JAVADOC",
                "title": "<short one-line summary>",
                "description": "<clear explanation of the problem>",
                "suggestion": "<specific actionable fix advice>",
                "fixedCode": "<optional: the corrected line or snippet, empty string if not applicable>"
              }
            ]

            RULES:
            1. Line numbers must be accurate — count carefully
            2. Be specific — reference the actual variable/method name in title
            3. For NAMING issues, always suggest a specific better name based on context
            4. For SPELL_CHECK, show: wrong → correct
            5. Return empty array [] if no issues found
            6. Return ONLY valid JSON — no explanation text outside the array

            JAVA SOURCE TO REVIEW:
            %s
            """.formatted(categories, source);
    }

    // ── HTTP ───────────────────────────────────────────────────────────────

    private String buildRequestBody(String prompt) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", prompt);
        JsonArray msgs = new JsonArray();
        msgs.add(msg);
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("max_tokens", MAX_TOKENS);
        body.add("messages", msgs);
        return new Gson().toJson(body);
    }

    private HttpURLConnection openConnection() throws IOException {
        HttpURLConnection conn = (HttpURLConnection)
            java.net.URI.create(API_URL).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", API_VERSION);
        return conn;
    }

    private void sendRequest(HttpURLConnection conn, String body) throws IOException {
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        }
    }

    private String extractText(String jsonResponse) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            if (content == null || content.isEmpty())
                throw new IOException("Empty Claude response");
            String text = content.get(0).getAsJsonObject()
                                  .get("text").getAsString().trim();
            // Strip markdown fences if present
            if (text.startsWith("```")) {
                text = text.replaceFirst("```(json)?\\s*", "");
                int end = text.lastIndexOf("```");
                if (end >= 0) text = text.substring(0, end);
            }
            return text.trim();
        } catch (JsonParseException e) {
            throw new IOException("Failed to parse Claude response: " + e.getMessage());
        }
    }

    // ── Parse JSON → ReviewIssue list ──────────────────────────────────────

    private List<ReviewIssue> parseIssues(String json) throws IOException {
        List<ReviewIssue> issues = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                int    line        = obj.get("line").getAsInt();
                String sevStr      = getString(obj, "severity", "SUGGESTION");
                String catStr      = getString(obj, "category", "READABILITY");
                String title       = getString(obj, "title", "");
                String description = getString(obj, "description", "");
                String suggestion  = getString(obj, "suggestion", "");
                String fixedCode   = getString(obj, "fixedCode", "");

                ReviewIssue.Severity severity = parseSeverity(sevStr);
                ReviewIssue.Category category = parseCategory(catStr);

                issues.add(new ReviewIssue(line, severity, category,
                                           title, description, suggestion, fixedCode));
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse review issues JSON: " + e.getMessage()
                + "\nRaw JSON: " + json);
        }
        return issues;
    }

    private String getString(JsonObject obj, String key, String fallback) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : fallback;
    }

    private ReviewIssue.Severity parseSeverity(String s) {
        return switch (s.toUpperCase()) {
            case "CRITICAL" -> ReviewIssue.Severity.CRITICAL;
            case "WARNING"  -> ReviewIssue.Severity.WARNING;
            default         -> ReviewIssue.Severity.SUGGESTION;
        };
    }

    private ReviewIssue.Category parseCategory(String c) {
        return switch (c.toUpperCase()) {
            case "BUG"         -> ReviewIssue.Category.BUG;
            case "SPELL_CHECK" -> ReviewIssue.Category.SPELL_CHECK;
            case "NAMING"      -> ReviewIssue.Category.NAMING;
            case "JAVADOC"     -> ReviewIssue.Category.JAVADOC;
            default            -> ReviewIssue.Category.READABILITY;
        };
    }
}

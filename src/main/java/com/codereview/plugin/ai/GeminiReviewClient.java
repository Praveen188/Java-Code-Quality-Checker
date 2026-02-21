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
 * Calls Google Gemini API for code review.
 *
 * NOTE: As of late 2025 the free tier only works with gemini-2.0-flash-exp.
 * All other models return 429 with limit:0 on free tier.
 *
 * Get your free API key at: https://aistudio.google.com/app/apikey
 */
public class GeminiReviewClient {

    private static final String MODEL   = "gemini-2.0-flash-exp";
    private static final String API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/"
        + MODEL + ":generateContent?key=";
    private static final int MAX_TOKENS = 4096;
    private static final int TIMEOUT_MS = 60_000;

    private final String apiKey;

    public GeminiReviewClient(String apiKey) {
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
            throw new IOException("Gemini API error " + status + ": " + err);
        }

        String response = readStream(conn.getInputStream());
        String json     = extractText(response);
        List<ReviewIssue> issues = parseIssues(json);
        return new ReviewResult(filePath, issues);
    }

    private String buildPrompt(String source, boolean bugs, boolean spelling,
                                boolean readability, boolean javadoc) {
        StringBuilder categories = new StringBuilder();
        if (bugs)        categories.append("- BUG: null pointer risks, unclosed resources, logic errors\n");
        if (spelling)    categories.append("- SPELL_CHECK: typos in method/variable/class names\n");
        if (spelling)    categories.append("- NAMING: meaningless names (x,temp,data,obj) - suggest better names\n");
        if (readability) categories.append("- READABILITY: methods over 20 lines, nesting over 3 levels, magic numbers\n");
        if (javadoc)     categories.append("- JAVADOC: missing Javadoc on public methods/classes\n");

        return "You are an expert Java code reviewer. Review the Java code below.\n\n" +
               "CATEGORIES TO CHECK:\n" + categories +
               "\nRESPONSE: Return ONLY a JSON array, no markdown, no explanation:\n" +
               "[{\"line\":1,\"severity\":\"CRITICAL|WARNING|SUGGESTION\"," +
               "\"category\":\"BUG|SPELL_CHECK|NAMING|READABILITY|JAVADOC\"," +
               "\"title\":\"short summary\",\"description\":\"explanation\"," +
               "\"suggestion\":\"how to fix\",\"fixedCode\":\"\"}]\n\n" +
               "Return [] if no issues. ONLY valid JSON.\n\n" +
               "JAVA CODE:\n" + source;
    }

    private String buildRequestBody(String prompt) {
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        JsonArray parts = new JsonArray();
        parts.add(textPart);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", MAX_TOKENS);
        genConfig.addProperty("temperature", 0.1);
        JsonObject body = new JsonObject();
        body.add("contents", contents);
        body.add("generationConfig", genConfig);
        return new Gson().toJson(body);
    }

    private HttpURLConnection openConnection() throws IOException {
        HttpURLConnection conn = (HttpURLConnection)
            java.net.URI.create(API_URL + apiKey).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
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
            if (root.has("error")) {
                String msg = root.getAsJsonObject("error").get("message").getAsString();
                throw new IOException("Gemini API error: " + msg);
            }
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty())
                throw new IOException("No candidates in Gemini response");

            String text = candidates.get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0).getAsJsonObject()
                .get("text").getAsString().trim();

            if (text.startsWith("```json")) text = text.substring(7);
            else if (text.startsWith("```")) text = text.substring(3);
            int end = text.lastIndexOf("```");
            if (end >= 0) text = text.substring(0, end);
            return text.trim();
        } catch (JsonParseException e) {
            throw new IOException("Failed to parse Gemini response: " + e.getMessage());
        }
    }

    private List<ReviewIssue> parseIssues(String json) throws IOException {
        List<ReviewIssue> issues = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                issues.add(new ReviewIssue(
                    obj.has("line") ? obj.get("line").getAsInt() : 1,
                    parseSeverity(getString(obj, "severity", "SUGGESTION")),
                    parseCategory(getString(obj, "category", "READABILITY")),
                    getString(obj, "title", "(no title)"),
                    getString(obj, "description", ""),
                    getString(obj, "suggestion", ""),
                    getString(obj, "fixedCode", "")
                ));
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse issues JSON: " + e.getMessage());
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

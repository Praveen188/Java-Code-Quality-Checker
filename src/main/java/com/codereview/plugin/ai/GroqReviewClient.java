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
 * Calls Groq API for code review — completely FREE.
 *
 * Free tier: 14,400 requests/day, no credit card needed.
 * Much more reliable than Gemini free tier in 2025.
 *
 * Get your free API key at: https://console.groq.com
 * Uses OpenAI-compatible API format.
 */
public class GroqReviewClient {

    // Best free model for code review on Groq
    private static final String MODEL   = "llama-3.3-70b-versatile";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final int    TIMEOUT_MS = 60_000;

    private final String apiKey;

    public GroqReviewClient(String apiKey) {
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
            throw new IOException("Groq API error " + status + ": " + err);
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
        if (spelling)    categories.append("- NAMING: meaningless names (x,temp,data,obj) - suggest better names based on context\n");
        if (readability) categories.append("- READABILITY: methods over 20 lines, nesting over 3 levels, magic numbers\n");
        if (javadoc)     categories.append("- JAVADOC: missing Javadoc on public methods/classes\n");

        return "You are an expert Java code reviewer. Review the Java code below.\n\n" +
               "CATEGORIES TO CHECK:\n" + categories +
               "\nRESPONSE FORMAT: Return ONLY a valid JSON array, no markdown fences, no explanation:\n" +
               "[{\"line\":1,\"severity\":\"CRITICAL|WARNING|SUGGESTION\"," +
               "\"category\":\"BUG|SPELL_CHECK|NAMING|READABILITY|JAVADOC\"," +
               "\"title\":\"short summary\",\"description\":\"explanation\"," +
               "\"suggestion\":\"how to fix\",\"fixedCode\":\"\"}]\n\n" +
               "Return [] if no issues found. Return ONLY the JSON array.\n\n" +
               "JAVA CODE TO REVIEW:\n" + source;
    }

    private String buildRequestBody(String prompt) {
        // Groq uses OpenAI-compatible format
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.add("messages", messages);
        body.addProperty("temperature", 0.1);
        body.addProperty("max_tokens", 4096);

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
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
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
                throw new IOException("Groq error: " + msg);
            }
            // OpenAI format: choices[0].message.content
            String text = root.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString().trim();

            // Strip markdown fences if model added them
            if (text.startsWith("```json")) text = text.substring(7);
            else if (text.startsWith("```")) text = text.substring(3);
            int end = text.lastIndexOf("```");
            if (end >= 0) text = text.substring(0, end);
            return text.trim();
        } catch (JsonParseException e) {
            throw new IOException("Failed to parse Groq response: " + e.getMessage());
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

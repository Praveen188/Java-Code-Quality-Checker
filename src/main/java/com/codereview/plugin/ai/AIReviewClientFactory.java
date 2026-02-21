package com.codereview.plugin.ai;

import com.codereview.plugin.model.ReviewResult;
import com.codereview.plugin.settings.ReviewSettings;

import java.io.IOException;

/**
 * Picks the right AI client based on user settings.
 *
 * Priority order (all FREE options first):
 *   1. Groq  — 14,400 requests/day FREE. Best free option. console.groq.com
 *   2. Gemini — Free tier limited in 2025. aistudio.google.com/app/apikey
 *   3. Claude — Paid but highest quality. console.anthropic.com
 */
public class AIReviewClientFactory {

    public enum Provider {
        GROQ("Groq (FREE - Llama 3.3)"),
        GEMINI("Google Gemini"),
        CLAUDE("Anthropic Claude"),
        NONE("Not configured");

        private final String label;
        Provider(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final ReviewSettings settings;

    public AIReviewClientFactory(ReviewSettings settings) {
        this.settings = settings;
    }

    public Provider detectProvider() {
        String pref = settings.getProvider();
        // Explicit selection
        if (pref.equals("GROQ")   && !settings.getGroqApiKey().isBlank())   return Provider.GROQ;
        if (pref.equals("GEMINI") && !settings.getGeminiApiKey().isBlank()) return Provider.GEMINI;
        if (pref.equals("CLAUDE") && !settings.getClaudeApiKey().isBlank()) return Provider.CLAUDE;
        // AUTO: prefer Groq > Gemini > Claude
        if (!settings.getGroqApiKey().isBlank())   return Provider.GROQ;
        if (!settings.getGeminiApiKey().isBlank()) return Provider.GEMINI;
        if (!settings.getClaudeApiKey().isBlank()) return Provider.CLAUDE;
        return Provider.NONE;
    }

    public ReviewResult review(String javaSource, String filePath) throws IOException {
        return switch (detectProvider()) {
            case GROQ -> new GroqReviewClient(settings.getGroqApiKey())
                .review(javaSource, filePath,
                    settings.isCheckBugs(), settings.isCheckSpelling(),
                    settings.isCheckReadability(), settings.isCheckJavadoc());

            case GEMINI -> new GeminiReviewClient(settings.getGeminiApiKey())
                .review(javaSource, filePath,
                    settings.isCheckBugs(), settings.isCheckSpelling(),
                    settings.isCheckReadability(), settings.isCheckJavadoc());

            case CLAUDE -> new ClaudeReviewClient(settings.getClaudeApiKey())
                .review(javaSource, filePath,
                    settings.isCheckBugs(), settings.isCheckSpelling(),
                    settings.isCheckReadability(), settings.isCheckJavadoc());

            case NONE -> throw new IOException(
                "No API key configured.\n\n" +
                "RECOMMENDED FREE option:\n" +
                "1. Go to console.groq.com -> sign up free\n" +
                "2. Create API Key (no credit card)\n" +
                "3. Paste in Settings -> Tools -> Java Code Quality Checker\n" +
                "Free limit: 14,400 checks/day");
        };
    }

    public String getProviderLabel() { return detectProvider().getLabel(); }
}

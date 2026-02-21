package com.codereview.plugin.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

@State(name = "ReviewSettings", storages = @Storage("ReviewSettings.xml"))
@Service
public final class ReviewSettings implements PersistentStateComponent<ReviewSettings.State> {

    public static class State {
        // Provider: "AUTO", "GROQ", "GEMINI", "CLAUDE"
        public String  provider         = "AUTO";

        // FREE providers
        public String  groqApiKey       = "";  // Best free: console.groq.com
        public String  geminiApiKey     = "";  // Limited free: aistudio.google.com

        // Paid provider (highest quality)
        public String  claudeApiKey     = "";

        // Categories
        public boolean checkBugs        = true;
        public boolean checkSpelling    = true;
        public boolean checkReadability = true;
        public boolean checkJavadoc     = true;

        // Options
        public boolean autoReviewOnSave = false;
        public int     maxFileSizeKb    = 100;
    }

    private State state = new State();

    public static ReviewSettings getInstance() {
        return ApplicationManager.getApplication().getService(ReviewSettings.class);
    }

    @Override public @NotNull State getState()        { return state; }
    @Override public void loadState(@NotNull State s) { this.state = s; }

    public String  getProvider()          { return state.provider; }
    public String  getGroqApiKey()        { return state.groqApiKey; }
    public String  getGeminiApiKey()      { return state.geminiApiKey; }
    public String  getClaudeApiKey()      { return state.claudeApiKey; }
    public boolean isCheckBugs()          { return state.checkBugs; }
    public boolean isCheckSpelling()      { return state.checkSpelling; }
    public boolean isCheckReadability()   { return state.checkReadability; }
    public boolean isCheckJavadoc()       { return state.checkJavadoc; }
    public boolean isAutoReviewOnSave()   { return state.autoReviewOnSave; }
    public int     getMaxFileSizeKb()     { return state.maxFileSizeKb; }

    public void setProvider(String p)          { state.provider = p; }
    public void setGroqApiKey(String k)        { state.groqApiKey = k; }
    public void setGeminiApiKey(String k)      { state.geminiApiKey = k; }
    public void setClaudeApiKey(String k)      { state.claudeApiKey = k; }
    public void setCheckBugs(boolean v)        { state.checkBugs = v; }
    public void setCheckSpelling(boolean v)    { state.checkSpelling = v; }
    public void setCheckReadability(boolean v) { state.checkReadability = v; }
    public void setCheckJavadoc(boolean v)     { state.checkJavadoc = v; }
    public void setAutoReviewOnSave(boolean v) { state.autoReviewOnSave = v; }
    public void setMaxFileSizeKb(int v)        { state.maxFileSizeKb = v; }
}

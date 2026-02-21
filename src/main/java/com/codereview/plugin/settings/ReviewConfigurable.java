package com.codereview.plugin.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class ReviewConfigurable implements Configurable {

    private JComboBox<String> providerCombo;
    private JBPasswordField   groqKeyField;
    private JBPasswordField   geminiKeyField;
    private JBPasswordField   claudeKeyField;
    private JBCheckBox        checkBugsBox;
    private JBCheckBox        checkSpellingBox;
    private JBCheckBox        checkReadabilityBox;
    private JBCheckBox        checkJavadocBox;
    private JBCheckBox        autoReviewBox;
    private JSpinner          maxFileSizeSpinner;
    private JLabel            statusLabel;

    @Override
    public @Nls String getDisplayName() { return "Java Code Quality Checker"; }

    @Override
    public @Nullable JComponent createComponent() {
        ReviewSettings s = ReviewSettings.getInstance();
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        // ── AI Provider ────────────────────────────────────────────────────
        JPanel providerPanel = new JPanel(new GridBagLayout());
        providerPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "AI Provider",
            TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(4, 6, 4, 6);
        g.fill   = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        providerPanel.add(new JBLabel("Provider:"), g);
        providerCombo = new JComboBox<>(new String[]{
            "AUTO - Best available (Groq > Gemini > Claude)",
            "GROQ - Llama 3.3 (FREE - RECOMMENDED)",
            "GEMINI - Google Gemini (FREE - limited)",
            "CLAUDE - Anthropic Claude (paid, best quality)"
        });
        providerCombo.setSelectedIndex(getProviderIndex(s.getProvider()));
        g.gridx = 1; g.weightx = 1.0;
        providerPanel.add(providerCombo, g);

        // Status
        g.gridx = 0; g.gridy = 1; g.gridwidth = 2;
        statusLabel = new JBLabel(getStatusText(s));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 11f));
        providerPanel.add(statusLabel, g);

        // --- Groq (best free option) ---
        g.gridy = 2; g.gridwidth = 1; g.weightx = 0;
        JBLabel groqLabel = new JBLabel("Groq API Key (FREE - BEST):");
        groqLabel.setForeground(new Color(0, 140, 0));
        providerPanel.add(groqLabel, g);
        groqKeyField = new JBPasswordField();
        groqKeyField.setText(s.getGroqApiKey());
        g.gridx = 1; g.weightx = 1.0;
        providerPanel.add(groqKeyField, g);

        g.gridx = 0; g.gridy = 3; g.gridwidth = 2;
        JBLabel groqHint = new JBLabel(
            "<html><small>FREE - 14,400 checks/day, no credit card." +
            " Get key: <b>console.groq.com</b> (sign up free, instant key)</small></html>");
        groqHint.setForeground(new Color(0, 120, 0));
        providerPanel.add(groqHint, g);

        // --- Gemini ---
        g.gridy = 4; g.gridwidth = 1; g.weightx = 0;
        JBLabel geminiLabel = new JBLabel("Gemini API Key (FREE - limited):");
        geminiLabel.setForeground(new Color(180, 120, 0));
        providerPanel.add(geminiLabel, g);
        geminiKeyField = new JBPasswordField();
        geminiKeyField.setText(s.getGeminiApiKey());
        g.gridx = 1; g.weightx = 1.0;
        providerPanel.add(geminiKeyField, g);

        g.gridx = 0; g.gridy = 5; g.gridwidth = 2;
        providerPanel.add(new JBLabel(
            "<html><small>Free tier restricted since late 2025." +
            " Get key: <b>aistudio.google.com/app/apikey</b></small></html>"), g);

        // --- Claude ---
        g.gridy = 6; g.gridwidth = 1; g.weightx = 0;
        providerPanel.add(new JBLabel("Claude API Key (paid, best quality):"), g);
        claudeKeyField = new JBPasswordField();
        claudeKeyField.setText(s.getClaudeApiKey());
        g.gridx = 1; g.weightx = 1.0;
        providerPanel.add(claudeKeyField, g);

        g.gridx = 0; g.gridy = 7; g.gridwidth = 2;
        providerPanel.add(new JBLabel(
            "<html><small>Highest quality results." +
            " Get key: <b>console.anthropic.com</b></small></html>"), g);

        root.add(providerPanel);
        root.add(Box.createVerticalStrut(8));
        providerCombo.addActionListener(e -> updateStatus());

        // ── Categories ─────────────────────────────────────────────────────
        JPanel catPanel = new JPanel(new GridBagLayout());
        catPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Quality Check Categories",
            TitledBorder.LEFT, TitledBorder.TOP));
        GridBagConstraints g2 = new GridBagConstraints();
        g2.anchor = GridBagConstraints.WEST;
        g2.insets = new Insets(4, 6, 4, 6);
        g2.gridwidth = 2; g2.fill = GridBagConstraints.HORIZONTAL;

        g2.gridy = 0;
        checkSpellingBox = new JBCheckBox(
            "[UNIQUE] Spell Check & Naming - typos in identifiers + context-aware naming suggestions",
            s.isCheckSpelling());
        checkSpellingBox.setForeground(new Color(0, 100, 180));
        catPanel.add(checkSpellingBox, g2);

        g2.gridy = 1;
        checkReadabilityBox = new JBCheckBox(
            "Readability - long methods, deep nesting, magic numbers", s.isCheckReadability());
        catPanel.add(checkReadabilityBox, g2);

        g2.gridy = 2;
        checkJavadocBox = new JBCheckBox(
            "Javadoc - missing or incomplete documentation", s.isCheckJavadoc());
        catPanel.add(checkJavadocBox, g2);

        g2.gridy = 3;
        checkBugsBox = new JBCheckBox(
            "Bug Detection - null pointers, unclosed resources, logic errors", s.isCheckBugs());
        catPanel.add(checkBugsBox, g2);

        root.add(catPanel);
        root.add(Box.createVerticalStrut(8));

        // ── Options ────────────────────────────────────────────────────────
        JPanel optPanel = new JPanel(new GridBagLayout());
        optPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Options",
            TitledBorder.LEFT, TitledBorder.TOP));
        GridBagConstraints g3 = new GridBagConstraints();
        g3.anchor = GridBagConstraints.WEST; g3.insets = new Insets(3, 6, 3, 6);
        g3.gridx = 0; g3.gridy = 0; g3.gridwidth = 2;
        autoReviewBox = new JBCheckBox("Auto-check on save (uses API quota)", s.isAutoReviewOnSave());
        optPanel.add(autoReviewBox, g3);
        g3.gridy = 1; g3.gridwidth = 1;
        optPanel.add(new JBLabel("Max file size (KB):"), g3);
        maxFileSizeSpinner = new JSpinner(new SpinnerNumberModel(s.getMaxFileSizeKb(), 10, 500, 10));
        g3.gridx = 1; optPanel.add(maxFileSizeSpinner, g3);
        root.add(optPanel);
        root.add(Box.createVerticalGlue());
        return root;
    }

    private int getProviderIndex(String p) {
        return switch (p) {
            case "GROQ"   -> 1;
            case "GEMINI" -> 2;
            case "CLAUDE" -> 3;
            default       -> 0;
        };
    }

    private String getSelectedProvider() {
        return switch (providerCombo.getSelectedIndex()) {
            case 1  -> "GROQ";
            case 2  -> "GEMINI";
            case 3  -> "CLAUDE";
            default -> "AUTO";
        };
    }

    private String getStatusText(ReviewSettings s) {
        if (!s.getGroqApiKey().isBlank())   return "Groq key configured - 14,400 FREE checks/day!";
        if (!s.getGeminiApiKey().isBlank()) return "Gemini key configured";
        if (!s.getClaudeApiKey().isBlank()) return "Claude key configured";
        return "No key set - add Groq key below for FREE checks (console.groq.com)";
    }

    private void updateStatus() {
        if (groqKeyField == null) return;
        String gk = new String(groqKeyField.getPassword()).trim();
        String mk = new String(geminiKeyField.getPassword()).trim();
        String ck = new String(claudeKeyField.getPassword()).trim();
        if      (!gk.isBlank()) statusLabel.setText("Groq key set - 14,400 FREE checks/day!");
        else if (!mk.isBlank()) statusLabel.setText("Gemini key set");
        else if (!ck.isBlank()) statusLabel.setText("Claude key set");
        else    statusLabel.setText("Add Groq key for free checks - console.groq.com");
    }

    @Override
    public boolean isModified() {
        ReviewSettings s = ReviewSettings.getInstance();
        return !getSelectedProvider().equals(s.getProvider())
            || !new String(groqKeyField.getPassword()).equals(s.getGroqApiKey())
            || !new String(geminiKeyField.getPassword()).equals(s.getGeminiApiKey())
            || !new String(claudeKeyField.getPassword()).equals(s.getClaudeApiKey())
            || checkBugsBox.isSelected()        != s.isCheckBugs()
            || checkSpellingBox.isSelected()    != s.isCheckSpelling()
            || checkReadabilityBox.isSelected() != s.isCheckReadability()
            || checkJavadocBox.isSelected()     != s.isCheckJavadoc()
            || autoReviewBox.isSelected()       != s.isAutoReviewOnSave()
            || (int)maxFileSizeSpinner.getValue()!= s.getMaxFileSizeKb();
    }

    @Override
    public void apply() {
        ReviewSettings s = ReviewSettings.getInstance();
        s.setProvider(getSelectedProvider());
        s.setGroqApiKey(new String(groqKeyField.getPassword()).trim());
        s.setGeminiApiKey(new String(geminiKeyField.getPassword()).trim());
        s.setClaudeApiKey(new String(claudeKeyField.getPassword()).trim());
        s.setCheckBugs(checkBugsBox.isSelected());
        s.setCheckSpelling(checkSpellingBox.isSelected());
        s.setCheckReadability(checkReadabilityBox.isSelected());
        s.setCheckJavadoc(checkJavadocBox.isSelected());
        s.setAutoReviewOnSave(autoReviewBox.isSelected());
        s.setMaxFileSizeKb((int) maxFileSizeSpinner.getValue());
    }

    @Override
    public void reset() {
        ReviewSettings s = ReviewSettings.getInstance();
        providerCombo.setSelectedIndex(getProviderIndex(s.getProvider()));
        groqKeyField.setText(s.getGroqApiKey());
        geminiKeyField.setText(s.getGeminiApiKey());
        claudeKeyField.setText(s.getClaudeApiKey());
        checkBugsBox.setSelected(s.isCheckBugs());
        checkSpellingBox.setSelected(s.isCheckSpelling());
        checkReadabilityBox.setSelected(s.isCheckReadability());
        checkJavadocBox.setSelected(s.isCheckJavadoc());
        autoReviewBox.setSelected(s.isAutoReviewOnSave());
        maxFileSizeSpinner.setValue(s.getMaxFileSizeKb());
    }
}

package com.codereview.plugin.annotator;

import com.intellij.lang.annotation.*;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.codereview.plugin.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * External annotator that draws inline highlights in the editor
 * based on stored ReviewResult for the current file.
 *
 * Severity colors:
 *   CRITICAL   → red underline
 *   WARNING    → yellow underline
 *   SUGGESTION → blue underline
 */
public class ReviewAnnotator extends ExternalAnnotator<ReviewAnnotator.FileInfo, ReviewResult> {

    // ── Step 1: collect info before PSI is released ────────────────────────

    @Override
    public @Nullable FileInfo collectInformation(@NotNull PsiFile file) {
        VirtualFileInfo vf = getFilePath(file);
        if (vf == null) return null;
        return new FileInfo(vf.path, file.getText());
    }

    // ── Step 2: do heavy work (we just look up stored results) ─────────────

    @Override
    public @Nullable ReviewResult doAnnotate(@NotNull FileInfo info) {
        return ReviewResultStore.getInstance().get(info.filePath).orElse(null);
    }

    // ── Step 3: apply annotations to the editor ────────────────────────────

    @Override
    public void apply(@NotNull PsiFile file, @Nullable ReviewResult result,
                      @NotNull AnnotationHolder holder) {
        if (result == null || result.isEmpty()) return;

        String[] lines = file.getText().split("\n", -1);

        for (ReviewIssue issue : result.getIssues()) {
            int lineIndex = issue.getLine() - 1; // convert to 0-based
            if (lineIndex < 0 || lineIndex >= lines.length) continue;

            // Calculate text range for this line
            int lineStart = getLineStartOffset(lines, lineIndex);
            int lineEnd   = lineStart + lines[lineIndex].length();

            // Trim leading whitespace for cleaner highlight
            String lineContent = lines[lineIndex];
            int trimStart = lineStart + (lineContent.length() - lineContent.stripLeading().length());
            if (trimStart >= lineEnd) trimStart = lineStart;

            TextRange range = TextRange.create(trimStart, lineEnd);

            // Build annotation
            String message = issue.getSeverityIcon() + " "
                + issue.getCategory().getLabel() + ": "
                + issue.getTitle();
            String tooltip = "<html><b>" + issue.getTitle() + "</b><br/>"
                + issue.getDescription() + "<br/><br/>"
                + "<i>💡 " + issue.getSuggestion() + "</i></html>";

            AnnotationBuilder builder = holder
                .newAnnotation(getSeverityLevel(issue.getSeverity()), message)
                .range(range)
                .tooltip(tooltip);

            // Apply color underline based on severity
            TextAttributes attrs = new TextAttributes();
            attrs.setEffectType(EffectType.WAVE_UNDERSCORE);
            attrs.setEffectColor(getSeverityColor(issue.getSeverity()));
            builder.enforcedTextAttributes(attrs);

            builder.create();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private HighlightSeverity getSeverityLevel(ReviewIssue.Severity s) {
        return switch (s) {
            case CRITICAL   -> HighlightSeverity.ERROR;
            case WARNING    -> HighlightSeverity.WARNING;
            case SUGGESTION -> HighlightSeverity.WEAK_WARNING;
        };
    }

    private Color getSeverityColor(ReviewIssue.Severity s) {
        return switch (s) {
            case CRITICAL   -> new Color(220, 50,  50);   // red
            case WARNING    -> new Color(200, 150, 0);    // amber
            case SUGGESTION -> new Color(50,  100, 200);  // blue
        };
    }

    private int getLineStartOffset(String[] lines, int lineIndex) {
        int offset = 0;
        for (int i = 0; i < lineIndex; i++) {
            offset += lines[i].length() + 1; // +1 for \n
        }
        return offset;
    }

    private VirtualFileInfo getFilePath(PsiFile file) {
        com.intellij.openapi.vfs.VirtualFile vf = file.getVirtualFile();
        if (vf == null) return null;
        return new VirtualFileInfo(vf.getPath());
    }

    // ── Inner classes ──────────────────────────────────────────────────────

    public static class FileInfo {
        final String filePath;
        final String source;
        FileInfo(String filePath, String source) {
            this.filePath = filePath;
            this.source   = source;
        }
    }

    private static class VirtualFileInfo {
        final String path;
        VirtualFileInfo(String path) { this.path = path; }
    }
}

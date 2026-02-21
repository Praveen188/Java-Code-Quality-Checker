package com.codereview.plugin.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.codereview.plugin.model.ReviewResultStore;
import org.jetbrains.annotations.NotNull;

/**
 * Clears review results for the current file - Alt+Shift+X
 */
public class ClearReviewAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile == null) return;

        String filePath = psiFile.getVirtualFile() != null
            ? psiFile.getVirtualFile().getPath()
            : psiFile.getName();

        ReviewResultStore.getInstance().clear(filePath);

        // Re-run the annotator on this specific file to clear highlights.
        // Uses restart(PsiFile) - targeted, non-deprecated, precise.
        // Avoids the broad restart() which is deprecated in 2025.3+.
        final PsiFile fileRef = psiFile;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed() && fileRef.isValid()) {
                DaemonCodeAnalyzer.getInstance(project).restart(fileRef);
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(file instanceof PsiJavaFile);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}

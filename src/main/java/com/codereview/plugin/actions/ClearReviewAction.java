package com.codereview.plugin.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.codereview.plugin.model.ReviewResultStore;
import org.jetbrains.annotations.NotNull;

/**
 * Clears review results for the current file — Alt+Shift+C
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

        // Trigger re-annotation (clear highlights)
        com.intellij.openapi.application.ApplicationManager.getApplication()
            .invokeLater(() -> {
                com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
                    .getInstance(project).restart(psiFile);
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

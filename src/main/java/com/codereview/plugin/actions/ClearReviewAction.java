package com.codereview.plugin.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.codereview.plugin.model.ReviewResultStore;
import org.jetbrains.annotations.NotNull;

/**
 * Clears review results and re-runs the annotator — Alt+Shift+X.
 *
 * Uses EditorEx.getMarkupModel() force-refresh to clear highlights.
 */
public class ClearReviewAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile == null) return;

        VirtualFile vFile = psiFile.getVirtualFile();
        String filePath = vFile != null ? vFile.getPath() : psiFile.getName();

        // 1. Remove stored results so the annotator returns nothing.
        ReviewResultStore.getInstance().clear(filePath);

        // 2. Force the editor to re-run external annotators by touching the
        //    document revision without changing content.
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || vFile == null) return;
            for (FileEditor fe : FileEditorManager.getInstance(project).getAllEditors(vFile)) {
                if (fe instanceof TextEditor textEditor) {
                    Editor editor = textEditor.getEditor();
                    if (editor instanceof EditorEx editorEx) {
                        // Repaint the gutter and markup — clears visible highlights
                        editorEx.getGutterComponentEx().repaint();
                        editorEx.getMarkupModel().removeAllHighlighters();
                    }
                }
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

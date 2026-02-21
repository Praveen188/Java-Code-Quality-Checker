package com.codereview.plugin.actions;

import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.codereview.plugin.ai.AIReviewClientFactory;
import com.codereview.plugin.model.*;
import com.codereview.plugin.settings.ReviewSettings;
import com.codereview.plugin.ui.ReviewToolWindowFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Main action triggered by Alt+Shift+Q
 * Supports both Gemini (free) and Claude via AIReviewClientFactory.
 */
public class ReviewAction extends AnAction {

    private static final String NOTIF_GROUP = "Java Code Quality Checker";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile)) {
            sendNotification(project, "Please open a Java file to check.", NotificationType.WARNING);
            return;
        }

        ReviewSettings settings = ReviewSettings.getInstance();
        AIReviewClientFactory factory = new AIReviewClientFactory(settings);

        if (factory.detectProvider() == AIReviewClientFactory.Provider.NONE) {
            sendNotification(project,
                "No API key set. Go to Settings -> Tools -> Java Code Quality Checker\n" +
                "Add your FREE Gemini key from aistudio.google.com/app/apikey",
                NotificationType.WARNING);
            return;
        }

        String source   = psiFile.getText();
        VirtualFile vf  = psiFile.getVirtualFile();
        String filePath = vf != null ? vf.getPath() : psiFile.getName();
        String fileName = psiFile.getName();

        if (source.length() / 1024 > settings.getMaxFileSizeKb()) {
            sendNotification(project,
                "File too large (" + source.length() / 1024 + "KB). " +
                "Max: " + settings.getMaxFileSizeKb() + "KB. " +
                "Adjust in Settings -> Java Code Quality Checker.",
                NotificationType.WARNING);
            return;
        }

        // Keep references for use inside anonymous class
        final String finalSource   = source;
        final String finalFilePath = filePath;
        final String finalFileName = fileName;
        final ReviewAction self    = this;

        ProgressManager.getInstance().run(
            new Task.Backgroundable(project,
                "Quality Check [" + factory.getProviderLabel() + "] - " + fileName, true) {

                ReviewResult result;
                String errorMsg;

                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    indicator.setText("Checking " + finalFileName
                        + " with " + factory.getProviderLabel() + "...");
                    try {
                        result = factory.review(finalSource, finalFilePath);
                    } catch (Exception ex) {
                        errorMsg = ex.getMessage();
                    }
                }

                @Override
                public void onSuccess() {
                    if (errorMsg != null) {
                        self.sendNotification(myProject,
                            "Check failed: " + errorMsg,
                            NotificationType.ERROR);
                        return;
                    }
                    ReviewResultStore.getInstance().store(result);
                    ReviewToolWindowFactory.show(myProject);

                    if (result.isEmpty()) {
                        self.sendNotification(myProject,
                            finalFileName + " looks great - no issues found!",
                            NotificationType.INFORMATION);
                    } else {
                        self.sendNotification(myProject,
                            "[" + factory.getProviderLabel() + "] "
                            + finalFileName + "\n" + result.getSummary(),
                            NotificationType.INFORMATION);
                    }
                }

                @Override
                public void onCancel() {
                    self.sendNotification(myProject,
                        "Quality check cancelled.",
                        NotificationType.WARNING);
                }
            }
        );
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

    public void sendNotification(Project p, String msg, NotificationType type) {
        Notifications.Bus.notify(
            new Notification(NOTIF_GROUP, "Java Code Quality Checker", msg, type), p);
    }
}

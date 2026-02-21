package com.codereview.plugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.*;
import com.intellij.ui.components.*;
import com.intellij.ui.treeStructure.Tree;
import com.codereview.plugin.model.*;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Factory for the "Code Quality" tool window (right side panel).
 * Shows review results grouped by severity and category.
 */
public class ReviewToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        ReviewPanel panel = new ReviewPanel(project);
        toolWindow.getContentManager().addContent(
            toolWindow.getContentManager().getFactory()
                .createContent(panel, "Quality Results", false));
    }

    public static void show(Project project) {
        com.intellij.openapi.application.ApplicationManager.getApplication()
            .invokeLater(() -> {
                ToolWindow tw = ToolWindowManager.getInstance(project)
                    .getToolWindow("Code Quality");
                if (tw != null) tw.show(null);
            });
    }
}

/**
 * The actual panel content showing review results in a tree.
 */
class ReviewPanel extends JPanel {

    private final Project project;
    private final Tree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode root;
    private final JLabel summaryLabel;
    private final JTextArea detailArea;

    ReviewPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        // ── Top bar ────────────────────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        summaryLabel = new JLabel("No results yet. Open a Java file and press Alt+Shift+Q.");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.PLAIN, 12f));
        topBar.add(summaryLabel, BorderLayout.CENTER);

        JButton clearBtn = new JButton("Clear");
        clearBtn.setFont(clearBtn.getFont().deriveFont(11f));
        clearBtn.addActionListener(e -> {
            ReviewResultStore.getInstance().clearAll();
            refreshTree(null);
        });
        topBar.add(clearBtn, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // ── Tree ───────────────────────────────────────────────────────────
        root = new DefaultMutableTreeNode("Quality Results");
        treeModel = new DefaultTreeModel(root);
        tree = new Tree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new ReviewTreeCellRenderer());

        // ── Detail pane ────────────────────────────────────────────────────
        detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        detailArea.setBackground(new Color(30, 30, 30));
        detailArea.setForeground(new Color(220, 220, 220));

        // Click on tree node → show detail
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node =
                (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof ReviewIssue issue) {
                showDetail(issue);
            }
        });

        // Split: tree top, detail bottom
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            new JBScrollPane(tree),
            new JBScrollPane(detailArea));
        split.setDividerLocation(300);
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);

        // Listen for new results
        ReviewResultStore.getInstance().addChangeListener(() ->
            refreshFromStore());

        refreshFromStore();
    }

    private void refreshFromStore() {
        Collection<ReviewResult> all = ReviewResultStore.getInstance().getAllResults();
        ReviewResult latest = all.stream()
            .max(Comparator.comparingLong(ReviewResult::getReviewedAt))
            .orElse(null);
        refreshTree(latest);
    }

    private void refreshTree(ReviewResult result) {
        root.removeAllChildren();

        if (result == null || result.isEmpty()) {
            summaryLabel.setText("No issues found - code looks good!");
            treeModel.reload();
            return;
        }

        summaryLabel.setText(result.getSummary());

        // Group by category
        Map<ReviewIssue.Category, List<ReviewIssue>> grouped = new LinkedHashMap<>();
        for (ReviewIssue.Category cat : ReviewIssue.Category.values()) {
            List<ReviewIssue> catIssues = result.getIssuesByCategory(cat);
            if (!catIssues.isEmpty()) grouped.put(cat, catIssues);
        }

        for (Map.Entry<ReviewIssue.Category, List<ReviewIssue>> entry : grouped.entrySet()) {
            String groupLabel = entry.getKey().getLabel()
                + " (" + entry.getValue().size() + ")";
            DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(groupLabel);
            for (ReviewIssue issue : entry.getValue()) {
                groupNode.add(new DefaultMutableTreeNode(issue));
            }
            root.add(groupNode);
        }

        treeModel.reload();
        // Expand all
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private void showDetail(ReviewIssue issue) {
        StringBuilder sb = new StringBuilder();
        sb.append(issue.getSeverityIcon()).append(" ")
          .append(issue.getCategory().getLabel()).append(" — Line ")
          .append(issue.getLine()).append("\n\n");
        sb.append("ISSUE\n").append(issue.getTitle()).append("\n\n");
        sb.append("DESCRIPTION\n").append(issue.getDescription()).append("\n\n");
        sb.append("SUGGESTION\n").append(issue.getSuggestion()).append("\n");
        if (issue.hasFixedCode()) {
            sb.append("\nFIXED CODE\n").append(issue.getFixedCode()).append("\n");
        }
        detailArea.setText(sb.toString());
        detailArea.setCaretPosition(0);
    }
}

/**
 * Custom tree cell renderer — colors nodes by severity.
 */
class ReviewTreeCellRenderer extends DefaultTreeCellRenderer {

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

        if (value instanceof DefaultMutableTreeNode node) {
            Object obj = node.getUserObject();
            if (obj instanceof ReviewIssue issue) {
                setText(issue.getSeverityIcon() + " Line " + issue.getLine()
                    + ": " + issue.getTitle());
                setForeground(getSeverityColor(issue.getSeverity()));
            } else if (obj instanceof String s) {
                setText(s);
                setForeground(new Color(180, 180, 180));
            }
        }
        setBackground(selected ? new Color(50, 80, 120) : new Color(30, 30, 30));
        setOpaque(true);
        return this;
    }

    private Color getSeverityColor(ReviewIssue.Severity s) {
        return switch (s) {
            case CRITICAL   -> new Color(255, 100, 100);
            case WARNING    -> new Color(255, 200, 50);
            case SUGGESTION -> new Color(100, 150, 255);
        };
    }
}

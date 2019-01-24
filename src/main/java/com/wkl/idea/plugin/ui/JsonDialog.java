package com.wkl.idea.plugin.ui;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonElement;
import com.intellij.json.psi.JsonFile;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.ui.ScreenUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.wkl.idea.plugin.parser.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonDialog extends DialogWrapper {
    public static final String FILE_NAME = "JsonViewPlugin.json";
    private JTextField nameInput;
    private Project mProject;
    private PsiFile mFile;
    private final AbstractAction mFormatAction = new AbstractAction("Format") {
        @Override
        public void actionPerformed(ActionEvent event) {
            doFormatAction();
        }
    };
    private final TextEditor mEditor;
    private final TextEditorProvider mTextEditorProvider;
    private final Editor mRealEditor;
    private int mErrorCount = 0;
    private Map<RangeHighlighter, String> mErrorTips = new HashMap<>();
    private PsiDirectory mDir;

    public JsonDialog(Project project, PsiDirectory file) {
        super(project);
        mDir = file;
        mProject = project;
        mFormatAction.setEnabled(true);
        mFormatAction.putValue(Action.NAME, "&Format");

        mFile = PsiFileFactory.getInstance(mProject).createFileFromText(FILE_NAME,
                JsonLanguage.INSTANCE, "");
        VirtualFile mJsonVirtualFile = mFile.getVirtualFile();
        mTextEditorProvider = TextEditorProvider.getInstance();
        mEditor = (TextEditor) mTextEditorProvider.createEditor(mProject, mJsonVirtualFile);
        mRealEditor = mEditor.getEditor();
        MarkupModelEx model = (MarkupModelEx) DocumentMarkupModel
                .forDocument(mRealEditor.getDocument(), project, true);
        model.addMarkupModelListener(() -> {
        }, new MarkupModelListener() {
            @Override
            public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
                calErrorCount(highlighter, 1);
            }

            @Override
            public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
                calErrorCount(highlighter, -1);
            }
        });
        EditorSettings settings = mRealEditor.getSettings();
        if (!settings.isLineNumbersShown()) {
            settings.setLineNumbersShown(true);
        }
        setTitle("Generate Class From Json");
        init();
        UIUtil.invokeLaterIfNeeded(() -> {
            RangeHighlighter[] highlighters = model.getAllHighlighters();
            for (RangeHighlighter highlighter : highlighters) {
                calErrorCount(highlighter, 1);
            }
        });
    }

    private void calErrorCount(RangeHighlighter highlighter, int delta) {
        HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
        if (info == null) {
            return;
        }
        if (info.type == HighlightInfoType.ERROR || info.getSeverity().myVal == HighlightSeverity.ERROR.myVal) {
            mErrorCount += delta;
            if (delta > 0) {
                mErrorTips.put(highlighter, info.getDescription());
            } else {
                mErrorTips.remove(highlighter);
            }
        }
    }

    @Override
    protected void doOKAction() {
        JsonParser jsonParser = new JsonParser(mProject, mDir, nameInput.getText().trim());
        mFile.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (element instanceof JsonElement || element instanceof PsiComment) {
                    element.accept(jsonParser);
                }
                super.visitElement(element);
            }

            @Override
            protected void elementFinished(PsiElement element) {
                if (element instanceof JsonFile) {
                    return;
                }
                if (element instanceof JsonElement) {
                    jsonParser.elementFinished(element);
                }
            }
        });
        super.doOKAction();
    }

    private void doFormatAction() {
        ApplicationManager.getApplication().invokeLater(
                () -> WriteCommandAction.runWriteCommandAction(mProject, () -> {
                    CodeStyleManager.getInstance(mProject).reformat(mFile, false);
                })
        );
    }

    @NotNull
    @Override
    protected List<ValidationInfo> doValidateAll() {
        List<ValidationInfo> vis = new ArrayList<>();
        vis.clear();
        String clazzName = nameInput.getText();
        if (clazzName == null) {
            clazzName = "";
        }
        if (StringUtil.isEmpty(clazzName.trim())) {
            vis.add(new ValidationInfo("Class name is empty"));
        }
        String text = mRealEditor.getDocument().getText().trim();
        if (StringUtil.isEmpty(text)) {
            vis.add(new ValidationInfo("Json is empty"));
        } else if (text.startsWith("[")) {
            vis.add(new ValidationInfo("Top element should be object"));
        } else if (mErrorCount > 0) {
            if (mErrorCount > 2) {
                vis.add(new ValidationInfo("Fix editor errors first"));
            } else {
                mErrorTips.values().forEach(s -> vis.add(new ValidationInfo(s)));
            }
        }
        return vis;
    }

    @Override
    protected void dispose() {
        mTextEditorProvider.disposeEditor(mEditor);
        super.dispose();
    }

    @NotNull
    @Override
    protected Action[] createActions() {
        Action okAction = getOKAction();
        Action cancelAction = getCancelAction();
        return new Action[]{cancelAction, mFormatAction, okAction};
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
        JPanel panel = new JPanel(new GridLayoutManager(1, 3));

        JLabel label = new JLabel("Class Name");
        GridConstraints constraints = new GridConstraints();
        constraints.setRow(0);
        constraints.setColumn(0);
        constraints.setRowSpan(1);
        constraints.setColSpan(1);
        constraints.setVSizePolicy(0);
        constraints.setHSizePolicy(0);
        constraints.setFill(0);
        constraints.setIndent(0);
        constraints.setUseParentLayout(false);
        panel.add(label, constraints);

        nameInput = new JTextField();
        constraints = new GridConstraints();
        constraints.setRow(0);
        constraints.setColumn(1);
        constraints.setRowSpan(1);
        constraints.setColSpan(2);
        constraints.setVSizePolicy(0);
        constraints.setHSizePolicy(6);
        constraints.setFill(1);
        constraints.setIndent(0);
        constraints.setUseParentLayout(false);
        Insets margin = JBUI.insets(2, 0);
        nameInput.setMargin(margin);
        panel.add(nameInput, constraints);
        return panel;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridLayoutManager(1, 1));
        GridConstraints constraints = new GridConstraints();
        constraints.setUseParentLayout(false);
        constraints.setFill(GridConstraints.FILL_BOTH);
        panel.add(mEditor.getComponent(), constraints);

        Rectangle size = ScreenUtil.getMainScreenBounds();
        panel.setPreferredSize(new Dimension((int) (size.width * 0.6), (int) (size.height * 0.6)));
        return panel;
    }

}

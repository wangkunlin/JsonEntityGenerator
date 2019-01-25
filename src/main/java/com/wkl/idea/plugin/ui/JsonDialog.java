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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonDialog extends DialogWrapper implements ItemListener {
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
    private JCheckBox mSetterGetter;
    private boolean mSetterGetterEnabled = false;

    public JsonDialog(Project project, PsiDirectory file) {
        super(project);
        mDir = file;
        mProject = project;
        // 格式化 按钮
        mFormatAction.setEnabled(true);
        mFormatAction.putValue(Action.NAME, "&Format");

        // 创建一个虚拟的 json 文件
        mFile = PsiFileFactory.getInstance(mProject).createFileFromText(FILE_NAME,
                JsonLanguage.INSTANCE, "");
        VirtualFile mJsonVirtualFile = mFile.getVirtualFile();
        mTextEditorProvider = TextEditorProvider.getInstance();
        // 获取一个 对应的 文本编辑器
        mEditor = (TextEditor) mTextEditorProvider.createEditor(mProject, mJsonVirtualFile);
        mRealEditor = mEditor.getEditor();

        // 增加初始 json 结构，防止报错
        WriteCommandAction.runWriteCommandAction(project,
                () -> mRealEditor.getDocument().insertString(0, "{}"));
        // 选中文本
        mRealEditor.getSelectionModel().setSelection(0, mRealEditor.getDocument().getTextLength());

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
        if (!settings.isLineNumbersShown()) { // 行号
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

    /**
     * 计算 编辑器内的错误数量，并把错误记录
     */
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
        JsonParser jsonParser = new JsonParser(mProject, mDir, nameInput.getText().trim(), mSetterGetterEnabled);
        mFile.accept(new PsiRecursiveElementWalkingVisitor() { // 递归遍历 json
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
                    // 调用 api 格式化 json
                    CodeStyleManager.getInstance(mProject).reformat(mFile, false);
                })
        );
    }

    /**
     * 检查错误信息
     */
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
        // 释放编辑器
        mTextEditorProvider.disposeEditor(mEditor);
        super.dispose();
    }

    @NotNull
    @Override
    protected Action[] createActions() {
        Action okAction = getOKAction();
        Action cancelAction = getCancelAction();
        // 底部按钮
        return new Action[]{cancelAction, mFormatAction, okAction};
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() { // 上方 的控件
        GridLayoutManager layoutManager = new GridLayoutManager(2, 3);
        Insets margin = JBUI.insetsBottom(5);
        layoutManager.setMargin(margin);
        JPanel panel = new JPanel(layoutManager);

        JLabel label = new JLabel("Class Name");
        GridConstraints constraints = new GridConstraints();
        constraints.setRow(0);
        constraints.setColumn(0);
        constraints.setRowSpan(1);
        constraints.setColSpan(1);
        constraints.setVSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
        constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
        constraints.setFill(GridConstraints.FILL_NONE);
        constraints.setIndent(0);
        constraints.setUseParentLayout(false);
        panel.add(label, constraints);

        nameInput = new JTextField();
        constraints = new GridConstraints();
        constraints.setRow(0);
        constraints.setColumn(1);
        constraints.setRowSpan(1);
        constraints.setColSpan(2);
        constraints.setVSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
        constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW);
        constraints.setFill(GridConstraints.FILL_HORIZONTAL);
        constraints.setIndent(0);
        constraints.setUseParentLayout(false);
        panel.add(nameInput, constraints);

        mSetterGetter = new JCheckBox("Getter and Setter", false);
        mSetterGetter.addItemListener(this);
        constraints = new GridConstraints();
        constraints.setRow(1);
        constraints.setColumn(1);
        constraints.setRowSpan(1);
        constraints.setColSpan(1);
        constraints.setVSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
        constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW);
        constraints.setAnchor(GridConstraints.ANCHOR_WEST);
        constraints.setFill(GridConstraints.FILL_NONE);
        constraints.setIndent(0);
        constraints.setUseParentLayout(false);
        panel.add(mSetterGetter, constraints);

        return panel;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() { // 中间的控件
        JPanel panel = new JPanel(new GridLayoutManager(1, 1));
        GridConstraints constraints = new GridConstraints();
        constraints.setUseParentLayout(false);
        constraints.setFill(GridConstraints.FILL_BOTH);
        panel.add(mEditor.getComponent(), constraints);

        Rectangle size = ScreenUtil.getMainScreenBounds();
        panel.setPreferredSize(new Dimension((int) (size.width * 0.6), (int) (size.height * 0.6)));
        return panel;
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        mSetterGetterEnabled = mSetterGetter.isSelected();
    }
}

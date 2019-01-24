package com.wkl.idea.plugin.parser;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by <a href="mailto:wangkunlin1992@gmail.com">Wang kunlin</a>
 * on 2019-01-24.
 */
class JavaBean {
    private Project project;
    private PsiDirectory fileDirectory;
    private final PsiElementFactory psiElementFactory;
    private final String name;
    final String originName;
    private List<ClassLine> mLines = new ArrayList<>();
    private List<ClassField> mFields = new ArrayList<>();
    private PsiType mClazz;

    JavaBean(Project project, PsiDirectory fileDirectory, PsiElementFactory psiElementFactory, String originName, String name) {
        this.project = project;
        this.fileDirectory = fileDirectory;
        this.name = name;
        this.originName = originName;
        this.psiElementFactory = psiElementFactory;
    }

    void addField(PsiType type, String name, String value) {
        ClassField field = ClassField.newField(type, name, value);
        mLines.add(field);
        mFields.add(field);
    }

    void addComment(String comment) {
        mLines.add(ClassComment.newComment(comment));
    }

    PsiType generaCode() {
        if (mClazz != null) {
            return mClazz;
        }
        PsiClass psiClass = psiElementFactory.createClass(name);
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiElementFactory factory = psiElementFactory;
            for (ClassLine line : mLines) {
                if (line.isComment()) {
                    ClassComment comment = (ClassComment) line;
                    psiClass.add(factory.createCommentFromText(comment.comment, null));
                } else {
                    ClassField field = (ClassField) line;
                    PsiField psiField = factory.createField(field.name, field.type);
                    psiClass.add(psiField);
                    if (!StringUtil.isEmpty(field.value)) {
                        PsiElement comment = factory.createCommentFromText("// " + field.value, null);
                        psiField.add(comment);
                    }
                }
            }

            fileDirectory.add(psiClass);
        });
        mClazz = psiElementFactory.createType(psiClass);
        return mClazz;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof JavaBean)) {
            return false;
        }
        JavaBean o = (JavaBean) obj;

        if (o.mFields.size() != mFields.size()) {
            return false;
        }
        return o.mFields.containsAll(mFields);
    }

    private static abstract class ClassLine {
        boolean isComment() {
            return false;
        }
    }

    private static class ClassField extends ClassLine {
        private PsiType type;
        private String name;
        private String value;

        private ClassField(PsiType type, String name, String value) {
            this.type = type;
            this.name = name;
            this.value = value;
        }

        private static ClassField newField(PsiType type, String name, String value) {
            return new ClassField(type, name, value);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof ClassField) {
                ClassField field = (ClassField) obj;
                return field.name.equals(name) &&
                        field.type == type;
            }

            return false;
        }
    }

    private static class ClassComment extends ClassLine {

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj instanceof ClassComment;
        }

        private String comment;

        private ClassComment(String comment) {
            this.comment = comment;
        }

        @Override
        boolean isComment() {
            return true;
        }

        private static ClassComment newComment(String comment) {
            return new ClassComment(comment);
        }

    }
}

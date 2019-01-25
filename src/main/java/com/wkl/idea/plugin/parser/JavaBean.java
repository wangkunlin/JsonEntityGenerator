package com.wkl.idea.plugin.parser;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by <a href="mailto:wangkunlin1992@gmail.com">Wang kunlin</a>
 * on 2019-01-24.
 */
class JavaBean {
    private Project project;
    private PsiDirectory fileDirectory;
    private final PsiElementFactory psiElementFactory;
    private final String name;
    private List<ClassLine> mLines = new ArrayList<>(); // field or comment
    private List<ClassField> mFields = new ArrayList<>();
    private PsiType mClazz; // 已经生成的类

    private JavaCodeStyleManager styleManager;
    private boolean mGetterSetter;

    JavaBean(Project project, PsiDirectory fileDirectory, PsiElementFactory psiElementFactory,
             String name, boolean getterSetter) {
        this.project = project;
        this.fileDirectory = fileDirectory;
        this.name = name;
        this.psiElementFactory = psiElementFactory;
        styleManager = JavaCodeStyleManager.getInstance(project);
        mGetterSetter = getterSetter;
    }

    void addField(PsiType type, String name, String value, boolean array) {
        ClassField field = ClassField.newField(type, name, value, array);
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

        StringBuilder builder = new StringBuilder("\n");
        StringBuilder getterSetter = new StringBuilder();
        for (ClassLine line : mLines) {
            if (line.isComment()) { // 注释
                ClassComment comment = (ClassComment) line;
                builder.append("    ").append(comment.comment).append("\n");
            } else { // 属性
                ClassField field = (ClassField) line;

                if (mGetterSetter) { // 生成 getter  setter
                    builder.append("    private ");
                } else {
                    builder.append("    public ");
                }
                String type;
                if (field.array) { // 数组
                    if (field.type instanceof PsiPrimitiveType) {
                        String boxName = ((PsiPrimitiveType) field.type).getBoxedTypeName();
                        type = "java.util.List<" + boxName + ">";
                        builder.append(type);
                    } else {
                        String typeName = field.type.getPresentableText();
                        type = "java.util.List<" + typeName + ">";
                        builder.append(type);
                    }
                } else { // 对象
                    type = field.type.getPresentableText();
                    builder.append(type);
                }
                builder.append(" ").append(field.name).append(";").append("\n");

                appendGetterSetter(getterSetter, field.name, type);

                if (!StringUtil.isEmpty(field.value)) { // 值不为null, 添加注释
                    builder.deleteCharAt(builder.length() - 1);
                    builder.append(" // ").append(field.value).append("\n");
                }
            }
        }

        if (mGetterSetter) {
            builder.append("\n").append(getterSetter);
        }

        // 生成类
        PsiClass psiClass = psiElementFactory.createClassFromText(builder.toString(), null);
        psiClass.setName(name); // 设置类名字
        // 设置 类 为 public
        Objects.requireNonNull(psiClass.getModifierList()).setModifierProperty(PsiModifier.PUBLIC, true);

        WriteCommandAction.runWriteCommandAction(project, new MyWriteAction(psiClass));
        mClazz = psiElementFactory.createType(psiClass);
        return mClazz;
    }

    private void appendGetterSetter(StringBuilder getterSetter, String name, String type) {
        if (mGetterSetter) {
            getterSetter.append("    public void set");
            getterSetter.append(JsonParser.captureName(name)).append("(");
            getterSetter.append(type).append(" ").append(name).append(") {\n");
            getterSetter.append("        this.").append(name).append(" = ");
            getterSetter.append(name).append(";\n");
            getterSetter.append("    }\n").append("\n");

            getterSetter.append("    public ").append(type).append(" get");
            getterSetter.append(JsonParser.captureName(name)).append("() {\n");
            getterSetter.append("        return this.").append(name).append(";\n");
            getterSetter.append("    }\n");
        }
    }

    private class MyWriteAction implements Runnable {

        private PsiClass psiClass;

        private MyWriteAction(PsiClass psiClass) {
            this.psiClass = psiClass;
        }

        @Override
        public void run() {
            styleManager.shortenClassReferences(psiClass);
            fileDirectory.add(psiClass);
        }
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
        private boolean array;

        private ClassField(PsiType type, String name, String value, boolean array) {
            this.type = type;
            this.name = name;
            this.value = value;
            this.array = array;
        }

        private static ClassField newField(PsiType type, String name, String value, boolean array) {
            return new ClassField(type, name, value, array);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof ClassField) {
                ClassField field = (ClassField) obj;
                return field.name.equals(name) &&
                        field.type == type &&
                        field.array == array;
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

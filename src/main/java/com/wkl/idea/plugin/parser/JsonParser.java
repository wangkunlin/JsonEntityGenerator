package com.wkl.idea.plugin.parser;

import com.intellij.json.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Created by <a href="mailto:wangkunlin1992@gmail.com">Wang kunlin</a>
 * on 2019-01-22.
 */
public class JsonParser extends JsonElementVisitor {

    private final PsiElementFactory psiElementFactory;
    private final Project project;
    private final PsiDirectory fileDirectory;
    private final String mFileName;

    private Stack<JavaBean> mObjects = new Stack<>(); // 使用栈来存储 生成的类
    private String mPropertyName;
    private List<JavaBean> mCreatedClasses = new ArrayList<>(); // 已经生产的不需要重新生产
    private PsiType mTypeString;
    private boolean mGetterSetter;

    public JsonParser(Project project, PsiDirectory fileDirectory, String name, boolean getterSetter) {
        this.project = project;
        this.fileDirectory = fileDirectory;
        this.mFileName = name;
        psiElementFactory = JavaPsiFacade.getElementFactory(project);
        mTypeString = getString();
        mGetterSetter = getterSetter;
    }

    @Override
    public void visitComment(PsiComment comment) { // 注释
        super.visitComment(comment);
        JavaBean bean = mObjects.peek();
        bean.addComment(comment.getText());
    }

    @Override
    public void visitProperty(@NotNull JsonProperty o) { // 属性
        super.visitProperty(o);
        mPropertyName = o.getName();
        JavaBean bean = mObjects.peek();
        JsonValue value = o.getValue();
        if (value == null || value instanceof JsonObject || value instanceof JsonArray) { // 需要特殊处理的 跳过
            return;
        }
        String valueText = value.getText(); // 判断 值的类型
        if (value instanceof JsonBooleanLiteral) {
            bean.addField(PsiType.BOOLEAN, mPropertyName, valueText, false);
        } else if (value instanceof JsonNumberLiteral) {
            String text = value.getText();
            if (text.contains(".")) {
                bean.addField(PsiType.FLOAT, mPropertyName, valueText, false);
            } else {
                bean.addField(PsiType.INT, mPropertyName, valueText, false);
            }
        } else if (value instanceof JsonStringLiteral) {
            bean.addField(mTypeString, mPropertyName, valueText, false);
        } else { // 未知的 用 String
            bean.addField(mTypeString, mPropertyName, "unknown type, use String type", false);
        }
    }

    @Override
    public void visitArray(@NotNull JsonArray o) { // 数组
        super.visitArray(o);
        JavaBean bean = mObjects.peek();
        List<JsonValue> valueList = o.getValueList();
        if (valueList.isEmpty()) {
            bean.addField(mTypeString, mPropertyName, "empty array, use String type", false);
        } else {
            JsonValue value = valueList.get(0);
            if (value instanceof JsonObject) {
                return;
            }
            if (value instanceof JsonBooleanLiteral) {
                bean.addField(PsiType.BOOLEAN, mPropertyName, null, true);
            } else if (value instanceof JsonNumberLiteral) {
                String text = value.getText();
                if (text.contains(".")) {
                    bean.addField(PsiType.FLOAT, mPropertyName, null, true);
                } else {
                    bean.addField(PsiType.INT, mPropertyName, null, true);
                }
            } else if (value instanceof JsonStringLiteral) {
                bean.addField(mTypeString, mPropertyName, null, true);
            } else {
                bean.addField(mTypeString, mPropertyName, "unknown type, use String type", true);
            }
        }
    }

    @Override
    public void visitObject(@NotNull JsonObject o) { // 对象
        super.visitObject(o);
        String name = mObjects.empty() ? mFileName : mPropertyName;
        mObjects.push(newJavaBean(name));
    }

    public void elementFinished(PsiElement element) { // 元素访问结束
        if (element instanceof JsonObject) {
            JavaBean javaBean = mObjects.peek(); // 拿到顶部的 对象
            if (mCreatedClasses.contains(javaBean)) { // 对比是否已经生产过
                mObjects.pop(); // 移除顶部对象
                int index = mCreatedClasses.indexOf(javaBean);
                mObjects.push(mCreatedClasses.get(index)); // 放入 已经创建过的对象
                return;
            }
            mCreatedClasses.add(javaBean);
            javaBean.generaCode(); // 生产类
        } else if (element instanceof JsonProperty) {
            JsonProperty property = (JsonProperty) element;
            String name = property.getName();
            if (property.getValue() instanceof JsonObject) {
                JavaBean child = mObjects.pop();
                PsiType psiType = child.generaCode();
                JavaBean parent = mObjects.peek();
                // 添加对象类型的 field
                parent.addField(psiType, name, null, false);
            } else if (property.getValue() instanceof JsonArray) {
                JsonArray array = (JsonArray) property.getValue();
                List<JsonValue> valueList = array.getValueList();
                if (valueList.isEmpty()) {
                    return;
                }
                JsonValue value = valueList.get(0);
                if (value instanceof JsonObject) {
                    JavaBean child = mObjects.pop();
                    PsiType psiType = child.generaCode();
                    JavaBean parent = mObjects.peek();
                    parent.addField(psiType, name, null, true);
                }
            }
        }
    }

    private PsiType getString() {
        return PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    }

    private JavaBean newJavaBean(String name) {
        return new JavaBean(project, fileDirectory, psiElementFactory, captureName(name), mGetterSetter);
    }

    static String captureName(String name) {
        name = name.substring(0, 1).toUpperCase() + name.substring(1);
        return name;
    }

}

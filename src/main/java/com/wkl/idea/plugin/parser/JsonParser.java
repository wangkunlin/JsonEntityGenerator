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

    private Stack<JavaBean> mObjects = new Stack<>();
    private String mPropertyName;
    private List<JavaBean> mCreatedClasses = new ArrayList<>();
    private PsiType mTypeObject;
    private PsiType mTypeObjectArray;
    private PsiType mTypeString;
    private PsiType mTypeStringArray;

    public JsonParser(Project project, PsiDirectory fileDirectory, String name) {
        this.project = project;
        this.fileDirectory = fileDirectory;
        this.mFileName = name;
        psiElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
        mTypeObject = getObject();
        mTypeObjectArray = mTypeObject.createArrayType();
        mTypeString = getString();
        mTypeStringArray = mTypeString.createArrayType();
    }

    @Override
    public void visitComment(PsiComment comment) {
        super.visitComment(comment);
        JavaBean bean = mObjects.peek();
        bean.addComment(comment.getText());
    }

    @Override
    public void visitProperty(@NotNull JsonProperty o) {
        super.visitProperty(o);
        mPropertyName = o.getName();
        JavaBean bean = mObjects.peek();
        JsonValue value = o.getValue();
        if (value == null || value instanceof JsonObject || value instanceof JsonArray) {
            return;
        }
        String valueText = value.getText();
        if (value instanceof JsonBooleanLiteral) {
            bean.addField(PsiType.BOOLEAN, mPropertyName, valueText);
        } else if (value instanceof JsonNumberLiteral) {
            String text = value.getText();
            if (text.contains(".")) {
                bean.addField(PsiType.FLOAT, mPropertyName, valueText);
            } else {
                bean.addField(PsiType.INT, mPropertyName, valueText);
            }
        } else if (value instanceof JsonStringLiteral) {
            bean.addField(mTypeString, mPropertyName, valueText);
        } else {
            bean.addField(mTypeObject, mPropertyName, "unknown type, use Object type");
        }
    }

    @Override
    public void visitArray(@NotNull JsonArray o) {
        super.visitArray(o);
        JavaBean bean = mObjects.peek();
        List<JsonValue> valueList = o.getValueList();
        if (valueList.isEmpty()) {
            bean.addField(mTypeObject, mPropertyName, "empty array, use Object type");
        } else {
            JsonValue value = valueList.get(0);
            if (value instanceof JsonObject) {
                return;
            }
            if (value instanceof JsonBooleanLiteral) {
                bean.addField(PsiType.BOOLEAN.createArrayType(), mPropertyName, null);
            } else if (value instanceof JsonNumberLiteral) {
                String text = value.getText();
                if (text.contains(".")) {
                    bean.addField(PsiType.FLOAT.createArrayType(), mPropertyName, null);
                } else {
                    bean.addField(PsiType.INT.createArrayType(), mPropertyName, null);
                }
            } else if (value instanceof JsonStringLiteral) {
                bean.addField(mTypeStringArray, mPropertyName, null);
            } else {
                bean.addField(mTypeObjectArray, mPropertyName, "unknown type, use Object type");
            }
        }
    }

    @Override
    public void visitObject(@NotNull JsonObject o) {
        super.visitObject(o);
        String name = mObjects.empty() ? mFileName : mPropertyName;
        mObjects.push(newJavaBean(name));
    }

    public void elementFinished(PsiElement element) {
        if (element instanceof JsonObject) {
            JavaBean javaBean = mObjects.peek();
            if (mCreatedClasses.contains(javaBean)) {
                mObjects.pop();
                int index = mCreatedClasses.indexOf(javaBean);
                mObjects.push(mCreatedClasses.get(index));
                return;
            }
            mCreatedClasses.add(javaBean);
            javaBean.generaCode();
        } else if (element instanceof JsonProperty) {
            JsonProperty property = (JsonProperty) element;
            String name = property.getName();
            if (property.getValue() instanceof JsonObject) {
                JavaBean child = mObjects.pop();
                PsiType psiType = child.generaCode();
                JavaBean parent = mObjects.peek();
                parent.addField(psiType, name, null);
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
                    parent.addField(psiType.createArrayType(), name, null);
                }
            }
        }
    }

    private PsiType getObject() {
        return PsiType.getJavaLangObject(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    }

    private PsiType getString() {
        return PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    }

    private JavaBean newJavaBean(String name) {
        return new JavaBean(project, fileDirectory, psiElementFactory, name, captureName(name));
    }

    private static String captureName(String name) {
        name = name.substring(0, 1).toUpperCase() + name.substring(1);
        return name;
    }

}

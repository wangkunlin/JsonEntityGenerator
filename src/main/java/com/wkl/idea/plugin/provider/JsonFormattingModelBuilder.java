package com.wkl.idea.plugin.provider;

import com.intellij.formatting.CustomFormattingModelBuilder;
import com.intellij.formatting.FormattingModel;
import com.intellij.json.formatter.JsonFormattingBuilderModel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Created by <a href="mailto:wangkunlin1992@gmail.com">Wang kunlin</a>
 * on 2019-01-23.
 */
public class JsonFormattingModelBuilder implements CustomFormattingModelBuilder {

    @NotNull
    @Override
    public FormattingModel createModel(PsiElement element, CodeStyleSettings settings) {
        JsonFormattingBuilderModel builder = new JsonFormattingBuilderModel();
        FormattingModel model = builder.createModel(element, settings);
        return model;
    }

    @Override
    public boolean isEngagedToFormat(PsiElement psiElement) {
        return true;
    }
}

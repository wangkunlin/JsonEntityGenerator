package com.wkl.idea.plugin.provider;

import com.intellij.json.codeinsight.JsonStandardComplianceProvider;
import com.intellij.psi.PsiComment;
import com.wkl.idea.plugin.ui.JsonDialog;
import org.jetbrains.annotations.NotNull;

/**
 * Created by <a href="mailto:wangkunlin1992@gmail.com">Wang kunlin</a>
 * on 2019-01-23.
 */
public class JsonComplianceProvider extends JsonStandardComplianceProvider {

    @Override
    public boolean isCommentAllowed(@NotNull PsiComment comment) {
        return JsonDialog.FILE_NAME.equals(comment.getContainingFile().getName());
    }
}

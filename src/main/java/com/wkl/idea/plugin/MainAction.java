package com.wkl.idea.plugin;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.wkl.idea.plugin.ui.JsonDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

/**
 * Created by <a href="mailto:wangkunlin1992@gmail.com">Wang kunlin</a>
 * on 2019-01-21.
 */
public class MainAction extends AnAction {
//    private final JavaDirectoryService myJavaDirectoryService;

    public MainAction() {
        super("Class From Json", "Class From Json", AllIcons.FileTypes.Java);
//        myJavaDirectoryService = JavaDirectoryService.getInstance();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        DataContext context = event.getDataContext();
        IdeView view = LangDataKeys.IDE_VIEW.getData(context);

        if (view == null) {
            return;
        }

        Module module = LangDataKeys.MODULE.getData(context);

        if (module == null) {
            return;
        }

        Project project = CommonDataKeys.PROJECT.getData(context);

        if (project == null) {
            return;
        }

        PsiDirectory dir = getDestinationDirectory(view);

        if (dir == null) {
            return;
        }

        JsonDialog dialog = new JsonDialog(project, dir);
        dialog.show();
    }

    @Nullable
    private static PsiDirectory getDestinationDirectory(@NotNull IdeView ide) {
        PsiDirectory[] directories = ide.getDirectories();

        if (directories.length == 1) {
            return directories[0];
        }
        return ide.getOrChooseDirectory();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean enabled = isAvailable(e.getDataContext());
        Presentation presentation = e.getPresentation();
        presentation.setVisible(enabled);
        presentation.setEnabled(enabled);
    }

    //-------↓ https://upsource.jetbrains.com/idea-ce/file/idea-ce-a7b3d4e9e48efbd4ac75105e9737cea25324f11e/android/android/src/com/android/tools/idea/actions/CreateClassAction.java
    // 这里的作用是 控制 'Class from Json' item 在 new 菜单里的显示或者隐藏
    private static boolean isAvailable(DataContext dataContext) {
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
        if (project == null || view == null || view.getDirectories().length == 0) {
            return false;
        }

        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        for (PsiDirectory dir : view.getDirectories()) {
            if (projectFileIndex.isUnderSourceRootOfType(dir.getVirtualFile(),
                    JavaModuleSourceRootTypes.SOURCES) && checkPackageExists(dir)) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkPackageExists(PsiDirectory directory) {
        PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if (psiPackage == null) {
            return false;
        }

        String name = psiPackage.getQualifiedName();
        return StringUtil.isEmpty(name) || PsiNameHelper.getInstance(directory.getProject()).isQualifiedName(name);
    }
    //-------↑

}

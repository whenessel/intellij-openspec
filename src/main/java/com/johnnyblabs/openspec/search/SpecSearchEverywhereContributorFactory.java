package com.johnnyblabs.openspec.search;

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Registers {@link SpecSearchEverywhereContributor} with the Search Everywhere framework. The
 * companion class to the retired always-on Browse-tree content search — the reach the tree filter
 * provided now lives in Search Everywhere. Available only when a project is present.
 */
public class SpecSearchEverywhereContributorFactory
        implements SearchEverywhereContributorFactory<SpecRequirementMatch> {

    @NotNull
    @Override
    public SearchEverywhereContributor<SpecRequirementMatch> createContributor(@NotNull AnActionEvent initEvent) {
        Project project = initEvent.getData(CommonDataKeys.PROJECT);
        return new SpecSearchEverywhereContributor(project);
    }
}

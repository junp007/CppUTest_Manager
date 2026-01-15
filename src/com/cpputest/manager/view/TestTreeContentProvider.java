package com.cpputest.manager.view;

import org.eclipse.jface.viewers.ITreeContentProvider;

import com.cpputest.manager.view.TestResultView.TestCase;
import com.cpputest.manager.view.TestResultView.TestGroup;
import com.cpputest.manager.view.TestResultView.TestProject;

public class TestTreeContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
        // ルート要素としてグループの一覧を返す
        return ((TestProject)inputElement).getTestGroups().toArray();
    }

    @Override
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof TestGroup) {
            return ((TestGroup) parentElement).cases.toArray();
        }
        return null;
    }

    @Override
    public Object getParent(Object element) {
        if (element instanceof TestCase) {
            return ((TestCase) element).getGroup();
        }
        return null;
    }

    @Override
    public boolean hasChildren(Object element) {
        return element instanceof TestGroup && !((TestGroup) element).cases.isEmpty();
    }
}
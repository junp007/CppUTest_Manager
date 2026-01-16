package com.cpputest.manager.view;

import org.eclipse.jface.viewers.ITreeContentProvider;

import com.cpputest.manager.model.TestCase;
import com.cpputest.manager.model.TestGroup;
import com.cpputest.manager.model.TestProjectManager;

public class TestTreeContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
        // ルート要素としてグループの一覧を返す
        return ((TestProjectManager)inputElement).getTestGroups().toArray();
    }

    @Override
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof TestGroup) {
            return ((TestGroup) parentElement).getCases().toArray();
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
        return element instanceof TestGroup && !((TestGroup) element).getCases().isEmpty();
    }
}
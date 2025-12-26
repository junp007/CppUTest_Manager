package com.example.cpputest.view;

import org.eclipse.jface.viewers.ITreeContentProvider;

import com.example.cpputest.view.TestResultView.TestCase;
import com.example.cpputest.view.TestResultView.TestGroup;

import java.util.List;

public class TestTreeContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
        // ルート要素としてグループの一覧を返す
        return ((List<?>) inputElement).toArray();
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
package com.example.cpputest.view;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

// ツールバーにコンボボックスを埋め込むためのクラス
public class ProjectComboContribution extends ControlContribution {
    private ComboViewer m_projectCombo;
    private TestResultView m_resultView;
    
    protected ProjectComboContribution(String id, TestResultView resultView) {
        super(id);
        m_resultView = resultView;
    }

    @Override
    protected Control createControl(Composite parent) {
        // ツールバー上に表示されるコンボボックスを作成
        m_projectCombo = new ComboViewer(parent, SWT.READ_ONLY | SWT.DROP_DOWN);
        Combo combo = m_projectCombo.getCombo();

        // 幅の調整（必要に応じて）
        combo.setToolTipText("Select Project");

        // コンテンツプロバイダーなどは以前と同様に設定
        m_projectCombo.setContentProvider(ArrayContentProvider.getInstance());
        m_projectCombo.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                return ((IProject) element).getName();
            }
        });

        // 初期リストの読み込み
        refreshProjectList();

        // 選択変更時のイベント
        m_projectCombo.addSelectionChangedListener(event -> {
            // プロジェクトのテストケースをスキャンする
            m_resultView.scanProjectTestCase();
        });

        return combo;
    }
    
    // 現在選択されているプロジェクト名を取得するメソッド
    public String getSelectedProjectName() {
        // インスタンス化前や破棄後に呼ばれても落ちないようにする
        if (m_projectCombo == null || m_projectCombo.getControl().isDisposed()) {
            return null;
        }
        IStructuredSelection selection = (IStructuredSelection) m_projectCombo.getSelection();
        IProject project = (IProject) selection.getFirstElement();
        return (project != null) ? project.getName() : null;
    }
    
    public Object getInput() {
        return m_projectCombo.getInput();
    }
    
    public void setInput(Object input) {
        m_projectCombo.setInput(input);
    }
    
    public StructuredSelection getSelection() {
        return (StructuredSelection)m_projectCombo.getSelection();
    }
    
    public void setSelection(StructuredSelection selection) {
        m_projectCombo.setSelection(selection);
    }
    
    // コンボボックスに表示されるプロジェクトリストを更新
    public void refreshProjectList() {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        // オープンされているプロジェクトのみに絞り込む
        List<IProject> openProjects = Arrays.stream(projects)
                .filter(IProject::isOpen)
                .collect(Collectors.toList());
        
        boolean isFirst = (getInput() == null);
        int selectIndex = 0;
        if (!isFirst) {
            // 最初に呼ばれたとき以外は現在選択されているプロジェクトのインデックスを覚えておく
            StructuredSelection selectionTmp = getSelection();
            int index = openProjects.indexOf(selectionTmp.getFirstElement());
            if (!selectionTmp.isEmpty() && index != -1) {
                selectIndex = index;
            }
        }
        
        // プロジェクトリストをコンボボックスにセット
        setInput(openProjects);
        
        if (openProjects != null && !openProjects.isEmpty()) {
            // プロジェクトを選択状態にする
            setSelection(new StructuredSelection(openProjects.get(selectIndex)));
        }
    }
}

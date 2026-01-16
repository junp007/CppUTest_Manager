package com.cpputest.manager.view;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.cpputest.manager.CppUTestSetupHandler;
import com.cpputest.manager.TestRunnerGenerator;
import com.cpputest.manager.model.TestCase;
import com.cpputest.manager.model.TestGroup;
import com.cpputest.manager.model.TestProjectManager;
import com.cpputest.manager.model.TestGroup.CheckState;
import com.cpputest.manager.parser.TestScanner;

import java.io.Console;

public class TestResultView extends ViewPart {
    private ProjectComboContribution m_projComboContribution;
    private CheckboxTreeViewer m_treeViewer;
    private TestProjectManager m_projectManager = new TestProjectManager();
    IActionBars toolbars;
    
    @Override
    public void createPartControl(Composite parent) {
        // 1. UIの作成
        createTreeViewer(parent);
        // 2. ツールバーの作成
        createToolbar();
    }
    
    // チェックボックス付きのテーブルビューアを作成
    private void createTreeViewer(Composite parent) {
     // TreeViewer の作成
        m_treeViewer = new CheckboxTreeViewer(parent, SWT.BORDER | SWT.MULTI | SWT.CHECK);
        m_treeViewer.getTree().setHeaderVisible(true);
        m_treeViewer.getTree().setLinesVisible(true);
        m_treeViewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));

        // 列の設定（1列目：Name）
        TreeViewerColumn colName = new TreeViewerColumn(m_treeViewer, SWT.NONE);
        colName.getColumn().setWidth(200);
        colName.getColumn().setText("Test Group / Name");
        colName.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                if (element instanceof TestGroup) return ((TestGroup) element).getName();
                if (element instanceof TestCase) return ((TestCase) element).getName();
                return "";
            }
            @Override
            public Color getBackground(Object element) {
                return getStatusColor(element);
            }
        });

        // 列の設定（2列目：Status）
        TreeViewerColumn colStatus = new TreeViewerColumn(m_treeViewer, SWT.NONE);
        colStatus.getColumn().setWidth(100);
        colStatus.getColumn().setText("Status");
        colStatus.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                if (element instanceof TestGroup) {
                    TestGroup group = (TestGroup) element;
                    // 成功数をカウント
                    long successCount = group.getCases().stream().filter(tc -> tc.isSuccess()).count();
                    int totalCount = group.getCases().size();
                    return String.format("(%d/%d)", successCount, totalCount);
                }
                if (element instanceof TestCase) {
                    TestCase tc = (TestCase)element;
                    if (tc.isTested()) {
                        return tc.isSuccess() ? "Success" : "Failure";
                    } else {
                        return "Idle";
                    }
                }
                return "";
            }
            
            @Override
            public Color getBackground(Object element) {
                return getStatusColor(element);
            }
        });

        m_treeViewer.setContentProvider(new TestTreeContentProvider());
        m_treeViewer.setCheckStateProvider(new ICheckStateProvider() {
            @Override
            public boolean isChecked(Object element) {
                if (element instanceof TestCase) {
                    return ((TestCase) element).isChecked();
                } else if (element instanceof TestGroup) {
                    CheckState state = ((TestGroup) element).getCheckState();
                    // グループ内のテストケースが1つ以上チェックされていればisCheckはtrueを返すようにする。
                    // (グレーチェックはisCheckedがtrueかつisGrayedがtrueのときになるので全チェック以外もtrueを返す)
                    return state == CheckState.AllChecked || state == CheckState.PartChecked;
                }
                return false;
            }

            @Override
            public boolean isGrayed(Object element) {
                if (element instanceof TestGroup) {
                    CheckState state = ((TestGroup) element).getCheckState();
                    return state == CheckState.PartChecked;
                } else {
                    return false;
                }
            }
        });

        // チェック状態が変更されたときに呼ばれる
        m_treeViewer.addCheckStateListener(event -> {
            Object element = event.getElement();
            boolean checked = event.getChecked();

            if (element instanceof TestGroup) {
                ((TestGroup)element).setChecked(checked);
            } else if (element instanceof TestCase) {
                ((TestCase)element).setChecked(checked);
            }
            m_treeViewer.refresh();
        });
        
        m_treeViewer.addTreeListener(new ITreeViewerListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                // グループが開かれた時の処理
                Object element = event.getElement();
                if (element instanceof TestGroup) {
                    TestGroup group = (TestGroup) element;
                    group.setExpand(true);
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                // グループが閉じられた時の処理
                Object element = event.getElement();
                if (element instanceof TestGroup) {
                    TestGroup group = (TestGroup) element;
                    group.setExpand(false);
                }
            }
        });
        
        // テストケースのデータ更新イベントハンドラ
        m_projectManager.addChangeListener(() -> {
            // UIスレッドで実行する必要がある
            Display.getDefault().asyncExec(() -> {
                if (!m_treeViewer.getControl().isDisposed()) {
                    m_treeViewer.refresh();
                    // モデルのフラグに基づいて展開状態を復元
                    syncExpandState();
                }
            });
        });
    
        m_treeViewer.setInput(m_projectManager);
    }
    
    private void syncExpandState() {
        // モデル（TestProjectなど）内の全グループをループ
        for (TestGroup group : m_projectManager.getTestGroups()) {
            // 第1引数に要素、第2引数に展開(true)/折り畳み(false)
            m_treeViewer.setExpandedState(group, group.isExpand());
        }
    }
    
    // --- 背景色を決定する共通メソッド ---
    private Color getStatusColor(Object element) {
        if (element instanceof TestGroup) {
            TestGroup group = (TestGroup) element;
            // 1つでも失敗があるか
            boolean anyFailure = group.getCases().stream().anyMatch(tc -> tc.isTested() && !tc.isSuccess());
            // 全件成功しているか（実行済みかつ失敗なし）
            long successCount = group.getCases().stream().filter(tc -> tc.isTested() && tc.isSuccess()).count();
            boolean allSuccess = (successCount == group.getCases().size() && successCount > 0);

            if (anyFailure) {
                // 実行済みかつ失敗あり
                return Display.getDefault().getSystemColor(SWT.COLOR_RED);
            } else if (allSuccess) {
                // 実行済みかつ全部成功
                return Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
            } else {
                // 実行していないものがある場合
                return null;
            }
        } else if (element instanceof TestCase) {
            TestCase tc = (TestCase) element;
            if (!tc.isTested()) {
                // 実行していない場合
                return null;
            } else if (tc.isSuccess()) {
                // 成功
                return Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
            } else {
                // 失敗
                return Display.getDefault().getSystemColor(SWT.COLOR_RED);
            }
        }
        return null; // デフォルト（白など）
    }

    // ツールバーを作成
    private void createToolbar() {
        // CppUtestRun.cppを生成ボタンのアクション
        org.eclipse.jface.action.Action generateAction = new org.eclipse.jface.action.Action("Generate") {
            @Override
            public void run() {
                String projectName = m_projComboContribution.getSelectedProjectName();
                if (projectName == null)
                    return; // プロジェクト未選択なら何もしない

                boolean confirm = MessageDialog.openQuestion(getViewSite().getShell(), "Generate",
                        "プロジェクト '" + projectName + "' に CppUTest のmain関数ファイルの生成を行いますか？");

                if (confirm) {
                    generateCppUTestRun(projectName);
                }
            }
        };

        // CppUTest用の設定ボタンのアクション
        org.eclipse.jface.action.Action setupAction = new org.eclipse.jface.action.Action("Setting") {
            @Override
            public void run() {
                String projectName = m_projComboContribution.getSelectedProjectName();
                if (projectName == null) return;

                boolean confirm = MessageDialog.openQuestion(getViewSite().getShell(), 
                    "Setting", "プロジェクト '" + projectName + "' に CppUTest の初期設定を行いますか？");

                if (confirm) {
                    try {
                        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
                        CppUTestSetupHandler.applyCppUTestSetting(project);
                        generateCppUTestRun(projectName);
                        MessageDialog.openInformation(getViewSite().getShell(), "Success", "CppUTest の初期設定が完了しました。");
                    } catch (Exception e) {
                        MessageDialog.openError(getViewSite().getShell(), "Error", "CppUTest の初期設定に失敗しました: " + e.getMessage());
                    }
                }
            }
        };
        
        // プロジェクトのスキャンボタンのアクション
        org.eclipse.jface.action.Action scanProjectAction = new org.eclipse.jface.action.Action("Scan") {
            @Override
            public void run() {
                m_projComboContribution.refreshProjectList();
                toolbars.updateActionBars();
            }
        };

        // アイコンの設定
//        setupAction.setImageDescriptor(org.eclipse.ui.PlatformUI.getWorkbench().getSharedImages().
//                getImageDescriptor(org.eclipse.ui.ISharedImages.IMG_ETOOL_HOME_NAV));
        scanProjectAction.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin(
                "org.eclipse.jdt.ui", "icons/full/elcl16/refresh.png"));
        setupAction.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin(
                "org.eclipse.ui", "icons/full/etool16/tricks.png"));
        generateAction.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin(
                "org.eclipse.jdt.ui", "icons/full/eview16/source.png"));
          
        scanProjectAction.setToolTipText("プロジェクトのスキャン");
        setupAction.setToolTipText("CppUTest用の設定");
        generateAction.setToolTipText("CppUtestRun.cppを生成");
        
        // ビューのツールバーにボタンを追加
        toolbars = getViewSite().getActionBars();
        IToolBarManager toolbarManager = toolbars.getToolBarManager();
        
        // プロジェクト選択コンボボックスを最初に追加
        m_projComboContribution = new ProjectComboContribution("projectSelector", this);
        
        // プロジェクト選択コンボボックス
        toolbarManager.add(m_projComboContribution);
        // Scanボタン
        toolbarManager.add(scanProjectAction);
        // セパレーター（区切り線）
        toolbarManager.add(new Separator());
        // CppUTestセットアップボタン
        toolbarManager.add(setupAction);
        // セパレーター（区切り線）
        toolbarManager.add(new Separator());
        // Generateボタン
        toolbarManager.add(generateAction);
        
        toolbars.updateActionBars();
        
        if (getSelectedProjectName() != null) {
            scanProjectTestCase(getSelectedProjectName());
        }
    }
    

    private void generateCppUTestRun(String projectName) {
        // チェックされている項目を取得
        Object[] checkedElements = m_treeViewer.getCheckedElements();
        // CppUtestRunファイルを生成
        TestRunnerGenerator.generateCppUTestRun(projectName, checkedElements, m_projectManager.getCurrentProject());
    }

    @Override
    public void setFocus() {
        m_treeViewer.getControl().setFocus();
    }

    private static TestResultView m_instance;

    public TestResultView() {
        m_instance = this;
    }

    public static void updateTestResult(final String groupName, final String testName, final boolean isSuccess, final boolean isTested) {
        m_instance.m_projectManager.updateTestResult(groupName, testName, isSuccess, isTested);
    }
    
    // プロジェクトを変更、及び再スキャンするときに呼ばれる
    public void changeProject(String oldProjectName, String newProjectName) {
        if (newProjectName == null || newProjectName.isEmpty()) {
            // プロジェクト未選択なら何もしない
            return;
        }
        
        // プロジェクトの変更
        m_projectManager.changeProject(oldProjectName, newProjectName);
        
        // テストケースをスキャンする
        scanProjectTestCase(newProjectName);
    }
    
    public void scanProjectTestCase(String projectName) {
        if (projectName == null) return; // プロジェクト未選択なら何もしない
        
        TestScanner.scanProjectTestCase(projectName, m_projectManager);
    }

    public String getSelectedProjectName() {
        return m_projComboContribution.getSelectedProjectName();
    }
}


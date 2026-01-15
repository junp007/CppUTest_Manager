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
import com.cpputest.manager.parser.TestScanner;
import com.cpputest.manager.view.TestResultView.TestCase;
import com.cpputest.manager.view.TestResultView.TestGroup;
import com.cpputest.manager.view.TestResultView.TestGroup.CheckState;

import java.io.Console;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TestResultView extends ViewPart {
    private ProjectComboContribution m_projComboContribution;
    private CheckboxTreeViewer m_treeViewer;
    private TestProject m_testProject = new TestProject();
    private Map<String, TestProject> m_testProjectMap = new HashMap<String, TestProject>();
    IActionBars toolbars;
    // テストケースのデータを保持する簡単な内部クラス
    public static class TestCase {
        // 属しているグループ
        private TestGroup group;
        // テスト名
        private String testName;
        // テスト結果が成功かどうか
        private boolean success;
        // テスト済みかどうか
        private boolean tested;
        // テスト対象かどうか
        private boolean checked;

        public TestCase(TestGroup group, String testName) {
          this.group = group;
          this.testName = testName;
          this.success = false;
          this.tested = false;
          this.checked = true;
        }

        public TestGroup getGroup() {
            return this.group;
        }

        public String getTestName() {
            return this.testName;
        }

        public String getFullName() {
            return this.group.getName() + "." + this.getTestName();
        }
        
        public void setChecked(boolean isChecked) {
            this.checked = isChecked;
        }

        public boolean isSameCase(final String groupName, final String testName) {
            return this.group.getName().equals(groupName) && this.testName.equals(testName);
        }
        
        public boolean isSuccess() {
            return tested && success;
        }
        
        public boolean isTested() {
            return tested;
        }
        
        public boolean isChecked() {
            return checked;
        }
    }

    public static class TestGroup {
        // テストグループ名
        private String name;
        // テストケースリスト
        List<TestCase> cases = new ArrayList<>();
        // 展開されているかどうか
        private boolean expand;
        
        public TestGroup(String name) {
            this.name = name;
            this.expand = true;
        }
        
        public String getName() {
            return name;
        }
        
        public List<TestCase> getCases() {
            return cases;
        }
        
        // グループ配下のテストケースすべてのチェック状態を設定する
        public void setChecked(boolean checked) {
            cases.stream().forEach(tc -> tc.setChecked(checked));
        }
        
        public enum CheckState {NonChecked, PartChecked, AllChecked};
        public CheckState getCheckState() {
            long checkedCount = cases.stream().filter(tc -> tc.isChecked()).count();

            if (checkedCount == 0) {
                // すべて未選択
                return CheckState.NonChecked;
            } else if (checkedCount == cases.size()) {
                // すべて選択
                return CheckState.AllChecked;
            } else {
                // 一部選択（グレー表示）
                return CheckState.PartChecked;
            }
        }
        
        public void setExpand(boolean isExpand) {
            expand = isExpand;
        }
        
        public boolean isExpand() {
            return expand;
        }
    }
    
    public static class TestProject implements Iterable<TestGroup> {
        // テストグループリスト
        private List<TestGroup> m_testGroups = new ArrayList<>();
        // 通知先のリスト
        private List<Runnable> listeners = new ArrayList<>();
        
        public TestProject() {
        }
        
        public TestProject(TestProject other) {
            this.m_testGroups = new ArrayList<TestGroup>(other.m_testGroups);
        }
        
        public void updateTestResult(String groupName, String testName, boolean isSuccess, boolean isTested) {
            // グループを探す
            TestGroup group = m_testGroups.stream()
                    .filter(g -> g.name.equals(groupName))
                    .findFirst().orElse(null);
            
            if (group == null) {
                // グループが見つからない場合は新しいグループを作成
                group = new TestGroup(groupName);
                m_testGroups.add(group);
            }
            
            // テストケースを探す
            TestCase target = group.cases.stream()
                    .filter(tc -> tc.testName.equals(testName))
                    .findFirst().orElse(null);
            
            if (target == null) {
                // テストケースが見つからない場合は新しいテストケースを作成
                target = new TestCase(group, testName);
                group.cases.add(target);
            }
            // テスト済みとして登録する場合は引数の値を設定する
            if (isTested) {
                target.success = isSuccess;
                target.tested = isTested;
            }
            
            // 変更を通知
            notifyChanged();
        }

        @Override
        public Iterator<TestGroup> iterator() {
            return m_testGroups.iterator();
        }
        
        public List<TestGroup> getTestGroups() {
            return m_testGroups;
        }
        
        public void clear() {
            m_testGroups.clear();
            notifyChanged();
        }
        
        public void copy(TestProject other) {
            m_testGroups.clear();
            m_testGroups.addAll(other.m_testGroups);
            notifyChanged();
        }
        
        public void addChangeListener(Runnable listener) {
            listeners.add(listener);
        }
        
        // データが更新されたときに呼ぶメソッド
        public void notifyChanged() {
            for (Runnable listener : listeners) {
                listener.run();
            }
        }
    }

    
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
                if (element instanceof TestGroup) return ((TestGroup) element).name;
                if (element instanceof TestCase) return ((TestCase) element).testName;
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
                    long successCount = group.getCases().stream().filter(tc -> tc.success).count();
                    int totalCount = group.getCases().size();
                    return String.format("(%d/%d)", successCount, totalCount);
                }
                if (element instanceof TestCase) {
                    TestCase tc = (TestCase)element;
                    if (tc.tested) {
                        return tc.success ? "Success" : "Failure";
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
        m_testProject.addChangeListener(() -> {
            // UIスレッドで実行する必要がある
            Display.getDefault().asyncExec(() -> {
                if (!m_treeViewer.getControl().isDisposed()) {
                    m_treeViewer.refresh();
                    // モデルのフラグに基づいて展開状態を復元
                    syncExpandState();
                }
            });
        });
    
        m_treeViewer.setInput(m_testProject);
    }
    
    private void syncExpandState() {
        // モデル（TestProjectなど）内の全グループをループ
        for (TestGroup group : m_testProject.getTestGroups()) {
            // 第1引数に要素、第2引数に展開(true)/折り畳み(false)
            m_treeViewer.setExpandedState(group, group.isExpand());
        }
    }
    
    // --- 背景色を決定する共通メソッド ---
    private Color getStatusColor(Object element) {
        if (element instanceof TestGroup) {
            TestGroup group = (TestGroup) element;
            // 1つでも失敗があるか
            boolean anyFailure = group.getCases().stream().anyMatch(tc -> tc.tested && !tc.success);
            // 全件成功しているか（実行済みかつ失敗なし）
            long successCount = group.getCases().stream().filter(tc -> tc.tested && tc.success).count();
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
            if (!tc.tested) {
                // 実行していない場合
                return null;
            } else if (tc.success) {
                // 成功
                return Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
            } else {
                // 失敗
                return Display.getDefault().getSystemColor(SWT.COLOR_RED);
            }
        }
        return null; // デフォルト（白など）
    }

    // 子要素の状態に基づいて、親グループのチェック・グレー表示状態を更新する
    private void updateGroupCheckState(TestGroup group) {
        List<TestCase> cases = group.getCases();
        long checkedCount = cases.stream().filter(tc -> m_treeViewer.getChecked(tc)).count();

        if (checkedCount == 0) {
            // すべて未選択
            m_treeViewer.setGrayChecked(group, false); // チェックなし、グレーなし
        } else if (checkedCount == cases.size()) {
            // すべて選択
            m_treeViewer.setGrayed(group, false);
            m_treeViewer.setChecked(group, true);
        } else {
            // 一部選択（グレー表示）
            m_treeViewer.setGrayChecked(group, true); // チェックあり、かつグレー状態
        }
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
        TestRunnerGenerator.generateCppUTestRun(projectName, checkedElements, m_testProject);
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
        m_instance.m_testProject.updateTestResult(groupName, testName, isSuccess, isTested);
    }
    
    // プロジェクトを変更、及び再スキャンするときに呼ばれる
    public void changeProject(String oldProjectName, String newProjectName) {
        if (newProjectName == null || newProjectName.isEmpty()) {
            // プロジェクト未選択なら何もしない
            return;
        }
        
        if (oldProjectName == newProjectName) {
            // プロジェクト変更ではなく、再スキャン時の処理
            
        } else {
            // プロジェクト変更時の処理
            
            if (oldProjectName != null && !oldProjectName.isEmpty()) {
                // 変更前のプロジェクトの情報を保存する
                m_testProjectMap.put(oldProjectName, new TestProject(m_testProject));
            }
            
            if (m_testProjectMap.containsKey(newProjectName)) {
                // 保存されているプロジェクトに変えた場合は保存されている情報で更新する
                m_testProject.copy(m_testProjectMap.get(newProjectName));
                m_testProject.forEach(tg -> {
                    m_treeViewer.expandToLevel(tg, tg.expand ? 1 : 0); // グループの展開状態をツリーに反映
                    tg.cases.forEach(tc -> m_treeViewer.setChecked(tc, tc.checked)); // グループ内の各チェック状態をツリーに反映
                    updateGroupCheckState(tg);  // グループのチェック状態を反映
                });
            }
            
            // 現在のテストケースをクリアする
            m_testProject.clear();
        }
        
        // テストケースをスキャンする
        scanProjectTestCase(newProjectName);
    }
    
    public void scanProjectTestCase(String projectName) {
        if (projectName == null) return; // プロジェクト未選択なら何もしない
        
        TestScanner.scanProjectTestCase(projectName, m_testProject);
    }

    public String getSelectedProjectName() {
        return m_projComboContribution.getSelectedProjectName();
    }
}


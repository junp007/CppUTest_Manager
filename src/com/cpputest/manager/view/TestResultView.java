package com.cpputest.manager.view;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestResultView extends ViewPart {
    private ProjectComboContribution m_projComboContribution;
    private CheckboxTreeViewer m_treeViewer;
    private List<TestGroup> m_testGroups = new ArrayList<>();
    private Map<String, List<TestGroup>> m_testGroupMap = new HashMap<String, List<TestGroup>>();
    IActionBars bars;
    // テストケースのデータを保持する簡単な内部クラス
    public static class TestCase {
        private TestGroup group;
        private String testName;
        private boolean success;
        private boolean tested;
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

        boolean IsSameCase(final String groupName, final String testName) {
            return this.group.getName().equals(groupName) && this.testName.equals(testName);
        }
    }

    public static class TestGroup {
        private String name;
        List<TestCase> cases = new ArrayList<>();
        private boolean expand;
        
        TestGroup(String name) {
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
            cases.stream().forEach(tc -> tc.checked = checked);
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
        // ... StatusのLabelProvider（色はここで設定）

        m_treeViewer.setContentProvider(new TestTreeContentProvider());
        m_treeViewer.setInput(m_testGroups);

        // チェック状態が変更されたときに呼ばれる
        m_treeViewer.addCheckStateListener(event -> {
            Object element = event.getElement();
            boolean checked = event.getChecked();

            if (element instanceof TestGroup) {
                // 1. 親（Group）が操作された場合、配下の子（TestCase）をすべて同じ状態にする
                // グレー表示を解除し、すべてチェックまたは未チェックに統一
                m_treeViewer.setGrayed(element, false); 
                m_treeViewer.setSubtreeChecked(element, checked);
                ((TestGroup)element).setChecked(checked);
            } else if (element instanceof TestCase) {
                // 2. 子（Case）が操作された場合、親（Group）の状態を再計算する
                TestCase tc = (TestCase) element;
                tc.checked = checked;
                updateGroupCheckState(tc.getGroup());
            }
        });
        
        m_treeViewer.addTreeListener(new ITreeViewerListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                // グループが開かれた時の処理
                Object element = event.getElement();
                if (element instanceof TestGroup) {
                    TestGroup group = (TestGroup) element;
                    group.expand = true;
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                // グループが閉じられた時の処理
                Object element = event.getElement();
                if (element instanceof TestGroup) {
                    TestGroup group = (TestGroup) element;
                    group.expand = false;
                }
            }
        });
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
                bars.updateActionBars();
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
        bars = getViewSite().getActionBars();
        IToolBarManager toolbarManager = bars.getToolBarManager();
        
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
        
        bars.updateActionBars();
        
        if (getSelectedProjectName() != null) {
            scanProjectTestCase(getSelectedProjectName());
        }
    }
    

    private void generateCppUTestRun(String projectName) {
        // チェックされている項目を取得
        Object[] checkedElements = m_treeViewer.getCheckedElements();
        // CppUtestRunファイルを生成
        TestRunnerGenerator.generateCppUTestRun(projectName, checkedElements, m_testGroups);
    }

    @Override
    public void setFocus() {
        m_treeViewer.getControl().setFocus();
    }

    private static TestResultView m_instance;

    public TestResultView() {
        m_instance = this;
    }

    // テスト項目を更新する
    public static void updateTestResult(final String groupName, final String testName, final boolean isSuccess, final boolean isTested) {
        if (m_instance == null)
            return;

        Display.getDefault().asyncExec(() -> {
            // グループを探す
            TestGroup group = m_instance.m_testGroups.stream()
                    .filter(g -> g.name.equals(groupName))
                    .findFirst().orElse(null);
            
            if (group == null) {
                group = new TestGroup(groupName);
                m_instance.m_testGroups.add(group);
                m_instance.m_treeViewer.refresh(); // 新しいグループが出たので全体更新
            }
            
            // テストを探す
            TestCase target = group.cases.stream()
                    .filter(tc -> tc.testName.equals(testName))
                    .findFirst().orElse(null);
            
            if (target == null) {
                target = new TestCase(group, testName);
                group.cases.add(target);
                m_instance.m_treeViewer.refresh(group); // グループ配下を更新
                m_instance.m_treeViewer.expandToLevel(group, 1); // 自動で展開
                m_instance.m_treeViewer.setChecked(target, target.checked);  // テストケースのチェック状態を反映
                m_instance.updateGroupCheckState(group);    // グループのチェック状態を確認
            }
            // テスト済みとして登録する場合は引数の値を設定する
            if (isTested) {
                target.success = isSuccess;
                target.tested = isTested;
            }

            m_instance.m_treeViewer.update(new Object[] {target, target.group}, null);
        });
    }
    
    // コンボボックスでプロジェクトが変更されたときに呼ばれる
    public void changeProject(String oldProjectName, String newProjectName) {
        if (oldProjectName != null && !oldProjectName.isEmpty()) {
            // 変更前のプロジェクトの情報を保存する
            m_testGroupMap.put(oldProjectName, new ArrayList<TestGroup>(m_testGroups));
        }
        
        // 現在表示されているデータをクリアする
        m_testGroups.clear();
        m_treeViewer.refresh();
        
        if (m_testGroupMap.containsKey(newProjectName)) {
            // 保存されているプロジェクトに変えた場合は保存されている情報で更新する
            m_testGroups.addAll(m_testGroupMap.get(newProjectName));
            m_treeViewer.refresh();
            m_testGroups.forEach(tg -> {
                m_treeViewer.expandToLevel(tg, tg.expand ? 1 : 0); // グループの展開状態をツリーに反映
                tg.cases.forEach(tc -> m_treeViewer.setChecked(tc, tc.checked)); // グループ内の各チェック状態をツリーに反映
                updateGroupCheckState(tg);  // グループのチェック状態を反映
            });
        }
        
        // テストケースをスキャンする
        scanProjectTestCase(newProjectName);
    }
    
    public void scanProjectTestCase(String projectName) {
        if (projectName == null) return; // プロジェクト未選択なら何もしない
        
        TestScanner.scanProjectTestCase(projectName);
    }

    public String getSelectedProjectName() {
        return m_projComboContribution.getSelectedProjectName();
    }
}


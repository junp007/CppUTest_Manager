package com.example.cpputest.view;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;

import com.example.cpputest.TestRunnerGenerator;
import com.example.cpputest.parser.TestScanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TestResultView extends ViewPart {
//    public static final String ID = "com.example.cpputest.view.TestResultView";
    private ComboViewer m_projectCombo;
    private org.eclipse.jface.action.Action m_scanAction;
    private org.eclipse.jface.action.Action m_runAction;
    private CheckboxTreeViewer m_viewer;
    private List<TestGroup> m_testGroups = new ArrayList<>();
    
    // テストケースのデータを保持する簡単な内部クラス
    public static class TestCase {
        private TestGroup group;
        private String testName;
        private boolean success;
        private boolean tested;

        public TestCase(TestGroup group, String testName) {
          this.group = group;
          this.testName = testName;
          this.success = false;
          this.tested = false;
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
        
        TestGroup(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
        
        public List<TestCase> getCases() {
            return cases;
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
        m_viewer = new CheckboxTreeViewer(parent, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION | SWT.CHECK);
        m_viewer.getTree().setHeaderVisible(true);
        m_viewer.getTree().setLinesVisible(true);
        m_viewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));

        // 列の設定（1列目：Name）
        TreeViewerColumn colName = new TreeViewerColumn(m_viewer, SWT.NONE);
        colName.getColumn().setWidth(300);
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
        TreeViewerColumn colStatus = new TreeViewerColumn(m_viewer, SWT.NONE);
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

        m_viewer.setContentProvider(new TestTreeContentProvider());
        m_viewer.setInput(m_testGroups);

        // チェック状態が変更されたときに呼ばれる
        m_viewer.addCheckStateListener(event -> {
            Object element = event.getElement();
            boolean checked = event.getChecked();

            if (element instanceof TestGroup) {
                // 1. 親（Group）が操作された場合、配下の子（TestCase）をすべて同じ状態にする
                // グレー表示を解除し、すべてチェックまたは未チェックに統一
                m_viewer.setGrayed(element, false); 
                m_viewer.setSubtreeChecked(element, checked);
            } else if (element instanceof TestCase) {
                // 2. 子（Case）が操作された場合、親（Group）の状態を再計算する
                TestCase tc = (TestCase) element;
                updateGroupCheckState(tc.getGroup());
            }
        });
    }
    
    // --- 背景色を決定する共通メソッド ---
    private Color getStatusColor(Object element) {
        if (element instanceof TestGroup) {
            TestGroup group = (TestGroup) element;
            // 1つでも失敗があるか
            boolean anyFailure = group.getCases().stream().anyMatch(tc -> !tc.success);
            // 全件成功しているか（実行済みかつ失敗なし）
            long successCount = group.getCases().stream().filter(tc -> tc.success).count();
            long testedCount = group.getCases().stream().filter(tc -> tc.tested).count();
            boolean allSuccess = (successCount == group.getCases().size() && successCount > 0);

            if (testedCount == 0) {
                // 1つも実行していない場合
                return null;
            } else if (anyFailure) {
                // 実行済みかつ失敗あり
                return Display.getDefault().getSystemColor(SWT.COLOR_RED);
            } else if (allSuccess) {
                // 実行済みかつ全部成功
                return Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
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
        long checkedCount = cases.stream().filter(tc -> m_viewer.getChecked(tc)).count();

        if (checkedCount == 0) {
            // すべて未選択
            m_viewer.setGrayChecked(group, false); // チェックなし、グレーなし
        } else if (checkedCount == cases.size()) {
            // すべて選択
            m_viewer.setGrayed(group, false);
            m_viewer.setChecked(group, true);
        } else {
            // 一部選択（グレー表示）
            m_viewer.setGrayChecked(group, true); // チェックあり、かつグレー状態
        }
    }

    private LaunchConfigurationMenuCreator menuCreator;
    private String runActionTooltipBase = "Run selected CppUTest cases";
    // ツールバーを作成
    private void createToolbar() {
        // 実行ボタンのアクションを定義
        m_runAction = new org.eclipse.jface.action.Action("Run", Action.AS_DROP_DOWN_MENU) {
            @Override
            public void run() {
                String projectName = getSelectedProjectName();
                if (projectName == null) return; // プロジェクト未選択なら何もしない

                // チェックされている項目を取得
                Object[] checkedElements = m_viewer.getCheckedElements();
                // mainファイルを生成
                TestRunnerGenerator.generateMain(projectName, checkedElements, m_testGroups);
                
                // 最後に選んだ構成があればそれを使う、なければ自動で探す
                ILaunchConfiguration config = menuCreator.getLastSelectedConfig();
                if (config == null) {
                    // 最後に選んだ構成が無ければプロジェクト名で構成を探す
                    config = findConfiguration(projectName);
                }
                launchTests(config, projectName);
            }
        };

        // アイコンの設定
//        runAction.setImageDescriptor(org.eclipse.ui.PlatformUI.getWorkbench().getSharedImages().
//        		getImageDescriptor(org.eclipse.ui.ISharedImages.IMG_ETOOL_SAVEAS_EDIT));

        m_scanAction = new org.eclipse.jface.action.Action("Scan") {
            @Override
            public void run() {
                String projectName = getSelectedProjectName();
                if (projectName == null) return; // プロジェクト未選択なら何もしない
                
                TestScanner.scanProject(projectName);
            }
        };

        m_runAction.setToolTipText(runActionTooltipBase);
        m_scanAction.setToolTipText("Scan CppUTest cases");
        
        // ビューのツールバーにボタンを追加
        IActionBars bars = getViewSite().getActionBars();
        IToolBarManager toolbarManager = bars.getToolBarManager();
        
        // プロジェクト選択コンボボックスを最初に追加
        ProjectComboContribution comboContribution = new ProjectComboContribution("projectSelector");
        
        // ▼ メニューの紐付け
        menuCreator = new LaunchConfigurationMenuCreator(this);
        m_runAction.setMenuCreator(menuCreator);
        
        toolbarManager.add(comboContribution);
        // セパレーター（区切り線）を入れる
        toolbarManager.add(new Separator());
        
        toolbarManager.add(m_scanAction);
        toolbarManager.add(m_runAction);
        
        bars.updateActionBars();
        
        if (getSelectedProjectName() != null) {
            m_scanAction.run(); // Scanボタンの処理を直接呼び出す
        }
    }
    
    // 現在選択されているプロジェクト名を取得するメソッド
    private String getSelectedProjectName() {
        // インスタンス化前や破棄後に呼ばれても落ちないようにする
        if (m_projectCombo == null || m_projectCombo.getControl().isDisposed()) {
            return null;
        }
        IStructuredSelection selection = (IStructuredSelection) m_projectCombo.getSelection();
        IProject project = (IProject) selection.getFirstElement();
        return (project != null) ? project.getName() : null;
    }

    @Override
    public void setFocus() {
        m_viewer.getControl().setFocus();
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
                m_instance.m_viewer.refresh(); // 新しいグループが出たので全体更新
            }
            
            // テストを探す
            TestCase target = group.cases.stream()
                    .filter(tc -> tc.testName.equals(testName))
                    .findFirst().orElse(null);
            
            if (target == null) {
                target = new TestCase(group, testName);
                group.cases.add(target);
                m_instance.m_viewer.refresh(group); // グループ配下を更新
                m_instance.m_viewer.setChecked(target,  true);
                m_instance.updateGroupCheckState(group);
                m_instance.m_viewer.expandToLevel(group, 1); // 自動で展開
            }
            // テスト済みとして登録する場合は引数の値を設定する
            if (isTested) {
                target.success = isSuccess;
                target.tested = isTested;
            }

            m_instance.m_viewer.update(new Object[] {target, target.group}, null);
        });
    }

    // コンボボックスに表示されるプロジェクトリストを更新
    private void refreshProjectList() {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        // オープンされているプロジェクトのみに絞り込む
        List<IProject> openProjects = Arrays.stream(projects)
                .filter(IProject::isOpen)
                .collect(Collectors.toList());
        
        m_projectCombo.setInput(openProjects);
        
        if (openProjects != null && !openProjects.isEmpty()) {
            // 最初のプロジェクトを選択状態にする
            m_projectCombo.setSelection(new StructuredSelection(openProjects.get(0)));
        }
    }

    // テストを実行する
    private void launchTests(ILaunchConfiguration targetConfig, String projectName) {
        try {
            if (targetConfig != null) {
                // デバッグモードで起動 (ILaunchManager.DEBUG_MODE)
                // 第2引数は true にすると、起動前にビルドを実行してくれます
                DebugUITools.launch(targetConfig, ILaunchManager.DEBUG_MODE);
                m_runAction.setToolTipText(runActionTooltipBase + " with " + targetConfig.getName());
            } else {
                System.err.println("No Launch Configuration found for: " + projectName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private ILaunchConfiguration findConfiguration(String projectName) {
        ILaunchConfiguration targetConfig = null;
        try {
            ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
            ILaunchConfiguration[] configs = manager.getLaunchConfigurations();
            
    
            // プロジェクト名が含まれるデバッグ構成を探す
            for (ILaunchConfiguration config : configs) {
                // e2 studioの標準的な構成名や、属性からプロジェクト名をチェック
                String name = config.getName();
                if (name.contains(projectName)) {
                    targetConfig = config;
                    break;
                }
            }
        } catch (CoreException e) {
            e.printStackTrace();
        }
        
        return targetConfig;
    }

    // ツールバーにコンボボックスを埋め込むためのクラス
    private class ProjectComboContribution extends ControlContribution {
        protected ProjectComboContribution(String id) {
            super(id);
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
                if (m_scanAction != null) {
                    m_scanAction.run(); // 切り替え時もScanボタンのrunを呼ぶ
                }
            });

            return combo;
        }
    }

    // デバッグ構成をリストアップしてメニュー（▼の中身）を生成するクラス
    private class LaunchConfigurationMenuCreator implements IMenuCreator {
        private Menu menu;
        private TestResultView view; // View本体を参照
        private ILaunchConfiguration lastSelectedConfig; // 最後に選んだ構成を保持

        public LaunchConfigurationMenuCreator(TestResultView view) {
            this.view = view;
        }

        @Override
        public void dispose() {
            if (menu != null) {
                menu.dispose();
                menu = null;
            }
        }

        @Override
        public Menu getMenu(Control parent) {
            if (menu != null) menu.dispose();
            menu = new Menu(parent);

            // メニューが開かれた時点でプロジェクト名を取得する
            String projectName = view.getSelectedProjectName();
            if (projectName == null) return menu; 

            try {
                ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
                ILaunchConfiguration[] configs = manager.getLaunchConfigurations();

                for (final ILaunchConfiguration config : configs) {
                    String configProj = config.getAttribute("org.eclipse.cdt.launch.PROJECT_ATTR", "");
                    if (configProj.equals(projectName) || config.getName().contains(projectName)) {
                     // 各デバッグ構成をメニュー項目（Action）として追加
                        Action configAction = new Action(config.getName()) {
                            @Override
                            public void run() {
                                lastSelectedConfig = config;
                                // 実行ロジックを呼び出す（後述のメインActionのrun相当）
                                launchTests(config, configProj);
                            }
                        };
                        ActionContributionItem item = new ActionContributionItem(configAction);
                        item.fill(menu, -1);
                    }
                }
            } catch (CoreException e) {
                e.printStackTrace();
            }
            return menu;
        }

        @Override
        public Menu getMenu(Menu parent) { return null; }
        
        public ILaunchConfiguration getLastSelectedConfig() {
            return lastSelectedConfig;
        }
    }
}


package com.cpputest.manager.view;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.IFileEditorInput;

import com.cpputest.manager.CppUTestSetupHandler;
import com.cpputest.manager.TestRunnerGenerator;
import com.cpputest.manager.model.TestCase;
import com.cpputest.manager.model.TestGroup;
import com.cpputest.manager.model.TestProjectManager;
import com.cpputest.manager.model.TestGroup.CheckState;
import com.cpputest.manager.parser.TestScanner;

import java.io.Console;

public class TestResultView extends ViewPart {
//    private ProjectComboContribution m_projComboContribution;
    private TestTreeViewer m_treeViewer;
    private TestProjectManager m_projectManager = new TestProjectManager();
    private IActionBars m_toolbars;
    private Label m_projectLabel;

    private ISelectionListener selectionListener;
    private IDebugEventSetListener debugListener;

    @Override
    public void createPartControl(Composite parent) {
        // 1. UIの作成
        m_treeViewer = new TestTreeViewer(parent, m_projectManager);
        // 2. ツールバーの作成
        createToolbar();
        
        // プロジェクト変更時のリスナーの登録
        settingSelectionListener();
        
        // projectManagerのリスナーの登録
        settingProjectManagerListener();
    }
    
    @Override
    public void dispose() {
        if (selectionListener != null) {
            getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(selectionListener);
        }
        super.dispose();
    }
    
    private void settingProjectManagerListener() {
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
    }
    
    private void settingSelectionListener() {
        // プロジェクトが変更されたときのリスナー
        selectionListener = (part, selection) -> {
            if (part == TestResultView.this) return;
            
            IProject project = extractProject(selection);

            // 選択から取れず、かつアクティブなのがエディタの場合
            if (project == null && part instanceof IEditorPart) {
                IEditorInput input = ((IEditorPart)part).getEditorInput();
                if (input instanceof IFileEditorInput) {
                    project = ((IFileEditorInput) input).getFile().getProject();
                }
            }
            
            if (project != null && project.getName() != m_projectManager.getCurrentProjectName()) {
                // 表示するプロジェクトを切り替えてリフレッシュ
                System.out.println("Selected Project: " + project.getName());
                updateProjectDisplay(project);
            }
        };
         // プロジェクトが変更されたときのリスナー登録
        getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(selectionListener);
        
        
        debugListener = new IDebugEventSetListener() {
            @Override
            public void handleDebugEvents(DebugEvent[] events) {
                for (DebugEvent event : events) {
                    // 新しい「作成(CREATE)」イベントかどうか かつ ソースが IProcess (実行プロセス) の場合
                    if (event.getKind() == DebugEvent.CREATE && event.getSource() instanceof IProcess) {
                        IProcess process = (IProcess) event.getSource();
                        ILaunch launch = process.getLaunch();
                        
                        // プロジェクトを特定
                        IProject project = getProjectFromLaunch(launch);
                        if (project == null) {
                            // 無ければ無視
                            continue;
                        }
                        // デバッグ中のプロジェクト名を設定
                        m_projectManager.setCurrentDebuggingProjectName(project.getName());
                        if (project.getName() != m_projectManager.getCurrentProjectName()) {
                            // プロジェクト名が変わっていればUIスレッドに切り替えて表示を更新
                            Display.getDefault().asyncExec(() -> {
                                updateProjectDisplay(project);
                            });
                        }
                    }
                }
            }
        };
        // デバッグ実行時のリスナー登録
        DebugPlugin.getDefault().addDebugEventListener(debugListener);
    }
    
    // Launch構成からプロジェクトを抽出する補助メソッド
    private IProject getProjectFromLaunch(ILaunch launch) {
        try {
            ILaunchConfiguration config = launch.getLaunchConfiguration();
            // CDT (C/C++ Development Tooling) のプロジェクト属性名
            String projectName = config.getAttribute("org.eclipse.cdt.launch.PROJECT_ATTR", (String) null);
            
            if (projectName != null) {
                return ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
            }
        } catch (CoreException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    // プロジェクトの表示更新
    private void updateProjectDisplay(IProject project) {
        if (project == null) {
            // Do nothing if project is null.
            return;
        }
        String newProjectName = project.getName();

        // プロジェクトの変更
        m_projectManager.changeProject(newProjectName);
        
        if (m_projectManager.getCurrentProject().isEmpty()) {
            // 変更後のプロジェクトが空のプロジェクトならテストケースのスキャンを行う
            scanProjectTestCase(newProjectName);
        }
        
        // プロジェクト名表示のラベルを更新
        if (m_projectLabel != null && !m_projectLabel.isDisposed()) {
            m_projectLabel.setText("Project: " + newProjectName);
            // 文字列の長さに合わせてツールバーの幅を再計算させる
            m_projectLabel.getParent().layout(true);
            m_toolbars.updateActionBars();
        }
    }
    
    // プロジェクトを抽出する補助メソッド
    private IProject extractProject(ISelection selection) {
        if (selection instanceof IStructuredSelection && !selection.isEmpty()) {
            Object firstElement = ((IStructuredSelection) selection).getFirstElement();
            if (firstElement instanceof IProject) {
                return (IProject) firstElement;
            } else if (firstElement instanceof IAdaptable) {
                return (IProject) ((IAdaptable) firstElement).getAdapter(IProject.class);
            }
        }
        return null;
    }

    private void syncExpandState() {
        // モデル（TestProjectなど）内の全グループをループ
        for (TestGroup group : m_projectManager.getTestGroups()) {
            // 第1引数に要素、第2引数に展開(true)/折り畳み(false)
            m_treeViewer.setExpandedState(group, group.isExpand());
        }
    }

    // ツールバーを作成
    private void createToolbar() {
        // CppUtestRun.cppを生成ボタンのアクション
        org.eclipse.jface.action.Action generateAction = new org.eclipse.jface.action.Action("Generate") {
            @Override
            public void run() {
                String projectName = m_projectManager.getCurrentProjectName();
                if (projectName == null || projectName.isEmpty()) {
                    return; // プロジェクト未選択なら何もしない
                }

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
                String projectName = m_projectManager.getCurrentProjectName();
                if (projectName == null || projectName.isEmpty()) {
                    return; // プロジェクト未選択なら何もしない
                }

                boolean confirm = MessageDialog.openQuestion(getViewSite().getShell(), 
                    "Setting", "プロジェクト '" + projectName + "' に CppUTest の初期設定を行いますか？");

                if (confirm) {
                    try {
                        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
                        // resourcesフォルダ内のファイルを全部対象プロジェクトにコピーする
                        CppUTestSetupHandler.applyCppUTestSetting(project);
                        // CppUtestRunファイルを生成
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
                scanProjectTestCase(m_projectManager.getCurrentProjectName());
                m_toolbars.updateActionBars();
            }
        };
        
        // テスト結果削除ボタンのアクション
        org.eclipse.jface.action.Action clearResultAllAction = new org.eclipse.jface.action.Action("ClearAll") {
            @Override
            public void run() {
                m_projectManager.clearCurrentProjectTestedFlag();
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
        clearResultAllAction.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin(
                "org.eclipse.ui", "icons/full/etool16/clear.png"));
          
        scanProjectAction.setToolTipText("プロジェクトのスキャン");
        setupAction.setToolTipText("CppUTest用の設定");
        generateAction.setToolTipText("CppUTestRun.cppを生成");
        clearResultAllAction.setToolTipText("テスト結果をクリア");
        
        // ビューのツールバーにボタンを追加
        m_toolbars = getViewSite().getActionBars();
        IToolBarManager toolbarManager = m_toolbars.getToolBarManager();
        
        toolbarManager.add(new ControlContribution("projectLabelId") {
            @Override
            protected Control createControl(Composite parent) {
                // ラベルを載せるためのコンテナを作成
                Composite container = new Composite(parent, SWT.NONE);
                
                // レイアウトをGridLayoutに設定（上下の余白を自動調整させる）
                GridLayout layout = new GridLayout(1, false);
                layout.marginHeight = 3;
                layout.marginWidth = 5;
                container.setLayout(layout);
                
                // ラベルを作成
                m_projectLabel = new Label(container, SWT.NONE);
                m_projectLabel.setText("Project: None");
                
                // ラベルをコンテナ内の垂直中央に配置する設定
                m_projectLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, true));
                
                return container;
            }
        });
        // CppUTestセットアップボタン
        toolbarManager.add(setupAction);
        // セパレーター（区切り線）
        toolbarManager.add(new Separator());
        // Scanボタン
        toolbarManager.add(scanProjectAction);
        // Clearボタン
        toolbarManager.add(clearResultAllAction);
        // セパレーター（区切り線）
        toolbarManager.add(new Separator());
        // Generateボタン
        toolbarManager.add(generateAction);
        
        m_toolbars.updateActionBars();
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
        m_instance.m_projectManager.updateTestResult(groupName, testName, isSuccess, isTested, "", 0);
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
    
    // テストケースをスキャンする
    public void scanProjectTestCase(String projectName) {
        if (projectName == null) return; // プロジェクト未選択なら何もしない
        
        System.out.println("Scan Project: " + projectName);
        TestScanner.scanProjectTestCase(projectName, m_projectManager);
    }

}

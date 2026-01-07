package com.example.cpputest.view;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;

// デバッグ構成をリストアップしてメニュー（▼の中身）を生成するクラス
public class LaunchConfigurationMenuCreator implements IMenuCreator {
    private Menu m_menu;
    private TestResultView m_view; // View本体を参照
    private ILaunchConfiguration m_lastSelectedConfig; // 最後に選んだ構成を保持

    public LaunchConfigurationMenuCreator(TestResultView view) {
        this.m_view = view;
    }

    @Override
    public void dispose() {
        if (m_menu != null) {
            m_menu.dispose();
            m_menu = null;
        }
    }

    @Override
    public Menu getMenu(Control parent) {
        if (m_menu != null) m_menu.dispose();
        m_menu = new Menu(parent);

        // メニューが開かれた時点でプロジェクト名を取得する
        String projectName = m_view.getSelectedProjectName();
        if (projectName == null) return m_menu; 

        try {
            ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
            ILaunchConfiguration[] configs = manager.getLaunchConfigurations();

            for (final ILaunchConfiguration config : configs) {
                String configProj = config.getAttribute("org.eclipse.cdt.launch.PROJECT_ATTR", "");
                if (configProj.equals(projectName)) {
                 // 各デバッグ構成をメニュー項目（Action）として追加
                    Action configAction = new Action(config.getName()) {
                        @Override
                        public void run() {
                            m_lastSelectedConfig = config;
                            // 実行ロジックを呼び出す（後述のメインActionのrun相当）
                            m_view.launchTests(config, configProj);
                        }
                    };
                    ActionContributionItem item = new ActionContributionItem(configAction);
                    item.fill(m_menu, -1);
                }
            }
        } catch (CoreException e) {
            e.printStackTrace();
        }
        return m_menu;
    }

    @Override
    public Menu getMenu(Menu parent) { return null; }
    
    public ILaunchConfiguration getLastSelectedConfig() {
        return m_lastSelectedConfig;
    }
}

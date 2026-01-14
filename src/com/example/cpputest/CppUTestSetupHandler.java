package com.example.cpputest;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.*;
import org.eclipse.cdt.managedbuilder.core.*;

public class CppUTestSetupHandler {

    public static void applyCppUTestSetting(IProject project) throws Exception {
        copyCppUTestSources(project);
        configureProjectSettings(project);
        configureLibraryGenerator(project);
    }
    
    private static void copyCppUTestSources(IProject project) throws Exception {
        // 1. プラグイン内のソース場所を特定
        Bundle bundle = FrameworkUtil.getBundle(CppUTestSetupHandler.class);
        URL installUrl = FileLocator.find(bundle, new Path("resources"), null);
        URL localUrl = FileLocator.toFileURL(installUrl);
        File srcDir = new File(localUrl.getPath());

        // 2. コピー先（プロジェクト直下の cpputest フォルダ）
        IFolder destFolder = project.getFolder("src");
        if (!destFolder.exists()) {
            destFolder.create(true, true, null);
        }

        // 3. 再帰的にコピー
        copyRecursive(srcDir, destFolder);
        
        // 4. Eclipseのリフレッシュ
        project.refreshLocal(IProject.DEPTH_INFINITE, null);
    }

    private static void copyRecursive(File src, IContainer dest) throws Exception {
        for (File file : src.listFiles()) {
            if (file.isDirectory()) {
                IFolder newFolder = dest.getFolder(new Path(file.getName()));
                if (!newFolder.exists()) newFolder.create(true, true, null);
                copyRecursive(file, newFolder);
            } else {
                IFile newFile = dest.getFile(new Path(file.getName()));
                try (FileInputStream is = new FileInputStream(file)) {
                    if (newFile.exists()) {
                        newFile.setContents(is, true, true, null);
                    } else {
                        newFile.create(is, true, null);
                    }
                }
            }
        }
    }
    
    private static void configureProjectSettings(IProject project) throws CoreException {
        // プロジェクト記述の取得（書き換え用）
        ICProjectDescription desc = CoreModel.getDefault().getProjectDescription(project, true);
        
        // 全ての構成（Debug/Releaseなど）に対して設定を適用
        ICConfigurationDescription[] configs = desc.getConfigurations();
        for (ICConfigurationDescription config : configs) {
            ICFolderDescription folderDesc = config.getRootFolderDescription();
            ICLanguageSetting[] langSettings = folderDesc.getLanguageSettings();

            for (ICLanguageSetting lang : langSettings) {
                String languageId = lang.getLanguageId();
                // CおよびC++のソースに対して適用
                if (languageId != null && (languageId.contains("gcc") || languageId.contains("g++"))) {
                    // --- インクルードパスの追加 ---
                    List<ICLanguageSettingEntry> entries = lang.getSettingEntriesList(ICSettingEntry.INCLUDE_PATH);
                    // プロジェクト相対パスで "${ProjName}/src/cpputest/include" を追加
                    AddSettingIfNotExist(entries, new CIncludePathEntry("/${ProjName}/src/cpputest/include", ICSettingEntry.VALUE_WORKSPACE_PATH));
                    AddSettingIfNotExist(entries, new CIncludePathEntry("/${ProjName}/src/cpputest_gen", ICSettingEntry.VALUE_WORKSPACE_PATH));
                    AddSettingIfNotExist(entries, new CIncludePathEntry("/${ProjName}/src/TargetCode", ICSettingEntry.VALUE_WORKSPACE_PATH));
                    lang.setSettingEntries(ICSettingEntry.INCLUDE_PATH, entries);

                    // --- プリプロセッサマクロの追加 ---
                    List<ICLanguageSettingEntry> macros = lang.getSettingEntriesList(ICSettingEntry.MACRO);
                    AddSettingIfNotExist(macros, new CMacroEntry("CPPUTEST_STD_CPP_LIB_DISABLED", "", 0));
                    AddSettingIfNotExist(macros, new CMacroEntry("CPPUTEST_MEM_LEAK_DETECTION_DISABLED", "", 0));
                    AddSettingIfNotExist(macros, new CMacroEntry("CPPUTEST_HAVE_EXCEPTIONS", "0", 0));
                    AddSettingIfNotExist(macros, new CMacroEntry("CPPUTEST_HAVE_FENV", "0", 0));
                    lang.setSettingEntries(ICSettingEntry.MACRO, macros);
                }
            }
        }
        
        // 変更を保存
        CoreModel.getDefault().setProjectDescription(project, desc);
    }
    
    // 指定した名前の設定項目が無い場合だけ設定を追加する
    private static void AddSettingIfNotExist(List<ICLanguageSettingEntry> entries, ICLanguageSettingEntry newSetting) {
        boolean isMatch = entries.stream().anyMatch(e -> e.getName().equals(newSetting.getName()));
        if (!isMatch) {
            entries.add(newSetting);
        }
    }
    
    // Library Generatorの設定を更新する
    public static void configureLibraryGenerator(IProject project) throws CoreException, BuildException {
        // 書き込み可能なプロジェクト記述を取得
        IManagedProject managedProj = ManagedBuildManager.getBuildInfo(project).getManagedProject();
        IConfiguration[] configs = managedProj.getConfigurations();

        for (IConfiguration config : configs) {
            IToolChain toolChain = config.getToolChain();
            // Library Generator ツールを取得
            ITool libGenTool = null;
            for (ITool tool : toolChain.getTools()) {
                // IDに "libgen" を含むツールを探す（RX/RL78/RH850等で共通のキーワード）
                if (tool.getId().contains("libgen") || tool.getName().contains("Library Generator")) {
                    libGenTool = tool;
                    break;
                }
            }

            if (libGenTool != null) {
                // math.h, mathf.h を有効化
                setOption(config, libGenTool, "math", true);
                setOption(config, libGenTool, "mathf", true);
            }
        }
        // 変更を保存
        ManagedBuildManager.saveBuildInfo(project, true);
    }

    // ツールオプションを名前(キーワード)で探して真偽値を設定するヘルパー
    private static void setOption(IConfiguration config, ITool tool, String keyword, boolean value) throws BuildException {
        IOption[] options = tool.getOptions();
        for (IOption opt : options) {
            // オプションのIDまたは名前にキーワードが含まれているかチェック
            if (opt.getId().toLowerCase().contains(keyword.toLowerCase()) || 
                opt.getName().toLowerCase().contains(keyword.toLowerCase())) {
                
                if (opt.getValueType() == IOption.BOOLEAN) {
                    ManagedBuildManager.setOption(config, tool, opt, value);
                }
            }
        }
    }
}
package com.cpputest.manager;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.*;
import org.eclipse.cdt.managedbuilder.core.*;

public class CppUTestSetupHandler {

    public static void applyCppUTestSetting(String projectName) throws Exception {
        // プロジェクトを取得
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        // CppUTestのソースをプロジェクト内にコピー
        copyCppUTestSources(project);
        // コンパイラの設定を行う
        configureCompilerSettings(project);
        // リンカの設定を行う
        configureLinkerSettings(project);
        // Library Generatorの設定を行う
        configureLibraryGenerator(project);
    }
    
    // CppUTestのソースをプロジェクト内にコピー
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
    
    // コンパイラの設定を行う
    private static void configureCompilerSettings(IProject project) throws CoreException {
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
    
    // リンカの設定を行う
    private static void configureLinkerSettings(IProject project) {
        IManagedBuildInfo buildInfo = ManagedBuildManager.getBuildInfo(project);
        if (buildInfo == null || buildInfo.getManagedProject() == null) {
            return;
        }

        ToolchainType toolchainType = getToolchainType(project);
        
        IConfiguration[] managedConfigs = buildInfo.getManagedProject().getConfigurations();
        for (IConfiguration mConfig : managedConfigs) {
            ITool linker = mConfig.calculateTargetTool();
            if (linker == null) continue;

            if (toolchainType == ToolchainType.GCC_ARM) {
                // --- GCCの設定 (以前の実装) ---
                applyGccLinkerFlags(mConfig, linker);
            } else if (toolchainType == ToolchainType.LLVM_ARM) {
                // --- LLVMの設定 (アーカイブファイルの操作) ---
                applyLlvmArchiveSettings(mConfig, linker);
            }
        }
        ManagedBuildManager.saveBuildInfo(project, true);
    }
    
    private static void applyGccLinkerFlags(IConfiguration config, ITool linker) {
        for (IOption option : linker.getOptions()) {
            String optionId = option.getId();
            if (optionId.contains("option.cpp.linker.other")) {
                try {
                    String currentFlags = option.getStringValue();
                    String flagToAdd = "--specs=rdimon.specs";
                    
                    if (!currentFlags.contains(flagToAdd)) {
                        String newFlags = currentFlags.isEmpty() ? flagToAdd : currentFlags + " " + flagToAdd;
                        ManagedBuildManager.setOption(config, linker, option, newFlags);
                    }
                } catch (BuildException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private static void applyLlvmArchiveSettings(IConfiguration config, ITool linker) {
        // LLVMのアーカイブファイル設定用オプションID (RA LLVM Toolchain)
        // 一般的に "linker.cpp.option.archives" やそれに類するIDです
        String[] archiveOptionIds = {"com.renesas.cdt.managedbuild.llvm.core.option.linker.archives.archiveLibraryFiles"};

        for (String archiveOptionId : archiveOptionIds) {
            IOption archiveOption = linker.getOptionBySuperClassId(archiveOptionId);
            if (archiveOption != null) {
                try {
                    // 現在のリストを取得
                    String[] currentArchives = archiveOption.getBasicStringListValue();
                    List<String> archiveList = new ArrayList<>(Arrays.asList(currentArchives));
    
                    boolean changed = false;
    
                    // "crt0" を "crt0-semihost" に置き換え
                    if (archiveList.contains("crt0")) {
                        int index = archiveList.indexOf("crt0");
                        archiveList.set(index, "crt0-semihost");
                        changed = true;
                    } else if (!archiveList.contains("crt0-semihost")) {
                        archiveList.add(0, "crt0-semihost"); // 先頭付近が望ましい
                        changed = true;
                    }
    
                    // "semihost" を追加
                    if (!archiveList.contains("semihost")) {
                        archiveList.add("semihost");
                        changed = true;
                    }
    
                    // 変更があれば適用
                    if (changed) {
                        ManagedBuildManager.setOption(config, linker, archiveOption, archiveList.toArray(new String[0]));
                    }
                } catch (BuildException e) {
                    e.printStackTrace();
                }
            }
        }

        
        // リンカーエラー回避フラグの追加 (-Wl,-z,norelro)
        // LLVMの "Other linker flags" 用のID (通常は以下のようなIDです)
        String flagOptionId = "com.renesas.cdt.managedbuild.llvm.core.option.linker.other.userDefinedOptions";
        IOption flagOption = linker.getOptionBySuperClassId(flagOptionId);

        if (flagOption != null) {
            try {
                String[] currentFlags = flagOption.getBasicStringListValue();

                // 既に含まれているかチェック
                String norelroFlag = "-Wl,-z,norelro";
                boolean isExist = false;
                for (String currentFlag : currentFlags) {
                    if (currentFlag.equals(norelroFlag)) {
                        isExist = true;
                    }
                }
                if (!isExist) {
                 // 4. 配列をリストに変換して追加
                    List<String> newFlagList = new ArrayList<>(Arrays.asList(currentFlags));
                    newFlagList.add(norelroFlag);
                    
                    // 5. リストを再び String[] に変換してセット
                    String[] updatedFlags = newFlagList.toArray(new String[0]);
                    ManagedBuildManager.setOption(config, linker, flagOption, updatedFlags);
                }
            } catch (BuildException e) {
                e.printStackTrace();
            }
        }
    }
    
    // 指定した名前の設定項目が無い場合だけ設定を追加する
    private static void AddSettingIfNotExist(List<ICLanguageSettingEntry> entries, ICLanguageSettingEntry newSetting) {
        boolean isMatch = entries.stream().anyMatch(e -> e.getName().equals(newSetting.getName()));
        if (!isMatch) {
            entries.add(newSetting);
        }
    }
    
    // Library Generatorの設定を行う
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
                if ((tool.getId() != null && tool.getId().contains("libgen")) ||
                        (tool.getName() != null && tool.getName().contains("Library Generator"))) {
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
            if ((opt.getId() != null && opt.getId().toLowerCase().contains(keyword.toLowerCase())) || 
                (opt.getName() != null && opt.getName().toLowerCase().contains(keyword.toLowerCase()))) {
                
                if (opt.getValueType() == IOption.BOOLEAN) {
                    ManagedBuildManager.setOption(config, tool, opt, value);
                }
            }
        }
    }

    public enum ToolchainType {
        GCC_ARM, LLVM_ARM, CC_RX, GCC_RX, UNKNOWN
    }
    public static ToolchainType getToolchainType(IProject project) {
        // プロジェクトのビルド情報を取得
        IManagedBuildInfo buildInfo = ManagedBuildManager.getBuildInfo(project);
        if (buildInfo == null) return ToolchainType.UNKNOWN;

        IManagedProject managedProject = buildInfo.getManagedProject();
        if (managedProject == null) return ToolchainType.UNKNOWN;

        // 現在アクティブな構成（Debug/Releaseなど）を取得
        IConfiguration activeConfig = buildInfo.getDefaultConfiguration();
        if (activeConfig == null) return ToolchainType.UNKNOWN;

        // ツールチェーンを取得
        IToolChain toolchain = activeConfig.getToolChain();
        if (toolchain == null) return ToolchainType.UNKNOWN;

        // ツールチェーンのIDを取得（これが最も確実な識別子です）
        String toolchainId = toolchain.getId();
        
        // RAのツールチェーンIDには通常以下の文字列が含まれます
        if (toolchainId.contains("llvm.arm")) {
            return ToolchainType.LLVM_ARM;
        } else if (toolchainId.contains("ccrx")) {
            return ToolchainType.CC_RX;
        } else if (toolchainId.contains("gcc.rx")) {
            return ToolchainType.GCC_RX;
        } else if (toolchainId.contains("gnuarm")) {
            return ToolchainType.GCC_ARM;
        }

        // IDで判別できない場合は名前でチェック
        String name = toolchain.getName().toLowerCase();
        if (name.contains("llvm")) return ToolchainType.LLVM_ARM;
        if (name.contains("gnu") || name.contains("gcc")) return ToolchainType.GCC_ARM;

        return ToolchainType.UNKNOWN;
    }
}
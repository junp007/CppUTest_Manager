package com.cpputest.manager.parser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.Job;

import com.cpputest.manager.model.TestProjectManager;
import com.cpputest.manager.view.TestResultView;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

public class TestScanner {

    // 指定したプロジェクト内のソースコードをスキャンしてテストケースを抽出する
    public static void scanProjectTestCase(final String projectName, final TestProjectManager testProjectManager) {
     // Jobの作成
        Job job = new Job("Scanning CppUTest cases in " + projectName) {
            @Override
            protected IStatus run(final IProgressMonitor monitor) {
                IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
                if (!project.exists() || !project.isOpen()) {
                    return Status.OK_STATUS;
                }

                // テストグループ、テストケースの存在フラグをクリアする
                testProjectManager.clearCurrentProjectExistFlag();
                
                try {
                 // 全体的なファイル数を把握するのは難しいため、不確定な進捗として開始
                    monitor.beginTask("Reading files...", IProgressMonitor.UNKNOWN);

                    project.accept(new IResourceProxyVisitor() {
                        @Override
                        public boolean visit(IResourceProxy proxy) throws CoreException {
                         // キャンセルボタンが押されたかチェック
                            if (monitor.isCanceled()) return false;
                            
                            if (proxy.getType() == IResource.FILE) {
                                String name = proxy.getName();
                                if (name.endsWith(".cpp")) {
                                    monitor.subTask("Analyzing: " + name);
                                    parseFile((IFile) proxy.requestResource(), testProjectManager);
                                }
                            }
                            return true; // 子リソースも探索
                        }
                    }, IResource.NONE);
                } catch (CoreException e) {
                    return new Status(IStatus.ERROR, "com.cpputest.manager", "Scan failed", e);
                } finally {
                    monitor.done();
                }

                // テストグループ、テストケースの存在フラグが経っていないものを削除する
                testProjectManager.removeNonExistTest();
                
                return Status.OK_STATUS;
            }
        };
        
        // Jobの優先度設定（ユーザー操作に対する反応なので、少し高めに設定）
        job.setUser(true); 
        job.schedule(); // 実行キューに入れる
    }

    // TEST(GroupName, TestName) にマッチする正規表現
    private static final Pattern TEST_PATTERN =  Pattern.compile("TEST\\s*\\(\\s*(\\w+)\\s*,\\s*(\\w+)\\s*\\)");
    
    private static void parseFile(IFile file, TestProjectManager testProjectManager) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(file.getContents()));
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = TEST_PATTERN.matcher(line);
                while (matcher.find()) {
                    String group = matcher.group(1);
                    String name = matcher.group(2);
                    testProjectManager.updateTestResult(group, name, false, false, file.getFullPath().toString(), lineNum);
                }
                ++lineNum;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // reader を確実に閉じる
            if (reader != null) {
                try {
                    reader.close();
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
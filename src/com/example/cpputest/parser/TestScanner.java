package com.example.cpputest.parser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import com.example.cpputest.view.TestResultView;

public class TestScanner {

    // TEST(GroupName, TestName) にマッチする正規表現
    private static final Pattern TEST_PATTERN = 
        Pattern.compile("TEST\\s*\\(\\s*(\\w+)\\s*,\\s*(\\w+)\\s*\\)");

    public static void scanProject(String projectName) {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (!project.exists() || !project.isOpen()) return;

        try {
            project.accept(new IResourceProxyVisitor() {
                @Override
                public boolean visit(IResourceProxy proxy) throws CoreException {
                    if (proxy.getType() == IResource.FILE) {
                        String name = proxy.getName();
                        if (name.endsWith(".cpp")) {
                            parseFile((IFile) proxy.requestResource());
                        }
                    }
                    return true; // 子リソースも探索
                }
            }, IResource.NONE);
        } catch (CoreException e) {
            e.printStackTrace();
        }
    }

    private static void parseFile(IFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getContents()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = TEST_PATTERN.matcher(line);
                while (matcher.find()) {
                    String group = matcher.group(1);
                    String name = matcher.group(2);
                    // ビューに未完了状態で追加
                    TestResultView.updateTestResult(group, name, false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
package com.cpputest.manager.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TestProjectManager implements Iterable<TestGroup> {
    private Map<String, TestProject> m_testProjectMap = new HashMap<String, TestProject>();
    TestProject m_currentProject = new TestProject();
    // 表示中のプロジェクト名
    String m_currentDidplayProjectName;
    // デバッグ中のプロジェクト名
    String m_currentDebuggingProjectName;
    
    // 通知先のリスト
    private List<Runnable> m_listeners = new ArrayList<>();

    public TestProjectManager() {
    }
    
    public TestProject getCurrentProject() {
        return m_currentProject;
    }
    
    public String getCurrentProjectName() {
        return m_currentDidplayProjectName;
    }
    
    public void setCurrentProjectName(String projectName) {
        m_currentDidplayProjectName = projectName;
        m_testProjectMap.put(projectName, m_currentProject);
    }
    
    public String getCurrentDebuggingProjectName() {
        return m_currentDebuggingProjectName;
    }
    public void setCurrentDebuggingProjectName(String projectName) {
        m_currentDebuggingProjectName = projectName;
    }
    
    public void addChangeListener(Runnable listener) {
        m_listeners.add(listener);
    }
    
    // データが更新されたときに呼ぶメソッド
    public void notifyChanged() {
        for (Runnable listener : m_listeners) {
            listener.run();
        }
    }
    
    @Override
    public Iterator<TestGroup> iterator() {
        return getTestGroups().iterator();
    }
    
    public List<TestGroup> getTestGroups() {
        // テスト結果ではない場合は表示中のプロジェクトを取得する
        return m_currentProject.getTestGroups();
    }
    
    public List<TestGroup> getDebuggingTestGroups() {
        // デバッグ中のプロジェクトを取得する
        return ((TestProject)m_testProjectMap.get(m_currentDebuggingProjectName)).getTestGroups();
    }
    
    public void changeProject(String oldProjectName, String newProjectName) {
        // 現在のプロジェクトを保存する
        m_testProjectMap.put(oldProjectName, m_currentProject);
        m_currentDidplayProjectName = newProjectName;

        if (m_testProjectMap.containsKey(newProjectName)) {
            // 保存済みのプロジェクトの場合はそのプロジェクトに切り替える
            m_currentProject = m_testProjectMap.get(newProjectName);
        } else {
            m_currentProject = new TestProject();
            // 保存してないプロジェクトの場合は新たに作る
            m_testProjectMap.put(newProjectName, m_currentProject);
        }
        notifyChanged();
    }
    
    public void updateTestResult(String groupName, String testName, boolean isSuccess, boolean isTested) {
        List<TestGroup> groups;
        if (isTested) {
            // テスト結果を入れる場合はデバッグ中プロジェクトを使う
            groups = getDebuggingTestGroups();
        } else {
            // テスト結果じゃない場合は表示中プロジェクトを使う
            groups = getTestGroups();
        }
        
        // 該当するグループを探す
        TestGroup group = groups.stream()
                    .filter(g -> g.m_name.equals(groupName))
                    .findFirst().orElse(null);


        if (group == null) {
            // グループが見つからない場合は新しいグループを作成
            group = new TestGroup(groupName);
            groups.add(group);
        }
        // グループの存在フラグをtrueにする
        group.setExist(true);
        
        // テストケースを探す
        TestCase target = group.m_cases.stream()
                .filter(tc -> tc.m_name.equals(testName))
                .findFirst().orElse(null);
        
        if (target == null) {
            // テストケースが見つからない場合は新しいテストケースを作成
            target = new TestCase(testName);
            group.addTestCase(target);
        }
        // テストケースの存在フラグをtrueにする
        target.setExist(true);
        // テスト済みとして登録する場合は引数の値を設定する
        if (isTested) {
            target.m_success = isSuccess;
            target.m_tested = isTested;
        }
        
        notifyChanged();
    }
    
    // 現在のプロジェクトの存在フラグを全部falseにする
    public void clearCurrentProjectExistFlag() {
        m_currentProject.clearExistFlag();
    }
    
    public void removeNonExistTest() {
        m_currentProject.removeNonExistTest();
        notifyChanged();
    }
}
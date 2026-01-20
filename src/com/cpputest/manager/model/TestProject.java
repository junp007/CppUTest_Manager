package com.cpputest.manager.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TestProject implements Iterable<TestGroup> {
    // テストグループリスト
    private List<TestGroup> m_testGroups = new ArrayList<>();

    public TestProject() {
    }
    
    public TestProject(TestProject other) {
        this.m_testGroups = new ArrayList<TestGroup>(other.m_testGroups);
    }

    @Override
    public Iterator<TestGroup> iterator() {
        return m_testGroups.iterator();
    }
    
    public List<TestGroup> getTestGroups() {
        return m_testGroups;
    }
    
    public void addTestGroup(TestGroup testGroup) {
        m_testGroups.add(testGroup);
    }
    
    public void clear() {
        m_testGroups.clear();
    }
    
    public void clearExistFlag() {
        getTestGroups().forEach(tg -> {
            tg.setExist(false);
            tg.clearTestCaseExistFlag();
        });
    }
    
    public void removeNonExistTest() {
        // 各グループの存在しないテストケースを削除
        m_testGroups.forEach(tg -> {
            tg.removeNonExistTestCase();
        });
        // 存在しないグループを削除
        m_testGroups.removeIf(tg -> !tg.isExist());
    }
    
    public boolean isEmpty() {
        return m_testGroups.isEmpty();
    }
}
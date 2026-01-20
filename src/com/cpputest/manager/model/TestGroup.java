package com.cpputest.manager.model;

import java.util.ArrayList;
import java.util.List;

public class TestGroup {
    // テストグループ名
    private String m_name;
    // テストケースリスト
    private List<TestCase> m_cases = new ArrayList<>();
    // 展開されているかどうか
    private boolean m_expand;
    private boolean m_exist;
    
    public TestGroup(String name) {
        this.m_name = name;
        this.m_expand = true;
        this.m_exist = false;
    }
    
    public String getName() {
        return m_name;
    }
    
    public List<TestCase> getCases() {
        return m_cases;
    }
    
    public void addTestCase(TestCase testCase) {
        testCase.setGroup(this);
        m_cases.add(testCase);
    }
    
    // グループ配下のテストケースすべてのチェック状態を設定する
    public void setChecked(boolean checked) {
        m_cases.stream().forEach(tc -> tc.setChecked(checked));
    }
    
    public enum CheckState {NonChecked, PartChecked, AllChecked};
    public TestGroup.CheckState getCheckState() {
        long checkedCount = m_cases.stream().filter(tc -> tc.isChecked()).count();

        if (checkedCount == 0) {
            // すべて未選択
            return CheckState.NonChecked;
        } else if (checkedCount == m_cases.size()) {
            // すべて選択
            return CheckState.AllChecked;
        } else {
            // 一部選択（グレー表示）
            return CheckState.PartChecked;
        }
    }
    
    public void setExpand(boolean isExpand) {
        m_expand = isExpand;
    }
    
    public boolean isExpand() {
        return m_expand;
    }
    
    public boolean isExist() {
        return m_exist;
    }
    
    public void setExist(boolean isExist) {
        m_exist = isExist;
    }

    public void clearTestCaseExistFlag() {
        m_cases.forEach(tc -> {
            tc.setExist(false);
        });
    }
    
    public void removeNonExistTestCase() {
        m_cases.removeIf(tc -> !tc.isExist());
    }
    
    public void clearTestedFlag() {
        m_cases.forEach(tc -> {
            tc.setTested(false);
        });
    }
}
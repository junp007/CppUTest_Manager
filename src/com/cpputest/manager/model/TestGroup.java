package com.cpputest.manager.model;

import java.util.ArrayList;
import java.util.List;

public class TestGroup implements ICheckable {
    // テストグループ名
    private String m_name;
    // テストケースリスト
    private List<TestCase> m_cases = new ArrayList<TestCase>();
    // 展開されているかどうか
    private boolean m_expand;
    // 存在確認フラグ(テストケーススキャン時にソース上に存在していないことを判断するために使う)
    private boolean m_exist;
    
    public TestGroup(String name) {
        m_name = name;
        m_expand = true;
        m_exist = false;
    }
    
    @Override
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
    @Override
    public void setChecked(boolean checked) {
        for (TestCase tc : m_cases) {
            tc.setChecked(checked);
        }
    }
    
    @Override
    public boolean isChecked() {
        // 1つでもチェックされていたらtrueを返す
        for (TestCase tc : m_cases) {
            if (tc.isChecked()) {
                return true;
            }
        }
        return false;
    }
    
    public enum CheckState {NonChecked, PartChecked, AllChecked};
    public TestGroup.CheckState getCheckState() {
        long checkedCount = 0;
        for (TestCase tc : m_cases) {
            if (tc.isChecked()) {
                checkedCount++;
            }
        }

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
        for (TestCase tc : getCases()) {
            tc.setExist(false);
        }
    }
    
    public void removeNonExistTestCase() {
        java.util.Iterator<TestCase> it = getCases().iterator();
        while (it.hasNext()) {
            TestCase tc = it.next();
            if (!tc.isExist()) {
                it.remove();
            }
        }
    }
    
    public void clearTestedFlag() {
        for (TestCase tc : getCases()) {
            tc.setTested(false);
            tc.setErrorMessage("");
        }
    }
    
    // グループ内のテストケースの成功数を取得
    public int getSuccessCount() {
        int successCount = 0;
        for (TestCase tc : getCases()) {
            if (tc.isSuccess() && tc.isTested()) {
                successCount++;
            }
        }
        return successCount;
    }
    
    // グループ内のテストケースの総数を取得
    public int getTotalCount() {
        return getCases().size();
    }
    
    // グループ内のテストケースに1つでも失敗があるかどうか
    public boolean hasFailure() {
        for (TestCase tc : getCases()) {
            if (!tc.isSuccess() && tc.isTested()) {
                return true;
            }
        }
        return false;
    }
}
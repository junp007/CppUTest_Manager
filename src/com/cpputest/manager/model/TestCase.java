package com.cpputest.manager.model;

// テストケースのデータを保持する簡単な内部クラス
public class TestCase {
    // テスト名
    private String m_name;
    // テスト結果が成功かどうか
    private boolean m_success;
    // テスト済みかどうか
    private boolean m_tested;
    // テスト対象かどうか
    private boolean m_checked;
    // 属しているグループ
    private TestGroup m_group;
    // 存在確認フラグ
    private boolean m_exist;

    public TestCase(String name) {
      this.m_name = name;
      this.m_success = false;
      this.m_tested = false;
      this.m_checked = true;
      this.m_exist = false;
    }

    public String getName() {
        return this.m_name;
    }
    
    public void setChecked(boolean isChecked) {
        this.m_checked = isChecked;
    }
    
    public boolean isSuccess() {
        return m_success;
    }
    
    public void setSuccess(boolean isSuccess) {
        m_success = isSuccess;
    }
    
    public boolean isTested() {
        return m_tested;
    }
    
    public void setTested(boolean isTested) {
        m_tested = isTested;
    }
    
    public boolean isChecked() {
        return m_checked;
    }

    public void setGroup(TestGroup group) {
        this.m_group = group;
    }
    
    public TestGroup getGroup() {
        return m_group;
    }
    
    public boolean isExist() {
        return m_exist;
    }
    
    public void setExist(boolean isExist) {
        m_exist = isExist;
    }
}
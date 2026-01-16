package com.cpputest.manager.model;

// テストケースのデータを保持する簡単な内部クラス
public class TestCase {
    // テスト名
    String m_name;
    // テスト結果が成功かどうか
    boolean m_success;
    // テスト済みかどうか
    boolean m_tested;
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
    
    public boolean isTested() {
        return m_tested;
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
package com.cpputest.manager.model;

// テストケースのデータを保持する簡単な内部クラス
public class TestCase implements ICheckable {
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
    // ファイル名
    private String m_fileName;
    // 行番号(エディタの仕様に合わせて最初の行を1とする)
    private int m_lineNumber;

    public TestCase(String name, String fileName, int lineNumber) {
      m_name = name;
      m_success = false;
      m_tested = false;
      m_checked = true;
      m_exist = false;
      m_fileName = fileName;
      m_lineNumber = lineNumber;
    }

    @Override
    public String getName() {
        return m_name;
    }
    
    @Override
    public void setChecked(boolean isChecked) {
        m_checked = isChecked;
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
    
    @Override
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
    
    public String getFileName() {
        return m_fileName;
    }
    
    public int getLineNumber() {
        return m_lineNumber;
    }
}
package com.example.cpputest.view;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;

import com.example.cpputest.TestRunnerGenerator;
import com.example.cpputest.VirtualConsoleMirror;
import com.example.cpputest.parser.TestScanner;

import java.util.ArrayList;
import java.util.List;

public class TestResultView extends ViewPart {
    public static final String ID = "com.example.cpputest.view.TestResultView";
    private CheckboxTableViewer viewer;

    // テストケースのデータを保持する簡単な内部クラス
    public static class TestCase {
    	String groupName;
        String testName;
        boolean success;
        public TestCase(String groupName, String testName, boolean success) {
        	this.groupName = groupName;
        	this.testName = testName;
        	this.success = success;
        }
        public TestCase(String groupName, String testName) {
        	this(groupName, testName, false);
        }
        
        public String getGroupName() {
        	return this.groupName;
        }
        
        public String getTestName() {
        	return this.testName;
        }
        
        public String getFullName() {
        	return this.groupName + "." + this.testName;
        }
        
        boolean IsSameCase(final String groupName, final String testName) {
        	return this.groupName.equals(groupName) && this.testName.equals(testName);
        }
    }

    @Override
    public void createPartControl(Composite parent) {
        // チェックボックス付きのテーブルビューアを作成
        viewer = CheckboxTableViewer.newCheckList(parent, SWT.BORDER | SWT.FULL_SELECTION);
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);

        // 1列目：テスト名
        TableViewerColumn colName = new TableViewerColumn(viewer, SWT.NONE);
        colName.getColumn().setWidth(300);
        colName.getColumn().setText("Test Case Name");
        colName.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((TestCase) element).getFullName();
            }
            
            @Override
            public Color getForeground(Object element) {
                // 成功なら緑、失敗なら赤（今は仮のロジック）
                if (((TestCase) element).success) {
                    return Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GREEN);
                } else {
                    return Display.getCurrent().getSystemColor(SWT.COLOR_RED);
                }
            }
        });

        viewer.setContentProvider(ArrayContentProvider.getInstance());
        
        createToolbar();
    }
    
    private void createToolbar() {
        // 実行ボタンのアクションを定義
        org.eclipse.jface.action.Action runAction = new org.eclipse.jface.action.Action("Run Tests") {
            @Override
            public void run() {
                // チェックされている項目を取得
                Object[] checkedElements = viewer.getCheckedElements();
                
                System.out.println("Selected tests count: " + checkedElements.length);
                for (Object obj : checkedElements) {
                    TestCase tc = (TestCase) obj;
                    System.out.println("Will run: " + tc.getFullName());
                }
                
                // 1. mainファイルを生成
                TestRunnerGenerator.generateMain("YHT_UnitTest", checkedElements);
                
                // 2. 将来的にはここに「ビルド開始」と「デバッグ開始」のコードを追加します
                System.out.println("Main file generated. Ready to build and run.");
                
//                VirtualConsoleMirror.scanAndHook();
            }
        };

        // アイコンの設定
//        runAction.setImageDescriptor(org.eclipse.ui.PlatformUI.getWorkbench().getSharedImages().
//        		getImageDescriptor(org.eclipse.ui.ISharedImages.IMG_ETOOL_SAVEAS_EDIT));

        org.eclipse.jface.action.Action scanAction = new org.eclipse.jface.action.Action("Scan Tests") {
            @Override
            public void run() {
            	TestScanner.scanProject("YHT_UnitTest");
            }
        };
        
        runAction.setToolTipText("Run selected CppUTest cases");
        scanAction.setToolTipText("Scan CppUTest cases");
        // ビューのツールバーにボタンを追加
        IActionBars bars = getViewSite().getActionBars();
        bars.getToolBarManager().add(runAction);
        bars.getToolBarManager().add(scanAction);
    }
    
    @Override
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    private static TestResultView instance;
    private List<TestCase> testCases = new ArrayList<>();
    
    public TestResultView() {
        instance = this;
    }

    private TestCase updateTestCase(final String groupName, final String testName) {
    	TestCase target = null;

        for (TestCase tc : testCases) {
            if (tc.IsSameCase(groupName, testName)) {
                target = tc;
                break;
            }
        }
        
        if (target == null) {
        	// 新しいものだったら追加する
        	target = new TestCase(groupName, testName);
        	testCases.add(target);
        	viewer.setInput(testCases);
        	viewer.setChecked(target, true);
        }
        
        return target;
    }
    
    public static void appendTestCase(final String groupName, final String testName) {
        if (instance == null) return;
        
        Display.getDefault().asyncExec(() -> {
        	TestCase target = instance.updateTestCase(groupName, testName);
            instance.viewer.refresh(target);
        });
    }
    
    public static void updateTestResult(final String groupName, final String testName, final boolean isSuccess) {
        if (instance == null) return;
        
        Display.getDefault().asyncExec(() -> {
        	TestCase target = instance.updateTestCase(groupName, testName);
            target.success = isSuccess;
            instance.viewer.refresh(target);
        });
    }
}
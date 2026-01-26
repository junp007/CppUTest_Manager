package com.cpputest.manager.view;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

import com.cpputest.manager.model.ICheckable;
import com.cpputest.manager.model.TestCase;
import com.cpputest.manager.model.TestGroup;
import com.cpputest.manager.model.TestGroup.CheckState;
import com.cpputest.manager.model.TestProjectManager;

public class TestTreeViewer extends CheckboxTreeViewer {
    // 選択行の色の濃さ
    private static final int SELECTION_ALPHA = 40;
    
    public TestTreeViewer(Composite parent, TestProjectManager projectManger) {
        super(parent, SWT.BORDER | SWT.MULTI | SWT.CHECK | SWT.FULL_SELECTION);
        init(projectManger);
    }

    private void init(TestProjectManager projectManger) {
        this.getTree().setHeaderVisible(true);
        this.getTree().setLinesVisible(true);
        this.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));

        // 列の設定（1列目：Name）
        TreeViewerColumn colName = new TreeViewerColumn(this, SWT.NONE);
        colName.getColumn().setWidth(200);
        colName.getColumn().setText("Test Group / Name");
        colName.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                if (element instanceof ICheckable) return ((ICheckable) element).getName();
                return "";
            }
            @Override
            public Color getBackground(Object element) {
                return getStatusColor(element);
            }
        });

        // 列の設定（2列目：Status）
        TreeViewerColumn colStatus = new TreeViewerColumn(this, SWT.NONE);
        colStatus.getColumn().setWidth(100);
        colStatus.getColumn().setText("Status");
        colStatus.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                if (element instanceof TestGroup) {
                    TestGroup group = (TestGroup) element;
                    // (成功数/総数)を表示
                    return String.format("(%d/%d)", group.getSuccessCount(), group.getTotalCount());
                }
                if (element instanceof TestCase) {
                    TestCase tc = (TestCase)element;
                    if (tc.isTested()) {
                        return tc.isSuccess() ? "Success" : "Failure";
                    } else {
                        return "Idle";
                    }
                }
                return "";
            }
            
            @Override
            public Color getBackground(Object element) {
                return getStatusColor(element);
            }
        });

        this.setContentProvider(new TestTreeContentProvider());
        this.setCheckStateProvider(new ICheckStateProvider() {
            @Override
            public boolean isChecked(Object element) {
                if (element instanceof ICheckable) {
                    return ((ICheckable) element).isChecked();
                }
                return false;
            }

            @Override
            public boolean isGrayed(Object element) {
                if (element instanceof TestGroup) {
                    CheckState state = ((TestGroup) element).getCheckState();
                    return state == CheckState.PartChecked;
                } else {
                    return false;
                }
            }
        });

        // チェック状態が変更されたときに呼ばれる
        this.addCheckStateListener(event -> {
            Object element = event.getElement();
            boolean checked = event.getChecked();

            if (element instanceof ICheckable) {
                ((ICheckable)element).setChecked(checked);
            }
            // チェック状態が変わったのでリフレッシュ
            CheckboxTreeViewer treeViewer = (CheckboxTreeViewer)event.getSource();
            treeViewer.refresh();
        });
        
        this.addTreeListener(new ITreeViewerListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                // グループが開かれた時の処理
                Object element = event.getElement();
                if (element instanceof TestGroup) {
                    TestGroup group = (TestGroup) element;
                    group.setExpand(true);
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                // グループが閉じられた時の処理
                Object element = event.getElement();
                if (element instanceof TestGroup) {
                    TestGroup group = (TestGroup) element;
                    group.setExpand(false);
                }
            }
        });
        
        this.getTree().addListener(SWT.EraseItem, event -> {
            boolean isSelected = (event.detail & (SWT.SELECTED | SWT.HOT)) != 0;
            if (!isSelected) return; // 選択されていないなら何もしない

            // 標準の選択色をキャンセル
            event.detail &= ~SWT.SELECTED;

            TreeItem item = (TreeItem) event.item;
            Color itemBackground = item.getBackground(event.index);
            GC gc = event.gc;
            // 描画後に復元するために現在値を保存しておく
            Color oldBackground = gc.getBackground();
            int oldAlpha = gc.getAlpha();
            // 背景描画
            if (itemBackground != null) {
                gc.setBackground(itemBackground);
            } else {
                gc.setBackground(((Control)event.widget).getBackground());
            }
            gc.fillRectangle(event.x, event.y, event.width, event.height);

            // 選択状態を暗く表示するため、半透明の黒を描く
            gc.setAlpha(SELECTION_ALPHA);
            gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
            gc.fillRectangle(event.x, event.y, event.width, event.height);
            
            // 背景、不透明度の復元
            gc.setBackground(oldBackground);
            gc.setAlpha(oldAlpha);
        });
        
        this.addDoubleClickListener(new IDoubleClickListener() {
            @Override
            public void doubleClick(DoubleClickEvent event) {
                // 選択された要素を取得
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                Object firstElement = selection.getFirstElement();

                if (firstElement instanceof TestCase) {
                    TestCase tc = (TestCase) firstElement;
                    // エディタを開く処理を呼び出す
                    openEditorAtLine(tc.getFileName(), tc.getLineNumber());
                }
            }
        });
        
        this.setInput(projectManger);
    }
    
    // --- 背景色を決定する共通メソッド ---
    private Color getStatusColor(Object element) {
        if (element instanceof TestGroup) {
            TestGroup group = (TestGroup) element;
            // 1つでも失敗があるか
            boolean anyFailure = group.getCases().stream().anyMatch(tc -> tc.isTested() && !tc.isSuccess());
            // 全件成功しているか（実行済みかつ失敗なし）
            long successCount = group.getCases().stream().filter(tc -> tc.isTested() && tc.isSuccess()).count();
            boolean allSuccess = (successCount == group.getCases().size() && successCount > 0);

            if (anyFailure) {
                // 実行済みかつ失敗あり
                return Display.getDefault().getSystemColor(SWT.COLOR_RED);
            } else if (allSuccess) {
                // 実行済みかつ全部成功
                return Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
            } else {
                // 実行していないものがある場合
                return null;
            }
        } else if (element instanceof TestCase) {
            TestCase tc = (TestCase) element;
            if (!tc.isTested()) {
                // 実行していない場合
                return null;
            } else if (tc.isSuccess()) {
                // 成功
                return Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
            } else {
                // 失敗
                return Display.getDefault().getSystemColor(SWT.COLOR_RED);
            }
        }
        return null; // デフォルト（白など）
    }
    
    private void openEditorAtLine(String fileName, int lineNumber) {
        if (fileName == null || fileName.isEmpty()) return;

        // UIスレッドで実行
        Display.getDefault().asyncExec(() -> {
            try {
                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                IWorkbenchPage page = window.getActivePage();

                IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
                IFile file = root.getFile(IPath.fromPortableString(fileName));
                if (file.exists()) {
                    // エディタを開く
                    IEditorPart editor = IDE.openEditor(page, file, true);

                    // 指定行へジャンプ (ITextEditorにキャスト)
                    if (editor instanceof ITextEditor && lineNumber > 0) {
                        ITextEditor textEditor = (ITextEditor) editor;
                        IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
                        
                        // 行番号からオフセット（文字数）に変換
                        // documentの行は0始まりなので -1 する
                        int offset = document.getLineInformation(lineNumber - 1).getOffset();
                        
                        // エディタ上で選択（ジャンプ）
                        textEditor.selectAndReveal(offset, 0);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    public class TestTreeContentProvider implements ITreeContentProvider {
        @Override
        public Object[] getElements(Object inputElement) {
            // ルート要素としてグループの一覧を返す
            return ((TestProjectManager)inputElement).getTestGroups().toArray();
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            if (parentElement instanceof TestGroup) {
                return ((TestGroup) parentElement).getCases().toArray();
            }
            return null;
        }

        @Override
        public Object getParent(Object element) {
            if (element instanceof TestCase) {
                return ((TestCase) element).getGroup();
            }
            return null;
        }

        @Override
        public boolean hasChildren(Object element) {
            return element instanceof TestGroup && !((TestGroup) element).getCases().isEmpty();
        }
    }
}

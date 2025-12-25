package com.example.cpputest;

import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import com.example.cpputest.view.TestResultView;

import java.util.HashMap;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

public class VirtualConsoleMirror {

    public static void scanAndHook() {
        Display.getDefault().asyncExec(() -> {
            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            // 現在のページにある全Viewの参照を取得
            IViewReference[] refs = page.getViewReferences();

            for (IViewReference ref : refs) {
                String title = ref.getTitle(); // タイトルで判定
                if (title != null && title.contains("Renesas Debug Virtual Console")) {
                    IViewPart view = ref.getView(true);
                    if (view != null) {
                        Control viewControl = (Control) view.getAdapter(org.eclipse.swt.widgets.Control.class);
                        if (viewControl == null) {
                            // ViewPartのメインコントロールを直接取得するリフレクションに近い方法
                            // Viewのサイトから親を取得
                            viewControl = view.getViewSite().getShell(); // 最悪、Shell（窓）から辿る
                        }
                        findAndHookTextControl(viewControl);
                    }
                }
            }
        });
    }

    private static HashMap<Integer, String> lastProcessedTextMap = new HashMap<Integer, String>(); // 前回取得したテキストを保持

    private static void findAndHookTextControl(Control control) {
        // StyledTextはEclipseの多くのエディタやコンソールの基盤です
        if (control instanceof StyledText) {
            StyledText st = (StyledText) control;

            st.addPaintListener((e) -> {
                Integer id = System.identityHashCode(e.getSource());
                String currentText = st.getText();
                String lastProcessedText = lastProcessedTextMap.get(id);
                if (currentText.contains("#include") && !currentText.contains("TEST(")) {
                    // 関係ないテキストは無視する
                    return;
                }
                // 全く同じ内容なら処理をスキップ（CPU負荷軽減）
                if (currentText != null && !currentText.equals(lastProcessedText)) {
                    // 増分（新しく追加された文字）を特定
                    String newPart = "";
                    if (lastProcessedText != null && currentText.length() > lastProcessedText.length()
                            && currentText.startsWith(lastProcessedText)) {
                        // 後ろに追記された場合
                        newPart = currentText.substring(lastProcessedText.length());
                    } else {
                        newPart = currentText;
                    }

                    if (!newPart.isEmpty()) {
                        // 更新されていればパース
                        processText(currentText);
                    }
                    lastProcessedTextMap.put(id, currentText);
                }
            });

        } else if (control instanceof Composite) {
            for (Control child : ((Composite) control).getChildren()) {
                findAndHookTextControl(child);
            }
        }
    }

    private static void processText(String text) {
        String[] lines = text.split("\\r?\\n");

        for (String line : lines) {
            // CppUTestの出力パターンの解析
            // 例: "TEST(MyGroup, MyTest) - 5 ms"
            if (line.contains("TEST(")) {
                try {
                    int start = line.indexOf("(") + 1;
                    int end = line.indexOf(")");
                    String content = line.substring(start, end);
                    String[] parts = content.split(",");
                    if (parts.length >= 2) {
                        String group = parts[0].trim();
                        String name = parts[1].trim();

                        // 失敗(Failure)という文字が含まれていれば false、そうでなければ true
                        boolean isSuccess = !line.contains("Failure");
                        TestResultView.updateTestResult(group, name, isSuccess);
                    }
                } catch (Exception e) {
                    // パース失敗時は何もしない
                }
            }
        }
    }
}
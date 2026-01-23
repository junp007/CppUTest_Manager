package com.cpputest.manager;

import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.TextConsole;

import com.cpputest.manager.view.TestResultView;

import java.lang.reflect.Field;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.widgets.Display;

public class VirtualConsoleMirror {

    private static IDocumentListener m_currentListener = null;
    public static void scanAndHook() {
        Display.getDefault().asyncExec(() -> {
            try {
                TextConsole textConsole = getTextConsole();

                if (textConsole != null) {
                    // TextConsoleからDocumentを取得
                    IDocument doc = textConsole.getDocument();
                    
                    if (m_currentListener != null) {
                        doc.removeDocumentListener(m_currentListener);
                    }
                    m_currentListener = new IDocumentListener() {
                        @Override
                        public void documentAboutToBeChanged(DocumentEvent event) { }

                        @Override
                        public void documentChanged(DocumentEvent event) {
                            // event.getText() で「新しく追加された文字列」だけが直接取れる！
                            String newText = event.getText();
                            if (newText != null && !newText.isEmpty()) {
                                System.out.println("Captured from Document: " + newText);
                                processText(newText);
                            }
                        }
                    };
                    
                    // 2. Documentにリスナーを貼る
                    doc.addDocumentListener(m_currentListener);
                    System.out.println("Successfully hooked DocumentListener to TextConsole");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private static void analyzeConsoleView(IViewPart consoleView) {
        Field[] fields = consoleView.getClass().getDeclaredFields();
        for (Field f : fields) {
            System.out.println("Field: " + f.getName() + " (Type: " + f.getType() + ")");
        }
        
        try {
            Field vmField = consoleView.getClass().getDeclaredField("consolePage");
            vmField.setAccessible(true);
            Object viewModel = vmField.get(consoleView);

            if (viewModel != null) {
                System.out.println("--- consolePage Deep Search ---");
                Class<?> currentClass = viewModel.getClass();
                
                // 親クラスを遡って全てのフィールドを表示
                while (currentClass != null && currentClass != Object.class) {
                    System.out.println("Checking Class: " + currentClass.getName());
                    for (Field f : currentClass.getDeclaredFields()) {
                        f.setAccessible(true);
                        try {
                            Object value = f.get(viewModel);
                            System.out.println("  Field: " + f.getName() + " | Type: " + f.getType() + " | Value: " + value);
                        } catch (Exception e) {
                            System.out.println("  Field: " + f.getName() + " | (Could not access value)");
                        }
                    }
                    currentClass = currentClass.getSuperclass(); // 親へ移動
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // RenesasのDebugConsoleからTextConsoleを取得する
    private static TextConsole getTextConsole() {
        try {
            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            
            // 1. Viewを探す。なければ作成する
            IViewPart consoleView = page.findView("com.renesas.cdt.debug.debugconsole.ui.views.DebugConsoleView");
            if (consoleView == null) {
                consoleView = page.showView("com.renesas.cdt.debug.debugconsole.ui.views.DebugConsoleView", 
                                            null, IWorkbenchPage.VIEW_CREATE);
            }
    
            // consolePage フィールドを取得
            Field pageField = consoleView.getClass().getDeclaredField("consolePage");
            pageField.setAccessible(true);
            Object consolePage = pageField.get(consoleView);
    
            // TextConsole (fConsoleフィールド) を取得
            Field fConsoleField = org.eclipse.ui.console.TextConsolePage.class.getDeclaredField("fConsole");
            fConsoleField.setAccessible(true);
            org.eclipse.ui.console.TextConsole textConsole = (org.eclipse.ui.console.TextConsole) fConsoleField.get(consolePage);
            return textConsole;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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
                        TestResultView.updateTestResult(group, name, isSuccess, true);
                    }
                } catch (Exception e) {
                    // パース失敗時は何もしない
                }
            }
        }
    }

}
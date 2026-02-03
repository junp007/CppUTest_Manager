package com.cpputest.manager;

import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.TextConsole;

import com.cpputest.manager.view.TestResultView;

import java.lang.reflect.Field;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.widgets.Display;

public class VirtualConsoleMirror {

    private static IDocumentListener m_currentListener = null;
    // 1行分のデータを保持しておくバッファ
    private StringBuilder m_lineBuffer = new StringBuilder();
    
    public void scanAndHook() {
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                try {
                    m_lineBuffer.setLength(0);
                    TextConsole textConsole = getAppropriateConsole();

                    if (textConsole != null) {
                        // TextConsoleからDocumentを取得
                        IDocument doc = textConsole.getDocument();
                        
                        // 二重にリスナーを登録しないように登録済みのリスナーがあれば登録解除する
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
    
    private static TextConsole getAppropriateConsole() {
        CppUTestSetupHandler.EnvironmentType type = CppUTestSetupHandler.getEnvironmentType();

        // CodeWarriorかe2studioかでConsole取得方法を変える
        if (type == CppUTestSetupHandler.EnvironmentType.CodeWarrior) {
            return getCWTextConsole();
        } else if (type == CppUTestSetupHandler.EnvironmentType.E2Studio) {
            return getE2StudioTextConsole();
        }
        
        // どちらでもない場合はe2studioとする
        return getE2StudioTextConsole(); 
    }

    // RenesasのDebugConsoleからTextConsoleを取得する
    private static TextConsole getE2StudioTextConsole() {
        try {
            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            
            String consoleViewID = "com.renesas.cdt.debug.debugconsole.ui.views.DebugConsoleView";
            // 1. Viewを探す。なければ作成する
            IViewPart consoleView = page.findView(consoleViewID);
            if (consoleView == null) {
                consoleView = page.showView(consoleViewID, null, IWorkbenchPage.VIEW_CREATE);
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
    
    private static TextConsole getCWTextConsole() {
        // 1. ConsoleManagerを取得
        IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
        IConsole[] consoles = manager.getConsoles();

        // 2. 既存のコンソールから "CodeWarrior" という名前を含むものを探す
        for (IConsole console : consoles) {
            String name = console.getName();
            if (console instanceof TextConsole && name.contains("[CodeWarrior]")) {
                return (TextConsole) console;
            }
        }

        // 3. 見つからない場合は、一番最初の TextConsole を返してみる
        for (IConsole console : consoles) {
            if (console instanceof TextConsole) {
                return (TextConsole) console;
            }
        }

        return null;
    }
    
    private void processText(String text) {
        if (text == null || text.isEmpty()) return;

        // 受信した文字列をバッファに追加
        m_lineBuffer.append(text);

        // バッファ内に改行が含まれているか確認
        String currentBuffer = m_lineBuffer.toString();
        
        // 改行で分割（末尾に改行がない部分は、最後の要素になる）
        // -1 を指定することで、最後が改行の場合は末尾が空文字になる
        String[] parts = currentBuffer.split("\\r?\\n", -1);

        // 最後の要素以外は「完成した行」として処理
        for (int i = 0; i < parts.length - 1; i++) {
            String completeLine = parts[i];
            analyzeLine(completeLine);
        }

        // 最後の要素（まだ改行が来ていない未完成の文字列）をバッファに書き戻す
        m_lineBuffer.setLength(0);
        m_lineBuffer.append(parts[parts.length - 1]);
    }

    // 1行分の文字列を解析する
    private void analyzeLine(String line) {
        if (line.contains("TEST(")) {
            try {
                int start = line.indexOf("(") + 1;
                int end = line.indexOf(")");
                if (start <= 0 || end <= 0) return;

                String content = line.substring(start, end);
                String[] parts = content.split(",");
                if (parts.length >= 2) {
                    String group = parts[0].trim();
                    String name = parts[1].trim();

                    boolean isSuccess = !line.contains("Failure");
                    TestResultView.updateTestResult(group, name, isSuccess, true);
                }
            } catch (Exception e) {
                // パース失敗時はログを出力するなど
            }
        }
    }

}
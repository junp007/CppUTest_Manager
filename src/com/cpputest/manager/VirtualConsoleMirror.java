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

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.widgets.Display;

public class VirtualConsoleMirror {

    private static IDocumentListener m_currentListener = null;
    public static void scanAndHook() {
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                try {
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
        String productId = Platform.getProduct().getId();
        
        // CodeWarriorの場合: 通常 "com.freescale.core.ide.ide"
        // e2studioの場合: 通常 "com.renesas.cdt.p2.product" など
        if (productId.contains("freescale") || productId.contains("codewarrior")) {
            return getCWTextConsole();
        } else if (productId.contains("renesas")) {
            return getE2StudioTextConsole();
        }
        
        // どちらでもない場合は標準的な取得を試みる
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
            if (console instanceof TextConsole && name.contains("CodeWarrior") && name.contains(".elf")) {
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
package com.cpputest.manager;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin implements IDebugEventSetListener {

    public void start(BundleContext context) throws Exception {
        super.start(context);
        // デバッグイベントの監視を開始
        // DebugPlugin.getDefault().addDebugEventListener(this);
        // UIが準備できてから実行
        Display.getDefault().asyncExec(() -> {
            VirtualConsoleMirror.scanAndHook();
        });
    }

    @Override
    public void handleDebugEvents(DebugEvent[] events) {
        for (DebugEvent event : events) {
            // プロセスが作成（デバッグ開始）されたとき
            if (event.getKind() == DebugEvent.CREATE && event.getSource() instanceof IProcess) {
                // デバッグが開始（プロセス作成）されたらスキャン
                if (event.getKind() == DebugEvent.CREATE) {
                    // Viewが生成されるまで少し待機してからスキャン
                    Display.getDefault().timerExec(2000, () -> {
                        VirtualConsoleMirror.scanAndHook();
                    });
                }
            }
        }
    }

    public void stop(BundleContext context) throws Exception {
        DebugPlugin.getDefault().removeDebugEventListener(this);
        super.stop(context);
    }
}
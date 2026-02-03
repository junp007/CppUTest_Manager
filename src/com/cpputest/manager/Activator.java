package com.cpputest.manager;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin implements IDebugEventSetListener {

    private VirtualConsoleMirror m_console;
    public void start(BundleContext context) throws Exception {
        super.start(context);
        
        DebugPlugin.getDefault().addDebugEventListener(this);
        // UIが準備できてから実行
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                m_console = new VirtualConsoleMirror();
                m_console.scanAndHook();
            }
        });
    }

    @Override
    public void handleDebugEvents(DebugEvent[] events) {
        for (DebugEvent event : events) {
            // プロセスが作成（デバッグ開始）されたとき
            if (event.getKind() == DebugEvent.CREATE && event.getSource() instanceof IProcess) {
                m_console.scanAndHook();
            }
        }
    }

    public void stop(BundleContext context) throws Exception {
        DebugPlugin.getDefault().removeDebugEventListener(this);
        super.stop(context);
    }
}
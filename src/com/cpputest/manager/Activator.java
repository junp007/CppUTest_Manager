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

    public void start(BundleContext context) throws Exception {
        super.start(context);
        
        DebugPlugin.getDefault().addDebugEventListener(this);
        
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
                VirtualConsoleMirror.scanAndHook();
            }
        }
    }

    public void stop(BundleContext context) throws Exception {
        DebugPlugin.getDefault().removeDebugEventListener(this);
        super.stop(context);
    }
}
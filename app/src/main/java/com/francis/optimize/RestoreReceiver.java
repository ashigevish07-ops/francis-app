package com.francis.optimize;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class RestoreReceiver extends BroadcastReceiver {
    public static final String ACTION_RESTORE = "com.francis.optimize.ACTION_RESTORE_STOCK";

    @Override
    public void onReceive(Context context, Intent intent) {
        new Thread(() -> {
            RootEngine.restoreStock("com.dts.freefireth");
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(1001);
            }
        }).start();
        Toast.makeText(context, "FRANCIS OPTIMIZE: Stock Graphics Restored!", Toast.LENGTH_SHORT).show();
    }
}

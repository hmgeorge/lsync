package com.github.syncservice;

import com.github.syncservice.SyncService;
import android.content.Intent;
import android.content.Context;
import android.util.Log;
import android.content.BroadcastReceiver;
import java.lang.ref.WeakReference;

public class FakeBootReceiver extends BroadcastReceiver {
    private final String FAKE_BOOT_COMPLETED = "android.intent.action.FAKE_BOOT_COMPLETED";
    private final String FAKE_SHUTDOWN = "android.intent.action.FAKE_SHUTDOWN";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (FAKE_BOOT_COMPLETED.equals(action)) {
            Log.e("SyncBootReceiver", "Received boot complete");
            Intent i  = new Intent(context, SyncService.class);
            context.startService(i);
        } else if (FAKE_SHUTDOWN.equals(action)) {
            Log.e("SyncBootReceiver", "Received shutdown");
            Intent i  = new Intent(context, SyncService.class);
            context.stopService(i);
        }
    }
}

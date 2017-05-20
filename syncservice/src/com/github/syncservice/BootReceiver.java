package com.github.syncservice;

import com.github.syncservice.SyncService;
import android.content.Intent;
import android.content.Context;
import android.util.Log;
import android.content.BroadcastReceiver;
import java.lang.ref.WeakReference;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e("SyncBootReceiver", "Received boot complete");
        Intent i  = new Intent(context, SyncService.class);
        context.startService(i);
    }
}
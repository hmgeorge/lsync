package com.github.syncservice;

import android.app.Service;
import android.os.Bundle;
import android.os.Message;
import android.os.Looper;
import android.os.HandlerThread;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import java.lang.Thread;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.net.NetworkInfo;
import android.content.BroadcastReceiver;
import java.util.concurrent.Future;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.lang.String;
import java.util.concurrent.Callable;
import android.net.Uri;
import android.os.Environment;
import android.app.Notification;
import android.app.NotificationManager;

public class SyncService extends Service
{
    private static final String TAG = "SyncService";
    public static final String START = "com.github.syncservice.START";
    private static Context instance;
    private HandlerThread mThread;
    private Handler mHandler;

    private final int NET_CHANGE     = 1;
    private final int FS_CHANGE      = 2;
    private final int REMOTE_UPDATE  = 3;
    private final int FETCH_COMPLETE = 4;
    private final int NEW_ITEM_LIST  = 5;
    private final int QUIT           = 6;
    private final int START_TASKS    = 7;
    private final int CANCEL_TASKS   = 8;
    private final int GET_ITEM_LIST  = 9;

    private String mIpAddr;

    private Context getContext() { return instance; }

    HashMap<Integer, Future<Void> > mTasks = new HashMap<Integer, Future<Void> >();
    TaskExecutor mTaskExecutor = new TaskExecutor();

    // current ongoing fetch tasks
    HashMap<Integer, Integer> mCurrentFetchTasks = new HashMap<Integer, Integer>();

    // current known items
    HashMap<Integer, Item> mItems = new HashMap<Integer, Item>();

    // items to fetch
    ArrayList<Item> mFetchList = new ArrayList<Item>();
    boolean mFetchItemsPending = false;

    @Override
    public void onCreate()
    {
        super.onCreate();
        Log.e(TAG, "onCreate");
        IntentFilter i = new IntentFilter();
        i.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        i.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        //i.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        registerReceiver(mWifiReceiver, i);
        instance = this;
    }

    @Override
    public void onDestroy()
    {
        Log.e(TAG, "onDestroy");
        unregisterReceiver(mWifiReceiver);
        mTaskExecutor.shutdown();
        mHandler.sendMessage(mHandler.obtainMessage(QUIT));
        mThread.quitSafely();
        deleteWakelock();
	super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onRebind(Intent intent) {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "Received start command");
        createWakelock();
        mThread  = new HandlerThread("SyncService Handler");
        mThread.start();
        mHandler = new Handler(mThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    incWakelock();
                    switch (msg.what) {
                        case NET_CHANGE: {
                            String ip = (String)msg.obj;
                            if (ip.equals("0.0.0.0")) {
                                Log.v(TAG, "0 Ip, cancel tasks");
                                mHandler.sendEmptyMessage(CANCEL_TASKS);
                                mIpAddr = ip;
                            } else if (!ip.equals(mIpAddr)) {
                                Log.v(TAG, "new IP address " + ip + " start tasks");
                                mIpAddr = ip;
                                mHandler.sendEmptyMessage(START_TASKS);
                            }
                            break;
                        }
                        case FS_CHANGE: {
                            break;
                        }
                        case REMOTE_UPDATE: {
                            break;
                        }
                        case FETCH_COMPLETE: {
                            int taskID = (int)msg.arg1;
                            int status = (int)msg.arg2;
                            Item item = (Item)msg.obj;
                            onTaskComplete(taskID);
                            onFetchComplete(item, status);
                            break;
                        }
                        case GET_ITEM_LIST: {
                            onGetItemList();
                            break;
                        }
                        case NEW_ITEM_LIST: {
                            int taskID = (int)msg.arg1;
                            int status = (int)msg.arg2;
                            ArrayList<Item> items = (ArrayList<Item>)msg.obj;
                            onTaskComplete(taskID);
                            if (status == Status.OK) {
                                onNewItemList(items);
                            }
                            break;
                        }
                        case QUIT: {
                            Log.e(TAG, "got quit message");
                            break;
                        }
                        case CANCEL_TASKS: {
                            onCancelTasks();
                            break;
                        }
                        case START_TASKS: {
                            onStartTasks();
                            break;
                        }
                        default:
                            break;
                    }
                    decWakelock();
                }
        };
        startTasksIfPossible();
        return START_STICKY;
    }

    private void onCancelTasks() {
        if (mTasks.isEmpty()) {
            return;
        }

        Iterator<Integer> keys = mTasks.keySet().iterator();
        while (!mTasks.isEmpty()) {
            Integer id = keys.next();
            cancelTask(id);
        }
    }

    private void execute(final ATask t, final int completionMsg) {
        Callable completion = new Callable<Void>() {
                              @Override
                              public Void call() {
                                  Message m = mHandler.obtainMessage(completionMsg,
                                                                     t.id(),
                                                                     t.status(),
                                                                     t.result());
                                  mHandler.sendMessage(m);
                                  return null;
                              }};
        mTasks.put(t.id(), mTaskExecutor.execute(t, completion));
        incWakelock();
    }


    private void onGetItemList() {
        if (!mCurrentFetchTasks.isEmpty()) {
            mFetchItemsPending = true;
            return;
        }
        execute(new GetItemList(), NEW_ITEM_LIST);
    }

    private void onStartTasks() {
        if (mFetchList.isEmpty()) {
            mHandler.sendEmptyMessage(GET_ITEM_LIST);
            return;
        }

        while (!mFetchList.isEmpty()) {
            Item it = mFetchList.get(0);

            if (mCurrentFetchTasks.containsKey(it.id)) {
                int taskID = mCurrentFetchTasks.get(it.id);
                cancelTask(taskID);
                mCurrentFetchTasks.remove(it.id);
            }

            Fetch f = new Fetch(it);
            execute(f, FETCH_COMPLETE);
            mCurrentFetchTasks.put(it.id, f.id());
            mFetchList.remove(0);
        }
    }

    private void onTaskComplete(int taskID) {
        Log.v(TAG, "task " + taskID + " is complete");
        if (mTasks.containsKey(taskID)) {
            mTasks.remove(taskID);
        }
        decWakelock();
    }

    private void onFetchComplete(Item i, int status) {
        if (mCurrentFetchTasks.containsKey(i.id)) {
            mCurrentFetchTasks.remove(i.id);
            showNotification(i, status);
            if (status == Status.OK) {
                mItems.put(i.id, i); // add new or replace existing item
                broadcastNewScanIntent();
            }
        }

        if (mCurrentFetchTasks.isEmpty()) {
            if (mFetchItemsPending) {
                mFetchItemsPending = false;
                mHandler.sendEmptyMessage(GET_ITEM_LIST);
            }
        }
    }

    private void onNewItemList(ArrayList<Item> items) {
        List fetchList = new ArrayList<Item>();
        for (Item i : items) {
            if (mItems.containsKey(i.id)) {
                Item i1 = mItems.get(i.id);
                // check new sha1 against old sha1
                if (i1.sha1.equals(i.sha1)) {
                    continue;
                }
            }
            fetchList.add(i);
        }

        mFetchList.addAll(fetchList);
        mHandler.sendEmptyMessage(START_TASKS);
    }

    private void startTasksIfPossible() {
        WifiManager wm = (WifiManager)getContext().getSystemService(Context.WIFI_SERVICE);
        if (wm.isWifiEnabled()) {
            parseWifiInfo(wm.getConnectionInfo());
        } else {
           Log.v(TAG, "Wifi isn't enabled yet, delay start tasks");
        }
    }

    private void cancelTask(int taskID) {
        Future<Void> f = mTasks.get(taskID);
        Log.v(TAG, "Cancelling task " + taskID);
        f.cancel(true /*mayInterruptIfRunning*/);
        mTasks.remove(taskID);
    } 

    private final BroadcastReceiver mWifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.isAvailable()) {
                    if (info.isConnected()) {
                        String bssid = intent.getStringExtra(WifiManager.EXTRA_BSSID);
                        WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                        parseWifiInfo(wifiInfo);
                    }
                } else {
                    Log.v(TAG, "Network is unavailable");
                }
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1);
                //Log.e(TAG, "New wifi state " + wifiState);
            }
        }
        };

    private void parseWifiInfo(WifiInfo info) {
        int ipAddr = info.getIpAddress();
        StringBuffer ipBuf = new StringBuffer();
        // The unsigned right shift operator ">>>" shifts a zero into the leftmost
        // position, while the leftmost position after ">>" depends on sign
        // extension.
        ipBuf.append(ipAddr  & 0xff).append('.').
            append((ipAddr >>>= 8) & 0xff).append('.').
            append((ipAddr >>>= 8) & 0xff).append('.').
            append((ipAddr >>>= 8) & 0xff);
        if (ipAddr == 0) {
            Log.v(TAG, "Connection turned to 0, pause tasks");
        } else {
            // Log.v(TAG, "Network is connected to " + info.getSSID() + " ipaddress " + ipBuf);
        }
        mHandler.sendMessage(mHandler.obtainMessage(NET_CHANGE, ipBuf.toString()));
    }

    private void showNotification(Item i, int status) {
        Notification noti =
            new Notification.Builder(getContext())
            .setSmallIcon(status == Status.OK ? R.drawable.ic_action_download :
                                                R.drawable.ic_action_warning)
            .setContentTitle("SyncService")
            .setContentText("File sync " + i.name + " " +
                            (status == Status.OK ? "completed" : "failed"))
            .build();
        NotificationManager nm =
            (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(i.id, noti);
    }

    private void broadcastNewScanIntent() {
        //adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/24k.mp3
        //TBD: we could replace below intent with just intent to scan the item
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED,
                                 Uri.parse("file://"
                                           + Environment.getExternalStorageDirectory())));
    }

    private PowerManager.WakeLock mWakeLock;
    private void createWakelock() {
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SyncService");
    }

    private int mDebugWakelockCount = 0;
    private void incWakelock() {
        ++mDebugWakelockCount;
        mWakeLock.acquire();
    }

    private void decWakelock() throws IllegalStateException {
        --mDebugWakelockCount;
        if (mDebugWakelockCount < 0 ) {
            Log.e(TAG, "mDebugWakelockCount should not go < 0");
            throw new IllegalStateException();
        }
        mWakeLock.release();
    }

    private void deleteWakelock() throws IllegalStateException {
        if (mDebugWakelockCount != 0) {
            Log.e(TAG, "mDebugWakeLockCount != 0, power leak");
            throw new IllegalStateException();
        }
    }
}

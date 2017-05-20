package com.github.syncservice;

import java.io.InterruptedIOException;
import java.lang.InterruptedException;
import android.util.Log;

public class DummyTask extends ATask
{
    private static final String TAG = "Dummy";

    public DummyTask(int time) {
        super();
        mTime = time;
    }

    public void run() {
        try {
            for (int i = 0; i < mTime; i++) {
                Log.e(TAG, "Fake sleep 1");
                Thread.sleep(1000);
            }
        } catch (InterruptedException i) {

        } finally {

        }
    }

    public int status() {
        return 0;
    }

    public Object result() {
        return null;
    }

    private int mTime;
}
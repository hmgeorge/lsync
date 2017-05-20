package com.github.syncservice;

import android.os.Environment;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import android.util.Log;

public class Fetch extends ATask
{
    private static final String TAG = "GetItemList";

    public Fetch(Item it) {
        super();
        mItem = it;
        mBytesWritten = 0;
        mStatus = Status.IDLE;
    }

    public void run() {
        HttpURLConnection urlConn = null;
        URL url = null;
        try {
            url = new URL("http://server/items/" + mItem.id);
            urlConn = (HttpURLConnection) url.openConnection();

            if (urlConn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.v(TAG, "URL conn failed w/ err " + urlConn.getResponseCode());
                mStatus = Status.ERROR;
                return;
            }
            saveInputStream(urlConn.getInputStream(),
                            Environment.getExternalStorageDirectory());
        } catch (MalformedURLException e) {
            Log.v(TAG, url + " is a malformed URL");
            return;
        } catch (InterruptedIOException e) {
            Log.v(TAG, url + " fetch got interrupted");
            return;
        } catch (IOException e) {
            Log.v(TAG, url + " failed to do IO");
        } finally {
            if (urlConn != null) urlConn.disconnect();
        }
    }

    public int status() {
        return mStatus;
    }

    public Object result() {
        return mItem;
    }

    private void saveInputStream(InputStream i, File dir) throws IOException {
        byte[] buffer = new byte[256];
        BufferedInputStream in = new BufferedInputStream(i);

        File tmpFile = File.createTempFile(mItem.name, null, dir);
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmpFile));

        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
            mBytesWritten += bytesRead;
        }

        if (mBytesWritten == mItem.size) {
            File dstFile = new File(dir, mItem.name);
            tmpFile.renameTo(dstFile);
            mStatus = Status.OK;
        } else {
            tmpFile.delete();
            mStatus = Status.ERROR;
        }
    }
    private Item mItem;
    private int mStatus;
    private int mBytesWritten;
};

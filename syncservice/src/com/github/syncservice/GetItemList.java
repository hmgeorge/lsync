package com.github.syncservice;

import android.util.JsonReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.ArrayList;
import android.util.Log;

public class GetItemList extends ATask
{
    private static final String TAG = "GetItemList";

    public GetItemList() {
        super();
        mItemsList = new ArrayList<Item>();
        mStatus = Status.IDLE;
    }

    public void run() {
        HttpURLConnection urlConn = null;
        try {
            URL url = new URL("http://server/items");
            urlConn = (HttpURLConnection) url.openConnection();
            if (urlConn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                mStatus = Status.ERROR;
                Log.v(TAG, "URL conn failed w/ err " + urlConn.getResponseCode());
            } else {
                if (urlConn.getContentType().equals("application/json")) {
                    parseInputStream(urlConn.getInputStream());
                }
            }
        } catch (InterruptedIOException e) {
            Log.v(TAG, "GetItemList got interrupted");
        } catch (MalformedURLException e) {
            Log.v(TAG, "GetItemList malformed URL");
        } catch (IOException e) {
            Log.v(TAG, "failed to do IO");
        } finally {
            if (urlConn != null) urlConn.disconnect();
        }
    }

    public int status() {
        return mStatus;
    }

    public Object result() {
        return mItemsList;
    }

    /*
      [ #reader.beginArray
      { #reader.beginObject
      "id" : 
      "sha1" :
      "name" :
      "size" :
      }, #reader.endObject
      {

      } #loop till reader.hasNext()
      ] #reader.endArray
    */

    private Item readOneItem(JsonReader reader) throws IOException {
        int id = -1;
        String sha1 = "";
        String name = "";
        int size = 0;
        reader.beginObject(); // assert we are the beginning of an object
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.equals("id")) {
                id = reader.nextInt();
            } else if (key.equals("sha1")) {
                sha1 = reader.nextString();
            } else if (key.equals("name")) {
                name = reader.nextString();
            } else if (key.equals("size")) {
                size = reader.nextInt();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return new Item(id, sha1, name, size);
    }

    private List readItemsArray(JsonReader reader) throws IOException {
        List items = new ArrayList<Item>();

        reader.beginArray(); // assert that we are at start of an array
        while (reader.hasNext()) { // more elements in the array
            items.add(readOneItem(reader));
        }
        reader.endArray();
        return items;
    }

    private void parseInputStream(InputStream in) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(in));
        try {
            mItemsList.addAll(readItemsArray(reader));
            mStatus = Status.OK;
        } catch (IOException e) {
            mStatus = Status.ERROR;
        } finally {
            reader.close();
        }
    }

    private int mStatus;
    private ArrayList<Item> mItemsList;
};

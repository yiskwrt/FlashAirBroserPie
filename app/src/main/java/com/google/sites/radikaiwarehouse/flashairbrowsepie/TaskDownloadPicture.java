package com.google.sites.radikaiwarehouse.flashairbrowsepie;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TaskDownloadPicture extends AsyncTask<String, String, Bitmap> {
    private WifiSwitcher m_switcher;
    private String m_ssid;
    private String m_baseurl;
    private Callback m_callback;

    public TaskDownloadPicture(WifiSwitcher switcher, String ssid, String baseurl, Callback callback) {
        super();
        m_switcher = switcher;
        m_ssid = ssid;
        m_baseurl = baseurl;
        m_callback = callback;
    }

    @Override
    protected Bitmap doInBackground(String... picpathnames) {
        //Switch WiFi to FlashAir network
        int status = m_switcher.connectToCardSSID(m_ssid, new WifiSwitcher.MessageCallback() {
            @Override
            public void onMessage(String msg) {
                publishProgress(msg);
            }
        });
        if(status!=WifiSwitcher.CONNECT_OK) {
            return null;
        }

        //Download Picture
        publishProgress("downloading picture...");
        Bitmap result = null;
        try {
            String urlstr = m_baseurl + "/" + picpathnames[0];
            URL url = new URL(urlstr);
            HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
            InputStream in = new BufferedInputStream(urlConn.getInputStream());
            result = BitmapFactory.decodeStream(in);
            urlConn.disconnect();
        } catch (Exception e) {
            Log.d("TaskDownloadPicture", "error at HttpsURLConnection: " + e.toString());
        }

        //switch back to original WiFi SSID
        publishProgress("connecting original WiFi SSID");
        m_switcher.recoverToOrgSSID(null);

        //Done
        if(result!=null) {
            publishProgress("complete downloading: " + picpathnames[0]);
        } else {
            publishProgress("failed to download: " + picpathnames[0]);
        }
        return result;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        m_callback.onProgressUpdate(values[0]);
    }

    @Override
    protected void onPostExecute(Bitmap bm) {
        m_callback.onComplete(bm);
    }

    @Override
    protected void onCancelled() {m_callback.onComplete(null);}

    public interface Callback {
        public abstract void onProgressUpdate(String msg);
        public abstract void onComplete(Bitmap bm);
    }
}

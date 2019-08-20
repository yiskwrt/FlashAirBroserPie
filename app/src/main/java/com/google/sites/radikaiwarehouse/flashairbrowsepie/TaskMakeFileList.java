package com.google.sites.radikaiwarehouse.flashairbrowsepie;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TaskMakeFileList extends AsyncTask<String, String, List<String>> {
    private WifiSwitcher m_switcher;
    private String m_ssid;
    private String m_baseurl;
    private Callback m_callback;

    public TaskMakeFileList(WifiSwitcher switcher, String ssid, String baseurl, Callback callback) {
        super();
        m_switcher = switcher;
        m_ssid = ssid;
        m_baseurl = baseurl;
        m_callback = callback;
    }

    @Override
    protected List<String> doInBackground(String... dirnames) {
        ArrayList<String> filelist = new ArrayList<String>();

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

        //List of Directories
        ArrayList<String> dirlist = new ArrayList<String>();
        for(String dirname : dirnames) {
            dirlist.add(dirname);
        }
        while(!dirlist.isEmpty()) {
            String dirname = dirlist.remove(0);
            Log.d("TaskMakeFileList", "scanning " + dirname);
            try{
                String urlstr = m_baseurl + "/command.cgi?op=100&DIR=" + dirname;
                URL url = new URL(urlstr);
                HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
                BufferedReader br = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
                String content;
                while((content=br.readLine())!=null) {
                    Log.d("TaskMakeFileList", content);
                    FlashAirFileEntry entry = FlashAirFileEntry.fromString(content);
                    if(entry!=null) {
                        if(entry.isDir()) {
                            dirlist.add(entry.getFullPathName());
                        } else {
                            filelist.add(entry.getFullPathName());
                        }
                    }
                }
                br.close();
                urlConn.disconnect();
            } catch(Exception e) {
                Log.d("TaskMakeFileList", e.toString());
                break;
            }
        }

        //switch back to original WiFi SSID
        m_switcher.recoverToOrgSSID(null);

        //Done
        return filelist;
    }

    @Override
    protected void onProgressUpdate(String... messages) {
        super.onProgressUpdate(messages);
        m_callback.onProgressUpdate(messages[0]);
    }

    @Override
    protected void onPostExecute(List<String> filelist) {
        m_callback.onComplete(filelist);
    }

    @Override
    protected void onCancelled() {m_callback.onComplete(null);}

    public interface Callback {
        public abstract void onProgressUpdate(String msg);
        public abstract void onComplete(List<String> filelist);
    }
}

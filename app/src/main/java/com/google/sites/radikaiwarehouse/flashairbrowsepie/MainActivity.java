package com.google.sites.radikaiwarehouse.flashairbrowsepie;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Button btn_scanwifi;
    private Button btn_download;
    private TextView lbl_message;
    private ImageView picview_download;
    private String m_SSID = null;
    private WifiSwitcher m_wifiswitcher= null;
    static final private int REQCODE_ACCESS_COARSE_LOCATION = 0x01;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //get accesses to UI components
        btn_scanwifi = findViewById(R.id.btn_scanwifi);
        btn_download = findViewById(R.id.btn_download);
        lbl_message = findViewById(R.id.lbl_message);
        picview_download = findViewById(R.id.picview_download);

        /*
        //Check and Request permission (Simplest version)
        if(ContextCompat.checkSelfPermission((Activity)this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //No permission is granted currently; try to acquire
            ActivityCompat.requestPermissions(
                this,
                new String[] {Manifest.permission.ACCESS_COARSE_LOCATION},
                REQCODE_ACCESS_COARSE_LOCATION
            );
        }
        */

        //Check and Request permission (with care for users)
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //permission is not granted currently; try to acquire
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                //skip this flow when user has explicitly rejected the permissions permanently
                //give description to user, why this permission is essential to the application.
                new AlertDialog.Builder(this)
                        .setTitle("Why requesting permission?")
                        .setMessage("COARSE_LOCATION is essential to browse SSID list")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(
                                        MainActivity.this,
                                        new String[] {Manifest.permission.ACCESS_COARSE_LOCATION},
                                        REQCODE_ACCESS_COARSE_LOCATION
                                );
                            }
                        })
                        .create().show();
            }
            else {
                new AlertDialog.Builder(this)
                        .setTitle("permissions have been rejected")
                        .setMessage("COARSE_LOCATION has been rejected already; please check permission manually")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MainActivity.this.finish();
                            }
                        })
                        .create().show();
            }
        }

        //Wifi Control
        m_wifiswitcher = new WifiSwitcher(this);

        //Listing WiFi Access Point
        btn_scanwifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //WifiManager.startScan() should be called in advance.
                WifiManager wiman = (WifiManager)MainActivity.this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                List<ScanResult> results = wiman.getScanResults();
                SpannableStringBuilder sb = new SpannableStringBuilder();
                for(ScanResult res : results) {
                    if(res.SSID.length()>0) {
                        sb.append(
                            res.SSID,
                            new MyClickableSpan(res.SSID) {
                                @Override
                                void onClick(View view, String msg) {
                                    MainActivity.this.lbl_message.setText("Set SSID: " + msg);
                                    MainActivity.this.m_SSID = msg;
                                }
                            },
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        sb.append(":" + WifiManager.calculateSignalLevel(res.level, 10) + "/10\n");
                    }
                }
                MainActivity.this.lbl_message.setText(sb);
                MainActivity.this.lbl_message.setMovementMethod(LinkMovementMethod.getInstance());
            }
        });

        //Download File From FlashAir
        btn_download.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                TaskMakeFileList task = new TaskMakeFileList(m_SSID, "http://flashair");
                task.execute("/DCIM");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestcode, String[] permissions, int[] requestStatus){
        if(requestcode==REQCODE_ACCESS_COARSE_LOCATION) {
            //check if the requested permissions are granted by user.
            if(requestStatus.length != 1 || requestStatus[0] != PackageManager.PERMISSION_GRANTED) {
                //request was rejected; show error message and exit
                new AlertDialog.Builder(this)
                        .setTitle("User rejected permission request")
                        .setMessage("COARSE_LOCATION request was rejected")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MainActivity.this.finish();
                            }
                        })
                        .create().show();
            }
        }
    }

    /*Utility class to attach an action to link text*/
    public abstract class MyClickableSpan extends ClickableSpan {
        private String m_msg;
        public MyClickableSpan(String msg){
            m_msg = msg;
        }

        @Override
        public void onClick(View view) {
            onClick(view, m_msg);
        }

        abstract void onClick(View view, String msg);
    }

    /*Utility class to attach an action to link text*/
    public class TaskMakeFileList extends AsyncTask<String, String, List<String>> {
        private String m_ssid;
        private String m_baseurl;

        public TaskMakeFileList(String ssid, String baseurl) {
            super();
            m_ssid = ssid;
            m_baseurl = baseurl;
        }

        @Override
        protected List<String> doInBackground(String... dirnames) {
            ArrayList<String> filelist = new ArrayList<String>();

            //Switch WiFi to FlashAir network
            MainActivity.this.m_wifiswitcher.connectToCardSSID(m_ssid, new WifiSwitcher.MessageCallback() {
                @Override
                public void onMessage(String msg) {
                    publishProgress(msg);
                }
            });

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
            MainActivity.this.m_wifiswitcher.recoverToOrgSSID(null);

            //Done
            return filelist;
        }

        @Override
        protected void onProgressUpdate(String... messages) {
            super.onProgressUpdate(messages);
            MainActivity.this.lbl_message.setText(messages[0]);
        }

        @Override
        protected void onPostExecute(List<String> filelist) {
            if(filelist.size()>0) {
                SpannableStringBuilder sb = new SpannableStringBuilder();
                for(int i=0; (i<filelist.size()||i<20); i++) {
                    sb.append(
                        filelist.get(i),
                        new MyClickableSpan(filelist.get(i)) {
                            @Override
                            void onClick(View view, String msg) {
                                MainActivity.this.lbl_message.setText("start downloading");
                                new TaskDownloadPicture(m_SSID, "http://flashair").execute(msg);
                            }
                        },
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.append('\n');
                }
                MainActivity.this.lbl_message.setText(sb);
            } else {
                MainActivity.this.lbl_message.setText("no items are detected by crawler");
            }
        }
    }

    public class TaskDownloadPicture extends AsyncTask<String, String, Bitmap> {
        private String m_ssid;
        private String m_baseurl;

        public TaskDownloadPicture(String ssid, String baseurl) {
            super();
            m_ssid = ssid;
            m_baseurl = baseurl;
        }

        @Override
        protected Bitmap doInBackground(String... picpathnames) {
            //Switch WiFi to FlashAir network
            int status = MainActivity.this.m_wifiswitcher.connectToCardSSID(m_ssid, new WifiSwitcher.MessageCallback() {
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
                Log.d("MainActivity", "error at HttpsURLConnection: " + e.toString());
            }

            //switch back to original WiFi SSID
            publishProgress("connecting original WiFi SSID");
            MainActivity.this.m_wifiswitcher.recoverToOrgSSID(null);

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
            MainActivity.this.lbl_message.setText(values[0]);
        }

        @Override
        protected void onPostExecute(Bitmap bm) {
            picview_download.setImageBitmap(bm);
        }
    }
}

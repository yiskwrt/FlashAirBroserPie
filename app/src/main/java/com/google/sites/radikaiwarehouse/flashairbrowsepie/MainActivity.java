package com.google.sites.radikaiwarehouse.flashairbrowsepie;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Button btn_scanwifi;
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
                                //note that the result will be sent to onRequestPermissionsResult()
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
        //Todo: register/unregister BroadCast receiver for scan-complete messages
        btn_scanwifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Todo: WifiManager.startScan() should be called in advance.
                WifiManager wiman = (WifiManager)MainActivity.this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                MainActivity.this.formatWifiAPList(wiman.getScanResults());
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

    private void formatWifiAPList(List<ScanResult> wifilist) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for(ScanResult res : wifilist) {
            if(res.SSID.length()>0) {
                sb.append(
                        res.SSID,
                        new MyClickableSpan(res.SSID) {
                            @Override
                            void onClick(View view, String msg) {
                                MainActivity.this.m_SSID = msg;
                                formatSSIDConfirmation();
                            }
                        },
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(":" + WifiManager.calculateSignalLevel(res.level, 10) + "/10\n");
            }
        }
        MainActivity.this.lbl_message.setText(sb);
        MainActivity.this.lbl_message.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void formatSSIDConfirmation() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("Set SSID to " + m_SSID + "\n");
        sb.append(
                "start listing Files",
                new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        MainActivity.this.launchFileListMaker();
                    }
                },
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        MainActivity.this.lbl_message.setText(sb);
    }

    private void launchFileListMaker() {
        MainActivity.this.lbl_message.setText("start crawling directories");
        MainActivity.this.btn_scanwifi.setEnabled(false);  /*disable click until the task complete*/
        TaskMakeFileList task = new TaskMakeFileList(m_wifiswitcher, m_SSID, "http://flashair", new TaskMakeFileList.Callback() {
            @Override
            public void onProgressUpdate(String msg) {
                //Just print progress message
                MainActivity.this.lbl_message.setText(msg);
            }

            @Override
            public void onComplete(List<String> filelist) {
                MainActivity.this.formatFileList(filelist);
                MainActivity.this.btn_scanwifi.setEnabled(true);
            }
        });
        task.execute("/DCIM");
    }

    private void formatFileList(List<String> filelist) {
        //format and print clickable text
        final int limit = 50;
        if(filelist!=null && filelist.size()>0) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            for(int i=0; (i<filelist.size()&&i<limit); i++) {
                sb.append(
                        filelist.get(i),
                        new MainActivity.MyClickableSpan(filelist.get(i)) {
                            @Override
                            void onClick(View view, String msg) {
                                MainActivity.this.launchPictureDownloader(msg);
                            }
                        },
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append('\n');
            }
            if(filelist.size()>limit) {
                sb.append("...only " + limit + " pictures are listed...\n");
            }
            MainActivity.this.lbl_message.setText(sb);
        } else {
            MainActivity.this.lbl_message.setText("no items are detected by crawler");
        }
    }

    private void launchPictureDownloader(String filename) {
        MainActivity.this.lbl_message.setText("start downloading picture");
        MainActivity.this.btn_scanwifi.setEnabled(false);  /*disable click until the task complete*/
        TaskDownloadPicture task =new TaskDownloadPicture(m_wifiswitcher, m_SSID, "http://flashair", new TaskDownloadPicture.Callback(){

            @Override
            public void onProgressUpdate(String msg) {
                MainActivity.this.lbl_message.setText(msg);
            }

            @Override
            public void onComplete(Bitmap bm) {
                MainActivity.this.picview_download.setImageBitmap(bm);
                MainActivity.this.btn_scanwifi.setEnabled(true);
            }
        });
        task.execute(filename);
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
}

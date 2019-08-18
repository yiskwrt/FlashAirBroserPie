package com.google.sites.radikaiwarehouse.flashairbrowsepie;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class WifiSwitcher {
    private String m_org_ssid=null;
    private WifiManager m_wiman;
    private ConnectivityManager m_connman;

    public static final int CONNECT_OK = 0;
    public static final int CONNECT_ERROR_SWITCH_WIFI = 1;
    public static final int CONNECT_ERROR_WAIT_WIFI = 2;
    public static final int CONNECT_ERROR_BIND_WIFI = 3;
    public static final int CONNECT_ERROR_WAIT_DNS = 4;

    public WifiSwitcher(Context context) {
        m_wiman = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        m_connman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /*====API====*/
    public int connectToCardSSID(String card_ssid, MessageCallback callback) {
        callback.onMessage("making connection to " + card_ssid);

        //Save card/original SSID
        if(m_wiman.getWifiState()==WifiManager.WIFI_STATE_ENABLED) {
            m_org_ssid = m_wiman.getConnectionInfo().getSSID();
        } else {
            m_org_ssid = null;
        }

        //Try connect to specified SSID
        callback.onMessage("switchWifiSSID");
        if(!switchWifiSSID(card_ssid)) {
            return CONNECT_ERROR_SWITCH_WIFI;
        }

        //Wait for flashair network by checking current SSID
        callback.onMessage("waitForWifiNetwork");
        if(!waitForWifiNetwork(card_ssid, 20)) {
            return CONNECT_ERROR_WAIT_WIFI;
        }

        //Force bind sockets to wifi
        callback.onMessage("bindToWifiNetwork");
        if(!bindToWifiNetwork(4)) {
            return CONNECT_ERROR_BIND_WIFI;
        }

        //wait for DNS update
        callback.onMessage("waitForDNS");
        if(!waitForDNS("flashair", 20)) {
            return CONNECT_ERROR_WAIT_DNS;
        }
        return CONNECT_OK;
    }

    public int recoverToOrgSSID(MessageCallback callback) {
        if(m_org_ssid!=null) {
            switchWifiSSID(m_org_ssid);
        }
        return 0;
    }

    /*====Impl====*/
    /*switch Wifi AP to specified SSID*/
    private boolean switchWifiSSID(String ssid){
        String qssid = "\"" + ssid + "\"";
        for(WifiConfiguration wc : m_wiman.getConfiguredNetworks()) {
            String wcid = wc.SSID;
            if(wcid.equals(ssid) || wcid.equals(qssid)) {
                m_wiman.enableNetwork(wc.networkId, true);
                return true;
            }
        }
        return false;
    }

    /*check Wifi status; wait until the state gets STATE_ENABLED*/
    private boolean waitForWifiNetwork(String ssid, int retrycnt) {
        String qssid = "\"" + ssid + "\"";
        for(int cnt=0; cnt<retrycnt; cnt++) {
            int wifistate = m_wiman.getWifiState();
            String curssid = m_wiman.getConnectionInfo().getSSID();
            if(wifistate==WifiManager.WIFI_STATE_ENABLED && curssid!=null && (curssid.equals(ssid) || curssid.equals(qssid))) {
                return true;
            }
            //wait 1sec and retry
            try {
                Thread.sleep(1000);
            }catch(Exception e) {
                Log.d("WifiSwitcher", e.toString());
                break;
            }
        }
        //timeout
        return false;
    }

    /*pick out Wifi network from all active networks, then bind the application's default socket*/
    private boolean bindToWifiNetwork(int retrycnt) {
        for(int cnt=0; cnt<retrycnt; cnt++) {
            Network[] allnetworks = m_connman.getAllNetworks();
            for(Network network : allnetworks) {
                NetworkCapabilities cap = m_connman.getNetworkCapabilities(network);
                if(cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    m_connman.bindProcessToNetwork(network);
                    return true;
                }
            }
            //wait 500msec and retry
            try {
                Thread.sleep(500);
            }catch(Exception e) {
                Log.d("WifiSwitcher", e.toString());
                break;
            }
        }
        return false;
    }

    /*check if name resolver is updated, by making query of the specified hostname*/
    private boolean waitForDNS(String hostname, int retrycnt) {
        for(int cnt=0; cnt<retrycnt; cnt++) {
            try {
                InetAddress addr = InetAddress.getByName(hostname);
                return true;
            } catch (UnknownHostException e) {
                //could not find the specified host; try again
            }
            //wait 1sec and retry
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                Log.d("WifiSwitcher", e.toString());
                return false;
            }
        }
        return false;
    }

    public interface MessageCallback {
        abstract void onMessage(String msg);
    }
}

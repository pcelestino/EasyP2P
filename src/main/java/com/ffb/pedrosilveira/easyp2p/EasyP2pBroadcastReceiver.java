package com.ffb.pedrosilveira.easyp2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.IBinder;
import android.util.Log;


public class EasyP2pBroadcastReceiver extends BroadcastReceiver {

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private EasyP2p easyP2pInstance;

    final static String TAG = "EasyP2p";

    public EasyP2pBroadcastReceiver(EasyP2p easyP2pInstance, WifiP2pManager manager, WifiP2pManager.Channel channel) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.easyP2pInstance = easyP2pInstance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {

            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);

            if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Log.v(TAG, " WiFi P2P não está mais ativo.");
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

            if (manager == null) {
                return;
            }

            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if (networkInfo.isConnected() && networkInfo.getTypeName().equals("WIFI_P2P")) {
                easyP2pInstance.isConnectedToAnotherDevice = true;
                manager.requestConnectionInfo(channel, easyP2pInstance);

            } else {

                easyP2pInstance.isConnectedToAnotherDevice = false;

                Log.v(TAG, "Não conectado a outro dispositivo.");
                if (easyP2pInstance.thisDevice.isRegistered) {
                    if (easyP2pInstance.unexpectedDisconnect != null) {
                        easyP2pInstance.unregisterClient(easyP2pInstance.unexpectedDisconnect, null, false);
                    }
                }

            }

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {

            WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);

            if (easyP2pInstance.thisDevice.deviceName == null) {
                easyP2pInstance.thisDevice.deviceName = device.deviceName;
                easyP2pInstance.thisDevice.macAddress = device.deviceAddress;
            }
        }

    }

    @Override
    public IBinder peekService(Context myContext, Intent service) {
        return super.peekService(myContext, service);
    }
}
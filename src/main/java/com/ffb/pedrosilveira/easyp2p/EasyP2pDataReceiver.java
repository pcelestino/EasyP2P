package com.ffb.pedrosilveira.easyp2p;

import android.app.Activity;
import android.content.Context;

import com.ffb.pedrosilveira.easyp2p.callbacks.EasyP2pDataCallback;

@SuppressWarnings("WeakerAccess")
public class EasyP2pDataReceiver {

    protected EasyP2pDataCallback dataCallback;
    protected Context context;
    protected Activity activity;

    public EasyP2pDataReceiver(Activity activity, EasyP2pDataCallback dataCallback) {
        this.dataCallback = dataCallback;
        this.context = activity.getApplicationContext();
        this.activity = activity;
    }
}

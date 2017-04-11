package com.ffb.pedrosilveira.easyp2p;

import android.net.wifi.p2p.WifiP2pDevice;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import java.util.Map;

@SuppressWarnings("WeakerAccess")
@JsonObject
public class EasyP2pDevice {

    @JsonField
    public int id;
    @JsonField
    public Map<String, String> txtRecord;
    @JsonField
    public String deviceName;
    @JsonField
    public String serviceName;
    @JsonField
    public String instanceName;
    @JsonField
    public String readableName;
    @JsonField
    public boolean isRegistered;
    @JsonField
    public boolean isLeader;
    @JsonField
    protected int servicePort;
    @JsonField
    protected String TTP = "._tcp.";
    @JsonField
    protected String macAddress;
    @JsonField
    protected String serviceAddress;
    @JsonField
    protected boolean isHost;

    public EasyP2pDevice() {
    }

    public EasyP2pDevice(WifiP2pDevice device, Map<String, String> txtRecord) {
        this.serviceName = txtRecord.get("SERVICE_NAME");
        this.readableName = txtRecord.get("INSTANCE_NAME");
        this.instanceName = txtRecord.get("INSTANCE_NAME");
        this.deviceName = device.deviceName;
        this.macAddress = device.deviceAddress;
        this.txtRecord = txtRecord;

    }

    @Override
    public String toString() {
        return String.format("Dispositivo EasyP2p | Service Name: %s TTP: %s Human-Readable Name: %s", instanceName, TTP, readableName);
    }
}

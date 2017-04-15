package com.ffb.pedrosilveira.easyp2p.payloads.device;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonIgnore;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.ffb.pedrosilveira.easyp2p.EasyP2pDevice;
import com.ffb.pedrosilveira.easyp2p.payloads.Payload;

import java.util.ArrayList;

@SuppressWarnings("WeakerAccess")
@JsonObject
public class DeviceInfo extends Payload {

    @JsonIgnore
    public static final String TYPE = "DeviceInfo";

    // Mensagens
    @JsonIgnore
    public static final String INFORM_DEVICE = "informDevice";
    @JsonIgnore
    public static final String INFORM_DEVICES = "informDevices";
    @JsonIgnore
    public static final String REMOVE_DEVICE = "removeDevice";

    @JsonField
    public String message;
    @JsonField
    public EasyP2pDevice device;
    @JsonField
    public ArrayList<EasyP2pDevice> devices;

    public DeviceInfo() {
        super(TYPE);
    }
}

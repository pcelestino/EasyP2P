package com.ffb.pedrosilveira.easyp2p;

import java.util.HashMap;

@SuppressWarnings("WeakerAccess")
public class EasyP2pServiceData {

    protected HashMap<String, String> serviceData;

    public EasyP2pServiceData(String serviceName, int port, String instanceName) {
        serviceData = new HashMap<>();
        serviceData.put("SERVICE_NAME", "_" + serviceName);
        serviceData.put("SERVICE_PORT", String.valueOf(port));
        serviceData.put("INSTANCE_NAME", instanceName);
    }
}

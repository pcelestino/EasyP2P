package com.ffb.pedrosilveira.easyp2p.payloads;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

/**
 * Created by Pedro on 09/04/2017.
 */
@JsonObject
public class Payload {
    @JsonField
    public String type;

    public Payload() {
    }

    public Payload(String type) {
        this.type = type;
    }
}

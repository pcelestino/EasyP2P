package com.ffb.pedrosilveira.easyp2p.payloads.bully;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonIgnore;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.ffb.pedrosilveira.easyp2p.EasyP2pDevice;
import com.ffb.pedrosilveira.easyp2p.payloads.Payload;

/**
 * Created by Pedro on 09/04/2017.
 */
@JsonObject
public class BullyElection extends Payload {

    @JsonIgnore
    public static final String TYPE = "BullyElection";
    @JsonIgnore
    public static final String START_ELECTION = "startElection";
    @JsonIgnore
    public static final String RESPOND_OK = "respondOk";
    @JsonIgnore
    public static final String INFORM_LEADER = "informLeader";

    @JsonField
    public String message;
    @JsonField
    public EasyP2pDevice device;

    public BullyElection() {
        super(TYPE);
    }
}

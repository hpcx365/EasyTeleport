package pers.hpcx.easyteleport;

import java.util.UUID;

public class Request {
    
    public UUID sourceID;
    public UUID targetID;
    public int keepAliveTicks;
    
    public Request(UUID sourceID, UUID targetID, int keepAliveTicks) {
        this.sourceID = sourceID;
        this.targetID = targetID;
        this.keepAliveTicks = keepAliveTicks;
    }
}

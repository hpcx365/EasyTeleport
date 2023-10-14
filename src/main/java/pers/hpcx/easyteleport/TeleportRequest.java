package pers.hpcx.easyteleport;

import java.util.UUID;

public class TeleportRequest {
    
    public int keepAliveTicks;
    public final UUID originID;
    public final UUID targetID;
    
    public TeleportRequest(int keepAliveTicks, UUID originID, UUID targetID) {
        this.keepAliveTicks = keepAliveTicks;
        this.originID = originID;
        this.targetID = targetID;
    }
}

package pers.hpcx.easyteleport;

import java.util.UUID;

public class TeleportRequest {
    
    public int keepAliveTicks;
    public final UUID sourcePlayerID;
    public final UUID targetPlayerID;
    
    public TeleportRequest(int keepAliveTicks, UUID sourcePlayerID, UUID targetPlayerID) {
        this.keepAliveTicks = keepAliveTicks;
        this.sourcePlayerID = sourcePlayerID;
        this.targetPlayerID = targetPlayerID;
    }
}

package pers.hpcx.easyteleport;

import java.util.UUID;

public class TeleportRequest {
    
    public int keepAliveTicks;
    public UUID sourcePlayerID;
    public UUID targetPlayerID;
    
    public TeleportRequest(UUID sourcePlayerID, UUID targetPlayerID, int keepAliveTicks) {
        this.sourcePlayerID = sourcePlayerID;
        this.targetPlayerID = targetPlayerID;
        this.keepAliveTicks = keepAliveTicks;
    }
}

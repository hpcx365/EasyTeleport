package pers.hpcx.easyteleport;

import java.util.UUID;

public class ShareRequest {
    
    public int keepAliveTicks;
    public final UUID sourcePlayerID;
    public final UUID targetPlayerID;
    public final TeleportAnchor anchor;
    
    public ShareRequest(int keepAliveTicks, UUID sourcePlayerID, UUID targetPlayerID, TeleportAnchor anchor) {
        this.keepAliveTicks = keepAliveTicks;
        this.sourcePlayerID = sourcePlayerID;
        this.targetPlayerID = targetPlayerID;
        this.anchor = anchor;
    }
}

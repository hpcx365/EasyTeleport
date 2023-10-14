package pers.hpcx.easyteleport;

import java.util.UUID;

public class ShareRequest {
    
    public int keepAliveTicks;
    public final UUID originID;
    public final UUID targetID;
    public final TeleportAnchor anchor;
    
    public ShareRequest(int keepAliveTicks, UUID originID, UUID targetID, TeleportAnchor anchor) {
        this.keepAliveTicks = keepAliveTicks;
        this.originID = originID;
        this.targetID = targetID;
        this.anchor = anchor;
    }
}

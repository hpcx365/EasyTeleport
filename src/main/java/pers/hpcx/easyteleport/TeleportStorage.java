package pers.hpcx.easyteleport;

import java.util.Map;

public interface TeleportStorage {
    
    TeleportStack easyTeleport$getStack();
    
    Map<String, TeleportAnchor> easyTeleport$getAnchors();
}

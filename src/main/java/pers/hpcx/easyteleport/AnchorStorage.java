package pers.hpcx.easyteleport;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface AnchorStorage {
    
    @NotNull Map<String, Anchor> easyTeleport$getAnchors();
    
    @NotNull OperationStack<Anchor> easyTeleport$getTeleportStack(EasyTeleportMod mod);
}

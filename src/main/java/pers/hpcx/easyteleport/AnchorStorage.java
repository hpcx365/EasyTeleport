package pers.hpcx.easyteleport;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface AnchorStorage {
    
    @NotNull AnchorStack easyTeleport$getStack();
    
    @NotNull Map<String, Anchor> easyTeleport$getAnchors();
}

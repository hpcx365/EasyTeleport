package pers.hpcx.easyteleport;

import com.mojang.brigadier.arguments.ArgumentType;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

public enum EasyTeleportConfig {
    
    STACK_DEPTH(integer(1)),
    ANCHOR_LIMIT(integer(0)),
    REQUEST_TIMEOUT(integer(1000));
    
    private final String key;
    private final ArgumentType<?> type;
    
    EasyTeleportConfig(ArgumentType<?> type) {
        this.type = type;
        this.key = toString().toLowerCase().replace('_', '-');
    }
    
    public String getKey() {
        return key;
    }
    
    public ArgumentType<?> getType() {
        return type;
    }
}

package pers.hpcx.easyteleport;

public enum EasyTeleportConfigEnum {
    
    STACK_DEPTH, ANCHOR_LIMIT;
    
    private final String key;
    
    EasyTeleportConfigEnum() {
        this.key = toString().toLowerCase().replace('_', '-');
    }
    
    public String getKey() {
        return key;
    }
}

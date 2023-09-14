package pers.hpcx.easyteleport.mixin;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.hpcx.easyteleport.TeleportAnchor;
import pers.hpcx.easyteleport.TeleportStack;
import pers.hpcx.easyteleport.TeleportStorage;

import java.util.HashMap;
import java.util.Map;

import static pers.hpcx.easyteleport.EasyTeleportUtils.MOD_ID;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerMixin implements TeleportStorage {
    
    @Unique
    private TeleportStack stack = new TeleportStack();
    @Unique
    private Map<String, TeleportAnchor> anchors = new HashMap<>();
    
    @Override
    public TeleportStack easyTeleport$getStack() {
        return stack;
    }
    
    @Override
    public Map<String, TeleportAnchor> easyTeleport$getAnchors() {
        return anchors;
    }
    
    @Inject(at = @At("RETURN"), method = "readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V")
    public void readCustomDataFromNbt(NbtCompound nbt, CallbackInfo c) {
        NbtCompound modData = null;
        if (nbt.contains(MOD_ID)) {
            modData = nbt.getCompound(MOD_ID);
        } else if (nbt.contains("PlayerPersisted") && nbt.getCompound("PlayerPersisted").contains(MOD_ID)) {
            modData = nbt.getCompound("PlayerPersisted").getCompound(MOD_ID);
        }
        if (modData == null) {
            return;
        }
        if (modData.contains("stack")) {
            NbtCompound stackData = modData.getCompound("stack");
            stack = TeleportStack.fromCompound(stackData);
        }
        if (modData.contains("anchors")) {
            NbtCompound anchorData = modData.getCompound("anchors");
            for (String anchorName : anchorData.getKeys()) {
                anchors.put(anchorName, TeleportAnchor.fromCompound(anchorData.getCompound(anchorName)));
            }
        }
    }
    
    @Inject(at = @At("RETURN"), method = "writeCustomDataToNbt(Lnet/minecraft/nbt/NbtCompound;)V")
    public void writeCustomDataToNbt(NbtCompound nbt, CallbackInfo c) {
        NbtCompound modData = new NbtCompound();
        
        NbtCompound stackData = stack.toCompound();
        NbtCompound anchorData = new NbtCompound();
        anchors.forEach((anchorName, anchor) -> anchorData.put(anchorName, anchor.toCompound()));
        
        modData.put("stack", stackData);
        modData.put("anchors", anchorData);
        nbt.put(MOD_ID, modData);
    }
    
    @Inject(at = @At("RETURN"), method = "copyFrom(Lnet/minecraft/server/network/ServerPlayerEntity;Z)V")
    public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo c) {
        ServerPlayerMixin mixin = (ServerPlayerMixin) (Object) oldPlayer;
        stack = mixin.stack;
        anchors = mixin.anchors;
    }
}

package pers.hpcx.easyteleport.mixin;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.hpcx.easyteleport.Anchor;
import pers.hpcx.easyteleport.AnchorStorage;
import pers.hpcx.easyteleport.EasyTeleportMod;
import pers.hpcx.easyteleport.OperationStack;

import java.util.HashMap;
import java.util.Map;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerMixin implements AnchorStorage {
    
    @Unique
    private OperationStack<Anchor> teleportStack = null;
    @Unique
    private Map<String, Anchor> anchors = new HashMap<>();
    
    @Override
    public @NotNull Map<String, Anchor> easyTeleport$getAnchors() {
        return anchors;
    }
    
    @Override
    public @NotNull OperationStack<Anchor> easyTeleport$getTeleportStack(EasyTeleportMod mod) {
        if (teleportStack == null) {
            teleportStack = new OperationStack<>(mod.stackDepth);
        }
        return teleportStack;
    }
    
    @Inject(at = @At("RETURN"), method = "readCustomDataFromNbt(Lnet/minecraft/nbt/NbtCompound;)V")
    public void readCustomDataFromNbt(NbtCompound nbt, CallbackInfo c) {
        NbtCompound data = null;
        if (nbt.contains(EasyTeleportMod.MOD_ID)) {
            data = nbt.getCompound(EasyTeleportMod.MOD_ID);
        } else if (nbt.contains("PlayerPersisted") && nbt.getCompound("PlayerPersisted").contains(EasyTeleportMod.MOD_ID)) {
            data = nbt.getCompound("PlayerPersisted").getCompound(EasyTeleportMod.MOD_ID);
        }
        if (data != null && data.contains("anchorData")) {
            NbtCompound anchorData = data.getCompound("anchorData");
            for (String name : anchorData.getKeys()) {
                anchors.put(name, Anchor.fromCompound(anchorData.getCompound(name)));
            }
        }
    }
    
    @Inject(at = @At("RETURN"), method = "writeCustomDataToNbt(Lnet/minecraft/nbt/NbtCompound;)V")
    public void writeCustomDataToNbt(NbtCompound nbt, CallbackInfo c) {
        NbtCompound data = new NbtCompound();
        NbtCompound anchorData = new NbtCompound();
        anchors.forEach((name, anchor) -> anchorData.put(name, anchor.toCompound()));
        data.put("anchorData", anchorData);
        nbt.put(EasyTeleportMod.MOD_ID, data);
    }
    
    @Inject(at = @At("RETURN"), method = "copyFrom(Lnet/minecraft/server/network/ServerPlayerEntity;Z)V")
    public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo c) {
        anchors = ((ServerPlayerMixin) (Object) oldPlayer).anchors;
    }
}

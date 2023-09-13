package pers.hpcx.easyteleport;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import static net.minecraft.text.Text.literal;
import static net.minecraft.util.Formatting.*;
import static pers.hpcx.easyteleport.EasyTeleportUtils.format;
import static pers.hpcx.easyteleport.EasyTeleportUtils.sendMessage;

public class TeleportStack {
    
    public final LinkedList<TeleportAnchor> tppAnchors = new LinkedList<>();
    public final LinkedList<TeleportAnchor> tpbAnchors = new LinkedList<>();
    
    public void afterDeath(ServerPlayerEntity player, int depth) {
        while (tpbAnchors.size() >= depth) {
            tpbAnchors.removeLast();
        }
        tpbAnchors.addFirst(new TeleportAnchor(player.getPos(), player.getServerWorld().getRegistryKey()));
        tppAnchors.clear();
    }
    
    public int tpp(ServerPlayerEntity player, String anchorName, int depth) {
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        TeleportAnchor anchor = anchors.get(anchorName);
        if (anchor == null) {
            sendMessage(player.getCommandSource(), false, literal("Anchor ").formatted(GRAY), literal(anchorName).formatted(RED),
                    literal(" not found.").formatted(GRAY));
            return 0;
        }
        tpp(player, anchor, anchorName, false, depth);
        return 1;
    }
    
    public void tpp(ServerPlayerEntity player, ServerPlayerEntity target, int depth) {
        while (tpbAnchors.size() >= depth) {
            tpbAnchors.removeLast();
        }
        tpbAnchors.addFirst(new TeleportAnchor(player.getPos(), player.getServerWorld().getRegistryKey()));
        Vec3d position = target.getPos();
        ServerWorld world = target.getServerWorld();
        player.teleport(world, position.x, position.y, position.z, player.getYaw(), player.getPitch());
        sendMessage(player.getCommandSource(), true, literal("Teleport to ").formatted(GREEN), literal(target.getName().getString()).formatted(GOLD),
                literal(" successfully.").formatted(GREEN));
        sendMessage(target.getCommandSource(), true, literal(player.getName().getString()).formatted(GOLD), literal(" is teleported to you.").formatted(GREEN));
    }
    
    public int tpp(ServerPlayerEntity player, int depth) {
        if (tppAnchors.isEmpty()) {
            sendMessage(player.getCommandSource(), false, literal("Cannot tpp anymore.").formatted(GRAY));
            return 0;
        } else {
            tpp(player, tppAnchors.removeFirst(), null, true, depth);
            return 1;
        }
    }
    
    public int tpb(ServerPlayerEntity player, int depth) {
        if (tpbAnchors.isEmpty()) {
            sendMessage(player.getCommandSource(), false, literal("Cannot tpb anymore.").formatted(GRAY));
            return 0;
        } else {
            tpb(player, tpbAnchors.removeFirst(), null, true, depth);
            return 1;
        }
    }
    
    public void tpp(ServerPlayerEntity player, TeleportAnchor anchor, String name, boolean temp, int depth) {
        while (tpbAnchors.size() >= depth) {
            tpbAnchors.removeLast();
        }
        tpbAnchors.addFirst(new TeleportAnchor(player.getPos(), player.getServerWorld().getRegistryKey()));
        if (!temp) {
            tppAnchors.clear();
        }
        Vec3d position = anchor.position();
        ServerWorld world = player.getServer().getWorld(anchor.world());
        player.teleport(world, position.x, position.y, position.z, player.getYaw(), player.getPitch());
        if (temp) {
            sendMessage(player.getCommandSource(), true, literal("Teleport to ").formatted(GREEN), literal(format(anchor.position())).formatted(GRAY),
                    literal(".").formatted(GREEN));
        } else {
            sendMessage(player.getCommandSource(), true, literal("Teleport to ").formatted(GREEN), literal(name).formatted(YELLOW),
                    literal(" successfully.").formatted(GREEN));
        }
    }
    
    public void tpb(ServerPlayerEntity player, TeleportAnchor anchor, String name, boolean temp, int depth) {
        while (tppAnchors.size() >= depth) {
            tppAnchors.removeLast();
        }
        tppAnchors.addFirst(new TeleportAnchor(player.getPos(), player.getServerWorld().getRegistryKey()));
        if (!temp) {
            tpbAnchors.clear();
        }
        Vec3d position = anchor.position();
        ServerWorld world = player.getServer().getWorld(anchor.world());
        player.teleport(world, position.x, position.y, position.z, player.getYaw(), player.getPitch());
        if (temp) {
            sendMessage(player.getCommandSource(), true, literal("Teleport to ").formatted(GREEN), literal(format(anchor.position())).formatted(GRAY),
                    literal(".").formatted(GREEN));
        } else {
            sendMessage(player.getCommandSource(), true, literal("Teleport to ").formatted(GREEN), literal(name).formatted(YELLOW),
                    literal(" successfully.").formatted(GREEN));
        }
    }
    
    public NbtCompound toCompound() {
        NbtCompound data = new NbtCompound();
        NbtCompound tpp = new NbtCompound();
        NbtCompound tpb = new NbtCompound();
        
        Iterator<TeleportAnchor> iterator;
        iterator = tppAnchors.iterator();
        for (int i = 0; i < tppAnchors.size(); i++) {
            tpp.put(Integer.toString(i), iterator.next().toCompound());
        }
        iterator = tpbAnchors.iterator();
        for (int i = 0; i < tpbAnchors.size(); i++) {
            tpb.put(Integer.toString(i), iterator.next().toCompound());
        }
        
        data.put("tpp", tpp);
        data.put("tpb", tpb);
        return data;
    }
    
    public static TeleportStack fromCompound(NbtCompound data) {
        NbtCompound tpp = data.getCompound("tpp");
        NbtCompound tpb = data.getCompound("tpb");
        
        TeleportStack stack = new TeleportStack();
        for (int i = 0; i < tpp.getSize(); i++) {
            stack.tppAnchors.addLast(TeleportAnchor.fromCompound(tpp.getCompound(Integer.toString(i))));
        }
        for (int i = 0; i < tpb.getSize(); i++) {
            stack.tpbAnchors.addLast(TeleportAnchor.fromCompound(tpb.getCompound(Integer.toString(i))));
        }
        
        return stack;
    }
}

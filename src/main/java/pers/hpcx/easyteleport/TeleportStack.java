package pers.hpcx.easyteleport;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Iterator;
import java.util.LinkedList;

import static pers.hpcx.easyteleport.EasyTeleportUtils.*;

public class TeleportStack {
    
    public final LinkedList<TeleportAnchor> tppAnchors = new LinkedList<>();
    public final LinkedList<TeleportAnchor> tpbAnchors = new LinkedList<>();
    
    public int tpp(ServerPlayerEntity player, int depth) {
        if (tppAnchors.isEmpty()) {
            send(player, false, gray("Cannot tpp anymore."));
            return 0;
        } else {
            tpp(player, tppAnchors.removeFirst(), null, true, depth);
            return 1;
        }
    }
    
    public int tpb(ServerPlayerEntity player, int depth) {
        if (tpbAnchors.isEmpty()) {
            send(player, false, gray("Cannot tpb anymore."));
            return 0;
        } else {
            tpb(player, tpbAnchors.removeFirst(), null, true, depth);
            return 1;
        }
    }
    
    public void afterDeath(ServerPlayerEntity player, int depth) {
        while (tpbAnchors.size() >= depth) {
            tpbAnchors.removeLast();
        }
        tpbAnchors.addFirst(new TeleportAnchor(player));
        tppAnchors.clear();
    }
    
    public int tpp(ServerPlayerEntity player, String anchorName, int depth) {
        TeleportAnchor anchor = ((TeleportStorage) player).easyTeleport$getAnchors().get(anchorName);
        if (anchor == null) {
            send(player, false, gray("Anchor "), red(anchorName), gray(" not found."));
            return 0;
        } else {
            tpp(player, anchor, anchorName, false, depth);
            return 1;
        }
    }
    
    public void tpp(ServerPlayerEntity player, ServerPlayerEntity target, int depth) {
        while (tpbAnchors.size() >= depth) {
            tpbAnchors.removeLast();
        }
        tpbAnchors.addFirst(new TeleportAnchor(player));
        teleport(player, target);
        send(player, true, green("Teleport to "), player(target), green("."));
        send(target, true, player(player), green(" is teleported to you."));
    }
    
    public void tpp(ServerPlayerEntity player, TeleportAnchor anchor, String name, boolean temp, int depth) {
        while (tpbAnchors.size() >= depth) {
            tpbAnchors.removeLast();
        }
        tpbAnchors.addFirst(new TeleportAnchor(player));
        if (!temp) {
            tppAnchors.clear();
        }
        teleport(player, anchor);
        send(player, true, green("Teleport to "), temp ? position(anchor.position()) : anchor(name, anchor), green("."));
    }
    
    public void tpb(ServerPlayerEntity player, TeleportAnchor anchor, String name, boolean temp, int depth) {
        while (tppAnchors.size() >= depth) {
            tppAnchors.removeLast();
        }
        tppAnchors.addFirst(new TeleportAnchor(player));
        if (!temp) {
            tpbAnchors.clear();
        }
        teleport(player, anchor);
        send(player, true, green("Teleport to "), temp ? position(anchor.position()) : anchor(name, anchor), green("."));
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

package pers.hpcx.easyteleport;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Iterator;
import java.util.LinkedList;

import static pers.hpcx.easyteleport.EasyTeleportUtils.*;

public class TeleportStack {
    
    public static final String TEMP = "temp";
    
    public final LinkedList<TeleportAnchor> tppAnchors = new LinkedList<>();
    public final LinkedList<TeleportAnchor> tpbAnchors = new LinkedList<>();
    
    public void afterDeath(ServerPlayerEntity player, int depth) {
        while (tpbAnchors.size() >= depth) {
            tpbAnchors.removeLast();
        }
        tpbAnchors.addFirst(new TeleportAnchor(TEMP, player));
        tppAnchors.clear();
    }
    
    public int tpp(ServerCommandSource source, ServerPlayerEntity player, int depth) {
        if (tppAnchors.isEmpty()) {
            send(source, false, gray("Cannot tpp anymore"));
            return 0;
        } else {
            tpp(source, player, null, tppAnchors.removeFirst(), true, false, depth);
            return 1;
        }
    }
    
    public int tpb(ServerCommandSource source, ServerPlayerEntity player, int depth) {
        if (tpbAnchors.isEmpty()) {
            send(source, false, gray("Cannot tpb anymore"));
            return 0;
        } else {
            tpb(source, player, null, tpbAnchors.removeFirst(), true, false, depth);
            return 1;
        }
    }
    
    public int tpp(ServerCommandSource source, ServerPlayerEntity player, EasyTeleport mod, String anchorName, int depth) {
        TeleportAnchor anchor = ((TeleportStorage) player).easyTeleport$getAnchors().get(anchorName);
        if (anchor != null) {
            tpp(source, player, anchorName, anchor, false, false, depth);
            return 1;
        }
        anchor = mod.publicAnchors.get(anchorName);
        if (anchor != null) {
            tpp(source, player, anchorName, anchor, false, true, depth);
            return 1;
        }
        send(source, false, gray("Anchor "), red(anchorName), gray(" not found"));
        return 0;
    }
    
    public void tpp(ServerCommandSource source, ServerPlayerEntity player, ServerPlayerEntity target, int depth) {
        while (tpbAnchors.size() >= depth) {
            tpbAnchors.removeLast();
        }
        tpbAnchors.addFirst(new TeleportAnchor(TEMP, player));
        teleport(player, target);
        send(source, true, green("Teleport to "), player(target));
        send(source, true, player(player), green(" is teleported to you"));
    }
    
    public void tpp(ServerCommandSource source, ServerPlayerEntity player, String anchorName, TeleportAnchor anchor, boolean isTemp, boolean isPublic, int depth) {
        while (tpbAnchors.size() >= depth) {
            tpbAnchors.removeLast();
        }
        tpbAnchors.addFirst(new TeleportAnchor(TEMP, player));
        if (!isTemp) {
            tppAnchors.clear();
        }
        teleport(source, player, anchor);
        send(source, true, green("Teleport to "), anchor(anchorName, anchor, isTemp, isPublic));
    }
    
    public void tpb(ServerCommandSource source, ServerPlayerEntity player, String anchorName, TeleportAnchor anchor, boolean isTemp, boolean isPublic, int depth) {
        while (tppAnchors.size() >= depth) {
            tppAnchors.removeLast();
        }
        tppAnchors.addFirst(new TeleportAnchor(TEMP, player));
        if (!isTemp) {
            tpbAnchors.clear();
        }
        teleport(source, player, anchor);
        send(source, true, green("Teleport to "), anchor(anchorName, anchor, isTemp, isPublic));
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

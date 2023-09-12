package pers.hpcx.easyteleport;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedList;

public class AnchorStack {
    
    public final LinkedList<Anchor> tppAnchors = new LinkedList<>();
    public final LinkedList<Anchor> tpbAnchors = new LinkedList<>();
    
    public void tpp(Anchor anchor, int depth) {
        if (depth <= 0) {
            throw new IllegalArgumentException("depth must be positive.");
        }
        while (tppAnchors.size() >= depth) {
            tppAnchors.removeLast();
        }
        tppAnchors.addFirst(anchor);
        tpbAnchors.clear();
    }
    
    @Nullable
    public Anchor tpp() {
        if (tpbAnchors.isEmpty()) {
            return null;
        }
        Anchor anchor = tpbAnchors.removeFirst();
        tppAnchors.addFirst(anchor);
        return anchor;
    }
    
    @Nullable
    public Anchor tpb() {
        if (tppAnchors.isEmpty()) {
            return null;
        }
        Anchor anchor = tppAnchors.removeFirst();
        tpbAnchors.addFirst(anchor);
        return anchor;
    }
    
    public NbtCompound toCompound() {
        NbtCompound data = new NbtCompound();
        NbtCompound tpp = new NbtCompound();
        NbtCompound tpb = new NbtCompound();
        
        Iterator<Anchor> iterator;
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
    
    public static AnchorStack fromCompound(NbtCompound data) {
        NbtCompound tpp = data.getCompound("tpp");
        NbtCompound tpb = data.getCompound("tpb");
        
        AnchorStack stack = new AnchorStack();
        for (int i = 0; i < tpp.getSize(); i++) {
            stack.tppAnchors.addLast(Anchor.fromCompound(tpp.getCompound(Integer.toString(i))));
        }
        for (int i = 0; i < tpb.getSize(); i++) {
            stack.tpbAnchors.addLast(Anchor.fromCompound(tpb.getCompound(Integer.toString(i))));
        }
        
        return stack;
    }
}

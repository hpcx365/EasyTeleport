package pers.hpcx.easyteleport;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.NoSuchElementException;

public class OperationStack<T> {
    
    private int maxDepth;
    private final LinkedList<T> invoked = new LinkedList<>();
    private final LinkedList<T> revoked = new LinkedList<>();
    
    public OperationStack(int maxDepth) {
        setMaxDepth(maxDepth);
    }
    
    public int getMaxDepth() {
        return maxDepth;
    }
    
    public void setMaxDepth(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException();
        }
        this.maxDepth = maxDepth;
    }
    
    public int countInvoked() {
        return invoked.size();
    }
    
    public int countRevoked() {
        return revoked.size();
    }
    
    @Nullable
    public T peekInvoked() {
        try {
            return invoked.getFirst();
        } catch (NoSuchElementException e) {
            return null;
        }
    }
    
    @Nullable
    public T peekRevoked() {
        try {
            return revoked.getFirst();
        } catch (NoSuchElementException e) {
            return null;
        }
    }
    
    @Nullable
    public T peekInvoked(int depth) {
        try {
            return invoked.get(depth);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }
    
    @Nullable
    public T peekRevoked(int depth) {
        try {
            return revoked.get(depth);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }
    
    public void clear() {
        invoked.clear();
        revoked.clear();
    }
    
    public void invoke(T opt) {
        while (invoked.size() >= maxDepth) {
            invoked.removeLast();
        }
        invoked.addFirst(opt);
        revoked.clear();
    }
    
    @Nullable
    public T invoke() {
        if (revoked.isEmpty()) {
            return null;
        }
        T opt = revoked.removeFirst();
        invoked.addFirst(opt);
        return opt;
    }
    
    @Nullable
    public T revoke() {
        if (invoked.isEmpty()) {
            return null;
        }
        T opt = invoked.removeFirst();
        revoked.addFirst(opt);
        return opt;
    }
}

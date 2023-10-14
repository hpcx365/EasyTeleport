package pers.hpcx.easyteleport;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public record TeleportAnchor(String name, Vec3d position, RegistryKey<World> world) {
    
    public TeleportAnchor(String name, ServerPlayerEntity player) {
        this(name, player.getPos(), player.getServerWorld().getRegistryKey());
    }
    
    public NbtCompound toCompound() {
        NbtCompound data = new NbtCompound();
        data.putString("name", name);
        data.putDouble("x", position.x);
        data.putDouble("y", position.y);
        data.putDouble("z", position.z);
        data.putString("world", world.getValue().toString());
        return data;
    }
    
    public static TeleportAnchor fromCompound(NbtCompound data) {
        String name = data.getString("name");
        double x = data.getDouble("x");
        double y = data.getDouble("y");
        double z = data.getDouble("z");
        String world = data.getString("world");
        return new TeleportAnchor(name, new Vec3d(x, y, z), RegistryKey.of(RegistryKeys.WORLD, new Identifier(world)));
    }
}

package pers.hpcx.easyteleport;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public record Anchor(Vec3d position, RegistryKey<World> world) {
    
    public NbtCompound toCompound() {
        NbtCompound data = new NbtCompound();
        data.putDouble("x", position.x);
        data.putDouble("y", position.y);
        data.putDouble("z", position.z);
        data.putString("world", world.getValue().toString());
        return data;
    }
    
    public static Anchor fromCompound(NbtCompound data) {
        double x = data.getDouble("x");
        double y = data.getDouble("y");
        double z = data.getDouble("z");
        String world = data.getString("world");
        return new Anchor(new Vec3d(x, y, z), RegistryKey.of(RegistryKeys.WORLD, new Identifier(world)));
    }
}

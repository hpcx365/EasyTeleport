package pers.hpcx.easyteleport;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.UUID;

import static net.minecraft.util.Formatting.*;

public final class EasyTeleportUtils {
    
    public static final String MOD_ID = "easyteleport";
    public static final String CONFIG_COMMENTS = "easy-teleport mod config";
    public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("easy-teleport.properties");
    
    private EasyTeleportUtils() {
    }
    
    public static String format(Vec3d position) {
        return "(%.02f, %.02f, %.02f)".formatted(position.x, position.y, position.z);
    }
    
    public static void sendMessage(ServerCommandSource source, boolean success, MutableText... texts) {
        if (texts.length == 0) {
            return;
        }
        MutableText comp = MutableText.of(TextContent.EMPTY);
        for (MutableText text : texts) {
            comp.append(text);
        }
        if (success) {
            source.sendMessage(comp);
        } else {
            source.sendError(comp);
        }
    }
    
    public static void noRequests(ServerPlayerEntity player) {
        sendMessage(player.getCommandSource(), false, Text.literal("You have no request to accept.").formatted(GRAY));
    }
    
    public static UUID selectPlayerID(ServerPlayerEntity player, Collection<GameProfile> profiles) {
        Iterator<GameProfile> iterator = profiles.iterator();
        if (!iterator.hasNext()) {
            playerNotFound(player);
            return null;
        }
        UUID id = iterator.next().getId();
        if (iterator.hasNext()) {
            sendMessage(player.getCommandSource(), false, Text.literal("Please specify only one player.").formatted(RED));
            return null;
        }
        if (id.equals(player.getGameProfile().getId())) {
            sendMessage(player.getCommandSource(), false, Text.literal("Cannot teleport to yourself.").formatted(RED));
            return null;
        }
        if (player.getServer().getPlayerManager().getPlayer(id) == null) {
            playerNotFound(player);
            return null;
        }
        return id;
    }
    
    public static void playerNotFound(ServerPlayerEntity player) {
        sendMessage(player.getCommandSource(), false, Text.literal("Player not found.").formatted(RED));
    }
    
    public static void notifyRequestTimedOut(MinecraftServer server, UUID sourceID, UUID targetID) {
        ServerPlayerEntity source = server.getPlayerManager().getPlayer(sourceID);
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetID);
        if (source != null && target != null) {
            sendMessage(source.getCommandSource(), true, Text.literal("Teleport request to ").formatted(GRAY),
                    Text.literal(target.getName().getString()).formatted(GOLD), Text.literal(" has timed out.").formatted(GRAY));
            sendMessage(target.getCommandSource(), true, Text.literal("Teleport request from ").formatted(GRAY),
                    Text.literal(source.getName().getString()).formatted(GOLD), Text.literal(" has timed out.").formatted(GRAY));
        }
    }
    
    public static int storeProperty(ServerCommandSource source, String key, String value) {
        sendMessage(source, true, Text.literal(key).formatted(LIGHT_PURPLE), Text.literal(" set to ").formatted(GREEN), Text.literal(value).formatted(GOLD),
                Text.literal(" successfully.").formatted(GREEN));
        Properties properties = new Properties();
        
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            properties.load(in);
        } catch (IOException e) {
            sendMessage(source, false, Text.literal("Failed to read config file: ").formatted(GRAY), Text.literal(e.getMessage()).formatted(RED));
            return 0;
        }
        
        properties.setProperty(key, value);
        
        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(out, CONFIG_COMMENTS);
        } catch (IOException e) {
            sendMessage(source, false, Text.literal("Failed to write config file: ").formatted(GRAY), Text.literal(e.getMessage()).formatted(RED));
            return 0;
        }
        
        return 1;
    }
    
    public static int storeProperties(ServerCommandSource source, String[] keys, String[] values) {
        int n = keys.length;
        for (int i = 0; i < n; i++) {
            sendMessage(source, true, Text.literal(keys[i]).formatted(LIGHT_PURPLE), Text.literal(" set to ").formatted(GREEN),
                    Text.literal(values[i]).formatted(GOLD), Text.literal(" successfully.").formatted(GREEN));
        }
        Properties properties = new Properties();
        
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            properties.load(in);
        } catch (IOException e) {
            sendMessage(source, false, Text.literal("Failed to read config file: ").formatted(GRAY), Text.literal(e.getMessage()).formatted(RED));
            return 0;
        }
        
        for (int i = 0; i < n; i++) {
            properties.setProperty(keys[i], values[i]);
        }
        
        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(out, CONFIG_COMMENTS);
        } catch (IOException e) {
            sendMessage(source, false, Text.literal("Failed to write config file: ").formatted(GRAY), Text.literal(e.getMessage()).formatted(RED));
            return 0;
        }
        
        return 1;
    }
}

package pers.hpcx.easyteleport;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.util.math.Vec3d;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.*;
import static net.minecraft.util.Formatting.*;
import static pers.hpcx.easyteleport.EasyTeleportConfigEnum.*;

public class EasyTeleportMod implements ModInitializer, CommandRegistrationCallback {
    
    public static final String MOD_ID = "easyteleport";
    public static final Path MOD_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("easy-teleport.properties");
    
    public int stackDepth = 8;
    public int anchorLimit = 16;
    public int requestTimeout = 5000;
    
    public final Map<UUID, List<Request>> requests = new HashMap<>();
    
    @Override
    public void onInitialize() {
        loadConfig();
        CommandRegistrationCallback.EVENT.register(this);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }
    
    public void loadConfig() {
        try {
            if (Files.exists(MOD_CONFIG_PATH)) {
                try (InputStream in = new BufferedInputStream(Files.newInputStream(MOD_CONFIG_PATH))) {
                    Properties properties = new Properties();
                    properties.load(in);
                    String stackDepth = properties.getProperty(STACK_DEPTH.getKey());
                    String anchorLimit = properties.getProperty(ANCHOR_LIMIT.getKey());
                    String requestTimeout = properties.getProperty(REQUEST_TIMEOUT.getKey());
                    if (stackDepth != null) {
                        this.stackDepth = Integer.parseInt(stackDepth);
                    }
                    if (anchorLimit != null) {
                        this.anchorLimit = Integer.parseInt(anchorLimit);
                    }
                    if (requestTimeout != null) {
                        this.requestTimeout = Integer.parseInt(requestTimeout);
                    }
                }
            } else {
                Files.createFile(MOD_CONFIG_PATH);
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(MOD_CONFIG_PATH))) {
                    Properties properties = new Properties();
                    properties.setProperty(STACK_DEPTH.getKey(), Integer.toString(stackDepth));
                    properties.setProperty(ANCHOR_LIMIT.getKey(), Integer.toString(anchorLimit));
                    properties.setProperty(REQUEST_TIMEOUT.getKey(), Integer.toString(requestTimeout));
                    properties.store(out, "easy-teleport mod config");
                }
            }
        } catch (IOException ignored) {
        }
    }
    
    @Override
    public void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, RegistrationEnvironment environment) {
        Predicate<ServerCommandSource> isPlayer = ServerCommandSource::isExecutedByPlayer;
        Predicate<ServerCommandSource> isOperator = source -> source.hasPermissionLevel(4);
        
        dispatcher.register(literal("tpb").requires(isPlayer).executes(this::teleportBack));
        
        dispatcher.register(literal("tpp").requires(isPlayer).executes(this::teleportReturn));
        
        dispatcher.register(literal("tpp").requires(isPlayer)
                .then(argument("anchor-name", StringArgumentType.string()).suggests(AnchorSuggestionProvider.suggestions()).executes(this::teleport)));
        
        dispatcher.register(
                literal("tpa").requires(isPlayer).then(argument("target-player", GameProfileArgumentType.gameProfile()).executes(this::teleportRequest)));
        
        dispatcher.register(literal("tpaccept").requires(isPlayer).executes(this::teleportAcceptAll));
        
        dispatcher.register(literal("home").requires(isPlayer).executes(this::home));
        
        dispatcher.register(literal("sethome").requires(isPlayer).executes(this::setHome));
        
        dispatcher.register(literal("anchor").requires(isPlayer).then(literal("list").executes(this::listAnchors)));
        
        dispatcher.register(literal("anchor").requires(isPlayer).then(literal("clear").executes(this::clearAnchors)));
        
        dispatcher.register(
                literal("anchor").requires(isPlayer).then(literal("set").then(argument("anchor-name", StringArgumentType.string()).executes(this::setAnchor))));
        
        dispatcher.register(literal("anchor").requires(isPlayer).then(literal("remove").then(
                argument("anchor-name", StringArgumentType.string()).suggests(AnchorSuggestionProvider.suggestions()).executes(this::removeAnchor))));
        
        dispatcher.register(literal("config").requires(isOperator)
                .then(literal("depth").then(argument(STACK_DEPTH.getKey(), STACK_DEPTH.getType()).executes(this::setStackDepth))));
        
        dispatcher.register(literal("config").requires(isOperator)
                .then(literal("limit").then(argument(ANCHOR_LIMIT.getKey(), ANCHOR_LIMIT.getType()).executes(this::setAnchorLimit))));
        
        dispatcher.register(literal("config").requires(isOperator)
                .then(literal("timeout").then(argument(REQUEST_TIMEOUT.getKey(), REQUEST_TIMEOUT.getType()).executes(this::setRequestTimeout))));
    }
    
    public static String toString(Vec3d position) {
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
    
    public void onServerTick(MinecraftServer server) {
        if (requests.isEmpty()) {
            return;
        }
        for (UUID targetID : requests.keySet()) {
            List<Request> requestList = requests.get(targetID);
            Iterator<Request> iterator = requestList.iterator();
            while (iterator.hasNext()) {
                Request request = iterator.next();
                if (--request.keepAliveTicks <= 0) {
                    iterator.remove();
                    notifyRequestTimedOut(server, request.sourceID, request.targetID);
                }
            }
            if (requestList.isEmpty()) {
                requests.keySet().remove(targetID);
            }
        }
    }
    
    public void notifyRequestTimedOut(MinecraftServer server, UUID sourceID, UUID targetID) {
        ServerPlayerEntity source = server.getPlayerManager().getPlayer(sourceID);
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetID);
        if (source != null && target != null) {
            sendMessage(source.getCommandSource(), true, Text.literal("Teleport request to ").formatted(GRAY),
                    Text.literal(target.getName().getString()).formatted(GOLD), Text.literal(" has timed out.").formatted(GRAY));
            sendMessage(target.getCommandSource(), true, Text.literal("Teleport request from ").formatted(GRAY),
                    Text.literal(source.getName().getString()).formatted(GOLD), Text.literal(" has timed out.").formatted(GRAY));
        }
    }
    
    public int home(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, Anchor> anchors = ((AnchorStorage) player).easyTeleport$getAnchors();
        AnchorStack stack = ((AnchorStorage) player).easyTeleport$getStack();
        Anchor anchor = anchors.get("home");
        if (anchor == null) {
            sendMessage(player.getCommandSource(), false, Text.literal("Anchor ").formatted(GRAY), Text.literal("home").formatted(RED),
                    Text.literal(" not set.").formatted(GRAY));
            return 0;
        } else {
            Vec3d position = anchor.position();
            stack.tpp(new Anchor(player.getPos(), player.getWorld().getRegistryKey()), stackDepth);
            player.teleport(player.getServer().getWorld(anchor.world()), position.x, position.y, position.z, player.getYaw(), player.getPitch());
            sendMessage(player.getCommandSource(), true, Text.literal("Teleport to ").formatted(GREEN), Text.literal("home").formatted(YELLOW),
                    Text.literal(".").formatted(GREEN));
            return 1;
        }
    }
    
    public int setHome(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, Anchor> anchors = ((AnchorStorage) player).easyTeleport$getAnchors();
        if (anchors.size() < anchorLimit) {
            Vec3d position = player.getPos();
            Anchor anchor = new Anchor(position, player.getWorld().getRegistryKey());
            anchors.put("home", anchor);
            sendMessage(player.getCommandSource(), true, Text.literal("Anchor ").formatted(GREEN), Text.literal("home").formatted(YELLOW),
                    Text.literal(" set at ").formatted(GREEN), Text.literal(toString(position)).formatted(GRAY),
                    Text.literal(" successfully.").formatted(GREEN));
            return 1;
        } else {
            sendMessage(player.getCommandSource(), false, Text.literal("Anchor count limit exceeded.").formatted(RED));
            return 0;
        }
    }
    
    public int teleport(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, Anchor> anchors = ((AnchorStorage) player).easyTeleport$getAnchors();
        AnchorStack stack = ((AnchorStorage) player).easyTeleport$getStack();
        String anchorName = StringArgumentType.getString(context, "anchor-name");
        Anchor anchor = anchors.get(anchorName);
        if (anchor == null) {
            sendMessage(player.getCommandSource(), false, Text.literal("Anchor ").formatted(GRAY), Text.literal(anchorName).formatted(RED),
                    Text.literal(" not set.").formatted(GRAY));
            return 0;
        } else {
            Vec3d position = anchor.position();
            stack.tpp(new Anchor(player.getPos(), player.getWorld().getRegistryKey()), stackDepth);
            player.teleport(player.getServer().getWorld(anchor.world()), position.x, position.y, position.z, player.getYaw(), player.getPitch());
            sendMessage(player.getCommandSource(), true, Text.literal("Teleport to ").formatted(GREEN), Text.literal(anchorName).formatted(YELLOW),
                    Text.literal(".").formatted(GREEN));
            return 1;
        }
    }
    
    public int teleportBack(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AnchorStack stack = ((AnchorStorage) player).easyTeleport$getStack();
        Anchor anchor = stack.tpb();
        if (anchor == null) {
            sendMessage(player.getCommandSource(), false, Text.literal("Cannot tpb anymore.").formatted(GRAY));
            return 0;
        } else {
            Vec3d position = anchor.position();
            player.teleport(player.getServer().getWorld(anchor.world()), position.x, position.y, position.z, player.getYaw(), player.getPitch());
            sendMessage(player.getCommandSource(), true, Text.literal("Teleport to ").formatted(GREEN), Text.literal(toString(position)).formatted(GRAY),
                    Text.literal(".").formatted(GREEN));
            return 1;
        }
    }
    
    public int teleportReturn(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AnchorStack stack = ((AnchorStorage) player).easyTeleport$getStack();
        Anchor anchor = stack.tpp();
        if (anchor == null) {
            sendMessage(player.getCommandSource(), false, Text.literal("Cannot tpp anymore.").formatted(GRAY));
            return 0;
        } else {
            Vec3d position = anchor.position();
            player.teleport(player.getServer().getWorld(anchor.world()), position.x, position.y, position.z, player.getYaw(), player.getPitch());
            sendMessage(player.getCommandSource(), true, Text.literal("Teleport to ").formatted(GREEN), Text.literal(toString(position)).formatted(GRAY),
                    Text.literal(".").formatted(GREEN));
            return 1;
        }
    }
    
    public int teleportRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Iterator<GameProfile> iterator = GameProfileArgumentType.getProfileArgument(context, "target-player").iterator();
        if (!iterator.hasNext()) {
            sendMessage(player.getCommandSource(), false, Text.literal("Target player not found.").formatted(RED));
            return 0;
        }
        UUID sourceID = player.getUuid();
        UUID targetID = iterator.next().getId();
        if (sourceID.equals(targetID)) {
            sendMessage(player.getCommandSource(), false, Text.literal("Cannot teleport to yourself.").formatted(RED));
            return 0;
        }
        if (iterator.hasNext()) {
            sendMessage(player.getCommandSource(), false, Text.literal("Please specify only one player.").formatted(RED));
            return 0;
        }
        ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetID);
        if (target == null) {
            sendMessage(player.getCommandSource(), false, Text.literal("Target player not found.").formatted(RED));
            return 0;
        }
        List<Request> requestList = requests.get(targetID);
        if (requestList == null) {
            requests.put(targetID, requestList = new ArrayList<>());
        } else {
            for (Request request : requestList) {
                if (request.sourceID.equals(sourceID)) {
                    sendMessage(player.getCommandSource(), false, Text.literal("You have requested to teleport to ").formatted(GRAY),
                            Text.literal(target.getName().getString()).formatted(GOLD), Text.literal(".").formatted(GRAY));
                    return 0;
                }
            }
        }
        requestList.add(new Request(sourceID, targetID, requestTimeout / 50));
        sendMessage(player.getCommandSource(), true, Text.literal("Requested to teleport to ").formatted(GREEN),
                Text.literal(target.getName().getString()).formatted(GOLD), Text.literal(" successfully.").formatted(GREEN));
        sendMessage(target.getCommandSource(), true, Text.literal(player.getName().getString()).formatted(GOLD),
                Text.literal(" has requested to teleport to you. Type ").formatted(GREEN), Text.literal("/tpaccept").formatted(YELLOW),
                Text.literal(" to accept.").formatted(GREEN));
        return 1;
    }
    
    public int teleportAcceptAll(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity targetPlayer = source.getPlayerOrThrow();
        UUID targetID = targetPlayer.getUuid();
        List<Request> requestList = requests.get(targetID);
        if (requestList == null || requestList.isEmpty()) {
            sendMessage(targetPlayer.getCommandSource(), false, Text.literal("You have no request to accept.").formatted(GRAY));
            return 0;
        }
        for (Request request : requestList) {
            ServerPlayerEntity sourcePlayer = targetPlayer.getServer().getPlayerManager().getPlayer(request.sourceID);
            if (sourcePlayer == null) {
                continue;
            }
            Vec3d position = targetPlayer.getPos();
            AnchorStack stack = ((AnchorStorage) sourcePlayer).easyTeleport$getStack();
            stack.tpp(new Anchor(sourcePlayer.getPos(), sourcePlayer.getWorld().getRegistryKey()), stackDepth);
            sourcePlayer.teleport(targetPlayer.getServerWorld(), position.x, position.y, position.z, sourcePlayer.getYaw(), sourcePlayer.getPitch());
            sendMessage(sourcePlayer.getCommandSource(), true, Text.literal("Teleport to ").formatted(GREEN),
                    Text.literal(targetPlayer.getName().getString()).formatted(GOLD), Text.literal(" successfully.").formatted(GREEN));
            sendMessage(targetPlayer.getCommandSource(), true, Text.literal(sourcePlayer.getName().getString()).formatted(GOLD),
                    Text.literal(" is teleported to you.").formatted(GREEN));
        }
        requestList.clear();
        requests.keySet().remove(targetID);
        return 1;
    }
    
    public int listAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, Anchor> anchors = ((AnchorStorage) player).easyTeleport$getAnchors();
        if (anchors.isEmpty()) {
            sendMessage(player.getCommandSource(), true, Text.literal("No anchors set.").formatted(GRAY));
            return 0;
        } else {
            sendMessage(player.getCommandSource(), true, Text.literal("Anchors set by ").formatted(GREEN), ((MutableText) player.getName()).formatted(GOLD),
                    Text.literal(":").formatted(GREEN));
            ArrayList<String> anchorNames = new ArrayList<>(anchors.keySet());
            anchorNames.sort(String::compareToIgnoreCase);
            for (String anchorName : anchorNames) {
                sendMessage(player.getCommandSource(), true, Text.literal(" -").formatted(GRAY), Text.literal(anchorName).formatted(YELLOW),
                        Text.literal(" at ").formatted(GRAY), Text.literal(toString(anchors.get(anchorName).position())).formatted(GRAY));
            }
            return 1;
        }
    }
    
    public int clearAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, Anchor> anchors = ((AnchorStorage) player).easyTeleport$getAnchors();
        if (anchors.isEmpty()) {
            sendMessage(player.getCommandSource(), true, Text.literal("No anchors set.").formatted(GRAY));
            return 0;
        } else {
            anchors.clear();
            sendMessage(player.getCommandSource(), true, Text.literal("Anchors cleared.").formatted(GREEN));
            return 1;
        }
    }
    
    public int setAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, Anchor> anchors = ((AnchorStorage) player).easyTeleport$getAnchors();
        if (anchors.size() < anchorLimit) {
            Vec3d position = player.getPos();
            String anchorName = StringArgumentType.getString(context, "anchor-name");
            Anchor anchor = new Anchor(position, player.getWorld().getRegistryKey());
            anchors.put(anchorName, anchor);
            sendMessage(player.getCommandSource(), true, Text.literal("Anchor ").formatted(GREEN), Text.literal(anchorName).formatted(YELLOW),
                    Text.literal(" set at ").formatted(GREEN), Text.literal(toString(position)).formatted(GRAY),
                    Text.literal(" successfully.").formatted(GREEN));
            return 1;
        } else {
            sendMessage(player.getCommandSource(), false, Text.literal("Anchor count limit exceeded.").formatted(RED));
            return 0;
        }
    }
    
    public int removeAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, Anchor> anchors = ((AnchorStorage) player).easyTeleport$getAnchors();
        String anchorName = StringArgumentType.getString(context, "anchor-name");
        if (anchors.keySet().remove(anchorName)) {
            sendMessage(player.getCommandSource(), true, Text.literal("Anchor ").formatted(GREEN), Text.literal(anchorName).formatted(YELLOW),
                    Text.literal(" removed.").formatted(GREEN));
            return 1;
        } else {
            sendMessage(player.getCommandSource(), false, Text.literal("Anchor ").formatted(GRAY), Text.literal(anchorName).formatted(RED),
                    Text.literal(" not set.").formatted(GRAY));
            return 0;
        }
    }
    
    public int setStackDepth(CommandContext<ServerCommandSource> context) {
        stackDepth = IntegerArgumentType.getInteger(context, STACK_DEPTH.getKey());
        return storeProperty(context.getSource(), STACK_DEPTH.getKey(), Integer.toString(stackDepth));
    }
    
    public int setAnchorLimit(CommandContext<ServerCommandSource> context) {
        anchorLimit = IntegerArgumentType.getInteger(context, ANCHOR_LIMIT.getKey());
        return storeProperty(context.getSource(), ANCHOR_LIMIT.getKey(), Integer.toString(anchorLimit));
    }
    
    public int setRequestTimeout(CommandContext<ServerCommandSource> context) {
        requestTimeout = IntegerArgumentType.getInteger(context, REQUEST_TIMEOUT.getKey());
        return storeProperty(context.getSource(), REQUEST_TIMEOUT.getKey(), Integer.toString(requestTimeout));
    }
    
    public int storeProperty(ServerCommandSource source, String key, String value) {
        sendMessage(source, true, Text.literal(key).formatted(LIGHT_PURPLE), Text.literal(" set to ").formatted(GREEN), Text.literal(value).formatted(GOLD),
                Text.literal(" successfully.").formatted(GREEN));
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(MOD_CONFIG_PATH))) {
            Properties properties = new Properties();
            properties.setProperty(key, value);
            properties.store(out, "easy-teleport mod config");
            return 1;
        } catch (IOException e) {
            sendMessage(source, false, Text.literal("Failed to write config file: ").formatted(GRAY), Text.literal(e.getMessage()).formatted(RED));
            return 0;
        }
    }
}

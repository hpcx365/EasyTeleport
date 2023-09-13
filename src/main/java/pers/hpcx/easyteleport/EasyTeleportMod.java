package pers.hpcx.easyteleport;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.*;
import static net.minecraft.util.Formatting.*;
import static pers.hpcx.easyteleport.EasyTeleportConfigEnum.*;
import static pers.hpcx.easyteleport.EasyTeleportUtils.*;

public class EasyTeleportMod implements ModInitializer, ServerLifecycleEvents.ServerStarting, ServerTickEvents.EndTick, CommandRegistrationCallback {
    
    public static final int DEFAULT_STACK_DEPTH = 8;
    public static final int DEFAULT_ANCHOR_LIMIT = 16;
    public static final int DEFAULT_REQUEST_TIMEOUT = 10000;
    
    public int stackDepth = DEFAULT_STACK_DEPTH;
    public int anchorLimit = DEFAULT_ANCHOR_LIMIT;
    public int requestTimeout = DEFAULT_REQUEST_TIMEOUT;
    
    public final Map<UUID, List<Request>> requests = new HashMap<>();
    
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this);
        ServerTickEvents.END_SERVER_TICK.register(this);
        CommandRegistrationCallback.EVENT.register(this);
    }
    
    @Override
    public void onServerStarting(MinecraftServer server) {
        try {
            if (Files.exists(MOD_CONFIG_PATH)) {
                try (InputStream in = Files.newInputStream(MOD_CONFIG_PATH)) {
                    Properties properties = new Properties();
                    properties.load(in);
                    getProperties(properties);
                }
            } else {
                Files.createFile(MOD_CONFIG_PATH);
                try (OutputStream out = Files.newOutputStream(MOD_CONFIG_PATH)) {
                    Properties properties = new Properties();
                    setProperties(properties);
                    properties.store(out, "easy-teleport mod config");
                }
            }
        } catch (IOException ignored) {
        }
    }
    
    public void getProperties(Properties properties) {
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
    
    public void setProperties(Properties properties) {
        if (stackDepth != DEFAULT_STACK_DEPTH) {
            properties.setProperty(STACK_DEPTH.getKey(), Integer.toString(stackDepth));
        }
        if (anchorLimit != DEFAULT_ANCHOR_LIMIT) {
            properties.setProperty(ANCHOR_LIMIT.getKey(), Integer.toString(anchorLimit));
        }
        if (requestTimeout != DEFAULT_REQUEST_TIMEOUT) {
            properties.setProperty(REQUEST_TIMEOUT.getKey(), Integer.toString(requestTimeout));
        }
    }
    
    @Override
    public void onEndTick(MinecraftServer server) {
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
        
        dispatcher.register(
                literal("tpaccept").requires(isPlayer).then(argument("source-player", GameProfileArgumentType.gameProfile()).executes(this::teleportAccept)));
        
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
            sendMessage(player.getCommandSource(), true, Text.literal("Teleport to ").formatted(GREEN), Text.literal(format(position)).formatted(GRAY),
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
            sendMessage(player.getCommandSource(), true, Text.literal("Teleport to ").formatted(GREEN), Text.literal(format(position)).formatted(GRAY),
                    Text.literal(".").formatted(GREEN));
            return 1;
        }
    }
    
    public int teleportRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "target-player");
        UUID sourceID = player.getUuid();
        UUID targetID = selectPlayer(player.getCommandSource(), profiles);
        if (sourceID.equals(targetID)) {
            sendMessage(player.getCommandSource(), false, Text.literal("Cannot teleport to yourself.").formatted(RED));
            return 0;
        }
        ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetID);
        if (target == null) {
            playerNotFound(player.getCommandSource());
            return 0;
        }
        List<Request> requestList = requests.get(targetID);
        if (requestList == null) {
            requests.put(targetID, requestList = new ArrayList<>());
        } else {
            for (Request request : requestList) {
                if (!request.sourceID.equals(sourceID)) {
                    continue;
                }
                sendMessage(player.getCommandSource(), false, Text.literal("You have requested to teleport to ").formatted(GRAY),
                        Text.literal(target.getName().getString()).formatted(GOLD), Text.literal(".").formatted(GRAY));
                return 0;
            }
        }
        requestList.add(new Request(sourceID, targetID, requestTimeout / 50));
        sendMessage(player.getCommandSource(), true, Text.literal("Requested to teleport to ").formatted(GREEN),
                Text.literal(target.getName().getString()).formatted(GOLD), Text.literal(".").formatted(GREEN));
        sendMessage(target.getCommandSource(), true, Text.literal(player.getName().getString()).formatted(GOLD),
                Text.literal(" has requested to teleport to you. Type ").formatted(GREEN), Text.literal("/tpaccept").formatted(YELLOW),
                Text.literal(" to accept.").formatted(GREEN));
        return 1;
    }
    
    public int teleportAccept(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity targetPlayer = source.getPlayerOrThrow();
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "source-player");
        UUID sourceID = selectPlayer(targetPlayer.getCommandSource(), profiles);
        UUID targetID = targetPlayer.getUuid();
        List<Request> requestList = requests.get(targetID);
        if (requestList == null || requestList.isEmpty()) {
            noRequests(targetPlayer.getCommandSource());
            return 0;
        }
        Iterator<Request> iterator = requestList.iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next();
            if (!request.sourceID.equals(sourceID)) {
                continue;
            }
            ServerPlayerEntity sourcePlayer = targetPlayer.getServer().getPlayerManager().getPlayer(sourceID);
            if (sourcePlayer == null) {
                playerNotFound(targetPlayer.getCommandSource());
                return 0;
            }
            Vec3d position = targetPlayer.getPos();
            AnchorStack stack = ((AnchorStorage) sourcePlayer).easyTeleport$getStack();
            stack.tpp(new Anchor(sourcePlayer.getPos(), sourcePlayer.getWorld().getRegistryKey()), stackDepth);
            sourcePlayer.teleport(targetPlayer.getServerWorld(), position.x, position.y, position.z, sourcePlayer.getYaw(), sourcePlayer.getPitch());
            sendMessage(sourcePlayer.getCommandSource(), true, Text.literal("Teleport to ").formatted(GREEN),
                    Text.literal(targetPlayer.getName().getString()).formatted(GOLD), Text.literal(" successfully.").formatted(GREEN));
            sendMessage(targetPlayer.getCommandSource(), true, Text.literal(sourcePlayer.getName().getString()).formatted(GOLD),
                    Text.literal(" is teleported to you.").formatted(GREEN));
            iterator.remove();
            if (requestList.isEmpty()) {
                requests.keySet().remove(targetID);
            }
            return 1;
        }
        playerNotFound(targetPlayer.getCommandSource());
        return 0;
    }
    
    public int teleportAcceptAll(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity targetPlayer = source.getPlayerOrThrow();
        UUID targetID = targetPlayer.getUuid();
        List<Request> requestList = requests.get(targetID);
        if (requestList == null || requestList.isEmpty()) {
            noRequests(targetPlayer.getCommandSource());
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
                    Text.literal(" set at ").formatted(GREEN), Text.literal(format(position)).formatted(GRAY), Text.literal(" successfully.").formatted(GREEN));
            return 1;
        } else {
            sendMessage(player.getCommandSource(), false, Text.literal("Anchor count limit exceeded.").formatted(RED));
            return 0;
        }
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
                        Text.literal(" at ").formatted(GRAY), Text.literal(format(anchors.get(anchorName).position())).formatted(GRAY));
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
                    Text.literal(" set at ").formatted(GREEN), Text.literal(format(position)).formatted(GRAY), Text.literal(" successfully.").formatted(GREEN));
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
}

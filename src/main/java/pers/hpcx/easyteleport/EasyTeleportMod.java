package pers.hpcx.easyteleport;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.*;
import static net.minecraft.util.Formatting.*;
import static pers.hpcx.easyteleport.EasyTeleportConfig.*;
import static pers.hpcx.easyteleport.EasyTeleportUtils.*;

public class EasyTeleportMod implements ModInitializer, ServerLifecycleEvents.ServerStarting, ServerTickEvents.EndTick, ServerLivingEntityEvents.AfterDeath,
        CommandRegistrationCallback {
    
    public static final int DEFAULT_STACK_DEPTH = 8;
    public static final int DEFAULT_ANCHOR_LIMIT = 16;
    public static final int DEFAULT_REQUEST_TIMEOUT = 10000;
    
    public int stackDepth = DEFAULT_STACK_DEPTH;
    public int anchorLimit = DEFAULT_ANCHOR_LIMIT;
    public int requestTimeout = DEFAULT_REQUEST_TIMEOUT;
    
    public final Map<UUID, List<TeleportRequest>> requests = new HashMap<>();
    
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this);
        ServerTickEvents.END_SERVER_TICK.register(this);
        ServerLivingEntityEvents.AFTER_DEATH.register(this);
        CommandRegistrationCallback.EVENT.register(this);
    }
    
    @Override
    public void onServerStarting(MinecraftServer server) {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                    Properties properties = new Properties();
                    properties.load(in);
                    getProperties(properties);
                }
            } else {
                Files.createFile(CONFIG_PATH);
                try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
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
            List<TeleportRequest> requestList = requests.get(targetID);
            Iterator<TeleportRequest> iterator = requestList.iterator();
            while (iterator.hasNext()) {
                TeleportRequest request = iterator.next();
                if (--request.keepAliveTicks <= 0) {
                    iterator.remove();
                    notifyRequestTimedOut(server, request.sourcePlayerID, request.targetPlayerID);
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
        
        dispatcher.register(literal("config").requires(isOperator).then(literal("default").executes(this::restoreDefault)));
    }
    
    @Override
    public void afterDeath(LivingEntity entity, DamageSource damageSource) {
        if (entity instanceof ServerPlayerEntity player) {
            TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
            stack.afterDeath(player, stackDepth);
        }
    }
    
    public int teleport(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
        return stack.tpp(player, StringArgumentType.getString(context, "anchor-name"), stackDepth);
    }
    
    public int teleportBack(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
        return stack.tpb(player, stackDepth);
    }
    
    public int teleportReturn(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
        return stack.tpp(player, stackDepth);
    }
    
    public int teleportRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity sourcePlayer = context.getSource().getPlayerOrThrow();
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "target-player");
        UUID sourceID = sourcePlayer.getUuid();
        UUID targetID = selectPlayerID(sourcePlayer, profiles);
        ServerPlayerEntity targetPlayer = sourcePlayer.getServer().getPlayerManager().getPlayer(targetID);
        if (targetPlayer == null) {
            playerNotFound(sourcePlayer);
            return 0;
        }
        List<TeleportRequest> requestList = requests.get(targetID);
        if (requestList == null) {
            requests.put(targetID, requestList = new ArrayList<>());
        } else {
            for (TeleportRequest request : requestList) {
                if (request.sourcePlayerID.equals(sourceID)) {
                    sendMessage(sourcePlayer.getCommandSource(), false, Text.literal("You have requested to teleport to ").formatted(GRAY),
                            Text.literal(targetPlayer.getName().getString()).formatted(GOLD), Text.literal(".").formatted(GRAY));
                    return 0;
                }
            }
        }
        requestList.add(new TeleportRequest(sourceID, targetID, requestTimeout / 50));
        sendMessage(sourcePlayer.getCommandSource(), true, Text.literal("Requested to teleport to ").formatted(GREEN),
                Text.literal(targetPlayer.getName().getString()).formatted(GOLD), Text.literal(".").formatted(GREEN));
        sendMessage(targetPlayer.getCommandSource(), true, Text.literal(sourcePlayer.getName().getString()).formatted(GOLD),
                Text.literal(" has requested to teleport to you. Type ").formatted(GREEN), Text.literal("/tpaccept").formatted(YELLOW),
                Text.literal(" to accept.").formatted(GREEN));
        return 1;
    }
    
    public int teleportAccept(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity targetPlayer = context.getSource().getPlayerOrThrow();
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "source-player");
        UUID sourceID = selectPlayerID(targetPlayer, profiles);
        UUID targetID = targetPlayer.getUuid();
        List<TeleportRequest> requestList = requests.get(targetID);
        if (requestList == null || requestList.isEmpty()) {
            noRequests(targetPlayer);
            return 0;
        }
        Iterator<TeleportRequest> iterator = requestList.iterator();
        while (iterator.hasNext()) {
            TeleportRequest request = iterator.next();
            if (!request.sourcePlayerID.equals(sourceID)) {
                continue;
            }
            ServerPlayerEntity sourcePlayer = targetPlayer.getServer().getPlayerManager().getPlayer(sourceID);
            if (sourcePlayer == null) {
                playerNotFound(targetPlayer);
                return 0;
            }
            TeleportStack stack = ((TeleportStorage) sourcePlayer).easyTeleport$getStack();
            stack.tpp(sourcePlayer, targetPlayer, stackDepth);
            iterator.remove();
            if (requestList.isEmpty()) {
                requests.keySet().remove(targetID);
            }
            return 1;
        }
        playerNotFound(targetPlayer);
        return 0;
    }
    
    public int teleportAcceptAll(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity targetPlayer = context.getSource().getPlayerOrThrow();
        UUID targetID = targetPlayer.getUuid();
        List<TeleportRequest> requestList = requests.get(targetID);
        if (requestList == null || requestList.isEmpty()) {
            noRequests(targetPlayer);
            return 0;
        }
        for (TeleportRequest request : requestList) {
            ServerPlayerEntity sourcePlayer = targetPlayer.getServer().getPlayerManager().getPlayer(request.sourcePlayerID);
            if (sourcePlayer == null) {
                playerNotFound(targetPlayer);
                continue;
            }
            TeleportStack stack = ((TeleportStorage) sourcePlayer).easyTeleport$getStack();
            stack.tpp(sourcePlayer, targetPlayer, stackDepth);
        }
        requestList.clear();
        requests.keySet().remove(targetID);
        return 1;
    }
    
    public int home(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
        return stack.tpp(player, "home", stackDepth);
    }
    
    public int setHome(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        if (anchors.size() >= anchorLimit) {
            sendMessage(player.getCommandSource(), false, Text.literal("Anchor count limit exceeded.").formatted(RED));
            return 0;
        }
        anchors.put("home", new TeleportAnchor("home", player.getPos(), player.getServerWorld().getRegistryKey()));
        sendMessage(player.getCommandSource(), true, Text.literal("Anchor ").formatted(GREEN), Text.literal("home").formatted(YELLOW),
                Text.literal(" set at ").formatted(GREEN), Text.literal(format(player.getPos())).formatted(GRAY),
                Text.literal(" successfully.").formatted(GREEN));
        return 1;
    }
    
    public int listAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        if (anchors.isEmpty()) {
            sendMessage(player.getCommandSource(), true, Text.literal("No anchors set.").formatted(GRAY));
            return 0;
        }
        sendMessage(player.getCommandSource(), true, Text.literal("Anchors set by ").formatted(GREEN),
                Text.literal(player.getName().getString()).formatted(GOLD), Text.literal(":").formatted(GREEN));
        ArrayList<String> anchorNames = new ArrayList<>(anchors.keySet());
        anchorNames.sort(String::compareToIgnoreCase);
        for (String anchorName : anchorNames) {
            sendMessage(player.getCommandSource(), true, Text.literal(" -").formatted(GRAY), Text.literal(anchorName).formatted(YELLOW),
                    Text.literal(" at ").formatted(GRAY), Text.literal(format(anchors.get(anchorName).position())).formatted(GRAY));
        }
        return 1;
    }
    
    public int clearAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        if (anchors.isEmpty()) {
            sendMessage(player.getCommandSource(), true, Text.literal("No anchors set.").formatted(GRAY));
            return 0;
        }
        anchors.clear();
        sendMessage(player.getCommandSource(), true, Text.literal("Anchors cleared.").formatted(GREEN));
        return 1;
    }
    
    public int setAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        if (anchors.size() >= anchorLimit) {
            sendMessage(player.getCommandSource(), false, Text.literal("Anchor count limit exceeded.").formatted(RED));
            return 0;
        }
        String name = StringArgumentType.getString(context, "anchor-name");
        anchors.put(name, new TeleportAnchor(name, player.getPos(), player.getServerWorld().getRegistryKey()));
        sendMessage(player.getCommandSource(), true, Text.literal("Anchor ").formatted(GREEN), Text.literal(name).formatted(YELLOW),
                Text.literal(" set at ").formatted(GREEN), Text.literal(format(player.getPos())).formatted(GRAY),
                Text.literal(" successfully.").formatted(GREEN));
        return 1;
    }
    
    public int removeAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
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
    
    public int restoreDefault(CommandContext<ServerCommandSource> context) {
        stackDepth = DEFAULT_STACK_DEPTH;
        anchorLimit = DEFAULT_ANCHOR_LIMIT;
        requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        String[] keys = {STACK_DEPTH.getKey(), ANCHOR_LIMIT.getKey(), REQUEST_TIMEOUT.getKey()};
        String[] values = {Integer.toString(stackDepth), Integer.toString(anchorLimit), Integer.toString(requestTimeout)};
        return storeProperties(context.getSource(), keys, values);
    }
}

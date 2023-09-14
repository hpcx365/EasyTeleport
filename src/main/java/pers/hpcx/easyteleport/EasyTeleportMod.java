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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.*;
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
    public final Map<UUID, TeleportRequest> requests2 = new HashMap<>();
    
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
        if (!requests.isEmpty()) {
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
        if (!requests2.isEmpty()) {
            Iterator<UUID> iterator = requests2.keySet().iterator();
            while (iterator.hasNext()) {
                TeleportRequest request = requests2.get(iterator.next());
                if (--request.keepAliveTicks <= 0) {
                    iterator.remove();
                    notifyRequestTimedOut(server, request.sourcePlayerID, request.targetPlayerID);
                }
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
        
        dispatcher.register(
                literal("tphere").requires(isPlayer).then(argument("source-player", GameProfileArgumentType.gameProfile()).executes(this::teleportHere)));
        
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
                    send(sourcePlayer, false, gray("You have requested to teleport to "), player(targetPlayer), gray("."));
                    return 0;
                }
            }
        }
        requestList.add(new TeleportRequest(sourceID, targetID, requestTimeout / 50));
        send(sourcePlayer, true, green("Requested to teleport to "), player(targetPlayer), green("."));
        send(targetPlayer, true, player(sourcePlayer), green(" has requested to teleport to you. Type "), yellow("/tpaccept"), green(" to accept."));
        return 1;
    }
    
    public int teleportHere(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity targetPlayer = context.getSource().getPlayerOrThrow();
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "source-player");
        UUID targetID = targetPlayer.getUuid();
        UUID sourceID = selectPlayerID(targetPlayer, profiles);
        ServerPlayerEntity sourcePlayer = targetPlayer.getServer().getPlayerManager().getPlayer(sourceID);
        if (sourcePlayer == null) {
            playerNotFound(targetPlayer);
            return 0;
        }
        TeleportRequest request = requests2.get(sourceID);
        if (request != null) {
            send(targetPlayer, false, player(sourcePlayer), gray(" has already received a request."));
            return 0;
        }
        requests2.put(sourceID, new TeleportRequest(sourceID, targetID, requestTimeout / 50));
        send(sourcePlayer, true, player(targetPlayer), green(" has requested to teleport you to there. Type "), yellow("/tpaccept"), green(" to accept."));
        send(targetPlayer, true, green("Requested to teleport "), player(sourcePlayer), green(" to you."));
        return 1;
    }
    
    public int teleportAccept(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity targetPlayer = context.getSource().getPlayerOrThrow();
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "source-player");
        UUID sourceID = selectPlayerID(targetPlayer, profiles);
        UUID targetID = targetPlayer.getUuid();
        List<TeleportRequest> requestList = requests.get(targetID);
        if (requestList == null || requestList.isEmpty()) {
            send(targetPlayer, false, gray("You have no request to accept."));
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
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        UUID playerID = player.getUuid();
        List<TeleportRequest> requestList = requests.get(playerID);
        TeleportRequest request2 = requests2.get(playerID);
        if (requestList != null) {
            for (TeleportRequest request : requestList) {
                ServerPlayerEntity sourcePlayer = player.getServer().getPlayerManager().getPlayer(request.sourcePlayerID);
                if (sourcePlayer == null) {
                    playerNotFound(player);
                } else {
                    TeleportStack stack = ((TeleportStorage) sourcePlayer).easyTeleport$getStack();
                    stack.tpp(sourcePlayer, player, stackDepth);
                }
            }
            requestList.clear();
            requests.keySet().remove(playerID);
        }
        if (request2 != null) {
            ServerPlayerEntity targetPlayer = player.getServer().getPlayerManager().getPlayer(request2.targetPlayerID);
            if (targetPlayer == null) {
                playerNotFound(player);
            } else {
                TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
                stack.tpp(player, targetPlayer, stackDepth);
            }
            requests2.keySet().remove(playerID);
        }
        return requestList != null || request2 != null ? 1 : 0;
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
            send(player, false, red("Anchor count limit exceeded."));
            return 0;
        }
        TeleportAnchor anchor = new TeleportAnchor(player);
        anchors.put("home", anchor);
        send(player, true, green("Set anchor "), anchor("home", anchor), green("."));
        return 1;
    }
    
    public int listAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        if (anchors.isEmpty()) {
            send(player, true, gray("No anchors set."));
            return 0;
        }
        ArrayList<String> anchorNames = new ArrayList<>(anchors.keySet());
        anchorNames.sort(String::compareToIgnoreCase);
        send(player, true, green("Anchors set by "), player(player), green(":"));
        for (String anchorName : anchorNames) {
            send(player, true, gray(" -"), anchor(anchorName, anchors.get(anchorName)));
        }
        return 1;
    }
    
    public int clearAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        if (anchors.isEmpty()) {
            send(player, true, gray("No anchors set."));
            return 0;
        } else {
            anchors.clear();
            send(player, true, green("Anchors cleared."));
            return 1;
        }
    }
    
    public int setAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        if (anchors.size() >= anchorLimit) {
            send(player, false, red("Anchor count limit exceeded."));
            return 0;
        }
        String anchorName = StringArgumentType.getString(context, "anchor-name");
        TeleportAnchor anchor = new TeleportAnchor(player);
        anchors.put(anchorName, anchor);
        send(player, true, green("Set anchor "), anchor(anchorName, anchor), green("."));
        return 1;
    }
    
    public int removeAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        String anchorName = StringArgumentType.getString(context, "anchor-name");
        TeleportAnchor anchor = anchors.get(anchorName);
        if (anchor == null) {
            send(player, false, gray("Anchor "), red(anchorName), gray(" not found."));
            return 0;
        } else {
            anchors.keySet().remove(anchorName);
            send(player, true, green("Remove anchor "), anchor(anchorName, anchor), green("."));
            return 1;
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
        return restoreProperties(context.getSource(), keys, values);
    }
}

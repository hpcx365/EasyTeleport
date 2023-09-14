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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

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
    
    public final Map<String, TeleportAnchor> publicAnchors = new HashMap<>();
    public final Map<UUID, List<TeleportRequest>> tpRequests = new HashMap<>();
    public final Map<UUID, TeleportRequest> tpHereRequests = new HashMap<>();
    
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
        try {
            NbtCompound anchorData = NbtIo.read(ANCHOR_PATH.toFile());
            if (anchorData != null) {
                for (String anchorName : anchorData.getKeys()) {
                    publicAnchors.put(anchorName, TeleportAnchor.fromCompound(anchorData.getCompound(anchorName)));
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
        if (!tpRequests.isEmpty()) {
            for (UUID targetID : tpRequests.keySet()) {
                List<TeleportRequest> requestList = tpRequests.get(targetID);
                for (Iterator<TeleportRequest> iterator = requestList.iterator(); iterator.hasNext(); ) {
                    TeleportRequest request = iterator.next();
                    if (--request.keepAliveTicks <= 0) {
                        iterator.remove();
                        notifyRequestTimedOut(server, request.sourcePlayerID, request.targetPlayerID);
                    }
                }
                if (requestList.isEmpty()) {
                    tpRequests.keySet().remove(targetID);
                }
            }
        }
        if (!tpHereRequests.isEmpty()) {
            for (Iterator<UUID> iterator = tpHereRequests.keySet().iterator(); iterator.hasNext(); ) {
                TeleportRequest request = tpHereRequests.get(iterator.next());
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
                .then(argument("anchor-name", StringArgumentType.string()).suggests(AnchorSuggestionProvider.suggestions(this)).executes(this::teleport)));
        
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
                argument("anchor-name", StringArgumentType.string()).suggests(AnchorSuggestionProvider.suggestions(this)).executes(this::removeAnchor))));
        
        dispatcher.register(literal("public").requires(isOperator).then(literal("list").executes(this::listPublicAnchors)));
        
        dispatcher.register(literal("public").requires(isOperator).then(literal("clear").executes(this::clearPublicAnchors)));
        
        dispatcher.register(literal("public").requires(isOperator)
                .then(literal("set").then(argument("anchor-name", StringArgumentType.string()).executes(this::setPublicAnchor))));
        
        dispatcher.register(literal("public").requires(isOperator).then(literal("remove").then(
                argument("anchor-name", StringArgumentType.string()).suggests(PublicAnchorSuggestionProvider.suggestions(this))
                        .executes(this::removePublicAnchor))));
        
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
        return stack.tpp(player, this, StringArgumentType.getString(context, "anchor-name"), stackDepth);
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
        List<TeleportRequest> requestList = tpRequests.get(targetID);
        if (requestList == null) {
            tpRequests.put(targetID, requestList = new ArrayList<>());
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
        targetPlayer.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
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
        TeleportRequest request = tpHereRequests.get(sourceID);
        if (request != null) {
            send(targetPlayer, false, player(sourcePlayer), gray(" has already received a request."));
            return 0;
        }
        tpHereRequests.put(sourceID, new TeleportRequest(sourceID, targetID, requestTimeout / 50));
        send(sourcePlayer, true, player(targetPlayer), green(" has requested to teleport you to there. Type "), yellow("/tpaccept"), green(" to accept."));
        send(targetPlayer, true, green("Requested to teleport "), player(sourcePlayer), green(" to you."));
        sourcePlayer.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
        return 1;
    }
    
    public int teleportAccept(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity targetPlayer = context.getSource().getPlayerOrThrow();
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "source-player");
        UUID sourceID = selectPlayerID(targetPlayer, profiles);
        UUID targetID = targetPlayer.getUuid();
        List<TeleportRequest> requestList = tpRequests.get(targetID);
        if (requestList == null || requestList.isEmpty()) {
            send(targetPlayer, false, gray("You have no request to accept."));
            return 0;
        }
        for (Iterator<TeleportRequest> iterator = requestList.iterator(); iterator.hasNext(); ) {
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
                tpRequests.keySet().remove(targetID);
            }
            return 1;
        }
        playerNotFound(targetPlayer);
        return 0;
    }
    
    public int teleportAcceptAll(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        UUID playerID = player.getUuid();
        List<TeleportRequest> tpRequestList = tpRequests.get(playerID);
        TeleportRequest tpHereRequest = tpHereRequests.get(playerID);
        if (tpRequestList != null) {
            for (TeleportRequest request : tpRequestList) {
                ServerPlayerEntity sourcePlayer = player.getServer().getPlayerManager().getPlayer(request.sourcePlayerID);
                if (sourcePlayer == null) {
                    playerNotFound(player);
                } else {
                    TeleportStack stack = ((TeleportStorage) sourcePlayer).easyTeleport$getStack();
                    stack.tpp(sourcePlayer, player, stackDepth);
                }
            }
            tpRequestList.clear();
            tpRequests.keySet().remove(playerID);
        }
        if (tpHereRequest != null) {
            ServerPlayerEntity targetPlayer = player.getServer().getPlayerManager().getPlayer(tpHereRequest.targetPlayerID);
            if (targetPlayer == null) {
                playerNotFound(player);
            } else {
                TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
                stack.tpp(player, targetPlayer, stackDepth);
            }
            tpHereRequests.keySet().remove(playerID);
        }
        return tpRequestList != null || tpHereRequest != null ? 1 : 0;
    }
    
    public int home(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
        return stack.tpp(player, this, "home", stackDepth);
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
    
    public int listPublicAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (publicAnchors.isEmpty()) {
            send(player, true, gray("No public anchors set."));
            return 0;
        }
        ArrayList<String> anchorNames = new ArrayList<>(publicAnchors.keySet());
        anchorNames.sort(String::compareToIgnoreCase);
        send(player, true, green("Public anchors:"));
        for (String anchorName : anchorNames) {
            send(player, true, gray(" -"), anchor(anchorName, publicAnchors.get(anchorName), false, true));
        }
        return 1;
    }
    
    public int clearPublicAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (publicAnchors.isEmpty()) {
            send(player, true, gray("No public anchors set."));
            return 0;
        } else {
            publicAnchors.clear();
            send(player, true, green("Public anchors cleared."));
            return storePublicAnchors(player, publicAnchors);
        }
    }
    
    public int setPublicAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String anchorName = StringArgumentType.getString(context, "anchor-name");
        TeleportAnchor anchor = new TeleportAnchor(player);
        publicAnchors.put(anchorName, anchor);
        send(player, true, green("Set public anchor "), anchor(anchorName, anchor, false, true), green("."));
        return storePublicAnchors(player, publicAnchors);
    }
    
    public int removePublicAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String anchorName = StringArgumentType.getString(context, "anchor-name");
        TeleportAnchor anchor = publicAnchors.get(anchorName);
        if (anchor == null) {
            send(player, false, gray("Public anchor "), red(anchorName), gray(" not found."));
            return 0;
        } else {
            publicAnchors.keySet().remove(anchorName);
            send(player, true, green("Remove public anchor "), anchor(anchorName, anchor, false, true), green("."));
            return storePublicAnchors(player, publicAnchors);
        }
    }
    
    public int setStackDepth(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        stackDepth = IntegerArgumentType.getInteger(context, STACK_DEPTH.getKey());
        return storeProperty(player, STACK_DEPTH.getKey(), Integer.toString(stackDepth));
    }
    
    public int setAnchorLimit(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        anchorLimit = IntegerArgumentType.getInteger(context, ANCHOR_LIMIT.getKey());
        return storeProperty(player, ANCHOR_LIMIT.getKey(), Integer.toString(anchorLimit));
    }
    
    public int setRequestTimeout(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        requestTimeout = IntegerArgumentType.getInteger(context, REQUEST_TIMEOUT.getKey());
        return storeProperty(player, REQUEST_TIMEOUT.getKey(), Integer.toString(requestTimeout));
    }
    
    public int restoreDefault(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        stackDepth = DEFAULT_STACK_DEPTH;
        anchorLimit = DEFAULT_ANCHOR_LIMIT;
        requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        String[] keys = {STACK_DEPTH.getKey(), ANCHOR_LIMIT.getKey(), REQUEST_TIMEOUT.getKey()};
        String[] values = {Integer.toString(stackDepth), Integer.toString(anchorLimit), Integer.toString(requestTimeout)};
        return restoreProperties(player, keys, values);
    }
}

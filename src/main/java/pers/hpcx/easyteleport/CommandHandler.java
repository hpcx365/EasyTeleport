package pers.hpcx.easyteleport;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.*;
import java.util.function.Predicate;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.minecraft.command.argument.GameProfileArgumentType.gameProfile;
import static net.minecraft.command.argument.GameProfileArgumentType.getProfileArgument;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static pers.hpcx.easyteleport.EasyTeleport.*;
import static pers.hpcx.easyteleport.EasyTeleportConfig.*;
import static pers.hpcx.easyteleport.EasyTeleportUtils.*;

public record CommandHandler(EasyTeleport mod) implements CommandRegistrationCallback {
    
    public static final String ANCHOR_NAME = "anchor-name";
    public static final String origin_PLAYER = "origin-player";
    public static final String TARGET_PLAYER = "target-player";
    
    @Override
    public void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        Predicate<ServerCommandSource> isPlayer = ServerCommandSource::isExecutedByPlayer;
        Predicate<ServerCommandSource> isOperator = source -> source.hasPermissionLevel(4) || "server".equals(source.getName());
        
        dispatcher.register(literal("tpb").requires(isPlayer).executes(this::teleportBack));
        
        dispatcher.register(literal("tpp").requires(isPlayer).executes(this::teleportReturn));
        
        dispatcher.register(literal("tpp").requires(isPlayer).then(argument(ANCHOR_NAME, string()).suggests(AnchorSuggestionProvider.suggestions(mod)).executes(this::teleport)));
        
        dispatcher.register(literal("tpa").requires(isPlayer).then(argument(TARGET_PLAYER, gameProfile()).executes(this::requestTeleport)));
        
        dispatcher.register(literal("tphere").requires(isPlayer).then(argument(origin_PLAYER, gameProfile()).executes(this::requestTeleportHere)));
        
        dispatcher.register(literal("tpaccept").requires(isPlayer).executes(this::acceptAllTeleport));
        
        dispatcher.register(literal("tpaccept").requires(isPlayer).then(argument(origin_PLAYER, gameProfile()).executes(this::acceptTeleport)));
        
        dispatcher.register(literal("home").requires(isPlayer).executes(this::home));
        
        dispatcher.register(literal("sethome").requires(isPlayer).executes(this::setHome));
        
        dispatcher.register(literal("anchor").requires(isPlayer).then(literal("list").executes(this::listAnchors)));
        
        dispatcher.register(literal("anchor").requires(isPlayer).then(literal("clear").executes(this::clearAnchors)));
        
        dispatcher.register(literal("anchor").requires(isPlayer).then(literal("set").then(argument(ANCHOR_NAME, string()).executes(this::setAnchor))));
        
        dispatcher.register(literal("anchor").requires(isPlayer).then(literal("remove").then(argument(ANCHOR_NAME, string()).suggests(AnchorSuggestionProvider.suggestions(mod)).executes(this::removeAnchor))));
        
        dispatcher.register(literal("anchor").requires(isPlayer).then(literal("share").then(argument(ANCHOR_NAME, string()).suggests(AnchorSuggestionProvider.suggestions(mod)).executes(this::shareAnchorWithAll))));
        
        dispatcher.register(literal("anchor").requires(isPlayer).then(literal("share").then(argument(ANCHOR_NAME, string()).suggests(AnchorSuggestionProvider.suggestions(mod)).then(argument(TARGET_PLAYER, gameProfile()).executes(this::shareAnchor)))));
        
        dispatcher.register(literal("anchor").requires(isPlayer).then(literal("accept").executes(this::acceptAllAnchors)));
        
        dispatcher.register(literal("anchor").requires(isPlayer).then(literal("accept").then(argument(ANCHOR_NAME, string()).suggests(SharingAnchorSuggestionProvider.suggestions(mod)).executes(this::acceptAnchor))));
        
        dispatcher.register(literal("public").requires(isOperator).then(literal("list").executes(this::listPublicAnchors)));
        
        dispatcher.register(literal("public").requires(isOperator).then(literal("clear").executes(this::clearPublicAnchors)));
        
        dispatcher.register(literal("public").requires(isPlayer.and(isOperator)).then(literal("set").then(argument(ANCHOR_NAME, string()).executes(this::setPublicAnchor))));
        
        dispatcher.register(literal("public").requires(isOperator).then(literal("remove").then(argument(ANCHOR_NAME, string()).suggests(PublicAnchorSuggestionProvider.suggestions(mod)).executes(this::removePublicAnchor))));
        
        dispatcher.register(literal("tpconfig").requires(isOperator).then(literal("depth").then(argument(STACK_DEPTH.getKey(), STACK_DEPTH.getType()).executes(this::setStackDepth))));
        
        dispatcher.register(literal("tpconfig").requires(isOperator).then(literal("limit").then(argument(ANCHOR_LIMIT.getKey(), ANCHOR_LIMIT.getType()).executes(this::setAnchorLimit))));
        
        dispatcher.register(literal("tpconfig").requires(isOperator).then(literal("timeout").then(argument(REQUEST_TIMEOUT.getKey(), REQUEST_TIMEOUT.getType()).executes(this::setRequestTimeout))));
        
        dispatcher.register(literal("tpconfig").requires(isOperator).then(literal("default").executes(this::restoreDefault)));
    }
    
    public int teleport(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
        return stack.tpp(source, player, mod, getString(context, ANCHOR_NAME), mod.stackDepth);
    }
    
    public int teleportBack(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
        return stack.tpb(source, player, mod.stackDepth);
    }
    
    public int teleportReturn(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
        return stack.tpp(source, player, mod.stackDepth);
    }
    
    public int requestTeleport(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity origin = source.getPlayerOrThrow();
        Collection<GameProfile> profiles = getProfileArgument(context, TARGET_PLAYER);
        UUID originID = origin.getUuid();
        UUID targetID = selectPlayerID(source, origin, profiles);
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetID);
        if (target == null) {
            playerNotFound(source);
            return 0;
        }
        List<TeleportRequest> requestList = mod.tpRequests.get(targetID);
        if (requestList == null) {
            mod.tpRequests.put(targetID, requestList = new ArrayList<>());
        } else {
            for (TeleportRequest request : requestList) {
                if (request.originID.equals(originID)) {
                    send(source, false, gray("You have requested to teleport to "), player(target));
                    return 0;
                }
            }
        }
        requestList.add(new TeleportRequest(mod.requestTimeout / 50, originID, targetID));
        send(source, true, green("Requested to teleport to "), player(target));
        send(source, true, player(origin), green(" has requested to teleport to you. Type "), yellow("/tpaccept"), green(" to accept"));
        target.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
        return 1;
    }
    
    public int requestTeleportHere(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity target = source.getPlayerOrThrow();
        Collection<GameProfile> profiles = getProfileArgument(context, origin_PLAYER);
        UUID targetID = target.getUuid();
        UUID originID = selectPlayerID(source, target, profiles);
        ServerPlayerEntity origin = source.getServer().getPlayerManager().getPlayer(originID);
        if (origin == null) {
            playerNotFound(source);
            return 0;
        }
        TeleportRequest request = mod.tpHereRequests.get(originID);
        if (request != null) {
            send(source, false, player(origin), gray(" has already received a request"));
            return 0;
        }
        mod.tpHereRequests.put(originID, new TeleportRequest(mod.requestTimeout / 50, originID, targetID));
        send(source, true, player(target), green(" has requested to teleport you to there. Type "), yellow("/tpaccept"), green(" to accept"));
        send(source, true, green("Requested to teleport "), player(origin), green(" to you"));
        origin.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
        return 1;
    }
    
    public int acceptTeleport(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity target = source.getPlayerOrThrow();
        Collection<GameProfile> profiles = getProfileArgument(context, origin_PLAYER);
        UUID originID = selectPlayerID(source, target, profiles);
        UUID targetID = target.getUuid();
        List<TeleportRequest> requestList = mod.tpRequests.get(targetID);
        if (requestList == null || requestList.isEmpty()) {
            send(source, false, gray("You have no request to accept"));
            return 0;
        }
        for (Iterator<TeleportRequest> iterator = requestList.iterator(); iterator.hasNext(); ) {
            TeleportRequest request = iterator.next();
            if (!request.originID.equals(originID)) {
                continue;
            }
            ServerPlayerEntity origin = source.getServer().getPlayerManager().getPlayer(originID);
            if (origin == null) {
                playerNotFound(source);
                return 0;
            }
            TeleportStack stack = ((TeleportStorage) origin).easyTeleport$getStack();
            stack.tpp(source, origin, target, mod.stackDepth);
            iterator.remove();
            if (requestList.isEmpty()) {
                mod.tpRequests.keySet().remove(targetID);
            }
            return 1;
        }
        playerNotFound(source);
        return 0;
    }
    
    public int acceptAllTeleport(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        UUID playerID = player.getUuid();
        List<TeleportRequest> tpRequestList = mod.tpRequests.get(playerID);
        TeleportRequest tpHereRequest = mod.tpHereRequests.get(playerID);
        if (tpRequestList != null) {
            for (TeleportRequest request : tpRequestList) {
                ServerPlayerEntity origin = source.getServer().getPlayerManager().getPlayer(request.originID);
                if (origin == null) {
                    playerNotFound(source);
                } else {
                    TeleportStack stack = ((TeleportStorage) origin).easyTeleport$getStack();
                    stack.tpp(source, origin, player, mod.stackDepth);
                }
            }
            tpRequestList.clear();
            mod.tpRequests.keySet().remove(playerID);
        }
        if (tpHereRequest != null) {
            ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(tpHereRequest.targetID);
            if (target == null) {
                playerNotFound(source);
            } else {
                TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
                stack.tpp(source, player, target, mod.stackDepth);
            }
            mod.tpHereRequests.keySet().remove(playerID);
        }
        return tpRequestList != null || tpHereRequest != null ? 1 : 0;
    }
    
    public int home(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
        return stack.tpp(source, player, mod, "home", mod.stackDepth);
    }
    
    public int setHome(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        if (anchors.size() >= mod.anchorLimit) {
            send(source, false, red("Anchor count limit exceeded"));
            return 0;
        }
        TeleportAnchor anchor = new TeleportAnchor("home", player);
        anchors.put("home", anchor);
        send(source, true, green("Set anchor "), anchor("home", anchor));
        return 1;
    }
    
    public int listAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        if (anchors.isEmpty()) {
            send(source, true, gray("No anchors set"));
            return 0;
        }
        ArrayList<String> anchorNames = new ArrayList<>(anchors.keySet());
        anchorNames.sort(String::compareToIgnoreCase);
        send(source, true, green("Anchors set by "), player(player), green(":"));
        for (String anchorName : anchorNames) {
            send(source, true, gray(" -"), anchor(anchorName, anchors.get(anchorName)));
        }
        return 1;
    }
    
    public int clearAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        if (anchors.isEmpty()) {
            send(source, true, gray("No anchors set"));
            return 0;
        } else {
            anchors.clear();
            send(source, true, green("Anchors cleared"));
            return 1;
        }
    }
    
    public int setAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        if (anchors.size() >= mod.anchorLimit) {
            send(source, false, red("Anchor count limit exceeded"));
            return 0;
        }
        String anchorName = getString(context, ANCHOR_NAME);
        TeleportAnchor anchor = new TeleportAnchor(anchorName, player);
        anchors.put(anchorName, anchor);
        send(source, true, green("Set anchor "), anchor(anchorName, anchor));
        return 1;
    }
    
    public int removeAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        String anchorName = getString(context, ANCHOR_NAME);
        TeleportAnchor anchor = anchors.get(anchorName);
        if (anchor == null) {
            send(source, false, gray("Anchor "), red(anchorName), gray(" not found"));
            return 0;
        } else {
            anchors.keySet().remove(anchorName);
            send(source, true, green("Remove anchor "), anchor(anchorName, anchor));
            return 1;
        }
    }
    
    public int shareAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity origin = source.getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) origin).easyTeleport$getAnchors();
        String anchorName = getString(context, ANCHOR_NAME);
        TeleportAnchor anchor = anchors.get(anchorName);
        if (anchor == null) {
            send(source, false, gray("Anchor "), red(anchorName), gray(" not found"));
            return 0;
        }
        Collection<GameProfile> profiles = getProfileArgument(context, TARGET_PLAYER);
        UUID originID = origin.getUuid();
        UUID targetID = selectPlayerID(source, origin, profiles);
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetID);
        if (target == null) {
            playerNotFound(source);
            return 0;
        }
        List<ShareRequest> requestList = mod.shareRequests.get(targetID);
        if (requestList == null) {
            mod.shareRequests.put(targetID, requestList = new ArrayList<>());
        } else {
            for (ShareRequest request : requestList) {
                if (request.anchor.name().equals(anchorName)) {
                    send(source, false, player(target), gray(" has already received an anchor named "), yellow(anchorName));
                    return 0;
                }
            }
        }
        requestList.add(new ShareRequest(mod.requestTimeout / 50, originID, targetID, anchor));
        send(source, true, green("Requested to share anchor with "), player(target));
        send(source, true, player(origin), green(" has requested to share "), anchor(anchorName, anchor), green(" with you. Type "), yellow("/anchor accept"), green(" to accept"));
        target.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
        return 1;
    }
    
    public int shareAnchorWithAll(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity origin = source.getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) origin).easyTeleport$getAnchors();
        String anchorName = getString(context, ANCHOR_NAME);
        TeleportAnchor anchor = anchors.get(anchorName);
        if (anchor == null) {
            send(source, false, gray("Anchor "), red(anchorName), gray(" not found"));
            return 0;
        }
        for (ServerPlayerEntity target : source.getServer().getPlayerManager().getPlayerList()) {
            UUID originID = origin.getUuid();
            UUID targetID = target.getUuid();
            if (originID.equals(targetID)) {
                continue;
            }
            List<ShareRequest> requestList = mod.shareRequests.get(targetID);
            if (requestList == null) {
                mod.shareRequests.put(targetID, requestList = new ArrayList<>());
            } else {
                boolean hasRequest = false;
                for (ShareRequest request : requestList) {
                    if (request.anchor.name().equals(anchorName)) {
                        hasRequest = true;
                        break;
                    }
                }
                if (hasRequest) {
                    send(source, false, player(target), gray(" has already received an anchor named "), yellow(anchorName));
                    continue;
                }
            }
            requestList.add(new ShareRequest(mod.requestTimeout / 50, originID, targetID, anchor));
            send(source, true, green("Requested to share anchor with "), player(target));
            send(source, true, player(origin), green(" has requested to share "), anchor(anchorName, anchor), green(" with you. Type "), yellow("/anchor accept"), green(" to accept"));
            target.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
        return 1;
    }
    
    public int acceptAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity target = source.getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) target).easyTeleport$getAnchors();
        String anchorName = getString(context, ANCHOR_NAME);
        List<ShareRequest> requestList = mod.shareRequests.get(target.getUuid());
        if (requestList == null || requestList.isEmpty()) {
            send(source, false, gray("You have no request to accept"));
            return 0;
        }
        for (Iterator<ShareRequest> iterator = requestList.iterator(); iterator.hasNext(); ) {
            ShareRequest request = iterator.next();
            if (!request.anchor.name().equals(anchorName)) {
                continue;
            }
            if (anchors.size() >= mod.anchorLimit) {
                send(source, false, red("Anchor count limit exceeded"));
            } else {
                anchors.put(request.anchor.name(), request.anchor);
                ServerPlayerEntity origin = source.getServer().getPlayerManager().getPlayer(request.originID);
                send(source, true, green("Accepted anchor "), anchor(request.anchor.name(), request.anchor));
                if (origin != null) {
                    send(source, true, green("Anchor "), anchor(request.anchor.name(), request.anchor), green(" shared with "), player(target), green(" successfully"));
                }
            }
            iterator.remove();
            if (requestList.isEmpty()) {
                mod.shareRequests.keySet().remove(target.getUuid());
            }
            return 1;
        }
        send(source, false, gray("Anchor "), red(anchorName), gray(" not found"));
        return 0;
    }
    
    public int acceptAllAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity target = source.getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) target).easyTeleport$getAnchors();
        List<ShareRequest> requestList = mod.shareRequests.get(target.getUuid());
        if (requestList == null || requestList.isEmpty()) {
            send(source, false, gray("You have no request to accept"));
            return 0;
        }
        for (ShareRequest request : requestList) {
            if (anchors.size() >= mod.anchorLimit) {
                send(source, false, red("Anchor count limit exceeded"));
                break;
            } else {
                anchors.put(request.anchor.name(), request.anchor);
                ServerPlayerEntity origin = source.getServer().getPlayerManager().getPlayer(request.originID);
                send(source, true, green("Accepted anchor "), anchor(request.anchor.name(), request.anchor));
                if (origin != null) {
                    send(source, true, green("Anchor "), anchor(request.anchor.name(), request.anchor), green(" shared with "), player(target), green(" successfully"));
                }
            }
        }
        requestList.clear();
        mod.shareRequests.keySet().remove(target.getUuid());
        return 1;
    }
    
    public int listPublicAnchors(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (mod.publicAnchors.isEmpty()) {
            send(source, true, gray("No public anchors set"));
            return 0;
        }
        ArrayList<String> anchorNames = new ArrayList<>(mod.publicAnchors.keySet());
        anchorNames.sort(String::compareToIgnoreCase);
        send(source, true, green("Public anchors:"));
        for (String anchorName : anchorNames) {
            send(source, true, gray(" -"), anchor(anchorName, mod.publicAnchors.get(anchorName), false, true));
        }
        return 1;
    }
    
    public int clearPublicAnchors(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (mod.publicAnchors.isEmpty()) {
            send(source, true, gray("No public anchors set"));
            return 0;
        } else {
            mod.publicAnchors.clear();
            send(source, true, green("Public anchors cleared"));
            return storePublicAnchors(source, mod.publicAnchors);
        }
    }
    
    public int setPublicAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String anchorName = getString(context, ANCHOR_NAME);
        TeleportAnchor anchor = new TeleportAnchor(anchorName, player);
        mod.publicAnchors.put(anchorName, anchor);
        send(source, true, green("Set public anchor "), anchor(anchorName, anchor, false, true));
        return storePublicAnchors(source, mod.publicAnchors);
    }
    
    public int removePublicAnchor(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String anchorName = getString(context, ANCHOR_NAME);
        TeleportAnchor anchor = mod.publicAnchors.get(anchorName);
        if (anchor == null) {
            send(source, false, gray("Public anchor "), red(anchorName), gray(" not found"));
            return 0;
        } else {
            mod.publicAnchors.keySet().remove(anchorName);
            send(source, true, green("Remove public anchor "), anchor(anchorName, anchor, false, true));
            return storePublicAnchors(source, mod.publicAnchors);
        }
    }
    
    public int setStackDepth(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        mod.stackDepth = IntegerArgumentType.getInteger(context, STACK_DEPTH.getKey());
        return storeProperty(source, STACK_DEPTH.getKey(), Integer.toString(mod.stackDepth));
    }
    
    public int setAnchorLimit(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        mod.anchorLimit = IntegerArgumentType.getInteger(context, ANCHOR_LIMIT.getKey());
        return storeProperty(source, ANCHOR_LIMIT.getKey(), Integer.toString(mod.anchorLimit));
    }
    
    public int setRequestTimeout(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        mod.requestTimeout = IntegerArgumentType.getInteger(context, REQUEST_TIMEOUT.getKey());
        return storeProperty(source, REQUEST_TIMEOUT.getKey(), Integer.toString(mod.requestTimeout));
    }
    
    public int restoreDefault(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        mod.stackDepth = DEFAULT_STACK_DEPTH;
        mod.anchorLimit = DEFAULT_ANCHOR_LIMIT;
        mod.requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        String[] keys = {STACK_DEPTH.getKey(), ANCHOR_LIMIT.getKey(), REQUEST_TIMEOUT.getKey()};
        String[] values = {Integer.toString(mod.stackDepth), Integer.toString(mod.anchorLimit), Integer.toString(mod.requestTimeout)};
        return restoreProperties(source, keys, values);
    }
}

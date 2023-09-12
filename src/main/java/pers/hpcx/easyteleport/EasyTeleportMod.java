package pers.hpcx.easyteleport;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.util.math.Vec3d;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.*;
import static net.minecraft.util.Formatting.*;
import static pers.hpcx.easyteleport.EasyTeleportConfigEnum.ANCHOR_LIMIT;
import static pers.hpcx.easyteleport.EasyTeleportConfigEnum.STACK_DEPTH;

public class EasyTeleportMod implements ModInitializer, CommandRegistrationCallback {
    
    public static final String MOD_ID = "easyteleport";
    public static final Path MOD_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("easy-teleport.properties");
    
    public int stackDepth = 8;
    public int anchorLimit = 16;
    
    @Override
    public void onInitialize() {
        loadConfig();
        CommandRegistrationCallback.EVENT.register(this);
    }
    
    public void loadConfig() {
        try {
            if (Files.exists(MOD_CONFIG_PATH)) {
                try (InputStream in = new BufferedInputStream(Files.newInputStream(MOD_CONFIG_PATH))) {
                    Properties properties = new Properties();
                    properties.load(in);
                    String stackDepth = properties.getProperty(STACK_DEPTH.getKey());
                    String anchorLimit = properties.getProperty(ANCHOR_LIMIT.getKey());
                    if (stackDepth != null) {
                        this.stackDepth = Integer.parseInt(stackDepth);
                    }
                    if (anchorLimit != null) {
                        this.anchorLimit = Integer.parseInt(anchorLimit);
                    }
                }
            } else {
                Files.createFile(MOD_CONFIG_PATH);
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(MOD_CONFIG_PATH))) {
                    Properties properties = new Properties();
                    properties.setProperty(STACK_DEPTH.getKey(), Integer.toString(stackDepth));
                    properties.setProperty(ANCHOR_LIMIT.getKey(), Integer.toString(anchorLimit));
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
    
    public int teleport(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, Anchor> anchors = ((AnchorStorage) player).easyTeleport$getAnchors();
        AnchorStack stack = ((AnchorStorage) player).easyTeleport$getStack();
        String anchorName = StringArgumentType.getString(context, "anchor-name");
        Anchor anchor = anchors.get(anchorName);
        if (anchor == null) {
            sendMessage(source, false, Text.literal("Anchor ").formatted(GRAY), Text.literal(anchorName).formatted(RED),
                    Text.literal(" not set.").formatted(GRAY));
            return 0;
        } else {
            Vec3d position = anchor.position();
            stack.tpp(new Anchor(player.getPos(), player.getWorld().getRegistryKey()), stackDepth);
            player.teleport(player.getServer().getWorld(anchor.world()), position.x, position.y, position.z, player.getYaw(), player.getPitch());
            sendMessage(source, true, Text.literal("Teleport to ").formatted(GREEN), Text.literal(anchorName).formatted(YELLOW));
            return 1;
        }
    }
    
    public int teleportBack(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AnchorStack stack = ((AnchorStorage) player).easyTeleport$getStack();
        Anchor anchor = stack.tpb();
        if (anchor == null) {
            sendMessage(source, false, Text.literal("Cannot tpb anymore.").formatted(GRAY));
            return 0;
        } else {
            Vec3d position = anchor.position();
            player.teleport(player.getServer().getWorld(anchor.world()), position.x, position.y, position.z, player.getYaw(), player.getPitch());
            sendMessage(source, true, Text.literal("Teleport to ").formatted(GREEN), Text.literal(toString(position)).formatted(GRAY));
            return 1;
        }
    }
    
    public int teleportReturn(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        AnchorStack stack = ((AnchorStorage) player).easyTeleport$getStack();
        Anchor anchor = stack.tpp();
        if (anchor == null) {
            sendMessage(source, false, Text.literal("Cannot tpp anymore.").formatted(GRAY));
            return 0;
        } else {
            Vec3d position = anchor.position();
            player.teleport(player.getServer().getWorld(anchor.world()), position.x, position.y, position.z, player.getYaw(), player.getPitch());
            sendMessage(source, true, Text.literal("Teleport to ").formatted(GREEN), Text.literal(toString(position)).formatted(GRAY));
            return 1;
        }
    }
    
    public int listAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, Anchor> anchors = ((AnchorStorage) player).easyTeleport$getAnchors();
        if (anchors.isEmpty()) {
            sendMessage(source, true, Text.literal("No anchors set.").formatted(GRAY));
            return 0;
        } else {
            sendMessage(source, true, Text.literal("Anchors set by ").formatted(GREEN), ((MutableText) player.getName()).formatted(GOLD));
            ArrayList<String> anchorNames = new ArrayList<>(anchors.keySet());
            anchorNames.sort(String::compareToIgnoreCase);
            for (String anchorName : anchorNames) {
                sendMessage(source, true, Text.literal(" -").formatted(GRAY), Text.literal(anchorName).formatted(YELLOW), Text.literal(" at ").formatted(GRAY),
                        Text.literal(toString(anchors.get(anchorName).position())).formatted(GRAY));
            }
            return 1;
        }
    }
    
    public int clearAnchors(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, Anchor> anchors = ((AnchorStorage) player).easyTeleport$getAnchors();
        if (anchors.isEmpty()) {
            sendMessage(source, true, Text.literal("No anchors set.").formatted(GRAY));
            return 0;
        } else {
            anchors.clear();
            sendMessage(source, true, Text.literal("Anchors cleared.").formatted(GREEN));
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
            sendMessage(source, true, Text.literal("Anchor ").formatted(GREEN), Text.literal(anchorName).formatted(YELLOW),
                    Text.literal(" set at ").formatted(GREEN), Text.literal(toString(position)).formatted(GRAY));
            return 1;
        } else {
            sendMessage(source, false, Text.literal("Anchor count limit exceeded.").formatted(RED));
            return 0;
        }
    }
    
    public int removeAnchor(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, Anchor> anchors = ((AnchorStorage) player).easyTeleport$getAnchors();
        String anchorName = StringArgumentType.getString(context, "anchor-name");
        if (anchors.keySet().remove(anchorName)) {
            sendMessage(source, true, Text.literal("Anchor ").formatted(GREEN), Text.literal(anchorName).formatted(YELLOW),
                    Text.literal(" removed.").formatted(GREEN));
            return 1;
        } else {
            sendMessage(source, false, Text.literal("Anchor ").formatted(GRAY), Text.literal(anchorName).formatted(RED),
                    Text.literal(" not set.").formatted(GRAY));
            return 0;
        }
    }
    
    public int setStackDepth(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        stackDepth = IntegerArgumentType.getInteger(context, STACK_DEPTH.getKey());
        sendMessage(source, true, Text.literal("Stack depth set to ").formatted(GREEN), Text.literal(Integer.toString(stackDepth)).formatted(GOLD));
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(MOD_CONFIG_PATH))) {
            Properties properties = new Properties();
            properties.setProperty(STACK_DEPTH.getKey(), Integer.toString(stackDepth));
            properties.store(out, "easy-teleport mod config");
            return 1;
        } catch (IOException e) {
            sendMessage(source, false, Text.literal("Failed to write config file: ").formatted(GRAY), Text.literal(e.getMessage()).formatted(RED));
            return 0;
        }
    }
    
    public int setAnchorLimit(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        anchorLimit = IntegerArgumentType.getInteger(context, ANCHOR_LIMIT.getKey());
        sendMessage(source, true, Text.literal("Anchor limit set to ").formatted(GREEN), Text.literal(Integer.toString(anchorLimit)).formatted(GOLD));
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(MOD_CONFIG_PATH))) {
            Properties properties = new Properties();
            properties.setProperty(ANCHOR_LIMIT.getKey(), Integer.toString(anchorLimit));
            properties.store(out, "easy-teleport mod config");
            return 1;
        } catch (IOException e) {
            sendMessage(source, false, Text.literal("Failed to write config file: ").formatted(GRAY), Text.literal(e.getMessage()).formatted(RED));
            return 0;
        }
    }
}

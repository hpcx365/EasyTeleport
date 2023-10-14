package pers.hpcx.easyteleport;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;

import static pers.hpcx.easyteleport.EasyTeleportConfig.*;
import static pers.hpcx.easyteleport.EasyTeleportUtils.*;

public class EasyTeleport implements ModInitializer {
    
    public static final int DEFAULT_STACK_DEPTH = 8;
    public static final int DEFAULT_ANCHOR_LIMIT = 16;
    public static final int DEFAULT_REQUEST_TIMEOUT = 20000;
    
    public int stackDepth = DEFAULT_STACK_DEPTH;
    public int anchorLimit = DEFAULT_ANCHOR_LIMIT;
    public int requestTimeout = DEFAULT_REQUEST_TIMEOUT;
    
    public final Map<String, TeleportAnchor> publicAnchors = new HashMap<>();
    public final Map<UUID, List<TeleportRequest>> tpRequests = new HashMap<>();
    public final Map<UUID, TeleportRequest> tpHereRequests = new HashMap<>();
    public final Map<UUID, List<ShareRequest>> shareRequests = new HashMap<>();
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(loadConfig);
        ServerLifecycleEvents.SERVER_STARTING.register(loadPublicAnchors);
        ServerLivingEntityEvents.AFTER_DEATH.register(afterDeath);
        ServerTickEvents.END_SERVER_TICK.register(updateTpRequests);
        ServerTickEvents.END_SERVER_TICK.register(updateTpHereRequests);
        ServerTickEvents.END_SERVER_TICK.register(updateShareRequests);
        CommandRegistrationCallback.EVENT.register(commandHandler);
    }
    
    public void getProperties(Properties properties) {
        stackDepth = getInteger(properties, STACK_DEPTH.getKey(), DEFAULT_STACK_DEPTH, LOGGER);
        anchorLimit = getInteger(properties, ANCHOR_LIMIT.getKey(), DEFAULT_ANCHOR_LIMIT, LOGGER);
        requestTimeout = getInteger(properties, REQUEST_TIMEOUT.getKey(), DEFAULT_REQUEST_TIMEOUT, LOGGER);
    }
    
    public void setProperties(Properties properties) {
        properties.setProperty(STACK_DEPTH.getKey(), stackDepth != DEFAULT_STACK_DEPTH ? Integer.toString(stackDepth) : "");
        properties.setProperty(ANCHOR_LIMIT.getKey(), anchorLimit != DEFAULT_ANCHOR_LIMIT ? Integer.toString(anchorLimit) : "");
        properties.setProperty(REQUEST_TIMEOUT.getKey(), requestTimeout != DEFAULT_REQUEST_TIMEOUT ? Integer.toString(requestTimeout) : "");
    }
    
    public final CommandHandler commandHandler = new CommandHandler(this);
    
    public final ServerLifecycleEvents.ServerStarting loadConfig = server -> {
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
                    properties.store(out, CONFIG_COMMENTS);
                }
            }
        } catch (IOException e) {
            LOGGER.error("failed to load config file", e);
        }
    };
    
    public final ServerLifecycleEvents.ServerStarting loadPublicAnchors = server -> {
        try {
            NbtCompound anchorData = NbtIo.read(ANCHOR_PATH.toFile());
            if (anchorData != null) {
                for (String anchorName : anchorData.getKeys()) {
                    TeleportAnchor anchor = TeleportAnchor.fromCompound(anchorData.getCompound(anchorName));
                    if (anchor.name().isEmpty()) {
                        publicAnchors.put(anchorName, new TeleportAnchor(anchorName, anchor.position(), anchor.world()));
                    } else {
                        publicAnchors.put(anchorName, anchor);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("failed to load public anchors", e);
        }
    };
    
    public final ServerLivingEntityEvents.AfterDeath afterDeath = (entity, damageSource) -> {
        if (entity instanceof ServerPlayerEntity player) {
            TeleportStack stack = ((TeleportStorage) player).easyTeleport$getStack();
            stack.afterDeath(player, stackDepth);
        }
    };
    
    public final ServerTickEvents.EndTick updateTpRequests = server -> {
        if (tpRequests.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, List<TeleportRequest>>> entryIterator = tpRequests.entrySet().iterator(); entryIterator.hasNext(); ) {
            List<TeleportRequest> requestList = entryIterator.next().getValue();
            for (Iterator<TeleportRequest> requestIterator = requestList.iterator(); requestIterator.hasNext(); ) {
                TeleportRequest request = requestIterator.next();
                if (--request.keepAliveTicks <= 0) {
                    requestIterator.remove();
                    notifyRequestTimedOut("Teleport", server, request.originID, request.targetID);
                }
            }
            if (requestList.isEmpty()) {
                entryIterator.remove();
            }
        }
    };
    
    public final ServerTickEvents.EndTick updateTpHereRequests = server -> {
        if (tpHereRequests.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, TeleportRequest>> entryIterator = tpHereRequests.entrySet().iterator(); entryIterator.hasNext(); ) {
            TeleportRequest request = entryIterator.next().getValue();
            if (--request.keepAliveTicks <= 0) {
                entryIterator.remove();
                notifyRequestTimedOut("Teleport here", server, request.originID, request.targetID);
            }
        }
    };
    
    public final ServerTickEvents.EndTick updateShareRequests = server -> {
        if (shareRequests.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, List<ShareRequest>>> entryIterator = shareRequests.entrySet().iterator(); entryIterator.hasNext(); ) {
            List<ShareRequest> requestList = entryIterator.next().getValue();
            for (Iterator<ShareRequest> requestIterator = requestList.iterator(); requestIterator.hasNext(); ) {
                ShareRequest request = requestIterator.next();
                if (--request.keepAliveTicks <= 0) {
                    requestIterator.remove();
                    notifyRequestTimedOut("Anchor share", server, request.originID, request.targetID);
                }
            }
            if (requestList.isEmpty()) {
                entryIterator.remove();
            }
        }
    };
}

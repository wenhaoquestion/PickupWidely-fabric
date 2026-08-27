package com.example.pickuprange;

import com.example.pickuprange.command.PickupRangeCommand;
import com.example.pickuprange.config.ServerConfig;
import com.example.pickuprange.data.PlayerRangeManager;
import com.example.pickuprange.network.ModPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public final class PickupRangeMod implements ModInitializer {
    public static final String MOD_ID = "pickuprange";
    public static final Logger LOGGER = LogManager.getLogger("PickupRange");
    private static ServerConfig serverConfig;

    @Override
    public void onInitialize() {
        ModPackets.registerServerReceivers();

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                PickupRangeCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Path path = FabricLoader.getInstance().getConfigDir()
                    .resolve("pickup-range-server.json");
            serverConfig = ServerConfig.load(path);
            LOGGER.info("Loaded Pickup Range config (items {}, xp {})",
                    serverConfig.getDefaultItemRange(), serverConfig.getDefaultXpRange());
        });
        ServerLifecycleEvents.SERVER_STARTED.register(PlayerRangeManager::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(PlayerRangeManager::save);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (serverConfig == null) {
                return;
            }
            if (ServerPlayNetworking.canSend(player, ModPackets.SYNC_CONFIG)) {
                ModPackets.sendSyncConfig(player, serverConfig);
            }
            if (ServerPlayNetworking.canSend(player, ModPackets.SYNC_PLAYER_RANGE)) {
                ModPackets.sendSyncPlayerRange(player,
                        PlayerRangeManager.getEffectiveItemRange(player),
                        PlayerRangeManager.getEffectiveXpRange(player));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PlayerRangeManager.onPlayerDisconnect(handler.player.getUuid()));

        LOGGER.info("Pickup Range 2.0 initialized.");
    }

    public static ServerConfig getServerConfig() {
        return serverConfig != null ? serverConfig : ServerConfig.createDefaults();
    }

    public static void setServerConfig(ServerConfig config) {
        serverConfig = config;
    }
}

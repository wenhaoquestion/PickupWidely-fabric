package com.example.pickuprange;

import com.example.pickuprange.command.PickupRangeCommand;
import com.example.pickuprange.config.ServerConfig;
import com.example.pickuprange.data.PlayerRangeManager;
import com.example.pickuprange.network.ModPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Common (server + client) mod initializer for Pickup Range.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Register networking payload types (must happen on both sides).</li>
 *   <li>Load the server config and hot-reload it on demand.</li>
 *   <li>Register server-side event listeners (player join, server stop).</li>
 *   <li>Register commands.</li>
 * </ul>
 */
public class PickupRangeMod implements ModInitializer {

    public static final String MOD_ID = "pickuprange";
    public static final Logger LOGGER = LoggerFactory.getLogger("PickupRange");

    private static ServerConfig serverConfig;

    @Override
    public void onInitialize() {
        LOGGER.info("Pickup Range v2 initializing…");

        // 1. Register packet payload types on both sides (required by Fabric API).
        ModPackets.registerPayloadTypes();

        // 2. Register server-side packet receivers.
        ModPackets.registerServerReceivers();

        // 3. Register commands.
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                PickupRangeCommand.register(dispatcher));

        // 4. Server lifecycle: load config and player data on start, persist on stop.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Path configPath = FabricLoader.getInstance()
                    .getConfigDir().resolve("pickup-range-server.json");
            serverConfig = ServerConfig.load(configPath);
            LOGGER.info("Server config loaded — item range: {}, xp range: {}",
                    serverConfig.getDefaultItemRange(), serverConfig.getDefaultXpRange());
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                PlayerRangeManager.load(server));

        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                PlayerRangeManager.save(server));

        // 5. On player join: sync config to clients that have the mod.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (serverConfig == null) return;

            // VERSION-SENSITIVE: canSend checks whether the client registered our payload type.
            if (ServerPlayNetworking.canSend(player, ModPackets.SYNC_CONFIG_TYPE)) {
                ModPackets.sendSyncConfig(player, serverConfig);
                LOGGER.debug("Sent config sync to {}", player.getName().getString());
            }

            // Push per-player data to client if it supports per-player packets.
            if (ServerPlayNetworking.canSend(player, ModPackets.SYNC_PLAYER_RANGE_TYPE)) {
                ModPackets.sendSyncPlayerRange(player,
                        PlayerRangeManager.getEffectiveItemRange(player),
                        PlayerRangeManager.getEffectiveXpRange(player));
            }
        });

        // 6. Clean up player data from memory on disconnect (data is already persisted).
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PlayerRangeManager.onPlayerDisconnect(handler.getPlayer().getUUID()));

        LOGGER.info("Pickup Range initialized.");
    }

    /**
     * Returns the active server configuration, or a default instance if not yet loaded.
     *
     * @return the server config (never null)
     */
    public static ServerConfig getServerConfig() {
        return serverConfig != null ? serverConfig : ServerConfig.createDefaults();
    }

    /**
     * Replaces the active server configuration (used by the reload command).
     *
     * @param config the newly loaded config
     */
    public static void setServerConfig(ServerConfig config) {
        serverConfig = config;
    }
}

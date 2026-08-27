package com.example.pickuprange.network;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.config.ServerConfig;
import com.example.pickuprange.data.PlayerRangeManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Central registry for all Pickup Range network packets.
 *
 * <p>Payload types must be registered on <em>both</em> sides (in the common initializer)
 * before handlers are attached. Handlers are registered per-side.
 *
 * <p>Packet flow:
 * <pre>
 *   Server → Client  SyncConfigPayload      — push config on join
 *   Server → Client  SyncPlayerRangePayload — push per-player ranges on join / after set
 *   Client → Server  (none currently — range changes go via commands)
 * </pre>
 *
 * <p>VERSION-SENSITIVE: {@link PayloadTypeRegistry} API may change across Fabric API updates.
 */
public final class ModPackets {

    /** Convenience reference to the sync-config payload type. */
    public static final CustomPacketPayload.Type<SyncConfigPayload> SYNC_CONFIG_TYPE =
            SyncConfigPayload.TYPE;

    /** Convenience reference to the per-player range payload type. */
    public static final CustomPacketPayload.Type<SyncPlayerRangePayload> SYNC_PLAYER_RANGE_TYPE =
            SyncPlayerRangePayload.TYPE;

    private ModPackets() {}

    // -------------------------------------------------------------------------
    // Registration — called from PickupRangeMod (common init, both sides)
    // -------------------------------------------------------------------------

    /**
     * Registers all payload types with Fabric's {@link PayloadTypeRegistry}.
     * Must be called from the common initializer ({@code ModInitializer}).
     */
    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPlayerRangePayload.TYPE, SyncPlayerRangePayload.CODEC);
    }

    // -------------------------------------------------------------------------
    // Server-side receivers
    // -------------------------------------------------------------------------

    /**
     * Registers server-side packet receivers.
     * Must be called from the common initializer (safe on dedicated server).
     */
    public static void registerServerReceivers() {
        // No C→S packets currently; range changes go through commands.
        // Add ServerPlayNetworking.registerGlobalReceiver() calls here if needed.
    }

    // -------------------------------------------------------------------------
    // Server-side senders
    // -------------------------------------------------------------------------

    /**
     * Sends the current server config to a player (typically on join).
     *
     * @param player the target player
     * @param config the server config to send
     */
    public static void sendSyncConfig(ServerPlayer player, ServerConfig config) {
        ServerPlayNetworking.send(player, new SyncConfigPayload(
                config.getDefaultItemRange(),
                config.getDefaultXpRange(),
                config.getMaxRange(),
                config.getMinRange(),
                config.isAllowPlayerOverride()
        ));
    }

    /**
     * Sends per-player range values to a specific player.
     *
     * @param player    the target player
     * @param itemRange effective item range for this player
     * @param xpRange   effective XP range for this player
     */
    public static void sendSyncPlayerRange(ServerPlayer player, double itemRange, double xpRange) {
        ServerPlayNetworking.send(player, new SyncPlayerRangePayload(itemRange, xpRange));
    }

    /**
     * Broadcasts updated range to all online players after a server config reload.
     *
     * @param server the running server
     * @param config reloaded config
     */
    public static void broadcastConfigReload(net.minecraft.server.MinecraftServer server,
                                              ServerConfig config) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, SYNC_CONFIG_TYPE)) {
                sendSyncConfig(player, config);
            }
            if (ServerPlayNetworking.canSend(player, SYNC_PLAYER_RANGE_TYPE)) {
                sendSyncPlayerRange(player,
                        PlayerRangeManager.getEffectiveItemRange(player),
                        PlayerRangeManager.getEffectiveXpRange(player));
            }
        }
    }
}

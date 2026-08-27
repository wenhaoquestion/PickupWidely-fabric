package com.example.pickuprange.network;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.config.ServerConfig;
import com.example.pickuprange.data.PlayerRangeManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Legacy Fabric play-channel packet registration and send helpers. */
public final class ModPackets {
    public static final ResourceLocation SYNC_CONFIG_TYPE =
            new ResourceLocation(PickupRangeMod.MOD_ID, "sync_config");
    public static final ResourceLocation SYNC_PLAYER_RANGE_TYPE =
            new ResourceLocation(PickupRangeMod.MOD_ID, "sync_player_range");

    private ModPackets() {}

    /** Legacy channels require no payload-type registry. */
    public static void registerPayloadTypes() {}

    /** There are currently no client-to-server packets. */
    public static void registerServerReceivers() {}

    public static void sendSyncConfig(ServerPlayer player, ServerConfig config) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new SyncConfigPayload(
                config.getDefaultItemRange(),
                config.getDefaultXpRange(),
                config.getMaxRange(),
                config.getMinRange(),
                config.isAllowPlayerOverride()).write(buf);
        ServerPlayNetworking.send(player, SYNC_CONFIG_TYPE, buf);
    }

    public static void sendSyncPlayerRange(ServerPlayer player, double itemRange, double xpRange) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new SyncPlayerRangePayload(itemRange, xpRange).write(buf);
        ServerPlayNetworking.send(player, SYNC_PLAYER_RANGE_TYPE, buf);
    }

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

package com.example.pickuprange.network;

import com.example.pickuprange.PickupRangeMod;
import com.example.pickuprange.config.ServerConfig;
import com.example.pickuprange.data.PlayerRangeManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class ModPackets {
    public static final Identifier SYNC_CONFIG =
            new Identifier(PickupRangeMod.MOD_ID, "sync_config");
    public static final Identifier SYNC_PLAYER_RANGE =
            new Identifier(PickupRangeMod.MOD_ID, "sync_player_range");

    private ModPackets() {
    }

    public static void registerServerReceivers() {
        // Range changes use commands. No client-to-server custom packets are required.
    }

    public static void sendSyncConfig(ServerPlayerEntity player, ServerConfig config) {
        PacketByteBuf buffer = PacketByteBufs.create();
        new SyncConfigPayload(
                config.getDefaultItemRange(),
                config.getDefaultXpRange(),
                config.getMaxRange(),
                config.getMinRange(),
                config.isAllowPlayerOverride()).write(buffer);
        ServerPlayNetworking.send(player, SYNC_CONFIG, buffer);
    }

    public static void sendSyncPlayerRange(ServerPlayerEntity player,
                                           double itemRange, double xpRange) {
        PacketByteBuf buffer = PacketByteBufs.create();
        new SyncPlayerRangePayload(itemRange, xpRange).write(buffer);
        ServerPlayNetworking.send(player, SYNC_PLAYER_RANGE, buffer);
    }

    public static void broadcastConfigReload(MinecraftServer server, ServerConfig config) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, SYNC_CONFIG)) {
                sendSyncConfig(player, config);
            }
            if (ServerPlayNetworking.canSend(player, SYNC_PLAYER_RANGE)) {
                sendSyncPlayerRange(player,
                        PlayerRangeManager.getEffectiveItemRange(player),
                        PlayerRangeManager.getEffectiveXpRange(player));
            }
        }
    }
}

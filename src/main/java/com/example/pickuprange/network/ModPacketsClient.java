package com.example.pickuprange.network;

import com.example.pickuprange.PickupRangeClientMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public final class ModPacketsClient {
    private ModPacketsClient() {
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.SYNC_CONFIG,
                (client, handler, buffer, responseSender) -> {
                    SyncConfigPayload payload = SyncConfigPayload.read(buffer);
                    client.execute(() -> PickupRangeClientMod.onServerConfigReceived(
                            payload.defaultItemRange(),
                            payload.defaultXpRange(),
                            payload.maxRange(),
                            payload.minRange(),
                            payload.allowPlayerOverride()));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.SYNC_PLAYER_RANGE,
                (client, handler, buffer, responseSender) -> {
                    SyncPlayerRangePayload payload = SyncPlayerRangePayload.read(buffer);
                    client.execute(() -> PickupRangeClientMod.onPlayerRangeReceived(
                            payload.itemRange(), payload.xpRange()));
                });
    }
}

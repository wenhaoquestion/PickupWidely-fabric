package com.example.pickuprange.network;

import com.example.pickuprange.PickupRangeClientMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client receivers for Fabric's legacy identifier-and-buffer packet API. */
@Environment(EnvType.CLIENT)
public final class ModPacketsClient {
    private ModPacketsClient() {}

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.SYNC_CONFIG_TYPE,
                (client, handler, buf, responseSender) -> {
                    SyncConfigPayload payload = SyncConfigPayload.read(buf);
                    client.execute(() -> PickupRangeClientMod.onServerConfigReceived(
                            payload.defaultItemRange(), payload.defaultXpRange(),
                            payload.maxRange(), payload.minRange(),
                            payload.allowPlayerOverride()));
                });

        ClientPlayNetworking.registerGlobalReceiver(ModPackets.SYNC_PLAYER_RANGE_TYPE,
                (client, handler, buf, responseSender) -> {
                    SyncPlayerRangePayload payload = SyncPlayerRangePayload.read(buf);
                    client.execute(() -> PickupRangeClientMod.onPlayerRangeReceived(
                            payload.itemRange(), payload.xpRange()));
                });
    }
}

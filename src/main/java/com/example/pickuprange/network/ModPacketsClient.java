package com.example.pickuprange.network;

import com.example.pickuprange.PickupRangeClientMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side packet receiver registrations, isolated here so the compiler never
 * attempts to load client-only classes on a dedicated server.
 *
 * <p>Called exclusively from {@link com.example.pickuprange.PickupRangeClientMod}.
 */
@Environment(EnvType.CLIENT)
public final class ModPacketsClient {

    private ModPacketsClient() {}

    /**
     * Registers all client-side payload handlers.
     * Must only be called from a {@link net.fabricmc.api.ClientModInitializer}.
     */
    public static void registerClientReceivers() {

        // Server pushes full config on join / after reload.
        ClientPlayNetworking.registerGlobalReceiver(SyncConfigPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        PickupRangeClientMod.onServerConfigReceived(
                                payload.defaultItemRange(),
                                payload.defaultXpRange(),
                                payload.maxRange(),
                                payload.minRange(),
                                payload.allowPlayerOverride()
                        )
                )
        );

        // Server pushes per-player effective ranges (after an op runs /pickuprange set).
        ClientPlayNetworking.registerGlobalReceiver(SyncPlayerRangePayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        PickupRangeClientMod.onPlayerRangeReceived(
                                payload.itemRange(),
                                payload.xpRange()
                        )
                )
        );
    }
}

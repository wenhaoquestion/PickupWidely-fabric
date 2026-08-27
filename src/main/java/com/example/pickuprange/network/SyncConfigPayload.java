package com.example.pickuprange.network;

import net.minecraft.network.FriendlyByteBuf;

/** Server-to-client config snapshot for Fabric's legacy play-channel API. */
public record SyncConfigPayload(
        double defaultItemRange,
        double defaultXpRange,
        double maxRange,
        double minRange,
        boolean allowPlayerOverride
) {
    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(defaultItemRange);
        buf.writeDouble(defaultXpRange);
        buf.writeDouble(maxRange);
        buf.writeDouble(minRange);
        buf.writeBoolean(allowPlayerOverride);
    }

    public static SyncConfigPayload read(FriendlyByteBuf buf) {
        return new SyncConfigPayload(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readBoolean());
    }
}

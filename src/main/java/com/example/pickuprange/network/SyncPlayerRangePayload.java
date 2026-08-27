package com.example.pickuprange.network;

import net.minecraft.network.FriendlyByteBuf;

/** Server-to-client effective per-player ranges. */
public record SyncPlayerRangePayload(double itemRange, double xpRange) {
    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(itemRange);
        buf.writeDouble(xpRange);
    }

    public static SyncPlayerRangePayload read(FriendlyByteBuf buf) {
        return new SyncPlayerRangePayload(buf.readDouble(), buf.readDouble());
    }
}
